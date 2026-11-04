package dev.minecraftai.engineer;

import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Immutable live/predicted resource observation, never a source of inserted items. */
final class PhysicalCraftFrame {
    static final int SLOTS=Inventory.INVENTORY_SIZE+Inventory.EQUIPMENT_SLOT_MAPPING.size();
    private final List<ItemStack> inventory;
    private final PhysicalMenuSnapshot menu;
    private final ItemStack preview;
    private final int selected;
    PhysicalCraftFrame(List<ItemStack> inventory,PhysicalMenuSnapshot menu,ItemStack preview,int selected){
        if(inventory.size()!=SLOTS || selected<0 || selected>=9)throw new IllegalArgumentException("Invalid native crafting inventory frame");
        this.inventory=inventory.stream().map(ItemStack::copy).toList();this.menu=menu;this.preview=preview.copy();this.selected=selected;
    }
    static PhysicalCraftFrame capture(ServerPlayer actor){
        var menu=PhysicalWorkerMenus.capture(actor);var items=new java.util.ArrayList<ItemStack>();
        for(int slot=0;slot<actor.getInventory().getContainerSize();slot++)items.add(actor.getInventory().getItem(slot));
        return new PhysicalCraftFrame(items,menu,actor.inventoryMenu.getSlot(0).getItem(),actor.getInventory().getSelectedSlot());
    }
    List<ItemStack> inventory(){return inventory.stream().map(ItemStack::copy).toList();}
    PhysicalMenuSnapshot menu(){return menu;}
    ItemStack preview(){return preview.copy();}
    int selected(){return selected;}
    boolean matches(PhysicalCraftFrame other){return selected==other.selected && ItemStack.listMatches(inventory,other.inventory) && menu.matches(other.menu) && ItemStack.matches(preview,other.preview);}
}
