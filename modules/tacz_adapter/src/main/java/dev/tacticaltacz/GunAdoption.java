package dev.tacticaltacz;
import java.util.*;
import com.tacz.guns.api.item.IGun;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
/** Canonical assembled-firearm boundary; original TaCZ gun identities are not adopted. */
public final class GunAdoption {
 public static final Map<ResourceLocation,String> CALIBERS;
 static {
  var map=new LinkedHashMap<ResourceLocation,String>();
  dev.tacticaltacz.assembled.AssembledWeapons.all().forEach(w->map.put(w.GUN,w.caliber));CALIBERS=Collections.unmodifiableMap(map);
 }
 public static String caliber(ItemStack s){return s.getItem() instanceof IGun g?CALIBERS.get(g.getGunId(s)):null;}
 public static boolean contains(ItemStack s){return caliber(s)!=null;}
 private GunAdoption(){}
}
