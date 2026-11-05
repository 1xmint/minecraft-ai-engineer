package dev.minecraftai.engineer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.minecraftai.engineer.client.ClientPreview;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
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

/**
 * Milestone 5 acceptance: one real dedicated server, owner Alice, editor Bob (no overlay) and
 * denied Eve, each in its own native client JVM. Every request below is ordinary addressed chat;
 * slash commands only set up fixtures (project, selection, sharing). The launcher pauses the real
 * companion process on request so a reply can arrive after a permission or selection change.
 * CI uses offline identities: this does not prove Mojang login or any human audio.
 */
public final class AcceptanceGameTests implements FabricClientGameTest {
    private static final ConcurrentLinkedQueue<String> MESSAGES = new ConcurrentLinkedQueue<>();
    private static final long WAIT_MILLIS = 300_000;
    private static final String GOAL = "Keep the entrance within six blocks.";
    private static final String DECISION = "Use a visible lamp for the output.";
    /** Text that only an authorized reader of Alice's notes, summary or guidance could receive. */
    private static final String[] PRIVATE = {"six blocks", "visible lamp", "by Alice", "Shared project notes", "Owner-authored",
            "Player statements", "Where we left off", "Lesson for component", "Suggested next step", "catalog not-gate"};

    @Override
    public void runTest(ClientGameTestContext context) {
        String role = System.getenv("MCE_ACCEPT_ROLE");
        if (role == null || role.isBlank()) return;
        try {
            Files.createDirectories(directory());
            ClientReceiveMessageEvents.GAME.register((message, overlay) -> MESSAGES.add(message.getString()));
            ClientReceiveMessageEvents.CHAT.register((message, signed, sender, type, time) -> MESSAGES.add(message.getString()));
            switch (role) {
                case "alice" -> owner(context);
                case "bob" -> editor(context);
                case "eve" -> denied(context);
                default -> throw new AssertionError("Unknown acceptance role");
            }
            write(role + ".done", "passed");
        } catch (Throwable failure) {
            write(role + ".failed", failure.toString());
            if (failure instanceof RuntimeException runtime) throw runtime;
            throw new AssertionError(failure);
        }
    }

    // ---------------------------------------------------------------- Alice (owner, overlay on)

