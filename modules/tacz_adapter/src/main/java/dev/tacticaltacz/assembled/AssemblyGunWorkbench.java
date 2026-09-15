package dev.tacticaltacz.assembled;
import com.tacz.guns.api.RefitInventoryExtension.Choice;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import dev.tacticalinventory.api.TacticalHeldExchange;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;

/** Only server-issued snapshots authorize mutation; replay and changed inventory fail closed. */
public final class AssemblyGunWorkbench {
    private record Session(UUID token,TacticalHeldExchange.View view,long expires){}
    private static final Map<ServerPlayer,Session> SESSIONS=new WeakHashMap<>();
    public static AssemblyGunProtocol.View handle(ServerPlayer player,AssemblyGunProtocol.Request request){
        String result="";
        if(request.action()!=0){
            var session=SESSIONS.remove(player);boolean ok=false;
            if(session!=null&&session.token.equals(request.token())&&player.level().getGameTime()<=session.expires&&AssemblyGunExchange.ready(player)){
                Optional<UUID> source=request.action()==1?session.view.sources().stream().filter(s->s.id().toString().equals(request.choiceId())).map(TacticalHeldExchange.Source::id).findFirst():Optional.empty();
                if((request.action()==1&&source.isPresent())||request.action()==2)
                    ok=TacticalHeldExchange.exchange(player,session.view,source,(held,payment)->AssemblyGunExchange.plan(held,payment,request.path()));
            }
            result=ok?"committed":"rejected";
            if(ok)AttachmentPropertyManager.postChangeEvent(player,player.getMainHandItem());
        }
        var weapon=AssembledWeapons.from(player.getMainHandItem());
        var view=AssemblyGunExchange.ready(player)?TacticalHeldExchange.inspect(player,weapon::isPart):Optional.<TacticalHeldExchange.View>empty();
        if(view.isEmpty()||view.get().sources().size()>512){SESSIONS.remove(player);return new AssemblyGunProtocol.View(request.requestId(),AssemblyGunProtocol.EMPTY,player.getMainHandItem(),List.of(),"unavailable");}
        // Malformed/unidentified items are never advertised as free replacements.
        var choices=view.get().sources().stream().filter(s->{try{weapon.project(s.stack());return true;}catch(IllegalArgumentException bad){return false;}}).map(s->new Choice(s.id().toString(),s.stack())).toList();
        var token=UUID.randomUUID();SESSIONS.put(player,new Session(token,view.get(),player.level().getGameTime()+1200));
        return new AssemblyGunProtocol.View(request.requestId(),token,view.get().held(),choices,result);
    }
    private AssemblyGunWorkbench(){}
}
