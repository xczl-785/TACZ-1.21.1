package dev.tacticaltacz.assembled;

import dev.itemfoundation.api.assembly.AssemblyComponents;
import dev.itemfoundation.api.assembly.AssemblyState;
import dev.itemfoundation.api.assembly.AssemblyTrees;
import dev.weaponassembly.api.AssemblyNode;
import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Builds a render-only physical stack for a workbench preview; it never mutates the quoted item. */
public final class NativeWorkbenchStack {
    private record Existing(ItemStack stack,boolean enabled) {}

    public static ItemStack materialize(AssembledWeapon weapon,ItemStack quoted,AssemblyNode target) {
        if(quoted.isEmpty()||!weapon.isGun(quoted)||!target.definitionId().equals(weapon.ROOT))return ItemStack.EMPTY;
        if(target.equals(weapon.project(quoted)))return quoted.copy();
        var existing=new HashMap<UUID,Existing>();collect(quoted,true,existing);
        var root=build(weapon,quoted.copy(),target,existing);
        AssemblyTrees.validate(root,AssembledWeapon.identity(root));return root;
    }

    private static ItemStack build(AssembledWeapon weapon,ItemStack seed,AssemblyNode node,Map<UUID,Existing> existing) {
        ItemStack stack=seed;var preserved=existing.get(node.instanceId());
        if(preserved!=null&&weapon.definition(preserved.stack()).equals(node.definitionId()))stack=preserved.stack().copy();
        else if(!weapon.definition(stack).equals(node.definitionId()))stack=weapon.createPart(node.definitionId());
        identify(stack,node.instanceId());
        var children=new ArrayList<AssemblyState.Installed>();
        node.children().forEach((slot,child)->{
            var old=existing.get(child.instanceId());
            var childSeed=old==null?weapon.createPart(child.definitionId()):old.stack().copy();
            var built=build(weapon,childSeed,child,existing);
            children.add(new AssemblyState.Installed(slot,child.instanceId(),built,old==null||old.enabled()));
        });
        if(children.isEmpty())stack.remove(AssemblyComponents.STATE.get());
        else stack.set(AssemblyComponents.STATE.get(),AssemblyTrees.state(stack).updated(children));
        return stack;
    }

    private static void collect(ItemStack stack,boolean enabled,Map<UUID,Existing> out) {
        out.put(AssembledWeapon.identity(stack),new Existing(stack.copy(),enabled));
        for(var child:AssemblyTrees.state(stack).installed())collect(child.stack(),child.enabled(),out);
    }

    private static void identify(ItemStack stack,UUID id) {
        var tag=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        tag.putUUID("newmod_assembly_instance",id);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
    }

    private NativeWorkbenchStack() {}
}