    private static void owner(ClientGameTestContext context) throws Exception {
        check(ClientPreview.enabled(), "Alice keeps the overlay so both client kinds are exercised");
        var config = properties();
        String projectId;
        try (TestDedicatedServerContext server = context.worldBuilder().createServer(config)) {
            server.runOnServer(s -> { check(!s.isSingleplayer(), "Acceptance must use a dedicated server"); s.setUsingWhitelist(false); s.setEnforceWhitelist(false); });
            publishServer(server, 1);
            try (var connection = server.connect()) {
                server.runCommand("op Alice");
                server.runCommand("forceload add 32 -16 64 16");
                send(context, "engineer project new");
                server.waitFor(s -> !EngineerMod.managerFor(s).projects.isEmpty());
                projectId = server.computeOnServer(s -> session(s, "Alice").projectId.toString());
                write("project", projectId);
                write("alice-uuid", server.computeOnServer(s -> player(s, "Alice").getUUID().toString()));
                write("world", server.computeOnServer(s -> EngineerMod.managerFor(s).worldId));
                send(context, "engineer select1 40 100 0");
                send(context, "engineer select2 45 101 0");
                send(context, "engineer reference signal-lamp");
                server.waitFor(s -> session(s, "Alice").blueprint != null && session(s, "Alice").blueprint.anchor().equals(new BlockPos(40, 100, 0)));
                server.runCommand("tp Alice 42 108 15 180 25");
                server.waitFor(s -> session(s, "Alice").blueprint.blocks().stream().allMatch(block -> session(s, "Alice").level().hasChunkAt(block.pos())));

                // Memory: literal text, attribution and bounded retrieval through ordinary chat.
                MESSAGES.clear(); memorySay(context, "Engineer, remember my goal: " + GOAL); expectChat(context, "knowledge revision 1");
                MESSAGES.clear(); memorySay(context, "Engineer, remember my decision: " + DECISION); expectChat(context, "knowledge revision 2");
                MESSAGES.clear(); memorySay(context, "Engineer, what do you remember about this project?"); expectChat(context, "Shared project notes, revision 2"); expectChat(context, DECISION);
                MESSAGES.clear(); memorySay(context, "Engineer, show note 1"); expectChat(context, "Note 1 (goal, current) by Alice: " + GOAL); expectChat(context, "not observed, built or tested");
                // Summary, guidance and comparison are read-only answers.
                MESSAGES.clear(); memorySay(context, "Engineer, summarize this project"); expectChat(context, "Player statements (owner-authored notes"); expectChat(context, "This summary started no work");
                MESSAGES.clear(); say(context, "Engineer, where did we leave off?"); expectChat(context, "Where we left off (rebuilt from project records just now)");
                MESSAGES.clear(); say(context, "Engineer, what should I do next?"); expectChat(context, "Suggested next step for"); expectChat(context, "This answer started no work");
                MESSAGES.clear(); say(context, "Engineer, what is still untested?"); expectChat(context, "What is still untested in");
                MESSAGES.clear(); memorySay(context, "Engineer, compare this with catalog not gate"); expectChat(context, "catalog not-gate"); expectChat(context, "Reliability: unavailable"); expectChat(context, "This comparison changed nothing.");
                context.takeScreenshot("acceptance-owner-memory");

                UUID project = UUID.fromString(projectId);
                server.runOnServer(s -> commandAs(s, "Alice", "engineer project share Bob editor"));
                server.waitFor(s -> project(s, project).access.allows(player(s, "Bob").getUUID(), ProjectAccess.Permission.BUILD));
                write("bob-uuid", server.computeOnServer(s -> player(s, "Bob").getUUID().toString()));
                write("snapshot-before.json", server.computeOnServer(s -> snapshot(s, project)));
                write("bob-go", "ready");
                awaitFile(context, "bob-reads.done");

                // Late reply A: Bob's read is in flight when Alice revokes him.
                pauseCompanion(context, "a");
                write("bob-late-ask", "ready"); awaitFile(context, "bob-late-asked");
                // Positive proof the request is really in flight on the frozen companion.
                await(context, () -> server.computeOnServer(s -> pending(s, "Bob") != null), "Bob's read in flight");
                Object bobFrame = server.computeOnServer(s -> pending(s, "Bob"));
                server.runOnServer(s -> commandAs(s, "Alice", "engineer project revoke Bob"));
                server.waitFor(s -> !project(s, project).access.allows(player(s, "Bob").getUUID(), ProjectAccess.Permission.VIEW));
                // The in-flight frame is dropped and its companion call cancelled before the companion can answer.
                await(context, () -> server.computeOnServer(s -> pending(s, "Bob") == null), "Bob's frame dropped after revocation");
                check(cancelled(bobFrame), "Bob's in-flight companion call was cancelled while the companion was frozen");
                resumeCompanion(context, "a");
                // A fresh owner read proves the resumed companion is answering again.
                MESSAGES.clear(); memorySay(context, "Engineer, show note 2"); expectChat(context, "Note 2 (decision, current) by Alice: " + DECISION, 60_000);
                write("late-a-resumed", "ready");
                awaitFile(context, "bob-late.done");

                // Late reply B: Alice's own search is in flight when she changes her selection.
                pauseCompanion(context, "b");
                MESSAGES.clear(); memorySay(context, "Engineer, find notes about entrance"); expectChat(context, "Searching shared project notes");
                await(context, () -> server.computeOnServer(s -> pending(s, "Alice") != null), "Alice's search in flight");
                Object aliceFrame = server.computeOnServer(s -> pending(s, "Alice"));
                send(context, "engineer project new");
                server.waitFor(s -> !projectId.equals(String.valueOf(EngineerMod.managerFor(s).selections.get(player(s, "Alice").getUUID()))));
                await(context, () -> server.computeOnServer(s -> pending(s, "Alice") == null), "Alice's frame dropped after the selection change");
                check(cancelled(aliceFrame), "Alice's in-flight companion call was cancelled while the companion was frozen");
                resumeCompanion(context, "b");
                // A fresh read on the new selection proves the resumed companion answers again
                // before the leak check, so the check is not vacuous.
                memorySay(context, "Engineer, what do you remember about this project?"); expectChat(context, "Shared project notes", 60_000);
                check(MESSAGES.stream().noneMatch(message -> message.contains("Note 1 (goal")), "A search answer for the previous selection must not arrive after the selection changed: " + MESSAGES);
                send(context, "engineer project use " + projectId);
                server.waitFor(s -> projectId.equals(String.valueOf(EngineerMod.managerFor(s).selections.get(player(s, "Alice").getUUID()))));
                MESSAGES.clear(); memorySay(context, "Engineer, find notes about entrance"); expectChat(context, "Note 1 (goal, current) by Alice", 60_000);
                write("late-b.done", "passed");

                write("eve-go", "ready");
                awaitFile(context, "eve-denied.done");
                write("snapshot-after.json", server.computeOnServer(s -> snapshot(s, project)));
                server.runOnServer(s -> {
                    var p = project(s, project);
                    check(p.owner.equals(player(s, "Alice").getUUID()), "Ownership never moves");
                    check(!p.access.members().containsKey(player(s, "Bob").getUUID()), "Revocation removed Bob");
                });
                write("bob-disconnect", "ready");
                await(context, () -> server.computeOnServer(s -> s.getPlayerList().getPlayerByName("Bob") == null && s.getPlayerList().getPlayerByName("Eve") == null), "Bob and Eve leave");
            }
            server.waitFor(s -> s.getPlayerList().getPlayerByName("Alice") == null);
        }
        disconnectedTitle(context);

        // Replace the real companion process too, then reopen the same saved world.
        write("companion-restart.request", "ready"); awaitFile(context, "companion-restart.done");
        try (var server = new TestDedicatedServerContextImpl(context, DedicatedServerImplUtil.start(context, config)); var connection = server.connect()) {
            String world = read("world");
            server.runOnServer(s -> check(EngineerMod.managerFor(s).worldId.equals(world) && EngineerMod.managerFor(s).projects.containsKey(UUID.fromString(projectId)), "The saved world reopens with the project"));
            publishServer(server, 2);
            send(context, "engineer project use " + projectId);
            server.waitFor(s -> projectId.equals(String.valueOf(EngineerMod.managerFor(s).selections.get(player(s, "Alice").getUUID()))));
            MESSAGES.clear(); memorySay(context, "Engineer, show note 1"); expectChat(context, "Note 1 (goal, current) by Alice: " + GOAL, 60_000);
            MESSAGES.clear(); memorySay(context, "Engineer, what do you remember about this project?"); expectChat(context, "Shared project notes, revision 2", 60_000);
            server.runOnServer(s -> {
                var p = project(s, UUID.fromString(projectId));
                check(p.owner.equals(UUID.fromString(read("alice-uuid"))), "Restart keeps ownership");
                check(!p.access.members().containsKey(UUID.fromString(read("bob-uuid"))), "Restart keeps the revocation");
            });
            write("snapshot-restart.json", server.computeOnServer(s -> snapshot(s, UUID.fromString(projectId))));
            write("gen2-ready", "ready");
            awaitFile(context, "bob-gen2.done");
            context.takeScreenshot("acceptance-owner-restart");
        }
    }

