package dev.minecraftai.engineer;

import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Called only after durable undo intent, for a canonically owned connected wire. */
final class ConnectedWireUndo {
    private ConnectedWireUndo() { }
    static boolean matches(Blueprint operation,EngineeringSession.Change change,BlockState actual,BlockGetter level){
        if(!ConnectedIntention.describes(operation.source()) || !actual.is(Blocks.REDSTONE_WIRE) || !change.after().is(Blocks.REDSTONE_WIRE))return false;
        var intention=ConnectedIntention.validated(operation);
        var owned=intention.blueprint().blocks().stream().filter(block->block.pos().equals(change.pos())).findFirst().orElse(null);
        if(owned==null || !Blueprint.matches(owned.state(),change.after()))return false;
        var predicted=((WireConnectionView)Blocks.REDSTONE_WIRE).engineer$connections(level,change.after(),change.pos());
        return Blueprint.matches(predicted,actual);
    }
}
