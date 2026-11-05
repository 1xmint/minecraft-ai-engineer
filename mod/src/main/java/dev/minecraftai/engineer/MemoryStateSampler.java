package dev.minecraftai.engineer;

import com.google.gson.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Read-only, tick-indexed observations for the native toggle-memory feasibility trace. */
final class MemoryStateSampler {
    private static final int PHASES=6,SAMPLES_PER_PHASE=16,TICKS_PER_PHASE=24,FIRST_SAMPLE=8;
    private static final boolean[] INPUTS={false,true,false,true,false,false};
    private final ServerLevel level;
    private final Blueprint blueprint;
    private final JsonObject source;
    private final long startedTick;
    private final boolean initialBit;
    private final BlockPos lever,bulb,comparator,wire;
    private final Block bulbBlock;
    private final MemoryInputTransition.Frame initial;
    private final JsonArray samples=new JsonArray();
    private long lastTick=-1;
    private int nextSample;
    private boolean complete,failed;

    MemoryStateSampler(ServerLevel level,Blueprint blueprint,long startedTick) {
        this.level=Objects.requireNonNull(level);this.blueprint=Objects.requireNonNull(blueprint);
        if(startedTick<0)throw new IllegalArgumentException("Memory sample clock must be nonnegative");
        if(!level.getServer().isSameThread())throw new IllegalStateException("Memory sampler belongs to the native server thread");
        var intention=ToggleMemoryIntention.validated(blueprint);
        if(!blueprint.dimension().equals(level.dimension().identifier().toString()))throw new IllegalArgumentException("Sampler level differs from canonical memory dimension");
        source=blueprint.source().deepCopy();this.startedTick=startedTick;
        lever=cell(Blocks.LEVER);var bulbCell=blueprint.blocks().stream().filter(cell->cell.state().getBlock() instanceof net.minecraft.world.level.block.CopperBulbBlock).findFirst().orElseThrow();
        bulb=bulbCell.pos().immutable();bulbBlock=bulbCell.state().getBlock();comparator=cell(Blocks.COMPARATOR);wire=cell(Blocks.REDSTONE_WIRE);
        if(blueprint.blocks().size()!=8)throw new IllegalArgumentException("Sampler requires the exact eight-cell canonical memory");
        long now=level.getGameTime();if(now!=startedTick)throw new IllegalArgumentException("Memory sampler must start at the current native game tick");
        initial=readFrame();
        if(initial.lever().getValue(BlockStateProperties.POWERED))throw new IllegalArgumentException("Memory sampler requires the initial unpowered input");
        initialBit=initial.bulb().getValue(BlockStateProperties.LIT);
        verifyGeometryAndSignals(initial,false,initialBit);
        // Re-check the exact canonical definition at construction; no supplied observations are accepted.
        if(!ToggleMemoryIntention.validated(blueprint).source().equals(source))throw new IllegalArgumentException("Memory intention changed during sampler creation");
    }

    /** Read current world state at precisely the next native sampling tick. */
    synchronized void sample() {
        requireUsable();
        try {
            if(complete)throw new IllegalStateException("Memory sampler is complete");
            if(nextSample>=PHASES*SAMPLES_PER_PHASE)throw new IllegalStateException("Memory sampler has already collected its bounded trace");
            requireThreadAndSource();
            int phase=nextSample/SAMPLES_PER_PHASE,within=FIRST_SAMPLE+(nextSample%SAMPLES_PER_PHASE);
            long expectedTick=startedTick+(long)phase*TICKS_PER_PHASE+within;
            long now=level.getGameTime();
            if(now!=expectedTick || now<=lastTick)throw new IllegalStateException("Memory sample tick was skipped, repeated or out of order");
            var frame=readFrame();boolean input=INPUTS[phase];boolean bit=expectedBit(phase,initialBit);
            verifyGeometryAndSignals(frame,input,bit);
            var row=new JsonObject();row.addProperty("gameTick",now);row.addProperty("phase",phase);row.addProperty("elapsedTicks",within);
            row.addProperty("input",frame.lever().getValue(BlockStateProperties.POWERED));row.addProperty("stored",frame.bulb().getValue(BlockStateProperties.LIT));
            row.addProperty("comparatorPowered",level.getBlockState(comparator).getValue(BlockStateProperties.POWERED));
            row.addProperty("signal",level.getBlockState(wire).getValue(BlockStateProperties.POWER));
            samples.add(row);lastTick=now;nextSample++;
        } catch(RuntimeException | Error failure) { failed=true;throw failure; }
    }

