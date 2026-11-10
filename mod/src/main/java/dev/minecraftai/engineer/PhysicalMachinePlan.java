package dev.minecraftai.engineer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The build order and click geometry for a small redstone machine. Supports come first, then levers, then torches,
 * and powered consumers last, so a placement never changes a cell another placement's lease has already observed.
 */
final class PhysicalMachinePlan {
    static final int MAX_CELLS=32;
    record Cell(BlockPos pos,BlockState state,PhysicalPlacementInventory.Material material){ }
    private final List<Cell> cells;
    private final Map<BlockPos,BlockState> planned;
    private PhysicalMachinePlan(List<Cell> cells,Map<BlockPos,BlockState> planned){this.cells=List.copyOf(cells);this.planned=Collections.unmodifiableMap(planned);}
    static PhysicalMachinePlan of(List<Blueprint.PlannedBlock> blocks){
        if(blocks.isEmpty() || blocks.size()>MAX_CELLS)throw new IllegalStateException("This companion builds machines of 1 to "+MAX_CELLS+" blocks; nothing was taken or placed");
        var cells=new ArrayList<Cell>();
        for(var block:blocks){
            if(block.state().isAir())throw new IllegalStateException("A machine plan cannot include an air cell; nothing was taken or placed");
            face(block.state());
            cells.add(new Cell(block.pos().immutable(),block.state(),PhysicalPlacementInventory.Material.forState(block.state())));
        }
        cells.sort(Comparator.<Cell>comparingInt(cell->rank(cell.state())).thenComparingInt(cell->cell.pos().getY()).thenComparingInt(cell->cell.pos().getX()).thenComparingInt(cell->cell.pos().getZ()));
        var planned=new LinkedHashMap<BlockPos,BlockState>();
        for(var cell:cells)if(planned.put(cell.pos(),cell.state())!=null)throw new IllegalStateException("A machine plan names one cell twice; nothing was taken or placed");
        return new PhysicalMachinePlan(cells,planned);
    }
    private static int rank(BlockState state){
        if(PhysicalActionGate.inertSupport(state))return 0;
        if(state.is(Blocks.LEVER))return 1;
        if(state.is(Blocks.REDSTONE_TORCH) || state.is(Blocks.REDSTONE_WALL_TORCH))return 2;
        return 3;
    }
    List<Cell> cells(){return cells;}
    Map<BlockPos,BlockState> planned(){return planned;}
    /** The face a survival player must click for this state to be the native placement result. */
    static Direction face(BlockState state){
        if(state.is(Blocks.LEVER)){
            var attached=state.getValue(BlockStateProperties.ATTACH_FACE);
            if(attached==AttachFace.FLOOR)return Direction.UP;
            if(attached==AttachFace.WALL)return state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            throw new IllegalStateException("Ceiling levers are not supported by this companion; nothing was taken or placed");
        }
        return state.is(Blocks.REDSTONE_WALL_TORCH)?state.getValue(BlockStateProperties.HORIZONTAL_FACING):Direction.UP;
    }
    static BlockPos support(BlockState state,BlockPos target){return target.relative(face(state).getOpposite());}
    /** The aim for this cell against the support as it stands right now. */
    static PhysicalPlacementInventory.Aim aim(Level level,BlockState state,BlockPos target){
        var support=support(state,target);
        return new PhysicalPlacementInventory.Aim(support,face(state),level.getBlockState(support),state);
    }
    private static Vec3 clickPoint(PhysicalPlacementInventory.Aim aim){
        var normal=aim.face();
        return Vec3.atCenterOf(aim.support()).add(normal.getStepX()*0.499,normal.getStepY()*0.499,normal.getStepZ()*0.499);
    }
    /** Whether the planned face is the first thing a look from {@code eye} would hit. */
    static boolean clickable(Level level,ServerPlayer worker,Vec3 eye,PhysicalPlacementInventory.Aim aim){
        if(eye.distanceTo(Vec3.atCenterOf(aim.target()))>4)return false;
        var point=clickPoint(aim);var state=aim.state();
        if(state.is(Blocks.LEVER) && state.getValue(BlockStateProperties.ATTACH_FACE)==AttachFace.FLOOR){
            // A floor lever faces the way the player looks, so the look must be clearly along its planned facing.
            var facing=state.getValue(BlockStateProperties.HORIZONTAL_FACING);var delta=point.subtract(eye);
            double along=delta.x*facing.getStepX()+delta.z*facing.getStepZ();
            double across=Math.abs(delta.x*facing.getStepZ())+Math.abs(delta.z*facing.getStepX());
            if(along<=across)return false;
        }
        var hit=level.clip(new ClipContext(eye,point,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,worker));
        return hit.getType()==HitResult.Type.BLOCK && hit.getBlockPos().equals(aim.support()) && hit.getDirection()==aim.face();
    }
    /** The native hit on a floor lever from {@code eye}, or null when anything else is in the way. */
    static BlockHitResult leverHit(Level level,ServerPlayer worker,Vec3 eye,BlockPos lever){
        if(eye.distanceTo(Vec3.atCenterOf(lever))>4)return null;
        var point=new Vec3(lever.getX()+0.5,lever.getY()+0.1,lever.getZ()+0.5);
        var hit=level.clip(new ClipContext(eye,point,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,worker));
        return hit.getType()==HitResult.Type.BLOCK && hit.getBlockPos().equals(lever)?hit:null;
    }
}
