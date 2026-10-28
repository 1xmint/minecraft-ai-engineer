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
    final Set<BlockPos> repairTargets = new LinkedHashSet<>();
    final Map<UUID, Diagnosis.Proposal> diagnoses = new LinkedHashMap<>();
    String activeOperation = "legacy";
    String activeComponent = "root";
    final ComponentAssignments assignments = new ComponentAssignments();
    String delegationComponent, delegationBasis;
    long delegationGeneration = -1;
    String operationKind = "BUILD";
    UUID originDiagnosis;
    String undoOperation;
    boolean pendingPlacement;
    boolean undoRequested;
    long nextPlacement;
    int testTick = -1;
    final FunctionalTrial trial = new FunctionalTrial(this);
    String verified = "untested";
    String memoryStatus = "not synchronized";
    java.util.concurrent.CompletableFuture<JsonObject> lastCheckpoint;
    java.util.concurrent.CompletableFuture<JsonObject> lastObservation;
    long reportSequence;
    record Change(BlockPos pos, BlockState before, BlockState after, String operation, String component) {
        Change(BlockPos pos, BlockState before, BlockState after, String operation) { this(pos,before,after,operation,"root"); }
        Change(BlockPos pos, BlockState before, BlockState after) { this(pos,before,after,"legacy"); }
    }

    EngineeringSession(ProjectManager manager, UUID projectId, UUID owner, String dimension) {
        this(manager,projectId,owner,dimension,null);
    }
    EngineeringSession(ProjectManager manager, UUID projectId, UUID owner, String dimension, JsonObject adopted) {
        this.manager=manager; this.server=manager.server; this.projectId=projectId; this.owner=owner;
        this.controller=owner; this.access=new ProjectAccess(owner); this.worldId=manager.worldId; this.dimension=dimension;
        file=manager.directory.resolve(projectId+".json");
        if(adopted==null)load();else loadDocument(adopted);
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
        if(state.equals("PLANNING"))throw new IllegalStateException("Planning is active; world actions are blocked until it finishes");
        requireCreative();
        var task=assignments.task(blueprint,activeComponent);
        if(ComponentTree.composite(blueprint) && !test && action==ProjectAccess.Permission.BUILD
                && (!task.executor.equals("assistant") || !actor().equals(task.actor)))
            throw new IllegalStateException("Explicitly delegate this component with component build "+activeComponent);
        if((state.equals("EXECUTING") || testTick>=0) && !controller.equals(actor())) throw new IllegalStateException("Stop the active assignment before taking over");
        var lease=manager.acquire(this,actor(),action,test);
        controller=actor(); delegation=lease;delegationConnection=player().connection;isolationLease=test;
        if(!test && action==ProjectAccess.Permission.BUILD && task.executor.equals("unassigned"))task.assign(actor(),"assistant");
        delegationComponent=activeComponent;delegationBasis=task.basis;delegationGeneration=task.generation;
    }
    Blueprint executionBlueprint() {
        if(blueprint==null)throw new IllegalStateException("No blueprint");
        return ComponentTree.component(blueprint,activeComponent);
    }
    List<Blueprint.PlannedBlock> assignmentBlocks() { return executionBlueprint().blocks(); }
    Set<BlockPos> dependencyPositions(boolean isolation) {
        return dependencyPositions(activeComponent,isolation);
    }
    Set<BlockPos> dependencyPositions(String component,boolean isolation) {
        Set<BlockPos> result=new LinkedHashSet<>();
        Set<String> visited=new HashSet<>();
        collectDependencies(component,isolation?2:1,visited,result);
        if(result.size()>65536)throw new IllegalStateException("Component dependency footprint exceeds its limit");
        return result;
    }
    private void collectDependencies(String id,int margin,Set<String> visited,Set<BlockPos> result) {
        if(!visited.add(id))return;
        for(var block:ComponentTree.component(blueprint,id).blocks()) {
            for(BlockPos pos:BlockPos.betweenClosed(block.pos().offset(-margin,-margin,-margin),block.pos().offset(margin,margin,margin)))result.add(pos.immutable());
            if(result.size()>65536)throw new IllegalStateException("Component dependency footprint exceeds its limit");
        }
        for(var node:ComponentTree.nodes(blueprint))if(node.id().equals(id))
            for(String prerequisite:node.dependsOn())collectDependencies(prerequisite,margin,visited,result);
    }
    void validateAssignmentFence() {
        var task=assignments.task(blueprint,activeComponent);
        if(!activeComponent.equals(delegationComponent) || !task.basis.equals(delegationBasis) || task.generation!=delegationGeneration)
            throw new IllegalStateException("Component assignment changed; fresh delegation is required");
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
        if (state.equals("PLANNING") || state.equals("EXECUTING") || testTick >= 0 || hasPendingTest() || !journal.isEmpty()) throw new IllegalStateException("Stop planning/building and undo assistant work before changing the delegated region");
        if (corner == 1) {corner1 = pos.immutable();corner2=null;}
        else {if(corner1==null)throw new IllegalStateException("Set the first corner before completing the workspace");manager.workspace(this,actor(),corner1,pos);corner2 = pos.immutable();}
        baseline.clear();dependencies.clear(); state = blueprint == null ? "PENDING" : "READY"; save(); tell("Corner " + corner + " set to " + pos.toShortString());
    }
    void accept(Blueprint value) {
        access.require(actor(),ProjectAccess.Permission.EDIT);
        ComponentTree.validate(value);
        if (state.equals("PLANNING") || state.equals("EXECUTING") || testTick >= 0 || hasPendingTest() || !journal.isEmpty()) throw new IllegalStateException("Stop planning/building and undo the previous assignment before replacing its blueprint");
        JsonObject intended=value.source().deepCopy();
        intended.addProperty("projectId",projectId.toString()); intended.addProperty("revision",Math.addExact(projectRevision,1));
        Blueprint candidate=Blueprint.parse(intended,worldId,dimension);
        trial.resetEvidence();
        clearDiagnoses();
        blueprint=candidate; projectRevision=blueprint.revision();
        activeComponent=ComponentTree.composite(blueprint)?ComponentTree.nodes(blueprint).stream().filter(node->node.kind().equals("mechanism")).findFirst().orElseThrow().id():"root";
        assignments.reconcile(blueprint);
        baseline.clear(); dependencies.clear();repairTargets.clear();originDiagnosis=null;activeOperation="legacy";operationKind="BUILD";cursors.clear(); stage = 0; teaching = true; verified = "untested"; state = "READY";
        manager.changed(this);
        save(); explain();
    }
    void explain() {
        if (blueprint == null) throw new IllegalStateException("Load a reference or request a plan first");
        Cursor c=cursor(actor()); Blueprint.Stage s = blueprint.stages().get(c.stage);
        tell("Step " + (c.stage + 1) + "/" + blueprint.stages().size() + ": " + s.title() + ". " + s.explanation());
    }
    void teachComponent(String id) {
        access.require(actor(),ProjectAccess.Permission.VIEW);
        if(blueprint==null)throw new IllegalStateException("Load a design before choosing a component lesson");
        ComponentTree.component(blueprint,id);
        Set<String> stages=new HashSet<>();
        for(var block:blueprint.blocks())if(!ComponentTree.composite(blueprint) || block.component().equals(id))stages.add(block.stage());
        int first=-1;for(int i=0;i<blueprint.stages().size();i++)if(stages.contains(blueprint.stages().get(i).id())){first=i;break;}
        if(first<0)throw new IllegalStateException("Choose a component with a construction lesson");
        Cursor c=cursor(actor());c.stage=first;c.teaching=true;save();explain();
    }
    void moveStage(int delta) { access.require(actor(),ProjectAccess.Permission.VIEW); if (blueprint == null) throw new IllegalStateException("No blueprint"); Cursor c=cursor(actor());c.stage = Math.clamp(c.stage + delta,0,blueprint.stages().size()-1); c.teaching=true; save(); explain(); }
    void componentAdd(String id,String parent,String template,BlockPos position) {
        access.require(actor(),ProjectAccess.Permission.EDIT);
        if(!owner.equals(actor()))throw new IllegalStateException("Only the project owner may change its component tree");
        if(state.equals("PLANNING") || state.equals("EXECUTING") || testTick>=0 || hasPendingTest() || !journal.isEmpty() || pendingPlacement || undoRequested)
            throw new IllegalStateException("Reconcile current work before changing the component tree");
        BlockPos pivot=blueprint==null?anchor():blueprint.anchor();int turn=blueprint==null?0:blueprint.rotation();
        JsonArray nodes=new JsonArray();
        if(blueprint!=null && ComponentTree.composite(blueprint))nodes=blueprint.source().getAsJsonObject("components").getAsJsonArray("nodes").deepCopy();
        else {
            JsonObject root=new JsonObject();root.addProperty("id","root");root.addProperty("kind","group");nodes.add(root);
            if(blueprint!=null) {
                MechanismCatalog.contract(blueprint);
                JsonObject original=componentNode("existing","root",catalogTemplate(blueprint),BlockPos.ZERO,0,
                        blueprint.source().has("parameters")?blueprint.source().getAsJsonObject("parameters").get("delay").getAsInt():1);
                nodes.add(original);
            }
        }
        if(template==null) {
            if(blueprint==null)throw new IllegalStateException("Add a mechanism before organizing it into groups");
            JsonObject group=new JsonObject();group.addProperty("id",id);group.addProperty("parent",parent);group.addProperty("kind","group");nodes.add(group);
        } else {
            BlockPos relative=position.subtract(pivot).rotate(Blueprint.rotation((360-turn)%360));
            nodes.add(componentNode(id,parent,template,relative,(360-turn)%360,1));
        }
        Blueprint candidate=ComponentTree.compose(worldId,dimension,pivot,turn,nodes);
        for(var block:candidate.blocks())if(!contains(block.pos()))throw new IllegalStateException("Component geometry escaped the selected workspace");
        accept(candidate);checkpoint();tell("Added component "+id);
    }
    void componentDepend(String id,String prerequisite) {
        access.require(actor(),ProjectAccess.Permission.EDIT);
        if(!owner.equals(actor()) || blueprint==null || !ComponentTree.composite(blueprint))throw new IllegalStateException("Only the owner can edit a composed project's prerequisites");
        if(state.equals("PLANNING") || state.equals("EXECUTING") || testTick>=0 || hasPendingTest() || !journal.isEmpty() || pendingPlacement || undoRequested)
            throw new IllegalStateException("Reconcile current work before changing component dependencies");
        JsonArray nodes=blueprint.source().getAsJsonObject("components").getAsJsonArray("nodes").deepCopy();boolean found=false;
        for(var entry:nodes)if(entry.getAsJsonObject().get("id").getAsString().equals(id)) {
            var node=entry.getAsJsonObject();if(!node.get("kind").getAsString().equals("mechanism"))throw new IllegalArgumentException("Assign prerequisites to a mechanism leaf");
            node.getAsJsonArray("dependsOn").add(prerequisite);found=true;
        }
        if(!found)throw new IllegalArgumentException("Unknown component");
        accept(ComponentTree.compose(worldId,dimension,blueprint.anchor(),blueprint.rotation(),nodes));checkpoint();tell("Component "+id+" requires "+prerequisite);
    }
    void validateDeclaredPrerequisites() {
        if(blueprint==null)return;
        for(var node:ComponentTree.nodes(blueprint))if(node.id().equals(activeComponent))for(String prerequisite:node.dependsOn())
            for(var block:ComponentTree.component(blueprint,prerequisite).blocks())
                if(!level().hasChunkAt(block.pos()) || !Blueprint.matches(block.state(),level().getBlockState(block.pos())))
                    throw new IllegalStateException("Required component "+prerequisite+" is incomplete or unloaded");
    }
    private static JsonObject componentNode(String id,String parent,String template,BlockPos anchor,int rotation,int delay) {
        JsonObject node=new JsonObject();node.addProperty("id",id);node.addProperty("parent",parent);node.addProperty("kind","mechanism");
        node.addProperty("template",template);node.add("anchor",Blueprint.jsonPos(anchor));node.addProperty("rotation",rotation);
        JsonObject parameters=new JsonObject();parameters.addProperty("delay",delay);node.add("parameters",parameters);node.add("dependsOn",new JsonArray());return node;
    }
    void componentList() {
        access.require(actor(),ProjectAccess.Permission.VIEW);
        if(blueprint==null)throw new IllegalStateException("Add or select a mechanism first");
        for(var node:ComponentTree.nodes(blueprint)) {
            if(node.kind().equals("group")){tell("Component "+node.id()+" | group | parent="+node.parent());continue;}
            var task=assignments.task(blueprint,node.id());
            tell("Component "+node.id()+" | parent="+node.parent()+" | "+node.template()+" | "+task.executor+" | actor="+task.actor+" | "+task.status+" | prerequisites="+node.dependsOn());
        }
    }
    void componentInfo(String id) {
        access.require(actor(),ProjectAccess.Permission.VIEW);
        if(blueprint==null)throw new IllegalStateException("Add or select a mechanism first");
        Blueprint selected=ComponentTree.component(blueprint,id);
        var task=assignments.task(blueprint,id);var contract=MechanismCatalog.contract(selected);
        String assignee="unassigned";
        if(task.actor!=null) {
            var connected=server.getPlayerList().getPlayer(task.actor);
            assignee=connected==null?"offline player "+task.actor:connected.getName().getString();
        }
        tell("Component "+id+": "+contract.template()+" | "+task.executor+" | assigned by "+assignee+" | "+task.status);
        var node=ComponentTree.nodes(blueprint).stream().filter(value->value.id().equals(id)).findFirst().orElseThrow();
        tell("Prerequisites: "+(node.dependsOn().isEmpty()?"none":String.join(", ",node.dependsOn())));
        var materials=new ArrayList<String>();
        for(var entry:selected.source().getAsJsonObject("resources").entrySet())
            materials.add(entry.getValue().getAsInt()+" "+entry.getKey().replace("minecraft:",""));
        tell("Materials for this component: "+String.join(", ",materials));
        for(var port:contract.ports())tell("Port "+port.name()+" ("+(port.input()?"input":"output")+") at "+port.pos().toShortString());
        tell("Functional result: "+task.verified);
        if(task.lastPassedScope!=null)tell("Last passing test used design revision "+task.lastPassedScope.get("blueprintRevision").getAsInt()+"; current project revision "+revision()+". Port labels alone do not prove wiring between components.");
    }
    void componentClaim(String id) {
        access.require(actor(),ProjectAccess.Permission.BUILD);
        if(blueprint==null)throw new IllegalStateException("No component project");
        var task=assignments.task(blueprint,id);
        if(state.equals("PLANNING") || pendingPlacement || undoRequested || hasPendingTest())throw new IllegalStateException("Reconcile active planning, placement, undo or test before handoff");
        if(id.equals(activeComponent))pause("Component handed to the player; journal retained");
        task.assign(actor(),"player");clearDiagnoses();save();checkpoint();tell("Component "+id+" assigned to you");
    }
    void componentBuild(String id) {
        requireCreative();access.require(actor(),ProjectAccess.Permission.BUILD);
        if(blueprint==null)throw new IllegalStateException("No component project");
        ComponentTree.validate(blueprint);var task=assignments.task(blueprint,id);
        if(state.equals("PLANNING") || state.equals("EXECUTING") || testTick>=0 || pendingPlacement || undoRequested || hasPendingTest())
            throw new IllegalStateException("Stop and reconcile active world work before delegating a component");
        Blueprint selected=ComponentTree.component(blueprint,id);
        var node=ComponentTree.nodes(blueprint).stream().filter(n->n.id().equals(id)).findFirst().orElseThrow();
        for(String prerequisite:node.dependsOn())for(var block:ComponentTree.component(blueprint,prerequisite).blocks()) {
            if(!level().hasChunkAt(block.pos()) || !Blueprint.matches(block.state(),level().getBlockState(block.pos())))
                throw new IllegalStateException("Required component "+prerequisite+" is incomplete or unloaded");
        }
        Map<BlockPos,BlockState> recorded=new LinkedHashMap<>();
        for(Change change:journal)if(change.component().equals(id))recorded.put(change.pos(),change.after());
        for(var entry:recorded.entrySet())if(!level().hasChunkAt(entry.getKey()) || !Blueprint.matches(entry.getValue(),level().getBlockState(entry.getKey())))
            throw new IllegalStateException("A recorded assistant component block changed; resolve it before delegating again");
        for(var block:selected.blocks()) {
            validateTarget(block.pos());BlockState actual=level().getBlockState(block.pos());
            if(!actual.isAir() && !Blueprint.matches(block.state(),actual))throw new IllegalStateException("Player component edit conflicts at "+block.pos().toShortString());
        }
        trial.resetEvidence();manager.release(this);clearDiagnoses();
        if(assignments.tasks.containsKey(activeComponent)){var old=assignments.tasks.get(activeComponent);old.status=state;old.verified=verified;}
        activeComponent=id;baseline.clear();dependencies.clear();repairTargets.clear();originDiagnosis=null;
        activeOperation="legacy";operationKind="BUILD";verified="untested";state="READY";task.assign(actor(),"assistant");
        try{build();}catch(RuntimeException failure){state="PAUSED";detail=failure.getMessage();save();checkpoint();throw failure;}
    }
    void build() {
        requireCreative();
        access.require(actor(),ProjectAccess.Permission.BUILD);
        if(state.equals("PLANNING"))throw new IllegalStateException("Planning is active; wait before building");
        if(hasPendingTest()) throw new IllegalStateException("Reconcile the interrupted functional test with resume before building");
        if (blueprint == null) throw new IllegalStateException("Preview a blueprint first");
        ComponentTree.validate(blueprint);
        if (state.equals("EXECUTING") || testTick >= 0 || undoRequested) throw new IllegalStateException("An operation is already running or needs reconciliation");
        if (!baseline.isEmpty()) { resume(); return; }
        if(pendingPlacement)throw new IllegalStateException("Reconcile the interrupted placement before starting a new build");
        Map<BlockPos,BlockState> proposedBaseline = new LinkedHashMap<>();
        for (Blueprint.PlannedBlock b : assignmentBlocks()) {
            validateTarget(b.pos());
            BlockState actual = level().getBlockState(b.pos());
            if (!actual.isAir() && !Blueprint.matches(b.state(), actual)) throw new IllegalStateException("Occupied or incorrect block at " + b.pos().toShortString() + "; move the preview or correct it yourself");
            proposedBaseline.put(b.pos(), actual);
        }
        var proposedDependencies=captureDependencies();
        long needed=proposedBaseline.entrySet().stream().filter(e->e.getValue().isAir()).count();
        ensureJournalCapacity(needed);
        claim(ProjectAccess.Permission.BUILD,false);
        activeOperation=UUID.randomUUID().toString();operationKind="BUILD";originDiagnosis=null;repairTargets.clear();
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
        for (Change c : journal) if(c.operation().equals(activeOperation)) expected.put(c.pos(), c.after());
        return expected;
    }
    private boolean matchesExpected(BlockPos pos,BlockState expected,BlockState actual) {
        if(operationKind.equals("REPAIR") && expected.is(net.minecraft.world.level.block.Blocks.LEVER))return expected.equals(actual);
        return Blueprint.matches(expected,actual);
    }
    void reconcile() {
        requireCreative();
        if (undoRequested) throw new IllegalStateException("Undo was interrupted; use /engineer undo to reconcile it");
        for (Map.Entry<BlockPos, BlockState> entry : expectedNow().entrySet()) {
            validateTarget(entry.getKey());
            BlockState actual = level().getBlockState(entry.getKey());
            if (!matchesExpected(entry.getKey(),entry.getValue(), actual)) {
                // A crash between journal flush and placement may leave before-state. It is safe to retry only that case.
                Change pending = journal.isEmpty() ? null : journal.getLast();
                if (pendingPlacement && pending != null && pending.operation().equals(activeOperation) && pending.pos().equals(entry.getKey()) && actual.equals(pending.before())) {
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
        var planned=new HashSet<BlockPos>(); assignmentBlocks().forEach(b->planned.add(b.pos()));
        var result=new LinkedHashMap<BlockPos,BlockState>();
        for(BlockPos p:dependencyPositions(false)) {
            if(planned.contains(p))continue;
            if(!level().hasChunkAt(p))throw new IllegalStateException("Assignment dependencies include an unloaded chunk");
            result.put(p.immutable(),level().getBlockState(p));
        }
        if(result.size()>65536)throw new IllegalStateException("Assignment dependency footprint exceeds the limit");
        return result;
    }
    private void ensureJournalCapacity(long additions) {
        if(additions<0 || journal.size()+additions>4096)throw new IllegalStateException("The bounded assistant change journal is full; undo a section before starting another");
    }
    @Override public void resume() {
        access.require(actor(),ProjectAccess.Permission.BUILD);
        if(state.equals("PLANNING"))throw new IllegalStateException("Planning is active; wait before resuming world work");
        if (hasPendingTest()) { claim(ProjectAccess.Permission.BUILD,true);restoreTest();state="PAUSED";detail=!hasPendingTest()?"Interrupted test reconciled; inspect before requesting another test":"Test input still needs restoration; preserve/resolve conflicting edits before resume";manager.release(this);save();tell(detail);return; }
        if (blueprint == null || baseline.isEmpty()) throw new IllegalStateException("No interrupted assignment to resume; use build");
        if(undoRequested)throw new IllegalStateException("Undo was interrupted; use undo to finish its selected operation");
        ComponentTree.validate(blueprint);
        reconcile(); var refreshedDependencies=dependencies.isEmpty()?captureDependencies():dependencies;claim(ProjectAccess.Permission.BUILD,operationKind.equals("REPAIR"));dependencies.putAll(refreshedDependencies);state="EXECUTING"; detail="Reconciled; resuming the same bounded assignment"; save(); checkpoint(); tell(detail);
    }
    @Override public void pause(String reason) {
        state="PAUSED"; detail=reason; teaching=false;
        cursor(controller).teaching=false;
        // An active functional test restores the exact input before relinquishing its authority.
        if (hasPendingTest()) restoreTest();
        manager.release(this);
        save(); checkpoint(); tell(reason);
    }
    @Override public String status() { return state + ": " + detail + "; assistant recorded changes=" + journal.size() + "; functional verification=" + verified+"; project memory="+memoryStatus; }
    @Override public void tick() {
        if (!available()) { if (state.equals("EXECUTING") || testTick>=0) pause("Controller disconnected or changed dimension; explicit resume required"); tickTutorials();return; }
        if (testTick>=0) { tickTest(); return; }
        if(blueprint!=null && ComponentTree.composite(blueprint) && server.getTickCount()%10==0) {
            boolean changed=false;
            for(var task:assignments.tasks.values())if(task.verified.startsWith("passed:") && !componentGeometryMatches(task.id)) {
                task.verified="stale: component or prerequisite geometry changed";task.status="PAUSED";changed=true;
                if(task.id.equals(activeComponent)){verified=task.verified;state="PAUSED";detail=task.verified;manager.release(this);}
            }
            if(changed){save();checkpoint();}
        }
        if (blueprint != null && verified.startsWith("passed:") && server.getTickCount()%10==0) {
            boolean changed=assignmentBlocks().stream().anyMatch(b->!level().hasChunkAt(b.pos()) || !Blueprint.matches(b.state(),level().getBlockState(b.pos())));
            if(changed){verified="stale: circuit changed or chunks unloaded since test";detail="Functional evidence is stale; inspect and test the current circuit again";state="PAUSED";save();checkpoint();tell(detail);}
        }
        if (state.equals("EXECUTING")) {
            try {
                validateMutation();
                reconcile();
                if (server.getTickCount() < nextPlacement) return;
                nextPlacement=server.getTickCount()+4;
                for (Blueprint.PlannedBlock b : assignmentBlocks()) {
                    if(operationKind.equals("REPAIR") && !repairTargets.contains(b.pos()))continue;
                    BlockState actual = level().getBlockState(b.pos());
                    if (Blueprint.matches(b.state(),actual)) continue;
                    BlockState after;
                    if(operationKind.equals("REPAIR")) {
                        if(!actual.isAir() && !actual.is(b.state().getBlock()))throw new IllegalStateException("Repair target changed to an unsupported block at "+b.pos().toShortString());
                        after=Diagnosis.corrected(b.state(),actual);
                        if(!after.canSurvive(level(),b.pos()))throw new IllegalStateException("Corrected component cannot survive at "+b.pos().toShortString());
                    } else {
                        if (!actual.isAir() || !b.state().canSurvive(level(), b.pos())) throw new IllegalStateException("Placement blocked at " + b.pos().toShortString());
                        after=b.state();
                    }
                    journal.add(new Change(b.pos(),actual,after,activeOperation,activeComponent)); pendingPlacement=true;save();
                    validateMutation();
                    if(!level().getBlockState(b.pos()).equals(actual))throw new IllegalStateException("World changed immediately before the authorized write at "+b.pos().toShortString());
                    if (!level().setBlockAndUpdate(b.pos(),after)) throw new IllegalStateException("Minecraft rejected placement");
                    pendingPlacement=false;save(); return;
                }
                state="COMPLETED"; detail=ComponentTree.composite(blueprint)?"Component "+activeComponent+" geometry matches. Functional test still required.":operationKind.equals("REPAIR")?"Supported corrections applied. Functional test still required.":"Geometry matches the blueprint. Functional test still required.";manager.release(this); save(); checkpoint(); tell(detail);
            } catch (RuntimeException e) { pause(e.getMessage()); }
        }
        tickTutorials();
    }
    private boolean componentGeometryMatches(String id) {
        // A prior pass also depends on loaded, clear prerequisite isolation margins.
        try { validateComponentTestArea(id);return true; }
        catch(RuntimeException changed){return false;}
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
    void diagnose() {
        componentDiagnose(activeComponent);
    }
    void componentDiagnose(String id) {
        access.require(actor(),ProjectAccess.Permission.VIEW);
        Diagnosis.Proposal proposal=Diagnosis.inspect(this,id);
        long now=server.getTickCount();diagnoses.entrySet().removeIf(entry->entry.getValue().expiresAtTick()<=now);
        if(!diagnoses.containsKey(actor()) && diagnoses.size()>=16)diagnoses.remove(diagnoses.keySet().iterator().next());
        diagnoses.put(actor(),proposal);
        int shown=0;
        for(var target:proposal.targets()) {
            if(shown++>=32)break;
            tell(bound(target.explanation()));
        }
        for(String message:proposal.messages()) {
            if(shown++>=48)break;
            tell(bound(message));
        }
        if(proposal.targets().size()>32 || proposal.messages().size()+Math.min(proposal.targets().size(),32)>48)
            tell("Additional diagnosis details were omitted; correct reported blockers and diagnose again.");
        if(proposal.repairable() && !proposal.targets().isEmpty())tell("Repair proposal ready");
        else tell("Repair blocked");
    }
    private static String bound(String value) { return value.length()<=350?value:value.substring(0,347)+"..."; }
    void clearDiagnoses() { diagnoses.clear(); }
    void forgetDiagnosis(UUID actor) { diagnoses.remove(actor); }
    void repair() {
        componentRepair(activeComponent);
    }
    void componentRepair(String id) {
        requireCreative();access.require(actor(),ProjectAccess.Permission.BUILD);
        if(state.equals("PLANNING") || state.equals("EXECUTING") || testTick>=0 || hasPendingTest()
                || pendingPlacement || undoRequested)throw new IllegalStateException("World work is active or needs reconciliation");
        if(blueprint==null)throw new IllegalStateException("Choose a canonical mechanism before repair");
        ComponentTree.validate(blueprint);
        Blueprint selected=ComponentTree.component(blueprint,id);
        MechanismCatalog.contract(selected);
        Diagnosis.Proposal proposal=diagnoses.get(actor());
        if(proposal==null || !proposal.actor().equals(actor()) || proposal.expiresAtTick()<=server.getTickCount()
                || !proposal.epoch().equals(manager.epoch) || !proposal.projectId().equals(projectId)
                || proposal.revision()!=revision() || proposal.authorization()!=access.revision()
                || !proposal.component().equals(id) || player()==null || player().connection!=proposal.connection()) {
            throw new IllegalStateException("No fresh diagnosis for component "+id);
        }
        if(!proposal.repairable() || proposal.targets().isEmpty())throw new IllegalStateException("Repair blocked");
        proposal.validate(this);

        var planned=new LinkedHashMap<BlockPos,Blueprint.PlannedBlock>();
        for(var block:selected.blocks())planned.put(block.pos(),block);
        var proposedBaseline=new LinkedHashMap<BlockPos,BlockState>();
        for(var block:selected.blocks()) {
            BlockState exact=proposal.observed().get(block.pos());
            if(exact==null)throw new IllegalStateException("Diagnosis omitted a planned component; diagnose again");
            validateTarget(block.pos());proposedBaseline.put(block.pos(),exact);
        }
        var proposedTargets=new LinkedHashSet<BlockPos>();
        for(var target:proposal.targets()) {
            Blueprint.PlannedBlock block=planned.get(target.pos());
            if(block==null || !proposedTargets.add(target.pos()) || !Objects.equals(proposedBaseline.get(target.pos()),target.before()))
                throw new IllegalStateException("Diagnosis target no longer matches the canonical plan");
            if(!Diagnosis.corrected(block.state(),target.before()).equals(target.after()))
                throw new IllegalStateException("Diagnosis correction changed; diagnose again");
        }
        if(proposedTargets.isEmpty())throw new IllegalStateException("Repair blocked");
        ensureJournalCapacity(proposedTargets.size());
        var proposedDependencies=new LinkedHashMap<BlockPos,BlockState>();
        for(var entry:proposal.observed().entrySet())if(!planned.containsKey(entry.getKey()))proposedDependencies.put(entry.getKey(),entry.getValue());
        if(proposedDependencies.size()>65536)throw new IllegalStateException("Repair dependency footprint exceeds the limit");

        // Recheck the complete read snapshot immediately before acquiring write authority.
        proposal.validate(this);
        assignments.task(blueprint,activeComponent).status=state;
        assignments.task(blueprint,activeComponent).verified=verified;
        save();
        if(!activeComponent.equals(id))trial.resetEvidence();
        String previousComponent=activeComponent;String previousState=state,previousDetail=detail,previousVerified=verified;
        activeComponent=id;
        var targetTask=assignments.task(blueprint,id);
        UUID previousActor=targetTask.actor;String previousExecutor=targetTask.executor,previousTaskStatus=targetTask.status;
        long previousGeneration=targetTask.generation;
        try { targetTask.assign(actor(),"assistant");claim(ProjectAccess.Permission.BUILD,true); }
        catch(RuntimeException failure) {
            targetTask.actor=previousActor;targetTask.executor=previousExecutor;targetTask.status=previousTaskStatus;targetTask.generation=previousGeneration;
            activeComponent=previousComponent;state=previousState;detail=previousDetail;verified=previousVerified;throw failure;
        }
        clearDiagnoses();
        baseline.clear();baseline.putAll(proposedBaseline);dependencies.clear();dependencies.putAll(proposedDependencies);
        repairTargets.clear();repairTargets.addAll(proposedTargets);
        activeOperation=UUID.randomUUID().toString();operationKind="REPAIR";originDiagnosis=proposal.snapshotId();
        state="EXECUTING";detail="Applying the explicitly approved, bounded diagnosis corrections";verified="untested";
        save();checkpoint();tell(detail);
    }
    boolean hasBuiltStructure() {
        if(!journal.isEmpty()) {
            for(Change change:journal)if(!contains(change.pos()))throw new IllegalStateException("Recorded assistant change escaped the delegated workspace");
            return true;
        }
        if(blueprint==null)return false;
        for(var block:blueprint.blocks()) {
            // An unbuilt preview may extend beyond the selected workspace. Do
            // not inspect those coordinates while deciding whether a layout exists.
            if(!contains(block.pos()))continue;
            if(!level().hasChunkAt(block.pos()))throw new IllegalStateException("Cannot choose a replacement design while planned construction is unloaded");
            if(!level().getBlockState(block.pos()).isAir())return true;
        }
        return false;
    }
    BlockPos planningAnchor() {
        if(hasBuiltStructure() && blueprint!=null) {
            int x=Integer.MAX_VALUE,y=Integer.MAX_VALUE,z=Integer.MAX_VALUE;
            for(var block:blueprint.blocks()){x=Math.min(x,block.pos().getX());y=Math.min(y,block.pos().getY());z=Math.min(z,block.pos().getZ());}
            return new BlockPos(x,y,z);
        }
        return anchor();
    }
    boolean reviseProposal(Blueprint candidate) {
        access.require(actor(),ProjectAccess.Permission.EDIT);
        if(state.equals("PLANNING") || state.equals("EXECUTING") || testTick>=0 || hasPendingTest() || pendingPlacement || undoRequested)
            throw new IllegalStateException("Stop and reconcile current world work before revising the intended design");
        MechanismCatalog.contract(candidate);
        if(!hasBuiltStructure()) { accept(candidate);return true; }
        if(blueprint==null || !sameCatalogTemplate(blueprint,candidate)
                || !blueprint.anchor().equals(candidate.anchor()) || blueprint.rotation()!=candidate.rotation())
            throw new IllegalArgumentException("A built mechanism can only revise its bounded delay at the same template, anchor and rotation");
        if(sameIntendedStates(blueprint,candidate))return false;
        MechanismCatalog.contract(blueprint);
        // Compare historical references through the same validated catalog.
        // This normalizes only their absent delay=1 metadata and relay alias;
        // the original geometry must already pass the full contract above.
        JsonObject priorParameters=blueprint.source().getAsJsonObject("parameters");
        Blueprint comparison=priorParameters==null
                ?MechanismCatalog.create(catalogTemplate(blueprint),worldId,dimension,blueprint.anchor(),blueprint.rotation(),1)
                :blueprint;
        if(!onlyDelayChanged(comparison,candidate))
            throw new IllegalArgumentException("A built mechanism can only revise its bounded repeater delay; layout changes need a separate component operation");
        boolean changed=false;int firstChangedStage=Integer.MAX_VALUE;
        Map<BlockPos,Blueprint.PlannedBlock> next=new HashMap<>();candidate.blocks().forEach(block->next.put(block.pos(),block));
        for(var prior:blueprint.blocks()) {
            Blueprint.PlannedBlock replacement=next.get(prior.pos());
            if(replacement==null || replacement.state().getBlock()!=prior.state().getBlock())throw new IllegalArgumentException("A built mechanism cannot move or replace a component");
            if(!prior.state().equals(replacement.state())) {
                changed=true;int index=stageIndex(replacement.stage());firstChangedStage=Math.min(firstChangedStage,index);
            }
        }
        if(!changed)return false;
        JsonObject intended=candidate.source().deepCopy();intended.addProperty("projectId",projectId.toString());
        intended.addProperty("revision",Math.addExact(projectRevision,1));
        Blueprint revised=Blueprint.parse(intended,worldId,dimension);
        trial.resetEvidence();clearDiagnoses();blueprint=revised;projectRevision=revised.revision();
        baseline.clear();dependencies.clear();repairTargets.clear();originDiagnosis=null;activeOperation="legacy";operationKind="BUILD";
        for(var entry:new ArrayList<>(cursors.entrySet())) {
            entry.getValue().stage=Math.clamp(entry.getValue().stage,0,revised.stages().size()-1);
        }
        Cursor own=cursor(actor());if(firstChangedStage<Integer.MAX_VALUE)own.stage=firstChangedStage;
        stage=own.stage;verified="untested";state="READY";detail="Intended delay revised; world blocks are unchanged. Diagnose and explicitly repair when ready.";
        manager.changed(this);save();checkpoint();tell(detail);return true;
    }
    void revise(int delay) {
        access.require(actor(),ProjectAccess.Permission.EDIT);
        if(blueprint==null)throw new IllegalStateException("Choose a catalog mechanism before revising its delay");
        String template=catalogTemplate(blueprint);
        Blueprint candidate=MechanismCatalog.create(template,worldId,dimension,blueprint.anchor(),blueprint.rotation(),delay);
        if(!reviseProposal(candidate))tell("The requested delay already matches the intended design; world blocks are unchanged.");
    }
    void componentRevise(String id,int delay) {
        access.require(actor(),ProjectAccess.Permission.EDIT);
        if(blueprint==null)throw new IllegalStateException("Choose a catalog mechanism before revising its delay");
        if(!ComponentTree.composite(blueprint)) {
            if(!id.equals("root"))throw new IllegalArgumentException("Unknown component");
            revise(delay);return;
        }
        ensureComponentContextIdle("revise the intended component");
        ComponentTree.validate(blueprint);
        var node=ComponentTree.nodes(blueprint).stream().filter(candidate->candidate.id().equals(id)).findFirst()
                .orElseThrow(()->new IllegalArgumentException("Choose a physical component, not a container or unknown name"));
        if(!node.kind().equals("mechanism"))throw new IllegalArgumentException("Choose a physical mechanism component");
        if(node.delay()==delay) { tell("Component "+id+" already matches delay "+delay+"; no revision was made.");return; }

        JsonArray nodes=blueprint.source().getAsJsonObject("components").getAsJsonArray("nodes").deepCopy();
        boolean found=false;
        for(JsonElement entry:nodes) {
            JsonObject candidate=entry.getAsJsonObject();
            if(!candidate.get("id").getAsString().equals(id))continue;
            candidate.getAsJsonObject("parameters").addProperty("delay",delay);found=true;break;
        }
        if(!found)throw new IllegalArgumentException("Unknown component");
        Blueprint composed=ComponentTree.compose(worldId,dimension,blueprint.anchor(),blueprint.rotation(),nodes);
        JsonObject intended=composed.source().deepCopy();intended.addProperty("projectId",projectId.toString());
        intended.addProperty("revision",Math.addExact(projectRevision,1));
        Blueprint revised=Blueprint.parse(intended,worldId,dimension);
        ComponentTree.validate(revised);
        // Validate the full selected contract and delay range before archiving evidence or changing intent.
        MechanismCatalog.contract(ComponentTree.component(revised,id));

        var currentTask=assignments.task(blueprint,activeComponent);
        currentTask.status=state;currentTask.verified=verified;
        save();
        trial.resetEvidence();
        Map<UUID,String> cursorStages=new HashMap<>();
        for(var entry:cursors.entrySet())cursorStages.put(entry.getKey(),blueprint.stages().get(entry.getValue().stage).id());
        String previousActive=activeComponent;
        blueprint=revised;projectRevision=revised.revision();
        assignments.reconcile(revised);
        baseline.clear();dependencies.clear();repairTargets.clear();originDiagnosis=null;undoOperation=null;
        pendingPlacement=false;undoRequested=false;activeOperation="legacy";operationKind="BUILD";clearDiagnoses();
        for(var entry:cursors.entrySet()) {
            String priorId=cursorStages.get(entry.getKey());
            int index=0;
            for(int i=0;i<revised.stages().size();i++)if(revised.stages().get(i).id().equals(priorId)){index=i;break;}
            entry.getValue().stage=index;
        }
        activeComponent=previousActive;
        ComponentAssignments.Task current=assignments.task(revised,activeComponent);
        verified=current.verified;state=current.status;
        stage=cursor(actor()).stage;
        detail=current.verified.startsWith("stale:")?current.verified:"Intended component delay revised; world blocks and operation journals are unchanged.";
        manager.changed(this);save();checkpoint();tell(detail);
    }

    private void ensureComponentContextIdle(String action) {
        if(state.equals("PLANNING") || state.equals("EXECUTING") || state.equals("VERIFYING") || testTick>=0 || hasPendingTest()
                || pendingPlacement || undoRequested || (state.equals("PAUSED") && (!baseline.isEmpty() || !dependencies.isEmpty())))
            throw new IllegalStateException("Stop and reconcile active or interrupted world work before you "+action);
    }

    void componentTest(String id) {
        access.require(actor(),ProjectAccess.Permission.BUILD);requireCreative();
        if(blueprint==null)throw new IllegalStateException("No component project");
        ensureComponentContextIdle("test a component");
        ComponentTree.validate(blueprint);
        Blueprint selected=ComponentTree.component(blueprint,id);
        MechanismCatalog.contract(selected);
        validateComponentTestArea(id);

        ComponentAssignments.Task oldTask=assignments.task(blueprint,activeComponent);
        oldTask.status=state;oldTask.verified=verified;
        save();
        trial.resetEvidence();
        String previousComponent=activeComponent,previousState=state,previousDetail=detail,previousVerified=verified;
        Map<BlockPos,BlockState> previousBaseline=new LinkedHashMap<>(baseline),previousDependencies=new LinkedHashMap<>(dependencies);
        Set<BlockPos> previousRepairTargets=new LinkedHashSet<>(repairTargets);
        String previousOperation=activeOperation,previousKind=operationKind,previousUndo=undoOperation;UUID previousOrigin=originDiagnosis;
        boolean previousPlacement=pendingPlacement,previousUndoRequested=undoRequested;
        activeComponent=id;
        baseline.clear();dependencies.clear();repairTargets.clear();originDiagnosis=null;undoOperation=null;
        pendingPlacement=false;undoRequested=false;activeOperation="legacy";operationKind="BUILD";
        try { trial.start(); }
        catch(RuntimeException failure) {
            if(delegation!=null)manager.release(this);
            if(hasPendingTest()) {
                testTick=-1;state="PAUSED";verified="untested";
                detail="Test initialization paused; captured inputs stay with component "+id+" for explicit recovery";
                throw failure;
            }
            activeComponent=previousComponent;state=previousState;detail=previousDetail;verified=previousVerified;
            baseline.clear();baseline.putAll(previousBaseline);dependencies.clear();dependencies.putAll(previousDependencies);
            repairTargets.clear();repairTargets.addAll(previousRepairTargets);activeOperation=previousOperation;operationKind=previousKind;
            originDiagnosis=previousOrigin;undoOperation=previousUndo;pendingPlacement=previousPlacement;undoRequested=previousUndoRequested;
            throw failure;
        }
    }

    private void validateComponentTestArea(String id) {
        Set<String> closure=new LinkedHashSet<>();collectComponentClosure(id,closure);
        var selected=ComponentTree.component(blueprint,id);
        Set<BlockPos> target=new HashSet<>();selected.blocks().forEach(block->target.add(block.pos()));
        var prerequisiteBlocks=new LinkedHashMap<BlockPos,Blueprint.PlannedBlock>();
        for(String prerequisite:closure)if(!prerequisite.equals(id))for(var block:ComponentTree.component(blueprint,prerequisite).blocks())
            prerequisiteBlocks.put(block.pos(),block);
        for(var block:selected.blocks()) {
            validateTarget(block.pos());
            if(!Blueprint.matches(block.state(),level().getBlockState(block.pos())))throw new IllegalStateException("Complete/correct component "+id+" before testing");
        }
        for(BlockPos pos:dependencyPositions(id,true)) {
            if(!level().hasChunkAt(pos) || !level().getWorldBorder().isWithinBounds(pos)
                    || pos.getY()<level().getMinY() || pos.getY()>level().getMaxY())
                throw new IllegalStateException("Test needs its complete loaded component and prerequisite read footprint");
            BlockState actual=level().getBlockState(pos);
            if(target.contains(pos))continue;
            Blueprint.PlannedBlock required=prerequisiteBlocks.get(pos);
            if(required!=null) {
                if(!Blueprint.matches(required.state(),actual))throw new IllegalStateException("Required component "+required.component()+" is incomplete or changed");
            } else if(!actual.isAir())throw new IllegalStateException("Test needs a two-block air margin around the isolated component and its prerequisites");
        }
    }

    private void collectComponentClosure(String id,Set<String> result) {
        if(!result.add(id))return;
        var node=ComponentTree.nodes(blueprint).stream().filter(candidate->candidate.id().equals(id)).findFirst()
                .orElseThrow(()->new IllegalArgumentException("Choose a physical component, not a container or unknown name"));
        for(String prerequisite:node.dependsOn())collectComponentClosure(prerequisite,result);
    }
    private int stageIndex(String id) {
        for(int i=0;i<blueprint.stages().size();i++)if(blueprint.stages().get(i).id().equals(id))return i;
        throw new IllegalArgumentException("Revised design contains an unknown tutorial section");
    }
    private static String catalogTemplate(Blueprint value) {
        JsonObject provenance=value.source().getAsJsonObject("provenance");
        return provenance.get("template").getAsString();
    }
    private static boolean sameCatalogTemplate(Blueprint a,Blueprint b) {
        return catalogTemplate(a).equals(catalogTemplate(b));
    }
    private static boolean sameIntendedStates(Blueprint before,Blueprint after) {
        if(before.blocks().size()!=after.blocks().size())return false;
        Map<BlockPos,Blueprint.PlannedBlock> next=new HashMap<>();after.blocks().forEach(block->next.put(block.pos(),block));
        for(var block:before.blocks()) {
            var replacement=next.get(block.pos());
            if(replacement==null || !block.state().equals(replacement.state()))return false;
        }
        return true;
    }
    private static boolean onlyDelayChanged(Blueprint before,Blueprint after) {
        JsonObject oldParameters=before.source().getAsJsonObject("parameters"),newParameters=after.source().getAsJsonObject("parameters");
        if(oldParameters==null || newParameters==null || !oldParameters.keySet().equals(Set.of("delay")) || !newParameters.keySet().equals(Set.of("delay")))return false;
        if(oldParameters.get("delay").getAsInt()==newParameters.get("delay").getAsInt())return false;
        if(before.blocks().size()!=after.blocks().size())return false;
        Map<BlockPos,Blueprint.PlannedBlock> next=new HashMap<>();after.blocks().forEach(block->next.put(block.pos(),block));
        for(var original:before.blocks()) {
            var revised=next.get(original.pos());
            if(revised==null || !original.component().equals(revised.component()) || !original.stage().equals(revised.stage())
                    || !original.state().is(revised.state().getBlock()))return false;
            if(original.state().equals(revised.state()))continue;
            if(!original.state().is(net.minecraft.world.level.block.Blocks.REPEATER)
                    || !original.state().getValue(BlockStateProperties.HORIZONTAL_FACING).equals(revised.state().getValue(BlockStateProperties.HORIZONTAL_FACING))
                    || original.state().getValue(BlockStateProperties.DELAY).equals(revised.state().getValue(BlockStateProperties.DELAY)))return false;
        }
        return true;
    }
    /** Descriptive execution frame only: rollback never restores a lease/connection. */
    private static final class UndoContext {
        final String component,state,detail,verified,operation,kind,undo;
        final UUID controller,origin;final boolean placement,requested;
        final Map<BlockPos,BlockState> baseline,dependencies;final Set<BlockPos> targets;
        final JsonObject trial=new JsonObject();
        UndoContext(EngineeringSession s) {
            component=s.activeComponent;state=s.state;detail=s.detail;verified=s.verified;controller=s.controller;
            operation=s.activeOperation;kind=s.operationKind;undo=s.undoOperation;origin=s.originDiagnosis;
            placement=s.pendingPlacement;requested=s.undoRequested;
            baseline=new LinkedHashMap<>(s.baseline);dependencies=new LinkedHashMap<>(s.dependencies);targets=new LinkedHashSet<>(s.repairTargets);
            s.trial.write(trial);
        }
        void restore(EngineeringSession s) {
            s.activeComponent=component;s.state=state;s.detail=detail;s.verified=verified;s.controller=controller;
            s.activeOperation=operation;s.operationKind=kind;s.undoOperation=undo;s.originDiagnosis=origin;
            s.pendingPlacement=placement;s.undoRequested=requested;
            s.baseline.clear();s.baseline.putAll(baseline);s.dependencies.clear();s.dependencies.putAll(dependencies);
            s.repairTargets.clear();s.repairTargets.addAll(targets);s.trial.load(trial);
            s.delegationComponent=null;s.delegationBasis=null;s.delegationGeneration=-1;s.isolationLease=false;
        }
    }
    void undo() {
        if(!beginUndo())return;
        try { undoReserved(); } finally { manager.release(this); }
    }
    /** Real production prepare/WAL phase; completion may resume after interruption. */
    boolean beginUndo() {
        requireCreative();access.require(actor(),ProjectAccess.Permission.UNDO);
        if(state.equals("PLANNING"))throw new IllegalStateException("Planning is active; wait before undoing world work");
        if(journal.isEmpty() && !undoRequested){tell("No assistant operation to undo.");return false;}
        if(state.equals("EXECUTING") || testTick>=0)pause("Undo requested; active execution paused");
        if(hasPendingTest())throw new IllegalStateException("Reconcile interrupted test inputs with resume before undo");
        UndoContext previous=new UndoContext(this);boolean committing=false;
        String selectedOperation=operationGroup(undoRequested?undoOperation:journal.getLast().operation());
        var selected=journal.stream().filter(change->change.operation().equals(selectedOperation)).findFirst();
        manager.release(this);
        try {
            if(selected.isPresent())activeComponent=selected.get().component();
            assignments.task(blueprint,activeComponent);
            claim(ProjectAccess.Permission.UNDO,true);
            preflightUndo(undoBatch(selectedOperation),journalPositions(),plannedPositions());
            // Keep the old buffer's original label while archiving its captured scope.
            var oldTask=assignments.task(blueprint,previous.component);oldTask.status=previous.state;oldTask.verified=previous.verified;
            trial.resetEvidence();
            baseline.clear();dependencies.clear();repairTargets.clear();originDiagnosis=null;pendingPlacement=false;clearDiagnoses();
            activeOperation=selectedOperation;operationKind="BUILD";verified="untested";
            state="PAUSED";detail="Undo requested for operation "+selectedOperation;undoRequested=true;undoOperation=selectedOperation;
            // A failed checkpoint can have persisted: retain this safe target frame after the attempt.
            committing=true;save();return true;
        }catch(RuntimeException failure) {
            manager.release(this);
            if(!committing)previous.restore(this);
            throw failure;
        }
    }
    private Set<BlockPos> journalPositions(){var result=new HashSet<BlockPos>();journal.forEach(change->result.add(change.pos()));return result;}
    private Set<BlockPos> plannedPositions(){var result=new HashSet<BlockPos>();if(blueprint!=null)blueprint.blocks().forEach(block->result.add(block.pos()));return result;}
    private void preflightUndo(List<Change> batch,Set<BlockPos> assistantPositions,Set<BlockPos> plannedPositions) {
        for(Change change:batch) {
            validateTarget(change.pos());BlockState actual=level().getBlockState(change.pos());
            if((undoRequested || (pendingPlacement && journal.getLast().equals(change))) && actual.equals(change.before()))continue;
            if(!undoAfterMatches(change,actual))throw new IllegalStateException("Undo blocked by later edit at "+change.pos().toShortString());
            validateUndoNeighbors(change,assistantPositions,plannedPositions);
            if(!change.before().isAir() && (change.before().hasBlockEntity() || !change.before().canSurvive(level(),change.pos())))
                throw new IllegalStateException("Undo cannot safely restore the recorded block at "+change.pos().toShortString());
        }
    }
    void undoReserved() {
        if (hasPendingTest()) restoreTest();
        if(hasPendingTest())throw new IllegalStateException("Reconcile the interrupted test input before undo; the circuit and recovery checkpoint were preserved");
        String targetOperation=undoRequested?undoOperation:(journal.isEmpty()?null:journal.getLast().operation());
        if(targetOperation==null) { manager.release(this);return; }
        List<Change> batch=undoBatch(targetOperation);
        if(batch.isEmpty()) { finishUndo();return; }
        // Repeat preflight after the durable target intent, then validate every actual mutation.
        Set<BlockPos> assistantPositions=journalPositions(),plannedPositions=plannedPositions();
        preflightUndo(batch,assistantPositions,plannedPositions);
        while (!journal.isEmpty() && journal.getLast().operation().equals(targetOperation)) {
            Change c = journal.getLast();
            if(!level().hasChunkAt(c.pos()))throw new IllegalStateException("Undo target chunk unloaded; journal retained");
            BlockState actual=level().getBlockState(c.pos());
            if(actual.equals(c.before())) { journal.removeLast();pendingPlacement=false;save();continue; }
            if(!undoAfterMatches(c,actual))throw new IllegalStateException("Undo blocked by later edit at "+c.pos().toShortString());
            validateUndoNeighbors(c,assistantPositions,plannedPositions);
            if(!c.before().isAir() && (c.before().hasBlockEntity() || !c.before().canSurvive(level(),c.pos())))throw new IllegalStateException("Undo cannot safely restore the recorded block at "+c.pos().toShortString());
            validateMutation(ProjectAccess.Permission.UNDO);
            if(!undoAfterMatches(c,level().getBlockState(c.pos())))throw new IllegalStateException("World changed immediately before undo at "+c.pos().toShortString());
            BlockState restore=restoreDynamic(c.before(),actual);
            if(!level().setBlockAndUpdate(c.pos(),restore))throw new IllegalStateException("Minecraft rejected undo; journal retained for recovery");
            BlockState restored=level().getBlockState(c.pos());
            if(c.before().is(net.minecraft.world.level.block.Blocks.LEVER)?!restored.equals(restore):!Blueprint.matches(c.before(),restored))
                throw new IllegalStateException("Minecraft rejected undo; journal retained for recovery");
            journal.removeLast();pendingPlacement=false;save();
        }
        if(!journal.isEmpty() && journal.stream().anyMatch(c->c.operation().equals(targetOperation)))throw new IllegalStateException("Undo target is not the latest contiguous operation; journal retained");
        finishUndo();
    }
    private List<Change> undoBatch(String operation) {
        var batch=new ArrayList<Change>();
        for(int i=journal.size()-1;i>=0;i--){Change change=journal.get(i);if(!change.operation().equals(operation))break;batch.add(change);}
        if(journal.stream().anyMatch(change->change.operation().equals(operation)) && batch.isEmpty())
            throw new IllegalStateException("Undo target is not the latest operation; no older work was changed");
        return batch;
    }
    private boolean undoAfterMatches(Change change,BlockState actual) {
        return change.after().is(net.minecraft.world.level.block.Blocks.LEVER)?change.after().equals(actual):Blueprint.matches(change.after(),actual);
    }
    private void validateUndoNeighbors(Change change,Set<BlockPos> assistantPositions,Set<BlockPos> plannedPositions) {
        for(net.minecraft.core.Direction direction:net.minecraft.core.Direction.values()) {
            BlockPos neighbor=change.pos().relative(direction);
            if(!level().hasChunkAt(neighbor))throw new IllegalStateException("Undo needs loaded neighboring blocks");
            BlockState actual=level().getBlockState(neighbor);
            if(actual.isAir() || assistantPositions.contains(neighbor))continue;
            boolean outside=!plannedPositions.contains(neighbor);
            boolean playerSupported=(actual.is(net.minecraft.world.level.block.Blocks.LEVER)||actual.is(net.minecraft.world.level.block.Blocks.REPEATER))&&neighbor.below().equals(change.pos());
            playerSupported|=actual.is(net.minecraft.world.level.block.Blocks.REDSTONE_WALL_TORCH)&&neighbor.relative(actual.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite()).equals(change.pos());
            if(outside || playerSupported)throw new IllegalStateException("Undo would disturb a player block at "+neighbor.toShortString()+"; remove its dependency or take over this section first");
        }
    }
    private static <T extends Comparable<T>> BlockState copyProperty(BlockState target,BlockState source,net.minecraft.world.level.block.state.properties.Property<T> property) {
        return target.hasProperty(property)&&source.hasProperty(property)?target.setValue(property,source.getValue(property)):target;
    }
    private static BlockState restoreDynamic(BlockState before,BlockState actual) {
        if(before.isAir() || !before.is(actual.getBlock()))return before;
        BlockState restore=before;
        restore=copyProperty(restore,actual,BlockStateProperties.POWERED);restore=copyProperty(restore,actual,BlockStateProperties.LIT);
        restore=copyProperty(restore,actual,BlockStateProperties.POWER);restore=copyProperty(restore,actual,BlockStateProperties.LOCKED);
        return restore;
    }
    private void finishUndo() {
        baseline.clear();dependencies.clear();repairTargets.clear();pendingPlacement=false;undoRequested=false;undoOperation=null;
        activeOperation="legacy";operationKind="BUILD";originDiagnosis=null;clearDiagnoses();verified="untested";state="READY";
        detail="Latest assistant operation undone; earlier sections and player-built blocks kept";
        manager.release(this);save();checkpoint();tell(detail);
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
        lastObservation=Companion.gateway(this,"/v2/observe",snapshot,actor());Companion.report(this,lastObservation);return snapshot;
    }
    boolean hasPendingTest() { return trial.pending(); }
    void test() {
        if(state.equals("PLANNING"))throw new IllegalStateException("Planning is active; wait before testing world state");
        trial.start();
    }
    void tickTest() { trial.tick(); }
    void restoreTest() { trial.restore(); }
    void validateTestArea() {
        if(blueprint!=null && ComponentTree.composite(blueprint)) {
            validateComponentTestArea(activeComponent);return;
        }
        validateDeclaredPrerequisites();
        for (Blueprint.PlannedBlock b:assignmentBlocks()) { validateTarget(b.pos()); if (!Blueprint.matches(b.state(),level().getBlockState(b.pos()))) throw new IllegalStateException("Complete/correct the blueprint before testing"); }
        Set<BlockPos> planned=new HashSet<>(); assignmentBlocks().forEach(b->planned.add(b.pos()));
        for (Blueprint.PlannedBlock b:assignmentBlocks()) for (BlockPos p:BlockPos.betweenClosed(b.pos().offset(-2,-2,-2),b.pos().offset(2,2,2))) {
            if (!level().hasChunkAt(p))throw new IllegalStateException("Test stopped: isolation margin is unloaded at "+p.toShortString());
            if (!planned.contains(p) && !level().getBlockState(p).isAir())throw new IllegalStateException("Test needs a two-block air margin around the floating circuit; occupied at "+p.toShortString());
        }
    }
    void checkpoint() {
        if (blueprint==null) return; JsonObject out=new JsonObject(); out.addProperty("worldId",worldId); out.addProperty("dimension",dimension);
        out.addProperty("projectId",blueprint.projectId());out.addProperty("blueprintRevision",blueprint.revision()); out.addProperty("taskId",ComponentTree.composite(blueprint)?projectId+":"+activeComponent:blueprint.id()); out.addProperty("status",state); out.addProperty("detail",detail); out.addProperty("verified",verified);
        var observed=new JsonObject();observed.addProperty("controller",controller.toString());
        observed.addProperty("assistantPlacements",journal.stream().filter(change->change.before().isAir()).count());
        observed.addProperty("assistantChanges",journal.size());observed.addProperty("operationID",activeOperation);
        observed.addProperty("component",activeComponent);var componentTask=assignments.task(blueprint,activeComponent);observed.addProperty("componentBasis",componentTask.basis);observed.addProperty("assignmentGeneration",componentTask.generation);observed.add("componentAssignments",assignments.json());
        observed.addProperty("operationKind",operationKind);observed.addProperty("undoRequested",undoRequested);
        if(undoOperation!=null)observed.addProperty("undoOperation",undoOperation);
        observed.addProperty("tick",level().getGameTime());observed.add("testEvidence",trial.evidence());observed.add("testInputs",trial.inputsSnapshot());
        observed.addProperty("componentBasisVersion",ComponentAssignments.BASIS_VERSION);
        JsonObject testScope=trial.scope();if(testScope!=null)observed.add("testScope",testScope);
        if(componentTask.lastPassedScope!=null)observed.add("lastPassedScope",componentTask.lastPassedScope.deepCopy());
        out.add("observed",observed);
        lastCheckpoint=Companion.gateway(this,"/v2/checkpoint",out,owner);Companion.report(this,lastCheckpoint);
    }
    void save() {
        if(blueprint!=null){assignments.reconcile(blueprint);var task=assignments.task(blueprint,activeComponent);task.status=state;task.verified=verified;}
        JsonObject json=new JsonObject(); json.addProperty("schemaVersion",2); json.addProperty("worldId",worldId); json.addProperty("dimension",dimension);
        json.addProperty("projectId",projectId.toString());json.addProperty("projectRevision",projectRevision);json.add("access",access.json());
        json.addProperty("memoryStatus",memoryStatus);
        json.addProperty("controller",controller.toString());json.addProperty("owner",owner.toString()); json.addProperty("status",state); json.addProperty("detail",detail); json.addProperty("stage",cursor(controller).stage); json.addProperty("verified",verified);
        JsonArray lessons=new JsonArray();cursors.forEach((actor,cursor)->{var item=new JsonObject();item.addProperty("actorId",actor.toString());item.addProperty("stage",cursor.stage);item.addProperty("teaching",cursor.teaching);lessons.add(item);});json.add("tutorials",lessons);
        json.addProperty("pendingPlacement",pendingPlacement);json.addProperty("undoRequested",undoRequested);
        json.addProperty("journalVersion",3);json.addProperty("activeComponent",activeComponent);json.addProperty("activeOperation",activeOperation);json.addProperty("operationKind",operationKind);
        if(originDiagnosis!=null)json.addProperty("originDiagnosis",originDiagnosis.toString());
        if(undoOperation!=null)json.addProperty("undoOperation",undoOperation);
        JsonArray targetPositions=new JsonArray();repairTargets.forEach(pos->targetPositions.add(Blueprint.jsonPos(pos)));json.add("repairTargets",targetPositions);
        if (corner1!=null) json.add("corner1",Blueprint.jsonPos(corner1)); if (corner2!=null) json.add("corner2",Blueprint.jsonPos(corner2));
        if (blueprint!=null) json.add("blueprint",blueprint.source()); JsonArray changes=new JsonArray();
        json.add("componentAssignments",assignments.json());
        trial.write(json);
        for(Change c:journal) { JsonObject x=new JsonObject(); x.add("pos",Blueprint.jsonPos(c.pos())); x.addProperty("before",Blueprint.describe(c.before())); x.addProperty("after",Blueprint.describe(c.after()));x.addProperty("operation",c.operation());x.addProperty("component",c.component()); changes.add(x); }
        json.add("journal",changes); JsonArray initial=new JsonArray(); baseline.forEach((pos,state)->{JsonObject x=new JsonObject();x.add("pos",Blueprint.jsonPos(pos));x.addProperty("state",Blueprint.describe(state));initial.add(x);});json.add("baseline",initial);
        JsonArray surrounding=new JsonArray();dependencies.forEach((pos,state)->{var item=new JsonObject();item.add("pos",Blueprint.jsonPos(pos));item.addProperty("state",Blueprint.describe(state));surrounding.add(item);});json.add("dependencies",surrounding);
        LocalFiles.write(file,json);
    }
    void load() {
        if(!Files.exists(file)) return;
        try {
            JsonObject json=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            loadDocument(json);
        }catch(Exception e){throw new IllegalStateException("Cannot safely load checkpoint; keep it for recovery",e);}
    }
    private static String operationGroup(String value) {
        if("legacy".equals(value))return value;
        UUID parsed=UUID.fromString(value);
        if(!parsed.toString().equals(value))throw new IllegalArgumentException("Invalid operation identity");
        return value;
    }
    private void loadDocument(JsonObject json) {
        try {
            if(json.get("schemaVersion").getAsInt()!=2 || !projectId.toString().equals(json.get("projectId").getAsString()))throw new IllegalStateException("Legacy or mismatched project data requires explicit administrator migration");
            if (!worldId.equals(json.get("worldId").getAsString()) || !dimension.equals(json.get("dimension").getAsString()) || !owner.toString().equals(json.get("owner").getAsString())) throw new IllegalStateException("Checkpoint identity mismatch");
            access=ProjectAccess.read(owner,json.getAsJsonObject("access"));controller=UUID.fromString(json.get("controller").getAsString());
            projectRevision=json.get("projectRevision").getAsBigDecimal().intValueExact();if(projectRevision<1)throw new IllegalStateException("Invalid project revision");
            if(json.has("corner1")) corner1=Blueprint.pos(json.getAsJsonObject("corner1")); if(json.has("corner2")) corner2=Blueprint.pos(json.getAsJsonObject("corner2"));
            if(json.has("blueprint")) blueprint=Blueprint.parse(json.getAsJsonObject("blueprint"),worldId,dimension);
            if(blueprint!=null && (blueprint.revision()!=projectRevision || !blueprint.projectId().equals(projectId.toString())))throw new IllegalStateException("Intended blueprint identity mismatch");
            stage=json.get("stage").getAsInt();
            if(stage<0 || (blueprint!=null && stage>=blueprint.stages().size())) throw new IllegalStateException("Checkpoint has an invalid tutorial stage");
            for(JsonElement entry:json.getAsJsonArray("tutorials")){var item=entry.getAsJsonObject();var actor=UUID.fromString(item.get("actorId").getAsString());var cursor=cursor(actor);cursor.stage=item.get("stage").getAsBigDecimal().intValueExact();cursor.teaching=false;if(cursor.stage<0 || (blueprint!=null && cursor.stage>=blueprint.stages().size()))throw new IllegalStateException("Invalid personal tutorial stage");}
            verified=json.get("verified").getAsString();
            if(verified.startsWith("passed:")) verified="stale: saved functional evidence requires a new world observation and test";
            pendingPlacement=json.has("pendingPlacement")&&json.get("pendingPlacement").getAsBoolean();undoRequested=json.has("undoRequested")&&json.get("undoRequested").getAsBoolean();
            boolean grouped=json.has("journalVersion");
            if(grouped) {
                int journalVersion=json.get("journalVersion").getAsBigDecimal().intValueExact();
                if(journalVersion!=2 && journalVersion!=3)throw new IllegalStateException("Unsupported operation journal version");
                if(journalVersion==3){activeComponent=json.get("activeComponent").getAsString();
                    if(blueprint!=null)assignments.load(blueprint,json.getAsJsonArray("componentAssignments"));}
                else if(blueprint!=null)assignments.reconcile(blueprint);
                activeOperation=operationGroup(json.get("activeOperation").getAsString());
                operationKind=json.get("operationKind").getAsString();
                if(!Set.of("BUILD","REPAIR").contains(operationKind))throw new IllegalStateException("Invalid active operation kind");
                originDiagnosis=json.has("originDiagnosis")?UUID.fromString(json.get("originDiagnosis").getAsString()):null;
                undoOperation=json.has("undoOperation")?operationGroup(json.get("undoOperation").getAsString()):null;
                if(undoRequested && undoOperation==null)throw new IllegalStateException("Interrupted undo has no selected operation");
                if(!undoRequested && undoOperation!=null)throw new IllegalStateException("Undo target exists without an active undo");
                if(!json.has("repairTargets") || !json.get("repairTargets").isJsonArray())throw new IllegalStateException("Repair target checkpoint is missing");
                for(JsonElement e:json.getAsJsonArray("repairTargets"))if(!repairTargets.add(Blueprint.pos(e.getAsJsonObject())))throw new IllegalStateException("Duplicate repair target");
            } else {
                // Flat historical journals remain one legacy operation; their old
                // baseline never authorizes a new diagnosis or repair operation.
                activeOperation="legacy";operationKind="BUILD";originDiagnosis=null;undoOperation=undoRequested?"legacy":null;repairTargets.clear();
            }
            trial.load(json);
            for(JsonElement e:json.getAsJsonArray("journal")){
                JsonObject x=e.getAsJsonObject();String operation=grouped?operationGroup(x.get("operation").getAsString()):"legacy";
                boolean scoped=grouped && json.get("journalVersion").getAsInt()==3;
                journal.add(new Change(Blueprint.pos(x.getAsJsonObject("pos")),Blueprint.state(x.get("before").getAsString()),Blueprint.state(x.get("after").getAsString()),operation,scoped?x.get("component").getAsString():"root"));
            }
            for(JsonElement e:json.getAsJsonArray("baseline")){JsonObject x=e.getAsJsonObject();baseline.put(Blueprint.pos(x.getAsJsonObject("pos")),Blueprint.state(x.get("state").getAsString()));}
            if(json.has("dependencies"))for(JsonElement e:json.getAsJsonArray("dependencies")){JsonObject x=e.getAsJsonObject();dependencies.put(Blueprint.pos(x.getAsJsonObject("pos")),Blueprint.state(x.get("state").getAsString()));}
            if(journal.size()>4096 || baseline.size()>4096 || dependencies.size()>65536 || cursors.size()>128)throw new IllegalStateException("Checkpoint exceeds project limits");
            if(!journal.isEmpty() && (corner1==null || corner2==null))throw new IllegalStateException("Journal has no bounded workspace");
            Set<String> closedGroups=new HashSet<>();String previousGroup=null;Map<String,String> groupComponents=new HashMap<>();
            for(Change change:journal) {
                if(!contains(change.pos()) || change.before().hasBlockEntity() || change.after().hasBlockEntity())throw new IllegalStateException("Journal entry escapes its workspace or includes a block entity");
                if(blueprint==null || ComponentTree.component(blueprint,change.component()).blocks().stream().noneMatch(block->block.pos().equals(change.pos())))
                    throw new IllegalStateException("Journal entry escaped its recorded component");
                String priorComponent=groupComponents.putIfAbsent(change.operation(),change.component());
                if(priorComponent!=null && !priorComponent.equals(change.component()))throw new IllegalStateException("Operation group spans multiple components");
                if(!change.operation().equals(previousGroup)) {
                    if(closedGroups.contains(change.operation()))throw new IllegalStateException("Operation journal groups are not contiguous");
                    if(previousGroup!=null)closedGroups.add(previousGroup);
                    previousGroup=change.operation();
                }
            }
            if(repairTargets.size()>4096)throw new IllegalStateException("Too many repair targets");
            if(blueprint!=null) {
                Set<BlockPos> planned=new HashSet<>();assignmentBlocks().forEach(block->planned.add(block.pos()));
                if(!planned.containsAll(baseline.keySet()))throw new IllegalStateException("Baseline escaped the canonical blueprint");
                if(dependencies.keySet().stream().anyMatch(planned::contains))throw new IllegalStateException("Dependency overlaps a planned write target");
                Set<BlockPos> allowedDependencies=dependencyPositions(true);
                for(BlockPos dependency:dependencies.keySet()) {
                    if(!allowedDependencies.contains(dependency))throw new IllegalStateException("Dependency escaped the bounded component read assumptions");
                }
            }
            if(operationKind.equals("REPAIR")) {
                if(originDiagnosis==null || repairTargets.isEmpty() || blueprint==null)throw new IllegalStateException("Repair operation identity or targets are incomplete");
                Set<BlockPos> planned=new HashSet<>();assignmentBlocks().forEach(block->planned.add(block.pos()));
                if(!planned.containsAll(repairTargets))throw new IllegalStateException("Repair target escaped the canonical blueprint");
            } else if(originDiagnosis!=null || !repairTargets.isEmpty())throw new IllegalStateException("Build operation contains repair-only metadata");
            if(pendingPlacement && (journal.isEmpty() || !journal.getLast().operation().equals(activeOperation)))throw new IllegalStateException("Pending placement has no matching operation entry");
            state="PAUSED";detail="Checkpoint restored. Inspect the world; explicit resume reconciles before editing.";
        }catch(Exception e){throw new IllegalStateException("Cannot safely load checkpoint; keep it for recovery",e);}
    }
}
