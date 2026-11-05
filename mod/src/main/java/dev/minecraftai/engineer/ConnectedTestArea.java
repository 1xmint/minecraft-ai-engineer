package dev.minecraftai.engineer;

import java.util.List;
import java.util.LinkedHashSet;
import net.minecraft.core.BlockPos;

/** Fixed intent geometry only. No world observations, permissions or leases. */
final class ConnectedTestArea {
    private final List<Blueprint.PlannedBlock> cells;
    private final List<BlockPos> margin;
    ConnectedTestArea(List<Blueprint.PlannedBlock> source){
        if(source.isEmpty() || source.size()>4096)throw new IllegalArgumentException("Connected test geometry is out of bounds");
        cells=source.stream().map(cell->new Blueprint.PlannedBlock(cell.pos().immutable(),cell.state(),cell.stage(),cell.component())).toList();
        var planned=new LinkedHashSet<BlockPos>();for(var cell:cells)if(!planned.add(cell.pos()))throw new IllegalArgumentException("Duplicate connected test cell");
        var all=new LinkedHashSet<BlockPos>();
        for(var cell:cells){
            var p=cell.pos();var min=new BlockPos(Math.subtractExact(p.getX(),2),Math.subtractExact(p.getY(),2),Math.subtractExact(p.getZ(),2));
            var max=new BlockPos(Math.addExact(p.getX(),2),Math.addExact(p.getY(),2),Math.addExact(p.getZ(),2));
            for(var pos:BlockPos.betweenClosed(min,max)){all.add(pos.immutable());if(all.size()>65536)throw new IllegalArgumentException("Connected test footprint exceeds its read budget");}
        }
        all.removeAll(planned);margin=List.copyOf(all);
    }
    boolean matches(List<Blueprint.PlannedBlock> current){return cells.equals(current);}
    List<Blueprint.PlannedBlock> cells(){return cells;}
    List<BlockPos> margin(){return margin;}
}