    // ---------------------------------------------------------------- Bob (editor, then revoked; no overlay)

    private static void editor(ClientGameTestContext context) throws Exception {
        check(!ClientPreview.enabled(), "Bob runs without the overlay");
        connect(context, 1);
        awaitFile(context, "bob-go");
        String project = read("project");
        MESSAGES.clear(); send(context, "engineer project use " + project);
        memorySay(context, "Engineer, what do you remember about this project?"); expectChat(context, GOAL);
        MESSAGES.clear(); memorySay(context, "Engineer, show note 1"); expectChat(context, "Note 1 (goal, current) by Alice: " + GOAL);
        MESSAGES.clear(); memorySay(context, "Engineer, find notes about entrance"); expectChat(context, "Note 1 (goal, current) by Alice");
        MESSAGES.clear(); memorySay(context, "Engineer, summarize this project"); expectChat(context, "This summary started no work");
        MESSAGES.clear(); memorySay(context, "Engineer, remember my decision: An editor cannot author this."); expectChat(context, "Only the project owner");
        MESSAGES.clear(); memorySay(context, "Engineer, compare this with catalog not gate"); expectChat(context, "This comparison changed nothing.");
        MESSAGES.clear(); say(context, "Engineer, explain how this works"); expectChat(context, "Lesson for component");
        check(view(context) == null, "A client without the overlay keeps no visual project cache");
        write("bob-reads.done", "passed");

        awaitFile(context, "bob-late-ask");
        MESSAGES.clear(); memorySay(context, "Engineer, what do you remember about this project?"); expectChat(context, "Reading shared project notes");
        write("bob-late-asked", "ready");
        awaitFile(context, "late-a-resumed");
        context.waitTicks(100);
        noLeak("A reply computed before revocation must not reach Bob afterwards");

        MESSAGES.clear(); send(context, "engineer project use " + project); expectChat(context, "unavailable or not shared");
        MESSAGES.clear(); memorySay(context, "Engineer, show note 1"); memorySay(context, "Engineer, summarize this project");
        say(context, "Engineer, where did we leave off?"); say(context, "Engineer, compare this with catalog not gate"); context.waitTicks(60);
        noLeak("A revoked member receives no notes, summary, guidance or comparison");
        write("bob-late.done", "passed");

        awaitFile(context, "bob-disconnect");
        disconnect(context);
        connect(context, 2);
        MESSAGES.clear(); send(context, "engineer project use " + project); expectChat(context, "unavailable or not shared");
        MESSAGES.clear(); memorySay(context, "Engineer, what do you remember about this project?"); memorySay(context, "Engineer, show note 1"); context.waitTicks(60);
        noLeak("Revocation survives the saved-world restart");
        write("bob-gen2.done", "passed");
        disconnect(context);
    }

