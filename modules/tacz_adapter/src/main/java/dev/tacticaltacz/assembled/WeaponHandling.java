package dev.tacticaltacz.assembled;

import com.google.gson.*;
import dev.weaponmodels.RecoilResponse;
import dev.weaponmodels.WeaponPresentation.Vec;
import net.minecraft.world.item.ItemStack;

/** Authored calibration, independent of the render frame and ammo state. */
public record WeaponHandling(Vec idleView, Vec fallbackView, Hand leftHand, Hand rightHand, Recoil recoil) {
    public record Hand(Vec rotation, Vec scale, Vec palm) {}
    public record Recoil(double referenceVertical,double referenceHorizontal,double pitchScale,double yawScale,
                         double springFrequency,double maxDisplacement,double visualKick,double visualPitch,double adsMotion) {
        public Recoil {
            for(double v:new double[]{referenceVertical,referenceHorizontal,pitchScale,yawScale,springFrequency,maxDisplacement,visualKick,visualPitch,adsMotion})
                if(!Double.isFinite(v)||v<0)throw new IllegalArgumentException("Invalid recoil calibration");
            if(referenceVertical==0||referenceHorizontal==0||springFrequency==0||maxDisplacement==0||adsMotion>1)throw new IllegalArgumentException("Invalid recoil reference");
        }
    }
    public record Factors(float pitch,float yaw) { public static final Factors IDENTITY=new Factors(1,1); }
    public Factors factors(AssembledWeapon weapon,ItemStack stack) {
        var tree=weapon.projectEnabled(stack);
        var base=weapon.CATALOG.require(weapon.ROOT).weapon().orElseThrow();
        double fraction=fraction(weapon,tree);
        if(fraction < -1)throw new IllegalArgumentException("Negative assembled recoil");
        return new Factors(RecoilResponse.factor(base.recoilVertical(),fraction,recoil.referenceVertical,recoil.pitchScale),
                RecoilResponse.factor(base.recoilHorizontal(),fraction,recoil.referenceHorizontal,recoil.yawScale));
    }
    private static double fraction(AssembledWeapon weapon,dev.firearms.assembly.AssemblyNode node){return weapon.CATALOG.require(node.definitionId()).modifiers().recoilFraction()+node.children().values().stream().mapToDouble(child->fraction(weapon,child)).sum();}
    public static WeaponHandling load(String json) {
        var o=JsonParser.parseString(json).getAsJsonObject();
        if(o.get("schemaVersion").getAsInt()!=1)throw new IllegalArgumentException("Unsupported handling");
        var r=o.getAsJsonObject("recoil");
        return new WeaponHandling(vec(o.get("idleView")),vec(o.get("fallbackView")),hand(o.getAsJsonObject("leftHand")),hand(o.getAsJsonObject("rightHand")),
                new Recoil(n(r,"referenceVertical"),n(r,"referenceHorizontal"),n(r,"pitchScale"),n(r,"yawScale"),n(r,"springFrequency"),n(r,"maxDisplacement"),n(r,"visualKick"),n(r,"visualPitch"),n(r,"adsMotion")));
    }
    private static double n(JsonObject o,String key){return o.get(key).getAsDouble();}
    private static Hand hand(JsonObject o){var scale=vec(o.get("scale"));if(scale.x()<=0||scale.y()<=0||scale.z()<=0)throw new IllegalArgumentException("Invalid hand scale");return new Hand(vec(o.get("rotation")),scale,vec(o.get("palm")));}
    private static Vec vec(JsonElement e){var a=e.getAsJsonArray();if(a.size()!=3)throw new IllegalArgumentException("Expected vector");return new Vec(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble());}
}
