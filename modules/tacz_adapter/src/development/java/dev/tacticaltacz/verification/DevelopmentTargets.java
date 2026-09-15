package dev.tacticaltacz.verification;

import dev.tacticaltacz.TacticalTaczAdapter;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobDespawnEvent;

/** Shared lifecycle for development mannequins, including previously saved targets. */
@EventBusSubscriber(modid="tacz")
final class DevelopmentTargets {
    private static final String TAG="tactical_development_target";
    private DevelopmentTargets() {}
    static Husk create(ServerLevel level,Vec3 position,Component name,float maximum,boolean floating) {
        var target=new Husk(EntityType.HUSK,level);
        target.setPos(position);target.setOldPosAndRot();
        target.setNoAi(true);target.setNoGravity(floating);target.setPersistenceRequired();
        target.addTag(TAG);target.setCustomName(DevelopmentText.text("target.prefix",name));
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(maximum);target.setHealth(maximum);
        return target;
    }
    /** Only exact, unstyled system names on tagged targets migrate; player custom names stay intact. */
    static java.util.Optional<Component> legacyName(Component name, java.util.Set<String> tags) {
        if(name==null || !name.getStyle().isEmpty() || !name.getSiblings().isEmpty()
                || !(name.getContents() instanceof net.minecraft.network.chat.contents.PlainTextContents)) return java.util.Optional.empty();
        if(!tags.contains(TAG)&&!tags.contains(ArmorVerification.TAG)&&!tags.contains("tactical_tacz_test_target")
                &&!tags.contains(RicochetTargetVerification.TAG))return java.util.Optional.empty();
        String value=name.getString();
        if(value.startsWith("[开发] "))value=value.substring(5);
        String key=switch(value) {
            case "弹药测试目标（不启用护甲）" -> "target.ammo";
            case "鱼鹰固定内衬靶标" -> "target.armor";
            case "七部位裸靶" -> "target.bare";
            case "跳弹演示靶（测试参数）" -> "target.ricochet";
            default -> null;
        };
        return key==null?java.util.Optional.empty():java.util.Optional.of(DevelopmentText.text("target.prefix",DevelopmentText.text(key)));
    }
    @SubscribeEvent public static void migrateName(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if(event.getLevel().isClientSide() || !(event.getEntity() instanceof Husk target))return;
        legacyName(target.getCustomName(),target.getTags()).ifPresent(target::setCustomName);
    }
    @SubscribeEvent public static void keepTarget(MobDespawnEvent event) {
        if(!(event.getEntity() instanceof Husk target))return;
        var tags=target.getTags();
        // Persistence alone does not bypass vanilla's peaceful-difficulty removal.
        if(tags.contains(TAG)||tags.contains(ArmorVerification.TAG)||tags.contains("tactical_tacz_test_target")
                ||tags.contains(RicochetTargetVerification.TAG))event.setResult(MobDespawnEvent.Result.DENY);
    }
}
