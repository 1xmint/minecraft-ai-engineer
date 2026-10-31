package dev.minecraftai.engineer;

import com.google.gson.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Files;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.*;

/** Mod initiates bounded authenticated requests; companion never receives a world-edit endpoint. */
final class Companion {
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    static CompletableFuture<JsonObject> post(String route, JsonObject body) {
        return request(route, body);
    }
    /** Capture server-owned policy on its thread; HTTP completions never inspect the live world. */
    static CompletableFuture<JsonObject> gateway(EngineeringSession session,String route,JsonObject operation,UUID actor) {
        if(!session.server.isSameThread())throw new IllegalStateException("Gateway identity must be captured on the server thread");
        var context=context(session,actor);
        var mirrorContext=context(session,session.owner);
        var mirror=new JsonObject();mirror.add("context",mirrorContext);mirror.addProperty("worldId",session.worldId);mirror.addProperty("dimension",session.dimension);
        mirror.addProperty("name",session.blueprint==null?"Engineering project":session.blueprint.name());
        if(session.blueprint!=null)mirror.add("blueprint",session.blueprint.source().deepCopy());
        var members=new JsonArray();
        session.access.members().forEach((id,grant)->{
            var member=new JsonObject();member.addProperty("actorId",id.toString());member.addProperty("role",grant.role().name().toLowerCase(Locale.ROOT));member.addProperty("canPlan",grant.permissions().contains(ProjectAccess.Permission.PLAN));members.add(member);
        });
        mirror.add("members",members);
        var open=new JsonObject();open.addProperty("serverId",session.manager.serverId);open.addProperty("serverEpoch",session.manager.epoch.toString());
        open.addProperty("serverGeneration",session.manager.serverGeneration);
        var body=operation.deepCopy();body.add("context",context);
        if("/v2/checkpoint".equals(route) || "/v2/observe".equals(route) || "/v2/inventory".equals(route)) {
            session.reportSequence=Math.incrementExact(session.reportSequence);
            if(session.reportSequence>9_007_199_254_740_991L)throw new IllegalStateException("Report sequence exhausted; restart the server before continuing");
            body.addProperty("reportSequence",session.reportSequence);
        }
        var result=new CompletableFuture<JsonObject>();
        List<CompletableFuture<?>> exchanges=Collections.synchronizedList(new ArrayList<>());
        java.util.function.Function<CompletableFuture<JsonObject>,CompletableFuture<JsonObject>> track=future->{
            exchanges.add(future);if(result.isCancelled())future.cancel(true);return future;
        };
        track.apply(request("/v2/server/open",open)).thenCompose(ready->{
            if(result.isCancelled())return CompletableFuture.failedFuture(new java.util.concurrent.CancellationException());
            if(!ready.has("protocol") || ready.get("protocol").getAsInt()!=2 || !open.get("serverId").equals(ready.get("serverId")) || !open.get("serverEpoch").equals(ready.get("serverEpoch")) || !open.get("serverGeneration").equals(ready.get("serverGeneration")))
                return CompletableFuture.failedFuture(new IllegalStateException("Companion registered a different server context"));
            return track.apply(request("/v2/project",mirror));
        }).thenCompose(synced->{
            if(!mirrorContext.equals(synced.get("context")))return CompletableFuture.failedFuture(new IllegalStateException("Companion accepted a different project context"));
            if(result.isCancelled())return CompletableFuture.failedFuture(new java.util.concurrent.CancellationException());
            return route==null?CompletableFuture.completedFuture(synced):track.apply(request(route,body));
        }).whenComplete((response,error)->{
            if(error!=null)result.completeExceptionally(error);
            else if(route!=null && !context.equals(response.get("context")))result.completeExceptionally(new IllegalStateException("Companion returned stale or mismatched authority"));
            else result.complete(response);
        });
        result.whenComplete((response,error)->{if(result.isCancelled())synchronized(exchanges){for(var exchange:exchanges)exchange.cancel(true);}});
        return result;
    }
    static JsonObject context(EngineeringSession session,UUID actor) {
        var out=new JsonObject();out.addProperty("serverId",session.manager.serverId);out.addProperty("serverEpoch",session.manager.epoch.toString());
        out.addProperty("actorId",actor.toString());out.addProperty("projectId",session.projectId.toString());out.addProperty("projectRevision",session.revision());
        out.addProperty("authorizationRevision",session.access.revision());out.addProperty("requestId",UUID.randomUUID().toString());
        var connected=session.server.getPlayerList().getPlayer(actor);
        if(connected!=null && !connected.hasDisconnected())out.addProperty("connectionId",session.manager.connectionId(connected));
        return out;
    }
    static void synchronize(EngineeringSession session) {
        report(session,gateway(session,null,new JsonObject(),session.owner));
    }
    static void report(EngineeringSession session,CompletableFuture<JsonObject> request) {
        int revision=session.revision();long authorization=session.access.revision();
        request.whenComplete((response,error)->session.server.execute(()->{
            if(!EngineerMod.current(session.manager) || session.revision()!=revision || session.access.revision()!=authorization)return;
            session.memoryStatus=error==null?"synchronized":"companion unavailable; local checkpoint retained";
            session.save();
        }));
    }
    // Interpretation has required nullable targets: absent and explicit null differ.
    // Keep other established wire codecs unchanged.
    static String encodeBody(String route,JsonObject body){return route.equals("/v2/interpret")?body.toString():LocalFiles.GSON.toJson(body);}
    static CompletableFuture<JsonObject> request(String route, JsonObject body) {
        try {
            JsonObject config = JsonParser.parseString(Files.readString(LocalFiles.configDir().resolve("companion.json"))).getAsJsonObject();
            String token = config.get("token").getAsString();
            int port = config.has("port") ? config.get("port").getAsInt() : 43127;
            if (token.length() < 32 || port < 1024 || port > 65535) throw new IllegalStateException("Invalid companion configuration");
            boolean manualAcceptance=(route.equals("/v2/design") || route.equals("/v2/behavior/propose") || route.equals("/v2/design/generated")) && "true".equals(System.getenv("CI"))
                    && "true".equals(System.getenv("GITHUB_ACTIONS")) && "true".equals(System.getenv("MCE_LIVE_ACCEPTANCE"));
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + route))
                    .timeout(Duration.ofSeconds(manualAcceptance?900:90)).header("Authorization", "Bearer " + token).header("Content-Type", "application/json");
            if (body == null) builder.GET(); else builder.POST(HttpRequest.BodyPublishers.ofString(encodeBody(route,body)));
            CompletableFuture<HttpResponse<String>> exchange=CLIENT.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString());
            CompletableFuture<JsonObject> parsed=exchange.thenApply(response -> {
                if (response.body().length() > 2_000_000) throw new IllegalStateException("Companion response too large");
                JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject();
                if (response.statusCode() != 200) throw new IllegalStateException(result.has("error") ? result.get("error").getAsString() : "Companion request failed");
                return result;
            });
            parsed.whenComplete((result,error)->{if(parsed.isCancelled())exchange.cancel(true);});
            return parsed;
        } catch (Exception e) { return CompletableFuture.failedFuture(new IllegalStateException("Start the companion service and check its local configuration", e)); }
    }
}
