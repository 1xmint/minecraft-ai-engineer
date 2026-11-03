package dev.minecraftai.engineer.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/**
 * Proves the local voice path with fake microphone/recognizer/speaker adapters inside a real
 * client and integrated server, and the process adapters with real local programs. No model,
 * microphone, speaker or network service is used.
 */
public final class VoiceClientGameTests implements FabricClientGameTest {
    /** Everything the server sent this client, except the client's own local "Voice:" notices. */
    private static final List<String> RECEIVED = new ArrayList<>();
    private static boolean listening;

    static final class FakeSource implements VoiceAdapters.AudioSource {
        final AtomicInteger starts = new AtomicInteger(), cancels = new AtomicInteger();
        @Override public void start() { starts.incrementAndGet(); }
        @Override public byte[] stop() { return new byte[3200]; }
        @Override public void cancel() { cancels.incrementAndGet(); }
    }

    static final class FakeRecognizer implements VoiceAdapters.Recognizer {
        final AtomicInteger calls = new AtomicInteger(), cancels = new AtomicInteger();
        volatile String answer = "what does this do";
        volatile boolean fail;
        volatile CountDownLatch gate;
        @Override public String transcribe(byte[] pcm) throws Exception {
            calls.incrementAndGet();
            var wait = gate;
            if (wait != null) wait.await(10, TimeUnit.SECONDS);
            if (fail) throw new IllegalStateException("model missing");
            return answer; // Even after cancel: the controller itself must drop late results.
        }
        @Override public void cancel() { cancels.incrementAndGet(); var wait = gate; if (wait != null) wait.countDown(); }
    }

    static final class FakeSpeaker implements VoiceAdapters.Speaker {
        final List<String> heard = new ArrayList<>();
        final AtomicInteger stops = new AtomicInteger();
        volatile boolean hold;
        volatile CountDownLatch release = new CountDownLatch(1);
        @Override public void speak(String text) throws Exception {
            synchronized (heard) { heard.add(text); }
            if (hold && !release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("never stopped");
        }
        @Override public void stop() { stops.incrementAndGet(); release.countDown(); }
        List<String> heard() { synchronized (heard) { return List.copyOf(heard); } }
    }

