package dev.tacticaltacz.assembled;

import dev.firearms.assembly.AssemblyNode;
import dev.firearms.workbench.WorkbenchPreviewStacks;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** TaCZ host of the shared preview builder: content lookup and identity stay here, the build is public. */
public final class NativeWorkbenchStack {
    public static ItemStack materialize(AssembledWeapon weapon,ItemStack quoted,AssemblyNode target) {
        return WorkbenchPreviewStacks.materialize(new Host(weapon),quoted,target);
    }

    public static ItemStack materialize(AssembledWeapon weapon,ItemStack quoted,AssemblyNode target,Map<UUID,ItemStack> payloads) {
        return WorkbenchPreviewStacks.materialize(new Host(weapon),quoted,target,payloads);
    }

    private static final class Host implements WorkbenchPreviewStacks.Parts {
        private final AssembledWeapon weapon;
        Host(AssembledWeapon weapon){this.weapon=weapon;}
        public String rootDefinitionId(){return weapon.ROOT;}
        public boolean isGun(ItemStack stack){return weapon.isGun(stack);}
        public String definition(ItemStack stack){return weapon.definition(stack);}
        public ItemStack createPart(String definitionId){return weapon.createPart(definitionId);}
        public AssemblyNode project(ItemStack stack){return weapon.project(stack);}
        public UUID identity(ItemStack stack){return AssembledWeapon.identity(stack);}
        public void identify(ItemStack stack,UUID instanceId){
            var tag=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
            tag.putUUID("newmod_assembly_instance",instanceId);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
        }
    }

    private NativeWorkbenchStack() {}
}
