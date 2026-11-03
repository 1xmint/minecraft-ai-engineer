package dev.minecraftai.engineer;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** CI-only feasibility probe, never registered or packaged in the product. */
public final class PhysicalBuilderGameTests {
    @SuppressWarnings({"unchecked","rawtypes"})
    private static void action(Object pack,String kind,String frequency) throws Exception {
        Class type=Class.forName("carpet.helpers.EntityPlayerActionPack$ActionType");
        Class action=Class.forName("carpet.helpers.EntityPlayerActionPack$Action");
        pack.getClass().getMethod("start",type,action).invoke(pack,Enum.valueOf(type,kind),action.getMethod(frequency).invoke(null));
    }
    private static void look(Object pack,Vec3 at) throws Exception {pack.getClass().getMethod("lookAt",Vec3.class).invoke(pack,at);}
    private static void update(Object pack) throws Exception {pack.getClass().getMethod("onUpdate").invoke(pack);}
    private static void stop(Object pack) throws Exception {pack.getClass().getMethod("stopAll").invoke(pack);}
    @GameTest(structure="engineer:analog_lab",maxTicks=160) public void survivalActorUsesItemsAndMinesWithoutProfileLookup(GameTestHelper helper) throws Exception {
        var level=helper.getLevel();var server=level.getServer();
        var profile=new GameProfile(UUID.fromString("78a23770-a2d9-4c38-ae47-97bc05edcbfa"),"EngineerProbe");
        var client=ClientInformation.createDefault();
        var actor=(ServerPlayer)Class.forName("carpet.patches.EntityPlayerMPFake")
            .getMethod("respawnFake",net.minecraft.server.MinecraftServer.class,net.minecraft.server.level.ServerLevel.class,GameProfile.class,ClientInformation.class)
            .invoke(null,server,level,profile,client);
        var connection=(Connection)Class.forName("carpet.patches.FakeClientConnection").getConstructor(PacketFlow.class).newInstance(PacketFlow.SERVERBOUND);
        server.getPlayerList().placeNewPlayer(connection,actor,new CommonListenerCookie(profile,0,client,false));
        actor.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        var origin=helper.absolutePos(new BlockPos(1,1,1));
        actor.snapTo(origin.getX()+0.5,origin.getY(),origin.getZ()+0.5,0,0);
        for(int x=0;x<6;x++)for(int z=0;z<6;z++)helper.setBlock(new BlockPos(x,0,z),Blocks.STONE);
        var support=helper.absolutePos(new BlockPos(1,0,3));var placed=support.above();
        Object pack=Class.forName("carpet.fakes.ServerPlayerInterface").getMethod("getActionPack").invoke(actor);
        actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DIRT,2));
        look(pack,new Vec3(support.getX()+0.5,support.getY()+1,support.getZ()+0.5));action(pack,"USE","once");update(pack);
        helper.assertTrue(level.getBlockState(placed).is(Blocks.DIRT) && actor.getMainHandItem().getCount()==1,"Actual survival item use places one dirt and consumes exactly one item");
        stop(pack);actor.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        var emptyTarget=helper.absolutePos(new BlockPos(3,0,3));look(pack,new Vec3(emptyTarget.getX()+0.5,emptyTarget.getY()+1,emptyTarget.getZ()+0.5));action(pack,"USE","once");update(pack);
        helper.assertTrue(level.getBlockState(emptyTarget.above()).isAir(),"Empty hand cannot create a building block");
        stop(pack);actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DIRT,1));
        // Use a separate ray: the previously placed dirt must not be a nearer hit.
        var far=origin.offset(12,-1,0);level.setBlockAndUpdate(far,Blocks.STONE.defaultBlockState());
        look(pack,new Vec3(far.getX()+0.5,far.getY()+1,far.getZ()+0.5));action(pack,"USE","once");update(pack);
        helper.assertTrue(level.getBlockState(far.above()).isAir() && actor.getMainHandItem().getCount()==1,"Out-of-reach item use does not place or consume");
        stop(pack);actor.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        helper.assertTrue(!actor.getInventory().contains(new ItemStack(Items.DIRT)),"No fixture dirt remains before native mining and pickup");
        look(pack,Vec3.atCenterOf(placed));action(pack,"ATTACK","continuous");
        // The actor's own action pack advances from Carpet's native player tick.
        helper.runAtTickTime(2,()->{
            try{stop(pack);}catch(Exception failed){throw new RuntimeException(failed);}
        });
        helper.runAtTickTime(20,()->{
            try{
                helper.assertTrue(level.getBlockState(placed).is(Blocks.DIRT),"Cancelled partial mining performs no delayed block destruction");
                look(pack,Vec3.atCenterOf(placed));action(pack,"ATTACK","continuous");
            }catch(Exception failed){server.getPlayerList().remove(actor);throw new RuntimeException(failed);}
        });
        helper.runAtTickTime(55,()->{
            try{
                stop(pack);
                helper.assertTrue(level.getBlockState(placed).isAir(),"Actual survival attack mines the placed dirt over native ticks");
                helper.assertTrue(level.getBlockState(support).is(Blocks.STONE),"Stopping attack retains the supporting block");
                pack.getClass().getMethod("look",float.class,float.class).invoke(pack,0f,0f);
                pack.getClass().getMethod("setForward",float.class).invoke(pack,1f);
            }catch(Exception failed){server.getPlayerList().remove(actor);throw new RuntimeException(failed);}
        });
        helper.runAtTickTime(80,()->{
            try{
                stop(pack);
                helper.assertTrue(actor.getZ()>origin.getZ()+1.25,"Actor walks toward the mined material through native movement");
                int collected=0;for(int slot=0;slot<actor.getInventory().getContainerSize();slot++){
                    var stack=actor.getInventory().getItem(slot);if(stack.is(Items.DIRT))collected+=stack.getCount();
                }
                helper.assertTrue(collected==1,"Exactly one mined drop is collected through physical pickup");
                gatedActions(helper,actor,support,placed);
                projectAuthority(helper,actor,support,placed);
                System.out.println("ENGINEER_PHYSICAL_PROBE survival-placement=1 consumed=1 empty-refused=true reach-refused=true mined=true walked=true collected=1 cancelled=true");
                PhysicalActionGate.forget(actor);server.getPlayerList().remove(actor);helper.succeed();
            }catch(Exception failed){PhysicalActionGate.forget(actor);server.getPlayerList().remove(actor);throw new RuntimeException(failed);}
        });
    }
    private static void gatedActions(GameTestHelper helper,ServerPlayer actor,BlockPos support,BlockPos target){
        PhysicalActionGate.register(actor);var level=actor.level();long now=level.getServer().getTickCount();
        var hit=new net.minecraft.world.phys.BlockHitResult(new Vec3(support.getX()+0.5,support.getY()+1,support.getZ()+0.5),net.minecraft.core.Direction.UP,support,false);
        var allowed=new java.util.concurrent.atomic.AtomicBoolean(true);var written=new java.util.concurrent.atomic.AtomicInteger();
        Runnable authority=()->{if(!allowed.get())throw new IllegalStateException("Human delegation revoked");};
        Runnable wal=written::incrementAndGet;
        for(String fault:java.util.List.of("missing","revoked","expired","expiry-boundary","wrong-target","edited","write-ahead-failed")){
            level.setBlockAndUpdate(target,Blocks.AIR.defaultBlockState());actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DIRT,1));allowed.set(!fault.equals("revoked"));
            if(!fault.equals("missing"))PhysicalActionGate.arm(actor,"place",fault.equals("wrong-target")?support:target,Blocks.AIR.defaultBlockState(),fault.equals("expired")?now-1:fault.equals("expiry-boundary")?now:now+100,authority,
                fault.equals("write-ahead-failed")?()->{throw new IllegalStateException("Persistence unavailable");}:wal);
            if(fault.equals("edited"))level.setBlockAndUpdate(target,Blocks.COBBLESTONE.defaultBlockState());
            var before=level.getBlockState(target);
            actor.gameMode.useItemOn(actor,level,actor.getMainHandItem(),InteractionHand.MAIN_HAND,hit);
            helper.assertTrue(level.getBlockState(target).equals(before) && actor.getMainHandItem().getCount()==1 && written.get()==0,"Native placement refuses "+fault+" without changing world/items or committing WAL");
        }
        level.setBlockAndUpdate(target,Blocks.AIR.defaultBlockState());allowed.set(true);
        PhysicalActionGate.arm(actor,"place",target,Blocks.AIR.defaultBlockState(),now+1,authority,wal);
        actor.gameMode.useItemOn(actor,level,actor.getMainHandItem(),InteractionHand.MAIN_HAND,hit);
        helper.assertTrue(level.getBlockState(target).is(Blocks.DIRT) && actor.getMainHandItem().isEmpty() && written.get()==1,"Live exact-destination ticket persists before actual survival placement");
        helper.assertTrue(!actor.gameMode.destroyBlock(target) && level.getBlockState(target).is(Blocks.DIRT),"Consumed placement ticket cannot authorize mining");
        PhysicalActionGate.arm(actor,"mine",target,Blocks.DIRT.defaultBlockState(),now+100,authority,wal);allowed.set(false);
        helper.assertTrue(!actor.gameMode.destroyBlock(target) && level.getBlockState(target).is(Blocks.DIRT) && written.get()==1,"Revocation after mining start is rechecked at final destruction");
        allowed.set(true);PhysicalActionGate.arm(actor,"mine",target,Blocks.DIRT.defaultBlockState(),now+100,authority,wal);
        helper.assertTrue(actor.gameMode.destroyBlock(target) && level.getBlockState(target).isAir() && written.get()==2,"Fresh live mining ticket permits actual native destruction");
        actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.WATER_BUCKET));
        actor.gameMode.useItemOn(actor,level,actor.getMainHandItem(),InteractionHand.MAIN_HAND,hit);
        helper.assertTrue(level.getBlockState(target).isAir() && actor.getMainHandItem().is(Items.WATER_BUCKET),"Unsupported bucket cannot bypass the placement ticket gate");
        System.out.println("ENGINEER_PHYSICAL_AUTHORITY missing=denied revoked=denied expired=denied wrong-destination=denied edited=denied wal-failure=denied one-use=true bucket=denied live-place=true live-mine=true");
    }
    private static void projectAuthority(GameTestHelper helper,ServerPlayer actor,BlockPos support,BlockPos target) throws Exception {
        var level=actor.level();var server=level.getServer();var client=ClientInformation.createDefault();
        var profile=new GameProfile(UUID.fromString("762fbfe0-89f7-4fd1-b266-cdc97bc85e7a"),"ControllerProbe");
        var human=(ServerPlayer)Class.forName("carpet.patches.EntityPlayerMPFake")
            .getMethod("respawnFake",net.minecraft.server.MinecraftServer.class,net.minecraft.server.level.ServerLevel.class,GameProfile.class,ClientInformation.class)
            .invoke(null,server,level,profile,client);
        var connection=(Connection)Class.forName("carpet.patches.FakeClientConnection").getConstructor(PacketFlow.class).newInstance(PacketFlow.SERVERBOUND);
        server.getPlayerList().placeNewPlayer(connection,human,new CommonListenerCookie(profile,0,client,false));
        human.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
        human.snapTo(actor.getX(),actor.getY(),actor.getZ(),0,0);
        var manager=EngineerMod.managerFor(server);var owner=UUID.randomUUID();
        var project=new EngineeringSession(manager,UUID.randomUUID(),owner,level.dimension().identifier().toString());
        manager.projects.put(project.projectId,project);
        try{
            project.access.member(owner,human.getUUID(),ProjectAccess.Role.EDITOR);project.commandActor=human.getUUID();
            project.corner1=target;project.corner2=target;
            var cells=new com.google.gson.JsonArray();var cell=new com.google.gson.JsonObject();var pos=new com.google.gson.JsonArray();pos.add(0);pos.add(0);pos.add(0);cell.add("pos",pos);cell.addProperty("state","minecraft:dirt");cells.add(cell);
            var candidate=SchematicBlueprint.candidate(cells,99,"a".repeat(64),"b".repeat(64),manager.worldId,project.dimension,project.projectId.toString());
            project.proposeImported(Blueprint.parse(candidate,manager.worldId,project.dimension),target,0);
            helper.assertTrue(!project.access.allows(actor.getUUID(),ProjectAccess.Permission.BUILD),"Physical actor receives no project membership or grant");
            var recordFile=manager.directory.resolve("physical-probe-"+UUID.randomUUID()+".json");
            for(String scenario:java.util.List.of("live","released","revoked","outside","disconnected")){
                level.setBlockAndUpdate(target,Blocks.AIR.defaultBlockState());
                var destination=scenario.equals("outside")?target.offset(2,0,0):target;
                var base=destination.below();level.setBlockAndUpdate(base,Blocks.STONE.defaultBlockState());level.setBlockAndUpdate(destination,Blocks.AIR.defaultBlockState());
                actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DIRT,1));
                project.claim(ProjectAccess.Permission.BUILD,false);
                PhysicalActionGate.arm(actor,"place",destination,Blocks.AIR.defaultBlockState(),server.getTickCount()+10,
                    ()->project.validateMutation(ProjectAccess.Permission.BUILD,destination),()->{
                        if(scenario.equals("live")){
                            PhysicalPlacementRecord.prepare(project,actor,destination,recordFile);
                            helper.assertTrue(PhysicalPlacementRecord.load(recordFile).status(project,actor)==PhysicalPlacementRecord.Status.BEFORE_MATCH,"Prepared disk record precedes native world/item mutation");
                        }
                    });
                if(scenario.equals("released"))manager.release(project);
                if(scenario.equals("revoked"))project.access.permission(owner,human.getUUID(),ProjectAccess.Permission.BUILD,false);
                if(scenario.equals("disconnected"))visibleLifecycle(helper,human,project);
                var hit=new net.minecraft.world.phys.BlockHitResult(new Vec3(base.getX()+0.5,base.getY()+1,base.getZ()+0.5),net.minecraft.core.Direction.UP,base,false);
                actor.gameMode.useItemOn(actor,level,actor.getMainHandItem(),InteractionHand.MAIN_HAND,hit);
                boolean live=scenario.equals("live");
                helper.assertTrue(level.getBlockState(destination).is(live?Blocks.DIRT:Blocks.AIR) && actor.getMainHandItem().getCount()==(live?0:1),"Actual project lease governs physical destination/items: "+scenario);
                manager.release(project);
                if(live){
                    var recovered=PhysicalPlacementRecord.load(recordFile);
                    helper.assertTrue(recovered.status(project,actor)==PhysicalPlacementRecord.Status.AFTER_MATCH && project.delegation==null,"Prepared record recovers matching native block/item facts without a lease");
                    recovered.observed(project,actor,recordFile);
                    helper.assertTrue(PhysicalPlacementRecord.load(recordFile).status(project,actor)==PhysicalPlacementRecord.Status.AFTER_MATCH,"Observed record survives a fresh disk read");
                    var controller=project.controller;project.controller=UUID.randomUUID();
                    helper.assertTrue(recovered.status(project,actor)==PhysicalPlacementRecord.Status.STALE,"Changed controller invalidates record scope without granting authority");project.controller=controller;
                    level.setBlockAndUpdate(destination,Blocks.COBBLESTONE.defaultBlockState());
                    helper.assertTrue(PhysicalPlacementRecord.load(recordFile).status(project,actor)==PhysicalPlacementRecord.Status.CONFLICT,"Later player edit is a conflict, never an authorized retry");
                    level.setBlockAndUpdate(destination,Blocks.DIRT.defaultBlockState());
                    var forged=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(recordFile)).getAsJsonObject();forged.addProperty("lease","invented");LocalFiles.write(recordFile,forged);
                    boolean refused=false;try{PhysicalPlacementRecord.load(recordFile);}catch(IllegalArgumentException invalid){refused=true;}
                    helper.assertTrue(refused,"Physical journal cannot import execution authority");
                }
                if(scenario.equals("revoked"))project.access.permission(owner,human.getUUID(),ProjectAccess.Permission.BUILD,true);
            }
            helper.assertTrue(!actor.isCreative(),"Physical actor remains in survival while human permission uses the unchanged core");
            System.out.println("ENGINEER_PHYSICAL_PROJECT_AUTHORITY live=placed released=denied revoked=denied outside-reservation=denied disconnected=denied actor-grants=none");
            System.out.println("ENGINEER_PHYSICAL_WAL prepared-before-write=true reloaded=after-match observed-reloaded=true player-edit=conflict forged-authority=denied restored-lease=none causal-proof=false");
        }finally{manager.release(project);manager.projects.remove(project.projectId);server.getPlayerList().remove(human);}
    }
    private static void visibleLifecycle(GameTestHelper helper,ServerPlayer owner,EngineeringSession editorProject){
        var manager=editorProject.manager;var level=owner.level();
        boolean denied=false;try{manager.physical.handle(editorProject,"summon my companion");}catch(IllegalStateException expected){denied=expected.getMessage().contains("owner");}
        helper.assertTrue(denied,"An editor cannot summon the owner's physical actor");
        var project=new EngineeringSession(manager,UUID.randomUUID(),owner.getUUID(),editorProject.dimension);
        var base=helper.absolutePos(BlockPos.ZERO);project.corner1=base;project.corner2=base.offset(2,2,2);
        manager.projects.put(project.projectId,project);manager.selections.put(owner.getUUID(),project.projectId);
        var before=new java.util.HashSet<UUID>();for(var p:manager.server.getPlayerList().getPlayers())before.add(p.getUUID());
        try{
            helper.assertTrue(manager.physical.handle(project,"summon my companion"),"Natural lifecycle request is handled without a model call");
            var actor=manager.server.getPlayerList().getPlayers().stream().filter(p->!before.contains(p.getUUID())).findFirst().orElseThrow();
            helper.assertTrue(actor.isAlive() && !actor.isCreative() && actor.getInventory().isEmpty() && PhysicalActionGate.managed(actor)
                && !project.access.allows(actor.getUUID(),ProjectAccess.Permission.BUILD),"Actual visible survival actor is empty and default-denied, without project grants");
            var target=base.offset(0,1,0);var support=target.below();actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DIRT));
            actor.gameMode.useItemOn(actor,level,actor.getMainHandItem(),InteractionHand.MAIN_HAND,new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(support).add(0,0.5,0),net.minecraft.core.Direction.UP,support,false));
            helper.assertTrue(level.getBlockState(target).isAir() && actor.getMainHandItem().getCount()==1,"Idle companion cannot place a block without a ticket");
            actor.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
            var drop=new net.minecraft.world.entity.item.ItemEntity(level,actor.getX(),actor.getY(),actor.getZ(),new ItemStack(Items.DIRT));level.addFreshEntity(drop);drop.playerTouch(actor);
            helper.assertTrue(actor.getInventory().isEmpty() && drop.getItem().getCount()==1,"Idle companion does not take a player's dropped materials");drop.discard();
            int xp=actor.totalExperience;actor.takeXpDelay=0;
            var orb=new net.minecraft.world.entity.ExperienceOrb(level,actor.getX(),actor.getY(),actor.getZ(),3);level.addFreshEntity(orb);orb.playerTouch(actor);
            helper.assertTrue(actor.totalExperience==xp && !orb.isRemoved(),"Idle companion does not consume a player's experience orbs");orb.discard();
            manager.physical.handle(project,"dismiss my companion");
            helper.assertTrue(manager.server.getPlayerList().getPlayer(actor.getUUID())==null && !PhysicalActionGate.managed(actor),"Dismiss removes actor before forgetting its deny gate");
            manager.physical.handle(project,"summon my companion");
            var second=manager.server.getPlayerList().getPlayers().stream().filter(p->!before.contains(p.getUUID())).findFirst().orElseThrow();
            manager.selections.remove(owner.getUUID());manager.physical.tick();
            helper.assertTrue(manager.server.getPlayerList().getPlayer(second.getUUID())==null,"Selecting away from the project removes the companion");
            manager.selections.put(owner.getUUID(),project.projectId);manager.physical.handle(project,"summon my companion");
            var third=manager.server.getPlayerList().getPlayers().stream().filter(p->!before.contains(p.getUUID())).findFirst().orElseThrow();
            project.access.member(owner.getUUID(),UUID.randomUUID(),ProjectAccess.Role.VIEWER);manager.physical.tick();
            helper.assertTrue(manager.server.getPlayerList().getPlayer(third.getUUID())==null,"Authorization revision change removes the physical actor");
            manager.physical.handle(project,"summon my companion");
            var fourth=manager.server.getPlayerList().getPlayers().stream().filter(p->!before.contains(p.getUUID())).findFirst().orElseThrow();
            manager.server.getPlayerList().remove(owner);manager.physical.tick();
            helper.assertTrue(manager.server.getPlayerList().getPlayer(fourth.getUUID())==null && !PhysicalActionGate.managed(fourth),"Actual controller removal despawns the companion and retires its deny registration");
            System.out.println("ENGINEER_PHYSICAL_LIFECYCLE owner=summoned editor=denied idle-actions=denied pickup=denied xp=denied dismissed=true selection-change=removed grant-change=removed disconnected=removed");
        }finally{manager.physical.close();manager.projects.remove(project.projectId);manager.selections.remove(owner.getUUID());}
    }
}
