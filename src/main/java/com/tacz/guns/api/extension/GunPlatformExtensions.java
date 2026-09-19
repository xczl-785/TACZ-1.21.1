package com.tacz.guns.api.extension;

import java.util.List;
import java.util.ServiceLoader;
import net.neoforged.bus.api.IEventBus;

/** Loads at most one platform owner. Standalone TaCZ uses the fail-closed no-op extension. */
public final class GunPlatformExtensions {
    private static final GunPlatformExtension NONE = new GunPlatformExtension() {};
    private static final GunPlatformExtension INSTANCE = load();

    private static GunPlatformExtension load() {
        List<GunPlatformExtension> providers = ServiceLoader.load(GunPlatformExtension.class,
                GunPlatformExtension.class.getClassLoader()).stream().map(ServiceLoader.Provider::get).toList();
        if (providers.size() > 1) throw new IllegalStateException("Multiple TaCZ gun platform extensions: " + providers);
        return providers.isEmpty() ? NONE : providers.getFirst();
    }

    public static GunPlatformExtension current() { return INSTANCE; }
    public static void register(IEventBus modBus) { INSTANCE.register(modBus); }
    private GunPlatformExtensions() {}
}
