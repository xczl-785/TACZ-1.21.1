package dev.tacticaltacz.assembled;
import com.tacz.guns.api.RefitInventoryExtension.Choice;
import dev.firearms.workbench.*;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;

/** TaCZ's server half: registers its provider and translates the legacy refit wire at the edge. */
public final class AssemblyGunWorkbench {
    private static final AssemblyGunProvider PROVIDER=new AssemblyGunProvider();
    /** Claims the public assembly entry for TaCZ's assembled guns. Called once at startup. */
    public static void register(){WorkbenchProviders.register(PROVIDER);}
    public static WorkbenchProvider provider(){return PROVIDER;}
    public static dev.tacticaltacz.refit.RefitProtocol.View handleRefit(ServerPlayer player,dev.tacticaltacz.refit.RefitProtocol.Request request){
        var quote=WorkbenchService.peek(player).orElse(null);var payment=quote==null?net.minecraft.world.item.ItemStack.EMPTY:quote.sources().stream().filter(v->v.id().toString().equals(request.choiceId())).map(WorkbenchInventoryHost.Source::stack).findFirst().orElse(net.minecraft.world.item.ItemStack.EMPTY);
        var attachment=com.tacz.guns.api.item.IAttachment.getIAttachmentOrNull(payment);
        var types=com.tacz.guns.api.item.attachment.AttachmentType.values();
        var type=request.action()==1&&attachment!=null?attachment.getType(payment):request.attachmentType()>=0&&request.attachmentType()<types.length?types[request.attachmentType()]:com.tacz.guns.api.item.attachment.AttachmentType.NONE;
        var response=WorkbenchService.handle(player,new WorkbenchService.Request(request.requestId(),request.token(),request.action(),request.choiceId(),NativeAttachmentProjection.path(player.getMainHandItem(),type,payment)));
        var choices=response.candidates().stream().filter(c->com.tacz.guns.api.item.IAttachment.getIAttachmentOrNull(c.stack())!=null).map(c->new Choice(c.id(),c.stack())).toList();
        return new dev.tacticaltacz.refit.RefitProtocol.View(response.requestId(),response.token(),response.held(),choices,response.result().equals("committed")?(request.action()==1?"installed":"unloaded"):response.result());
    }
    private AssemblyGunWorkbench(){}
}
