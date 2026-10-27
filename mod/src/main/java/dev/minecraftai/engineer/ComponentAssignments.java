package dev.minecraftai.engineer;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Coordination and durable progress descriptions; these records never contain execution authority. */
final class ComponentAssignments {
    static final int BASIS_VERSION = 2;
    static final class Task {
        final String id;
        String basis;
        UUID actor;
        String executor = "unassigned", status = "PENDING", verified = "untested";
        long generation;
        JsonObject lastPassedScope;
        Task(String id, String basis) { this.id=id;this.basis=basis; }
        void assign(UUID actor, String executor) {
            if(generation>=9_007_199_254_740_991L)throw new IllegalStateException("Component assignment generation exhausted");
            generation=Math.addExact(generation,1);this.actor=actor;this.executor=executor;
            status=executor.equals("player")?"PAUSED":"READY";
        }
    }
    final Map<String,Task> tasks = new LinkedHashMap<>();
    private Blueprint reconciled;

    /** Exact pre-v2 digest, retained for reading and labeling evidence created by old builds. */
    static String legacyBasis(Blueprint project,String id) {
        Blueprint component=ComponentTree.component(project,id);
        StringBuilder text=new StringBuilder(project.worldId()).append('|').append(project.dimension()).append('|').append(id);
        for(var block:component.blocks())text.append('|').append(block.pos().toShortString()).append(':').append(Blueprint.describe(block.state())).append(':').append(block.stage());
        for(var node:ComponentTree.nodes(project))if(node.id().equals(id))text.append('|').append(node.dependsOn());
        return digest(text.toString());
    }

    /** Computes all v2 bases once, memoizing the validated dependency DAG. */
    static Map<String,String> bases(Blueprint project) {
        List<ComponentTree.Node> nodes=ComponentTree.nodes(project);
        Map<String,ComponentTree.Node> byId=new LinkedHashMap<>();
        for(var node:nodes)if(node.kind().equals("mechanism"))byId.put(node.id(),node);
        Map<String,String> out=new LinkedHashMap<>();
        for(String id:byId.keySet())basis(project,id,byId,out,new HashSet<>());
        return Collections.unmodifiableMap(out);
    }
    static String basis(Blueprint project,String id) { return bases(project).get(id); }

