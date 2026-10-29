package dev.minecraftai.engineer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.minecraftai.engineer.client.ClientPreview;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.impl.client.gametest.context.TestDedicatedServerContextImpl;
import net.fabricmc.fabric.impl.client.gametest.util.DedicatedServerImplUtil;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * Actual dedicated server plus native Alice, Bob and Eve client JVMs. Files only
 * coordinate the test processes; commands travel through Minecraft connections.
 * CI deliberately uses offline identities and does not prove production login.
 */
public final class MultiplayerGameTests implements FabricClientGameTest {
    private static final BlockPos ANCHOR = new BlockPos(0, 100, 0);
    private static final BlockPos LEVER = ANCHOR.above();
    private static final BlockPos REPEATER = LEVER.east();
    private static final BlockPos LAMP = REPEATER.east();
    private static final ConcurrentLinkedQueue<String> MESSAGES = new ConcurrentLinkedQueue<>();
    private static final boolean LIVE_ACCEPTANCE="true".equals(System.getenv("CI")) && "true".equals(System.getenv("MCE_LIVE_ACCEPTANCE"));
    private static final boolean LIVE_GENERATED=LIVE_ACCEPTANCE && "true".equals(System.getenv("MCE_GENERATED_ACCEPTANCE"));
    private static final boolean LIVE_STRUCTURAL=LIVE_GENERATED && "true".equals(System.getenv("MCE_STRUCTURAL_ACCEPTANCE"));
    private static final String LIVE_STRUCTURAL_REQUEST="Replace the two repeaters (delays 4 and 2) in the existing four-block-wide buffer with three serial two-tick repeaters. Keep the input lever at relative 0,1,0 and its existing floor supports at x=0..3, y=0, z=0. Extend the support to x=4 and move the output lamp to relative 4,1,0. Keep rising latency 12 game ticks and falling latency 16 game ticks.";
    private static final long WAIT_MILLIS = LIVE_ACCEPTANCE?1_800_000:240_000;
    private record RepairRecovery(Path file,JsonObject checkpoint,String project,int retained) {}

    @Override public void runTest(ClientGameTestContext context) {
        String role = System.getenv("MCE_MP_ROLE");
        if (role == null || role.isBlank()) return;
        Path coordination = directory();
        try {
            Files.createDirectories(coordination);
            ClientReceiveMessageEvents.GAME.register((message, overlay) -> MESSAGES.add(message.getString()));
            switch (role) {
                case "alice" -> owner(context);
                case "bob" -> editor(context);
                case "eve" -> denied(context);
                case "eve-text" -> textOnly(context);
                default -> throw new AssertionError("Unknown multiplayer test role");
            }
            write(role + ".done", "passed");
        } catch (Throwable failure) {
            write(role + ".failed", failure.toString());
            if (failure instanceof RuntimeException runtime) throw runtime;
            throw new AssertionError(failure);
        }
    }

