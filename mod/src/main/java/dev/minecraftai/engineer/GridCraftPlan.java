package dev.minecraftai.engineer;

import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;

/** Read-only native 2x2 recipe facts. Its stacks are predictions, never inserted into a worker. */
final class GridCraftPlan {
    /** The bounded recipe set. Cells are 2x2 craft-grid indexes (row-major, menu slot = 1 + cell). */
    enum Recipe {
        OAK_PLANKS("minecraft:oak_planks",Items.OAK_LOG,List.of(0),Items.OAK_PLANKS,4,"oak planks"),
        STICK("minecraft:stick",Items.OAK_PLANKS,List.of(0,2),Items.STICK,4,"sticks"),
        CRAFTING_TABLE("minecraft:crafting_table",Items.OAK_PLANKS,List.of(0,1,2,3),Items.CRAFTING_TABLE,1,"a crafting table");
        final String id;final Item input;final List<Integer> cells;final Item output;final int count;final String label;
        Recipe(String id,Item input,List<Integer> cells,Item output,int count,String label){this.id=id;this.input=input;this.cells=cells;this.output=output;this.count=count;this.label=label;}
        ItemStack input(){return new ItemStack(input);}
        ItemStack output(){return new ItemStack(output,count);}
        static Recipe named(String name){
            for(var recipe:values())if(recipe.name().equals(name))return recipe;
            throw new IllegalStateException("Unknown recorded grid recipe");
        }
    }
    private GridCraftPlan() { }

    /** Native preview for an arbitrary 2x2 grid, exactly as the inventory menu would compute it, or empty. */
    static ItemStack preview(ServerLevel level,List<ItemStack> grid){
        if(grid.size()!=4)throw new IllegalArgumentException("A worker craft grid has four cells");
        if(grid.stream().allMatch(ItemStack::isEmpty))return ItemStack.EMPTY;
        var input=CraftingInput.of(2,2,grid.stream().map(ItemStack::copy).toList());
        return level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,level).map(recipe->recipe.value().assemble(input)).orElse(ItemStack.EMPTY);
    }

    /** Remainders a native result take would leave, used to refuse any grid whose take is not a plain single-unit consumption. */
    static boolean consumesCleanly(ServerLevel level,List<ItemStack> grid){
        var input=CraftingInput.of(2,2,grid.stream().map(ItemStack::copy).toList());
        var recipe=level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,level);
        return recipe.isPresent() && recipe.get().value().getRemainingItems(input).stream().allMatch(ItemStack::isEmpty);
    }

    /** Confirms the live native recipe still makes exactly the bounded output with no remainders. */
    static void verify(ServerLevel level,Recipe recipe){
        var grid=new java.util.ArrayList<ItemStack>(java.util.Collections.nCopies(4,ItemStack.EMPTY));
        for(int cell:recipe.cells)grid.set(cell,recipe.input());
        var key=ResourceKey.create(Registries.RECIPE,Identifier.parse(recipe.id));
        var input=CraftingInput.of(2,2,grid);
        var found=level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,level).orElseThrow(()->new IllegalStateException("The native recipe for "+recipe.label+" is unavailable; no crafting was started"));
        if(!found.id().equals(key) || !found.value().matches(input,level))throw new IllegalStateException("The native recipe for "+recipe.label+" changed; no crafting was started");
        if(!ItemStack.matches(found.value().assemble(input),recipe.output()) || found.value().getRemainingItems(input).stream().anyMatch(stack->!stack.isEmpty()))
            throw new IllegalStateException("The native recipe for "+recipe.label+" has unsupported output or remainders; no crafting was started");
    }
}
