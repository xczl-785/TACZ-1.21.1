package dev.tacticaltacz.verification;
import com.lowdragmc.lowdraglib2.gui.ui.*;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import dev.itemfoundation.client.api.*;
import dev.itemfoundation.api.inspection.InspectionSection;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
/** Renders the real inspection component using a codec snapshot exported by the real server smoke. */
@EventBusSubscriber(modid="tacz",value=Dist.CLIENT)
public final class InspectionClientSmoke {
    private static boolean queued,started;private static int ticks;
    private static InventoryInspectionElement window;
    private static String hoverCardId;
    private static float[] armorGeometry;
    private static UiDesign design;
    private static InspectionData fixtureData;
    private static float titleY,previewY;
    private static Float initialVisibleHeight;
    static void queue(){queued=true;}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) throws Exception {
        if(!Boolean.getBoolean("tacticaltacz.clientSmoke")||!queued)return;
        var mc=Minecraft.getInstance();
        if(mc.getOverlay()!=null)return;
        if(!started) {
            started=true;
            var raw=com.google.gson.JsonParser.parseString(Files.readString(Path.of("../tacz-adapter-smoke/protection-inspection.json")));
            var sections=InspectionSection.CODEC.listOf().parse(com.mojang.serialization.JsonOps.INSTANCE,raw).getOrThrow();
            hoverCardId="inspection-card-tactical_combat:parts/tactical_combat:fixed/"+sections.stream().filter(s->s.id().equals("tactical_combat:fixed")).findFirst().orElseThrow().rows().getLast().id();
            var item=new ItemStack(dev.tarkovcontent.TarkovContent.CONTAINERS.get("60a3c68c37ea821725773ef5").get());
            var displayRaw=com.google.gson.JsonParser.parseString(Files.readString(Path.of("../tacz-adapter-smoke/protection-inspection-display.json")));
            InspectionLanguageSmoke.verify(raw,displayRaw);
            var display=dev.itemfoundation.api.definition.ItemDisplayData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,displayRaw).getOrThrow();
            var data=new InspectionData(item,List.of(),display).sections(sections);
            var root=new InventoryRootElement();
            design=dev.tacticalinventory.client.ui.GearDesign.fit(mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());
            window=new InventoryInspectionElement(design,()->Optional.of(data),"","");
            if(window.overlay.isVisible())throw new IllegalStateException("Inspection must start hidden until layout is ready");
            root.addSurface(window,InventoryRootElement.Layer.MODAL);
            var ui=ModularUI.of(UI.of(root,size->size));ui.getTaffyTree().disableRounding();
            mc.setScreen(new ModularUIScreen(ui,Component.literal("Protection inspection smoke")));
        }
        ticks++;
        if(ticks<=20&&window.overlay.isVisible()) {
            if(initialVisibleHeight==null)initialVisibleHeight=window.overlay.getSizeHeight();
            else near(window.overlay.getSizeHeight(),initialVisibleHeight,"no opening resize after first reveal");
        }
        if(ticks==20) {
            if(initialVisibleHeight==null)throw new IllegalStateException("Inspection never became visible");
            armorGeometry=geometry();
            verifyDescriptionBottom();
            near(armorGeometry[0],design.px(InventoryInspectionElement.WIDTH),"actual width");
            if(armorGeometry[1]>=design.px(InventoryInspectionElement.HEIGHT))throw new IllegalStateException("Short armor content should size below maximum height");
            verifyCardDescriptionGap();
            verifyScrollDisplay(false);
            var first=window.selectId("inspection-card-tactical_combat:parts/tactical_combat:fixed/soft_armor_front").findFirst().orElseThrow();
            var last=window.selectId(hoverCardId).findFirst().orElseThrow();
            near(first.getSizeWidth(),last.getSizeWidth(),"wrapped card width");
            near(last.getPositionY()-first.getPositionY(),design.px(75),"shared compact strip wraps after nine cards");
            net.minecraft.client.Screenshot.grab(mc.gameDirectory,"protection-inspection-top.png",mc.getMainRenderTarget(),m->{});
        }
        if(ticks==30) {
            var scroll=(InventoryScrollView)window.selectId("gear-inspection-details").findFirst().orElseThrow();
            if(scroll.getContainerHeight()<=0)throw new IllegalStateException("Protection details missing");
            scroll.verticalScroller.setValue(1f);
        }
        if(ticks==45)net.minecraft.client.Screenshot.grab(mc.gameDirectory,"protection-inspection-slots.png",mc.getMainRenderTarget(),m->{});
        if(ticks==48) {
            var section=window.selectId(hoverCardId).findFirst().orElseThrow();
            var scroll=(InventoryScrollView)window.selectId("gear-inspection-details").findFirst().orElseThrow();
            // Center a visible card in the actual details viewport before requesting real hover rendering.
            double x=section.getPositionX()+section.getSizeWidth()/2;
            double y=section.getPositionY()+section.getSizeHeight()/2;
            if(y<scroll.viewPort.getPositionY()||y>scroll.viewPort.getPositionY()+scroll.viewPort.getSizeHeight())throw new IllegalStateException("Hover card outside viewport");
            // Deliver coordinates to the isolated client even when the OS leaves its window unfocused.
            for(var axis:List.of("xpos","ypos")) {
                var field=net.minecraft.client.MouseHandler.class.getDeclaredField(axis);field.setAccessible(true);
                field.setDouble(mc.mouseHandler,axis.equals("xpos")?x*mc.getWindow().getScreenWidth()/mc.getWindow().getGuiScaledWidth():y*mc.getWindow().getScreenHeight()/mc.getWindow().getGuiScaledHeight());
            }
            ((ModularUIScreen)mc.screen).modularUI.refreshHoveredElementAtScreen((float)x,(float)y);
        }
        if(ticks==90) {
            var ui=((ModularUIScreen)mc.screen).modularUI;
            if(!window.selectId("gear-inspection-tooltip").findFirst().orElseThrow().isVisible())throw new IllegalStateException("Actual card hover tooltip missing; hovered="+ui.getLastHoveredElement());
            net.minecraft.client.Screenshot.grab(mc.gameDirectory,"protection-inspection-hover.png",mc.getMainRenderTarget(),m->{});
        }
        if(ticks==100) {
            var tooltip=window.selectId("gear-inspection-tooltip").findFirst().orElseThrow();
            if(tooltip.getPositionX()<window.overlay.getPositionX()||tooltip.getPositionX()+tooltip.getSizeWidth()>window.overlay.getPositionX()+window.overlay.getSizeWidth()
                    ||tooltip.getPositionY()+tooltip.getSizeHeight()>window.overlay.getPositionY()+window.overlay.getSizeHeight())throw new IllegalStateException("Tooltip must stay inside window");
            fixtureData=new InspectionData(new ItemStack(net.minecraft.world.item.Items.STONE),List.of());
            window=new InventoryInspectionElement(design,()->Optional.of(fixtureData),"","");
            var root=new InventoryRootElement();root.addSurface(window,InventoryRootElement.Layer.MODAL);
            var ui=ModularUI.of(UI.of(root,size->size));ui.getTaffyTree().disableRounding();
            mc.setScreen(new ModularUIScreen(ui,Component.literal("Inspection layout fixture")));
        }
        if(ticks==112) {
            compareGeometry();
            verifyDescriptionBottom();
            if(window.overlay.getSizeHeight()>=armorGeometry[1])throw new IllegalStateException("Plain item window should shrink with less content");
            window.actions(()->java.util.stream.IntStream.range(0,8).mapToObj(i->new InventoryInspectionElement.ViewAction("fixture"+i,"测试操作"+i,true,"",()->{})).toList());
        }
        if(ticks==120) {
            var more=window.selectId("gear-inspection-more").findFirst().orElseThrow();
            var field=com.lowdragmc.lowdraglib2.gui.ui.elements.Button.class.getDeclaredField("onClick");field.setAccessible(true);
            var click=com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent.create("mouseDown");click.target=more;click.button=0;
            ((com.lowdragmc.lowdraglib2.gui.ui.event.UIEventListener)field.get(more)).handleEvent(click);
        }
        if(ticks==130) {
            compareGeometry();
            if(window.selectId("gear-inspection-more-actions").findFirst().orElseThrow().getSizeHeight()<=0)throw new IllegalStateException("More actions must expand inside content");
            net.minecraft.client.Screenshot.grab(mc.gameDirectory,"inspection-actions-fixture.png",mc.getMainRenderTarget(),m->{});
        }
        if(ticks==140) {
            fixtureData=new InspectionData(new ItemStack(net.minecraft.world.item.Items.STONE),List.of(),
                    dev.itemfoundation.api.definition.ItemDisplayData.EMPTY.withDescription(Optional.of("长说明换行与底部留白验证。\n".repeat(40)),Optional.empty()));
            window.reconcile();
        }
        if(ticks==152) {
            var scroll=(InventoryScrollView)window.selectId("gear-inspection-details").findFirst().orElseThrow();
            if(scroll.getContainerHeight()<=scroll.viewPort.getContentHeight())throw new IllegalStateException("Long description must expand the scroll range");
            near(window.overlay.getSizeHeight(),design.px(InventoryInspectionElement.HEIGHT),"maximum window height");
            verifyScrollDisplay(true);
            titleY=window.selectId("gear-inspection-close").findFirst().orElseThrow().getPositionY();
            previewY=window.selectId("gear-inspection-preview").findFirst().orElseThrow().getPositionY();
            scroll.verticalScroller.setValue(1f);
        }
        if(ticks==160) {
            verifyDescriptionBottom();
            near(window.selectId("gear-inspection-close").findFirst().orElseThrow().getPositionY(),titleY,"header remains fixed while body scrolls");
            if(window.selectId("gear-inspection-preview").findFirst().orElseThrow().getPositionY()>=previewY-design.px(20))throw new IllegalStateException("Preview must scroll with the whole body");
            net.minecraft.client.Screenshot.grab(mc.gameDirectory,"inspection-long-description.png",mc.getMainRenderTarget(),m->{});
        }
        if(ticks==165) {
            fixtureData=new InspectionData(new ItemStack(net.minecraft.world.item.Items.STONE),List.of());
            window.reconcile();
        }
        if(ticks==178) {
            verifyDescriptionBottom();
            verifyScrollDisplay(false);
            if(window.overlay.getSizeHeight()>=design.px(InventoryInspectionElement.HEIGHT))throw new IllegalStateException("Window must shrink again after long content is removed");
        }
        if(ticks==185) {
            if(!Files.exists(Path.of("screenshots/protection-inspection-top.png"))||!Files.exists(Path.of("screenshots/protection-inspection-slots.png")))throw new IllegalStateException("Inspection screenshots missing");
            Files.writeString(Path.of("protection-inspection.pass"),"PROTECTION_INSPECTION_CLIENT PASS: authoritative sections rendered and scrolled\n");
            System.out.println("PROTECTION_INSPECTION_CLIENT PASS: real inspection widget, server data, hover, adaptive height, fixed 20px spacing, whole-body scrolling, fixed header and action overflow");
            var raw=com.google.gson.JsonParser.parseString(Files.readString(Path.of("../tacz-adapter-smoke/ammunition-inspection.json")));
            var sections=InspectionSection.CODEC.listOf().parse(com.mojang.serialization.JsonOps.INSTANCE,raw).getOrThrow();
            var displayRaw=com.google.gson.JsonParser.parseString(Files.readString(Path.of("../tacz-adapter-smoke/ammunition-inspection-display.json")));
            var display=dev.itemfoundation.api.definition.ItemDisplayData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,displayRaw).getOrThrow();
            var item=new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(Files.readString(Path.of("../tacz-adapter-smoke/ammunition-inspection-id.txt")))));
            window.actions(List::of);
            fixtureData=new InspectionData(item,List.of(),display).sections(sections);
            window.reconcile();
        }
        if(ticks==205) {
            if(!window.overlay.isVisible())throw new IllegalStateException("Ammo inspection missing");
            if(fixtureData.display().descriptionText().orElseThrow().component().getString().startsWith("item.tarkov_content"))throw new IllegalStateException("Ammo description not localized");
            net.minecraft.client.Screenshot.grab(mc.gameDirectory,"ammunition-inspection.png",mc.getMainRenderTarget(),m->{});
            Files.writeString(Path.of("ammunition-inspection.pass"),"AMMUNITION_INSPECTION PASS\n");
            AssemblyDragClientSmoke.queue();
        }
    }
    private static void verifyScrollDisplay(boolean expected) {
        var scroll=(InventoryScrollView)window.selectId("gear-inspection-details").findFirst().orElseThrow();
        boolean shown=scroll.verticalScroller.getTaffyStyle().style.display==dev.vfyjxf.taffy.style.TaffyDisplay.FLEX;
        if(shown!=expected)throw new IllegalStateException("Scrollbar display: "+shown+" expected "+expected);
    }
    private static void verifyDescriptionBottom() {
        var description=window.selectId("gear-inspection-description").findFirst().orElseThrow();
        var gap=window.selectId("gear-inspection-description-gap").findFirst().orElseThrow();
        near(window.overlay.getPositionY()+window.overlay.getSizeHeight()-description.getPositionY()-description.getSizeHeight(),design.px(20),"description bottom inset");
        near(gap.getSizeHeight(),design.px(20),"fixed description separation");
        var sections=window.selectId("gear-inspection-properties").findFirst().orElseThrow();
        near(description.getPositionY()-sections.getPositionY()-sections.getSizeHeight(),design.px(20),"description follows sections by exactly 20");
    }
    private static void verifyCardDescriptionGap() {
        var last=window.selectId(hoverCardId).findFirst().orElseThrow();
        var description=window.selectId("gear-inspection-description").findFirst().orElseThrow();
        near(description.getPositionY()-last.getPositionY()-last.getSizeHeight(),design.px(20),"visible last card to description gap");
    }
    private static float[] geometry() {
        var preview=window.selectId("gear-inspection-preview").findFirst().orElseThrow();
        var actions=window.selectId("gear-inspection-actions").findFirst().orElseThrow();
        var details=window.selectId("gear-inspection-details").findFirst().orElseThrow();
        return new float[]{window.overlay.getSizeWidth(),window.overlay.getSizeHeight(),preview.getSizeHeight(),preview.getPositionY()-window.overlay.getPositionY(),actions.getPositionY()-window.overlay.getPositionY(),actions.getSizeHeight(),details.getPositionY()-window.overlay.getPositionY(),details.getSizeHeight()};
    }
    private static void compareGeometry() {var actual=geometry();for(int i:new int[]{0,2,3,4,5,6})near(actual[i],armorGeometry[i],"stable region geometry "+i);}
    private static void near(float actual,float expected,String label) {if(Math.abs(actual-expected)>.1f)throw new IllegalStateException(label+": "+actual+" != "+expected);}
}
