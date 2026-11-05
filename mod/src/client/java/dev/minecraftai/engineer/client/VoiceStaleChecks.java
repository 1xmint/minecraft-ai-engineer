package dev.minecraftai.engineer.client;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Deterministic checks, with injected fake audio and no world, that a transcript arriving late never
 * fills the chat box (disconnect, leaving the world, or a screen or focus change between ticks), and that
 * a failing speech program is reported in plain words instead of failing silently.
 */
final class VoiceStaleChecks {
    private VoiceStaleChecks() { }

    private static final class FakeOutput implements VoiceController.Output {
        final List<String> prefills = new ArrayList<>(), notices = new ArrayList<>();
        final AtomicBoolean ready = new AtomicBoolean(true);
        @Override public void prefill(String chatText) { prefills.add(chatText); }
        @Override public void notice(String text) { notices.add(text); }
        @Override public boolean ready() { return ready.get(); }
    }

    /** Recognition runs only when the check says so, so "late" is exact rather than timing-dependent. */
    private static final class Held {
        final List<Runnable> pending = new ArrayList<>();
        void runAll() { var now = List.copyOf(pending); pending.clear(); now.forEach(Runnable::run); }
    }

    static void run() throws Exception {
        controllerDropsLateSpeech();
        speakerFailureIsReported();
    }

    private static void controllerDropsLateSpeech() {
        // Control: with nothing in the way, the transcript only prefills the chat box.
        var c = fresh();
        speakAndRelease(c);
        c.background.runAll();
        check(c.output.prefills.equals(List.of("Engineer, what does this do")), "Control transcript is prefilled: " + c.output.prefills);

        // A screen opened or focus moved after release but before the transcript arrived.
        c = fresh();
        speakAndRelease(c);
        c.output.ready.set(false);
        c.background.runAll();
        check(c.output.prefills.isEmpty() && c.controller.state() == VoiceController.State.IDLE, "A late transcript never replaces an open screen: " + c.output.prefills);
        check(c.output.notices.contains("Voice cancelled. Nothing was sent."), "The dropped transcript is explained: " + c.output.notices);

        // Disconnect while transcribing: the client calls cancel(null), and the late result is dropped silently.
        c = fresh();
        speakAndRelease(c);
        c.controller.cancel(null);
        c.background.runAll();
        check(c.output.prefills.isEmpty() && c.recognizer.cancels.get() == 1 && c.output.notices.isEmpty(), "Disconnect drops a late transcript: " + c.output.prefills + " " + c.output.notices);

        // Leaving the world while transcribing.
        c = fresh();
        speakAndRelease(c);
        c.controller.tick(false, false, true, false);
        c.background.runAll();
        check(c.output.prefills.isEmpty() && c.recognizer.cancels.get() == 1, "Leaving the world drops a late transcript: " + c.output.prefills);

        // A new press after a cancelled one gets only its own words, never the older transcript.
        c = fresh();
        speakAndRelease(c);
        c.controller.cancel("Voice cancelled. Nothing was sent.");
        var stale = List.copyOf(c.background.pending);
        c.background.pending.clear();
        c.recognizer.answer = "build the door";
        speakAndRelease(c);
        stale.forEach(Runnable::run);
        check(c.output.prefills.isEmpty() && c.controller.state() == VoiceController.State.TRANSCRIBING, "The older transcript is dropped: " + c.output.prefills);
        c.background.runAll();
        check(c.output.prefills.equals(List.of("Engineer, build the door")), "Only the newest press is prefilled: " + c.output.prefills);
    }

    private record Rig(VoiceController controller, FakeOutput output, Held background, VoiceClientGameTests.FakeRecognizer recognizer) { }

    private static Rig fresh() {
        var output = new FakeOutput();
        var background = new Held();
        var source = new VoiceClientGameTests.FakeSource();
        var recognizer = new VoiceClientGameTests.FakeRecognizer();
        var controller = new VoiceController(() -> new VoiceController.Setup(source, recognizer, null), background.pending::add, Runnable::run, output, () -> { });
        return new Rig(controller, output, background, recognizer);
    }

