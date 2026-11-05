package dev.minecraftai.engineer;

import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** The sole supported four-click protocol. Prediction does not mutate a player. */
final class OakCraftingSequence {
    private final int source;
    private final List<PhysicalCraftFrame> frames;
    private OakCraftingSequence(int source,List<PhysicalCraftFrame> frames){this.source=source;this.frames=List.copyOf(frames);}
    static OakCraftingSequence capture(ServerPlayer actor,int source){
        PhysicalWorkerMenus.requireSettled(actor);
        if(source<0 || source>=9)throw new IllegalArgumentException("Oak source must be a native hotbar slot");
        var frame=PhysicalCraftFrame.capture(actor);var plan=OakCraftingPlan.inspect(actor.level(),actor.getInventory().getItem(source));
        return predict(frame,source,plan);
    }
    static OakCraftingSequence predict(PhysicalCraftFrame initial,int source,OakCraftingPlan plan){
        if(source<0 || source>=9 || !initial.menu().empty() || !initial.preview().isEmpty())throw new IllegalArgumentException("Crafting must begin with an empty native grid and cursor");
        var original=initial.inventory();
        for(int slot=0;slot<original.size();slot++)if(slot==source?!ItemStack.matches(original.get(slot),plan.input()):!original.get(slot).isEmpty())
            throw new IllegalStateException("Oak crafting requires only its one retained source item; other resources are preserved");
        var empty=java.util.Collections.nCopies(4,ItemStack.EMPTY);var taken=new java.util.ArrayList<ItemStack>(original);taken.set(source,ItemStack.EMPTY);
        var result=new java.util.ArrayList<ItemStack>(taken);result.set(source,plan.output());
        int selected=initial.selected();
        return new OakCraftingSequence(source,List.of(initial,
            new PhysicalCraftFrame(taken,new PhysicalMenuSnapshot(empty,plan.input()),ItemStack.EMPTY,selected),
            new PhysicalCraftFrame(taken,new PhysicalMenuSnapshot(List.of(plan.input(),ItemStack.EMPTY,ItemStack.EMPTY,ItemStack.EMPTY),ItemStack.EMPTY),plan.output(),selected),
            new PhysicalCraftFrame(taken,new PhysicalMenuSnapshot(empty,plan.output()),ItemStack.EMPTY,selected),
            new PhysicalCraftFrame(result,new PhysicalMenuSnapshot(empty,ItemStack.EMPTY),ItemStack.EMPTY,selected)));
    }
    PhysicalCraftFrame before(int step){requireStep(step);return frames.get(step-1);}
    PhysicalCraftFrame after(int step){requireStep(step);return frames.get(step);}
    int slot(int step){requireStep(step);return switch(step){case 1,4->36+source;case 2->1;case 3->0;default->throw new AssertionError();};}
    private static void requireStep(int step){if(step<1 || step>4)throw new IllegalArgumentException("Unsupported native crafting step");}
}
