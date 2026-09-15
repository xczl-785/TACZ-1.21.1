package com.tacz.guns.ammunition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** TEMPORARY fixed-caliber refund policy, explicitly requested on 2026-09-15.
 * This does NOT recover the loaded variant. Replace with exact-variant refunds
 * when gun ammunition identity is integrated; never use this for loading or damage.
 */
public final class TemporaryAmmoRefundPolicy {
    private TemporaryAmmoRefundPolicy() {}
    private static final Map<String, String> SOURCE_IDS = Map.ofEntries(
            Map.entry("tacz:12g", "560d5e524bdc2d25448b4571"),
            Map.entry("tacz:338", "5fc275cf85fd526b824a571a"),
            Map.entry("tacz:357mag", "62330b3ed4dc74626d570b95"),
            Map.entry("tacz:45acp", "5e81f423763d9f754677bf2e"),
            Map.entry("tacz:50ae", "668fe62ac62660a5d8071446"),
            Map.entry("tacz:50bmg", "67dc255ee3028a8b120efc48"),
            Map.entry("tacz:556x45", "54527a984bdc2d4e668b4567"),
            Map.entry("tacz:57x28", "5cc80f38e4a949001152b560"),
            Map.entry("tacz:58x42", "6a07208057b2695f9d001e63"),
            Map.entry("tacz:762x39", "5656d7c34bdc2d9d198b4587"),
            Map.entry("tacz:308", "58dd3ad986f77403051cba8f"),
            Map.entry("tacz:9mm", "56d59d3ad2720bdb418b4577"));

    public static Optional<String> sourceId(String nativeCaliberId) {
        return Optional.ofNullable(SOURCE_IDS.get(nativeCaliberId));
    }

    public static List<Integer> split(int count, int stackLimit) {
        if (count < 0 || stackLimit < 1) throw new IllegalArgumentException("Invalid refund quantity/capacity");
        List<Integer> result = new ArrayList<>();
        for (int remaining = count; remaining > 0;) {
            int amount = Math.min(remaining, stackLimit);
            result.add(amount);
            remaining -= amount;
        }
        return List.copyOf(result);
    }
}
