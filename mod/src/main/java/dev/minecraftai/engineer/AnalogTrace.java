package dev.minecraftai.engineer;

import com.google.gson.*;
import java.util.Arrays;
import java.util.Set;

/** One settled transfer, not a whole mechanism pass, latency measurement or execution authority. */
final class AnalogTrace {
    static final int START=8, STABLE=3, END=10;
    static final long MAX_TICK=9_007_199_254_740_991L;
    private static final Set<String> ROOT=Set.of("schemaVersion","function","mode","requested","startedTick","sampleStartGameTicks","stableSamples","expectedOutput","samples","verdict","failure");
    private static final Set<String> ROW=Set.of("tick","elapsedTicks","mode","inputs","output");
    private final String mode;
    private final int[] requested;
    private final long startedTick;
    private final int expectedOutput;
    private final JsonArray samples=new JsonArray();
    private int last=START-1;
    private String missing,mismatch;

    AnalogTrace(String intendedMode,int[] intendedInputs,long startedTick){
        this.mode=mode(intendedMode);this.requested=levels(intendedInputs);
        if(startedTick<0 || startedTick>MAX_TICK-END)throw new IllegalArgumentException("Invalid analog start clock");
        this.startedTick=startedTick;
        int side=Math.max(requested[1],requested[2]);
        expectedOutput=mode.equals("subtract")?Math.max(0,requested[0]-side):requested[0]>=side?requested[0]:0;
    }
    void sample(long tick,long elapsed,String observedMode,int[] observedInputs,int output){
        if(elapsed<START || elapsed>END || elapsed<=last)throw new IllegalArgumentException("Analog samples must advance within the bounded window");
        if(tick<0 || tick>MAX_TICK || tick!=startedTick+elapsed)throw new IllegalArgumentException("Analog sample clock does not match elapsed ticks");
        String actualMode=mode(observedMode);int[] inputs=levels(observedInputs);strength(output);
        if(elapsed!=last+1 && missing==null)missing="missing game-tick sample after "+last+", next observed "+elapsed;
        if(mismatch==null){
            if(!mode.equals(actualMode))mismatch="comparator mode differs at "+elapsed;
            else if(!Arrays.equals(requested,inputs))mismatch="input signals differ at "+elapsed;
            else if(output!=expectedOutput)mismatch="output signal differs at "+elapsed;
        }
        var row=new JsonObject();row.addProperty("tick",tick);row.addProperty("elapsedTicks",elapsed);
        row.addProperty("mode",actualMode);row.add("inputs",array(inputs));row.addProperty("output",output);
        samples.add(row);last=(int)elapsed;
    }
    boolean complete(){return last==END;}
    boolean passed(){return complete() && missing==null && mismatch==null;}
    String verdict(){return missing!=null?"inconclusive":mismatch!=null?"failed":complete()?"passed":"running";}
    String failure(){return missing!=null?"inconclusive: "+missing:mismatch==null?"":mismatch;}
    JsonObject json(){
        var out=new JsonObject();out.addProperty("schemaVersion",1);out.addProperty("function","comparator-transfer");
        out.addProperty("mode",mode);out.add("requested",array(requested));out.addProperty("startedTick",startedTick);
        out.addProperty("sampleStartGameTicks",START);out.addProperty("stableSamples",STABLE);out.addProperty("expectedOutput",expectedOutput);
        out.add("samples",samples.deepCopy());out.addProperty("verdict",verdict());out.addProperty("failure",failure());return out;
    }
    /** Caller supplies canonical intent separately and must bind project/component/epoch scope outside this codec. */
    static AnalogTrace read(JsonObject source,String intendedMode,int[] intendedInputs){
        if(source==null || !source.keySet().equals(ROOT) || integer(source.get("schemaVersion"))!=1)throw new IllegalArgumentException("Invalid analog trace fields/version");
        var trace=new AnalogTrace(intendedMode,intendedInputs,integer(source.get("startedTick")));
        var raw=source.get("samples");
        if(raw==null || !raw.isJsonArray() || raw.getAsJsonArray().size()>STABLE)throw new IllegalArgumentException("Invalid bounded analog samples");
        for(var value:raw.getAsJsonArray()){
            if(!value.isJsonObject() || !value.getAsJsonObject().keySet().equals(ROW))throw new IllegalArgumentException("Invalid analog sample fields");
            var row=value.getAsJsonObject();
            trace.sample(integer(row.get("tick")),integer(row.get("elapsedTicks")),text(row.get("mode")),levels(row.get("inputs")),level(row.get("output")));
        }
        if(!trace.json().equals(source))throw new IllegalArgumentException("Stored analog claims disagree with observed samples or intended transfer");
        return trace;
    }
    private static String mode(String value){
        if(!"compare".equals(value) && !"subtract".equals(value))throw new IllegalArgumentException("Unknown comparator mode");return value;
    }
    private static void strength(int value){if(value<0 || value>15)throw new IllegalArgumentException("Analog signal outside 0..15");}
    private static int[] levels(int[] value){
        if(value==null || value.length!=3)throw new IllegalArgumentException("Expected three analog input levels");
        int[] copy=value.clone();for(int item:copy)strength(item);return copy;
    }
    private static int[] levels(JsonElement value){
        if(value==null || !value.isJsonArray() || value.getAsJsonArray().size()!=3)throw new IllegalArgumentException("Expected three analog input levels");
        int[] out=new int[3];for(int index=0;index<3;index++)out[index]=level(value.getAsJsonArray().get(index));return out;
    }
    private static int level(JsonElement value){long out=integer(value);if(out<0 || out>15)throw new IllegalArgumentException("Analog signal outside 0..15");return (int)out;}
    private static JsonArray array(int[] values){var out=new JsonArray();for(int value:values)out.add(value);return out;}
    private static String text(JsonElement value){
        if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Expected comparator mode string");return value.getAsString();
    }
    private static long integer(JsonElement value){
        if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Expected analog integer");
        try{return value.getAsBigDecimal().longValueExact();}catch(ArithmeticException | NumberFormatException invalid){throw new IllegalArgumentException("Invalid analog integer",invalid);}
    }
}
