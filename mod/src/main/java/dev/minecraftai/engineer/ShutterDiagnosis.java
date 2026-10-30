package dev.minecraftai.engineer;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** A single physical observation, never a motion authorization or a functional verdict. */
final class ShutterDiagnosis {
    record Report(Map<BlockPos,BlockState> observed,List<String> messages) {
        Report{observed=Map.copyOf(observed);messages=List.copyOf(messages);}
    }
    private ShutterDiagnosis() { }
    static Report inspect(ServerLevel level,ShutterDesign.Design design) {
        if(!level.getServer().isSameThread() || !level.dimension().identifier().toString().equals(design.binding().dimension()))
            throw new IllegalStateException("Shutter diagnosis requires its logical server thread and dimension");
        var observed=new LinkedHashMap<BlockPos,BlockState>();var entities=new HashSet<BlockPos>();boolean moving=false;
        var positions=new ArrayList<>(design.readCells());positions.sort(Comparator.comparingInt((BlockPos pos)->pos.getX()).thenComparingInt(pos->pos.getY()).thenComparingInt(pos->pos.getZ()));
        // Validate the entire footprint before reading any state; never request a chunk.
        for(var pos:positions)if(!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos) || pos.getY()<level.getMinY() || pos.getY()>level.getMaxY())
            throw new IllegalStateException("Shutter diagnosis stopped: unloaded or out-of-bounds footprint at "+pos.toShortString());
        for(var pos:positions){var state=level.getBlockState(pos);observed.put(pos,state);if(level.getBlockEntity(pos)!=null)entities.add(pos);if(state.is(Blocks.MOVING_PISTON))moving=true;}
        var messages=new ArrayList<String>();
        if(moving)messages.add("Inconclusive: piston animation is present. Let motion settle before comparing the shutter pose; no timing failure is inferred from one snapshot.");
        for(var pos:entities)if(!observed.get(pos).is(Blocks.MOVING_PISTON))messages.add("Protected block entity at "+pos.toShortString()+"; its contents were not read and no automatic correction is proposed.");
        var input=observed.get(design.input());var expectedInput=design.pose(false).get("input");
        boolean inputValid=Blueprint.matches(expectedInput,input);
        if(!inputValid)messages.add("Input missing or incorrectly attached at "+design.input().toShortString()+": expected "+Blueprint.describe(expectedInput)+", observed "+Blueprint.describe(input)+". Input request is unknown.");
        var base=observed.get(design.piston());
        boolean closed=inputValid?input.getValue(BlockStateProperties.POWERED):base.is(Blocks.STICKY_PISTON) && base.getValue(BlockStateProperties.EXTENDED);
        var pose=design.pose(closed);var cells=Map.of("base",design.piston(),"front",design.payload(),"aperture",design.aperture());boolean matches=!moving && inputValid;
        if(inputValid)messages.add("Observed lever is "+(closed?"on and requests a closed aperture":"off and requests an open aperture")+". This does not measure actual actuator power or prove that motion completed.");
        for(String name:List.of("base","front","aperture")){
            var pos=cells.get(name);var actual=observed.get(pos);var intended=pose.get(name);
            if(actual.equals(intended) && !entities.contains(pos))continue;
            matches=false;if(moving)continue;
            String fault=actual.isAir()?"Missing mechanical part":name.equals("aperture") && !actual.isAir() && (intended.isAir() || !actual.is(Blocks.STONE))?"Blocked aperture or misplaced payload":actual.is(Blocks.STICKY_PISTON) && actual.getValue(BlockStateProperties.FACING)!=design.facing()?"Incorrect piston orientation":"Mechanical pose mismatch";
            messages.add(fault+" at "+pos.toShortString()+": expected "+Blueprint.describe(intended)+", observed "+Blueprint.describe(actual)+". Preserve the block; inspect position recovery before attempting a repair.");
        }
        for(var block:design.worldBlocks())if(!design.motionCells().contains(block.pos()) && !block.pos().equals(design.input()) && !observed.get(block.pos()).equals(block.state())){
            matches=false;messages.add("Missing or changed support at "+block.pos().toShortString()+": expected "+Blueprint.describe(block.state())+", observed "+Blueprint.describe(observed.get(block.pos()))+".");
        }
        int interference=0;var writes=design.writeCells();
        for(var entry:observed.entrySet())if(!writes.contains(entry.getKey()) && !entry.getValue().isAir()){
            if(interference++<8)messages.add("Possible environmental interference at "+entry.getKey().toShortString()+": occupied isolation margin contains "+Blueprint.describe(entry.getValue())+". Its electrical effect was not tested.");
        }
        if(interference>8)messages.add("Additional occupied isolation cells: "+(interference-8)+".");
        if(matches)messages.add("Observed "+(closed?"closed":"open")+" mechanical pose matches the input request at this instant.");
        messages.add("Single snapshot only: this does not verify timing, reliability or function; existing test history is separate. Run an explicit controlled test when safe; diagnosis changed no blocks, motion guards or execution permissions.");
        messages.add("No automatic mechanical repair is proposed from this observation. Use the guarded position/recovery workflow for interrupted motion; generic block repair cannot move a piston safely.");
        return new Report(observed,messages);
    }
}