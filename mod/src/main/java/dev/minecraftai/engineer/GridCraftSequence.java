package dev.minecraftai.engineer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Bounded native 2x2 menu protocols for the worker's own inventory menu.
 * Every frame is a prediction computed from native recipe rules; nothing here
 * mutates a player or inserts a stack. A craft takes its result exactly once;
 * a recovery only moves resources back and never touches the result slot.
 */
final class GridCraftSequence implements PhysicalMenuProtocol {
    enum Kind { CRAFT, RECOVERY }
    /** A craft needs at most 8 clicks; recovering a full grid plus a held cursor needs 9. */
    static final int MAX_CLICKS=9;
    static final int MAIN_SLOTS=36;
    final Kind kind;
    final GridCraftPlan.Recipe recipe;
    private final List<PhysicalCraftFrame> frames;
    private final List<Integer> slots;
    private final List<Integer> buttons;
    private GridCraftSequence(Kind kind,GridCraftPlan.Recipe recipe,List<PhysicalCraftFrame> frames,List<Integer> slots,List<Integer> buttons){
        if(frames.size()!=slots.size()+1 || slots.size()!=buttons.size() || slots.size()>MAX_CLICKS)throw new IllegalStateException("Grid protocol exceeds its bounded native actions");
        this.kind=kind;this.recipe=recipe;this.frames=List.copyOf(frames);this.slots=List.copyOf(slots);this.buttons=List.copyOf(buttons);
    }

    /** Native slot index in the worker inventory menu for a main inventory index. */
    static int menuSlot(int inventory){
        if(inventory<0 || inventory>=MAIN_SLOTS)throw new IllegalArgumentException("Only main inventory slots are used by grid crafting");
        return inventory<9?36+inventory:inventory;
    }

    /** Mutable prediction model; only this class uses it, and only on copies. */
    private static final class Model {
        final ServerLevel level;final GridCraftPlan.Recipe recorded;final List<ItemStack> inventory;final List<ItemStack> grid;ItemStack cursor;final int selected;
        /** recorded!=null replays saved history against the bounded recipe facts, so a later recipe reload cannot strand recovery. */
        Model(ServerLevel level,PhysicalCraftFrame frame,GridCraftPlan.Recipe recorded){
            this.level=level;this.recorded=recorded;inventory=new ArrayList<>(frame.inventory());grid=new ArrayList<>(frame.menu().inputs());cursor=frame.menu().cursor();selected=frame.selected();
        }
        ItemStack preview(){
            if(recorded==null)return GridCraftPlan.preview(level,grid);
            for(int cell=0;cell<4;cell++){var held=grid.get(cell);if(recorded.cells.contains(cell)?!ItemStack.matches(held,recorded.input()):!held.isEmpty())return ItemStack.EMPTY;}
            return recorded.output();
        }
        boolean consumesCleanly(){return recorded!=null || GridCraftPlan.consumesCleanly(level,grid);}
        PhysicalCraftFrame frame(){return new PhysicalCraftFrame(inventory,new PhysicalMenuSnapshot(grid,cursor),preview(),selected);}
        ItemStack get(int slot){
            if(slot>=1 && slot<=4)return grid.get(slot-1);
            if(slot>=9 && slot<=35)return inventory.get(slot);
            if(slot>=36 && slot<=44)return inventory.get(slot-36);
            throw new IllegalStateException("Grid protocol never uses menu slot "+slot);
        }
        void set(int slot,ItemStack stack){
            if(slot>=1 && slot<=4)grid.set(slot-1,stack);
            else if(slot>=9 && slot<=35)inventory.set(slot,stack);
            else if(slot>=36 && slot<=44)inventory.set(slot-36,stack);
            else throw new IllegalStateException("Grid protocol never uses menu slot "+slot);
        }
        /** Predicts exactly one supported native PICKUP click; anything else is refused, not guessed. */
        void click(int slot,int button){
            if(slot==0){
                var result=preview();
                if(button!=0 || !cursor.isEmpty() || result.isEmpty() || !consumesCleanly())throw new IllegalStateException("Unsupported result take");
                cursor=result;
                for(int cell=0;cell<4;cell++)if(!grid.get(cell).isEmpty()){var left=grid.get(cell).copy();left.shrink(1);grid.set(cell,left.isEmpty()?ItemStack.EMPTY:left);}
                return;
            }
            var held=get(slot).copy();
            if(button==1){
                if(slot<1 || slot>4 || cursor.isEmpty() || !held.isEmpty())throw new IllegalStateException("Unsupported single-item placement");
                set(slot,cursor.copyWithCount(1));var left=cursor.copy();left.shrink(1);cursor=left.isEmpty()?ItemStack.EMPTY:left;return;
            }
            if(button!=0)throw new IllegalStateException("Unsupported menu button");
            if(cursor.isEmpty()){
                if(held.isEmpty())throw new IllegalStateException("A pickup needs a resource");
                cursor=held;set(slot,ItemStack.EMPTY);return;
            }
            if(held.isEmpty()){set(slot,cursor.copy());cursor=ItemStack.EMPTY;return;}
            if(!ItemStack.isSameItemSameComponents(held,cursor) || held.getCount()+cursor.getCount()>held.getMaxStackSize())
                throw new IllegalStateException("Unsupported partial merge or swap");
            set(slot,held.copyWithCount(held.getCount()+cursor.getCount()));cursor=ItemStack.EMPTY;
        }
    }
    private static final class Builder {
        final Model model;final List<PhysicalCraftFrame> frames=new ArrayList<>();final List<Integer> slots=new ArrayList<>();final List<Integer> buttons=new ArrayList<>();
        Builder(Model model){this.model=model;frames.add(model.frame());}
        void click(int slot,int button){
            if(slots.size()>=MAX_CLICKS)throw new IllegalStateException("Grid protocol exceeds its bounded native actions");
            model.click(slot,button);slots.add(slot);buttons.add(button);frames.add(model.frame());
        }
    }
    private static boolean plain(ItemStack stack,ItemStack kind){return ItemStack.isSameItemSameComponents(stack,kind) && stack.getComponentsPatch().isEmpty();}

