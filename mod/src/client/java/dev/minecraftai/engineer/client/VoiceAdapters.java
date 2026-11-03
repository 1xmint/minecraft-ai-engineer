package dev.minecraftai.engineer.client;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;

/** The three local voice parts and their real implementations. Tests replace them through VoiceClient. */
final class VoiceAdapters {
    private VoiceAdapters() { }

    static final AudioFormat FORMAT = new AudioFormat(16000f, 16, 1, true, false);
    static final int MAX_RECORDING_BYTES = 16000 * 2 * 30; // 30 seconds

    static final class Unavailable extends Exception {
        Unavailable(String message) { super(message); }
    }

    /** Records 16 kHz mono 16-bit PCM between start and stop. */
    interface AudioSource {
        void start() throws Unavailable;
        byte[] stop();
        void cancel();
    }

    /** Turns a recording into text. Must not keep the audio. */
    interface Recognizer {
        String transcribe(byte[] pcm) throws Exception;
        void cancel();
    }

    /** Speaks one utterance and returns when it ends; stop ends it at once. */
    interface Speaker {
        void speak(String text) throws Exception;
        void stop();
    }

    static boolean microphonePresent() {
        try { return AudioSystem.isLineSupported(new DataLine.Info(TargetDataLine.class, FORMAT)); }
        catch (RuntimeException unavailable) { return false; }
    }

    static final class Microphone implements AudioSource {
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private TargetDataLine line;
        private Thread reader;
        private volatile boolean running;

        @Override public void start() throws Unavailable {
            try {
                line = AudioSystem.getTargetDataLine(FORMAT);
                line.open(FORMAT);
                line.start();
            } catch (LineUnavailableException | IllegalArgumentException | SecurityException missing) {
                close();
                throw new Unavailable("No microphone is available.");
            }
            running = true;
            reader = Thread.ofPlatform().daemon().name("engineer-voice-microphone").start(this::read);
        }

        private void read() {
            byte[] chunk = new byte[3200];
            while (running) {
                int n = line.read(chunk, 0, chunk.length);
                if (n <= 0) { if (!line.isOpen()) break; continue; }
                synchronized (buffer) {
                    int room = MAX_RECORDING_BYTES - buffer.size();
                    buffer.write(chunk, 0, Math.min(room, n));
                    if (room <= n) running = false;
                }
            }
        }

        @Override public byte[] stop() {
            running = false;
            close();
            join();
            synchronized (buffer) { return buffer.toByteArray(); }
        }

        @Override public void cancel() {
            running = false;
            close();
            join();
            synchronized (buffer) { buffer.reset(); }
        }

        private void close() {
            if (line == null) return;
            try { line.stop(); line.close(); } catch (RuntimeException ignored) { }
        }

        private void join() {
            if (reader == null) return;
            try { reader.join(1000); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        }
    }

    /** Writes a private temporary WAV, runs the player's recognizer on it, and always deletes it. */
    static final class ProcessRecognizer implements Recognizer {
        private final VoiceConfig.Command command;
        private volatile LocalProcess running;
        private volatile boolean cancelled;

        ProcessRecognizer(VoiceConfig.Command command) { this.command = command; }

        @Override public String transcribe(byte[] pcm) throws Exception {
            Path dir = privateDirectory();
            Path wav = dir.resolve("speech.wav");
            try {
                try (var audio = new AudioInputStream(new ByteArrayInputStream(pcm), FORMAT, pcm.length / FORMAT.getFrameSize())) {
                    AudioSystem.write(audio, AudioFileFormat.Type.WAVE, wav.toFile());
                }
                if (cancelled) throw new LocalProcess.Failure("was stopped");
                running = LocalProcess.start(command.args(), wav.toAbsolutePath().toString());
                if (cancelled) running.kill();
                return running.finish(null, command.timeoutSeconds());
            } finally {
                running = null;
                Files.deleteIfExists(wav);
                Files.deleteIfExists(dir);
            }
        }

        @Override public void cancel() {
            cancelled = true;
            var process = running;
            if (process != null) process.kill();
        }

        private static Path privateDirectory() throws IOException {
            if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix"))
                return Files.createTempDirectory("engineer-voice-", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
            return Files.createTempDirectory("engineer-voice-"); // Windows: the user's own temp folder.
        }
    }

    /** Sends the text on standard input to the player's speech program. */
    static final class ProcessSpeaker implements Speaker {
        private final VoiceConfig.Command command;
        private volatile LocalProcess running;

        ProcessSpeaker(VoiceConfig.Command command) { this.command = command; }

        @Override public void speak(String text) throws Exception {
            var process = LocalProcess.start(command.args(), null);
            running = process;
            try { process.finish(text, command.timeoutSeconds()); }
            finally { running = null; }
        }

        @Override public void stop() {
            var process = running;
            if (process != null) process.kill();
        }
    }
}
