package dev.tacticaltacz;
import dev.tarkovcontent.ammunition.*;
import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AdoptedAmmunitionTest {
    @Test void approvedRoundsRetainSourceParametersAndStorageProfiles() throws Exception {
        var entries=AmmunitionCatalog.load();assertEquals(86,entries.size());assertEquals(12,entries.stream().map(AmmunitionDefinition::caliber).distinct().count());
        try(var in=AmmunitionCatalog.class.getResourceAsStream("/data/tarkov_content/catalog/ammunition.json")) {
            var rows=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonArray();
            for(int i=0;i<rows.size();i++) {
                var row=rows.get(i).getAsJsonObject();var original=row.getAsJsonObject("source");var entry=entries.get(i);
                assertEquals(original.get("project_caliber").getAsString(),entry.caliber());
                assertEquals(original.get("Damage").getAsFloat(),entry.fleshDamage());
                assertEquals(original.get("PenetrationPower").getAsFloat(),entry.penetrationPower());
                assertEquals(original.get("ArmorDamage").getAsFloat(),entry.armorDamage());
                assertEquals(original.get("InitialSpeed").getAsFloat(),entry.initialSpeed());
                assertEquals(original.get("ammoRec").getAsFloat(),entry.recoilModifier());
                assertEquals(original.get("StackMaxSize").getAsInt(),entry.stackMaxSize());
                try(var profile=AmmunitionCatalog.class.getResourceAsStream("/data/tarkov_content/item_foundation/items/ammo_"+entry.sourceId()+".json")) {
                    assertNotNull(profile);var p=JsonParser.parseReader(new InputStreamReader(profile,StandardCharsets.UTF_8)).getAsJsonObject();
                    assertEquals(original.get("Weight").getAsDouble(),p.get("weight_kg").getAsDouble());
                    assertEquals("tarkov_content:ammunition",p.get("category").getAsString());
                    assertEquals(1,p.getAsJsonArray("footprint").get(0).getAsInt());
                }
                assertNotNull(AmmunitionCatalog.class.getResource("/assets/tarkov_content/textures/item/ammo_"+entry.sourceId()+".png"));
            }
        }
    }
}
