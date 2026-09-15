package dev.tacticaltacz.verification;
import dev.tacticalcharacter.core.*;
import dev.tacticalcharacter.player.PlayerBody;


import dev.tacticalcombat.api.*;
import dev.tacticalcombat.core.*;
import dev.tacticaltacz.TacticalTaczAdapter;
import java.util.*;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Explicit development fixture. Its test probabilities never enter production armor catalogs. */
@EventBusSubscriber(modid="tacz")
// DEVELOPMENT_COMMANDS: source/development/commands.json; retire this registration method with the catalog entry.
public final class RicochetTargetVerification {
    public static final String TAG="tactical_ricochet_demo_target";
    public static final String STATE="tactical_ricochet_demo_v1";
    public static final ArmorLayer LAYER=new ArmorLayer(1000,60,.5f,.2f);
    public static final RicochetParameters PARAMETERS=new RicochetParameters(1,1,30);
    private static final double FACE_Z=2*1.0625/16;
    private static final double PANEL_HALF_WIDTH=.24,PANEL_BOTTOM=1.08,PANEL_TOP=1.22;
    public record Hit(BodyPart part,Vec3 point,Vec3 normal,boolean protectedHit) {}
    private RicochetTargetVerification() {}

    public static Husk target(ServerLevel level,Vec3 position,float yaw) {
        var target=DevelopmentTargets.create(level,position,DevelopmentText.text("target.ricochet"),440,true);
        target.setYRot(yaw);target.yBodyRot=yaw;target.yHeadRot=yaw;
        target.yBodyRotO=yaw;target.yHeadRotO=yaw;target.setOldPosAndRot();
        target.addTag(TAG);target.setCustomNameVisible(true);
        target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        target.getPersistentData().put(STATE,encode(BodyHealth.full(),LAYER.factoryMaximum(),0));
        return target;
    }
    private static CompoundTag encode(BodyHealth health,float durability,int revision) {
        var tag=new CompoundTag();tag.putInt("revision",revision);tag.putFloat("durability",durability);
        tag.put("body",ArmorVerification.encode(health,revision));return tag;
    }
    public static BodyHealth body(Husk target) {
        var values=new EnumMap<BodyPart,Float>(BodyPart.class);
        var tag=target.getPersistentData().getCompound(STATE).getCompound("body");
        for(var part:BodyPart.values())values.put(part,tag.getFloat(part.name()));
        return new BodyHealth(values);
    }
    public static float durability(Husk target) {return target.getPersistentData().getCompound(STATE).getFloat("durability");}
    public static Optional<Hit> trace(Husk target,Vec3 start,Vec3 end) {
        float yaw=target.getYRot();
        var localStart=ArmorTargetGeometry.local(start,target.position(),yaw,target.getBbHeight());
        var localEnd=ArmorTargetGeometry.local(end,target.position(),yaw,target.getBbHeight());
        return ArmorTargetGeometry.trace(localStart,localEnd,target.tickCount).map(hit->{
            var point=hit.point();
            boolean panel=hit.part()==BodyPart.CHEST&&Math.abs(point.z-FACE_Z)<1e-5
                    &&Math.abs(point.x)<=PANEL_HALF_WIDTH&&point.y>=PANEL_BOTTOM&&point.y<=PANEL_TOP;
            // This normal is the actual front face of the same rotated visible torso box.
            var normal=panel?ArmorTargetGeometry.world(new Vec3(0,0,1),Vec3.ZERO,yaw):Vec3.ZERO;
            return new Hit(hit.part(),ArmorTargetGeometry.world(point,target.position(),yaw),normal,panel);
        });
    }
    @SubscribeEvent public static void trace(BulletTraceEvent event) {
        if(!(event.target instanceof Husk target)||!target.getTags().contains(TAG))return;
        event.resolve(trace(target,event.start,event.end).map(Hit::point));
    }
    @SubscribeEvent public static void quote(BulletImpactEvent event) {
        if(!(event.impact().target() instanceof Husk target)||!target.getTags().contains(TAG))return;
        var impact=event.impact();var hit=trace(target,impact.segmentStart(),impact.segmentEnd());
        var before=target.getPersistentData().getCompound(STATE).copy();var health=body(target);
        if(hit.isEmpty()||health.dead()) {event.resolve(new DamageCommit(0,source->true));return;}
        var region=hit.orElseThrow();float oldDurability=durability(target);
        var roll=new Random(impact.projectile().getUUID().getMostSignificantBits()^target.getUUID().getLeastSignificantBits()).nextFloat();
        var continuation=region.protectedHit()?RicochetFlight.quote(impact.ammunition(),PARAMETERS,oldDurability,
                region.point(),region.normal(),impact.projectile().getDeltaMovement(),impact.ricochetCount(),roll).orElse(null):null;
        var result=!region.protectedHit()?null:continuation!=null
                ?ArmorRicochet.resolveDeflected(impact.ammunition(),LAYER,oldDurability)
                :SingleLayerArmor.resolve(impact.ammunition(),LAYER,oldDurability,roll);
        float damage=result==null?impact.ammunition().fleshDamage():result.bodyDamage();
        float afterDurability=result==null?oldDurability:result.remainingDurability();
        var after=health.damage(region.part(),damage);
        var afterTag=encode(after,afterDurability,before.getInt("revision")+1);
        double cosine=region.protectedHit()?-impact.projectile().getDeltaMovement().normalize().dot(region.normal()):0;
        double angle=Math.toDegrees(Math.acos(Math.max(-1,Math.min(1,cosine))));
        float feedback=after.dead()?target.getHealth():health.total()-after.total();
        var position=target.position();float yaw=target.getYRot();
        event.resolve(new DamageCommit(feedback,source->{
            if(!target.isAlive()||!target.position().equals(position)||target.getYRot()!=yaw
                    ||!target.getPersistentData().getCompound(STATE).equals(before))return false;
            target.getPersistentData().put(STATE,afterTag);
            target.setHealth(after.dead()?0:after.total());
            if(after.dead())target.die(source);
            if(target.level() instanceof ServerLevel level)level.sendParticles(ParticleTypes.END_ROD,
                    region.point().x,region.point().y,region.point().z,6,.015,.015,.015,0);
            if(source.getEntity() instanceof ServerPlayer player)player.sendSystemMessage(DevelopmentText.text("ricochet.hit",
                    DevelopmentText.text(region.protectedHit()?"ricochet.face":"ricochet.bare"),
                    region.protectedHit()?Component.literal(String.format(Locale.ROOT,"%.1f°",angle)):DevelopmentText.text("na"),
                    DevelopmentText.yesNo(continuation!=null),String.format(Locale.ROOT,"%.2f",damage),
                    String.format(Locale.ROOT,"%.2f",after.total()),String.format(Locale.ROOT,"%.2f",afterDurability),
                    continuation!=null?continuation.ricochetCount():impact.ricochetCount()));
            return true;
        },continuation));
    }
    @SubscribeEvent public static void outline(EntityTickEvent.Post event) {
        if(!(event.getEntity() instanceof Husk target)||!target.getTags().contains(TAG)
                ||!(target.level() instanceof ServerLevel level)||target.tickCount%10!=0)return;
        // Particles follow the front surface of the visible rotated chest, not a separate invisible wall.
        for(int i=0;i<=8;i++) {
            double x=-PANEL_HALF_WIDTH+2*PANEL_HALF_WIDTH*i/8;
            for(double y:new double[]{PANEL_BOTTOM,PANEL_TOP})particle(level,target,new Vec3(x,y,FACE_Z+.001));
        }
        for(int i=1;i<6;i++)for(double x:new double[]{-PANEL_HALF_WIDTH,PANEL_HALF_WIDTH})
            particle(level,target,new Vec3(x,PANEL_BOTTOM+(PANEL_TOP-PANEL_BOTTOM)*i/6,FACE_Z+.001));
    }
    private static void particle(ServerLevel level,Husk target,Vec3 local) {
        var p=ArmorTargetGeometry.world(local,target.position(),target.getYRot());
        level.sendParticles(ParticleTypes.END_ROD,p.x,p.y,p.z,1,0,0,0,0);
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("armortest").requires(source->source.hasPermission(2))
                .then(Commands.literal("ricochet").executes(context->{
                    var player=context.getSource().getPlayerOrException();
                    var position=player.position().add(Vec3.directionFromRotation(0,player.getYRot()).scale(6));
                    var target=target(player.serverLevel(),position,player.getYRot()+225);
                    if(!player.serverLevel().addFreshEntity(target))return 0;
                    context.getSource().sendSuccess(()->DevelopmentText.text("ricochet.spawn"),false);
                    return 1;
                })));
    }
}
