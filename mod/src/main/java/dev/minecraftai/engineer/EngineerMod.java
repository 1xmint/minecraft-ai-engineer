package dev.minecraftai.engineer;

import com.google.gson.JsonObject;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** The Minecraft server is the only entry point for player identity and project authority. */
public final class EngineerMod implements ModInitializer {
    private static final Map<MinecraftServer,ProjectManager> MANAGERS = new IdentityHashMap<>();
    private static final Map<EngineeringSession,PlanRequest> PLANS = new IdentityHashMap<>();
    private record PlanRequest(UUID actor, UUID epoch, int revision, long authorization, CompletableFuture<JsonObject> future) { }
    private static final String HELP = "project new/list/use/share/revoke/permission, select1/select2 [x y z], inspect, reference, plan <request>, rotate, anchor, teach, next, previous, repeat, skip, pause, build, stop, resume, undo, test, status";

    @Override public void onInitialize() {
        EngineerNetwork.initialize();
        CommandRegistrationCallback.EVENT.register((dispatcher,registry,environment)->dispatcher.register(
            Commands.literal("engineer")
                .executes(ctx->help(ctx.getSource()))
                .then(Commands.literal("help").executes(ctx->help(ctx.getSource())))
                .then(Commands.literal("project")
                    .then(Commands.literal("new").executes(ctx->withManager(ctx.getSource(),(m,p)->{var s=m.create(p);p.sendSystemMessage(Component.literal("Engineer: Created private project "+s.projectId));})))
                    .then(Commands.literal("list").executes(ctx->withManager(ctx.getSource(),(m,p)->{
                        for(var s:m.projects.values())if(s.access.allows(p.getUUID(),ProjectAccess.Permission.VIEW))p.sendSystemMessage(Component.literal("Engineer: "+s.projectId+" | "+(s.blueprint==null?"New project":s.blueprint.name())+" | "+s.state));
                    })))
                    .then(Commands.literal("use").then(Commands.argument("project",StringArgumentType.word()).executes(ctx->withManager(ctx.getSource(),(m,p)->m.use(p,UUID.fromString(StringArgumentType.getString(ctx,"project")))))))
                    .then(Commands.literal("share").then(Commands.argument("player",StringArgumentType.word()).then(Commands.argument("role",StringArgumentType.word()).executes(ctx->run(ctx.getSource(),ProjectAccess.Permission.VIEW,s->{
                        var role=ProjectAccess.Role.valueOf(StringArgumentType.getString(ctx,"role").toUpperCase(Locale.ROOT));
                        s.access.member(s.actor(),target(s.server,StringArgumentType.getString(ctx,"player")),role);s.manager.changed(s);s.tell("Project membership updated");
                    })))))
                    .then(Commands.literal("revoke").then(Commands.argument("player",StringArgumentType.word()).executes(ctx->run(ctx.getSource(),ProjectAccess.Permission.VIEW,s->{
                        s.access.revoke(s.actor(),target(s.server,StringArgumentType.getString(ctx,"player")));s.manager.changed(s);s.tell("Project access revoked; old delegations invalidated");
                    }))))
                    .then(Commands.literal("permission").then(Commands.argument("player",StringArgumentType.word()).then(Commands.argument("permission",StringArgumentType.word()).then(Commands.argument("allowed",BoolArgumentType.bool()).executes(ctx->run(ctx.getSource(),ProjectAccess.Permission.VIEW,s->{
                        var permission=ProjectAccess.Permission.valueOf(StringArgumentType.getString(ctx,"permission").toUpperCase(Locale.ROOT));
                        s.access.permission(s.actor(),target(s.server,StringArgumentType.getString(ctx,"player")),permission,BoolArgumentType.getBool(ctx,"allowed"));s.manager.changed(s);s.tell("Project rights updated");
                    })))))))
                .then(Commands.literal("permit").then(Commands.argument("player",StringArgumentType.word()).executes(ctx->withManager(ctx.getSource(),(m,p)->{m.permit(p,target(m.server,StringArgumentType.getString(ctx,"player")));p.sendSystemMessage(Component.literal("Engineer: Project creation permitted"));}))))
                .then(Commands.literal("stopall").executes(ctx->withManager(ctx.getSource(),(m,p)->{if(!m.operator(p))throw new IllegalStateException("Only operators can stop all projects");for(var s:m.projects.values()){cancelPlan(s);s.pause("Emergency stop by server operator");}})))
                .then(Commands.literal("world").then(Commands.literal("fork").executes(ctx->fork(ctx.getSource()))))
                .then(Commands.literal("select1").executes(ctx->run(ctx.getSource(),ProjectAccess.Permission.EDIT,s->s.select(1,s.player().blockPosition())))
                    .then(Commands.argument("position",BlockPosArgument.blockPos()).executes(ctx->run(ctx.getSource(),ProjectAccess.Permission.EDIT,s->s.select(1,BlockPosArgument.getBlockPos(ctx,"position"))))))
                .then(Commands.literal("select2").executes(ctx->run(ctx.getSource(),ProjectAccess.Permission.EDIT,s->s.select(2,s.player().blockPosition())))
                    .then(Commands.argument("position",BlockPosArgument.blockPos()).executes(ctx->run(ctx.getSource(),ProjectAccess.Permission.EDIT,s->s.select(2,BlockPosArgument.getBlockPos(ctx,"position"))))))
                .then(Commands.literal("inspect").executes(ctx->run(ctx.getSource(),ProjectAccess.Permission.VIEW,EngineeringSession::inspect)))
                .then(Commands.literal("reference").executes(ctx->run(ctx.getSource(),ProjectAccess.Permission.EDIT,s->s.accept(Blueprint.reference(s.worldId,s.dimension,s.anchor(),0)))))
                .then(Commands.literal("plan").then(Commands.argument("request",StringArgumentType.greedyString()).executes(ctx->run(ctx.getSource(),ProjectAccess.Permission.PLAN,s->plan(s,StringArgumentType.getString(ctx,"request"))))))
                .then(Commands.literal("rotate").then(Commands.argument("degrees",IntegerArgumentType.integer(0,270)).executes(ctx->run(ctx.getSource(),ProjectAccess.Permission.EDIT,s->{if(s.blueprint==null)throw new IllegalStateException("No blueprint");s.accept(s.blueprint.transformed(s.blueprint.anchor(),IntegerArgumentType.getInteger(ctx,"degrees")));}))))
                .then(Commands.literal("anchor").then(Commands.argument("position",BlockPosArgument.blockPos()).executes(ctx->run(ctx.getSource(),ProjectAccess.Permission.EDIT,s->{if(!s.owner.equals(s.actor()))throw new IllegalStateException("Only the project owner can move its workspace");if(s.blueprint==null)throw new IllegalStateException("No blueprint");s.accept(s.blueprint.transformed(BlockPosArgument.getBlockPos(ctx,"position"),s.blueprint.rotation()));}))))
                .then(Commands.literal("teach").executes(ctx->run(ctx.getSource(),ProjectAccess.Permission.VIEW,s->{s.cursor(s.actor()).teaching=true;s.save();s.explain();})))
                .then(Commands.literal("next").executes(ctx->run(ctx.getSource(),ProjectAccess.Permission.VIEW,s->s.moveStage(1))))
                .then(Commands.literal("skip").executes(ctx->run(ctx.getSource(),ProjectAccess.Permission.VIEW,s->s.moveStage(1))))
                .then(Commands.literal("previous").executes(ctx->run(ctx.getSource(),ProjectAccess.Permission.VIEW,s->s.moveStage(-1))))
                .then(Commands.literal("repeat").executes(ctx->run(ctx.getSource(),ProjectAccess.Permission.VIEW,EngineeringSession::explain)))
                .then(Commands.literal("pause").executes(ctx->run(ctx.getSource(),ProjectAccess.Permission.VIEW,s->{s.cursor(s.actor()).teaching=false;s.save();s.tell("Your tutorial is paused; stop pauses project execution");})))
                .then(Commands.literal("stop").executes(ctx->run(ctx.getSource(),ProjectAccess.Permission.BUILD,s->{cancelPlan(s);s.pause("Project stopped by an authorized player");})))
                .then(Commands.literal("build").executes(ctx->run(ctx.getSource(),ProjectAccess.Permission.BUILD,EngineeringSession::build)))
                .then(Commands.literal("resume").executes(ctx->run(ctx.getSource(),ProjectAccess.Permission.BUILD,EngineeringSession::resume)))
                .then(Commands.literal("undo").executes(ctx->run(ctx.getSource(),ProjectAccess.Permission.UNDO,EngineeringSession::undo)))
                .then(Commands.literal("test").executes(ctx->run(ctx.getSource(),ProjectAccess.Permission.BUILD,EngineeringSession::test)))
                .then(Commands.literal("status").executes(ctx->run(ctx.getSource(),ProjectAccess.Permission.VIEW,s->s.tell("Project "+s.projectId+" | "+s.status()))))
        ));
        ServerTickEvents.END_SERVER_TICK.register(server->{
            for(var entry:new ArrayList<>(PLANS.entrySet())) {
                var s=entry.getKey();var request=entry.getValue();if(s.server!=server)continue;
                ServerPlayer actor=server.getPlayerList().getPlayer(request.actor());
                if(actor==null || !s.manager.operator(actor) || s.revision()!=request.revision() || s.access.revision()!=request.authorization()) {
                    cancelPlan(s);s.pause("Planning controller disconnected or lost authority; request cancelled");
                }
            }
            ProjectManager manager=MANAGERS.get(server);if(manager!=null)manager.tick();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server->{
            ProjectManager manager=MANAGERS.remove(server);
            if(manager!=null){for(var s:manager.projects.values())cancelPlan(s);manager.close();}
            EngineerNetwork.close();
        });
    }
    static ProjectManager managerFor(MinecraftServer server) { return MANAGERS.computeIfAbsent(server,ProjectManager::new); }
    static EngineeringSession sessionFor(ServerPlayer player) { return managerFor(player.level().getServer()).selected(player,false); }
    private static int help(CommandSourceStack source) { source.sendSuccess(()->Component.literal("Engineer: "+HELP),false);return Command.SINGLE_SUCCESS; }
    private static int withManager(CommandSourceStack source,BiConsumer<ProjectManager,ServerPlayer> action) {
        try {ServerPlayer p=source.getPlayerOrException();action.accept(managerFor(source.getServer()),p);return Command.SINGLE_SUCCESS;}
        catch(Exception error){source.sendFailure(Component.literal("Engineer: "+safe(error)));return 0;}
    }
    private static int run(CommandSourceStack source,ProjectAccess.Permission permission,Consumer<EngineeringSession> action) {
        EngineeringSession project=null;
        try {
            ServerPlayer player=source.getPlayerOrException();ProjectManager manager=managerFor(source.getServer());
            project=manager.selected(player,true);project.access.require(player.getUUID(),permission);
            project.commandActor=player.getUUID();action.accept(project);return Command.SINGLE_SUCCESS;
        } catch(Exception error){source.sendFailure(Component.literal("Engineer: "+safe(error)));return 0;}
        finally {if(project!=null)project.commandActor=null;}
    }
    private static UUID target(MinecraftServer server,String value) {
        ServerPlayer player=server.getPlayerList().getPlayerByName(value);
        if(player!=null)return player.getUUID();
        try{return UUID.fromString(value);}catch(IllegalArgumentException invalid){throw new IllegalArgumentException("Choose an online player name or an exact player UUID");}
    }
    private static int fork(CommandSourceStack source) {
        try {
            ServerPlayer p=source.getPlayerOrException();MinecraftServer server=source.getServer();
            if(!server.getPlayerList().isOp(p.nameAndId()) && !(server.isSingleplayer()&&server.isSingleplayerOwner(p.nameAndId())))throw new IllegalStateException("Only the server operator can fork a world identity");
            ProjectManager current=MANAGERS.get(server);
            if(current!=null && !current.projects.isEmpty())throw new IllegalStateException("Fork only a newly copied world before adopting projects; existing server projects need explicit migration");
            LocalFiles.forkWorld(server);if(current!=null){current.close();MANAGERS.remove(server);}
            source.sendSuccess(()->Component.literal("Engineer: World identity forked; the previous identity was backed up. Old projects were not granted authority."),false);return 1;
        }catch(Exception error){source.sendFailure(Component.literal("Engineer: "+safe(error)));return 0;}
    }
    static void cancelPlan(EngineeringSession session) {var plan=PLANS.remove(session);if(plan!=null)plan.future().cancel(true);}
    private static String safe(Throwable error) {
        Throwable t=error;while(t.getCause()!=null)t=t.getCause();
        String message=t.getMessage()==null?"Operation failed; inspect local logs":t.getMessage();return message.substring(0,Math.min(350,message.length()));
    }
    private static void plan(EngineeringSession s,String request) {
        if(!s.manager.operator(s.player()) || !s.owner.equals(s.actor()))throw new IllegalStateException("Planning currently requires the project owner to be a server operator; shared subscription access is not enabled");
        if(PLANS.containsKey(s))throw new IllegalStateException("Planning is active; stop it first");
        if(s.state.equals("EXECUTING") || s.testTick>=0 || s.testOriginal!=null || !s.journal.isEmpty())throw new IllegalStateException("Stop and reconcile existing work before replacing the plan");
        UUID actor=s.actor();int revision=s.revision();long authorization=s.access.revision();
        JsonObject body=new JsonObject();body.addProperty("request",request);body.addProperty("worldId",s.worldId);body.addProperty("dimension",s.dimension);body.add("anchor",Blueprint.jsonPos(s.anchor()));body.addProperty("rotation",0);body.addProperty("projectId",s.projectId.toString());
        JsonObject size=new JsonObject();size.addProperty("x",Math.abs(s.corner1.getX()-s.corner2.getX())+1);size.addProperty("y",Math.abs(s.corner1.getY()-s.corner2.getY())+1);size.addProperty("z",Math.abs(s.corner1.getZ()-s.corner2.getZ())+1);body.add("maxSize",size);
        s.state="PLANNING";s.detail="One bounded operator-owned planning request";s.save();s.tell(s.detail);
        CompletableFuture<JsonObject> future=Companion.post("/v1/design",body);
        var pending=new PlanRequest(actor,s.manager.epoch,revision,authorization,future);PLANS.put(s,pending);
        future.whenComplete((result,error)->s.server.execute(()->{
            if(PLANS.get(s)!=pending)return;PLANS.remove(s);
            if(MANAGERS.get(s.server)!=s.manager || !pending.epoch().equals(s.manager.epoch) || s.revision()!=pending.revision()
                    || s.access.revision()!=pending.authorization() || !s.access.allows(actor,ProjectAccess.Permission.PLAN)
                    || s.server.getPlayerList().getPlayer(actor)==null || !s.manager.operator(s.server.getPlayerList().getPlayer(actor))){s.pause("Planning authority changed; late result discarded");return;}
            s.commandActor=actor;
            try {
                if(error!=null)throw new IllegalStateException(safe(error));
                s.state="READY";s.accept(Blueprint.parse(result.getAsJsonObject("blueprint"),s.worldId,s.dimension));s.tell(result.get("message").getAsString());
            }catch(Exception rejected){s.state="BLOCKED";s.detail="Plan unavailable or rejected: "+safe(rejected);s.save();s.tell(s.detail);}
            finally{s.commandActor=null;}
        }));
    }
}
