package dev.minecraftai.engineer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/** Canonical physical analog workbench. Geometry carries no permission or functional pass. */
final class AnalogDesign {
    record Cell(BlockPos pos,BlockState state){Cell{pos=pos.immutable();Objects.requireNonNull(state);}}
    record Selector(int bank,int index,BlockPos pos){Selector{pos=pos.immutable();}}
    private final String mode;
    private final BlockPos anchor;
    private final Rotation rotation;
    private final List<Cell> cells;
    private final List<Selector> selectors;

    AnalogDesign(String mode,BlockPos anchor,int degrees){
        if(!"compare".equals(mode) && !"subtract".equals(mode))throw new IllegalArgumentException("Unknown analog mode");
        this.mode=mode;this.anchor=Objects.requireNonNull(anchor).immutable();rotation=Blueprint.rotation(degrees);
        var blocks=new ArrayList<Cell>();var controls=new ArrayList<Selector>();
        for(int bank=0;bank<3;bank++)for(int index=0;index<15;index++){
            var wire=relativeWire(bank,index);
            blocks.add(cell(wire.below(),"minecraft:stone"));
        }
        blocks.add(cell(BlockPos.ZERO.below(),"minecraft:stone"));
        blocks.add(cell(new BlockPos(1,-1,0),"minecraft:stone"));
        for(int bank=0;bank<3;bank++)for(int index=0;index<15;index++){
            var wire=relativeWire(bank,index);
            blocks.add(cell(wire.below(2),"minecraft:lever[face=ceiling,facing=north,powered=false]"));
            controls.add(new Selector(bank,index,absolute(wire.below(2))));
            blocks.add(cell(wire,wireState(bank)));
        }
        // Native DiodeBlock reads its rear along FACING; WEST therefore emits EAST.
        blocks.add(cell(BlockPos.ZERO,"minecraft:comparator[facing=west,mode="+mode+",powered=false]"));
        blocks.add(cell(new BlockPos(1,0,0),wireState(0)));
        cells=List.copyOf(blocks);selectors=List.copyOf(controls);
    }
    String mode(){return mode;}
    List<Cell> cells(){return cells;}
    List<Selector> selectors(){return selectors;}
    BlockPos comparator(){return anchor;}
    BlockPos output(){return absolute(new BlockPos(1,0,0));}
    BlockPos input(int bank){return absolute(relativeWire(bank,bank==2?0:14));}
    boolean selected(Selector selector,int[] levels){
        if(levels==null || levels.length!=3)throw new IllegalArgumentException("Exactly three analog inputs required");
        for(int level:levels)if(level<0 || level>15)throw new IllegalArgumentException("Analog strength outside 0..15");
        if(!selectors.contains(selector))throw new IllegalArgumentException("Selector is outside this design");
        int level=levels[selector.bank()];
        int index=level==0?-1:selector.bank()==2?15-level:level-1;
        return selector.index()==index;
    }
    private Cell cell(BlockPos relative,String state){return new Cell(absolute(relative),Blueprint.state(state).rotate(rotation));}
    private BlockPos absolute(BlockPos relative){
        long x=(long)anchor.getX()+relative.rotate(rotation).getX(),y=(long)anchor.getY()+relative.getY(),z=(long)anchor.getZ()+relative.rotate(rotation).getZ();
        if(Math.abs(x)>29_999_984 || Math.abs(z)>29_999_984 || y < -2048 || y>2047)throw new IllegalArgumentException("Analog design exceeds supported world bounds");
        return new BlockPos((int)x,(int)y,(int)z);
    }
    private static BlockPos relativeWire(int bank,int index){
        if(index<0 || index>=15)throw new IllegalArgumentException("Unknown analog selector index");
        return switch(bank){case 0->new BlockPos(index-15,0,0);case 1->new BlockPos(0,0,index-15);case 2->new BlockPos(0,0,index+1);default->throw new IllegalArgumentException("Unknown analog bank");};
    }
    private static String wireState(int bank){return bank==0?"minecraft:redstone_wire[north=none,south=none,east=side,west=side,power=0]":"minecraft:redstone_wire[north=side,south=side,east=none,west=none,power=0]";}
}
