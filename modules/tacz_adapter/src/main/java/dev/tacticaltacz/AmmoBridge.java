package dev.tacticaltacz;
import dev.tarkovcontent.ammunition.*;
import com.tacz.guns.api.item.IGun;
import dev.firearms.ammunition.*;
import dev.tarkovcontent.ammunition.TarkovAmmunitionItem;
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
    private static final java.util.Map<String,AmmunitionDefinition> ENTRIES=AmmunitionCatalog.load().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(AmmunitionDefinition::id,java.util.function.Function.identity()));
    public static AmmunitionDefinition definition(ItemStack gun){var ammo=ammunition(gun);return ammo==null?null:ENTRIES.get(ammo.definition().id());}
    private AmmoBridge() {}
    public static boolean managed(ItemStack gun) {
        return GunAdoption.contains(gun);
    }
    public static TarkovAmmunitionItem ammunition(ItemStack gun) {
        if (!managed(gun)) return null;
        var id = ResourceLocation.tryParse(gun.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getString(KEY));
        return id != null && BuiltInRegistries.ITEM.get(id) instanceof TarkovAmmunitionItem ammo && ammo.definition().caliber().equals(GunAdoption.caliber(gun)) ? ammo : null;
    }
    public static void select(ItemStack gun, TarkovAmmunitionItem ammo) {
        if (!GunAdoption.contains(gun)) throw new IllegalArgumentException("Unsupported gun");
        var g=(IGun)gun.getItem();
        var current=ammunition(gun);var feed=new FeedState(java.util.Optional.ofNullable(current).map(a->new AmmunitionIdentity(a.definition().id(),a.definition().caliber())),g.getCurrentAmmoCount(gun),g.hasBulletInBarrel(gun),Math.max(g.getCurrentAmmoCount(gun),0));
        if (!feed.canChangeVariant()) throw new IllegalStateException("Empty magazine AND chamber before selecting ammunition");
        if (!ammo.definition().caliber().equals(GunAdoption.caliber(gun)) || g.useInventoryAmmo(gun) || g.useDummyAmmo(gun))
            throw new IllegalStateException("Only matching physical ammunition loading is supported");
        gun.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, data -> data.update(tag -> tag.putString(KEY, ammo.definition().id())));
    }
    public static boolean matches(ItemStack gun, ItemStack ammo) {
        var selected = ammunition(gun);
        return selected != null && ammo.is(selected);
    }
    public static BallisticSnapshot snapshot(ItemStack gun) {
        var ammo = ammunition(gun);
        if (ammo == null) return null;
        var d = definition(gun);
        if (d == null) return null;
        return new BallisticSnapshot(new AmmunitionIdentity(d.id(),d.caliber()),d.fleshDamage(),d.penetrationPower(),d.armorDamage(),d.initialSpeed(),d.projectileCount());
    }
    public static TarkovAmmunitionItem candidate(Player player,ItemStack gun) {
        if(!managed(gun))return null;
        var g=(IGun)gun.getItem();
        if(g.getCurrentAmmoCount(gun)>0||g.hasBulletInBarrel(gun))return ammunition(gun);
        var supply=AmmunitionSupplies.current().orElse(null);if(supply==null)return null;
        var found=supply.find(player,stack->stack.getItem() instanceof TarkovAmmunitionItem ammo && ammo.definition().caliber().equals(GunAdoption.caliber(gun)));
        return found.getItem() instanceof TarkovAmmunitionItem ammo?ammo:null;
    }
    public static boolean hasAmmo(Player player, ItemStack gun) {
        var ammo=candidate(player,gun);
        var supply=AmmunitionSupplies.current().orElse(null);return ammo!=null&&supply!=null&&!supply.find(player,stack->stack.is(ammo)).isEmpty();
    }
    public static int reserveCount(Player player,ItemStack gun) {
        var ammo=candidate(player,gun);var supply=AmmunitionSupplies.current().orElse(null);return ammo==null||supply==null?0:supply.count(player,stack->stack.is(ammo));
    }
    public static int consume(ServerPlayer player, ItemStack gun, int needed) {
        if (needed <= 0) return 0;
        // Existing public API commits a single matching stack atomically; never access native hidden slots.
        var selected=candidate(player,gun);
        var supply=AmmunitionSupplies.current().orElse(null);if(selected==null||supply==null)return 0;
        int paid = 0;
        while (paid < needed) {
            var found = supply.find(player, stack -> stack.is(selected));
            if (found.isEmpty()) break;
            int amount = Math.min(needed - paid, found.getCount());
            var payment = supply.consume(player, gunAmmo -> gunAmmo.is(selected), amount);
            if (payment.isEmpty()) break;
            gun.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, data -> data.update(tag -> tag.putString(KEY, selected.definition().id())));
            paid += payment.getCount();
        }
        return paid;
    }
    public static boolean refund(ServerPlayer player,java.util.List<ItemStack> stacks){return AmmunitionSupplies.current().map(supply->supply.refund(player,stacks)).orElse(false);}
}
