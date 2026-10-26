package dev.minecraftai.engineer;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Immutable, permission-filtered presentation data. This is never execution authority. */
public record ProjectView(UUID recipient, String worldId, String dimension, String projectId,
                          String serverEpoch, long sequence, int blueprintRevision,
                          long authorityRevision, int stage, String text, List<Ghost> blocks) {
    public static final int MAX_BLOCKS = 4096;
    public static final int MAX_PACKET_BYTES = 900_000;
    public static final int MAX_TEXT_LENGTH = 1024;

    public record Ghost(BlockPos pos, BlockState state, boolean currentStage, boolean matches, boolean wrong) {
        public Ghost {
            Objects.requireNonNull(pos, "position");
            Objects.requireNonNull(state, "state");
            if (Math.abs((long) pos.getX()) > 29_999_984 || Math.abs((long) pos.getZ()) > 29_999_984
                    || pos.getY() < -2048 || pos.getY() > 2047) {
                throw new IllegalArgumentException("Preview position outside supported world bounds");
            }
            pos = pos.immutable();
        }
    }

    public ProjectView {
        Objects.requireNonNull(recipient, "recipient");
        for (String value : List.of(worldId, dimension, projectId, serverEpoch)) {
            if (!value.matches("[a-zA-Z0-9_.:-]{1,128}")) {
                throw new IllegalArgumentException("Invalid preview identity");
            }
        }
        if (sequence < 0 || blueprintRevision < 1 || authorityRevision < 0 || stage < 0 || stage > 63) {
            throw new IllegalArgumentException("Invalid preview revision or tutorial cursor");
        }
        if (text == null || text.length() > MAX_TEXT_LENGTH || text.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Invalid preview text");
        }
        blocks = List.copyOf(blocks);
        if (blocks.size() > MAX_BLOCKS) throw new IllegalArgumentException("Preview exceeds block limit");
        var positions = new HashSet<BlockPos>();
        int estimatedBytes = text.length() * 3 + 2048;
        for (Ghost ghost : blocks) {
            if (!positions.add(ghost.pos())) throw new IllegalArgumentException("Duplicate preview position");
            String state = Blueprint.describe(ghost.state());
            if (state.length() > 512) throw new IllegalArgumentException("Preview block state is too large");
            estimatedBytes += state.length() * 3 + 16;
            if (estimatedBytes > MAX_PACKET_BYTES) throw new IllegalArgumentException("Preview exceeds packet budget");
        }
    }
}
