package dev.minecraftai.engineer;

import com.google.gson.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Files;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/** Mod initiates bounded authenticated requests; companion never receives a world-edit endpoint. */
final class Companion {
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    static CompletableFuture<JsonObject> post(String route, JsonObject body) {
        return request(route, body);
    }
    static CompletableFuture<JsonObject> request(String route, JsonObject body) {
        try {
            JsonObject config = JsonParser.parseString(Files.readString(LocalFiles.configDir().resolve("companion.json"))).getAsJsonObject();
            String token = config.get("token").getAsString();
            int port = config.has("port") ? config.get("port").getAsInt() : 43127;
            if (token.length() < 32 || port < 1024 || port > 65535) throw new IllegalStateException("Invalid companion configuration");
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + route))
                    .timeout(Duration.ofSeconds(90)).header("Authorization", "Bearer " + token).header("Content-Type", "application/json");
            if (body == null) builder.GET(); else builder.POST(HttpRequest.BodyPublishers.ofString(LocalFiles.GSON.toJson(body)));
            return CLIENT.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
                if (response.body().length() > 2_000_000) throw new IllegalStateException("Companion response too large");
                JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject();
                if (response.statusCode() != 200) throw new IllegalStateException(result.has("error") ? result.get("error").getAsString() : "Companion request failed");
                return result;
            });
        } catch (Exception e) { return CompletableFuture.failedFuture(new IllegalStateException("Start the companion service and check its local configuration", e)); }
    }
}
