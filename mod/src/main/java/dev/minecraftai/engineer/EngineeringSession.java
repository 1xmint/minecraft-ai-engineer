package dev.minecraftai.engineer;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** All methods run on the server thread. Persistence is write-ahead, never best-effort. */
final class EngineeringSession implements BuildExecutor {
    final MinecraftServer server;
    final ProjectManager manager;
    final UUID projectId;
    final UUID owner;
    final String worldId;
    final String dimension;
    final Path file;
    ProjectAccess access;
    UUID controller;
    UUID commandActor;
    RegionReservations.Fence delegation;
    net.minecraft.server.network.ServerGamePacketListenerImpl delegationConnection;
    boolean isolationLease;
    private int projectRevision = 1;
    static final class Cursor { int stage; boolean teaching = true; }
    final Map<UUID,Cursor> cursors = new HashMap<>();
    BlockPos corner1, corner2;
    Blueprint blueprint;
    int stage;
    boolean teaching;
    String state = "PENDING", detail = "Select a region, then ask for a design.";
    final List<Change> journal = new ArrayList<>();
    final Map<BlockPos, BlockState> baseline = new LinkedHashMap<>();
    final Map<BlockPos, BlockState> dependencies = new LinkedHashMap<>();
    boolean pendingPlacement;
    boolean undoRequested;
    long nextPlacement;
    int testTick = -1;
    BlockState testOriginal;
    BlockState testLast;
    String verified = "untested";
    String memoryStatus = "not synchronized";
    java.util.concurrent.CompletableFuture<JsonObject> lastCheckpoint;
    record Change(BlockPos pos, BlockState before, BlockState after) {}

