package dev.tacticaltacz.refit;

import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import dev.firearms.workbench.*;
import dev.tacticaltacz.AmmoBridge;
import dev.tacticaltacz.GunAdoption;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Native TaCZ guns: only the attachment-specific half of the shared refit flow. The request, the
 * quote, the catalogue, the single transaction and the result wording conventions come from the
 * public service; this class never keeps a session and never touches the inventory.
 */
public final class NativeGunRefitProvider implements WorkbenchProvider {
    /** Something a player can hold that this family refuses to edit. */
    @Override public boolean handles(ItemStack held) {
        return !dev.tacticaltacz.assembled.AssembledWeapons.isGun(held) && GunAdoption.contains(held);
    }

    @Override public boolean ready(ServerPlayer player, ItemStack held) {
        var gun = IGun.getIGunOrNull(held);
        if (gun == null || gun.hasAttachmentLock(held) || !GunAdoption.contains(held)) return false;
        var operator = com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(player);
        return !operator.getSynIsBolting() && operator.getSynReloadState().getCountDown() < 0
                && operator.getSynShootCoolDown() <= 0 && operator.getSynDrawCoolDown() <= 0;
    }

    @Override public Predicate<ItemStack> stock(ItemStack held) { return stack -> IAttachment.getIAttachmentOrNull(stack) != null; }

    @Override public int candidateLimit() { return 4096; }

    /** Native refit advertises every quoted attachment; there is no per-slot projection to filter. */
    @Override public Optional<WorkbenchCandidate> candidate(ItemStack held, WorkbenchInventoryHost.Source source) {
        return Optional.of(new WorkbenchCandidate(source.id().toString(), source.stack()));
    }

    /** An empty payment removes the occupant of the addressed attachment type; a stack installs it. */
    @Override public Optional<WorkbenchInventoryHost.Change> plan(ServerPlayer player, ItemStack held, ItemStack payment, List<String> path) {
        var gun = IGun.getIGunOrNull(held);
        if (gun == null || gun.hasAttachmentLock(held) || !GunAdoption.contains(held)) return Optional.empty();
        AttachmentType type;
        if (!payment.isEmpty()) {
            var part = IAttachment.getIAttachmentOrNull(payment);
            if (part == null || !gun.allowAttachment(held, payment)) return Optional.empty();
            type = part.getType(payment);
        } else {
            type = typeOf(path);
        }
        if (type == AttachmentType.NONE) return Optional.empty();
        var old = gun.getAttachment(player.registryAccess(), held, type);
        if (payment.isEmpty() && old.isEmpty()) return Optional.empty();
        var refunds = new ArrayList<ItemStack>();
        if (!old.isEmpty()) refunds.add(old);
        if (!payment.isEmpty()) gun.installAttachment(player.registryAccess(), held, payment);
        else gun.unloadAttachment(player.registryAccess(), held, type);
        var installed = gun.getAttachment(player.registryAccess(), held, type);
        if (!payment.isEmpty() ? !ItemStack.matches(installed, payment) : !installed.isEmpty()) return Optional.empty();
        if (type == AttachmentType.EXTENDED_MAG) {
            int count = gun.getCurrentAmmoCount(held);
            if (count > 0) {
                var ammo = AmmoBridge.ammunition(held);
                if (ammo == null) return Optional.empty();
                refunds.add(new ItemStack(ammo, count));
                gun.setCurrentAmmoCount(held, 0);
            }
            // Preserve the chamber and selected variant, as native TaCZ dropAllAmmo does.
        }
        return Optional.of(new WorkbenchInventoryHost.Change(held, refunds));
    }

    @Override public void committed(ServerPlayer player, ItemStack held) {
        AttachmentPropertyManager.postChangeEvent(player, held);
    }

    @Override public String resultText(WorkbenchOutcome outcome, int action) {
        return switch (outcome) {
            case ACCEPTED -> action == 1 ? "installed" : "unloaded";
            case STALE -> "stale";
            case REJECTED -> "rejected";
            case NONE -> "";
        };
    }

    /** Keep a real reason when the exchange already produced one; only a fresh catalogue is unavailable. */
    @Override public String unavailableText(WorkbenchOutcome outcome, int action) {
        return outcome == WorkbenchOutcome.NONE ? "unavailable" : resultText(outcome, action);
    }

    /** This family addresses an attachment type, not a slot path: the target descriptor is its name. */
    static List<String> target(AttachmentType type) {
        return type == null || type == AttachmentType.NONE ? List.of() : List.of(type.name());
    }

    static AttachmentType typeOf(List<String> target) {
        if (target.size() != 1) return AttachmentType.NONE;
        try {
            return AttachmentType.valueOf(target.getFirst());
        } catch (IllegalArgumentException unknown) {
            return AttachmentType.NONE;
        }
    }
}
