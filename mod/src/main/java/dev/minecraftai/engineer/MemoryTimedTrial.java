package dev.minecraftai.engineer;

import com.google.gson.JsonObject;
import java.util.Objects;
import java.util.UUID;

/** Fixture-only owner-authorized timing run; its detached trace is never a functional pass. */
final class MemoryTimedTrial {
    enum State { RUNNING,COMPLETE,INTERRUPTED }
    private final MemoryInputDriver driver;
    private final MemoryStateSampler sampler;
    private final UUID controller;
    private final long startedTick;
    private State state=State.RUNNING;
    private long lastTick;
    private String detail="Timed native observation run is active";
    private JsonObject result;

    private MemoryTimedTrial(MemoryInputDriver driver,UUID controller,long startedTick,MemoryStateSampler sampler) {
        this.driver=driver;this.controller=controller;this.startedTick=startedTick;this.lastTick=startedTick;this.sampler=sampler;
    }

    /** Begin from fresh live authority and the exact same current world/source frame. */
    static MemoryTimedTrial open(EngineeringSession session,UUID controller) {
        Objects.requireNonNull(session);Objects.requireNonNull(controller);
        MemoryInputDriver driver=MemoryInputDriver.open(session,controller);
        try {
            var level=driver.boundLevel();var blueprint=driver.boundBlueprint();long start=level.getGameTime();
            var before=driver.inspect();
            if(!driver.initialFrame().matches(before))throw new IllegalStateException("Timed trial initial pair differs from the fresh driver's captured pair");
            var sampler=new MemoryStateSampler(level,blueprint,start);
            if(!driver.initialFrame().matches(driver.inspect()))throw new IllegalStateException("Memory pair changed while binding the timed sampler");
            return new MemoryTimedTrial(driver,controller,start,sampler);
        } catch(RuntimeException | Error failure) {
            try { driver.stop(); } catch(RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    /** Advance exactly one server tick; duplicates inspect but do not advance, gaps interrupt. */
    synchronized State tick() {
        if(state!=State.RUNNING)return state;
        if(!driver.boundLevel().getServer().isSameThread())throw new IllegalStateException("Timed memory trial belongs to the server thread");
        try {
            long now=driver.boundLevel().getGameTime();
            driver.inspect();
            if(now==lastTick)return state;
            if(now!=lastTick+1)throw new IllegalStateException("Timed memory trial missed a server tick; no catch-up is allowed");
            long elapsed=now-startedTick;
            if(elapsed<0 || elapsed>143)throw new IllegalStateException("Timed memory trial passed its fixed observation window");
            if(elapsed==24 || elapsed==48 || elapsed==72 || elapsed==96)driver.pulse();
            if(state!=State.RUNNING)return state;
            int within=(int)(elapsed%24);
            if(within>=8 && within<24)sampler.sample();
            lastTick=now;
            if(elapsed==143)completeAfterFinalSample();
            return state;
        } catch(RuntimeException | Error failure) {
            interrupt(failure);return state;
        }
    }

    /** Stop and freeze the actual observed transaction without another native pulse. */
    synchronized void stop() {
        if(state!=State.RUNNING)return;
        if(!driver.boundLevel().getServer().isSameThread())throw new IllegalStateException("Timed memory trial belongs to the server thread");
        interrupt(new IllegalStateException("Timed memory trial was stopped"));
    }

    synchronized State state(){return state;}
    synchronized long startedTick(){return startedTick;}
    synchronized String detail(){return detail;}
    synchronized JsonObject data(){
        if(state!=State.COMPLETE || result==null)throw new IllegalStateException("Timed memory observations are unavailable until durable completion");
        return result.deepCopy();
    }

    private void completeAfterFinalSample() {
        var observations=sampler.complete();
        var inputJournal=driver.finishRestored();
        if(!inputJournal.get("stage").getAsString().equals("RESTORED"))throw new IllegalStateException("Restored input journal did not verify");
        var combined=new JsonObject();combined.addProperty("schemaVersion",1);combined.addProperty("kind","memory-timed-trial");
        combined.addProperty("controller",controller.toString());combined.add("observations",observations);combined.add("inputJournal",inputJournal);
        result=combined;state=State.COMPLETE;detail="Timed paired-state observations and restored input journal are durable";
    }

    private void interrupt(Throwable reason) {
        state=State.INTERRUPTED;detail="Timed memory observations interrupted: "+reason.getMessage();
        try { driver.stop(); }
        catch(RuntimeException | Error cleanup) { reason.addSuppressed(cleanup);detail+="; input journal could not be cleanly finalized"; }
        result=null;
    }
}
