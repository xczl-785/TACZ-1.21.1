package dev.tacticaltacz.assembled;

import dev.itemfoundation.api.assembly.AssemblyTrees;
import java.util.List;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeWorkbenchStackTest {
    @BeforeAll static void boot(){
        Bootstrap.bootStrap();var definitions=new java.util.ArrayList<dev.itemfoundation.api.assembly.AssemblyDefinition>();
        for(var weapon:AssembledWeapons.all())if(weapon.nativeRig){
            var json=com.google.gson.JsonParser.parseString(AssembledWeapon.resource("data/"+weapon.GUN.getNamespace()+"/assembly/"+weapon.GUN.getPath()+".json")).getAsJsonObject();
            for(var entry:json.getAsJsonArray("items")){
                var object=entry.getAsJsonObject();var slots=new java.util.ArrayList<dev.itemfoundation.api.assembly.AssemblyDefinition.Slot>();
                for(var value:object.getAsJsonArray("slots")){var slot=value.getAsJsonObject();var ids=new java.util.HashSet<String>();for(var id:slot.getAsJsonArray("compatibleItems"))ids.add(id.getAsString());slots.add(new dev.itemfoundation.api.assembly.AssemblyDefinition.Slot(slot.get("id").getAsString(),ids,java.util.Set.of(),java.util.Set.of(),false));}
                definitions.add(new dev.itemfoundation.api.assembly.AssemblyDefinition(object.get("itemId").getAsString(),slots));
            }
        }
        dev.itemfoundation.api.assembly.AssemblyDefinitions.replace(definitions);
    }

    @Test void materializesThePreviewTreeWithoutMutatingTheHeldGun() {
        var weapon=AssembledWeapons.byId(net.minecraft.resources.ResourceLocation.parse("tacz_assembly:m4a1"));
        var held=weapon.preset();var before=held.copy();
        var target=weapon.ENGINE.replace(weapon.project(held),List.of("upper","barrel_mount","handguard"),
                dev.weaponassembly.api.AssemblyNode.leaf(java.util.UUID.randomUUID(),"handguard_tactical")).after();
        target=weapon.ENGINE.install(target,List.of("upper","barrel_mount","handguard","grip"),
                dev.weaponassembly.api.AssemblyNode.leaf(java.util.UUID.randomUUID(),"tacz_grip_rk1_b25u")).after();

        var rendered=NativeWorkbenchStack.materialize(weapon,held,target);

        assertEquals(target,weapon.project(rendered));
        assertTrue(net.minecraft.world.item.ItemStack.matches(before,held));
        AssemblyTrees.validate(rendered,AssembledWeapon.identity(rendered));
    }

    @Test void everyNativeGunCanUseTheSharedWorkbenchProjection() {
        for(var weapon:AssembledWeapons.all())if(weapon.nativeRig){
            var held=weapon.preset();var rendered=NativeWorkbenchStack.materialize(weapon,held,weapon.project(held));
            assertEquals(weapon.project(held),weapon.project(rendered),weapon.PROFILE);
            assertTrue(net.minecraft.world.item.ItemStack.matches(held,rendered),weapon.PROFILE);
        }
    }
}
