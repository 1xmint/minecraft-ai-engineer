package dev.minecraftai.engineer;

import com.google.gson.*;
import java.util.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Complete off/on/off evidence plus independently captured original-state restoration. */
final class ShutterEvidence {
    private ShutterEvidence() { }
    static void validate(JsonArray cases,ShutterDesign.Design design,BlockState original,boolean requirePass){
        if(original==null || !original.is(Blocks.LEVER))throw new IllegalStateException("Mechanical evidence needs its captured original input");
        boolean originallyOn=original.getValue(BlockStateProperties.POWERED);
        if(!original.equals(design.pose(originallyOn).get("input")))throw new IllegalStateException("Original mechanical input differs from accepted geometry");
        int count=originallyOn?4:3;
        if(cases.size()>count || requirePass && cases.size()!=count)throw new IllegalStateException("Mechanical evidence needs all phases and original-state restoration");
        long previous=-1;
        for(int index=0;index<cases.size();index++){
            var row=cases.get(index).getAsJsonObject();boolean expected=index==1 || index==3;
            if(!row.keySet().equals(Set.of("case","purpose","tick","elapsedTicks","inputs","outputs","trace")) || number(row.get("case"))!=index || !new JsonPrimitive(index<3?"test":"restore-original").equals(row.get("purpose")))throw new IllegalStateException("Mechanical case differs from accepted sequence");
            var trace=ShutterTrace.read(row.getAsJsonObject("trace"),design,expected);var samples=trace.json().getAsJsonArray("samples");
            if(samples.isEmpty() || (requirePass || index+1<cases.size()) && !trace.passed())throw new IllegalStateException("Mechanical phase is incomplete or failed");
            var last=samples.get(samples.size()-1).getAsJsonObject();long tick=number(row.get("tick")),elapsed=number(row.get("elapsedTicks"));
            if(tick<elapsed || elapsed!=number(last.get("elapsedTicks")) || previous>=0 && tick-elapsed!=previous)throw new IllegalStateException("Mechanical case game-tick timestamps disagree");
            var inputs=row.getAsJsonObject("inputs");var outputs=row.getAsJsonObject("outputs");
            boolean aperture=Blueprint.state(last.getAsJsonObject("states").get("aperture").getAsString()).is(Blocks.STONE);
            if(!inputs.keySet().equals(Set.of("input")) || !outputs.keySet().equals(Set.of("aperture")) || !new JsonPrimitive(expected).equals(inputs.get("input")) || !new JsonPrimitive(aperture).equals(outputs.get("aperture")))throw new IllegalStateException("Mechanical summary differs from raw observed samples");
            previous=tick;
        }
    }
    private static long number(JsonElement value){
        if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())throw new IllegalStateException("Expected mechanical evidence integer");
        try{long n=value.getAsBigDecimal().longValueExact();if(n<0 || n>9_007_199_254_740_991L)throw new ArithmeticException();return n;}catch(ArithmeticException bad){throw new IllegalStateException("Invalid mechanical evidence integer",bad);}
    }
}
