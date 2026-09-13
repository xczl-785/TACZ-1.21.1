package com.tacz.guns.ammunition;
import java.util.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;
/** 2026-09-13: fork owns registration; old namespace deliberately preserved for saves. */
public final class AmmunitionRegistry {
 private static final DeferredRegister.Items ITEMS=DeferredRegister.createItems("tarkov_content");
 public static final Map<String,DeferredItem<TarkovAmmoItem>> AMMUNITION;
 static {
  var rounds=new LinkedHashMap<String,DeferredItem<TarkovAmmoItem>>();
  for(var entry:AmmunitionContent.load())rounds.put(entry.sourceId(),ITEMS.register(entry.id().split(":",2)[1],()->new TarkovAmmoItem(entry.definition(),entry.stackMaxSize())));
  AMMUNITION=Collections.unmodifiableMap(rounds);
 }
 private AmmunitionRegistry(){}
 public static void register(IEventBus bus){ITEMS.register(bus);}
}
