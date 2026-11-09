package dev.minecraftai.engineer.client;

import java.util.ArrayList;
import java.util.List;

/** Test seam: records what this client would say aloud, through the real spoken-reply path. */
public final class SpokenReplyCapture {
    private static final List<String> HEARD = new ArrayList<>();
    private static final VoiceAdapters.Speaker SPEAKER = new VoiceAdapters.Speaker() {
        @Override public void speak(String text) { synchronized (HEARD) { HEARD.add(text); } }
        @Override public void stop() { }
    };
    private SpokenReplyCapture() { }
    public static void start() { VoiceClient.focus = () -> true; VoiceClient.speakers = () -> SPEAKER; }
    public static void clear() { synchronized (HEARD) { HEARD.clear(); } }
    public static List<String> heard() { synchronized (HEARD) { return List.copyOf(HEARD); } }
}
