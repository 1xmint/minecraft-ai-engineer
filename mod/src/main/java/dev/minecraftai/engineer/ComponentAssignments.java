package dev.minecraftai.engineer;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Coordination and durable progress descriptions; these records never contain execution authority. */
final class ComponentAssignments {
    static final class Task {
        final String id;
        String basis;
        UUID actor;
        String executor = "unassigned", status = "PENDING", verified = "untested";
        long generation;
        Task(String id, String basis) { this.id=id;this.basis=basis; }
        void assign(UUID actor, String executor) {
            if(generation>=9_007_199_254_740_991L)throw new IllegalStateException("Component assignment generation exhausted");
            generation=Math.addExact(generation,1);this.actor=actor;this.executor=executor;
            status=executor.equals("player")?"PAUSED":"READY";
        }
    }
    final Map<String,Task> tasks = new LinkedHashMap<>();
    private Blueprint reconciled;

    static String basis(Blueprint project,String id) {
        Blueprint component=ComponentTree.component(project,id);
        StringBuilder text=new StringBuilder(project.worldId()).append('|').append(project.dimension()).append('|').append(id);
        for(var block:component.blocks())text.append('|').append(block.pos().toShortString()).append(':').append(Blueprint.describe(block.state())).append(':').append(block.stage());
        for(var node:ComponentTree.nodes(project))if(node.id().equals(id))text.append('|').append(node.dependsOn());
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    void reconcile(Blueprint project) {
        if(reconciled==project)return;
        Set<String> present=new HashSet<>();
        for(var node:ComponentTree.nodes(project))if(node.kind().equals("mechanism")) {
            present.add(node.id());String basis=basis(project,node.id());
            Task task=tasks.computeIfAbsent(node.id(),id->new Task(id,basis));
            if(!task.basis.equals(basis)) {
                task.basis=basis;task.generation=Math.addExact(task.generation,1);
                task.status="PAUSED";task.verified="stale: intended component changed";
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
            item.addProperty("generation",task.generation);item.addProperty("executor",task.executor);
            if(task.actor!=null)item.addProperty("actor",task.actor.toString());
            item.addProperty("status",task.status);item.addProperty("verified",task.verified);result.add(item);
        }
        return result;
    }
    void load(Blueprint project,JsonArray saved) {
        if(saved.size()>16)throw new IllegalStateException("Too many component assignments");
        reconcile(project);Set<String> seen=new HashSet<>();
        for(JsonElement value:saved) {
            JsonObject item=value.getAsJsonObject();String id=item.get("id").getAsString();
            if(!seen.add(id) || !tasks.containsKey(id))throw new IllegalStateException("Invalid component assignment identity");
            Task task=tasks.get(id);
            if(!task.basis.equals(item.get("basis").getAsString()))throw new IllegalStateException("Component assignment basis disagrees with intended geometry");
            task.generation=item.get("generation").getAsBigDecimal().longValueExact();
            task.executor=item.get("executor").getAsString();
            if(task.generation<0 || task.generation>9_007_199_254_740_991L || !Set.of("unassigned","player","assistant").contains(task.executor))throw new IllegalStateException("Invalid component assignment");
            task.actor=item.has("actor")?UUID.fromString(item.get("actor").getAsString()):null;
            if(!task.executor.equals("unassigned") && task.actor==null)throw new IllegalStateException("Assigned component has no granting actor");
            task.status="PAUSED";task.verified=item.get("verified").getAsString();
            if(task.verified.startsWith("passed:"))task.verified="stale: restored component needs observation and testing";
        }
        if(seen.size()!=tasks.size())throw new IllegalStateException("Component assignment checkpoint is incomplete");
    }
}
