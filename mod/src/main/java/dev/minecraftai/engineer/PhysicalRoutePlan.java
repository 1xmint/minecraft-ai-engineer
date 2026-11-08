package dev.minecraftai.engineer;

import java.util.*;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;

/** Pure bounded walking search. A route is an intention, never movement permission. */
record PhysicalRoutePlan(List<BlockPos> cells) {
    private static final int MAX_VISITED=1024,MAX_STEPS=128;
    PhysicalRoutePlan {
        cells=cells.stream().map(BlockPos::immutable).toList();
        if(cells.isEmpty() || cells.size()>MAX_STEPS+1)throw new IllegalArgumentException("Invalid route length");
        for(int i=1;i<cells.size();i++){
            var before=cells.get(i-1);var next=cells.get(i);
            if(Math.abs((long)next.getY()-before.getY())>1 || Math.abs((long)next.getX()-before.getX())+Math.abs((long)next.getZ()-before.getZ())!=1)
                throw new IllegalArgumentException("Routes require adjacent cardinal steps of at most one block up or down");
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
    /** Walking rules: each returns null when admitted, otherwise a plain reason naming the block and position. */
    interface Terrain {
        String standing(BlockPos feet);
        String step(BlockPos from,BlockPos to);
    }
    private static final int[] RISES={0,1,-1};
    /** Cardinal walking search over real terrain: level steps first, then one block up, then one block down. */
    static PhysicalRoutePlan walking(BlockPos start,BlockPos destination,Terrain terrain){
        start=start.immutable();destination=destination.immutable();
        if(Math.abs((long)start.getX()-destination.getX())+Math.abs((long)start.getZ()-destination.getZ())>64 || Math.abs((long)start.getY()-destination.getY())>16)
            throw new IllegalStateException("Destination exceeds walking navigation range");
        var reason=terrain.standing(start);
        if(reason!=null)throw new IllegalStateException("The companion is not standing on walkable ground: "+reason);
        reason=terrain.standing(destination);
        if(reason!=null)throw new IllegalStateException("The stance at "+destination.toShortString()+" is not walkable: "+reason);
        var queue=new ArrayDeque<BlockPos>();var previous=new HashMap<BlockPos,BlockPos>();
        queue.add(start);previous.put(start,start);
        BlockPos blocked=null;String why=null;long rank=Long.MAX_VALUE;
        while(!queue.isEmpty()){
            var cell=queue.removeFirst();
            if(cell.equals(destination)){
                var reversed=new ArrayList<BlockPos>();var cursor=cell;
                while(!cursor.equals(start)){reversed.add(cursor);cursor=previous.get(cursor);}
                reversed.add(start);Collections.reverse(reversed);
                if(reversed.size()>MAX_STEPS+1)throw new IllegalStateException("The walking route is longer than "+MAX_STEPS+" steps");
                return new PhysicalRoutePlan(reversed);
            }
            for(var next:List.of(cell.north(),cell.east(),cell.south(),cell.west())){
                var reasons=new String[RISES.length];BlockPos accepted=null;boolean seen=false;
                for(int i=0;i<RISES.length;i++){
                    var candidate=next.offset(0,RISES[i],0).immutable();
                    if(previous.containsKey(candidate)){seen=true;break;}
                    reasons[i]=terrain.standing(candidate);
                    if(reasons[i]==null)reasons[i]=terrain.step(cell,candidate);
                    if(reasons[i]==null){accepted=candidate;break;}
                }
                if(seen)continue;
                if(accepted==null){
                    // Report the closest real obstacle; the workspace edge only when nothing else blocked the way.
                    var explained=explain(reasons,next);
                    long score=(explained.startsWith("Outside")?1L<<40:0)+Math.abs((long)next.getX()-destination.getX())+Math.abs((long)next.getY()-destination.getY())+Math.abs((long)next.getZ()-destination.getZ());
                    if(score<rank){rank=score;blocked=next.immutable();why=explained;}
                    continue;
                }
                if(previous.size()>=MAX_VISITED)throw new IllegalStateException("Navigation search exceeds its bounded workspace budget");
                previous.put(accepted,cell);queue.addLast(accepted);
            }
        }
        throw new IllegalStateException("No walking route reaches "+destination.toShortString()+(why==null?"":"; nearest blocked step "+blocked.toShortString()+": "+why));
    }
    private static String explain(String[] reasons,BlockPos next){
        for(var reason:reasons)if(reason.startsWith("Dangerous"))return reason;
        if(reasons[0].startsWith("Blocked"))return reasons[1].startsWith("Blocked")?"A climb taller than one block at "+next.toShortString():reasons[1];
        if(reasons[0].startsWith("No solid support"))return reasons[2].startsWith("No solid support")?"A drop deeper than one block at "+next.toShortString():reasons[2];
        return reasons[0];
    }
}
