package dev.tacticaltacz.refit;

import com.tacz.guns.api.RefitInventoryExtension;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.*;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import dev.tacticalinventory.api.TacticalHeldExchange;
import dev.tacticaltacz.*;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Optional gun-specific adapter: the inventory module owns every physical commit. */
public final class RefitBridge {
    private record Session(UUID token,TacticalHeldExchange.View view,long expires) {}
    private static final Map<ServerPlayer,Session> SESSIONS=new WeakHashMap<>();
    private RefitBridge(){}
    public static void register(){
        RefitInventoryExtension.register(new RefitInventoryExtension.Handler(){
            public boolean active(Player player){return true;}
            public void opened(Player player){RefitClient.open();}
            public List<RefitInventoryExtension.Choice> choices(Player player){return RefitClient.choices();}
            public void install(Player player,String id){RefitClient.install(id);}
            public void unload(Player player,AttachmentType type){RefitClient.unload(type);}
        });
    }
    private static boolean ready(ServerPlayer player){
        if(dev.tacticaltacz.assembled.AssembledWeapons.isGun(player.getMainHandItem())||!GunAdoption.contains(player.getMainHandItem()))return false;
        var gun=IGun.getIGunOrNull(player.getMainHandItem());var operator=IGunOperator.fromLivingEntity(player);
        return gun!=null&&!gun.hasAttachmentLock(player.getMainHandItem())&&!operator.getSynIsBolting()
                &&operator.getSynReloadState().getCountDown()<0&&operator.getSynShootCoolDown()<=0&&operator.getSynDrawCoolDown()<=0;
    }
    public static RefitProtocol.View handle(ServerPlayer player,RefitProtocol.Request request){
        var assembled=dev.tacticaltacz.assembled.AssembledWeapons.from(player.getMainHandItem());
        if(assembled!=null&&assembled.nativeRig)return dev.tacticaltacz.assembled.AssemblyGunWorkbench.handleRefit(player,request);
        String result="";
        if(request.action()!=0){
            var session=SESSIONS.get(player);
            if(session==null||!session.token.equals(request.token())||player.level().getGameTime()>session.expires)result="stale";
            else {
                SESSIONS.remove(player); // A displayed proposal can be submitted only once.
                boolean accepted=false;
                if(ready(player)){
                    Optional<UUID> source=Optional.empty();
                    if(request.action()==1)source=session.view.sources().stream().filter(s->s.id().toString().equals(request.choiceId())).map(TacticalHeldExchange.Source::id).findFirst();
                    if((request.action()==1&&source.isPresent())||(request.action()==2&&request.attachmentType()>=0&&request.attachmentType()<AttachmentType.values().length)){
                        accepted=TacticalHeldExchange.exchange(player,session.view,source,(held,payment)->plan(player,held,payment,request));
                    }
                }
                if(accepted){
                    AttachmentPropertyManager.postChangeEvent(player,player.getMainHandItem());
                    result=request.action()==1?"installed":"unloaded";
                }else result="rejected";
            }
        }
        // Every response carries a fresh server-owned catalog, including after a rejected action.
        var view=ready(player)?TacticalHeldExchange.inspect(player,s->IAttachment.getIAttachmentOrNull(s)!=null):Optional.<TacticalHeldExchange.View>empty();
        if(view.isEmpty()||view.get().sources().size()>4096){SESSIONS.remove(player);return new RefitProtocol.View(request.requestId(),RefitProtocol.EMPTY,player.getMainHandItem(),List.of(),result.isEmpty()?"unavailable":result);}
        var token=UUID.randomUUID();SESSIONS.put(player,new Session(token,view.get(),player.level().getGameTime()+1200));
        var choices=view.get().sources().stream().map(s->new RefitInventoryExtension.Choice(s.id().toString(),s.stack())).toList();
        return new RefitProtocol.View(request.requestId(),token,view.get().held(),choices,result);
    }
    private static Optional<TacticalHeldExchange.Change> plan(ServerPlayer player,ItemStack held,ItemStack payment,RefitProtocol.Request request){
        var gun=IGun.getIGunOrNull(held);
        if(gun==null||gun.hasAttachmentLock(held)||!GunAdoption.contains(held))return Optional.empty();
        AttachmentType type;
        if(request.action()==1){var part=IAttachment.getIAttachmentOrNull(payment);if(part==null||!gun.allowAttachment(held,payment))return Optional.empty();type=part.getType(payment);}
        else type=AttachmentType.values()[request.attachmentType()];
        if(type==AttachmentType.NONE)return Optional.empty();
        var old=gun.getAttachment(player.registryAccess(),held,type);
        if(request.action()==2&&old.isEmpty())return Optional.empty();
        var refunds=new ArrayList<ItemStack>();if(!old.isEmpty())refunds.add(old);
        if(request.action()==1)gun.installAttachment(player.registryAccess(),held,payment);
        else gun.unloadAttachment(player.registryAccess(),held,type);
        var installed=gun.getAttachment(player.registryAccess(),held,type);
        if(request.action()==1 ? !ItemStack.matches(installed,payment) : !installed.isEmpty())return Optional.empty();
        if(type==AttachmentType.EXTENDED_MAG){
            int count=gun.getCurrentAmmoCount(held);
            if(count>0){var ammo=AmmoBridge.ammunition(held);if(ammo==null)return Optional.empty();refunds.add(new ItemStack(ammo,count));gun.setCurrentAmmoCount(held,0);}
            // Preserve the chamber and selected variant, as native TaCZ dropAllAmmo does.
        }
        return Optional.of(new TacticalHeldExchange.Change(held,refunds));
    }
}
