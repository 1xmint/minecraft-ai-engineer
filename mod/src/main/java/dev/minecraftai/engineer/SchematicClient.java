package dev.minecraftai.engineer;

import com.google.gson.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Conversational schematic library: list, inspect, import, transform, compare,
 * show and export through the companion. Nothing here places blocks; a shown
 * design is converted into an untested blueprint candidate only.
 */
final class SchematicClient {
    static final int MAX_EXPORT_JSON=1_900_000;
    private final ProjectManager manager;
    private final Map<UUID,Frame> active=new HashMap<>();
    private boolean closed;

    private static final class Frame {
        final EngineeringSession session;final UUID actor,epoch;final ServerPlayer player;
        final net.minecraft.server.network.ServerGamePacketListenerImpl connection;
        final String connectionId;int revision;final long authorization,startedNanos=System.nanoTime();
        final BlockPos first,second;
        CompletableFuture<JsonObject> future;
        Frame(EngineeringSession s,ServerPlayer p,String id){session=s;actor=p.getUUID();epoch=s.manager.epoch;player=p;connection=p.connection;connectionId=id;revision=s.revision();authorization=s.access.revision();first=s.corner1;second=s.corner2;}
    }
    SchematicClient(ProjectManager manager){this.manager=manager;}

    private Frame start(EngineeringSession s,ProjectAccess.Permission permission){
        if(closed || !manager.server.isSameThread())throw new IllegalStateException("Schematics require an active server thread");
        s.access.require(s.actor(),permission);var player=s.player();
        if(player==null || player.hasDisconnected() || !Objects.equals(manager.selections.get(player.getUUID()),s.projectId)
                || !s.dimension.equals(player.level().dimension().identifier().toString()))throw new IllegalStateException("Choose this project in your current dimension first");
        if(active.containsKey(player.getUUID()))throw new IllegalStateException("A schematic request is still running; wait for its answer first.");
        if(active.size()>=128)throw new IllegalStateException("Too many pending schematic requests");
        var frame=new Frame(s,player,manager.connectionId(player));active.put(frame.actor,frame);return frame;
    }
    private boolean current(Frame f){
        var s=f.session;
        return !closed && active.get(f.actor)==f && EngineerMod.current(manager) && manager.epoch.equals(f.epoch)
                && manager.server.getPlayerList().getPlayer(f.actor)==f.player && f.player.connection==f.connection && !f.player.hasDisconnected()
                && manager.connectionId(f.player).equals(f.connectionId) && Objects.equals(manager.selections.get(f.actor),s.projectId)
                && s.revision()==f.revision && s.access.revision()==f.authorization && s.access.allows(f.actor,ProjectAccess.Permission.VIEW)
                && Objects.equals(s.corner1,f.first) && Objects.equals(s.corner2,f.second)
                && s.dimension.equals(f.player.level().dimension().identifier().toString())
                && System.nanoTime()-f.startedNanos<=30_000_000_000L;
    }
    private void tell(Frame f,String message){if(current(f))f.player.sendSystemMessage(Component.literal("Engineer: "+message.substring(0,Math.min(800,message.length()))));}
    private void finish(Frame f){active.remove(f.actor,f);if(f.future!=null && !f.future.isDone())f.future.cancel(true);}
    void tick(){for(var f:new ArrayList<>(active.values()))if(!current(f)){if(active.get(f.actor)==f && !f.player.hasDisconnected())f.player.sendSystemMessage(Component.literal("Engineer: The schematic result was not confirmed. Ask again; nothing was retried automatically."));finish(f);}}
    void close(){closed=true;for(var f:new ArrayList<>(active.values()))finish(f);}

