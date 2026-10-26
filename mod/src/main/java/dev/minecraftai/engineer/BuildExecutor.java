package dev.minecraftai.engineer;

/** Replace with legal player/NPC placement for physical survival mode in phase 2. */
public interface BuildExecutor {
    void tick();
    void pause(String reason);
    void resume();
    String status();
}
