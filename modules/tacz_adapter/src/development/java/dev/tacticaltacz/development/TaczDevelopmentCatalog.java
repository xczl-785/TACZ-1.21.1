package dev.tacticaltacz.development;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import dev.tarkovcontent.TarkovContent;
import dev.tacticalinventory.verification.DevelopmentItemCatalog;
import dev.tacticaltacz.GunAdoption;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** TaCZ knowledge stays in its optional adapter, outside the inventory framework. */
@EventBusSubscriber(modid="tacz")
public final class TaczDevelopmentCatalog {
    @SubscribeEvent public static void populate(DevelopmentItemCatalog.Populate event){
        for(var name:java.util.List.of("ammo","modern_kinetic_gun","attachment"))event.remove(ResourceLocation.parse("tacz:"+name));
        for(var id:GunAdoption.CALIBERS.keySet())TimelessAPI.getCommonGunIndex(id).ifPresent(index->{
            var data=index.getGunData();
            var stack=GunItemBuilder.create().setId(id).setFireMode(data.getFireModeSet().getFirst())
                .setHeatData(data.hasHeatData()).setAmmoCount(0).setAmmoInBarrel(false).build(null);
            var assembled=dev.tacticaltacz.assembled.AssembledWeapons.byId(id);
            if(assembled!=null)stack=assembled.preset();
            event.put(ResourceLocation.fromNamespaceAndPath(id.getNamespace(),"gun/"+id.getPath()),assembled!=null?assembled.developmentCategory:"tacz",stack);
        });
        for(var weapon:dev.tacticaltacz.assembled.AssembledWeapons.all()) for(var entry:weapon.ITEMS.entrySet()){
            var id=ResourceLocation.parse(entry.getValue());event.remove(id);
            if(!entry.getKey().equals(weapon.ROOT))event.put(id,weapon.developmentCategory,weapon.createPart(entry.getKey()));
        }
        for(var entry:TimelessAPI.getAllCommonAttachmentIndex()){
            var id=entry.getKey();
            event.put(ResourceLocation.fromNamespaceAndPath(id.getNamespace(),"attachment/"+id.getPath()),"tacz",AttachmentItemBuilder.create().setId(id).build());
        }
        for(var holder:TarkovContent.AMMUNITION.values()){
            var item=holder.get();event.put(BuiltInRegistries.ITEM.getKey(item),"tacz",item.getDefaultInstance());
        }
    }
}