    @Override public void runTest(ClientGameTestContext context) {
        if (System.getenv("MCE_MP_ROLE") != null) return;
        try {
            configAndCleanup();
            processAdapters();
        } catch (Exception failed) {
            throw new AssertionError("Voice adapter checks failed: " + failed, failed);
        }
        if (!listening) {
            listening = true;
            ClientReceiveMessageEvents.GAME.register((message, overlay) -> record(message.getString()));
            ClientReceiveMessageEvents.CHAT.register((message, signed, sender, type, time) -> record(message.getString()));
        }
        var source = new FakeSource();
        var recognizer = new FakeRecognizer();
        var speaker = new FakeSpeaker();
        var focused = new AtomicBoolean(true);
        VoiceClient.focus = focused::get;
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            TestServerContext server = world.getServer();
            server.runOnServer(s -> player(s).setGameMode(GameType.CREATIVE));
            context.waitTicks(5);
            world.getConnection().waitForChunksRender();

            // 1. Voice is off without a config; nothing is recorded or sent and typing is unaffected.
            Path config = VoiceClient.configFile();
            write(config, null);
            VoiceClient.setups = VoiceClient::configuredSetup;
            clear(context);
            talk(context);
            check(notice(context).contains("Voice is off") && notice(context).contains("Typing still works"), "Missing config explains how to turn voice on: " + notice(context));
            write(config, "{");
            talk(context);
            check(notice(context).contains("is invalid"), "Invalid config is named: " + notice(context));
            write(config, "{\"enabled\": false, \"recognizer\": {\"command\": [\"sh\", \"{wav}\"]}}");
            talk(context);
            check(notice(context).contains("enabled is false"), "Disabled config stays off: " + notice(context));
            // 2. The real microphone adapter on a runner with no audio device.
            write(config, "{\"enabled\": true, \"recognizer\": {\"command\": [\"sh\", \"-c\", \"printf 'from the real path'\", \"{wav}\"]}}");
            if (!VoiceAdapters.microphonePresent()) {
                talk(context);
                check(notice(context).contains("No microphone is available") && notice(context).contains("Typing still works"), "No microphone degrades to text: " + notice(context));
            } else {
                hold(context); release(context);
                context.waitFor(c -> chatText(c) != null || stateOn(c) == VoiceController.State.IDLE, 400);
                context.runOnClient(c -> c.gui.setScreen(null));
            }
            write(config, null);
            context.waitTicks(40);
            check(sent().isEmpty(), "Voice that is off sends nothing: " + sent());

            VoiceClient.setups = () -> new VoiceController.Setup(source, recognizer, null);

            // 3. Push-to-talk shows listening, then only prefills the chat box.
            hold(context);
            check(context.computeOnClient(c -> VoiceClient.controller().status()).startsWith("Listening"), "Listening state is visible");
            context.takeScreenshot("voice-01-listening");
            release(context);
            context.waitFor(c -> chatText(c) != null, 200);
            check("Engineer, what does this do".equals(context.computeOnClient(VoiceClientGameTests::chatText)), "Transcript is prefilled as an addressed request");
            context.takeScreenshot("voice-02-transcript-preview");
            context.waitTicks(40);
            check(sent().isEmpty(), "A transcript is never sent by itself: " + sent());

            // 4. Esc discards the preview: nothing is sent.
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.waitTicks(20);
            check(context.computeOnClient(c -> c.gui.screen() == null) && sent().isEmpty(), "Discarded preview sends nothing: " + sent());

            // 5. Enter sends exactly what typing the same words sends, through the same server path.
            hold(context); release(context);
            context.waitFor(c -> chatText(c) != null, 200);
            context.getInput().pressKey(InputConstants.KEY_RETURN);
            context.waitFor(c -> sent().stream().anyMatch(line -> line.startsWith("Engineer: ")), 200);
            context.waitTicks(40);
            var voiced = sent();
            check(voiced.contains("You: what does this do"), "The voiced request reaches the server once: " + voiced);
            clear(context);
            context.setScreen(() -> new ChatScreen("", false));
            context.getInput().typeChars("Engineer, what does this do");
            context.getInput().pressKey(InputConstants.KEY_RETURN);
            context.waitFor(c -> sent().stream().anyMatch(line -> line.startsWith("Engineer: ")), 200);
            context.waitTicks(40);
            check(voiced.equals(sent()), "Voice and typing get the same reply: voiced " + voiced + ", typed " + sent());
            clear(context);

            // 6. Esc while listening cancels before recognition.
            int calls = recognizer.calls.get();
            hold(context);
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.waitTicks(3);
            check(state(context) == VoiceController.State.IDLE && source.cancels.get() == 1 && recognizer.calls.get() == calls, "Esc while listening cancels before recognition");
            context.getInput().releaseKey(VoiceClient.talkKey());
            context.setScreen(() -> null);
            context.waitTicks(40);
            check(sent().isEmpty() && context.computeOnClient(c -> !(c.gui.screen() instanceof ChatScreen)), "Cancelled speech sends nothing: " + sent());
            check(notice(context).contains("Nothing was sent"), "Cancel is confirmed: " + notice(context));

            // 7. Esc while transcribing drops the late transcript.
            recognizer.gate = new CountDownLatch(1);
            hold(context); release(context);
            check(state(context) == VoiceController.State.TRANSCRIBING, "Recognition is visibly running");
            check(context.computeOnClient(c -> VoiceClient.controller().status()).startsWith("Transcribing"), "Transcribing state is visible");
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.waitTicks(3);
            check(recognizer.cancels.get() == 1 && state(context) == VoiceController.State.IDLE, "Esc stops recognition");
            context.setScreen(() -> null);
            context.waitTicks(40);
            check(sent().isEmpty() && context.computeOnClient(c -> !(c.gui.screen() instanceof ChatScreen)), "A late transcript after cancel is dropped: " + sent());
            recognizer.gate = null;

            // 8. Losing window focus while listening cancels.
            hold(context);
            focused.set(false);
            context.waitTicks(3);
            check(state(context) == VoiceController.State.IDLE && source.cancels.get() == 2, "Focus loss cancels");
            context.getInput().releaseKey(VoiceClient.talkKey());
            focused.set(true);
            context.waitTicks(40);
            check(sent().isEmpty() && recognizer.calls.get() == calls + 1, "Focus loss sends nothing: " + sent());

            // 9. A spoken "yes" cannot approve anything by itself.
            recognizer.answer = "yes";
            hold(context); release(context);
            context.waitFor(c -> chatText(c) != null, 200);
            check("Engineer, yes".equals(context.computeOnClient(VoiceClientGameTests::chatText)), "Yes is only a preview");
            context.waitTicks(40);
            check(sent().isEmpty(), "Spoken yes sends nothing: " + sent());
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.waitTicks(3);

            // 10. A spoken slash command stays an addressed chat request and never runs.
            recognizer.answer = "/gamemode survival";
            hold(context); release(context);
            context.waitFor(c -> chatText(c) != null, 200);
            check("Engineer, /gamemode survival".equals(context.computeOnClient(VoiceClientGameTests::chatText)), "A spoken command is prefixed");
            context.getInput().pressKey(InputConstants.KEY_RETURN);
            context.waitFor(c -> !sent().isEmpty(), 200);
            context.waitTicks(20);
            server.runOnServer(s -> check(player(s).isCreative(), "A spoken command never runs as a command"));
            check(sent().contains("You: /gamemode survival"), "It reached the engineer as chat: " + sent());
            clear(context);

            // 11. Recognizer failure and silence fall back to typing.
            recognizer.fail = true;
            hold(context); release(context);
            context.waitFor(c -> stateOn(c) == VoiceController.State.IDLE, 200);
            check(notice(context).contains("Speech recognition failed") && notice(context).contains("Typing still works"), "Recognizer failure is explained: " + notice(context));
            recognizer.fail = false;
            recognizer.answer = " [BLANK_AUDIO] ";
            hold(context); release(context);
            context.waitFor(c -> stateOn(c) == VoiceController.State.IDLE, 200);
            check(notice(context).contains("did not hear"), "Silence is explained: " + notice(context));
            context.waitTicks(20);
            check(sent().isEmpty() && context.computeOnClient(c -> c.gui.screen() == null), "Failures send nothing: " + sent());

            // 12. Playback: only the engineer's own replies, stoppable, bounded.
            VoiceClient.speakers = () -> speaker;
            server.runOnServer(s -> player(s).sendSystemMessage(Component.literal("Engineer: hello there")));
            context.waitFor(c -> speaker.heard().contains("hello there"), 200);
            server.runOnServer(s -> player(s).sendSystemMessage(Component.literal("Server restarting soon")));
            server.runCommand("say Engineer: this is chat, not the engineer");
            context.waitTicks(20);
            check(speaker.heard().equals(List.of("hello there")), "Other messages are never spoken: " + speaker.heard());

            speaker.hold = true;
            speaker.release = new CountDownLatch(1);
            server.runOnServer(s -> { for (int i = 0; i < 8; i++) player(s).sendSystemMessage(Component.literal("Engineer: reply " + i)); });
            context.waitFor(c -> speaker.heard().contains("reply 0"), 200);
            context.waitTicks(5);
            int queued = context.computeOnClient(c -> VoiceClient.playback().queued());
            check(queued <= VoicePlayback.MAX_QUEUED, "Playback queue is bounded: " + queued);
            context.getInput().pressKey(VoiceClient.stopKey());
            context.waitFor(c -> !VoiceClient.playback().busy(), 100);
            check(speaker.stops.get() >= 1 && !speaker.heard().contains("reply 1"), "Stop silences speech and drops the queue: " + speaker.heard());

            speaker.release = new CountDownLatch(1);
            server.runOnServer(s -> player(s).sendSystemMessage(Component.literal("Engineer: talking over you")));
            context.waitFor(c -> speaker.heard().contains("talking over you"), 200);
            int stops = speaker.stops.get();
            recognizer.answer = "stop talking";
            hold(context);
            check(speaker.stops.get() == stops + 1, "Pressing talk stops playback first");
            server.runOnServer(s -> player(s).sendSystemMessage(Component.literal("Engineer: while listening")));
            context.waitTicks(5);
            check(!speaker.heard().contains("while listening"), "Nothing is spoken while the microphone is open");
            release(context);
            context.waitFor(c -> chatText(c) != null, 200);
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.waitTicks(20);
            check(sent().stream().noneMatch(line -> line.startsWith("You:")), "Nothing was sent during playback checks: " + sent());
            context.takeScreenshot("voice-03-after-playback");
        } finally {
            VoiceClient.setups = VoiceClient::configuredSetup;
            VoiceClient.speakers = VoiceClient::configuredSpeaker;
            VoiceClient.focus = () -> Minecraft.getInstance().isWindowActive();
            try { write(VoiceClient.configFile(), null); } catch (Exception ignored) { }
        }
    }

