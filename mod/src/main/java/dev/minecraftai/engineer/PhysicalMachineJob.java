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
    private final MechanismCatalog.Contract contract;
    /** What each machine cell must hold right now: its planned state once built, air until then. */
    private final Map<BlockPos,BlockState> expected=new LinkedHashMap<>();
    private PhysicalMoveTask movement;
    private PhysicalPlacementTask place;
    private PhysicalMachineTrial trial;
    private BlockPos building;
    private boolean closed,announced;
    private int placed;

    private PhysicalMachineJob(ProjectManager manager,EngineeringSession project,ServerPlayer worker,Object pack){
        this.manager=manager;this.project=project;this.worker=worker;this.pack=pack;blueprint=project.blueprint;
        if(blueprint==null)throw new IllegalStateException("There is no machine plan in this project yet; nothing was taken or placed");
        owner=manager.server.getPlayerList().getPlayer(project.owner);ownerConnection=owner==null?null:owner.connection;workerConnection=worker.connection;
        revision=project.revision();authorization=project.access.revision();component=project.activeComponent;
        var assignment=project.assignments.task(blueprint,component);basis=assignment.basis;generation=assignment.generation;expires=manager.server.getTickCount()+38400L;
        var a=project.corner1;var b=project.corner2;
        if(a==null || b==null)throw new IllegalStateException("Select a workspace first; nothing was taken or placed");
        long x=Math.abs((long)a.getX()-b.getX())+1,y=Math.abs((long)a.getY()-b.getY())+1,z=Math.abs((long)a.getZ()-b.getZ())+1;
        if(x>128 || y>128 || z>128 || x*y*z>4096)throw new IllegalStateException("Workspace exceeds the bounded job limit; nothing was taken or placed");
        plan=PhysicalMachinePlan.of(blueprint.blocks());
        contract=testable(EngineeringContracts.contract(blueprint),plan);
    }
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
        var job=new PhysicalMachineJob(manager,project,worker,pack);
        PhysicalPlacementRecovery.requireNone(manager,project,worker);
        job.observe();job.fence();job.stock(chest,exclusive);
        return job;
    }
    /** Tests a finished machine. When the machine does not match its plan it reports which cells differ, records the failure and returns null without moving a lever. */
    static PhysicalMachineJob test(ProjectManager manager,EngineeringSession project,ServerPlayer worker,Object pack,Runnable exclusive){
        exclusive.run();
        var job=new PhysicalMachineJob(manager,project,worker,pack);
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
        for(var take:takes.entrySet())ChestTakeTask.take(manager,project,worker,chest,take.getKey(),take.getValue(),exclusive);
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
            || worker.gameMode.getGameModeForPlayer()!=GameType.SURVIVAL || project.blueprint!=blueprint || !component.equals(project.activeComponent)
            || revision!=project.revision() || authorization!=project.access.revision())throw new IllegalStateException("Machine job authority changed; request fresh work after inspection");
        project.access.require(owner.getUUID(),ProjectAccess.Permission.BUILD);PhysicalWorkLease.requireIdle(project);
        var assignment=project.assignments.task(blueprint,component);
        if(generation!=assignment.generation || !basis.equals(assignment.basis))throw new IllegalStateException("Machine assignment changed; request fresh work after inspection");
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
    private void walk(BlockPos destination){movement=new PhysicalMoveTask(manager,project,worker,pack,destination,new java.util.HashSet<>(expected.keySet()),this::fence);}
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
            var result=trial.summary();close();owner.sendSystemMessage(Component.literal("Engineer: "+result));return true;
        }
        var next=nextCell();
        if(next!=null){
            building=next.pos();var aim=PhysicalMachinePlan.aim(project.level(),next.state(),building);
            if(PhysicalMachinePlan.clickable(project.level(),worker,worker.getEyePosition(),aim) && !blocked(worker.blockPosition()))
                place=new PhysicalPlacementTask(manager,project,worker,pack,aim,new java.util.HashSet<>(expected.keySet()),this::fence);
            else walk(stance(building,eye->PhysicalMachinePlan.clickable(project.level(),worker,eye,aim),"place the block at "+building.toShortString()));
            return false;
        }
        building=null;
        if(!announced){
            announced=true;
            owner.sendSystemMessage(Component.literal("Engineer: Companion placed "+placed+" blocks; every cell now matches the plan. Testing the machine by pulling its real levers."));
        }
        var levers=contract.ports().stream().filter(MechanismCatalog.Port::input).map(MechanismCatalog.Port::pos).toList();
        Predicate<Vec3> reaches=eye->levers.stream().allMatch(lever->PhysicalMachinePlan.leverHit(project.level(),worker,eye,lever)!=null);
        if(reaches.test(worker.getEyePosition()))trial=new PhysicalMachineTrial(manager,project,worker,plan,contract,this::fence);
        else walk(stance(levers.getFirst(),reaches,"reach every lever at once"));
        return false;
    }
    @Override public void close(){
        try{if(movement!=null)movement.close();}finally{try{if(place!=null)place.close();}finally{try{if(trial!=null)trial.close();}finally{closed=true;PhysicalActionGate.cancel(worker);}}}
    }
}
