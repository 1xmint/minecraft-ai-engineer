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
    private final Map<UUID,PhysicalJob> jobs=new HashMap<>();
    /** The optional second worker of a project; it only ever runs claimed component builds. */
    private final Map<UUID,Binding> helpers=new HashMap<>();
    private final Map<UUID,PhysicalMachineJob> helperJobs=new HashMap<>();
    PhysicalCompanions(ProjectManager manager){this.manager=manager;}
    /** The bounded table recipe a request names, accepting an optional "at your crafting table". */
    static TableCraftSequence.Recipe tableRecipe(String text){
        var named=text.endsWith(" at your crafting table")?text.substring(0,text.length()-" at your crafting table".length()):text;
        for(var recipe:TableCraftSequence.Recipe.values())if(named.equals("craft "+recipe.label))return recipe;
        return null;
    }
    /** The original one-log hotbar craft keeps its exact contract; any other oak log layout uses the bounded grid protocol. */
    private static boolean oakPath(ServerPlayer worker){
        var inventory=worker.getInventory();int logs=0,occupied=0;boolean single=false;
        for(int slot=0;slot<36;slot++){var held=inventory.getItem(slot);if(held.is(net.minecraft.world.item.Items.OAK_LOG))logs+=held.getCount();}
        if(logs==0)return false;
        for(int slot=0;slot<9;slot++){var held=inventory.getItem(slot);if(held.isEmpty())continue;occupied++;single=held.is(net.minecraft.world.item.Items.OAK_LOG) && held.getCount()==1;}
        return occupied==1 && single && logs==1;
    }
    boolean handle(EngineeringSession project,String request){
        String text=request.trim().toLowerCase(Locale.ROOT).replaceFirst("[.!?]+$","");
        var gather=java.util.regex.Pattern.compile("gather (dirt|oak log) at (-?\\d+) (-?\\d+) (-?\\d+)").matcher(text);
        boolean gatherRequest=gather.matches();
        var place=java.util.regex.Pattern.compile("place retained (dirt|oak planks|crafting table) at (-?\\d+) (-?\\d+) (-?\\d+)").matcher(text);boolean placeRequest=place.matches();
        var aimed=java.util.regex.Pattern.compile("place retained (stone|cobblestone|dirt|lever|redstone torch|redstone lamp) on the (top|north|south|east|west) (?:face|side) of (-?\\d+) (-?\\d+) (-?\\d+)(?: facing (north|south|east|west))?").matcher(text);boolean aimRequest=aimed.matches();
        boolean blueprintRequest=text.equals("build the next blueprint block with retained dirt");
        boolean jobRequest=text.equals("gather dirt and build this blueprint");
        boolean oakRequest=Set.of("craft oak planks","craft planks from your retained oak log","turn your retained oak log into planks").contains(text);
        var gridRecipe=Set.of("craft sticks","craft a crafting table").contains(text)?(text.equals("craft sticks")?GridCraftPlan.Recipe.STICK:GridCraftPlan.Recipe.CRAFTING_TABLE):null;
        var tableRecipe=tableRecipe(text);
        var chestTake=java.util.regex.Pattern.compile("take (\\d{1,2}) ([a-z_]+(?: [a-z_]+)*) from the chest at (-?\\d{1,8}) (-?\\d{1,4}) (-?\\d{1,8})").matcher(text);boolean chestRequest=chestTake.matches();
        boolean craftRequest=oakRequest || gridRecipe!=null || tableRecipe!=null || chestRequest;
        boolean recoveryRequest=Set.of("recover companion crafting resources","recover my companion's crafting resources").contains(text);
        var machineChest=java.util.regex.Pattern.compile("build this machine from the chest at (-?\\d{1,8}) (-?\\d{1,4}) (-?\\d{1,8})").matcher(text);boolean machineFromChest=machineChest.matches();
        boolean machineTest=text.equals("test this machine");boolean machineRequest=machineFromChest || machineTest || text.equals("build this machine");
        var second=java.util.regex.Pattern.compile("summon a second companion at (-?\\d{1,8}) (-?\\d{1,4}) (-?\\d{1,8})").matcher(text);boolean secondRequest=second.matches();
        var crewBuild=java.util.regex.Pattern.compile("have my (first|second) companion build (?:component )?([a-z0-9_-]+) from the chest at (-?\\d{1,8}) (-?\\d{1,4}) (-?\\d{1,8})").matcher(text);boolean crewRequest=crewBuild.matches();
        boolean dismissSecond=text.equals("dismiss my second companion");
        boolean recoverSecond=text.equals("recover my second companion's resources");
        if(!gatherRequest && !placeRequest && !aimRequest && !blueprintRequest && !jobRequest && !craftRequest && !recoveryRequest && !machineRequest && !secondRequest && !crewRequest && !dismissSecond && !recoverSecond && !Set.of("summon my companion","dismiss my companion","where is my companion","inspect companion recovery","what happened to my gathered resources").contains(text))return false;
        project.access.require(project.actor(),ProjectAccess.Permission.VIEW);
        if(text.equals("where is my companion")){
            var binding=actors.get(project.projectId);var extra=helpers.get(project.projectId);var helperJob=helperJobs.get(project.projectId);
            String secondary=extra==null?"":" The second companion is at "+extra.actor.blockPosition().toShortString()+(helperJob!=null?". It is building component "+helperJob.componentName()+".":". It is idle.");
            project.tell((binding==null?extra==null?"No physical companion is present for this project.":"The first companion is gone.":"The companion is at "+binding.actor.blockPosition().toShortString()+(jobs.get(project.projectId) instanceof PhysicalMachineJob?". It is building and testing your machine.":jobs.containsKey(project.projectId)?". It is gathering materials and constructing your blueprint.":placing.containsKey(project.projectId)?". It is placing your retained material.":gathering.containsKey(project.projectId)?". It is gathering your requested resource.":". It is idle."))+secondary);return true;
        }
        project.access.require(project.actor(),ProjectAccess.Permission.BUILD);
        if(!project.owner.equals(project.actor()))throw new IllegalStateException("Only the project owner can summon or dismiss its physical companion");
        if(text.equals("inspect companion recovery") || text.equals("what happened to my gathered resources")){PhysicalGatherInspection.show(manager,project);PhysicalCraftInspection.show(manager,project);var shown=actors.get(project.projectId);if(shown!=null){GridCraftRecord.show(manager,project,shown.actor);TableCraftRecord.show(manager,project,shown.actor);ChestTakeRecord.show(manager,project,shown.actor);}PhysicalPlacementRecovery.show(manager,project,shown==null?null:shown.actor);var extra=helpers.get(project.projectId);if(extra!=null)ChestTakeRecord.show(manager,project,extra.actor);PhysicalPlacementRecovery.showSecond(manager,project,extra==null?null:extra.actor);return true;}
        if(secondRequest){
            spawn(project,2,new BlockPos(Integer.parseInt(second.group(1))-1,Integer.parseInt(second.group(2))-1,Integer.parseInt(second.group(3))-1),helpers);
            project.tell("Your second companion is here. It is a separate survival actor with its own inventory, currently idle. Say 'dismiss my second companion' to remove it.");return true;
        }
        if(dismissSecond){
            if(!helpers.containsKey(project.projectId)){project.tell("No second companion is present for this project.");return true;}
            boolean removed=remove(project.projectId,helpers);project.tell(removed?"Second companion dismissed. Your first companion and its work were left alone.":"Second companion dismissal is pending until its current native action returns and its resources are saved.");return true;
        }
        if(recoverSecond){
            var binding=helpers.get(project.projectId);if(binding==null)throw new IllegalStateException("Summon your second companion first");
            var alone=exclusive(project,binding,true);
            // Recovery runs under the project-wide scope, which must never replace another live project-wide lease.
            Runnable whole=()->{alone.run();if(gathering.containsKey(project.projectId) || placing.containsKey(project.projectId) || jobs.containsKey(project.projectId))throw new IllegalStateException("Stop all companion work before recovering the second companion's resources");};
            whole.run();
            if(PhysicalCraftGuard.active(binding.actor))throw new IllegalStateException("Companion crafting must stop and preserve resources before new work");
            PhysicalPlacementRecovery.closeForOwner(manager,project,binding.actor);
            var chest=ChestTakeRecord.unresolvedForRecovery(manager,project,binding.actor);
            if(chest==null){project.tell("Your second companion has no unfinished resources to recover. No items were moved.");return true;}
            ChestTakeTask.recover(manager,project,binding.actor,chest,whole);
            project.tell("Your second companion's resources are stored and their saved state was verified. Nothing was retried.");return true;
        }
        if(crewRequest){
            boolean helper=crewBuild.group(1).equals("second");
            var binding=(helper?helpers:actors).get(project.projectId);if(binding==null)throw new IllegalStateException(helper?"Summon a second companion first":"Summon your companion first");
            if(gathering.containsKey(project.projectId) || placing.containsKey(project.projectId) || (jobs.containsKey(project.projectId) && !(jobs.get(project.projectId) instanceof PhysicalMachineJob running && running.crew())))throw new IllegalStateException("Stop the current physical task first");
            if(helper?helperJobs.containsKey(project.projectId):jobs.containsKey(project.projectId))throw new IllegalStateException("That companion is already building something; stop it first");
            if(PhysicalCraftGuard.active(binding.actor))throw new IllegalStateException("Companion crafting must stop and preserve resources before new work");
            PhysicalWorkerMenus.requireSettled(binding.actor);
            var chest=new BlockPos(Integer.parseInt(crewBuild.group(3)),Integer.parseInt(crewBuild.group(4)),Integer.parseInt(crewBuild.group(5)));
            var job=PhysicalMachineJob.crewBuild(manager,project,binding.actor,binding.pack,crewBuild.group(2),chest,exclusive(project,binding,helper));
            if(helper)helperJobs.put(project.projectId,job);else jobs.put(project.projectId,job);
            project.tell("Your "+crewBuild.group(1)+" companion took its materials and is building component "+crewBuild.group(2)+" by hand. Say 'dismiss my second companion' to stop only that one, or 'dismiss my companion' to stop both. Your blocks are left alone.");return true;
        }
        if(gatherRequest || placeRequest || aimRequest || blueprintRequest || jobRequest || craftRequest || recoveryRequest || machineRequest){if(helperJobs.containsKey(project.projectId))throw new IllegalStateException("Your second companion is building; stop it before asking for other companion work");var binding=actors.get(project.projectId);if(binding!=null){if(PhysicalCraftGuard.active(binding.actor))throw new IllegalStateException("Companion crafting must stop and preserve resources before new work");
            // An owner message must never settle a placement that is still in flight; each branch below refuses a busy worker.
            boolean live=gathering.containsKey(project.projectId) || placing.containsKey(project.projectId) || jobs.containsKey(project.projectId);
            if(!live){if(!recoveryRequest)PhysicalWorkerMenus.requireSettled(binding.actor);if(!craftRequest && !recoveryRequest)GridCraftTask.requireNoUnresolved(manager,project,binding.actor);if(!recoveryRequest)PhysicalPlacementRecovery.requireNone(manager,project,binding.actor);}}}
        if(craftRequest || recoveryRequest || machineRequest){
            var binding=actors.get(project.projectId);if(binding==null)throw new IllegalStateException("Summon your companion first");
            Runnable exclusive=exclusive(project,binding,false);
            if(machineRequest){
                var job=machineTest?PhysicalMachineJob.test(manager,project,binding.actor,binding.pack,exclusive)
                    :PhysicalMachineJob.build(manager,project,binding.actor,binding.pack,machineFromChest?new BlockPos(Integer.parseInt(machineChest.group(1)),Integer.parseInt(machineChest.group(2)),Integer.parseInt(machineChest.group(3))):null,exclusive);
                if(job==null)return true;
                jobs.put(project.projectId,job);
                project.tell(machineTest?"Your companion is testing this machine by pulling its real levers. Say 'dismiss my companion' to stop.":"Your companion is building this machine from real items, by hand, one block at a time. Say 'dismiss my companion' to stop. Your blocks are left alone.");return true;
            }
            if(recoveryRequest){
                exclusive.run();PhysicalPlacementRecovery.closeForOwner(manager,project,binding.actor);var original=PhysicalCraftRecord.unresolvedForRecovery(manager,project,binding.actor);var grid=GridCraftRecord.unresolvedForRecovery(manager,project,binding.actor);var table=TableCraftRecord.unresolvedForRecovery(manager,project,binding.actor);var chest=ChestTakeRecord.unresolvedForRecovery(manager,project,binding.actor);
                if((original!=null?1:0)+(grid!=null?1:0)+(table!=null?1:0)+(chest!=null?1:0)>1)throw new IllegalStateException("Several unfinished crafting records need operator inspection; none was selected");
                if(original==null && grid==null && table==null && chest==null){project.tell("No unfinished crafting resources need recovery. No items were moved.");return true;}
                if(chest!=null)ChestTakeTask.recover(manager,project,binding.actor,chest,exclusive);
                else if(table!=null)TableCraftTask.recover(manager,project,binding.actor,table,exclusive);
                else if(grid!=null)GridCraftTask.recover(manager,project,binding.actor,grid,exclusive);
                else PhysicalCraftRecoveryTask.execute(manager,project,binding.actor,original,exclusive);
                project.tell("Your companion's crafting resources are stored and their saved state was verified. No recipe was retried. Ask separately if you want to craft.");return true;
            }
            if(chestRequest){
                var item=chestItem(chestTake.group(2));int count=Integer.parseInt(chestTake.group(1));
                var at=new BlockPos(Integer.parseInt(chestTake.group(3)),Integer.parseInt(chestTake.group(4)),Integer.parseInt(chestTake.group(5)));
                ChestTakeTask.take(manager,project,binding.actor,at,item,count,exclusive);
                project.tell("Your companion took "+count+" "+new net.minecraft.world.item.ItemStack(item).getHoverName().getString()+" from the chest and verified its saved inventory. Nothing was placed.");return true;
            }
            if(tableRecipe!=null){
                TableCraftTask.craft(manager,project,binding.actor,tableRecipe,exclusive);
                project.tell("Your companion crafted "+tableRecipe.label+" at its crafting table and verified its saved inventory. No blocks were placed.");return true;
            }
            var recipe=gridRecipe!=null?gridRecipe:oakPath(binding.actor)?null:GridCraftPlan.Recipe.OAK_PLANKS;
            if(recipe==null){
                PhysicalCraftTask.execute(manager,project,binding.actor,exclusive);
                project.tell("Your companion crafted four oak planks from its retained log and verified its saved inventory. No blocks were placed.");return true;
            }
            GridCraftTask.craft(manager,project,binding.actor,recipe,exclusive);
            project.tell("Your companion crafted "+recipe.label+" and verified its saved inventory. No blocks were placed.");return true;
        }
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
            var target=new BlockPos(Integer.parseInt(place.group(2)),Integer.parseInt(place.group(3)),Integer.parseInt(place.group(4)));
            placing.put(project.projectId,new PhysicalPlacementTask(manager,project,binding.actor,binding.pack,target,Set.of(),()->{},switch(place.group(1)){case "dirt"->PhysicalPlacementInventory.Material.DIRT;case "oak planks"->PhysicalPlacementInventory.Material.OAK_PLANKS;default->PhysicalPlacementInventory.Material.CRAFTING_TABLE;}));
            project.tell("Your companion is placing one retained item through a native survival action.");return true;
        }
        if(aimRequest){
            var binding=actors.get(project.projectId);if(binding==null)throw new IllegalStateException("Summon your companion first");
            if(gathering.containsKey(project.projectId) || placing.containsKey(project.projectId) || jobs.containsKey(project.projectId))throw new IllegalStateException("Dismiss the companion to stop its current physical task first");
            var support=new BlockPos(Integer.parseInt(aimed.group(3)),Integer.parseInt(aimed.group(4)),Integer.parseInt(aimed.group(5)));
            var face=aimed.group(2).equals("top")?net.minecraft.core.Direction.UP:net.minecraft.core.Direction.byName(aimed.group(2));
            var facing=aimed.group(6)==null?null:net.minecraft.core.Direction.byName(aimed.group(6));
            if(!project.contains(support) || !project.level().hasChunkAt(support))throw new IllegalStateException("The support block must be loaded inside this workspace; nothing was placed");
            var aim=new PhysicalPlacementInventory.Aim(support,face,project.level().getBlockState(support),plannedState(aimed.group(1),face,facing));
            placing.put(project.projectId,new PhysicalPlacementTask(manager,project,binding.actor,binding.pack,aim,Set.of(),()->{}));
            project.tell("Your companion is placing one retained "+aimed.group(1)+" on the "+aimed.group(2)+" face of "+support.toShortString()+" through a native survival action. Only the exact planned orientation will be accepted.");return true;
        }
        if(gatherRequest){
            var binding=actors.get(project.projectId);
            if(binding==null)throw new IllegalStateException("Summon your companion first");
            if(gathering.containsKey(project.projectId) || placing.containsKey(project.projectId) || jobs.containsKey(project.projectId))throw new IllegalStateException("Stop the current task by dismissing your companion first");
            var source=new BlockPos(Integer.parseInt(gather.group(2)),Integer.parseInt(gather.group(3)),Integer.parseInt(gather.group(4)));
            var material=gather.group(1).equals("dirt")?PhysicalMaterial.DIRT:PhysicalMaterial.OAK_LOG;
            if(!project.contains(source) || !project.level().hasChunkAt(source) || !project.level().getBlockState(source).is(material.block))throw new IllegalStateException("Requested resource does not match a loaded source inside this workspace");
            gathering.put(project.projectId,new PhysicalResourceTask(manager,project,binding.actor,binding.pack,source));
            project.tell("Your companion is gathering one "+(material==PhysicalMaterial.DIRT?"dirt block":"oak log")+" from the requested location. Say 'stop' to stop.");return true;
        }
        if(text.equals("dismiss my companion")){boolean removed=dismissCrew(project.projectId);project.tell(removed?"Physical companion dismissed. No new work was delegated.":"Companion dismissal is pending until the current native action returns and its resources are saved.");return true;}
        spawn(project,1,project.anchor(),actors);project.tell("Your physical companion is here. It is a survival actor, currently idle. Saved resources are retained. Say 'dismiss my companion' to remove it.");return true;
    }
    /** A plain item id spoken with spaces, such as "cobblestone" or "oak planks"; unknown names are refused before any action. */
    private static net.minecraft.world.item.Item chestItem(String name){
        var id=net.minecraft.resources.Identifier.tryParse("minecraft:"+name.replace(' ','_'));
        if(id==null || !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id))throw new IllegalStateException("Your companion does not know an item called "+name+"; nothing was taken");
        var item=net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(id);
        if(item==net.minecraft.world.item.Items.AIR)throw new IllegalStateException("Your companion does not know an item called "+name+"; nothing was taken");
        return item;
    }
    /** The exact block state one native use must produce for a requested material, support face and lever facing. */
    static net.minecraft.world.level.block.state.BlockState plannedState(String material,net.minecraft.core.Direction face,net.minecraft.core.Direction facing){
        boolean top=face==net.minecraft.core.Direction.UP;
        if(facing!=null && !(material.equals("lever") && top))throw new IllegalStateException("Only a lever on a top face takes a facing; its side-face orientation follows the face");
        return switch(material){
            case "lever"->{
                if(top && facing==null)throw new IllegalStateException("Say which way the lever on a top face should face, for example 'facing east'");
                yield Blocks.LEVER.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ATTACH_FACE,top?net.minecraft.world.level.block.state.properties.AttachFace.FLOOR:net.minecraft.world.level.block.state.properties.AttachFace.WALL)
                    .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING,top?facing:face);
            }
            case "redstone torch"->top?Blocks.REDSTONE_TORCH.defaultBlockState()
                :Blocks.REDSTONE_WALL_TORCH.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING,face);
            case "redstone lamp"->Blocks.REDSTONE_LAMP.defaultBlockState();
            case "stone"->Blocks.STONE.defaultBlockState();
            case "cobblestone"->Blocks.COBBLESTONE.defaultBlockState();
            case "dirt"->Blocks.DIRT.defaultBlockState();
            default->throw new IllegalStateException("The companion has no machine placement rule for "+material);
        };
    }
    private static void compatible(){
        var carpet=FabricLoader.getInstance().getModContainer("carpet");
        if(carpet.isEmpty() || !carpet.get().getMetadata().getVersion().getFriendlyString().equals("26.3+v260915"))
            throw new IllegalStateException("Physical companions require the optional free Carpet 26.3+v260915 server mod. The ordinary Engineer still works without it");
    }
    /** Per-worker idle gate: this worker must still be the bound, idle body of a current owner request. */
    private Runnable exclusive(EngineeringSession project,Binding binding,boolean helper){
        var table=helper?helpers:actors;
        return ()->{
            var owner=manager.server.getPlayerList().getPlayer(project.owner);
            if(table.get(project.projectId)!=binding || binding.project!=project || owner==null || owner.connection!=binding.ownerConnection
                || !project.projectId.equals(manager.selections.get(project.owner))
                || project.revision()!=binding.revision || project.access.revision()!=binding.authorization || !binding.actor.blockPosition().equals(binding.spawn)
                || (helper?helperJobs.containsKey(project.projectId):gathering.containsKey(project.projectId) || placing.containsKey(project.projectId) || jobs.containsKey(project.projectId)))
                throw new IllegalStateException("Inventory work needs your current idle companion; stop other work and request it again");
        };
    }
    private void spawn(EngineeringSession project,int slot,BlockPos base,Map<UUID,Binding> table){
        compatible();var owner=project.player();
        if(!manager.server.isSameThread() || owner==null || owner.hasDisconnected() || manager.server.getPlayerList().getPlayer(owner.getUUID())!=owner
            || !Objects.equals(manager.selections.get(owner.getUUID()),project.projectId))throw new IllegalStateException("Select this project using your current connection first");
        if(slot==2 && !actors.containsKey(project.projectId))throw new IllegalStateException("Summon your first companion before a second one");
        if(table.containsKey(project.projectId))throw new IllegalStateException(slot==1?"This project already has a physical companion; dismiss it first":"This project already has a second companion; dismiss it first");
        if(actors.size()+helpers.size()>=4)throw new IllegalStateException("The server already has four visible companions");
        var spawn=base.offset(1,1,1);var level=project.level();
        for(int x=0;x<3;x++)for(int z=0;z<3;z++)for(int y=0;y<3;y++){
            var pos=base.offset(x,y,z);
            if(!project.contains(pos) || !level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos) || pos.getY()<level.getMinY() || pos.getY()>=level.getMaxY())throw new IllegalStateException("Companion needs a loaded 3x3x3 space inside your selected workspace");
            var state=level.getBlockState(pos);
            if(y==0?!state.is(Blocks.STONE):!state.isAir())throw new IllegalStateException("Prepare a 3x3 stone floor with two clear air blocks above it "+(slot==1?"at the workspace minimum corner":"around that spot"));
        }
        var box=new net.minecraft.world.phys.AABB(base.getX(),base.getY()+1,base.getZ(),base.getX()+3,base.getY()+3,base.getZ()+3);
        if(!level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class,box).isEmpty())throw new IllegalStateException("Move players and entities out of the companion's spawn space first");
        ServerPlayer actor=null;
        try{
            var identity=PhysicalWorkerIdentity.open(manager,project,slot);
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
            PhysicalWorkerMenus.finishRestore(actor);
            actor.snapTo(spawn.getX()+0.5,spawn.getY(),spawn.getZ()+0.5,0,0);
            actor.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);actor.resetFallDistance();
            if(!identity.restore() && (!actor.getInventory().isEmpty() || !PhysicalWorkerMenus.capture(actor).empty()) || manager.server.getPlayerList().isOp(actor.nameAndId()))throw new IllegalStateException("Companion did not start with an unprivileged identity");
            var pack=Class.forName("carpet.fakes.ServerPlayerInterface").getMethod("getActionPack").invoke(actor);pack.getClass().getMethod("stopAll").invoke(pack);
            PhysicalWorkerStorage.save(actor);
            identity.activate(manager,project);
            table.put(project.projectId,new Binding(project,owner.getUUID(),owner.connection,project.revision(),project.access.revision(),actor,pack,spawn));
        }catch(Exception failed){
            if(actor!=null)try{
                if(manager.server.getPlayerList().getPlayer(actor.getUUID())==actor)manager.server.getPlayerList().remove(actor);
                PhysicalActionGate.forget(actor);
            }catch(RuntimeException cleanup){failed.addSuppressed(cleanup);}
            throw new IllegalStateException("Physical companion could not be created safely; no construction was started",failed);
        }
    }
    boolean stop(EngineeringSession project){
        var current=actors.get(project.projectId);var helper=helpers.get(project.projectId);
        if(!jobs.containsKey(project.projectId) && !helperJobs.containsKey(project.projectId) && !gathering.containsKey(project.projectId) && !placing.containsKey(project.projectId) && (current==null || !PhysicalCraftGuard.active(current.actor)) && (helper==null || !PhysicalCraftGuard.active(helper.actor)))return false;
        project.access.require(project.actor(),ProjectAccess.Permission.BUILD);
        if(!project.owner.equals(project.actor()))throw new IllegalStateException("Only the project owner can stop its physical companion");
        if(current==null && helper==null)throw new IllegalStateException("Physical actor is unavailable; inspect recovery");
        boolean crafting=current!=null && PhysicalCraftGuard.requestStop(current.actor);
        crafting=helper!=null && PhysicalCraftGuard.requestStop(helper.actor) || crafting;
        if(crafting){project.tell("Crafting stop requested. No further crafting clicks are authorized.");return true;}
        // Both workers stop even if the first one reports an error.
        try{if(current!=null)stopWorker(project,current,actors);}finally{if(helper!=null)stopWorker(project,helper,helpers);}
        project.tell("Physical work stopped. Saved inventory was checked; inspect companion recovery before a fresh request.");return true;
    }
    private void stopWorker(EngineeringSession project,Binding binding,Map<UUID,Binding> table){
        var failure=stopActions(binding);
        if(failure!=null){remove(project.projectId,table);throw new IllegalStateException("Companion removed after an action-stop error; inspect recovery",failure);}
        try{PhysicalWorkerStorage.save(binding.actor);}catch(RuntimeException uncertain){remove(project.projectId,table);throw new IllegalStateException("Physical work stopped, but inventory saving could not be verified; inspect recovery",uncertain);}
        table.put(project.projectId,rebound(binding));
    }
    private static Binding rebound(Binding binding){return new Binding(binding.project,binding.owner,binding.ownerConnection,binding.revision,binding.authorization,binding.actor,binding.pack,binding.actor.blockPosition());}
    private RuntimeException stopActions(Binding binding){
        UUID project=binding.project.projectId;
        RuntimeException failure=null;
        var second=helpers.get(project);
        if(second!=null && second.actor==binding.actor){
            var built=helperJobs.remove(project);if(built!=null)try{built.close();}catch(RuntimeException stopped){failure=stopped;}
        }else{
            var job=jobs.remove(project);if(job!=null)try{job.close();}catch(RuntimeException stopped){failure=stopped;}
            var task=gathering.remove(project);if(task!=null)try{task.close();}catch(RuntimeException stopped){failure=stopped;}
            var placement=placing.remove(project);if(placement!=null)try{placement.close();}catch(RuntimeException stopped){failure=stopped;}
        }
        try{binding.pack.getClass().getMethod("stopAll").invoke(binding.pack);binding.pack.getClass().getMethod("setForward",float.class).invoke(binding.pack,0f);}catch(ReflectiveOperationException stopped){failure=new IllegalStateException("Could not stop physical companion actions",stopped);}
        PhysicalActionGate.cancel(binding.actor);return failure;
    }
    private boolean dismiss(UUID project){return remove(project,actors);}
    /** Removes the whole crew; the second companion goes first so its claim is released before its sibling's. */
    private boolean dismissCrew(UUID project){boolean second=remove(project,helpers);boolean first=remove(project,actors);return second && first;}
    private boolean remove(UUID project,Map<UUID,Binding> table){
        var binding=table.get(project);if(binding==null)return true;
        if(PhysicalCraftGuard.deferRemoval(binding.actor,()->{var current=table.get(project);if(current!=null && current.actor==binding.actor)remove(project,table);}))return false;
        var failure=stopActions(binding);
        // Despawn even if the optional action pack failed. Keep default denial
        // registered until the native player is actually absent.
        try{if(manager.server.getPlayerList().getPlayer(binding.actor.getUUID())==binding.actor)manager.server.getPlayerList().remove(binding.actor);}finally{
            if(manager.server.getPlayerList().getPlayer(binding.actor.getUUID())!=binding.actor){PhysicalActionGate.forget(binding.actor);table.remove(project);}
        }
        if(failure!=null){
            System.getLogger(PhysicalCompanions.class.getName()).log(System.Logger.Level.WARNING,"Companion removed after an action-stop error; inspect saved resources",failure);
            projectMessage(binding,"Companion removed after an action-stop error. Inspect saved resources before another request.");
        }
        return manager.server.getPlayerList().getPlayer(binding.actor.getUUID())!=binding.actor;
    }
    private void projectMessage(Binding binding,String text){var owner=manager.server.getPlayerList().getPlayer(binding.owner);if(owner!=null)owner.sendSystemMessage(net.minecraft.network.chat.Component.literal("Engineer: "+text));}
    void tick(){
        for(var binding:new ArrayList<>(actors.values())){
            var project=binding.project;
            if(ownerLost(binding)){dismissCrew(project.projectId);continue;}
            var job=jobs.get(project.projectId);
            if(bodyLost(binding,gathering.containsKey(project.projectId) || job!=null)){
                boolean crewed=job instanceof PhysicalMachineJob built && built.crew();
                dismiss(project.projectId);
                if(crewed)projectMessage(binding,"Your first companion was lost, so its build stopped. Your other companion and every placed block were left alone.");
                continue;
            }
            if(job!=null){
                try{if(job.tick()){
                    jobs.remove(project.projectId);actors.put(project.projectId,rebound(binding));
                }}catch(RuntimeException stopped){dismiss(project.projectId);projectMessage(binding,(job instanceof PhysicalMachineJob?"Machine job":"Blueprint job")+" stopped. Inspect the blueprint and resources before a fresh request: "+stopped.getMessage());}
                continue;
            }
            var task=gathering.get(project.projectId);if(task!=null)try{
                if(task.tick()){
                    gathering.remove(project.projectId);
                    actors.put(project.projectId,new Binding(project,binding.owner,binding.ownerConnection,binding.revision,binding.authorization,binding.actor,binding.pack,binding.actor.blockPosition()));
                    project.tell("Your companion gathered one resource and verified its saved inventory. Construction has not started.");
                }
            }catch(RuntimeException stopped){
                dismiss(project.projectId);project.tell("Gathering stopped. Inspect the source, dropped resources and saved inventory before another request: "+stopped.getMessage());
            }
            var placement=placing.get(project.projectId);if(placement!=null)try{
                if(placement.tick()){placing.remove(project.projectId);project.tell("Your companion placed "+placement.placedMaterial()+" and verified its saved inventory. The mechanism remains untested.");}
            }catch(RuntimeException stopped){dismiss(project.projectId);project.tell("Physical placement stopped; inspect the cell and saved inventory before retry: "+stopped.getMessage());}
        }
        for(var binding:new ArrayList<>(helpers.values())){
            var project=binding.project;var current=helpers.get(project.projectId);
            if(current==null || current.actor!=binding.actor)continue;
            if(ownerLost(binding)){dismissCrew(project.projectId);continue;}
            var job=helperJobs.get(project.projectId);
            if(bodyLost(binding,job!=null)){
                remove(project.projectId,helpers);
                if(job!=null)projectMessage(binding,"Your second companion was lost, so its build stopped. Your other companion and every placed block were left alone.");
                continue;
            }
            if(job==null)continue;
            try{if(job.tick()){helperJobs.remove(project.projectId);helpers.put(project.projectId,rebound(binding));}}
            catch(RuntimeException stopped){remove(project.projectId,helpers);projectMessage(binding,"Second companion's machine job stopped. Inspect the blueprint and resources before a fresh request: "+stopped.getMessage());}
        }
    }
    /** Owner or project authority is gone, which ends every worker of the project. */
    private boolean ownerLost(Binding binding){
        var project=binding.project;var owner=manager.server.getPlayerList().getPlayer(binding.owner);
        return owner==null || owner.hasDisconnected() || owner.connection!=binding.ownerConnection || !project.access.allows(binding.owner,ProjectAccess.Permission.BUILD)
            || project.revision()!=binding.revision || project.access.revision()!=binding.authorization || !Objects.equals(manager.selections.get(binding.owner),project.projectId)
            || owner.level()!=project.level();
    }
    /** This worker's own body is gone or out of place, which ends only this worker. */
    private boolean bodyLost(Binding binding,boolean moving){
        return !binding.actor.isAlive() || binding.actor.hasDisconnected() || manager.server.getPlayerList().getPlayer(binding.actor.getUUID())!=binding.actor
            || !binding.project.contains(binding.actor.blockPosition()) || !moving && !binding.actor.blockPosition().equals(binding.spawn);
    }
    void close(){for(var id:new ArrayList<>(helpers.keySet()))remove(id,helpers);for(var id:new ArrayList<>(actors.keySet()))dismiss(id);}
}
