package dev.tacticaltacz.verification;
import dev.tacticalcharacter.core.*;
import dev.tacticalcharacter.player.PlayerBody;

import dev.tacticaltacz.TacticalTaczAdapter;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import java.nio.file.*;
@EventBusSubscriber(modid="tacz", value = Dist.CLIENT)
public final class ClientLoadSmoke {
    @SubscribeEvent public static void screen(ScreenEvent.Init.Post event) {
        if (!Boolean.getBoolean("tacticaltacz.clientSmoke") || !(event.getScreen() instanceof TitleScreen)) return;
        try {
            if (!Path.of("").toAbsolutePath().normalize().endsWith("runs/tacz-adapter-client-smoke"))
                throw new IllegalStateException("Isolated client smoke directory required");
            // Force application of the client-only HUD mixin even without entering a world.
            Class.forName("com.tacz.guns.client.gui.overlay.GunHudOverlay");
            Class.forName("com.tacz.guns.client.gameplay.LocalPlayerShoot");
            verifyContentModels();
            verifyHuskArms();
            verifyPlayerGunArms();
            dev.tacticalinventory.verification.TacticalLocalizationClientSmoke.verify(Minecraft.getInstance());
            Files.writeString(Path.of("tacz-client-smoke.pass"), "TACZ_CLIENT_SMOKE PASS: title screen, HUD and client/server Husk arm transforms verified\n");
        } catch (Exception e) { throw new IllegalStateException(e); }
        InspectionClientSmoke.queue();
    }
    private static void verifyContentModels() {
        var mc = Minecraft.getInstance();
        var missing = net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation();
        var failures = new java.util.ArrayList<String>();
        int checked = 0;
        for (var item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
            if (!id.getNamespace().equals("tarkov_content")) continue;
            checked++;
            var model = mc.getItemRenderer().getModel(new net.minecraft.world.item.ItemStack(item), null, null, 0);
            var quads = new java.util.ArrayList<net.minecraft.client.renderer.block.model.BakedQuad>();
            quads.addAll(model.getQuads(null, null, net.minecraft.util.RandomSource.create(0)));
            for (var face : net.minecraft.core.Direction.values())
                quads.addAll(model.getQuads(null, face, net.minecraft.util.RandomSource.create(0)));
            if (model.getParticleIcon().contents().name().equals(missing) || quads.isEmpty()
                    || quads.stream().anyMatch(q -> q.getSprite().contents().name().equals(missing)))
                failures.add(id.toString());
        }
        if (checked == 0 || !failures.isEmpty())
            throw new IllegalStateException("Content baked model failures (" + failures.size() + "/" + checked + "): " + failures);
        System.out.println("CONTENT_MODEL_SMOKE PASS: " + checked + " registered items have textured baked quads for world rendering");
    }

    private static void verifyHuskArms() {
        var root=Minecraft.getInstance().getEntityModels().bakeLayer(net.minecraft.client.model.geom.ModelLayers.HUSK);
        var left=root.getChild("left_arm");var right=root.getChild("right_arm");
        for(float age:new float[]{0,20,137}) {
            net.minecraft.client.model.AnimationUtils.animateZombieArms(left,right,false,0,age);
            for(boolean isLeft:new boolean[]{true,false})for(float y:new float[]{0,8}) {
                var part=isLeft?left:right;var pose=new com.mojang.blaze3d.vertex.PoseStack();
                part.translateAndRotate(pose);
                float x=isLeft?1:-1;
                var v=new org.joml.Vector3f(x/16,y/16,0).mulPosition(pose.last().pose());
                var expected=new net.minecraft.world.phys.Vec3(v.x*1.0625,1.501*1.0625-v.y*1.0625,-v.z*1.0625);
                var actual=ArmorTargetGeometry.armPoint(new net.minecraft.world.phys.Vec3(x,y,0),isLeft,age);
                if(expected.distanceTo(actual)>.0001)throw new IllegalStateException("Husk model/armor shape mismatch: "+expected+" / "+actual);
            }
        }
        System.out.println("HUSK_ARM_MODEL_SMOKE PASS: baked client pivots and idle animation match server arm transforms");
    }

    private static void verifyPlayerGunArms() {
        var root=Minecraft.getInstance().getEntityModels().bakeLayer(net.minecraft.client.model.geom.ModelLayers.PLAYER);
        var left=root.getChild("left_arm");var right=root.getChild("right_arm");
        var head=root.getChild("head");var body=root.getChild("body");
        for(boolean crouch:new boolean[]{false,true})for(float aim:new float[]{0,1})for(float yaw:new float[]{0,90,180,270}) {
            head.xRot=.2f;head.yRot=.1f;left.y=right.y=crouch?5.2f:2;left.zRot=right.zRot=0;
            net.minecraft.client.model.AnimationUtils.bobArms(right,left,20);
            com.tacz.guns.api.client.other.ThirdPersonManager.getAnimation("default").animateGunAim(null,right,left,body,head,aim);
            var geometry=new dev.tacticalcombat.player.PlayerGeometry.Pose(crouch,yaw,(float)Math.toDegrees(.1),(float)Math.toDegrees(.2),0,0,20,true,aim,1);
            for(boolean isLeft:new boolean[]{true,false}) {
                var part=isLeft?left:right;var matrix=new com.mojang.blaze3d.vertex.PoseStack();part.translateAndRotate(matrix);
                var v=new org.joml.Vector3f((isLeft?1f:-1f)/16,8f/16,0).mulPosition(matrix.last().pose());
                var model=new net.minecraft.world.phys.Vec3(v.x*16,v.y*16,v.z*16);
                var point=dev.tacticalcombat.player.PlayerGeometry.world(model,net.minecraft.world.phys.Vec3.ZERO,geometry);
                var hit=dev.tacticalcombat.player.PlayerGeometry.trace(point,point.add(0,0,.001),net.minecraft.world.phys.Vec3.ZERO,geometry).orElseThrow();
                var expected=isLeft?dev.tacticalcharacter.core.BodyPart.LEFT_ARM:dev.tacticalcharacter.core.BodyPart.RIGHT_ARM;
                if(hit.part()!=expected||hit.region()!=null)throw new IllegalStateException("Player baked TaCZ forearm mismatch: "+hit);
            }
        }
        System.out.println("PLAYER_GUN_MODEL_SMOKE PASS: baked standard player forearms with real TaCZ aim pose, crouch and four yaws");
    }

}
