package dev.minecraftai.engineer;

import com.google.gson.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;

/** Explicit private observations; no item setters, menu creation, NBT, names or world saves. */
final class InventoryPerception {
    private static final Map<Class<?>,Class<?>> MENUS=Map.ofEntries(
        Map.entry(ChestBlockEntity.class,ChestMenu.class),Map.entry(TrappedChestBlockEntity.class,ChestMenu.class),
        Map.entry(BarrelBlockEntity.class,ChestMenu.class),Map.entry(HopperBlockEntity.class,HopperMenu.class),
        Map.entry(DispenserBlockEntity.class,DispenserMenu.class),Map.entry(DropperBlockEntity.class,DispenserMenu.class),
        Map.entry(ShulkerBoxBlockEntity.class,ShulkerBoxMenu.class),Map.entry(FurnaceBlockEntity.class,FurnaceMenu.class),
        Map.entry(BlastFurnaceBlockEntity.class,BlastFurnaceMenu.class),Map.entry(SmokerBlockEntity.class,SmokerMenu.class),
        Map.entry(BrewingStandBlockEntity.class,BrewingStandMenu.class),Map.entry(CrafterBlockEntity.class,CrafterMenu.class));
    private final ProjectManager manager;
    private final Map<UUID,Frame> active=new LinkedHashMap<>();
    private static final class Frame {
        final EngineeringSession project;final ServerPlayer player;final UUID actor,epoch;final String connectionId;
        final net.minecraft.server.network.ServerGamePacketListenerImpl connection;
        final int revision;final long authorization,startedTick,startedNanos=System.nanoTime();
        boolean waiting;Candidate candidate;CompletableFuture<JsonObject> future;
        Frame(EngineeringSession s,ServerPlayer p,String nonce) {
            project=s;player=p;actor=p.getUUID();connection=p.connection;connectionId=nonce;epoch=s.manager.epoch;
            revision=s.revision();authorization=s.access.revision();startedTick=s.level().getGameTime();
        }
    }
    private record Candidate(BlockPos pos,BlockEntity entity,AbstractContainerMenu previous) {}
    InventoryPerception(ProjectManager manager){this.manager=manager;}
    private boolean live(Frame f) {
        var s=f.project;
        try { return EngineerMod.current(manager) && manager.epoch.equals(f.epoch) && active.get(f.actor)==f
            && manager.server.getPlayerList().getPlayer(f.actor)==f.player && f.player.connection==f.connection && !f.player.hasDisconnected()
            && Objects.equals(manager.selections.get(f.actor),s.projectId) && s.revision()==f.revision && s.access.revision()==f.authorization
            && s.access.allows(f.actor,ProjectAccess.Permission.VIEW) && s.dimension.equals(f.player.level().dimension().identifier().toString())
            && s.level().getGameTime()-f.startedTick<400 && System.nanoTime()-f.startedNanos<20_000_000_000L;
        }catch(RuntimeException unavailable){return false;}
    }
    private void message(Frame f,String text) {
        if(manager.server.getPlayerList().getPlayer(f.actor)==f.player && f.player.connection==f.connection && !f.player.hasDisconnected())
            f.player.sendSystemMessage(Component.literal("Engineer: "+text));
    }
    private void finish(Frame f) {
        active.remove(f.actor,f);if(f.future!=null && !f.future.isDone())f.future.cancel(true);
    }
    private Frame start(EngineeringSession s) {
        if(!manager.server.isSameThread())throw new IllegalStateException("Inventory observation belongs to the server thread");
        s.access.require(s.actor(),ProjectAccess.Permission.VIEW);ServerPlayer player=s.player();
        if(player==null || player.hasDisconnected() || !s.dimension.equals(player.level().dimension().identifier().toString())
                || !Objects.equals(manager.selections.get(player.getUUID()),s.projectId))throw new IllegalStateException("Choose this project in your current dimension first");
        Frame previous=active.get(player.getUUID());if(previous!=null)finish(previous);
        if(active.size()>=128)throw new IllegalStateException("Too many pending private inspections");
        Frame frame=new Frame(s,player,manager.connectionId(player));active.put(frame.actor,frame);return frame;
    }
    void own(EngineeringSession s) {
        Frame f=start(s);
        try {
            Container inventory=f.player.getInventory();int size=inventory.getContainerSize();checkSize(size);
            var totals=new TreeMap<String,Long>();for(int slot=0;slot<size;slot++)add(totals,inventory.getItem(slot));
            remember(f,snapshot(f,"player",size,List.of(),totals));
        }catch(RuntimeException error){finish(f);throw error;}
    }
    void container(EngineeringSession s) {
        Frame f=start(s);f.waiting=true;message(f,"Open a vanilla container in this workspace within 20 seconds; inspection is private and read-only.");
    }
    void materials(EngineeringSession s) {materials(s,s.activeComponent);}
    void materials(EngineeringSession s,String component) {
        s.access.require(s.actor(),ProjectAccess.Permission.VIEW);
        Blueprint intended=s.blueprint==null?null:ComponentTree.component(s.blueprint,component);
        Frame f=start(s);
        f.future=Companion.gateway(s,"/v2/inventory/read",new JsonObject(),f.actor);
        f.future.whenComplete((reply,error)->manager.server.execute(()->{
            if(!live(f)){finish(f);return;}
            try {
                if(error!=null)throw new IllegalStateException("Private inventory memory unavailable; inspect again");
                if(!reply.has("inventory") || reply.get("inventory").isJsonNull()){message(f,"No fresh inventory snapshot for this connection; inspect your inventory or a container first.");return;}
                JsonObject remembered=reply.getAsJsonObject("inventory"),snapshot=remembered.getAsJsonObject("snapshot");
                if(!f.actor.toString().equals(snapshot.get("actorId").getAsString()) || !f.connectionId.equals(snapshot.get("connectionId").getAsString()))
                    throw new IllegalStateException("Private snapshot identity changed; inspect again");
                var totals=new TreeMap<String,Long>();
                for(var value:snapshot.getAsJsonArray("items")){var item=value.getAsJsonObject();String id=item.get("id").getAsString();long count=item.get("count").getAsBigDecimal().longValueExact();
                    if(!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || id.length()>128 || count<1 || totals.putIfAbsent(id,count)!=null || totals.size()>128)throw new IllegalStateException("Invalid private item totals");}
                message(f,"Private materials for "+component+" ("+snapshot.get("source").getAsString()+" snapshot remembered "+remembered.get("ageMs").getAsLong()/1000+"s ago, observed at tick "+snapshot.get("tick").getAsLong()+"):");
                if(intended==null){message(f,summary(totals));return;}
                for(var resource:intended.source().getAsJsonObject("resources").entrySet()){
                    long have=totals.getOrDefault(resource.getKey(),0L),need=resource.getValue().getAsLong();
                    message(f,resource.getKey().replace("minecraft:","")+": "+have+" observed / "+need+" planned; missing "+Math.max(0,need-have));}
            }catch(RuntimeException failure){message(f,"Private material check unavailable; inspect again.");}
            finally {finish(f);}
        }));
    }
    /** Called before vanilla interaction. PASS is returned by the event hook on every path. */
    void interact(ServerPlayer player,BlockHitResult hit) {
        Frame f=active.get(player.getUUID());if(f==null || !f.waiting)return;f.candidate=null;
        if(!live(f)){finish(f);return;}
        try {
            BlockPos pos=hit.getBlockPos().immutable();readable(f,pos);
            BlockEntity entity=f.project.level().getBlockEntity(pos);
            if(entity==null || !MENUS.containsKey(entity.getClass()))throw new IllegalStateException("Open supported vanilla storage; player/ender/crafting or custom backing is excluded");
            // Metadata only. Vanilla may independently open/unpack this container after PASS.
            f.candidate=new Candidate(pos,entity,player.containerMenu);
        }catch(RuntimeException failure){message(f,"Storage inspection refused: "+failure.getMessage());finish(f);}
    }
    void tick() {
        for(Frame f:new ArrayList<>(active.values())) {
            if(!live(f)){message(f,"Private inventory request cancelled or expired.");finish(f);continue;}
            if(!f.waiting || f.candidate==null)continue;
            try {sampleContainer(f);}
            catch(RuntimeException failure){message(f,"Storage inspection refused: "+failure.getMessage());finish(f);}
        }
    }
    private void readable(Frame f,BlockPos pos) {
        var s=f.project;var level=s.level();
        if(!s.contains(pos))throw new IllegalStateException("container backing outside selected workspace");
        if(!level.hasChunkAt(pos))throw new IllegalStateException("unloaded container backing");
        if(!level.getWorldBorder().isWithinBounds(pos) || pos.getY()<level.getMinY() || pos.getY()>level.getMaxY())throw new IllegalStateException("container outside world bounds");
        for(var other:manager.projects.values())if(other!=s && other.dimension.equals(s.dimension) && other.contains(pos) && !other.access.allows(f.actor,ProjectAccess.Permission.VIEW))
            throw new IllegalStateException("neighboring project has not granted view permission");
    }
    private void backing(Frame f,BlockPos pos,BlockEntity expected) {
        readable(f,pos);
        if(expected.isRemoved() || expected.getLevel()!=f.project.level() || !expected.getBlockPos().equals(pos)
                || f.project.level().getBlockEntity(pos)!=expected || !MENUS.containsKey(expected.getClass()))throw new IllegalStateException("container backing changed");
        if(expected instanceof RandomizableContainerBlockEntity loot && loot.getLootTable()!=null)throw new IllegalStateException("unresolved loot cannot be read by inspection");
    }
    private void sampleContainer(Frame f) {
        Candidate c=f.candidate;var menu=f.player.containerMenu;
        if(menu==c.previous() || menu==f.player.inventoryMenu || menu.getClass()!=MENUS.get(c.entity().getClass()))throw new IllegalStateException("storage did not open as its exact vanilla menu");
        backing(f,c.pos(),c.entity());Container source=(Container)c.entity();var positions=new ArrayList<BlockPos>();positions.add(c.pos());
        if(menu instanceof ChestMenu chest) {
            source=chest.getContainer();
            if(source instanceof CompoundContainer compound) {
                if(source.getClass()!=CompoundContainer.class)throw new IllegalStateException("Unsupported modded storage wrapper");
                var state=f.project.level().getBlockState(c.pos());
                if(!(c.entity() instanceof ChestBlockEntity) || !(state.getBlock() instanceof ChestBlock) || state.getValue(ChestBlock.TYPE)==ChestType.SINGLE)throw new IllegalStateException("ambiguous double-chest backing");
                BlockPos partner=ChestBlock.getConnectedBlockPos(c.pos(),state);readable(f,partner);
                var neighbor=f.project.level().getBlockEntity(partner);var neighborState=f.project.level().getBlockState(partner);
                if(neighbor==null || !(neighbor instanceof ChestBlockEntity) || neighborState.getBlock()!=state.getBlock()
                        || neighborState.getValue(ChestBlock.FACING)!=state.getValue(ChestBlock.FACING)
                        || neighborState.getValue(ChestBlock.TYPE)==ChestType.SINGLE || neighborState.getValue(ChestBlock.TYPE)==state.getValue(ChestBlock.TYPE)
                        || !ChestBlock.getConnectedBlockPos(partner,neighborState).equals(c.pos()))throw new IllegalStateException("double-chest halves no longer match");
                backing(f,partner,neighbor);
                if(!compound.contains((Container)c.entity()) || !compound.contains((Container)neighbor))throw new IllegalStateException("menu does not reference these exact chest halves");
                positions.add(partner);
            } else if(source!=c.entity())throw new IllegalStateException("menu backing is not this storage");
        }
        // Validate every backing/index before stillValid or an item getter can touch it.
        int size=source.getContainerSize();checkSize(size);var indices=new LinkedHashSet<Integer>();
        for(Slot slot:menu.slots) {
            if(slot.container==f.player.getInventory())continue;
            if(menu.getClass()==CrafterMenu.class && slot.getClass()==NonInteractiveResultSlot.class && slot.container.getClass()==ResultContainer.class)continue; // Virtual preview: never invoke its item getter.
            int index=slot.getContainerSlot();
            if(slot.container!=source || index<0 || index>=size)throw new IllegalStateException("unknown or nonstorage menu slot");
            indices.add(index);
        }
        if(indices.size()!=size || !menu.stillValid(f.player) || !live(f))throw new IllegalStateException("storage is no longer accessible");
        var totals=new TreeMap<String,Long>();for(int index:indices)add(totals,source.getItem(index));
        f.waiting=false;f.candidate=null;remember(f,snapshot(f,"container",size,positions,totals));
    }
    private static void checkSize(int size){if(size<1 || size>128)throw new IllegalStateException("storage exceeds inspection limits");}
    private static void add(Map<String,Long> totals,ItemStack item) {
        if(item.isEmpty())return;String id=BuiltInRegistries.ITEM.getKey(item.getItem()).toString();
        totals.merge(id,(long)item.getCount(),Math::addExact);
    }
    private JsonObject snapshot(Frame f,String source,int slots,List<BlockPos> positions,Map<String,Long> totals) {
        JsonObject out=new JsonObject();out.addProperty("schemaVersion",1);out.addProperty("worldId",f.project.worldId);out.addProperty("dimension",f.project.dimension);
        out.addProperty("actorId",f.actor.toString());out.addProperty("connectionId",f.connectionId);out.addProperty("source",source);out.addProperty("slots",slots);out.addProperty("tick",f.project.level().getGameTime());
        JsonArray containers=new JsonArray();positions.forEach(pos->containers.add(Blueprint.jsonPos(pos)));out.add("containers",containers);
        JsonArray items=new JsonArray();totals.forEach((id,count)->{var item=new JsonObject();item.addProperty("id",id);item.addProperty("count",count);items.add(item);});out.add("items",items);return out;
    }
    private static String summary(Map<String,Long> totals) {
        if(totals.isEmpty())return "empty";var pairs=new ArrayList<String>();
        for(var entry:totals.entrySet()){if(pairs.size()>=12)break;pairs.add(entry.getKey().replace("minecraft:","")+"="+entry.getValue());}
        return String.join(", ",pairs)+(totals.size()>12?"; additional item types omitted from chat":"");
    }
    private void remember(Frame f,JsonObject snapshot) {
        if(!live(f))throw new IllegalStateException("Private inspection context changed");
        var totals=new TreeMap<String,Long>();for(var value:snapshot.getAsJsonArray("items")){var item=value.getAsJsonObject();totals.put(item.get("id").getAsString(),item.get("count").getAsLong());}
        f.future=Companion.gateway(f.project,"/v2/inventory",snapshot,f.actor);
        f.future.whenComplete((reply,error)->manager.server.execute(()->{
            if(!live(f)){finish(f);return;}
            message(f,error==null?"Private inventory remembered ("+snapshot.get("source").getAsString()+"): "+summary(totals):"Private inventory memory unavailable; inspect again.");finish(f);
        }));
    }
    void close(){for(Frame f:new ArrayList<>(active.values()))finish(f);}
}
