package dev.minecraftai.engineer;

import com.google.gson.JsonObject;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

public final class EngineerMod implements ModInitializer {
    private static final Map<String,EngineeringSession> SESSIONS = new HashMap<>();
    private static final Map<UUID,CompletableFuture<?>> PLANS = new HashMap<>();
    public record Ghost(BlockPos pos, BlockState state, boolean currentStage, boolean matches, boolean wrong) {}
    public record View(UUID owner,String dimension,List<Ghost> blocks,String text) {}
    public static volatile View preview;

    @Override public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher,registry,environment)->dispatcher.register(
            Commands.literal("engineer")
                .executes(ctx->run(ctx.getSource(),s->s.tell("select1/select2 [x y z], inspect, reference, plan <request>, rotate <0|90|180|270>, teach, next, previous, repeat, skip, pause, build, stop, resume, undo, test, status")))
                .then(Commands.literal("help").executes(ctx->run(ctx.getSource(),s->s.tell("Select two corners, plan or reference, follow the preview, then build to delegate. stop, resume, undo, test, status. See the launch guide for the full flow."))))
                .then(Commands.literal("select1").executes(ctx->run(ctx.getSource(),s->s.select(1,s.player().blockPosition())))
                    .then(Commands.argument("position",BlockPosArgument.blockPos()).executes(ctx->run(ctx.getSource(),s->s.select(1,BlockPosArgument.getBlockPos(ctx,"position"))))))
                .then(Commands.literal("select2").executes(ctx->run(ctx.getSource(),s->s.select(2,s.player().blockPosition())))
                    .then(Commands.argument("position",BlockPosArgument.blockPos()).executes(ctx->run(ctx.getSource(),s->s.select(2,BlockPosArgument.getBlockPos(ctx,"position"))))))
                .then(Commands.literal("inspect").executes(ctx->run(ctx.getSource(),EngineeringSession::inspect)))
                .then(Commands.literal("reference").executes(ctx->run(ctx.getSource(),s->s.accept(Blueprint.reference(s.worldId,s.dimension,s.anchor(),0)))))
                .then(Commands.literal("plan").then(Commands.argument("request",StringArgumentType.greedyString()).executes(ctx->run(ctx.getSource(),s->plan(s,StringArgumentType.getString(ctx,"request"))))))
                .then(Commands.literal("rotate").then(Commands.argument("degrees",IntegerArgumentType.integer(0,270)).executes(ctx->run(ctx.getSource(),s->{ if(s.blueprint==null)throw new IllegalStateException("No blueprint");s.accept(s.blueprint.transformed(s.blueprint.anchor(),IntegerArgumentType.getInteger(ctx,"degrees")));}))))
                .then(Commands.literal("anchor").then(Commands.argument("position",BlockPosArgument.blockPos()).executes(ctx->run(ctx.getSource(),s->{if(s.blueprint==null)throw new IllegalStateException("No blueprint");s.accept(s.blueprint.transformed(BlockPosArgument.getBlockPos(ctx,"position"),s.blueprint.rotation()));}))))
                .then(Commands.literal("teach").executes(ctx->run(ctx.getSource(),s->{s.teaching=true;s.explain();})))
                .then(Commands.literal("next").executes(ctx->run(ctx.getSource(),s->s.moveStage(1))))
                .then(Commands.literal("skip").executes(ctx->run(ctx.getSource(),s->s.moveStage(1))))
                .then(Commands.literal("previous").executes(ctx->run(ctx.getSource(),s->s.moveStage(-1))))
                .then(Commands.literal("repeat").executes(ctx->run(ctx.getSource(),EngineeringSession::explain)))
                .then(Commands.literal("pause").executes(ctx->run(ctx.getSource(),s->stop(s,"Paused by player"))))
                .then(Commands.literal("stop").executes(ctx->run(ctx.getSource(),s->stop(s,"Emergency stop requested by player"))))
                .then(Commands.literal("build").executes(ctx->run(ctx.getSource(),EngineeringSession::build)))
                .then(Commands.literal("resume").executes(ctx->run(ctx.getSource(),EngineeringSession::resume)))
                .then(Commands.literal("undo").executes(ctx->run(ctx.getSource(),EngineeringSession::undo)))
                .then(Commands.literal("test").executes(ctx->run(ctx.getSource(),EngineeringSession::test)))
                .then(Commands.literal("status").executes(ctx->run(ctx.getSource(),s->s.tell(s.status()))))
        ));
        ServerTickEvents.END_SERVER_TICK.register(server->{
            for(EngineeringSession session:SESSIONS.values()) {session.tick();publish(session);}
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server->{
            PLANS.values().forEach(p->p.cancel(true));PLANS.clear();
            SESSIONS.values().forEach(s->s.pause("Minecraft is closing; explicit reconciliation required on restart"));
            SESSIONS.clear();preview=null;
        });
    }
    private static int run(CommandSourceStack source,Consumer<EngineeringSession> action) {
        try {
            ServerPlayer p=source.getPlayerOrException();
            if(!source.getServer().isSingleplayer() || source.getServer().isPublished() || source.getServer().getPlayerList().getPlayerCount()!=1) throw new IllegalStateException("Use a local single-player world; remote and LAN worlds are unsupported");
            String key=p.getUUID()+":"+p.level().dimension().identifier();
            EngineeringSession session=SESSIONS.computeIfAbsent(key,k->new EngineeringSession(source.getServer(),p));
            action.accept(session);publish(session);return Command.SINGLE_SUCCESS;
        }catch(Exception e){source.sendFailure(Component.literal("Engineer: "+safe(e)));return 0;}
    }
    private static String safe(Throwable error) {
        Throwable t=error;while(t.getCause()!=null)t=t.getCause();
        return t.getMessage()==null ? "Operation failed; inspect local logs" : t.getMessage().substring(0,Math.min(350,t.getMessage().length()));
    }
    private static void stop(EngineeringSession session,String reason) {
        CompletableFuture<?> plan=PLANS.remove(session.owner);if(plan!=null)plan.cancel(true);session.pause(reason);
    }
    private static void plan(EngineeringSession s,String request) {
        if(PLANS.containsKey(s.owner))throw new IllegalStateException("A planning request is already active; stop it first");
        if(s.state.equals("EXECUTING") || s.testTick>=0 || s.testOriginal!=null || !s.journal.isEmpty())throw new IllegalStateException("Stop, reconcile interrupted tests, and undo existing assistant work before replacing the plan");
        JsonObject body=new JsonObject();body.addProperty("request",request);body.addProperty("worldId",s.worldId);body.addProperty("dimension",s.dimension);body.add("anchor",Blueprint.jsonPos(s.anchor()));body.addProperty("rotation",0);
        if(s.blueprint!=null)body.addProperty("projectId",s.blueprint.projectId());
        JsonObject size=new JsonObject();size.addProperty("x",Math.abs(s.corner1.getX()-s.corner2.getX())+1);size.addProperty("y",Math.abs(s.corner1.getY()-s.corner2.getY())+1);size.addProperty("z",Math.abs(s.corner1.getZ()-s.corner2.getZ())+1);body.add("maxSize",size);
        s.state="PLANNING";s.detail="One bounded Codex request; /engineer stop cancels local planning";s.save();s.tell(s.detail);
        CompletableFuture<JsonObject> future=Companion.post("/v1/design",body);PLANS.put(s.owner,future);
        future.whenComplete((result,error)->s.server.execute(()->{
            if(PLANS.get(s.owner)!=future)return;PLANS.remove(s.owner);
            if(!s.available()){s.pause("World changed while planning; request discarded");return;}
            if(error!=null){s.state="BLOCKED";s.detail=safe(error);s.save();s.tell(s.detail);return;}
            try{s.state="READY";s.accept(Blueprint.parse(result.getAsJsonObject("blueprint"),s.worldId,s.dimension));s.tell(result.get("message").getAsString());}
            catch(Exception e){s.state="BLOCKED";s.detail="Rejected blueprint: "+safe(e);s.save();s.tell(s.detail);}
        }));
    }
    private static void publish(EngineeringSession session) {
        if(!session.available() || session.blueprint==null){if(preview!=null && preview.owner().equals(session.owner) && preview.dimension().equals(session.dimension))preview=null;return;}
        String stage=session.blueprint.stages().get(session.stage).id();List<Ghost> blocks=new ArrayList<>();
        for(Blueprint.PlannedBlock b:session.blueprint.blocks()){
            boolean loaded=session.player().level().hasChunkAt(b.pos());BlockState actual=loaded?session.player().level().getBlockState(b.pos()):null;
            boolean match=loaded && Blueprint.matches(b.state(),actual);
            blocks.add(new Ghost(b.pos(),b.state(),b.stage().equals(stage),match,loaded && !actual.isAir() && !match));
        }
        preview=new View(session.owner,session.dimension,List.copyOf(blocks),session.blueprint.name()+" | "+session.blueprint.stages().get(session.stage).title()+" | "+session.state);
    }
}
