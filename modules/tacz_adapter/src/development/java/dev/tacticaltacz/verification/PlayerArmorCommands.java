package dev.tacticaltacz.verification;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.item.ModernKineticGunScriptAPI;
import dev.tarkovcontent.TarkovContent;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Explicit single-player developer checks; excluded from release jars. */
@EventBusSubscriber(modid="tacz")
// DEVELOPMENT_COMMANDS: source/development/commands.json; retire this registration method with the catalog entry.
public final class PlayerArmorCommands {
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("armortest").requires(s->s.hasPermission(2))
            .then(Commands.literal("assembly_kit").executes(c -> {
                var player = c.getSource().getPlayerOrException();
                var definitions = dev.itemfoundation.api.assembly.AssemblyDefinitions.all();
                var selected = new java.util.ArrayList<dev.itemfoundation.api.assembly.AssemblyDefinition>();
                definitions.stream().filter(d -> d.slots().stream().anyMatch(slot -> slot.toggleable()))
                        .sorted(java.util.Comparator.comparing(dev.itemfoundation.api.assembly.AssemblyDefinition::itemId)).findFirst().ifPresent(selected::add);
                definitions.stream().filter(d -> !d.slots().isEmpty() && d.slots().stream().noneMatch(slot -> slot.toggleable()))
                        .sorted(java.util.Comparator.comparing(dev.itemfoundation.api.assembly.AssemblyDefinition::itemId)).findFirst().ifPresent(selected::add);
                if (selected.isEmpty()) { c.getSource().sendFailure(DevelopmentText.text("assembly.unloaded")); return 0; }
                for (var definition : selected) {
                    var ids = new java.util.ArrayList<String>(); ids.add(definition.itemId());
                    for (var slot : definition.slots()) slot.compatibleItems().stream().sorted().findFirst().ifPresent(ids::add);
                    for (var id : ids) {
                        var stack = new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(id)));
                        if (!dev.tacticalinventory.api.TacticalContent.tryGrant(player, java.util.List.of(stack))) player.drop(stack, false);
                    }
                }
                c.getSource().sendSuccess(() -> DevelopmentText.text("assembly.kit"), false);
                return 1;
            }))
            .then(Commands.literal("catalog_kit").executes(c->{
                var p=c.getSource().getPlayerOrException();
                for(var stack:java.util.List.of(ArmorVerification.armor(),
                        new net.minecraft.world.item.ItemStack(TarkovContent.ARMORS.get("5648a7494bdc2d9d488b4583").get()),
                        new net.minecraft.world.item.ItemStack(TarkovContent.ARMORS.get("60bf74184a63fc79b60c57f6").get())))
                    if(!dev.tacticalinventory.api.TacticalContent.tryGrant(p,java.util.List.of(stack)))p.drop(stack,false);
                c.getSource().sendSuccess(()->DevelopmentText.text("armor.kit"),false);return 1;
            }))
            .then(Commands.literal("player_kit").executes(c->{
                var p=c.getSource().getPlayerOrException();var armor=ArmorVerification.armor();
                if(!dev.tacticalinventory.api.TacticalContent.tryGrant(p,java.util.List.of(armor)))p.drop(armor,false);
                c.getSource().sendSuccess(()->DevelopmentText.text("armor.player_kit"),false);return 1;
            }))
            .then(Commands.literal("shoot_me").then(Commands.argument("ammo",StringArgumentType.word())
                .suggests((c,b)->{b.suggest("ap");b.suggest("flesh");return b.buildFuture();})
                .executes(c->{
                    var name=StringArgumentType.getString(c,"ammo");
                    if(!name.equals("ap")&&!name.equals("flesh")){c.getSource().sendFailure(DevelopmentText.text("shoot.ammo"));return 0;}
                    var p=c.getSource().getPlayerOrException();
                    if(p.isCreative()||p.isSpectator()){c.getSource().sendFailure(DevelopmentText.text("shoot.survival"));return 0;}
                    var pose=dev.tacticalcombat.player.PlayerCombat.pose(p);
                    if(pose.isEmpty()){c.getSource().sendFailure(DevelopmentText.text("shoot.pose"));return 0;}
                    var target=dev.tacticalcombat.player.PlayerGeometry.world(new Vec3(0,pose.get().crouching()?7.2:4,0),p.position(),pose.get());
                    double angle=Math.toRadians(p.yBodyRot);var front=new Vec3(-Math.sin(angle),0,Math.cos(angle));
                    var shooter=new Zombie(p.serverLevel());
                    // Unspawned source owns one real projectile, without leaving an AI mob in the test world.
                    var origin=target.add(front.scale(3));shooter.setPos(origin.x,origin.y-shooter.getEyeHeight(),origin.z);shooter.setNoAi(true);
                    // TaCZ spawns at the midpoint of current and previous positions, even for this unticked source.
                    shooter.xOld=shooter.getX();shooter.yOld=shooter.getY();shooter.zOld=shooter.getZ();
                    var gun=AdapterVerification.gun(name.equals("ap")?dev.tacticaltacz.development.VerificationRounds.ap():dev.tacticaltacz.development.VerificationRounds.flesh(),p.serverLevel());
                    shooter.setItemSlot(EquipmentSlot.MAINHAND,gun);IGunOperator.fromLivingEntity(shooter).draw(shooter::getMainHandItem);
                    ((IGun)gun.getItem()).setBulletInBarrel(gun,true);
                    var api=new ModernKineticGunScriptAPI();api.setShooter(shooter);api.setItemStack(gun);api.setDataHolder(new ShooterDataHolder());
                    api.setPitchSupplier(()->0f);api.setYawSupplier(()->p.yBodyRot+180);api.shootOnce(true);
                    p.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,origin.x,origin.y,origin.z,6,.02,.02,.02,0);
                    c.getSource().sendSuccess(()->DevelopmentText.text("shoot.fired"),false);return 1;
                }))));
    }
}
