package dev.minecraftai.engineer;

import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** Ephemeral native-call lifetime only; never a crafting or world-write grant. */
public final class PhysicalCraftGuard {
    private static final class Run {boolean running=true,inClick,stopped;Runnable removal;}
    private static final Map<ServerPlayer,Run> RUNS=Collections.synchronizedMap(new IdentityHashMap<>());
    private PhysicalCraftGuard() { }
    private static void thread(ServerPlayer actor){if(!actor.level().getServer().isSameThread())throw new IllegalStateException("Native craft lifetime needs the server thread");}
    static void begin(ServerPlayer actor){thread(actor);if(!PhysicalActionGate.exactConnection(actor) || RUNS.putIfAbsent(actor,new Run())!=null)throw new IllegalStateException("Worker already has crafting or resource recovery in progress");}
    static boolean active(ServerPlayer actor){thread(actor);return RUNS.containsKey(actor);}
    static void requireRunning(ServerPlayer actor){thread(actor);var run=RUNS.get(actor);if(run==null || !run.running || run.stopped)throw new IllegalStateException("Native crafting was stopped; preserve its resources");}
    static void click(ServerPlayer actor,Runnable nativeCall){
        requireRunning(actor);var run=RUNS.get(actor);if(run.inClick)throw new IllegalStateException("Reentrant native crafting click refused");
        run.inClick=true;try{nativeCall.run();}finally{run.inClick=false;}
    }
    public static boolean deferSave(Player player){
        if(!(player instanceof ServerPlayer actor) || !PhysicalActionGate.exactActor(actor))return false;
        thread(actor);var run=RUNS.get(actor);return run!=null && run.inClick;
    }
    static boolean requestStop(ServerPlayer actor){
        thread(actor);var run=RUNS.get(actor);if(run==null)return false;
        run.stopped=true;PhysicalActionGate.cancel(actor);return true;
    }
    static boolean deferRemoval(ServerPlayer actor,Runnable removal){
        thread(actor);var run=RUNS.get(actor);if(run==null)return false;
        run.stopped=true;run.removal=Objects.requireNonNull(removal);PhysicalActionGate.cancel(actor);
        if(!run.running)drain(actor,run);return true;
    }
    public static boolean deferNativeRemoval(ServerPlayer actor,Runnable removal){
        if(!PhysicalActionGate.exactActor(actor))return false;
        return deferRemoval(actor,removal);
    }
    static void finish(ServerPlayer actor){
        thread(actor);var run=RUNS.get(actor);if(run==null)return;
        if(run.inClick)throw new IllegalStateException("Do not finish crafting while native click is mutating");
        run.running=false;drain(actor,run);
    }
    private static void drain(ServerPlayer actor,Run run){
        // Failed verification keeps the actor and pending removal frozen. A
        // later explicit dismissal may retry persistence, never the recipe.
        if(run.removal!=null)PhysicalWorkerStorage.saveResources(actor);
        RUNS.remove(actor,run);if(run.removal!=null)run.removal.run();
    }
}
