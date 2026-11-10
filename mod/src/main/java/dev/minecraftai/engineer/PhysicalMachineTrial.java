package dev.minecraftai.engineer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Proves a companion-built machine by behaviour: the companion pulls the real levers by native use, the world's own
 * redstone settles, and the real lamps are read for every contract case. It never edits a block or a test result.
 */
final class PhysicalMachineTrial implements AutoCloseable {
    private static final Set<Block> QUIET=Set.of(Blocks.AIR,Blocks.STONE,Blocks.COBBLESTONE,Blocks.DIRT,Blocks.GRASS_BLOCK,Blocks.OAK_PLANKS,Blocks.CRAFTING_TABLE,Blocks.CHEST);
    private final ProjectManager manager;
    private final ServerPlayer worker;
    private final ServerLevel level;
    private final MechanismCatalog.Contract contract;
    private final Runnable authority;
    private final java.util.Map<BlockPos,net.minecraft.world.level.block.state.BlockState> planned;
    private final PhysicalWorkLease lease;
    private final BlockPos start,cell;
    private final Path file;
    private final JsonObject record;
    private final JsonArray results=new JsonArray();
    private int index,waiting,passed;
    private boolean applied,finished,closed;
    private String failure;

    PhysicalMachineTrial(ProjectManager manager,EngineeringSession project,ServerPlayer worker,PhysicalMachinePlan plan,MechanismCatalog.Contract contract,Runnable authority){
        this(manager,project,worker,plan,contract,authority,null);
    }
    PhysicalMachineTrial(ProjectManager manager,EngineeringSession project,ServerPlayer worker,PhysicalMachinePlan plan,MechanismCatalog.Contract contract,Runnable authority,PhysicalWorkLease.Crew crew){
        this.manager=manager;this.worker=worker;level=project.level();this.contract=contract;this.authority=authority;planned=Map.copyOf(plan.planned());start=worker.blockPosition();cell=plan.cells().getFirst().pos();
        authority.run();
        var writes=plan.planned().keySet();var reads=new HashSet<BlockPos>();
        for(var pos:writes)for(var near:BlockPos.betweenClosed(pos.offset(-2,-2,-2),pos.offset(2,2,2))){
            if(writes.contains(near))continue;
            boolean adjacent=Math.abs(near.getX()-pos.getX())<=1 && Math.abs(near.getY()-pos.getY())<=1 && Math.abs(near.getZ()-pos.getZ())<=1;
            if(!project.contains(near) || !level.hasChunkAt(near)){if(adjacent)throw new IllegalStateException("The machine must sit one block inside this workspace to be tested; no lever was moved");continue;}
            var state=level.getBlockState(near);
            if(!QUIET.contains(state.getBlock()))throw new IllegalStateException("Found "+Blueprint.describe(state)+" at "+near.toShortString()+" beside the machine; it could affect the result, so no lever was moved");
            reads.add(near.immutable());
        }
        int hand=-1;var inventory=worker.getInventory();
        if(inventory.getItem(inventory.getSelectedSlot()).isEmpty())hand=inventory.getSelectedSlot();
        else for(int slot=0;slot<9 && hand<0;slot++)if(inventory.getItem(slot).isEmpty())hand=slot;
        if(hand<0)throw new IllegalStateException("The companion needs one empty hotbar slot to pull levers; no lever was moved");
        inventory.setSelectedSlot(hand);
        lease=new PhysicalWorkLease(manager,project,manager.server.getPlayerList().getPlayer(project.owner),writes,reads,crew);
        file=manager.directory.resolve(project.projectId+".machine-trial-"+UUID.randomUUID()+".json");
        record=header(manager,project,worker,"RUNNING");
        if(crew!=null)record.addProperty("component",crew.component());
        record.addProperty("cases",contract.cases().size());record.addProperty("settleTicks",contract.settleTicks());
        try{LocalFiles.write(file,record);}catch(RuntimeException failed){lease.close();throw failed;}
    }
    private static JsonObject header(ProjectManager manager,EngineeringSession project,ServerPlayer worker,String stage){
        var value=new JsonObject();value.addProperty("schemaVersion",1);value.addProperty("world",manager.worldId);value.addProperty("dimension",project.dimension);
        value.addProperty("project",project.projectId.toString());value.addProperty("worker",worker.getUUID().toString());value.addProperty("blueprint",project.blueprint.id());value.addProperty("stage",stage);return value;
    }
    /** Evidence for a machine that does not match its plan: no lever moved and no circuit claim made. */
    static void geometryFailed(ProjectManager manager,EngineeringSession project,ServerPlayer worker,List<String> mismatches){
        var value=header(manager,project,worker,"FAILED");var list=new JsonArray();mismatches.forEach(list::add);value.add("geometryMismatches",list);
        LocalFiles.write(manager.directory.resolve(project.projectId+".machine-trial-"+UUID.randomUUID()+".json"),value);
    }
    private void validate(){
        if(closed)throw new IllegalStateException("Machine test closed");
        authority.run();
        if(!worker.blockPosition().equals(start))throw new IllegalStateException("The companion moved during the machine test; no result was recorded");
        if(!worker.getMainHandItem().isEmpty())throw new IllegalStateException("The companion's hand changed during the machine test; no result was recorded");
        // The lease guards the cells around the machine; its own cells are guarded here, so no result ever describes a different machine.
        for(var part:planned.entrySet())if(!Blueprint.matches(part.getValue(),level.getBlockState(part.getKey())))
            throw new IllegalStateException("The machine changed at "+part.getKey().toShortString()+" during the test; no result was recorded");
        lease.validate(cell);
    }
    boolean tick(){
        validate();
        if(applied)requireInputsHeld();
        if(waiting>0){waiting--;return false;}
        if(applied){
            observe();applied=false;index++;
            if(index==contract.cases().size()){finish();return true;}
        }
        apply();applied=true;waiting=contract.settleTicks();return false;
    }
    private void apply(){
        var vector=contract.cases().get(index);
        for(var port:contract.ports()){
            if(!port.input())continue;
            var desired=vector.inputs().get(port.name());
            if(desired==null)throw new IllegalStateException("Machine test case has no value for input "+port.name());
            var state=level.getBlockState(port.pos());
            if(!state.is(Blocks.LEVER))throw new IllegalStateException("Input "+port.name()+" is no longer a lever; no result was recorded");
            if(state.getValue(BlockStateProperties.POWERED)!=desired.booleanValue())pull(port.pos(),desired);
        }
    }
    /** A lever someone else flipped during the settle window would make the result describe inputs that were never tested. */
    private void requireInputsHeld(){
        var vector=contract.cases().get(index);
        for(var port:contract.ports()){
            if(!port.input())continue;
            var state=level.getBlockState(port.pos());
            if(!state.is(Blocks.LEVER) || state.getValue(BlockStateProperties.POWERED)!=vector.inputs().get(port.name()).booleanValue())throw new IllegalStateException("The lever at "+port.pos().toShortString()+" changed during the machine test; no result was recorded");
        }
    }
    private void pull(BlockPos lever,boolean desired){
        var hit=PhysicalMachinePlan.leverHit(level,worker,worker.getEyePosition(),lever);
        if(hit==null)throw new IllegalStateException("The companion cannot reach the lever at "+lever.toShortString()+"; no result was recorded");
        // The native use call below skips the packet handler's checks, so apply them here: world border and spawn protection.
        if(!level.getWorldBorder().isWithinBounds(lever) || !level.mayInteract(worker,lever))throw new IllegalStateException("The lever at "+lever.toShortString()+" is protected from the companion; no result was recorded");
        lease.validate(lever);
        PhysicalActionGate.armLever(worker,lever,(long)manager.server.getTickCount()+1,this::validate,()->{});
        try{worker.gameMode.useItemOn(worker,level,worker.getMainHandItem(),InteractionHand.MAIN_HAND,hit);}finally{PhysicalActionGate.cancel(worker);}
        var after=level.getBlockState(lever);
        if(!after.is(Blocks.LEVER) || after.getValue(BlockStateProperties.POWERED)!=desired)throw new IllegalStateException("The lever at "+lever.toShortString()+" did not respond to the companion; this is not a circuit result");
    }
    private void observe(){
        var vector=contract.cases().get(index);var result=new JsonObject();boolean ok=true;
        var inputs=new JsonObject();vector.inputs().forEach(inputs::addProperty);result.add("inputs",inputs);
        var outputs=new JsonObject();
        for(var port:contract.ports()){
            if(port.input())continue;
            var want=vector.outputs().get(port.name());
            if(want==null)continue;
            var state=level.getBlockState(port.pos());
            if(!state.is(Blocks.REDSTONE_LAMP))throw new IllegalStateException("Output "+port.name()+" is no longer a lamp; no result was recorded");
            boolean lit=state.getValue(BlockStateProperties.LIT);outputs.addProperty(port.name(),lit);
            if(lit!=want.booleanValue()){ok=false;if(failure==null)failure="with "+vector.inputs()+" the "+port.name()+" lamp was "+(lit?"on":"off")+" but should be "+(want?"on":"off");}
        }
        result.add("observed",outputs);result.addProperty("passed",ok);results.add(result);if(ok)passed++;
    }
    private void finish(){
        finished=true;record.add("results",results);record.addProperty("passed",passed);record.addProperty("stage",passed==contract.cases().size()?"PASSED":"FAILED");LocalFiles.write(file,record);
    }
    boolean succeeded(){return finished && passed==contract.cases().size();}
    String summary(){
        int total=contract.cases().size();
        return succeeded()?"Machine test passed "+passed+" of "+total+" cases: the companion pulled the real levers and read the real lamps."
            :"Machine test failed: passed "+passed+" of "+total+" cases; "+failure+". The machine was left as it is.";
    }
    @Override public void close(){
        if(closed)return;closed=true;
        try{if(!finished){record.add("results",results);record.addProperty("stage","ABORTED");LocalFiles.write(file,record);}}
        finally{try{PhysicalActionGate.cancel(worker);}finally{lease.close();}}
    }
}
