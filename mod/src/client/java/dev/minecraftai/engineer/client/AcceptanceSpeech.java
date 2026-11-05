package dev.minecraftai.engineer.client;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Acceptance only: stands in for the player's speech program on a CI client and records what the
 * voice client would have read aloud. Everything upstream of the speech program is real: the
 * server's private spoken-reply payload, the client receiver and the playback queue.
 */
public final class AcceptanceSpeech {
    private static final ConcurrentLinkedQueue<String> HEARD = new ConcurrentLinkedQueue<>(), LOG = new ConcurrentLinkedQueue<>();

    private AcceptanceSpeech() { }

    public static void install() {
        VoiceAdapters.Speaker recorder = new VoiceAdapters.Speaker() {
            @Override public void speak(String text) { HEARD.add(text); LOG.add(text); }
            @Override public void stop() { }
        };
        VoiceClient.speakers = () -> recorder;
    }

    /** Spoken since the last clear. */
    public static List<String> heard() { return List.copyOf(HEARD); }

    /** Everything spoken in this client run. */
    public static List<String> log() { return List.copyOf(LOG); }

    public static void clear() { HEARD.clear(); }

    /** The exact text playback speaks for a reply whose chat line is "Engineer: " + reply. */
    public static String spokenForm(String reply) {
        String text = VoiceController.clean(reply);
        return text.length() > VoicePlayback.MAX_SPOKEN ? text.substring(0, VoicePlayback.MAX_SPOKEN) : text;
    }
}
