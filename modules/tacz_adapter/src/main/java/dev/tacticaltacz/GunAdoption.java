package dev.tacticaltacz;
import java.util.*;
import com.tacz.guns.api.item.IGun;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
/** 2026-09-13 approved 15-gun boundary; unrelated packs are not implicitly adopted. */
public final class GunAdoption {
 public static final Map<ResourceLocation,String> CALIBERS;
 static {
  var map=new LinkedHashMap<ResourceLocation,String>();
  add(map,"9x19","glock_17","uzi");add(map,"556x45","m16a1","m4a1","scar_l");
  add(map,"12/70","m870","aa12");add(map,"57x28","p90");add(map,"58x42","qbz_191");
  add(map,"762x51","scar_h","m700","mk14");add(map,"45acp","ump45");
  add(map,"762x39","sks_tactical");add(map,"338lapua","ai_awp");dev.tacticaltacz.assembled.AssembledWeapons.all().forEach(w->map.put(w.GUN,w.caliber));CALIBERS=Collections.unmodifiableMap(map);
 }
 private static void add(Map<ResourceLocation,String> m,String caliber,String... names){for(String n:names)m.put(ResourceLocation.parse("tacz:"+n),caliber);}
 public static String caliber(ItemStack s){return s.getItem() instanceof IGun g?CALIBERS.get(g.getGunId(s)):null;}
 public static boolean contains(ItemStack s){return caliber(s)!=null;}
 private GunAdoption(){}
}
