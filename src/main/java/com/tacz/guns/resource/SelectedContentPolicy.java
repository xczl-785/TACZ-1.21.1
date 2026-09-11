package com.tacz.guns.resource;

import java.util.Set;

/** Project content selection; definitions and shared rendering assets remain available. */
public final class SelectedContentPolicy {
    private SelectedContentPolicy() {}
    private static final Set<String> EXCLUDED_GUNS = Set.of(
            "kar98",
            "lonetrail",
            "m320",
            "m700",
            "minigun",
            "rpg7",
            "springfield1873",
            "taurus500",
            "taurus943");

    public static boolean excludesResource(String namespace, String path) {
        if (!"tacz".equals(namespace)) return false;
        // Include upstream additions: all native ammunition recipes are retired.
        if (path.startsWith("recipe/ammo/") && path.endsWith(".json")) return true;
        for (String gun : EXCLUDED_GUNS) {
            if (path.equals("index/guns/" + gun + ".json")
                    || path.equals("recipe/gun/" + gun + ".json")) return true;
        }
        return false;
    }
}
