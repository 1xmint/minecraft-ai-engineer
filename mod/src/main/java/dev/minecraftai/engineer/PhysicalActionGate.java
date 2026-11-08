package dev.minecraftai.engineer;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Managed actor actions fail closed; tickets never replace project authority. */
public final class PhysicalActionGate {
    private record MenuTicket(int step,int slot,int button,long expires,Runnable writeAhead) { }
    private static final class MenuRun {
        final UUID operation;final PhysicalMenuProtocol sequence;final Object connection;final net.minecraft.world.inventory.AbstractContainerMenu menu;final Runnable authority;
        int next=1;boolean inFlight;MenuTicket ticket;
        MenuRun(UUID operation,PhysicalMenuProtocol sequence,ServerPlayer actor,Runnable authority){this.operation=operation;this.sequence=sequence;connection=actor.connection;menu=sequence.menu(actor);this.authority=authority;}
    }
    private record Ticket(String operation,BlockPos target,BlockState before,long expires,Runnable authority,Runnable writeAhead) { }
    private record TableOpen(BlockPos table,net.minecraft.world.level.block.Block block,ItemStack stack,long expires,Runnable authority,Runnable writeAhead) { }
    private record Pickup(net.minecraft.world.entity.item.ItemEntity drop,ItemStack stack,List<ItemStack> baseline,long expires,Runnable authority,Runnable writeAhead) { }
    private static final class Entry {
        final ServerPlayer actor;
        Object connection;
        Ticket ticket;
        Pickup pickup;
        MenuRun menuRun;
        PhysicalPlacementInventory placement;
        Ticket placementCommit;
        BlockPos minedTarget;
        net.minecraft.world.entity.item.ItemEntity minedDrop;
        net.minecraft.world.item.Item minedItem;
        BlockPos pendingPlace,observedPlace;
        TableOpen tableOpen;
        Entry(ServerPlayer actor){this.actor=actor;connection=actor.connection;}
    }
    private static final Map<UUID,Entry> ACTORS=new java.util.concurrent.ConcurrentHashMap<>();
    private PhysicalActionGate() { }
    static void register(ServerPlayer actor){thread(actor);if(ACTORS.putIfAbsent(actor.getUUID(),new Entry(actor))!=null)throw new IllegalStateException("Actor already bound");}
    static void bindConnection(ServerPlayer actor){thread(actor);var entry=ACTORS.get(actor.getUUID());if(entry==null || entry.actor!=actor || entry.connection!=null || actor.connection==null)throw new IllegalStateException("Actor connection cannot be rebound");entry.connection=actor.connection;}
    static void forget(ServerPlayer actor){thread(actor);PhysicalWorkerMenus.forget(actor);var entry=ACTORS.get(actor.getUUID());if(entry!=null && entry.actor==actor)ACTORS.remove(actor.getUUID(),entry);}
    public static boolean managed(Player actor){return actor!=null && ACTORS.containsKey(actor.getUUID());}
    public static boolean exactActor(Player actor){var entry=actor==null?null:ACTORS.get(actor.getUUID());return entry!=null && entry.actor==actor;}
    static boolean exactConnection(ServerPlayer actor){thread(actor);var entry=ACTORS.get(actor.getUUID());return entry!=null && entry.actor==actor && actor.connection!=null && !actor.hasDisconnected() && entry.connection==actor.connection;}
    static void arm(ServerPlayer actor,String operation,BlockPos target,BlockState before,long expires,Runnable authority,Runnable writeAhead){
        armNative(actor,operation,target,before,expires,authority,writeAhead,operation.equals("place")?PhysicalPlacementInventory.capture(actor,PhysicalPlacementInventory.Material.DIRT):null);
    }
    static void armPlace(ServerPlayer actor,BlockPos target,long expires,Runnable authority,Runnable writeAhead,PhysicalPlacementInventory placement){
        armNative(actor,"place",target,Blocks.AIR.defaultBlockState(),expires,authority,writeAhead,java.util.Objects.requireNonNull(placement));
    }
    private static void armNative(ServerPlayer actor,String operation,BlockPos target,BlockState before,long expires,Runnable authority,Runnable writeAhead,PhysicalPlacementInventory placement){
        PhysicalWorkerMenus.requireSettled(actor);
        thread(actor);var entry=ACTORS.get(actor.getUUID());
        if(entry==null || entry.actor!=actor || entry.ticket!=null || entry.pickup!=null || entry.menuRun!=null || entry.placement!=null || entry.placementCommit!=null || entry.pendingPlace!=null || entry.observedPlace!=null || entry.minedTarget!=null || entry.minedDrop!=null || entry.minedItem!=null || entry.tableOpen!=null)throw new IllegalStateException("No idle bound actor");
        if(!Set.of("place","mine").contains(operation))throw new IllegalArgumentException("Unsupported action");
        entry.ticket=new Ticket(operation,target.immutable(),before,expires,authority,writeAhead);
        if(operation.equals("place")){if(placement==null || !placement.beforeMatches(actor)){entry.ticket=null;throw new IllegalStateException("Placement source is not the exact selected retained stack");}entry.placement=placement;}
    }
    /** One-call capability to open exactly one placed crafting table with an empty-hand interaction; never a placement. */
    static void armTable(ServerPlayer actor,BlockPos table,long expires,Runnable authority,Runnable writeAhead){
        PhysicalWorkerMenus.requireSettled(actor);
        thread(actor);var entry=ACTORS.get(actor.getUUID());
        if(entry==null || entry.actor!=actor || !menuIdle(entry) || !actor.level().hasChunkAt(table) || !actor.level().getBlockState(table).is(Blocks.CRAFTING_TABLE))throw new IllegalStateException("No idle bound actor beside a placed crafting table");
        entry.tableOpen=new TableOpen(table.immutable(),Blocks.CRAFTING_TABLE,actor.getMainHandItem(),expires,java.util.Objects.requireNonNull(authority),java.util.Objects.requireNonNull(writeAhead));
    }
    /** One-call capability to open exactly one plain chest with an empty main hand; never a placement. */
    static void armChest(ServerPlayer actor,BlockPos chest,long expires,Runnable authority,Runnable writeAhead){
        PhysicalWorkerMenus.requireSettled(actor);
        thread(actor);var entry=ACTORS.get(actor.getUUID());
        if(entry==null || entry.actor!=actor || !menuIdle(entry) || !actor.getMainHandItem().isEmpty() || !actor.level().hasChunkAt(chest) || !actor.level().getBlockState(chest).is(Blocks.CHEST))throw new IllegalStateException("No idle empty-handed bound actor beside a chest");
        entry.tableOpen=new TableOpen(chest.immutable(),Blocks.CHEST,actor.getMainHandItem(),expires,java.util.Objects.requireNonNull(authority),java.util.Objects.requireNonNull(writeAhead));
    }
    /** One-call capability to flip exactly one lever with an empty main hand; never a placement. */
    static void armLever(ServerPlayer actor,BlockPos lever,long expires,Runnable authority,Runnable writeAhead){
        PhysicalWorkerMenus.requireSettled(actor);
        thread(actor);var entry=ACTORS.get(actor.getUUID());
        if(entry==null || entry.actor!=actor || !menuIdle(entry) || !actor.getMainHandItem().isEmpty() || !actor.level().hasChunkAt(lever) || !actor.level().getBlockState(lever).is(Blocks.LEVER))throw new IllegalStateException("No idle empty-handed bound actor beside a lever");
        entry.tableOpen=new TableOpen(lever.immutable(),Blocks.LEVER,actor.getMainHandItem(),expires,java.util.Objects.requireNonNull(authority),java.util.Objects.requireNonNull(writeAhead));
    }
    static void armPickup(ServerPlayer actor,net.minecraft.world.entity.item.ItemEntity drop,long expires,Runnable authority,Runnable writeAhead){
        PhysicalWorkerMenus.requireSettled(actor);
        thread(actor);var entry=ACTORS.get(actor.getUUID());
        if(entry==null || entry.actor!=actor || entry.pickup!=null || entry.ticket!=null || entry.menuRun!=null || entry.placement!=null || entry.placementCommit!=null || entry.pendingPlace!=null || entry.observedPlace!=null || entry.tableOpen!=null
            || entry.minedDrop!=drop || entry.minedItem==null || drop.level()!=actor.level() || !drop.isAlive() || !drop.getItem().is(entry.minedItem) || drop.getItem().getCount()!=1
            || !PhysicalGatherInventory.hasRoom(actor,entry.minedItem))
            throw new IllegalStateException("Pickup requires the exact expected native resource drop and room in the bound inventory");
        // The pickup may only add this drop to the exact inventory held when it was armed.
        entry.pickup=new Pickup(drop,drop.getItem().copy(),PhysicalGatherInventory.snapshot(actor),expires,authority,writeAhead);
    }
    static boolean eligibleDrop(ServerPlayer actor,net.minecraft.world.entity.item.ItemEntity drop){
        thread(actor);var entry=ACTORS.get(actor.getUUID());
        return entry!=null && entry.actor==actor && entry.connection==actor.connection && entry.minedDrop==drop && drop.isAlive();
    }
    static net.minecraft.world.entity.item.ItemEntity capturedDrop(ServerPlayer actor){thread(actor);var entry=ACTORS.get(actor.getUUID());return entry!=null && entry.actor==actor?entry.minedDrop:null;}
    static void cancel(ServerPlayer actor){thread(actor);var entry=ACTORS.get(actor.getUUID());if(entry!=null && entry.actor==actor){entry.ticket=null;entry.pickup=null;entry.menuRun=null;entry.placement=null;entry.placementCommit=null;entry.minedTarget=null;entry.minedDrop=null;entry.minedItem=null;entry.pendingPlace=null;entry.observedPlace=null;entry.tableOpen=null;}}
    private static Entry menuActor(ServerPlayer actor){
        thread(actor);var entry=ACTORS.get(actor.getUUID());
        if(entry==null || entry.actor!=actor || !exactConnection(actor) || actor.level().getServer().getPlayerList().getPlayer(actor.getUUID())!=actor
            || !actor.isAlive() || actor.gameMode.getGameModeForPlayer()!=net.minecraft.world.level.GameType.SURVIVAL || PhysicalWorkerMenus.pending(actor))throw new IllegalStateException("No current survival worker menu connection");
        return entry;
    }
    static void beginMenu(ServerPlayer actor,UUID operation,PhysicalMenuProtocol sequence,Runnable authority){
        var entry=menuActor(actor);java.util.Objects.requireNonNull(sequence);if(sequence.needsSettledStart())PhysicalWorkerMenus.requireSettled(actor);java.util.Objects.requireNonNull(operation);java.util.Objects.requireNonNull(authority);
        if(sequence.clicks()<1 || sequence.clicks()>sequence.maxClicks())throw new IllegalStateException("Menu protocol has no bounded native actions");
        if(!menuIdle(entry))throw new IllegalStateException("Worker already has an action or resource phase");
        authority.run();
        if(menuActor(actor)!=entry || !menuIdle(entry) || !sequence.ownsMenu(actor,sequence.menu(actor)) || actor.containerMenu!=sequence.menu(actor) || !sequence.beforeMatches(1,actor))throw new IllegalStateException("Worker changed before crafting admission");
        entry.menuRun=new MenuRun(operation,sequence,actor,authority);
    }
    private static boolean menuIdle(Entry entry){return entry.ticket==null && entry.pickup==null && entry.menuRun==null && entry.placement==null && entry.placementCommit==null && entry.minedTarget==null && entry.minedDrop==null && entry.minedItem==null && entry.pendingPlace==null && entry.observedPlace==null && entry.tableOpen==null;}
    static void armMenu(ServerPlayer actor,UUID operation,int step,long expires,Runnable writeAhead){
        var entry=menuActor(actor);var run=entry.menuRun;
        if(run==null || !run.operation.equals(operation) || run.next!=step || run.inFlight || run.ticket!=null || expires!=(long)actor.level().getServer().getTickCount()+1)throw new IllegalStateException("No exact same-tick crafting step");
        validateMenu(actor,entry,run,step);
        int button=run.sequence.button(step);if(button!=0 && button!=1)throw new IllegalStateException("Unsupported native menu button");
        run.ticket=new MenuTicket(step,run.sequence.slot(step),button,expires,java.util.Objects.requireNonNull(writeAhead));
    }
    private static void validateMenu(ServerPlayer actor,Entry entry,MenuRun run,int step){
        if(menuActor(actor)!=entry || entry.menuRun!=run || actor.connection!=run.connection || !run.sequence.ownsMenu(actor,run.menu) || actor.containerMenu!=run.menu
            || !run.sequence.beforeMatches(step,actor))throw new IllegalStateException("Native crafting resource frame changed");
        // Recipes may reload independently of inventory. Recheck current native
        // behavior using a prediction copy, never by inserting assembled output.
        run.sequence.validateNative(actor);
        run.authority.run();
        if(entry.menuRun!=run || !exactConnection(actor) || actor.connection!=run.connection || !run.sequence.ownsMenu(actor,run.menu) || actor.containerMenu!=run.menu || !run.sequence.beforeMatches(step,actor))throw new IllegalStateException("Crafting authority changed during validation");
    }
    /** Event-time one-call capability; native clicked/onTake still performs the work. */
    public static boolean menuClick(net.minecraft.world.inventory.AbstractContainerMenu menu,int slot,int button,net.minecraft.world.inventory.ContainerInput input,Player player){
        if(!managed(player))return true;
        var entry=ACTORS.get(player.getUUID());if(!(player instanceof ServerPlayer actor) || entry==null || entry.actor!=actor)return false;
        var run=entry.menuRun;
        try{
            var ticket=run==null?null:run.ticket;
            if(ticket==null || run.inFlight || run.next!=ticket.step() || menu!=run.menu || slot!=ticket.slot() || button!=ticket.button() || input!=net.minecraft.world.inventory.ContainerInput.PICKUP
                || ticket.expires()!=(long)actor.level().getServer().getTickCount()+1)throw new IllegalStateException("No exact native menu ticket");
            validateMenu(actor,entry,run,ticket.step());run.ticket=null;run.inFlight=true;ticket.writeAhead().run();
            validateMenu(actor,entry,run,ticket.step());
            if(ticket.expires()!=(long)actor.level().getServer().getTickCount()+1)throw new IllegalStateException("Native menu ticket expired before use");
            return true;
        }catch(RuntimeException refused){entry.menuRun=null;return false;}
    }
    /** Observation is separate from authority: never infer a click succeeded at HEAD. */
    static void observeMenu(ServerPlayer actor,UUID operation,int step){
        thread(actor);var entry=ACTORS.get(actor.getUUID());var run=entry==null?null:entry.menuRun;
        if(entry==null || entry.actor!=actor || run==null || !run.operation.equals(operation) || run.next!=step || !run.inFlight || actor.connection!=run.connection
            || !run.sequence.ownsMenu(actor,run.menu) || actor.containerMenu!=run.menu || !run.sequence.afterMatches(step,actor)){if(entry!=null && entry.actor==actor)entry.menuRun=null;throw new IllegalStateException("Native crafting outcome is uncertain; preserve resources and do not retry");}
        run.inFlight=false;run.next++;
    }
    static void finishMenu(ServerPlayer actor,UUID operation){
        thread(actor);var entry=ACTORS.get(actor.getUUID());var run=entry==null?null:entry.menuRun;
        if(entry==null || entry.actor!=actor || run==null || !run.operation.equals(operation) || run.next!=run.sequence.clicks()+1 || run.inFlight || run.ticket!=null
            || actor.connection!=run.connection || !run.sequence.ownsMenu(actor,run.menu) || actor.containerMenu!=run.menu || !run.sequence.afterMatches(run.sequence.clicks(),actor))throw new IllegalStateException("Crafting sequence is incomplete or its final resource frame changed");
        entry.menuRun=null;
    }
    public static boolean pickup(Player player,net.minecraft.world.entity.item.ItemEntity drop){
        if(!managed(player))return true;
        var entry=ACTORS.get(player.getUUID());if(entry==null || entry.actor!=player)return false;var ticket=entry.pickup;
        try{
            if(!(player instanceof ServerPlayer actor) || entry.actor!=actor || entry.connection!=actor.connection || actor.hasDisconnected())throw new IllegalStateException("Worker connection changed");
            thread(actor);
            if(ticket==null || ticket.drop!=drop || !drop.isAlive() || drop.level()!=actor.level()
                || actor.level().getServer().getTickCount()>=ticket.expires || !PhysicalGatherInventory.unchanged(ticket.baseline,actor)
                || actor.gameMode.getGameModeForPlayer()!=net.minecraft.world.level.GameType.SURVIVAL
                || !ItemStack.matches(ticket.stack,drop.getItem()))throw new IllegalStateException("No exact current pickup ticket");
            if(drop.hasPickUpDelay())return false;
            ticket.authority.run();entry.pickup=null;entry.minedDrop=null;entry.minedItem=null;ticket.writeAhead.run();return true;
        }catch(RuntimeException refused){entry.pickup=null;return false;}
    }
    private static void thread(ServerPlayer actor){if(!actor.level().getServer().isSameThread())throw new IllegalStateException("Server thread required");}
    private static boolean check(Player player,String operation,BlockPos target,boolean consume){
        if(!managed(player))return true;
        var entry=ACTORS.get(player.getUUID());if(entry==null || entry.actor!=player)return false;var ticket=entry.ticket;
        try{
            if(!(player instanceof ServerPlayer actor) || entry.actor!=actor || entry.connection!=actor.connection || actor.hasDisconnected())throw new IllegalStateException("Actor connection changed");
            thread(actor);
            if(ticket==null || !ticket.operation.equals(operation) || !ticket.target.equals(target)
                || actor.level().getServer().getTickCount()>=ticket.expires || !actor.level().hasChunkAt(target)
                || !actor.level().getBlockState(target).equals(ticket.before))throw new IllegalStateException("No current exact-cell ticket");
            ticket.authority.run();
            if(consume){entry.ticket=null;ticket.writeAhead.run();}
            return true;
        }catch(RuntimeException refused){entry.ticket=null;return false;}
    }
    public static boolean use(ServerPlayer actor,Level level,ItemStack stack,BlockHitResult hit){
        if(!managed(actor))return true;
        var bound=ACTORS.get(actor.getUUID());if(bound==null || bound.actor!=actor)return false;
        var open=bound.tableOpen;
        if(open!=null){
            // A table ticket only opens that table; it never falls through to placement.
            bound.tableOpen=null;
            try{
                if(level!=actor.level() || bound.connection!=actor.connection || actor.hasDisconnected() || !hit.getBlockPos().equals(open.table()) || !level.hasChunkAt(open.table())
                    || !level.getBlockState(open.table()).is(open.block()) || stack!=open.stack() || stack!=actor.getMainHandItem() || actor.isSecondaryUseActive()
                    || actor.level().getServer().getTickCount()>=open.expires())return false;
                open.authority().run();open.writeAhead().run();return true;
            }catch(RuntimeException refused){return false;}
        }
        // No other containers, secondary block interactions, buckets, fire or spawn eggs.
        if(level!=actor.level() || !heldPlacement(bound,actor,stack,net.minecraft.world.InteractionHand.MAIN_HAND) || !level.hasChunkAt(hit.getBlockPos()) || !supportClicked(bound,level,hit)){
            ACTORS.get(actor.getUUID()).ticket=null;return false;
        }
        return check(actor,"place",new BlockPlaceContext(actor,net.minecraft.world.InteractionHand.MAIN_HAND,stack,hit).getClickedPos(),false);
    }
    public static boolean place(BlockPlaceContext context){
        var actor=context.getPlayer();if(!managed(actor))return true;
        var bound=ACTORS.get(actor.getUUID());if(bound==null || bound.actor!=actor)return false;
        var ticket=bound.ticket;var plan=bound.placement;
        if(!(actor instanceof ServerPlayer current) || context.getLevel()!=current.level() || !heldPlacement(bound,current,context.getItemInHand(),context.getHand()) || !context.getLevel().hasChunkAt(context.getClickedPos()) || !context.getLevel().getBlockState(context.getClickedPos()).isAir()){
            ACTORS.get(actor.getUUID()).ticket=null;return false;
        }
        boolean permitted=check(actor,"place",context.getClickedPos(),true);
        if(permitted && ACTORS.get(actor.getUUID())==bound && bound.placement==plan && heldPlacement(bound,current,context.getItemInHand(),context.getHand())){bound.pendingPlace=context.getClickedPos().immutable();bound.placementCommit=ticket;return true;}return false;
    }
    /** Legacy commits the default state; aimed commits must match the planned state exactly except for neighbour-driven power and light. */
    private static boolean plannedCommit(PhysicalPlacementInventory placement,BlockState state){
        return placement.aim()==null?state.equals(placement.planned()):Blueprint.matches(placement.planned(),state);
    }
    /** Legacy placements click stone; aimed ones click exactly their planned face of their unchanged inert support. */
    private static boolean supportClicked(Entry entry,Level level,BlockHitResult hit){
        var aim=entry.placement==null?null:entry.placement.aim();var live=level.getBlockState(hit.getBlockPos());
        if(aim==null)return live.is(Blocks.STONE);
        return hit.getBlockPos().equals(aim.support()) && hit.getDirection()==aim.face() && live.equals(aim.supportState()) && inertSupport(live);
    }
    /** Supports whose server-side use does nothing, so the click can only reach item placement. */
    static boolean inertSupport(BlockState state){return state.is(Blocks.STONE) || state.is(Blocks.COBBLESTONE) || state.is(Blocks.DIRT);}
    private static boolean heldPlacement(Entry entry,ServerPlayer actor,ItemStack stack,net.minecraft.world.InteractionHand hand){
        try{return hand==net.minecraft.world.InteractionHand.MAIN_HAND && entry.placement!=null && stack==actor.getMainHandItem() && stack.is(entry.placement.item()) && entry.placement.beforeMatches(actor);}catch(RuntimeException invalid){return false;}
    }
    /** One-use fence immediately before BlockItem writes its native block. */
    public static boolean placeBlock(BlockPlaceContext context,BlockState state){
        var player=context.getPlayer();if(!managed(player))return true;var entry=ACTORS.get(player.getUUID());if(!(player instanceof ServerPlayer actor) || entry==null || entry.actor!=actor)return false;
        var ticket=entry.placementCommit;var plan=entry.placement;entry.placementCommit=null;
        try{
            thread(actor);
            if(ticket==null || !ticket.operation.equals("place") || !ticket.target.equals(context.getClickedPos()) || !ticket.target.equals(entry.pendingPlace)
                || !exactConnection(actor) || actor.level().getServer().getPlayerList().getPlayer(actor.getUUID())!=actor || !actor.isAlive()
                || actor.gameMode.getGameModeForPlayer()!=net.minecraft.world.level.GameType.SURVIVAL || actor.level()!=context.getLevel()
                || actor.level().getServer().getTickCount()>=ticket.expires || !actor.level().hasChunkAt(ticket.target) || !actor.level().getBlockState(ticket.target).equals(ticket.before)
                || !heldPlacement(entry,actor,context.getItemInHand(),context.getHand()) || !plannedCommit(entry.placement,state))throw new IllegalStateException("No exact native placement commit");
            ticket.authority.run();
            if(ACTORS.get(actor.getUUID())!=entry || entry.placement!=plan || !exactConnection(actor) || actor.level().getServer().getPlayerList().getPlayer(actor.getUUID())!=actor
                || !actor.isAlive() || actor.gameMode.getGameModeForPlayer()!=net.minecraft.world.level.GameType.SURVIVAL || actor.level()!=context.getLevel()
                || actor.level().getServer().getTickCount()>=ticket.expires || !actor.level().hasChunkAt(ticket.target) || !heldPlacement(entry,actor,context.getItemInHand(),context.getHand())
                || !ticket.target.equals(entry.pendingPlace) || !actor.level().getBlockState(ticket.target).equals(ticket.before))throw new IllegalStateException("Placement changed during final authorization");
            return true;
        }catch(RuntimeException invalid){entry.pendingPlace=null;return false;}
    }
    public static void placed(BlockPlaceContext context,net.minecraft.world.InteractionResult result){
        var actor=context.getPlayer();if(!managed(actor))return;var entry=ACTORS.get(actor.getUUID());
        if(entry==null || entry.actor!=actor)return;
        var target=entry.pendingPlace;entry.pendingPlace=null;entry.placementCommit=null;
        if(target!=null && entry.placement!=null && target.equals(context.getClickedPos()) && result.consumesAction() && context.getLevel().getBlockState(target).is(entry.placement.planned().getBlock()))entry.observedPlace=target;
    }
    static boolean placementObserved(ServerPlayer actor,BlockPos target){thread(actor);var entry=ACTORS.get(actor.getUUID());return entry!=null && entry.actor==actor && target.equals(entry.observedPlace);}
    public static boolean mine(ServerPlayer actor,BlockPos target){
        if(!managed(actor))return true;
        var bound=ACTORS.get(actor.getUUID());if(bound==null || bound.actor!=actor)return false;
        PhysicalMaterial material;
        if(!actor.level().hasChunkAt(target)){bound.ticket=null;return false;}
        try{material=PhysicalMaterial.source(actor.level().getBlockState(target));}catch(IllegalStateException unsupported){bound.ticket=null;return false;}
        var entry=ACTORS.get(actor.getUUID());entry.minedTarget=null;entry.minedDrop=null;entry.minedItem=null;
        boolean permitted=check(actor,"mine",target,true);
        if(permitted){entry.minedTarget=target.immutable();entry.minedItem=material.item;}return permitted;
    }
    private record DropScope(ServerPlayer actor,BlockPos target,java.util.List<net.minecraft.world.entity.item.ItemEntity> drops) { }
    private static final ThreadLocal<DropScope> DROP_SCOPE=new ThreadLocal<>();
    public static void nativeDrops(ServerPlayer actor,BlockPos target,Runnable nativeDestroy){
        if(!managed(actor)){nativeDestroy.run();return;}
        thread(actor);var entry=ACTORS.get(actor.getUUID());
        if(entry.actor!=actor || !target.equals(entry.minedTarget) || DROP_SCOPE.get()!=null)throw new IllegalStateException("No authorized native drop scope");
        entry.minedTarget=null;var scope=new DropScope(actor,target.immutable(),new java.util.ArrayList<>());DROP_SCOPE.set(scope);
        try {
            nativeDestroy.run();
            if(scope.drops.size()==1){var drop=scope.drops.getFirst();
                if(entry.minedItem!=null && drop.isAlive() && drop.getItem().is(entry.minedItem) && drop.getItem().getCount()==1)entry.minedDrop=drop;
            }
        }finally{DROP_SCOPE.remove();}
    }
    public static void emittedDrop(net.minecraft.world.entity.Entity entity){
        var scope=DROP_SCOPE.get();if(scope==null)return;
        if(entity instanceof net.minecraft.world.entity.item.ItemEntity drop && drop.level()==scope.actor.level()
            && drop.blockPosition().equals(scope.target))scope.drops.add(drop);
    }
}
