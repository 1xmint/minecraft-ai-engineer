package dev.minecraftai.engineer.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * One run of a program the player configured. Started directly (never through a shell), with
 * stderr discarded, bounded stdout, a hard timeout and a kill that also ends child processes.
 */
final class LocalProcess {
    static final int MAX_OUTPUT = 16 * 1024;

    static final class Failure extends Exception {
        Failure(String message) { super(message); }
    }

    private final Process process;
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private final Thread reader;
    private volatile boolean killed;

    private LocalProcess(Process process) {
        this.process = process;
        this.reader = Thread.ofPlatform().daemon().name("engineer-voice-output").start(this::drain);
    }

    /** Replaces {wav} in each argument; nothing else is interpreted. */
    static LocalProcess start(List<String> command, String wav) throws Failure {
        var args = new ArrayList<String>(command.size());
        for (String arg : command) args.add(wav == null ? arg : arg.replace("{wav}", wav));
        try {
            return new LocalProcess(new ProcessBuilder(args).redirectError(ProcessBuilder.Redirect.DISCARD).start());
        } catch (IOException | SecurityException missing) {
            throw new Failure("could not start " + args.getFirst());
        }
    }

    private void drain() {
        byte[] chunk = new byte[4096];
        try (InputStream in = process.getInputStream()) {
            int n;
            while ((n = in.read(chunk)) >= 0) {
                synchronized (output) {
                    int room = MAX_OUTPUT - output.size();
                    if (room > 0) output.write(chunk, 0, Math.min(room, n));
                }
            }
        } catch (IOException closed) {
            // The process ended or was killed.
        }
    }

    /** Writes stdin (may be null), waits, and returns stdout. Non-zero exit, timeout and kill are failures. */
    String finish(String stdin, int timeoutSeconds) throws Failure {
        try (var in = process.getOutputStream()) {
            if (stdin != null) in.write(stdin.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // A program that does not read its input may close it early.
        }
        try {
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                kill();
                throw new Failure("took longer than " + timeoutSeconds + " seconds and was stopped");
            }
            reader.join(1000);
        } catch (InterruptedException interrupted) {
            kill();
            Thread.currentThread().interrupt();
            throw new Failure("was interrupted");
        }
        if (killed) throw new Failure("was stopped");
        if (process.exitValue() != 0) throw new Failure("exited with code " + process.exitValue());
        synchronized (output) { return output.toString(StandardCharsets.UTF_8); }
    }

    void kill() {
        killed = true;
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }

    boolean alive() { return process.isAlive(); }
}
