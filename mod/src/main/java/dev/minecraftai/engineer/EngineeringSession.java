package dev.minecraftai.engineer;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** All methods run on the server thread. Persistence is write-ahead, never best-effort. */
final class EngineeringSession implements BuildExecutor {
    final MinecraftServer server;
    final UUID owner;
    final String worldId;
    final String dimension;
    final Path file;
    BlockPos corner1, corner2;
    Blueprint blueprint;
    int stage;
    boolean teaching;
    String state = "PENDING", detail = "Select a region, then ask for a design.";
    final List<Change> journal = new ArrayList<>();
    final Map<BlockPos, BlockState> baseline = new LinkedHashMap<>();
    boolean pendingPlacement;
    boolean undoRequested;
    long nextPlacement;
    int testTick = -1;
    BlockState testOriginal;
    BlockState testLast;
    String verified = "untested";
    record Change(BlockPos pos, BlockState before, BlockState after) {}

    EngineeringSession(MinecraftServer server, ServerPlayer player) {
        this.server = server; owner = player.getUUID(); worldId = LocalFiles.worldId(server);
        dimension = player.level().dimension().identifier().toString();
        file = LocalFiles.configDir().resolve("sessions").resolve(worldId + "-" + owner + "-" + dimension.replace(':', '_') + ".json");
        load();
    }
    ServerPlayer player() { return server.getPlayerList().getPlayer(owner); }
    void tell(String text) { ServerPlayer player = player(); if (player != null) player.sendSystemMessage(Component.literal("Engineer: " + text)); }
    boolean available() { ServerPlayer p = player(); return p != null && server.isSingleplayer() && !server.isPublished()
            && server.getPlayerList().getPlayerCount() == 1 && dimension.equals(p.level().dimension().identifier().toString()); }
    void requireCreative() {
        if (!server.isSameThread() || !available() || !player().isCreative()) throw new IllegalStateException("World edits require the single-player owner in creative mode");
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
        if (state.equals("PLANNING") || state.equals("EXECUTING") || testTick >= 0 || testOriginal!=null || !journal.isEmpty()) throw new IllegalStateException("Stop planning/building and undo assistant work before changing the delegated region");
        if (corner == 1) corner1 = pos.immutable(); else corner2 = pos.immutable();
        baseline.clear(); state = blueprint == null ? "PENDING" : "READY"; save(); tell("Corner " + corner + " set to " + pos.toShortString());
    }
    void accept(Blueprint value) {
        if (state.equals("PLANNING") || state.equals("EXECUTING") || testTick >= 0 || testOriginal!=null || !journal.isEmpty()) throw new IllegalStateException("Stop planning/building and undo the previous assignment before replacing its blueprint");
        blueprint = value; baseline.clear(); stage = 0; teaching = true; verified = "untested"; state = "READY";
        save(); explain();
    }
    void explain() {
        if (blueprint == null) throw new IllegalStateException("Load a reference or request a plan first");
        Blueprint.Stage s = blueprint.stages().get(stage);
        tell("Step " + (stage + 1) + "/" + blueprint.stages().size() + ": " + s.title() + ". " + s.explanation());
    }
    void moveStage(int delta) { if (blueprint == null) throw new IllegalStateException("No blueprint"); stage = Math.clamp(stage + delta,0,blueprint.stages().size()-1); teaching=true; save(); explain(); }
    void build() {
        requireCreative();
        if (blueprint == null) throw new IllegalStateException("Preview a blueprint first");
        if (state.equals("EXECUTING") || testTick >= 0) throw new IllegalStateException("An operation is already running");
        if (!baseline.isEmpty()) { resume(); return; }
        Map<BlockPos,BlockState> proposedBaseline = new LinkedHashMap<>();
        for (Blueprint.PlannedBlock b : blueprint.blocks()) {
            validateTarget(b.pos());
            BlockState actual = player().level().getBlockState(b.pos());
            if (!actual.isAir() && !Blueprint.matches(b.state(), actual)) throw new IllegalStateException("Occupied or incorrect block at " + b.pos().toShortString() + "; move the preview or correct it yourself");
            proposedBaseline.put(b.pos(), actual);
        }
        baseline.putAll(proposedBaseline);
        // Selecting the immutable blueprint and issuing build is the explicit bounded delegation.
        state = "EXECUTING"; detail = "Building remaining blocks inside the selected region"; verified="untested";
        save(); checkpoint(); tell(detail + ". /engineer stop interrupts; /engineer undo reverts only assistant blocks.");
    }
    void validateTarget(BlockPos pos) {
        if (!contains(pos)) throw new IllegalStateException("Target outside delegated region: " + pos.toShortString());
        if (!player().level().hasChunkAt(pos)) throw new IllegalStateException("Chunk unloaded: " + pos.toShortString());
        if (!player().level().getWorldBorder().isWithinBounds(pos) || pos.getY() < player().level().getMinY() || pos.getY() > player().level().getMaxY())
            throw new IllegalStateException("Target outside world bounds");
        if (player().level().getBlockEntity(pos) != null) throw new IllegalStateException("Inventories and block entities are protected");
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
            BlockState actual = player().level().getBlockState(entry.getKey());
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
    }
    @Override public void resume() {
        if (testOriginal!=null) { requireCreative();restoreTest();state="PAUSED";detail="Interrupted test reconciled; inspect before requesting another test";save();tell(detail);return; }
        if (blueprint == null || baseline.isEmpty()) throw new IllegalStateException("No interrupted assignment to resume; use build");
        reconcile(); state="EXECUTING"; detail="Reconciled; resuming the same bounded assignment"; save(); checkpoint(); tell(detail);
    }
    @Override public void pause(String reason) {
        state="PAUSED"; detail=reason; teaching=false;
        // An active functional test restores the exact input before relinquishing its authority.
        if (testOriginal != null) restoreTest();
        save(); checkpoint(); tell(reason);
    }
    @Override public String status() { return state + ": " + detail + "; assistant placements=" + journal.size() + "; functional verification=" + verified; }
    @Override public void tick() {
        if (!available()) { if (state.equals("EXECUTING") || testTick>=0) pause("Owner disconnected or changed dimension; explicit resume required"); return; }
        if (testTick>=0) { tickTest(); return; }
        if (blueprint != null && verified.startsWith("passed:") && server.getTickCount()%10==0) {
            boolean changed=blueprint.blocks().stream().anyMatch(b->!player().level().hasChunkAt(b.pos()) || !Blueprint.matches(b.state(),player().level().getBlockState(b.pos())));
            if(changed){verified="stale: circuit changed or chunks unloaded since test";detail="Functional evidence is stale; inspect and test the current circuit again";state="PAUSED";save();checkpoint();tell(detail);}
        }
        if (state.equals("EXECUTING")) {
            try {
                reconcile();
                if (server.getTickCount() < nextPlacement) return;
                nextPlacement=server.getTickCount()+4;
                for (Blueprint.PlannedBlock b : blueprint.blocks()) {
                    BlockState actual = player().level().getBlockState(b.pos());
                    if (Blueprint.matches(b.state(),actual)) continue;
                    if (!actual.isAir() || !b.state().canSurvive(player().level(), b.pos())) throw new IllegalStateException("Placement blocked at " + b.pos().toShortString());
                    journal.add(new Change(b.pos(),actual,b.state())); pendingPlacement=true;save();
                    if (!player().level().setBlockAndUpdate(b.pos(),b.state())) throw new IllegalStateException("Minecraft rejected placement");
                    pendingPlacement=false;save(); return;
                }
                state="COMPLETED"; detail="Geometry matches the blueprint. Functional test still required."; save(); checkpoint(); tell(detail);
            } catch (RuntimeException e) { pause(e.getMessage()); }
        }
        if (teaching && blueprint != null && server.getTickCount()%10==0) {
            String current=blueprint.stages().get(stage).id();
            boolean complete=blueprint.blocks().stream().filter(b->b.stage().equals(current)).allMatch(b->player().level().hasChunkAt(b.pos()) && Blueprint.matches(b.state(),player().level().getBlockState(b.pos())));
            if (complete) {
                if (stage < blueprint.stages().size()-1) { stage++; save(); explain(); }
                else { teaching=false; save(); tell("All tutorial sections match. Use /engineer test in an isolated area to verify behavior."); }
            }
        }
    }
    void undo() {
        requireCreative();
        if (testOriginal!=null) restoreTest();
        if (undoRequested && !journal.isEmpty() && player().level().hasChunkAt(journal.getLast().pos())
                && player().level().getBlockState(journal.getLast().pos()).equals(journal.getLast().before())) { journal.removeLast();save(); }
        // Preflight every entry before removing anything. Never erase a later player edit.
        for (Change c : journal) { validateTarget(c.pos()); if (!Blueprint.matches(c.after(),player().level().getBlockState(c.pos()))) throw new IllegalStateException("Undo blocked by later edit at " + c.pos().toShortString()); }
        state="PAUSED"; detail="Undo requested";undoRequested=true;save();
        while (!journal.isEmpty()) {
            Change c = journal.getLast();
            player().level().setBlockAndUpdate(c.pos(),c.before());
            if(!player().level().getBlockState(c.pos()).equals(c.before())) throw new IllegalStateException("Minecraft rejected undo; journal retained for recovery");
            journal.removeLast(); save();
        }
        baseline.clear();pendingPlacement=false;undoRequested=false;verified="untested"; state="READY"; detail="Assistant placements undone; player-built blocks kept"; save(); checkpoint(); tell(detail);
    }
    JsonObject inspect() {
        BlockPos min=anchor();
        BlockPos max=new BlockPos(Math.max(corner1.getX(),corner2.getX()),Math.max(corner1.getY(),corner2.getY()),Math.max(corner1.getZ(),corner2.getZ()));
        long volume=(long)(max.getX()-min.getX()+1)*(max.getY()-min.getY()+1)*(max.getZ()-min.getZ()+1);
        if (volume>4096 || volume<=0) throw new IllegalStateException("Inspection limit is 4096 blocks");
        JsonObject snapshot=new JsonObject(); snapshot.addProperty("worldId",worldId); snapshot.addProperty("dimension",dimension);
        snapshot.addProperty("tick",player().level().getGameTime()); JsonObject region=new JsonObject(); region.add("min",Blueprint.jsonPos(min)); region.add("max",Blueprint.jsonPos(max)); snapshot.add("region",region);
        JsonArray blocks=new JsonArray(); int nonair=0, mismatch=0;
        for (BlockPos p : BlockPos.betweenClosed(min,max)) {
            if (!player().level().hasChunkAt(p)) throw new IllegalStateException("Inspection stopped: unloaded chunk is not air");
            BlockState actual=player().level().getBlockState(p); JsonObject b=new JsonObject(); b.add("pos",Blueprint.jsonPos(p)); b.addProperty("state",Blueprint.describe(actual)); blocks.add(b); if (!actual.isAir()) nonair++;
        }
        if (blueprint!=null) for (Blueprint.PlannedBlock b:blueprint.blocks()) if (!player().level().hasChunkAt(b.pos()) || !Blueprint.matches(b.state(),player().level().getBlockState(b.pos()))) mismatch++;
        snapshot.add("blocks",blocks); LocalFiles.write(file.resolveSibling(file.getFileName()+".snapshot.json"),snapshot);
        tell("Inspected " + volume + " exact block states; " + nonair + " non-air; " + mismatch + " blueprint differences. Snapshot saved locally.");
        Companion.post("/v1/observe",snapshot).exceptionally(error -> null); return snapshot;
    }
    void test() {
        requireCreative(); if (blueprint==null || !blueprint.source().has("provenance") || !blueprint.source().getAsJsonObject("provenance").get("template").getAsString().equals("signal-lamp")) throw new IllegalStateException("Only the curated signal-lamp has a functional test");
        if (state.equals("EXECUTING") || testTick>=0) throw new IllegalStateException("Stop building before testing");
        for (Blueprint.PlannedBlock b:blueprint.blocks()) { validateTarget(b.pos()); if (!Blueprint.matches(b.state(),player().level().getBlockState(b.pos()))) throw new IllegalStateException("Complete/correct the blueprint before testing"); }
        Set<BlockPos> planned=new HashSet<>(); blueprint.blocks().forEach(b->planned.add(b.pos()));
        // Refuse nearby structures: controlled inputs must not operate unrelated player circuits.
        for (Blueprint.PlannedBlock b:blueprint.blocks()) for (BlockPos p:BlockPos.betweenClosed(b.pos().offset(-2,-2,-2),b.pos().offset(2,2,2))) {
            if (!player().level().hasChunkAt(p) || (!planned.contains(p) && !player().level().getBlockState(p).isAir())) throw new IllegalStateException("Test needs a two-block air margin around the floating reference circuit");
        }
        testOriginal=player().level().getBlockState(input()); testLast=testOriginal; testTick=0; verified="untested"; state="VERIFYING"; detail="Tick-accurate off/on/off test running"; save(); tell(detail);
    }
    BlockPos input() { return blueprint.blocks().stream().filter(b->b.component().equals("input")).findFirst().orElseThrow().pos(); }
    BlockPos output() { return blueprint.blocks().stream().filter(b->b.state().hasProperty(BlockStateProperties.LIT)).findFirst().orElseThrow().pos(); }
    void tickTest() {
        try {
            requireCreative(); validateTarget(input()); validateTarget(output());
            if (!player().level().getBlockState(input()).equals(testLast)) throw new IllegalStateException("Input changed during test; player edit preserved");
            if (testTick==0) setTestInput(false);
            if (testTick==8) { assertLamp(false); setTestInput(true); }
            if (testTick==16) { assertLamp(true); setTestInput(false); }
            if (testTick==24) { assertLamp(false); restoreTest(); verified="passed:off@8,on@16,off@24"; state="COMPLETED"; detail="Actual Minecraft functional test passed at ticks 8, 16, 24"; save(); checkpoint(); tell(detail); return; }
            testTick++;
        } catch(RuntimeException e) { verified="failed: "+e.getMessage(); pause("Functional test failed: "+e.getMessage()); }
    }
    void assertLamp(boolean lit) { if (player().level().getBlockState(output()).getValue(BlockStateProperties.LIT)!=lit) throw new IllegalStateException("Lamp expected lit="+lit+" at test tick "+testTick); }
    void setTestInput(boolean value) {
        testLast=testOriginal.setValue(BlockStateProperties.POWERED,value);save();
        player().level().setBlockAndUpdate(input(),testLast);
    }
    void restoreTest() {
        if (testOriginal!=null && server.isSameThread() && available() && player().isCreative() && contains(input()) && player().level().hasChunkAt(input()) && player().level().getBlockEntity(input())==null) {
            if(player().level().getBlockState(input()).equals(testLast)) player().level().setBlockAndUpdate(input(),testOriginal);
            // A different state belongs to a later edit. Preserve it and relinquish restoration.
            testOriginal=null;testLast=null;
        }
        testTick=-1;
    }
    void checkpoint() {
        if (blueprint==null) return; JsonObject out=new JsonObject(); out.addProperty("worldId",worldId); out.addProperty("dimension",dimension);
        out.addProperty("projectId",blueprint.projectId());out.addProperty("blueprintRevision",blueprint.revision()); out.addProperty("taskId",blueprint.id()); out.addProperty("status",state); out.addProperty("detail",detail); out.addProperty("verified",verified);
        Companion.post("/v1/checkpoint",out).exceptionally(error->null);
    }
    void save() {
        JsonObject json=new JsonObject(); json.addProperty("schemaVersion",1); json.addProperty("worldId",worldId); json.addProperty("dimension",dimension);
        json.addProperty("owner",owner.toString()); json.addProperty("status",state); json.addProperty("detail",detail); json.addProperty("stage",stage); json.addProperty("verified",verified);
        json.addProperty("pendingPlacement",pendingPlacement);json.addProperty("undoRequested",undoRequested);
        if (corner1!=null) json.add("corner1",Blueprint.jsonPos(corner1)); if (corner2!=null) json.add("corner2",Blueprint.jsonPos(corner2));
        if (blueprint!=null) json.add("blueprint",blueprint.source()); JsonArray changes=new JsonArray();
        if (testOriginal!=null) { json.addProperty("testOriginal",Blueprint.describe(testOriginal)); json.addProperty("testLast",Blueprint.describe(testLast)); }
        for(Change c:journal) { JsonObject x=new JsonObject(); x.add("pos",Blueprint.jsonPos(c.pos())); x.addProperty("before",Blueprint.describe(c.before())); x.addProperty("after",Blueprint.describe(c.after())); changes.add(x); }
        json.add("journal",changes); JsonArray initial=new JsonArray(); baseline.forEach((pos,state)->{JsonObject x=new JsonObject();x.add("pos",Blueprint.jsonPos(pos));x.addProperty("state",Blueprint.describe(state));initial.add(x);});json.add("baseline",initial);
        LocalFiles.write(file,json);
    }
    void load() {
        if(!Files.exists(file)) return;
        try {
            JsonObject json=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            if (!worldId.equals(json.get("worldId").getAsString()) || !dimension.equals(json.get("dimension").getAsString()) || !owner.toString().equals(json.get("owner").getAsString())) throw new IllegalStateException("Checkpoint identity mismatch");
            if(json.has("corner1")) corner1=Blueprint.pos(json.getAsJsonObject("corner1")); if(json.has("corner2")) corner2=Blueprint.pos(json.getAsJsonObject("corner2"));
            if(json.has("blueprint")) blueprint=Blueprint.parse(json.getAsJsonObject("blueprint"),worldId,dimension);
            if(json.has("testOriginal")) { testOriginal=Blueprint.state(json.get("testOriginal").getAsString());testLast=Blueprint.state(json.get("testLast").getAsString()); }
            stage=json.get("stage").getAsInt();
            if(stage<0 || (blueprint!=null && stage>=blueprint.stages().size())) throw new IllegalStateException("Checkpoint has an invalid tutorial stage");
            verified=json.get("verified").getAsString();
            if(verified.startsWith("passed:")) verified="stale: saved functional evidence requires a new world observation and test";
            pendingPlacement=json.has("pendingPlacement")&&json.get("pendingPlacement").getAsBoolean();undoRequested=json.has("undoRequested")&&json.get("undoRequested").getAsBoolean();
            for(JsonElement e:json.getAsJsonArray("journal")){JsonObject x=e.getAsJsonObject();journal.add(new Change(Blueprint.pos(x.getAsJsonObject("pos")),Blueprint.state(x.get("before").getAsString()),Blueprint.state(x.get("after").getAsString())));}
            for(JsonElement e:json.getAsJsonArray("baseline")){JsonObject x=e.getAsJsonObject();baseline.put(Blueprint.pos(x.getAsJsonObject("pos")),Blueprint.state(x.get("state").getAsString()));}
            state="PAUSED";detail="Checkpoint restored. Inspect the world; explicit resume reconciles before editing.";
        }catch(Exception e){throw new IllegalStateException("Cannot safely load checkpoint; keep it for recovery",e);}
    }
}
