package dev.minecraftai.engineer;

import com.google.gson.*;
import java.util.*;

/** Bounded observations only. Current authority, source scope and restoration belong to the caller. */
final class FullAdderTrace {
    static final int SETTLE=256,END=263;
    private static final long MAX_TICK=9_007_199_254_740_991L;
    private static final Set<String> ROOT=Set.of("schemaVersion","function","graphDigest","vector","startGameTick","settleGameTicks","stableSamples","samples","verdict","firstMismatch");
    private static final Set<String> ROW=Set.of("gameTick","elapsedTicks","ports");
    private static final Set<String> BOOLEANS=Set.of("a:control","b:control","cin:control","sum:result","carry:result");
    private final String digest;
    private final int vector;
    private final long start;
    private final Map<String,Boolean> expected;
    private final JsonArray samples=new JsonArray();
    private JsonObject mismatch;
    FullAdderTrace(String digest,int vector,long start){
        if(digest==null || !digest.matches("[a-f0-9]{64}") || vector<0 || vector>7 || start<0 || start>MAX_TICK-END)throw new IllegalArgumentException("Invalid bounded full-adder trace identity");
        this.digest=digest;this.vector=vector;this.start=start;expected=expected(vector);
    }
    void sample(long tick,JsonObject raw){
        var ports=ports(raw,expected.keySet());
        if(samples.size()>=8 || tick!=start+SETTLE+samples.size())throw new IllegalArgumentException("Full-adder observations need eight consecutive settled game ticks");
        for(var entry:expected.entrySet()){
            var actual=ports.get(entry.getKey());boolean logical=BOOLEANS.contains(entry.getKey())?actual.getAsBoolean():actual.getAsInt()>0;
            if(mismatch==null && logical!=entry.getValue()){
                mismatch=new JsonObject();mismatch.addProperty("gameTick",tick);mismatch.addProperty("pin",entry.getKey());mismatch.addProperty("expected",entry.getValue());mismatch.add("observed",actual.deepCopy());
            }
        }
        var row=new JsonObject();row.addProperty("gameTick",tick);row.addProperty("elapsedTicks",tick-start);row.add("ports",ports);samples.add(row);
    }
    boolean passed(){return samples.size()==8 && mismatch==null;}
    String verdict(){return mismatch!=null?"failed":passed()?"passed":"incomplete";}
    JsonObject json(){
        var out=new JsonObject();out.addProperty("schemaVersion",2);out.addProperty("function","full-adder");out.addProperty("graphDigest",digest);out.addProperty("vector",vector);out.addProperty("startGameTick",start);out.addProperty("settleGameTicks",SETTLE);out.addProperty("stableSamples",8);out.add("samples",samples.deepCopy());out.addProperty("verdict",verdict());out.add("firstMismatch",mismatch==null?JsonNull.INSTANCE:mismatch.deepCopy());return out;
    }
    static FullAdderTrace read(JsonObject source,String digest,int vector){
        if(source==null || source.toString().length()>20000 || !source.keySet().equals(ROOT))throw new IllegalArgumentException("Invalid full-adder trace fields or size");
        try{
            if(integer(source.get("schemaVersion"))!=2 || !text(source.get("function")).equals("full-adder") || !text(source.get("graphDigest")).equals(digest) || integer(source.get("vector"))!=vector || integer(source.get("settleGameTicks"))!=SETTLE || integer(source.get("stableSamples"))!=8)throw new IllegalArgumentException("Full-adder trace differs from intended graph, inputs or fixed window");
            var trace=new FullAdderTrace(digest,vector,integer(source.get("startGameTick")));
            if(!source.get("samples").isJsonArray() || source.getAsJsonArray("samples").size()>8)throw new IllegalArgumentException("Invalid full-adder sample count");
            for(var value:source.getAsJsonArray("samples")){
                if(!value.isJsonObject() || !value.getAsJsonObject().keySet().equals(ROW))throw new IllegalArgumentException("Invalid full-adder sample fields");
                var row=value.getAsJsonObject();long tick=integer(row.get("gameTick"));
                if(integer(row.get("elapsedTicks"))!=tick-trace.start || !row.get("ports").isJsonObject())throw new IllegalArgumentException("Full-adder sample clock differs");
                trace.sample(tick,row.getAsJsonObject("ports"));
            }
            if(!trace.json().equals(source))throw new IllegalArgumentException("Full-adder claims disagree with recorded observations");return trace;
        }catch(IllegalArgumentException invalid){throw invalid;}catch(RuntimeException malformed){throw new IllegalArgumentException("Malformed full-adder trace",malformed);}
    }
    private static JsonObject ports(JsonObject source,Set<String> expected){
        if(source==null || !source.keySet().equals(expected))throw new IllegalArgumentException("Full-adder evidence needs exactly all35 typed pins");
        var out=new JsonObject();for(String pin:expected){var value=source.get(pin);
            if(BOOLEANS.contains(pin)){
                if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("Full-adder controls/receivers must be Boolean");out.addProperty(pin,value.getAsBoolean());
            }else{long power=integer(value);if(power<0 || power>15)throw new IllegalArgumentException("Full-adder wire power must be0..15");out.addProperty(pin,power);}
        }return out;
    }
    private static Map<String,Boolean> expected(int vector){
        boolean a=(vector&4)!=0,b=(vector&2)!=0,c=(vector&1)!=0;
        boolean n1=!(a&&b),n2=!(a&&n1),n3=!(b&&n1),xor=!(n2&&n3),n5=!(xor&&c),n6=!(xor&&n5),n7=!(c&&n5);
        var values=new LinkedHashMap<String,Boolean>();
        values.put("a:control",a);values.put("a:out",a);values.put("b:control",b);values.put("b:out",b);values.put("cin:control",c);values.put("cin:out",c);
        gate(values,"n1",a,b,n1);gate(values,"n2",a,n1,n2);gate(values,"n3",b,n1,n3);gate(values,"xor",n2,n3,xor);gate(values,"n5",xor,c,n5);gate(values,"n6",xor,n5,n6);gate(values,"n7",c,n5,n7);gate(values,"sum",n6,n7,!(n6&&n7));gate(values,"carry",n1,n5,!(n1&&n5));
        values.put("sum:result",(Integer.bitCount(vector)&1)!=0);values.put("carry:result",Integer.bitCount(vector)>=2);return Collections.unmodifiableMap(values);
    }
    private static void gate(Map<String,Boolean> values,String id,boolean a,boolean b,boolean out){values.put(id+":in0",a);values.put(id+":in1",b);values.put(id+":out",out);}
    private static long integer(JsonElement value){if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Expected full-adder integer");try{return value.getAsBigDecimal().longValueExact();}catch(ArithmeticException | NumberFormatException invalid){throw new IllegalArgumentException("Invalid full-adder integer",invalid);}}
    private static String text(JsonElement value){if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Expected full-adder text");return value.getAsString();}
}
