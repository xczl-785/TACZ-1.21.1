package dev.tacticaltacz;

import com.tacz.guns.api.extension.AssemblyEntryExtension;
import com.tacz.guns.api.extension.AssemblyEntryExtensions;
import com.tacz.guns.api.extension.GunClientExtension;
import com.tacz.guns.api.extension.GunClientExtensions;
import com.tacz.guns.api.extension.GunPlatformExtension;
import com.tacz.guns.api.extension.GunPlatformExtensions;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlatformExtensionContractTest {
    @Test void discoversExactlyOneCommonAndClientOwner() {
        var common = ServiceLoader.load(GunPlatformExtension.class, GunPlatformExtension.class.getClassLoader())
                .stream().map(ServiceLoader.Provider::get).toList();
        var client = ServiceLoader.load(GunClientExtension.class, GunClientExtension.class.getClassLoader())
                .stream().map(ServiceLoader.Provider::get).toList();
        assertEquals(1, common.size());
        assertInstanceOf(TacticalGunPlatformExtension.class, common.getFirst());
        assertInstanceOf(TacticalGunPlatformExtension.class, GunPlatformExtensions.current());
        assertEquals(1, client.size());
        assertInstanceOf(TacticalGunClientExtension.class, client.getFirst());
        assertInstanceOf(TacticalGunClientExtension.class, GunClientExtensions.current());
    }

    /** The assembly entry owner keeps TaCZ's own refit key out of the way for claimed items. */
    @Test void discoversExactlyOneAssemblyEntryOwner() {
        var entries = ServiceLoader.load(AssemblyEntryExtension.class, AssemblyEntryExtension.class.getClassLoader())
                .stream().map(ServiceLoader.Provider::get).toList();
        assertEquals(1, entries.size());
        assertInstanceOf(TacticalAssemblyEntryExtension.class, entries.getFirst());
        assertInstanceOf(TacticalAssemblyEntryExtension.class, AssemblyEntryExtensions.current());
    }
}
