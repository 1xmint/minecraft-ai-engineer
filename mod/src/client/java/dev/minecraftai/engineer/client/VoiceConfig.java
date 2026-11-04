package dev.minecraftai.engineer.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The player's own local voice setup, read only from their config folder. Server data can never
 * supply or change it. Commands are argument lists started without a shell; {wav} is the only
 * placeholder and speech text is always written to standard input, never into an argument.
 */
record VoiceConfig(boolean enabled, Command recognizer, Command speaker) {
    static final String FILE = "engineer-voice.json";
    static final int MAX_ARGS = 32, MAX_ARG_LENGTH = 1024;

    record Command(List<String> args, int timeoutSeconds) {
        Command { args = List.copyOf(args); }
    }

    /** Null when the file does not exist. Throws IllegalArgumentException with a plain reason when it is invalid. */
    static VoiceConfig load(Path file) {
        if (!Files.isRegularFile(file)) return null;
        JsonObject root;
        try {
            if (Files.size(file) > 64 * 1024) throw new IllegalArgumentException("the file is larger than 64 KB");
            root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        } catch (IllegalArgumentException invalid) {
            throw invalid;
        } catch (Exception unreadable) {
            throw new IllegalArgumentException("it is not a readable JSON object");
        }
        boolean enabled = root.has("enabled") && root.get("enabled").isJsonPrimitive() && root.get("enabled").getAsBoolean();
        Command recognizer = command(root, "recognizer", 60);
        Command speaker = command(root, "speaker", 120);
        if (recognizer != null && recognizer.args().stream().noneMatch(arg -> arg.contains("{wav}")))
            throw new IllegalArgumentException("the recognizer command must include {wav} where the recording goes");
        return new VoiceConfig(enabled, recognizer, speaker);
    }

    private static Command command(JsonObject root, String name, int defaultTimeout) {
        if (!root.has(name) || root.get(name).isJsonNull()) return null;
        if (!root.get(name).isJsonObject()) throw new IllegalArgumentException(name + " must be an object");
        var object = root.getAsJsonObject(name);
        if (!object.has("command") || !object.get("command").isJsonArray()) throw new IllegalArgumentException(name + ".command must be a list of strings");
        var args = new ArrayList<String>();
        for (JsonElement element : object.getAsJsonArray("command")) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) throw new IllegalArgumentException(name + ".command must be a list of strings");
            String arg = element.getAsString();
            if (arg.length() > MAX_ARG_LENGTH || arg.indexOf('\0') >= 0) throw new IllegalArgumentException(name + ".command has an argument that is too long or invalid");
            args.add(arg);
        }
        if (args.isEmpty() || args.size() > MAX_ARGS || args.getFirst().isBlank()) throw new IllegalArgumentException(name + ".command needs a program and at most " + MAX_ARGS + " arguments");
        int timeout = defaultTimeout;
        if (object.has("timeoutSeconds")) {
            try { timeout = object.get("timeoutSeconds").getAsInt(); }
            catch (RuntimeException notNumber) { throw new IllegalArgumentException(name + ".timeoutSeconds must be a whole number"); }
        }
        if (timeout < 1 || timeout > 300) throw new IllegalArgumentException(name + ".timeoutSeconds must be between 1 and 300");
        return new Command(args, timeout);
    }
}
