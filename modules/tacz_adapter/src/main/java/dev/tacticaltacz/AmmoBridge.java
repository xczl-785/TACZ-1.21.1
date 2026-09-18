package dev.tacticaltacz;
import com.tacz.guns.ammunition.*;
import com.tacz.guns.api.item.IGun;
import dev.tacticalcombat.api.BallisticProfile;
import dev.tacticalinventory.api.TacticalAmmunition;
import com.tacz.guns.ammunition.TarkovAmmoItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Single-variant loading policy. Vanilla TaCZ owns counts/chamber; this component owns variant identity.
 * Change of variant is legal only after both magazine and chamber are empty. */
public final class AmmoBridge {
    public static final String KEY = "tactical_tacz_adapter:ammunition_v1";
    public static final ResourceLocation GUN = ResourceLocation.parse("tacz_fork_tarkov:glock_17");
    public static final ResourceLocation CALIBER = ResourceLocation.parse("tacz:9mm");
    private static final java.util.Map<String,AmmunitionContent.Entry> ENTRIES=AmmunitionContent.load().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(AmmunitionContent.Entry::id,java.util.function.Function.identity()));
    public static AmmunitionContent.Entry definition(ItemStack gun){var ammo=ammunition(gun);return ammo==null?null:ENTRIES.get(ammo.definition().id());}
    private AmmoBridge() {}
    public static boolean managed(ItemStack gun) {
        return GunAdoption.contains(gun);
    }
    public static TarkovAmmoItem ammunition(ItemStack gun) {
        if (!managed(gun)) return null;
        var id = ResourceLocation.tryParse(gun.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getString(KEY));
        return id != null && BuiltInRegistries.ITEM.get(id) instanceof TarkovAmmoItem ammo && ammo.definition().caliber().equals(GunAdoption.caliber(gun)) ? ammo : null;
    }
    public static void select(ItemStack gun, TarkovAmmoItem ammo) {
        if (!GunAdoption.contains(gun)) throw new IllegalArgumentException("Unsupported gun");
        var g=(IGun)gun.getItem();
        if (g.getCurrentAmmoCount(gun) != 0 || g.hasBulletInBarrel(gun)) throw new IllegalStateException("Empty magazine AND chamber before selecting ammunition");
        if (!ammo.definition().caliber().equals(GunAdoption.caliber(gun)) || g.useInventoryAmmo(gun) || g.useDummyAmmo(gun))
            throw new IllegalStateException("Only matching physical ammunition loading is supported");
        gun.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, data -> data.update(tag -> tag.putString(KEY, ammo.definition().id())));
    }
    public static boolean matches(ItemStack gun, ItemStack ammo) {
        var selected = ammunition(gun);
        return selected != null && ammo.is(selected);
    }
    public static BallisticProfile snapshot(ItemStack gun) {
        var ammo = ammunition(gun);
        if (ammo == null) return null;
        var d = ammo.definition();
        return new BallisticProfile(d.id(), d.caliber(), d.fleshDamage(), d.penetrationPower(), d.armorDamage());
    }
    public static TarkovAmmoItem candidate(Player player,ItemStack gun) {
        if(!managed(gun))return null;
        var g=(IGun)gun.getItem();
        if(g.getCurrentAmmoCount(gun)>0||g.hasBulletInBarrel(gun))return ammunition(gun);
        var found=TacticalAmmunition.find(player,stack->stack.getItem() instanceof TarkovAmmoItem ammo && ammo.definition().caliber().equals(GunAdoption.caliber(gun)));
        return found.getItem() instanceof TarkovAmmoItem ammo?ammo:null;
    }
    public static boolean hasAmmo(Player player, ItemStack gun) {
        var ammo=candidate(player,gun);
        return ammo!=null&&!TacticalAmmunition.find(player,stack->stack.is(ammo)).isEmpty();
    }
    public static int reserveCount(Player player,ItemStack gun) {
        var ammo=candidate(player,gun);return ammo==null?0:TacticalAmmunition.count(player,stack->stack.is(ammo));
    }
    public static int consume(ServerPlayer player, ItemStack gun, int needed) {
        if (needed <= 0) return 0;
        // Existing public API commits a single matching stack atomically; never access native hidden slots.
        var selected=candidate(player,gun);
        if(selected==null)return 0;
        int paid = 0;
        while (paid < needed) {
            var found = TacticalAmmunition.find(player, stack -> stack.is(selected));
            if (found.isEmpty()) break;
            int amount = Math.min(needed - paid, found.getCount());
            var payment = TacticalAmmunition.consume(player, gunAmmo -> gunAmmo.is(selected), amount);
            if (payment.isEmpty()) break;
            gun.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, data -> data.update(tag -> tag.putString(KEY, selected.definition().id())));
            paid += payment.getCount();
        }
        return paid;
    }
}
