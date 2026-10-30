package dev.minecraftai.engineer;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;

/** Read-only, short-lived proposal derived from one exact server-world snapshot. */
final class Diagnosis {
    private static final long MAX_TTL_TICKS = 1200;
    private static final int MAX_FOOTPRINT_STATES = 65536;
    private static final Set<String> DYNAMIC = Set.of("powered", "lit", "power", "locked");

    record Target(BlockPos pos, BlockState before, BlockState after, String component, String explanation) {
        Target {
            pos = pos.immutable();
            Objects.requireNonNull(before);
            Objects.requireNonNull(after);
            Objects.requireNonNull(component);
            Objects.requireNonNull(explanation);
        }
    }

    record Proposal(UUID snapshotId, UUID actor, net.minecraft.server.network.ServerGamePacketListenerImpl connection,
                    UUID epoch, UUID projectId, int revision, long authorization, String component,
                    long assignmentGeneration, String componentBasis, int componentBasisVersion, long expiresAtTick,
                    Map<BlockPos, BlockState> observed, List<Target> targets, List<String> messages,
                    boolean repairable) {
        Proposal {
            Objects.requireNonNull(snapshotId);
            Objects.requireNonNull(actor);
            Objects.requireNonNull(connection);
            Objects.requireNonNull(epoch);
            Objects.requireNonNull(projectId);
            var exact = new LinkedHashMap<BlockPos, BlockState>();
            observed.forEach((pos, state) -> exact.put(pos.immutable(), Objects.requireNonNull(state)));
            observed = Collections.unmodifiableMap(exact);
            targets = List.copyOf(targets);
            messages = List.copyOf(messages);
        }

        void validate(EngineeringSession s) {
            Objects.requireNonNull(s);
            if (!s.server.isSameThread()) throw new IllegalStateException("Diagnosis validation belongs to the server thread");
            long now = s.server.getTickCount();
            if (!actor.equals(s.actor())) throw new IllegalStateException("Diagnosis belongs to another player; diagnose again");
            ServerPlayer player = s.server.getPlayerList().getPlayer(actor);
            if (player == null || player.connection != connection)
                throw new IllegalStateException("Diagnosis connection changed; diagnose again");
            if (!epoch.equals(s.manager.epoch) || !projectId.equals(s.projectId)
                    || revision != s.revision() || authorization != s.access.revision())
                throw new IllegalStateException("Project authority or design changed; diagnose again");
            if (now >= expiresAtTick || expiresAtTick > now + MAX_TTL_TICKS || !EngineerMod.current(s.manager))
                throw new IllegalStateException("Diagnosis expired; diagnose again");
            s.access.require(actor, ProjectAccess.Permission.BUILD);
            var assignment=s.assignments.task(s.blueprint,component);
            if(assignment.generation!=assignmentGeneration || !assignment.basis.equals(componentBasis)
                    || componentBasisVersion!=ComponentAssignments.BASIS_VERSION)
                throw new IllegalStateException("Component assignment changed; diagnose again");
            if (!s.dimension.equals(player.level().dimension().identifier().toString()))
                throw new IllegalStateException("Return to the project dimension and diagnose again");

            var level = s.level();
            for (var entry : observed.entrySet()) {
                BlockPos pos = entry.getKey();
                if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)
                        || pos.getY() < level.getMinY() || pos.getY() > level.getMaxY())
                    throw new IllegalStateException("Diagnosis footprint is no longer loaded or in bounds; diagnose again");
                if (level.getBlockEntity(pos) != null)
                    throw new IllegalStateException("A block entity entered the diagnosis footprint; inspect it manually");
                if (!level.getBlockState(pos).equals(entry.getValue()))
                    throw new IllegalStateException("World changed at " + pos.toShortString() + "; diagnose again");
            }
        }
    }

    private Diagnosis() { }

    static Proposal inspect(EngineeringSession s) {
        return inspect(s,s.activeComponent);
    }

    static Proposal inspect(EngineeringSession s,String componentId) {
        Objects.requireNonNull(s);
        Objects.requireNonNull(componentId);
        if (!s.server.isSameThread()) throw new IllegalStateException("World diagnosis belongs to the server thread");
        UUID actor = s.actor();
        s.access.require(actor, ProjectAccess.Permission.VIEW);
        if (s.blueprint == null) throw new IllegalStateException("Choose a canonical mechanism before diagnosis");
        ComponentTree.validate(s.blueprint);
        Blueprint selected=ComponentTree.component(s.blueprint,componentId);

        ServerPlayer player = s.server.getPlayerList().getPlayer(actor);
        if (player == null || !s.dimension.equals(player.level().dimension().identifier().toString()))
            throw new IllegalStateException("Diagnosis requires the connected player in the project dimension");

        if(ShutterDesign.describes(selected.source())) {
            var design=ShutterDesign.read(selected.source());
            if(!componentId.equals("root") || !design.binding().project().equals(s.projectId))throw new IllegalStateException("Mechanical diagnosis requires the accepted standalone component");
            for(var pos:design.writeCells())if(!s.contains(pos))throw new IllegalStateException("Mechanical component is outside the selected workspace at "+pos.toShortString());
            var report=ShutterDiagnosis.inspect(s.level(),design);var assignment=s.assignments.task(s.blueprint,componentId);
            return new Proposal(UUID.randomUUID(),actor,player.connection,s.manager.epoch,s.projectId,s.revision(),s.access.revision(),componentId,
                assignment.generation,assignment.basis,ComponentAssignments.BASIS_VERSION,s.server.getTickCount()+MAX_TTL_TICKS,report.observed(),List.of(),report.messages(),false);
        }
        MechanismCatalog.contract(selected);
        var level = s.level();
        var footprint = new LinkedHashSet<BlockPos>();
        Set<String> closure=new LinkedHashSet<>();
        collectClosure(s.blueprint,componentId,closure);
        var planned = new LinkedHashMap<BlockPos, Blueprint.PlannedBlock>();
        for(String id:closure)for(Blueprint.PlannedBlock block:ComponentTree.component(s.blueprint,id).blocks()) {
            BlockPos pos = block.pos().immutable();
            if (!s.contains(pos)) throw new IllegalStateException("Planned component is outside the delegated workspace at " + pos.toShortString());
            planned.put(pos, block);
        }
        Set<BlockPos> targetPositions=new LinkedHashSet<>();
        Set<BlockPos> targetMargin=new LinkedHashSet<>();
        for(Blueprint.PlannedBlock block:selected.blocks()) {
            targetPositions.add(block.pos());
            for(BlockPos neighbor:BlockPos.betweenClosed(block.pos().offset(-2,-2,-2),block.pos().offset(2,2,2))) {
                BlockPos immutable=neighbor.immutable();footprint.add(immutable);targetMargin.add(immutable);
            }
        }
        // Prerequisites contribute their complete read closure, including their own margins.
        for(String id:closure)if(!id.equals(componentId))for(Blueprint.PlannedBlock block:ComponentTree.component(s.blueprint,id).blocks())
            for(BlockPos neighbor:BlockPos.betweenClosed(block.pos().offset(-2,-2,-2),block.pos().offset(2,2,2)))footprint.add(neighbor.immutable());
        if(footprint.size()>MAX_FOOTPRINT_STATES)throw new IllegalStateException("Diagnosis footprint exceeds the 65536-state limit");

        var observed = new LinkedHashMap<BlockPos, BlockState>();
        var blockEntities = new HashSet<BlockPos>();
        for (BlockPos pos : footprint) {
            if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)
                    || pos.getY() < level.getMinY() || pos.getY() > level.getMaxY())
                throw new IllegalStateException("Diagnosis stopped: footprint is unloaded or outside world bounds at " + pos.toShortString());
            if (level.getBlockEntity(pos) != null) blockEntities.add(pos);
            observed.put(pos, level.getBlockState(pos));
        }

        var targets = new ArrayList<Target>();
        var messages = new ArrayList<String>();
        boolean repairable = true;
        for (Blueprint.PlannedBlock block : selected.blocks()) {
            BlockState actual = observed.get(block.pos());
            BlockState intended = block.state();
            if (blockEntities.contains(block.pos())) {
                repairable = false;
                messages.add("Component '" + block.component() + "' at " + block.pos().toShortString()
                        + " contains a protected block entity; its contents were not read and automatic repair is blocked.");
                continue;
            }
            if (Blueprint.matches(intended, actual)) continue;
            String component = block.component();
            if (actual.isAir()) {
                BlockState after = corrected(intended, actual);
                targets.add(new Target(block.pos(), actual, after, component,
                        missingExplanation(component, block.pos(), intended)));
            } else if (actual.is(intended.getBlock())) {
                try {
                    BlockState after = corrected(intended, actual);
                    targets.add(new Target(block.pos(), actual, after, component,
                            mismatchExplanation(component, block.pos(), intended, actual)));
                } catch (IllegalArgumentException unsupported) {
                    repairable = false;
                    messages.add("Component '" + component + "' at " + block.pos().toShortString()
                            + " needs manual correction: " + unsupported.getMessage());
                }
            } else {
                repairable = false;
                messages.add("Component '" + component + "' at " + block.pos().toShortString()
                        + " has " + Blueprint.describe(actual) + " instead of " + Blueprint.describe(intended)
                        + "; wrong block kinds are preserved for manual correction.");
            }
        }

        Set<BlockPos> plannedPositions = new LinkedHashSet<>(targetPositions);
        for (var entry : observed.entrySet()) {
            if (plannedPositions.contains(entry.getKey())) continue;
            if(blockEntities.contains(entry.getKey())) {
                repairable=false;
                messages.add("Protected block entity at "+entry.getKey().toShortString()+" entered the component read footprint; contents were not read and automatic repair is blocked.");
                continue;
            }
            Blueprint.PlannedBlock prerequisite=planned.get(entry.getKey());
            if(targetMargin.contains(entry.getKey()) && !entry.getValue().isAir()) {
                repairable=false;
                String description=Blueprint.describe(entry.getValue());
                messages.add("Occupied isolation margin at "+entry.getKey().toShortString()+" contains "+description+"; move/remove it yourself before automatic repair.");
                continue;
            }
            if(prerequisite!=null) {
                if(!Blueprint.matches(prerequisite.state(),entry.getValue())) {
                    repairable=false;messages.add("Required component '"+prerequisite.component()+"' at "+entry.getKey().toShortString()+" is not canonical; complete it before diagnosis repair.");
                }
                continue;
            }
            if(entry.getValue().isAir())continue;
            repairable = false;
            String description = blockEntities.contains(entry.getKey()) ? "a protected block entity (contents not read)"
                    : Blueprint.describe(entry.getValue());
            messages.add("Occupied isolation margin at " + entry.getKey().toShortString() + " contains "
                    + description + "; move/remove it yourself before automatic repair.");
        }
        if (targets.isEmpty() && messages.isEmpty()) {
            repairable = false;
            messages.add("Geometry matches the canonical mechanism; a functional test is still required.");
        } else if (targets.isEmpty()) {
            repairable = false;
        } else if (repairable)
            messages.add("Found " + targets.size() + " supported correction(s); review them and explicitly request repair.");
        else if (!targets.isEmpty())
            messages.add("Some differences are supported, but automatic repair is blocked until every unsupported condition is resolved.");

        var assignment=s.assignments.task(s.blueprint,componentId);
        return new Proposal(UUID.randomUUID(), actor, player.connection, s.manager.epoch, s.projectId, s.revision(),
                s.access.revision(),componentId,assignment.generation,assignment.basis,ComponentAssignments.BASIS_VERSION,
                s.server.getTickCount() + MAX_TTL_TICKS, observed, targets, messages, repairable);
    }

    private static void collectClosure(Blueprint project,String id,Set<String> result) {
        if(!result.add(id))return;
        var node=ComponentTree.nodes(project).stream().filter(candidate->candidate.id().equals(id)).findFirst()
                .orElseThrow(()->new IllegalArgumentException("Choose a physical component, not a container or unknown name"));
        for(String prerequisite:node.dependsOn())collectClosure(project,prerequisite,result);
    }

    static BlockState corrected(BlockState intended, BlockState observed) {
        Objects.requireNonNull(intended);
        Objects.requireNonNull(observed);
        if (observed.isAir()) return intended;
        if (!intended.is(observed.getBlock()))
            throw new IllegalArgumentException("only an empty position or the intended block kind can be corrected automatically");

        Set<String> allowed = allowedProperties(intended);
        boolean mismatch = false;
        for (Property<?> property : intended.getProperties()) {
            String name = property.getName();
            if (DYNAMIC.contains(name)) continue;
            if (Objects.equals(intended.getValue(property), observed.getValue(property))) continue;
            mismatch = true;
            if (!allowed.contains(name))
                throw new IllegalArgumentException("unsupported structural property '" + name + "'");
        }
        if (!mismatch) throw new IllegalArgumentException("no supported structural difference was found");

        BlockState corrected = intended;
        corrected = copyDynamic(corrected, observed, BlockStateProperties.POWERED);
        corrected = copyDynamic(corrected, observed, BlockStateProperties.LIT);
        corrected = copyDynamic(corrected, observed, BlockStateProperties.POWER);
        corrected = copyDynamic(corrected, observed, BlockStateProperties.LOCKED);
        return corrected;
    }

    private static Set<String> allowedProperties(BlockState intended) {
        if (intended.is(Blocks.REPEATER)) return Set.of("facing", "delay");
        if (intended.is(Blocks.LEVER)) return Set.of("face", "facing");
        if (intended.is(Blocks.REDSTONE_WALL_TORCH)) return Set.of("facing");
        return Set.of();
    }

    private static <T extends Comparable<T>> BlockState copyDynamic(BlockState target, BlockState source, Property<T> property) {
        return target.hasProperty(property) && source.hasProperty(property)
                ? target.setValue(property, source.getValue(property)) : target;
    }

    private static String missingExplanation(String component, BlockPos pos, BlockState intended) {
        String prefix = (intended.is(Blocks.STONE) ? "Missing support component '" : "Missing component '")
                + component + "' at " + pos.toShortString() + ".";
        if (intended.is(Blocks.REPEATER)) return prefix + " Place the repeater with input from "
                + inputSide(intended) + " and delay " + intended.getValue(BlockStateProperties.DELAY) + " redstone ticks.";
        if (intended.is(Blocks.LEVER)) return prefix + " Its intended face is "
                + intended.getValue(BlockStateProperties.ATTACH_FACE) + " with facing "
                + intended.getValue(BlockStateProperties.HORIZONTAL_FACING) + ".";
        if (intended.is(Blocks.REDSTONE_WALL_TORCH)) return prefix + " Attach it to the "
                + intended.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite() + " wall.";
        return prefix + " The planned block is " + Blueprint.describe(intended) + ".";
    }

    private static String mismatchExplanation(String component, BlockPos pos, BlockState intended, BlockState actual) {
        String prefix = "Component '" + component + "' at " + pos.toShortString() + ": ";
        if (intended.is(Blocks.REPEATER)) {
            return prefix + "repeater input side should be " + inputSide(intended) + " (observed " + inputSide(actual)
                    + "); required delay is " + intended.getValue(BlockStateProperties.DELAY) + " redstone ticks"
                    + " (observed " + actual.getValue(BlockStateProperties.DELAY) + ").";
        }
        if (intended.is(Blocks.LEVER)) {
            return prefix + "lever attachment should be " + intended.getValue(BlockStateProperties.ATTACH_FACE)
                    + " with facing " + intended.getValue(BlockStateProperties.HORIZONTAL_FACING)
                    + " (observed " + actual.getValue(BlockStateProperties.ATTACH_FACE) + " / "
                    + actual.getValue(BlockStateProperties.HORIZONTAL_FACING) + ").";
        }
        if (intended.is(Blocks.REDSTONE_WALL_TORCH)) {
            return prefix + "wall torch should attach to the "
                    + intended.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite() + " wall (observed attachment: "
                    + actual.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite() + " wall).";
        }
        return prefix + "expected " + Blueprint.describe(intended) + ", observed " + Blueprint.describe(actual) + ".";
    }

    private static Direction inputSide(BlockState repeater) {
        return repeater.getValue(BlockStateProperties.HORIZONTAL_FACING);
    }
}
