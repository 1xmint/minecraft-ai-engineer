package dev.minecraftai.engineer;

import com.google.gson.*;
import java.util.Set;

/** Bounded every-tick evidence for one canonical observer input edge; contains no authority. */
final class PulseTrace {
    static final int DELAY=2, WIDTH=2, LAMP_RELEASE=4, HORIZON=16;
    private final boolean from;
    private final JsonArray samples=new JsonArray();
    private int last=-1;
    private String missing,mismatch;

    PulseTrace(boolean from){this.from=from;}
    void sample(long elapsed,boolean input,boolean observer,boolean receiver,boolean lamp){
        if(elapsed<0 || elapsed>HORIZON || elapsed<=last)
            throw new IllegalArgumentException("Pulse samples must advance within the bounded window");
        if(elapsed!=last+1 && missing==null)missing="missing game-tick sample after "+last+", next observed "+elapsed;
        boolean pulse=elapsed>=DELAY && elapsed<DELAY+WIDTH;
        boolean lit=elapsed>=DELAY && elapsed<DELAY+WIDTH+LAMP_RELEASE;
        if(mismatch==null){
            if(input==from)mismatch="input changed or did not reach the requested edge at "+elapsed;
            else if(observer!=pulse)mismatch="observer pulse differs at "+elapsed;
            else if(receiver!=pulse)mismatch="receiver signal differs at "+elapsed;
            else if(lamp!=lit)mismatch="lamp response differs at "+elapsed;
        }
        var row=new JsonObject();row.addProperty("elapsedTicks",elapsed);row.addProperty("input",input);
        row.addProperty("observer",observer);row.addProperty("receiver",receiver);row.addProperty("lamp",lamp);
        samples.add(row);last=(int)elapsed;
    }
    boolean complete(){return last==HORIZON;}
    boolean passed(){return complete() && missing==null && mismatch==null;}
    String verdict(){return missing!=null?"inconclusive":mismatch!=null?"failed":complete()?"passed":"running";}
    String failure(){return missing!=null?"inconclusive: "+missing:mismatch==null?"":mismatch;}
    JsonObject json(){
        var out=new JsonObject();out.addProperty("schemaVersion",1);out.addProperty("function","observer-pulse");
        out.addProperty("from",from);out.addProperty("to",!from);out.addProperty("delayGameTicks",DELAY);
        out.addProperty("widthGameTicks",WIDTH);out.addProperty("lampReleaseGameTicks",LAMP_RELEASE);
        out.addProperty("horizonGameTicks",HORIZON);out.add("samples",samples.deepCopy());
        out.addProperty("verdict",verdict());out.addProperty("failure",failure());return out;
    }
    static PulseTrace read(JsonObject source){
        if(source==null || integer(source.get("schemaVersion"))!=1)throw new IllegalArgumentException("Unknown pulse trace version");
        var trace=new PulseTrace(bool(source.get("from")));var raw=source.get("samples");
        if(raw==null || !raw.isJsonArray() || raw.getAsJsonArray().size()>HORIZON+1)
            throw new IllegalArgumentException("Invalid bounded pulse samples");
        for(var value:raw.getAsJsonArray()){
            if(!value.isJsonObject() || !value.getAsJsonObject().keySet().equals(Set.of("elapsedTicks","input","observer","receiver","lamp")))
                throw new IllegalArgumentException("Invalid pulse sample fields");
            var row=value.getAsJsonObject();trace.sample(integer(row.get("elapsedTicks")),bool(row.get("input")),bool(row.get("observer")),bool(row.get("receiver")),bool(row.get("lamp")));
        }
        if(!trace.json().equals(source))throw new IllegalArgumentException("Stored pulse claims disagree with observed samples");
        return trace;
    }
    private static boolean bool(JsonElement value){
        if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("Expected a pulse boolean");
        return value.getAsBoolean();
    }
    private static int integer(JsonElement value){
        if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Expected a pulse integer");
        try{return value.getAsBigDecimal().intValueExact();}
        catch(ArithmeticException | NumberFormatException invalid){throw new IllegalArgumentException("Invalid pulse integer",invalid);}
    }
}
