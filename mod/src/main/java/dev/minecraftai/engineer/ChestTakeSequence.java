package dev.minecraftai.engineer;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;

/**
 * Bounded native two-click take of one exact plain stack from one slot of a
 * single chest into one empty main slot of the companion. Planned from the
 * settled inventory and the chest's live contents before the chest opens, then
 * bound to the exact menu vanilla opened. Nothing here mutates a player or a
 * container.
 */
final class ChestTakeSequence implements PhysicalMenuProtocol {
    static final int MAX_CLICKS=2;
    static final int CHEST_SLOTS=27;
    static final int MAIN_SLOTS=36;

    /** Immutable live or predicted frame: companion inventory, the chest's 27 slots, cursor and selected slot. */
    static final class Frame {
        private final List<ItemStack> inventory,chest;private final ItemStack cursor;private final int selected;
        Frame(List<ItemStack> inventory,List<ItemStack> chest,ItemStack cursor,int selected){
            if(inventory.size()!=PhysicalCraftFrame.SLOTS || chest.size()!=CHEST_SLOTS || selected<0 || selected>=9)throw new IllegalArgumentException("Invalid native chest frame");
            this.inventory=inventory.stream().map(ItemStack::copy).toList();this.chest=chest.stream().map(ItemStack::copy).toList();this.cursor=cursor.copy();this.selected=selected;
        }
        List<ItemStack> inventory(){return inventory.stream().map(ItemStack::copy).toList();}
        List<ItemStack> chest(){return chest.stream().map(ItemStack::copy).toList();}
        ItemStack cursor(){return cursor.copy();}
        boolean matches(Frame other){return selected==other.selected && ItemStack.listMatches(inventory,other.inventory) && ItemStack.listMatches(chest,other.chest) && ItemStack.matches(cursor,other.cursor);}
    }

    /** The single plain chest this protocol may open, or a refusal before any action. */
    static ChestBlockEntity takeable(ServerLevel level,BlockPos pos){
        if(!level.hasChunkAt(pos))throw new IllegalStateException("That chest is not loaded; nothing was taken");
        var state=level.getBlockState(pos);
        if(!state.is(Blocks.CHEST) || state.getValue(ChestBlock.TYPE)!=ChestType.SINGLE)throw new IllegalStateException("Your companion only takes from a single plain chest; nothing was taken");
        if(!(level.getBlockEntity(pos) instanceof ChestBlockEntity chest) || chest.getClass()!=ChestBlockEntity.class || chest.isRemoved())throw new IllegalStateException("That chest has no plain storage; nothing was taken");
        if(chest.isLocked() || chest.getLootTable()!=null)throw new IllegalStateException("That chest is locked or holds unrolled loot; nothing was taken");
        if(ChestBlockEntity.getOpenCount(level,pos)!=0)throw new IllegalStateException("Someone has that chest open; nothing was taken");
        if(!level.getBlockState(pos.above()).isAir())throw new IllegalStateException("That chest needs open air above it; nothing was taken");
        return chest;
    }
    static List<ItemStack> contents(Container chest){
        if(chest.getContainerSize()!=CHEST_SLOTS)throw new IllegalStateException("Unsupported chest size");
        var items=new ArrayList<ItemStack>();for(int slot=0;slot<CHEST_SLOTS;slot++)items.add(chest.getItem(slot));return items;
    }
    /** The settled frame before the chest opens: the companion's own 2x2 menu must be empty. */
    static Frame capture(ServerPlayer actor,Container chest){
        PhysicalWorkerMenus.requireSettled(actor);
        var frame=PhysicalCraftFrame.capture(actor);
        return new Frame(frame.inventory(),contents(chest),ItemStack.EMPTY,frame.selected());
    }
    /** The live frame of the open chest menu. The worker's own inventory grid and cursor stay empty throughout. */
    static Frame capture(ServerPlayer actor,ChestMenu menu){
        var own=actor.inventoryMenu;
        for(int slot=0;slot<=4;slot++)if(!own.getSlot(slot).getItem().isEmpty())throw new IllegalStateException("Worker inventory grid is not empty during a chest take");
        if(!own.getCarried().isEmpty())throw new IllegalStateException("Worker inventory cursor is not empty during a chest take");
        var items=new ArrayList<ItemStack>();for(int slot=0;slot<actor.getInventory().getContainerSize();slot++)items.add(actor.getInventory().getItem(slot));
        return new Frame(items,contents(menu.getContainer()),menu.getCarried(),actor.getInventory().getSelectedSlot());
    }

    private static boolean plain(ItemStack stack,Item item){return stack.is(item) && stack.getComponentsPatch().isEmpty();}
    /** The one chest slot holding exactly count plain items, or a refusal. Several matching slots are refused rather than guessed. */
    private static int source(List<ItemStack> chest,Item item,int count){
        int found=-1;
        for(int slot=0;slot<CHEST_SLOTS;slot++){
            var held=chest.get(slot);if(held.isEmpty() || !held.is(item))continue;
            if(!plain(held,item))throw new IllegalStateException("That chest's "+new ItemStack(item).getHoverName().getString()+" is modified; nothing was taken");
            if(held.getCount()!=count)continue;
            if(found>=0)throw new IllegalStateException("That chest has several matching stacks; nothing was taken");
            found=slot;
        }
        if(found<0)throw new IllegalStateException("That chest has no stack of exactly "+count+" "+new ItemStack(item).getHoverName().getString()+"; nothing was taken");
        return found;
    }
    /** An empty main slot that is not the held hotbar slot, preferring the hotbar so a native placement can use what was taken. */
    private static int emptyMain(List<ItemStack> inventory,int selected){
        for(int slot=0;slot<9;slot++)if(slot!=selected && inventory.get(slot).isEmpty())return slot;
        for(int slot=9;slot<MAIN_SLOTS;slot++)if(inventory.get(slot).isEmpty())return slot;
        throw new IllegalStateException("Your companion has no free inventory space; nothing was taken");
    }

