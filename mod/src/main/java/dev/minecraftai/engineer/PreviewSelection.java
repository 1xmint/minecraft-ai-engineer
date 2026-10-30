package dev.minecraftai.engineer;

import java.util.*;
import net.minecraft.world.phys.Vec3;

/** Bounded presentation selection from an approved snapshot. Never reads or edits a world. */
public final class PreviewSelection {
    public static final int MAX_VISIBLE_BLOCKS=256;
    public static final double RADIUS=48;
    private record Candidate(ProjectView.Ghost ghost,int priority,double distance) { }
    private static final Comparator<Candidate> ORDER=Comparator.comparingInt(Candidate::priority).thenComparingDouble(Candidate::distance)
        .thenComparingInt(c->c.ghost().pos().getX()).thenComparingInt(c->c.ghost().pos().getY()).thenComparingInt(c->c.ghost().pos().getZ());
    private PreviewSelection() { }
    public static List<ProjectView.Ghost> nearby(ProjectView view,Vec3 camera){
        if(view==null)return List.of();
        var selected=new PriorityQueue<Candidate>(MAX_VISIBLE_BLOCKS,ORDER.reversed());
        for(var ghost:view.blocks()){
            double x=ghost.pos().getX()+.5-camera.x,y=ghost.pos().getY()+.5-camera.y,z=ghost.pos().getZ()+.5-camera.z;
            double distance=x*x+y*y+z*z;if(distance>RADIUS*RADIUS)continue;
            int priority=ghost.matches()?(ghost.currentStage()?3:4):ghost.currentStage()?0:ghost.wrong()?1:2;
            var candidate=new Candidate(ghost,priority,distance);
            if(selected.size()<MAX_VISIBLE_BLOCKS)selected.add(candidate);
            else if(ORDER.compare(candidate,selected.peek())<0){selected.remove();selected.add(candidate);}
        }
        var ordered=new ArrayList<>(selected);ordered.sort(ORDER);
        return ordered.stream().map(Candidate::ghost).toList();
    }
}
