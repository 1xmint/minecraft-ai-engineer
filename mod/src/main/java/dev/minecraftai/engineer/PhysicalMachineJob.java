package dev.minecraftai.engineer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * One fresh owner request to build a small redstone machine from real items and prove it by pulling its real levers.
 * Every take and placement is a native survival action through the existing gates; a stop leaves every item and block
 * where it is. Never restored from disk: a fresh request builds only what is still missing, with what is already held.
 */
final class PhysicalMachineJob implements PhysicalJob {
    private final ProjectManager manager;
    private final EngineeringSession project;
    private final Blueprint blueprint;
    private final ServerPlayer worker,owner;
    private final Object workerConnection,ownerConnection,pack;
    private final int revision;
    private final long authorization,generation,expires;
    private final String basis,component;
    private final PhysicalMachinePlan plan;
    /** Null only for an imported design, which has no functional contract: it is placed and never tested. */
    private final MechanismCatalog.Contract contract;
    /** What each machine cell must hold right now: its planned state once built, air until then. */
    private final Map<BlockPos,BlockState> expected=new LinkedHashMap<>();
    /** A crew build's finished dependencies; any later change to them stops the build. */
    private final Map<BlockPos,BlockState> dependencyCells=new LinkedHashMap<>();
    private final PhysicalWorkLease.Crew crew;
    private final long workerEpoch;
    private PhysicalMoveTask movement;
    private PhysicalPlacementTask place;
    private PhysicalMachineTrial trial;
    private BlockPos building;
    private boolean closed,announced;
    private int placed;

