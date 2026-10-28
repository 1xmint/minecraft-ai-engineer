package dev.minecraftai.engineer;

import com.google.gson.*;
import java.util.Set;

/** Bounded observable edge evidence. Its expectation comes from accepted intent, never geometry. */
final class TransitionTrace {
    private final boolean from,to;
    private final int expectedTicks,stableTicks;
    private final JsonArray samples=new JsonArray();
    private int last=-1;
    private Integer transitionTick;
    private String mismatch,missing;

    TransitionTrace(boolean from,boolean to,int expectedTicks,int stableTicks) {
        if(from==to || expectedTicks<1 || expectedTicks>128 || stableTicks<1 || stableTicks>32)
            throw new IllegalArgumentException("Invalid bounded transition expectation");
        this.from=from;this.to=to;this.expectedTicks=expectedTicks;this.stableTicks=stableTicks;
    }
    int horizon(){return expectedTicks+stableTicks;}
    void sample(long elapsedTicks,boolean observed) {
        if(elapsedTicks<0 || elapsedTicks>horizon() || elapsedTicks<=last)
            throw new IllegalArgumentException("Trace samples must advance within their bounded window");
        int elapsed=(int)elapsedTicks;
        if(elapsed!=last+1 && missing==null)missing="missing game-tick sample after "+last+", next observed "+elapsed;
        if(transitionTick==null && observed!=from)transitionTick=elapsed;
        boolean expected=elapsed<expectedTicks?from:to;
        if(observed!=expected && mismatch==null)
            mismatch="output expected "+expected+", observed "+observed+" at elapsed game tick "+elapsed;
        var row=new JsonObject();row.addProperty("elapsedTicks",elapsed);row.addProperty("observed",observed);samples.add(row);
        last=elapsed;
    }
    boolean complete(){return last==horizon();}
    boolean passed(){return complete() && missing==null && mismatch==null;}
    String failure(){return missing!=null?"inconclusive: "+missing:mismatch==null?"":mismatch;}
    String verdict(){return missing!=null?"inconclusive":mismatch!=null?"failed":complete()?"passed":"running";}
    JsonObject json() {
        var out=new JsonObject();out.addProperty("schemaVersion",1);out.addProperty("from",from);out.addProperty("to",to);
        out.addProperty("expectedGameTicks",expectedTicks);out.addProperty("stableGameTicks",stableTicks);
        out.add("samples",samples.deepCopy());if(transitionTick!=null)out.addProperty("transitionTick",transitionTick);
        out.addProperty("verdict",verdict());out.addProperty("failure",failure());return out;
    }
    static TransitionTrace read(JsonObject source) {
        if(source==null || integer(source.get("schemaVersion"))!=1)throw new IllegalArgumentException("Unknown transition trace version");
        var trace=new TransitionTrace(bool(source.get("from")),bool(source.get("to")),integer(source.get("expectedGameTicks")),integer(source.get("stableGameTicks")));
        JsonElement raw=source.get("samples");
        if(raw==null || !raw.isJsonArray() || raw.getAsJsonArray().size()>trace.horizon()+1)
            throw new IllegalArgumentException("Transition samples exceed their bounded window");
        for(var value:raw.getAsJsonArray()){
            if(!value.isJsonObject() || !value.getAsJsonObject().keySet().equals(Set.of("elapsedTicks","observed")))
                throw new IllegalArgumentException("Invalid transition sample fields");
            var row=value.getAsJsonObject();trace.sample(integer(row.get("elapsedTicks")),bool(row.get("observed")));
        }
        if(!trace.json().equals(source))throw new IllegalArgumentException("Stored transition claims disagree with their observed samples");
        return trace;
    }
    private static boolean bool(JsonElement value) {
        if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("Expected a trace boolean");
        return value.getAsBoolean();
    }
    private static int integer(JsonElement value) {
        if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Expected a trace integer");
        try{return value.getAsBigDecimal().intValueExact();}
        catch(ArithmeticException invalid){throw new IllegalArgumentException("Trace integer is outside range",invalid);}
    }
}
