package dev.minecraftai.engineer;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Delivers one engineer reply to one player: the visible "Engineer: " chat line, plus a private
 * spoken-reply payload on that player's own connection when their client understands it.
 *
 * Voice clients speak only this payload, never chat text. Chat text can be imitated (a renamed pet's
 * death message, /tellraw, other mods); this payload can only come from server code that calls
 * {@link #tell}. It carries no project data beyond the reply text the same player already sees, and
 * it is never broadcast. Vanilla clients and clients without voice get only the chat line.
 */
public final class SpokenReplies {
    public static final int MAX_TEXT = 800;
    private static boolean initialized;

    public record SpokenReplyPayload(String text) implements CustomPacketPayload {
        public static final Type<SpokenReplyPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("engineer", "spoken_reply"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SpokenReplyPayload> CODEC = StreamCodec.of(
                (buffer, value) -> buffer.writeUtf(value.text(), MAX_TEXT),
                buffer -> {
                    String text = buffer.readUtf(MAX_TEXT);
                    if (buffer.isReadable()) throw new IllegalArgumentException("Trailing spoken reply data");
                    return new SpokenReplyPayload(text);
                });
        public SpokenReplyPayload {
            if (text == null || text.isEmpty() || text.length() > MAX_TEXT) throw new IllegalArgumentException("Invalid spoken reply");
        }
        @Override public Type<SpokenReplyPayload> type() { return TYPE; }
    }

    private SpokenReplies() { }

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        PayloadTypeRegistry.clientboundPlay().register(SpokenReplyPayload.TYPE, SpokenReplyPayload.CODEC);
    }

    /**
     * Shows the reply to this player and, for a voice-capable client, marks it as speakable. Callers
     * keep their own currency checks (revocation, project switch, disconnect) before calling.
     */
    public static void tell(ServerPlayer player, String text) {
        if (player == null || text == null) return;
        player.sendSystemMessage(Component.literal("Engineer: " + text)); // Chat text is unchanged.
        speak(player, text.substring(0, Math.min(MAX_TEXT, text.length())));
    }

    private static void speak(ServerPlayer player, String reply) {
        var server = player.level().getServer();
        if (reply.isBlank() || server == null || !server.isSameThread() || player.hasDisconnected()) return;
        if (server.getPlayerList().getPlayer(player.getUUID()) != player) return; // A stale handle never speaks.
        if (ServerPlayNetworking.canSend(player, SpokenReplyPayload.TYPE)) ServerPlayNetworking.send(player, new SpokenReplyPayload(reply));
    }
}
