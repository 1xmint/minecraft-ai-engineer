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
        DIRT(Items.DIRT,Blocks.DIRT,1),OAK_PLANKS(Items.OAK_PLANKS,Blocks.OAK_PLANKS,4),CRAFTING_TABLE(Items.CRAFTING_TABLE,Blocks.CRAFTING_TABLE,1),
        // Machine materials: placed only against an aimed support face, beside other retained resources that must stay unchanged.
        STONE(Items.STONE,Blocks.STONE,64,true),COBBLESTONE(Items.COBBLESTONE,Blocks.COBBLESTONE,64,true),SCAFFOLD_DIRT(Items.DIRT,Blocks.DIRT,64,true),
        LEVER(Items.LEVER,Blocks.LEVER,64,true),REDSTONE_TORCH(Items.REDSTONE_TORCH,Blocks.REDSTONE_TORCH,64,true),REDSTONE_LAMP(Items.REDSTONE_LAMP,Blocks.REDSTONE_LAMP,64,true);
        final Item item;final Block block;final int maximum;final boolean shared;
        Material(Item item,Block block,int maximum){this(item,block,maximum,false);}
        Material(Item item,Block block,int maximum,boolean shared){this.item=item;this.block=block;this.maximum=maximum;this.shared=shared;}
        /** Whether this material can natively produce {@code state}; a redstone torch item places either torch block. */
        boolean produces(net.minecraft.world.level.block.state.BlockState state){return state.is(block) || this==REDSTONE_TORCH && state.is(Blocks.REDSTONE_WALL_TORCH);}
        static Material forState(net.minecraft.world.level.block.state.BlockState state){
            for(var material:values())if(material.shared && material.produces(state))return material;
            throw new IllegalStateException("The companion has no native placement rule for "+Blueprint.describe(state));
        }
    }
    /** An aimed placement: click {@code face} of the unchanged inert {@code support}, and commit only the exact planned {@code state}. */
    record Aim(net.minecraft.core.BlockPos support,net.minecraft.core.Direction face,net.minecraft.world.level.block.state.BlockState supportState,net.minecraft.world.level.block.state.BlockState state){
        Aim{support=support.immutable();java.util.Objects.requireNonNull(face);java.util.Objects.requireNonNull(supportState);java.util.Objects.requireNonNull(state);}
        net.minecraft.core.BlockPos target(){return support.relative(face);}
    }
    private final Material material;
    private final int source,count;
    private final PhysicalCraftFrame before,after;
    private final Aim aim;
    private PhysicalPlacementInventory(Material material,int source,int count,PhysicalCraftFrame before,PhysicalCraftFrame after,Aim aim){this.material=material;this.source=source;this.count=count;this.before=before;this.after=after;this.aim=aim;}
    /** The same accounting bound to one aimed support face and exact planned state. */
    PhysicalPlacementInventory aimed(Aim aim){
        if(!material.shared || !material.produces(aim.state()))throw new IllegalStateException("Aimed placement needs a machine material that produces the planned state");
        return new PhysicalPlacementInventory(material,source,count,before,after,java.util.Objects.requireNonNull(aim));
    }
    static PhysicalPlacementInventory capture(ServerPlayer actor,Material material){PhysicalWorkerMenus.requireSettled(actor);return predict(PhysicalCraftFrame.capture(actor),material);}
    static PhysicalPlacementInventory predict(PhysicalCraftFrame initial,Material material){
        java.util.Objects.requireNonNull(material);
        if(!initial.menu().empty() || !initial.preview().isEmpty())throw new IllegalStateException("Preserve unsettled crafting resources before native placement");
        var inventory=initial.inventory();int source=-1,count=0;
        if(material.shared){
            // Use the first hotbar stack of exactly this item; every other retained stack must stay unchanged.
            for(int slot=0;slot<9 && source<0;slot++){var stack=inventory.get(slot);if(!stack.isEmpty() && ItemStack.matches(stack,new ItemStack(material.item,stack.getCount()))){source=slot;count=stack.getCount();}}
            if(source<0)throw new IllegalStateException("The companion holds no plain "+net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(material.item)+" in its hotbar; nothing was placed");
        }else
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
        return new PhysicalPlacementInventory(material,source,count,before,after,null);
    }
    int source(){return source;}
    int count(){return count;}
    int remaining(){return count-1;}
    Item item(){return material.item;}
    Block block(){return material.block;}
    Material material(){return material;}
    /** The aimed support face and planned state, or null for the legacy above-stone default-state placement. */
    Aim aim(){return aim;}
    /** The exact state the native commit must write. */
    net.minecraft.world.level.block.state.BlockState planned(){return aim==null?material.block.defaultBlockState():aim.state();}
    PhysicalCraftFrame before(){return before;}
    PhysicalCraftFrame after(){return after;}
    boolean beforeMatches(ServerPlayer actor){return before.matches(PhysicalCraftFrame.capture(actor));}
    boolean afterMatches(ServerPlayer actor){return after.matches(PhysicalCraftFrame.capture(actor));}
}
