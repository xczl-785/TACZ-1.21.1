package dev.tacticaltacz.verification;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import com.tacz.guns.api.item.IGun;
import dev.tacticalinventory.api.TacticalContent;
import java.util.List;
@EventBusSubscriber(modid="tacz")
public final class BarrelFireCommands {
    // DEVELOPMENT_COMMANDS: source/development/commands.json
    @SubscribeEvent public static void commands(RegisterCommandsEvent event){
        event.getDispatcher().register(Commands.literal("assemblyfire").requires(s->s.hasPermission(2))
            .then(Commands.literal("kit").executes(c->{
                var p=c.getSource().getPlayerOrException();var gun=AdapterVerification.gun(dev.tacticaltacz.development.VerificationRounds.flesh(),p.serverLevel());
                gun=BarrelFireFixture.attach(gun,BarrelFireFixture.barrel());var api=IGun.getIGunOrNull(gun);api.setCurrentAmmoCount(gun,15);api.setBulletInBarrel(gun,true);
                gun.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,Component.literal("Glock · 枪管能力测试"));
                boolean ok=TacticalContent.tryGrant(p,List.of(gun));c.getSource().sendSuccess(()->Component.literal(ok?"测试枪已放入库存。持枪后用 /assemblyfire remove 或 install。":"库存空间不足，未发放。"),false);return ok?1:0;
            }))
            .then(Commands.literal("remove").executes(c->change(c.getSource().getPlayerOrException(),false)))
            .then(Commands.literal("install").executes(c->change(c.getSource().getPlayerOrException(),true)))
            .then(Commands.literal("status").executes(c->{var p=c.getSource().getPlayerOrException();var s=p.getMainHandItem();var g=IGun.getIGunOrNull(s);var r=dev.tacticaltacz.LegacyFirearmProfiles.firing(s,g==null?"":g.getGunId(s).toString());c.getSource().sendSuccess(()->Component.literal(r.toString()),false);return 1;})));
    }
    private static int change(net.minecraft.server.level.ServerPlayer p,boolean install){boolean ok=BarrelFireFixture.exchange(p,install);p.sendSystemMessage(Component.literal(ok?(install?"原枪管已装回，可以射击。":"枪管已返还库存，射击已被禁止。") : "操作未提交：请持测试枪并确认有零件/返还空间。"));return ok?1:0;}
}
