package dev.minecraftai.engineer.client;

import java.util.ArrayDeque;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Reads the engineer's own replies aloud through the player's local speech program. Only system
 * lines that start with "Engineer: " are spoken; other players' chat never is. One utterance at a
 * time, at most MAX_QUEUED waiting, and stop silences everything at once.
 *
 * A failing speech program is one episode: it is reported once, and later replies stay text-only
 * (no new attempt, no repeated notice) until the player explicitly retries with the stop key.
 * Nothing retries on its own.
 */
final class VoicePlayback {
    static final String SPOKEN_PREFIX = "Engineer: ";
    static final int MAX_QUEUED = 4, MAX_SPOKEN = 600;

    private final Supplier<VoiceAdapters.Speaker> speakers;
    private final BooleanSupplier idle;
    record FailureNotice(String text,BooleanSupplier current) {
        void deliver(Consumer<String> output){if(current.getAsBoolean())output.accept(text);}
    }
    private final Consumer<FailureNotice> failures;
    private final ArrayDeque<String> queue = new ArrayDeque<>();
    private VoiceAdapters.Speaker current;
    private Thread worker;
    private int spoken, generation;
    private boolean failed;

    /** failures receives one plain line when the speech program fails on its own (never when stop ended it). */
    VoicePlayback(Supplier<VoiceAdapters.Speaker> speakers, BooleanSupplier idle, Consumer<FailureNotice> failures) {
        this.speakers = speakers;
        this.idle = idle;
        this.failures = failures;
    }

    static String failure(Exception failed) {
        String why = failed instanceof LocalProcess.Failure process ? process.getMessage() : "failed";
        return "Spoken replies stopped: the speech program " + why + ". Replies still show in chat, and typing still works."
                + " Fix the speaker in config/" + VoiceConfig.FILE + ", then press the voice stop key (B by default) to try spoken replies again.";
    }

    /** Called with each received system line. Returns true when it was queued to be spoken. */
    boolean offer(String line, boolean overlay) {
        if (overlay || line == null || !line.startsWith(SPOKEN_PREFIX) || !idle.getAsBoolean()) return false;
        String text = VoiceController.clean(line.substring(SPOKEN_PREFIX.length()));
        if (text.isEmpty()) return false;
        if (text.length() > MAX_SPOKEN) text = text.substring(0, MAX_SPOKEN);
        synchronized (this) {
            if (failed || queue.size() >= MAX_QUEUED) return false; // A failed program is not retried by the next reply.
            queue.add(text);
            if (worker == null || !worker.isAlive()) worker = Thread.ofPlatform().daemon().name("engineer-voice-speaker").start(this::run);
            notifyAll();
            return true;
        }
    }

    private void run() {
        while (true) {
            String text;
            VoiceAdapters.Speaker speaker;
            int run;
            synchronized (this) {
                if (queue.isEmpty()) { worker = null; return; }
                text = queue.poll();
                try { speaker = speakers.get(); } catch (RuntimeException broken) { speaker = null; }
                if (speaker == null) { queue.clear(); worker = null; return; } // Speech is off.
                current = speaker;
                run = generation;
            }
            try { speaker.speak(text); }
            catch (Exception error) {
                boolean own;
                synchronized (this) { own = run == generation; if (own) { queue.clear(); failed = true; } }
                if (own) failures.accept(new FailureNotice(failure(error),()->{synchronized(this){return run==generation;}}));
            }
            synchronized (this) { current = null; if (run == generation) spoken++; }
        }
    }

    /** Silences the current utterance and drops everything waiting. */
    void stop() {
        VoiceAdapters.Speaker speaking;
        synchronized (this) {
            generation++;
            queue.clear();
            speaking = current;
        }
        if (speaking != null) speaking.stop();
    }

    /** The player's explicit retry after a failure: the next reply tries the speech program again. */
    synchronized void retry() { failed = false; }

    synchronized boolean failed() { return failed; }
    synchronized boolean busy() { return current != null || !queue.isEmpty(); }
    synchronized int queued() { return queue.size(); }
    synchronized int spoken() { return spoken; }
}
