package com.tacz.guns.api.extension;

import java.util.List;
import java.util.ServiceLoader;

public final class GunClientExtensions {
    private static final GunClientExtension NONE = new GunClientExtension() {};
    private static final GunClientExtension INSTANCE = load();
    private static GunClientExtension load() {
        List<GunClientExtension> providers = ServiceLoader.load(GunClientExtension.class,
                GunClientExtension.class.getClassLoader()).stream().map(ServiceLoader.Provider::get).toList();
        if (providers.size() > 1) throw new IllegalStateException("Multiple TaCZ client extensions: " + providers);
        return providers.isEmpty() ? NONE : providers.getFirst();
    }
    public static GunClientExtension current() { return INSTANCE; }
    private GunClientExtensions() {}
}
