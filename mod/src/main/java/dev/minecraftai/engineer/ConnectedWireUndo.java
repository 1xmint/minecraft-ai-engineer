package dev.minecraftai.engineer;

import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Read-only native projection for canonically owned connected wires. */
final class ConnectedWireUndo {
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
        if(owned==null || !Blueprint.matches(owned.state(),change.after()))return false;
        // A just-written wire may retain its exact recorded arms until a later
        // neighbor update. Accept that finite state as well as native evolution.
        if(Blueprint.matches(change.after(),actual))return true;
        var predicted=((WireConnectionView)Blocks.REDSTONE_WIRE).engineer$connections(level,change.after(),change.pos());
        return Blueprint.matches(predicted,actual);
    }
}