    private static void speakAndRelease(Rig rig) {
        rig.controller.tick(false, false, true, true);
        rig.controller.tick(true, false, true, true);
        check(rig.controller.state() == VoiceController.State.LISTENING, "Pressing talk starts listening");
        rig.controller.tick(false, false, true, true);
        check(rig.controller.state() == VoiceController.State.TRANSCRIBING, "Releasing talk starts recognition");
    }

    private static void speakerFailureIsReported() throws Exception {
        // A speech program that cannot start is named, and the rest of the queue is dropped.
        var failures = new ArrayList<String>();
        var reported = new CountDownLatch(1);
        VoiceAdapters.Speaker missing = new VoiceAdapters.Speaker() {
            @Override public void speak(String text) throws Exception { throw new LocalProcess.Failure("could not start espeak-ng"); }
            @Override public void stop() { }
        };
        var playback = new VoicePlayback(() -> missing, () -> true, notice -> notice.deliver(line -> { synchronized (failures) { failures.add(line); } reported.countDown(); }));
        check(playback.offer("hello"), "The reply is queued");
        check(reported.await(5, TimeUnit.SECONDS), "A failing speech program is reported");
        waitIdle(playback);
        synchronized (failures) {
            check(failures.size() == 1 && failures.getFirst().contains("could not start espeak-ng") && failures.getFirst().contains("Replies still show in chat"),
                    "The failure names the program and says text still works: " + failures);
        }

        // One notice per failure episode: later replies are not queued, not retried and not re-reported.
        check(playback.failed(), "The failure starts an episode");
        for (int i = 0; i < 3; i++) check(!playback.offer("later " + i), "A later reply is not queued during the episode");
        playback.stop(); // Stopping alone (for example pressing talk) is not a retry.
        check(!playback.offer("after stop") && playback.failed(), "Stop alone does not end the episode");
        Thread.sleep(100);
        synchronized (failures) { check(failures.size() == 1, "Exactly one notice per episode: " + failures); }
        playback.retry();
        check(!playback.failed() && playback.offer("after retry"), "The explicit retry tries the program again");
        waitIdle(playback);
        synchronized (failures) { check(failures.size() == 2, "A failed retry is a new episode with one new notice: " + failures); }

        // Stopping speech on purpose is not a failure and reports nothing.
        var stoppedFailures = new ArrayList<String>();
        var speaking = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        VoiceAdapters.Speaker slow = new VoiceAdapters.Speaker() {
            @Override public void speak(String text) throws Exception {
                speaking.countDown();
                release.await(5, TimeUnit.SECONDS);
                throw new LocalProcess.Failure("was stopped");
            }
            @Override public void stop() { release.countDown(); }
        };
        var stoppable = new VoicePlayback(() -> slow, () -> true, notice -> notice.deliver(line -> { synchronized (stoppedFailures) { stoppedFailures.add(line); } }));
        stoppable.offer("a long answer");
        check(speaking.await(5, TimeUnit.SECONDS), "Speech started");
        stoppable.stop();
        waitIdle(stoppable);
        synchronized (stoppedFailures) { check(stoppedFailures.isEmpty(), "Stopping on purpose is not reported as a failure: " + stoppedFailures); }

        // Stop/disconnect can happen after the speaker failed but before its queued
        // client-thread notice runs. Recheck the generation at actual delivery.
        var queued = new ArrayList<VoicePlayback.FailureNotice>();
        var enqueued = new CountDownLatch(1);
        var delayed = new VoicePlayback(() -> missing, () -> true, notice -> { synchronized(queued){queued.add(notice);} enqueued.countDown(); });
        delayed.offer("reply before stop");
        check(enqueued.await(5,TimeUnit.SECONDS),"Failure reaches the pending client delivery queue");
        waitIdle(delayed);delayed.stop();
        var delivered=new ArrayList<String>();synchronized(queued){queued.getFirst().deliver(delivered::add);}
        check(delivered.isEmpty(),"A queued old failure cannot report after an intentional stop/disconnect");
    }

    private static void waitIdle(VoicePlayback playback) throws InterruptedException {
        for (int i = 0; i < 100 && playback.busy(); i++) Thread.sleep(20);
        check(!playback.busy(), "Playback becomes idle");
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
