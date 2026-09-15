package dev.tacticaltacz.verification;
import dev.tacticalcharacter.core.*;
import dev.tacticalcharacter.player.PlayerBody;

import dev.tacticalcharacter.core.BodyPart;
import java.util.*;
import net.minecraft.world.phys.*;
/** Adult, stationary, non-aggressive Husk: HumanoidModel cubes + AnimationUtils idle arms.
 * Model pixels are transformed by the Husk renderer scale, not by the entity's unrelated AABB height. */
public final class ArmorTargetGeometry {
    private static final double S=1.0625/16, ORIGIN=1.501*1.0625;
    public record Hit(BodyPart part,String segment,Vec3 point) {}
    private record Box(BodyPart part,AABB bounds,String segment) {}
    private static final List<Box> BOXES=List.of(
        new Box(BodyPart.HEAD,modelBox(-4,-8,-4,4,0,4),null),
        new Box(BodyPart.CHEST,modelBox(-4,0,-2,4,8,2),"torso"),
        new Box(BodyPart.STOMACH,modelBox(-4,8,-2,4,12,2),"torso"),
        new Box(BodyPart.LEFT_LEG,modelBox(-.1,12,-2,3.9,24,2),null),
        new Box(BodyPart.RIGHT_LEG,modelBox(-3.9,12,-2,.1,24,2),null));
    private static AABB modelBox(double x1,double y1,double z1,double x2,double y2,double z2) {
        return new AABB(x1*S,ORIGIN-y2*S,-z2*S,x2*S,ORIGIN-y1*S,-z1*S);
    }
    public static Optional<Hit> trace(Vec3 start,Vec3 end) {return trace(start,end,0);}
    public static Optional<Hit> trace(Vec3 start,Vec3 end,float age) {
        Hit nearest=null;double distance=Double.POSITIVE_INFINITY;
        for(var b:BOXES) {
            var point=b.bounds.contains(start)?Optional.of(start):b.bounds.clip(start,end);
            if(point.isEmpty()||start.distanceToSqr(point.get())>=distance)continue;
            var p=point.get();String segment=b.segment;
            if("torso".equals(segment)) {
                segment=Math.abs(p.x)>=4*S-1e-6 ? (p.x>0?"Soft_armor_left":"soft_armor_right")
                        : p.z>0?"Soft_armor_front":"Soft_armor_back";
                // Test collar is a central strip at the top of the torso; HP assignment remains a test choice.
                if(p.y>ORIGIN-1.5*S&&Math.abs(p.x)<1.5*S)segment="Collar";
            }
            nearest=new Hit("Collar".equals(segment)?BodyPart.HEAD:b.part,segment,p);distance=start.distanceToSqr(p);
        }
        for(boolean left:new boolean[]{true,false}) {
            var a=armInverse(start,left,age);var b=armInverse(end,left,age);
            var bounds=left?new AABB(-1,-2,-2,3,10,2):new AABB(-3,-2,-2,1,10,2);
            var intersection=bounds.contains(a)?Optional.of(a):bounds.clip(a,b);
            if(intersection.isEmpty())continue;
            var pixel=intersection.get();var p=armPoint(pixel,left,age);
            if(start.distanceToSqr(p)>=distance)continue;
            nearest=new Hit(left?BodyPart.LEFT_ARM:BodyPart.RIGHT_ARM,pixel.y<=2?(left?"Shoulder_l":"Shoulder_r"):null,p);
            distance=start.distanceToSqr(p);
        }
        return Optional.ofNullable(nearest);
    }
    private static double ax(boolean left,float age) {return -Math.PI/2.25+(left?-1:1)*Math.sin(age*.067)*.05;}
    private static double az(boolean left,float age) {return (left?-1:1)*(Math.cos(age*.09)*.05+.05);}
    private static Vec3 rx(Vec3 p,double a) {return new Vec3(p.x,p.y*Math.cos(a)-p.z*Math.sin(a),p.y*Math.sin(a)+p.z*Math.cos(a));}
    private static Vec3 ry(Vec3 p,double a) {return new Vec3(p.x*Math.cos(a)+p.z*Math.sin(a),p.y,-p.x*Math.sin(a)+p.z*Math.cos(a));}
    private static Vec3 rz(Vec3 p,double a) {return new Vec3(p.x*Math.cos(a)-p.y*Math.sin(a),p.x*Math.sin(a)+p.y*Math.cos(a),p.z);}
    static Vec3 armPoint(Vec3 pixel,boolean left,float age) {
        var p=rz(ry(rx(pixel,ax(left,age)),left?.1:-.1),az(left,age)).add(left?5:-5,2,0);
        return new Vec3(p.x*S,ORIGIN-p.y*S,-p.z*S);
    }
    private static Vec3 armInverse(Vec3 point,boolean left,float age) {
        var p=new Vec3(point.x/S,(ORIGIN-point.y)/S,-point.z/S).subtract(left?5:-5,2,0);
        return rx(ry(rz(p,-az(left,age)),left?-.1:.1),-ax(left,age));
    }
    public static Vec3 local(Vec3 world,Vec3 position,float yaw,float ignoredHeight) {
        var p=world.subtract(position);double r=Math.toRadians(yaw),c=Math.cos(r),s=Math.sin(r);
        return new Vec3(c*p.x+s*p.z,p.y,-s*p.x+c*p.z);
    }
    public static Vec3 world(Vec3 local,Vec3 position,float yaw) {
        double r=Math.toRadians(yaw),c=Math.cos(r),s=Math.sin(r);
        return new Vec3(c*local.x-s*local.z,local.y,s*local.x+c*local.z).add(position);
    }
}