    private PhysicalMachineJob(ProjectManager manager,EngineeringSession project,ServerPlayer worker,Object pack,boolean geometryOnly){this(manager,project,worker,pack,geometryOnly,null);}
    private PhysicalMachineJob(ProjectManager manager,EngineeringSession project,ServerPlayer worker,Object pack,String explicit){this(manager,project,worker,pack,false,explicit);}
    /** A non-null {@code explicit} builds that one component under this worker's own exclusive claim instead of the session's active component. */
    private PhysicalMachineJob(ProjectManager manager,EngineeringSession project,ServerPlayer worker,Object pack,boolean geometryOnly,String explicit){
        this.manager=manager;this.project=project;this.worker=worker;this.pack=pack;blueprint=project.blueprint;
        if(blueprint==null)throw new IllegalStateException("There is no machine plan in this project yet; nothing was taken or placed");
        owner=manager.server.getPlayerList().getPlayer(project.owner);ownerConnection=owner==null?null:owner.connection;workerConnection=worker.connection;
        revision=project.revision();authorization=project.access.revision();component=explicit==null?project.activeComponent:explicit;
        if(explicit!=null && (!ComponentTree.composite(blueprint) || ComponentTree.nodes(blueprint).stream().noneMatch(node->node.kind().equals("mechanism") && node.id().equals(explicit))))
            throw new IllegalStateException("This project has no buildable component called "+explicit+"; nothing was taken or placed");
        var assignment=project.assignments.task(blueprint,component);basis=assignment.basis;generation=assignment.generation;expires=manager.server.getTickCount()+38400L;
        var a=project.corner1;var b=project.corner2;
        if(a==null || b==null)throw new IllegalStateException("Select a workspace first; nothing was taken or placed");
        long x=Math.abs((long)a.getX()-b.getX())+1,y=Math.abs((long)a.getY()-b.getY())+1,z=Math.abs((long)a.getZ()-b.getZ())+1;
        if(x>128 || y>128 || z>128 || x*y*z>4096)throw new IllegalStateException("Workspace exceeds the bounded job limit; nothing was taken or placed");
        var part=explicit==null?blueprint:ComponentTree.component(blueprint,explicit);
        plan=PhysicalMachinePlan.of(part.blocks());
        contract=geometryOnly?null:testable(EngineeringContracts.contract(part),plan);
        crew=explicit==null?null:new PhysicalWorkLease.Crew(worker.getUUID(),explicit);
        workerEpoch=manager.reservations.workerEpoch(manager.scope(project));
        // Last, so a constructor refusal can never leave a claim behind.
        if(crew!=null)claim(assignment);
    }
    /** Takes this component for this worker, or refuses when a player, another companion or an unfinished dependency stands in the way. */
    private void claim(ComponentAssignments.Task task){
        if("player".equals(task.executor))throw new IllegalStateException("Component "+component+" is assigned to a player; nothing was taken or placed");
        if(task.worker!=null)throw new IllegalStateException("Component "+component+" already has a companion working on it; nothing was taken or placed");
        var edges=new java.util.HashMap<String,java.util.Collection<String>>();
        for(var node:ComponentTree.nodes(blueprint))edges.put(node.id(),node.dependsOn());
        for(var node:ComponentTree.nodes(blueprint)){
            if(!node.kind().equals("mechanism"))continue;
            if(!node.id().equals(component)){
                if(closure(edges,node.id()).contains(component) && project.assignments.task(blueprint,node.id()).worker!=null)
                    throw new IllegalStateException("Component "+node.id()+" depends on "+component+" and is being built; nothing was taken or placed");
                continue;
            }
            for(var dependency:closure(edges,component)){
                if(project.assignments.task(blueprint,dependency).worker!=null)throw new IllegalStateException("Component "+component+" depends on "+dependency+", which a companion is still building; nothing was taken or placed");
                for(var block:ComponentTree.component(blueprint,dependency).blocks()){
                    if(!project.level().hasChunkAt(block.pos()) || !Blueprint.matches(block.state(),project.level().getBlockState(block.pos())))
                        throw new IllegalStateException("Component "+component+" depends on "+dependency+", which is not built yet; nothing was taken or placed");
                    dependencyCells.put(block.pos().immutable(),block.state());
                }
            }
        }
        task.worker=worker.getUUID();
    }
    /** Everything a component depends on, directly or through other components. */
    private static java.util.Set<String> closure(java.util.Map<String,java.util.Collection<String>> edges,String start){
        var found=new java.util.LinkedHashSet<String>();var pending=new java.util.ArrayDeque<>(edges.getOrDefault(start,java.util.List.of()));
        while(!pending.isEmpty()){var next=pending.pop();if(found.add(next))pending.addAll(edges.getOrDefault(next,java.util.List.of()));}
        found.remove(start);return found;
    }
    boolean crew(){return crew!=null;}
    String componentName(){return component;}
    private String label(){return crew==null?"":"Component "+component+": ";}
    /** The contract this companion can prove with levers and lamps, or a refusal before anything is taken. */
    private static MechanismCatalog.Contract testable(MechanismCatalog.Contract contract,PhysicalMachinePlan plan){
        if(contract.cases().isEmpty() || contract.pulse()!=null || contract.analogMode()!=null)throw new IllegalStateException("This machine has no lever and lamp test the companion can run; nothing was taken or placed");
        for(var port:contract.ports()){
            var state=plan.planned().get(port.pos());
            boolean usable=state!=null && (port.input()?state.is(Blocks.LEVER) && state.getValue(BlockStateProperties.ATTACH_FACE)==AttachFace.FLOOR:state.is(Blocks.REDSTONE_LAMP));
            if(!usable)throw new IllegalStateException("Port "+port.name()+" is not a floor lever or lamp in this plan, so the companion cannot test it; nothing was taken or placed");
        }
        for(var vector:contract.cases()){
            if(vector.outputs().isEmpty())throw new IllegalStateException("A test case names no lamp to read; nothing was taken or placed");
            for(var port:contract.ports())if(port.input() && !vector.inputs().containsKey(port.name()))throw new IllegalStateException("A test case gives no value for input "+port.name()+"; nothing was taken or placed");
        }
        return contract;
    }
    /** Takes what is missing from the chest (or uses what is held) and returns the running job. Refuses before any take when the build cannot start. */
    static PhysicalMachineJob build(ProjectManager manager,EngineeringSession project,ServerPlayer worker,Object pack,BlockPos chest,Runnable exclusive){
        exclusive.run();
        var job=new PhysicalMachineJob(manager,project,worker,pack,false);
        PhysicalPlacementRecovery.requireNone(manager,project,worker);
        job.observe();job.fence();job.stock(chest,exclusive);
        return job;
    }
    /** Builds the previewed imported design from real items and stops at geometry: no lever is pulled and no test or pass is recorded. */
    static PhysicalMachineJob geometry(ProjectManager manager,EngineeringSession project,ServerPlayer worker,Object pack,BlockPos chest,Runnable exclusive){
        exclusive.run();
        if(project.blueprint==null || !ImportedBlueprint.describes(project.blueprint.source()))throw new IllegalStateException("Preview an imported design first; nothing was taken or placed");
        var blockers=ImportedBuildReport.blockers(project.blueprint.blocks(),project.level());
        if(!blockers.isEmpty())throw new IllegalStateException("The companion cannot build this design by hand: "+String.join("; ",blockers)+". Nothing was taken or placed");
        var job=new PhysicalMachineJob(manager,project,worker,pack,true);
        PhysicalPlacementRecovery.requireNone(manager,project,worker);
        PhysicalPlacementRecovery.requireNoSiblingPrepared(manager,project,worker.getUUID(),job.plan.planned().keySet());
        job.observe();job.fence();job.stock(chest,exclusive);
        return job;
    }
    /** One crew worker's build of one component from one chest. Every refusal releases the claim; nothing is restored from disk. */
    static PhysicalMachineJob crewBuild(ProjectManager manager,EngineeringSession project,ServerPlayer worker,Object pack,String id,BlockPos chest,Runnable exclusive){
        exclusive.run();
        var job=new PhysicalMachineJob(manager,project,worker,pack,id);
        try{
            PhysicalPlacementRecovery.requireNone(manager,project,worker);
            GridCraftTask.requireNoUnresolved(manager,project,worker);
            PhysicalPlacementRecovery.requireNoSiblingPrepared(manager,project,worker.getUUID(),job.plan.planned().keySet());
            job.observe();job.fence();job.stock(chest,exclusive);
        }catch(RuntimeException refused){job.close();throw refused;}
        return job;
    }
    /** Tests a finished machine. When the machine does not match its plan it reports which cells differ, records the failure and returns null without moving a lever. */
    static PhysicalMachineJob test(ProjectManager manager,EngineeringSession project,ServerPlayer worker,Object pack,Runnable exclusive){
        exclusive.run();
        var job=new PhysicalMachineJob(manager,project,worker,pack,false);
        PhysicalPlacementRecovery.requireNone(manager,project,worker);
        var mismatches=new ArrayList<String>();
        for(var cell:job.plan.cells()){
            var live=project.level().hasChunkAt(cell.pos())?project.level().getBlockState(cell.pos()):null;
            if(live==null || !Blueprint.matches(cell.state(),live))mismatches.add(cell.pos().toShortString()+" should be "+Blueprint.describe(cell.state())+" but is "+(live==null?"not loaded":Blueprint.describe(live)));
            else job.expected.put(cell.pos(),cell.state());
        }
        if(!mismatches.isEmpty()){
            PhysicalMachineTrial.geometryFailed(manager,project,worker,mismatches);
            project.tell("Machine test failed: it does not match the plan, so no lever was pulled. "+String.join("; ",mismatches.subList(0,Math.min(4,mismatches.size())))+(mismatches.size()>4?"; and "+(mismatches.size()-4)+" more.":"."));
            return null;
        }
        job.fence();return job;
    }
    private void observe(){
        for(var cell:plan.cells()){
            if(!project.contains(cell.pos()) || !project.level().hasChunkAt(cell.pos()))throw new IllegalStateException("The machine at "+cell.pos().toShortString()+" is outside the loaded workspace; nothing was taken or placed");
            var live=project.level().getBlockState(cell.pos());
            if(Blueprint.matches(cell.state(),live))expected.put(cell.pos(),cell.state());
            else if(live.isAir())expected.put(cell.pos(),live);
            else throw new IllegalStateException(Blueprint.describe(live)+" at "+cell.pos().toShortString()+" is in the way of this machine. It was left alone; nothing was taken or placed");
        }
    }
    private void stock(BlockPos chest,Runnable exclusive){
        var inventory=worker.getInventory();var need=new LinkedHashMap<Item,Integer>();
        for(var cell:plan.cells())if(expected.get(cell.pos()).isAir())need.merge(cell.material().item,1,Integer::sum);
        for(int slot=0;slot<9;slot++){
            var stack=inventory.getItem(slot);
            if(stack.isEmpty() || !stack.getComponentsPatch().isEmpty() || !need.containsKey(stack.getItem()))continue;
            int rest=need.get(stack.getItem())-stack.getCount();
            if(rest>0)need.put(stack.getItem(),rest);else need.remove(stack.getItem());
        }
        if(need.isEmpty())return;
        if(chest==null)throw new IllegalStateException("Your companion is missing "+names(need)+". Say which chest holds them; nothing was taken or placed");
        int hand=inventory.getItem(inventory.getSelectedSlot()).isEmpty()?inventory.getSelectedSlot():-1;
        for(int slot=0;slot<9 && hand<0;slot++)if(inventory.getItem(slot).isEmpty())hand=slot;
        if(hand<0)throw new IllegalStateException("Your companion needs one empty hotbar slot to open the chest; nothing was taken or placed");
        int free=0;for(int slot=0;slot<9;slot++)if(slot!=hand && inventory.getItem(slot).isEmpty())free++;
        if(free<need.size())throw new IllegalStateException("Your companion needs "+need.size()+" free hotbar slots for these materials; nothing was taken or placed");
        chest=chest.immutable();
        if(!project.contains(chest) || !worker.isWithinBlockInteractionRange(chest,1.0))throw new IllegalStateException("That chest must be inside the workspace and within your companion's reach; nothing was taken or placed");
        inventory.setSelectedSlot(hand);
        var storage=ChestTakeSequence.takeable(worker.level(),chest);var frame=ChestTakeSequence.capture(worker,storage);var contents=ChestTakeSequence.contents(storage);
        var takes=new LinkedHashMap<Item,Integer>();
        for(var wanted:need.entrySet()){
            int smallest=0;
            for(var stack:contents)if(stack.is(wanted.getKey()) && stack.getComponentsPatch().isEmpty() && stack.getCount()>=wanted.getValue() && (smallest==0 || stack.getCount()<smallest))smallest=stack.getCount();
            if(smallest==0)throw new IllegalStateException("That chest has no single stack of "+wanted.getValue()+" "+new net.minecraft.world.item.ItemStack(wanted.getKey()).getHoverName().getString()+"; nothing was taken or placed");
            ChestTakeSequence.plan(frame,wanted.getKey(),smallest);
            takes.put(wanted.getKey(),smallest);
        }
        for(var take:takes.entrySet())ChestTakeTask.take(manager,project,worker,chest,take.getKey(),take.getValue(),exclusive,step->{},crew);
    }
    private static String names(Map<Item,Integer> items){
        var parts=new ArrayList<String>();
        items.forEach((item,count)->parts.add(count+" "+new net.minecraft.world.item.ItemStack(item).getHoverName().getString()));
        return String.join(", ",parts);
    }
    private void fence(){
        if(closed || !manager.server.isSameThread() || manager.server.getTickCount()>=expires || owner==null
            || manager.server.getPlayerList().getPlayer(owner.getUUID())!=owner || owner.connection!=ownerConnection || owner.hasDisconnected()
            || !project.projectId.equals(manager.selections.get(owner.getUUID())) || owner.level()!=project.level() || !(owner.isCreative() || owner.gameMode.getGameModeForPlayer()==GameType.SURVIVAL)
            || manager.server.getPlayerList().getPlayer(worker.getUUID())!=worker || worker.connection!=workerConnection || worker.hasDisconnected() || !worker.isAlive() || worker.level()!=project.level()
            || worker.gameMode.getGameModeForPlayer()!=GameType.SURVIVAL || project.blueprint!=blueprint || (crew==null && !component.equals(project.activeComponent))
            || revision!=project.revision() || authorization!=project.access.revision())throw new IllegalStateException("Machine job authority changed; request fresh work after inspection");
        project.access.require(owner.getUUID(),ProjectAccess.Permission.BUILD);PhysicalWorkLease.requireIdle(project);
        var assignment=project.assignments.task(blueprint,component);
        if(generation!=assignment.generation || !basis.equals(assignment.basis))throw new IllegalStateException("Machine assignment changed; request fresh work after inspection");
        if(crew!=null && workerEpoch!=manager.reservations.workerEpoch(manager.scope(project)))throw new IllegalStateException("Project authority was withdrawn; the build stopped and needs fresh work after inspection");
        if(crew!=null && !crew.worker().equals(assignment.worker))throw new IllegalStateException("This companion no longer holds component "+component+"; request fresh work after inspection");
        for(var dependency:dependencyCells.entrySet())
            if(!project.level().hasChunkAt(dependency.getKey()) || !Blueprint.matches(dependency.getValue(),project.level().getBlockState(dependency.getKey())))
                throw new IllegalStateException("A component this one depends on changed at "+dependency.getKey().toShortString()+"; the build stopped and left it alone");
        for(var cell:plan.cells()){
            var pos=cell.pos();
            if(!project.contains(pos) || !project.level().hasChunkAt(pos))throw new IllegalStateException("Machine footprint unloaded or changed");
            var want=expected.get(pos);var live=project.level().getBlockState(pos);
            boolean still=want.isAir()?live.isAir():Blueprint.matches(want,live);
            if(!still && !(place!=null && pos.equals(building) && PhysicalActionGate.placementObserved(worker,pos) && Blueprint.matches(cell.state(),live)))
                throw new IllegalStateException("A player or world update changed "+pos.toShortString()+"; the machine job paused and left it alone");
        }
    }
    private Vec3 eyeAt(BlockPos feet){return new Vec3(feet.getX()+0.5,feet.getY()+worker.getEyeHeight(),feet.getZ()+0.5);}
    /** Levers and torches are not standable neighbours, so never pick a stance beside one, built or still to come. */
    private boolean blocked(BlockPos feet){
        for(var cell:plan.cells()){
            var at=cell.pos();boolean sameColumn=at.getX()==feet.getX() && at.getZ()==feet.getZ();
            boolean body=at.getY()==feet.getY() || at.getY()==feet.getY()+1;
            if(!body)continue;
            if(sameColumn)return true;
            boolean thin=cell.state().is(Blocks.LEVER) || cell.state().is(Blocks.REDSTONE_TORCH) || cell.state().is(Blocks.REDSTONE_WALL_TORCH);
            if(thin && Math.abs(at.getX()-feet.getX())<=1 && Math.abs(at.getZ()-feet.getZ())<=1)return true;
        }
        return false;
    }
    /** The nearest walkable stance whose eye satisfies {@code reaches}. Issues no action permission. */
    private BlockPos stance(BlockPos around,Predicate<Vec3> reaches,String purpose){
        BlockPos best=null;int distance=Integer.MAX_VALUE;var here=worker.blockPosition();
        for(int dx=-4;dx<=4;dx++)for(int dz=-4;dz<=4;dz++){
            var candidate=new BlockPos(around.getX()+dx,here.getY(),around.getZ()+dz);
            if(candidate.equals(here) || !project.contains(candidate) || blocked(candidate) || !reaches.test(eyeAt(candidate)))continue;
            try{var route=PhysicalMoveTask.plan(project,worker,candidate);if(route.cells().size()<distance){best=candidate;distance=route.cells().size();}}
            catch(IllegalStateException unreachable){/* No permission is issued during route search. */}
        }
        if(best==null)throw new IllegalStateException("No walkable spot lets your companion "+purpose+"; everything built so far was left in place");
        return best;
    }
    private java.util.Set<BlockPos> planReads(){var reads=new java.util.HashSet<>(expected.keySet());reads.addAll(dependencyCells.keySet());return reads;}
    private void walk(BlockPos destination){movement=new PhysicalMoveTask(manager,project,worker,pack,destination,planReads(),this::fence,crew);}
    private PhysicalMachinePlan.Cell nextCell(){
        for(var cell:plan.cells())if(expected.get(cell.pos()).isAir())return cell;
        return null;
    }
    @Override public boolean tick(){
        fence();
        if(movement!=null){if(movement.tick())movement=null;return false;}
        if(place!=null){
            if(place.tick()){expected.put(building,plan.planned().get(building));placed++;place=null;building=null;}
            return false;
        }
        if(trial!=null){
            if(!trial.tick())return false;
            var result=trial.summary();close();owner.sendSystemMessage(Component.literal("Engineer: "+label()+result));return true;
        }
        var next=nextCell();
        if(next!=null){
            building=next.pos();var aim=PhysicalMachinePlan.aim(project.level(),next.state(),building);
            if(PhysicalMachinePlan.clickable(project.level(),worker,worker.getEyePosition(),aim) && !blocked(worker.blockPosition()))
                place=new PhysicalPlacementTask(manager,project,worker,pack,aim,planReads(),this::fence,crew);
            else walk(stance(building,eye->PhysicalMachinePlan.clickable(project.level(),worker,eye,aim),"place the block at "+building.toShortString()));
            return false;
        }
        building=null;
        if(contract==null){
            close();owner.sendSystemMessage(Component.literal("Engineer: Companion placed "+placed+" blocks; every cell now matches the imported design. Geometry only: it is untested, no lever was pulled and no test was recorded."));
            return true;
        }
        if(!announced){
            announced=true;
            owner.sendSystemMessage(Component.literal("Engineer: "+label()+"Companion placed "+placed+" blocks; every cell now matches the plan. Testing the machine by pulling its real levers."));
        }
        var levers=contract.ports().stream().filter(MechanismCatalog.Port::input).map(MechanismCatalog.Port::pos).toList();
        Predicate<Vec3> reaches=eye->levers.stream().allMatch(lever->PhysicalMachinePlan.leverHit(project.level(),worker,eye,lever)!=null);
        if(reaches.test(worker.getEyePosition()))trial=new PhysicalMachineTrial(manager,project,worker,plan,contract,this::fence,crew);
        else walk(stance(levers.getFirst(),reaches,"reach every lever at once"));
        return false;
    }
    @Override public void close(){
        try{if(movement!=null)movement.close();}finally{try{if(place!=null)place.close();}finally{try{if(trial!=null)trial.close();}finally{closed=true;try{PhysicalActionGate.cancel(worker);}finally{release();}}}}
    }
    private void release(){
        if(crew==null)return;
        var task=project.assignments.tasks.get(component);
        if(task!=null && crew.worker().equals(task.worker))task.worker=null;
    }
}
