package dev.minecraftai.engineer;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.AttachFace;

/** Exact static corrections for an off, retracted shutter. No authority, writes or motion. */
final class ShutterRepair {
    private ShutterRepair() { }
    static List<Diagnosis.Target> corrections(ShutterDesign.Design design,Map<BlockPos,BlockState> observed,Set<BlockPos> blockEntities) {
        if(!observed.keySet().equals(design.readCells()))throw new IllegalStateException("Mechanical repair requires its complete exact read snapshot");
        if(!blockEntities.isEmpty())throw new IllegalStateException("Mechanical repair preserves block entities and moving state");
        var planned=new LinkedHashMap<BlockPos,BlockState>();for(var block:design.worldBlocks())planned.put(block.pos(),block.state());
        for(var entry:observed.entrySet()){
            var actual=Objects.requireNonNull(entry.getValue());var intended=planned.get(entry.getKey());
            if(intended==null && !actual.isAir())throw new IllegalStateException("Mechanical repair preserves the aperture or isolation edit at "+entry.getKey().toShortString());
            if(intended!=null)corrected(intended,actual);
        }
        var targets=new ArrayList<Diagnosis.Target>();
        for(var entry:planned.entrySet()){
            var before=observed.get(entry.getKey());var after=corrected(entry.getValue(),before);
            if(!before.equals(after))targets.add(new Diagnosis.Target(entry.getKey(),before,after,"root","Proposed static correction at "+entry.getKey().toShortString()+": "+Blueprint.describe(before)+" -> "+Blueprint.describe(after)+". The shutter stays off and retracted; function needs a separate test."));
        }
        return List.copyOf(targets);
    }
    static BlockState corrected(BlockState intended,BlockState actual){
        Objects.requireNonNull(intended);Objects.requireNonNull(actual);
        if(!intended.is(Blocks.STONE) && !intended.is(Blocks.STICKY_PISTON) && !intended.is(Blocks.LEVER))throw new IllegalStateException("Unsupported mechanical target block");
        if(intended.is(Blocks.STICKY_PISTON) && (intended.getValue(BlockStateProperties.EXTENDED) || intended.getValue(BlockStateProperties.FACING).getAxis()==net.minecraft.core.Direction.Axis.Y))throw new IllegalStateException("Mechanical repair target must be a retracted horizontal piston");
        if(intended.is(Blocks.LEVER) && (intended.getValue(BlockStateProperties.POWERED) || intended.getValue(BlockStateProperties.ATTACH_FACE)!=AttachFace.FLOOR))throw new IllegalStateException("Mechanical repair target must be an off floor lever");
        if(actual.isAir())return intended;
        if(!actual.is(intended.getBlock()))throw new IllegalStateException("Mechanical repair cannot replace a foreign block kind");
        if(intended.is(Blocks.STICKY_PISTON)){
            if(intended.getValue(BlockStateProperties.EXTENDED) || actual.getValue(BlockStateProperties.EXTENDED))throw new IllegalStateException("Retract and reconcile the shutter before static repair");
            return intended;
        }
        if(intended.is(Blocks.LEVER)){
            if(intended.getValue(BlockStateProperties.POWERED) || actual.getValue(BlockStateProperties.POWERED))throw new IllegalStateException("Turn the input off and reconcile before static repair");
            if(intended.getValue(BlockStateProperties.ATTACH_FACE)!=AttachFace.FLOOR || actual.getValue(BlockStateProperties.ATTACH_FACE)!=AttachFace.FLOOR)throw new IllegalStateException("Unsupported lever attachment needs manual correction");
            return intended;
        }
        if(!intended.is(Blocks.STONE) || !actual.equals(intended))throw new IllegalStateException("Unsupported mechanical structural correction");
        return intended;
    }
}