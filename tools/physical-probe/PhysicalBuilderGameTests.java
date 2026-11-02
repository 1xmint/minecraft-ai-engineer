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
        Object pack=Class.forName("carpet.helpers.EntityPlayerActionPack").getConstructor(ServerPlayer.class).newInstance(actor);
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
        look(pack,Vec3.atCenterOf(placed));action(pack,"ATTACK","continuous");
        for(int tick=1;tick<=50;tick++)helper.runAtTickTime(tick,()->{try{update(pack);}catch(Exception failed){throw new RuntimeException(failed);}});
        helper.runAtTickTime(55,()->{
            try{
                stop(pack);
                helper.assertTrue(level.getBlockState(placed).isAir(),"Actual survival attack mines the placed dirt over native ticks");
                helper.assertTrue(level.getBlockState(support).is(Blocks.STONE),"Stopping attack retains the supporting block");
                System.out.println("ENGINEER_PHYSICAL_PROBE survival-placement=1 consumed=1 empty-refused=true reach-refused=true mined=true");
                server.getPlayerList().remove(actor);helper.succeed();
            }catch(Exception failed){server.getPlayerList().remove(actor);throw new RuntimeException(failed);}
        });
    }
}
