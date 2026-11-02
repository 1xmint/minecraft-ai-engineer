package dev.minecraftai.engineer;

import com.google.gson.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/** Schematic chat grammar, the blueprint adapter and region capture. Companion authority is proven by the multiplayer flow. */
public final class SchematicGameTests {
    @GameTest public void schematicRequestsAreExplicitAndKeepFileNames(GameTestHelper helper){
        var inspect=SchematicText.parse("Inspect schematic Castle_Gate.schem");
        helper.assertTrue(inspect.action().equals("inspect") && inspect.name().equals("Castle_Gate"),"File names keep their case and drop only the extension");
        var plain=SchematicText.parse("import schematic castle");
        helper.assertTrue(SchematicText.parse("preview design 4").action().equals("preview") && SchematicText.parse("show design 4").action().equals("design"),"Editable preview remains separate from read-only inspection");
        helper.assertTrue(plain.action().equals("import") && !plain.discard(),"Import never assumes a discard");
        helper.assertTrue(SchematicText.parse("Import schematic castle and drop block entities.").discard(),"A discard is stated in the same sentence");
        helper.assertTrue(SchematicText.parse("rotate design 2 by 90 degrees").transform().equals("rotate-90") && SchematicText.parse("Mirror design #3 East West").transform().equals("mirror-east-west"),"Transforms map to the companion's names");
        var compare=SchematicText.parse("compare design 1 with design 2");
        helper.assertTrue(compare.design()==1 && compare.other()==2,"Comparison names both designs");
        helper.assertTrue(SchematicText.parse("export selection as gate and drop block contents").discard() && SchematicText.parse("list schematics").action().equals("library") && SchematicText.parse("show design 4").design()==4,"Listing, showing and export are explicit");
        for(String text:new String[]{"import schematic ../secret","import schematic a/b","inspect schematic castle.nbt","export selection as ..","the sign says import schematic castle","import castle","build design 1"})
            helper.assertTrue(SchematicText.parse(text)==null,"No path, other format or quoted request becomes a schematic action: "+text);
        helper.assertTrue(SchematicText.ambiguous("import the castle schematic please") && !SchematicText.ambiguous("explain the timing"),"Near-misses get the exact phrasing instead of a guess");
        helper.succeed();
    }
    @GameTest public void schematicCandidateIsUntestedAndOnlySafeBlocksParse(GameTestHelper helper){
        var placement=new JsonArray();placement.add(block(0,0,0,"minecraft:stone"));placement.add(block(1,0,0,"minecraft:redstone_lamp[lit=false]"));
        var candidate=SchematicBlueprint.candidate(placement,7,"d".repeat(64),"s".repeat(64),"world","minecraft:overworld","00000000-0000-0000-0000-000000000001");
        helper.assertTrue(candidate.get("verification").getAsString().equals("untested") && candidate.getAsJsonObject("provenance").get("kind").getAsString().equals("schematic"),"Imported geometry never arrives tested");
        helper.assertTrue(SchematicBlueprint.unsupported(candidate).isEmpty(),"Safe-library blocks are recognised");
        var blueprint=Blueprint.parse(candidate,"world","minecraft:overworld");
        helper.assertTrue(blueprint.blocks().size()==2 && blueprint.blocks().get(1).pos().equals(new BlockPos(1,0,0)),"The adapter feeds the existing blueprint parser unchanged");
        placement.add(block(2,0,0,"minecraft:oak_planks"));placement.add(block(3,0,0,"minecraft:chest[facing=north]"));placement.add(block(4,0,0,"minecraft:no_such_block"));
        var wider=SchematicBlueprint.candidate(placement,8,"d".repeat(64),"s".repeat(64),"world","minecraft:overworld","00000000-0000-0000-0000-000000000001");
        var unsupported=SchematicBlueprint.unsupported(wider);
        helper.assertTrue(unsupported.contains("oak_planks") && unsupported.contains("chest") && unsupported.contains("no_such_block (unknown to this game version)"),"Unsupported and unknown blocks are named: "+unsupported);
        boolean refused=false;try{Blueprint.parse(wider,"world","minecraft:overworld");}catch(IllegalArgumentException expected){refused=true;}
        helper.assertTrue(refused,"The core parser still refuses blocks outside the safe library");
        helper.succeed();
    }
    @GameTest public void regionCaptureUsesSpongeOrderAndRefusesSilentContentLoss(GameTestHelper helper){
        helper.setBlock(new BlockPos(1,1,0),Blocks.STONE);helper.setBlock(new BlockPos(0,2,1),Blocks.REDSTONE_LAMP);
        var a=helper.absolutePos(new BlockPos(1,2,1));var b=helper.absolutePos(new BlockPos(0,1,0));
        var geometry=SchematicClient.region(helper.getLevel(),a,b,false);
        var size=geometry.getAsJsonObject("size");
        helper.assertTrue(size.get("x").getAsInt()==2 && size.get("y").getAsInt()==2 && size.get("z").getAsInt()==2 && geometry.getAsJsonArray("blocks").size()==8,"Corners in any order give the full inclusive box, air included");
        var palette=geometry.getAsJsonArray("palette");var blocks=geometry.getAsJsonArray("blocks");
        helper.assertTrue(name(palette,blocks,1).equals("minecraft:stone"),"Index x + z*sx + y*sx*sz holds the stone at (1,0,0)");
        helper.assertTrue(name(palette,blocks,0+1*2+1*4).equals("minecraft:redstone_lamp"),"The lamp at (0,1,1) is at index 6");
        helper.assertTrue(name(palette,blocks,0).equals("minecraft:air") && palette.get(blocks.get(6).getAsInt()).getAsJsonObject().getAsJsonObject("properties").has("lit"),"Air is exported and properties are kept");
        helper.assertTrue(geometry.get("dataVersion").getAsInt()>0,"Exports carry the running game's data version");
        helper.setBlock(new BlockPos(1,2,1),Blocks.CHEST);
        boolean refused=false;try{SchematicClient.region(helper.getLevel(),a,b,false);}catch(IllegalStateException expected){refused=expected.getMessage().contains("drop block contents");}
        helper.assertTrue(refused,"Block contents are never dropped without the player saying so");
        helper.assertTrue(name(SchematicClient.region(helper.getLevel(),a,b,true).getAsJsonArray("palette"),SchematicClient.region(helper.getLevel(),a,b,true).getAsJsonArray("blocks"),7).equals("minecraft:chest"),"With the stated discard the block itself is exported");
        boolean tooBig=false;try{SchematicClient.region(helper.getLevel(),a,a.offset(64,0,0),false);}catch(IllegalStateException expected){tooBig=true;}
        helper.assertTrue(tooBig,"Exports stay within 64 blocks a side");
        helper.succeed();
    }
    private static JsonObject block(int x,int y,int z,String state){var o=new JsonObject();var pos=new JsonArray();pos.add(x);pos.add(y);pos.add(z);o.add("pos",pos);o.addProperty("state",state);return o;}
    private static String name(JsonArray palette,JsonArray blocks,int index){return palette.get(blocks.get(index).getAsInt()).getAsJsonObject().get("name").getAsString();}
}
