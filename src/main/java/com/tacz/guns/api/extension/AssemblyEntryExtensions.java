package com.tacz.guns.api.extension;

import java.util.List;
import java.util.ServiceLoader;

/** Loads at most one external assembly entry owner. */
public final class AssemblyEntryExtensions {
    private static final AssemblyEntryExtension NONE = new AssemblyEntryExtension() {};
    private static final AssemblyEntryExtension INSTANCE = load();

    private static AssemblyEntryExtension load() {
        List<AssemblyEntryExtension> providers = ServiceLoader.load(AssemblyEntryExtension.class,
                AssemblyEntryExtension.class.getClassLoader()).stream().map(ServiceLoader.Provider::get).toList();
        if (providers.size() > 1) throw new IllegalStateException("Multiple TaCZ assembly entry extensions: " + providers);
        return providers.isEmpty() ? NONE : providers.getFirst();
    }

    public static AssemblyEntryExtension current() { return INSTANCE; }

    private AssemblyEntryExtensions() {}
}
