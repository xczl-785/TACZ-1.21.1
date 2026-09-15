package dev.tacticaltacz.verification;
import dev.tacticalcharacter.core.*;
import dev.tacticalcharacter.player.PlayerBody;


import com.google.gson.*;
import dev.tacticalcombat.api.*;
import dev.tacticalcombat.core.*;
import dev.tacticaltacz.TacticalTaczAdapter;
import dev.tarkovcontent.TarkovContent;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** First real-item combat flow is intentionally restricted to explicit development mannequins.
 * No player health, tactical inventory internals, normal mob or production listener is taken over. */
@EventBusSubscriber(modid="tacz")
// DEVELOPMENT_COMMANDS: source/development/commands.json; retire this registration method with the catalog entry.
public final class ArmorVerification {
    static final String TAG="tactical_armor_target", BODY="tactical_armor_body_v1";
    static final String OSPREY="60a3c68c37ea821725773ef5";
    private static Map<String,ArmorLayer> layers;
    static int committed;
    static Map<String,ArmorLayer> layers() {
        if(layers!=null)return layers;
        try(var stream=TarkovContent.class.getResourceAsStream("/data/tarkov_content/armor/osprey_defence.json")) {
            if(stream==null)throw new IllegalStateException("Missing Osprey profile");
            var root=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            var result=new LinkedHashMap<String,ArmorLayer>();
            root.getAsJsonObject("segments").entrySet().forEach(e->{var p=e.getValue().getAsJsonObject();
                result.put(e.getKey(),new ArmorLayer(p.get("maximum").getAsFloat(),p.get("resistance").getAsFloat(),
                        p.get("destructibility").getAsFloat(),p.get("bluntThroughput").getAsFloat()));});
            if(result.size()!=7)throw new IllegalStateException("Expected seven fixed liners");
            return layers=Map.copyOf(result);
        }catch(IOException e){throw new IllegalStateException(e);}
    }
    static ItemStack armor() {
        var stack=new ItemStack(TarkovContent.CONTAINERS.get(OSPREY).get());
        dev.itemfoundation.api.storage.ContainerComponents.ensureState(stack);
        var initial=new HashMap<String,Float>();layers().forEach((k,v)->initial.put(k,v.factoryMaximum()));
        stack.set(CombatComponents.ARMOR.get(),new ArmorDurability(initial));return stack;
    }
    static CompoundTag encode(BodyHealth body,int revision) {
        var tag=new CompoundTag();tag.putInt("revision",revision);
        for(var p:BodyPart.values())tag.putFloat(p.name(),body.health(p));return tag;
    }
    static BodyHealth body(LivingEntity entity) {
        var tag=entity.getPersistentData().getCompound(BODY);var map=new EnumMap<BodyPart,Float>(BodyPart.class);
        for(var p:BodyPart.values())map.put(p,tag.getFloat(p.name()));return new BodyHealth(map);
    }
    static Husk target(ServerLevel level,Vec3 position,boolean armored) {
        var target=DevelopmentTargets.create(level,position,DevelopmentText.text(armored?"target.armor":"target.bare"),440,true);
        target.addTag(TAG);
        target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        target.getPersistentData().put(BODY,encode(BodyHealth.full(),0));
        if(armored)target.setItemSlot(EquipmentSlot.CHEST,armor());
        return target;
    }
    @SubscribeEvent public static void alterBeforeCommit(com.tacz.guns.api.event.common.EntityHurtByGunEvent.Pre event) {
        var t=event.getHurtEntity();
        if(t.getPersistentData().getBoolean("armor_swap_before_commit")&&t instanceof LivingEntity living) {
            t.getPersistentData().remove("armor_swap_before_commit");living.setItemSlot(EquipmentSlot.CHEST,ItemStack.EMPTY);
        }
    }
    @SubscribeEvent public static void trace(BulletTraceEvent event) {
        if(!(event.target instanceof Husk target)||!target.getTags().contains(TAG))return;
        var hit=ArmorTargetGeometry.trace(ArmorTargetGeometry.local(event.start,target.position(),target.getYRot(),target.getBbHeight()),
                ArmorTargetGeometry.local(event.end,target.position(),target.getYRot(),target.getBbHeight()),target.tickCount);
        event.resolve(hit.map(h->ArmorTargetGeometry.world(h.point(),target.position(),target.getYRot())));
    }
    @SubscribeEvent public static void quote(BulletImpactEvent event) {
        if(!(event.impact().target() instanceof LivingEntity target)||!target.getTags().contains(TAG))return;
        var impact=event.impact();var before=target.getPersistentData().getCompound(BODY).copy();
        var health=body(target);var worn=target.getItemBySlot(EquipmentSlot.CHEST);var wornSnapshot=worn.copy();
        var hit=ArmorTargetGeometry.trace(ArmorTargetGeometry.local(impact.segmentStart(),target.position(),target.getYRot(),target.getBbHeight()),
                ArmorTargetGeometry.local(impact.segmentEnd(),target.position(),target.getYRot(),target.getBbHeight()),target.tickCount);
        // The broad TaCZ box may include empty mannequin space. A miss suppresses native damage.
        if(hit.isEmpty()||health.dead()) {event.resolve(new DamageCommit(0,source->true));return;}
        var region=hit.get();float damage=impact.ammunition().fleshDamage();var updated=worn.copy();
        String segment=region.segment();boolean protectedHit=segment!=null&&worn.is(TarkovContent.CONTAINERS.get(OSPREY).get());
        SingleLayerArmor.Result result=null;
        if(protectedHit) {
            var layer=layers().get(segment);
            var old=worn.getOrDefault(CombatComponents.ARMOR.get(),new ArmorDurability(Map.of()));
            float durability=old.segments().getOrDefault(segment,layer.factoryMaximum());
            float roll=new Random(impact.projectile().getUUID().getMostSignificantBits() ^ target.getUUID().getLeastSignificantBits()).nextFloat();
            // Test harness can force a branch; no such hook is in the release Jar.
            if(target.getPersistentData().contains("armor_test_roll"))roll=target.getPersistentData().getFloat("armor_test_roll");
            result=SingleLayerArmor.resolve(impact.ammunition(),layer,durability,roll);damage=result.bodyDamage();
            var states=new HashMap<>(old.segments());states.put(segment,result.remainingDurability());
            updated.set(CombatComponents.ARMOR.get(),new ArmorDurability(states));
        }
        var after=health.damage(region.part(),damage);var afterTag=encode(after,before.getInt("revision")+1);
        var armorAfter=updated;var armorResult=result;float appliedDamage=damage;
        // Test mannequin projects remaining seven-part total to a 440 max MC bar; critical death overrides the total.
        float feedback=after.dead()?target.getHealth():health.total()-after.total();
        event.resolve(new DamageCommit(feedback,source->{
            if(!target.isAlive()||!target.getPersistentData().getCompound(BODY).equals(before)
                    ||target.getItemBySlot(EquipmentSlot.CHEST)!=worn||!ItemStack.matches(worn,wornSnapshot))return false;
            if(protectedHit)target.setItemSlot(EquipmentSlot.CHEST,armorAfter);
            target.getPersistentData().put(BODY,afterTag);
            target.setHealth(after.dead()?0:after.total());
            committed++;
            var marker=ArmorTargetGeometry.world(region.point(),target.position(),target.getYRot());
            if(target.level() instanceof ServerLevel level)level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                    marker.x,marker.y,marker.z,8,.015,.015,.015,0);
            if(after.dead())target.die(source);
            if(source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)
                player.sendSystemMessage(DevelopmentText.text("armor.hit",label(region.part()),protectedHit?Component.literal(segment):DevelopmentText.text("bare"),
                        appliedDamage,after.total(),armorResult==null?Component.empty():DevelopmentText.text("armor.wear",
                        DevelopmentText.yesNo(armorResult.penetrated()),armorResult.remainingDurability())));
            return true;
        }));
    }
    private static Component label(BodyPart part) {
        return DevelopmentText.text("part."+part.name().toLowerCase(java.util.Locale.ROOT));
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("armortest").requires(s->s.hasPermission(2))
            .then(Commands.literal("target").executes(c->spawn(c.getSource().getPlayerOrException(),true)))
            .then(Commands.literal("bare").executes(c->spawn(c.getSource().getPlayerOrException(),false)))
            .then(Commands.literal("status").then(Commands.argument("target",EntityArgument.entity()).executes(c->{
                var entity=EntityArgument.getEntity(c,"target");
                if(!(entity instanceof LivingEntity living)||!entity.getTags().contains(TAG))return 0;
                c.getSource().sendSuccess(()->Component.literal(body(living).values()+" / "+living.getItemBySlot(EquipmentSlot.CHEST)
                    .getOrDefault(CombatComponents.ARMOR.get(),new ArmorDurability(Map.of())).segments()),false);return 1;
            }))));
    }
    private static int spawn(net.minecraft.server.level.ServerPlayer p,boolean armored) {
        var pos=p.position().add(p.getLookAngle().multiply(6,0,6));var target=target(p.serverLevel(),pos,armored);
        target.setYRot(p.getYRot()+180);target.yBodyRot=target.getYRot();target.yHeadRot=target.getYRot();
        p.serverLevel().addFreshEntity(target);return 1;
    }
}
