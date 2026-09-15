package dev.tacticaltacz.verification;

import dev.itemfoundation.api.assembly.*;
import dev.weaponruntime.*;
import dev.weaponassembly.io.AssemblyJson;
import dev.tacticalinventory.api.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import java.util.*;

/** One physical development barrel; no registration or sample data ships in the player Jar. */
@EventBusSubscriber(modid="tacz",bus=EventBusSubscriber.Bus.MOD)
public final class BarrelFireFixture {
    public static final String PROFILE="tactical_tacz_adapter:glock_barrel_test";
    public static final ResourceLocation BARREL=ResourceLocation.parse("tactical_tacz_adapter:assembly_test_barrel");
    @SubscribeEvent public static void items(net.neoforged.neoforge.registries.RegisterEvent event){
        event.register(Registries.ITEM,helper->helper.register(BARREL,new Item(new Item.Properties().stacksTo(1))));
    }
    @SubscribeEvent public static void setup(net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event){event.enqueueWork(()->{
        try(var in=BarrelFireFixture.class.getResourceAsStream("/assembly-fire-test/catalog.json")) {
            if(in==null)throw new IllegalStateException("No barrel catalog");
            var catalog=AssemblyJson.readCatalog(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
            WeaponCapabilities.register(PROFILE,new WeaponCapabilities.Profile("tacz:glock_17",catalog,"5a7ae0c351dfba0017554310",
                Map.of(BARREL.toString(),"5a6b5f868dc32e000a311389"),List.of(List.of("mod_barrel"))));
        }catch(java.io.IOException ex){throw new java.io.UncheckedIOException(ex);}
    });}
    public static ItemStack barrel(){var stack=new ItemStack(BuiltInRegistries.ITEM.get(BARREL));var tag=new CompoundTag();tag.putUUID("assembly_test_instance",UUID.randomUUID());stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));return stack;}
    private static UUID identity(ItemStack part){return part.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getUUID("assembly_test_instance");}
    public static ItemStack attach(ItemStack gun,ItemStack barrel){
        var result=gun.copy();result.set(WeaponRuntime.PROFILE.get(),PROFILE);
        var state=AssemblyTrees.state(result);if(state.in("mod_barrel").isPresent())throw new IllegalArgumentException("Occupied barrel slot");
        var children=new ArrayList<>(state.installed());children.add(new AssemblyState.Installed("mod_barrel",identity(barrel),barrel,true));
        result.set(AssemblyComponents.STATE.get(),state.updated(children));return result;
    }
    public static boolean exchange(ServerPlayer player,boolean install){
        var view=TacticalHeldExchange.inspect(player,s->BuiltInRegistries.ITEM.getKey(s.getItem()).equals(BARREL));
        if(view.isEmpty()||!PROFILE.equals(view.get().held().get(WeaponRuntime.PROFILE.get())))return false;
        var source=install?view.get().sources().stream().findFirst().map(TacticalHeldExchange.Source::id):Optional.<UUID>empty();
        if(install&&source.isEmpty())return false;
        return TacticalHeldExchange.exchange(player,view.get(),source,(held,payment)->{
            var state=AssemblyTrees.state(held);var existing=state.in("mod_barrel");
            if(install){
                if(existing.isPresent()||!BuiltInRegistries.ITEM.getKey(payment.getItem()).equals(BARREL))return Optional.empty();
                try{var result=attach(held,payment);if(!WeaponCapabilities.firing(result,"tacz:glock_17").ready())return Optional.empty();return Optional.of(new TacticalHeldExchange.Change(result,List.of()));}
                catch(IllegalArgumentException e){return Optional.empty();}
            }
            if(existing.isEmpty())return Optional.empty();
            var result=held.copy();result.set(AssemblyComponents.STATE.get(),state.updated(state.installed().stream().filter(n->!n.slotId().equals("mod_barrel")).toList()));
            return Optional.of(new TacticalHeldExchange.Change(result,List.of(existing.get().stack())));
        });
    }
}
