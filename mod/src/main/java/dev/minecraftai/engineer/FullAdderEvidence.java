package dev.minecraftai.engineer;

import com.google.gson.*;

/** Scope-free recorded suite validation; this never restores permission or grants a current pass. */
final class FullAdderEvidence {
    private FullAdderEvidence(){ }
    static int vector(int index){if(index<0 || index>=16)throw new IllegalArgumentException("Full-adder case outside bounded suite");return index<8?index:15-index;}
    static void validate(JsonArray cases,String digest,boolean complete){
        if(digest==null || !digest.matches("[a-f0-9]{64}") || cases==null || cases.size()>16 || complete && cases.size()!=16)throw new IllegalArgumentException("Full-adder suite needs exactly16 ordered cases to complete");
        long previousEnd=-1;
        for(int index=0;index<cases.size();index++){
            if(!cases.get(index).isJsonObject())throw new IllegalArgumentException("Invalid full-adder case");
            var trace=FullAdderTrace.read(cases.get(index).getAsJsonObject(),digest,vector(index));long start=trace.json().get("startGameTick").getAsLong();
            if(start<=previousEnd)throw new IllegalArgumentException("Full-adder case clocks overlap or go backward");
            if((complete || index<cases.size()-1) && !trace.passed())throw new IllegalArgumentException("Full-adder earlier/complete cases need every settled observation");
            previousEnd=start+FullAdderTrace.END;
        }
    }
}