    private static void configAndCleanup() throws Exception {
        Path dir = Files.createTempDirectory("engineer-voice-config-test");
        Path file = dir.resolve("engineer-voice.json");
        check(VoiceConfig.load(file) == null, "A missing config means voice is off");
        for (String invalid : List.of("[]", "{\"recognizer\": {\"command\": [\"whisper\"]}}", "{\"recognizer\": {\"command\": []}}",
                "{\"recognizer\": {\"command\": [\"a\", \"{wav}\"], \"timeoutSeconds\": 0}}", "{\"speaker\": {\"command\": \"say hi\"}}")) {
            write(file, invalid);
            try { VoiceConfig.load(file); throw new AssertionError("Invalid config was accepted: " + invalid); }
            catch (IllegalArgumentException expected) { /* rejected with a reason */ }
        }
        write(file, "{\"enabled\": true, \"recognizer\": {\"command\": [\"whisper-cli\", \"-f\", \"{wav}\"]}, \"speaker\": {\"command\": [\"espeak-ng\", \"--stdin\"], \"timeoutSeconds\": 9}}");
        var config = VoiceConfig.load(file);
        check(config.enabled() && config.recognizer().timeoutSeconds() == 60 && config.speaker().timeoutSeconds() == 9, "A valid config is read: " + config);
        write(file, null);
        Files.deleteIfExists(dir);

        check(VoiceController.clean("  [BLANK_AUDIO]\n").isEmpty() && VoiceController.clean("(music)").isEmpty(), "Recognizer markers are not words");
        check(VoiceController.clean("Engineer, build a lamp").equals("build a lamp") && VoiceController.clean("hey engineer: why").equals("why"), "A spoken address is not doubled");
        check(VoiceController.clean("line one\n\tline\u0007two").equals("line one line two"), "Control characters become spaces");
        check(VoiceController.clean("red\u00a7cstone\u202e").equals("red cstone"), "Formatting codes are not passed on");
        check(("Engineer, " + VoiceController.clean("x".repeat(400))).length() == VoiceController.MAX_CHAT, "Transcripts fit one chat message");
    }

