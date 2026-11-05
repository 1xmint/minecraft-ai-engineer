package dev.minecraftai.engineer.client;

import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Push-to-talk state on the client thread. Speech only ever becomes a chat box prefilled with
 * "Engineer, ..." that the player sends or discards themself; nothing here sends a message.
 */
final class VoiceController {
    enum State { IDLE, LISTENING, TRANSCRIBING }

    /** What one press uses; off is the plain reason voice cannot start, or null. */
    record Setup(VoiceAdapters.AudioSource source, VoiceAdapters.Recognizer recognizer, String off) {
        static Setup off(String reason) { return new Setup(null, null, reason); }
    }

    interface Output {
        void prefill(String chatText);
        void notice(String text);
        /** False when the chat box may not be filled now (a screen is open, the window lost focus or the world is gone). */
        default boolean ready() { return true; }
    }

    static final int MAX_LISTEN_TICKS = 30 * 20, NOTICE_TICKS = 100, MAX_CHAT = 256;
    static final String PREFIX = "Engineer, ";

    private final Supplier<Setup> setups;
    private final Executor background, client;
    private final Output output;
    private final Runnable stopPlayback;
    private State state = State.IDLE;
    private Setup current;
    private int generation, listenTicks, ticks, noticeUntil;
    private boolean wasDown;
    private String notice;

    VoiceController(Supplier<Setup> setups, Executor background, Executor client, Output output, Runnable stopPlayback) {
        this.setups = setups; this.background = background; this.client = client; this.output = output; this.stopPlayback = stopPlayback;
    }

    State state() { return state; }

    /** Called once per client tick with the live key, screen, focus and world state. */
    void tick(boolean keyDown, boolean screenOpen, boolean focused, boolean inWorld) {
        ticks++;
        boolean pressed = keyDown && !wasDown;
        wasDown = keyDown;
        switch (state) {
            case IDLE -> { if (pressed && !screenOpen && focused && inWorld) press(); }
            case LISTENING -> {
                if (!inWorld || screenOpen || !focused) cancel("Voice cancelled. Nothing was sent.");
                else if (!keyDown || ++listenTicks >= MAX_LISTEN_TICKS) finish();
            }
            case TRANSCRIBING -> { if (!inWorld || screenOpen || !focused) cancel("Voice cancelled. Nothing was sent."); }
        }
    }

    private void press() {
        stopPlayback.run(); // Never record the engineer's own voice.
        Setup setup;
        try { setup = setups.get(); }
        catch (RuntimeException broken) { setup = Setup.off("Voice could not start. Typing still works."); }
        if (setup.off() != null) { notice(setup.off()); return; }
        try { setup.source().start(); }
        catch (VoiceAdapters.Unavailable unavailable) { notice(unavailable.getMessage() + " Typing still works."); return; }
        current = setup;
        generation++;
        listenTicks = 0;
        state = State.LISTENING;
        notice = null;
    }

    private void finish() {
        byte[] pcm = current.source().stop();
        var recognizer = current.recognizer();
        int run = generation;
        state = State.TRANSCRIBING;
        background.execute(() -> {
            String text = null, error = null;
            try { text = recognizer.transcribe(pcm); }
            catch (LocalProcess.Failure failure) { error = "Speech recognition " + failure.getMessage() + ". Typing still works."; }
            catch (Exception failure) { error = "Speech recognition failed. Typing still works."; }
            String heard = text, failed = error;
            client.execute(() -> deliver(run, heard, failed));
        });
    }

    /**
     * Waits at most millis for work already queued on background (a cancelled recognition and its
     * own cleanup) to finish. Used on quit so this session's temporary recording is deleted before
     * the game exits. Returns false at the bound; it never waits longer and deletes nothing itself.
     */
    static boolean drain(java.util.concurrent.Executor background, long millis) {
        var done = new java.util.concurrent.CompletableFuture<Void>();
        try {
            background.execute(() -> done.complete(null));
            done.get(millis, java.util.concurrent.TimeUnit.MILLISECONDS);
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception notFinished) {
            return false;
        }
    }

    /** Ends listening or recognition; any late transcript is dropped and nothing is sent. */
    void cancel(String message) {
        if (state == State.LISTENING) current.source().cancel();
        if (state == State.TRANSCRIBING) current.recognizer().cancel();
        boolean active = state != State.IDLE;
        generation++;
        state = State.IDLE;
        current = null;
        if (active && message != null) notice(message);
    }

    private void deliver(int run, String text, String error) {
        if (run != generation || state != State.TRANSCRIBING) return; // Cancelled meanwhile.
        state = State.IDLE;
        current = null;
        if (error != null) { notice(error); return; }
        if (!output.ready()) { notice("Voice cancelled. Nothing was sent."); return; } // Never overwrite what the player opened meanwhile.
        String heard = clean(text);
        if (heard.isEmpty()) { notice("I did not hear any words. Try again, or type."); return; }
        output.prefill(PREFIX + heard);
    }

    /** One line of plain text, no address prefix of its own, short enough for one chat message. */
    static String clean(String text) {
        if (text == null) return "";
        String line = text.codePoints().map(c -> Character.isISOControl(c) || c == '\u00a7' || Character.getType(c) == Character.FORMAT ? ' ' : c)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString().replaceAll("\\s+", " ").strip();
        if (line.matches("^[\\[(][^\\])]*[\\])]$")) return ""; // e.g. [BLANK_AUDIO]
        line = line.replaceFirst("(?i)^(?:hey\\s+)?engineer\\s*[,:]?\\s*", "");
        int room = MAX_CHAT - PREFIX.length();
        if (line.length() > room) line = line.substring(0, room).strip();
        return line;
    }

    private void notice(String text) {
        notice = text;
        noticeUntil = ticks + NOTICE_TICKS;
        output.notice(text);
    }

    /** The line the HUD shows, or null. */
    String status() {
        return switch (state) {
            case LISTENING -> "Listening... release to finish, Esc to cancel (" + String.format(Locale.ROOT, "%d", (MAX_LISTEN_TICKS - listenTicks) / 20) + "s left)";
            case TRANSCRIBING -> "Transcribing on this computer... Esc to cancel";
            case IDLE -> notice != null && ticks < noticeUntil ? "Voice: " + notice : null;
        };
    }

    String lastNotice() { return notice; }

    /** Shows a voice notice from elsewhere in the voice client, such as a failed speech program. */
    void report(String text) { notice(text); }
}
