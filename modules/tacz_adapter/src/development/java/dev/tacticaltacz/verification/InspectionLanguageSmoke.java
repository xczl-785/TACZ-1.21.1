package dev.tacticaltacz.verification;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import dev.itemfoundation.api.definition.DisplayText;
import dev.itemfoundation.api.inspection.InspectionSection;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/** One real client resolves the same server-exported contribution against both actual language packs. */
final class InspectionLanguageSmoke {
    private InspectionLanguageSmoke() {}
    static void verify(JsonElement wireSections, JsonElement wireDisplay) throws IOException {
        var sections = InspectionSection.CODEC.listOf().parse(JsonOps.INSTANCE, wireSections).getOrThrow();
        var texts = texts(sections);
        var display = dev.itemfoundation.api.definition.ItemDisplayData.CODEC.parse(JsonOps.INSTANCE, wireDisplay).getOrThrow();
        var projectedDescription = display.descriptionText().orElseThrow();
        require(projectedDescription.translated(), "server-exported description must retain translation key");
        texts.add(projectedDescription.component());
        require(texts.stream().flatMap(text -> keys(text).stream()).anyMatch(key -> key.startsWith("protection.tactical_combat.")),
                "server export retains translatable protection components");
        var source = JsonParser.parseString(Files.readString(Path.of("../../mods/tarkov_content/scripts/armor/descriptions.json")))
                .getAsJsonObject().getAsJsonObject("items");
        var catalog = resource("/data/tarkov_content/protection/catalog.json");
        var armors = catalog.getAsJsonObject("armors");
        var labelSource = JsonParser.parseString(Files.readString(Path.of("../../mods/tarkov_content/scripts/armor/labels.json"))).getAsJsonObject();
        var expectedLabels = new HashMap<String, JsonObject>();
        for (var group : labelSource.entrySet()) for (var entry : group.getValue().getAsJsonObject().entrySet()) {
            var label = entry.getValue().getAsJsonObject();
            require(expectedLabels.put(label.get("key").getAsString(), label) == null, "unique source label key");
        }
        require(armors.size() == 212 && source.size() == 212, "all 212 adopted protection descriptions are covered");
        var previous = Language.getInstance();
        var resolved = new LinkedHashMap<String, List<String>>();
        try {
            for (String locale : List.of("en_us", "zh_cn")) {
                var language = ClientLanguage.loadFrom(Minecraft.getInstance().getResourceManager(), List.of(locale), false);
                Language.inject(language);
                verifyDevelopmentText(language,locale);
                var snapshotPath=Path.of("../../../docs/参考资料/ammunition-audit/eft/snapshots/public-"+(locale.equals("zh_cn")?"zh":"en")+".json.gz");
                com.google.gson.JsonObject allSource;
                try(var input=new java.util.zip.GZIPInputStream(Files.newInputStream(snapshotPath))) {
                    allSource=JsonParser.parseReader(new java.io.InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("data");
                }
                int checkedItems=0, checkedProjectSupplies=0;
                for(var item:net.minecraft.core.registries.BuiltInRegistries.ITEM) {
                    var id=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
                    if(!id.getNamespace().equals("tarkov_content")||id.getPath().startsWith("test_"))continue;
                    var profile=resource("/data/tarkov_content/item_foundation/items/"+id.getPath()+".json");
                    var text=DisplayText.parse(profile.get("description"));
                    if(item instanceof dev.itemfoundation.api.behavior.ProfiledUseItem supply && supply.useProvider().equals(net.minecraft.resources.ResourceLocation.parse("tactical_character:medical"))){
                        require(text.translated()&&language.has(text.value())&&!text.component().getString().isBlank(),locale+" project medical description "+id);
                        require(language.has(item.getDescriptionId()),locale+" project medical name "+id);
                        checkedProjectSupplies++;continue;
                    }
                    var sourceId=id.getPath().substring(id.getPath().lastIndexOf('_')+1);
                    require(text.translated()&&language.has(text.value()),locale+" translated description "+id);
                    require(text.component().getString().equals(allSource.get(sourceId+" Description").getAsString()),locale+" source description "+id);
                    checkedItems++;
                }
                require(checkedProjectSupplies==6,"all six project medical supplies in "+locale);
                require(checkedItems==437,"all 437 registered EFT descriptions in "+locale);

                var checkedLabels = new HashSet<String>();
                for (String group : List.of("materials", "segmentTypes", "slotTypes")) {
                    for (var entry : catalog.getAsJsonObject(group).entrySet()) {
                        var label = DisplayText.parse(entry.getValue().getAsJsonObject().get("label"));
                        require(label.translated(), "catalog label remains translatable: " + group + "/" + entry.getKey());
                        require(language.has(label.value()), locale + " missing catalog label " + label.value());
                        var renderedLabel = label.component().getString();
                        require(!renderedLabel.isBlank(), locale + " empty catalog label " + label.value());
                        var expected = expectedLabels.get(label.value());
                        require(expected != null && renderedLabel.equals(expected.get(locale).getAsString()),
                                locale + " source/catalog label mismatch " + label.value());
                        require(checkedLabels.add(label.value()), "duplicate catalog label key " + label.value());
                    }
                }
                require(checkedLabels.size() == 29 && checkedLabels.equals(expectedLabels.keySet()), "all 29 source/catalog labels are checked");
                require(projectedDescription.component().getString().equals(source.getAsJsonObject("60a3c68c37ea821725773ef5").get(locale).getAsString()),
                        locale + " same server-exported display resolves source description");
                var rendered = new ArrayList<String>();
                for (var text : texts) {
                    for (var key : keys(text)) require(language.has(key), locale + " missing projected translation " + key);
                    rendered.add(text.getString());
                }
                resolved.put(locale, rendered);
                for (var entry : armors.entrySet()) {
                    var item = entry.getValue().getAsJsonObject().get("item").getAsString();
                    var path = item.substring(item.indexOf(':') + 1);
                    var definition = resource("/data/tarkov_content/item_foundation/items/" + path + ".json");
                    var description = DisplayText.parse(definition.get("description"));
                    require(description.translated(), "description remains a translation key: " + item);
                    require(language.has(description.value()), locale + " missing description " + item);
                    var sourceId = entry.getKey().substring(entry.getKey().indexOf(':') + 1);
                    require(description.component().getString().equals(source.getAsJsonObject(sourceId).get(locale).getAsString()),
                            locale + " source description mismatch " + item);
                }
                var itemName = Component.translatable("item.tarkov_content.container_60a3c68c37ea821725773ef5");
                require(language.has(((TranslatableContents)itemName.getContents()).getKey()), locale + " nested item name resource");
                var nested = Component.translatableWithFallback("smoke.missing.nested", "%s / %s", itemName, 12.5);
                var nestedWire = net.minecraft.network.chat.ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, nested).getOrThrow();
                var decoded = net.minecraft.network.chat.ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, nestedWire).getOrThrow();
                require(decoded.getString().equals(itemName.getString() + " / 12.5"), locale + " nested item name and numeric argument");
                require(Component.translatableWithFallback("smoke.missing.fallback", "Readable fallback").getString().equals("Readable fallback"), "explicit missing-key fallback");
                require(Component.translatable("smoke.missing.no_fallback").getString().equals("smoke.missing.no_fallback"), "missing key remains identifiable");
                require(sections.stream().flatMap(s -> s.rows().stream()).flatMap(r -> r.fields().stream())
                        .filter(f -> f.number().isPresent()).allMatch(f -> f.value().startsWith(InspectionSection.decimal(f.number().orElseThrow()))),
                        locale + " projected numeric values remain typed and render unchanged");
            }
            require(!resolved.get("en_us").equals(resolved.get("zh_cn")), "same decoded server projection changes with client language");
            require(InspectionSection.CODEC.listOf().encodeStart(JsonOps.INSTANCE, sections).getOrThrow().equals(wireSections),
                    "language resolution must not change projected wire components");
        } finally { Language.inject(previous); }
        Files.writeString(Path.of("inspection-language-smoke.pass"),
                "INSPECTION_LANGUAGE_SMOKE PASS: same server-exported sections, actual zh_cn/en_us client resources, 212 source descriptions and 29 catalog labels each, nested item name/numeric arguments and missing-key fallback. One client resource-resolution check; not two network clients.\n");
        System.out.println("INSPECTION_LANGUAGE_SMOKE PASS: 437 item descriptions and 29 armor labels x 2 languages and server-projected text");
    }
    private static void verifyDevelopmentText(Language language,String locale) throws IOException {
        for (var resource : java.util.Map.of(
                "tactical_tacz_development",DevelopmentText.class,
                "tactical_combat_development",dev.tacticalcombat.player.PlayerCombat.class).entrySet()) {
            try(var stream=resource.getValue().getResourceAsStream("/assets/"+resource.getKey()+"/lang/"+locale+".json")) {
                require(stream!=null,"development language resource present: "+resource.getKey());
                var expected=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
                for(var entry:expected.entrySet()) require(language.has(entry.getKey())
                        &&language.getOrDefault(entry.getKey()).equals(entry.getValue().getAsString()),"development translation loaded: "+entry.getKey());
            }
        }
        require(language.has("message.tactical_combat.protection_unavailable"),"production protection failure translated");
        var old=Component.literal("[开发] 鱼鹰固定内衬靶标");
        var migrated=DevelopmentTargets.legacyName(old,java.util.Set.of("tactical_development_target")).orElseThrow();
        require(keys(migrated).contains("message.tactical_tacz_development.target.armor"),"known legacy target migrates to nested components");
        require(DevelopmentTargets.legacyName(Component.literal("My target"),java.util.Set.of("tactical_development_target")).isEmpty(),"player name is preserved");
        require(DevelopmentTargets.legacyName(old,java.util.Set.of()).isEmpty(),"untagged matching name is preserved");
        require(DevelopmentTargets.legacyName(old.copy().withStyle(net.minecraft.ChatFormatting.GOLD),java.util.Set.of("tactical_development_target")).isEmpty(),"styled player name is preserved");
        require(DevelopmentTargets.legacyName(migrated,java.util.Set.of("tactical_development_target")).isEmpty(),"migration is idempotent");
        var gunName=DevelopmentText.text("gun.name",Component.translatable("item.tarkov_content.container_60a3c68c37ea821725773ef5"));
        require(keys(gunName).size()==2,"gun name keeps nested translated item name");
        require(!gunName.getString().contains("item.tarkov_content."),"nested gun label resolves in client language");
    }
    private static JsonObject resource(String path) throws IOException {
        try (var stream = (path.contains("/items/ammo_")?dev.tarkovcontent.ammunition.AmmunitionCatalog.class:dev.tarkovcontent.TarkovContent.class).getResourceAsStream(path)) {
            if (stream == null) throw new IOException("Missing resource " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    private static List<Component> texts(List<InspectionSection> sections) {
        var result = new ArrayList<Component>();
        for (var section : sections) {
            result.add(section.titleText());
            for (var row : section.rows()) {
                result.add(row.titleText()); result.add(row.descriptionText());
                for (var field : row.fields()) { result.add(field.labelText()); result.add(field.textText()); result.add(field.unitText()); }
                for (var action : row.actions()) { result.add(action.labelText()); result.add(action.reasonText()); }
                row.card().ifPresent(card -> { result.add(card.badgeText()); result.add(card.captionText()); });
            }
        }
        return result;
    }
    static Set<String> keys(Component text) {
        var keys = new HashSet<String>();
        if (text.getContents() instanceof TranslatableContents translation) {
            keys.add(translation.getKey());
            for (var argument : translation.getArgs()) if (argument instanceof Component child) keys.addAll(keys(child));
        }
        for (var sibling : text.getSiblings()) keys.addAll(keys(sibling));
        return keys;
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
