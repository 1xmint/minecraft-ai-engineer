package dev.minecraftai.engineer;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Separately confirmed generated-design flow. Pending consent is deliberately ephemeral. */
final class GeneratedPlanning {
    private static final long TTL_MILLIS = 300_000L;

    private record Basis(EngineeringSession session, UUID actor, ServerPlayer player,
                         net.minecraft.server.network.ServerGamePacketListenerImpl connection,
                         String connectionId, UUID epoch, int revision, long authorization,
                         UUID selectedProject, BlockPos corner1, BlockPos corner2,
                         BlockPos origin, int rotation, JsonObject maxSize,
                         String request, String priorState, BehaviorSpec behavior,
                         String explanation, UUID nonce, long expiresAtMillis,
                         long expiresAtNanos) { }
    private record Flight(Basis basis, CompletableFuture<JsonObject> future) { }

    private final ProjectManager manager;
    private final Map<EngineeringSession, Basis> pending = new java.util.IdentityHashMap<>();
    private final Map<EngineeringSession, Flight> flights = new java.util.IdentityHashMap<>();
    private boolean closed;

    GeneratedPlanning(ProjectManager manager) { this.manager = manager; }

    void design(EngineeringSession session, String request) {
        requireServerThread();
        ensureOpen();
        UUID actor = session.actor();
        ServerPlayer player = requireAuthority(session, actor, true);
        if (pending.containsKey(session) || flights.containsKey(session))
            throw new IllegalStateException("A generated design request is already active");
        requireSafeDesignState(session);
        if (session.hasBuiltStructure())
            throw new IllegalStateException("Undo or reconcile built structures before requesting a generated design");

        BlockPos corner1 = session.corner1.immutable(), corner2 = session.corner2.immutable();
        BlockPos origin = session.planningAnchor().immutable();
        int rotation = session.blueprint == null ? 0 : session.blueprint.rotation();
        JsonObject maxSize = size(corner1, corner2);
        long nowMillis = System.currentTimeMillis();
        long expiresAtMillis = Math.addExact(nowMillis, TTL_MILLIS);
        long expiresAtNanos = System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(TTL_MILLIS);
        Basis captured = new Basis(session, actor, player, player.connection, manager.connectionId(player),
                manager.epoch, session.revision(), session.access.revision(), manager.selections.get(actor),
                corner1, corner2, origin, rotation, maxSize, request, session.state, null, null,
                UUID.randomUUID(), expiresAtMillis, expiresAtNanos);

        JsonObject body = requestBody(captured, false, null);
        session.state = "PLANNING";
        session.detail = "Requesting an independently confirmed behavior proposal";
        try { session.save(); }
        catch (RuntimeException failure) {
            session.state = captured.priorState();
            session.detail = "Behavior proposal unavailable; existing design kept";
            throw failure;
        }
        tell(player, "Requesting a behavior proposal. No geometry or world changes are authorized yet.");
        CompletableFuture<JsonObject> future;
        try {
            future = Companion.gateway(session, "/v2/behavior/propose", body, actor);
        } catch (RuntimeException failure) {
            restoreState(session, captured.priorState(), "Behavior proposal failed; existing design kept");
            throw failure;
        }
        Flight flight = new Flight(captured, future);
        flights.put(session, flight);
        future.whenComplete((result, error) -> session.server.execute(() -> finishProposal(flight, result, error)));
    }

