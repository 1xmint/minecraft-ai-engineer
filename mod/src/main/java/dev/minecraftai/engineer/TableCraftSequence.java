package dev.minecraftai.engineer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;

/**
 * Bounded native 3x3 protocol for one placed crafting table's own menu.
 * Planned from a settled inventory before the table opens, then bound to the
 * exact menu vanilla opened. Every frame is a prediction from native recipe
 * rules; nothing here mutates a player or inserts a stack.
 */
final class TableCraftSequence implements PhysicalMenuProtocol {
    /** Two inputs need at most two pickups, five single placements, two put-backs, the result take and the stow: 11 clicks. */
    static final int MAX_CLICKS=12;
    static final int MAIN_SLOTS=36;
    static final int GRID=9;

    /** One plain input item and the 3x3 grid cells it fills (row-major, menu slot = 1 + cell). */
    record Input(net.minecraft.world.item.Item item,List<Integer> cells){}
    /** The bounded table recipes, each with exactly two plain inputs, one output and no remainders. */
    enum Recipe {
        WOODEN_PICKAXE("minecraft:wooden_pickaxe",List.of(new Input(Items.OAK_PLANKS,List.of(0,1,2)),new Input(Items.STICK,List.of(4,7))),Items.WOODEN_PICKAXE,"a wooden pickaxe"),
        LEVER("minecraft:lever",List.of(new Input(Items.STICK,List.of(1)),new Input(Items.COBBLESTONE,List.of(4))),Items.LEVER,"a lever"),
        REDSTONE_TORCH("minecraft:redstone_torch",List.of(new Input(Items.REDSTONE,List.of(1)),new Input(Items.STICK,List.of(4))),Items.REDSTONE_TORCH,"a redstone torch"),
        REDSTONE_LAMP("minecraft:redstone_lamp",List.of(new Input(Items.REDSTONE,List.of(1,3,5,7)),new Input(Items.GLOWSTONE,List.of(4))),Items.REDSTONE_LAMP,"a redstone lamp");
        final String id;final List<Input> inputs;final net.minecraft.world.item.Item output;final String label;
        Recipe(String id,List<Input> inputs,net.minecraft.world.item.Item output,String label){this.id=id;this.inputs=inputs;this.output=output;this.label=label;}
        ItemStack output(){return new ItemStack(output);}
        List<ItemStack> grid(){
            var grid=new ArrayList<ItemStack>(Collections.nCopies(GRID,ItemStack.EMPTY));
            for(var input:inputs)for(int cell:input.cells())grid.set(cell,new ItemStack(input.item()));
            return grid;
        }
    }

    /** Immutable live or predicted table frame: inventory, nine grid cells, cursor, preview and selected slot. */
    static final class Frame {
        private final List<ItemStack> inventory,grid;private final ItemStack cursor,preview;private final int selected;
        Frame(List<ItemStack> inventory,List<ItemStack> grid,ItemStack cursor,ItemStack preview,int selected){
            if(inventory.size()!=PhysicalCraftFrame.SLOTS || grid.size()!=GRID || selected<0 || selected>=9)throw new IllegalArgumentException("Invalid native table frame");
            this.inventory=inventory.stream().map(ItemStack::copy).toList();this.grid=grid.stream().map(ItemStack::copy).toList();this.cursor=cursor.copy();this.preview=preview.copy();this.selected=selected;
        }
        List<ItemStack> inventory(){return inventory.stream().map(ItemStack::copy).toList();}
        List<ItemStack> grid(){return grid.stream().map(ItemStack::copy).toList();}
        ItemStack cursor(){return cursor.copy();}
        ItemStack preview(){return preview.copy();}
        int selected(){return selected;}
        boolean menuEmpty(){return cursor.isEmpty() && preview.isEmpty() && grid.stream().allMatch(ItemStack::isEmpty);}
        boolean matches(Frame other){return selected==other.selected && ItemStack.listMatches(inventory,other.inventory) && ItemStack.listMatches(grid,other.grid) && ItemStack.matches(cursor,other.cursor) && ItemStack.matches(preview,other.preview);}
    }

