package dev.minecraftai.engineer;

import com.google.gson.*;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

final class LocalFiles {
    static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    static Path configDir() {
        String override = System.getenv("MCE_CONFIG_DIR");
        if (override != null && !override.isBlank()) return Path.of(override);
        String appdata = System.getenv("APPDATA");
        if (System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win") && appdata != null)
            return Path.of(appdata, "minecraft-ai-engineer");
        String xdg = System.getenv("XDG_CONFIG_HOME");
        return xdg != null && !xdg.isBlank() ? Path.of(xdg, "minecraft-ai-engineer")
                : Path.of(System.getProperty("user.home"), ".config", "minecraft-ai-engineer");
    }
    static String worldId(MinecraftServer server) {
        try {
            Path root = server.getWorldPath(LevelResource.ROOT).toRealPath();
            Path identity = root.resolve("data").resolve("engineer-world.json");
            String world = id(identity);
            Path registryFile = configDir().resolve("servers").resolve(serverId()).resolve("world-registry.json");
            JsonObject registry = Files.exists(registryFile) ? JsonParser.parseString(Files.readString(registryFile)).getAsJsonObject() : new JsonObject();
            if (registry.has(world)) {
                Path previous = Path.of(registry.get(world).getAsString());
                if (!previous.equals(root) && Files.exists(previous))
                    throw new IllegalStateException("This world UUID is already used by another save. An operator must explicitly fork the copied world before using Engineer");
            }
            registry.addProperty(world, root.toString());
            write(registryFile, registry);
            return serverId() + "." + world;
        } catch (Exception e) { throw new IllegalStateException("Cannot identify this world", e); }
    }
    static String serverId() { return id(configDir().resolve("server-identity.json")); }
    static long nextServerGeneration() {
        Path directory=configDir().resolve("servers").resolve(serverId());
        try {
            Files.createDirectories(directory);
            try(FileChannel lockFile=FileChannel.open(directory.resolve("boot.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
                var lock=lockFile.lock()) {
                Path file=directory.resolve("boot-generation.json");
                long previous=Files.exists(file)?JsonParser.parseString(Files.readString(file)).getAsJsonObject().get("generation").getAsBigDecimal().longValueExact():0;
                if(previous<0 || previous>=9_007_199_254_740_991L)throw new IllegalStateException("Server boot generation needs administrator recovery");
                long next=Math.incrementExact(previous);var value=new JsonObject();value.addProperty("generation",next);write(file,value);return next;
            }
        }catch(Exception error){throw new IllegalStateException("Cannot preserve the server boot fence; companion requests were not authorized",error);}
    }
    private static String id(Path file) {
        try {
            if (Files.exists(file)) {
                JsonObject value = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                return UUID.fromString(value.get("id").getAsString()).toString();
            }
            JsonObject value = new JsonObject();
            value.addProperty("id", UUID.randomUUID().toString());
            write(file, value);
            return value.get("id").getAsString();
        } catch (IOException | IllegalArgumentException e) { throw new IllegalStateException("Identity file needs administrator recovery; no automatic reset was made", e); }
    }
    static void forkWorld(MinecraftServer server) {
        Path identity = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("engineer-world.json");
        try {
            if (Files.exists(identity)) Files.copy(identity, identity.resolveSibling("engineer-world-before-fork-" + System.currentTimeMillis() + ".json"));
            JsonObject value = new JsonObject(); value.addProperty("id", UUID.randomUUID().toString());
            write(identity, value);
        } catch (IOException e) { throw new IllegalStateException("Could not preserve the old identity; fork cancelled", e); }
    }
    static String legacyWorldId(MinecraftServer server) {
        try {
            String path = server.getWorldPath(LevelResource.ROOT).toRealPath().toString();
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(path.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException("Cannot locate the legacy world", e); }
    }
    static void write(Path file, JsonObject value) {
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, GSON.toJson(value), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) { channel.force(true); }
            try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException e) { throw new IllegalStateException("Checkpoint could not be saved; no further world edits allowed", e); }
    }
}
