package dev.minecraftai.engineer;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;

/** Native player-data recovery. Never material creation or restored authority. */
final class PhysicalWorkerStorage {
    private PhysicalWorkerStorage() { }
    static void restore(ServerPlayer worker){
        var server=worker.level().getServer();
        if(!server.isSameThread() || worker.connection!=null)throw new IllegalStateException("Load worker data before joining on the server thread");
        var identity=worker.getUUID();
        var tag=server.getPlayerList().loadPlayerData(worker.nameAndId()).orElseThrow(()->new IllegalStateException("No saved worker data; resources require explicit recovery"));
        var problems=new ProblemReporter.Collector();var input=TagValueInput.create(problems,server.registryAccess(),tag);
        var inventory=decodeInventory(server.registryAccess(),identity,input,problems);var menu=PhysicalWorkerMenus.read(tag,worker,false);worker.load(input);
        if(!problems.isEmpty() || !worker.getUUID().equals(identity))throw new IllegalStateException("Worker data did not restore cleanly with its bound identity");
        PhysicalWorkerMenus.stage(worker,menu,inventory);
    }
    static void save(ServerPlayer worker){
        var server=worker.level().getServer();
        if(!server.isSameThread() || server.getPlayerList().getPlayer(worker.getUUID())!=worker || worker.isCreative() || !PhysicalActionGate.exactActor(worker))throw new IllegalStateException("Save requires the current bound native survival worker");
        if(PhysicalWorkerMenus.pending(worker))throw new IllegalStateException("Finish worker hydration before certifying a save");
        PhysicalWorkerMenus.capture(worker);
        server.getPlayerList().saveAll();
        // Constructing even an unjoined ServerPlayer rebinds the UUID's cached
        // advancement handler. Decode inventory data alone, with no player clone.
        if(!savedInventory(worker,worker.nameAndId()).equals(inventory(worker)))throw new IllegalStateException("Saved worker inventory differs; stop for explicit recovery");
        PhysicalWorkerMenus.verifySaved(worker);
    }
    static com.google.gson.JsonArray savedInventory(ServerPlayer reader,net.minecraft.server.players.NameAndId identity){
        var server=reader.level().getServer();
        if(!server.isSameThread())throw new IllegalStateException("Inspect native inventory on the server thread");
        var tag=server.getPlayerList().loadPlayerData(identity).orElseThrow(()->new IllegalStateException("No saved worker data; resources require explicit recovery"));
        var problems=new ProblemReporter.Collector();var input=TagValueInput.create(problems,server.registryAccess(),tag);
        return decodeInventory(server.registryAccess(),identity.id(),input,problems);
    }
    static com.google.gson.JsonArray decodeInventory(net.minecraft.core.HolderLookup.Provider registries,java.util.UUID identity,net.minecraft.world.level.storage.ValueInput input,ProblemReporter.Collector problems){
        if(!input.read("UUID",net.minecraft.core.UUIDUtil.CODEC).orElseThrow(()->new IllegalStateException("Saved worker identity is missing")).equals(identity))throw new IllegalStateException("Saved worker identity differs");
        var equipment=input.read("equipment",net.minecraft.world.entity.EntityEquipment.CODEC).orElseGet(net.minecraft.world.entity.EntityEquipment::new);
        var slots=new net.minecraft.world.item.ItemStack[net.minecraft.world.entity.player.Inventory.INVENTORY_SIZE+net.minecraft.world.entity.player.Inventory.EQUIPMENT_SLOT_MAPPING.size()];
        java.util.Arrays.fill(slots,net.minecraft.world.item.ItemStack.EMPTY);var seen=new java.util.HashSet<Integer>();
        for(var saved:input.list("Inventory",net.minecraft.world.ItemStackWithSlot.CODEC).orElseThrow(()->new IllegalStateException("Saved worker inventory is missing or malformed"))){
            if(!saved.isValidInContainer(net.minecraft.world.entity.player.Inventory.INVENTORY_SIZE) || !seen.add(saved.slot()))throw new IllegalStateException("Saved inventory slot is invalid or duplicated");
            slots[saved.slot()]=saved.stack();
        }
        for(var entry:net.minecraft.world.entity.player.Inventory.EQUIPMENT_SLOT_MAPPING.int2ObjectEntrySet())slots[entry.getIntKey()]=equipment.get(entry.getValue());
        if(!problems.isEmpty())throw new IllegalStateException("Saved worker inventory did not decode cleanly");
        var operations=registries.createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);var result=new com.google.gson.JsonArray();
        for(var stack:slots)result.add(net.minecraft.world.item.ItemStack.OPTIONAL_CODEC.encodeStart(operations,stack).getOrThrow());
        return result;
    }
    static com.google.gson.JsonArray inventory(ServerPlayer worker){
        var result=new com.google.gson.JsonArray();
        var operations=worker.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
        for(int slot=0;slot<worker.getInventory().getContainerSize();slot++){
            result.add(net.minecraft.world.item.ItemStack.OPTIONAL_CODEC.encodeStart(operations,worker.getInventory().getItem(slot)).getOrThrow());
        }
        return result;
    }
}
