package dev.tacticaltacz.assembled;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import dev.firearms.assembly.AssemblyCatalog;
import dev.firearms.assembly.AssemblyNode;
import dev.firearms.client.workbench.WorkbenchClientProvider;
import dev.firearms.client.workbench.WorkbenchModelBackend;
import dev.firearms.client.workbench.WorkbenchPresentation;
import dev.firearms.presentation.AssemblyMaterials;
import dev.firearms.presentation.ModelGeometry;
import java.util.*;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * TaCZ's client half of the public workbench: how to project a quoted stack locally and how to
 * present it. The screen, its lifecycle and the quote token belong to the public controller.
 */
public final class AssemblyGunClientProvider implements WorkbenchClientProvider {
    @Override public boolean handles(ItemStack quoted) { return AssembledWeapons.isGun(quoted); }

    @Override public Optional<Projection> projection(ItemStack quoted) {
        return Optional.ofNullable(AssembledWeapons.from(quoted)).map(GunProjection::new);
    }

    @Override public Optional<WorkbenchPresentation> present(ItemStack quoted, Context context) {
        var weapon = AssembledWeapons.from(quoted);
        if (weapon == null) return Optional.empty();
        var display = TimelessAPI.getClientGunIndex(weapon.GUN).orElseThrow().getDefaultDisplay();
        var displayModel = display.getGunModel();
        var geometry = displayModel instanceof AssemblyGunModel assembled ? assembled.geometry() : NativeAssemblyView.geometry(weapon);
        var materials = displayModel instanceof AssemblyGunModel assembled ? assembled.materials() : NativeAssemblyView.materials(weapon, geometry);
        var backend = displayModel == null ? null : new NativeWorkbenchScene(weapon, display.createWorkbenchGunModel(),
                display.getModelTexture(), display.enablesTransparency(), context.quotedStack(), context.shownTree(), context.candidatePayloads());
        return Optional.of(new Presentation(quoted.getHoverName().getString(), geometry, materials,
                id -> partName(weapon, id), weapon::partIcon, backend));
    }

    /** Converted attachment properties are only re-applied for the native rig, mirroring the held path. */
    @Override public void committed(ItemStack held) {
        var weapon = AssembledWeapons.from(held);
        if (weapon != null && weapon.nativeRig) AttachmentPropertyManager.postChangeEvent(Minecraft.getInstance().player, held);
    }

    private static String partName(AssembledWeapon weapon, String id) {
        return weapon.nativeRig ? weapon.createPart(id).getHoverName().getString()
                : Component.translatable("item." + weapon.ITEMS.get(id).replace(':', '.')).getString();
    }

    /** Equal for two stacks of one weapon definition, so a refresh can detect a weapon change. */
    private record GunProjection(AssembledWeapon weapon) implements Projection {
        @Override public AssemblyCatalog catalog() { return weapon.CATALOG; }
        @Override public AssemblyNode tree(ItemStack stack) { return weapon.project(stack); }
        // Presence suppresses misleading converted stats in preset mode.
        @Override public Optional<String> statsExplanation() { return weapon.nativeRig ? Optional.of("") : Optional.empty(); }
    }

    private record Presentation(String title, Map<String,ModelGeometry> geometry, AssemblyMaterials materials,
                                Function<String,String> partName, Function<String,ResourceLocation> partImage,
                                WorkbenchModelBackend backend) implements WorkbenchPresentation {}
}
