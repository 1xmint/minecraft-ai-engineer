package dev.minecraftai.engineer;

import com.google.gson.*;

/** Ordered whole-circuit observations only. Caller owns scope and exact input restoration. */
final class ConnectedEvidence {
    private ConnectedEvidence() { }
    static int vector(int index){if(index<0 || index>=8)throw new IllegalArgumentException("Connected case outside bounded suite");return index<4?index:7-index;}
    static void validate(JsonArray cases,String digest,boolean complete){
        if(digest==null || !digest.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Invalid connected graph digest");
        if(cases==null || cases.size()>8 || complete && cases.size()!=8)throw new IllegalArgumentException("Connected suite needs exactly eight ordered cases to complete");
        long previousEnd=-1;
        for(int index=0;index<cases.size();index++){
            var value=cases.get(index);if(!value.isJsonObject())throw new IllegalArgumentException("Invalid connected case");
            int vector=vector(index);var trace=ConnectedTrace.read(value.getAsJsonObject(),digest,(vector&2)!=0,(vector&1)!=0);
            var snapshot=trace.json();long start=snapshot.get("startGameTick").getAsLong();
            if(start<=previousEnd)throw new IllegalArgumentException("Connected case clocks overlap or go backward");
            if((complete || index<cases.size()-1) && !trace.passed())throw new IllegalArgumentException("Earlier or complete connected cases must have all consecutive settled observations");
            previousEnd=start+ConnectedTrace.END;
        }
    }
}
