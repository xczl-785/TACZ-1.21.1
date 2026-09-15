package dev.tacticaltacz.refit;

import com.tacz.guns.api.RefitInventoryExtension.Choice;
import java.util.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.server.level.ServerPlayer;

public final class RefitProtocol {
    public static final UUID EMPTY = new UUID(0,0);
    public record Request(UUID requestId, UUID token, int action, String choiceId, int attachmentType) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(ResourceLocation.parse("tactical_tacz_adapter:refit_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Request> CODEC = StreamCodec.of((b,v)->{
            b.writeUUID(v.requestId);b.writeUUID(v.token);b.writeVarInt(v.action);b.writeUtf(v.choiceId,64);b.writeVarInt(v.attachmentType);
        },b->new Request(b.readUUID(),b.readUUID(),b.readVarInt(),b.readUtf(64),b.readVarInt()));
        @Override public Type<Request> type(){return TYPE;}
    }
    public record View(UUID requestId, UUID token, ItemStack held, List<Choice> choices, String result) implements CustomPacketPayload {
        public View { held=held.copy(); choices=List.copyOf(choices); }
        @Override public ItemStack held(){return held.copy();}
        public static final Type<View> TYPE = new Type<>(ResourceLocation.parse("tactical_tacz_adapter:refit_view"));
        public static final StreamCodec<RegistryFriendlyByteBuf,View> CODEC = StreamCodec.of((b,v)->{
            b.writeUUID(v.requestId);b.writeUUID(v.token);ItemStack.OPTIONAL_STREAM_CODEC.encode(b,v.held);
            b.writeVarInt(v.choices.size());for(var c:v.choices){b.writeUtf(c.id(),64);ItemStack.STREAM_CODEC.encode(b,c.stack());}b.writeUtf(v.result,64);
        },b->{var request=b.readUUID();var token=b.readUUID();var held=ItemStack.OPTIONAL_STREAM_CODEC.decode(b);int n=b.readVarInt();
            if(n<0||n>4096)throw new IllegalArgumentException("Invalid refit catalog size");var choices=new ArrayList<Choice>();
            for(int i=0;i<n;i++)choices.add(new Choice(b.readUtf(64),ItemStack.STREAM_CODEC.decode(b)));
            return new View(request,token,held,choices,b.readUtf(64));});
        @Override public Type<View> type(){return TYPE;}
    }
    public static void register(RegisterPayloadHandlersEvent event){
        var registrar=event.registrar("1");
        registrar.playToServer(Request.TYPE,Request.CODEC,(request,context)->context.enqueueWork(()->{
            var player=(ServerPlayer)context.player();PacketDistributor.sendToPlayer(player,RefitBridge.handle(player,request));
        }));
        registrar.playToClient(View.TYPE,View.CODEC,(view,context)->context.enqueueWork(()->RefitClient.receive(view)));
    }
}