    private static String basis(Blueprint project,String id,Map<String,ComponentTree.Node> byId,Map<String,String> memo,Set<String> active) {
        String existing=memo.get(id);if(existing!=null)return existing;
        ComponentTree.Node node=byId.get(id);if(node==null)throw new IllegalArgumentException("Unknown mechanism component");
        if(!active.add(id))throw new IllegalArgumentException("Component dependencies contain a cycle");
        String own=legacyBasis(project,id);
        // Preserve the exact v1 identity for roots and other dependency-free leaves.
        if(node.dependsOn().isEmpty()){memo.put(id,own);active.remove(id);return own;}
        StringBuilder text=new StringBuilder("component-basis-v2|").append(own);
        for(String dependency:node.dependsOn())text.append('|').append(dependency).append('=').append(basis(project,dependency,byId,memo,active));
        active.remove(id);String value=digest(text.toString());memo.put(id,value);return value;
    }
    private static String digest(String text) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }

    void reconcile(Blueprint project) {
        if(reconciled==project)return;
        Map<String,String> current=bases(project);Set<String> present=new HashSet<>();
        for(var entry:current.entrySet()) {
            String id=entry.getKey(),basis=entry.getValue();present.add(id);
            Task task=tasks.computeIfAbsent(id,key->new Task(key,basis));
            if(!task.basis.equals(basis)) {
                task.basis=basis;
                if(task.generation>=9_007_199_254_740_991L)throw new IllegalStateException("Component assignment generation exhausted");
                task.generation=Math.addExact(task.generation,1);
                task.status="PAUSED";task.verified="stale: intended component or prerequisite changed";
            }
        }
        tasks.keySet().removeIf(id->!present.contains(id));
        reconciled=project;
    }
    Task task(Blueprint project,String id) {
        reconcile(project);Task task=tasks.get(id);
        if(task==null)throw new IllegalArgumentException("Choose a physical component, not a container or unknown name");
        return task;
    }
    JsonArray json() {
        JsonArray result=new JsonArray();
        for(Task task:tasks.values()) {
            JsonObject item=new JsonObject();item.addProperty("id",task.id);item.addProperty("basis",task.basis);
            item.addProperty("basisVersion",BASIS_VERSION);item.addProperty("generation",task.generation);item.addProperty("executor",task.executor);
            if(task.actor!=null)item.addProperty("actor",task.actor.toString());
            item.addProperty("status",task.status);item.addProperty("verified",task.verified);
            if(task.lastPassedScope!=null)item.add("lastPassedScope",task.lastPassedScope.deepCopy());
            result.add(item);
        }
        return result;
    }
    void load(Blueprint project,JsonArray saved) {
        if(saved.size()>16)throw new IllegalStateException("Too many component assignments");
        reconcile(project);Set<String> seen=new HashSet<>();Map<String,String> oldBases=new HashMap<>();
        for(JsonElement value:saved) {
            JsonObject item=value.getAsJsonObject();String id=item.get("id").getAsString();
            if(!seen.add(id) || !tasks.containsKey(id))throw new IllegalStateException("Invalid component assignment identity");
            int version=item.has("basisVersion")?item.get("basisVersion").getAsBigDecimal().intValueExact():1;
            if(version!=1 && version!=BASIS_VERSION)throw new IllegalStateException("Unsupported component basis version");
            String stored=item.get("basis").getAsString();Task task=tasks.get(id);
            boolean migrated=false;
            if(version==1) {
                String legacy=oldBases.computeIfAbsent(id,key->legacyBasis(project,key));
                if(!legacy.equals(stored))throw new IllegalStateException("Legacy component assignment basis disagrees with exact stored intent");
                if(!task.basis.equals(legacy)) {
                    task.basis=basis(project,id);
                    migrated=true;task.status="PAUSED";
                    task.verified="stale: component basis migrated; retest required";
                }
            } else if(!task.basis.equals(stored))throw new IllegalStateException("Component assignment basis disagrees with intended geometry");
            long generation=item.get("generation").getAsBigDecimal().longValueExact();
            String executor=item.get("executor").getAsString();
            if(generation<0 || generation>9_007_199_254_740_991L || !Set.of("unassigned","player","assistant").contains(executor))throw new IllegalStateException("Invalid component assignment");
            if(migrated && generation>=9_007_199_254_740_991L)throw new IllegalStateException("Component assignment generation exhausted");
            task.generation=Math.max(task.generation,Math.addExact(generation,migrated?1:0));
            task.executor=executor;
            if(item.has("actor"))try{task.actor=UUID.fromString(item.get("actor").getAsString());}catch(RuntimeException bad){throw new IllegalStateException("Invalid assignment actor",bad);}else task.actor=null;
            if(!task.executor.equals("unassigned") && task.actor==null)throw new IllegalStateException("Assigned component has no granting actor");
            if(item.has("lastPassedScope")) {
                JsonObject scope=FunctionalTrial.validateHistoricalScope(item.getAsJsonObject("lastPassedScope"));
                if(!scope.get("projectId").getAsString().equals(project.projectId()) || !scope.get("component").getAsString().equals(id)
                        || scope.get("blueprintRevision").getAsLong()>project.revision())throw new IllegalStateException("Historical pass belongs to another project, component or future revision");
                task.lastPassedScope=scope;
            }
            task.status="PAUSED";task.verified=item.get("verified").getAsString();
            if(task.verified.startsWith("passed:"))task.verified="stale: restored component needs observation and testing";
        }
        if(seen.size()!=tasks.size())throw new IllegalStateException("Component assignment checkpoint is incomplete");
        reconciled=project;
    }
}
