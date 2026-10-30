package dev.minecraftai.engineer;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Event-time transaction gate. Registration is server-internal; unregistered vanilla pistons are unchanged. */
public final class PistonMotionGate {
    private sealed interface Entry permits Ticket,Blocked,Idle { }
    private record Blocked(ShutterJournal.Scope scope,UUID motion) implements Entry { }
    private record Idle(ShutterDesign.Binding binding) implements Entry { }
    private static final Map<ServerLevel,Map<BlockPos,Entry>> LEVELS=Collections.synchronizedMap(new IdentityHashMap<>());
    enum Phase { ARMED,CHECKING,COMMITTED,SETTLED,REJECTED }
    static final class Ticket implements Entry {
        private final UUID id=UUID.randomUUID();
        private final ServerLevel level;
        private final ShutterMotion.Snapshot snapshot;
        private final Set<BlockPos> writes;
        private final Runnable authority;
        private final Consumer<UUID> writeAhead;
        private final ShutterDesign.Binding binding;
        private final ShutterJournal.Scope scope;
        private final long expires;
        private Phase phase=Phase.ARMED;
        private boolean cancelled;
        private String reason="";
        private Ticket(ServerLevel level,ShutterMotion.Snapshot snapshot,Set<BlockPos> writes,Runnable authority,Consumer<UUID> writeAhead){
            this(level,snapshot,writes,authority,writeAhead,null);
        }
        private Ticket(ServerLevel level,ShutterMotion.Snapshot snapshot,Set<BlockPos> writes,Runnable authority,Consumer<UUID> writeAhead,ShutterJournal.Scope scope){
            this.level=level;this.snapshot=snapshot;this.writes=Set.copyOf(writes);this.authority=authority;this.writeAhead=writeAhead;this.scope=scope;this.binding=scope==null?null:new ShutterDesign.Binding(scope.worldId(),scope.dimension(),scope.project(),scope.blueprint(),scope.revision(),scope.piston(),scope.facing());expires=level.getGameTime()+20;
        }
        UUID id(){return id;}
        Phase phase(){return phase;}
        boolean cancelled(){return cancelled;}
        String reason(){return reason;}
    }
    private PistonMotionGate() { }
    static Ticket arm(ServerLevel level,ShutterMotion.Snapshot snapshot,Set<BlockPos> writes,Runnable authority,Consumer<UUID> writeAhead){
        thread(level);Objects.requireNonNull(authority);Objects.requireNonNull(writeAhead);
        authority.run();snapshot.requireFresh(level,writes);
        var slots=LEVELS.computeIfAbsent(level,ignored->new HashMap<>());
        var prior=slots.get(snapshot.base());
        if(prior instanceof Idle)throw new IllegalStateException("Accepted mechanical design is guarded; explicit managed actuation is required");
        if(prior instanceof Blocked)throw new IllegalStateException("Restored motion is blocked; reconcile its journal with fresh authority");
        if(prior instanceof Ticket active && (active.phase==Phase.ARMED || active.phase==Phase.CHECKING || active.phase==Phase.COMMITTED))throw new IllegalStateException("A piston transaction is still pending; reconcile before rearming");
        if(prior==null && slots.size()>=64)throw new IllegalStateException("Too many managed piston transactions");
        var ticket=new Ticket(level,snapshot,snapshot.writes(),authority,writeAhead);slots.put(snapshot.base(),ticket);return ticket;
    }
    /** Restore only a denial marker. The supplied scope must come from the current accepted project. */
    static void restoreBlocked(ServerLevel level,ShutterJournal journal,ShutterJournal.Scope currentScope){
        thread(level);journal.requireScope(level,currentScope);
        var slots=LEVELS.computeIfAbsent(level,ignored->new HashMap<>());var prior=slots.get(journal.base());
        var blocked=new Blocked(currentScope,journal.motion());
        if(prior!=null){if(prior.equals(blocked))return;if(!(prior instanceof Idle idle && matches(idle.binding,currentScope)))throw new IllegalStateException("A different transaction already guards this piston");}
        if(prior==null && slots.size()>=64)throw new IllegalStateException("Too many managed piston transactions");
        slots.put(journal.base(),blocked);
    }
    /** Accepted designs remain denial-only even after an old interrupted journal is reconciled. */
    static void guard(ServerLevel level,ShutterDesign.Design design){
        thread(level);var binding=design.binding();
        if(!binding.dimension().equals(level.dimension().identifier().toString()))throw new IllegalStateException("Guarded design belongs to another dimension");
        var slots=LEVELS.computeIfAbsent(level,ignored->new HashMap<>());var expected=new Idle(binding);var prior=slots.get(binding.piston());
        if(prior!=null){if(prior.equals(expected))return;throw new IllegalStateException("Another motion still guards this accepted design");}
        if(slots.size()>=64)throw new IllegalStateException("Too many managed piston transactions");slots.put(binding.piston(),expected);
    }
    /** Fresh managed actuation is distinct from observing/reconciling an old journal. */
    static Ticket armGuarded(ServerLevel level,ShutterDesign.Design design,ShutterJournal.Scope scope,ShutterMotion.Snapshot snapshot,Set<BlockPos> writes,Runnable authority,Consumer<UUID> writeAhead){
        thread(level);Objects.requireNonNull(authority);Objects.requireNonNull(writeAhead);
        if(!design.motionScope(scope.operation()).equals(scope))throw new IllegalStateException("Actuation operation differs from the accepted design scope");
        var slots=LEVELS.get(level);var expected=new Idle(design.binding());
        if(slots==null || !expected.equals(slots.get(design.piston())))throw new IllegalStateException("Accepted design is not idle; reconcile pending motion first");
        authority.run();
        if(!snapshot.base().equals(design.piston()) || snapshot.before().get(snapshot.base()).getValue(BlockStateProperties.FACING)!=design.facing())throw new IllegalStateException("Motion snapshot differs from accepted design frame");
        snapshot.requireFresh(level,writes);authority.run();
        if(LEVELS.get(level)!=slots || !expected.equals(slots.get(design.piston())))throw new IllegalStateException("Accepted guard changed while validating actuation");
        var ticket=new Ticket(level,snapshot,snapshot.writes(),authority,writeAhead,scope);slots.put(design.piston(),ticket);return ticket;
    }
    /** Stop/settle returns ownership to denial, including after commit; it never reverses physics. */
    static void hold(Ticket ticket,ShutterJournal journal,ShutterJournal.Scope scope,String reason){
        current(ticket);journal.requireScope(ticket.level,scope);
        if(ticket.binding==null || !scope.equals(ticket.scope) || !matches(ticket.binding,scope) || !journal.motion().equals(ticket.id) || !journal.matchesSnapshot(ticket.snapshot))throw new IllegalStateException("Pending denial differs from the current owned motion");
        if(ticket.phase==Phase.CHECKING)throw new IllegalStateException("Cancel event checking before replacing its guard");
        cancel(ticket,reason);LEVELS.get(ticket.level).put(ticket.snapshot.base(),new Blocked(scope,journal.motion()));
    }
    /** Archives observation before moving from pending denial to idle denial. Never issues a ticket. */
    static ShutterJournal.PositionState reconcileBlocked(ServerLevel level,ShutterJournal journal,ShutterJournal.Scope scope,ShutterDesign.Design design,Runnable authority,Consumer<ShutterJournal.PositionState> writeAhead){
        thread(level);journal.requireScope(level,scope);
        if(!design.motionScope(scope.operation()).equals(scope))throw new IllegalStateException("Reconciliation design differs from accepted motion");
        var slots=LEVELS.get(level);var expected=new Blocked(scope,journal.motion());
        if(slots==null || !expected.equals(slots.get(journal.base())))throw new IllegalStateException("Pending motion binding changed");
        authority.run();var observed=reconciledPose(level,journal,scope,design);writeAhead.accept(observed);
        authority.run();
        if(LEVELS.get(level)!=slots || !expected.equals(slots.get(journal.base())) || reconciledPose(level,journal,scope,design)!=observed)throw new IllegalStateException("Motion changed while recording reconciliation");
        slots.put(journal.base(),new Idle(design.binding()));return observed;
    }
    private static ShutterJournal.PositionState reconciledPose(ServerLevel level,ShutterJournal journal,ShutterJournal.Scope scope,ShutterDesign.Design design){
        var observed=journal.positionState(level,scope);
        if(observed!=ShutterJournal.PositionState.BEFORE && observed!=ShutterJournal.PositionState.TERMINAL)throw new IllegalStateException("Motion is unloaded, unsettled or conflicted; preserve and reconcile it first");
        if(!level.hasChunkAt(design.input()))throw new IllegalStateException("Reconciliation input is unloaded");
        var input=level.getBlockState(design.input());
        if(!input.is(net.minecraft.world.level.block.Blocks.LEVER) || input.getValue(BlockStateProperties.POWERED)!=level.getBlockState(design.piston()).getValue(BlockStateProperties.EXTENDED))throw new IllegalStateException("Restored input and observed piston pose disagree; no physical reconciliation was recorded");
        return observed;
    }
    private static boolean matches(ShutterDesign.Binding binding,ShutterJournal.Scope scope){return binding.world().equals(scope.worldId()) && binding.dimension().equals(scope.dimension()) && binding.project().equals(scope.project()) && binding.blueprint().equals(scope.blueprint()) && binding.revision()==scope.revision() && binding.piston().equals(scope.piston()) && binding.facing()==scope.facing() && scope.component().equals("root");}
    /** Explicit reconciliation creates a new ticket; no actor, callback or permit is deserialized. */
    static Ticket rearmBlocked(ServerLevel level,ShutterJournal journal,ShutterJournal.Scope currentScope,ShutterMotion.Snapshot snapshot,Set<BlockPos> writes,Runnable authority,Consumer<UUID> writeAhead){
        thread(level);Objects.requireNonNull(authority);Objects.requireNonNull(writeAhead);journal.requireScope(level,currentScope);
        var slots=LEVELS.get(level);var expected=new Blocked(currentScope,journal.motion());
        if(slots==null || !expected.equals(slots.get(journal.base())))throw new IllegalStateException("The restored motion binding changed");
        authority.run();var observed=journal.positionState(level,currentScope);
        if(observed!=ShutterJournal.PositionState.BEFORE && observed!=ShutterJournal.PositionState.TERMINAL)throw new IllegalStateException("Interrupted motion is unloaded, unsettled or conflicted; preserve and reconcile it first");
        if(!snapshot.base().equals(currentScope.piston()) || snapshot.before().get(snapshot.base()).getValue(BlockStateProperties.FACING)!=currentScope.facing())throw new IllegalStateException("Fresh motion belongs to a different accepted frame");
        snapshot.requireFresh(level,writes);authority.run();
        if(LEVELS.get(level)!=slots || !expected.equals(slots.get(journal.base())))throw new IllegalStateException("Restored motion changed during reconciliation");
        var ticket=new Ticket(level,snapshot,snapshot.writes(),authority,writeAhead,currentScope);slots.put(snapshot.base(),ticket);return ticket;
    }
    /** Invoked at triggerEvent HEAD, before vanilla removes a head or moves any payload. */
    public static boolean allowEvent(BlockState state,Level level,BlockPos pos,int event,int directionData,BooleanSupplier powered){
        if(!(level instanceof ServerLevel server))return true;
        var slots=LEVELS.get(server);if(slots==null)return true;
        if(!server.getServer().isSameThread())return false;
        var entry=slots.get(pos);if(entry==null)return true;
        if(!(entry instanceof Ticket ticket))return false;
        if(ticket.phase!=Phase.ARMED)return false; // No duplicate or compensating movement under an old token.
        try{
            if(ticket.cancelled || server.getGameTime()>ticket.expires)throw new IllegalStateException("Piston transaction cancelled or expired");
            var expected=ticket.snapshot.before().get(ticket.snapshot.base());
            int expectedEvent=ticket.snapshot.closing()?0:1;
            if(event!=expectedEvent || directionData!=expected.getValue(BlockStateProperties.FACING).get3DDataValue() || !state.equals(expected))throw new IllegalStateException("Queued piston event no longer matches the captured intent");
            ticket.phase=Phase.CHECKING;
            validate(ticket,powered);
            ticket.writeAhead.accept(ticket.id); // Caller must durably record the pending commit before returning.
            validate(ticket,powered); // A callback cannot silently change authority, signal or geometry while recording.
            ticket.phase=Phase.COMMITTED;
            return true;
        }catch(RuntimeException rejected){
            ticket.phase=Phase.REJECTED;ticket.reason=rejected.getMessage()==null?"Piston transaction rejected":rejected.getMessage();return false;
        }
    }
    private static void validate(Ticket ticket,BooleanSupplier powered){
        if(ticket.phase!=Phase.CHECKING || ticket.cancelled)throw new IllegalStateException("Piston transaction was cancelled while checking");
        ticket.authority.run();
        if(ticket.phase!=Phase.CHECKING || ticket.cancelled)throw new IllegalStateException("Piston transaction changed while validating authority");
        ticket.snapshot.requireFresh(ticket.level,ticket.writes);
        if(powered.getAsBoolean()!=ticket.snapshot.closing())throw new IllegalStateException("Piston input changed before the queued event committed");
    }
    /** Cancels new motion; already committed vanilla animation is observed, never restarted as a new lease. */
    static void cancel(Ticket ticket,String reason){
        current(ticket);ticket.cancelled=true;ticket.reason=Objects.requireNonNull(reason);
        if(ticket.phase==Phase.ARMED || ticket.phase==Phase.CHECKING)ticket.phase=Phase.REJECTED;
    }
    static boolean observeTerminal(Ticket ticket){
        current(ticket);
        if(ticket.phase==Phase.COMMITTED && ticket.snapshot.matchesTerminalState(ticket.level))ticket.phase=Phase.SETTLED;
        return ticket.phase==Phase.SETTLED;
    }
    private static void current(Ticket ticket){
        thread(ticket.level);var slots=LEVELS.get(ticket.level);
        if(slots==null || slots.get(ticket.snapshot.base())!=ticket)throw new IllegalStateException("Piston transaction is no longer current");
    }
    private static void thread(ServerLevel level){if(!level.getServer().isSameThread())throw new IllegalStateException("Piston transactions require the logical server thread");}
    static void close(MinecraftServer server){synchronized(LEVELS){LEVELS.keySet().removeIf(level->level.getServer()==server);}}
}
