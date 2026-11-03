package dev.minecraftai.engineer;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** CI-only durable facts for one inert survival placement. Never execution authority. */
final class PhysicalPlacementRecord {
    enum Status { BEFORE_MATCH, AFTER_MATCH, CONFLICT, UNLOADED, ACTOR_UNAVAILABLE, STALE }
    private static final Set<String> FIELDS=Set.of("schemaVersion","worldId","dimension","projectId","blueprintId","revision","component","controller","worker","operation","pos","before","after","item","countBefore","countAfter","phase");
    private final JsonObject source;
    private PhysicalPlacementRecord(JsonObject source){this.source=source.deepCopy();validate();}
    static PhysicalPlacementRecord prepare(EngineeringSession project,ServerPlayer worker,BlockPos target,Path file){
        project.validateMutation(ProjectAccess.Permission.BUILD,target);
        if(worker.level()!=project.level() || worker.isCreative() || !worker.getMainHandItem().is(Items.DIRT)
            || !project.level().getBlockState(target).isAir())throw new IllegalStateException("Physical placement pre-state differs");
        if(project.executionBlueprint().blocks().stream().noneMatch(block->block.pos().equals(target) && block.state().equals(Blocks.DIRT.defaultBlockState())))throw new IllegalStateException("Physical material differs from accepted intention");
        var data=new JsonObject();data.addProperty("schemaVersion",1);data.addProperty("worldId",project.worldId);data.addProperty("dimension",project.dimension);
        data.addProperty("projectId",project.projectId.toString());data.addProperty("blueprintId",project.blueprint.id());data.addProperty("revision",project.revision());data.addProperty("component",project.activeComponent);
        data.addProperty("controller",project.controller.toString());data.addProperty("worker",worker.getUUID().toString());data.addProperty("operation",UUID.randomUUID().toString());
        data.add("pos",Blueprint.jsonPos(target));data.addProperty("before","minecraft:air");data.addProperty("after","minecraft:dirt");data.addProperty("item","minecraft:dirt");
        data.addProperty("countBefore",worker.getMainHandItem().getCount());data.addProperty("countAfter",worker.getMainHandItem().getCount()-1);data.addProperty("phase","PREPARED");
        var record=new PhysicalPlacementRecord(data);
        if(Files.exists(file,LinkOption.NOFOLLOW_LINKS))throw new IllegalStateException("Physical record already exists");
        LocalFiles.write(file,record.source);return record;
    }
    static PhysicalPlacementRecord load(Path file){
        try(var stream=Files.newInputStream(file)){
            var bytes=stream.readNBytes(8193);if(bytes.length>8192)throw new IllegalArgumentException("Physical record exceeds limit");
            return new PhysicalPlacementRecord(JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject());
        }catch(java.io.IOException failed){throw new IllegalStateException("Cannot load physical record",failed);}
    }
    private void validate(){
        if(!source.keySet().equals(FIELDS) || number("schemaVersion")!=1 || number("revision")<1)throw new IllegalArgumentException("Invalid physical record schema");
        for(String field:List.of("projectId","blueprintId","controller","worker","operation"))if(!UUID.fromString(text(field)).toString().equals(text(field)))throw new IllegalArgumentException("Invalid physical identity");
        if(!text("worldId").matches("[a-zA-Z0-9_.:-]{1,256}") || !text("dimension").matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || !text("component").matches("[a-z][a-z0-9_-]{0,31}"))throw new IllegalArgumentException("Invalid physical scope");
        if(!text("before").equals("minecraft:air") || !text("after").equals("minecraft:dirt") || !text("item").equals("minecraft:dirt")
            || !Set.of("PREPARED","OBSERVED").contains(text("phase")) || number("countBefore")<1 || number("countBefore")>64 || number("countAfter")!=number("countBefore")-1)throw new IllegalArgumentException("Unsupported physical transition");
        var at=source.getAsJsonObject("pos");if(!at.keySet().equals(Set.of("x","y","z")))throw new IllegalArgumentException("Invalid physical position");
        for(String axis:List.of("x","y","z"))integer(at.get(axis));
    }
    Status status(EngineeringSession project,ServerPlayer worker){
        if(!project.worldId.equals(text("worldId")) || !project.dimension.equals(text("dimension")) || !project.projectId.toString().equals(text("projectId"))
            || project.blueprint==null || !project.blueprint.id().equals(text("blueprintId")) || project.revision()!=number("revision") || !project.activeComponent.equals(text("component"))
            || !project.controller.toString().equals(text("controller")))return Status.STALE;
        if(worker==null || !worker.getUUID().toString().equals(text("worker")) || worker.hasDisconnected() || worker.level()!=project.level())return Status.ACTOR_UNAVAILABLE;
        var target=Blueprint.pos(source.getAsJsonObject("pos"));if(!project.level().hasChunkAt(target))return Status.UNLOADED;
        var block=project.level().getBlockState(target);var stack=worker.getMainHandItem();
        int count=stack.isEmpty()?0:stack.is(Items.DIRT)?stack.getCount():-1;
        if(text("phase").equals("PREPARED") && block.isAir() && count==number("countBefore"))return Status.BEFORE_MATCH;
        // Matching facts cannot establish causation or authorize retry/undo.
        if(block.is(Blocks.DIRT) && count==number("countAfter"))return Status.AFTER_MATCH;
        return Status.CONFLICT;
    }
    PhysicalPlacementRecord observed(EngineeringSession project,ServerPlayer worker,Path file){
        if(status(project,worker)!=Status.AFTER_MATCH)throw new IllegalStateException("Physical placement has no matching block/item observation");
        if(!load(file).source.equals(source))throw new IllegalStateException("Physical record changed before observation");
        var updated=source.deepCopy();updated.addProperty("phase","OBSERVED");var record=new PhysicalPlacementRecord(updated);LocalFiles.write(file,record.source);return record;
    }
    private String text(String field){var value=source.get(field);if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Expected physical text");return value.getAsString();}
    private int number(String field){return integer(source.get(field));}
    private static int integer(JsonElement value){if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Expected physical integer");try{return value.getAsBigDecimal().intValueExact();}catch(ArithmeticException failed){throw new IllegalArgumentException("Physical integer outside bounds",failed);}}
}
