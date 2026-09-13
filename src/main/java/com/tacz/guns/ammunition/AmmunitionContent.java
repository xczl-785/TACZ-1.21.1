package com.tacz.guns.ammunition;

import com.google.gson.Gson;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Approved ammunition catalog. Raw source fields remain in the packaged JSON for traceability. */
public final class AmmunitionContent {
    public record Entry(String id, String sourceId, String caliber, float fleshDamage,
                        float penetrationPower, float armorDamage, int stackMaxSize, float initialSpeed, float recoilModifier, int projectileCount) {
        public TarkovAmmoItem.Definition definition() {
            return new TarkovAmmoItem.Definition(id, caliber, fleshDamage, penetrationPower, armorDamage);
        }
    }
    private AmmunitionContent() {}
    public static List<Entry> load() {
        try (var in=AmmunitionContent.class.getResourceAsStream("/data/tarkov_content/catalog/ammunition.json")) {
            if(in==null) throw new IllegalStateException("Missing ammunition catalog");
            var entries=new Gson().fromJson(new InputStreamReader(in,StandardCharsets.UTF_8),Entry[].class);
            var ids=new HashSet<String>();
            for(var e:entries) {
                if(!e.id().equals("tarkov_content:ammo_"+e.sourceId()) || !ids.add(e.id())
                        || e.projectileCount()<1 || e.caliber()==null || e.caliber().isBlank() || e.stackMaxSize()<1 || e.stackMaxSize()>99)
                    throw new IllegalArgumentException("Invalid ammunition definition: "+e.id());
                for(float value:new float[]{e.fleshDamage(),e.penetrationPower(),e.armorDamage(),e.initialSpeed()})
                    if(!Float.isFinite(value)||value<0)throw new IllegalArgumentException("Invalid ammunition parameter");
                if(!Float.isFinite(e.recoilModifier()))throw new IllegalArgumentException("Invalid recoil modifier");
            }
            return List.of(entries);
        } catch(IOException e) { throw new UncheckedIOException(e); }
    }
}
