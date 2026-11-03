package dev.minecraftai.engineer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Native port feasibility. These fixtures never grant a project a connected-circuit pass. */
public final class ConnectedGameTests {
    @GameTest public void connectedTraceKeepsExplicitNullThroughDiskAndHttp(GameTestHelper helper){
        var trace=new ConnectedTrace("a".repeat(64),false,false,0).json();
        var document=new JsonObject();document.add("trace",trace);
        var manager=EngineerMod.managerFor(helper.getLevel().getServer());
        var file=manager.directory.resolve("connected-codec-"+java.util.UUID.randomUUID()+".json");
        LocalFiles.write(file,document);
        try{
            var disk=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(file)).getAsJsonObject().getAsJsonObject("trace");
            var http=com.google.gson.JsonParser.parseString(Companion.encodeBody("/v2/checkpoint",document)).getAsJsonObject().getAsJsonObject("trace");
            helper.assertTrue(disk.equals(trace) && http.equals(trace),"Actual disk/HTTP serializers retain all strict trace fields, including explicit null mismatch");
            ConnectedTrace.read(disk,"a".repeat(64),false,false);ConnectedTrace.read(http,"a".repeat(64),false,false);
        }catch(java.io.IOException failed){throw new IllegalStateException(failed);}
        helper.succeed();
    }
    private static final BlockPos CENTER=new BlockPos(24,2,24);
    private static final int WINDOW=24;
    private static final boolean[][] CONTROLS={{false,false},{false,true},{true,true},{true,false},{false,false}};
    private enum Variant { CONNECTED, GAP, REVERSED_CONSUMER }
    private record Circuit(GameTestHelper helper,Rotation rotation,int lane,Variant variant) {
        BlockPos pos(int x,int z){return CENTER.offset(new BlockPos(x,0,z+lane).rotate(rotation));}
        void block(int x,int z,String state){helper.setBlock(pos(x,z),Blueprint.state(state).rotate(rotation));}
        net.minecraft.world.level.block.state.BlockState state(int x,int z){return helper.getLevel().getBlockState(helper.absolutePos(pos(x,z)));}
        int wire(int x){
            var state=state(x,0);
            if(variant==Variant.GAP && x==3){helper.assertTrue(state.isAir(),"Disconnected input pin remains air");return -1;}
            helper.assertTrue(state.is(Blocks.REDSTONE_WIRE),"Signal pin is actual wire, not a local lever");
            return state.getValue(BlockStateProperties.POWER);
        }
        boolean lever(int x,int z){var state=state(x,z);helper.assertTrue(state.is(Blocks.LEVER),"Only external controls are levers");return state.getValue(BlockStateProperties.POWERED);}
        void drive(int x,int z,boolean value){if(lever(x,z)!=value)helper.pullLever(pos(x,z));}
        void build(){
            for(int x=0;x<=6;x++)helper.setBlock(pos(x,0).below(),Blocks.STONE.defaultBlockState());
            helper.setBlock(pos(3,3).below(),Blocks.STONE.defaultBlockState());
            block(0,0,"minecraft:lever[face=floor,facing=east,powered=false]");
            block(3,3,"minecraft:lever[face=floor,facing=east,powered=false]");
            block(1,0,"minecraft:repeater[facing=west,delay=1,locked=false,powered=false]");
            block(2,0,"minecraft:redstone_wire[east=side,west=side]");
            if(variant!=Variant.GAP)block(3,0,"minecraft:redstone_wire[east=side,west=side]");
            block(4,0,variant==Variant.REVERSED_CONSUMER
                ?"minecraft:repeater[facing=east,delay=1,locked=false,powered=false]"
                :"minecraft:repeater[facing=west,delay=1,locked=false,powered=false]");
            block(5,0,"minecraft:redstone_wire[east=side,west=side]");
            block(6,0,"minecraft:redstone_lamp[lit=false]");
        }
        JsonObject sample(int phase,int elapsed,long started){
            long tick=helper.getLevel().getGameTime();
            helper.assertTrue(tick-started==elapsed,"Signal observations use consecutive actual game ticks");
            boolean requested=CONTROLS[phase][0],noise=CONTROLS[phase][1];
            helper.assertTrue(lever(0,0)==requested && lever(3,3)==noise,"External controls match the requested physical levers");
            int producer=wire(2),consumer=wire(3),receiver=wire(5);
            boolean lamp=state(6,0).getValue(BlockStateProperties.LIT);
            boolean producerPowered=state(1,0).getValue(BlockStateProperties.POWERED);
            boolean consumerPowered=state(4,0).getValue(BlockStateProperties.POWERED);
            if(elapsed>=12){
                boolean transmitted=requested && variant==Variant.CONNECTED;
                helper.assertTrue(producer==(requested?15:0),"Measure the producer output, not just a receiver lamp");
                if(variant!=Variant.GAP)helper.assertTrue(consumer==(requested?14:0),"The consumer input is driven by the physical route");
                helper.assertTrue(receiver==(transmitted?15:0) && lamp==transmitted,"A missing link or wrong input direction cannot pass connectivity");
                helper.assertTrue(producerPowered==requested && consumerPowered==transmitted,"Both native component states match the complete transfer");
            }
            // Toggling the unwired control must not cause even a transient at any pin.
            if(phase==1 || phase==3){
                boolean transmitted=requested && variant==Variant.CONNECTED;
                helper.assertTrue(producer==(requested?15:0) && receiver==(transmitted?15:0) && lamp==transmitted,"Unwired control changes do not couple into another signal network");
                if(variant!=Variant.GAP)helper.assertTrue(consumer==(requested?14:0),"Unwired control cannot disturb the consumer input");
            }
            var row=new JsonObject();row.addProperty("variant",variant.name());row.addProperty("phase",phase);
            row.addProperty("gameTick",tick);row.addProperty("elapsedTicks",elapsed);
            row.addProperty("externalInput",requested);row.addProperty("unwiredControl",noise);
            row.addProperty("producerOutput",producer);row.addProperty("consumerInput",consumer);
            row.addProperty("consumerOutput",receiver);row.addProperty("lamp",lamp);
            row.addProperty("producerPowered",producerPowered);row.addProperty("consumerPowered",consumerPowered);
            return row;
        }
    }
    @GameTest(structure="engineer:analog_lab",maxTicks=160)
    public void drivenPortsEast(GameTestHelper helper){probe(helper,Rotation.NONE);}
    @GameTest(structure="engineer:analog_lab",maxTicks=160)
    public void drivenPortsSouth(GameTestHelper helper){probe(helper,Rotation.CLOCKWISE_90);}
    @GameTest(structure="engineer:analog_lab",maxTicks=160)
    public void drivenPortsWest(GameTestHelper helper){probe(helper,Rotation.CLOCKWISE_180);}
    @GameTest(structure="engineer:analog_lab",maxTicks=160)
    public void drivenPortsNorth(GameTestHelper helper){probe(helper,Rotation.COUNTERCLOCKWISE_90);}

    private static void probe(GameTestHelper helper,Rotation rotation){
        var circuits=new Circuit[]{new Circuit(helper,rotation,-12,Variant.CONNECTED),
            new Circuit(helper,rotation,0,Variant.GAP),new Circuit(helper,rotation,12,Variant.REVERSED_CONSUMER)};
        for(var circuit:circuits)circuit.build();
        var samples=new JsonArray();long[] started={0};
        for(int phase=0;phase<CONTROLS.length;phase++){
            int index=phase,base=8+phase*(WINDOW+1);
            helper.runAtTickTime(base,()->{
                for(var circuit:circuits){circuit.drive(0,0,CONTROLS[index][0]);circuit.drive(3,3,CONTROLS[index][1]);}
                started[0]=helper.getLevel().getGameTime();
            });
            for(int elapsed=1;elapsed<=WINDOW;elapsed++){
                int offset=elapsed;
                helper.runAtTickTime(base+elapsed,()->{for(var circuit:circuits)samples.add(circuit.sample(index,offset,started[0]));});
            }
        }
        helper.runAtTickTime(8+CONTROLS.length*(WINDOW+1),()->{
            for(var circuit:circuits)helper.assertTrue(!circuit.lever(0,0) && !circuit.lever(3,3),"Restore the two original external controls; no consumer lever exists");
            helper.assertTrue(samples.size()==CONTROLS.length*WINDOW*circuits.length,"Every quiet/edge window has complete pin observations");
            var report=new JsonObject();report.addProperty("schemaVersion",1);report.addProperty("rotation",rotation.name());
            report.addProperty("authority","native fixture only; not a project pass or graph admission");report.add("samples",samples);
            System.out.println("ENGINEER_CONNECTION_PROBE "+report);helper.succeed();
        });
    }
}
