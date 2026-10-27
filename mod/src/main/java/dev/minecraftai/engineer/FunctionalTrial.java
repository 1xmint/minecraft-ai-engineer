package dev.minecraftai.engineer;

import com.google.gson.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Server-thread truth tables and per-input write-ahead restoration, never persisted authority. */
final class FunctionalTrial {
    private record Input(String name,BlockPos pos,BlockState original,BlockState last,BlockState before,boolean pending) {}
    private final EngineeringSession session;
    private final Map<String,Input> inputs=new LinkedHashMap<>();
    private JsonArray evidence=new JsonArray();
    private MechanismCatalog.Contract contract;
    private int vector,nextTick;
    FunctionalTrial(EngineeringSession session){this.session=session;}
    boolean pending(){return !inputs.isEmpty();}
    JsonArray evidence(){return evidence.deepCopy();}
    void resetEvidence(){if(pending())throw new IllegalStateException("Pending inputs must be reconciled before revising the design");archiveEvidence();evidence=new JsonArray();contract=null;}
    private void archiveEvidence(){
        if(evidence.isEmpty())return;var s=session;
        if(s.blueprint==null)throw new IllegalStateException("Stored test evidence needs its original intended identity");
        var history=new JsonObject();history.addProperty("worldId",s.worldId);history.addProperty("dimension",s.dimension);history.addProperty("projectId",s.projectId.toString());
        history.addProperty("blueprintId",s.blueprint.id());history.addProperty("blueprintRevision",s.revision());history.addProperty("verificationAtArchive",s.verified);
        history.addProperty("archivedAtTick",s.level().getGameTime());history.add("cases",evidence.deepCopy());
        history.add("blueprint",s.blueprint.source().deepCopy());
        LocalFiles.write(s.file.resolveSibling(s.projectId+".evidence-"+UUID.randomUUID()+".json"),history);
    }