    /** The settled inventory frame before any table opens: the companion's own 2x2 menu must be empty. */
    static Frame capture(ServerPlayer actor){
        PhysicalWorkerMenus.requireSettled(actor);
        var frame=PhysicalCraftFrame.capture(actor);
        return new Frame(frame.inventory(),Collections.nCopies(GRID,ItemStack.EMPTY),ItemStack.EMPTY,ItemStack.EMPTY,frame.selected());
    }
    /** The live frame of an open table menu. The worker's own inventory grid must stay empty throughout. */
    static Frame capture(ServerPlayer actor,CraftingMenu menu){
        var own=actor.inventoryMenu;
        for(int slot=0;slot<=4;slot++)if(!own.getSlot(slot).getItem().isEmpty())throw new IllegalStateException("Worker inventory grid is not empty during table crafting");
        if(!own.getCarried().isEmpty())throw new IllegalStateException("Worker inventory cursor is not empty during table crafting");
        var items=new ArrayList<ItemStack>();for(int slot=0;slot<actor.getInventory().getContainerSize();slot++)items.add(actor.getInventory().getItem(slot));
        var grid=new ArrayList<ItemStack>();for(int slot=1;slot<=GRID;slot++)grid.add(menu.getSlot(slot).getItem());
        return new Frame(items,grid,menu.getCarried(),menu.getSlot(0).getItem(),actor.getInventory().getSelectedSlot());
    }

    /** Native table menu slot for a main inventory index. */
    static int menuSlot(int inventory){
        if(inventory<0 || inventory>=MAIN_SLOTS)throw new IllegalArgumentException("Only main inventory slots are used by table crafting");
        return inventory<9?37+inventory:inventory+1;
    }

