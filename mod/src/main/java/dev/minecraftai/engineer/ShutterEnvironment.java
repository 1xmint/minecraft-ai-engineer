package dev.minecraftai.engineer;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Read dependencies for the fixed floating shutter, independent from its changing motion pose. */
final class ShutterEnvironment {
    private ShutterEnvironment() { }
    /** Construction accepts missing intended blocks, never powered/extended or foreign geometry. */
    static void validateConstruction(ServerLevel level,ShutterDesign.Design design,Set<BlockPos> reservedReads){
        if(!level.getServer().isSameThread())throw new IllegalStateException("Shutter construction requires the server thread");
        if(!reservedReads.containsAll(design.readCells()))throw new IllegalStateException("Construction dependencies exceed the reserved read footprint");
        for(var pos:design.readCells())readable(level,pos);
        var planned=new HashMap<BlockPos,BlockState>();for(var block:design.worldBlocks())planned.put(block.pos(),block.state());
        for(var pos:design.readCells()){
            var actual=level.getBlockState(pos);var expected=planned.get(pos);
            if(level.getBlockEntity(pos)!=null || !actual.isAir() && (expected==null || !actual.equals(expected)))throw new IllegalStateException("Shutter construction requires an unpowered open partial frame and clear isolation; preserve the edit at "+pos.toShortString());
        }
    }
    static void validateConstructionReserved(ServerLevel level,ShutterDesign.Design design,RegionReservations reservations,RegionReservations.Fence fence,UUID actor,long authorizationRevision,long tick){
        reservations.validateFootprint(design.reservationScope(),fence,actor,design.revision(),authorizationRevision,design.writeCells(),design.readCells(),tick);
        validateConstruction(level,design,reservations.permissionFootprint(design.reservationScope(),fence,true,tick));
    }
    /** The execution caller must additionally supply current player/connection/grant checks. */
    static Snapshot captureReserved(ServerLevel level,ShutterDesign.Design design,RegionReservations reservations,RegionReservations.Fence fence,UUID actor,long authorizationRevision,long tick){
        reservations.validateFootprint(design.reservationScope(),fence,actor,design.revision(),authorizationRevision,design.writeCells(),design.readCells(),tick);
        return capture(level,design,reservations.permissionFootprint(design.reservationScope(),fence,true,tick));
    }
    static final class Snapshot {
        private final ServerLevel level;
        private final BlockPos input;
        private final Map<BlockPos,BlockState> expected;
        private Snapshot(ServerLevel level,BlockPos input,Map<BlockPos,BlockState> expected){this.level=level;this.input=input.immutable();this.expected=Map.copyOf(expected);}
        Set<BlockPos> reads(){return expected.keySet();}
        void requireFresh(ServerLevel current,boolean powered){
            if(current!=level || !current.getServer().isSameThread())throw new IllegalStateException("Shutter dependencies belong to a different level or thread");
            for(var entry:expected.entrySet()){
                readable(current,entry.getKey());var target=entry.getKey().equals(input)?entry.getValue().setValue(BlockStateProperties.POWERED,powered):entry.getValue();
                if(!current.getBlockState(entry.getKey()).equals(target) || current.getBlockEntity(entry.getKey())!=null)throw new IllegalStateException("Shutter input or isolation dependencies changed; preserve and reconcile");
            }
        }
    }
    static Snapshot capture(ServerLevel level,ShutterDesign.Design design,Set<BlockPos> reservedReads){
        if(!level.getServer().isSameThread())throw new IllegalStateException("Shutter dependencies require the server thread");
        var cells=design.readCells();
        if(!reservedReads.containsAll(cells))throw new IllegalStateException("Shutter dependencies exceed the reserved read footprint");
        // Check every chunk before reading any block; dependency discovery never loads one.
        for(var pos:cells)readable(level,pos);
        var fixed=new HashMap<BlockPos,BlockState>();
        for(var block:design.worldBlocks())if(!design.motionCells().contains(block.pos()))fixed.put(block.pos(),block.state());
        var expected=new LinkedHashMap<BlockPos,BlockState>();
        for(var pos:cells){
            if(design.motionCells().contains(pos))continue; // Motion snapshot owns base/head/payload/aperture.
            var actual=level.getBlockState(pos);var intended=fixed.get(pos);
            if(level.getBlockEntity(pos)!=null)throw new IllegalStateException("Shutter dependencies contain a block entity");
            if(intended==null){if(!actual.isAir())throw new IllegalStateException("Shutter needs a clear two-block isolation margin");}
            else {
                if(pos.equals(design.input()))intended=intended.setValue(BlockStateProperties.POWERED,actual.is(intended.getBlock()) && actual.getValue(BlockStateProperties.POWERED));
                if(!actual.equals(intended))throw new IllegalStateException("Shutter input or support differs from accepted intention");
            }
            expected.put(pos,actual);
        }
        return new Snapshot(level,design.input(),expected);
    }
    private static void readable(ServerLevel level,BlockPos pos){
        if(pos.getY()<level.getMinY() || pos.getY()>level.getMaxY() || !level.getWorldBorder().isWithinBounds(pos) || !level.hasChunkAt(pos))throw new IllegalStateException("Shutter dependencies must be inside loaded world bounds");
    }
}