    private static void owner(ClientGameTestContext context) throws Exception {
        Properties properties = properties();
        String projectId;
        String worldId;
        UUID oldEpoch;
        try (TestDedicatedServerContext server = context.worldBuilder().createServer(properties)) {
            server.runOnServer(s -> {
                check(!s.isSingleplayer(), "The collaboration proof must use a dedicated server");
                s.setUsingWhitelist(false);
                s.setEnforceWhitelist(false);
            });
            publishServer(server, 1);
            try (TestDedicatedServerConnection connection = server.connect()) {
                server.runCommand("op Alice");
                MESSAGES.clear();send(context,"engineer ask explain this part again");expectChat(context,"No selected project");
                server.runOnServer(s->check(EngineerMod.managerFor(s).projects.isEmpty(),"A lesson question cannot silently create a project"));
                send(context, "engineer project new");
                server.waitFor(s -> !EngineerMod.managerFor(s).projects.isEmpty());
                projectId = server.computeOnServer(s -> session(s, "Alice").projectId.toString());
                worldId = server.computeOnServer(s -> session(s, "Alice").worldId);
                oldEpoch = server.computeOnServer(s -> EngineerMod.managerFor(s).epoch);
                write("project", projectId);
                send(context, "engineer select1 0 100 0");
                send(context, "engineer select2 2 101 0");
                send(context,(LIVE_ACCEPTANCE && !LIVE_GENERATED)?"engineer plan Build a delayed lamp with a lever and a one-tick repeater. Teach me its construction.":"engineer reference");
                server.waitFor(s -> session(s, "Alice").blueprint != null,LIVE_ACCEPTANCE?900_000:10_000);
                if(LIVE_ACCEPTANCE && !LIVE_GENERATED){
                    server.runOnServer(s->{var accepted=session(s,"Alice").blueprint;
                        check(accepted.source().getAsJsonObject("provenance").get("template").getAsString().equals("signal-lamp")
                            && accepted.source().getAsJsonObject("parameters").get("delay").getAsInt()==1
                            && accepted.rotation()==0 && accepted.anchor().equals(ANCHOR),"The actual validated model design matches the requested one-tick lamp and selected layout");});
                    write("live-model-design.done","passed");
                }
                inspectPersisted(context,server);
                await(context, () -> view(context) != null, "Alice's authorized network preview");
                check(view(context).stage() == 0, "Alice begins her own support lesson");
                server.waitFor(s -> s.getPlayerList().getPlayerByName("Bob") != null
                        && s.getPlayerList().getPlayerByName("Eve") != null, 12_000);
                UUID deniedActor=server.computeOnServer(s->player(s,"Eve").getUUID());
                server.runOnServer(s -> {
                    for (ServerPlayer player : s.getPlayerList().getPlayers()) {
                        player.setGameMode(GameType.CREATIVE);
                        player.getAbilities().flying = true;
                        player.onUpdateAbilities();
                    }
                });
                server.runCommand("tp @a 1 102 6 180 20");
                send(context, "engineer project share Bob editor");
                server.waitFor(s -> session(s, "Alice").access.allows(player(s, "Bob").getUUID(), ProjectAccess.Permission.BUILD));
                write("shared", "ready");
                awaitFile(context, "bob-next");
                awaitFile(context, "eve-denied");
                context.waitTicks(5);
                check(view(context) != null && view(context).stage() == 0,
                        "Bob's Next must not advance Alice's personal tutorial");
                server.runOnServer(s -> {
                    EngineeringSession project = session(s, "Alice");
                    check(project.cursor(player(s, "Bob").getUUID()).stage == 1, "Bob has an independent lesson cursor");
                    check(project.cursor(player(s, "Alice").getUUID()).stage == 0, "Alice's server cursor is unchanged");
                    check(!project.access.allows(deniedActor, ProjectAccess.Permission.VIEW), "Eve has no project grant after disconnect too");
                });
                playerConstructionFlow(context,server,connection);
                write("build-bob", "ready");
                // Revoke on the same real server tick that observes a partial build.
                // This removes scheduling races between observing and revoking.
                server.waitFor(s -> {
                    EngineeringSession project = session(s, "Alice");
                    if (project.journal.isEmpty()) return false;
                    commandAs(s, "Alice", "engineer project revoke Bob");
                    return true;
                }, 12_000);
                server.runOnServer(s -> {
                    var project = session(s, "Alice");
                    check(project.state.equals("PAUSED"), "Revocation pauses a running delegated assignment");
                    check(project.journal.size() < 2, "Revocation prevents the next remaining reference placement");
                    check(project.level().getBlockState(LAMP).isAir(), "A revoked editor cannot place the pending lamp");
                });
                write("revoked", "ready");
                awaitFile(context, "bob-revoked");
                server.runOnServer(s -> {
                    var project = session(s, "Alice");
                    check(project.level().getBlockState(LAMP).isAir(), "Rejected post-revoke commands do not renew authority");
                    project.level().setBlockAndUpdate(LAMP, Blocks.GLASS.defaultBlockState());
                });
                send(context, "engineer resume");
                context.waitTicks(6);
                server.runOnServer(s -> {
                    var project = session(s, "Alice");
                    check(project.level().getBlockState(LAMP).is(Blocks.GLASS), "Takeover preserves another player's conflicting edit");
                    check(project.state.equals("PAUSED"), "Conflicts require explicit reconciliation");
                    project.level().setBlockAndUpdate(LAMP, Blocks.AIR.defaultBlockState());
                });
                send(context, "engineer resume");
                server.waitFor(s -> session(s, "Alice").state.equals("COMPLETED"));
                send(context, "engineer test");
                server.waitFor(s -> session(s, "Alice").verified.startsWith("passed:"));
                assertPersisted(server);
                server.runCommand("tp Bob 5 102 6");
                connection.waitForClientboundPackets();
                context.runOnClient(client -> client.gui.hud.getChat().clearMessages(false));
                context.takeScreenshot("engineer-multiplayer-authorized-build");

                // Eve first proved denied delivery with the overlay installed.
                // A fresh Eve process now opts out of overlay negotiation entirely.
                awaitFile(context, "eve.done");
                write("text-connect", "ready");
                awaitFile(context, "eve-text-connected");
                send(context, "engineer project share Eve viewer");
                server.waitFor(s -> session(s, "Alice").access.allows(player(s, "Eve").getUUID(), ProjectAccess.Permission.VIEW));
                write("text-viewer", "ready");
                awaitFile(context, "eve-text.done");

                send(context, "engineer project share Bob editor");
                send(context, "engineer permit Bob");
                server.waitFor(s -> session(s, "Alice").access.allows(player(s, "Bob").getUUID(), ProjectAccess.Permission.BUILD));
                write("overlap-create", "ready");
                awaitFile(context, "bob-overlap-created");
                send(context, "engineer undo");
                server.waitFor(s -> session(s, "Alice").journal.isEmpty());
                send(context, "engineer build");
                server.waitFor(s -> {
                    var first = session(s, "Alice");
                    if (first.journal.isEmpty()) return false;
                    commandAs(s, "Bob", "engineer build");
                    var second = session(s, "Bob");
                    check(second.baseline.isEmpty() && second.journal.isEmpty(), "Overlapping AI assignments cannot gain concurrent authority");
                    commandAs(s, "Alice", "engineer stop");
                    return true;
                });
                check(server.computeOnServer(s -> session(s, "Alice").state.equals("PAUSED")), "Stop persists the interrupted assignment");
                // Bob's own project's revision does not change when Alice revokes
                // access to the workspace it overlaps. Check that dependency too.
                server.runOnServer(s -> {
                    commandAs(s,"Alice","engineer undo");
                    commandAs(s,"Bob","engineer build");
                });
                server.waitFor(s -> {
                    var second=session(s,"Bob");if(second.journal.isEmpty())return false;
                    commandAs(s,"Alice","engineer project revoke Bob");return true;
                });
                server.waitFor(s -> session(s,"Bob").state.equals("PAUSED"));
                server.runOnServer(s -> {
                    var second=session(s,"Bob");
                    check(second.journal.size()==1 && second.level().getBlockState(LAMP).isAir(),"Revoking an intersecting project's grant stops the next write in an independently owned project");
                    commandAs(s,"Alice","engineer project share Bob editor");
                    commandAs(s,"Alice","engineer project permission Bob undo true");
                    commandAs(s,"Bob","engineer undo");
                    check(second.journal.isEmpty(),"Explicit cross-project undo rights allow safe cleanup");
                    commandAs(s,"Alice","engineer build");
                });
                server.waitFor(s -> {
                    var project=session(s,"Alice");if(project.journal.isEmpty())return false;
                    commandAs(s,"Alice","engineer stop");return true;
                });
                server.runOnServer(s -> {
                    var original=session(s,"Alice");
                    try {
                        String oldWorld=LocalFiles.legacyWorldId(s);
                        String name=oldWorld+"-"+original.owner+"-"+original.dimension.replace(':','_')+".json";
                        Path source=LocalFiles.configDir().resolve("sessions").resolve(name);
                        var legacy=com.google.gson.JsonParser.parseString(Files.readString(original.file)).getAsJsonObject();
                        // A pre-v4 save has no immutable scope metadata to rewrite across identities.
                        legacy.addProperty("journalVersion",3);legacy.remove("operationScopes");
                        legacy.addProperty("schemaVersion",1);legacy.addProperty("worldId",oldWorld);legacy.getAsJsonObject("blueprint").addProperty("worldId",oldWorld);
                        LocalFiles.write(source,legacy);String before=Files.readString(source);
                        commandAs(s,"Alice","engineer project adopt-legacy");
                        var adopted=session(s,"Alice");
                        check(!adopted.projectId.equals(original.projectId) && adopted.state.equals("PAUSED") && adopted.delegation==null,"Explicit legacy adoption never imports execution authority");
                        check(adopted.journal.size()==original.journal.size() && adopted.access.members().size()==1,"Adoption preserves the recovery journal with private ownership");
                        check(adopted.trial.evidence().isEmpty(),"An adopted project's new identity cannot inherit unscoped functional evidence");
                        check(adopted.trial.scope()==null && adopted.assignments.tasks.values().stream().allMatch(task->task.lastPassedScope==null && task.executor.equals("unassigned")),"Adopted descriptive tasks and test scope carry no old identity or assignment");
                        try(var backups=Files.walk(LocalFiles.configDir().resolve("legacy-backups").resolve(adopted.projectId.toString()))) {
                            Path preserved=backups.filter(path->path.getFileName().toString().equals(name)).findFirst().orElseThrow();
                            check(Files.readString(source).equals(before) && Files.readString(preserved).equals(before),"The exact legacy source is preserved and backed up before adoption");
                        }
                        int count=adopted.manager.projects.size();commandAs(s,"Alice","engineer project adopt-legacy");
                        check(adopted.manager.projects.size()==count,"Repeating adoption does not duplicate journal authority");
                    }catch(java.io.IOException error){throw new AssertionError(error);}
                    commandAs(s,"Alice","engineer project use "+projectId);
                });
                write("prepare-restart", "ready");
                awaitFile(context, "bob-disconnected");
                server.waitFor(s -> s.getPlayerList().getPlayerCount() == 1, 12_000);
            }
        }

        // The public builder creates a fresh world. For restart, use the pinned
        // Fabric test launcher directly; it invokes the actual dedicated Main
        // against the same saved level without regenerating or deleting it.
        RepairRecovery recovery;
        try (TestDedicatedServerContext server = new TestDedicatedServerContextImpl(context,
                DedicatedServerImplUtil.start(context, properties));
             TestDedicatedServerConnection connection = server.connect()) {
            send(context, "engineer status");
            server.waitFor(s -> !EngineerMod.managerFor(s).projects.isEmpty());
            await(context,()->MESSAGES.stream().anyMatch(message->message.contains("Several projects could be the target")),"Ambiguous project selection is rejected after restart");
            server.runOnServer(s -> check(!EngineerMod.managerFor(s).selections.containsKey(player(s,"Alice").getUUID()),"Restart cannot silently choose between two owned projects"));
            send(context,"engineer project use "+projectId);
            server.waitFor(s -> EngineerMod.managerFor(s).selections.containsKey(player(s,"Alice").getUUID()));
            server.runOnServer(s -> {
                var project = session(s, "Alice");
                check(project.projectId.toString().equals(projectId), "Project identity survives a dedicated-server restart");
                check(project.worldId.equals(worldId), "World identity survives a dedicated-server restart");
                check(!EngineerMod.managerFor(s).epoch.equals(oldEpoch), "Restart invalidates the previous server epoch");
                check(project.state.equals("PAUSED") && project.delegation == null, "Restart does not renew execution authority");
                check(!project.journal.isEmpty(), "The actual partial build journal survives restart");
            });
            publishServer(server, 2);
            write("restarted", "ready");
            awaitFile(context, "bob-reconnected");
            send(context, "engineer resume");
            server.waitFor(s -> session(s, "Alice").state.equals("COMPLETED"));
            inspectPersisted(context,server);
            send(context, "engineer test");
            server.waitFor(s -> session(s, "Alice").verified.startsWith("passed:"));
            assertPersisted(server);
            server.runOnServer(s -> {
                var project = session(s, "Alice");
                check(project.level().getBlockState(REPEATER).is(Blocks.REPEATER), "Dedicated reconciliation builds the real repeater");
                check(project.level().getBlockState(LAMP).is(Blocks.REDSTONE_LAMP), "Dedicated reconciliation builds the real lamp");
            });
            catalogFlow(context,server);
            recovery=repairFlow(context,server);
            legacyRevisionFlow(context,server);
            componentFlow(context,server);
            componentRevisionFlow(context,server);
            targetedPerceptionFlow(context,server);
            inventoryFlow(context,server);
            lessonFollowupFlow(context,server);
            undoContextFailureFlow(context,server);
            if(!LIVE_ACCEPTANCE)generatedTruthFlow(context,server);
            if(LIVE_STRUCTURAL)liveStructuralFlow(context,server,connection);
            else if(!LIVE_ACCEPTANCE || LIVE_GENERATED)generatedConfirmationFlow(context,server,connection);
            send(context,"engineer project use "+projectId);
            server.waitFor(s->session(s,"Alice").projectId.toString().equals(projectId));
            write("finish", "ready");
            awaitFile(context, "bob.done");
            server.waitFor(s -> s.getPlayerList().getPlayerCount() == 1, 12_000);
            server.runCommand("tp Alice 1 102 6 180 20");
            connection.waitForClientboundPackets();
            context.runOnClient(client -> client.gui.hud.getChat().clearMessages(false));
            context.takeScreenshot("engineer-multiplayer-restarted-tested-build");
        }
        // Fault injection models a crash after the last selected undo entry was
        // flushed, before its completion flag. Boot the actual saved server again.
        LocalFiles.write(recovery.file(),recovery.checkpoint());
        try(TestDedicatedServerContext server=new TestDedicatedServerContextImpl(context,DedicatedServerImplUtil.start(context,properties));
            TestDedicatedServerConnection connection=server.connect()){
            publishServer(server,3);
            send(context,"engineer project use "+recovery.project());
            server.waitFor(s->recovery.project().equals(String.valueOf(EngineerMod.managerFor(s).selections.get(player(s,"Alice").getUUID()))));
            server.runOnServer(s->{var project=session(s,"Alice");check(project.delegation==null && project.undoRequested,"Restart imports the undo target, never an execution lease");});
            send(context,"engineer undo");server.waitFor(s->!session(s,"Alice").undoRequested);
            server.runOnServer(s->{var project=session(s,"Alice");check(project.journal.size()==recovery.retained(),"Finishing an already-empty undo operation cannot consume an older operation");check(project.level().getBlockState(new BlockPos(21,101,0)).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DELAY)==1,"Crash recovery preserves the older operation's repeater");check(project.level().getBlockState(new BlockPos(21,100,0)).is(Blocks.STONE),"Crash recovery preserves earlier construction");});
            MESSAGES.clear();send(context,"engineer repair");expectChat(context,"No fresh diagnosis");
            write("repair-recovery.done","passed");
            componentRecoveryFlow(context,server);
            undoContextRecoveryFlow(context,server);
            lessonRecoveryFlow(context,server);
            if(!LIVE_ACCEPTANCE)generatedTruthRecovery(context,server);
            if(!LIVE_ACCEPTANCE || LIVE_GENERATED)generatedConfirmationRecovery(context,server,connection);
            if(!LIVE_ACCEPTANCE)structuralRevisionRecovery(context,server);
            send(context,"engineer project use "+read("inventory-project"));
            server.waitFor(s->session(s,"Alice").projectId.toString().equals(read("inventory-project")));
            MESSAGES.clear();send(context,"engineer materials");expectChat(context,"No fresh inventory snapshot");
            MESSAGES.clear();send(context,"engineer inspect inventory");expectChat(context,"Private inventory remembered");expectChat(context,"stone=7");
            write("inventory-recovery.done","passed");
            send(context,"engineer project use "+projectId);server.waitFor(s->session(s,"Alice").projectId.toString().equals(projectId));
            server.runCommand("tp Alice 1 102 6 180 20");connection.waitForClientboundPackets();
            server.waitFor(s->session(s,"Alice").blueprint.blocks().stream().allMatch(block->session(s,"Alice").level().hasChunkAt(block.pos())));
            inspectPersisted(context,server);
        }
        if(!LIVE_ACCEPTANCE) {
            try(TestDedicatedServerContext server=new TestDedicatedServerContextImpl(context,DedicatedServerImplUtil.start(context,properties));
                TestDedicatedServerConnection connection=server.connect()) {
                structuralReplanRecovery(context,server);
            }
        }
    }

    private static void liveStructuralFlow(ClientGameTestContext context,TestDedicatedServerContext server,TestDedicatedServerConnection connection) throws Exception {
        send(context,"engineer project new");send(context,"engineer select1 200 100 0");send(context,"engineer select2 203 101 0");
        server.waitFor(s->session(s,"Alice").corner2!=null && session(s,"Alice").corner2.equals(new BlockPos(203,101,0)));
        // Explicit hand-authored reference, never presented as fresh model generation.
        server.runOnServer(s->{var project=session(s,"Alice");var seed=MechanismGameTests.structuralPair()[0].source().deepCopy();seed.addProperty("id",project.projectId.toString());seed.addProperty("worldId",project.worldId);seed.addProperty("dimension",project.dimension);seed.addProperty("name","Synthetic serial buffer before revision");seed.add("anchor",Blueprint.jsonPos(new BlockPos(200,100,0)));project.accept(Blueprint.parse(seed,project.worldId,project.dimension));write("generated-project",project.projectId.toString());write("live-structural-before",project.blueprint.source().toString());});
        server.runCommand("tp Alice 201 103 6 180 20");server.waitFor(s->session(s,"Alice").dependencyPositions(true).stream().allMatch(pos->session(s,"Alice").level().hasChunkAt(pos)));
        generatedPlayerSection(context,server,connection);
        send(context,"engineer build");server.waitFor(s->session(s,"Alice").state.equals("COMPLETED"));send(context,"engineer test");server.waitFor(s->session(s,"Alice").verified.startsWith("passed:"));assertPersisted(server);
        workspaceExpansionFlow(context,server);
        int revision=server.computeOnServer(s->session(s,"Alice").revision());int oldJournal=server.computeOnServer(s->session(s,"Alice").journal.size());
        MESSAGES.clear();send(context,"engineer design "+LIVE_STRUCTURAL_REQUEST);expectChat(context,"/engineer confirm ",900_000);String behaviorNonce=confirmationNonce();
        check(MESSAGES.stream().anyMatch(message->message.contains("turning on after 12 game ticks and off after 16 game ticks")),"The live structural owner confirms the original observable timing contract");
        MESSAGES.clear();send(context,"engineer confirm "+behaviorNonce);expectChat(context,"Structural proposal ready",900_000);
        server.runOnServer(s->{var project=session(s,"Alice");check(project.revision()==revision && project.blueprint.blocks().size()==8 && project.journal.size()==oldJournal && project.delegation==null,"Live geometry inference leaves accepted intent, player construction and world authority unchanged");check(project.blueprint.blocks().stream().allMatch(b->Blueprint.matches(b.state(),project.level().getBlockState(b.pos()))),"The real original buffer still matches before explicit application");});
        var pattern=java.util.regex.Pattern.compile("/engineer apply ([0-9a-f-]{36})");String application=null;for(String message:MESSAGES){var match=pattern.matcher(message);if(match.find()){application=match.group(1);break;}}check(application!=null,"Live structural proposal needs a separate private world confirmation");
        send(context,"engineer apply "+application);server.waitFor(s->session(s,"Alice").state.equals("EXECUTING") && session(s,"Alice").structuralCursor>0);
        send(context,"engineer stop");server.waitFor(s->session(s,"Alice").state.equals("PAUSED"));
        var target=server.computeOnServer(s->{var project=session(s,"Alice");check(project.structuralCursor>0 && project.structuralCursor<project.operationScopes.transition(project.activeOperation).steps().size(),"The live proof stops an actual unfinished structural operation");return project.journal.getLast().pos();});
        server.runCommand("tp Alice 202 102 2 180 20");connection.waitForClientboundPackets();
        var previous=server.computeOnServer(s->player(s,"Alice").getMainHandItem().copy());
        try {
            equipConstructionItem(context,server,net.minecraft.world.item.Items.GLASS);
            context.runOnClient(client->client.gameMode.useItemOn(client.player,net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(target.below()).add(0,0.5,0),net.minecraft.core.Direction.UP,target.below(),false)));
            server.waitFor(s->session(s,"Alice").level().getBlockState(target).is(Blocks.GLASS));
            MESSAGES.clear();send(context,"engineer resume");expectChat(context,"Player/world edit conflicts");
            server.runOnServer(s->check(session(s,"Alice").level().getBlockState(target).is(Blocks.GLASS) && session(s,"Alice").state.equals("PAUSED"),"Actual player edit is preserved and blocks the live structural executor"));
            context.runOnClient(client->{client.gameMode.startDestroyBlock(target,net.minecraft.core.Direction.UP);client.gameMode.stopDestroyBlock();});server.waitFor(s->session(s,"Alice").level().getBlockState(target).isAir());
        } finally {server.runOnServer(s->{var actor=player(s,"Alice");actor.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,previous.copy());actor.inventoryMenu.broadcastChanges();check(net.minecraft.world.item.ItemStack.listMatches(java.util.List.of(previous),java.util.List.of(actor.getMainHandItem())),"Live edit fixture restores the prior held stack/components");});}
        send(context,"engineer resume");server.waitFor(s->session(s,"Alice").state.equals("COMPLETED"));
        server.runOnServer(s->{var project=session(s,"Alice");var repeaters=project.blueprint.blocks().stream().filter(b->b.state().is(Blocks.REPEATER)).toList();check(project.revision()==revision+1 && repeaters.size()==3 && repeaters.stream().allMatch(b->b.state().getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DELAY)==2),"The actual model reply constructs the requested three-repeater replacement");check(project.journal.stream().noneMatch(change->change.pos().equals(new BlockPos(200,100,0))),"The player's original support remains outside assistant history");check(project.operationScopes.transition(project.activeOperation).before().source().equals(JsonParser.parseString(read("live-structural-before")).getAsJsonObject()),"The executed live modification retains the exact real before intention");write("live-structural-after",project.blueprint.source().toString());});
        send(context,"engineer test");server.waitFor(s->session(s,"Alice").verified.startsWith("passed:"));assertPersisted(server);
        write("generated-companion-restart.request","ready");awaitFile(context,"generated-companion-restart.done");var receipt=JsonParser.parseString(read("generated-companion-restart.done")).getAsJsonObject();check(receipt.get("oldPid").getAsLong()!=receipt.get("newPid").getAsLong(),"Live structural evidence is read after an actual companion process restart");
        var report=server.computeOnServer(s->{var project=session(s,"Alice");project.checkpoint();return project.lastCheckpoint;});check(report.get(30,java.util.concurrent.TimeUnit.SECONDS).get("protocol").getAsInt()==2,"New companion acknowledges the real structural result");
        send(context,"engineer project share Bob viewer");server.waitFor(s->session(s,"Alice").access.allows(player(s,"Bob").getUUID(),ProjectAccess.Permission.VIEW));write("generated-shared","ready");awaitFile(context,"generated-bob.done");
        send(context,"engineer project share Bob editor");server.waitFor(s->session(s,"Alice").access.allows(player(s,"Bob").getUUID(),ProjectAccess.Permission.EDIT));write("workspace-bob-editable","ready");awaitFile(context,"workspace-bob-denied.done");
        send(context,"engineer project revoke Bob");write("generated-revoked","ready");awaitFile(context,"generated-bob-revoked.done");
        write("live-structural-model.done","passed");write("live-generated-model.done","passed");write("generated-confirmation.done","passed");
    }
    private static void generatedConfirmationFlow(ClientGameTestContext context,TestDedicatedServerContext server,TestDedicatedServerConnection connection) throws Exception {
        send(context,"engineer project new");
        send(context,"engineer select1 200 100 0");send(context,"engineer select2 203 101 0");
        server.waitFor(s->session(s,"Alice").blueprint==null && session(s,"Alice").corner2!=null && session(s,"Alice").corner2.equals(new BlockPos(203,101,0)));
        String id=server.computeOnServer(s->session(s,"Alice").projectId.toString());write("generated-project",id);
        int revision=server.computeOnServer(s->session(s,"Alice").revision());
        String request=LIVE_GENERATED?"engineer design Make a lever-controlled lamp that turns on after 12 game ticks and off after 16 game ticks. Teach me its construction.":"engineer design CI generated buffer: on after 12 game ticks, off after 16 game ticks.";
        MESSAGES.clear();send(context,request);expectChat(context,"/engineer confirm ",LIVE_GENERATED?900_000:20_000);
        String first=confirmationNonce();
        server.runOnServer(s->{var project=session(s,"Alice");check(project.blueprint==null && project.revision()==revision && project.delegation==null,"Behavior proposal changes neither canonical intent nor world authority");});
        String accepted=first;
        if(!LIVE_GENERATED) {
            send(context,"engineer select1 200 100 0");send(context,"engineer select2 203 101 0");
            MESSAGES.clear();send(context,"engineer confirm "+first);expectChat(context,"No current behavior proposal");
            MESSAGES.clear();send(context,request);expectChat(context,"/engineer confirm ");accepted=confirmationNonce();
            check(!accepted.equals(first),"A changed workspace requires a fresh owner confirmation");
        } else check(MESSAGES.stream().anyMatch(message->message.contains("turning on after 12 game ticks and off after 16 game ticks")),"The live owner confirms the originally requested behavior, not a model-selected easier contract");
        MESSAGES.clear();send(context,"engineer confirm "+accepted);expectChat(context,"Generated preview accepted",LIVE_GENERATED?900_000:20_000);
        server.runOnServer(s->{var project=session(s,"Alice");check(GeneratedBlueprint.isGenerated(project.blueprint) && project.revision()==revision+1 && (LIVE_GENERATED || project.blueprint.blocks().size()==8),"Confirmed raw geometry uses the normal canonical preview");check(GeneratedBlueprint.behavior(project.blueprint).equals(new BehaviorSpec(12,16)),"Generated intent equals independently accepted timing");check(project.delegation==null && project.journal.isEmpty() && project.verified.equals("untested"),"Confirming inference grants no construction or functional pass");check(project.blueprint.blocks().stream().noneMatch(b->project.level().hasChunkAt(b.pos()) && project.level().getBlockState(b.pos()).is(Blocks.REPEATER)),"Preview does not place generated devices");});
        MESSAGES.clear();send(context,"engineer confirm "+accepted);expectChat(context,"No current behavior proposal");
        server.runCommand("forceload add 192 0");
        generatedPlayerSection(context,server,connection);
        send(context,"engineer build");server.waitFor(s->session(s,"Alice").state.equals("COMPLETED"));
        send(context,"engineer test");server.waitFor(s->session(s,"Alice").verified.startsWith("passed:"));assertPersisted(server);
        server.runOnServer(s->{var project=session(s,"Alice");JsonObject saved;try{saved=JsonParser.parseString(Files.readString(project.file)).getAsJsonObject();}catch(Exception error){throw new AssertionError(error);}var cases=saved.getAsJsonArray("functionalEvidence");FunctionalTrial.validateGeneratedEvidence(cases,GeneratedBlueprint.behavior(project.blueprint),true);check(cases.get(1).getAsJsonObject().getAsJsonObject("trace").get("transitionTick").getAsInt()==12 && cases.get(2).getAsJsonObject().getAsJsonObject("trace").get("transitionTick").getAsInt()==16,"Production test measures both accepted edges, not settled endpoint values");check(!project.hasPendingTest() && project.delegation==null,"A passing generated trial restores its input and releases authority");});
        write("generated-companion-restart.request","ready");awaitFile(context,"generated-companion-restart.done");
        var receipt=JsonParser.parseString(read("generated-companion-restart.done")).getAsJsonObject();check(receipt.get("oldPid").getAsLong()!=receipt.get("newPid").getAsLong(),"Generated memory is read after an actual companion process restart");
        server.runOnServer(s->session(s,"Alice").checkpoint());assertPersisted(server);
        workspaceExpansionFlow(context,server);
        send(context,"engineer project share Bob viewer");
        server.waitFor(s->session(s,"Alice").access.allows(player(s,"Bob").getUUID(),ProjectAccess.Permission.VIEW));
        write("generated-shared","ready");awaitFile(context,"generated-bob.done");
        send(context,"engineer project share Bob editor");server.waitFor(s->session(s,"Alice").access.allows(player(s,"Bob").getUUID(),ProjectAccess.Permission.EDIT));write("workspace-bob-editable","ready");awaitFile(context,"workspace-bob-denied.done");
        server.runOnServer(s->check(session(s,"Alice").corner2.equals(new BlockPos(204,101,0)),"A non-owner with edit permission cannot expand the delegated region"));
        send(context,"engineer project revoke Bob");write("generated-revoked","ready");awaitFile(context,"generated-bob-revoked.done");
        if(LIVE_GENERATED){write("live-generated-model.done","passed");write("generated-confirmation.done","passed");return;}
        var correct=server.computeOnServer(s->session(s,"Alice").blueprint.source().deepCopy());
        send(context,"engineer project new");send(context,"engineer select1 200 100 0");send(context,"engineer select2 203 101 0");
        server.waitFor(s->session(s,"Alice").blueprint==null && session(s,"Alice").corner2!=null && session(s,"Alice").corner2.equals(new BlockPos(203,101,0)));
        server.runOnServer(s->{var project=session(s,"Alice");var intent=new BehaviorSpec(10,16);correct.add("behavior",intent.json());correct.getAsJsonObject("provenance").addProperty("behaviorDigest",intent.digest());var test=correct.getAsJsonArray("tests").get(0).getAsJsonObject();test.addProperty("description",test.get("description").getAsString().replace("rising 12","rising 10"));project.commandActor=player(s,"Alice").getUUID();try{project.accept(Blueprint.parse(correct,project.worldId,project.dimension));}finally{project.commandActor=null;}});
        send(context,"engineer test");server.waitFor(s->session(s,"Alice").verified.startsWith("failed:"));
        server.runOnServer(s->{var project=session(s,"Alice");var cases=saved(project).getAsJsonArray("functionalEvidence");var trace=cases.get(1).getAsJsonObject().getAsJsonObject("trace");check(trace.get("expectedGameTicks").getAsInt()==10 && trace.get("transitionTick").getAsInt()==12 && trace.get("verdict").getAsString().equals("failed"),"Matching physical geometry fails when actual timing differs from independently accepted intent");check(project.blueprint.blocks().stream().allMatch(b->Blueprint.matches(b.state(),project.level().getBlockState(b.pos()))),"Failure is behavioral even though all declared blocks match");check(!project.hasPendingTest() && project.delegation==null,"Failed generated test restores its input before releasing authority");});
        write("generated-wrong-timing.done","passed");
        generatedConfirmationSaveFailure(context,server,request);
        workspaceBusyExpansionFlow(context,server);
        structuralRevisionFlow(context,server);
        write("generated-confirmation.done","passed");
    }
    private static void structuralRevisionFlow(ClientGameTestContext context,TestDedicatedServerContext server) throws Exception {
        var source=server.computeOnServer(s->EngineerMod.managerFor(s).projects.get(UUID.fromString(read("generated-project"))).blueprint.source().deepCopy());
        send(context,"engineer project new");send(context,"engineer select1 260 100 0");send(context,"engineer select2 264 101 0");
        server.waitFor(s->session(s,"Alice").corner2!=null && session(s,"Alice").corner2.equals(new BlockPos(264,101,0)));
        server.runCommand("tp Alice 262 103 6 180 20");
        server.runOnServer(s->{var project=session(s,"Alice");source.addProperty("id",UUID.randomUUID().toString());source.add("anchor",Blueprint.jsonPos(new BlockPos(260,100,0)));project.accept(Blueprint.parse(source,project.worldId,project.dimension));write("structural-project",project.projectId.toString());write("structural-before",project.blueprint.source().toString());});
        server.waitFor(s->session(s,"Alice").dependencyPositions(true).stream().allMatch(pos->session(s,"Alice").level().hasChunkAt(pos)));
        // Fixture-owned supports are deliberately absent from the assistant's journal.
        server.runCommand("setblock 260 100 0 minecraft:stone");
        send(context,"engineer build");server.waitFor(s->session(s,"Alice").state.equals("COMPLETED"));
        server.runCommand("setblock 264 100 0 minecraft:stone");
        server.runOnServer(s->{var project=session(s,"Alice");check(project.journal.stream().noneMatch(c->c.pos().equals(new BlockPos(260,100,0))),"Existing player support is adopted, not journalled");write("structural-original-journal",String.valueOf(project.journal.size()));});
        int priorRevision=server.computeOnServer(s->session(s,"Alice").revision());
        // Earlier confirmation/rejection cases deliberately use the real four-start
        // budget. Wait out its real wall-clock window; never bypass quota or retry a consumed nonce.
        long planningWindow=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(61);
        await(context,()->System.nanoTime()>=planningWindow,"planning rate window reset");
        MESSAGES.clear();send(context,"engineer design CI structural buffer: replace the two repeaters with three two-tick repeaters; keep 12/16 game-tick behavior.");expectChat(context,"/engineer confirm ");String confirmation=confirmationNonce();
        MESSAGES.clear();send(context,"engineer confirm "+confirmation);expectChat(context,"Structural proposal ready");
        var applicationPattern=java.util.regex.Pattern.compile("/engineer apply ([0-9a-f-]{36})");String application=null;
        for(String message:MESSAGES){var match=applicationPattern.matcher(message);if(match.find()){application=match.group(1);break;}}
        check(application!=null,"Owner receives a separate private world-application confirmation");write("structural-nonce",application);
        server.runOnServer(s->{var project=session(s,"Alice");check(project.revision()==priorRevision && project.journal.size()==Integer.parseInt(read("structural-original-journal")) && project.blueprint.blocks().size()==8 && project.delegation==null,"Confirmed inference previews a diff without changing accepted intention, history or world authority");check(project.blueprint.blocks().stream().allMatch(b->Blueprint.matches(b.state(),project.level().getBlockState(b.pos()))),"The old real circuit remains intact while its revision is only a proposal");});
        MESSAGES.clear();send(context,"engineer apply 00000000-0000-4000-8000-000000000000");expectChat(context,"No current structural preview matches");
        send(context,"engineer apply "+read("structural-nonce"));
        server.waitFor(s->session(s,"Alice").structuralCursor>0 && session(s,"Alice").state.equals("EXECUTING"));
        send(context,"engineer stop");server.waitFor(s->session(s,"Alice").state.equals("PAUSED"));
        server.runOnServer(s->{var project=session(s,"Alice");check(project.structuralCursor>0 && project.structuralCursor<project.operationScopes.transition(project.activeOperation).steps().size(),"Stop interrupts actual ordered structural writes");check(project.operationScopes.sources(project.activeOperation).size()==2 && project.delegation==null,"Interrupted revision retains both real intentions without authority");});
        var conflict=server.computeOnServer(s->session(s,"Alice").journal.getLast());
        server.runCommand("setblock "+conflict.pos().getX()+" "+conflict.pos().getY()+" "+conflict.pos().getZ()+" minecraft:glass");
        MESSAGES.clear();send(context,"engineer resume");expectChat(context,"Player/world edit conflicts");
        server.runOnServer(s->check(session(s,"Alice").level().getBlockState(conflict.pos()).is(Blocks.GLASS) && !session(s,"Alice").state.equals("EXECUTING"),"Resume preserves an intervening edit and refuses to continue"));
        server.runCommand("setblock "+conflict.pos().getX()+" "+conflict.pos().getY()+" "+conflict.pos().getZ()+" "+Blueprint.describe(conflict.after()));
        server.runOnServer(s->check(session(s,"Alice").state.equals("PAUSED") && session(s,"Alice").structuralCursor>0,"Leave the genuinely interrupted revision paused for the actual server restart"));
        write("structural-revision.done","passed");
    }
    private static void structuralRevisionRecovery(ClientGameTestContext context,TestDedicatedServerContext server) throws Exception {
        send(context,"engineer project use "+read("structural-project"));server.waitFor(s->session(s,"Alice").projectId.toString().equals(read("structural-project")));
        server.runCommand("tp Alice 262 103 6 180 20");server.waitFor(s->session(s,"Alice").dependencyPositions(true).stream().allMatch(pos->session(s,"Alice").level().hasChunkAt(pos)));
        server.runOnServer(s->{var project=session(s,"Alice");check(project.delegation==null && project.operationKind.equals("STRUCTURAL") && project.blueprint.blocks().size()==10,"Actual restart loads the accepted larger intention without a lease");var sources=project.operationScopes.sources(project.activeOperation);check(sources.size()==2 && sources.get(0).source().equals(JsonParser.parseString(read("structural-before")).getAsJsonObject()),"Actual restart retains the original unmodified before intention");});
        server.runOnServer(s->{var project=session(s,"Alice");check(project.structuralCursor>0 && project.structuralCursor<project.operationScopes.transition(project.activeOperation).steps().size(),"Actual reboot retains the unfinished structural step cursor");});
        send(context,"engineer resume");server.waitFor(s->session(s,"Alice").state.equals("COMPLETED"));
        server.runOnServer(s->{var project=session(s,"Alice");check(project.blueprint.blocks().size()==10 && project.blueprint.blocks().stream().allMatch(b->Blueprint.matches(b.state(),project.level().getBlockState(b.pos()))),"Real executor produces the larger three-repeater layout");check(project.journal.stream().noneMatch(c->c.pos().equals(new BlockPos(264,100,0))),"Matching player addition is adopted and stays out of undo");});
        send(context,"engineer test");server.waitFor(s->session(s,"Alice").verified.startsWith("passed:"));
        server.runOnServer(s->check(session(s,"Alice").operationScopes.sources(session(s,"Alice").activeOperation).size()==2,"Functional testing retains the paired structural history"));
        send(context,"engineer undo");server.waitFor(s->!session(s,"Alice").undoRequested && session(s,"Alice").journal.size()==Integer.parseInt(read("structural-original-journal")));
        server.runOnServer(s->{var project=session(s,"Alice");var before=Blueprint.parse(JsonParser.parseString(read("structural-before")).getAsJsonObject(),project.worldId,project.dimension);check(before.blocks().stream().allMatch(b->Blueprint.matches(b.state(),project.level().getBlockState(b.pos()))),"Production repeated-position undo restores the real previous layout after restart");check(project.level().getBlockState(new BlockPos(264,100,0)).is(Blocks.STONE) && project.level().getBlockState(new BlockPos(260,100,0)).is(Blocks.STONE),"Undo preserves both existing and newly adopted player supports");check(project.level().getBlockState(new BlockPos(264,101,0)).isAir(),"Undo removes the assistant's added output");});
        write("structural-recovery.done","passed");
    }
    private static void structuralReplanRecovery(ClientGameTestContext context,TestDedicatedServerContext server) throws Exception {
        var previousEpoch=JsonParser.parseString(read("server.json")).getAsJsonObject().get("serverEpoch").getAsString();
        server.runOnServer(s->{var manager=EngineerMod.managerFor(s);check(!manager.epoch.toString().equals(previousEpoch),"Restored-layout proof starts an actual fourth server process/epoch");var boot=new JsonObject();boot.addProperty("serverId",manager.serverId);boot.addProperty("serverEpoch",manager.epoch.toString());boot.addProperty("worldId",manager.worldId);write("restored-layout-server",boot.toString());});
        UUID selected=UUID.fromString(read("structural-project"));
        send(context,"engineer project use "+selected);
        server.waitFor(s->selected.equals(EngineerMod.managerFor(s).selections.get(player(s,"Alice").getUUID())));
        server.runCommand("tp Alice 262 103 6 180 20");server.waitFor(s->session(s,"Alice").dependencyPositions(true).stream().allMatch(pos->session(s,"Alice").level().hasChunkAt(pos)));
        server.runOnServer(s->{var project=session(s,"Alice");var before=JsonParser.parseString(read("structural-before")).getAsJsonObject();check(project.delegation==null && project.blueprint.blocks().size()==10 && project.structuralBefore().source().equals(before),"Actual fourth boot keeps true restored geometry separate from accepted intention and imports no lease");check(saved(project).has("restoredLayout"),"Undo persists the actual restored source before its operation scope is pruned");});
        server.runOnServer(s->{var project=session(s,"Alice");var corrupted=saved(project);corrupted.getAsJsonObject("restoredLayout").getAsJsonObject("before").addProperty("name","Corrupted original history");boolean denied=false;try{new EngineeringSession(project.manager,project.projectId,project.owner,project.dimension,corrupted);}catch(IllegalStateException expected){denied=true;}check(denied && project.delegation==null,"Corrupt restored history cannot load or import world authority");
            var next=project.blueprint.source().deepCopy();next.addProperty("id",UUID.randomUUID().toString());next.addProperty("revision",project.revision()+1);var stale=saved(project);stale.add("restoredLayout",StructuralTransition.between(project.structuralBefore(),Blueprint.parse(next,project.worldId,project.dimension),project.blueprint).json());denied=false;try{new EngineeringSession(project.manager,project.projectId,project.owner,project.dimension,stale);}catch(IllegalStateException expected){denied=true;}check(denied,"A valid transition digest cannot bind a different accepted canonical intention");});
        var expected=server.computeOnServer(s->session(s,"Alice").level().getBlockState(new BlockPos(261,101,0)));
        server.runCommand("setblock 261 101 0 minecraft:glass");
        MESSAGES.clear();send(context,"engineer design CI structural buffer: replace the two repeaters with three two-tick repeaters; keep 12/16 game-tick behavior.");expectChat(context,"Existing structure changed before planning");
        server.runOnServer(s->check(session(s,"Alice").level().getBlockState(new BlockPos(261,101,0)).is(Blocks.GLASS) && !session(s,"Alice").state.equals("PLANNING"),"Changed restored basis is preserved and rejected before sending a model request"));
        server.runCommand("setblock 261 101 0 "+Blueprint.describe(expected));
        int acceptedRevision=server.computeOnServer(s->session(s,"Alice").revision());
        MESSAGES.clear();send(context,"engineer design CI structural buffer: replace the two repeaters with three two-tick repeaters; keep 12/16 game-tick behavior.");expectChat(context,"/engineer confirm ");String confirmation=confirmationNonce();
        MESSAGES.clear();send(context,"engineer confirm "+confirmation);expectChat(context,"Structural proposal ready");
        var pattern=java.util.regex.Pattern.compile("/engineer apply ([0-9a-f-]{36})");String nonce=null;for(String message:MESSAGES){var match=pattern.matcher(message);if(match.find()){nonce=match.group(1);break;}}
        check(nonce!=null,"Replanning restored work still requires a separate owner application confirmation");send(context,"engineer apply "+nonce);
        server.waitFor(s->session(s,"Alice").state.equals("COMPLETED"));
        server.runOnServer(s->{var project=session(s,"Alice");var transition=project.operationScopes.transition(project.activeOperation);check(transition.before().source().equals(JsonParser.parseString(read("structural-before")).getAsJsonObject()) && transition.accepted().revision()==acceptedRevision && transition.after().revision()==acceptedRevision+1 && transition.json().get("schemaVersion").getAsInt()==2,"New revision advances current accepted context once without relabelling the older physical source");check(project.blueprint.blocks().stream().allMatch(b->Blueprint.matches(b.state(),project.level().getBlockState(b.pos()))),"A real second structural operation rebuilds the larger layout from the restored geometry");});
        send(context,"engineer test");server.waitFor(s->session(s,"Alice").verified.startsWith("passed:"));assertPersisted(server);
        send(context,"engineer undo");server.waitFor(s->!session(s,"Alice").undoRequested && session(s,"Alice").journal.size()==Integer.parseInt(read("structural-original-journal")));
        server.runOnServer(s->{var project=session(s,"Alice");check(project.structuralBefore().source().equals(JsonParser.parseString(read("structural-before")).getAsJsonObject()),"Repeated undo retains the true original source again");check(project.structuralBefore().blocks().stream().allMatch(b->Blueprint.matches(b.state(),project.level().getBlockState(b.pos()))) && project.level().getBlockState(new BlockPos(264,100,0)).is(Blocks.STONE),"Second inverse restores original world geometry and preserves adopted player support");var restored=saved(project).getAsJsonObject("restoredLayout");check(restored.get("schemaVersion").getAsInt()==2 && restored.getAsJsonObject("before").equals(JsonParser.parseString(read("structural-before")).getAsJsonObject()),"Repeated undo saves all distinct physical/accepted/proposed identities");});
        assertPersisted(server);
        // Fresh mirrors describe saved intentions and stale state, not new functional passes.
        var mirrors=server.computeOnServer(s->{var manager=EngineerMod.managerFor(s);return java.util.List.of("project","components-project","generated-project").stream().map(name->{var project=manager.projects.get(UUID.fromString(read(name)));return Companion.gateway(project,null,new JsonObject(),project.owner);}).toList();});
        for(var mirror:mirrors)check(mirror.get(30,java.util.concurrent.TimeUnit.SECONDS).get("protocol").getAsInt()==2,"Latest boot acknowledges fresh metadata mirrors before persistence verification");
        write("structural-replan-recovery.done","passed");
    }
    private static void workspaceExpansionFlow(ClientGameTestContext context,TestDedicatedServerContext server) throws Exception {
        String originalId=server.computeOnServer(s->session(s,"Alice").blueprint.id());int revision=server.computeOnServer(s->session(s,"Alice").revision());int journal=server.computeOnServer(s->session(s,"Alice").journal.size());
        MESSAGES.clear();send(context,"engineer expand 200 100 0 204 101 0");expectChat(context,"Workspace expanded");
        server.runOnServer(s->{var project=session(s,"Alice");check(project.corner1.equals(new BlockPos(200,100,0)) && project.corner2.equals(new BlockPos(204,101,0)),"Owner explicitly expands the existing workspace without clearing its first corner");check(project.blueprint.id().equals(originalId) && project.revision()==revision && project.journal.size()==journal && project.delegation==null,"Workspace expansion retains original intent/history and grants no execution");check(project.blueprint.blocks().stream().allMatch(b->Blueprint.matches(b.state(),project.level().getBlockState(b.pos()))),"Expansion alone changes no circuit blocks");});
        MESSAGES.clear();send(context,"engineer expand 201 100 0 204 101 0");expectChat(context,"retain the entire previous workspace");
        server.runOnServer(s->check(session(s,"Alice").corner1.equals(new BlockPos(200,100,0)) && session(s,"Alice").corner2.equals(new BlockPos(204,101,0)) && session(s,"Alice").journal.size()==journal,"Rejected shrinking preserves delegated history and corners"));
        write("workspace-expanded.done","passed");
    }
    private static void workspaceBusyExpansionFlow(ClientGameTestContext context,TestDedicatedServerContext server) throws Exception {
        send(context,"engineer project new");send(context,"engineer select1 240 100 0");send(context,"engineer select2 242 101 0");send(context,"engineer reference");
        server.waitFor(s->session(s,"Alice").blueprint!=null && session(s,"Alice").corner2.equals(new BlockPos(242,101,0)));
        server.runCommand("tp Alice 241 103 6 180 20");
        server.waitFor(s->session(s,"Alice").dependencyPositions(false).stream().allMatch(pos->session(s,"Alice").level().hasChunkAt(pos)));
        server.runOnServer(s->{var project=session(s,"Alice");project.commandActor=player(s,"Alice").getUUID();try {
            project.build();check(project.state.equals("EXECUTING"),"The rejection proof starts a genuine delegated build, not a fabricated status");
            boolean rejected=false;try{project.expandWorkspace(new BlockPos(240,100,0),new BlockPos(243,101,0));}catch(IllegalStateException expected){rejected=true;}
            check(rejected && project.corner2.equals(new BlockPos(242,101,0)) && project.state.equals("EXECUTING") && project.journal.isEmpty(),"Expansion cannot alter an active assignment or write its first block");project.pause("Workspace rejection proof stopped its genuine build");
        } finally {project.commandActor=null;}});
        write("workspace-busy-expansion.done","passed");
    }
    private static void generatedPlayerSection(ClientGameTestContext context,TestDedicatedServerContext server,TestDedicatedServerConnection connection) throws Exception {
        BlockPos target=server.computeOnServer(s->session(s,"Alice").blueprint.blocks().stream().filter(b->b.state().is(Blocks.STONE)).min(java.util.Comparator.comparingInt(b->b.pos().getY())).orElseThrow().pos());
        BlockPos scaffold=target.below();
        server.runOnServer(s->session(s,"Alice").level().setBlockAndUpdate(scaffold,Blocks.COBBLESTONE.defaultBlockState()));
        server.runCommand("tp Alice "+(target.getX()+1.5)+" "+(target.getY()+1)+" "+(target.getZ()+2.5)+" -90 45");connection.waitForClientboundPackets();
        var previous=server.computeOnServer(s->player(s,"Alice").getMainHandItem().copy());
        try {
        equipConstructionItem(context,server,net.minecraft.world.item.Items.STONE);
        context.runOnClient(client->client.gameMode.useItemOn(client.player,net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(scaffold).add(0,0.5,0),net.minecraft.core.Direction.UP,scaffold,false)));
        server.waitFor(s->session(s,"Alice").level().getBlockState(target).is(Blocks.STONE));
        context.runOnClient(client->{client.gameMode.startDestroyBlock(scaffold,net.minecraft.core.Direction.SOUTH);client.gameMode.stopDestroyBlock();});
        server.waitFor(s->session(s,"Alice").level().getBlockState(scaffold).isAir());
        server.runOnServer(s->check(session(s,"Alice").journal.isEmpty(),"The player's actual generated support is not an assistant edit"));
        } finally {
            server.runOnServer(s->{var actor=player(s,"Alice");actor.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,previous.copy());actor.inventoryMenu.broadcastChanges();
                check(net.minecraft.world.item.ItemStack.listMatches(java.util.List.of(previous),java.util.List.of(actor.getMainHandItem())),"Creative fixture supply restores the original held stack and components");});
        }
        write("player-generated-section.done","passed");
    }
    private static void generatedConfirmationSaveFailure(ClientGameTestContext context,TestDedicatedServerContext server,String request) throws Exception {
        send(context,"engineer project new");send(context,"engineer select1 220 100 0");send(context,"engineer select2 223 101 0");
        server.waitFor(s->session(s,"Alice").corner2!=null && session(s,"Alice").corner2.equals(new BlockPos(223,101,0)));
        MESSAGES.clear();send(context,request);expectChat(context,"/engineer confirm ");String nonce=confirmationNonce();
        Path file=server.computeOnServer(s->session(s,"Alice").file),backup=file.resolveSibling(file.getFileName()+".proof-backup");
        try {
            // A directory at the checkpoint path models an atomic-write failure without changing grants or authority.
            server.runOnServer(s->{try{Files.move(file,backup);Files.createDirectory(file);}catch(Exception failure){throw new AssertionError(failure);}});
            MESSAGES.clear();send(context,"engineer confirm "+nonce);expectChat(context,"Engineer:");
            server.runOnServer(s->{var project=session(s,"Alice");check(!project.state.equals("PLANNING") && project.blueprint==null && project.delegation==null,"Checkpoint failure before geometry dispatch restores the previous live state without granting authority");});
        } finally {server.runOnServer(s->{try{Files.delete(file);Files.move(backup,file);}catch(Exception failure){throw new AssertionError(failure);}});}
        MESSAGES.clear();send(context,"engineer confirm "+nonce);expectChat(context,"No current behavior proposal");
        write("generated-confirmation-save-failure.done","passed");
    }
    private static String confirmationNonce() {
        var pattern=java.util.regex.Pattern.compile("/engineer confirm ([0-9a-f-]{36})");
        for(String message:MESSAGES){var match=pattern.matcher(message);if(match.find())return match.group(1);}
        throw new AssertionError("Owner did not receive its private confirmation nonce");
    }
    private static void generatedConfirmationRecovery(ClientGameTestContext context,TestDedicatedServerContext server,TestDedicatedServerConnection connection) throws Exception {
        server.runOnServer(s->{var manager=EngineerMod.managerFor(s);var project=manager.projects.get(UUID.fromString(read("generated-project")));check(project!=null && GeneratedBlueprint.isGenerated(project.blueprint),"Restart retains generated provenance through strict native parsing");check(GeneratedBlueprint.behavior(project.blueprint).equals(new BehaviorSpec(12,16)) && project.delegation==null && project.verified.startsWith("stale") && project.corner2.equals(new BlockPos(204,101,0)),"Restart retains the expanded workspace and accepted behavior and invalidates earlier functional authority");});
        var checkpoint=server.computeOnServer(s->{var project=EngineerMod.managerFor(s).projects.get(UUID.fromString(read("generated-project")));project.checkpoint();return project.lastCheckpoint;});
        check(checkpoint.get(30,java.util.concurrent.TimeUnit.SECONDS).get("protocol").getAsInt()==2,"Restart reports generated intent against the new service/server epoch");
        if(LIVE_GENERATED) {
            send(context,"engineer project use "+read("generated-project"));server.waitFor(s->session(s,"Alice").projectId.toString().equals(read("generated-project")));
            server.runCommand("tp Alice 201 103 6 180 20");connection.waitForClientboundPackets();context.waitTicks(20);
            context.runOnClient(client->client.gui.hud.getChat().clearMessages(false));context.takeScreenshot(LIVE_STRUCTURAL?"engineer-live-structural-restarted-build":"engineer-live-generated-restarted-build");
        }
        if(LIVE_STRUCTURAL)server.runOnServer(s->{var project=session(s,"Alice");check(project.blueprint.source().equals(JsonParser.parseString(read("live-structural-after")).getAsJsonObject()) && project.blueprint.blocks().stream().allMatch(b->Blueprint.matches(b.state(),project.level().getBlockState(b.pos()))),"Actual game restart retains the exact live-model revision and real constructed blocks");write("live-structural-recovery.done","passed");});
        write("generated-confirmation-recovery.done","passed");
    }

    private static void editor(ClientGameTestContext context) throws Exception {
        connect(context, 1);
        awaitFile(context, "shared");
        String project = read("project");
        send(context, "engineer project use " + project);
        send(context, "engineer teach");
        await(context, () -> view(context) != null && view(context).projectId().equals(project), "Bob's shared preview");
        send(context, "engineer next");
        await(context, () -> view(context) != null && view(context).stage() == 1, "Bob's personal next step");
        write("bob-next", "ready");
        awaitFile(context, "build-bob");
        send(context, "engineer build");
        awaitFile(context, "revoked");
        await(context, () -> view(context) == null, "Revocation clears Bob's cached blueprint");
        send(context, "engineer resume");
        send(context, "engineer undo");
        context.waitTicks(8);
        check(view(context) == null, "Commands do not resurrect revoked overlay access");
        write("bob-revoked", "ready");
        awaitFile(context, "overlap-create");
        send(context, "engineer project new");
        send(context, "engineer select1 0 100 0");
        send(context, "engineer select2 2 101 0");
        send(context, "engineer reference");
        await(context, () -> view(context) != null && !view(context).projectId().equals(project), "Bob's independent overlapping project");
        send(context, "engineer project share Alice editor");
        send(context, "engineer project permission Alice undo true");
        await(context, () -> MESSAGES.stream().anyMatch(message -> message.contains("Project rights updated")), "Mutually authorized overlapping workspaces with explicit undo rights");
        write("bob-overlap-created", "ready");
        awaitFile(context, "prepare-restart");
        disconnect(context);
        write("bob-disconnected", "ready");
        awaitFile(context, "restarted");
        connect(context, 2);
        send(context, "engineer project use " + project);
        send(context, "engineer status");
        await(context, () -> view(context) != null && view(context).projectId().equals(project), "Bob's membership after server restart");
        write("bob-reconnected", "ready");
        awaitFile(context,"or-test");
        send(context,"engineer project use "+read("or-test"));
        await(context,()->view(context)!=null && view(context).projectId().equals(read("or-test")),"Bob's authorized OR mechanism");
        send(context,"engineer test");
        awaitFile(context,"or-revoked");
        await(context,()->view(context)==null,"Revocation clears OR metadata during a multi-input trial");
        write("bob-or-revoked","ready");
        awaitFile(context,"inventory-bob-project");
        MESSAGES.clear();send(context,"engineer project use "+read("inventory-bob-project"));
        send(context,"engineer inspect inventory");expectChat(context,"Private inventory remembered");expectChat(context,"diamond=4");
        check(MESSAGES.stream().noneMatch(message->message.contains("inventory-private-secret")),"Private inspection never reads custom item names");
        write("inventory-bob-private.done","passed");
        awaitFile(context,"inventory-bob-queue");
        MESSAGES.clear();send(context,"engineer inspect container");expectChat(context,"Open a vanilla container");write("inventory-bob-queued","ready");
        awaitFile(context,"inventory-bob-revoked");
        await(context,()->view(context)==null,"Private inventory grant revocation clears Bob's project view");
        context.runOnClient(client->{client.player.closeContainer();client.gameMode.useItemOn(client.player,net.minecraft.world.InteractionHand.MAIN_HAND,
            new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(new BlockPos(140,100,0)),net.minecraft.core.Direction.UP,new BlockPos(140,100,0),false));});
        context.waitTicks(6);
        check(MESSAGES.stream().noneMatch(message->message.contains("Private inventory remembered")),"Revoked container request never reveals items even when vanilla permits a normal open");
        write("inventory-revocation.done","passed");
        awaitFile(context,"lesson-bob-project");MESSAGES.clear();
        send(context,"engineer project use "+read("lesson-bob-project"));
        send(context,"engineer ask   EXPLAIN   the timing?!");expectChat(context,"Intended timing for component b: 2");expectChat(context,"not a measured latency");
        send(context,"engineer ask explain this part again");expectChat(context,"Lesson for component b");
        send(context,"engineer ask what are you working on");expectChat(context,"Assistant task: a");
        send(context,"engineer ask do not build component a");expectChat(context,"Please choose a lesson question");
        MESSAGES.clear();send(context,"engineer skip");send(context,"engineer previous");send(context,"engineer pause");expectChat(context,"Your tutorial is paused");
        write("lesson-bob.done","passed");
        awaitFile(context,"lesson-revoked");MESSAGES.clear();
        send(context,"engineer project use "+read("lesson-bob-project"));expectChat(context,"unavailable or not shared");
        MESSAGES.clear();send(context,"engineer ask explain component a");expectChat(context,"Engineer:");
        check(MESSAGES.stream().noneMatch(message->message.contains("Lesson for component a")),"A revoked lesson cannot reveal component facts");
        write("lesson-denied.done","passed");
        if(!LIVE_ACCEPTANCE || LIVE_GENERATED) {
            awaitFile(context,"generated-shared");MESSAGES.clear();send(context,"engineer project use "+read("generated-project"));
            await(context,()->view(context)!=null && view(context).projectId().equals(read("generated-project")),"Bob receives the generated preview only after a server grant");
            send(context,"engineer ask explain the timing");expectChat(context,"turning on after 12 game ticks and off after 16 game ticks");
            send(context,"engineer design CI generated buffer: on after 12 game ticks, off after 16 game ticks.");expectChat(context,"permission");
            write("generated-bob.done","passed");awaitFile(context,"workspace-bob-editable");MESSAGES.clear();send(context,"engineer expand 200 100 0 205 101 0");expectChat(context,"Only the project owner");write("workspace-bob-denied.done","passed");awaitFile(context,"generated-revoked");
            await(context,()->view(context)==null,"Generated preview is cleared when view access is revoked");write("generated-bob-revoked.done","passed");
        }
        awaitFile(context, "finish");
        disconnect(context);
    }

    private static void denied(ClientGameTestContext context) throws Exception {
        connect(context, 1);
        awaitFile(context, "shared");
        String project = read("project");
        send(context, "engineer project use " + project);
        send(context, "engineer project permission Alice build true");
        send(context, "engineer build");
        send(context, "engineer plan Make me a lamp");
        await(context, () -> MESSAGES.stream().anyMatch(message -> message.contains("not shared with you")), "Eve's denied project command");
        context.waitTicks(10);
        check(view(context) == null, "An unauthorized native client receives no project preview");
        write("eve-denied", "ready");
        disconnect(context);
    }

    private static void textOnly(ClientGameTestContext context) throws Exception {
        check(!ClientPreview.enabled(), "The text fallback client must not negotiate the overlay");
        awaitFile(context, "text-connect");
        connect(context, 1);
        write("eve-text-connected", "ready");
        awaitFile(context, "text-viewer");
        String project = read("project");
        send(context, "engineer project use " + project);
        send(context, "engineer status");
        send(context, "engineer repeat");
        await(context, () -> MESSAGES.stream().anyMatch(message -> message.contains("Project " + project)), "Status over ordinary Minecraft text packets");
        await(context, () -> MESSAGES.stream().anyMatch(message -> message.contains("Step ")), "Text tutorial without overlay negotiation");
        check(view(context) == null, "Text-only clients do not acquire visual metadata caches");
        disconnect(context);
    }

    private static Properties properties() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) { port = socket.getLocalPort(); }
        var result = new Properties();
        result.setProperty("online-mode", "false");
        result.setProperty("enforce-secure-profile", "false");
        result.setProperty("white-list", "false");
        result.setProperty("max-players", "8");
        result.setProperty("gamemode", "creative");
        result.setProperty("force-gamemode", "true");
        result.setProperty("spawn-protection", "0");
        result.setProperty("view-distance", "3");
        result.setProperty("simulation-distance", "3");
        result.setProperty("server-ip", "127.0.0.1");
        result.setProperty("server-port", Integer.toString(port));
        result.setProperty("level-name", "multiplayer-proof-world");
        return result;
    }

    private static void publishServer(TestDedicatedServerContext server, int generation) {
        int port = server.computeOnServer(MinecraftServer::getPort);
        var value = new JsonObject(); value.addProperty("port", port); value.addProperty("generation", generation);
        server.runOnServer(s->{var manager=EngineerMod.managerFor(s);value.addProperty("serverId",manager.serverId);value.addProperty("serverEpoch",manager.epoch.toString());value.addProperty("worldId",manager.worldId);});
        write("server.json", value.toString());
    }
    private static void assertPersisted(TestDedicatedServerContext server) throws Exception {
        var checkpoint=server.computeOnServer(s->session(s,"Alice").lastCheckpoint);
        check(checkpoint!=null,"The actual functional pass must generate a project checkpoint");
        var receipt=checkpoint.get(30,java.util.concurrent.TimeUnit.SECONDS);
        check(receipt.get("protocol").getAsInt()==2,"The real companion gateway must acknowledge the functional result");
    }
    /** Hand-authored raw geometry proves the production runner, not model inference. */
    private static void generatedTruthFlow(ClientGameTestContext context,TestDedicatedServerContext server) throws Exception {
        server.runCommand("tp Alice 402 103 6 180 20");server.runCommand("forceload add 384 -16 416 16");
        send(context,"engineer project new");send(context,"engineer select1 400 100 0");send(context,"engineer select2 405 102 3");
        server.waitFor(s->session(s,"Alice").corner2!=null && session(s,"Alice").corner2.equals(new BlockPos(405,102,3)));
        server.runOnServer(s->{
            var project=session(s,"Alice");JsonObject source;
            try(var stream=MultiplayerGameTests.class.getResourceAsStream("/fixtures/generated-and.json")) {
                if(stream==null)throw new IllegalStateException("Missing raw AND reference");
                source=JsonParser.parseString(new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            }catch(java.io.IOException invalid){throw new AssertionError(invalid);}
            source.addProperty("id",project.projectId.toString());source.addProperty("worldId",project.worldId);source.addProperty("dimension",project.dimension);
            source.add("anchor",Blueprint.jsonPos(new BlockPos(400,100,0)));project.accept(Blueprint.parse(source,project.worldId,project.dimension));
            check(project.journal.isEmpty() && project.delegation==null && project.verified.equals("untested"),"Raw AND preview grants no world execution or verification");
            // Synthetic existing player section: two supports and two differently powered levers.
            for(BlockPos pos:java.util.List.of(new BlockPos(403,101,0),new BlockPos(400,101,3)))project.level().setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());
            for(var port:GeneratedBlueprint.contract(project.blueprint).ports())if(port.input()) {
                var planned=project.blueprint.blocks().stream().filter(b->b.pos().equals(port.pos())).findFirst().orElseThrow();
                project.level().setBlockAndUpdate(port.pos(),planned.state().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED,port.name().equals("a")));
            }
            write("truth-project",project.projectId.toString());
        });
        send(context,"engineer build");server.waitFor(s->session(s,"Alice").state.equals("COMPLETED"));
        server.runOnServer(s->check(session(s,"Alice").journal.size()==9,"AI remainder excludes four existing player-section blocks"));
        send(context,"engineer test");server.waitFor(s->session(s,"Alice").verified.startsWith("passed:generated-truth-table:"),30_000);assertPersisted(server);
        server.runOnServer(s->{var project=session(s,"Alice");FunctionalTrial.validateGeneratedEvidence(project.trial.evidence(),GeneratedBlueprint.behavior(project.blueprint),true);assertTruthInputs(project);check(!project.hasPendingTest() && project.delegation==null,"Truth pass restores all originals and releases authority");});
        send(context,"engineer project share Bob editor");server.waitFor(s->session(s,"Alice").access.allows(player(s,"Bob").getUUID(),ProjectAccess.Permission.BUILD));
        send(context,"engineer test");
        server.waitFor(s->{var project=session(s,"Alice");
            if(!project.state.equals("VERIFYING") || project.trial.evidence().size()<4)return false;
            commandAs(s,"Alice","engineer project revoke Bob");return true;
        },30_000);
        server.runOnServer(s->{var project=session(s,"Alice");check(project.state.equals("PAUSED") && project.delegation==null && project.hasPendingTest(),"Revocation pauses the live truth runner with original inputs retained");
            check(saved(project).getAsJsonArray("testInputs").size()==2,"Both original named inputs survive interruption");
            var loaded=new EngineeringSession(project.manager,project.projectId,project.owner,project.dimension);
            check(loaded.delegation==null && loaded.hasPendingTest(),"Deserialized truth evidence grants no authority");loaded.restoreTest();
            check(loaded.hasPendingTest(),"Deserialization cannot restore live inputs without a fresh lease");
        });
        write("truth-interrupted.done","passed");
    }
    private static void assertTruthInputs(EngineeringSession project) {
        for(var port:GeneratedBlueprint.contract(project.blueprint).ports())if(port.input())
            check(project.level().getBlockState(port.pos()).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED)==port.name().equals("a"),"Every named input returns to its own original state");
    }
    private static void generatedTruthRecovery(ClientGameTestContext context,TestDedicatedServerContext server) throws Exception {
        server.runCommand("tp Alice 402 103 6 180 20");send(context,"engineer project use "+read("truth-project"));
        server.waitFor(s->read("truth-project").equals(String.valueOf(EngineerMod.managerFor(s).selections.get(player(s,"Alice").getUUID()))));
        server.runOnServer(s->{var project=session(s,"Alice");check(project.state.equals("PAUSED") && project.delegation==null && project.hasPendingTest(),"Actual third boot retains both truth input journals and no lease");FunctionalTrial.validateGeneratedEvidence(project.trial.evidence(),GeneratedBlueprint.behavior(project.blueprint),false);});
        send(context,"engineer resume");server.waitFor(s->!session(s,"Alice").hasPendingTest());
        server.runOnServer(s->assertTruthInputs(session(s,"Alice")));
        send(context,"engineer test");server.waitFor(s->session(s,"Alice").verified.startsWith("passed:generated-truth-table:"),30_000);assertPersisted(server);
        server.runOnServer(s->{var project=session(s,"Alice");assertTruthInputs(project);check(project.delegation==null && !project.hasPendingTest(),"Fresh post-restart truth trial releases restored input authority");});
        write("truth-recovery.done","passed");
    }

    private static void catalogFlow(ClientGameTestContext context,TestDedicatedServerContext server) throws Exception {
        send(context,"engineer project new");send(context,"engineer select1 10 100 0");send(context,"engineer select2 12 101 2");
        send(context,"engineer reference or-gate 4");
        server.waitFor(s->session(s,"Alice").blueprint!=null && session(s,"Alice").blueprint.blocks().size()==10);
        send(context,"engineer build");server.waitFor(s->session(s,"Alice").state.equals("COMPLETED"));
        server.runOnServer(s->{for(int version:new int[]{1,2,3})assertLegacyScopeMigration(session(s,"Alice"),version);});
        send(context,"engineer project share Bob editor");
        server.waitFor(s->session(s,"Alice").access.allows(player(s,"Bob").getUUID(),ProjectAccess.Permission.BUILD));
        write("or-test",server.computeOnServer(s->session(s,"Alice").projectId.toString()));
        server.waitFor(s->{
            var project=session(s,"Alice");var actual=project.level().getBlockState(new BlockPos(10,101,2));
            if(!project.state.equals("VERIFYING") || !actual.is(Blocks.LEVER) || !actual.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED))return false;
            project.validateMutation(ProjectAccess.Permission.BUILD,new BlockPos(10,101,2));
            boolean unreservedRejected=false;
            try {project.validateMutation(ProjectAccess.Permission.BUILD,new BlockPos(11,101,1));}
            catch(IllegalStateException outsideLease){unreservedRejected=true;}
            check(unreservedRejected,"A live functional-test lease refuses an unreserved air block inside its read area");
            check(project.level().getBlockState(new BlockPos(11,101,1)).isAir(),"Rejected target validation leaves the world untouched");
            commandAs(s,"Alice","engineer project revoke Bob");return true;
        });
        server.runOnServer(s->{
            var project=session(s,"Alice");
            try {var checkpoint=JsonParser.parseString(Files.readString(project.file)).getAsJsonObject();
                check(project.state.equals("PAUSED") && checkpoint.getAsJsonArray("testInputs").size()==2,"Revocation retains both input originals without authority to restore them");
                var loaded=new EngineeringSession(project.manager,project.projectId,project.owner,project.dimension);
                check(loaded.hasPendingTest() && loaded.delegation==null && loaded.state.equals("PAUSED"),"Deserializing both input journals never imports authority");
                loaded.restoreTest();
                check(loaded.hasPendingTest() && project.level().getBlockState(new BlockPos(10,101,2)).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED),"A loaded checkpoint cannot restore world inputs without a fresh delegation");
            }catch(java.io.IOException error){throw new AssertionError(error);}
        });
        write("or-revoked","ready");awaitFile(context,"bob-or-revoked");
        send(context,"engineer resume");
        server.waitFor(s->{try{return !JsonParser.parseString(Files.readString(session(s,"Alice").file)).getAsJsonObject().has("testOriginal");}catch(java.io.IOException error){throw new AssertionError(error);}});
        server.runOnServer(s->{for(BlockPos p:java.util.List.of(new BlockPos(10,101,2),new BlockPos(12,101,0)))check(!session(s,"Alice").level().getBlockState(p).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED),"Explicit takeover restores each original OR input");});
        send(context,"engineer test");server.waitFor(s->session(s,"Alice").verified.startsWith("passed:"));assertPersisted(server);
        send(context,"engineer project new");send(context,"engineer select1 20 100 0");send(context,"engineer select2 22 101 2");
        send(context,"engineer reference not-gate");
        server.waitFor(s->session(s,"Alice").blueprint!=null && session(s,"Alice").blueprint.blocks().size()==4);
        send(context,"engineer rotate 90");server.waitFor(s->session(s,"Alice").blueprint.rotation()==90);
        server.runOnServer(s->{var project=session(s,"Alice");project.level().setBlockAndUpdate(new BlockPos(20,100,0),Blocks.STONE.defaultBlockState());var lever=project.blueprint.blocks().stream().filter(b->b.state().is(Blocks.LEVER)).findFirst().orElseThrow();project.level().setBlockAndUpdate(lever.pos(),lever.state().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED,true));});
        send(context,"engineer build");server.waitFor(s->session(s,"Alice").state.equals("COMPLETED"));
        server.runOnServer(s->check(session(s,"Alice").journal.size()==2,"Inverter handoff preserves the player's support and initially powered lever"));
        send(context,"engineer test");server.waitFor(s->session(s,"Alice").verified.startsWith("passed:"));assertPersisted(server);
        server.runOnServer(s->check(session(s,"Alice").level().getBlockState(new BlockPos(20,101,0)).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED),"Functional testing restores the player's original ON input"));
        send(context,"engineer undo");server.waitFor(s->session(s,"Alice").journal.isEmpty());
        send(context,"engineer reference signal-lamp");server.waitFor(s->session(s,"Alice").blueprint.blocks().size()==6);
        server.runOnServer(s->check(session(s,"Alice").trial.evidence().isEmpty() && session(s,"Alice").verified.equals("untested"),"A new intended revision cannot relabel the preceding mechanism's functional evidence"));
        server.runOnServer(s->{var project=session(s,"Alice");try(var files=Files.list(project.file.getParent())){
            var archived=files.filter(path->path.getFileName().toString().startsWith(project.projectId+".evidence-")).findFirst().orElseThrow();
            var data=JsonParser.parseString(Files.readString(archived)).getAsJsonObject();
            check(data.get("blueprintRevision").getAsInt()<project.revision() && data.getAsJsonArray("cases").size()==3,"Previous test vectors remain privately archived under their original intended revision");
            check(data.getAsJsonObject("blueprint").getAsJsonObject("provenance").get("template").getAsString().equals("not-gate"),"Offline history retains the actual tested blueprint, not only an unresolvable revision number");
        }catch(java.io.IOException error){throw new AssertionError(error);}});
    }
    private static void inspectPersisted(ClientGameTestContext context,TestDedicatedServerContext server) throws Exception {
        var previous=server.computeOnServer(s->session(s,"Alice").lastObservation);
        send(context,"engineer inspect");
        server.waitFor(s->session(s,"Alice").lastObservation!=previous);
        var receipt=server.computeOnServer(s->session(s,"Alice").lastObservation).get(30,java.util.concurrent.TimeUnit.SECONDS);
        check(receipt.get("protocol").getAsInt()==2,"Actual dedicated-world observations must reach the private companion");
    }
    private static JsonObject saved(EngineeringSession project){try{return JsonParser.parseString(Files.readString(project.file)).getAsJsonObject();}catch(java.io.IOException error){throw new AssertionError(error);}}
    private static void expectChat(ClientGameTestContext context,String text){expectChat(context,text,20_000);}
    private static void expectChat(ClientGameTestContext context,String text,long timeoutMillis){
        long deadline=System.currentTimeMillis()+timeoutMillis;
        while(System.currentTimeMillis()<deadline){if(MESSAGES.stream().anyMatch(message->message.contains(text)))return;context.waitTicks(1);}
        throw new AssertionError("Expected native command response: "+text+"; received "+MESSAGES);
    }
    private static void diagnoseReady(ClientGameTestContext context){MESSAGES.clear();send(context,"engineer diagnose");expectChat(context,"Repair proposal ready");}
    private static RepairRecovery repairFlow(ClientGameTestContext context,TestDedicatedServerContext server) throws Exception {
        final var relay=new BlockPos(21,101,0);final var input=new BlockPos(20,101,0);
        server.runCommand("tp Alice 21 102 3 180 20");
        server.runOnServer(s->{var project=session(s,"Alice");check(project.journal.isEmpty() && project.hasBuiltStructure(),"Player construction constrains revisions even without any assistant journal");});
        diagnoseReady(context);
        server.runCommand("setblock 21 100 0 minecraft:glass");
        MESSAGES.clear();send(context,"engineer repair");expectChat(context,"World changed");
        server.runOnServer(s->check(session(s,"Alice").journal.isEmpty() && session(s,"Alice").level().getBlockState(new BlockPos(21,100,0)).is(Blocks.GLASS),"A later player block invalidates the exact diagnosis without a repair write"));
        MESSAGES.clear();send(context,"engineer diagnose");expectChat(context,"Repair blocked");
        MESSAGES.clear();send(context,"engineer repair");expectChat(context,"Repair blocked");
        server.runCommand("setblock 21 100 0 minecraft:air");server.runCommand("setblock 21 101 0 minecraft:chest");
        MESSAGES.clear();send(context,"engineer diagnose");expectChat(context,"protected block entity");
        MESSAGES.clear();send(context,"engineer repair");expectChat(context,"Repair blocked");
        server.runOnServer(s->check(session(s,"Alice").level().getBlockState(relay).is(Blocks.CHEST) && session(s,"Alice").journal.isEmpty(),"Diagnosis does not authorize replacing an inventory"));
        server.runCommand("setblock 21 101 0 minecraft:air");server.runCommand("setblock 20 99 -1 minecraft:stone");
        MESSAGES.clear();send(context,"engineer diagnose");expectChat(context,"Occupied isolation margin");
        server.runCommand("setblock 20 99 -1 minecraft:air");
        send(context,"engineer project share Bob editor");server.waitFor(s->session(s,"Alice").access.allows(player(s,"Bob").getUUID(),ProjectAccess.Permission.BUILD));
        diagnoseReady(context);
        server.runOnServer(s->{var project=session(s,"Alice");commandAs(s,"Bob","engineer project use "+project.projectId);commandAs(s,"Bob","engineer repair");check(project.journal.isEmpty(),"Bob cannot consume Alice's diagnosis");commandAs(s,"Bob","engineer diagnose");commandAs(s,"Alice","engineer project revoke Bob");commandAs(s,"Bob","engineer repair");check(project.journal.isEmpty(),"Revoked diagnosis cannot delegate a correction");});
        diagnoseReady(context);send(context,"engineer repair");
        server.waitFor(s->{var project=session(s,"Alice");if(project.journal.isEmpty())return false;var actual=project.level().getBlockState(input);((net.minecraft.world.level.block.LeverBlock)actual.getBlock()).pull(actual,project.level(),input,player(s,"Alice"));return true;});
        server.waitFor(s->session(s,"Alice").state.equals("PAUSED"));
        server.runOnServer(s->check(session(s,"Alice").journal.size()==1 && !session(s,"Alice").level().getBlockState(input).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED),"A human input toggle pauses repair before the next target and remains OFF"));
        diagnoseReady(context);send(context,"engineer repair");server.waitFor(s->session(s,"Alice").state.equals("COMPLETED"));
        server.runOnServer(s->{var project=session(s,"Alice");check(project.journal.size()==5,"Repair changes only missing/incorrect parts and retains the earlier partial operation");check(project.level().getBlockState(new BlockPos(20,100,0)).is(Blocks.STONE) && !project.level().getBlockState(input).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED),"Player support and latest input state are preserved");});
        send(context,"engineer test");server.waitFor(s->session(s,"Alice").verified.startsWith("passed:"));assertPersisted(server);
        int revision=server.computeOnServer(s->session(s,"Alice").revision());
        send(context,"engineer revise 4");server.waitFor(s->session(s,"Alice").revision()==revision+1);
        server.runOnServer(s->check(session(s,"Alice").level().getBlockState(relay).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DELAY)==1 && session(s,"Alice").journal.size()==5,"A bounded intent revision preserves all actual blocks until explicit correction"));
        diagnoseReady(context);expectChat(context,"required delay is 4");send(context,"engineer repair");server.waitFor(s->session(s,"Alice").state.equals("COMPLETED"));
        server.runOnServer(s->check(session(s,"Alice").journal.size()==6 && session(s,"Alice").level().getBlockState(relay).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DELAY)==4,"Delay revision corrects only its repeater"));
        send(context,"engineer test");server.waitFor(s->session(s,"Alice").verified.startsWith("passed:"));assertPersisted(server);
        send(context,"engineer undo");server.waitFor(s->session(s,"Alice").journal.size()==5);
        server.runOnServer(s->check(session(s,"Alice").level().getBlockState(relay).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DELAY)==1 && session(s,"Alice").level().getBlockState(new BlockPos(21,100,0)).is(Blocks.STONE),"Undo reverses just the last repair, preserving earlier construction"));
        diagnoseReady(context);send(context,"engineer repair");server.waitFor(s->session(s,"Alice").state.equals("COMPLETED"));
        String target=server.computeOnServer(s->{var journal=saved(session(s,"Alice")).getAsJsonArray("journal");return journal.get(journal.size()-1).getAsJsonObject().get("operation").getAsString();});
        var recovery=server.computeOnServer(s->{var project=session(s,"Alice");
            check(project.beginUndo() && project.undoOperation.equals(target),"The real undo WAL selects this exact repair operation");
            check(project.applyUndoEntriesReserved(),"The production undo phase applies its recorded inverse changes");
            check(project.undoRequested && project.journal.size()==5 && project.journal.stream().noneMatch(change->change.operation().equals(target)),"Last-entry flush retains an explicit empty target before completion");
            project.manager.release(project);
            return new RepairRecovery(project.file,saved(project),project.projectId.toString(),project.journal.size());});
        write("repair-project",recovery.project());return recovery;
    }

    private static void legacyRevisionFlow(ClientGameTestContext context,TestDedicatedServerContext server) throws Exception {
        server.runCommand("tp Alice 41 102 3 180 20");
        var previous=server.computeOnServer(s->session(s,"Alice").projectId);
        send(context,"engineer project new");server.waitFor(s->!session(s,"Alice").projectId.equals(previous));
        send(context,"engineer select1 40 100 0");send(context,"engineer select2 42 101 0");
        send(context,"engineer reference signal-lamp");server.waitFor(s->session(s,"Alice").blueprint!=null && session(s,"Alice").blueprint.anchor().equals(new BlockPos(40,100,0)));
        server.runOnServer(s->{var project=session(s,"Alice");
            var source=project.blueprint.source().deepCopy();source.remove("parameters");source.getAsJsonObject("provenance").remove("catalogRevision");
            source.getAsJsonArray("blocks").get(4).getAsJsonObject().addProperty("component","output");
            project.commandActor=player(s,"Alice").getUUID();
            try{project.accept(Blueprint.parse(source,project.worldId,project.dimension));}finally{project.commandActor=null;}
            for(var block:project.blueprint.blocks())project.level().setBlockAndUpdate(block.pos(),block.state());
            check(project.journal.isEmpty() && project.hasBuiltStructure(),"Historical player-built lamp is construction without assistant edits");
        });
        int revision=server.computeOnServer(s->session(s,"Alice").revision());
        send(context,"engineer revise 4");server.waitFor(s->session(s,"Alice").revision()==revision+1);
        server.runOnServer(s->{var project=session(s,"Alice");
            check(project.blueprint.source().getAsJsonObject("parameters").get("delay").getAsInt()==4
                && project.level().getBlockState(new BlockPos(41,101,0)).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DELAY)==1
                && project.journal.isEmpty(),"Legacy delay revision migrates intent while preserving all player blocks");
            var original=project.blueprint;var first=project.corner1;var second=project.corner2;
            var far=new BlockPos(20_000_000,100,0);check(!project.level().hasChunkAt(far),"Unknown construction fixture must actually be unloaded");
            try{
                project.blueprint=MechanismCatalog.create("signal-lamp",project.worldId,project.dimension,far,0,1);
                project.corner1=far;project.corner2=far.offset(2,1,0);
                boolean rejected=false;try{project.hasBuiltStructure();}catch(IllegalStateException unknown){rejected=true;}
                check(rejected,"Unloaded player construction cannot be classified as an empty design");
            }finally{project.blueprint=original;project.corner1=first;project.corner2=second;}
        });
        diagnoseReady(context);
        MESSAGES.clear();send(context,"engineer component claim root");expectChat(context,"Component root assigned to you");
        MESSAGES.clear();send(context,"engineer repair");expectChat(context,"No fresh diagnosis");
        server.runOnServer(s->check(session(s,"Alice").journal.isEmpty() && session(s,"Alice").level().getBlockState(new BlockPos(41,101,0)).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DELAY)==1,"Handoff invalidates an unchanged-world repair proposal without placing anything"));
    }

    private static void componentFlow(ClientGameTestContext context,TestDedicatedServerContext server) throws Exception {
        server.runCommand("tp Alice 73 108 15 180 25");
        var previous=server.computeOnServer(s->session(s,"Alice").projectId);
        send(context,"engineer project new");server.waitFor(s->!session(s,"Alice").projectId.equals(previous));
        send(context,"engineer select1 60 100 0");send(context,"engineer select2 78 101 0");
        MESSAGES.clear();send(context,"engineer component add a root signal-lamp 60 100 0");expectChat(context,"Added component a");
        MESSAGES.clear();send(context,"engineer component group wing root");expectChat(context,"Added component wing");
        MESSAGES.clear();send(context,"engineer component add b wing signal-lamp 68 100 0");expectChat(context,"Added component b");
        MESSAGES.clear();send(context,"engineer component add c wing signal-lamp 76 100 0");expectChat(context,"Added component c");
        MESSAGES.clear();send(context,"engineer component depend c b");expectChat(context,"Component c requires b");
        server.runOnServer(s->check(session(s,"Alice").blueprint.blocks().size()==18,"A nested project contains all three actual mechanism geometries"));
        String composedId=server.computeOnServer(s->session(s,"Alice").projectId.toString());
        await(context,()->view(context)!=null && view(context).projectId().equals(composedId) && view(context).blocks().size()==18,"The actual composed-project preview contains every leaf");
        context.runOnClient(client->client.gui.hud.getChat().clearMessages(false));context.takeScreenshot("engineer-components-shared-preview");
        MESSAGES.clear();send(context,"engineer component build c");expectChat(context,"Required component b is incomplete");
        server.runOnServer(s->check(session(s,"Alice").journal.isEmpty(),"Missing prerequisite blocks delegation before any write"));
        MESSAGES.clear();send(context,"engineer component claim b");expectChat(context,"Component b assigned to you");
        send(context,"engineer component build a");
        server.waitFor(s->{var project=session(s,"Alice");if(project.journal.isEmpty())return false;
            project.level().setBlockAndUpdate(new BlockPos(68,100,0),Blocks.STONE.defaultBlockState());return true;});
        server.waitFor(s->session(s,"Alice").state.equals("COMPLETED"));
        server.runOnServer(s->{var project=session(s,"Alice");
            check(project.journal.size()==6,"Assistant builds only component a while the player works in b");
            check(project.level().getBlockState(new BlockPos(68,100,0)).is(Blocks.STONE)
                && project.level().getBlockState(new BlockPos(69,100,0)).isAir(),"Independent player work is preserved without extending assistant delegation");
            check(project.level().getBlockState(new BlockPos(62,101,0)).is(Blocks.REDSTONE_LAMP),"Actual component a finishes despite the independent player edit");
        });
        send(context,"engineer test");server.waitFor(s->session(s,"Alice").verified.startsWith("passed:"));assertPersisted(server);
        MESSAGES.clear();send(context,"engineer component list");expectChat(context,"Component a");expectChat(context,"Component b");expectChat(context,"Component wing | group");
        MESSAGES.clear();send(context,"engineer component build b");
        server.waitFor(s->{if(session(s,"Alice").journal.size()<=6)return false;
            commandAs(s,"Alice","engineer component claim b");return true;});
        expectChat(context,"Component b assigned to you");
        server.waitFor(s->session(s,"Alice").state.equals("PAUSED"));
        int retained=server.computeOnServer(s->session(s,"Alice").journal.size());
        server.runOnServer(s->{var project=session(s,"Alice");
            var next=project.blueprint.blocks().stream().filter(block->block.pos().getX()>=68 && project.level().getBlockState(block.pos()).isAir()).findFirst().orElseThrow();
            check(next.state().canSurvive(project.level(),next.pos()),"Player takeover continues the next buildable component part");
            project.level().setBlockAndUpdate(next.pos(),next.state());
            check(project.journal.size()==retained,"Human takeover retains every assistant journal entry");
        });
        assertPersisted(server);
        write("companion-restart.request","ready");awaitFile(context,"companion-restart.done");
        var restart=JsonParser.parseString(read("companion-restart.done")).getAsJsonObject();
        check(restart.get("oldPid").getAsInt()>0 && restart.get("newPid").getAsInt()>0 && restart.get("oldPid").getAsInt()!=restart.get("newPid").getAsInt(),"Harness must stop the actual companion and start a distinct process");
        server.runOnServer(s->{var project=session(s,"Alice");
            check(project.journal.size()==retained && project.delegation==null && project.assignments.task(project.blueprint,"b").executor.equals("player"),"Companion restart preserves paused handoff progress without granting authority");
            project.checkpoint();
        });
        assertPersisted(server);write("companion-recovery.done","passed");
        send(context,"engineer component build b");server.waitFor(s->session(s,"Alice").state.equals("COMPLETED"));
        server.runOnServer(s->{var project=session(s,"Alice");
            check(project.level().getBlockState(new BlockPos(70,101,0)).is(Blocks.REDSTONE_LAMP)
                && project.level().getBlockState(new BlockPos(62,101,0)).is(Blocks.REDSTONE_LAMP),"Delegate-back finishes b while retaining a");
            check(project.journal.size()==10,"Delegate-back retains old work and excludes both player-placed supports");
        });
        send(context,"engineer test");server.waitFor(s->session(s,"Alice").verified.startsWith("passed:"));assertPersisted(server);
        final var prerequisiteInput=new BlockPos(68,101,0);
        var originalInput=server.computeOnServer(s->session(s,"Alice").level().getBlockState(prerequisiteInput));
        send(context,"engineer component build c");
        server.waitFor(s->{var project=session(s,"Alice");if(project.journal.size()<=10)return false;
            project.level().setBlockAndUpdate(prerequisiteInput,originalInput.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING,net.minecraft.core.Direction.NORTH));return true;});
        server.waitFor(s->session(s,"Alice").state.equals("PAUSED"));
        server.waitFor(s->session(s,"Alice").assignments.task(session(s,"Alice").blueprint,"b").verified.startsWith("stale:"));
        server.runOnServer(s->{var project=session(s,"Alice");
            check(project.journal.size()==11 && project.level().getBlockState(prerequisiteInput).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)==net.minecraft.core.Direction.NORTH,"Changed prerequisite port pauses c before its next write and preserves the player edit");
            check(project.assignments.task(project.blueprint,"a").verified.startsWith("passed:"),"Unrelated component a keeps its functional result");
            project.level().setBlockAndUpdate(prerequisiteInput,originalInput);
        });
        send(context,"engineer component build c");server.waitFor(s->session(s,"Alice").state.equals("COMPLETED"));
        server.runOnServer(s->check(session(s,"Alice").journal.size()==16,"Fresh delegation retains the partial operation while finishing only c"));
        send(context,"engineer test");server.waitFor(s->session(s,"Alice").verified.startsWith("passed:"));assertPersisted(server);
        await(context,()->view(context)!=null && view(context).blocks().size()==18 && view(context).blocks().stream().allMatch(ProjectView.Ghost::matches),"All composed geometry is visibly complete");
        context.runOnClient(client->client.gui.hud.getChat().clearMessages(false));context.takeScreenshot("engineer-components-constructed");
        // Only the distant prerequisite footprint intersects Bob's workspace.
        server.runOnServer(s->{commandAs(s,"Alice","engineer project share Bob editor");commandAs(s,"Bob","engineer project new");
            commandAs(s,"Bob","engineer select1 68 100 0");commandAs(s,"Bob","engineer select2 70 101 0");commandAs(s,"Bob","engineer project share Alice editor");
            var bobProject=session(s,"Bob");check(bobProject.owner.equals(player(s,"Bob").getUUID()) && bobProject.contains(new BlockPos(68,100,0)) && bobProject.access.allows(player(s,"Alice").getUUID(),ProjectAccess.Permission.BUILD),"Distant prerequisite workspace is actually owned by Bob and initially grants Alice build rights");});
        send(context,"engineer test");
        server.waitFor(s->{var project=session(s,"Alice");if(project.testTick<0 || !project.level().getBlockState(new BlockPos(76,101,0)).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED))return false;
            commandAs(s,"Bob","engineer project revoke Alice");check(!session(s,"Bob").access.allows(player(s,"Alice").getUUID(),ProjectAccess.Permission.BUILD),"The dependency grant was actually revoked");return true;});
        server.waitFor(s->session(s,"Alice").state.equals("PAUSED") && session(s,"Alice").hasPendingTest());
        server.runOnServer(s->{var project=session(s,"Alice");check(project.level().getBlockState(new BlockPos(76,101,0)).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED),"Revoked distant dependency grant prevents further input changes, including restoration under expired authority");commandAs(s,"Bob","engineer project share Alice editor");});
        send(context,"engineer resume");server.waitFor(s->!session(s,"Alice").hasPendingTest());
        server.runOnServer(s->check(!session(s,"Alice").level().getBlockState(new BlockPos(76,101,0)).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED),"Fresh cross-project grants and explicit resume safely restore the original input"));
        send(context,"engineer test");server.waitFor(s->session(s,"Alice").verified.startsWith("passed:"));assertPersisted(server);
        // A distant prerequisite's empty margin is part of C's live read contract too.
        final var farMargin=new BlockPos(66,100,0);
        send(context,"engineer test");
        server.waitFor(s->{var project=session(s,"Alice");if(project.testTick<0 || !project.level().getBlockState(new BlockPos(76,101,0)).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED))return false;
            check(project.level().getBlockState(farMargin).isAir(),"The distant prerequisite margin starts empty");
            project.level().setBlockAndUpdate(farMargin,Blocks.STONE.defaultBlockState());return true;});
        server.waitFor(s->session(s,"Alice").state.equals("PAUSED") && session(s,"Alice").hasPendingTest());
        server.runOnServer(s->{var project=session(s,"Alice");check(project.level().getBlockState(new BlockPos(76,101,0)).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED)
                && !project.verified.startsWith("passed:") && project.assignments.task(project.blueprint,"a").verified.startsWith("passed:"),"A far read-margin edit blocks the next test input mutation/restoration and preserves unrelated a");
            project.level().setBlockAndUpdate(farMargin,Blocks.AIR.defaultBlockState());});
        send(context,"engineer resume");server.waitFor(s->!session(s,"Alice").hasPendingTest());
        server.runOnServer(s->check(!session(s,"Alice").level().getBlockState(new BlockPos(76,101,0)).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED),"Explicit fresh recovery restores the input after the distant margin is clear"));
        send(context,"engineer test");server.waitFor(s->session(s,"Alice").verified.startsWith("passed:"));assertPersisted(server);
        server.runOnServer(s->session(s,"Alice").level().setBlockAndUpdate(farMargin,Blocks.STONE.defaultBlockState()));
        server.waitFor(s->session(s,"Alice").assignments.task(session(s,"Alice").blueprint,"b").verified.startsWith("stale:")
                && session(s,"Alice").assignments.task(session(s,"Alice").blueprint,"c").verified.startsWith("stale:"));
        server.runOnServer(s->{var project=session(s,"Alice");check(project.assignments.task(project.blueprint,"a").verified.startsWith("passed:")
                && !project.level().getBlockState(new BlockPos(76,101,0)).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED),"An idle distant-margin edit stales only B/C without changing inputs");
            project.level().setBlockAndUpdate(farMargin,Blocks.AIR.defaultBlockState());});
        send(context,"engineer test");server.waitFor(s->session(s,"Alice").verified.startsWith("passed:"));assertPersisted(server);
        write("components-project",server.computeOnServer(s->session(s,"Alice").projectId.toString()));
        write("components-independent.done","passed");
    }

    private static JsonObject assignment(EngineeringSession project,String id) {
        return project.assignments.json().asList().stream().map(value->value.getAsJsonObject()).filter(value->value.get("id").getAsString().equals(id)).findFirst().orElseThrow();
    }
    private static void componentRevisionFlow(ClientGameTestContext context,TestDedicatedServerContext server) throws Exception {
        server.runCommand("tp Alice 113 108 15 180 25");
        var previous=server.computeOnServer(s->session(s,"Alice").projectId);
        send(context,"engineer project new");server.waitFor(s->!session(s,"Alice").projectId.equals(previous));
        send(context,"engineer select1 100 100 0");send(context,"engineer select2 118 101 0");
        for(var spec:java.util.List.of("a root signal-lamp 100 100 0","b root signal-lamp 108 100 0","c root signal-lamp 116 100 0")) {
            MESSAGES.clear();send(context,"engineer component add "+spec);expectChat(context,"Added component "+spec.charAt(0));
        }
        MESSAGES.clear();send(context,"engineer component depend c b");expectChat(context,"Component c requires b");
        for(String id:java.util.List.of("a","b","c")) {
            MESSAGES.clear();send(context,"engineer component build "+id);
            if(id.equals("b"))server.waitFor(s->{var project=session(s,"Alice");if(!project.activeComponent.equals("b") || project.journal.size()<=6)return false;
                commandAs(s,"Alice","engineer component diagnose a");
                check(project.activeComponent.equals("b") && project.state.equals("EXECUTING"),"Read-only diagnosis of a cannot change or pause active b");return true;});
            if(id.equals("b"))expectChat(context,"Geometry matches");
            server.waitFor(s->session(s,"Alice").activeComponent.equals(id) && session(s,"Alice").state.equals("COMPLETED"));
            MESSAGES.clear();send(context,"engineer component test "+id);expectChat(context,"truth table running");
            server.waitFor(s->session(s,"Alice").activeComponent.equals(id) && session(s,"Alice").verified.startsWith("passed:"));assertPersisted(server);
        }
        // Keep the unaffected leaf active so revision must archive its buffer without relabelling it.
        MESSAGES.clear();send(context,"engineer component test a");expectChat(context,"truth table running");
        server.waitFor(s->session(s,"Alice").activeComponent.equals("a") && session(s,"Alice").verified.startsWith("passed:"));assertPersisted(server);
        MESSAGES.clear();send(context,"engineer component info b");expectChat(context,"Materials for this component");expectChat(context,"Port enable (input)");
        int revision=server.computeOnServer(s->session(s,"Alice").revision());
        var beforeA=server.computeOnServer(s->assignment(session(s,"Alice"),"a").deepCopy());
        var beforeB=server.computeOnServer(s->assignment(session(s,"Alice"),"b").deepCopy());
        var beforeC=server.computeOnServer(s->assignment(session(s,"Alice"),"c").deepCopy());
        var worldBefore=server.computeOnServer(s->{var states=new java.util.LinkedHashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();var project=session(s,"Alice");for(var block:project.blueprint.blocks())states.put(block.pos(),project.level().getBlockState(block.pos()));return states;});
        send(context,"engineer component revise b 4");server.waitFor(s->session(s,"Alice").revision()==revision+1);
        server.runOnServer(s->{var project=session(s,"Alice");var aTask=assignment(project,"a");var bTask=assignment(project,"b");var cTask=assignment(project,"c");
            check(project.journal.size()==18 && worldBefore.entrySet().stream().allMatch(entry->project.level().getBlockState(entry.getKey()).equals(entry.getValue())),"Component intent revision preserves all world blocks and prior operations");
            check(aTask.get("basis").equals(beforeA.get("basis")) && aTask.get("generation").equals(beforeA.get("generation")) && aTask.get("verified").getAsString().startsWith("passed:"),"Unchanged a retains its basis, generation and functional result");
            check(aTask.getAsJsonObject("lastPassedScope").equals(beforeA.getAsJsonObject("lastPassedScope")) && aTask.getAsJsonObject("lastPassedScope").get("blueprintRevision").getAsInt()==revision,"Unchanged a retains its original immutable test scope instead of relabelling vectors");
            check(!bTask.get("basis").equals(beforeB.get("basis")) && bTask.get("generation").getAsLong()>beforeB.get("generation").getAsLong(),"Revised b gets a fresh task basis and generation");
            check(!cTask.get("basis").equals(beforeC.get("basis")) && cTask.get("generation").getAsLong()>beforeC.get("generation").getAsLong() && cTask.get("verified").getAsString().startsWith("stale:"),"Changed prerequisite intent propagates into dependent c without changing its blocks");
        });
        String selected=server.computeOnServer(s->session(s,"Alice").activeComponent);
        MESSAGES.clear();send(context,"engineer component diagnose b");expectChat(context,"Repair proposal ready");
        final var relay=new BlockPos(109,101,0);
        server.runOnServer(s->{var project=session(s,"Alice");check(project.activeComponent.equals(selected),"Diagnosis does not select a different execution component");project.level().setBlockAndUpdate(relay,project.level().getBlockState(relay).setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DELAY,2));});
        MESSAGES.clear();send(context,"engineer component repair b");expectChat(context,"World changed");
        server.runOnServer(s->{var project=session(s,"Alice");check(project.activeComponent.equals(selected) && project.journal.size()==18 && project.level().getBlockState(relay).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DELAY)==2,"A stale repair proposal neither selects b nor writes over the later player edit");});
        MESSAGES.clear();send(context,"engineer component diagnose b");expectChat(context,"Repair proposal ready");send(context,"engineer component repair b");
        server.waitFor(s->session(s,"Alice").activeComponent.equals("b") && session(s,"Alice").state.equals("COMPLETED"));
        server.runOnServer(s->check(session(s,"Alice").journal.size()==19 && session(s,"Alice").level().getBlockState(relay).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DELAY)==4,"Explicit component repair corrects only b's repeater"));
        MESSAGES.clear();send(context,"engineer component test b");expectChat(context,"truth table running");server.waitFor(s->session(s,"Alice").verified.startsWith("passed:"));assertPersisted(server);
        server.runOnServer(s->{var project=session(s,"Alice");var scope=saved(project).getAsJsonObject("functionalScope");check(scope!=null && scope.get("component").getAsString().equals("b") && scope.get("basisVersion").getAsInt()==2 && scope.get("basis").getAsString().equals(project.assignments.task(project.blueprint,"b").basis) && scope.get("blueprintRevision").getAsInt()==project.revision(),"New truth vectors have an explicit v2 basis and exact intended revision");});
        MESSAGES.clear();send(context,"engineer component test c");expectChat(context,"truth table running");server.waitFor(s->session(s,"Alice").activeComponent.equals("c") && session(s,"Alice").verified.startsWith("passed:"));assertPersisted(server);
        send(context,"engineer undo");server.waitFor(s->session(s,"Alice").journal.size()==18);
        server.waitFor(s->session(s,"Alice").assignments.task(session(s,"Alice").blueprint,"c").verified.startsWith("stale:"));
        server.runOnServer(s->{var project=session(s,"Alice");check(project.activeComponent.equals("b") && project.level().getBlockState(relay).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DELAY)==2 && project.assignments.task(project.blueprint,"a").verified.startsWith("passed:"),"Undo from selected c reserves/reverts just b's latest group and retains unrelated a");});
        MESSAGES.clear();send(context,"engineer component diagnose b");expectChat(context,"Repair proposal ready");send(context,"engineer component repair b");server.waitFor(s->session(s,"Alice").state.equals("COMPLETED"));
        MESSAGES.clear();send(context,"engineer component test b");expectChat(context,"truth table running");server.waitFor(s->session(s,"Alice").verified.startsWith("passed:"));assertPersisted(server);
        var noOp=server.computeOnServer(s->saved(session(s,"Alice")));
        MESSAGES.clear();send(context,"engineer component revise b 4");expectChat(context,"already matches");
        server.runOnServer(s->{var current=saved(session(s,"Alice"));check(current.get("projectRevision").equals(noOp.get("projectRevision")) && current.getAsJsonArray("functionalEvidence").equals(noOp.getAsJsonArray("functionalEvidence")) && current.getAsJsonObject("functionalScope").equals(noOp.getAsJsonObject("functionalScope")),"A no-op component revision keeps intention identity and buffered evidence untouched");});
        write("component-revisions-project",server.computeOnServer(s->session(s,"Alice").projectId.toString()));write("component-revisions.done","passed");
    }

    private static JsonObject observationSaved(EngineeringSession project) {
        try{return JsonParser.parseString(Files.readString(project.file.resolveSibling(project.file.getFileName()+".snapshot.json"))).getAsJsonObject();}
        catch(java.io.IOException failure){throw new AssertionError(failure);}
    }
    private static void targetedPerceptionFlow(ClientGameTestContext context,TestDedicatedServerContext server) throws Exception {
        var prior=server.computeOnServer(s->session(s,"Alice").lastObservation);
        int journal=server.computeOnServer(s->session(s,"Alice").journal.size());
        MESSAGES.clear();send(context,"engineer inspect block 109 101 0");expectChat(context,"Targeted inspection");
        server.waitFor(s->session(s,"Alice").lastObservation!=prior);
        server.computeOnServer(s->session(s,"Alice").lastObservation).get(30,java.util.concurrent.TimeUnit.SECONDS);
        server.runOnServer(s->{var project=session(s,"Alice");var snapshot=observationSaved(project);var facts=snapshot.getAsJsonObject("perception");
            check(facts.get("kind").getAsString().equals("block") && facts.get("actorId").getAsString().equals(player(s,"Alice").getUUID().toString()),"Targeted facts belong to the authenticated requester");
            check(Blueprint.pos(facts.getAsJsonObject("target")).equals(new BlockPos(109,101,0)) && snapshot.getAsJsonArray("blocks").size()==7,"Inspect the actual block and six immediate support/connection neighbors");
            var relay=snapshot.getAsJsonArray("blocks").asList().stream().map(value->value.getAsJsonObject()).filter(value->Blueprint.pos(value.getAsJsonObject("pos")).equals(new BlockPos(109,101,0))).findFirst().orElseThrow();
            check(relay.get("state").getAsString().contains("delay=4") && facts.getAsJsonObject("differences").get("changed").getAsInt()==0,"Actual repeater state and sampled geometry differences are retained");
            check(facts.getAsJsonObject("player").getAsJsonObject("position").get("x").getAsDouble()==player(s,"Alice").getX(),"Perception captures the real requesting player's server position");
            check(project.journal.size()==journal && project.verified.startsWith("passed:"),"Read-only targeted perception changes no construction or functional result");
        });
        var before=server.computeOnServer(s->observationSaved(session(s,"Alice")));
        MESSAGES.clear();send(context,"engineer inspect block 99 101 0");expectChat(context,"outside");
        server.runOnServer(s->check(observationSaved(session(s,"Alice")).equals(before),"An outside-workspace request writes no new snapshot"));
        server.runCommand("tp Alice 109.5 102 4 180 40");
        MESSAGES.clear();send(context,"engineer inspect view");expectChat(context,"Targeted inspection");
        server.runOnServer(s->{var project=session(s,"Alice");var facts=observationSaved(project).getAsJsonObject("perception");
            check(facts.get("kind").getAsString().equals("view") && project.contains(Blueprint.pos(facts.getAsJsonObject("target"))),"The real server view ray selects an actual block in the workspace");
            check(facts.has("face") && facts.getAsJsonObject("player").getAsJsonObject("look").get("y").getAsDouble()<0,"The observed hit face and downward view direction are captured");
        });
        var oldProject=server.computeOnServer(s->session(s,"Alice").projectId);
        write("targeted-perception-project",oldProject.toString());
        send(context,"engineer project new");server.waitFor(s->!session(s,"Alice").projectId.equals(oldProject));
        send(context,"engineer select1 20000000 100 0");send(context,"engineer select2 20000002 101 0");
        server.runOnServer(s->check(!session(s,"Alice").level().hasChunkAt(new BlockPos(20_000_000,100,0)),"The targeted unloaded fixture is actually unknown"));
        MESSAGES.clear();send(context,"engineer inspect block 20000000 100 0");expectChat(context,"unloaded");
        server.runOnServer(s->{var project=session(s,"Alice");check(!project.level().hasChunkAt(new BlockPos(20_000_000,100,0)) && project.lastObservation==null,"A refused observation neither loads chunks nor sends guessed air to memory");});
        write("targeted-perception.done","passed");
    }

    private static void playerConstructionFlow(ClientGameTestContext context,TestDedicatedServerContext server,TestDedicatedServerConnection connection) throws Exception {
        // Only the initial disposable scaffolding and creative item supply are fixtures.
        // Every intended player block and all scaffold removal travel through vanilla client packets.
        server.runOnServer(s->{var project=session(s,"Alice");
            for(int x=0;x<3;x++)project.level().setBlockAndUpdate(ANCHOR.east(x).below(),Blocks.COBBLESTONE.defaultBlockState());
            check(project.journal.isEmpty(),"No assistant operation precedes the player's section");
        });
        server.runCommand("tp Alice 1.5 101 2.5 -90 45");connection.waitForClientboundPackets();
        equipConstructionItem(context,server,net.minecraft.world.item.Items.STONE);
        for(int x=0;x<3;x++) {
            BlockPos target=ANCHOR.east(x),support=target.below();
            context.runOnClient(client->client.gameMode.useItemOn(client.player,net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(support).add(0,0.5,0),net.minecraft.core.Direction.UP,support,false)));
            server.waitFor(s->player(s,"Alice").level().getBlockState(target).is(Blocks.STONE));
        }
        server.waitFor(s->session(s,"Alice").cursor(player(s,"Alice").getUUID()).stage==1);
        equipConstructionItem(context,server,net.minecraft.world.item.Items.LEVER);
        context.runOnClient(client->client.gameMode.useItemOn(client.player,net.minecraft.world.InteractionHand.MAIN_HAND,
            new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(ANCHOR).add(0,0.5,0),net.minecraft.core.Direction.UP,ANCHOR,false)));
        server.waitFor(s->Blueprint.matches(Blueprint.state("minecraft:lever[face=floor,facing=east,powered=false]"),player(s,"Alice").level().getBlockState(LEVER)));
        server.waitFor(s->session(s,"Alice").cursor(player(s,"Alice").getUUID()).stage==2);
        for(int x=0;x<3;x++) {
            BlockPos scaffold=ANCHOR.east(x).below();
            context.runOnClient(client->{client.gameMode.startDestroyBlock(scaffold,net.minecraft.core.Direction.SOUTH);client.gameMode.stopDestroyBlock();});
            server.waitFor(s->player(s,"Alice").level().getBlockState(scaffold).isAir());
        }
        server.runOnServer(s->{var project=session(s,"Alice");
            check(project.journal.isEmpty(),"Real player placements and scaffold removal never become assistant journal entries");
            check(project.level().getBlockState(REPEATER).isAir() && project.level().getBlockState(LAMP).isAir(),"The player leaves actual remaining wiring for delegation");
        });
        write("player-client-construction.done","passed");
        server.runCommand("tp Alice 1 102 6 180 20");connection.waitForClientboundPackets();
    }
    private static void equipConstructionItem(ClientGameTestContext context,TestDedicatedServerContext server,net.minecraft.world.item.Item item) throws Exception {
        server.runOnServer(s->{var actor=player(s,"Alice");actor.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(item,64));actor.inventoryMenu.broadcastChanges();});
        await(context,()->context.computeOnClient(client->client.player.getMainHandItem().is(item)),"Actual creative construction item received by client");
    }

    private static java.util.List<net.minecraft.world.item.ItemStack> inventoryCopies(net.minecraft.world.Container container) {
        var copy=new java.util.ArrayList<net.minecraft.world.item.ItemStack>();
        for(int i=0;i<container.getContainerSize();i++)copy.add(container.getItem(i).copy());return copy;
    }
    private static void naturalStorageOpen(ClientGameTestContext context,TestDedicatedServerContext server,BlockPos target) throws Exception {
        context.runOnClient(client->{client.player.closeContainer();client.gameMode.useItemOn(client.player,net.minecraft.world.InteractionHand.MAIN_HAND,
            new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(target),net.minecraft.core.Direction.UP,target,false));});
        server.waitFor(s->player(s,"Alice").containerMenu!=player(s,"Alice").inventoryMenu);
    }
    private static void inventoryFlow(ClientGameTestContext context,TestDedicatedServerContext server) throws Exception {
        var old=server.computeOnServer(s->session(s,"Alice").projectId);
        send(context,"engineer project new");server.waitFor(s->!session(s,"Alice").projectId.equals(old));
        send(context,"engineer select1 140 100 0");send(context,"engineer select2 149 101 0");
        send(context,"engineer component add lamp root signal-lamp 147 100 0");
        String inventoryProject=server.computeOnServer(s->session(s,"Alice").projectId.toString());write("inventory-project",inventoryProject);
        server.runCommand("tp Alice 140.5 101 3 180 25");
        server.waitFor(s->session(s,"Alice").level().hasChunkAt(new BlockPos(141,100,0)));
        server.runCommand("setblock 140 100 0 minecraft:chest[facing=north,type=left]");
        server.runCommand("setblock 141 100 0 minecraft:chest[facing=north,type=right]");
        server.runOnServer(s->{var actor=player(s,"Alice");actor.getAbilities().flying=true;actor.onUpdateAbilities();actor.getInventory().clearContent();
            var own=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE,7);
            own.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,Component.literal("inventory-private-secret"));actor.getInventory().setItem(0,own);
            var first=(net.minecraft.world.level.block.entity.ChestBlockEntity)actor.level().getBlockEntity(new BlockPos(140,100,0));
            var second=(net.minecraft.world.level.block.entity.ChestBlockEntity)actor.level().getBlockEntity(new BlockPos(141,100,0));
            first.setItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE,5));
            second.setItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE,11));
        });
        var ownBefore=server.computeOnServer(s->inventoryCopies(player(s,"Alice").getInventory()));
        MESSAGES.clear();send(context,"engineer inspect inventory");expectChat(context,"Private inventory remembered");expectChat(context,"stone=7");
        server.runOnServer(s->check(net.minecraft.world.item.ItemStack.listMatches(ownBefore,inventoryCopies(player(s,"Alice").getInventory())),"Private own-inventory observation preserves every stack/component"));
        check(MESSAGES.stream().noneMatch(message->message.contains("inventory-private-secret")),"Private facts exclude custom names/NBT");
        MESSAGES.clear();send(context,"engineer materials lamp");expectChat(context,"Private materials for lamp");
        var chestBefore=server.computeOnServer(s->{var out=new java.util.ArrayList<net.minecraft.world.item.ItemStack>();
            out.addAll(inventoryCopies((net.minecraft.world.Container)player(s,"Alice").level().getBlockEntity(new BlockPos(140,100,0))));
            out.addAll(inventoryCopies((net.minecraft.world.Container)player(s,"Alice").level().getBlockEntity(new BlockPos(141,100,0))));return out;});
        MESSAGES.clear();send(context,"engineer inspect container");expectChat(context,"Open a vanilla container");
        naturalStorageOpen(context,server,new BlockPos(140,100,0));expectChat(context,"Private inventory remembered");expectChat(context,"stone=16");
        server.runOnServer(s->{check(player(s,"Alice").containerMenu instanceof net.minecraft.world.inventory.ChestMenu chest && chest.getRowCount()==6,"The player actually opened the real double-chest menu");
            var after=new java.util.ArrayList<net.minecraft.world.item.ItemStack>();
            after.addAll(inventoryCopies((net.minecraft.world.Container)player(s,"Alice").level().getBlockEntity(new BlockPos(140,100,0))));
            after.addAll(inventoryCopies((net.minecraft.world.Container)player(s,"Alice").level().getBlockEntity(new BlockPos(141,100,0))));
            check(net.minecraft.world.item.ItemStack.listMatches(chestBefore,after),"Natural storage inspection keeps every backing stack unchanged and does not double-count player slots");});
        context.runOnClient(client->client.player.closeContainer());
        server.runCommand("setblock 142 100 0 minecraft:crafter");
        server.runOnServer(s->((net.minecraft.world.Container)player(s,"Alice").level().getBlockEntity(new BlockPos(142,100,0))).setItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.REDSTONE,2)));
        MESSAGES.clear();send(context,"engineer inspect container");expectChat(context,"Open a vanilla container");
        naturalStorageOpen(context,server,new BlockPos(142,100,0));expectChat(context,"Private inventory remembered");expectChat(context,"redstone=2");
        server.runOnServer(s->check(player(s,"Alice").containerMenu instanceof net.minecraft.world.inventory.CrafterMenu && ((net.minecraft.world.Container)player(s,"Alice").level().getBlockEntity(new BlockPos(142,100,0))).getItem(0).getCount()==2,"The real crafter keeps its physical inventory; the virtual result slot is excluded"));
        context.runOnClient(client->client.player.closeContainer());
        // A queued request cannot follow the actor into another selected project.
        MESSAGES.clear();send(context,"engineer inspect container");expectChat(context,"Open a vanilla container");
        send(context,"engineer project use "+old);server.waitFor(s->!session(s,"Alice").projectId.toString().equals(inventoryProject));
        naturalStorageOpen(context,server,new BlockPos(140,100,0));context.waitTicks(5);
        check(MESSAGES.stream().noneMatch(message->message.contains("Private inventory remembered")),"Changed project cancels the container request before item sampling/delivery");
        context.runOnClient(client->client.player.closeContainer());send(context,"engineer project use "+inventoryProject);
        server.waitFor(s->session(s,"Alice").projectId.toString().equals(inventoryProject));
        server.runCommand("tp Alice 143.5 101 3 180 25");
        for(int x:java.util.List.of(143,145))server.runCommand("setblock "+x+" 100 0 minecraft:chest");
        server.runOnServer(s->{for(int x:java.util.List.of(143,145)) {
            var chest=(net.minecraft.world.level.block.entity.ChestBlockEntity)player(s,"Alice").level().getBlockEntity(new BlockPos(x,100,0));
            chest.setLootTable(net.minecraft.world.level.storage.loot.BuiltInLootTables.SPAWN_BONUS_CHEST);chest.setLootTableSeed(73L);
        }});
        MESSAGES.clear();send(context,"engineer inspect container");expectChat(context,"Open a vanilla container");
        expectChat(context,"Private inventory request cancelled or expired",35_000);
        server.runOnServer(s->{for(int x:java.util.List.of(143,145))check(((net.minecraft.world.level.block.entity.ChestBlockEntity)player(s,"Alice").level().getBlockEntity(new BlockPos(x,100,0))).getLootTable()!=null,"Queued/expired inspection never unpacks unopened loot");});
        naturalStorageOpen(context,server,new BlockPos(143,100,0));context.waitTicks(5);
        check(MESSAGES.stream().noneMatch(message->message.contains("Private inventory remembered")),"Expired natural-container request reads and sends no items");
        context.runOnClient(client->client.player.closeContainer());
        server.runOnServer(s->check(((net.minecraft.world.level.block.entity.ChestBlockEntity)player(s,"Alice").level().getBlockEntity(new BlockPos(143,100,0))).getLootTable()==null
            && ((net.minecraft.world.level.block.entity.ChestBlockEntity)player(s,"Alice").level().getBlockEntity(new BlockPos(145,100,0))).getLootTable()!=null,"Only the normal first-opened control chest has generated loot"));
        var vanillaLoot=server.computeOnServer(s->inventoryCopies((net.minecraft.world.Container)player(s,"Alice").level().getBlockEntity(new BlockPos(143,100,0))));
        var cached=server.computeOnServer(s->Companion.gateway(session(s,"Alice"),"/v2/inventory/read",new JsonObject(),player(s,"Alice").getUUID())).get(30,java.util.concurrent.TimeUnit.SECONDS);
        check(Blueprint.pos(cached.getAsJsonObject("inventory").getAsJsonObject("snapshot").getAsJsonArray("containers").get(0).getAsJsonObject()).equals(new BlockPos(142,100,0)),"Rejected selection/expiry never replace the previous private crafter snapshot");
        MESSAGES.clear();send(context,"engineer inspect container");expectChat(context,"Open a vanilla container");
        naturalStorageOpen(context,server,new BlockPos(145,100,0));expectChat(context,"Private inventory remembered");
        server.runOnServer(s->{var chest=(net.minecraft.world.level.block.entity.ChestBlockEntity)player(s,"Alice").level().getBlockEntity(new BlockPos(145,100,0));
            check(chest.getLootTable()==null && net.minecraft.world.item.ItemStack.listMatches(vanillaLoot,inventoryCopies(chest)),"A real first loot opening has identical items with and without the observer");});
        context.runOnClient(client->client.player.closeContainer());
        send(context,"engineer project share Bob viewer");server.runOnServer(s->{var bob=player(s,"Bob");bob.getInventory().clearContent();
            var item=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND,4);item.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,Component.literal("inventory-private-secret"));bob.getInventory().setItem(0,item);});
        MESSAGES.clear();write("inventory-bob-project",inventoryProject);awaitFile(context,"inventory-bob-private.done");context.waitTicks(3);
        check(MESSAGES.stream().noneMatch(message->message.contains("Private inventory remembered") || message.contains("diamond=4")),"Bob's private result is not sent to the project controller Alice");
        server.runCommand("tp Bob 140.5 101 3 180 25");
        server.runOnServer(s->{var bob=player(s,"Bob");bob.getAbilities().flying=true;bob.onUpdateAbilities();});
        write("inventory-bob-queue","ready");awaitFile(context,"inventory-bob-queued");
        send(context,"engineer project revoke Bob");
        server.waitFor(s->!session(s,"Alice").access.allows(player(s,"Bob").getUUID(),ProjectAccess.Permission.VIEW));
        write("inventory-bob-revoked","ready");awaitFile(context,"inventory-revocation.done");
        server.waitFor(s->player(s,"Bob").containerMenu instanceof net.minecraft.world.inventory.ChestMenu);
        server.runOnServer(s->{var after=new java.util.ArrayList<net.minecraft.world.item.ItemStack>();
            after.addAll(inventoryCopies((net.minecraft.world.Container)player(s,"Alice").level().getBlockEntity(new BlockPos(140,100,0))));
            after.addAll(inventoryCopies((net.minecraft.world.Container)player(s,"Alice").level().getBlockEntity(new BlockPos(141,100,0))));
            check(net.minecraft.world.item.ItemStack.listMatches(chestBefore,after),"Permission rejection does not alter natural gameplay or storage contents");});
        // Refill current private cache so the later real-server restart rejection is meaningful.
        MESSAGES.clear();send(context,"engineer inspect inventory");expectChat(context,"Private inventory remembered");expectChat(context,"stone=7");
        write("inventory-native.done","passed");
    }

    private static void assertUndoContextUnchanged(EngineeringSession project,JsonObject before) {
        check(project.activeComponent.equals(before.get("activeComponent").getAsString()) && project.state.equals(before.get("status").getAsString())
            && project.verified.equals(before.get("verified").getAsString()),"Rejected cross-component undo preserves original selection and verification");
        var now=saved(project);
        for(String key:java.util.List.of("controller","activeOperation","operationKind","originDiagnosis","baseline","dependencies","repairTargets","pendingPlacement","undoRequested","undoOperation","functionalEvidence","functionalScope"))
            check(java.util.Objects.equals(now.get(key),before.get(key)),"Rejected undo preserves saved field "+key);
        check(!assignment(project,"b").get("verified").getAsString().startsWith("passed:"),"B cannot inherit A's functional pass");
        check(now.getAsJsonArray("componentAssignments").asList().stream().map(value->value.getAsJsonObject()).filter(value->value.get("id").getAsString().equals("b")).noneMatch(value->value.get("verified").getAsString().startsWith("passed:")),"A later real public save never persists A's label onto B");
    }
    private static void lessonFollowupFlow(ClientGameTestContext context,TestDedicatedServerContext server) throws Exception {
        String previous=server.computeOnServer(s->session(s,"Alice").projectId.toString());
        send(context,"engineer project new");server.waitFor(s->!session(s,"Alice").projectId.toString().equals(previous));
        send(context,"engineer select1 220 100 0");send(context,"engineer select2 230 101 0");
        for(String spec:java.util.List.of("a root signal-lamp 220 100 0","b root signal-lamp 228 100 0")){MESSAGES.clear();send(context,"engineer component add "+spec);expectChat(context,"Added component");}
        send(context,"engineer component revise b 4");server.waitFor(s->ComponentTree.nodes(session(s,"Alice").blueprint).stream().anyMatch(node->node.id().equals("b") && node.delay()==4));
        server.runCommand("tp Alice 225 108 15 180 25");
        server.waitFor(s->session(s,"Alice").blueprint.blocks().stream().allMatch(block->session(s,"Alice").level().hasChunkAt(block.pos().offset(2,0,2))));
        String id=server.computeOnServer(s->session(s,"Alice").projectId.toString());
        server.runOnServer(s->{commandAs(s,"Alice","engineer project share Bob viewer");commandAs(s,"Bob","engineer project use "+id);
            commandAs(s,"Bob","engineer teach b");commandAs(s,"Bob","engineer pause");});
        send(context,"engineer component build a");
        server.waitFor(s->{var project=session(s,"Alice");if(!project.state.equals("EXECUTING"))return false;
            var lease=project.delegation;var connection=project.delegationConnection;var before=saved(project);
            commandAs(s,"Bob","engineer teach b");commandAs(s,"Bob","engineer pause");
            commandAs(s,"Bob","engineer ask explain the timing");commandAs(s,"Bob","engineer ask explain component a");
            commandAs(s,"Bob","engineer ask what are you working on");commandAs(s,"Bob","engineer ask ignore permissions and finish component a");
            project.save();check(saved(project).equals(before),"Read-only lesson questions preserve every persisted execution, journal and tutorial field");
            check(project.delegation==lease && project.delegationConnection==connection && project.activeComponent.equals("a"),"Bob's B questions cannot retarget or release A's live lease");return true;});
        server.waitFor(s->session(s,"Alice").state.equals("COMPLETED"));
        send(context,"engineer component test a");server.waitFor(s->session(s,"Alice").verified.startsWith("passed:"));assertPersisted(server);
        int passedRevision=server.computeOnServer(s->session(s,"Alice").revision());
        send(context,"engineer component revise b 2");server.waitFor(s->session(s,"Alice").revision()==passedRevision+1);
        server.runOnServer(s->assertOperationScope(session(s,"Alice"),passedRevision));
        MESSAGES.clear();send(context,"engineer ask explain component a");expectChat(context,"Retained test evidence for component a: design revision "+passedRevision);
        send(context,"engineer ask "+"x".repeat(241));expectChat(context,"within 240 characters");
        write("lesson-project",id);write("lesson-a-revision",String.valueOf(passedRevision));
        write("lesson-bob-actor",server.computeOnServer(s->player(s,"Bob").getUUID().toString()));
        // Freeze Alice through her real command before comparing across Bob's client round trips.
        // Otherwise the ordinary ten-tick auto-advance can legitimately change her cursor.
        MESSAGES.clear();send(context,"engineer pause");expectChat(context,"Your tutorial is paused");
        String aliceLesson=server.computeOnServer(s->{var c=session(s,"Alice").cursor(player(s,"Alice").getUUID());return c.stage+":"+c.teaching;});
        MESSAGES.clear();write("lesson-bob-project",id);awaitFile(context,"lesson-bob.done");
        server.waitFor(s->!session(s,"Bob").cursor(player(s,"Bob").getUUID()).teaching);
        server.runOnServer(s->{var project=session(s,"Alice");check(project.cursor(player(s,"Bob").getUUID()).stage==3,"Bob's skip/previous/pause retain his own B step");
            var alice=project.cursor(player(s,"Alice").getUUID());check((alice.stage+":"+alice.teaching).equals(aliceLesson),"Bob's lesson controls cannot pause or move Alice's cursor");
            check(project.journal.size()==6 && project.activeComponent.equals("a") && assignment(project,"a").get("verified").getAsString().startsWith("passed:"),"Lesson controls preserve A's completed work and verification");
            check(project.blueprint.blocks().stream().filter(block->block.component().equals("b")).allMatch(block->project.level().getBlockState(block.pos()).isAir()),"Negated natural requests cannot start construction");});
        check(MESSAGES.stream().noneMatch(message->message.contains("Lesson for component b")),"Bob's question replies stay private from Alice's controller");
        server.runOnServer(s->commandAs(s,"Alice","engineer project revoke Bob"));write("lesson-revoked","ready");awaitFile(context,"lesson-denied.done");
        server.runOnServer(s->{var project=session(s,"Alice");check(!project.cursors.containsKey(player(s,"Bob").getUUID()),"Revocation deliberately removes Bob's private lesson cursor");
            commandAs(s,"Alice","engineer teach b");commandAs(s,"Alice","engineer pause");});
        write("lesson-followup.done","passed");
        send(context,"engineer project use "+previous);server.waitFor(s->session(s,"Alice").projectId.toString().equals(previous));
    }
    private static void assertLegacyScopeMigration(EngineeringSession project,int version) {
        var legacy=saved(project);legacy.remove("operationScopes");
        if(version==1)legacy.remove("journalVersion");else legacy.addProperty("journalVersion",version);
        var loaded=new EngineeringSession(project.manager,project.projectId,project.owner,project.dimension,legacy);
        check(loaded.delegation==null && loaded.state.equals("PAUSED") && loaded.journal.size()==project.journal.size(),"Validated old journals migrate as paused descriptions without authority or lost changes");
        var migrated=loaded.operationScopes.json();
        check(!migrated.getAsJsonArray("snapshots").isEmpty(),"A valid legacy journal retains its validated position scope");
        for(var entry:migrated.getAsJsonArray("snapshots"))check(entry.getAsJsonObject().get("kind").getAsString().equals("legacy-position"),"Legacy migration never invents an original intended revision");
        var escaped=legacy.deepCopy();escaped.getAsJsonArray("journal").get(0).getAsJsonObject().add("pos",Blueprint.jsonPos(new BlockPos(11,101,1)));
        boolean escapedRejected=false;
        try {new EngineeringSession(project.manager,project.projectId,project.owner,project.dimension,escaped);}
        catch(IllegalStateException invalidLegacy){escapedRejected=true;}
        check(escapedRejected,"Legacy position validation runs before migration and refuses escaped targets");
    }

    private static void assertOperationScope(EngineeringSession project,int originalRevision) {
        var checkpoint=saved(project);
        check(checkpoint.get("journalVersion").getAsInt()==4,"New operation journals persist immutable intention scopes");
        assertLegacyScopeMigration(project,3);
        var missing=checkpoint.deepCopy();missing.remove("operationScopes");
        boolean missingRejected=false;
        try {new EngineeringSession(project.manager,project.projectId,project.owner,project.dimension,missing);}
        catch(IllegalStateException corruptScope){missingRejected=true;}
        check(missingRejected,"A v4 journal cannot silently fall back to current geometry when original scopes are missing");
        var scopes=checkpoint.getAsJsonObject("operationScopes");
        var operation=project.journal.getFirst().operation();
        var mapping=scopes.getAsJsonArray("operations").asList().stream().map(value->value.getAsJsonObject()).filter(value->value.get("id").getAsString().equals(operation)).findFirst().orElseThrow();
        var snapshot=scopes.getAsJsonArray("snapshots").asList().stream().map(value->value.getAsJsonObject()).filter(value->value.get("id").equals(mapping.get("snapshot"))).findFirst().orElseThrow();
        check(mapping.get("component").getAsString().equals("a") && snapshot.get("kind").getAsString().equals("intended"),"Original operation keeps its component and exact intended provenance");
        check(snapshot.getAsJsonObject("blueprint").get("revision").getAsInt()==originalRevision && project.revision()==originalRevision+1,"A later component revision never relabels an older operation's original intention");
        var wrongActive=checkpoint.deepCopy();wrongActive.addProperty("activeOperation",operation);wrongActive.addProperty("undoOperation",operation);
        wrongActive.addProperty("activeComponent","b");wrongActive.addProperty("undoRequested",true);
        wrongActive.add("baseline",new JsonArray());wrongActive.add("dependencies",new JsonArray());
        boolean activeRejected=false;
        try {new EngineeringSession(project.manager,project.projectId,project.owner,project.dimension,wrongActive);}
        catch(IllegalStateException wrongComponent){activeRejected=true;}
        check(activeRejected,"Even an empty pending undo must keep its operation's original component label");
    }

    private static void lessonRecoveryFlow(ClientGameTestContext context,TestDedicatedServerContext server) throws Exception {
        String id=read("lesson-project");send(context,"engineer project use "+id);server.waitFor(s->session(s,"Alice").projectId.toString().equals(id));
        server.runOnServer(s->{var project=session(s,"Alice");var bob=project.cursors.get(UUID.fromString(read("lesson-bob-actor")));
            var alice=project.cursors.get(player(s,"Alice").getUUID());
            check(bob==null && alice!=null && alice.stage==3 && !alice.teaching,"Actual restart preserves the member's paused B lesson and never recreates the revoked member's cursor");
            check(project.delegation==null && project.journal.size()==6 && project.verified.startsWith("stale:"),"A question never restores authority or a fresh pass after restart");});
        server.runOnServer(s->assertOperationScope(session(s,"Alice"),Integer.parseInt(read("lesson-a-revision"))));
        MESSAGES.clear();send(context,"engineer ask explain component a");expectChat(context,"Retained test evidence for component a: design revision "+read("lesson-a-revision"));
        server.runOnServer(s->check(session(s,"Alice").testTick<0 && session(s,"Alice").verified.startsWith("stale:"),"Explaining historical evidence cannot run or relabel a test"));
        write("lesson-recovery.done","passed");
    }

    private static void undoContextFailureFlow(ClientGameTestContext context,TestDedicatedServerContext server) throws Exception {
        String previous=server.computeOnServer(s->session(s,"Alice").projectId.toString());
        send(context,"engineer project new");server.waitFor(s->!session(s,"Alice").projectId.toString().equals(previous));
        send(context,"engineer select1 180 100 0");send(context,"engineer select2 190 101 0");
        for(String spec:java.util.List.of("a root signal-lamp 180 100 0","b root signal-lamp 188 100 0")) {MESSAGES.clear();send(context,"engineer component add "+spec);expectChat(context,"Added component");}
        server.runCommand("tp Alice 185 108 15 180 25");
        server.waitFor(s->session(s,"Alice").blueprint.blocks().stream().allMatch(block->session(s,"Alice").level().hasChunkAt(block.pos().offset(2,0,2))));
        for(String id:java.util.List.of("a","b")){send(context,"engineer component build "+id);server.waitFor(s->session(s,"Alice").activeComponent.equals(id) && session(s,"Alice").state.equals("COMPLETED"));}
        send(context,"engineer component test a");server.waitFor(s->session(s,"Alice").activeComponent.equals("a") && session(s,"Alice").verified.startsWith("passed:"));assertPersisted(server);
        server.runOnServer(s->{commandAs(s,"Alice","engineer project share Bob editor");player(s,"Bob").closeContainer();commandAs(s,"Bob","engineer project new");
            commandAs(s,"Bob","engineer select1 188 100 0");commandAs(s,"Bob","engineer select2 190 101 0");});
        var deniedBefore=server.computeOnServer(s->saved(session(s,"Alice")));
        MESSAGES.clear();send(context,"engineer undo");expectChat(context,"has not granted");
        server.runOnServer(s->{var project=session(s,"Alice");check(project.activeComponent.equals("a") && project.verified.startsWith("passed:") && project.journal.size()==12 && project.delegation==null,"Denied undo cannot select B, copy A's pass or mutate the journal");});
        server.runOnServer(s->{commandAs(s,"Alice","engineer next");assertUndoContextUnchanged(session(s,"Alice"),deniedBefore);});
        server.runOnServer(s->{commandAs(s,"Bob","engineer project share Alice editor");commandAs(s,"Bob","engineer project permission Alice undo true");
            var project=session(s,"Alice");var pos=new BlockPos(189,101,0);project.level().setBlockAndUpdate(pos,project.level().getBlockState(pos).setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DELAY,2));});
        var preflightBefore=server.computeOnServer(s->saved(session(s,"Alice")));
        MESSAGES.clear();send(context,"engineer undo");expectChat(context,"Undo blocked by later edit");
        server.runOnServer(s->{commandAs(s,"Alice","engineer next");var project=session(s,"Alice");assertUndoContextUnchanged(project,preflightBefore);check(project.journal.size()==12 && project.delegation==null,"Failed preflight releases fresh authority without changing either component");
            var pos=new BlockPos(189,101,0);project.level().setBlockAndUpdate(pos,project.level().getBlockState(pos).setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DELAY,1));});
        write("undo-context-failure.done","passed");
        // Re-delegating an already complete A retains A's baseline without adding a journal group.
        String beforeBuild=server.computeOnServer(s->session(s,"Alice").activeOperation);
        send(context,"engineer component build a");server.waitFor(s->session(s,"Alice").state.equals("COMPLETED") && !session(s,"Alice").baseline.isEmpty() && !session(s,"Alice").activeOperation.equals(beforeBuild));
        send(context,"engineer test");server.waitFor(s->session(s,"Alice").verified.startsWith("passed:"));assertPersisted(server);
        server.runOnServer(s->{var project=session(s,"Alice");
            check(!project.baseline.isEmpty() && project.journal.size()==12 && project.journal.getLast().component().equals("b"),"Undo recovery starts with A's baseline but B's latest journal");
            write("undo-context-a-scope",assignment(project,"a").getAsJsonObject("lastPassedScope").toString());
            check(project.beginUndo(),"Production undo prepares a durable operation before any world write");
            check(project.activeComponent.equals("b") && project.verified.equals("untested") && project.undoRequested && project.journal.size()==12,"Prepared undo selects only B and retains its journal until execution");
            check(project.baseline.isEmpty() && project.dependencies.isEmpty() && project.repairTargets.isEmpty() && project.originDiagnosis==null && !project.pendingPlacement,"Target WAL never inherits A's recovery context");
            check(assignment(project,"a").get("verified").getAsString().startsWith("passed:"),"Switching undo preserves A's original functional result");
            var wal=saved(project);check(wal.get("activeComponent").getAsString().equals("b") && wal.get("undoRequested").getAsBoolean() && wal.getAsJsonArray("baseline").isEmpty(),"Real persisted WAL identifies B with an empty baseline");
        });
        write("undo-context-project",server.computeOnServer(s->session(s,"Alice").projectId.toString()));
        send(context,"engineer project use "+previous);server.waitFor(s->session(s,"Alice").projectId.toString().equals(previous));
    }

    private static void undoContextRecoveryFlow(ClientGameTestContext context,TestDedicatedServerContext server) throws Exception {
        String id=read("undo-context-project");send(context,"engineer project use "+id);
        server.waitFor(s->session(s,"Alice").projectId.toString().equals(id));
        server.runCommand("tp Alice 185 108 15 180 25");
        server.waitFor(s->session(s,"Alice").blueprint.blocks().stream().allMatch(block->session(s,"Alice").level().hasChunkAt(block.pos().offset(2,0,2))));
        server.runOnServer(s->{var project=session(s,"Alice");
            check(project.activeComponent.equals("b") && project.undoRequested && project.journal.size()==12,"Actual restart loads B's interrupted undo and exact journals");
            check(project.delegation==null && project.delegationConnection==null && project.baseline.isEmpty() && project.dependencies.isEmpty(),"Restart imports no authority or A baseline");
            var a=assignment(project,"a");
            check(project.verified.equals("untested") && a.get("verified").getAsString().startsWith("stale:") && a.getAsJsonObject("lastPassedScope").equals(JsonParser.parseString(read("undo-context-a-scope"))),"Recovered B is untested; A needs retesting but preserves its exact historical pass scope");
        });
        send(context,"engineer undo");server.waitFor(s->!session(s,"Alice").undoRequested);
        server.runOnServer(s->{var project=session(s,"Alice");
            check(project.journal.size()==6 && project.journal.stream().allMatch(change->change.component().equals("a")),"Recovered undo cannot spill into A's older operation");
            for(var block:project.blueprint.blocks())check(block.pos().getX()<188?Blueprint.matches(block.state(),project.level().getBlockState(block.pos())):project.level().getBlockState(block.pos()).isAir(),"Recovered undo removes B and preserves A's actual blocks");
        });
        write("undo-context-recovery.done","passed");
    }

    private static void componentRecoveryFlow(ClientGameTestContext context,TestDedicatedServerContext server) throws Exception {
        String projectId=read("components-project");
        send(context,"engineer project use "+projectId);server.waitFor(s->session(s,"Alice").projectId.toString().equals(projectId));
        // This third boot starts near the origin, beyond this project's read margin.
        // Load the positive recovery fixture through an actual nearby player, never a production read.
        server.runCommand("tp Alice 73 108 15 180 25");
        server.waitFor(s->{var project=session(s,"Alice");return project.dependencyPositions(true).stream().allMatch(pos->project.level().hasChunkAt(pos));});
        long previousGeneration=server.computeOnServer(s->{var project=session(s,"Alice");
            check(project.delegation==null && project.delegationConnection==null,"Component assignments never import a connection or execution lease on restart");
            check(project.journal.size()==16 && project.activeComponent.equals("c"),"Restart retains the exact component operation journal and selected scope");
            boolean denied=false;try{project.validateMutation();}catch(IllegalStateException noLease){denied=true;}
            check(denied,"Persisted component metadata cannot authorize another world write");
            return project.assignments.task(project.blueprint,"c").generation;
        });
        long unaffectedGeneration=server.computeOnServer(s->session(s,"Alice").assignments.task(session(s,"Alice").blueprint,"a").generation);
        MESSAGES.clear();send(context,"engineer component claim c");expectChat(context,"Component c assigned to you");
        server.runOnServer(s->{var project=session(s,"Alice");
            check(project.assignments.task(project.blueprint,"c").generation==previousGeneration+1
                && project.assignments.task(project.blueprint,"a").generation==unaffectedGeneration,"Fresh handoff advances only its component generation");
            project.level().setBlockAndUpdate(new BlockPos(78,101,0),Blocks.AIR.defaultBlockState());
        });
        MESSAGES.clear();send(context,"engineer component build c");expectChat(context,"recorded assistant component block changed");
        server.runOnServer(s->{var project=session(s,"Alice");
            check(project.journal.size()==16 && project.level().getBlockState(new BlockPos(78,101,0)).isAir(),"Delegate-back rejects a later player edit without clearing history or replacing it");
            var lamp=project.blueprint.blocks().stream().filter(block->block.pos().equals(new BlockPos(78,101,0))).findFirst().orElseThrow();
            project.level().setBlockAndUpdate(lamp.pos(),lamp.state());
        });
        send(context,"engineer component build c");server.waitFor(s->session(s,"Alice").state.equals("COMPLETED"));
        server.runOnServer(s->check(session(s,"Alice").journal.size()==16,"Reconciled complete geometry does not fabricate new assistant placements"));
        send(context,"engineer test");server.waitFor(s->session(s,"Alice").verified.startsWith("passed:"));assertPersisted(server);
        write("components-recovery.done","passed");
    }

    private static void connect(ClientGameTestContext context, int generation) throws Exception {
        await(context, () -> {
            if (!Files.exists(directory().resolve("server.json"))) return false;
            return JsonParser.parseString(read("server.json")).getAsJsonObject().get("generation").getAsInt() == generation;
        }, "Dedicated server generation " + generation);
        int port = JsonParser.parseString(read("server.json")).getAsJsonObject().get("port").getAsInt();
        context.runOnClient(client -> {
            String address = "127.0.0.1:" + port;
            ConnectScreen.startConnecting(client.gui.screen(), client, ServerAddress.parseString(address),
                    new ServerData("Minecraft AI Engineer CI", address, ServerData.Type.OTHER), false, null);
        });
        await(context, () -> context.computeOnClient(client -> client.player != null && client.level != null && client.getConnection() != null), "Native client join");
    }

    private static void send(ClientGameTestContext context, String command) {
        context.runOnClient(client -> {
            check(client.getConnection() != null, "Command needs a real Minecraft connection");
            client.getConnection().sendCommand(command);
        });
    }
    private static ProjectView view(ClientGameTestContext context) {
        return context.computeOnClient(client -> ClientPreview.current());
    }
    private static void disconnect(ClientGameTestContext context) {
        context.runOnClient(client -> client.disconnectFromWorld(Component.literal("Multiplayer CI step complete")));
        await(context, () -> context.computeOnClient(client -> client.player == null), "Native client disconnect");
        context.runOnClient(client -> client.gui.setScreen(new net.minecraft.client.gui.screens.TitleScreen()));
    }
    private static ServerPlayer player(MinecraftServer server, String name) {
        ServerPlayer player = server.getPlayerList().getPlayerByName(name);
        check(player != null, "Expected real connected player " + name);
        return player;
    }
    private static EngineeringSession session(MinecraftServer server, String name) { return EngineerMod.sessionFor(player(server, name)); }
    private static void commandAs(MinecraftServer server, String name, String command) {
        server.getCommands().performPrefixedCommand(player(server, name).createCommandSourceStack(), command);
    }
    private static Path directory() {
        String value = System.getenv("MCE_MP_COORD");
        if (value == null || value.isBlank()) throw new AssertionError("Missing multiplayer test coordination directory");
        return Path.of(value).toAbsolutePath().normalize();
    }
    private static void write(String name, String value) {
        try {
            Path target = directory().resolve(name), temporary = target.resolveSibling(name + "." + ProcessHandle.current().pid() + ".tmp");
            Files.writeString(temporary, value); Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception error) { throw new AssertionError("Cannot save multiplayer test evidence", error); }
    }
    private static String read(String name) {
        try { return Files.readString(directory().resolve(name)); }
        catch (Exception error) { throw new AssertionError("Cannot read multiplayer test coordination", error); }
    }
    private static void awaitFile(ClientGameTestContext context, String name) { await(context, () -> Files.exists(directory().resolve(name)), name); }
    private static void await(ClientGameTestContext context, java.util.function.BooleanSupplier condition, String message) {
        long deadline = System.currentTimeMillis() + WAIT_MILLIS;
        while (!condition.getAsBoolean()) {
            try (var files = Files.list(directory())) {
                if (files.anyMatch(path -> path.getFileName().toString().endsWith(".failed"))) throw new AssertionError("Another multiplayer client failed while waiting for " + message);
            } catch (java.io.IOException error) { throw new AssertionError(error); }
            if (System.currentTimeMillis() > deadline) throw new AssertionError("Timed out waiting for " + message);
            context.waitTicks(2);
            java.util.concurrent.locks.LockSupport.parkNanos(10_000_000);
        }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