    EngineeringSession(ProjectManager manager, UUID projectId, UUID owner, String dimension) {
        this.manager=manager; this.server=manager.server; this.projectId=projectId; this.owner=owner;
        this.controller=owner; this.access=new ProjectAccess(owner); this.worldId=manager.worldId; this.dimension=dimension;
        file=manager.directory.resolve(projectId+".json");
        load();
    }
    UUID actor() { return commandActor == null ? controller : commandActor; }
    Cursor cursor(UUID actor) { return cursors.computeIfAbsent(actor, ignored->new Cursor()); }
    int revision() { return projectRevision; }
    ServerPlayer player() { return server.getPlayerList().getPlayer(actor()); }
    ServerLevel level() {
        for(ServerLevel level:server.getAllLevels()) if(dimension.equals(level.dimension().identifier().toString()))return level;
        throw new IllegalStateException("Project dimension is unavailable");
    }
    void tell(String text) { ServerPlayer player = player(); if (player != null) player.sendSystemMessage(Component.literal("Engineer: " + text)); }
    boolean available() { ServerPlayer p = server.getPlayerList().getPlayer(controller);
        return p != null && dimension.equals(p.level().dimension().identifier().toString()); }
    void requireCreative() {
        ServerPlayer p=player();
        if (!server.isSameThread() || p==null || !p.isCreative() || !dimension.equals(p.level().dimension().identifier().toString()))
            throw new IllegalStateException("World edits require the authenticated controller in the project dimension, in creative mode");
    }
    void claim(ProjectAccess.Permission action, boolean test) {
        requireCreative();
        if((state.equals("EXECUTING") || testTick>=0) && !controller.equals(actor())) throw new IllegalStateException("Stop the active assignment before taking over");
        var lease=manager.acquire(this,actor(),action,test);
        controller=actor(); delegation=lease;delegationConnection=player().connection;isolationLease=test;
    }
    void validateMutation() {
        validateMutation(ProjectAccess.Permission.BUILD);
    }
    void validateMutation(ProjectAccess.Permission required) {
        manager.validate(this);
        if(delegation.action()!=required)throw new IllegalStateException("The current delegation does not authorize this operation");
        ServerPlayer p=server.getPlayerList().getPlayer(controller);
        if(p==null || !p.isCreative() || !dimension.equals(p.level().dimension().identifier().toString())) throw new IllegalStateException("The controller disconnected, changed dimension or left creative mode");
    }
    boolean contains(BlockPos p) {
        return corner1 != null && corner2 != null && p.getX() >= Math.min(corner1.getX(), corner2.getX())
                && p.getX() <= Math.max(corner1.getX(), corner2.getX()) && p.getY() >= Math.min(corner1.getY(), corner2.getY())
                && p.getY() <= Math.max(corner1.getY(), corner2.getY()) && p.getZ() >= Math.min(corner1.getZ(), corner2.getZ())
                && p.getZ() <= Math.max(corner1.getZ(), corner2.getZ());
    }
    BlockPos anchor() {
        if (corner1 == null || corner2 == null) throw new IllegalStateException("Set both selection corners first");
        return new BlockPos(Math.min(corner1.getX(),corner2.getX()), Math.min(corner1.getY(),corner2.getY()), Math.min(corner1.getZ(),corner2.getZ()));
    }
    void select(int corner, BlockPos pos) {
        access.require(actor(),ProjectAccess.Permission.EDIT);
        if(!owner.equals(actor()))throw new IllegalStateException("Only the project owner can change the delegated workspace");
        if (state.equals("PLANNING") || state.equals("EXECUTING") || testTick >= 0 || testOriginal!=null || !journal.isEmpty()) throw new IllegalStateException("Stop planning/building and undo assistant work before changing the delegated region");
        if (corner == 1) {corner1 = pos.immutable();corner2=null;}
        else {if(corner1==null)throw new IllegalStateException("Set the first corner before completing the workspace");manager.workspace(this,actor(),corner1,pos);corner2 = pos.immutable();}
        baseline.clear();dependencies.clear(); state = blueprint == null ? "PENDING" : "READY"; save(); tell("Corner " + corner + " set to " + pos.toShortString());
    }
    void accept(Blueprint value) {
        access.require(actor(),ProjectAccess.Permission.EDIT);
        if (state.equals("PLANNING") || state.equals("EXECUTING") || testTick >= 0 || testOriginal!=null || !journal.isEmpty()) throw new IllegalStateException("Stop planning/building and undo the previous assignment before replacing its blueprint");
        JsonObject intended=value.source().deepCopy();
        intended.addProperty("projectId",projectId.toString()); intended.addProperty("revision",Math.addExact(projectRevision,1));
        blueprint=Blueprint.parse(intended,worldId,dimension); projectRevision=blueprint.revision();
        baseline.clear(); dependencies.clear(); cursors.clear(); stage = 0; teaching = true; verified = "untested"; state = "READY";
        manager.changed(this);
        save(); explain();
    }
    void explain() {
        if (blueprint == null) throw new IllegalStateException("Load a reference or request a plan first");
        Cursor c=cursor(actor()); Blueprint.Stage s = blueprint.stages().get(c.stage);
        tell("Step " + (c.stage + 1) + "/" + blueprint.stages().size() + ": " + s.title() + ". " + s.explanation());
    }
    void moveStage(int delta) { access.require(actor(),ProjectAccess.Permission.VIEW); if (blueprint == null) throw new IllegalStateException("No blueprint"); Cursor c=cursor(actor());c.stage = Math.clamp(c.stage + delta,0,blueprint.stages().size()-1); c.teaching=true; save(); explain(); }
    void build() {
        requireCreative();
        access.require(actor(),ProjectAccess.Permission.BUILD);
        if(testOriginal!=null) throw new IllegalStateException("Reconcile the interrupted functional test with resume before building");
        if (blueprint == null) throw new IllegalStateException("Preview a blueprint first");
        if (state.equals("EXECUTING") || testTick >= 0) throw new IllegalStateException("An operation is already running");
        if (!baseline.isEmpty()) { resume(); return; }
        Map<BlockPos,BlockState> proposedBaseline = new LinkedHashMap<>();
        for (Blueprint.PlannedBlock b : blueprint.blocks()) {
            validateTarget(b.pos());
            BlockState actual = level().getBlockState(b.pos());
            if (!actual.isAir() && !Blueprint.matches(b.state(), actual)) throw new IllegalStateException("Occupied or incorrect block at " + b.pos().toShortString() + "; move the preview or correct it yourself");
            proposedBaseline.put(b.pos(), actual);
        }
        var proposedDependencies=captureDependencies();
        claim(ProjectAccess.Permission.BUILD,false);
        baseline.putAll(proposedBaseline); dependencies.putAll(proposedDependencies);
        // Selecting the immutable blueprint and issuing build is the explicit bounded delegation.
        state = "EXECUTING"; detail = "Building remaining blocks inside the selected region"; verified="untested";
        save(); checkpoint(); tell(detail + ". /engineer stop interrupts; /engineer undo reverts only assistant blocks.");
    }
    void validateTarget(BlockPos pos) {
        if (!contains(pos)) throw new IllegalStateException("Target outside delegated region: " + pos.toShortString());
        if (!level().hasChunkAt(pos)) throw new IllegalStateException("Chunk unloaded: " + pos.toShortString());
        if (!level().getWorldBorder().isWithinBounds(pos) || pos.getY() < level().getMinY() || pos.getY() > level().getMaxY())
            throw new IllegalStateException("Target outside world bounds");
        if (level().getBlockEntity(pos) != null) throw new IllegalStateException("Inventories and block entities are protected");
    }
    Map<BlockPos, BlockState> expectedNow() {
        Map<BlockPos, BlockState> expected = new LinkedHashMap<>(baseline);
        for (Change c : journal) expected.put(c.pos(), c.after());
        return expected;
    }
    void reconcile() {
        requireCreative();
        if (undoRequested) throw new IllegalStateException("Undo was interrupted; use /engineer undo to reconcile it");
        for (Map.Entry<BlockPos, BlockState> entry : expectedNow().entrySet()) {
            validateTarget(entry.getKey());
            BlockState actual = level().getBlockState(entry.getKey());
            if (!Blueprint.matches(entry.getValue(), actual)) {
                // A crash between journal flush and placement may leave before-state. It is safe to retry only that case.
                Change pending = journal.isEmpty() ? null : journal.getLast();
                if (pendingPlacement && pending != null && pending.pos().equals(entry.getKey()) && actual.equals(pending.before())) {
                    journal.removeLast(); pendingPlacement=false;save(); continue;
                }
                throw new IllegalStateException("Player/world edit conflicts at " + entry.getKey().toShortString() + ". Resolve the conflict before resuming.");
            }
        }
        if (pendingPlacement) { pendingPlacement=false;save(); }
        for(var entry:dependencies.entrySet()) {
            if(!level().hasChunkAt(entry.getKey()) || !Blueprint.matches(entry.getValue(),level().getBlockState(entry.getKey())))
                throw new IllegalStateException("A support or neighboring dependency changed; reconcile before resuming");
        }
    }
    Map<BlockPos,BlockState> captureDependencies() {
        var planned=new HashSet<BlockPos>(); blueprint.blocks().forEach(b->planned.add(b.pos()));
        var result=new LinkedHashMap<BlockPos,BlockState>();
        for(var block:blueprint.blocks()) for(BlockPos p:BlockPos.betweenClosed(block.pos().offset(-1,-1,-1),block.pos().offset(1,1,1))) {
            if(planned.contains(p))continue;
            if(!level().hasChunkAt(p))throw new IllegalStateException("Assignment dependencies include an unloaded chunk");
            result.put(p.immutable(),level().getBlockState(p));
        }
        if(result.size()>65536)throw new IllegalStateException("Assignment dependency footprint exceeds the limit");
        return result;
    }
    @Override public void resume() {
        access.require(actor(),ProjectAccess.Permission.BUILD);
        if (testOriginal!=null) { claim(ProjectAccess.Permission.BUILD,true);restoreTest();state="PAUSED";detail=testOriginal==null?"Interrupted test reconciled; inspect before requesting another test":"Test input still needs restoration; preserve/resolve conflicting edits before resume";manager.release(this);save();tell(detail);return; }
        if (blueprint == null || baseline.isEmpty()) throw new IllegalStateException("No interrupted assignment to resume; use build");
        reconcile(); claim(ProjectAccess.Permission.BUILD,false);state="EXECUTING"; detail="Reconciled; resuming the same bounded assignment"; save(); checkpoint(); tell(detail);
    }
    @Override public void pause(String reason) {
        state="PAUSED"; detail=reason; teaching=false;
        cursor(controller).teaching=false;
        // An active functional test restores the exact input before relinquishing its authority.
        if (testOriginal != null) restoreTest();
        manager.release(this);
        save(); checkpoint(); tell(reason);
    }
    @Override public String status() { return state + ": " + detail + "; assistant placements=" + journal.size() + "; functional verification=" + verified+"; project memory="+memoryStatus; }
    @Override public void tick() {
        if (!available()) { if (state.equals("EXECUTING") || testTick>=0) pause("Controller disconnected or changed dimension; explicit resume required"); tickTutorials();return; }
        if (testTick>=0) { tickTest(); return; }
        if (blueprint != null && verified.startsWith("passed:") && server.getTickCount()%10==0) {
            boolean changed=blueprint.blocks().stream().anyMatch(b->!level().hasChunkAt(b.pos()) || !Blueprint.matches(b.state(),level().getBlockState(b.pos())));
            if(changed){verified="stale: circuit changed or chunks unloaded since test";detail="Functional evidence is stale; inspect and test the current circuit again";state="PAUSED";save();checkpoint();tell(detail);}
        }
        if (state.equals("EXECUTING")) {
            try {
                validateMutation();
                reconcile();
                if (server.getTickCount() < nextPlacement) return;
                nextPlacement=server.getTickCount()+4;
                for (Blueprint.PlannedBlock b : blueprint.blocks()) {
                    BlockState actual = level().getBlockState(b.pos());
                    if (Blueprint.matches(b.state(),actual)) continue;
                    if (!actual.isAir() || !b.state().canSurvive(level(), b.pos())) throw new IllegalStateException("Placement blocked at " + b.pos().toShortString());
                    journal.add(new Change(b.pos(),actual,b.state())); pendingPlacement=true;save();
                    validateMutation();
                    if (!level().setBlockAndUpdate(b.pos(),b.state())) throw new IllegalStateException("Minecraft rejected placement");
                    pendingPlacement=false;save(); return;
                }
                state="COMPLETED"; detail="Geometry matches the blueprint. Functional test still required.";manager.release(this); save(); checkpoint(); tell(detail);
            } catch (RuntimeException e) { pause(e.getMessage()); }
        }
        tickTutorials();
    }
    void tickTutorials() {
        if(blueprint==null || server.getTickCount()%10!=0)return;
        for(var entry:new ArrayList<>(cursors.entrySet())) {
            UUID actor=entry.getKey();Cursor c=entry.getValue();ServerPlayer p=server.getPlayerList().getPlayer(actor);
            if(!c.teaching || p==null || !dimension.equals(p.level().dimension().identifier().toString()) || !access.allows(actor,ProjectAccess.Permission.VIEW))continue;
            String current=blueprint.stages().get(c.stage).id();
            boolean complete=blueprint.blocks().stream().filter(b->b.stage().equals(current)).allMatch(b->level().hasChunkAt(b.pos()) && Blueprint.matches(b.state(),level().getBlockState(b.pos())));
            if(complete){
                if(c.stage<blueprint.stages().size()-1){c.stage++;p.sendSystemMessage(Component.literal("Engineer: Step "+(c.stage+1)+": "+blueprint.stages().get(c.stage).explanation()));}
                else {c.teaching=false;p.sendSystemMessage(Component.literal("Engineer: Your tutorial sections match; a functional test is still required."));}
                save();
            }
        }
    }
    void undo() {
        requireCreative();
        access.require(actor(),ProjectAccess.Permission.UNDO);
        if(state.equals("EXECUTING") || testTick>=0)pause("Undo requested; active execution paused");
        claim(ProjectAccess.Permission.UNDO,true);
        try { undoReserved(); } finally { manager.release(this); }
    }
    void undoReserved() {
        if (testOriginal!=null) restoreTest();
        if(testOriginal!=null)throw new IllegalStateException("Reconcile the interrupted test input before undo; the circuit and recovery checkpoint were preserved");
        if (undoRequested && !journal.isEmpty() && level().hasChunkAt(journal.getLast().pos())
                && level().getBlockState(journal.getLast().pos()).equals(journal.getLast().before())) { journal.removeLast();save(); }
        // Preflight every entry before removing anything. Never erase a later player edit.
        Set<BlockPos> assistantPositions=new HashSet<>();journal.forEach(c->assistantPositions.add(c.pos()));
        Set<BlockPos> plannedPositions=new HashSet<>();if(blueprint!=null)blueprint.blocks().forEach(b->plannedPositions.add(b.pos()));
        for (Change c : journal) {
            validateTarget(c.pos());
            if (!Blueprint.matches(c.after(),level().getBlockState(c.pos()))) throw new IllegalStateException("Undo blocked by later edit at " + c.pos().toShortString());
            for(net.minecraft.core.Direction direction:net.minecraft.core.Direction.values()) {
                BlockPos neighbor=c.pos().relative(direction);
                if(!level().hasChunkAt(neighbor))throw new IllegalStateException("Undo needs loaded neighboring blocks");
                BlockState actual=level().getBlockState(neighbor);
                if(actual.isAir() || assistantPositions.contains(neighbor))continue;
                boolean outside=!plannedPositions.contains(neighbor);
                boolean playerSupported=(actual.is(net.minecraft.world.level.block.Blocks.LEVER)||actual.is(net.minecraft.world.level.block.Blocks.REPEATER))&&neighbor.below().equals(c.pos());
                if(outside || playerSupported) throw new IllegalStateException("Undo would disturb a player block at "+neighbor.toShortString()+"; remove its dependency or take over this section first");
            }
        }
        state="PAUSED"; detail="Undo requested";undoRequested=true;save();
        while (!journal.isEmpty()) {
            Change c = journal.getLast();
            validateMutation(ProjectAccess.Permission.UNDO);
            level().setBlockAndUpdate(c.pos(),c.before());
            if(!level().getBlockState(c.pos()).equals(c.before())) throw new IllegalStateException("Minecraft rejected undo; journal retained for recovery");
            journal.removeLast(); save();
        }
        baseline.clear();dependencies.clear();pendingPlacement=false;undoRequested=false;verified="untested"; state="READY"; detail="Assistant placements undone; player-built blocks kept";manager.release(this); save(); checkpoint(); tell(detail);
    }
    JsonObject inspect() {
        access.require(actor(),ProjectAccess.Permission.VIEW);
        BlockPos min=anchor();
        BlockPos max=new BlockPos(Math.max(corner1.getX(),corner2.getX()),Math.max(corner1.getY(),corner2.getY()),Math.max(corner1.getZ(),corner2.getZ()));
        long volume=(long)(max.getX()-min.getX()+1)*(max.getY()-min.getY()+1)*(max.getZ()-min.getZ()+1);
        if (volume>4096 || volume<=0) throw new IllegalStateException("Inspection limit is 4096 blocks");
        JsonObject snapshot=new JsonObject(); snapshot.addProperty("worldId",worldId); snapshot.addProperty("dimension",dimension);
        snapshot.addProperty("tick",level().getGameTime()); JsonObject region=new JsonObject(); region.add("min",Blueprint.jsonPos(min)); region.add("max",Blueprint.jsonPos(max)); snapshot.add("region",region);
        JsonArray blocks=new JsonArray(); int nonair=0, mismatch=0;
        for (BlockPos p : BlockPos.betweenClosed(min,max)) {
            if (!level().hasChunkAt(p)) throw new IllegalStateException("Inspection stopped: unloaded chunk is not air");
            BlockState actual=level().getBlockState(p); JsonObject b=new JsonObject(); b.add("pos",Blueprint.jsonPos(p)); b.addProperty("state",Blueprint.describe(actual)); blocks.add(b); if (!actual.isAir()) nonair++;
        }
        if (blueprint!=null) for (Blueprint.PlannedBlock b:blueprint.blocks()) if (!level().hasChunkAt(b.pos()) || !Blueprint.matches(b.state(),level().getBlockState(b.pos()))) mismatch++;
        snapshot.add("blocks",blocks); LocalFiles.write(file.resolveSibling(file.getFileName()+".snapshot.json"),snapshot);
        tell("Inspected " + volume + " exact block states; " + nonair + " non-air; " + mismatch + " blueprint differences. Snapshot saved locally.");
        Companion.report(this,Companion.gateway(this,"/v2/observe",snapshot,actor())); return snapshot;
    }
    void test() {
        access.require(actor(),ProjectAccess.Permission.BUILD);
        requireCreative(); if (blueprint==null || !blueprint.source().has("provenance") || !blueprint.source().getAsJsonObject("provenance").get("template").getAsString().equals("signal-lamp")) throw new IllegalStateException("Only the curated signal-lamp has a functional test");
        if(testOriginal!=null) throw new IllegalStateException("Reconcile the interrupted functional test with resume before starting another test");
        if (state.equals("EXECUTING") || testTick>=0) throw new IllegalStateException("Stop building before testing");
        validateTestArea();
        claim(ProjectAccess.Permission.BUILD,true);
        testOriginal=level().getBlockState(input()); testLast=testOriginal; testTick=0; verified="untested"; state="VERIFYING"; detail="Tick-accurate off/on/off test running"; save(); tell(detail);
    }
    void validateTestArea() {
        for (Blueprint.PlannedBlock b:blueprint.blocks()) { validateTarget(b.pos()); if (!Blueprint.matches(b.state(),level().getBlockState(b.pos()))) throw new IllegalStateException("Complete/correct the blueprint before testing"); }
        Set<BlockPos> planned=new HashSet<>(); blueprint.blocks().forEach(b->planned.add(b.pos()));
        // Refuse nearby structures: controlled inputs must not operate unrelated player circuits.
        for (Blueprint.PlannedBlock b:blueprint.blocks()) for (BlockPos p:BlockPos.betweenClosed(b.pos().offset(-2,-2,-2),b.pos().offset(2,2,2))) {
            if (!level().hasChunkAt(p) || (!planned.contains(p) && !level().getBlockState(p).isAir())) throw new IllegalStateException("Test needs a two-block air margin around the floating reference circuit");
        }
    }
    BlockPos input() { return blueprint.blocks().stream().filter(b->b.component().equals("input")).findFirst().orElseThrow().pos(); }
    BlockPos output() { return blueprint.blocks().stream().filter(b->b.state().hasProperty(BlockStateProperties.LIT)).findFirst().orElseThrow().pos(); }
    void tickTest() {
        try {
            validateMutation(); requireCreative(); validateTestArea();
            if (!level().getBlockState(input()).equals(testLast)) throw new IllegalStateException("Input changed during test; player edit preserved");
            if (testTick==0) setTestInput(false);
            if (testTick==8) { assertLamp(false); setTestInput(true); }
            if (testTick==16) { assertLamp(true); setTestInput(false); }
            if (testTick==24) { assertLamp(false); restoreTest(); verified="passed:off@8,on@16,off@24"; state="COMPLETED"; detail="Actual Minecraft functional test passed at ticks 8, 16, 24";manager.release(this); save(); checkpoint(); tell(detail); return; }
            testTick++;
        } catch(RuntimeException e) { verified="failed: "+e.getMessage(); pause("Functional test failed: "+e.getMessage()); }
    }
    void assertLamp(boolean lit) { if (level().getBlockState(output()).getValue(BlockStateProperties.LIT)!=lit) throw new IllegalStateException("Lamp expected lit="+lit+" at test tick "+testTick); }
    void setTestInput(boolean value) {
        validateMutation();
        testLast=testOriginal.setValue(BlockStateProperties.POWERED,value);save();
        level().setBlockAndUpdate(input(),testLast);
    }
    void restoreTest() {
        if (testOriginal!=null && server.isSameThread() && available() && manager.mayMutate(this) && server.getPlayerList().getPlayer(controller).isCreative() && contains(input()) && level().hasChunkAt(input()) && level().getBlockEntity(input())==null) {
            BlockState actual=level().getBlockState(input());
            if(actual.equals(testOriginal) || !actual.equals(testLast)) {
                // Already restored, or superseded by a later player edit. No write.
                testOriginal=null;testLast=null;
            } else {
                try { validateTestArea(); }
                catch(RuntimeException unsafe) { testTick=-1; return; }
                level().setBlockAndUpdate(input(),testOriginal);
                if(level().getBlockState(input()).equals(testOriginal)) {testOriginal=null;testLast=null;}
            }
        }
        testTick=-1;
    }
    void checkpoint() {
        if (blueprint==null) return; JsonObject out=new JsonObject(); out.addProperty("worldId",worldId); out.addProperty("dimension",dimension);
        out.addProperty("projectId",blueprint.projectId());out.addProperty("blueprintRevision",blueprint.revision()); out.addProperty("taskId",blueprint.id()); out.addProperty("status",state); out.addProperty("detail",detail); out.addProperty("verified",verified);
        var observed=new JsonObject();observed.addProperty("controller",controller.toString());observed.addProperty("assistantPlacements",journal.size());observed.addProperty("tick",level().getGameTime());out.add("observed",observed);
        lastCheckpoint=Companion.gateway(this,"/v2/checkpoint",out,owner);Companion.report(this,lastCheckpoint);
    }
    void save() {
        JsonObject json=new JsonObject(); json.addProperty("schemaVersion",2); json.addProperty("worldId",worldId); json.addProperty("dimension",dimension);
        json.addProperty("projectId",projectId.toString());json.addProperty("projectRevision",projectRevision);json.add("access",access.json());
        json.addProperty("memoryStatus",memoryStatus);
        json.addProperty("controller",controller.toString());json.addProperty("owner",owner.toString()); json.addProperty("status",state); json.addProperty("detail",detail); json.addProperty("stage",cursor(controller).stage); json.addProperty("verified",verified);
        JsonArray lessons=new JsonArray();cursors.forEach((actor,cursor)->{var item=new JsonObject();item.addProperty("actorId",actor.toString());item.addProperty("stage",cursor.stage);item.addProperty("teaching",cursor.teaching);lessons.add(item);});json.add("tutorials",lessons);
        json.addProperty("pendingPlacement",pendingPlacement);json.addProperty("undoRequested",undoRequested);
        if (corner1!=null) json.add("corner1",Blueprint.jsonPos(corner1)); if (corner2!=null) json.add("corner2",Blueprint.jsonPos(corner2));
        if (blueprint!=null) json.add("blueprint",blueprint.source()); JsonArray changes=new JsonArray();
        if (testOriginal!=null) { json.addProperty("testOriginal",Blueprint.describe(testOriginal)); json.addProperty("testLast",Blueprint.describe(testLast)); }
        for(Change c:journal) { JsonObject x=new JsonObject(); x.add("pos",Blueprint.jsonPos(c.pos())); x.addProperty("before",Blueprint.describe(c.before())); x.addProperty("after",Blueprint.describe(c.after())); changes.add(x); }
        json.add("journal",changes); JsonArray initial=new JsonArray(); baseline.forEach((pos,state)->{JsonObject x=new JsonObject();x.add("pos",Blueprint.jsonPos(pos));x.addProperty("state",Blueprint.describe(state));initial.add(x);});json.add("baseline",initial);
        JsonArray surrounding=new JsonArray();dependencies.forEach((pos,state)->{var item=new JsonObject();item.add("pos",Blueprint.jsonPos(pos));item.addProperty("state",Blueprint.describe(state));surrounding.add(item);});json.add("dependencies",surrounding);
        LocalFiles.write(file,json);
    }
    void load() {
        if(!Files.exists(file)) return;
        try {
            JsonObject json=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            if(json.get("schemaVersion").getAsInt()!=2 || !projectId.toString().equals(json.get("projectId").getAsString()))throw new IllegalStateException("Legacy or mismatched project data requires explicit administrator migration");
            if (!worldId.equals(json.get("worldId").getAsString()) || !dimension.equals(json.get("dimension").getAsString()) || !owner.toString().equals(json.get("owner").getAsString())) throw new IllegalStateException("Checkpoint identity mismatch");
            access=ProjectAccess.read(owner,json.getAsJsonObject("access"));controller=UUID.fromString(json.get("controller").getAsString());
            projectRevision=json.get("projectRevision").getAsBigDecimal().intValueExact();if(projectRevision<1)throw new IllegalStateException("Invalid project revision");
            if(json.has("corner1")) corner1=Blueprint.pos(json.getAsJsonObject("corner1")); if(json.has("corner2")) corner2=Blueprint.pos(json.getAsJsonObject("corner2"));
            if(json.has("blueprint")) blueprint=Blueprint.parse(json.getAsJsonObject("blueprint"),worldId,dimension);
            if(blueprint!=null && (blueprint.revision()!=projectRevision || !blueprint.projectId().equals(projectId.toString())))throw new IllegalStateException("Intended blueprint identity mismatch");
            if(json.has("testOriginal")) { testOriginal=Blueprint.state(json.get("testOriginal").getAsString());testLast=Blueprint.state(json.get("testLast").getAsString()); }
            stage=json.get("stage").getAsInt();
            if(stage<0 || (blueprint!=null && stage>=blueprint.stages().size())) throw new IllegalStateException("Checkpoint has an invalid tutorial stage");
            for(JsonElement entry:json.getAsJsonArray("tutorials")){var item=entry.getAsJsonObject();var actor=UUID.fromString(item.get("actorId").getAsString());var cursor=cursor(actor);cursor.stage=item.get("stage").getAsBigDecimal().intValueExact();cursor.teaching=false;if(cursor.stage<0 || (blueprint!=null && cursor.stage>=blueprint.stages().size()))throw new IllegalStateException("Invalid personal tutorial stage");}
            verified=json.get("verified").getAsString();
            if(verified.startsWith("passed:")) verified="stale: saved functional evidence requires a new world observation and test";
            pendingPlacement=json.has("pendingPlacement")&&json.get("pendingPlacement").getAsBoolean();undoRequested=json.has("undoRequested")&&json.get("undoRequested").getAsBoolean();
            for(JsonElement e:json.getAsJsonArray("journal")){JsonObject x=e.getAsJsonObject();journal.add(new Change(Blueprint.pos(x.getAsJsonObject("pos")),Blueprint.state(x.get("before").getAsString()),Blueprint.state(x.get("after").getAsString())));}
            for(JsonElement e:json.getAsJsonArray("baseline")){JsonObject x=e.getAsJsonObject();baseline.put(Blueprint.pos(x.getAsJsonObject("pos")),Blueprint.state(x.get("state").getAsString()));}
            if(json.has("dependencies"))for(JsonElement e:json.getAsJsonArray("dependencies")){JsonObject x=e.getAsJsonObject();dependencies.put(Blueprint.pos(x.getAsJsonObject("pos")),Blueprint.state(x.get("state").getAsString()));}
            if(journal.size()>4096 || baseline.size()>4096 || dependencies.size()>65536 || cursors.size()>128)throw new IllegalStateException("Checkpoint exceeds project limits");
            state="PAUSED";detail="Checkpoint restored. Inspect the world; explicit resume reconciles before editing.";
        }catch(Exception e){throw new IllegalStateException("Cannot safely load checkpoint; keep it for recovery",e);}
    }
}
