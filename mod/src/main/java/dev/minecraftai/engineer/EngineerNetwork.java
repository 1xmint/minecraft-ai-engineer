package dev.minecraftai.engineer;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/** Optional presentation protocol. The only client input is its protocol version. */
public final class EngineerNetwork {
    public static final int PROTOCOL = 1;
    private static final Map<ServerGamePacketListenerImpl, Channel> CHANNELS = new ConcurrentHashMap<>();
    private static boolean initialized;

    private static final class Channel {
        long sequence;
    }

    public record HelloPayload(int protocol) implements CustomPacketPayload {
        public static final Type<HelloPayload> TYPE = CustomPacketPayload.createType("engineer:overlay_hello");
        public static final StreamCodec<RegistryFriendlyByteBuf, HelloPayload> CODEC = StreamCodec.of(
                (buffer, value) -> buffer.writeVarInt(value.protocol()),
                buffer -> {
                    int protocol = buffer.readVarInt();
                    if (buffer.isReadable()) throw new IllegalArgumentException("Trailing overlay hello data");
                    return new HelloPayload(protocol);
                });
        @Override public Type<HelloPayload> type() { return TYPE; }
    }

    /** The transport sequence also fences CLEAR against already queued snapshots. */
    public record SnapshotPayload(long transportSequence, ProjectView view) implements CustomPacketPayload {
        public static final Type<SnapshotPayload> TYPE = CustomPacketPayload.createType("engineer:overlay_snapshot");
        public static final StreamCodec<RegistryFriendlyByteBuf, SnapshotPayload> CODEC =
                StreamCodec.of(EngineerNetwork::encode, EngineerNetwork::decode);
        public SnapshotPayload {
            if (transportSequence < 1) throw new IllegalArgumentException("Invalid overlay sequence");
        }
        @Override public Type<SnapshotPayload> type() { return TYPE; }
    }

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        PayloadTypeRegistry.serverboundPlay().register(HelloPayload.TYPE, HelloPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SnapshotPayload.TYPE, SnapshotPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(HelloPayload.TYPE, (payload, context) -> {
            // The connection supplies identity. A payload can never choose an actor or role.
            if (payload.protocol() == PROTOCOL && ServerPlayNetworking.canSend(context.player(), SnapshotPayload.TYPE)) {
                CHANNELS.computeIfAbsent(context.player().connection, ignored -> new Channel());
            } else {
                CHANNELS.remove(context.player().connection);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> CHANNELS.remove(handler));
    }

    /** Caller must authorize access immediately before passing presentation data. */
    public static void send(ServerPlayer player, ProjectView view) {
        if (!player.level().getServer().isSameThread()) throw new IllegalStateException("Preview filtering must run on the server thread");
        Channel channel = CHANNELS.get(player.connection);
        if (channel == null || !ServerPlayNetworking.canSend(player, SnapshotPayload.TYPE)) return;
        if (view != null && (!view.recipient().equals(player.getUUID())
                || !view.dimension().equals(player.level().dimension().identifier().toString()))) {
            throw new IllegalArgumentException("Preview recipient does not match its server connection");
        }
        ServerPlayNetworking.send(player, new SnapshotPayload(++channel.sequence, view));
    }

    public static boolean negotiated(ServerPlayer player) {
        return CHANNELS.containsKey(player.connection) && ServerPlayNetworking.canSend(player, SnapshotPayload.TYPE);
    }

    public static void close() { CHANNELS.clear(); }

    private static void encode(RegistryFriendlyByteBuf buffer, SnapshotPayload payload) {
        int start = buffer.writerIndex();
        buffer.writeLong(payload.transportSequence());
        ProjectView view = payload.view();
        buffer.writeBoolean(view != null);
        if (view != null) {
            buffer.writeUUID(view.recipient());
            buffer.writeUtf(view.worldId(), 128);
            buffer.writeUtf(view.dimension(), 128);
            buffer.writeUtf(view.projectId(), 128);
            buffer.writeUtf(view.serverEpoch(), 128);
            buffer.writeLong(view.sequence());
            buffer.writeVarInt(view.blueprintRevision());
            buffer.writeLong(view.authorityRevision());
            buffer.writeVarInt(view.stage());
            buffer.writeUtf(view.text(), ProjectView.MAX_TEXT_LENGTH);
            buffer.writeVarInt(view.blocks().size());
            for (ProjectView.Ghost ghost : view.blocks()) {
                buffer.writeBlockPos(ghost.pos());
                buffer.writeUtf(Blueprint.describe(ghost.state()), 512);
                buffer.writeByte((ghost.currentStage() ? 1 : 0) | (ghost.matches() ? 2 : 0) | (ghost.wrong() ? 4 : 0));
            }
        }
        if (buffer.writerIndex() - start > ProjectView.MAX_PACKET_BYTES) {
            throw new IllegalArgumentException("Overlay payload exceeds its bound");
        }
    }

    private static SnapshotPayload decode(RegistryFriendlyByteBuf buffer) {
        if (buffer.readableBytes() > ProjectView.MAX_PACKET_BYTES) throw new IllegalArgumentException("Oversized overlay payload");
        long transport = buffer.readLong();
        ProjectView view = null;
        if (buffer.readBoolean()) {
            var recipient = buffer.readUUID();
            String world = buffer.readUtf(128), dimension = buffer.readUtf(128), project = buffer.readUtf(128), epoch = buffer.readUtf(128);
            long sequence = buffer.readLong();
            int revision = buffer.readVarInt();
            long authority = buffer.readLong();
            int stage = buffer.readVarInt();
            String text = buffer.readUtf(ProjectView.MAX_TEXT_LENGTH);
            int count = buffer.readVarInt();
            if (count < 0 || count > ProjectView.MAX_BLOCKS) throw new IllegalArgumentException("Invalid overlay block count");
            var blocks = new ArrayList<ProjectView.Ghost>(count);
            for (int index = 0; index < count; index++) {
                var position = buffer.readBlockPos();
                var state = Blueprint.state(buffer.readUtf(512));
                int flags = buffer.readUnsignedByte();
                if ((flags & ~7) != 0) throw new IllegalArgumentException("Invalid overlay flags");
                blocks.add(new ProjectView.Ghost(position, state, (flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0));
            }
            view = new ProjectView(recipient, world, dimension, project, epoch, sequence, revision, authority, stage, text, blocks);
        }
        if (buffer.isReadable()) throw new IllegalArgumentException("Trailing overlay data");
        return new SnapshotPayload(transport, view);
    }

    private EngineerNetwork() { }
}
