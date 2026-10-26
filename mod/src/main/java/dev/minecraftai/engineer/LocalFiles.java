package dev.minecraftai.engineer;

import com.google.gson.*;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
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
            String path = server.getWorldPath(LevelResource.ROOT).toRealPath().toString();
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(path.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException("Cannot identify this world", e); }
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
