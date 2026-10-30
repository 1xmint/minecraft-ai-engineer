package dev.minecraftai.engineer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Drives real production commands, an integrated server, persistence, rendering,
 * and the executor. Fixture operations represent the player's manual section;
 * they are deliberately not evidence of mouse-level player placement or Codex.
 */
public final class EngineerClientGameTests implements FabricClientGameTest {
    private static final BlockPos ANCHOR = new BlockPos(0,100,0);
    private static final BlockPos LEVER = new BlockPos(0,101,0);
    private static final BlockPos REPEATER = new BlockPos(1,101,0);
    private static final BlockPos LAMP = new BlockPos(2,101,0);

    @Override public void runTest(ClientGameTestContext context) {
        if(System.getenv("MCE_MP_ROLE")!=null)return;
        TestWorldSave save;
        String project;
        try (TestSingleplayerContext world=context.worldBuilder().create()) {
            TestServerContext server=world.getServer();
            server.runOnServer(s->{
                ServerPlayer p=player(s);
                p.setGameMode(GameType.CREATIVE);
                p.getAbilities().flying=true;
                p.onUpdateAbilities();
            });
            server.runCommand("tp @a 1 102 6 180 20");
            context.waitTicks(5);
            world.getConnection().waitForChunksRender();
            command(server,"engineer select1 0 100 0");
            command(server,"engineer select2 2 101 0");
            command(server,"engineer reference");
            project=server.computeOnServer(s->checkpoint(s).getAsJsonObject("blueprint").get("id").getAsString());
            context.waitTicks(3);
            context.waitFor(client->dev.minecraftai.engineer.client.ClientPreview.current()!=null);
            context.waitFor(client->dev.minecraftai.engineer.client.GhostPreviewRenderer.submittedModelCount()>0);
            context.runOnClient(client->{
                check(dev.minecraftai.engineer.client.ClientPreview.current()!=null,"Reference preview must reach the client");
                check(dev.minecraftai.engineer.client.ClientPreview.current().blocks().size()==6,"All six reference blocks must be previewed");
                check(dev.minecraftai.engineer.client.ClientPreview.current().blocks().stream().filter(ProjectView.Ghost::currentStage).count()==3,"Only the three support blocks belong to the first step");
                check(dev.minecraftai.engineer.client.ClientPreview.renderInput()==dev.minecraftai.engineer.client.ClientPreview.current(),"Rendering consumes the same immutable approved snapshot");
                check(dev.minecraftai.engineer.client.GhostPreviewRenderer.submittedModelCount()<=PreviewSelection.MAX_VISIBLE_BLOCKS,"Native model submission remains bounded");
                client.gui.hud.getChat().clearMessages(false);
            });
            server.runOnServer(s->{check(EngineerMod.sessionFor(player(s)).blueprint.blocks().stream().allMatch(block->player(s).level().getBlockState(block.pos()).isAir()),"Textured preview never creates fake server blocks");check(checkpoint(s).getAsJsonArray("journal").isEmpty(),"Model submission grants no execution or assistant ownership");});
            context.takeScreenshot("engineer-01-reference-preview");

            // Actual player section in the real world. The assistant must retain it.
            server.runOnServer(s->{for(int x=0;x<3;x++)player(s).level().setBlockAndUpdate(ANCHOR.offset(x,0,0),Blocks.STONE.defaultBlockState());});
            server.waitFor(s->checkpoint(s).get("stage").getAsInt()==1);
            server.runOnServer(s->player(s).level().setBlockAndUpdate(LEVER,Blueprint.state("minecraft:lever[face=floor,facing=east,powered=false]")));
            server.waitFor(s->checkpoint(s).get("stage").getAsInt()==2);
            context.waitFor(client->{var view=dev.minecraftai.engineer.client.ClientPreview.current();return view!=null && view.blocks().stream().filter(ProjectView.Ghost::matches).count()==4 && dev.minecraftai.engineer.client.GhostPreviewRenderer.submittedModelCount()==2;});
            context.runOnClient(client->client.gui.hud.getChat().clearMessages(false));
            context.takeScreenshot("engineer-01b-partial-textured-preview");
            server.runOnServer(s->check(checkpoint(s).getAsJsonArray("journal").isEmpty(),"Partial preview does not claim fixture-placed player work"));
            command(server,"engineer inspect");
            server.runOnServer(s->check(snapshot(s).getAsJsonArray("blocks").size()==6,"Inspector exports exact selected coordinates"));
            command(server,"engineer build");
            server.waitFor(s->checkpoint(s).getAsJsonArray("journal").size()==1);
            command(server,"engineer stop");
            context.waitTicks(10);
            server.runOnServer(s->{
                check(checkpoint(s).get("status").getAsString().equals("PAUSED"),"Stop persists a paused task");
                check(player(s).level().getBlockState(REPEATER).is(Blocks.REPEATER),"The first delegated block was actually placed");
                check(player(s).level().getBlockState(LAMP).isAir(),"Stop prevents the remaining placement");
            });
            save=world.getWorldSave();
        }

        context.waitFor(client->dev.minecraftai.engineer.client.ClientPreview.renderInput()==null && dev.minecraftai.engineer.client.GhostPreviewRenderer.submittedModelCount()==0);
        // Reopen the actual saved Minecraft world, not a reconstructed mock world.
        try (TestSingleplayerContext world=save.open()) {
            TestServerContext server=world.getServer();
            world.getConnection().waitForChunksDownload();
            command(server,"engineer status");
            server.runOnServer(s->{
                JsonObject stored=checkpoint(s);
                check(stored.get("status").getAsString().equals("PAUSED"),"Restart does not renew execution permission");
                check(stored.getAsJsonObject("blueprint").get("id").getAsString().equals(project),"Same project is restored");
                check(stored.getAsJsonArray("journal").size()==1,"The actual placement journal survives reload");
                player(s).level().setBlockAndUpdate(LAMP,Blocks.GLASS.defaultBlockState());
            });
            command(server,"engineer resume");
            context.waitTicks(5);
            server.runOnServer(s->{
                check(player(s).level().getBlockState(LAMP).is(Blocks.GLASS),"Resume must preserve a conflicting player edit");
                check(checkpoint(s).get("status").getAsString().equals("PAUSED"),"Conflict keeps execution paused");
                player(s).level().setBlockAndUpdate(LAMP,Blocks.AIR.defaultBlockState());
            });
            command(server,"engineer resume");
            server.waitFor(s->checkpoint(s).get("status").getAsString().equals("COMPLETED"));
            server.runOnServer(s->{
                check(player(s).level().getBlockState(LAMP).is(Blocks.REDSTONE_LAMP),"Resume completes the real world");
                check(Blueprint.matches(Blueprint.state("minecraft:repeater[facing=west,delay=1]"),player(s).level().getBlockState(REPEATER)),"Executor places the correct repeater orientation");
            });
            command(server,"engineer test");
            server.waitFor(s->player(s).level().getBlockState(LEVER).getValue(BlockStateProperties.POWERED));
            BlockPos intruder=LEVER.offset(0,0,1);
            server.runOnServer(s->player(s).level().setBlockAndUpdate(intruder,Blocks.STONE.defaultBlockState()));
            context.waitTicks(8);
            String pendingInput=server.computeOnServer(s->{
                JsonObject stored=checkpoint(s);
                check(stored.get("status").getAsString().equals("PAUSED"),"A neighbor introduced during testing pauses the actual test");
                check(stored.get("verified").getAsString().startsWith("failed:"),"Interrupted testing records failure rather than a pass");
                check(player(s).level().getBlockState(LEVER).getValue(BlockStateProperties.POWERED),"Test must not restore or toggle an input beside an unexpected new block");
                check(player(s).level().getBlockState(intruder).is(Blocks.STONE),"A player-added test neighbor is preserved");
                check(stored.has("testOriginal"),"Unsafe input restoration remains pending for explicit reconciliation");
                return stored.get("testOriginal").getAsString();
            });
            command(server,"engineer test");
            command(server,"engineer build");
            command(server,"engineer undo");
            server.runOnServer(s->{
                JsonObject stored=checkpoint(s);
                check(stored.has("testOriginal") && stored.get("testOriginal").getAsString().equals(pendingInput),"New test/build requests cannot replace a pending restoration checkpoint");
                check(stored.get("status").getAsString().equals("PAUSED"),"Pending test restoration does not silently grant build authority");
                check(player(s).level().getBlockState(LEVER).getValue(BlockStateProperties.POWERED),"Rejected requests preserve the pending actual input state");
                check(stored.getAsJsonArray("journal").size()==2 && player(s).level().getBlockState(REPEATER).is(Blocks.REPEATER) && player(s).level().getBlockState(LAMP).is(Blocks.REDSTONE_LAMP),"Unsafe pending test recovery blocks undo before it removes its circuit");
                player(s).level().setBlockAndUpdate(intruder,Blocks.AIR.defaultBlockState());
            });
            command(server,"engineer resume");
            server.runOnServer(s->{
                check(!checkpoint(s).has("testOriginal"),"Explicit resume safely reconciles the original input once the interference is gone");
                check(!player(s).level().getBlockState(LEVER).getValue(BlockStateProperties.POWERED),"Explicit safe reconciliation restores the original off state");
            });
            command(server,"engineer test");
            server.waitFor(s->checkpoint(s).get("verified").getAsString().startsWith("passed:"));
            server.runOnServer(s->{
                check(!player(s).level().getBlockState(LAMP).getValue(BlockStateProperties.LIT),"Functional test observes the final off transition");
                check(!player(s).level().getBlockState(LEVER).getValue(BlockStateProperties.POWERED),"Functional test restores the player's input");
            });
            context.waitTick();
            world.getConnection().waitForClientboundPackets();
            world.getConnection().waitForChunksRender();
            context.runOnClient(client->client.gui.hud.getChat().clearMessages(false));
            context.takeScreenshot("engineer-02-reloaded-tested-build");
            server.runOnServer(s->player(s).level().setBlockAndUpdate(REPEATER,Blueprint.state("minecraft:repeater[facing=east,delay=1,locked=false,powered=false]")));
            context.waitFor(client->{var view=dev.minecraftai.engineer.client.ClientPreview.current();return view!=null && view.blocks().stream().anyMatch(ghost->ghost.pos().equals(REPEATER) && ghost.wrong()) && dev.minecraftai.engineer.client.GhostPreviewRenderer.submittedModelCount()>0;});
            context.runOnClient(client->client.gui.hud.getChat().clearMessages(false));
            context.takeScreenshot("engineer-03-wrong-orientation-preview");
            server.runOnServer(s->{check(player(s).level().getBlockState(REPEATER).getValue(BlockStateProperties.HORIZONTAL_FACING)==net.minecraft.core.Direction.EAST,"Intended model must preserve the player's wrong-facing physical block");check(checkpoint(s).getAsJsonArray("journal").size()==2,"Visual correction never writes a repair journal");player(s).level().setBlockAndUpdate(REPEATER,Blueprint.state("minecraft:repeater[facing=west,delay=1,locked=false,powered=false]"));});

            // A new attachment can depend on an assistant block even outside the blueprint.
            BlockPos attachment=LAMP.offset(1,0,0);
            server.runOnServer(s->{
                player(s).level().setBlockAndUpdate(attachment,Blueprint.state("minecraft:wall_torch[facing=east]"));
                check(player(s).level().getBlockState(attachment).is(Blocks.WALL_TORCH),"Attachment fixture is actually supported by the lamp");
            });
            command(server,"engineer undo");
            server.runOnServer(s->{
                check(player(s).level().getBlockState(attachment).is(Blocks.WALL_TORCH),"Undo preserves the player's later attached block");
                check(player(s).level().getBlockState(LAMP).is(Blocks.REDSTONE_LAMP),"Undo retains the attachment's support");
                check(player(s).level().getBlockState(REPEATER).is(Blocks.REPEATER),"Attachment preflight prevents a partial undo");
                check(checkpoint(s).getAsJsonArray("journal").size()==2,"Rejected attachment undo retains the full journal");
                player(s).level().setBlockAndUpdate(attachment,Blocks.AIR.defaultBlockState());
            });

            // Undo first refuses a later edit, then removes only assistant blocks.
            server.runOnServer(s->player(s).level().setBlockAndUpdate(REPEATER,Blocks.GLASS.defaultBlockState()));
            command(server,"engineer undo");
            server.runOnServer(s->{
                check(player(s).level().getBlockState(REPEATER).is(Blocks.GLASS),"Undo preserves later edits");
                check(player(s).level().getBlockState(LAMP).is(Blocks.REDSTONE_LAMP),"Undo preflight prevents a partial destructive revert");
                player(s).level().setBlockAndUpdate(REPEATER,Blueprint.state("minecraft:repeater[facing=west,delay=1,locked=false,powered=false]"));
            });
            command(server,"engineer undo");
            server.runOnServer(s->{
                check(player(s).level().getBlockState(REPEATER).isAir() && player(s).level().getBlockState(LAMP).isAir(),"Undo removes the two delegated blocks");
                check(player(s).level().getBlockState(ANCHOR).is(Blocks.STONE) && player(s).level().getBlockState(LEVER).is(Blocks.LEVER),"Undo retains the player's first sections");
                check(checkpoint(s).getAsJsonArray("journal").isEmpty(),"Successful undo clears the journal");
                player(s).setGameMode(GameType.SURVIVAL);
            });
            command(server,"engineer build");
            context.waitTicks(5);
            server.runOnServer(s->{check(player(s).level().getBlockState(REPEATER).isAir(),"Survival direct editing is rejected");player(s).setGameMode(GameType.CREATIVE);});

            command(server,"engineer select2 1 101 0");
            command(server,"engineer build");
            context.waitTicks(5);
            server.runOnServer(s->{
                check(player(s).level().getBlockState(REPEATER).isAir() && player(s).level().getBlockState(LAMP).isAir(),"Out-of-region preflight rejects the whole assignment before placement");
                check(checkpoint(s).getAsJsonArray("baseline").isEmpty(),"Rejected preflight does not partially grant authority");
            });
            command(server,"engineer select2 2 101 0");
            server.runOnServer(s->{
                player(s).level().setBlockAndUpdate(LAMP,Blocks.CHEST.defaultBlockState());
                ((ChestBlockEntity)player(s).level().getBlockEntity(LAMP)).setItem(0,new ItemStack(Items.DIAMOND));
            });
            command(server,"engineer build");
            context.waitTicks(5);
            server.runOnServer(s->{
                check(player(s).level().getBlockState(LAMP).is(Blocks.CHEST),"Inventory block is never replaced");
                check(((ChestBlockEntity)player(s).level().getBlockEntity(LAMP)).getItem(0).is(Items.DIAMOND),"Inventory contents survive rejection");
                check(player(s).level().getBlockState(REPEATER).isAir(),"Inventory rejection happens before any partial building");
                player(s).level().setBlockAndUpdate(LAMP,Blocks.AIR.defaultBlockState());
            });
            command(server,"engineer select1 1000000 100 0");
            command(server,"engineer select2 1000002 101 0");
            command(server,"engineer reference");
            command(server,"engineer build");
            server.runOnServer(s->{
                check(!player(s).level().hasChunkAt(new BlockPos(1000000,100,0)),"Executor does not load distant chunks to bypass its boundary");
                check(checkpoint(s).getAsJsonArray("journal").isEmpty(),"Unloaded chunks are rejected rather than treated as air");
            });
            context.waitFor(client->dev.minecraftai.engineer.client.GhostPreviewRenderer.submittedModelCount()==0);
            command(server,"engineer project new");command(server,"engineer select1 16 100 0");command(server,"engineer select2 18 101 2");command(server,"engineer reference");command(server,"engineer rotate 90");
            server.runOnServer(s->{player(s).getAbilities().flying=true;player(s).onUpdateAbilities();check(EngineerMod.sessionFor(player(s)).blueprint.rotation()==90,"Native rotated preview uses the accepted world orientation");});
            server.runCommand("tp @a 20 103 6 140 25");world.getConnection().waitForClientboundPackets();world.getConnection().waitForChunksRender();
            context.waitFor(client->{var view=dev.minecraftai.engineer.client.ClientPreview.current();return view!=null && view.blocks().stream().anyMatch(ghost->ghost.state().is(Blocks.REPEATER) && ghost.state().getValue(BlockStateProperties.HORIZONTAL_FACING)==net.minecraft.core.Direction.NORTH) && dev.minecraftai.engineer.client.GhostPreviewRenderer.submittedModelCount()>0;});
            context.runOnClient(client->client.gui.hud.getChat().clearMessages(false));context.takeScreenshot("engineer-04-rotated-textured-preview");
            server.runOnServer(s->{var session=EngineerMod.sessionFor(player(s));check(session.blueprint.blocks().stream().allMatch(block->player(s).level().getBlockState(block.pos()).isAir()) && session.journal.isEmpty(),"Rotated textured previews remain intentions with no world mutation");});
            // A live dimension transition must retire the old world's presentation.
            server.runCommand("execute in minecraft:the_nether run tp @a 20 103 6");
            world.getConnection().waitForClientboundPackets();
            context.waitFor(client->client.level!=null && client.level.dimension().identifier().toString().equals("minecraft:the_nether") && dev.minecraftai.engineer.client.ClientPreview.current()==null && dev.minecraftai.engineer.client.ClientPreview.renderInput()==null && dev.minecraftai.engineer.client.GhostPreviewRenderer.submittedModelCount()==0);
            server.runCommand("execute in minecraft:overworld run tp @a 20 103 6 140 25");
            world.getConnection().waitForClientboundPackets();world.getConnection().waitForChunksRender();
            context.waitFor(client->dev.minecraftai.engineer.client.ClientPreview.current()!=null && dev.minecraftai.engineer.client.GhostPreviewRenderer.submittedModelCount()>0);
            command(server,"engineer project new");
            context.waitFor(client->dev.minecraftai.engineer.client.ClientPreview.current()==null && dev.minecraftai.engineer.client.ClientPreview.renderInput()==null && dev.minecraftai.engineer.client.GhostPreviewRenderer.submittedModelCount()==0);
        }
    }

    private static void command(TestServerContext server,String command) {
        server.runOnServer(s->s.getCommands().performPrefixedCommand(player(s).createCommandSourceStack(),command));
    }
    private static ServerPlayer player(MinecraftServer server) {
        check(server.isSingleplayer() && !server.isPublished(),"Test must use an actual unpublished integrated server");
        check(server.getPlayerList().getPlayerCount()==1,"Exactly one actual client must be connected");
        return server.getPlayerList().getPlayers().getFirst();
    }
    private static Path checkpointPath(MinecraftServer server) {
        return EngineerMod.sessionFor(player(server)).file;
    }
    private static JsonObject checkpoint(MinecraftServer server) { return read(checkpointPath(server)); }
    private static JsonObject snapshot(MinecraftServer server) { Path file=checkpointPath(server);return read(file.resolveSibling(file.getFileName()+".snapshot.json")); }
    private static JsonObject read(Path path) {
        try{return JsonParser.parseString(Files.readString(path)).getAsJsonObject();}
        catch(Exception e){throw new AssertionError("Expected durable production evidence at "+path,e);}
    }
    private static void check(boolean condition,String message) { if(!condition)throw new AssertionError(message); }
}
