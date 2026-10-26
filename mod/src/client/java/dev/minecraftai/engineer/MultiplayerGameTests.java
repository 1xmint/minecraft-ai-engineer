package dev.minecraftai.engineer;

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
    private static final long WAIT_MILLIS = 240_000;

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
                send(context, "engineer project new");
                server.waitFor(s -> !EngineerMod.managerFor(s).projects.isEmpty());
                projectId = server.computeOnServer(s -> session(s, "Alice").projectId.toString());
                worldId = server.computeOnServer(s -> session(s, "Alice").worldId);
                oldEpoch = server.computeOnServer(s -> EngineerMod.managerFor(s).epoch);
                write("project", projectId);
                send(context, "engineer select1 0 100 0");
                send(context, "engineer select2 2 101 0");
                send(context, "engineer reference");
                server.waitFor(s -> session(s, "Alice").blueprint != null);
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
                    for (int x = 0; x < 3; x++) project.level().setBlockAndUpdate(ANCHOR.east(x), Blocks.STONE.defaultBlockState());
                    project.level().setBlockAndUpdate(LEVER, Blueprint.state("minecraft:lever[face=floor,facing=east,powered=false]"));
                });
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
                        legacy.addProperty("schemaVersion",1);legacy.addProperty("worldId",oldWorld);legacy.getAsJsonObject("blueprint").addProperty("worldId",oldWorld);
                        LocalFiles.write(source,legacy);String before=Files.readString(source);
                        commandAs(s,"Alice","engineer project adopt-legacy");
                        var adopted=session(s,"Alice");
                        check(!adopted.projectId.equals(original.projectId) && adopted.state.equals("PAUSED") && adopted.delegation==null,"Explicit legacy adoption never imports execution authority");
                        check(adopted.journal.size()==original.journal.size() && adopted.access.members().size()==1,"Adoption preserves the recovery journal with private ownership");
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
            send(context, "engineer test");
            server.waitFor(s -> session(s, "Alice").verified.startsWith("passed:"));
            assertPersisted(server);
            server.runOnServer(s -> {
                var project = session(s, "Alice");
                check(project.level().getBlockState(REPEATER).is(Blocks.REPEATER), "Dedicated reconciliation builds the real repeater");
                check(project.level().getBlockState(LAMP).is(Blocks.REDSTONE_LAMP), "Dedicated reconciliation builds the real lamp");
            });
            write("finish", "ready");
            awaitFile(context, "bob.done");
            server.waitFor(s -> s.getPlayerList().getPlayerCount() == 1, 12_000);
            connection.waitForClientboundPackets();
            context.takeScreenshot("engineer-multiplayer-restarted-tested-build");
        }
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