    /** Where a carried stack goes: an identical plain stack it fully fits, else the first empty main slot, else refusal. */
    private static int target(List<ItemStack> inventory,ItemStack carried){
        if(carried.getComponentsPatch().isEmpty())
            for(int slot=0;slot<MAIN_SLOTS;slot++){var held=inventory.get(slot);if(!held.isEmpty() && plain(held,carried) && held.getCount()+carried.getCount()<=held.getMaxStackSize())return slot;}
        for(int slot=0;slot<MAIN_SLOTS;slot++)if(inventory.get(slot).isEmpty())return slot;
        throw new IllegalStateException("Your companion has no free inventory space for the crafted result; no crafting was started");
    }

    static GridCraftSequence capture(ServerPlayer actor,GridCraftPlan.Recipe recipe){
        PhysicalWorkerMenus.requireSettled(actor);
        return craft(actor.level(),PhysicalCraftFrame.capture(actor),recipe);
    }
    /** Pure prediction of the bounded craft from a settled frame. Refuses before any action when it cannot be exact. */
    static GridCraftSequence craft(ServerLevel level,PhysicalCraftFrame initial,GridCraftPlan.Recipe recipe){return craft(level,initial,recipe,false);}
    /** Replays a recorded craft from its saved baseline using the bounded recipe facts, never the live recipe book. */
    static GridCraftSequence replayCraft(ServerLevel level,PhysicalCraftFrame initial,GridCraftPlan.Recipe recipe){return craft(level,initial,recipe,true);}
    private static GridCraftSequence craft(ServerLevel level,PhysicalCraftFrame initial,GridCraftPlan.Recipe recipe,boolean replay){
        if(!initial.menu().empty() || !replay && !initial.preview().isEmpty())throw new IllegalStateException("Crafting must begin with an empty native grid and cursor");
        if(!replay)GridCraftPlan.verify(level,recipe);
        var inventory=initial.inventory();int source=-1;
        for(int slot=0;slot<inventory.size();slot++){
            var held=inventory.get(slot);if(held.isEmpty() || !held.is(recipe.input))continue;
            if(slot>=MAIN_SLOTS || source>=0 || !plain(held,recipe.input()))throw new IllegalStateException("Your companion's "+recipe.input().getHoverName().getString()+" is split or modified; no crafting was started");
            source=slot;
        }
        if(source<0 || inventory.get(source).getCount()<recipe.cells.size())
            throw new IllegalStateException("Your companion needs "+recipe.cells.size()+" "+recipe.input().getHoverName().getString()+" to craft "+recipe.label+"; no crafting was started");
        var builder=new Builder(new Model(level,initial,replay?recipe:null));int from=menuSlot(source);
        builder.click(from,0);
        for(int cell:recipe.cells)builder.click(1+cell,1);
        if(!builder.model.cursor.isEmpty())builder.click(from,0);
        int result=builder.slots.size()+1;
        if(!ItemStack.matches(builder.model.preview(),recipe.output()))throw new IllegalStateException("Native preview does not match "+recipe.label+"; no crafting was started");
        builder.click(0,0);
        if(!ItemStack.matches(builder.model.cursor,recipe.output()) || !builder.model.grid.stream().allMatch(ItemStack::isEmpty))throw new IllegalStateException("Native craft would leave unexpected resources; no crafting was started");
        builder.click(menuSlot(target(builder.model.inventory,builder.model.cursor)),0);
        var sequence=new GridCraftSequence(Kind.CRAFT,recipe,builder.frames,builder.slots,builder.buttons);
        if(sequence.resultStep()!=result)throw new AssertionError();
        return sequence;
    }
    /** Moves every grid and carried resource back to main inventory from any observed frame. Never takes a result. */
    static GridCraftSequence recovery(ServerLevel level,PhysicalCraftFrame current,GridCraftPlan.Recipe recipe){return recovery(level,current,recipe,false);}
    static GridCraftSequence replayRecovery(ServerLevel level,PhysicalCraftFrame current,GridCraftPlan.Recipe recipe){return recovery(level,current,recipe,true);}
    private static GridCraftSequence recovery(ServerLevel level,PhysicalCraftFrame current,GridCraftPlan.Recipe recipe,boolean replay){
        // Recovery never takes a result, so its frames always use the native preview a half-filled grid really shows.
        var builder=new Builder(new Model(level,current,null));
        if(!builder.model.cursor.isEmpty())builder.click(menuSlot(target(builder.model.inventory,builder.model.cursor)),0);
        for(int cell=0;cell<4;cell++){
            if(builder.model.grid.get(cell).isEmpty())continue;
            builder.click(1+cell,0);builder.click(menuSlot(target(builder.model.inventory,builder.model.cursor)),0);
        }
        return new GridCraftSequence(Kind.RECOVERY,recipe,builder.frames,builder.slots,builder.buttons);
    }

