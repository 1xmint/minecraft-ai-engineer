package dev.minecraftai.engineer;

import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;

/** Read-only native recipe prediction. Its stacks are never inserted into a worker. */
final class OakCraftingPlan {
    private final ItemStack input,output;
    private OakCraftingPlan(ItemStack input,ItemStack output){this.input=input.copy();this.output=output.copy();}
    ItemStack input(){return input.copy();}
    ItemStack output(){return output.copy();}
    /** Historical schema comparison only; never native recipe/action authority. */
    static OakCraftingPlan recordedContract(){return new OakCraftingPlan(new ItemStack(Items.OAK_LOG),new ItemStack(Items.OAK_PLANKS,4));}
    static OakCraftingPlan inspect(ServerLevel level,ItemStack retained){
        var original=new ItemStack(Items.OAK_LOG);
        if(!ItemStack.matches(retained,original))throw new IllegalStateException("Crafting needs exactly one unchanged retained oak log; other resources are preserved");
        var key=ResourceKey.create(Registries.RECIPE,Identifier.parse("minecraft:oak_planks"));
        var grid=CraftingInput.of(2,2,List.of(retained.copy(),ItemStack.EMPTY,ItemStack.EMPTY,ItemStack.EMPTY));
        var recipe=level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,grid,level,key).orElseThrow(()->new IllegalStateException("The native oak-planks recipe is unavailable"));
        if(!recipe.id().equals(key) || !recipe.value().matches(grid,level))throw new IllegalStateException("The native oak recipe changed; no crafting was started");
        var result=recipe.value().assemble(grid);
        if(!ItemStack.matches(result,new ItemStack(Items.OAK_PLANKS,4)) || recipe.value().getRemainingItems(grid).stream().anyMatch(stack->!stack.isEmpty())
            || !ItemStack.matches(grid.getItem(0),original))throw new IllegalStateException("The native oak recipe has unsupported output or remainders; no crafting was started");
        return new OakCraftingPlan(retained,result);
    }
}