    void handle(EngineeringSession s,SchematicText.Request r){
        var body=new JsonObject();
        boolean editing=Set.of("import","transform","export","preview").contains(r.action());
        switch(r.action()){
            case "inspect" -> body.addProperty("name",r.name());
            case "import" -> {body.addProperty("name",r.name());body.addProperty("discard",r.discard());body.add("transform",JsonNull.INSTANCE);}
            case "design","preview" -> body.addProperty("designId",r.design());
            case "transform" -> {body.addProperty("designId",r.design());body.addProperty("transform",r.transform());}
            case "compare" -> {body.addProperty("designId",r.design());body.addProperty("otherId",r.other());}
            case "export" -> {body.addProperty("name",r.name());body.add("geometry",capture(s,r.discard()));}
            default -> { }
        }
        var f=start(s,editing?ProjectAccess.Permission.EDIT:ProjectAccess.Permission.VIEW);
        try{f.future=Companion.gateway(s,"/v2/schematic/"+(r.action().equals("preview")?"design":r.action()),body,f.actor);}catch(RuntimeException failure){finish(f);throw failure;}
        f.future.whenComplete((reply,error)->manager.server.execute(()->{
            if(!current(f)){finish(f);return;}
            try{
                if(error!=null){tell(f,refusal(error));return;}
                if(r.action().equals("preview")){
                    s.access.require(f.actor,ProjectAccess.Permission.EDIT);var previous=s.commandActor;
                    try{s.commandActor=f.actor;var message=preview(reply,s);
                        f.revision=s.revision(); // Advance only after this frame's own accepted preview.
                        tell(f,message);
                    }finally{s.commandActor=previous;}
                }else tell(f,describe(r,reply,s));
            }catch(IllegalArgumentException | IllegalStateException refused){tell(f,"Schematic request refused: "+refused.getMessage()+". No blocks were placed.");}
            catch(RuntimeException invalid){tell(f,"The companion's schematic answer was not understood, so nothing was used. Ask again.");}
            finally{finish(f);}
        }));
    }

    private static String refusal(Throwable error){
        Throwable cause=error instanceof CompletionException && error.getCause()!=null?error.getCause():error;
        String text=cause.getMessage()==null?"The companion did not answer.":cause.getMessage();
        return text.substring(0,Math.min(400,text.length()))+(text.endsWith(".")?"":".")+" Nothing in the world changed.";
    }