    // ---------------------------------------------------------------- Eve (never shared; overlay on)

    private static void denied(ClientGameTestContext context) throws Exception {
        check(ClientPreview.enabled(), "Eve's client negotiates the overlay");
        connect(context, 1);
        awaitFile(context, "eve-go");
        String project = read("project");
        MESSAGES.clear(); send(context, "engineer project use " + project); expectChat(context, "not shared");
        MESSAGES.clear(); memorySay(context, "Engineer, what do you remember about this project?"); memorySay(context, "Engineer, show note 1");
        memorySay(context, "Engineer, find notes about entrance"); memorySay(context, "Engineer, summarize this project");
        say(context, "Engineer, what should I do next?"); say(context, "Engineer, compare this with catalog not gate"); context.waitTicks(60);
        noLeak("A denied player learns nothing about the project");
        check(view(context) == null, "A denied player receives no project preview");
        write("eve-denied.done", "passed");
        disconnect(context);
    }

    // ---------------------------------------------------------------- Fixtures and evidence

    private static void noLeak(String message) {
        for (String text : PRIVATE) check(MESSAGES.stream().noneMatch(line -> line.contains(text)), message + " (saw \"" + text + "\"): " + MESSAGES);
    }

    /** The player's in-flight project-knowledge frame, read reflectively so production code stays unchanged. */
    private static Object pending(MinecraftServer server, String name) {
        var player = server.getPlayerList().getPlayerByName(name);
        if (player == null) return null;
        try {
            var field = ProjectKnowledgeClient.class.getDeclaredField("active");
            field.setAccessible(true);
            return ((java.util.Map<?, ?>) field.get(EngineerMod.managerFor(server).knowledge)).get(player.getUUID());
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static boolean cancelled(Object frame) {
        check(frame != null, "Expected an in-flight frame");
        try {
            var field = frame.getClass().getDeclaredField("future");
            field.setAccessible(true);
            var future = (java.util.concurrent.CompletableFuture<?>) field.get(frame);
            return future != null && future.isCancelled();
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static void pauseCompanion(ClientGameTestContext context, String episode) {
        write("pause-" + episode + ".request", "ready"); awaitFile(context, "pause-" + episode + ".done");
    }

    private static void resumeCompanion(ClientGameTestContext context, String episode) {
        write("resume-" + episode + ".request", "ready"); awaitFile(context, "resume-" + episode + ".done");
    }

    private static EngineeringSession project(MinecraftServer server, UUID id) {
        var project = EngineerMod.managerFor(server).projects.get(id);
        check(project != null, "Expected project " + id);
        return project;
    }

    /** Blocks around the design, online players' inventories, ownership, verification and history. */
    private static String snapshot(MinecraftServer server, UUID id) {
        var p = project(server, id);
        var blocks = new StringBuilder();
        for (var pos : BlockPos.betweenClosed(34, 97, -6, 51, 106, 6)) {
            var state = p.level().getBlockState(pos);
            if (!state.isAir()) blocks.append(pos.toShortString()).append('=').append(state).append(';');
        }
        var result = new JsonObject();
        result.addProperty("blocksSha256", sha256(blocks.toString()));
        result.addProperty("nonAirBlocks", blocks.toString().chars().filter(c -> c == ';').count());
        var inventories = new JsonObject();
        for (String name : new String[]{"Alice", "Bob"}) {
            var player = server.getPlayerList().getPlayerByName(name);
            if (player == null) continue;
            var inventory = player.getInventory();
            var items = new StringBuilder();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                var stack = inventory.getItem(slot);
                if (!stack.isEmpty()) items.append(slot).append(':').append(stack).append(';');
            }
            inventories.addProperty(name, sha256(items.toString()));
        }
        result.add("inventories", inventories);
        result.addProperty("owner", p.owner.toString());
        result.addProperty("verified", p.verified);
        result.addProperty("journal", p.journal.size());
        result.addProperty("designRevision", p.revision());
        return result.toString();
    }

    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception error) { throw new AssertionError(error); }
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
        result.setProperty("level-name", "acceptance-world");
        return result;
    }

    private static void publishServer(TestDedicatedServerContext server, int generation) {
        int port = server.computeOnServer(MinecraftServer::getPort);
        var value = new JsonObject();
        value.addProperty("port", port);
        value.addProperty("generation", generation);
        server.runOnServer(s -> {
            var manager = EngineerMod.managerFor(s);
            value.addProperty("serverId", manager.serverId);
            value.addProperty("serverEpoch", manager.epoch.toString());
            value.addProperty("worldId", manager.worldId);
        });
        write("server.json", value.toString());
    }

    private static void expectChat(ClientGameTestContext context, String text) { expectChat(context, text, 20_000); }

    private static void expectChat(ClientGameTestContext context, String text, long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (MESSAGES.stream().anyMatch(message -> message.contains(text))) return;
            context.waitTicks(1);
        }
        throw new AssertionError("Expected chat: " + text + "; received " + MESSAGES);
    }

