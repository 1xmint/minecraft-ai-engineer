package dev.minecraftai.engineer;

import com.google.gson.JsonArray;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueOutput;

/** Native resource persistence only; pending restore never grants an action. */
public final class PhysicalWorkerMenus {
    static final String KEY="engineer_worker_menu";
    private record Pending(UUID identity,PhysicalMenuSnapshot menu,JsonArray inventory) { }
    private static final Map<ServerPlayer,Pending> PENDING=new IdentityHashMap<>();
    private PhysicalWorkerMenus() { }
    private static void thread(ServerPlayer actor){if(!actor.level().getServer().isSameThread())throw new IllegalStateException("Worker menu recovery needs the server thread");}
    static boolean pending(ServerPlayer actor){thread(actor);return PENDING.containsKey(actor);}
    static PhysicalMenuSnapshot capture(ServerPlayer actor){
        thread(actor);if(actor.containerMenu!=actor.inventoryMenu)throw new IllegalStateException("Worker has an unsupported open menu; preserve recovery");
        var cells=new java.util.ArrayList<net.minecraft.world.item.ItemStack>();for(int slot=1;slot<=4;slot++)cells.add(actor.inventoryMenu.getSlot(slot).getItem());
        return new PhysicalMenuSnapshot(cells,actor.inventoryMenu.getCarried());
    }
    static void requireSettled(ServerPlayer actor){
        if(pending(actor) || !capture(actor).empty())throw new IllegalStateException("Worker menu resources need recovery before new work");
    }
    static PhysicalMenuSnapshot read(CompoundTag player,ServerPlayer actor,boolean required){
        thread(actor);
        if(!player.contains(KEY)){
            if(required)throw new IllegalStateException("Saved worker menu receipt is missing");
            return new PhysicalMenuSnapshot(java.util.Collections.nCopies(4,net.minecraft.world.item.ItemStack.EMPTY),net.minecraft.world.item.ItemStack.EMPTY);
        }
        return PhysicalMenuSnapshot.decode(player.getCompound(KEY).orElseThrow(()->new IllegalStateException("Saved worker menu is malformed")),actor.registryAccess());
    }
    static void stage(ServerPlayer actor,PhysicalMenuSnapshot menu,JsonArray inventory){
        thread(actor);
        if(actor.connection!=null || PENDING.containsKey(actor) || !capture(actor).empty() || !actor.inventoryMenu.getSlot(0).getItem().isEmpty()
            || !PhysicalWorkerStorage.inventory(actor).equals(inventory))throw new IllegalStateException("Native worker restore does not match its prevalidated record");
        PENDING.put(actor,new Pending(actor.getUUID(),menu,inventory.deepCopy()));
    }
    static void finishRestore(ServerPlayer actor){
        thread(actor);var saved=PENDING.get(actor);if(saved==null)return;
        if(!PhysicalActionGate.exactConnection(actor) || actor.level().getServer().getPlayerList().getPlayer(actor.getUUID())!=actor
            || !actor.getUUID().equals(saved.identity()) || !PhysicalWorkerStorage.inventory(actor).equals(saved.inventory()) || !capture(actor).empty())
            throw new IllegalStateException("Worker join changed before menu hydration; saved resources retained");
        var connection=actor.connection;var cells=saved.menu().inputs();
        // Each set recalculates the native preview and sends a packet. Never do
        // this on the unjoined player, and never restore the derived result slot.
        for(int slot=1;slot<=4;slot++)actor.inventoryMenu.getSlot(slot).set(cells.get(slot-1));
        actor.inventoryMenu.setCarried(saved.menu().cursor());
        if(actor.connection!=connection || !PhysicalActionGate.exactConnection(actor) || !saved.menu().matches(capture(actor))
            || !PhysicalWorkerStorage.inventory(actor).equals(saved.inventory()))throw new IllegalStateException("Worker hydration did not finish exactly; original snapshot retained");
        PENDING.remove(actor);
    }
    private static PhysicalMenuSnapshot durable(ServerPlayer actor){
        var saved=PENDING.get(actor);if(saved==null)return capture(actor);
        if(!actor.getUUID().equals(saved.identity()) || !PhysicalWorkerStorage.inventory(actor).equals(saved.inventory()))
            throw new IllegalStateException("Pending worker inventory changed; do not overwrite recovery");
        return saved.menu();
    }
    /** Called only by the native player-data writer, in the same record as Inventory. */
    public static void write(ServerPlayer actor,ValueOutput output){
        if(!PhysicalActionGate.exactActor(actor))return;
        thread(actor);output.store(KEY,CompoundTag.CODEC,durable(actor).encode(actor.registryAccess()));
    }
    static void verifySaved(ServerPlayer actor){
        thread(actor);var tag=actor.level().getServer().getPlayerList().loadPlayerData(actor.nameAndId()).orElseThrow(()->new IllegalStateException("Saved worker data is missing"));
        if(!durable(actor).matches(read(tag,actor,true)))throw new IllegalStateException("Saved worker menu differs; preserve recovery");
    }
    static void forget(ServerPlayer actor){
        thread(actor);if(actor.level().getServer().getPlayerList().getPlayer(actor.getUUID())==actor)throw new IllegalStateException("Do not discard pending resources for a live worker");
        var saved=PENDING.get(actor);
        if(saved!=null){
            var tag=actor.level().getServer().getPlayerList().loadPlayerData(actor.nameAndId()).orElseThrow(()->new IllegalStateException("Pending worker recovery file is missing"));
            if(!saved.menu().matches(read(tag,actor,false)) || !PhysicalWorkerStorage.savedInventory(actor,actor.nameAndId()).equals(saved.inventory()))
                throw new IllegalStateException("Pending worker resources were not preserved; retain recovery state");
        }
        PENDING.remove(actor);
    }
}
