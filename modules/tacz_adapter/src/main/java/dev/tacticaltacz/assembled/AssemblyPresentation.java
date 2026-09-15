package dev.tacticaltacz.assembled;

import com.tacz.guns.client.model.bedrock.BedrockPart;
import dev.itemfoundation.api.assembly.AssemblyTrees;
import dev.weaponassembly.api.AssemblyEngine;
import dev.weaponmodels.*;
import static dev.weaponmodels.WeaponPresentation.*;
import net.minecraft.world.item.ItemStack;
import org.joml.*;
import java.lang.Math;
import java.util.*;

/** One resource model's local presentation state. Assembly state is never modified. */
public final class AssemblyPresentation {
    private final AssembledWeapon weapon;
    private final Map<String,ModelGeometry> models;
    private final WeaponPresentation markers;
    private Object previousAssembly;
    private UUID identity;
    private Result result=new Result(List.of(),Map.of());
    private String selected="";
    private final ShotSpring kick, pitch;
    private long lastTime;
    private Vec eye;
    private Quaternionf eyeRotation=new Quaternionf();
    public AssemblyPresentation(AssembledWeapon weapon,Map<String,ModelGeometry> models,WeaponPresentation markers){
        this.weapon=weapon;this.models=models;this.markers=markers;
        var r=weapon.handling.recoil();kick=new ShotSpring(r.springFrequency(),r.maxDisplacement());pitch=new ShotSpring(r.springFrequency(),r.maxDisplacement());
    }
    public Result resolve(ItemStack stack) {
        var id=AssembledWeapon.identity(stack);var state=AssemblyTrees.state(stack);
        if(!id.equals(identity)){identity=id;previousAssembly=null;selected="";eye=null;kick.reset();pitch.reset();lastTime=0;}
        if(state!=previousAssembly){
            var tree=weapon.projectEnabled(stack);var occurrences=WeaponPresentation.occurrences(tree,models);
            result=markers.resolve(occurrences);selected=result.select(selected).map(Aim::id).orElse("");previousAssembly=state;
        }
        return result;
    }
    public Optional<Aim> aim(ItemStack stack){return resolve(stack).select(selected);}
    public String cycle(ItemStack stack){var aims=resolve(stack).aims();if(aims.isEmpty())return "";int i=0;while(i<aims.size()&&!aims.get(i).id().equals(selected))i++;selected=aims.get((i+1)%aims.size()).id();return selected;}
    public void shot(ItemStack stack){resolve(stack);advance();var f=weapon.handling.factors(weapon,stack);var r=weapon.handling.recoil();kick.kick(f.pitch()*r.visualKick());pitch.kick(f.pitch()*r.visualPitch());}
    private double advance(){long now=System.nanoTime();double dt=lastTime==0?0:Math.max((now-lastTime)/1e9,0);lastTime=now;kick.advance(dt);pitch.advance(dt);return dt;}
    public void apply(ItemStack stack,BedrockPart sight,BedrockPart left,BedrockPart right,BedrockPart root,float aiming){
        var resolved=resolve(stack);double dt=advance();var active=resolved.select(selected);
        Vec target;Quaternionf rotation;
        if(active.isPresent()){
            var frame=active.get().eye();var p=frame.position();float s=weapon.meshScale;
            target=new Vec(p.x()*s,8+p.y()*s,-p.z()*s);
            // Art -> render is a 180 degree X rotation; use the same conversion for orientation.
            var c=new Quaternionf().rotationX((float)Math.PI);
            rotation=new Quaternionf(c).mul(frame.matrix().getUnnormalizedRotation(new Quaternionf())).mul(new Quaternionf(c).conjugate());
        }else{target=weapon.handling.fallbackView();rotation=new Quaternionf();}
        double alpha=1-Math.exp(-dt*20);
        if(eye==null){eye=target;eyeRotation.set(rotation);}else{eye=eye.add(target.subtract(eye).scale(alpha));eyeRotation.slerp(rotation,(float)alpha);}
        sight.setPos((float)eye.x(),24-(float)eye.y(),(float)eye.z());
        var angles=eyeRotation.getEulerAnglesZYX(new Vector3f());sight.xRot=angles.x;sight.yRot=angles.y;sight.zRot=angles.z;
        contact(left,resolved.contacts().get("left"),weapon.handling.leftHand());
        contact(right,resolved.contacts().get("right"),weapon.handling.rightHand());
    }
    public void applyShot(BedrockPart root,float aiming){
        double amount=1-aiming*(1-weapon.handling.recoil().adsMotion());
        // TaCZ's constraint cancels animation offsets in ADS; procedural motion is added after that pass.
        root.offsetZ+=(float)(kick.value()*amount/16);
        root.additionalQuaternion.mul(new Quaternionf().rotationX((float)Math.toRadians(-pitch.value()*amount)));
    }
    private void contact(BedrockPart hand,Vec contact,WeaponHandling.Hand authored){
        hand.visible=contact!=null;if(contact==null)return;
        // Native skin arms have their own shoulder pivot. Solve the translated hand frame so its palm lands on the marker.
        var p=authored.palm();float s=weapon.meshScale;
        var target=new Vec(contact.x()*s,-contact.y()*s,-contact.z()*s);
        var rotation=new Quaternionf().rotationZYX(hand.zRot,hand.yRot,hand.xRot).mul(hand.additionalQuaternion);
        var translation=HandPlacement.translation(target,new Vec(-p.x(),-p.y(),p.z()),new Vec(hand.xScale,hand.yScale,hand.zScale),rotation);
        hand.setPos((float)translation.x(),(float)translation.y(),(float)translation.z());
    }
}
