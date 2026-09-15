package dev.weaponruntime;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;
/** A profile reference, not a second copy of the physical assembly. */
public final class WeaponRuntime {
    private static final DeferredRegister.DataComponents COMPONENTS=DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE,"weapon_runtime");
    public static final DeferredHolder<DataComponentType<?>,DataComponentType<String>> PROFILE=COMPONENTS.registerComponentType("profile",b->b.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));
    private WeaponRuntime() {}
    /** Called once by TaCZ; the persistent component ID stays unchanged. */
    public static void register(IEventBus bus){COMPONENTS.register(bus);}
}
