package dev.minecraftai.engineer;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.PistonType;

/** Read-only projection for the single-stone sticky shutter. Never authority or functional proof. */
final class ShutterMotion {
    private ShutterMotion() { }
    static final class Snapshot {
        private final ServerLevel level;
        private final BlockPos base;
        private final boolean closing;
        private final Map<BlockPos,BlockState> before,terminal;
        private Snapshot(ServerLevel level,BlockPos base,boolean closing,Map<BlockPos,BlockState> before,Map<BlockPos,BlockState> terminal){
            this.level=level;this.base=base.immutable();this.closing=closing;this.before=Map.copyOf(before);this.terminal=Map.copyOf(terminal);
        }
        BlockPos base(){return base;}
        boolean closing(){return closing;}
        Map<BlockPos,BlockState> before(){return before;}
        Map<BlockPos,BlockState> terminal(){return terminal;}
        Set<BlockPos> writes(){return before.keySet();}
        /** Caller must separately validate current project grants, connection and execution fence. */
        void requireFresh(ServerLevel current,Set<BlockPos> allowed){
            sameLevel(current);
            var fresh=capture(current,base,closing,allowed);
            if(!before.equals(fresh.before) || !terminal.equals(fresh.terminal))throw new IllegalStateException("Piston motion basis changed; reconcile before actuation");
        }
        /** Endpoint comparison only: no tick history, lease, execution or verified result. */
        boolean matchesTerminalState(ServerLevel current){
            sameLevel(current);
            for(var entry:terminal.entrySet()){
                var pos=entry.getKey();
                if(!current.hasChunkAt(pos) || !current.getBlockState(pos).equals(entry.getValue()) || current.getBlockEntity(pos)!=null)return false;
            }
            return true;
        }
        private void sameLevel(ServerLevel current){
            if(current!=level || !current.getServer().isSameThread())throw new IllegalStateException("Motion snapshot belongs to a different server level or thread");
        }
    }
    static Snapshot capture(ServerLevel level,BlockPos position,boolean closing,Set<BlockPos> allowed){
        if(!level.getServer().isSameThread())throw new IllegalStateException("Piston projection requires the logical server thread");
        BlockPos base=position.immutable();checkReadable(level,base,allowed);
        BlockState piston=level.getBlockState(base);
        if(!piston.is(Blocks.STICKY_PISTON))throw new IllegalStateException("The shutter requires a sticky piston");
        Direction direction=piston.getValue(BlockStateProperties.FACING);
        if(direction.getAxis()==Direction.Axis.Y || piston.getValue(BlockStateProperties.EXTENDED)==closing)throw new IllegalStateException("The shutter pose or orientation changed");
        BlockPos front=base.relative(direction).immutable(),aperture=base.relative(direction,2).immutable();
        // Check all destinations before reading them; getBlockState must never discover/load a new chunk.
        checkReadable(level,front,allowed);checkReadable(level,aperture,allowed);
        var before=new LinkedHashMap<BlockPos,BlockState>();
        for(var pos:List.of(base,front,aperture)){
            if(level.getBlockEntity(pos)!=null)throw new IllegalStateException("Moving or block-entity state must settle before projection");
            before.put(pos,level.getBlockState(pos));
        }
        BlockState payload=before.get(closing?front:aperture);
        if(!payload.is(Blocks.STONE))throw new IllegalStateException("The shutter payload changed; no extra blocks may be pushed or destroyed");
        BlockState head=Blocks.PISTON_HEAD.defaultBlockState().setValue(BlockStateProperties.FACING,direction).setValue(BlockStateProperties.PISTON_TYPE,PistonType.STICKY).setValue(BlockStateProperties.SHORT,false);
        if(closing){
            if(!before.get(aperture).isAir())throw new IllegalStateException("The aperture was edited or obstructed; replacement preserved");
            // Exact stone/air preconditions bound vanilla's reads and exclude sticky branching/destruction.
            var resolver=new PistonStructureResolver(level,base,direction,true);
            if(!resolver.resolve() || resolver.getPushDirection()!=direction || !resolver.getToDestroy().isEmpty()
                    || resolver.getToPush().size()!=1 || !resolver.getToPush().getFirst().equals(front))throw new IllegalStateException("Vanilla motion no longer matches the captured shutter");
        }else if(!before.get(front).equals(head)){
            throw new IllegalStateException("The extended sticky head changed; reconcile before retraction");
        }
        // Retraction removes the head before vanilla resolves the pull. These exact one-stone
        // preconditions describe that phase without removing anything merely to preview it.
        var terminal=new LinkedHashMap<BlockPos,BlockState>();
        terminal.put(base,piston.setValue(BlockStateProperties.EXTENDED,closing));
        terminal.put(front,closing?head:payload);
        terminal.put(aperture,closing?payload:Blocks.AIR.defaultBlockState());
        return new Snapshot(level,base,closing,before,terminal);
    }
    private static void checkReadable(ServerLevel level,BlockPos pos,Set<BlockPos> allowed){
        if(!allowed.contains(pos))throw new IllegalStateException("Piston motion exceeds the delegated write footprint");
        if(pos.getY()<level.getMinY() || pos.getY()>level.getMaxY() || !level.getWorldBorder().isWithinBounds(pos))throw new IllegalStateException("Piston motion exceeds world bounds");
        if(!level.hasChunkAt(pos))throw new IllegalStateException("Piston motion requires already loaded chunks");
    }
}
