package dev.tacticaltacz.verification;
import net.minecraft.network.chat.Component;
/** Development-only messages retain components until the receiving client renders them. */
final class DevelopmentText {
    private DevelopmentText() {}
    static Component text(String key, Object... arguments) {
        return Component.translatable("message.tactical_tacz_development." + key, arguments);
    }
    static Component yesNo(boolean value) { return text(value ? "yes" : "no"); }
}
