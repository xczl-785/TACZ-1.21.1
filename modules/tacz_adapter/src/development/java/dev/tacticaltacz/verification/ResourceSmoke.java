package dev.tacticaltacz.verification;
import dev.tacticalcharacter.resource.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
/** Uses a real server player in the existing disposable adapter world. */
final class ResourceSmoke {
 static void verify(ServerPlayer player) {
  player.setData(PlayerResources.STATE,CharacterResources.full(ResourceConfig.bodyMax(),ResourceConfig.armMax()));
  java.util.function.Consumer<ResourceActionEvent> aiming=event->{if(event.player==player)event.aiming=true;};
  NeoForge.EVENT_BUS.addListener(aiming);
  try {
   PlayerResources.tick(new PlayerTickEvent.Post(player));
   var state=PlayerResources.state(player);
   require(state.arms().current()<state.arms().maximum(),"server action consumes arms");
   require(state.stamina().current()==state.stamina().maximum(),"stationary does not consume body");
   PlayerResources.tick(new PlayerTickEvent.Post(player));
   require(PlayerResources.state(player).equals(state),"same tick cannot double charge");
   var saved=player.saveWithoutId(new net.minecraft.nbt.CompoundTag());
   var restored=new ServerPlayer(player.server,player.serverLevel(),new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"ResourceRestore"),net.minecraft.server.level.ClientInformation.createDefault());
   restored.load(saved);
   require(PlayerResources.state(restored).equals(state),"actual attachment save and reload preserves resource and delay");
   restored.discard();
   var empty=state;
   for(int i=0;i<1000;i++)empty=empty.tick(true,false,new CharacterResources.Rates(100,0,0,0,20,.15f));
   player.setData(PlayerResources.STATE,empty);
   player.setSprinting(true);
   PlayerResources.pre(new PlayerTickEvent.Pre(player));
   require(!player.isSprinting(),"server rejects sprint while exhausted");
   System.out.println("CHARACTER_RESOURCE_SMOKE PASS: server action / stationary / idempotence / attachment reload / sprint denial");
  } finally { NeoForge.EVENT_BUS.unregister(aiming); }
 }
 private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