    private static void processAdapters() throws Exception {
        var recognizer = new VoiceAdapters.ProcessRecognizer(new VoiceConfig.Command(List.of("sh", "-c", "head -c 4 \"$0\"; printf ' %s' \"$0\"", "{wav}"), 10));
        String out = recognizer.transcribe(new byte[3200]);
        check(out.startsWith("RIFF "), "The recognizer receives a WAV file: " + out);
        Path wav = Path.of(out.substring(5).strip());
        check(!Files.exists(wav) && !Files.exists(wav.getParent()), "The recording is deleted after recognition: " + wav);

        expectFailure(() -> new VoiceAdapters.ProcessRecognizer(new VoiceConfig.Command(List.of("sh", "-c", "exit 3", "{wav}"), 10)).transcribe(new byte[10]), "exited with code 3");
        expectFailure(() -> new VoiceAdapters.ProcessRecognizer(new VoiceConfig.Command(List.of("/nonexistent/whisper-cli", "{wav}"), 10)).transcribe(new byte[10]), "could not start");

        var slow = LocalProcess.start(List.of("sh", "-c", "sleep 30"), null);
        long started = System.nanoTime();
        expectFailure(() -> slow.finish(null, 1), "took longer than 1 seconds");
        check(!slow.alive() && System.nanoTime() - started < TimeUnit.SECONDS.toNanos(5), "A slow program is stopped at its timeout");

        var cancellable = new VoiceAdapters.ProcessRecognizer(new VoiceConfig.Command(List.of("sh", "-c", "sleep 30", "{wav}"), 60));
        var error = new AtomicReference<Exception>();
        var thread = Thread.ofPlatform().start(() -> { try { cancellable.transcribe(new byte[10]); } catch (Exception e) { error.set(e); } });
        Thread.sleep(500);
        cancellable.cancel();
        thread.join(5000);
        check(!thread.isAlive() && error.get() instanceof LocalProcess.Failure && error.get().getMessage().contains("stopped"), "Cancel stops recognition: " + error.get());

        Path spoken = Files.createTempFile("engineer-voice-spoken", ".txt");
        new VoiceAdapters.ProcessSpeaker(new VoiceConfig.Command(List.of("sh", "-c", "cat > \"$0\"", spoken.toString()), 10)).speak("hello; $(rm -rf ~) there");
        check(Files.readString(spoken, StandardCharsets.UTF_8).equals("hello; $(rm -rf ~) there"), "Speech text goes to stdin untouched, never to a shell");
        Files.deleteIfExists(spoken);

        var speaker = new VoiceAdapters.ProcessSpeaker(new VoiceConfig.Command(List.of("sh", "-c", "sleep 30"), 60));
        var speakError = new AtomicReference<Exception>();
        var speaking = Thread.ofPlatform().start(() -> { try { speaker.speak("long"); } catch (Exception e) { speakError.set(e); } });
        Thread.sleep(500);
        speaker.stop();
        speaking.join(5000);
        check(!speaking.isAlive() && speakError.get() != null, "Stop ends playback at once");

        var early = new VoiceAdapters.ProcessSpeaker(new VoiceConfig.Command(List.of("sh", "-c", "sleep 30"), 60));
        early.stop();
        long before = System.nanoTime();
        try { early.speak("too late"); throw new AssertionError("A stopped speaker still spoke"); }
        catch (LocalProcess.Failure expected) { check(System.nanoTime() - before < TimeUnit.SECONDS.toNanos(5), "Stop before start never plays"); }

        Path foreign = Files.createTempDirectory("engineer-voice-");
        byte[] otherRecording=new byte[]{1,2,3,4};
        try {
            Files.write(foreign.resolve("speech.wav"),otherRecording);
            Files.writeString(foreign.resolve("notes.txt"),"not ours");
            var isolated=new VoiceAdapters.ProcessRecognizer(new VoiceConfig.Command(List.of("sh","-c","head -c 4 \"$0\"; printf ' %s' \"$0\"","{wav}"),10));
            String own=isolated.transcribe(new byte[32]);Path ownWav=Path.of(own.substring(5).strip());
            check(!Files.exists(ownWav) && !Files.exists(ownWav.getParent()),"Recognition cleans only its own private directory");
            check(java.util.Arrays.equals(Files.readAllBytes(foreign.resolve("speech.wav")),otherRecording) && Files.readString(foreign.resolve("notes.txt")).equals("not ours"),"Another instance's identically named audio and files remain unchanged");
        } finally {
            Files.deleteIfExists(foreign.resolve("speech.wav"));Files.deleteIfExists(foreign.resolve("notes.txt"));Files.deleteIfExists(foreign);
        }
    }