    void confirm(EngineeringSession session, String nonce) {
        requireServerThread();
        ensureOpen();
        UUID actor = session.actor();
        Basis basis = pending.get(session);
        if (basis == null) throw new IllegalStateException("No current behavior proposal");
        if (nonce == null || !basis.nonce().toString().equalsIgnoreCase(nonce.trim()))
            throw new IllegalStateException("No current behavior proposal");
        // Consume before any network dispatch: even duplicate commands cannot make a second call.
        pending.remove(session);
        ServerPlayer player;
        try {
            player = requireAuthority(session, actor, true);
            requireCurrentBasis(basis, true);
            if (expired(basis)) throw new IllegalStateException("Behavior proposal expired; request a new one");
            requireSafeDesignState(session);
            if (session.hasBuiltStructure()) throw new IllegalStateException("Built structures changed; request a new design after reconciling them");
        } catch (RuntimeException invalid) {
            tell(basis.player(), "Generated behavior proposal cancelled: " + safe(invalid));
            throw invalid;
        }

        JsonObject behavior = basis.behavior().json();
        JsonObject body = requestBody(basis, true, behavior);
        JsonObject acceptance = new JsonObject();
        acceptance.addProperty("schemaVersion", 1);
        acceptance.addProperty("proposalId", basis.nonce().toString());
        acceptance.addProperty("behaviorDigest", basis.behavior().digest());
        JsonObject acceptedBasis = new JsonObject();
        acceptedBasis.addProperty("serverId", manager.serverId);
        acceptedBasis.addProperty("serverEpoch", basis.epoch().toString());
        acceptedBasis.addProperty("actorId", basis.actor().toString());
        acceptedBasis.addProperty("projectId", session.projectId.toString());
        acceptedBasis.addProperty("projectRevision", basis.revision());
        acceptedBasis.addProperty("authorizationRevision", basis.authorization());
        acceptedBasis.addProperty("connectionId", basis.connectionId());
        acceptance.add("basis", acceptedBasis);
        acceptance.addProperty("worldId", session.worldId);
        acceptance.addProperty("dimension", session.dimension);
        acceptance.add("corner1", Blueprint.jsonPos(basis.corner1()));
        acceptance.add("corner2", Blueprint.jsonPos(basis.corner2()));
        acceptance.addProperty("rotation", basis.rotation());
        acceptance.addProperty("expiresAt", basis.expiresAtMillis());
        body.add("acceptance", acceptance);

        session.state = "PLANNING";
        session.detail = "One explicitly confirmed generated geometry request";
        try { session.save(); }
        catch (RuntimeException failure) {
            session.state = basis.priorState();
            session.detail = "Generated request was not dispatched; checkpoint could not be saved";
            throw failure;
        }
        CompletableFuture<JsonObject> future;
        try {
            future = Companion.gateway(session, "/v2/design/generated", body, basis.actor());
        } catch (RuntimeException failure) {
            restoreState(session, basis.priorState(), "Generated design failed; existing design kept");
            tell(player, "Generated design failed; existing design kept: " + safe(failure));
            throw failure;
        }
        Flight flight = new Flight(basis, future);
        flights.put(session, flight);
        future.whenComplete((result, error) -> session.server.execute(() -> finishGeometry(flight, result, error)));
    }

    void cancelActor(EngineeringSession session, UUID actor) {
        requireServerThread();
        var proposal=pending.get(session);var flight=flights.get(session);
        if((proposal!=null && proposal.actor().equals(actor)) || (flight!=null && flight.basis().actor().equals(actor)))cancelCaptured(session);
    }

    void cancel(EngineeringSession session) {
        requireServerThread();
        cancelCaptured(session);
    }

    private void cancelCaptured(EngineeringSession session) {
        Basis basis = pending.remove(session);
        Flight flight = flights.remove(session);
        if (flight != null) {
            basis = flight.basis();
            flight.future().cancel(true);
            if (session.state.equals("PLANNING")) restoreState(session, basis.priorState(), "Generated planning cancelled");
        }
        if (basis != null) tell(basis.player(), "Generated planning cancelled. Existing design kept.");
    }

