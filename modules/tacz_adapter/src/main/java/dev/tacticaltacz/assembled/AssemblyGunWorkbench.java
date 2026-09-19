package dev.tacticaltacz.assembled;
import com.tacz.guns.api.RefitInventoryExtension.Choice;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import dev.firearms.workbench.*;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;

/** Only server-issued snapshots authorize mutation; replay and changed inventory fail closed. */
public final class AssemblyGunWorkbench {
    private static final WorkbenchProposalSessions<WorkbenchInventoryHost.Quote> SESSIONS=new WorkbenchProposalSessions<>();
    public static dev.tacticaltacz.refit.RefitProtocol.View handleRefit(ServerPlayer player,dev.tacticaltacz.refit.RefitProtocol.Request request){
        var quote=SESSIONS.peek(player.getUUID()).orElse(null);var payment=quote==null?net.minecraft.world.item.ItemStack.EMPTY:quote.sources().stream().filter(v->v.id().toString().equals(request.choiceId())).map(WorkbenchInventoryHost.Source::stack).findFirst().orElse(net.minecraft.world.item.ItemStack.EMPTY);
        var attachment=com.tacz.guns.api.item.IAttachment.getIAttachmentOrNull(payment);
        var types=com.tacz.guns.api.item.attachment.AttachmentType.values();
        var type=request.action()==1&&attachment!=null?attachment.getType(payment):request.attachmentType()>=0&&request.attachmentType()<types.length?types[request.attachmentType()]:com.tacz.guns.api.item.attachment.AttachmentType.NONE;
        var response=handle(player,new AssemblyGunProtocol.Request(request.requestId(),request.token(),request.action(),request.choiceId(),NativeAttachmentProjection.path(player.getMainHandItem(),type,payment)));
        var choices=response.choices().stream().filter(c->com.tacz.guns.api.item.IAttachment.getIAttachmentOrNull(c.stack())!=null).toList();
        return new dev.tacticaltacz.refit.RefitProtocol.View(response.requestId(),response.token(),response.held(),choices,response.result().equals("committed")?(request.action()==1?"installed":"unloaded"):response.result());
    }
    public static AssemblyGunProtocol.View handle(ServerPlayer player,AssemblyGunProtocol.Request request){
        String result="";
        var inventory=WorkbenchInventoryHosts.current().orElse(null);
        if(request.action()!=0){
            var quote=SESSIONS.take(player.getUUID(),request.token(),player.level().getGameTime()).orElse(null);boolean ok=false;
            if(quote!=null&&inventory!=null&&AssemblyGunExchange.ready(player)){
                Optional<UUID> source=request.action()==1?quote.sources().stream().filter(s->s.id().toString().equals(request.choiceId())).map(WorkbenchInventoryHost.Source::id).findFirst():Optional.empty();
                if((request.action()==1&&source.isPresent())||request.action()==2)
                    ok=inventory.exchange(player,quote,source,(held,payment)->AssemblyGunExchange.plan(held,payment.isEmpty()?payment:AssembledWeapons.from(held).proposalPart(payment,source.orElseThrow()),request.path()));
            }
            result=ok?"committed":"rejected";
            if(ok)AttachmentPropertyManager.postChangeEvent(player,player.getMainHandItem());
        }
        var weapon=AssembledWeapons.from(player.getMainHandItem());
        var view=AssemblyGunExchange.ready(player)&&inventory!=null?inventory.inspect(player,weapon::isPart):Optional.<WorkbenchInventoryHost.Quote>empty();
        if(view.isEmpty()||view.get().sources().size()>512){SESSIONS.remove(player.getUUID());return new AssemblyGunProtocol.View(request.requestId(),AssemblyGunProtocol.EMPTY,player.getMainHandItem(),List.of(),"unavailable");}
        // Malformed/unidentified items are never advertised as free replacements.
        var choices=view.get().sources().stream().filter(s->{try{weapon.project(weapon.proposalPart(s.stack(),s.id()));return true;}catch(IllegalArgumentException bad){return false;}}).map(s->new Choice(s.id().toString(),weapon.proposalPart(s.stack(),s.id()))).toList();
        var token=SESSIONS.issue(player.getUUID(),view.get(),player.level().getGameTime()).token();
        return new AssemblyGunProtocol.View(request.requestId(),token,view.get().held(),choices,result);
    }
    private AssemblyGunWorkbench(){}
}
