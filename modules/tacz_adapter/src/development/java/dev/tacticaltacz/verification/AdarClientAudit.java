package dev.tacticaltacz.verification;
import dev.tacticaltacz.assembled.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.Screenshot;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.Difficulty;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.*;
import java.util.*;

/** Isolated integrated client: native held model and ADS. Never touches the daily world. */
@EventBusSubscriber(modid="tacz",value=Dist.CLIENT)
public final class AdarClientAudit {
    private static final AssembledWeapon WEAPON=AssembledWeapons.byId(net.minecraft.resources.ResourceLocation.parse("newmod_adar:adar"));
    private static boolean started,issued,finished;private static int ticks;private static volatile boolean equipped;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception{
        if(!Boolean.getBoolean("newmod.adarAudit")||finished)return;var mc=Minecraft.getInstance();
        if(!mc.gameDirectory.toPath().toAbsolutePath().normalize().endsWith("runs/adar-audit"))throw new IllegalStateException("Isolated ADAR directory required");
        if(mc.getOverlay()!=null)return;
        if(!started&&mc.screen instanceof TitleScreen){
            started=true;
            mc.createWorldOpenFlows().createFreshLevel("adar-"+System.currentTimeMillis(),new LevelSettings("ADAR engineering audit",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(123,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),new TitleScreen());return;
        }
        if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;
        if(!issued){issued=true;mc.getSingleplayerServer().execute(()->{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());if(p==null)throw new IllegalStateException("Missing integrated player");
            var gun=WEAPON.preset();var g=(com.tacz.guns.api.item.IGun)gun.getItem();
            var ammo=com.tacz.guns.ammunition.AmmunitionRegistry.AMMUNITION.values().stream().map(v->v.get()).filter(a->a.definition().caliber().equals("556x45")).findFirst().orElseThrow();
            dev.tacticaltacz.AmmoBridge.select(gun,ammo);g.setCurrentAmmoCount(gun,10);g.setBulletInBarrel(gun,true);
            var bag=dev.tarkovcontent.TarkovContent.CONTAINERS.values().stream().map(h->h.get().getDefaultInstance()).filter(s->dev.itemfoundation.api.definition.ItemProfiles.definition(s).orElseThrow().wearableSlots().contains("tactical_inventory:backpack")).findFirst().orElseThrow();
            if(!dev.tacticalinventory.api.TacticalPickup.tryPickup(p,bag).accepted()||!dev.tacticalinventory.api.TacticalPickup.tryPickup(p,gun).accepted())throw new IllegalStateException("Cannot pick up test assets");
            if(!dev.tacticalinventory.platform.PlayerInventoryService.activateSource(p,new dev.tacticalinventory.core.FixedSlotLocation(dev.tacticalinventory.core.GearSlot.PRIMARY_WEAPON_1),0,UUID.randomUUID()))throw new IllegalStateException("Cannot equip ADAR");
            equipped=true;
        });return;}
        if(!equipped||!WEAPON.isGun(mc.player.getMainHandItem()))return;
        ticks++;
        if(ticks==20){mc.setScreen(null);if(!(com.tacz.guns.api.TimelessAPI.getClientGunIndex(WEAPON.GUN).orElseThrow().getDefaultDisplay().getGunModel() instanceof AssemblyGunModel))throw new IllegalStateException("ADAR custom model not loaded");}
        if(ticks==100){capture("01-held.png");var model=(AssemblyGunModel)com.tacz.guns.api.TimelessAPI.getClientGunIndex(WEAPON.GUN).orElseThrow().getDefaultDisplay().getGunModel();if(model.renderedTriangles!=7260)throw new IllegalStateException("Full held geometry not rendered: "+model.renderedTriangles);}
        if(ticks==110){com.tacz.guns.client.input.AimKey.AIM_KEY.setDown(true);com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator.fromLocalPlayer(mc.player).aim(true);}
        if(ticks==150){if(!com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator.fromLocalPlayer(mc.player).isAim())throw new IllegalStateException("ADS not active");capture("02-aim.png");com.tacz.guns.client.input.AimKey.AIM_KEY.setDown(false);com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator.fromLocalPlayer(mc.player).aim(false);}
        if(ticks==180)mc.setScreen(new IconSheet());
        if(ticks==195)capture("03-part-icons.png");
        if(ticks==205){mc.setScreen(null);mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);}
        if(ticks==225)capture("04-third-person.png");
        if(ticks==240){mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);}
        if(ticks==250){Files.writeString(mc.gameDirectory.toPath().resolve("adar-client.pass"),"PASS: integrated held model 7260 triangles; actual ADS; inventory item icon sheet; third-person render captured. Formal visual acceptance belongs to the user.\n");finished=true;mc.stop();}
        if(ticks>900)throw new IllegalStateException("ADAR client timeout");
    }
    private static final class IconSheet extends Screen {
        IconSheet(){super(net.minecraft.network.chat.Component.literal("ADAR 配件材质检查"));}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(net.minecraft.client.gui.GuiGraphics graphics,int mouseX,int mouseY,float partialTick){
            graphics.fill(0,0,width,height,0xff182029);
            graphics.drawCenteredString(font,title,width/2,20,0xffffff);
            var parts=WEAPON.ITEMS.keySet().stream().filter(id->!id.equals(WEAPON.ROOT)).sorted().toList();
            for(int i=0;i<parts.size();i++){
                var stack=WEAPON.createPart(parts.get(i));int x=width/2-150+(i%5)*62,y=55+(i/5)*100;
                graphics.pose().pushPose();graphics.pose().translate(x,y,0);graphics.pose().scale(3,3,3);graphics.renderItem(stack,0,0);graphics.pose().popPose();
                graphics.drawString(font,stack.getHoverName().getString().substring(0,Math.min(8,stack.getHoverName().getString().length())),x,y+55,0xffffff,false);
            }
        }
    }
    private static void capture(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->{});}
}
