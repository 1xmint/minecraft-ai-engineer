package dev.minecraftai.engineer;

import com.google.gson.*;
import java.util.Set;

/** Recomputes archived observer evidence. Scope/authority remain the trial owner's responsibility. */
final class PulseEvidence {
    static final int BASELINE=8;
    private static final Set<String> FIELDS=Set.of("case","tick","elapsedTicks","originalInput","inputs","outputs","pulse");
    private PulseEvidence() { }
    static void validate(JsonArray cases,boolean requirePass){
        if(cases==null || cases.size()>3 || (requirePass && cases.size()!=3))throw new IllegalArgumentException("Observer evidence needs baseline and both edges");
        Boolean original=null;long previous=-1;
        for(int index=0;index<cases.size();index++){
            var row=cases.get(index).getAsJsonObject();
            if(!row.keySet().equals(FIELDS) || integer(row.get("case"))!=index)throw new IllegalArgumentException("Invalid observer evidence case");
            boolean initial=bool(row.get("originalInput"));if(original!=null && original!=initial)throw new IllegalArgumentException("Observer evidence changed its original input");original=initial;
            int elapsed=integer(row.get("elapsedTicks"));long tick=longInteger(row.get("tick"));
            if(tick<0 || elapsed<0 || tick<elapsed || (previous>=0 && tick-elapsed!=previous))throw new IllegalArgumentException("Observer evidence phases must meet on an actual game tick");
            var trace=row.getAsJsonObject("pulse");JsonArray samples;
            boolean complete,passed;
            if(index==0){
                if(!trace.keySet().equals(Set.of("schemaVersion","function","samples")) || integer(trace.get("schemaVersion"))!=1 || !trace.get("function").getAsString().equals("observer-baseline"))throw new IllegalArgumentException("Invalid observer baseline");
                samples=trace.getAsJsonArray("samples");if(samples.isEmpty() || samples.size()>BASELINE+1)throw new IllegalArgumentException("Invalid observer baseline length");
                passed=true;
                for(int i=0;i<samples.size();i++){
                    var sample=samples.get(i).getAsJsonObject();
                    if(!sample.keySet().equals(Set.of("elapsedTicks","input","observer","receiver","lamp")) || integer(sample.get("elapsedTicks"))!=i)throw new IllegalArgumentException("Observer baseline is missing consecutive ticks");
                    passed &= bool(sample.get("input"))==initial && !bool(sample.get("observer")) && !bool(sample.get("receiver")) && !bool(sample.get("lamp"));
                }
                complete=samples.size()==BASELINE+1;
            }else{
                var pulse=PulseTrace.read(trace);if(bool(trace.get("from"))!=(index==1?initial:!initial))throw new IllegalArgumentException("Observer edge direction differs from original input");
                samples=trace.getAsJsonArray("samples");complete=pulse.complete();passed=pulse.passed();
                if(samples.isEmpty())throw new IllegalArgumentException("Observer edge has no actual sample");
            }
            var last=samples.get(samples.size()-1).getAsJsonObject();
            if(integer(last.get("elapsedTicks"))!=elapsed || !row.getAsJsonObject("inputs").keySet().equals(Set.of("input")) || !row.getAsJsonObject("outputs").keySet().equals(Set.of("output")) || bool(row.getAsJsonObject("inputs").get("input"))!=bool(last.get("input")) || bool(row.getAsJsonObject("outputs").get("output"))!=bool(last.get("lamp")))throw new IllegalArgumentException("Observer endpoint differs from actual trace");
            if((requirePass || index<cases.size()-1) && (!complete || !passed))throw new IllegalArgumentException("Observer evidence has not proved its complete temporal contract");
            previous=tick;
        }
    }
    static boolean bool(JsonElement value){if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("Expected observer evidence boolean");return value.getAsBoolean();}
    private static int integer(JsonElement value){try{return Math.toIntExact(longInteger(value));}catch(ArithmeticException invalid){throw new IllegalArgumentException("Invalid observer evidence integer",invalid);}}
    private static long longInteger(JsonElement value){if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Expected observer evidence integer");try{return value.getAsBigDecimal().longValueExact();}catch(ArithmeticException | NumberFormatException invalid){throw new IllegalArgumentException("Invalid observer evidence integer",invalid);}}
}
