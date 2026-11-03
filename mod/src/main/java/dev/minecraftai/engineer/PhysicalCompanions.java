package dev.minecraftai.engineer;

import com.mojang.authlib.GameProfile;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/** Optional server-owned visible actor. No autonomous construction or resource task. */
final class PhysicalCompanions {
    private record Binding(EngineeringSession project,UUID owner,Object ownerConnection,int revision,long authorization,ServerPlayer actor,Object pack,BlockPos spawn) { }
    private final ProjectManager manager;
    private final Map<UUID,Binding> actors=new HashMap<>();
    private final Map<UUID,PhysicalResourceTask> gathering=new HashMap<>();
    private final Map<UUID,PhysicalPlacementTask> placing=new HashMap<>();
    private final Map<UUID,PhysicalBlueprintJob> jobs=new HashMap<>();
    PhysicalCompanions(ProjectManager manager){this.manager=manager;}
    boolean handle(EngineeringSession project,String request){
        String text=request.trim().toLowerCase(Locale.ROOT).replaceFirst("[.!?]+$","");
        var gather=java.util.regex.Pattern.compile("gather dirt at (-?\\d+) (-?\\d+) (-?\\d+)").matcher(text);
        boolean gatherRequest=gather.matches();
        var place=java.util.regex.Pattern.compile("place retained dirt at (-?\\d+) (-?\\d+) (-?\\d+)").matcher(text);boolean placeRequest=place.matches();
        boolean blueprintRequest=text.equals("build the next blueprint block with retained dirt");
        boolean jobRequest=text.equals("gather dirt and build this blueprint");
        if(!gatherRequest && !placeRequest && !blueprintRequest && !jobRequest && !Set.of("summon my companion","dismiss my companion","where is my companion","inspect companion recovery","what happened to my gathered resources").contains(text))return false;
        project.access.require(project.actor(),ProjectAccess.Permission.VIEW);
        if(text.equals("where is my companion")){
            var binding=actors.get(project.projectId);
            project.tell(binding==null?"No physical companion is present for this project.":"The companion is at "+binding.actor.blockPosition().toShortString()+(jobs.containsKey(project.projectId)?". It is gathering materials and constructing your blueprint.":placing.containsKey(project.projectId)?". It is placing your retained dirt.":gathering.containsKey(project.projectId)?". It is gathering your requested dirt block.":". It is idle."));return true;
        }
        project.access.require(project.actor(),ProjectAccess.Permission.BUILD);
        if(!project.owner.equals(project.actor()))throw new IllegalStateException("Only the project owner can summon or dismiss its physical companion");
        if(text.equals("inspect companion recovery") || text.equals("what happened to my gathered resources")){PhysicalGatherInspection.show(manager,project);return true;}
        if(jobRequest){
            var binding=actors.get(project.projectId);if(binding==null)throw new IllegalStateException("Summon your companion first");
            if(gathering.containsKey(project.projectId) || placing.containsKey(project.projectId) || jobs.containsKey(project.projectId))throw new IllegalStateException("Stop the current physical task first");
            jobs.put(project.projectId,new PhysicalBlueprintJob(manager,project,binding.actor,binding.pack));
            project.tell("Your companion will gather nearby dirt and place the supported blueprint cells. Say 'dismiss my companion' to stop. Geometry alone will not prove it works.");return true;
        }
        if(blueprintRequest){
            var binding=actors.get(project.projectId);if(binding==null)throw new IllegalStateException("Summon your companion first");
            if(gathering.containsKey(project.projectId) || placing.containsKey(project.projectId) || jobs.containsKey(project.projectId))throw new IllegalStateException("Stop the current physical task first");
            var step=PhysicalBlueprintStep.select(project);
            placing.put(project.projectId,new PhysicalPlacementTask(manager,project,binding.actor,binding.pack,step.target(),step.reads(),step.intention()));
            project.tell("Your companion is placing the next blueprint dirt cell using its retained item. This does not verify the blueprint.");return true;
        }
        if(placeRequest){
            var binding=actors.get(project.projectId);if(binding==null)throw new IllegalStateException("Summon your companion first");
            if(gathering.containsKey(project.projectId) || placing.containsKey(project.projectId) || jobs.containsKey(project.projectId))throw new IllegalStateException("Dismiss the companion to stop its current physical task first");
            var target=new BlockPos(Integer.parseInt(place.group(1)),Integer.parseInt(place.group(2)),Integer.parseInt(place.group(3)));
            placing.put(project.projectId,new PhysicalPlacementTask(manager,project,binding.actor,binding.pack,target));
            project.tell("Your companion is placing its retained dirt through a native survival action.");return true;
        }
        if(gatherRequest){
            var binding=actors.get(project.projectId);
            if(binding==null)throw new IllegalStateException("Summon your companion first");
            if(gathering.containsKey(project.projectId) || placing.containsKey(project.projectId) || jobs.containsKey(project.projectId))throw new IllegalStateException("Stop the current task by dismissing your companion first");
            var source=new BlockPos(Integer.parseInt(gather.group(1)),Integer.parseInt(gather.group(2)),Integer.parseInt(gather.group(3)));
            gathering.put(project.projectId,new PhysicalResourceTask(manager,project,binding.actor,binding.pack,source));
            project.tell("Your companion is gathering one dirt block from the requested location. Say 'dismiss my companion' to stop.");return true;
        }
        if(text.equals("dismiss my companion")){dismiss(project.projectId);project.tell("Physical companion dismissed. No new work was delegated.");return true;}
        summon(project);project.tell("Your physical companion is here. It is a survival actor, currently idle. Saved resources are retained. Say 'dismiss my companion' to remove it.");return true;
    }
    private static void compatible(){
        var carpet=FabricLoader.getInstance().getModContainer("carpet");
        if(carpet.isEmpty() || !carpet.get().getMetadata().getVersion().getFriendlyString().equals("26.3+v260915"))
            throw new IllegalStateException("Physical companions require the optional free Carpet 26.3+v260915 server mod. The ordinary Engineer still works without it");
    }
    private void summon(EngineeringSession project){
        compatible();var owner=project.player();
        if(!manager.server.isSameThread() || owner==null || owner.hasDisconnected() || manager.server.getPlayerList().getPlayer(owner.getUUID())!=owner
            || !Objects.equals(manager.selections.get(owner.getUUID()),project.projectId))throw new IllegalStateException("Select this project using your current connection first");
        if(actors.containsKey(project.projectId))throw new IllegalStateException("This project already has a physical companion; dismiss it first");
        if(actors.size()>=4)throw new IllegalStateException("The server already has four visible companions");
        var base=project.anchor();var spawn=base.offset(1,1,1);var level=project.level();
        for(int x=0;x<3;x++)for(int z=0;z<3;z++)for(int y=0;y<3;y++){
            var pos=base.offset(x,y,z);
            if(!project.contains(pos) || !level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos) || pos.getY()<level.getMinY() || pos.getY()>=level.getMaxY())throw new IllegalStateException("Companion needs a loaded 3x3x3 space inside your selected workspace");
            var state=level.getBlockState(pos);
            if(y==0?!state.is(Blocks.STONE):!state.isAir())throw new IllegalStateException("Prepare a 3x3 stone floor with two clear air blocks above it at the workspace minimum corner");
        }
        var box=new net.minecraft.world.phys.AABB(base.getX(),base.getY()+1,base.getZ(),base.getX()+3,base.getY()+3,base.getZ()+3);
        if(!level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class,box).isEmpty())throw new IllegalStateException("Move players and entities out of the companion's spawn space first");
        ServerPlayer actor=null;
        try{
            var identity=PhysicalWorkerIdentity.open(manager,project);
            var id=identity.id();var profile=new GameProfile(id,"Eng_"+id.toString().substring(0,8));var client=ClientInformation.createDefault();
            if(manager.server.getPlayerList().getPlayer(id)!=null || manager.server.getPlayerList().getPlayerByName(profile.name())!=null)throw new IllegalStateException("Companion identity is already connected; no player was replaced");
            actor=(ServerPlayer)Class.forName("carpet.patches.EntityPlayerMPFake").getMethod("respawnFake",net.minecraft.server.MinecraftServer.class,net.minecraft.server.level.ServerLevel.class,GameProfile.class,ClientInformation.class).invoke(null,manager.server,level,profile,client);
            // A retry of an uninitialized profile may already have native data.
            // Load it if present; only a never-saved pending profile starts empty.
            var data=manager.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.PLAYER_DATA_DIR);
            if(identity.restore() || java.nio.file.Files.exists(data.resolve(id+".dat")) || java.nio.file.Files.exists(data.resolve(id+".dat_old")))PhysicalWorkerStorage.restore(actor);
            // Register default denial before the actor can enter any native player tick.
            PhysicalActionGate.register(actor);
            var connection=(Connection)Class.forName("carpet.patches.FakeClientConnection").getConstructor(PacketFlow.class).newInstance(PacketFlow.SERVERBOUND);
            manager.server.getPlayerList().placeNewPlayer(connection,actor,new CommonListenerCookie(profile,0,client,false));
            PhysicalActionGate.bindConnection(actor);
            actor.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            actor.snapTo(spawn.getX()+0.5,spawn.getY(),spawn.getZ()+0.5,0,0);
            actor.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);actor.resetFallDistance();
            if(!identity.restore() && !actor.getInventory().isEmpty() || manager.server.getPlayerList().isOp(actor.nameAndId()))throw new IllegalStateException("Companion did not start with an unprivileged identity");
            var pack=Class.forName("carpet.fakes.ServerPlayerInterface").getMethod("getActionPack").invoke(actor);pack.getClass().getMethod("stopAll").invoke(pack);
            PhysicalWorkerStorage.save(actor);
            identity.activate(manager,project);
            actors.put(project.projectId,new Binding(project,owner.getUUID(),owner.connection,project.revision(),project.access.revision(),actor,pack,spawn));
        }catch(Exception failed){
            if(actor!=null){if(manager.server.getPlayerList().getPlayer(actor.getUUID())==actor)manager.server.getPlayerList().remove(actor);PhysicalActionGate.forget(actor);}
            throw new IllegalStateException("Physical companion could not be created safely; no construction was started",failed);
        }
    }
    boolean stop(EngineeringSession project){
        if(!jobs.containsKey(project.projectId) && !gathering.containsKey(project.projectId) && !placing.containsKey(project.projectId))return false;
        project.access.require(project.actor(),ProjectAccess.Permission.BUILD);
        if(!project.owner.equals(project.actor()))throw new IllegalStateException("Only the project owner can stop its physical companion");
        var binding=actors.get(project.projectId);if(binding==null)throw new IllegalStateException("Physical actor is unavailable; inspect recovery");
        var failure=stopActions(binding);
        if(failure!=null){dismiss(project.projectId);throw new IllegalStateException("Companion removed after an action-stop error; inspect recovery",failure);}
        try{PhysicalWorkerStorage.save(binding.actor);}catch(RuntimeException uncertain){dismiss(project.projectId);throw new IllegalStateException("Physical work stopped, but inventory saving could not be verified; inspect recovery",uncertain);}
        actors.put(project.projectId,new Binding(project,binding.owner,binding.ownerConnection,binding.revision,binding.authorization,binding.actor,binding.pack,binding.actor.blockPosition()));
        project.tell("Physical work stopped. Saved inventory was checked; inspect companion recovery before a fresh request.");return true;
    }
    private RuntimeException stopActions(Binding binding){
        UUID project=binding.project.projectId;
        RuntimeException failure=null;
        var job=jobs.remove(project);if(job!=null)try{job.close();}catch(RuntimeException stopped){failure=stopped;}
        var task=gathering.remove(project);if(task!=null)try{task.close();}catch(RuntimeException stopped){failure=stopped;}
        var placement=placing.remove(project);if(placement!=null)try{placement.close();}catch(RuntimeException stopped){failure=stopped;}
        try{binding.pack.getClass().getMethod("stopAll").invoke(binding.pack);binding.pack.getClass().getMethod("setForward",float.class).invoke(binding.pack,0f);}catch(ReflectiveOperationException stopped){failure=new IllegalStateException("Could not stop physical companion actions",stopped);}
        PhysicalActionGate.cancel(binding.actor);return failure;
    }
    private void dismiss(UUID project){
        var binding=actors.get(project);if(binding==null)return;
        var failure=stopActions(binding);
        // Despawn even if the optional action pack failed. Keep default denial
        // registered until the native player is actually absent.
        try{manager.server.getPlayerList().remove(binding.actor);}finally{
            if(manager.server.getPlayerList().getPlayer(binding.actor.getUUID())!=binding.actor){PhysicalActionGate.forget(binding.actor);actors.remove(project);}
        }
        if(failure!=null){
            System.getLogger(PhysicalCompanions.class.getName()).log(System.Logger.Level.WARNING,"Companion removed after an action-stop error; inspect saved resources",failure);
            projectMessage(binding,"Companion removed after an action-stop error. Inspect saved resources before another request.");
        }
    }
    private void projectMessage(Binding binding,String text){var owner=manager.server.getPlayerList().getPlayer(binding.owner);if(owner!=null)owner.sendSystemMessage(net.minecraft.network.chat.Component.literal("Engineer: "+text));}
    void tick(){
        for(var binding:new ArrayList<>(actors.values())){
            var project=binding.project;var owner=manager.server.getPlayerList().getPlayer(binding.owner);
            if(owner==null || owner.hasDisconnected() || owner.connection!=binding.ownerConnection || !project.access.allows(binding.owner,ProjectAccess.Permission.BUILD)
                || project.revision()!=binding.revision || project.access.revision()!=binding.authorization || !Objects.equals(manager.selections.get(binding.owner),project.projectId)
                || owner.level()!=project.level() || !binding.actor.isAlive() || binding.actor.hasDisconnected() || !project.contains(binding.actor.blockPosition()) || !gathering.containsKey(project.projectId) && !jobs.containsKey(project.projectId) && !binding.actor.blockPosition().equals(binding.spawn)){dismiss(project.projectId);continue;}
            var job=jobs.get(project.projectId);if(job!=null){
                try{if(job.tick()){
                    jobs.remove(project.projectId);actors.put(project.projectId,new Binding(project,binding.owner,binding.ownerConnection,binding.revision,binding.authorization,binding.actor,binding.pack,binding.actor.blockPosition()));
                }}catch(RuntimeException stopped){dismiss(project.projectId);projectMessage(binding,"Blueprint job stopped. Inspect the blueprint and resources before a fresh request: "+stopped.getMessage());}
                continue;
            }
            var task=gathering.get(project.projectId);if(task!=null)try{
                if(task.tick()){
                    gathering.remove(project.projectId);
                    actors.put(project.projectId,new Binding(project,binding.owner,binding.ownerConnection,binding.revision,binding.authorization,binding.actor,binding.pack,binding.actor.blockPosition()));
                    project.tell("Your companion gathered one dirt block and verified its saved inventory. Construction has not started.");
                }
            }catch(RuntimeException stopped){
                dismiss(project.projectId);project.tell("Gathering stopped. Inspect the source, dropped resources and saved inventory before another request: "+stopped.getMessage());
            }
            var placement=placing.get(project.projectId);if(placement!=null)try{
                if(placement.tick()){placing.remove(project.projectId);project.tell("Your companion placed the retained dirt and verified its saved inventory. The mechanism remains untested.");}
            }catch(RuntimeException stopped){dismiss(project.projectId);project.tell("Physical placement stopped; inspect the cell and saved inventory before retry: "+stopped.getMessage());}
        }
    }
    void close(){for(var id:new ArrayList<>(actors.keySet()))dismiss(id);}
}