    /** Finish only a complete 96-sample trace whose real input and bit returned to their start values. */
    synchronized JsonObject complete() {
        requireUsable();
        try {
            if(complete)throw new IllegalStateException("Memory sampler result was already collected");
            requireThreadAndSource();
            if(nextSample!=PHASES*SAMPLES_PER_PHASE || samples.size()!=96)throw new IllegalStateException("Memory sampler needs all 96 strictly timed native observations");
            long expectedLast=startedTick+5L*TICKS_PER_PHASE+23;
            if(lastTick!=expectedLast || level.getGameTime()<lastTick)throw new IllegalStateException("Memory sampler did not finish on the final phase sample");
            var restored=readFrame();
            if(!initial.matches(restored))throw new IllegalStateException("Original lever and stored bit were not observed restored");
            verifyGeometryAndSignals(restored,false,initialBit);
            var result=new JsonObject();result.addProperty("schemaVersion",1);result.addProperty("kind","memory-state-samples");
            result.add("intention",source.deepCopy());result.addProperty("rotation",rotationName());result.add("initial",initial.json());
            result.addProperty("startedTick",startedTick);result.addProperty("stage","COMPLETE");result.addProperty("sampleCount",samples.size());
            result.add("samples",samples.deepCopy());result.add("restored",restored.json());complete=true;return result;
        } catch(RuntimeException | Error failure) { failed=true;throw failure; }
    }

    private void requireThreadAndSource() {
        if(!level.getServer().isSameThread())throw new IllegalStateException("Memory samples belong to the native server thread");
        if(!source.equals(blueprint.source()) || !ToggleMemoryIntention.validated(blueprint).source().equals(source)
                || !blueprint.dimension().equals(level.dimension().identifier().toString()))throw new IllegalStateException("Memory source or native world changed during sampling");
    }
    private MemoryInputTransition.Frame readFrame() {
        if(!level.hasChunkAt(lever) || !level.hasChunkAt(bulb) || !level.hasChunkAt(comparator) || !level.hasChunkAt(wire))
            throw new IllegalStateException("Memory sample cells are unloaded");
        return new MemoryInputTransition.Frame(level.getBlockState(lever),level.getBlockState(bulb));
    }
    private void verifyGeometryAndSignals(MemoryInputTransition.Frame frame,boolean input,boolean bit) {
        for(var cell:blueprint.blocks()) {
            if(!level.hasChunkAt(cell.pos()) || !Blueprint.matches(cell.state(),level.getBlockState(cell.pos())))
                throw new IllegalStateException("Canonical memory geometry changed or unloaded at "+cell.pos().toShortString());
        }
        if(!frame.lever().is(Blocks.LEVER) || frame.lever().getValue(BlockStateProperties.POWERED)!=input
                || !frame.bulb().is(bulbBlock) || frame.bulb().getValue(BlockStateProperties.POWERED)!=input
                || frame.bulb().getValue(BlockStateProperties.LIT)!=bit)throw new IllegalStateException("Actual lever/bulb pair differs from the expected phase");
        var observedComparator=level.getBlockState(comparator);var observedWire=level.getBlockState(wire);
        if(!observedComparator.is(Blocks.COMPARATOR) || observedComparator.getValue(BlockStateProperties.POWERED)!=bit
                || !observedWire.is(Blocks.REDSTONE_WIRE) || observedWire.getValue(BlockStateProperties.POWER)!=(bit?15:0))
            throw new IllegalStateException("Actual comparator or output wire differs from the stored bit");
    }
    private BlockPos cell(net.minecraft.world.level.block.Block block) {
        return blueprint.blocks().stream().filter(cell->cell.state().is(block)).findFirst().orElseThrow().pos().immutable();
    }
    private void requireUsable(){if(failed)throw new IllegalStateException("Memory sampler failed and is terminal");}
    private String rotationName(){return switch(source.get("rotation").getAsInt()){case 0->Rotation.NONE.name();case 90->Rotation.CLOCKWISE_90.name();case 180->Rotation.CLOCKWISE_180.name();case 270->Rotation.COUNTERCLOCKWISE_90.name();default->throw new IllegalStateException("Canonical memory rotation is invalid");};}
    private static boolean expectedBit(int phase,boolean initial) {return phase==1 || phase==2 ? !initial : initial;}
}
