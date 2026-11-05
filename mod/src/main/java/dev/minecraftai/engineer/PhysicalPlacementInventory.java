package dev.minecraftai.engineer;

import java.util.ArrayList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Detached accounting for one placement, after the producer selects the source. */
final class PhysicalPlacementInventory {
    enum Material {
        DIRT(Items.DIRT,Blocks.DIRT,1),OAK_PLANKS(Items.OAK_PLANKS,Blocks.OAK_PLANKS,4);
        final Item item;final Block block;final int maximum;
        Material(Item item,Block block,int maximum){this.item=item;this.block=block;this.maximum=maximum;}
    }
    private final Material material;
    private final int source,count;
    private final PhysicalCraftFrame before,after;
    private PhysicalPlacementInventory(Material material,int source,int count,PhysicalCraftFrame before,PhysicalCraftFrame after){this.material=material;this.source=source;this.count=count;this.before=before;this.after=after;}
    static PhysicalPlacementInventory capture(ServerPlayer actor,Material material){PhysicalWorkerMenus.requireSettled(actor);return predict(PhysicalCraftFrame.capture(actor),material);}
    static PhysicalPlacementInventory predict(PhysicalCraftFrame initial,Material material){
        java.util.Objects.requireNonNull(material);
        if(!initial.menu().empty() || !initial.preview().isEmpty())throw new IllegalStateException("Preserve unsettled crafting resources before native placement");
        var inventory=initial.inventory();int source=-1,count=0;
        for(int slot=0;slot<inventory.size();slot++){
            var stack=inventory.get(slot);if(stack.isEmpty())continue;
            if(source!=-1 || slot>=9 || stack.getCount()<1 || stack.getCount()>material.maximum || !ItemStack.matches(stack,new ItemStack(material.item,stack.getCount())))throw new IllegalStateException("Placement needs one supported unchanged retained material stack in its hotbar; other resources are preserved");
            source=slot;count=stack.getCount();
        }
        if(source<0)throw new IllegalStateException("The companion has no retained placement material");
        var remainder=new ArrayList<>(inventory);var stack=inventory.get(source).copy();stack.shrink(1);remainder.set(source,stack);
        // These copied stacks are predictions only; never insert them into a player.
        var before=new PhysicalCraftFrame(inventory,initial.menu(),ItemStack.EMPTY,source);
        var after=new PhysicalCraftFrame(remainder,initial.menu(),ItemStack.EMPTY,source);
        return new PhysicalPlacementInventory(material,source,count,before,after);
    }
    int source(){return source;}
    int count(){return count;}
    int remaining(){return count-1;}
    Item item(){return material.item;}
    Block block(){return material.block;}
    PhysicalCraftFrame before(){return before;}
    PhysicalCraftFrame after(){return after;}
    boolean beforeMatches(ServerPlayer actor){return before.matches(PhysicalCraftFrame.capture(actor));}
    boolean afterMatches(ServerPlayer actor){return after.matches(PhysicalCraftFrame.capture(actor));}
}
