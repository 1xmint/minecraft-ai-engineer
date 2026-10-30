package dev.minecraftai.engineer.client;

import dev.minecraftai.engineer.PreviewSelection;
import java.util.*;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.level.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** Read-only rendering of immutable, server-approved intention. No fake world blocks. */
public final class GhostPreviewRenderer {
    private record Model(BlockPos pos,List<BlockStateModelPart> parts,int color){
        Model{pos=pos.immutable();parts=List.copyOf(parts);}
    }
    private record Frame(Vec3 camera,List<Model> models){Frame{models=List.copyOf(models);}}
    private static final Frame EMPTY=new Frame(Vec3.ZERO,List.of());
    private static final RenderStateDataKey<Frame> FRAME=RenderStateDataKey.create(()->"engineer:ghost-preview");
    private static volatile int extractedModels,submittedModels;
    private GhostPreviewRenderer() { }
    static void initialize(){
        LevelExtractionEvents.END_EXTRACTION.register(GhostPreviewRenderer::extract);
        LevelRenderEvents.COLLECT_SUBMITS.register(GhostPreviewRenderer::submit);
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{extractedModels=0;submittedModels=0;});
    }
    private static void extract(LevelExtractionContext context){
        var target=(FabricRenderState)context.levelState();var view=ClientPreview.renderInput();
        if(view==null || !view.dimension().equals(context.level().dimension().identifier().toString())){
            target.setData(FRAME,EMPTY);extractedModels=0;return;
        }
        var camera=context.camera().position();var models=new ArrayList<Model>();
        var modelSet=Minecraft.getInstance().getModelManager().getBlockStateModelSet();
        for(var ghost:PreviewSelection.nearby(view,camera)){
            if(ghost.matches() || ghost.state().isAir())continue;
            var parts=new ArrayList<BlockStateModelPart>();
            modelSet.get(ghost.state()).collectParts(RandomSource.create(ghost.state().getSeed(ghost.pos())),parts);
            if(!parts.isEmpty())models.add(new Model(ghost.pos(),parts,ghost.currentStage()?0x80ffffff:0x55ffffff));
        }
        target.setData(FRAME,new Frame(camera,models));extractedModels=models.size();
    }
    private static void submit(LevelRenderContext context){
        var frame=((FabricRenderState)context.levelState()).getDataOrDefault(FRAME,EMPTY);
        var poses=context.poseStack();submittedModels=0;
        for(var model:frame.models()){
            poses.pushPose();
            poses.translate(model.pos().getX()-frame.camera().x,model.pos().getY()-frame.camera().y,model.pos().getZ()-frame.camera().z);
            // submitBlockModel's last argument is an outline color: its body forces opaque white.
            // Feed the same baked vanilla quads through the native geometry submission with alpha.
            context.submitNodeCollector().submitCustomGeometry(poses,RenderTypes.translucentMovingBlock(),(pose,vertices)->{
                var instance=new com.mojang.blaze3d.vertex.QuadInstance();
                instance.setColor(model.color());instance.setLightCoords(LightCoordsUtil.FULL_BRIGHT);instance.setOverlayCoords(OverlayTexture.NO_OVERLAY);
                for(var part:model.parts()){
                    for(var direction:net.minecraft.core.Direction.values())for(var quad:part.getQuads(direction))vertices.putBakedQuad(pose,quad,instance);
                    for(var quad:part.getQuads(null))vertices.putBakedQuad(pose,quad,instance);
                }
            });
            poses.popPose();submittedModels++;
        }
    }
    /** Diagnostic counters for native visual acceptance, never world or permission state. */
    public static int extractedModelCount(){return extractedModels;}
    public static int submittedModelCount(){return submittedModels;}
}