    void tick() {
        requireServerThread();
        if (closed) return;
        for (Basis basis : new ArrayList<>(pending.values())) {
            try {
                if (expired(basis)) throw new IllegalStateException("Behavior proposal expired");
                requireCurrentBasis(basis, false);
            } catch (RuntimeException invalid) {
                cancelBasis(basis, "Generated behavior proposal cancelled: " + safe(invalid));
            }
        }
        for (Flight flight : new ArrayList<>(flights.values())) {
            Basis basis = flight.basis();
            try {
                if (expired(basis)) throw new IllegalStateException("Generated planning request expired");
                if (!basis.session().state.equals("PLANNING")) throw new IllegalStateException("Project state changed");
                requireCurrentBasis(basis, false);
            } catch (RuntimeException invalid) {
                if (flights.remove(basis.session(), flight)) {
                    flight.future().cancel(true);
                    if (basis.session().state.equals("PLANNING")) restoreState(basis.session(), basis.priorState(), "Generated planning cancelled");
                    tell(basis.player(), "Generated planning cancelled: " + safe(invalid));
                }
            }
        }
    }

    void close() {
        if (!manager.server.isSameThread()) throw new IllegalStateException("Generated planning belongs to the server thread");
        closed = true;
        for (EngineeringSession session : new ArrayList<>(pending.keySet())) cancelCaptured(session);
        for (EngineeringSession session : new ArrayList<>(flights.keySet())) cancelCaptured(session);
    }

    private void finishProposal(Flight flight, JsonObject result, Throwable error) {
        Basis basis = flight.basis();
        EngineeringSession session = basis.session();
        if (flights.get(session) != flight) return;
        flights.remove(session);
        try {
            if (error != null) throw new IllegalStateException(safe(error));
            requireCurrentBasis(basis, false);
            if (!session.state.equals("PLANNING")) throw new IllegalStateException("Planning state changed");
            JsonObject proposal = exact(result.getAsJsonObject("proposal"), java.util.Set.of("behavior", "explanation"), "behavior proposal");
            if (!proposal.get("explanation").isJsonPrimitive() || !proposal.get("explanation").getAsJsonPrimitive().isString())
                throw new IllegalArgumentException("The behavior explanation is invalid");
            String explanation = proposal.get("explanation").getAsString();
            if (explanation.isBlank() || explanation.length() > 4000) throw new IllegalArgumentException("The behavior explanation is invalid");
            if (!proposal.has("behavior") || proposal.get("behavior") == null)
                throw new IllegalArgumentException("Invalid behavior proposal");
            if (proposal.get("behavior").isJsonNull()) {
                restoreState(session, basis.priorState(), "No supported behavior was proposed; existing design kept");
                tell(basis.player(), "No supported behavior was proposed. " + explanation);
                return;
            }
            JsonObject behaviorJson = proposal.getAsJsonObject("behavior");
            BehaviorSpec behavior = BehaviorSpec.read(behaviorJson);
            restoreState(session, basis.priorState(), "Behavior proposed; geometry not requested");
            Basis accepted = new Basis(session, basis.actor(), basis.player(), basis.connection(), basis.connectionId(), basis.epoch(),
                    basis.revision(), basis.authorization(), basis.selectedProject(), basis.corner1(), basis.corner2(), basis.origin(),
                    basis.rotation(), basis.maxSize(), basis.request(), basis.priorState(), behavior, explanation,
                    basis.nonce(), basis.expiresAtMillis(), basis.expiresAtNanos());
            pending.put(session, accepted);
            tell(basis.player(), behavior.summary() + "\n" + explanation + "\nTo request one geometry proposal, run: /engineer confirm " + basis.nonce());
        } catch (RuntimeException rejected) {
            restoreState(session, basis.priorState(), "Behavior proposal unavailable; existing design kept");
            tell(basis.player(), "Behavior proposal unavailable; existing design kept: " + safe(rejected));
        }
    }