    final Item item;final int count;final int chestSlot,inventorySlot;
    private final Frame initial,carried,settled;
    private final ChestMenu bound;private final BlockPos chest;private final Container container;
    private final int menuChestSlot,menuInventorySlot;
    private ChestTakeSequence(Item item,int count,int chestSlot,int inventorySlot,Frame initial,Frame carried,Frame settled,ChestMenu bound,BlockPos chest,Container container,int menuChestSlot,int menuInventorySlot){
        this.item=item;this.count=count;this.chestSlot=chestSlot;this.inventorySlot=inventorySlot;this.initial=initial;this.carried=carried;this.settled=settled;
        this.bound=bound;this.chest=chest;this.container=container;this.menuChestSlot=menuChestSlot;this.menuInventorySlot=menuInventorySlot;
    }

    /** Pure prediction from a settled pre-open frame. Refuses before any action when it cannot be exact. */
    static ChestTakeSequence plan(Frame initial,Item item,int count){
        if(!initial.cursor.isEmpty())throw new IllegalStateException("A chest take must begin with an empty cursor");
        if(count<1 || count>new ItemStack(item).getMaxStackSize())throw new IllegalStateException("Your companion takes one whole stack of at most "+new ItemStack(item).getMaxStackSize()+"; nothing was taken");
        if(!initial.inventory.get(initial.selected).isEmpty())throw new IllegalStateException("Your companion needs an empty main hand to open a chest; nothing was taken");
        int from=source(initial.chest,item,count),to=emptyMain(initial.inventory,initial.selected);
        var chest=new ArrayList<>(initial.chest());var inventory=new ArrayList<>(initial.inventory());var stack=chest.get(from);
        chest.set(from,ItemStack.EMPTY);var carried=new Frame(inventory,chest,stack,initial.selected);
        inventory.set(to,stack.copy());var settled=new Frame(inventory,chest,ItemStack.EMPTY,initial.selected);
        return new ChestTakeSequence(item,count,from,to,initial,carried,settled,null,null,null,-1,-1);
    }
    /** Binds this plan to the exact native chest menu vanilla opened over that chest's own storage. */
    ChestTakeSequence opened(ServerPlayer actor,ChestMenu menu,BlockPos pos){
        if(bound!=null)throw new IllegalStateException("Chest protocol is already bound");
        var entity=actor.level().getBlockEntity(pos);
        if(menu.getRowCount()!=3 || entity==null || menu.getContainer()!=entity)throw new IllegalStateException("The chest did not open as its own single storage menu");
        int chestMenu=-1,inventoryMenu=-1;
        for(var slot:menu.slots){
            if(slot.container==entity && slot.getContainerSlot()==chestSlot){if(chestMenu>=0)throw new IllegalStateException("Ambiguous chest slot");chestMenu=slot.index;}
            if(slot.container==actor.getInventory() && slot.getContainerSlot()==inventorySlot){if(inventoryMenu>=0)throw new IllegalStateException("Ambiguous inventory slot");inventoryMenu=slot.index;}
        }
        if(chestMenu<0 || inventoryMenu<0)throw new IllegalStateException("The chest menu lacks the planned slots");
        return new ChestTakeSequence(item,count,chestSlot,inventorySlot,initial,carried,settled,menu,pos.immutable(),menu.getContainer(),chestMenu,inventoryMenu);
    }

    public int clicks(){return 2;}
    public int slot(int step){requireStep(step);return step==1?menuChestSlot:menuInventorySlot;}
    public int maxClicks(){return MAX_CLICKS;}
    Frame initial(){return initial;}
    Frame settled(){return settled;}
    List<ItemStack> finalInventory(){return settled.inventory();}
    ItemStack taken(){return carried.cursor();}
    public AbstractContainerMenu menu(ServerPlayer actor){
        if(bound==null)throw new IllegalStateException("Chest protocol is not bound to an open chest");
        return bound;
    }
    public boolean ownsMenu(ServerPlayer actor,AbstractContainerMenu menu){
        return bound!=null && menu==bound && bound.stillValid(actor) && actor.level().hasChunkAt(chest) && actor.level().getBlockState(chest).is(Blocks.CHEST)
            && actor.level().getBlockEntity(chest)==container && bound.getContainer()==container;
    }
    public boolean needsSettledStart(){return false;}
    @Override public boolean beforeMatches(int step,ServerPlayer actor){requireStep(step);return bound!=null && (step==1?initial:carried).matches(capture(actor,bound));}
    @Override public boolean afterMatches(int step,ServerPlayer actor){requireStep(step);return bound!=null && (step==1?carried:settled).matches(capture(actor,bound));}
    public void validateNative(ServerPlayer actor){
        if(!(container instanceof ChestBlockEntity entity) || entity.isRemoved() || entity.isLocked() || entity.getLootTable()!=null)throw new IllegalStateException("The chest changed during the take");
    }
    private void requireStep(int step){if(step<1 || step>2)throw new IllegalArgumentException("Unsupported chest take step");}
}
