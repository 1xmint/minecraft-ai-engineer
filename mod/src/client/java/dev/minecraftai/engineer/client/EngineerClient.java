package dev.minecraftai.engineer.client;

import dev.minecraftai.engineer.EngineerMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/** Only immutable preview records cross from the integrated server to the client. */
public final class EngineerClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            EngineerMod.View view=current(client);if(view==null)return;
            try(var collection=client.collectPerTickGizmos()) {
                for(EngineerMod.Ghost ghost:view.blocks()) {
                    int color=ghost.wrong()?0xffff5555:ghost.matches()?0xff55dd88:ghost.currentStage()?0xff55ddff:0xff778899;
                    int fill=(ghost.currentStage()?0x33000000:0x11000000)|(color&0xffffff);
                    Gizmos.cuboid(ghost.pos(),0.003f,GizmoStyle.strokeAndFill(color,ghost.currentStage()?2f:1f,fill));
                    if(ghost.state().hasProperty(BlockStateProperties.HORIZONTAL_FACING)){
                        var facing=ghost.state().getValue(BlockStateProperties.HORIZONTAL_FACING);
                        Vec3 center=Vec3.atCenterOf(ghost.pos());
                        Gizmos.arrow(center,center.add(facing.getStepX()*.45,0,facing.getStepZ()*.45),color,2f);
                    }
                    if(ghost.currentStage()&&!ghost.matches()) Gizmos.billboardTextOverBlock(ghost.state().getBlock().getName().getString(),ghost.pos(),color,0,0.018f);
                }
            }
        });
        HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT,Identifier.fromNamespaceAndPath("engineer","tutorial"),(graphics,delta)->{
            Minecraft client=Minecraft.getInstance();EngineerMod.View view=current(client);if(view==null)return;
            graphics.text(client.font,view.text(),8,8,0xff55ddff,true);
            graphics.text(client.font,"Cyan: current step | Green: correct | Red: fix block/orientation | /engineer",8,20,0xffdddddd,true);
        });
    }
    private static EngineerMod.View current(Minecraft client){
        EngineerMod.View view=EngineerMod.preview;
        return client.player!=null && client.level!=null && client.hasSingleplayerServer() && view!=null
            && view.owner().equals(client.player.getUUID()) && view.dimension().equals(client.level.dimension().identifier().toString()) ? view:null;
    }
}
