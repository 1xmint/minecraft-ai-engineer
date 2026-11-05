package dev.minecraftai.engineer;

import java.util.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Resource-movement predictions only. Never takes a result or inserts a stack. */
final class OakCraftRecoveryPlan implements PhysicalMenuProtocol {
    private final List<PhysicalCraftFrame> frames;
    private final List<Integer> slots;
    private OakCraftRecoveryPlan(List<PhysicalCraftFrame> frames,List<Integer> slots){this.frames=List.copyOf(frames);this.slots=List.copyOf(slots);}
    static OakCraftRecoveryPlan inspect(PhysicalCraftFrame current,int source){
        if(source<0 || source>=9)throw new IllegalArgumentException("Recovery needs the original hotbar slot");
        var inventory=current.inventory();var menu=current.menu();var grid=menu.inputs();var cursor=menu.cursor();
        var log=new ItemStack(Items.OAK_LOG);var planks=new ItemStack(Items.OAK_PLANKS,4);
        for(int slot=0;slot<inventory.size();slot++)if(slot!=source && !inventory.get(slot).isEmpty())throw new IllegalStateException("Other resources need operator inspection; no recovery was planned");
        for(int slot=1;slot<grid.size();slot++)if(!grid.get(slot).isEmpty())throw new IllegalStateException("Unsupported recovery grid; preserve its resources");
        var held=inventory.get(source);var input=grid.getFirst();
        if(!held.isEmpty()){
            if(!menu.empty() || !current.preview().isEmpty() || (!ItemStack.matches(held,log) && !ItemStack.matches(held,planks)))throw new IllegalStateException("Recovery resources do not match a settled oak transaction");
            return new OakCraftRecoveryPlan(List.of(current),List.of());
        }
        var empty=new PhysicalMenuSnapshot(Collections.nCopies(4,ItemStack.EMPTY),ItemStack.EMPTY);
        if(!cursor.isEmpty()){
            if(!input.isEmpty() || !current.preview().isEmpty() || (!ItemStack.matches(cursor,log) && !ItemStack.matches(cursor,planks)))throw new IllegalStateException("Unsupported carried recovery resource");
            var returned=new ArrayList<>(inventory);returned.set(source,cursor);
            return new OakCraftRecoveryPlan(List.of(current,new PhysicalCraftFrame(returned,empty,ItemStack.EMPTY,current.selected())),List.of(36+source));
        }
        if(!ItemStack.matches(input,log))throw new IllegalStateException("Recovery needs its retained oak log or four planks; nothing is assumed lost or empty");
        // The current derived preview is observed, never collected. Removing the
        // only input clears it even if the server recipe has changed meanwhile.
        var carried=new PhysicalCraftFrame(inventory,new PhysicalMenuSnapshot(Collections.nCopies(4,ItemStack.EMPTY),input),ItemStack.EMPTY,current.selected());
        var returned=new ArrayList<>(inventory);returned.set(source,input);
        return new OakCraftRecoveryPlan(List.of(current,carried,new PhysicalCraftFrame(returned,empty,ItemStack.EMPTY,current.selected())),List.of(1,36+source));
    }
    public int clicks(){return slots.size();}
    public int slot(int step){requireStep(step);return slots.get(step-1);}
    public PhysicalCraftFrame before(int step){requireStep(step);return frames.get(step-1);}
    public PhysicalCraftFrame after(int step){requireStep(step);return frames.get(step);}
    public boolean needsSettledStart(){return false;}
    public void validateNative(net.minecraft.server.level.ServerPlayer actor){
        // Resource movement must remain available if the recipe was removed or
        // changed. This protocol never takes its derived result.
        for(int slot:slots)if(slot!=1 && (slot<36 || slot>44))throw new IllegalStateException("Recovery cannot take a result or unrelated slot");
    }
    PhysicalCraftFrame settled(){return frames.getLast();}
    private void requireStep(int step){if(step<1 || step>slots.size())throw new IllegalArgumentException("Unsupported resource recovery step");}
}