    /** Live native preview for a 3x3 grid, as the table menu computes it, or empty. */
    static ItemStack preview(ServerLevel level,List<ItemStack> grid){
        if(grid.stream().allMatch(ItemStack::isEmpty))return ItemStack.EMPTY;
        var input=CraftingInput.of(3,3,grid.stream().map(ItemStack::copy).toList());
        return level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,level).map(recipe->recipe.value().assemble(input)).orElse(ItemStack.EMPTY);
    }
    static boolean consumesCleanly(ServerLevel level,List<ItemStack> grid){
        var input=CraftingInput.of(3,3,grid.stream().map(ItemStack::copy).toList());
        var recipe=level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,level);
        return recipe.isPresent() && recipe.get().value().getRemainingItems(input).stream().allMatch(ItemStack::isEmpty);
    }
    /** Confirms the live native recipe still makes exactly the bounded output with no remainders. */
    static void verify(ServerLevel level,Recipe recipe){
        var input=CraftingInput.of(3,3,recipe.grid());
        var key=ResourceKey.create(Registries.RECIPE,Identifier.parse(recipe.id));
        var found=level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,level).orElseThrow(()->new IllegalStateException("The native recipe for "+recipe.label+" is unavailable; no crafting was started"));
        if(!found.id().equals(key) || !found.value().matches(input,level))throw new IllegalStateException("The native recipe for "+recipe.label+" changed; no crafting was started");
        if(!ItemStack.matches(found.value().assemble(input),recipe.output()) || found.value().getRemainingItems(input).stream().anyMatch(stack->!stack.isEmpty()))
            throw new IllegalStateException("The native recipe for "+recipe.label+" has unsupported output or remainders; no crafting was started");
    }

    /** Mutable prediction model over copies only. */
    private static final class Model {
        final ServerLevel level;final List<ItemStack> inventory,grid;ItemStack cursor;final int selected;
        Model(ServerLevel level,Frame frame){this.level=level;inventory=new ArrayList<>(frame.inventory());grid=new ArrayList<>(frame.grid());cursor=frame.cursor();selected=frame.selected();}
        Frame frame(){return new Frame(inventory,grid,cursor,preview(level,grid),selected);}
        ItemStack get(int slot){
            if(slot>=1 && slot<=GRID)return grid.get(slot-1);
            if(slot>=10 && slot<=36)return inventory.get(slot-1);
            if(slot>=37 && slot<=45)return inventory.get(slot-37);
            throw new IllegalStateException("Table protocol never uses menu slot "+slot);
        }
        void set(int slot,ItemStack stack){
            if(slot>=1 && slot<=GRID)grid.set(slot-1,stack);
            else if(slot>=10 && slot<=36)inventory.set(slot-1,stack);
            else if(slot>=37 && slot<=45)inventory.set(slot-37,stack);
            else throw new IllegalStateException("Table protocol never uses menu slot "+slot);
        }
        /** Predicts exactly one supported native PICKUP click; anything else is refused, not guessed. */
        void click(int slot,int button){
            if(slot==0){
                var result=preview(level,grid);
                if(button!=0 || !cursor.isEmpty() || result.isEmpty() || !consumesCleanly(level,grid))throw new IllegalStateException("Unsupported result take");
                cursor=result;
                for(int cell=0;cell<GRID;cell++)if(!grid.get(cell).isEmpty()){var left=grid.get(cell).copy();left.shrink(1);grid.set(cell,left.isEmpty()?ItemStack.EMPTY:left);}
                return;
            }
            var held=get(slot).copy();
            if(button==1){
                if(slot<1 || slot>GRID || cursor.isEmpty() || !held.isEmpty())throw new IllegalStateException("Unsupported single-item placement");
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

    final Recipe recipe;
    private final List<Frame> frames;private final List<Integer> slots,buttons;
    private final CraftingMenu bound;private final BlockPos table;
    private TableCraftSequence(Recipe recipe,List<Frame> frames,List<Integer> slots,List<Integer> buttons,CraftingMenu bound,BlockPos table){
        if(frames.size()!=slots.size()+1 || slots.size()!=buttons.size() || slots.size()<1 || slots.size()>MAX_CLICKS)throw new IllegalStateException("Table protocol exceeds its bounded native actions");
        this.recipe=recipe;this.frames=List.copyOf(frames);this.slots=List.copyOf(slots);this.buttons=List.copyOf(buttons);this.bound=bound;this.table=table;
    }

    private static boolean plain(ItemStack stack,net.minecraft.world.item.Item item){return stack.is(item) && stack.getComponentsPatch().isEmpty();}
    /** The single plain main-inventory stack of one input, or refusal before any action. */
    private static int source(List<ItemStack> inventory,net.minecraft.world.item.Item item,int needed,Recipe recipe){
        int source=-1;
        for(int slot=0;slot<inventory.size();slot++){
            var held=inventory.get(slot);if(held.isEmpty() || !held.is(item))continue;
            if(slot>=MAIN_SLOTS || source>=0 || !plain(held,item))throw new IllegalStateException("Your companion's "+new ItemStack(item).getHoverName().getString()+" is split or modified; no crafting was started");
            source=slot;
        }
        if(source<0 || inventory.get(source).getCount()<needed)
            throw new IllegalStateException("Your companion needs "+needed+" "+new ItemStack(item).getHoverName().getString()+" to craft "+recipe.label+"; no crafting was started");
        return source;
    }
    private static int emptyMain(List<ItemStack> inventory){
        for(int slot=0;slot<MAIN_SLOTS;slot++)if(inventory.get(slot).isEmpty())return slot;
        throw new IllegalStateException("Your companion has no free inventory space for the crafted result; no crafting was started");
    }

    /** Pure prediction of the bounded table craft from a settled pre-open frame. Refuses before any action when it cannot be exact. */
    static TableCraftSequence plan(ServerLevel level,Frame initial,Recipe recipe){
        if(!initial.menuEmpty())throw new IllegalStateException("Table crafting must begin with an empty native grid and cursor");
        verify(level,recipe);
        var inventory=initial.inventory();
        var sources=new ArrayList<Integer>();for(var input:recipe.inputs)sources.add(source(inventory,input.item(),input.cells().size(),recipe));
        var model=new Model(level,initial);var frames=new ArrayList<Frame>(List.of(model.frame()));var slots=new ArrayList<Integer>();var buttons=new ArrayList<Integer>();
        java.util.function.BiConsumer<Integer,Integer> click=(slot,button)->{
            if(slots.size()>=MAX_CLICKS)throw new IllegalStateException("Table protocol exceeds its bounded native actions");
            model.click(slot,button);slots.add(slot);buttons.add(button);frames.add(model.frame());
        };
        for(int i=0;i<recipe.inputs.size();i++){
            int from=menuSlot(sources.get(i));
            click.accept(from,0);
            for(int cell:recipe.inputs.get(i).cells())click.accept(1+cell,1);
            if(!model.cursor.isEmpty())click.accept(from,0);
        }
        if(!ItemStack.matches(preview(level,model.grid),recipe.output()))throw new IllegalStateException("Native preview does not match "+recipe.label+"; no crafting was started");
        click.accept(0,0);
        if(!ItemStack.matches(model.cursor,recipe.output()) || !model.grid.stream().allMatch(ItemStack::isEmpty))throw new IllegalStateException("Native craft would leave unexpected resources; no crafting was started");
        click.accept(menuSlot(emptyMain(model.inventory)),0);
        if(!model.cursor.isEmpty())throw new AssertionError();
        return new TableCraftSequence(recipe,frames,slots,buttons,null,null);
    }
    /** Binds this plan to the exact native table menu vanilla opened. */
    TableCraftSequence opened(CraftingMenu menu,BlockPos table){
        if(bound!=null)throw new IllegalStateException("Table protocol is already bound");
        return new TableCraftSequence(recipe,frames,slots,buttons,java.util.Objects.requireNonNull(menu),table.immutable());
    }

    public int clicks(){return slots.size();}
    public int slot(int step){requireStep(step);return slots.get(step-1);}
    public int button(int step){requireStep(step);return buttons.get(step-1);}
    public int maxClicks(){return MAX_CLICKS;}
    Frame before(int step){requireStep(step);return frames.get(step-1);}
    Frame after(int step){requireStep(step);return frames.get(step);}
    Frame initial(){return frames.getFirst();}
    Frame settled(){return frames.getLast();}
    /** The predicted inventory after the final click; the grid and cursor are then empty. */
    List<ItemStack> finalInventory(){return settled().inventory();}
    public net.minecraft.world.inventory.AbstractContainerMenu menu(ServerPlayer actor){
        if(bound==null)throw new IllegalStateException("Table protocol is not bound to an open table");
        return bound;
    }
    public boolean ownsMenu(ServerPlayer actor,net.minecraft.world.inventory.AbstractContainerMenu menu){
        return bound!=null && menu==bound && bound.stillValid(actor) && actor.level().hasChunkAt(table) && actor.level().getBlockState(table).is(Blocks.CRAFTING_TABLE);
    }
    public boolean needsSettledStart(){return false;}
    @Override public boolean beforeMatches(int step,ServerPlayer actor){return bound!=null && before(step).matches(capture(actor,bound));}
    @Override public boolean afterMatches(int step,ServerPlayer actor){return bound!=null && after(step).matches(capture(actor,bound));}
    public void validateNative(ServerPlayer actor){verify(actor.level(),recipe);}
    private void requireStep(int step){if(step<1 || step>slots.size())throw new IllegalArgumentException("Unsupported table crafting step");}
}
