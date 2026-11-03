package dev.minecraftai.engineer;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

/** Selects one intended cell; matching preexisting blocks never become owned work. */
record PhysicalBlueprintStep(BlockPos target,Set<BlockPos> reads,Runnable intention) {
    static PhysicalBlueprintStep select(EngineeringSession project) {
        if(project.blueprint==null)throw new IllegalStateException("Preview a blueprint first");
        if(ComponentTree.composite(project.blueprint))throw new IllegalStateException("Physical component scheduling is not available yet");
        var blueprint=project.blueprint;
        for(var block:blueprint.blocks())if(!block.state().is(Blocks.DIRT) && !block.state().is(Blocks.STONE))
            throw new IllegalStateException("This physical blueprint step currently supports only dirt cells with existing stone supports");
        var prior=new java.util.ArrayList<Blueprint.PlannedBlock>();
        var reads=new java.util.HashSet<BlockPos>();
        for(var block:blueprint.blocks()){
            if(!project.contains(block.pos()) || !project.level().hasChunkAt(block.pos()))throw new IllegalStateException("Blueprint must remain loaded inside the workspace");
            var actual=project.level().getBlockState(block.pos());
            if(!actual.isAir() && !Blueprint.matches(block.state(),actual))throw new IllegalStateException("A blueprint cell is occupied or edited; inspect it before physical construction");
            reads.add(block.pos().immutable());
        }
        Blueprint.PlannedBlock next=null;
        for(var block:blueprint.blocks()){
            var actual=project.level().getBlockState(block.pos());
            if(Blueprint.matches(block.state(),actual)){prior.add(block);continue;}
            if(!actual.isAir())throw new IllegalStateException("A blueprint cell is occupied or edited; inspect it before physical construction");
            if(!block.state().is(Blocks.DIRT))throw new IllegalStateException("The next blueprint cell needs stone, which this executor cannot gather/place yet");
            next=block;break;
        }
        if(next==null)throw new IllegalStateException("Blueprint geometry already matches. This does not prove it works");
        var preserved=java.util.List.copyOf(prior);
        return new PhysicalBlueprintStep(next.pos(),Set.copyOf(reads),()->{
            if(project.blueprint!=blueprint)throw new IllegalStateException("Accepted blueprint changed");
            for(var block:preserved)if(!Blueprint.matches(block.state(),project.level().getBlockState(block.pos())))
                throw new IllegalStateException("A preceding blueprint cell changed; reconcile before construction");
        });
    }
}