    static String describe(SchematicText.Request r,JsonObject reply,EngineeringSession s){
        String status=reply.get("status").getAsString();
        return switch(status){
            case "listed" -> reply.has("files")?library(reply):designs(reply.getAsJsonArray("designs"));
            case "inspected" -> r.name()+": "+shape(reply)+(reply.get("requiresDiscard").getAsBoolean()?" It holds "+reply.getAsJsonObject("discarded").getAsJsonArray("blockEntities").size()+" block entities (chest contents, sign text and similar), which import cannot keep: say 'import schematic "+r.name()+" and drop block entities' to import the blocks without them.":"")+" Imported geometry is untrusted and untested.";
            case "needs-discard" -> r.name()+" holds "+reply.getAsJsonObject("discarded").getAsJsonArray("blockEntities").size()+" block entities that cannot be imported. Nothing was stored. Say 'import schematic "+r.name()+" and drop block entities' to import the blocks without them.";
            case "imported","transformed" -> "Stored as design "+reply.getAsJsonObject("design").get("designId").getAsInt()+transforms(reply.getAsJsonObject("design"))+": "+shape(reply)+" Untrusted and untested.";
            case "compared" -> compared(reply);
            case "shared" -> shared(reply,s);
            case "exported" -> "Exported "+reply.get("file").getAsString()+" ("+reply.get("bytes").getAsInt()+" bytes) to the companion's exports folder: "+shape(reply)+" Block contents and entities were not exported.";
            default -> throw new IllegalArgumentException("Unknown schematic status");
        };
    }
    private static String library(JsonObject reply){
        var files=reply.getAsJsonArray("files");if(files.isEmpty())return "The schematic library folder is empty. Put .schem files in the companion's schematics/library folder.";
        var names=new ArrayList<String>();for(var e:files)names.add(e.getAsJsonObject().get("name").getAsString());
        int total=reply.get("total").getAsInt();
        return "Schematic library ("+total+"): "+String.join(", ",names.subList(0,Math.min(20,names.size())))+(total>Math.min(20,names.size())?", +"+(total-Math.min(20,names.size()))+" more":"")+".";
    }
    private static String designs(JsonArray list){
        if(list.isEmpty())return "No designs are imported in this project yet.";
        var parts=new ArrayList<String>();
        for(int i=Math.max(0,list.size()-8);i<list.size();i++){var d=list.get(i).getAsJsonObject();parts.add("#"+d.get("designId").getAsInt()+" "+d.getAsJsonObject("source").get("name").getAsString()+transforms(d)+" "+size(d.getAsJsonObject("size")));}
        return "Imported designs ("+list.size()+(list.size()>8?", newest 8":"")+"): "+String.join("; ",parts)+". All untested.";
    }
    private static String transforms(JsonObject design){var t=design.getAsJsonArray("transforms");if(t.isEmpty())return "";var names=new ArrayList<String>();for(var e:t)names.add(e.getAsString());return " ("+String.join(", ",names)+")";}
    private static String size(JsonObject size){return size.get("x").getAsInt()+"x"+size.get("y").getAsInt()+"x"+size.get("z").getAsInt();}
    static String shape(JsonObject reply){
        var parts=new ArrayList<String>();var materials=reply.getAsJsonArray("materials");
        for(int i=0;i<Math.min(8,materials.size());i++){var m=materials.get(i).getAsJsonObject();parts.add(m.get("key").getAsString().replaceFirst("^minecraft:","")+" x"+m.get("count").getAsInt());}
        int kinds=reply.get("materialKinds").getAsInt();
        return size(reply.getAsJsonObject("size"))+", "+reply.get("nonAirBlocks").getAsInt()+" blocks"+(parts.isEmpty()?".":": "+String.join(", ",parts)+(kinds>parts.size()?", +"+(kinds-parts.size())+" more kinds":"")+".");
    }
    private static String compared(JsonObject reply){
        var a=reply.getAsJsonObject("a");var b=reply.getAsJsonObject("b");var parts=new ArrayList<String>();var delta=reply.getAsJsonArray("materials");
        for(int i=0;i<Math.min(6,delta.size());i++){var m=delta.get(i).getAsJsonObject();parts.add(m.get("block").getAsString().replaceFirst("^minecraft:","")+" "+m.get("a").getAsInt()+"->"+m.get("b").getAsInt());}
        return "Design "+a.get("designId").getAsInt()+" is "+size(a.getAsJsonObject("size"))+" with "+a.get("nonAirBlocks").getAsInt()+" blocks; design "+b.get("designId").getAsInt()+" is "+size(b.getAsJsonObject("size"))+" with "+b.get("nonAirBlocks").getAsInt()+" blocks. "
                +(parts.isEmpty()?"They use the same materials.":"Material changes: "+String.join(", ",parts)+(delta.size()>6?", +"+(delta.size()-6)+" more":"")+".")
                +" Geometry only: neither design is tested, and a difference in shape is not a difference in behaviour.";
    }
    private static String preview(JsonObject reply,EngineeringSession s){
        if(!reply.get("status").getAsString().equals("shared"))throw new IllegalArgumentException("Expected an authorized stored design");
        var placement=reply.getAsJsonObject("placement");
        if(!placement.get("available").getAsBoolean())return placement.get("reason").getAsString();
        var design=reply.getAsJsonObject("design");int id=design.get("designId").getAsInt();
        var source=SchematicBlueprint.candidate(placement.getAsJsonArray("blocks"),id,design.get("digest").getAsString(),design.getAsJsonObject("source").get("sha256").getAsString(),s.worldId,s.dimension,s.projectId.toString());
        var unsupported=SchematicBlueprint.unsupported(source);
        if(!unsupported.isEmpty())return "This design cannot be built with the current safe block library: "+String.join(", ",unsupported.subList(0,Math.min(6,unsupported.size())))+". You can still inspect and export it.";
        s.proposeImported(Blueprint.parse(source,s.worldId,s.dimension),s.anchor(),0);
        return "Preview ready: Imported design "+id+". No blocks were placed. It is untested and has no functional test contract. Ask me to build it for a separate approval.";
    }
    private static String shared(JsonObject reply,EngineeringSession s){
        var design=reply.getAsJsonObject("design");var placement=reply.getAsJsonObject("placement");int id=design.get("designId").getAsInt();
        String head="Design "+id+transforms(design)+": "+shape(reply);
        if(!placement.get("available").getAsBoolean())return head+" "+placement.get("reason").getAsString();
        var candidate=SchematicBlueprint.candidate(placement.getAsJsonArray("blocks"),id,design.get("digest").getAsString(),design.getAsJsonObject("source").get("sha256").getAsString(),s.worldId,s.dimension,s.projectId.toString());
        var unsupported=SchematicBlueprint.unsupported(candidate);
        if(!unsupported.isEmpty())return head+" It uses blocks outside the safe build library ("+String.join(", ",unsupported.subList(0,Math.min(6,unsupported.size())))+(unsupported.size()>6?", +"+(unsupported.size()-6)+" more":"")+"), so it can be inspected and exported but not built.";
        return head+" Every block is in the safe build library. Say 'preview design "+id+"' to prepare it in your marked workspace. Preview and construction remain untested.";
    }

