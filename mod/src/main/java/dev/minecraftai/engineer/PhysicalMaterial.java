package dev.minecraftai.engineer;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Explicit native gather contracts. No tag, arbitrary loot or crafting permission. */
enum PhysicalMaterial {
    DIRT("dirt",Blocks.DIRT,Items.DIRT,"minecraft:dirt"),OAK_LOG("oak-log",Blocks.OAK_LOG,Items.OAK_LOG,"minecraft:oak_log");
    final String id,itemId;
    final Block block;
    final Item item;
    PhysicalMaterial(String id,Block block,Item item,String itemId){this.id=id;this.block=block;this.item=item;this.itemId=itemId;}
    static PhysicalMaterial source(BlockState state){
        for(var material:values())if(state.is(material.block))return material;
        throw new IllegalStateException("Native gathering supports only dirt or an isolated oak log");
    }
    static PhysicalMaterial named(String id){
        for(var material:values())if(material.id.equals(id))return material;
        throw new IllegalStateException("Unknown native resource material");
    }
}
