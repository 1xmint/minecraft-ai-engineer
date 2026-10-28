package dev.minecraftai.engineer;

import java.util.*;
import java.util.function.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Read-only inverse simulation; actual mutations still require fresh live checks. */
final class UndoProjection {
    interface View { BlockState get(BlockPos pos); boolean projected(BlockPos pos); }
    private UndoProjection() { }
    static Map<BlockPos,BlockState> project(List<EngineeringSession.Change> inverse,
            Function<BlockPos,BlockState> current,Predicate<EngineeringSession.Change> mayAlreadyBeRestored,
            BiConsumer<EngineeringSession.Change,View> validateRestoration) {
        if(inverse.size()>8192)throw new IllegalStateException("Undo projection exceeds its bounded operation");
        Map<BlockPos,BlockState> projected=new LinkedHashMap<>();
        View view=new View() {
            public BlockState get(BlockPos pos){return projected.containsKey(pos)?projected.get(pos):Objects.requireNonNull(current.apply(pos));}
            public boolean projected(BlockPos pos){return projected.containsKey(pos);}
        };
        for(var change:inverse) {
            BlockPos pos=change.pos().immutable();BlockState actual=view.get(pos);
            if(mayAlreadyBeRestored.test(change) && actual.equals(change.before())){projected.put(pos,actual);continue;}
            if(!matches(change,actual))throw new IllegalStateException("Undo blocked by later edit at "+pos.toShortString());
            validateRestoration.accept(change,view);
            projected.put(pos,restored(change.before(),actual));
        }
        return Map.copyOf(projected);
    }
    static boolean matches(EngineeringSession.Change change,BlockState actual) {
        return change.after().is(Blocks.LEVER)?change.after().equals(actual):Blueprint.matches(change.after(),actual);
    }
    static BlockState restored(BlockState before,BlockState actual) {
        if(before.isAir() || !before.is(actual.getBlock()))return before;
        BlockState restored=copy(before,actual,BlockStateProperties.POWERED);
        restored=copy(restored,actual,BlockStateProperties.LIT);restored=copy(restored,actual,BlockStateProperties.POWER);
        return copy(restored,actual,BlockStateProperties.LOCKED);
    }
    private static <T extends Comparable<T>> BlockState copy(BlockState target,BlockState source,net.minecraft.world.level.block.state.properties.Property<T> property) {
        return target.hasProperty(property)&&source.hasProperty(property)?target.setValue(property,source.getValue(property)):target;
    }
    /** Attachment dependencies in the supported engineering block library. */
    static BlockPos support(BlockState state,BlockPos pos) {
        if(state.is(Blocks.REPEATER))return pos.below();
        if(state.is(Blocks.REDSTONE_WALL_TORCH))return pos.relative(state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite());
        if(state.is(Blocks.LEVER))return switch(state.getValue(BlockStateProperties.ATTACH_FACE)) {
            case FLOOR -> pos.below();case CEILING -> pos.above();
            case WALL -> pos.relative(state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite());
        };
        return null;
    }
}
