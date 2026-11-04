package dev.minecraftai.engineer.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.nio.file.Path;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Optional, fully local push-to-talk. Off until the player writes config/engineer-voice.json.
 * A transcript only prefills the chat box; the player still presses Enter, so every request goes
 * through the same server permissions, delegation and confirmations as typed chat.
 */
public final class VoiceClient implements ClientModInitializer {
    private static final String SETUP_HELP = " See docs/install.md. Typing still works.";
    private static final Executor RECOGNITION = Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("engineer-voice-recognizer").factory());

    // Test seams: CI swaps these for fakes; players always get the defaults.
    static volatile Supplier<VoiceController.Setup> setups = VoiceClient::configuredSetup;
    static volatile Supplier<VoiceAdapters.Speaker> speakers = VoiceClient::configuredSpeaker;
    static volatile BooleanSupplier focus = () -> Minecraft.getInstance().isWindowActive();

    private static KeyMapping talk, stop;
    private static VoiceController controller;
    private static VoicePlayback playback;

    @Override public void onInitializeClient() {
        var category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("engineer", "voice"));
        talk = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.engineer.voice_talk", InputConstants.Type.KEYBOARD, InputConstants.KEY_V, category));
        stop = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.engineer.voice_stop", InputConstants.Type.KEYBOARD, InputConstants.KEY_B, category));
        playback = new VoicePlayback(() -> speakers.get(), () -> controller == null || controller.state() == VoiceController.State.IDLE,
                failure -> Minecraft.getInstance().execute(() -> controller.report(failure)));
        controller = new VoiceController(() -> setups.get(), RECOGNITION, r -> Minecraft.getInstance().execute(r), new VoiceController.Output() {
            @Override public void prefill(String chatText) { Minecraft.getInstance().gui.setScreen(new ChatScreen(chatText, false)); }
            @Override public void notice(String text) {
                var player = Minecraft.getInstance().player;
                if (player != null) player.sendSystemMessage(Component.literal("Voice: " + text));
            }
            @Override public boolean ready() {
                var client = Minecraft.getInstance();
                return client.player != null && client.level != null && client.gui.screen() == null && focus.getAsBoolean();
            }
        }, playback::stop);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean inWorld = client.player != null && client.level != null;
            while (stop.consumeClick()) playback.stop();
            controller.tick(talk.isDown(), client.gui.screen() != null, focus.getAsBoolean(), inWorld);
        });
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> playback.offer(message.getString(), overlay));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            controller.cancel(null);
            playback.stop();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            controller.cancel(null);
            playback.stop();
        });
        HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath("engineer", "voice"), (graphics, delta) -> {
            String line = controller.status();
            if (line != null) graphics.text(Minecraft.getInstance().font, line, 8, 44, controller.state() == VoiceController.State.IDLE ? 0xffdddddd : 0xffffdd55, true);
        });
    }

    static Path configFile() { return FabricLoader.getInstance().getConfigDir().resolve(VoiceConfig.FILE); }

    /** Read on every press so edits apply without a restart. */
    static VoiceController.Setup configuredSetup() {
        VoiceConfig config;
        try { config = VoiceConfig.load(configFile()); }
        catch (IllegalArgumentException invalid) { return VoiceController.Setup.off("Voice is off because config/" + VoiceConfig.FILE + " is invalid: " + invalid.getMessage() + "." + SETUP_HELP); }
        if (config == null) return VoiceController.Setup.off("Voice is off. To turn it on, create config/" + VoiceConfig.FILE + "." + SETUP_HELP);
        if (!config.enabled()) return VoiceController.Setup.off("Voice is off in config/" + VoiceConfig.FILE + " (enabled is false). Typing still works.");
        if (config.recognizer() == null) return VoiceController.Setup.off("Voice has no speech recognizer set in config/" + VoiceConfig.FILE + "." + SETUP_HELP);
        return new VoiceController.Setup(new VoiceAdapters.Microphone(), new VoiceAdapters.ProcessRecognizer(config.recognizer()), null);
    }

    static VoiceAdapters.Speaker configuredSpeaker() {
        try {
            VoiceConfig config = VoiceConfig.load(configFile());
            return config != null && config.enabled() && config.speaker() != null ? new VoiceAdapters.ProcessSpeaker(config.speaker()) : null;
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    static KeyMapping talkKey() { return talk; }
    static KeyMapping stopKey() { return stop; }
    static VoiceController controller() { return controller; }
    static VoicePlayback playback() { return playback; }
}
