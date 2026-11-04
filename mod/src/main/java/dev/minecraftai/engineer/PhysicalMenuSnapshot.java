package dev.minecraftai.engineer;

import java.util.List;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.ItemStack;

/** Resource data only. The result preview and execution authority are never stored. */
final class PhysicalMenuSnapshot {
    private final List<ItemStack> inputs;
    private final ItemStack cursor;

    PhysicalMenuSnapshot(List<ItemStack> inputs,ItemStack cursor){
        if(inputs.size()!=4)throw new IllegalArgumentException("Worker menu needs exactly four input slots");
        this.inputs=inputs.stream().map(ItemStack::copy).toList();this.cursor=cursor.copy();
    }
    List<ItemStack> inputs(){return inputs.stream().map(ItemStack::copy).toList();}
    ItemStack cursor(){return cursor.copy();}
    boolean empty(){return cursor.isEmpty() && inputs.stream().allMatch(ItemStack::isEmpty);}
    boolean matches(PhysicalMenuSnapshot other){
        for(int index=0;index<4;index++)if(!ItemStack.matches(inputs.get(index),other.inputs.get(index)))return false;
        return ItemStack.matches(cursor,other.cursor);
    }
    CompoundTag encode(HolderLookup.Provider registries){
        var ops=registries.createSerializationContext(NbtOps.INSTANCE);var result=new CompoundTag();result.putInt("schemaVersion",1);
        var grid=new ListTag();for(var stack:inputs)grid.add(ItemStack.OPTIONAL_CODEC.encodeStart(ops,stack).getOrThrow());result.put("inputs",grid);
        result.put("cursor",ItemStack.OPTIONAL_CODEC.encodeStart(ops,cursor).getOrThrow());return result;
    }
    static PhysicalMenuSnapshot decode(CompoundTag record,HolderLookup.Provider registries){
        if(!record.keySet().equals(Set.of("schemaVersion","inputs","cursor")) || !(record.get("schemaVersion") instanceof IntTag) || record.getIntOr("schemaVersion",-1)!=1)
            throw new IllegalStateException("Unsupported or malformed worker menu record");
        var grid=record.getList("inputs").orElseThrow(()->new IllegalStateException("Worker menu inputs are malformed"));
        if(grid.size()!=4)throw new IllegalStateException("Worker menu needs exactly four saved inputs");
        var ops=registries.createSerializationContext(NbtOps.INSTANCE);var inputs=new java.util.ArrayList<ItemStack>();
        for(var value:grid)inputs.add(ItemStack.OPTIONAL_CODEC.parse(ops,value).getOrThrow());
        var cursor=ItemStack.OPTIONAL_CODEC.parse(ops,record.get("cursor")).getOrThrow();
        return new PhysicalMenuSnapshot(inputs,cursor);
    }
}