    private interface Action { void run() throws Exception; }
    private static void expectFailure(Action action, String text) throws Exception {
        try { action.run(); } catch (LocalProcess.Failure failure) { check(failure.getMessage().contains(text), "Expected '" + text + "', got " + failure.getMessage()); return; }
        throw new AssertionError("Expected failure: " + text);
    }

    private static void record(String line) {
        if (line.startsWith("Voice: ")) return;
        synchronized (RECEIVED) { RECEIVED.add(line); }
    }
    private static List<String> sent() { synchronized (RECEIVED) { return List.copyOf(RECEIVED); } }
    private static void clear(ClientGameTestContext context) {
        context.waitTicks(2);
        synchronized (RECEIVED) { RECEIVED.clear(); }
    }

    private static void talk(ClientGameTestContext context) { hold(context); context.getInput().releaseKey(VoiceClient.talkKey()); context.waitTicks(3); }
    private static void hold(ClientGameTestContext context) { context.getInput().holdKey(VoiceClient.talkKey()); context.waitTicks(3); }
    private static void release(ClientGameTestContext context) { context.getInput().releaseKey(VoiceClient.talkKey()); context.waitTicks(2); }
    private static VoiceController.State state(ClientGameTestContext context) { return context.computeOnClient(VoiceClientGameTests::stateOn); }
    private static VoiceController.State stateOn(Minecraft client) { return VoiceClient.controller().state(); }
    private static String notice(ClientGameTestContext context) { return String.valueOf((Object) context.computeOnClient(c -> VoiceClient.controller().lastNotice())); }

    private static String chatText(Minecraft client) {
        if (!(client.gui.screen() instanceof ChatScreen chat)) return null;
        for (var child : chat.children()) if (child instanceof EditBox box) return box.getValue();
        return null;
    }

    private static void write(Path file, String text) {
        try {
            if (text == null) { Files.deleteIfExists(file); return; }
            Files.createDirectories(file.getParent());
            Files.writeString(file, text, StandardCharsets.UTF_8);
        } catch(java.io.IOException failed){throw new java.io.UncheckedIOException("Voice fixture config could not be written",failed);}
    }

    private static ServerPlayer player(MinecraftServer server) { return server.getPlayerList().getPlayers().getFirst(); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
