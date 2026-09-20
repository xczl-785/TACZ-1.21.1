package dev.tacticaltacz;
import dev.tarkovcontent.ammunition.*;
import dev.tarkovcontent.ammunition.*;

import dev.itemfoundation.api.inspection.InspectionSection;
import dev.itemfoundation.api.inspection.InspectionSection.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Source statistics for adopted ammunition; presentation does not alter firing behavior. */
public final class AmmunitionInspection {
    private static final Map<String,AmmunitionDefinition> ENTRIES=AmmunitionCatalog.load().stream()
            .collect(Collectors.toUnmodifiableMap(AmmunitionDefinition::id, Function.identity()));
    private AmmunitionInspection() {}
    public static List<InspectionSection> inspect(ItemStack stack) {
        if(stack.isEmpty() || !(stack.getItem() instanceof TarkovAmmunitionItem ammo))return List.of();
        var entry=ENTRIES.get(ammo.definition().id());
        return entry==null ? List.of() : List.of(section(entry));
    }
    public static InspectionSection section(AmmunitionDefinition entry) {
        String recoil=new BigDecimal(Float.toString(entry.recoilModifier())).stripTrailingZeros().toPlainString();
        if(entry.recoilModifier()>0)recoil="+"+recoil;
        return new InspectionSection("tarkov_content:ammunition",Component.empty(),List.of(
                number("damage",entry.fleshDamage(),Component.empty()),
                text("recoil",recoil),
                text("caliber",entry.caliber()),
                number("penetration",entry.penetrationPower(),Component.empty()),
                number("speed",entry.initialSpeed(),label("speed_unit"))
        ),InspectionSection.ATTRIBUTES,20);
    }
    private static Component label(String id) { return Component.translatable("inspection.tarkov_content.ammunition."+id); }
    private static Row number(String id,double value,Component unit) {
        return row(id,Field.number(id,label(id),value,unit));
    }
    private static Row text(String id,String value) { return row(id,Field.text(id,label(id),Component.literal(value))); }
    private static Row row(String id,Field value) {
        return new Row(id,label(id),Component.empty(),"attribute",List.of(value),List.of());
    }
}
