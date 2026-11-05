package dev.minecraftai.engineer;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Exact conservation for one gathered item added to whatever the companion already holds.
 * The only accepted change is a single slot gaining one plain unit of the expected item.
 */
final class PhysicalGatherInventory {
    private static final int MAIN_SLOTS=36;
    private PhysicalGatherInventory() { }

    static List<ItemStack> snapshot(ServerPlayer worker){
        var inventory=worker.getInventory();var result=new ArrayList<ItemStack>(inventory.getContainerSize());
        for(int slot=0;slot<inventory.getContainerSize();slot++)result.add(inventory.getItem(slot).copy());
        return List.copyOf(result);
    }
    static boolean unchanged(List<ItemStack> baseline,ServerPlayer worker){
        var inventory=worker.getInventory();if(baseline.size()!=inventory.getContainerSize())return false;
        for(int slot=0;slot<baseline.size();slot++)if(!ItemStack.matches(baseline.get(slot),inventory.getItem(slot)))return false;
        return true;
    }
    /** True only when the current inventory equals the baseline plus one plain unit of item in one slot. */
    static boolean gainedExactlyOne(List<ItemStack> baseline,ServerPlayer worker,Item item){
        var inventory=worker.getInventory();if(baseline.size()!=inventory.getContainerSize())return false;
        int changed=0;
        for(int slot=0;slot<baseline.size();slot++){
            var before=baseline.get(slot);var now=inventory.getItem(slot);
            if(ItemStack.matches(before,now))continue;
            if(++changed>1)return false;
            if(before.isEmpty()){if(!ItemStack.matches(now,new ItemStack(item)))return false;continue;}
            if(!plain(before,item) || now.getCount()!=before.getCount()+1)return false;
            var expected=before.copy();expected.setCount(before.getCount()+1);
            if(!ItemStack.matches(expected,now))return false;
        }
        return changed==1;
    }
    /** Room for one native pickup: an empty main slot or a plain stack of the item below its limit. */
    static boolean hasRoom(ServerPlayer worker,Item item){
        var inventory=worker.getInventory();int slots=Math.min(MAIN_SLOTS,inventory.getContainerSize());
        for(int slot=0;slot<slots;slot++){
            var stack=inventory.getItem(slot);
            if(stack.isEmpty() || plain(stack,item) && stack.getCount()<stack.getMaxStackSize())return true;
        }
        return false;
    }
    /** Hand mining only: the held stack must be empty or a gathered resource that mining cannot wear. */
    static boolean handMining(ServerPlayer worker){
        var held=worker.getMainHandItem();if(held.isEmpty())return true;
        for(var material:PhysicalMaterial.values())if(plain(held,material.item))return true;
        return false;
    }
    static void requireGatherable(ServerPlayer worker,PhysicalMaterial material){
        if(!handMining(worker))throw new IllegalStateException("Your companion mines by hand and is holding a tool or other item; tool mining is not supported yet, so nothing was gathered");
        if(!hasRoom(worker,material.item))throw new IllegalStateException("Your companion has no free inventory space for one "+(material==PhysicalMaterial.DIRT?"dirt block":"oak log")+"; nothing was gathered or moved");
    }
    private static boolean plain(ItemStack stack,Item item){
        if(!stack.is(item))return false;var unit=stack.copy();unit.setCount(1);return ItemStack.matches(unit,new ItemStack(item));
    }
}
