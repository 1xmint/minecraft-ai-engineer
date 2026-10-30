package dev.minecraftai.engineer.client;

import dev.minecraftai.engineer.EngineerNetwork;
import dev.minecraftai.engineer.ProjectView;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

/** Per-connection presentation cache. It cannot authorize commands or world edits. */
public final class ClientPreview {
    private static ClientPacketListener connection;
    private static ProjectView view;
    private static volatile ProjectView renderInput;
    private static String epoch;
    private static String dimension;
    private static long transportSequence;
    private static final Map<String, Revision> REVISIONS = new HashMap<>();
    private static boolean initialized;
    private record Revision(int blueprint, long authority, long sequence) { }

    public static boolean enabled() {
        return !Boolean.parseBoolean(System.getenv("MCE_DISABLE_OVERLAY"));
    }

    public static void initialize() {
        if (!enabled()) return;
        if (initialized) return;
        initialized = true;
        ClientPlayNetworking.registerGlobalReceiver(EngineerNetwork.SnapshotPayload.TYPE, (payload, context) -> {
            ClientPacketListener source = context.player().connection;
            context.client().execute(() -> accept(payload, source));
        });
        ClientPlayConnectionEvents.INIT.register((handler, client) -> reset(handler));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            reset(handler);
            if (ClientPlayNetworking.canSend(EngineerNetwork.HelloPayload.TYPE)) {
                ClientPlayNetworking.send(new EngineerNetwork.HelloPayload(EngineerNetwork.PROTOCOL));
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            if (connection == handler) reset(null);
        });
    }

    /** Public for the client integration verifier; normal input is the registered S2C receiver. */
    public static boolean accept(EngineerNetwork.SnapshotPayload payload, ClientPacketListener source) {
        Minecraft client = Minecraft.getInstance();
        if (source == null || source != connection || source != client.getConnection()
                || payload.transportSequence() <= transportSequence) return false;
        ProjectView proposed = payload.view();
        if (proposed == null) {
            transportSequence = payload.transportSequence();
            view = null;
            renderInput = null;
            return true;
        }
        if (client.player == null || client.level == null || !proposed.recipient().equals(client.player.getUUID())
                || !proposed.dimension().equals(client.level.dimension().identifier().toString())) return false;
        if (epoch != null && !epoch.equals(proposed.serverEpoch())) return false;
        String key = proposed.worldId() + "/" + proposed.dimension() + "/" + proposed.projectId();
        Revision previous = REVISIONS.get(key);
        if (previous != null && (proposed.blueprintRevision() < previous.blueprint()
                || proposed.authorityRevision() < previous.authority() || proposed.sequence() <= previous.sequence())) return false;
        if (REVISIONS.size() >= 256 && !REVISIONS.containsKey(key)) REVISIONS.clear();
        REVISIONS.put(key, new Revision(proposed.blueprintRevision(), proposed.authorityRevision(), proposed.sequence()));
        epoch = proposed.serverEpoch();
        dimension = proposed.dimension();
        transportSequence = payload.transportSequence();
        view = proposed;
        renderInput = proposed;
        return true;
    }

    public static ProjectView current() {
        if (!enabled()) return null;
        Minecraft client = Minecraft.getInstance();
        if (client.getConnection() != connection || client.player == null || client.level == null) return null;
        String actualDimension = client.level.dimension().identifier().toString();
        if (dimension != null && !dimension.equals(actualDimension)) {
            view = null;
            renderInput = null;
            dimension = actualDimension;
        }
        return view != null && view.recipient().equals(client.player.getUUID())
                && view.dimension().equals(actualDimension) ? view : null;
    }

    private static void reset(ClientPacketListener next) {
        renderInput = null;
        connection = next;
        view = null;
        epoch = null;
        dimension = null;
        transportSequence = 0;
        REVISIONS.clear();
    }

    private ClientPreview() { }
    /** One immutable publication for extraction; never reads mutable client/world fields. */
    public static ProjectView renderInput(){return renderInput;}
}
