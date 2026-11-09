package dev.minecraftai.engineer;

import java.util.*;

/**
 * Turns a catalog circuit and the outputs observed in the world into the exact lever sequence for a test.
 * The test value is chosen from the observed start so a passing run ends with every lever and output restored.
 */
final class SequentialPlan {
    /** Lever changes in pull order, then the value every output must show once this step has settled. */
    record Step(Map<String,Boolean> changes,Map<String,Boolean> outputs){}
    private SequentialPlan(){}

    static List<Step> steps(SequentialCircuits.Circuit circuit,Map<String,Boolean> start){
        if(!start.keySet().equals(circuit.outputs().keySet()))throw new IllegalArgumentException("Observed outputs do not match "+circuit.id());
        boolean counter=circuit.steps().stream().anyMatch(step->step.expect().containsKey("count"));
        int initial=counter?count(start):0;
        boolean q0=!counter && start.values().iterator().next();
        boolean b=switch(circuit.id()){case "data-latch"->!q0;case "edge-register"->q0;default->false;};
        var levers=new LinkedHashMap<String,Boolean>();circuit.controls().keySet().forEach(name->levers.put(name,false));
        var held=new LinkedHashMap<>(start);var out=new ArrayList<Step>();
        for(var step:circuit.steps()){
            var changes=new LinkedHashMap<String,Boolean>();
            step.inputs().forEach((name,value)->{boolean wanted=resolve(value,b);if(!levers.get(name).equals(wanted)){changes.put(name,wanted);levers.put(name,wanted);}});
            step.expect().forEach((name,value)->{
                if(name.equals("count")){int expected=(initial+Integer.parseInt(value))%4;held.replaceAll((output,old)->((expected>>(output.endsWith("1")?1:0))&1)==1);}
                else held.put(name,resolve(value,b));
            });
            out.add(new Step(Collections.unmodifiableMap(changes),Map.copyOf(held)));
        }
        // Return levers to rest, storage-triggering controls first so the data change cannot be captured.
        var rest=new ArrayList<>(levers.keySet());rest.sort(Comparator.comparing(name->name.equals("data")));
        for(String name:rest)if(levers.get(name)){
            levers.put(name,false);var change=new LinkedHashMap<String,Boolean>();change.put(name,false);
            out.add(new Step(Collections.unmodifiableMap(change),Map.copyOf(held)));
        }
        if(!held.equals(start))throw new IllegalStateException("Test sequence for "+circuit.id()+" would not restore its starting value");
        return List.copyOf(out);
    }
    static int count(Map<String,Boolean> outputs){
        int value=0;for(var entry:outputs.entrySet())if(entry.getValue())value|=1<<(entry.getKey().endsWith("1")?1:0);return value;
    }
    private static boolean resolve(String value,boolean b){
        return switch(value){case "0"->false;case "1"->true;case "b"->b;case "!b"->!b;default->throw new IllegalArgumentException(value);};
    }
}
