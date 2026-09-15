package dev.tacticaltacz.verification;
import dev.tacticalcharacter.core.*;
import dev.tacticalcharacter.player.*;
import dev.tacticalcharacter.status.*;
import dev.tacticalcharacter.medical.*;
import dev.itemfoundation.api.behavior.UseInvocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.Optional;
/** Completion/interruption integration in disposable smoke only; not a substitute for UI acceptance. */
final class MedicalSmoke {
 static void verify(ServerPlayer p){
  var before=CharacterBodyService.state(p);var statuses=p.getData(CharacterStatuses.STATE);float hp=p.getHealth();
  try {
   MedicalService.target(p,(dev.tacticalcharacter.core.BodyPart)null);p.setData(CharacterStatuses.STATE,StatusLedger.EMPTY);
   set(p,BodyHealth.full().damage(BodyPart.LEFT_ARM,40));
   var item=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse("tarkov_content:medical_medkit"));
   var stack=new ItemStack(item,2);var behavior=new MedicalUseBehavior(ResourceLocation.parse("tarkov_content:medkit"));
   require(behavior.canUse(p,stack),"healing available");
   var interrupted=UseInvocation.begin(p,behavior,stack,Optional.empty(),0);
   var remainder=interrupted.settle(p,interrupted.initialized(),10,false);
   require(remainder.getCount()==2&&CharacterBodyService.state(p).health().health(BodyPart.LEFT_ARM)==20,"interruption costs nothing and heals nothing");
   var completed=UseInvocation.begin(p,behavior,stack,Optional.empty(),0);
   remainder=completed.finish(p,completed.initialized(),completed.duration());
   require(remainder.getCount()==1&&CharacterBodyService.state(p).health().health(BodyPart.LEFT_ARM)==60,"completion heals target and consumes once");
   require(completed.finish(p,remainder,completed.duration()+1).getCount()==1,"duplicate finish cannot consume twice");
   set(p,BodyHealth.full().damage(BodyPart.LEFT_ARM,40));
   var invalid=UseInvocation.begin(p,behavior,stack,Optional.empty(),0);
   set(p,BodyHealth.full());
   require(invalid.finish(p,invalid.initialized(),invalid.duration()).getCount()==2,"invalidated target costs nothing");
   set(p,BodyHealth.full().damage(BodyPart.LEFT_ARM,60));
   var surgery=new MedicalUseBehavior(ResourceLocation.parse("tarkov_content:surgical_kit"));
   var kit=new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse("tarkov_content:medical_surgical_kit")));
   var use=UseInvocation.begin(p,surgery,kit,Optional.empty(),0);
   require(use.finish(p,use.initialized(),use.duration()).isEmpty(),"surgery consumes its item");
   require(Math.abs(CharacterBodyService.state(p).health().maximum(BodyPart.LEFT_ARM)-36)<.001,"surgery reduces effective cap");
   System.out.println("MEDICAL_USE_SMOKE PASS: interruption / completion / duplicate / target revalidation / surgery");
  }finally{p.setData(PlayerBody.STATE,before);p.setData(CharacterStatuses.STATE,statuses);p.setHealth(hp);MedicalService.target(p,(dev.tacticalcharacter.core.BodyPart)null);}
 }
 private static void set(ServerPlayer p,BodyHealth body){float hp=body.total()/440*p.getMaxHealth();p.setData(PlayerBody.STATE,new PlayerBody(body,hp,p.getMaxHealth(),CharacterBodyService.state(p).revision()+1));p.setHealth(hp);}
 private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
