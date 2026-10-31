package dev.minecraftai.engineer;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Actual Minecraft semantics; executed by GitHub CI, never substitutes for client acceptance. */
public final class EngineerGameTests {
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
