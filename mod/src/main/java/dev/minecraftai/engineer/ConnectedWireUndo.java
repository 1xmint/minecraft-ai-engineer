package dev.minecraftai.engineer;

import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Read-only native projection for canonically owned connected wires. */
final class ConnectedWireUndo {
    /** Invocation-local indexes. Never reuse across journal changes, ticks or world validations. */
    record Frame(java.util.Map<net.minecraft.core.BlockPos,Blueprint.PlannedBlock> planned,java.util.Map<net.minecraft.core.BlockPos,EngineeringSession.Change> owned){
        static Frame capture(java.util.List<Blueprint.PlannedBlock> cells,java.util.List<EngineeringSession.Change> journal,String operation){
            var planned=new java.util.HashMap<net.minecraft.core.BlockPos,Blueprint.PlannedBlock>();
            for(var cell:cells)if(planned.put(cell.pos(),cell)!=null)throw new IllegalArgumentException("Duplicate connected intent position");
            var owned=new java.util.HashMap<net.minecraft.core.BlockPos,EngineeringSession.Change>();
            for(var change:journal)if(change.operation().equals(operation))owned.put(change.pos(),change);
            return new Frame(java.util.Map.copyOf(planned),java.util.Map.copyOf(owned));
        }
        boolean owns(net.minecraft.core.BlockPos pos,BlockState expected){
            var change=owned.get(pos);return change!=null && Blueprint.matches(change.after(),expected);
        }
        boolean matches(net.minecraft.core.BlockPos pos,BlockState expected,BlockState actual,BlockGetter level){
            var change=owned.get(pos);return change!=null && Blueprint.matches(change.after(),expected) && matchesPlanned(planned.get(pos),change,actual,level);
        }
    }
    private ConnectedWireUndo() { }
    static boolean matches(Blueprint operation,EngineeringSession.Change change,BlockState actual,BlockGetter level){
        if(!ConnectedIntention.describes(operation.source()) || !actual.is(Blocks.REDSTONE_WIRE) || !change.after().is(Blocks.REDSTONE_WIRE))return false;
        var intention=ConnectedIntention.validated(operation);
        return matchesValidated(intention.blueprint(),change,actual,level);
    }
    /** Caller validates the operation once before reconciling its bounded journal. */
    static boolean matchesValidated(Blueprint canonical,EngineeringSession.Change change,BlockState actual,BlockGetter level){
        if(!actual.is(Blocks.REDSTONE_WIRE) || !change.after().is(Blocks.REDSTONE_WIRE))return false;
        var owned=canonical.blocks().stream().filter(block->block.pos().equals(change.pos())).findFirst().orElse(null);
        return matchesPlanned(owned,change,actual,level);
    }
    private static boolean matchesPlanned(Blueprint.PlannedBlock owned,EngineeringSession.Change change,BlockState actual,BlockGetter level){
        if(!actual.is(Blocks.REDSTONE_WIRE) || !change.after().is(Blocks.REDSTONE_WIRE) || owned==null || !owned.pos().equals(change.pos()) || !Blueprint.matches(owned.state(),change.after()))return false;
        // A just-written wire may retain its exact recorded arms until a later
        // neighbor update. Accept that finite state as well as native evolution.
        if(Blueprint.matches(change.after(),actual))return true;
        var predicted=((WireConnectionView)Blocks.REDSTONE_WIRE).engineer$connections(level,change.after(),change.pos());
        return Blueprint.matches(predicted,actual);
    }
}
