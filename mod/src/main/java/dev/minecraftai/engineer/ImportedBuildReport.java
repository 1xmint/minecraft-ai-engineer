package dev.minecraftai.engineer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** What a previewed imported design would cost and whether the companion can place it by hand. Reads only; changes nothing. */
final class ImportedBuildReport {
    private static final int SHOWN=4;
    private ImportedBuildReport() { }

    /** Exact reasons the companion cannot build this design; empty when it can. */
    static List<String> blockers(List<Blueprint.PlannedBlock> blocks,Level level){
        var reasons=new ArrayList<String>();
        if(blocks.size()>PhysicalMachinePlan.MAX_CELLS)reasons.add("it has "+blocks.size()+" blocks and the companion builds at most "+PhysicalMachinePlan.MAX_CELLS);
        Map<BlockPos,BlockState> planned=new HashMap<>();
        for(var block:blocks)planned.put(block.pos(),block.state());
        var noRule=new LinkedHashSet<String>();var ceiling=new ArrayList<String>();var floating=new ArrayList<String>();
        for(var block:blocks){
            var state=block.state();
            try{PhysicalPlacementInventory.Material.forState(state);}
            catch(IllegalStateException unknown){noRule.add(name(state));continue;}
            if(state.is(Blocks.LEVER) && state.getValue(BlockStateProperties.ATTACH_FACE)==AttachFace.CEILING){ceiling.add(block.pos().toShortString());continue;}
            var support=PhysicalMachinePlan.support(state,block.pos());var inPlan=planned.get(support);
            boolean solid=inPlan!=null?PhysicalActionGate.inertSupport(inPlan):level.hasChunkAt(support) && PhysicalActionGate.inertSupport(level.getBlockState(support));
            if(!solid)floating.add(name(state)+" at "+block.pos().toShortString());
        }
        if(!noRule.isEmpty())reasons.add("no hand-placement rule for "+String.join(", ",bounded(new ArrayList<>(noRule))));
        if(!ceiling.isEmpty())reasons.add("ceiling levers at "+String.join(", ",bounded(ceiling)));
        if(!floating.isEmpty())reasons.add("nothing stone, cobblestone or dirt to place against for "+String.join(", ",bounded(floating)));
        return List.copyOf(reasons);
    }
    /** The player-facing material and buildability report for a preview. */
    static String describe(List<Blueprint.PlannedBlock> blocks,Level level){
        var counts=new LinkedHashMap<String,Integer>();var inTheWay=new ArrayList<String>();boolean active=false;
        for(var block:blocks){
            counts.merge(item(block.state()),1,Integer::sum);
            var state=block.state();
            if(state.is(Blocks.LEVER) && state.getValue(BlockStateProperties.POWERED) || state.is(Blocks.REDSTONE_LAMP) && state.getValue(BlockStateProperties.LIT))active=true;
            if(level.hasChunkAt(block.pos())){
                var live=level.getBlockState(block.pos());
                if(!live.isAir() && !Blueprint.matches(state,live))inTheWay.add(name(live)+" at "+block.pos().toShortString());
            }
        }
        var parts=new ArrayList<String>();counts.forEach((name,count)->parts.add(name+" x"+count));
        var text=new StringBuilder("Materials: ").append(String.join(", ",parts.subList(0,Math.min(6,parts.size())))).append(parts.size()>6?", +"+(parts.size()-6)+" more kinds.":".");
        if(!inTheWay.isEmpty())text.append(" In the way: ").append(String.join(", ",bounded(inTheWay))).append(".");
        if(active)text.append(" Switched-on levers and lit lamps are not copied in that state.");
        var blockers=blockers(blocks,level);
        text.append(blockers.isEmpty()?" Your companion can build it from real items: say 'build this design from the chest at X Y Z'.":" Your companion cannot build it by hand: "+String.join("; ",blockers)+".");
        return text.toString();
    }
    private static List<String> bounded(List<String> items){
        if(items.size()<=SHOWN)return items;
        var shown=new ArrayList<>(items.subList(0,SHOWN));shown.add("+"+(items.size()-SHOWN)+" more");return shown;
    }
    private static String name(BlockState state){return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();}
    private static String item(BlockState state){
        try{return BuiltInRegistries.ITEM.getKey(PhysicalPlacementInventory.Material.forState(state).item).getPath();}
        catch(IllegalStateException unknown){return name(state);}
    }
}
