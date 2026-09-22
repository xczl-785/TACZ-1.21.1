package dev.tacticaltacz.assembled;

import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import dev.firearms.workbench.*;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * TaCZ's half of the public workbench flow for its native-rig assembled guns. It answers questions
 * about TaCZ content only; requests, quotes, candidate view and the inventory commit belong to the
 * public service. It owns no screen, token or session.
 */
public final class AssemblyGunProvider implements WorkbenchProvider {
    @Override public boolean handles(ItemStack held) { return AssembledWeapons.isGun(held); }

    @Override public boolean ready(ServerPlayer player, ItemStack held) { return AssemblyGunExchange.ready(player); }

    @Override public Predicate<ItemStack> stock(ItemStack held) {
        var weapon = AssembledWeapons.from(held);
        return weapon == null ? stack -> false : weapon::isPart;
    }

    @Override public Optional<WorkbenchCandidate> candidate(ItemStack held, WorkbenchInventoryHost.Source source) {
        var weapon = AssembledWeapons.from(held);
        if (weapon == null) return Optional.empty();
        // Malformed/unidentified items are never advertised as free replacements.
        try {
            return Optional.of(new WorkbenchCandidate(source.id().toString(), weapon.proposalPart(source.stack(), source.id())));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    @Override public ItemStack payment(ItemStack held, ItemStack source, Optional<UUID> sourceId) {
        if (source.isEmpty()) return source;
        var weapon = AssembledWeapons.from(held);
        return weapon == null ? source : weapon.proposalPart(source, sourceId.orElseThrow());
    }

    @Override public Optional<WorkbenchInventoryHost.Change> plan(ServerPlayer player, ItemStack held, ItemStack payment, List<String> path) {
        return AssemblyGunExchange.plan(held, payment, path);
    }

    @Override public void committed(ServerPlayer player, ItemStack held) {
        AttachmentPropertyManager.postChangeEvent(player, held);
    }

    @Override public String resultText(WorkbenchOutcome outcome, int action) {
        return switch (outcome) {
            case ACCEPTED -> "committed";
            case NONE -> "";
            default -> "rejected";
        };
    }

    @Override public String toString() { return "tacz assembled workbench provider"; }
}
