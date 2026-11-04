package dev.minecraftai.engineer;

import java.util.*;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;

/** Pure bounded flat-ground search. A route is an intention, never movement permission. */
record PhysicalRoutePlan(List<BlockPos> cells) {
    private static final int MAX_VISITED=1024,MAX_STEPS=128;
    PhysicalRoutePlan {
        cells=cells.stream().map(BlockPos::immutable).toList();
        if(cells.isEmpty() || cells.size()>MAX_STEPS+1)throw new IllegalArgumentException("Invalid route length");
        for(int i=1;i<cells.size();i++){
            var before=cells.get(i-1);var next=cells.get(i);
            if(next.getY()!=before.getY() || Math.abs((long)next.getX()-before.getX())+Math.abs((long)next.getZ()-before.getZ())!=1)
                throw new IllegalArgumentException("Routes require adjacent flat cardinal steps");
        }
    }
    static PhysicalRoutePlan search(BlockPos start,BlockPos destination,Predicate<BlockPos> admitted){
        start=start.immutable();destination=destination.immutable();
        if(start.getY()!=destination.getY() || Math.abs((long)start.getX()-destination.getX())+Math.abs((long)start.getZ()-destination.getZ())>64)
            throw new IllegalStateException("Destination exceeds flat navigation range");
        if(!admitted.test(start) || !admitted.test(destination))throw new IllegalStateException("Route endpoints are not clear supported walking cells");
        var queue=new ArrayDeque<BlockPos>();var previous=new HashMap<BlockPos,BlockPos>();
        queue.add(start);previous.put(start,start);
        while(!queue.isEmpty()){
            var cell=queue.removeFirst();
            if(cell.equals(destination)){
                var reversed=new ArrayList<BlockPos>();var cursor=cell;
                while(!cursor.equals(start)){reversed.add(cursor);cursor=previous.get(cursor);}
                reversed.add(start);Collections.reverse(reversed);return new PhysicalRoutePlan(reversed);
            }
            // Stable order makes equally short routes reproducible without diagonal corner cutting.
            for(var next:List.of(cell.north(),cell.east(),cell.south(),cell.west())){
                if(previous.containsKey(next) || !admitted.test(next))continue;
                if(previous.size()>=MAX_VISITED)throw new IllegalStateException("Navigation search exceeds its bounded workspace budget");
                previous.put(next,cell);queue.addLast(next);
            }
        }
        throw new IllegalStateException("No clear flat route reaches the requested stance");
    }
}
