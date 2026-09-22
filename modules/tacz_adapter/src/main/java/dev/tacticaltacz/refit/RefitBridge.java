package dev.tacticaltacz.refit;

import com.tacz.guns.api.RefitInventoryExtension;
import com.tacz.guns.api.RefitInventoryExtension.Choice;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.attachment.AttachmentType;
import dev.firearms.workbench.*;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Boundary of the legacy refit wire. It keeps only what belongs to TaCZ's own refit screen: which
 * provider serves the held gun, how an attachment type becomes the target descriptor, and how the
 * shared result is worded for that screen. Candidate collection, the transaction and the quote
 * lifetime all come from the public service.
 */
public final class RefitBridge {
    private static final NativeGunRefitProvider NATIVE = new NativeGunRefitProvider();
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

    public static RefitProtocol.View handle(ServerPlayer player,RefitProtocol.Request request){
        var held=player.getMainHandItem();
        var assembled=dev.tacticaltacz.assembled.AssembledWeapons.from(held);
        var provider=assembled!=null&&assembled.nativeRig?dev.tacticaltacz.assembled.AssemblyGunWorkbench.provider():NATIVE;
        var view=WorkbenchService.handle(player,provider,serviceRequest(player,provider,request));
        var choices=view.candidates().stream().filter(c->IAttachment.getIAttachmentOrNull(c.stack())!=null).map(c->new Choice(c.id(),c.stack())).toList();
        // The shared flow words an accepted exchange as "committed"; this screen distinguishes the two actions.
        var result=view.result().equals("committed")&&request.action()!=0?(request.action()==1?"installed":"unloaded"):view.result();
        return new RefitProtocol.View(view.requestId(),view.token(),view.held(),choices,result);
    }

    private static WorkbenchService.Request serviceRequest(ServerPlayer player,WorkbenchProvider provider,RefitProtocol.Request request){
        var types=AttachmentType.values();
        var type=request.attachmentType()>=0&&request.attachmentType()<types.length?types[request.attachmentType()]:AttachmentType.NONE;
        var payment=request.action()==1?payment(player,provider,request.choiceId()):ItemStack.EMPTY;
        if(request.action()==1){var attachment=IAttachment.getIAttachmentOrNull(payment);if(attachment!=null)type=attachment.getType(payment);}
        var target=provider instanceof NativeGunRefitProvider?NativeGunRefitProvider.target(type)
                :dev.tacticaltacz.assembled.NativeAttachmentProjection.path(player.getMainHandItem(),type,payment);
        return new WorkbenchService.Request(request.requestId(),request.token(),request.action(),request.choiceId(),target);
    }

    private static ItemStack payment(ServerPlayer player,WorkbenchProvider provider,String choiceId){
        return WorkbenchService.peek(player,provider)
                .flatMap(quote->quote.sources().stream().filter(source->source.id().toString().equals(choiceId)).findFirst())
                .map(WorkbenchInventoryHost.Source::stack).orElse(ItemStack.EMPTY);
    }
}