    private void finishGeometry(Flight flight, JsonObject result, Throwable error) {
        Basis basis = flight.basis();
        EngineeringSession session = basis.session();
        if (flights.get(session) != flight) return;
        flights.remove(session);
        boolean acceptedToCanonical = false;
        try {
            if (error != null) throw new IllegalStateException(safe(error));
            requireCurrentBasis(basis, false);
            if (!session.state.equals("PLANNING")) throw new IllegalStateException("Planning state changed");
            session.state = basis.priorState();
            if (expired(basis)) throw new IllegalStateException("Generated design request expired");
            requireSafeDesignState(session);
            if (session.hasBuiltStructure()) throw new IllegalStateException("Built structures changed while the model was working");
            if (result == null || !result.has("blueprint") || !result.get("blueprint").isJsonObject())
                throw new IllegalArgumentException("The companion returned no generated blueprint");
            Blueprint candidate = Blueprint.parse(result.getAsJsonObject("blueprint"), session.worldId, session.dimension);
            GeneratedBlueprint.validate(candidate);
            BehaviorSpec accepted = GeneratedBlueprint.behavior(candidate);
            if (!GeneratedBlueprint.isGenerated(candidate) || !accepted.digest().equals(basis.behavior().digest())
                    || candidate.rotation() != basis.rotation()
                    || candidate.blocks().stream().anyMatch(block -> !session.contains(block.pos())))
                throw new IllegalArgumentException("Generated geometry does not match the accepted behavior and workspace");
            if (candidate.revision() != Math.addExact(basis.revision(), 1)
                    || !candidate.projectId().equals(session.projectId.toString()))
                throw new IllegalArgumentException("Generated blueprint belongs to a stale project revision");
            session.state = "READY";
            session.accept(candidate);
            acceptedToCanonical = true;
            session.checkpoint();
            tell(basis.player(), "Generated preview accepted. " + accepted.summary() + " Geometry is untested. Review the preview, then explicitly run /engineer build to construct it.");
        } catch (RuntimeException rejected) {
            if (acceptedToCanonical) {
                tell(basis.player(), "Generated preview accepted. " + basis.behavior().summary() + " Geometry is untested; checkpoint reporting needs attention.");
                return;
            }
            session.state = basis.priorState();
            session.detail = "Generated design rejected; existing blueprint and revision kept";
            session.save();
            tell(basis.player(), "Generated design rejected; existing blueprint and revision kept: " + safe(rejected));
        }
    }

    private void cancelBasis(Basis basis, String reason) {
        EngineeringSession session = basis.session();
        if (pending.remove(session, basis)) tell(basis.player(), reason + ". Existing design kept.");
    }

    private void requireCurrentBasis(Basis basis, boolean requireState) {
        EngineeringSession session = basis.session();
        if (expired(basis)) throw new IllegalStateException("Generated planning request expired");
        if (manager != session.manager || !EngineerMod.current(manager) || manager.epoch != basis.epoch()
                || session.revision() != basis.revision() || session.access.revision() != basis.authorization()
                || !session.owner.equals(basis.actor()) || !session.access.allows(basis.actor(), ProjectAccess.Permission.PLAN)
                || !session.access.allows(basis.actor(), ProjectAccess.Permission.EDIT)
                || !basis.session().worldId.equals(manager.worldId)
                || !java.util.Objects.equals(manager.selections.get(basis.actor()), basis.selectedProject())
                || !basis.corner1().equals(session.corner1) || !basis.corner2().equals(session.corner2)
                || !basis.origin().equals(session.planningAnchor())
                || basis.rotation() != (session.blueprint == null ? 0 : session.blueprint.rotation()))
            throw new IllegalStateException("Actor, selection, workspace or project context changed");
        ServerPlayer current = manager.server.getPlayerList().getPlayer(basis.actor());
        if (current == null || current != basis.player() || current.connection != basis.connection() || current.hasDisconnected()
                || !manager.connectionId(current).equals(basis.connectionId())
                || !session.dimension.equals(current.level().dimension().identifier().toString())
                || !manager.modelOperator(current))
            throw new IllegalStateException("The configured model operator connection or authority changed");
        session.access.require(basis.actor(), ProjectAccess.Permission.PLAN);
        session.access.require(basis.actor(), ProjectAccess.Permission.EDIT);
        if (requireState && session.state.equals("PLANNING"))
            throw new IllegalStateException("Project state changed while generated planning was active");
    }

