package dev.minecraftai.engineer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Read-only catalog of clocked and stored circuits: preview, materials and lessons.
 * It never touches the world or a project. Building and testing happen from a project preview, through
 * SequentialIntention and SequentialFunctionalRun, because the combinational test would be false for memory.
 */
public final class SequentialCircuits {
    public record Cell(BlockPos at,String state){}
    public record Step(Map<String,String> inputs,Map<String,String> expect){}
    public record Circuit(String id,String name,String summary,List<String> lesson,Map<String,BlockPos> controls,Map<String,BlockPos> outputs,List<Cell> cells,List<Step> steps,String limit){}
    private static final Map<String,Circuit> CIRCUITS=load();
    private static final Map<String,String> SPOKEN=Map.of("data latch","data-latch","edge register","edge-register","edge-triggered register","edge-register","up counter","up-counter","binary counter","up-counter","pulse tally","pulse-tally");
    private SequentialCircuits(){}

    public static Set<String> ids(){return CIRCUITS.keySet();}
    public static Circuit get(String id){
        var circuit=CIRCUITS.get(id);
        if(circuit==null)throw new IllegalArgumentException("No stored circuit is named "+id+". Known circuits: "+String.join(", ",ids()));
        return circuit;
    }
    public static List<String> list(){
        var lines=new ArrayList<String>();
        lines.add("Stored and clocked circuits (preview in a project to build and live test):");
        CIRCUITS.values().forEach(c->lines.add(c.id()+": "+c.summary()));
        return lines;
    }
    public static List<String> command(String action,String id){
        var circuit=get(id);
        return switch(action){
            case "show" -> show(circuit);
            case "lesson" -> lesson(circuit);
            case "build","approve","test" -> List.of(refusal(circuit));
            default -> throw new IllegalArgumentException("Use circuit list, or circuit show, lesson, build, approve or test followed by a circuit name.");
        };
    }
    /** Addressed chat. Empty means the request is about something else and other routes should handle it. */
    public static List<String> conversation(String request){return conversation(request,null);}
    /** heldId names the circuit already previewed in this project, whose build and test belong to approval. */
    public static List<String> conversation(String request,String heldId){
        String text=request.toLowerCase(Locale.ROOT);
        if(text.contains("sequential circuit") || text.contains("stored circuit") || text.contains("clocked circuit"))return list();
        var match=SPOKEN.entrySet().stream().filter(e->text.contains(e.getKey())).map(Map.Entry::getValue).findFirst();
        if(match.isEmpty())return List.of();
        var circuit=get(match.get());
        if(has(text,"build","place","construct","approve","test"))return circuit.id().equals(heldId)?List.of():List.of(refusal(circuit));
        if(has(text,"how","why","explain","teach","learn","lesson"))return lesson(circuit);
        return show(circuit);
    }
    private static boolean has(String text,String... words){return Arrays.stream(words).anyMatch(word->java.util.regex.Pattern.compile("\\b"+word+"\\b").matcher(text).find());}
    private static String refusal(Circuit circuit){
        String ask=switch(circuit.id()){case "data-latch"->"show me a data latch";case "edge-register"->"show me an edge register";case "up-counter"->"show me an up counter";default->"show me a pulse tally";};
        return "To build the "+circuit.name()+", mark a workspace in a project and say 'Engineer, "+ask+"'. You can then approve its build and its live test. Nothing was changed.";
    }
    private static List<String> lesson(Circuit circuit){
        var lines=new ArrayList<String>();
        lines.add("Lesson: "+circuit.name()+". "+circuit.summary());
        lines.addAll(circuit.lesson());
        lines.add("Limit: "+circuit.limit());
        return lines;
    }
    private static List<String> show(Circuit circuit){
        var lines=new ArrayList<String>();
        lines.add(circuit.name()+": "+circuit.summary());
        lines.add("Top-down layout (north is up): L lever, > repeater, C comparator, O lamp, B copper bulb, T torch, # block, . empty");
        lines.addAll(layout(circuit));
        lines.add("Materials: "+materials(circuit));
        lines.add("Controls: "+String.join(", ",circuit.controls().keySet())+". Shows: "+String.join(", ",circuit.outputs().keySet())+".");
        lines.add("Limit: "+circuit.limit());
        lines.add("This preview changes nothing and does not claim the circuit works in your world.");
        return lines;
    }
    private static List<String> layout(Circuit circuit){
        var at=new HashMap<Long,Character>();int minX=99,maxX=-99,minZ=99,maxZ=-99;
        for(var cell:circuit.cells()){
            minX=Math.min(minX,cell.at().getX());maxX=Math.max(maxX,cell.at().getX());minZ=Math.min(minZ,cell.at().getZ());maxZ=Math.max(maxZ,cell.at().getZ());
            at.put(cell.at().asLong(),glyph(cell.state()));
        }
        var rows=new ArrayList<String>();
        for(int z=minZ;z<=maxZ;z++){
            var row=new StringBuilder();
            for(int x=minX;x<=maxX;x++)row.append(at.getOrDefault(new BlockPos(x,0,z).asLong(),'.'));
            rows.add(row.toString());
        }
        return rows;
    }
    private static char glyph(String state){
        if(state.contains("lever"))return 'L';if(state.contains("repeater"))return '>';if(state.contains("comparator"))return 'C';
        if(state.contains("lamp"))return 'O';if(state.contains("bulb"))return 'B';if(state.contains("torch"))return 'T';return '#';
    }
    private static String materials(Circuit circuit){
        var counts=new TreeMap<String,Integer>();
        for(var cell:circuit.cells()){
            String item=cell.state().replaceFirst("\\[.*$","").replace("minecraft:","").replace("redstone_wall_torch","redstone_torch");
            counts.merge(item,1,Integer::sum);
        }
        counts.merge("stone",circuit.cells().size(),Integer::sum); // One floor block under every part.
        var parts=new ArrayList<String>();counts.forEach((item,count)->parts.add(count+" "+item));
        return String.join(", ",parts);
    }
    private static Map<String,Circuit> load(){
        try(var stream=SequentialCircuits.class.getResourceAsStream("/engineer/sequential-circuits.json")){
            var root=JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(stream),StandardCharsets.UTF_8)).getAsJsonObject();
            var circuits=new LinkedHashMap<String,Circuit>();
            for(var element:root.getAsJsonArray("circuits")){
                var circuit=parse(element.getAsJsonObject());
                validate(circuit);
                if(circuits.put(circuit.id(),circuit)!=null)throw new IllegalStateException("Duplicate circuit "+circuit.id());
            }
            return Collections.unmodifiableMap(circuits);
        }catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
    }
    private static Circuit parse(JsonObject o){
        var lesson=new ArrayList<String>();o.getAsJsonArray("lesson").forEach(e->lesson.add(e.getAsString()));
        var cells=new ArrayList<Cell>();o.getAsJsonArray("cells").forEach(e->{var c=e.getAsJsonObject();cells.add(new Cell(pos(c.getAsJsonArray("at")),c.get("state").getAsString()));});
        var steps=new ArrayList<Step>();o.getAsJsonArray("steps").forEach(e->{var s=e.getAsJsonObject();steps.add(new Step(strings(s.getAsJsonObject("inputs")),strings(s.getAsJsonObject("expect"))));});
        return new Circuit(o.get("id").getAsString(),o.get("name").getAsString(),o.get("summary").getAsString(),List.copyOf(lesson),positions(o.getAsJsonObject("controls")),positions(o.getAsJsonObject("outputs")),List.copyOf(cells),List.copyOf(steps),o.get("limit").getAsString());
    }
    private static BlockPos pos(com.google.gson.JsonArray a){return new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt());}
    private static Map<String,BlockPos> positions(JsonObject o){var map=new LinkedHashMap<String,BlockPos>();o.entrySet().forEach(e->map.put(e.getKey(),pos(e.getValue().getAsJsonArray())));return Collections.unmodifiableMap(map);}
    private static Map<String,String> strings(JsonObject o){var map=new LinkedHashMap<String,String>();o.entrySet().forEach(e->map.put(e.getKey(),e.getValue().getAsString()));return Collections.unmodifiableMap(map);}
    private static void validate(Circuit circuit){
        var byPos=new HashMap<BlockPos,String>();
        for(var cell:circuit.cells())if(byPos.put(cell.at(),cell.state())!=null)throw new IllegalStateException(circuit.id()+" repeats a cell");
        circuit.controls().forEach((name,pos)->{if(!byPos.getOrDefault(pos,"").contains("lever"))throw new IllegalStateException(circuit.id()+" control is not a lever: "+name);});
        circuit.outputs().forEach((name,pos)->{var state=byPos.getOrDefault(pos,"");if(!state.contains("lamp") && !state.contains("bulb"))throw new IllegalStateException(circuit.id()+" output is not readable: "+name);});
        for(var step:circuit.steps())for(var input:step.inputs().keySet())if(!circuit.controls().containsKey(input))throw new IllegalStateException(circuit.id()+" drives an unknown control "+input);
    }
}