    void start(){
        var s=session;s.access.require(s.actor(),ProjectAccess.Permission.BUILD);s.requireCreative();
        if(pending())throw new IllegalStateException("Reconcile all interrupted test inputs with resume before testing again");
        if(s.blueprint==null || s.state.equals("EXECUTING") || s.testTick>=0)throw new IllegalStateException("Complete and stop building before testing");
        var candidate=MechanismCatalog.contract(s.blueprint);s.validateTestArea();
        var captured=new LinkedHashMap<String,Input>();
        for(var port:candidate.ports())if(port.input()){
            s.validateTarget(port.pos());BlockState actual=s.level().getBlockState(port.pos());
            if(!actual.is(Blocks.LEVER))throw new IllegalStateException("Catalog input is not a lever");
            captured.put(port.name(),new Input(port.name(),port.pos(),actual,actual,actual,false));
        }
        archiveEvidence();s.claim(ProjectAccess.Permission.BUILD,true);
        contract=candidate;inputs.putAll(captured);evidence=new JsonArray();vector=-1;nextTick=contract.settleTicks();
        s.testTick=0;s.verified="untested";s.state="VERIFYING";s.detail="Tick-accurate "+contract.template()+" truth table running";
        s.save();s.checkpoint();
        s.tell(s.detail);
    }
    void tick(){
        var s=session;
        try{
            s.validateMutation();s.requireCreative();s.validateTestArea();
            for(var input:inputs.values())if(input.pending() || !s.level().getBlockState(input.pos()).equals(input.last()))throw new IllegalStateException("Player changed test input "+input.name()+"; edit preserved");
            if(s.testTick>=nextTick){
                // Let pending construction updates settle before the first input
                // transition; no case is claimed from the initial world state.
                if(vector<0){vector=0;nextTick+=contract.settleTicks();apply(contract.cases().getFirst().inputs());s.testTick++;return;}
                observe(contract.cases().get(vector));s.save();
                if(vector==contract.cases().size()-1){
                    int elapsed=s.testTick;restore();
                    if(pending())throw new IllegalStateException("Inputs still need safe restoration; no functional pass was recorded");
                    s.verified="passed:"+contract.template()+":"+evidence.size()+" vectors@"+elapsed;
                    s.state="COMPLETED";s.detail="Actual Minecraft functional test passed: "+evidence.size()+" input vectors over "+elapsed+" ticks";
                    s.manager.release(s);s.save();s.checkpoint();s.tell(s.detail);return;
                }
                vector++;nextTick+=contract.settleTicks();apply(contract.cases().get(vector).inputs());
            }
            s.testTick++;
        }catch(RuntimeException failure){s.verified="failed: "+failure.getMessage();s.pause("Functional test failed: "+failure.getMessage());}
    }
    private void observe(MechanismCatalog.Vector expected){
        var s=session;var row=new JsonObject();row.addProperty("case",vector);row.addProperty("tick",s.level().getGameTime());row.addProperty("elapsedTicks",s.testTick);
        var observedInputs=new JsonObject();var observedOutputs=new JsonObject();
        for(var port:contract.ports()){
            BlockState actual=s.level().getBlockState(port.pos());boolean value=actual.getValue(port.input()?BlockStateProperties.POWERED:BlockStateProperties.LIT);
            boolean wanted=(port.input()?expected.inputs():expected.outputs()).get(port.name());
            if(value!=wanted)throw new IllegalStateException("Port "+port.name()+" expected "+wanted+", observed "+value+" at test tick "+s.testTick);
            (port.input()?observedInputs:observedOutputs).addProperty(port.name(),value);
        }
        row.add("inputs",observedInputs);row.add("outputs",observedOutputs);evidence.add(row);
    }
    private void apply(Map<String,Boolean> values){
        var s=session;
        for(var old:new ArrayList<>(inputs.values())){
            s.validateMutation();s.validateTestArea();s.validateTarget(old.pos());
            BlockState actual=s.level().getBlockState(old.pos());
            if(old.pending() || !actual.equals(old.last()))throw new IllegalStateException("Input changed before a test transition: "+old.name());
            BlockState wanted=actual.setValue(BlockStateProperties.POWERED,values.get(old.name()));
            if(actual.equals(wanted))continue;
            inputs.put(old.name(),new Input(old.name(),old.pos(),old.original(),wanted,actual,true));s.save();
            activate(old.pos(),actual,wanted,ProjectAccess.Permission.BUILD);
            inputs.put(old.name(),new Input(old.name(),old.pos(),old.original(),wanted,actual,false));s.save();
        }
    }
    private void activate(BlockPos pos,BlockState before,BlockState wanted,ProjectAccess.Permission action){
        var s=session;s.validateMutation(action);s.requireCreative();s.validateTarget(pos);
        if(!s.level().getBlockState(pos).equals(before) || !before.is(Blocks.LEVER) || !wanted.is(Blocks.LEVER))throw new IllegalStateException("Lever changed before authorized activation");
        if(!before.equals(wanted))((LeverBlock)before.getBlock()).pull(before,s.level(),pos,s.server.getPlayerList().getPlayer(s.controller));
        if(!s.level().getBlockState(pos).equals(wanted))throw new IllegalStateException("Minecraft rejected the test input; restoration journal retained");
    }
    void restore(){
        var s=session;s.testTick=-1;
        if(!pending() || !s.server.isSameThread() || !s.available() || !s.manager.mayMutate(s))return;
        for(var input:new ArrayList<>(inputs.values())){
            try{
                var action=s.delegation.action();s.validateMutation(action);s.requireCreative();s.validateTarget(input.pos());
                BlockState actual=s.level().getBlockState(input.pos());
                if(actual.equals(input.original())){inputs.remove(input.name());s.save();continue;}
                if(!actual.equals(input.last())){
                    // A crash before a write cannot be distinguished from a player
                    // reverting to its before-state. Keep ambiguous recovery paused.
                    if(input.pending() && actual.equals(input.before()))continue;
                    inputs.remove(input.name());s.save();continue; // observed player edit: never overwrite
                }
                s.validateTestArea();
                inputs.put(input.name(),new Input(input.name(),input.pos(),input.original(),input.original(),actual,true));s.save();
                activate(input.pos(),actual,input.original(),action);
                inputs.remove(input.name());s.save();
            }catch(RuntimeException unavailable){return; /* Preserve the remaining journal; stop on lost authority or checkpoint failure. */}
        }
    }
    JsonArray inputsSnapshot(){
        var entries=new JsonArray();
        for(var input:inputs.values()){
            var entry=new JsonObject();entry.addProperty("name",input.name());entry.add("pos",Blueprint.jsonPos(input.pos()));
            entry.addProperty("original",Blueprint.describe(input.original()));entry.addProperty("last",Blueprint.describe(input.last()));
            entry.addProperty("before",Blueprint.describe(input.before()));entry.addProperty("pending",input.pending());entries.add(entry);
        }
        return entries;
    }
    void write(JsonObject json){
        json.add("testInputs",inputsSnapshot());json.add("functionalEvidence",evidence.deepCopy());
        // Readable legacy alias for the first remaining input; the array owns recovery.
        if(pending()){var first=inputs.values().iterator().next();json.addProperty("testOriginal",Blueprint.describe(first.original()));json.addProperty("testLast",Blueprint.describe(first.last()));}
    }
    void load(JsonObject json){
        inputs.clear();evidence=json.has("functionalEvidence")?json.getAsJsonArray("functionalEvidence").deepCopy():new JsonArray();
        if(evidence.size()>128)throw new IllegalStateException("Too much functional evidence");
        JsonArray entries=json.has("testInputs")?json.getAsJsonArray("testInputs"):null;
        if(entries==null && !json.has("testOriginal"))return;
        if(entries!=null && entries.isEmpty()){if(json.has("testOriginal"))throw new IllegalStateException("Conflicting test recovery formats");return;}
        contract=MechanismCatalog.contract(session.blueprint);
        if(entries==null){
            var port=contract.ports().stream().filter(MechanismCatalog.Port::input).findFirst().orElseThrow();
            if(contract.ports().stream().filter(MechanismCatalog.Port::input).count()!=1)throw new IllegalStateException("Scalar test recovery cannot authorize a multi-input mechanism");
            BlockState original=Blueprint.state(json.get("testOriginal").getAsString()),last=Blueprint.state(json.get("testLast").getAsString());
            putLoaded(new Input(port.name(),port.pos(),original,last,last.cycle(BlockStateProperties.POWERED),true));
        }else{
            if(entries.size()>32)throw new IllegalStateException("Too many test inputs");
            for(var value:entries){var entry=value.getAsJsonObject();putLoaded(new Input(entry.get("name").getAsString(),Blueprint.pos(entry.getAsJsonObject("pos")),Blueprint.state(entry.get("original").getAsString()),Blueprint.state(entry.get("last").getAsString()),Blueprint.state(entry.get("before").getAsString()),entry.get("pending").getAsBoolean()));}
        }
    }
    private void putLoaded(Input input){
        var port=contract.ports().stream().filter(p->p.input() && p.name().equals(input.name()) && p.pos().equals(input.pos())).findFirst().orElseThrow(()->new IllegalStateException("Recovery input is not in its catalog contract"));
        BlockState planned=session.blueprint.blocks().stream().filter(b->b.pos().equals(port.pos())).findFirst().orElseThrow().state();
        if(inputs.containsKey(input.name()) || !input.original().is(Blocks.LEVER) || !Blueprint.matches(planned,input.original()) || !Blueprint.matches(planned,input.last()) || !Blueprint.matches(planned,input.before()))throw new IllegalStateException("Invalid recovery input state");
        inputs.put(input.name(),input);
    }
}