    private ServerPlayer requireAuthority(EngineeringSession session, UUID actor, boolean selected) {
        if (session.manager != manager || manager.projects.get(session.projectId) != session)
            throw new IllegalStateException("Project is unavailable");
        if (!session.owner.equals(actor)) throw new IllegalStateException("Generated planning is limited to the project owner");
        session.access.require(actor, ProjectAccess.Permission.PLAN);
        session.access.require(actor, ProjectAccess.Permission.EDIT);
        ServerPlayer player = manager.server.getPlayerList().getPlayer(actor);
        if (player == null || player.hasDisconnected() || !manager.modelOperator(player)
                || !session.dimension.equals(player.level().dimension().identifier().toString()))
            throw new IllegalStateException("Generated planning requires the configured model operator in the project dimension");
        if (selected && !session.projectId.equals(manager.selections.get(actor)))
            throw new IllegalStateException("Select this project before generated planning");
        if (session.corner1 == null || session.corner2 == null)
            throw new IllegalStateException("Set both workspace corners before requesting a generated design");
        manager.workspace(session, actor, session.corner1, session.corner2);
        return player;
    }

    private static void requireSafeDesignState(EngineeringSession session) {
        if (session.state.equals("EXECUTING") || session.state.equals("PLANNING") || session.pendingPlacement
                || session.undoRequested || session.testTick >= 0 || session.hasPendingTest()
                || !session.journal.isEmpty() || !session.baseline.isEmpty())
            throw new IllegalStateException("Stop and reconcile active work, undo, tests or built structures before generated planning");
    }

    private JsonObject requestBody(Basis basis, boolean geometry, JsonObject behavior) {
        JsonObject body = new JsonObject();
        body.addProperty("request", basis.request());
        body.addProperty("worldId", basis.session().worldId);
        body.addProperty("dimension", basis.session().dimension);
        body.add("anchor", Blueprint.jsonPos(basis.origin()));
        body.addProperty("rotation", basis.rotation());
        body.add("maxSize", basis.maxSize().deepCopy());
        body.addProperty("projectId", basis.session().projectId.toString());
        body.addProperty("modelAuthorized", true);
        body.addProperty("isOperator", true);
        if (geometry) body.add("behavior", behavior.deepCopy());
        return body;
    }

    private static JsonObject size(BlockPos a, BlockPos b) {
        JsonObject out = new JsonObject();
        out.addProperty("x", Math.abs((long) a.getX() - b.getX()) + 1);
        out.addProperty("y", Math.abs((long) a.getY() - b.getY()) + 1);
        out.addProperty("z", Math.abs((long) a.getZ() - b.getZ()) + 1);
        return out;
    }

    private static JsonObject exact(JsonObject value, java.util.Set<String> keys, String label) {
        if (value == null || !value.keySet().equals(keys)) throw new IllegalArgumentException("Invalid " + label + " fields");
        return value;
    }

    private static boolean expired(Basis basis) {
        return System.currentTimeMillis() >= basis.expiresAtMillis() || System.nanoTime() >= basis.expiresAtNanos();
    }

    private void restoreState(EngineeringSession session, String state, String detail) {
        if (session.state.equals("PLANNING")) {
            session.state = state;
            session.detail = detail;
            session.save();
        }
    }

    private void requireServerThread() {
        if (!manager.server.isSameThread()) throw new IllegalStateException("Generated planning belongs to the server thread");
    }

    private void ensureOpen() { if (closed) throw new IllegalStateException("Generated planning is closed"); }

    private static void tell(ServerPlayer player, String message) {
        if (player != null && !player.hasDisconnected()) player.sendSystemMessage(Component.literal("Engineer: " + message));
    }

    private static String safe(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        String message = current.getMessage() == null ? "request failed" : current.getMessage();
        return message.substring(0, Math.min(350, message.length()));
    }
}