    int resultStep(){return slots.indexOf(0)+1;}
    public int clicks(){return slots.size();}
    public int slot(int step){requireStep(step);return slots.get(step-1);}
    public int button(int step){requireStep(step);return buttons.get(step-1);}
    public PhysicalCraftFrame before(int step){requireStep(step);return frames.get(step-1);}
    public PhysicalCraftFrame after(int step){requireStep(step);return frames.get(step);}
    PhysicalCraftFrame initial(){return frames.getFirst();}
    PhysicalCraftFrame settled(){return frames.getLast();}
    /** Every frame this protocol can leave behind: before step 1 through after the last step. */
    List<PhysicalCraftFrame> frames(){return frames;}
    public boolean needsSettledStart(){return kind==Kind.CRAFT;}
    public void validateNative(ServerPlayer actor){
        if(kind==Kind.CRAFT){GridCraftPlan.verify(actor.level(),recipe);return;}
        // Resource movement stays available if the recipe changed; it never takes output.
        if(slots.contains(0))throw new IllegalStateException("Recovery cannot take a crafted result");
    }
    private void requireStep(int step){if(step<1 || step>slots.size())throw new IllegalArgumentException("Unsupported grid crafting step");}
    static List<ItemStack> emptyGrid(){return Collections.nCopies(4,ItemStack.EMPTY);}
}
