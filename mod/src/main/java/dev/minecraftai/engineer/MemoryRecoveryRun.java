package dev.minecraftai.engineer;

/** Ephemeral explicitly requested recovery. Loading a checkpoint never creates this runner. */
final class MemoryRecoveryRun {
    private final EngineeringSession session;
    private final MemoryRecoveryDriver driver;
    private long nextTick;
    private boolean closed;
    private MemoryRecoveryRun(EngineeringSession session,MemoryRecoveryDriver driver) {
        this.session=session;this.driver=driver;nextTick=session.level().getGameTime()+1;
    }
    static MemoryRecoveryRun open(EngineeringSession session) {
        var driver=MemoryRecoveryDriver.open(session,session.actor());
        return new MemoryRecoveryRun(session,driver);
    }
    void tick() {
        if(!session.server.isSameThread())throw new IllegalStateException("Recovery ticks belong to the server thread");
        if(closed)return;
        try {
            driver.inspect();long now=session.level().getGameTime();if(now<nextTick)return;
            if(driver.remainingEdges()>0){driver.pulse();nextTick=now+4;return;}
            driver.finishRestored();closed=true;session.memoryRecoveryRun=null;
            session.state="PAUSED";session.detail="Original memory input and stored bit restored. Recovery is complete; no functional test was started or passed.";
            session.save();session.checkpoint();session.tell(session.detail);
        } catch(RuntimeException failure) {
            try{stop();}catch(RuntimeException cleanup){failure.addSuppressed(cleanup);}
            session.state="PAUSED";session.detail="Memory recovery paused: "+failure.getMessage()
                    +(session.pendingMemoryInput!=null?". Saved progress remains pending; inspect before explicitly resuming.":". The pending scope was already cleared; inspect the completed restoration.");
            session.save();session.checkpoint();session.tell(session.detail);
        }
    }
    void stop() {
        if(!session.server.isSameThread())throw new IllegalStateException("Recovery stop belongs to the server thread");
        if(closed)return;
        try{driver.stop();}finally{closed=true;if(session.memoryRecoveryRun==this)session.memoryRecoveryRun=null;}
    }
}
