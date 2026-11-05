package dev.minecraftai.engineer;

import com.google.gson.*;
import java.util.*;

/** Recent observations only. No pass, permission, input journal or execution state is restored here. */
final class NativeTrialHistory {
    static final int LIMIT=64,MAX_ROW_CHARS=32768;
    private final LinkedHashMap<String,JsonObject> records=new LinkedHashMap<>();
    void capture(JsonObject scope,JsonArray rows){
        if(scope==null || rows==null || rows.isEmpty() || !rows.get(0).isJsonObject() || !rows.get(0).getAsJsonObject().has("baselineSamples"))return;
        if(rows.size()>3 || rows.toString().length()>MAX_ROW_CHARS)throw new IllegalStateException("Native trial history exceeds its observation bound");
        var first=rows.get(0).getAsJsonObject();
        if(!first.has("tick") || !first.has("elapsedTicks"))return;
        var accepted=FunctionalTrial.validateHistoricalScope(scope);
        long start=Math.subtractExact(first.get("tick").getAsBigDecimal().longValueExact(),first.get("elapsedTicks").getAsBigDecimal().longValueExact());
        if(start<0)throw new IllegalStateException("Negative native trial start");
        String key=accepted.toString()+"@"+start;
        var record=new JsonObject();record.add("scope",accepted.deepCopy());record.add("rows",rows.deepCopy());
        records.put(key,record);
        while(records.size()>LIMIT)records.remove(records.keySet().iterator().next());
    }
    JsonArray json(){var out=new JsonArray();records.values().forEach(value->out.add(value.deepCopy()));return out;}
    void load(JsonArray array){
        if(array.size()>LIMIT)throw new IllegalStateException("Too many stored native trial attempts");
        var loaded=new NativeTrialHistory();
        for(var element:array){
            var record=element.getAsJsonObject();
            if(!record.keySet().equals(Set.of("scope","rows")))throw new IllegalStateException("Unknown native trial history field");
            var rows=record.getAsJsonArray("rows");int before=loaded.records.size();
            loaded.capture(record.getAsJsonObject("scope"),rows);
            if(loaded.records.size()!=before+1)throw new IllegalStateException("Missing or duplicate native trial identity");
        }
        records.clear();records.putAll(loaded.records);
    }
    List<TrialEvidenceTally.Attempt> attempts(){return records.values().stream().map(record->new TrialEvidenceTally.Attempt(record.getAsJsonObject("scope").deepCopy(),record.getAsJsonArray("rows").deepCopy())).toList();}
}
