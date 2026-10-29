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
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
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
    private record PlanRequest(UUID actor, UUID epoch, int revision, long authorization, net.minecraft.server.network.ServerGamePacketListenerImpl connection, CompletableFuture<JsonObject> future, String previousState) { }
    private static final String HELP = "project new/list/use/share/revoke/permission, select1/select2 [x y z], expand <x1 y1 z1> <x2 y2 z2>, component add/group/depend/list/info/claim/build/diagnose/repair/revise/test, inspect [block <x y z>|view|inventory|container], materials [component], diagnose, repair, revise <delay>, reference, plan <request>, design <request>, confirm <nonce>, rotate, anchor, teach [component], ask <lesson question>, next, previous, repeat, skip, pause, build, stop, resume, undo, test, status";

    @Override public void onInitialize() {
        EngineerNetwork.initialize();
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{
            ProjectManager manager=MANAGERS.get(server);if(manager==null)return;
            for(var project:manager.projects.values()) {
                project.forgetDiagnosis(handler.player.getUUID());
                manager.generated.cancelActor(project,handler.player.getUUID());
                if(project.controller.equals(handler.player.getUUID())
                    && (project.state.equals("EXECUTING") || project.state.equals("PLANNING") || project.testTick>=0)) {
                cancelPlan(project);manager.reservations.revoke(manager.scope(project));project.delegation=null;project.delegationConnection=null;
                project.pause("Controller disconnected; another authorized connection must explicitly resume");
            }
            }
            manager.selections.remove(handler.player.getUUID());
        });
        CommandRegistrationCallback.EVENT.register((dispatcher,registry,environment)->dispatcher.register(
            Commands.literal("engineer")
                .executes(ctx->help(ctx.getSource()))
                .then(Commands.literal("help").executes(ctx->help(ctx.getSource())))
                .then(Commands.literal("catalog").executes(ctx->{ctx.getSource().sendSuccess(()->Component.literal("Engineer mechanisms: "+String.join(", ",MechanismCatalog.ids())+". Use reference <mechanism> [delay]; repeater delay is 1–4 redstone ticks, inverter uses 1."),false);return 1;}))
                .then(Commands.literal("project")
                    .then(Commands.literal("adopt-legacy").executes(ctx->withManager(ctx.getSource(),(m,p)->{
                        var adopted=LegacyAdoption.adopt(m,p);p.sendSystemMessage(Component.literal("Engineer: Legacy project backed up and adopted privately as "+adopted.projectId+". Inspect before explicitly resuming or undoing."));
                    })))
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
                .then(Commands.literal("expand").then(Commands.argument("first",BlockPosArgument.blockPos()).then(Commands.argument("second",BlockPosArgument.blockPos()).executes(ctx->run(ctx.getSource(),ProjectAccess.Permission.EDIT,s->s.expandWorkspace(BlockPosArgument.getBlockPos(ctx,"first"),BlockPosArgument.getBlockPos(ctx,"second")),false)))))
                .then(Commands.literal("component")
                    .then(Commands.literal("list").executes(ctx->run(ctx.getSource(),ProjectAccess.Permission.VIEW,EngineeringSession::componentList)))
                    .then(Commands.literal("info").then(Commands.argument("component",StringArgumentType.word()).executes(ctx->run(ctx.getSource(),ProjectAccess.Permission.VIEW,s->s.componentInfo(StringArgumentType.getString(ctx,"component"))))))
                    .then(Commands.literal("diagnose").then(Commands.argument("component",StringArgumentType.word()).executes(ctx->run(ctx.getSource(),ProjectAccess.Permission.VIEW,s->s.componentDiagnose(StringArgumentType.getString(ctx,"component"))))))
                    .then(Commands.literal("repair").then(Commands.argument("component",StringArgumentType.word()).executes(ctx->run(ctx.getSource(),ProjectAccess.Permission.BUILD,s->s.componentRepair(StringArgumentType.getString(ctx,"component"))))))
                    .then(Commands.literal("test").then(Commands.argument("component",StringArgumentType.word()).executes(ctx->run(ctx.getSource(),ProjectAccess.Permission.BUILD,s->s.componentTest(StringArgumentType.getString(ctx,"component"))))))
                    .then(Commands.literal("revise").then(Commands.argument("component",StringArgumentType.word()).then(Commands.argument("delay",IntegerArgumentType.integer(1,4)).executes(ctx->run(ctx.getSource(),ProjectAccess.Permission.EDIT,s->s.componentRevise(StringArgumentType.getString(ctx,"component"),IntegerArgumentType.getInteger(ctx,"delay")))))))
                    .then(Commands.literal("claim").then(Commands.argument("component",StringArgumentType.word()).executes(ctx->run(ctx.getSource(),ProjectAccess.Permission.BUILD,s->s.componentClaim(StringArgumentType.getString(ctx,"component"))))))
                    .then(Commands.literal("build").then(Commands.argument("component",StringArgumentType.word()).executes(ctx->run(ctx.getSource(),ProjectAccess.Permission.BUILD,s->s.componentBuild(StringArgumentType.getString(ctx,"component"))))))
                    .then(Commands.literal("group").then(Commands.argument("id",StringArgumentType.word()).then(Commands.argument("parent",StringArgumentType.word()).executes(ctx->run(ctx.getSource(),ProjectAccess.Permission.EDIT,s->s.componentAdd(StringArgumentType.getString(ctx,"id"),StringArgumentType.getString(ctx,"parent"),null,null))))))
                    .then(Commands.literal("depend").then(Commands.argument("component",StringArgumentType.word()).then(Commands.argument("prerequisite",StringArgumentType.word()).executes(ctx->run(ctx.getSource(),ProjectAccess.Permission.EDIT,s->s.componentDepend(StringArgumentType.getString(ctx,"component"),StringArgumentType.getString(ctx,"prerequisite")))))))
                    .then(Commands.literal("add").then(Commands.argument("id",StringArgumentType.word()).then(Commands.argument("parent",StringArgumentType.word()).then(Commands.argument("mechanism",StringArgumentType.word()).then(Commands.argument("position",BlockPosArgument.blockPos()).executes(ctx->run(ctx.getSource(),ProjectAccess.Permission.EDIT,s->s.componentAdd(StringArgumentType.getString(ctx,"id"),StringArgumentType.getString(ctx,"parent"),StringArgumentType.getString(ctx,"mechanism"),BlockPosArgument.getBlockPos(ctx,"position"))))))))))
                .then(Commands.literal("inspect").executes(ctx->run(ctx.getSource(),ProjectAccess.Permission.VIEW,EngineeringSession::inspect))
                    .then(Commands.literal("inventory").executes(ctx->run(ctx.getSource(),ProjectAccess.Permission.VIEW,s->s.manager.inventories.own(s))))
                    .then(Commands.literal("container").executes(ctx->run(ctx.getSource(),ProjectAccess.Permission.VIEW,s->s.manager.inventories.container(s))))
                    .then(Commands.literal("block").then(Commands.argument("position",BlockPosArgument.blockPos()).executes(ctx->run(ctx.getSource(),ProjectAccess.Permission.VIEW,s->WorldPerception.block(s,BlockPosArgument.getBlockPos(ctx,"position"))))))
                    .then(Commands.literal("view").executes(ctx->run(ctx.getSource(),ProjectAccess.Permission.VIEW,WorldPerception::view))))
                .then(Commands.literal("materials").executes(ctx->run(ctx.getSource(),ProjectAccess.Permission.VIEW,s->s.manager.inventories.materials(s)))
                    .then(Commands.argument("component",StringArgumentType.word()).executes(ctx->run(ctx.getSource(),ProjectAccess.Permission.VIEW,s->s.manager.inventories.materials(s,StringArgumentType.getString(ctx,"component"))))))
                .then(Commands.literal("diagnose").executes(ctx->run(ctx.getSource(),ProjectAccess.Permission.VIEW,EngineeringSession::diagnose)))
                .then(Commands.literal("repair").executes(ctx->run(ctx.getSource(),ProjectAccess.Permission.BUILD,EngineeringSession::repair)))
                .then(Commands.literal("revise").then(Commands.argument("delay",IntegerArgumentType.integer(1,4)).executes(ctx->run(ctx.getSource(),ProjectAccess.Permission.EDIT,s->s.revise(IntegerArgumentType.getInteger(ctx,"delay"))))))
                .then(Commands.literal("reference")
                    .executes(ctx->run(ctx.getSource(),ProjectAccess.Permission.EDIT,s->s.accept(Blueprint.reference(s.worldId,s.dimension,s.anchor(),0))))
                    .then(Commands.argument("mechanism",StringArgumentType.word())
                        .executes(ctx->run(ctx.getSource(),ProjectAccess.Permission.EDIT,s->s.accept(MechanismCatalog.create(StringArgumentType.getString(ctx,"mechanism"),s.worldId,s.dimension,s.anchor(),0,1))))
                        .then(Commands.argument("delay",IntegerArgumentType.integer(1,4)).executes(ctx->run(ctx.getSource(),ProjectAccess.Permission.EDIT,s->s.accept(MechanismCatalog.create(StringArgumentType.getString(ctx,"mechanism"),s.worldId,s.dimension,s.anchor(),0,IntegerArgumentType.getInteger(ctx,"delay"))))))))
                .then(Commands.literal("design").then(Commands.argument("request",StringArgumentType.greedyString()).executes(ctx->run(ctx.getSource(),ProjectAccess.Permission.PLAN,s->{if(PLANS.containsKey(s))throw new IllegalStateException("Planning is active; stop it first");s.manager.generated.design(s,StringArgumentType.getString(ctx,"request"));}))))
                .then(Commands.literal("confirm").then(Commands.argument("nonce",StringArgumentType.word()).executes(ctx->run(ctx.getSource(),ProjectAccess.Permission.PLAN,s->s.manager.generated.confirm(s,StringArgumentType.getString(ctx,"nonce")),false))))
                .then(Commands.literal("changes").executes(ctx->run(ctx.getSource(),ProjectAccess.Permission.VIEW,EngineeringSession::showStructuralChanges,false)))
                .then(Commands.literal("apply").then(Commands.argument("nonce",StringArgumentType.word()).executes(ctx->run(ctx.getSource(),ProjectAccess.Permission.BUILD,s->s.applyStructuralPreview(StringArgumentType.getString(ctx,"nonce")),false))))
                .then(Commands.literal("plan").then(Commands.argument("request",StringArgumentType.greedyString()).executes(ctx->run(ctx.getSource(),ProjectAccess.Permission.PLAN,s->plan(s,StringArgumentType.getString(ctx,"request"))))))
                .then(Commands.literal("rotate").then(Commands.argument("degrees",IntegerArgumentType.integer(0,270)).executes(ctx->run(ctx.getSource(),ProjectAccess.Permission.EDIT,s->{if(s.blueprint==null)throw new IllegalStateException("No blueprint");s.accept(s.blueprint.transformed(s.blueprint.anchor(),IntegerArgumentType.getInteger(ctx,"degrees")));}))))
                .then(Commands.literal("anchor").then(Commands.argument("position",BlockPosArgument.blockPos()).executes(ctx->run(ctx.getSource(),ProjectAccess.Permission.EDIT,s->{if(!s.owner.equals(s.actor()))throw new IllegalStateException("Only the project owner can move its workspace");if(s.blueprint==null)throw new IllegalStateException("No blueprint");s.accept(s.blueprint.transformed(BlockPosArgument.getBlockPos(ctx,"position"),s.blueprint.rotation()));}))))
                .then(Commands.literal("discuss").then(Commands.argument("message",StringArgumentType.greedyString()).executes(ctx->run(ctx.getSource(),ProjectAccess.Permission.PLAN,s->s.manager.knowledge.discuss(s,StringArgumentType.getString(ctx,"message")),false))))
                .then(Commands.literal("notes").executes(ctx->run(ctx.getSource(),ProjectAccess.Permission.VIEW,s->s.manager.knowledge.read(s),false)))
                .then(Commands.literal("remember").then(Commands.argument("kind",StringArgumentType.word())
                    .suggests((ctx,builder)->net.minecraft.commands.SharedSuggestionProvider.suggest(java.util.List.of("goal","constraint","preference","decision"),builder))
                    .then(Commands.argument("text",StringArgumentType.greedyString()).executes(ctx->run(ctx.getSource(),ProjectAccess.Permission.EDIT,s->s.manager.knowledge.remember(s,StringArgumentType.getString(ctx,"kind"),StringArgumentType.getString(ctx,"text")),false)))))
                .then(Commands.literal("ask").then(Commands.argument("question",StringArgumentType.greedyString()).executes(ctx->run(ctx.getSource(),ProjectAccess.Permission.VIEW,s->LessonAssistant.ask(s,StringArgumentType.getString(ctx,"question")),false))))
                .then(Commands.literal("teach").executes(ctx->run(ctx.getSource(),ProjectAccess.Permission.VIEW,s->{s.cursor(s.actor()).teaching=true;s.save();s.explain();}))
                    .then(Commands.argument("component",StringArgumentType.word()).executes(ctx->run(ctx.getSource(),ProjectAccess.Permission.VIEW,s->s.teachComponent(StringArgumentType.getString(ctx,"component")),false))))
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
        UseBlockCallback.EVENT.register((player,level,hand,hit)->{
            if(player instanceof ServerPlayer connected){var manager=MANAGERS.get(connected.level().getServer());if(manager!=null)manager.inventories.interact(connected,hit);}
            return net.minecraft.world.InteractionResult.PASS;
        });
        ServerTickEvents.END_SERVER_TICK.register(server->{
            for(var entry:new ArrayList<>(PLANS.entrySet())) {
                var s=entry.getKey();var request=entry.getValue();if(s.server!=server)continue;
                ServerPlayer actor=server.getPlayerList().getPlayer(request.actor());
                if(!s.state.equals("PLANNING") || actor==null || actor.connection!=request.connection() || !s.manager.operator(actor) || s.revision()!=request.revision() || s.access.revision()!=request.authorization()) {
                    cancelPlan(s);s.pause("Planning context changed; request cancelled");
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
    static boolean current(ProjectManager manager) { return MANAGERS.get(manager.server)==manager; }
    static EngineeringSession sessionFor(ServerPlayer player) { return managerFor(player.level().getServer()).selected(player,false); }
    private static int help(CommandSourceStack source) { source.sendSuccess(()->Component.literal("Engineer: "+HELP),false);return Command.SINGLE_SUCCESS; }
    private static int withManager(CommandSourceStack source,BiConsumer<ProjectManager,ServerPlayer> action) {
        try {ServerPlayer p=source.getPlayerOrException();action.accept(managerFor(source.getServer()),p);return Command.SINGLE_SUCCESS;}
        catch(Exception error){source.sendFailure(Component.literal("Engineer: "+safe(error)));return 0;}
    }
    private static int run(CommandSourceStack source,ProjectAccess.Permission permission,Consumer<EngineeringSession> action) {
        return run(source,permission,action,true);
    }
    private static int run(CommandSourceStack source,ProjectAccess.Permission permission,Consumer<EngineeringSession> action,boolean create) {
        EngineeringSession project=null;
        try {
            ServerPlayer player=source.getPlayerOrException();ProjectManager manager=managerFor(source.getServer());
            project=manager.selected(player,create);project.access.require(player.getUUID(),permission);
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
    static void knowledgeChanging(EngineeringSession session) {
        session.manager.knowledge.cancelDiscussion(session);session.clearStructuralPreview();session.manager.generated.cancel(session);
        var pending=PLANS.remove(session);
        if(pending!=null){pending.future().cancel(true);if(session.state.equals("PLANNING")){session.state=pending.previousState();session.detail="Project knowledge changed; pending reasoning discarded";session.save();}}
    }
    static void cancelPlan(EngineeringSession session) {session.manager.knowledge.cancelDiscussion(session);session.clearStructuralPreview();session.manager.generated.cancel(session);var plan=PLANS.remove(session);if(plan!=null)plan.future().cancel(true);}
    private static String safe(Throwable error) {
        Throwable t=error;while(t.getCause()!=null)t=t.getCause();
        String message=t.getMessage()==null?"Operation failed; inspect local logs":t.getMessage();return message.substring(0,Math.min(350,message.length()));
    }
    private static void plan(EngineeringSession s,String request) {
        if(!s.manager.modelOperator(s.player()) || !s.owner.equals(s.actor()))throw new IllegalStateException("Planning requires the single configured account-owning operator; shared subscription access is not enabled");
        if(s.state.equals("PLANNING") || PLANS.containsKey(s))throw new IllegalStateException("Planning is active; stop it first");
        if(s.state.equals("EXECUTING") || s.testTick>=0 || s.hasPendingTest() || s.pendingPlacement || s.undoRequested)throw new IllegalStateException("Stop and reconcile active work before planning");
        s.manager.generated.cancel(s);
        UUID actor=s.actor();int revision=s.revision();long authorization=s.access.revision();
        boolean built=s.hasBuiltStructure();BlockPos origin=s.planningAnchor();String previousState=s.state;
        JsonObject body=new JsonObject();body.addProperty("request",request);body.addProperty("worldId",s.worldId);body.addProperty("dimension",s.dimension);body.add("anchor",Blueprint.jsonPos(origin));body.addProperty("rotation",built?s.blueprint.rotation():0);body.addProperty("revisionOnly",built);body.addProperty("projectId",s.projectId.toString());
        JsonObject size=new JsonObject();size.addProperty("x",Math.max(s.corner1.getX(),s.corner2.getX())-origin.getX()+1);size.addProperty("y",Math.max(s.corner1.getY(),s.corner2.getY())-origin.getY()+1);size.addProperty("z",Math.max(s.corner1.getZ(),s.corner2.getZ())-origin.getZ()+1);body.add("maxSize",size);
        body.addProperty("modelAuthorized",true);body.addProperty("isOperator",true);
        s.state="PLANNING";s.detail="One bounded operator-owned planning request";s.save();s.tell(s.detail);
        CompletableFuture<JsonObject> future=Companion.gateway(s,"/v2/design",body,actor);
        var pending=new PlanRequest(actor,s.manager.epoch,revision,authorization,s.player().connection,future,previousState);PLANS.put(s,pending);
        future.whenComplete((result,error)->s.server.execute(()->{
            if(PLANS.get(s)!=pending)return;PLANS.remove(s);
            if(!s.state.equals("PLANNING") || MANAGERS.get(s.server)!=s.manager || !pending.epoch().equals(s.manager.epoch) || s.revision()!=pending.revision()
                    || s.access.revision()!=pending.authorization() || !s.access.allows(actor,ProjectAccess.Permission.PLAN)
                    || s.server.getPlayerList().getPlayer(actor)==null || s.server.getPlayerList().getPlayer(actor).connection!=pending.connection() || !s.manager.operator(s.server.getPlayerList().getPlayer(actor))){s.pause("Planning authority changed; late result discarded");return;}
            s.commandActor=actor;
            try {
                if(error!=null)throw new IllegalStateException(safe(error));
                s.state="READY";
                if(!s.reviseProposal(Blueprint.parse(result.getAsJsonObject("blueprint"),s.worldId,s.dimension))){s.state=pending.previousState();s.save();}
                s.tell(result.get("message").getAsString());
            }catch(Exception rejected){s.state=s.blueprint==null?"BLOCKED":pending.previousState();s.detail="Plan unavailable or rejected; "+(s.blueprint==null?"no design accepted: ":"existing design kept: ")+safe(rejected);s.save();s.tell(s.detail);}
            finally{s.commandActor=null;}
        }));
    }
}
