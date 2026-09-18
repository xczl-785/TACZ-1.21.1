package dev.tacticaltacz.assembled;

import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.attachment.AttachmentType;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** New optical IDs use the same physical slot and retain state on the returned item. */
class OpticalSampleStateTest {
    @BeforeAll static void boot() throws Exception { NativeAssemblyStateTest.boot(); }

    @Test void independentlyInstallReplaceAndReturnBothSamples() {
        var weapon=AssembledWeapons.byId(ResourceLocation.parse("tacz_fork_tarkov:m4a1"));
        var original=weapon.preset();
        var path=List.of("upper","scope");
        var red=weapon.createPart("fork_sight_t2_sample");
        var magnified=weapon.createPart("fork_scope_elcan_sample");
        var installed=AssemblyGunExchange.plan(original,red,path).orElseThrow().held();
        assertTrue(NativeAttachmentProjection.get(original,AttachmentType.SCOPE).isEmpty());
        assertEquals(ResourceLocation.parse("tacz_fork_tarkov:sight_t2_sample"),
            IAttachment.getIAttachmentOrNull(red).getAttachmentId(NativeAttachmentProjection.get(installed,AttachmentType.SCOPE)));
        var replacement=AssemblyGunExchange.plan(installed,magnified,path).orElseThrow();
        assertEquals(1,replacement.returned().size());
        assertEquals(AssembledWeapon.identity(red),AssembledWeapon.identity(replacement.returned().getFirst()));
        var tag=new CompoundTag();tag.putInt("ZoomNumber",1);
        var zoomed=replacement.held();
        NativeAttachmentProjection.setTag(zoomed,AttachmentType.SCOPE,tag);
        var removed=AssemblyGunExchange.plan(zoomed,ItemStack.EMPTY,path).orElseThrow();
        assertEquals(AssembledWeapon.identity(magnified),AssembledWeapon.identity(removed.returned().getFirst()));
        assertEquals(1,removed.returned().getFirst().get(net.minecraft.core.component.DataComponents.CUSTOM_DATA).copyTag().getInt("ZoomNumber"));
        assertTrue(NativeAttachmentProjection.get(removed.held(),AttachmentType.SCOPE).isEmpty());
        assertEquals(weapon.project(original),weapon.project(removed.held()));
    }
}