    /** Copies the authorized selection, air included, in Sponge order. Never reads entities or block-entity data. */
    static JsonObject capture(EngineeringSession s,boolean discard){
        s.access.require(s.actor(),ProjectAccess.Permission.EDIT);
        if(s.corner1==null || s.corner2==null)throw new IllegalStateException("Select both corners of the region to export first.");
        return region(s.level(),s.corner1,s.corner2,discard);
    }
    static JsonObject region(ServerLevel level,BlockPos a,BlockPos b,boolean discard){
        BlockPos min=new BlockPos(Math.min(a.getX(),b.getX()),Math.min(a.getY(),b.getY()),Math.min(a.getZ(),b.getZ()));
        BlockPos max=new BlockPos(Math.max(a.getX(),b.getX()),Math.max(a.getY(),b.getY()),Math.max(a.getZ(),b.getZ()));
        int sx=max.getX()-min.getX()+1,sy=max.getY()-min.getY()+1,sz=max.getZ()-min.getZ()+1;
        if(sx>64 || sy>64 || sz>64)throw new IllegalStateException("Exports are limited to 64 blocks on each side; this selection is "+sx+"x"+sy+"x"+sz+".");
        var palette=new LinkedHashMap<String,Integer>();var entries=new JsonArray();var blocks=new JsonArray();int contents=0;
        for(int y=0;y<sy;y++)for(int z=0;z<sz;z++)for(int x=0;x<sx;x++){
            var pos=min.offset(x,y,z);
            if(!level.hasChunkAt(pos))throw new IllegalStateException("Part of the selection is not loaded. Stand closer and try again; nothing was exported.");
            BlockState state=level.getBlockState(pos);if(state.hasBlockEntity())contents++;
            String key=Blueprint.describe(state);
            Integer index=palette.get(key);
            if(index==null){if(palette.size()>=4096)throw new IllegalStateException("The selection has too many block states to export.");index=palette.size();palette.put(key,index);entries.add(paletteEntry(state));}
            blocks.add(index);
        }
        if(contents>0 && !discard)throw new IllegalStateException("The selection has "+contents+" blocks with contents (chests, signs and similar) that export cannot keep. Say 'export selection as NAME and drop block contents' to export the blocks without them.");
        var geometry=new JsonObject();
        geometry.addProperty("format","minecraft-ai-engineer.schematic-geometry");geometry.addProperty("formatVersion",1);
        geometry.addProperty("dataVersion",SharedConstants.getCurrentVersion().dataVersion().version());
        geometry.add("size",xyz(sx,sy,sz));geometry.add("offset",xyz(0,0,0));geometry.add("palette",entries);geometry.add("blocks",blocks);
        if(geometry.toString().length()>MAX_EXPORT_JSON)throw new IllegalStateException("The selection is too detailed to send to the companion; export a smaller region.");
        return geometry;
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    static JsonObject paletteEntry(BlockState state){
        var entry=new JsonObject();entry.addProperty("name",BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        var properties=new JsonObject();
        for(Property property:state.getProperties())properties.addProperty(property.getName(),property.getName(state.getValue(property)));
        entry.add("properties",properties);return entry;
    }
    private static JsonObject xyz(int x,int y,int z){var o=new JsonObject();o.addProperty("x",x);o.addProperty("y",y);o.addProperty("z",z);return o;}
}
