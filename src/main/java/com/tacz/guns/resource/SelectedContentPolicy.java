package com.tacz.guns.resource;

import java.util.Set;

/** NewMod selection, modified 2026-09-13: 15 guns; retired IDs also blocked in external overrides. */
public final class SelectedContentPolicy {
    private SelectedContentPolicy() {}
    private static final Set<String> EXCLUDED_GUNS = Set.of(
            "ak47",
            "aug",
            "b93r",
            "cz75",
            "db_long",
            "db_short",
            "deagle",
            "deagle_golden",
            "fn_evolys",
            "fn_fal",
            "g36k",
            "hk416d",
            "hk_g3",
            "hk_mk23",
            "hk_mp5a5",
            "kar98",
            "lonetrail",
            "m1014",
            "m107",
            "m16a4",
            "m1911",
            "m249",
            "m320",
            "m95",
            "m9a4",
            "minigun",
            "p320",
            "qbz_95",
            "rhino357",
            "rpg7",
            "rpk",
            "spas_12",
            "spr15hb",
            "springfield1873",
            "taurus500",
            "taurus943",
            "timeless50",
            "type_81",
            "vector45");
    private static final Set<String> EXCLUDED_ATTACHMENTS = Set.of(
            "bayonet_6h3",
            "deagle_golden_long_barrel",
            "laser_peq6",
            "muzzle_brake_timeless50",
            "muzzle_silencer_vulture",
            "oem_stock_heavy",
            "oem_stock_light",
            "oem_stock_tactical",
            "scope_1873_6x",
            "scope_98k",
            "scope_aug_default",
            "sight_t1",
            "stock_heavy_spas_12",
            "stock_tactical_spas_12");

    public static boolean excludesResource(String namespace, String path) {
        if (!"tacz".equals(namespace)) return false;
        // Include upstream additions: all native ammunition recipes are retired.
        if (path.startsWith("recipe/ammo/") && path.endsWith(".json")) return true;
        for (String gun : EXCLUDED_GUNS) {
            if (path.equals("index/guns/" + gun + ".json")
                    || path.equals("recipe/gun/" + gun + ".json")) return true;
        }
        for (String attachment : EXCLUDED_ATTACHMENTS) {
            if (path.equals("index/attachments/" + attachment + ".json")
                    || path.equals("recipe/attachments/" + attachment + ".json")) return true;
        }
        return false;
    }
}
