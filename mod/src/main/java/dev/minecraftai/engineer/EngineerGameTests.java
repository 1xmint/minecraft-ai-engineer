package dev.minecraftai.engineer;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Actual Minecraft semantics; executed by GitHub CI, never substitutes for client acceptance. */
public final class EngineerGameTests {
    @GameTest public void oakRecoveryPlansOnlyNativeResourceMovement(GameTestHelper helper){
        var items=new java.util.ArrayList<net.minecraft.world.item.ItemStack>(java.util.Collections.nCopies(PhysicalCraftFrame.SLOTS,net.minecraft.world.item.ItemStack.EMPTY));items.set(3,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_LOG));
        var empty=new PhysicalMenuSnapshot(java.util.Collections.nCopies(4,net.minecraft.world.item.ItemStack.EMPTY),net.minecraft.world.item.ItemStack.EMPTY);
        var initial=new PhysicalCraftFrame(items,empty,net.minecraft.world.item.ItemStack.EMPTY,7);var recipe=OakCraftingPlan.inspect(helper.getLevel(),items.get(3));var sequence=OakCraftingSequence.predict(initial,3,recipe);
        int[] clicks={1,2,1,0};
        for(int stopped=1;stopped<=4;stopped++){
            var original=sequence.after(stopped);var recovery=OakCraftRecoveryPlan.inspect(original,3);helper.assertTrue(recovery.clicks()==clicks[stopped-1],"Each interrupted resource shape has its hand-worked movement count");
            for(int step=1;step<=recovery.clicks();step++)helper.assertTrue(recovery.slot(step)==1 || recovery.slot(step)==39,"Recovery never takes native result slot0 or unrelated inventory slots");
            helper.assertTrue(recovery.settled().matches(stopped<3?initial:sequence.after(4)),"Recovery preserves the actual log or actual four planks and selected slot");
            helper.assertTrue(original.matches(sequence.after(stopped)),"Prediction never mutates the interrupted resource frame");
        }
        helper.assertTrue(OakCraftRecoveryPlan.inspect(initial,3).clicks()==0,"Already settled original log requires no native action");
        var unavailable=new PhysicalCraftFrame(java.util.Collections.nCopies(PhysicalCraftFrame.SLOTS,net.minecraft.world.item.ItemStack.EMPTY),empty,net.minecraft.world.item.ItemStack.EMPTY,7);
        rejectsOakRecovery(helper,unavailable,"Missing resources never become a manufactured replacement");
        var unrelated=new java.util.ArrayList<>(sequence.after(1).inventory());unrelated.set(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIRT));rejectsOakRecovery(helper,new PhysicalCraftFrame(unrelated,sequence.after(1).menu(),net.minecraft.world.item.ItemStack.EMPTY,7),"Other items remain untouched for explicit inspection");
        var named=recipe.input();named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("changed"));rejectsOakRecovery(helper,new PhysicalCraftFrame(unavailable.inventory(),new PhysicalMenuSnapshot(java.util.Collections.nCopies(4,net.minecraft.world.item.ItemStack.EMPTY),named),net.minecraft.world.item.ItemStack.EMPTY,7),"Changed stack components are preserved rather than normalized");
        helper.succeed();
    }
    private static void rejectsOakRecovery(GameTestHelper helper,PhysicalCraftFrame frame,String message){boolean refused=false;try{OakCraftRecoveryPlan.inspect(frame,3);}catch(IllegalStateException expected){refused=true;}helper.assertTrue(refused,message);}
    @GameTest public void oakClickProtocolKeepsEveryResourcePositionExact(GameTestHelper helper){
        var items=new java.util.ArrayList<net.minecraft.world.item.ItemStack>(java.util.Collections.nCopies(PhysicalCraftFrame.SLOTS,net.minecraft.world.item.ItemStack.EMPTY));var log=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_LOG);items.set(3,log);
        var empty=new PhysicalMenuSnapshot(java.util.Collections.nCopies(4,net.minecraft.world.item.ItemStack.EMPTY),net.minecraft.world.item.ItemStack.EMPTY);var initial=new PhysicalCraftFrame(items,empty,net.minecraft.world.item.ItemStack.EMPTY,7);
        var plan=OakCraftingPlan.inspect(helper.getLevel(),log);var sequence=OakCraftingSequence.predict(initial,3,plan);
        helper.assertTrue(sequence.slot(1)==39 && sequence.slot(2)==1 && sequence.slot(3)==0 && sequence.slot(4)==39,"Only the original hotbar, first grid input and native result slots are selected");
        helper.assertTrue(sequence.after(1).inventory().stream().allMatch(net.minecraft.world.item.ItemStack::isEmpty) && net.minecraft.world.item.ItemStack.matches(sequence.after(1).menu().cursor(),log),"First click moves only the retained log to the cursor");
        helper.assertTrue(sequence.after(2).menu().cursor().isEmpty() && net.minecraft.world.item.ItemStack.matches(sequence.after(2).menu().inputs().getFirst(),log) && sequence.after(2).preview().is(net.minecraft.world.item.Items.OAK_PLANKS) && sequence.after(2).preview().getCount()==4,"Second click leaves the real log in the grid with a derived four-plank preview");
        helper.assertTrue(sequence.after(3).menu().inputs().stream().allMatch(net.minecraft.world.item.ItemStack::isEmpty) && sequence.after(3).preview().isEmpty() && sequence.after(3).menu().cursor().is(net.minecraft.world.item.Items.OAK_PLANKS) && sequence.after(3).menu().cursor().getCount()==4,"Native result take must leave exactly four carried planks and no input or preview");
        var finalItems=sequence.after(4).inventory();helper.assertTrue(sequence.after(4).menu().empty() && finalItems.get(3).is(net.minecraft.world.item.Items.OAK_PLANKS) && finalItems.get(3).getCount()==4,"Final click deposits output only into the original source slot");
        for(int slot=0;slot<finalItems.size();slot++)if(slot!=3)helper.assertTrue(finalItems.get(slot).isEmpty(),"No extra inventory or equipment output");
        for(int step=1;step<=4;step++)helper.assertTrue(sequence.before(step).selected()==7 && sequence.after(step).selected()==7,"Crafting does not change selected hotbar position");
        items.get(3).setCount(2);finalItems.get(3).setCount(1);helper.assertTrue(sequence.before(1).inventory().get(3).getCount()==1 && sequence.after(4).inventory().get(3).getCount()==4,"Caller aliases cannot alter the protocol");
        var named=plan.input();named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("changed"));var changed=new PhysicalCraftFrame(sequence.after(1).inventory(),new PhysicalMenuSnapshot(java.util.Collections.nCopies(4,net.minecraft.world.item.ItemStack.EMPTY),named),net.minecraft.world.item.ItemStack.EMPTY,7);
        helper.assertTrue(!sequence.after(1).matches(changed),"Changed item components invalidate a crafting frame");
        reject(helper,()->sequence.slot(0),"No hidden native crafting steps");reject(helper,()->OakCraftingSequence.predict(initial,9,plan),"Off-hotbar sources refuse");helper.succeed();
    }
    @GameTest public void oakCraftingPredictionUsesNativeRecipeWithoutConsumingResources(GameTestHelper helper){
        var log=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_LOG);var plan=OakCraftingPlan.inspect(helper.getLevel(),log);
        helper.assertTrue(log.getCount()==1 && net.minecraft.world.item.ItemStack.matches(plan.input(),log) && net.minecraft.world.item.ItemStack.matches(plan.output(),new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_PLANKS,4)),"Actual native recipe predicts four default planks without consuming the retained log");
        plan.input().setCount(2);plan.output().setCount(1);helper.assertTrue(plan.input().getCount()==1 && plan.output().getCount()==4,"Predictions are detached item data, not mutable resource sources");
        reject(helper,()->OakCraftingPlan.inspect(helper.getLevel(),new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIRT)),"A different resource cannot substitute for the retained log");
        reject(helper,()->OakCraftingPlan.inspect(helper.getLevel(),new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_LOG,2)),"The first crafting contract consumes only one explicitly retained log");
        var named=log.copy();named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("keep this"));reject(helper,()->OakCraftingPlan.inspect(helper.getLevel(),named),"Named/custom resources require separate explicit handling");helper.succeed();
    }
    @GameTest public void workerMenuSnapshotsPreserveActualComponentsAndRejectInventedFields(GameTestHelper helper){
        var registries=helper.getLevel().registryAccess();var log=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_LOG);
        log.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("retained native input"));
        var cursor=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_PLANKS,4);
        cursor.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("actual carried stack"));
        var snapshot=new PhysicalMenuSnapshot(java.util.List.of(log,net.minecraft.world.item.ItemStack.EMPTY,net.minecraft.world.item.ItemStack.EMPTY,net.minecraft.world.item.ItemStack.EMPTY),cursor);
        var encoded=snapshot.encode(registries);helper.assertTrue(encoded.keySet().equals(java.util.Set.of("schemaVersion","inputs","cursor")),"No derived result or authority is stored");
        var nativeOps=registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);var written=encoded.getList("inputs").orElseThrow();
        helper.assertTrue(net.minecraft.world.item.ItemStack.matches(log,net.minecraft.world.item.ItemStack.OPTIONAL_CODEC.parse(nativeOps,written.get(0)).getOrThrow()),"Vanilla independently reads the named log at its original position");
        for(int slot=1;slot<4;slot++)helper.assertTrue(net.minecraft.world.item.ItemStack.OPTIONAL_CODEC.parse(nativeOps,written.get(slot)).getOrThrow().isEmpty(),"Vanilla independently reads each empty grid position");
        helper.assertTrue(net.minecraft.world.item.ItemStack.matches(cursor,net.minecraft.world.item.ItemStack.OPTIONAL_CODEC.parse(nativeOps,encoded.get("cursor")).getOrThrow()),"Vanilla independently reads all four carried planks and their components");
        var restored=PhysicalMenuSnapshot.decode(encoded,registries);helper.assertTrue(snapshot.matches(restored),"Native component-aware codec retains each grid position and cursor exactly");
        log.setCount(2);cursor.setCount(1);restored.inputs().getFirst().setCount(3);restored.cursor().setCount(2);
        helper.assertTrue(snapshot.matches(restored) && snapshot.inputs().getFirst().getCount()==1 && snapshot.cursor().getCount()==4,"Input aliases and returned stacks cannot alter a snapshot");
        var extra=encoded.copy();extra.putBoolean("verified",true);reject(helper,()->PhysicalMenuSnapshot.decode(extra,registries),"Authority fields are rejected");
        var version=encoded.copy();version.putInt("schemaVersion",2);reject(helper,()->PhysicalMenuSnapshot.decode(version,registries),"Unknown versions cannot be assumed compatible");
        var shortGrid=encoded.copy();shortGrid.getList("inputs").orElseThrow().remove(3);reject(helper,()->PhysicalMenuSnapshot.decode(shortGrid,registries),"Missing grid positions are never assumed empty");
        var missing=encoded.copy();missing.remove("cursor");reject(helper,()->PhysicalMenuSnapshot.decode(missing,registries),"A missing cursor is never assumed empty");
        var malformed=encoded.copy();malformed.putString("cursor","not an item");reject(helper,()->PhysicalMenuSnapshot.decode(malformed,registries),"Malformed item data cannot silently become empty");
        helper.succeed();
    }
    @GameTest public void nativeInventorySnapshotDecodesAllSlotsWithoutPlayerClones(GameTestHelper helper){
        var registries=helper.getLevel().registryAccess();var nbtOps=registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);var jsonOps=registries.createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
        var id=java.util.UUID.randomUUID();var source=new net.minecraft.nbt.CompoundTag();source.put("UUID",net.minecraft.core.UUIDUtil.CODEC.encodeStart(nbtOps,id).getOrThrow());
        var items=new net.minecraft.nbt.ListTag();var expected=new com.google.gson.JsonArray();
        for(int slot=0;slot<net.minecraft.world.entity.player.Inventory.INVENTORY_SIZE;slot++){
            var stack=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE,slot+1);stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("main-"+slot));
            items.add(net.minecraft.world.ItemStackWithSlot.CODEC.encodeStart(nbtOps,new net.minecraft.world.ItemStackWithSlot(slot,stack)).getOrThrow());expected.add(net.minecraft.world.item.ItemStack.OPTIONAL_CODEC.encodeStart(jsonOps,stack).getOrThrow());
        }
        source.put("Inventory",items);var equipment=new net.minecraft.world.entity.EntityEquipment();
        for(int slot=36;slot<43;slot++){
            var stack=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE);stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("equipment-"+slot));
            equipment.set(net.minecraft.world.entity.player.Inventory.EQUIPMENT_SLOT_MAPPING.get(slot),stack);expected.add(net.minecraft.world.item.ItemStack.OPTIONAL_CODEC.encodeStart(jsonOps,stack).getOrThrow());
        }
        source.put("equipment",net.minecraft.world.entity.EntityEquipment.CODEC.encodeStart(nbtOps,equipment).getOrThrow());
        java.util.function.Function<net.minecraft.nbt.CompoundTag,com.google.gson.JsonArray> decode=tag->{var problems=new net.minecraft.util.ProblemReporter.Collector();return PhysicalWorkerStorage.decodeInventory(registries,id,net.minecraft.world.level.storage.TagValueInput.create(problems,registries,tag),problems);};
        helper.assertTrue(decode.apply(source).equals(expected) && expected.size()==43,"All main/equipment slots and item components survive native-codec decoding without constructing any player");
        var duplicate=source.copy();var repeated=items.copy();repeated.add(items.getFirst().copy());duplicate.put("Inventory",repeated);reject(helper,()->decode.apply(duplicate),"Duplicate native item slots refuse rather than silently overwrite");
        var outside=source.copy();var badItems=items.copy();badItems.add(net.minecraft.world.ItemStackWithSlot.CODEC.encodeStart(nbtOps,new net.minecraft.world.ItemStackWithSlot(36,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIRT))).getOrThrow());outside.put("Inventory",badItems);reject(helper,()->decode.apply(outside),"Equipment cannot be smuggled into the ordinary item list");
        var noIdentity=source.copy();noIdentity.remove("UUID");reject(helper,()->decode.apply(noIdentity),"Missing identity refuses");
        var otherIdentity=source.copy();otherIdentity.put("UUID",net.minecraft.core.UUIDUtil.CODEC.encodeStart(nbtOps,java.util.UUID.randomUUID()).getOrThrow());reject(helper,()->decode.apply(otherIdentity),"A differently named native profile cannot substitute its inventory");
        var missing=source.copy();missing.remove("Inventory");reject(helper,()->decode.apply(missing),"Missing inventory is not assumed empty");
        var malformed=source.copy();malformed.putString("Inventory","not a list");reject(helper,()->decode.apply(malformed),"Malformed inventory is not assumed empty");
        var badEquipment=source.copy();badEquipment.putString("equipment","not equipment");reject(helper,()->decode.apply(badEquipment),"Malformed equipment cannot silently become empty gear");
        var badStack=source.copy();var invalid=new net.minecraft.nbt.ListTag();var row=new net.minecraft.nbt.CompoundTag();row.putByte("Slot",(byte)0);row.putString("id","minecraft:unknown_inventory_probe");invalid.add(row);badStack.put("Inventory",invalid);reject(helper,()->decode.apply(badStack),"Codec errors refuse an unknown saved item");
        helper.succeed();
    }
    @GameTest public void flatRouteSearchUsesRealDetourAndRejectsMissingPaths(GameTestHelper helper){
        var available=new java.util.HashSet<BlockPos>();
        for(int x=0;x<=4;x++)for(int z=0;z<=4;z++)available.add(new BlockPos(x,0,z));
        for(int z=0;z<4;z++)available.remove(new BlockPos(2,0,z));
        var start=new BlockPos(0,0,0);var goal=new BlockPos(4,0,0);
        var route=PhysicalRoutePlan.search(start,goal,available::contains);
        helper.assertTrue(route.cells().size()==13 && route.cells().contains(new BlockPos(2,0,4)),"A wall requires the hand-worked twelve-step detour through its only opening");
        helper.assertTrue(route.cells().stream().allMatch(available::contains),"Every route cell was admitted; no diagonal or obstacle shortcut");
        reject(helper,()->route.cells().add(start),"A caller cannot expand the planned route");
        available.remove(new BlockPos(2,0,4));
        reject(helper,()->PhysicalRoutePlan.search(start,goal,available::contains),"A sealed wall has no route");
        reject(helper,()->PhysicalRoutePlan.search(start,goal.above(),cell->true),"Flat navigation cannot invent a jump");
        reject(helper,()->PhysicalRoutePlan.search(start,new BlockPos(65,0,0),cell->true),"Far destinations are bounded before search");
        reject(helper,()->new PhysicalRoutePlan(java.util.List.of(start,goal)),"Externally assembled routes cannot skip swept cells");
        var mutable=new BlockPos.MutableBlockPos(1,0,0);var copied=new PhysicalRoutePlan(java.util.List.of(start,mutable));mutable.set(50,0,0);
        helper.assertTrue(copied.cells().getLast().equals(new BlockPos(1,0,0)),"Mutable Minecraft positions cannot change a captured route");
        helper.succeed();
    }
    @GameTest public void movementOccupancyNeverGrantsMutation(GameTestHelper helper){
        var leases=new RegionReservations(java.util.UUID.randomUUID());var owner=java.util.UUID.randomUUID();
        var a=new RegionReservations.Scope("world","minecraft:overworld",java.util.UUID.randomUUID());
        var b=new RegionReservations.Scope("world","minecraft:overworld",java.util.UUID.randomUUID());
        var body=java.util.Set.of(BlockPos.ZERO,BlockPos.ZERO.above());var floor=java.util.Set.of(BlockPos.ZERO.below());
        var motion=leases.acquireMovement(a,owner,1,1,body,floor,0);
        leases.validateOccupancy(a,motion,owner,1,1,body,1);
        reject(helper,()->leases.validateWrite(a,motion,owner,1,1,BlockPos.ZERO,1),"Standing in a cell grants no native block action");
        reject(helper,()->leases.validateFootprint(a,motion,owner,1,1,body,floor,1),"Movement cannot authorize deferred block mutation");
        helper.assertTrue(leases.permissionFootprint(a,motion,false,1).isEmpty(),"Movement has no block write footprint");
        helper.assertTrue(leases.permissionFootprint(a,motion,true,1).containsAll(body),"Permission checks include the occupied body cells");
        reject(helper,()->leases.acquireMovement(b,owner,1,1,body,floor,1),"Two workers cannot reserve the same swept body space");
        reject(helper,()->leases.acquire(b,owner,1,1,ProjectAccess.Permission.BUILD,body,java.util.Set.of(),1),"A builder cannot edit an occupied route");
        reject(helper,()->leases.acquire(b,owner,1,1,ProjectAccess.Permission.BUILD,java.util.Set.of(new BlockPos(8,0,0)),body,1),"A test read cannot overlap exclusive movement");
        reject(helper,()->leases.validateOccupancy(a,motion,owner,1,1,java.util.Set.of(new BlockPos(2,0,0)),1),"Movement cannot expand its captured route");
        leases.release(a,motion);
        var build=leases.acquire(b,owner,1,1,ProjectAccess.Permission.BUILD,body,floor,2);
        reject(helper,()->leases.acquireMovement(a,owner,1,1,body,floor,2),"A route cannot overlap an existing builder");
        leases.release(b,build);
        var fresh=leases.acquireMovement(a,owner,1,1,body,floor,3);
        reject(helper,()->leases.validateOccupancy(a,motion,owner,1,1,body,3),"Replacing a movement lease invalidates its old fence");
        leases.revoke(a);
        reject(helper,()->leases.validateOccupancy(a,fresh,owner,1,1,body,4),"Revocation denies movement as well as actions");
        reject(helper,()->leases.acquireMovement(a,owner,1,1,java.util.Set.of(),floor,4),"Empty occupancy is not a movement assignment");
        helper.succeed();
    }
    @GameTest public void naturalFollowupWirePreservesExplicitProjectTarget(GameTestHelper helper){
        var body=new com.google.gson.JsonObject();var recent=new com.google.gson.JsonObject();recent.addProperty("action","status");recent.add("componentId",com.google.gson.JsonNull.INSTANCE);body.add("recentAction",recent);
        var decoded=com.google.gson.JsonParser.parseString(Companion.encodeBody("/v2/interpret",body)).getAsJsonObject().getAsJsonObject("recentAction");
        helper.assertTrue(decoded.keySet().equals(java.util.Set.of("action","componentId")) && decoded.get("componentId").isJsonNull(),"The actual HTTP encoder must preserve a project action's explicit null target for strict companion validation");
        helper.assertTrue(decoded.get("action").getAsString().equals("status"),"Encoding preserves the last typed intention without adding authority");helper.succeed();
    }

    @GameTest public void naturalChoicesCannotInventAuthorityOrTargets(GameTestHelper helper){
        var value=com.google.gson.JsonParser.parseString("{\"schemaVersion\":1,\"kind\":\"action\",\"action\":\"build\",\"componentId\":\"entrance\",\"text\":\"Build this part\",\"basis\":[\"component:entrance\"]}").getAsJsonObject();
        var choice=NaturalActions.read(value,"entrance",true);
        helper.assertTrue(choice.needsConsent() && choice.action().equals("build"),"Inferred world work requires separate consent");
        reject(helper,()->NaturalActions.read(value,"other",true),"An uncertain target cannot be silently selected");
        reject(helper,()->NaturalActions.read(value,"entrance",false),"No design means no build choice");
        for(String field:java.util.List.of("command","coordinates","permissions","nonce","requiresDelegation","verified")){
            var forged=value.deepCopy();forged.addProperty(field,"approved");
            reject(helper,()->NaturalActions.read(forged,"entrance",true),"Model fields cannot add authority: "+field);
        }
        var wrong=value.deepCopy();wrong.addProperty("action","run_shell");reject(helper,()->NaturalActions.read(wrong,"entrance",true),"Only known actions dispatch");
        var mixed=value.deepCopy();mixed.addProperty("kind","clarification");reject(helper,()->NaturalActions.read(mixed,"entrance",true),"Clarification cannot contain an action");
        var citation=value.deepCopy();citation.add("basis",com.google.gson.JsonParser.parseString("[\"inventory:other\"]"));reject(helper,()->NaturalActions.read(citation,"entrance",true),"Private inventory is not supplied context");
        helper.succeed();
    }
    @GameTest public void naturalAdviceAndStatusCarryNoWorldConsent(GameTestHelper helper){
        var advice=com.google.gson.JsonParser.parseString("{\"schemaVersion\":1,\"kind\":\"clarification\",\"action\":null,\"componentId\":null,\"text\":\"Which component?\",\"basis\":[]}").getAsJsonObject();
        helper.assertTrue(!NaturalActions.read(advice,null,false).needsConsent(),"A clarification has no action");
        advice.addProperty("kind","action");advice.addProperty("action","status");advice.add("basis",com.google.gson.JsonParser.parseString("[\"project\"]"));
        helper.assertTrue(!NaturalActions.read(advice,null,false).needsConsent(),"Status can be interpreted before a design exists");
        advice.addProperty("action","design");helper.assertTrue(NaturalActions.read(advice,null,false).needsConsent(),"New AI planning requires separate consent before dispatch");
        advice.addProperty("action","plan");helper.assertTrue(NaturalActions.read(advice,null,false).needsConsent(),"Catalog selection also requires explicit consent before its extra request");
        var substituted=advice.deepCopy();substituted.addProperty("request","Build something the player never requested");reject(helper,()->NaturalActions.read(substituted,null,false),"The model cannot replace the original design requirements");helper.succeed();
    }
    @GameTest public void savedProjectSearchUsesLiteralIntentionWithoutInference(GameTestHelper helper){
        var plan=Blueprint.reference("search-proof","minecraft:overworld",BlockPos.ZERO,0);
        var id=java.util.UUID.randomUUID();
        helper.assertTrue(ProjectSearch.matches(id,"minecraft:overworld",plan,ProjectSearch.terms("OVERWORLD")),"Dimension lookup ignores case");
        helper.assertTrue(ProjectSearch.matches(id,"minecraft:overworld",plan,ProjectSearch.terms(plan.name())),"Saved blueprint name is retrievable");
        helper.assertTrue(ProjectSearch.matches(id,"minecraft:overworld",plan,ProjectSearch.terms(id.toString())),"Exact identity remains searchable");
        helper.assertTrue(!ProjectSearch.matches(id,"minecraft:overworld",plan,ProjectSearch.terms("overworld absent-search-word")),"Every requested word must occur; no invented semantic match");
        helper.assertTrue(ProjectSearch.matches(id,"minecraft:overworld",null,ProjectSearch.terms("new project")),"Undesigned saved projects remain findable");
        reject(helper,()->ProjectSearch.terms("   "),"Empty searches cannot enumerate accidentally");
        reject(helper,()->ProjectSearch.terms("x".repeat(161)),"Query work stays bounded");
        reject(helper,()->ProjectSearch.terms("red\nstone"),"Control characters cannot inject chat output");helper.succeed();
    }
    @GameTest public void largePreviewPrioritizesCurrentMissingBlocksWithoutWorldReads(GameTestHelper helper){
        var ghosts=new java.util.ArrayList<ProjectView.Ghost>();
        for(int x=0;x<20;x++)for(int z=0;z<20;z++)ghosts.add(new ProjectView.Ghost(new BlockPos(x,100,z),Blocks.STONE.defaultBlockState(),false,false,false));
        var current=new ProjectView.Ghost(new BlockPos(40,100,0),Blocks.REPEATER.defaultBlockState(),true,false,false);ghosts.add(current);
        var far=new ProjectView.Ghost(new BlockPos(1000,100,0),Blocks.STONE.defaultBlockState(),true,false,false);ghosts.add(far);
        var complete=new ProjectView.Ghost(new BlockPos(0,101,0),Blocks.STONE.defaultBlockState(),true,true,false);ghosts.add(complete);
        var view=new ProjectView(java.util.UUID.randomUUID(),"preview-proof","minecraft:overworld","project","epoch",1,1,1,0,"Preview",ghosts);
        var selected=PreviewSelection.nearby(view,new net.minecraft.world.phys.Vec3(.5,100.5,.5));
        helper.assertTrue(selected.size()==PreviewSelection.MAX_VISIBLE_BLOCKS && selected.contains(current),"A large preview stays bounded while current missing work outranks closer later steps");
        helper.assertTrue(!selected.contains(far) && !selected.contains(complete),"Distant and completed geometry cannot displace nearby missing work");
        helper.assertTrue(PreviewSelection.nearby(null,net.minecraft.world.phys.Vec3.ZERO).isEmpty(),"Clearing an approved preview leaves no selectable rendering data");helper.succeed();
    }
    @GameTest
    public void legacyAdoptionIsPrivateAndPaused(GameTestHelper helper){
        var owner=java.util.UUID.randomUUID();var id=java.util.UUID.randomUUID();var source=new com.google.gson.JsonObject();
        source.addProperty("schemaVersion",1);source.addProperty("worldId","old");source.addProperty("dimension","minecraft:overworld");source.addProperty("owner",owner.toString());
        source.addProperty("status","EXECUTING");source.addProperty("verified","passed:old");source.addProperty("stage",0);
        source.add("blueprint",Blueprint.reference("old","minecraft:overworld",BlockPos.ZERO,0).source());
        var journal=new com.google.gson.JsonArray();var change=new com.google.gson.JsonObject();change.add("pos",Blueprint.jsonPos(BlockPos.ZERO));change.addProperty("before","minecraft:air");change.addProperty("after","minecraft:stone");journal.add(change);source.add("journal",journal);
        source.add("functionalScope",new com.google.gson.JsonObject());
        source.add("shutterRepairs",new com.google.gson.JsonArray());
        var adopted=LegacyAdoption.convert(source,"old","new","minecraft:overworld",owner,id);
        helper.assertTrue(!adopted.has("shutterRepairs"),"Empty optional repair history carries no immutable facts into legacy adoption");
        var withRepair=source.deepCopy();var repairFacts=new com.google.gson.JsonArray();repairFacts.add(new com.google.gson.JsonObject());withRepair.add("shutterRepairs",repairFacts);
        boolean repairRejected=false;try{LegacyAdoption.convert(withRepair,"old","new","minecraft:overworld",owner,id);}catch(IllegalArgumentException expected){repairRejected=true;}
        helper.assertTrue(repairRejected,"Nonempty mechanical repair history cannot be relabelled under an adopted identity");
        helper.assertTrue(adopted.get("status").getAsString().equals("PAUSED") && adopted.get("controller").getAsString().equals(owner.toString()),"Historical execution never becomes an active delegation");
        helper.assertTrue(!adopted.get("verified").getAsString().startsWith("passed:") && adopted.getAsJsonArray("journal").equals(journal),"Preserve undo journal while invalidating old evidence");
        helper.assertTrue(ProjectAccess.read(owner,adopted.getAsJsonObject("access")).members().size()==1,"No legacy file grants friends access");
        helper.assertTrue(!adopted.has("functionalScope") && adopted.getAsJsonArray("functionalEvidence").isEmpty(),"Adoption never relabels captured results under the new identity");
        var interrupted=source.deepCopy();var inputs=new com.google.gson.JsonArray();inputs.add(new com.google.gson.JsonObject());interrupted.add("testInputs",inputs);
        reject(helper,()->LegacyAdoption.convert(interrupted,"old","new","minecraft:overworld",owner,id),"Versioned pending input recovery must remain with its original identity");
        helper.assertTrue(adopted.getAsJsonObject("blueprint").get("projectId").getAsString().equals(id.toString()),"Intended identity moves with its new server project");
        helper.assertTrue(source.get("worldId").getAsString().equals("old") && source.get("status").getAsString().equals("EXECUTING"),"Conversion leaves the recovery source unchanged");
        reject(helper,()->LegacyAdoption.convert(source,"old","new","minecraft:overworld",java.util.UUID.randomUUID(),id),"Another player's checkpoint cannot be claimed");
        reject(helper,()->LegacyAdoption.convert(source,"different","new","minecraft:overworld",owner,id),"Another world path cannot be silently adopted");
        var mixed=source.deepCopy();mixed.addProperty("journalVersion",4);mixed.add("operationScopes",new com.google.gson.JsonObject());
        reject(helper,()->LegacyAdoption.convert(mixed,"old","new","minecraft:overworld",owner,id),"Immutable v4 history cannot be silently relabelled by legacy identity adoption");
        helper.succeed();
    }
    @GameTest
    public void operationScopesFreezeIntentWithoutGrantingAuthority(GameTestHelper helper){
        var plan=Blueprint.reference("test","minecraft:overworld",BlockPos.ZERO,0);
        var operation=java.util.UUID.randomUUID().toString();
        var second=java.util.UUID.randomUUID().toString();
        var scopes=new OperationScopes();
        scopes.capture(operation,"root",plan,false);scopes.capture(second,"root",plan,false);
        var saved=scopes.json();
        helper.assertTrue(saved.getAsJsonArray("snapshots").size()==1 && saved.getAsJsonArray("operations").size()==2,"Identical immutable intention is stored once across operation groups");
        plan.source().addProperty("name","Changed mutable source after delegation");
        helper.assertTrue(scopes.json().equals(saved),"An operation's captured intention cannot change through Blueprint.source access");
        var project=java.util.UUID.fromString(plan.projectId());
        var restored=OperationScopes.read(saved,"test","minecraft:overworld",project);
        restored.validate(operation,"root",BlockPos.ZERO);
        reject(helper,()->restored.validate(operation,"root",new BlockPos(40,0,0)),"Frozen scope refuses an unrecorded target");
        reject(helper,()->restored.validate(operation,"other",BlockPos.ZERO),"Operation scope cannot be relabelled as another component");
        var forged=saved.deepCopy();forged.getAsJsonArray("snapshots").get(0).getAsJsonObject().addProperty("id","0".repeat(64));
        reject(helper,()->OperationScopes.read(forged,"test","minecraft:overworld",project),"A changed immutable snapshot fails its content digest");
        var duplicate=saved.deepCopy();duplicate.getAsJsonArray("operations").add(duplicate.getAsJsonArray("operations").get(0).deepCopy());
        reject(helper,()->OperationScopes.read(duplicate,"test","minecraft:overworld",project),"Repeated operation identities are not silently overwritten");
        var unknown=saved.deepCopy();unknown.addProperty("schemaVersion",99);
        reject(helper,()->OperationScopes.read(unknown,"test","minecraft:overworld",project),"Unknown scope versions fail closed");
        reject(helper,()->OperationScopes.read(saved,"test","minecraft:overworld",java.util.UUID.randomUUID()),"Frozen intentions cannot move between projects");
        restored.retain(java.util.Set.of(operation));
        helper.assertTrue(restored.json().getAsJsonArray("operations").size()==1 && restored.json().getAsJsonArray("snapshots").size()==1,"Pruning an operation retains a shared snapshot still in use");
        restored.retain(java.util.Set.of());
        helper.assertTrue(restored.json().getAsJsonArray("snapshots").isEmpty(),"Unreferenced intention snapshots do not accumulate");
        helper.succeed();
    }
    @GameTest
    public void serverProjectPolicyAndLeases(GameTestHelper helper){
        var owner=java.util.UUID.randomUUID();var editor=java.util.UUID.randomUUID();var viewer=java.util.UUID.randomUUID();
        var policy=new ProjectAccess(owner);
        policy.member(owner,editor,ProjectAccess.Role.EDITOR);policy.member(owner,viewer,ProjectAccess.Role.VIEWER);
        helper.assertTrue(policy.allows(editor,ProjectAccess.Permission.BUILD),"An explicitly shared editor can delegate");
        helper.assertTrue(!policy.allows(editor,ProjectAccess.Permission.UNDO) && !policy.allows(editor,ProjectAccess.Permission.PLAN),"Building does not silently grant removal or account quota");
        reject(helper,()->policy.require(viewer,ProjectAccess.Permission.BUILD),"A viewer cannot build");
        reject(helper,()->policy.member(editor,viewer,ProjectAccess.Role.EDITOR),"An editor cannot escalate membership");
        reject(helper,()->policy.member(owner,viewer,ProjectAccess.Role.OWNER),"Membership cannot replace the immutable owner");
        policy.permission(owner,editor,ProjectAccess.Permission.UNDO,true);
        var restored=ProjectAccess.read(owner,policy.json());
        helper.assertTrue(restored.allows(editor,ProjectAccess.Permission.UNDO),"Explicit rights persist independently of role defaults");
        var epoch=java.util.UUID.randomUUID();var leases=new RegionReservations(epoch);
        var a=new RegionReservations.Scope("world","minecraft:overworld",java.util.UUID.randomUUID());
        var b=new RegionReservations.Scope("world","minecraft:overworld",java.util.UUID.randomUUID());
        var first=leases.acquire(a,editor,1,policy.revision(),ProjectAccess.Permission.BUILD,java.util.Set.of(BlockPos.ZERO),java.util.Set.of(new BlockPos(1,0,0)),0);
        reject(helper,()->leases.acquire(b,owner,1,1,ProjectAccess.Permission.BUILD,java.util.Set.of(new BlockPos(1,0,0)),java.util.Set.of(),1),"A neighboring write cannot invalidate another task's read dependency");
        leases.acquire(b,owner,1,1,ProjectAccess.Permission.BUILD,java.util.Set.of(new BlockPos(10,0,0)),java.util.Set.of(),1);
        leases.validate(a,first,editor,1,policy.revision(),2);
        helper.assertTrue(leases.permissionFootprint(a,first,true,2).equals(java.util.Set.of(BlockPos.ZERO,new BlockPos(1,0,0))),"Live permission checks use the lease's captured writes and isolation reads");
        reject(helper,()->leases.permissionFootprint(a,first,true,2).add(new BlockPos(2,0,0)),"Callers cannot expand a captured lease footprint");
        leases.validateWrite(a,first,editor,1,policy.revision(),BlockPos.ZERO,2);
        reject(helper,()->leases.validateWrite(a,first,editor,1,policy.revision(),new BlockPos(1,0,0),2),"A reserved read dependency never grants write authority");
        reject(helper,()->leases.validateWrite(a,first,editor,1,policy.revision(),new BlockPos(2,0,0),2),"A valid lease never authorizes an unreserved block in its workspace");
        reject(helper,()->leases.validateWrite(b,first,editor,1,policy.revision(),BlockPos.ZERO,2),"Reserved positions cannot be replayed under another project scope");
        policy.revoke(owner,editor);
        reject(helper,()->leases.validateWrite(a,first,editor,1,policy.revision(),BlockPos.ZERO,3),"Write membership cannot bypass a changed grant revision");
        reject(helper,()->leases.validate(a,first,editor,1,policy.revision(),3),"Revocation fences already-issued work");
        reject(helper,()->new RegionReservations(java.util.UUID.randomUUID()).validate(a,first,editor,1,1,4),"A restarted server never restores an old lease");
        reject(helper,()->leases.validate(b,first,owner,1,1,4),"A lease cannot be replayed for another project or actor");
        reject(helper,()->leases.validate(a,first,editor,1,first.authorizationRevision(),200),"Expired work needs explicit reconciliation");
        helper.succeed();
    }
    @GameTest
    public void boundedOverlayWireFormat(GameTestHelper helper){
        var recipient=java.util.UUID.randomUUID();
        var view=new ProjectView(recipient,"world","minecraft:overworld","project","epoch",1,1,1,0,"Private lesson",
                java.util.List.of(new ProjectView.Ghost(BlockPos.ZERO,Blocks.STONE.defaultBlockState(),true,false,false)));
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),helper.getLevel().registryAccess());
        try {
            EngineerNetwork.SnapshotPayload.CODEC.encode(buffer,new EngineerNetwork.SnapshotPayload(1,view));
            var decoded=EngineerNetwork.SnapshotPayload.CODEC.decode(buffer);
            helper.assertTrue(decoded.view().recipient().equals(recipient) && decoded.view().blocks().size()==1,"Presentation retains its exact authorized recipient and bounded geometry");
            buffer.clear();buffer.writeVarInt(1);buffer.writeByte(7);
            reject(helper,()->EngineerNetwork.HelloPayload.CODEC.decode(buffer),"Hello cannot smuggle actor or command data after its protocol field");
        } finally {buffer.release();}
        helper.succeed();
    }
    private static void reject(GameTestHelper helper,Runnable action,String message){
        boolean rejected=false;try{action.run();}catch(RuntimeException expected){rejected=true;}helper.assertTrue(rejected,message);
    }
    @GameTest(maxTicks=50)
    public void signalLamp(GameTestHelper helper){
        Blueprint plan=Blueprint.reference("test","minecraft:overworld",new BlockPos(2,2,2),0);
        for(Blueprint.PlannedBlock b:plan.blocks())helper.setBlock(b.pos(),b.state());
        BlockPos input=new BlockPos(2,3,2),output=new BlockPos(4,3,2);
        helper.runAtTickTime(5,()->{
            helper.assertBlockProperty(output,BlockStateProperties.LIT,false);
            helper.setBlock(input,Blueprint.state("minecraft:lever[face=floor,facing=east,powered=true]"));
        });
        helper.runAtTickTime(13,()->{
            helper.assertBlockProperty(output,BlockStateProperties.LIT,true);
            helper.setBlock(input,Blueprint.state("minecraft:lever[face=floor,facing=east,powered=false]"));
        });
        helper.runAtTickTime(23,()->{helper.assertBlockProperty(output,BlockStateProperties.LIT,false);helper.succeed();});
    }
    @GameTest
    public void rotationAndValidation(GameTestHelper helper){
        Blueprint plan=Blueprint.reference("test","minecraft:overworld",BlockPos.ZERO,90);
        var repeater=plan.blocks().stream().filter(b->b.state().is(Blocks.REPEATER)).findFirst().orElseThrow();
        helper.assertTrue(repeater.pos().equals(new BlockPos(0,1,1)),"Coordinates rotate with the blueprint");
        helper.assertTrue(repeater.state().equals(Blueprint.state("minecraft:repeater[facing=west,delay=1,locked=false,powered=false]").rotate(Rotation.CLOCKWISE_90)),"Directional state rotates with coordinates");
        boolean rejected=false;try{Blueprint.state("minecraft:repeater[delay=99]");}catch(IllegalArgumentException e){rejected=true;}
        helper.assertTrue(rejected,"Unsupported block-state values must fail closed");
        helper.assertTrue(!Blueprint.matches(Blueprint.state("minecraft:repeater[facing=west]"),Blueprint.state("minecraft:repeater[facing=east]")),"Wrong orientations are construction mistakes");
        helper.assertTrue(Blueprint.matches(Blueprint.state("minecraft:redstone_lamp[lit=false]"),Blueprint.state("minecraft:redstone_lamp[lit=true]")),"Runtime signals do not create false construction conflicts");
        Blueprint moved=plan.transformed(new BlockPos(5,6,7),180);
        helper.assertTrue(moved.revision()==plan.revision()+1 && !moved.id().equals(plan.id()),"Geometry revisions must not reuse test evidence identity");
        helper.succeed();
    }
    @GameTest(maxTicks=50)
    public void rotatedSignalLamp(GameTestHelper helper){
        Blueprint plan=Blueprint.reference("test","minecraft:overworld",new BlockPos(3,2,2),90);
        for(Blueprint.PlannedBlock b:plan.blocks())helper.setBlock(b.pos(),b.state());
        BlockPos input=new BlockPos(3,3,2),output=new BlockPos(3,3,4);
        var lever=plan.blocks().stream().filter(b->b.pos().equals(input)).findFirst().orElseThrow().state();
        helper.runAtTickTime(5,()->{helper.assertBlockProperty(output,BlockStateProperties.LIT,false);helper.setBlock(input,lever.setValue(BlockStateProperties.POWERED,true));});
        helper.runAtTickTime(13,()->{helper.assertBlockProperty(output,BlockStateProperties.LIT,true);helper.setBlock(input,lever.setValue(BlockStateProperties.POWERED,false));});
        helper.runAtTickTime(23,()->{helper.assertBlockProperty(output,BlockStateProperties.LIT,false);helper.succeed();});
    }
    @GameTest
    public void rejectsWrongWorldAndUnsafeBlocks(GameTestHelper helper){
        Blueprint plan=Blueprint.reference("test","minecraft:overworld",BlockPos.ZERO,0);
        boolean wrongWorld=false;try{Blueprint.parse(plan.source(),"different","minecraft:overworld");}catch(IllegalArgumentException e){wrongWorld=true;}
        helper.assertTrue(wrongWorld,"A blueprint from another world must be rejected");
        var json=plan.source().deepCopy();json.getAsJsonArray("blocks").get(0).getAsJsonObject().addProperty("state","minecraft:chest");
        boolean inventory=false;try{Blueprint.parse(json,"test","minecraft:overworld");}catch(IllegalArgumentException e){inventory=true;}
        helper.assertTrue(inventory,"Inventory blueprints must be rejected");
        json=plan.source().deepCopy();json.getAsJsonArray("blocks").get(0).getAsJsonObject().addProperty("state","minecraft:tnt");
        boolean unsafe=false;try{Blueprint.parse(json,"test","minecraft:overworld");}catch(IllegalArgumentException e){unsafe=true;}
        helper.assertTrue(unsafe,"Blocks outside the safe library must be rejected");
        var overflow=plan.source().deepCopy();overflow.getAsJsonArray("blocks").get(0).getAsJsonObject().getAsJsonObject("pos").addProperty("x",Integer.MIN_VALUE);
        reject(helper,()->Blueprint.parse(overflow,"test","minecraft:overworld"),"Integer overflow cannot bypass relative extent limits");
        var fractional=plan.source().deepCopy();fractional.getAsJsonObject("anchor").addProperty("x",0.5);
        reject(helper,()->Blueprint.parse(fractional,"test","minecraft:overworld"),"Fractional coordinates cannot be silently truncated");
        var huge=plan.source().deepCopy();huge.addProperty("name","x".repeat(1025));
        reject(helper,()->Blueprint.parse(huge,"test","minecraft:overworld"),"Unbounded model text cannot break authorized preview packets");
        helper.succeed();
    }
}
