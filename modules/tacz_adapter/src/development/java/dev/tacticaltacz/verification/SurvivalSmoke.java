package dev.tacticaltacz.verification;
import dev.tacticalcharacter.resource.*;
import dev.tacticalcharacter.player.*;
import dev.tacticalcharacter.core.*;
import dev.tacticalcharacter.status.*;
import dev.tacticalcharacter.medical.*;
import dev.tacticalinventory.api.CharacterLifecycleEvent;
import dev.itemfoundation.api.behavior.UseInvocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.alchemy.*;
final class SurvivalSmoke {
 static void verify(ServerPlayer p){
 var oldBody=CharacterBodyService.state(p);var oldResources=PlayerResources.state(p);var oldStatuses=p.getData(CharacterStatuses.STATE);float hp=p.getHealth();
 try{
  require(p.getFoodData() instanceof CharacterFoodData,"nutrition mixin loaded");
  p.setData(PlayerResources.STATE,CharacterResources.full(100,100).nutrition(-80,-80));
  p.getFoodData().eat(4,.3f);require(PlayerResources.state(p).energy().current()==40,"food restores energy only");
  require(PlayerResources.state(p).hydration().current()==20,"food does not invent hydration");
  p.getFoodData().setFoodLevel(0);p.getFoodData().addExhaustion(40);p.getFoodData().tick(p);
  require(p.getFoodData().getFoodLevel()==20&&p.getHealth()==hp,"vanilla hunger neutral and no healing/damage");
  var bottle=new ItemStack(Items.POTION);bottle.set(DataComponents.POTION_CONTENTS,new PotionContents(Potions.WATER));
  var water=new WaterUseBehavior();var use=UseInvocation.begin(p,water,bottle,java.util.Optional.empty(),0);
  var interrupted=use.settle(p,use.initialized(),10,false);require(interrupted.is(Items.POTION)&&PlayerResources.state(p).hydration().current()==20,"interrupted water keeps item and resource");
  use=UseInvocation.begin(p,water,bottle,java.util.Optional.empty(),0);var remainder=use.finish(p,use.initialized(),use.duration());
  require(remainder.is(Items.GLASS_BOTTLE)&&PlayerResources.state(p).hydration().current()==60,"water completes with bottle remainder");
  WaterIntakeSmoke.verify(p,bottle,remainder);
  use.finish(p,remainder,use.duration()+1);require(PlayerResources.state(p).hydration().current()==60,"water completion exactly once");
  var body=BodyHealth.full().damage(BodyPart.LEFT_ARM,60).surgery(BodyPart.LEFT_ARM,.6f);
  float projected=body.total()/440*p.getMaxHealth();p.setData(PlayerBody.STATE,new PlayerBody(body,projected,p.getMaxHealth(),1));p.setHealth(projected);
  p.setData(CharacterStatuses.STATE,MedicalStatuses.add(StatusLedger.EMPTY,MedicalStatuses.FRACTURE,BodyPart.LEFT_ARM,-1,"smoke"));
  var saved=p.saveWithoutId(new net.minecraft.nbt.CompoundTag());
  var copy=new ServerPlayer(p.server,p.serverLevel(),new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"SurvivalReload"),net.minecraft.server.level.ClientInformation.createDefault());
  copy.load(saved);require(Math.abs(CharacterBodyService.state(copy).health().maximum(BodyPart.LEFT_ARM)-36)<.001,"save preserves surgery cap");
  require(copy.getData(CharacterStatuses.STATE).equals(p.getData(CharacterStatuses.STATE)),"save preserves injuries");
  require(PlayerResources.state(copy).equals(PlayerResources.state(p)),"save preserves nutrition");copy.discard();
  CharacterLifecycle.transition(new CharacterLifecycleEvent(p,CharacterLifecycleEvent.Phase.EXTRACTED));require(CharacterBodyService.state(p).health().equals(body),"extraction retains injuries");
  CharacterLifecycle.transition(new CharacterLifecycleEvent(p,CharacterLifecycleEvent.Phase.DEPARTED));require(CharacterBodyService.state(p).health().equals(BodyHealth.full())&&p.getData(CharacterStatuses.STATE).instances().isEmpty()&&PlayerResources.state(p).energy().current()==100,"new round explicit full recovery");
  System.out.println("SURVIVAL_LIFECYCLE_SMOKE PASS: native hunger / food / water interruption and cost / save reload / extraction / new round");
 }finally{p.setData(PlayerBody.STATE,oldBody);p.setData(PlayerResources.STATE,oldResources);p.setData(CharacterStatuses.STATE,oldStatuses);p.setHealth(hp);}
 }
 private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