    private static void connect(ClientGameTestContext context, int generation) throws Exception {
        await(context, () -> {
            if (!Files.exists(directory().resolve("server.json"))) return false;
            return JsonParser.parseString(read("server.json")).getAsJsonObject().get("generation").getAsInt() == generation;
        }, "Dedicated server generation " + generation);
        int port = JsonParser.parseString(read("server.json")).getAsJsonObject().get("port").getAsInt();
        context.runOnClient(client -> {
            String address = "127.0.0.1:" + port;
            ConnectScreen.startConnecting(client.gui.screen(), client, ServerAddress.parseString(address), new ServerData("Minecraft AI Engineer acceptance", address, ServerData.Type.OTHER), false, null);
        });
        await(context, () -> context.computeOnClient(client -> client.player != null && client.level != null && client.getConnection() != null), "Native client join");
    }

    private static void memorySay(ClientGameTestContext context, String message) { say(context, message); context.waitTicks(21); }

    private static void say(ClientGameTestContext context, String message) {
        context.runOnClient(client -> client.getConnection().sendChat(message));
        context.waitTicks(1);
    }

    private static void send(ClientGameTestContext context, String command) {
        context.runOnClient(client -> {
            check(client.getConnection() != null, "Command needs a real Minecraft connection");
            client.getConnection().sendCommand(command);
        });
    }

    private static Object view(ClientGameTestContext context) { return context.computeOnClient(client -> ClientPreview.current()); }

    private static void disconnect(ClientGameTestContext context) {
        context.runOnClient(client -> client.disconnectFromWorld(Component.literal("Acceptance step complete")));
        disconnectedTitle(context);
    }

    private static void disconnectedTitle(ClientGameTestContext context) {
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
        String value = System.getenv("MCE_ACCEPT_COORD");
        if (value == null || value.isBlank()) throw new AssertionError("Missing acceptance coordination directory");
        return Path.of(value).toAbsolutePath().normalize();
    }

    private static void write(String name, String value) {
        try {
            Path target = directory().resolve(name), temporary = target.resolveSibling(name + "." + ProcessHandle.current().pid() + ".tmp");
            Files.writeString(temporary, value);
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception error) { throw new AssertionError("Cannot save acceptance coordination file", error); }
    }

    private static String read(String name) {
        try { return Files.readString(directory().resolve(name)); }
        catch (Exception error) { throw new AssertionError("Cannot read acceptance coordination file " + name, error); }
    }

    private static void awaitFile(ClientGameTestContext context, String name) { await(context, () -> Files.exists(directory().resolve(name)), name); }

    private static void await(ClientGameTestContext context, java.util.function.BooleanSupplier condition, String message) {
        long deadline = System.currentTimeMillis() + WAIT_MILLIS;
        while (!condition.getAsBoolean()) {
            try (var files = Files.list(directory())) {
                if (files.anyMatch(path -> path.getFileName().toString().endsWith(".failed"))) throw new AssertionError("Another acceptance client failed while waiting for " + message);
            } catch (java.io.IOException error) { throw new AssertionError(error); }
            if (System.currentTimeMillis() > deadline) throw new AssertionError("Timed out waiting for " + message);
            context.waitTicks(2);
            java.util.concurrent.locks.LockSupport.parkNanos(10_000_000);
        }
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
