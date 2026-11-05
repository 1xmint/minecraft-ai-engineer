package dev.minecraftai.engineer;

import com.google.gson.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Native feasibility for a stored bit. This is not a production memory/register contract. */
public final class ToggleMemoryGameTests {
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=180) public void initiallyOffEast(GameTestHelper h){probe(h,false,Rotation.NONE);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=180) public void initiallyOnEast(GameTestHelper h){probe(h,true,Rotation.NONE);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=180) public void initiallyOffSouth(GameTestHelper h){probe(h,false,Rotation.CLOCKWISE_90);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=180) public void initiallyOnSouth(GameTestHelper h){probe(h,true,Rotation.CLOCKWISE_90);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=180) public void initiallyOffWest(GameTestHelper h){probe(h,false,Rotation.CLOCKWISE_180);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=180) public void initiallyOnWest(GameTestHelper h){probe(h,true,Rotation.CLOCKWISE_180);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=180) public void initiallyOffNorth(GameTestHelper h){probe(h,false,Rotation.COUNTERCLOCKWISE_90);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=180) public void initiallyOnNorth(GameTestHelper h){probe(h,true,Rotation.COUNTERCLOCKWISE_90);}
    private static BlockPos at(BlockPos p,Rotation rotation){return p.rotate(rotation).offset(8,0,8);}
    private static void probe(GameTestHelper h,boolean initial,Rotation rotation){
        var lever=at(new BlockPos(3,2,4),rotation);var bulb=at(new BlockPos(4,2,4),rotation);
        var comparator=at(new BlockPos(5,2,4),rotation);var output=at(new BlockPos(6,2,4),rotation);
        var memory=Blueprint.state("minecraft:waxed_copper_bulb");
        var identity=java.util.UUID.fromString("d0c177a4-75a5-436c-9440-554ad92f9d62");
        int angle=switch(rotation){case NONE->0;case CLOCKWISE_90->90;case CLOCKWISE_180->180;case COUNTERCLOCKWISE_90->270;};
        var intention=ToggleMemoryIntention.create(identity,1,identity,"test","minecraft:overworld",at(new BlockPos(4,2,4),rotation),angle);
        var blueprint=intention.blueprint();
        for(var cell:blueprint.blocks())h.setBlock(cell.pos(),cell.state().is(memory.getBlock())?cell.state().setValue(BlockStateProperties.LIT,initial):cell.state());
        var samples=new JsonArray();long[] previous={-1};
        // The same unpowered input appears with two different stored outputs. Holding
        // a high input or returning it low must never produce a second toggle.
        for(int phase=0;phase<6;phase++){
            int step=phase;boolean input=phase==1 || phase==3;boolean bit=phase==1 || phase==2?!initial:initial;
            int base=8+phase*24;
            if(phase>=1 && phase<=4)h.runAtTickTime(base,()->h.pullLever(lever));
            for(int elapsed=8;elapsed<24;elapsed++){
                int within=elapsed;
                h.runAtTickTime(base+elapsed,()->{
                    long tick=h.getLevel().getGameTime();
                    h.assertTrue(previous[0]<tick,"Memory observations must advance in native game time");previous[0]=tick;
                    for(var cell:blueprint.blocks())h.assertTrue(Blueprint.matches(cell.state(),h.getBlockState(cell.pos())),"Actual settled memory geometry must match its canonical intention");
                    var actualLever=h.getBlockState(lever);var actualBulb=h.getBlockState(bulb);var actualComparator=h.getBlockState(comparator);var actualOutput=h.getBlockState(output);
                    h.assertTrue(actualLever.is(Blocks.LEVER) && actualLever.getValue(BlockStateProperties.POWERED)==input,"Actual input differs from the requested memory phase");
                    h.assertTrue(actualBulb.is(memory.getBlock()) && actualBulb.getValue(BlockStateProperties.POWERED)==input && actualBulb.getValue(BlockStateProperties.LIT)==bit,"A real rising edge toggles once; falling/held input preserves the stored bit");
                    h.assertTrue(actualComparator.is(Blocks.COMPARATOR) && actualComparator.getValue(BlockStateProperties.POWERED)==bit,"Comparator reads the actual stored bulb bit");
                    int strength=actualOutput.getValue(BlockStateProperties.POWER);h.assertTrue(actualOutput.is(Blocks.REDSTONE_WIRE) && strength==(bit?15:0),"Stored bit has the correct independent redstone output");
                    var row=new JsonObject();row.addProperty("phase",step);row.addProperty("elapsedTicks",within);row.addProperty("gameTick",tick);row.addProperty("input",actualLever.getValue(BlockStateProperties.POWERED));row.addProperty("stored",actualBulb.getValue(BlockStateProperties.LIT));row.addProperty("signal",strength);samples.add(row);
                });
            }
        }
        h.runAtTickTime(154,()->{
            h.assertTrue(samples.size()==96 && !h.getBlockState(lever).getValue(BlockStateProperties.POWERED) && h.getBlockState(bulb).getValue(BlockStateProperties.LIT)==initial,"Two native pulses restore the original stored bit and original unpowered input");
            var record=new JsonObject();record.addProperty("kind","toggle-memory-native-feasibility");record.addProperty("rotation",rotation.name());record.addProperty("initial",initial);record.add("samples",samples);
            System.out.println("ENGINEER_TOGGLE_MEMORY_NATIVE "+record);h.succeed();
        });
    }
    @GameTest public void canonicalIntentionRejectsClaimsAndRelabeling(GameTestHelper h){
        var id=java.util.UUID.fromString("d0c177a4-75a5-436c-9440-554ad92f9d62");
        var intended=ToggleMemoryIntention.create(id,1,id,"test","minecraft:overworld",new BlockPos(0,80,0),0);
        var source=intended.source();h.assertTrue(ToggleMemoryIntention.read(source,"test","minecraft:overworld",id).blueprint().blocks().size()==8,"Canonical memory has eight intended cells and no actual pass");
        for(String field:java.util.List.of("owner","permissions","verified")){
            var altered=source.deepCopy();altered.addProperty(field,"passed");boolean refused=false;try{ToggleMemoryIntention.read(altered,"test","minecraft:overworld",id);}catch(RuntimeException expected){refused=true;}h.assertTrue(refused,"Extra authority or verification claim refuses: "+field);
        }
        var altered=source.deepCopy();altered.getAsJsonObject("memory").addProperty("stableSamples",1);boolean refused=false;try{ToggleMemoryIntention.read(altered,"test","minecraft:overworld",id);}catch(RuntimeException expected){refused=true;}h.assertTrue(refused,"Changed memory behavior refuses");
        var copy=intended.source();copy.addProperty("verification","passed");h.assertTrue(intended.source().get("verification").getAsString().equals("untested"),"Returned source cannot mutate canonical intention");
        System.out.println("ENGINEER_TOGGLE_MEMORY_INTENTION "+source);h.succeed();
    }

}
