package dev.minecraftai.engineer;

import com.google.gson.*;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** One half-adder case. Graph/inputs are supplied independently; this never grants world authority. */
final class ConnectedTrace {
    static final long MAX_TICK=9_007_199_254_740_991L;
    static final int SETTLE=48,END=55;
    private static final List<String> PINS=List.of("a:control","a:out","b:control","b:out","n1:in0","n1:in1","n1:out",
        "n2:in0","n2:in1","n2:out","n3:in0","n3:in1","n3:out","sum:in0","sum:in1","sum:out","carry:in","carry:out","sum-light:in","carry-light:in");
    private static final Set<String> BOOLEANS=Set.of("a:control","b:control","sum-light:in","carry-light:in");
    private static final Set<String> ROOT=Set.of("schemaVersion","function","graphDigest","a","b","startGameTick","settleGameTicks","stableSamples","samples","verdict","firstMismatch");
    private static final Set<String> ROW=Set.of("gameTick","elapsedTicks","ports");
    private final String digest;
    private final boolean a,b;
    private final long start;
    private final Map<String,Boolean> expected;
    private final JsonArray samples=new JsonArray();
    private JsonObject mismatch;
    private boolean gap;
    private int last=-1;

    ConnectedTrace(String graphDigest,boolean a,boolean b,long startGameTick){
        if(graphDigest==null || !graphDigest.matches("[a-f0-9]{64}") || startGameTick<0 || startGameTick>MAX_TICK-END)throw new IllegalArgumentException("Invalid connected trace identity or start clock");
        digest=graphDigest;this.a=a;this.b=b;start=startGameTick;expected=expected(a,b);
    }
    void sample(long gameTick,JsonObject observed){
        long elapsed=gameTick-start;
        if(gameTick<0 || gameTick>MAX_TICK || elapsed<0 || elapsed>END || elapsed<=last)throw new IllegalArgumentException("Connected trace ticks must advance within elapsed ticks0..55");
        var ports=ports(observed); // Reject malformed data before changing captured facts.
        if(elapsed!=last+1)gap=true;
        if(mismatch==null)for(String pin:PINS){
            if(elapsed<SETTLE && !pin.equals("a:control") && !pin.equals("b:control"))continue;
            var raw=ports.get(pin);boolean value=BOOLEANS.contains(pin)?raw.getAsBoolean():raw.getAsInt()>0;
            if(value!=expected.get(pin)){
                mismatch=new JsonObject();mismatch.addProperty("elapsedTicks",elapsed);mismatch.addProperty("gameTick",gameTick);mismatch.addProperty("pin",pin);
                mismatch.addProperty("expected",expected.get(pin));mismatch.add("observed",raw.deepCopy());break;
            }
        }
        var row=new JsonObject();row.addProperty("gameTick",gameTick);row.addProperty("elapsedTicks",elapsed);row.add("ports",ports);samples.add(row);last=(int)elapsed;
    }
    boolean passed(){return verdict().equals("passed");}
    String failure(){
        if(mismatch==null)return "inconclusive: connected observation window is incomplete";
        return "Connected pin "+mismatch.get("pin").getAsString()+" expected "+(mismatch.get("expected").getAsBoolean()?"on":"off")
            +", observed "+mismatch.get("observed")+" at game tick "+mismatch.get("gameTick").getAsLong()+" (elapsed "+mismatch.get("elapsedTicks").getAsInt()+")";
    }
    String verdict(){return mismatch!=null?"failed":!gap && samples.size()==END+1 && last==END?"passed":"inconclusive";}
    JsonObject json(){
        var out=new JsonObject();out.addProperty("schemaVersion",1);out.addProperty("function","half-adder");out.addProperty("graphDigest",digest);
        out.addProperty("a",a);out.addProperty("b",b);out.addProperty("startGameTick",start);out.addProperty("settleGameTicks",SETTLE);out.addProperty("stableSamples",8);
        out.add("samples",samples.deepCopy());out.addProperty("verdict",verdict());out.add("firstMismatch",mismatch==null?JsonNull.INSTANCE:mismatch.deepCopy());return out;
    }
    static ConnectedTrace read(JsonObject source,String expectedDigest,boolean expectedA,boolean expectedB){
        if(source==null || !source.keySet().equals(ROOT))throw new IllegalArgumentException("Invalid connected trace fields");
        var trace=new ConnectedTrace(expectedDigest,expectedA,expectedB,integer(source.get("startGameTick")));
        var values=source.get("samples");if(values==null || !values.isJsonArray() || values.getAsJsonArray().size()>END+1)throw new IllegalArgumentException("Invalid bounded connected samples");
        for(var value:values.getAsJsonArray()){
            if(!value.isJsonObject() || !value.getAsJsonObject().keySet().equals(ROW))throw new IllegalArgumentException("Invalid connected sample fields");
            var row=value.getAsJsonObject();long tick=integer(row.get("gameTick")),elapsed=integer(row.get("elapsedTicks"));
            if(elapsed!=tick-trace.start || row.get("ports")==null || !row.get("ports").isJsonObject())throw new IllegalArgumentException("Connected clock or pin data differs");
            trace.sample(tick,row.getAsJsonObject("ports"));
        }
        if(!trace.json().equals(source))throw new IllegalArgumentException("Stored connected claims disagree with observations or intended graph/inputs");
        return trace;
    }
    private static JsonObject ports(JsonObject value){
        if(value==null || !value.keySet().equals(Set.copyOf(PINS)))throw new IllegalArgumentException("Connected evidence needs all twenty typed pins");
        var out=new JsonObject();
        for(String pin:PINS){
            var raw=value.get(pin);
            if(BOOLEANS.contains(pin)){
                if(raw==null || !raw.isJsonPrimitive() || !raw.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("Connected control/receiver must be boolean");
                out.addProperty(pin,raw.getAsBoolean());
            }else{
                long level=integer(raw);if(level<0 || level>15)throw new IllegalArgumentException("Connected wire strength must be0..15");out.addProperty(pin,level);
            }
        }
        return out;
    }
    private static Map<String,Boolean> expected(boolean a,boolean b){
        boolean n1=!(a&&b),n2=!(a&&n1),n3=!(n1&&b);
        return Map.ofEntries(Map.entry("a:control",a),Map.entry("a:out",a),Map.entry("b:control",b),Map.entry("b:out",b),
            Map.entry("n1:in0",a),Map.entry("n1:in1",b),Map.entry("n1:out",n1),Map.entry("n2:in0",a),Map.entry("n2:in1",n1),Map.entry("n2:out",n2),
            Map.entry("n3:in0",n1),Map.entry("n3:in1",b),Map.entry("n3:out",n3),Map.entry("sum:in0",n2),Map.entry("sum:in1",n3),Map.entry("sum:out",a^b),
            Map.entry("carry:in",n1),Map.entry("carry:out",a&&b),Map.entry("sum-light:in",a^b),Map.entry("carry-light:in",a&&b));
    }
    private static long integer(JsonElement value){
        if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Expected connected integer");
        try{return value.getAsBigDecimal().longValueExact();}catch(ArithmeticException | NumberFormatException invalid){throw new IllegalArgumentException("Invalid connected integer",invalid);}
    }
}
