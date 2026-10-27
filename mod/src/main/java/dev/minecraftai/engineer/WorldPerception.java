package dev.minecraftai.engineer;

import com.google.gson.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Explicit, bounded server observations. Never reads inventory contents, names, signs or NBT. */
final class WorldPerception {
    private WorldPerception() {}
    private static ServerPlayer requester(EngineeringSession s) {
        s.access.require(s.actor(),ProjectAccess.Permission.VIEW);
        ServerPlayer player=s.player();
        if(!s.server.isSameThread() || !EngineerMod.current(s.manager) || player==null
                || !s.dimension.equals(player.level().dimension().identifier().toString()))
            throw new IllegalStateException("Inspection needs the connected requester in the project dimension");
        if(s.corner1==null || s.corner2==null)throw new IllegalStateException("Select a workspace before inspecting");
        return player;
    }
    private static void readable(EngineeringSession s,BlockPos pos) {
        if(!s.level().hasChunkAt(pos))throw new IllegalStateException("Inspection stopped: unloaded chunk at "+pos.toShortString());
        if(!s.level().getWorldBorder().isWithinBounds(pos) || pos.getY()<s.level().getMinY() || pos.getY()>s.level().getMaxY())
            throw new IllegalStateException("Inspection stopped: outside world bounds");
        for(var other:s.manager.projects.values())if(other!=s && other.dimension.equals(s.dimension) && other.contains(pos)
                && !other.access.allows(s.actor(),ProjectAccess.Permission.VIEW))
            throw new IllegalStateException("Inspection stopped: neighboring project has not granted view permission");
    }
    static JsonObject block(EngineeringSession s,BlockPos target) {
        requester(s);return capture(s,target,null);
    }
    static JsonObject view(EngineeringSession s) {
        ServerPlayer player=requester(s);
        Vec3 eye=player.getEyePosition(),end=eye.add(player.getViewVector(1.0F).scale(6));
        BlockPos a=BlockPos.containing(eye),b=BlockPos.containing(end);
        // Preflight the complete bounded ray and shape-neighbor area before vanilla ray casting.
        BlockPos min=new BlockPos(Math.min(a.getX(),b.getX())-2,Math.min(a.getY(),b.getY())-2,Math.min(a.getZ(),b.getZ())-2);
        BlockPos max=new BlockPos(Math.max(a.getX(),b.getX())+2,Math.max(a.getY(),b.getY())+2,Math.max(a.getZ(),b.getZ())+2);
        for(BlockPos pos:BlockPos.betweenClosed(min,max))readable(s,pos);
        HitResult hit=player.pick(6,1.0F,false);
        if(!(hit instanceof BlockHitResult block) || hit.getType()!=HitResult.Type.BLOCK)
            throw new IllegalStateException("Look at a block in the selected workspace within six blocks");
        return capture(s,block.getBlockPos(),block.getDirection());
    }
    private static JsonObject vector(Vec3 v) {
        JsonObject out=new JsonObject();out.addProperty("x",v.x);out.addProperty("y",v.y);out.addProperty("z",v.z);return out;
    }
    private static JsonObject capture(EngineeringSession s,BlockPos target,Direction face) {
        ServerPlayer player=requester(s);
        if(!s.contains(target))throw new IllegalStateException("Inspection target is outside the selected workspace");
        Set<BlockPos> positions=new LinkedHashSet<>();positions.add(target.immutable());
        for(Direction direction:Direction.values())positions.add(target.relative(direction));
        for(BlockPos pos:positions)readable(s,pos);
        // All permission/loading checks precede any state sample; a failure emits no partial snapshot.
        JsonObject out=new JsonObject();out.addProperty("worldId",s.worldId);out.addProperty("dimension",s.dimension);out.addProperty("tick",s.level().getGameTime());
        JsonObject region=new JsonObject();region.add("min",Blueprint.jsonPos(target.offset(-1,-1,-1)));region.add("max",Blueprint.jsonPos(target.offset(1,1,1)));out.add("region",region);
        Map<BlockPos,Blueprint.PlannedBlock> intended=new HashMap<>();if(s.blueprint!=null)for(var block:s.blueprint.blocks())intended.put(block.pos(),block);
        JsonArray blocks=new JsonArray();int missing=0,changed=0,unplanned=0;
        for(BlockPos pos:positions) {
            var actual=s.level().getBlockState(pos);var expected=intended.get(pos);
            JsonObject block=new JsonObject();block.add("pos",Blueprint.jsonPos(pos));block.addProperty("state",Blueprint.describe(actual));blocks.add(block);
            if(expected==null){if(!actual.isAir())unplanned++;}
            else if(actual.isAir())missing++;
            else if(!Blueprint.matches(expected.state(),actual))changed++;
        }
        out.add("blocks",blocks);
        JsonObject facts=new JsonObject();facts.addProperty("schemaVersion",1);facts.addProperty("kind",face==null?"block":"view");facts.addProperty("actorId",player.getUUID().toString());
        facts.add("target",Blueprint.jsonPos(target));if(face!=null)facts.addProperty("face",face.name().toLowerCase(Locale.ROOT));
        JsonObject self=new JsonObject();self.add("position",vector(player.position()));self.add("eye",vector(player.getEyePosition()));self.add("look",vector(player.getViewVector(1.0F)));facts.add("player",self);
        JsonObject differences=new JsonObject();differences.addProperty("missing",missing);differences.addProperty("changed",changed);differences.addProperty("unplanned",unplanned);facts.add("differences",differences);out.add("perception",facts);
        LocalFiles.write(s.file.resolveSibling(s.file.getFileName()+".snapshot.json"),out);
        s.lastObservation=Companion.gateway(s,"/v2/observe",out,s.actor());Companion.report(s,s.lastObservation);
        s.tell("Targeted inspection: "+Blueprint.describe(s.level().getBlockState(target))+" | sampled 7 states; missing="+missing+", changed="+changed+", unplanned="+unplanned);
        return out;
    }
}
