package dev.tacticaltacz.assembled;

import net.minecraft.server.Bootstrap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeAssemblyIconsFallbackTest {
    @BeforeAll static void boot(){Bootstrap.bootStrap();}
    @Test void nonAssemblyStackUsesCallerFallbackAndNominalBounds(){
        var fallback=ResourceLocation.parse("test:fallback");
        assertEquals(fallback,NativeAssemblyIcons.texture(ItemStack.EMPTY,fallback));
        assertEquals(new dev.itemfoundation.client.api.ItemModelBounds.Bounds(-8,-8,8,8),NativeAssemblyIcons.bounds(ItemStack.EMPTY));
    }
    @Test void missingPhysicalIdentityDoesNotReachRenderingOrEscapeGui(){
        var weapon=AssembledWeapons.byId(ResourceLocation.parse("tacz_assembly:m4a1"));
        var malformed=new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(weapon.GUN));
        var fallback=ResourceLocation.parse("test:native-icon");
        for(int i=0;i<3;i++){
            assertEquals(fallback,NativeAssemblyIcons.texture(malformed,fallback));
            assertEquals(new dev.itemfoundation.client.api.ItemModelBounds.Bounds(-8,-8,8,8),NativeAssemblyIcons.bounds(malformed));
        }
    }
}
