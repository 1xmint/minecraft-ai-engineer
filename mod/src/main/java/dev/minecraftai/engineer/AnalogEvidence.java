package dev.minecraftai.engineer;

import com.google.gson.*;
import java.util.*;
import net.minecraft.core.BlockPos;

/** Settled analog suite replay. Caller owns project/revision/epoch and all execution authority. */
final class AnalogEvidence {
    private static final List<int[]> CASES=cases();
    private static final Set<String> FIELDS=Set.of("case","tick","elapsedTicks","inputs","outputs","analog");
    private AnalogEvidence() { }
    private static List<int[]> cases(){
        var result=new ArrayList<int[]>();
        for(int bank=0;bank<3;bank++)for(int level=0;level<=15;level++){int[] values={8,0,0};values[bank]=level;result.add(values);}
        for(int[] values:new int[][]{{0,0,0},{1,0,0},{15,0,0},{8,3,0},{8,0,3},{8,8,0},{8,0,8},{8,9,0},{8,0,9},{8,3,7},{8,7,3},{3,15,0},{15,14,1},{15,1,14},{1,1,1},{0,15,15},{15,15,15},{0,0,0}})result.add(values);
        return List.copyOf(result);
    }
    static int count(){return CASES.size();}
    static int[] requested(int index){return CASES.get(index).clone();}
    static Map<String,Boolean> controls(AnalogDesign design,int[] values){
        var result=new LinkedHashMap<String,Boolean>();for(var selector:design.selectors())result.put("bank-"+selector.bank()+"-"+selector.index(),design.selected(selector,values));return Map.copyOf(result);
    }
    static void validate(JsonArray rows,String mode,boolean requirePass){
        if(rows==null || rows.size()>count() || (requirePass && rows.size()!=count()))throw new IllegalArgumentException("Incomplete or oversized analog suite");
        var design=new AnalogDesign(mode,BlockPos.ZERO,0);
        long previousEnd=-1;
        for(int index=0;index<rows.size();index++){
            var row=rows.get(index).getAsJsonObject();
            if(!row.keySet().equals(FIELDS) || integer(row.get("case"))!=index)throw new IllegalArgumentException("Invalid analog case identity or fields");
            var trace=AnalogTrace.read(row.getAsJsonObject("analog"),mode,requested(index));var data=trace.json();var samples=data.getAsJsonArray("samples");
            long started=integer(data.get("startedTick"));
            if(started<previousEnd || (index>0 && !AnalogTrace.read(rows.get(index-1).getAsJsonObject().getAsJsonObject("analog"),mode,requested(index-1)).passed()))throw new IllegalArgumentException("Analog suite cannot skip failed cases or reorder native time");
            long tick=started;int elapsed=0;int output=0;
            if(!samples.isEmpty()){var last=samples.get(samples.size()-1).getAsJsonObject();tick=integer(last.get("tick"));elapsed=Math.toIntExact(integer(last.get("elapsedTicks")));output=Math.toIntExact(integer(last.get("output")));}
            if(integer(row.get("tick"))!=tick || integer(row.get("elapsedTicks"))!=elapsed)throw new IllegalArgumentException("Analog summary cannot change its observed game clock");
            var inputs=new JsonObject();controls(design,requested(index)).forEach(inputs::addProperty);var outputs=new JsonObject();outputs.addProperty("output",output>0);
            if(!inputs.equals(row.get("inputs")) || !outputs.equals(row.get("outputs")))throw new IllegalArgumentException("Analog summary disagrees with its intended selectors or numeric observation");
            if(requirePass && !trace.passed())throw new IllegalArgumentException("A numeric case has no complete functional pass");
            previousEnd=tick;
        }
    }
    private static long integer(JsonElement value){if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Expected analog suite integer");try{return value.getAsBigDecimal().longValueExact();}catch(ArithmeticException invalid){throw new IllegalArgumentException("Invalid analog suite integer",invalid);}}
}
