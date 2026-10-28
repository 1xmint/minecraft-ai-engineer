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
    private JsonObject evidenceScope,evidenceBlueprint;
    private MechanismCatalog.Contract contract;
    private int vector,nextTick;
    private BehaviorSpec generatedBehavior;
    private TransitionTrace transition;
    private JsonObject generatedRow;
    private long startedGameTick,phaseStart,lastBaseline=-1;
    FunctionalTrial(EngineeringSession session){this.session=session;}
    boolean pending(){return !inputs.isEmpty();}
    JsonArray evidence(){return evidence.deepCopy();}
    JsonObject scope(){return evidenceScope==null?null:evidenceScope.deepCopy();}
    void resetEvidence(){if(pending())throw new IllegalStateException("Pending inputs must be reconciled before revising the design");archiveEvidence();evidence=new JsonArray();evidenceScope=null;evidenceBlueprint=null;contract=null;}
    private void archiveEvidence(){
        if(evidence.isEmpty())return;var s=session;
        if(evidenceScope==null || evidenceBlueprint==null)throw new IllegalStateException("Stored test evidence needs its original intended identity");
        var history=new JsonObject();history.addProperty("worldId",s.worldId);history.addProperty("dimension",s.dimension);
        history.addProperty("projectId",evidenceScope.get("projectId").getAsString());
        history.addProperty("blueprintId",evidenceScope.get("blueprintId").getAsString());history.addProperty("blueprintRevision",evidenceScope.get("blueprintRevision").getAsLong());history.addProperty("verificationAtArchive",s.verified);
        history.addProperty("archivedAtTick",s.level().getGameTime());history.add("cases",evidence.deepCopy());
        history.add("blueprint",evidenceBlueprint.deepCopy());history.addProperty("component",evidenceScope.get("component").getAsString());history.addProperty("componentBasis",evidenceScope.get("basis").getAsString());
        history.addProperty("componentBasisVersion",evidenceScope.get("basisVersion").getAsInt());history.add("functionalScope",evidenceScope.deepCopy());
        LocalFiles.write(s.file.resolveSibling(s.projectId+".evidence-"+UUID.randomUUID()+".json"),history);
    }

    void start(){
        var s=session;s.access.require(s.actor(),ProjectAccess.Permission.BUILD);s.requireCreative();
        if(pending())throw new IllegalStateException("Reconcile all interrupted test inputs with resume before testing again");
        if(s.blueprint==null || s.state.equals("EXECUTING") || s.testTick>=0)throw new IllegalStateException("Complete and stop building before testing");
        var candidate=EngineeringContracts.contract(s.executionBlueprint());s.validateTestArea();
        var captured=new LinkedHashMap<String,Input>();
        for(var port:candidate.ports())if(port.input()){
            s.validateTarget(port.pos());BlockState actual=s.level().getBlockState(port.pos());
            if(!actual.is(Blocks.LEVER))throw new IllegalStateException("Catalog input is not a lever");
            captured.put(port.name(),new Input(port.name(),port.pos(),actual,actual,actual,false));
        }
        archiveEvidence();s.claim(ProjectAccess.Permission.BUILD,true);
        contract=candidate;inputs.putAll(captured);evidence=new JsonArray();
        evidenceScope=makeScope(s.blueprint,s.activeComponent,ComponentAssignments.BASIS_VERSION);
        evidenceBlueprint=s.blueprint.source().deepCopy();vector=-1;nextTick=contract.settleTicks();
        generatedBehavior=GeneratedBlueprint.isGenerated(s.executionBlueprint())?GeneratedBlueprint.behavior(s.executionBlueprint()):null;
        generatedRow=null;transition=null;startedGameTick=s.level().getGameTime();lastBaseline=-1;
        s.testTick=0;s.verified="untested";s.state="VERIFYING";s.detail="Tick-accurate "+contract.template()+" truth table running";
        s.save();s.checkpoint();
        s.tell(s.detail);
    }
    void tick(){
        var s=session;
        try{
            s.validateMutation();s.requireCreative();s.validateTestArea();
            for(var input:inputs.values())if(input.pending() || !s.level().getBlockState(input.pos()).equals(input.last()))throw new IllegalStateException("Player changed test input "+input.name()+"; edit preserved");
            if(generatedBehavior!=null){tickGenerated();return;}
            if(s.testTick>=nextTick){
                // Let pending construction updates settle before the first input
                // transition; no case is claimed from the initial world state.
                if(vector<0){vector=0;nextTick+=contract.settleTicks();apply(contract.cases().getFirst().inputs());s.testTick++;return;}
                observe(contract.cases().get(vector));s.save();
                if(vector==contract.cases().size()-1){
                    int elapsed=s.testTick;restore();
                    if(pending())throw new IllegalStateException("Inputs still need safe restoration; no functional pass was recorded");
                    s.verified="passed:"+contract.template()+":"+evidence.size()+" vectors@"+elapsed;
                    ComponentAssignments.Task task=s.assignments.task(s.blueprint,evidenceScope.get("component").getAsString());
                    task.lastPassedScope=evidenceScope.deepCopy();
                    s.state="COMPLETED";s.detail="Actual Minecraft functional test passed: "+evidence.size()+" input vectors over "+elapsed+" ticks";
                    s.manager.release(s);s.save();s.checkpoint();s.tell(s.detail);return;
                }
                vector++;nextTick+=contract.settleTicks();apply(contract.cases().get(vector).inputs());
            }
            s.testTick++;
        }catch(RuntimeException failure){s.verified="failed: "+failure.getMessage();s.pause("Functional test failed: "+failure.getMessage());}
    }
    private void tickGenerated() {
        var s=session;long now=s.level().getGameTime();
        s.testTick=Math.toIntExact(now-startedGameTick);
        if(vector<0) {
            if(now-startedGameTick<contract.settleTicks())return;
            apply(Map.of("input",false));vector=0;phaseStart=now;lastBaseline=-1;
            generatedRow=generatedRow(0);generatedRow.add("baselineSamples",new JsonArray());evidence.add(generatedRow);
        }
        sampleGenerated(now);
        if(vector==0) {
            if(now-phaseStart<8)return;
            s.save();beginGeneratedEdge(now,true);return;
        }
        if(!transition.complete())return;
        s.save();
        if(!transition.passed())throw new IllegalStateException(transition.failure());
        if(vector==1){beginGeneratedEdge(now,false);return;}
        validateGeneratedEvidence(evidence,generatedBehavior,true);
        int elapsed=s.testTick;restore();
        if(pending())throw new IllegalStateException("Inputs still need safe restoration; no functional pass was recorded");
        s.verified="passed:generated-buffer:3 vectors@"+elapsed;
        var task=s.assignments.task(s.blueprint,evidenceScope.get("component").getAsString());task.lastPassedScope=evidenceScope.deepCopy();
        s.state="COMPLETED";s.detail="Actual Minecraft buffer test passed: stable off/on/off, rising "+generatedBehavior.risingGameTicks()+" and falling "+generatedBehavior.fallingGameTicks()+" game ticks";
        s.manager.release(s);s.save();s.checkpoint();s.tell(s.detail);
    }
    private JsonObject generatedRow(int index) {
        var row=new JsonObject();row.addProperty("case",index);row.addProperty("behaviorDigest",generatedBehavior.digest());
        row.add("inputs",new JsonObject());row.add("outputs",new JsonObject());return row;
    }
    private void beginGeneratedEdge(long now,boolean on) {
        apply(Map.of("input",on));vector=on?1:2;phaseStart=now;
        transition=new TransitionTrace(!on,on,on?generatedBehavior.risingGameTicks():generatedBehavior.fallingGameTicks(),8);
        generatedRow=generatedRow(vector);evidence.add(generatedRow);
        // Vanilla can update an adjacent lamp synchronously. The activation tick is sample zero.
        sampleGenerated(now);session.save();
    }
    private void sampleGenerated(long now) {
        var s=session;long elapsed=now-phaseStart;
        if(elapsed<0 || elapsed>(vector==0?8:transition.horizon()))throw new IllegalStateException("inconclusive: game-tick sampling window was missed");
        var input=contract.ports().stream().filter(MechanismCatalog.Port::input).findFirst().orElseThrow();
        var output=contract.ports().stream().filter(port->!port.input()).findFirst().orElseThrow();
        boolean actualInput=s.level().getBlockState(input.pos()).getValue(BlockStateProperties.POWERED);
        boolean actualOutput=s.level().getBlockState(output.pos()).getValue(BlockStateProperties.LIT);
        generatedRow.addProperty("tick",now);generatedRow.addProperty("elapsedTicks",elapsed);
        generatedRow.getAsJsonObject("inputs").addProperty("input",actualInput);generatedRow.getAsJsonObject("outputs").addProperty("output",actualOutput);
        if(vector==0) {
            var sample=new JsonObject();sample.addProperty("elapsedTicks",elapsed);sample.addProperty("observed",actualOutput);generatedRow.getAsJsonArray("baselineSamples").add(sample);
            if(elapsed!=lastBaseline+1)throw new IllegalStateException("inconclusive: missing baseline game-tick sample");
            lastBaseline=elapsed;
            if(actualOutput)throw new IllegalStateException("Output must remain off throughout the baseline");
        } else {transition.sample(elapsed,actualOutput);generatedRow.add("trace",transition.json());}
    }
    /** Recompute stored claims from bounded actual samples and independently accepted intent. */
    static void validateGeneratedEvidence(JsonArray cases,BehaviorSpec behavior,boolean requirePass) {
        if(cases.size()>3 || (requirePass && cases.size()!=3))throw new IllegalStateException("Generated evidence needs complete off/on/off phases");
        long previousTick=-1;
        for(int index=0;index<cases.size();index++) {
            JsonObject row=cases.get(index).getAsJsonObject();
            Set<String> fields=new HashSet<>(Set.of("case","tick","elapsedTicks","inputs","outputs","behaviorDigest",index==0?"baselineSamples":"trace"));
            if(!row.keySet().equals(fields) || row.get("case").getAsBigDecimal().intValueExact()!=index || !row.get("behaviorDigest").getAsString().equals(behavior.digest()))throw new IllegalStateException("Generated evidence differs from accepted intent");
            long tick=row.get("tick").getAsBigDecimal().longValueExact();int elapsed=row.get("elapsedTicks").getAsBigDecimal().intValueExact();
            if(tick<0 || elapsed<0 || (previousTick>=0 && tick-elapsed!=previousTick))throw new IllegalStateException("Generated evidence game-time phases disagree");
            JsonObject inputs=row.getAsJsonObject("inputs"),outputs=row.getAsJsonObject("outputs");
            if(!inputs.keySet().equals(Set.of("input")) || !outputs.keySet().equals(Set.of("output")) || !inputs.get("input").getAsJsonPrimitive().isBoolean() || !outputs.get("output").getAsJsonPrimitive().isBoolean() || inputs.get("input").getAsBoolean()!=(index==1))throw new IllegalStateException("Generated evidence input differs from its phase");
            if(index==0) {
                JsonArray samples=row.getAsJsonArray("baselineSamples");
                if(samples.isEmpty() || samples.size()>9 || (requirePass && samples.size()!=9))throw new IllegalStateException("Generated baseline is incomplete");
                int last=-1;boolean observed=false;
                for(var value:samples){var sample=value.getAsJsonObject();if(!sample.keySet().equals(Set.of("elapsedTicks","observed")) || !sample.get("observed").getAsJsonPrimitive().isBoolean())throw new IllegalStateException("Invalid baseline sample");int at=sample.get("elapsedTicks").getAsBigDecimal().intValueExact();observed=sample.get("observed").getAsBoolean();if(at<=last || at>8 || (requirePass && (at!=last+1 || observed)))throw new IllegalStateException("Baseline ticks cannot establish stable off behavior");last=at;}
                if(last!=elapsed || observed!=outputs.get("output").getAsBoolean())throw new IllegalStateException("Baseline summary differs from its samples");
            } else {
                var trace=TransitionTrace.read(row.getAsJsonObject("trace"));var source=trace.json();boolean on=index==1;
                if(source.get("from").getAsBoolean()==on || source.get("to").getAsBoolean()!=on || source.get("expectedGameTicks").getAsInt()!=(on?behavior.risingGameTicks():behavior.fallingGameTicks()) || source.get("stableGameTicks").getAsInt()!=8 || (requirePass && !trace.passed()))throw new IllegalStateException("Trace differs from the accepted edge timing");
                var samples=source.getAsJsonArray("samples");if(samples.isEmpty())throw new IllegalStateException("Trace has no samples");var last=samples.get(samples.size()-1).getAsJsonObject();if(last.get("elapsedTicks").getAsInt()!=elapsed || last.get("observed").getAsBoolean()!=outputs.get("output").getAsBoolean())throw new IllegalStateException("Trace summary differs from its samples");
            }
            previousTick=tick;
        }
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
        var s=session;s.validateMutation(action,pos);s.requireCreative();s.validateTarget(pos);
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
        if(evidenceScope!=null)json.add("functionalScope",evidenceScope.deepCopy());
        // Readable legacy alias for the first remaining input; the array owns recovery.
        if(pending()){var first=inputs.values().iterator().next();json.addProperty("testOriginal",Blueprint.describe(first.original()));json.addProperty("testLast",Blueprint.describe(first.last()));}
    }
    void load(JsonObject json){
        inputs.clear();evidence=json.has("functionalEvidence")?json.getAsJsonArray("functionalEvidence").deepCopy():new JsonArray();
        if(evidence.size()>128)throw new IllegalStateException("Too much functional evidence");
        validateCases(evidence);
        evidenceScope=null;evidenceBlueprint=null;
        if(json.has("functionalScope")) {
            evidenceScope=validateHistoricalScope(json.getAsJsonObject("functionalScope"));
            validateCurrentScope(evidenceScope);
            evidenceBlueprint=session.blueprint.source().deepCopy();
        } else if(!evidence.isEmpty() || (json.has("testInputs") && !json.getAsJsonArray("testInputs").isEmpty()) || json.has("testOriginal")) {
            if(json.has("componentAssignments"))for(JsonElement entry:json.getAsJsonArray("componentAssignments")) {
                JsonObject assignment=entry.getAsJsonObject();
                if(assignment.get("id").getAsString().equals(session.activeComponent) && assignment.has("basisVersion")
                        && assignment.get("basisVersion").getAsBigDecimal().intValueExact()>=2)
                    throw new IllegalStateException("Versioned functional evidence is missing its captured scope");
            }
            // Old buffers predate explicit scope. Bind them only to the exact saved intent,
            // and preserve the old v1 identity through archive instead of relabeling as v2.
            evidenceScope=makeScope(session.blueprint,session.activeComponent,1);
            evidenceBlueprint=session.blueprint.source().deepCopy();
            if(session.blueprint==null)throw new IllegalStateException("Legacy functional evidence has no intended blueprint");
        }
        if(session.blueprint!=null && GeneratedBlueprint.isGenerated(session.blueprint))validateGeneratedEvidence(evidence,GeneratedBlueprint.behavior(session.blueprint),session.verified.startsWith("passed:") || session.verified.startsWith("stale:"));
        JsonArray entries=json.has("testInputs")?json.getAsJsonArray("testInputs"):null;
        if(entries==null && !json.has("testOriginal"))return;
        if(entries!=null && entries.isEmpty()){if(json.has("testOriginal"))throw new IllegalStateException("Conflicting test recovery formats");return;}
        contract=EngineeringContracts.contract(session.executionBlueprint());
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

    private JsonObject makeScope(Blueprint blueprint,String component,int version) {
        if(blueprint==null)throw new IllegalStateException("Functional evidence needs its intended blueprint");
        String basis=version==1?ComponentAssignments.legacyBasis(blueprint,component):ComponentAssignments.basis(blueprint,component);
        JsonObject scope=new JsonObject();scope.addProperty("projectId",blueprint.projectId());scope.addProperty("blueprintId",blueprint.id());
        scope.addProperty("blueprintRevision",blueprint.revision());scope.addProperty("component",component);scope.addProperty("basis",basis);scope.addProperty("basisVersion",version);
        return scope;
    }
    static JsonObject validateHistoricalScope(JsonObject saved) {
        if(saved==null || !saved.keySet().equals(Set.of("projectId","blueprintId","blueprintRevision","component","basis","basisVersion")))
            throw new IllegalStateException("Invalid functional evidence scope fields");
        try {
            UUID.fromString(saved.get("projectId").getAsString());
            String blueprint=saved.get("blueprintId").getAsString(),component=saved.get("component").getAsString(),basis=saved.get("basis").getAsString();
            long revision=saved.get("blueprintRevision").getAsBigDecimal().longValueExact();int version=saved.get("basisVersion").getAsBigDecimal().intValueExact();
            if(blueprint.isBlank() || blueprint.length()>128 || !component.matches("[a-z][a-z0-9_-]{0,31}") || !basis.matches("[0-9a-f]{64}") || revision<1 || revision>Integer.MAX_VALUE || (version!=1 && version!=ComponentAssignments.BASIS_VERSION))
                throw new IllegalStateException("Invalid functional evidence scope values");
            return saved.deepCopy();
        } catch(RuntimeException bad) { if(bad instanceof IllegalStateException e)throw e;throw new IllegalStateException("Invalid functional evidence scope",bad); }
    }
    private void validateCurrentScope(JsonObject scope) {
        var s=session;Blueprint blueprint=s.blueprint;String component=scope.get("component").getAsString();int version=scope.get("basisVersion").getAsBigDecimal().intValueExact();
        if(blueprint==null || !scope.get("projectId").getAsString().equals(blueprint.projectId()) || !scope.get("blueprintId").getAsString().equals(blueprint.id())
                || scope.get("blueprintRevision").getAsLong()!=blueprint.revision() || !component.equals(s.activeComponent))
            throw new IllegalStateException("Functional evidence scope does not match the exact loaded intent");
        String expected=version==1?ComponentAssignments.legacyBasis(blueprint,component):ComponentAssignments.basis(blueprint,component);
        if(!expected.equals(scope.get("basis").getAsString()))throw new IllegalStateException("Functional evidence basis disagrees with its intended component");
    }
    private static void validateCases(JsonArray cases) {
        for(JsonElement value:cases) {
            if(!value.isJsonObject())throw new IllegalStateException("Invalid functional evidence case");JsonObject row=value.getAsJsonObject();
            if(!row.has("case") || !row.has("tick") || !row.has("elapsedTicks") || !row.has("inputs") || !row.has("outputs")
                    || !row.get("inputs").isJsonObject() || !row.get("outputs").isJsonObject())throw new IllegalStateException("Invalid functional evidence case fields");
            try {
                if(row.get("case").getAsInt()<0 || row.get("tick").getAsLong()<0 || row.get("elapsedTicks").getAsInt()<0
                        || row.getAsJsonObject("inputs").size()>32 || row.getAsJsonObject("outputs").size()>32)throw new IllegalStateException("Invalid functional evidence case values");
                for(JsonObject ports:List.of(row.getAsJsonObject("inputs"),row.getAsJsonObject("outputs")))for(var entry:ports.entrySet())
                    if(entry.getKey().length()>128 || !entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isBoolean())throw new IllegalStateException("Invalid functional evidence port value");
            }catch(RuntimeException bad){if(bad instanceof IllegalStateException e)throw e;throw new IllegalStateException("Invalid functional evidence case values",bad);}
        }
    }
    private void putLoaded(Input input){
        var port=contract.ports().stream().filter(p->p.input() && p.name().equals(input.name()) && p.pos().equals(input.pos())).findFirst().orElseThrow(()->new IllegalStateException("Recovery input is not in its catalog contract"));
        BlockState planned=session.blueprint.blocks().stream().filter(b->b.pos().equals(port.pos())).findFirst().orElseThrow().state();
        if(inputs.containsKey(input.name()) || !input.original().is(Blocks.LEVER) || !Blueprint.matches(planned,input.original()) || !Blueprint.matches(planned,input.last()) || !Blueprint.matches(planned,input.before()))throw new IllegalStateException("Invalid recovery input state");
        inputs.put(input.name(),input);
    }
}
