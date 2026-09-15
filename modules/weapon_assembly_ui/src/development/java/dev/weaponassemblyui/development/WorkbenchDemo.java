package dev.weaponassemblyui.development;

import dev.weaponmodels.*;
import com.google.gson.*;
import dev.weaponassembly.api.*;
import dev.weaponassembly.io.AssemblyJson;
import dev.weaponassemblyui.client.*;
import dev.weaponassemblyui.session.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Opt-in development host. Never included in the player Jar or attached to a world. */
@EventBusSubscriber(modid="tacz",value=Dist.CLIENT)
public final class WorkbenchDemo {
    private static boolean started,finished,languageReady;
    private static int ticks;
    private static WorkbenchScreen screen;
    private static AssemblyNode initial;
    private static UUID candidate;
    private static Path report;
    private static final Map<String,com.lowdragmc.lowdraglib2.gui.ui.UIElement> slotElements=new HashMap<>();
    private static final Map<String,SlotLayout.Point> slotCoordinates=new HashMap<>();
    private static AssemblyViewport stableViewport;
    private static com.lowdragmc.lowdraglib2.gui.ui.UIElement stablePage;
    private static final List<String> evidence=new ArrayList<>();
    private static final List<List<String>> allPartPaths=new ArrayList<>();
    private static String resource(String name) throws IOException {
        try(var in=WorkbenchDemo.class.getResourceAsStream("/assembly-adar/"+name)) {
            if(in==null)throw new FileNotFoundException(name);return new String(in.readAllBytes(),StandardCharsets.UTF_8);
        }
    }
    public static WorkbenchScreen create() throws IOException {
        var catalog=AssemblyJson.readCatalog(resource("catalog.json"));
        var tree=AssemblyJson.readSnapshot(resource("scene.json"),new AssemblyEngine(catalog));initial=tree;
        allPartPaths.clear();collectPaths(tree,List.of());
        var stock=new ArrayList<AssemblyNode>();
        for(var definition:catalog.parts().values())if(definition.weapon().isEmpty())for(int i=0;i<2;i++)
            stock.add(AssemblyNode.leaf(UUID.nameUUIDFromBytes(("demo:"+definition.id()+":"+i).getBytes(StandardCharsets.UTF_8)),definition.id()));
        var names=new HashMap<String,String>();
        var manifest=resource("manifest.json");
        for(var item:JsonParser.parseString(manifest).getAsJsonObject().getAsJsonArray("models")) {
            var model=item.getAsJsonObject();names.put(model.get("definitionId").getAsString(),model.get("name").getAsString().replace('_',' '));
        }
        var models=ModelGeometry.load(new StringReader(manifest));
        var resources=Minecraft.getInstance().getResourceManager();
        AssemblyMaterials materials;
        try(var library=resources.openAsReader(net.minecraft.resources.ResourceLocation.parse("weapon_assembly_ui:materials/basic.json"));
            var bindings=resources.openAsReader(net.minecraft.resources.ResourceLocation.parse("weapon_assembly_ui:materials/adar.json"))) {
            materials=AssemblyMaterials.load(library,bindings,models);
        }
        for(var material:materials.all())if(!material.texture().isEmpty())
            resources.getResourceOrThrow(net.minecraft.resources.ResourceLocation.parse(material.texture()));
        return new WorkbenchScreen(new AssemblySession(catalog,tree,stock,new WeaponStats.Context(0,0)),
            models,materials,id->names.getOrDefault(id,id),id->net.minecraft.resources.ResourceLocation.parse("weapon_assembly_ui:textures/demo-parts/part_"+id+".png"),"ADAR 2-15 · 配件组装测试");
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("weaponassembly.demo")||finished)return;
        var mc=Minecraft.getInstance();
        try {
            if(!started&&mc.screen instanceof AccessibilityOnboardingScreen){mc.setScreen(new TitleScreen());return;}
            if(mc.getOverlay()!=null)return;
            if(!started&&mc.screen instanceof TitleScreen){
                if(!languageReady){languageReady=true;mc.options.languageCode="zh_cn";mc.getLanguageManager().setSelected("zh_cn");mc.reloadResourcePacks();return;}
                report=mc.gameDirectory.toPath().resolve("assembly-ui-audit.txt");Files.deleteIfExists(report);
                screen=create();mc.setScreen(screen);started=true;return;
            }
            if(!started||!Boolean.getBoolean("weaponassembly.audit"))return;
            if(!(mc.screen instanceof WorkbenchScreen))throw new IllegalStateException("Workbench screen closed unexpectedly");
            if(mc.getOverlay()!=null)return;
            ticks++;
            if(ticks>=450) {
                int part=(ticks-450)/60,phase=(ticks-450)%60;
                if(part==allPartPaths.size()) {
                    closeChooserIfOpen();capture("09-all-parts-verified.png");
                    evidence.add("PASS: isolated native UI audit");Files.write(report,evidence);
                    System.out.println("ASSEMBLY_UI_AUDIT PASS "+report);finished=true;mc.stop();return;
                }
                var path=allPartPaths.get(part);
                if(phase==0) {
                    if(screen.chooserOpen())closeChooserIfOpen();
                    click("assembly-slot-"+String.join("-",path));
                }
                if(phase==15)click("assembly-remove");
                if(phase==30) {
                    require(screen.access().nodeAt(path).isEmpty(),"detached "+String.join("/",path));
                    candidate=screen.access().detached().getFirst().instanceId();
                    click("assembly-slot-"+String.join("-",path));
                }
                if(phase==40)click("assembly-candidate-"+candidate);
                if(phase==45)require(screen.access().tree().equals(initial)&&screen.access().detached().isEmpty(),"restored same instances "+String.join("/",path));
                return;
            }
            switch(ticks) {
                case 60 -> {
                    require(mc.level==null,"no world");
                    try {Class.forName("dev.tacticalinventory.TacticalInventory");throw new IllegalStateException("Tactical present");}catch(ClassNotFoundException expected){}
                    require(screen.viewport().getSizeWidth()>0,"viewport layout");
                    require(screen.access().validation().complete(),"initial ADAR complete");
                    require(screen.access().catalog().parts().size()==11,"11 modelled ADAR definitions");
                    capture("01-workbench.png");click("assembly-slot-mod_reciever");
                }
                case 75 -> closeChooserIfOpen();
                case 90 -> click("assembly-slot-mod_reciever-mod_barrel");
                case 105 -> {
                    require(screen.chooserOpen(),"projected label opens chooser");
                    candidate=screen.access().candidates().getFirst().instanceId();
                    var b=screen.modularUI.getElementById("assembly-candidate-"+candidate);
                    screen.modularUI.requestFocus(b);
                }
                case 120 -> {
                    require(screen.access().preview().isPresent(),"focus previews candidate");
                    require(screen.access().tree()==initial&&!screen.access().canUndo(),"preview does not commit");
                    capture("02-preview.png");click("assembly-candidate-"+candidate);
                }
                case 135 -> {
                    require(screen.access().tree()!=initial&&screen.access().detached().size()==1,"install retains replaced part");
                    click("assembly-slot-mod_reciever-mod_barrel");
                }
                case 140 -> click("assembly-remove");
                case 150 -> {
                    require(!screen.access().validation().complete()&&screen.access().nodeAt(List.of("mod_reciever","mod_barrel")).isEmpty(),"required barrel can be detached");
                    capture("03-incomplete.png");closeChooserIfOpen();click("assembly-undo");
                }
                case 165 -> {require(screen.access().nodeAt(List.of("mod_reciever","mod_barrel")).isPresent(),"undo restores barrel");click("assembly-reset");}
                case 180 -> {require(screen.access().tree().equals(initial),"reset restores entire session");click("assembly-slot-mod_reciever");}
                case 195 -> closeChooserIfOpen();
                case 210 -> {
                    require(screen.modularUI.getElementById("assembly-slot-mod_reciever-mod_scope")!=null,"recursive optic slot visible");
                    click("assembly-slot-mod_reciever-mod_scope");capture("04-direct-optic.png");
                }
                case 225 -> {
                    var before=screen.viewport().projectSlot(List.of("mod_reciever"),"mod_scope").orElseThrow();
                    double dragX=4,dragY=screen.height*.5;
                    screen.mouseClicked(dragX,dragY,0);screen.mouseDragged(dragX+40,dragY,0,40,0);screen.mouseReleased(dragX+40,dragY,0);
                    var after=screen.viewport().projectSlot(List.of("mod_reciever"),"mod_scope").orElseThrow();
                    require(!before.equals(after),"camera drag changes projection");
                    require(screen.chooserOpen(),"camera drag preserves chooser");
                    closeChooserIfOpen();require(!screen.chooserOpen(),"Esc closes chooser");
                }
                case 240 -> {
                    var before=screen.viewport().projectSlot(List.of("mod_reciever"),"mod_scope").orElseThrow();
                    screen.mouseScrolled(4,screen.height/2.,0,2);
                    require(!before.equals(screen.viewport().projectSlot(List.of("mod_reciever"),"mod_scope").orElseThrow()),"wheel changes camera zoom");
                    click("assembly-camera-reset");
                    require(screen.viewport().getSizeWidth()>screen.width*.97&&screen.viewport().getSizeHeight()>screen.height*.97,"viewport fills window with small border");
                    var card=screen.modularUI.getElementById("assembly-slot-mod_reciever-mod_scope");
                    double mx=card.getPositionX()+card.getSizeWidth()/2,my=card.getPositionY()+card.getSizeHeight()/2;
                    var anchorBefore=screen.viewport().projectSlot(List.of("mod_reciever"),"mod_scope").orElseThrow();
                    screen.mouseScrolled(mx,my,0,.125);
                    var anchorAfter=screen.viewport().projectSlot(List.of("mod_reciever"),"mod_scope").orElseThrow();
                    double ratio=Math.exp(.125*.12);
                    require(Math.abs(anchorAfter.x()-(mx+(anchorBefore.x()-mx)*ratio))<.02&&Math.abs(anchorAfter.y()-(my+(anchorBefore.y()-my)*ratio))<.02,"fractional wheel over slot zooms around cursor");
                    click("assembly-camera-reset");
                    click("assembly-material-mode");require(screen.viewport().whiteModel(),"white comparison enabled");
                }
                case 255 -> {capture("05-white-model.png");click("assembly-material-mode");require(!screen.viewport().whiteModel(),"original materials restored");click("assembly-slot-mod_reciever-mod_barrel");}
                case 270 -> closeChooserIfOpen();
                case 285 -> {
                    click("assembly-slot-mod_reciever-mod_barrel-mod_muzzle");
                    require(screen.access().selectedPath().size()==3,"direct muzzle slot keeps full internal identity");
                    capture("06-direct-muzzle.png");
                }
                case 300 -> {rememberSlotLayout();click("assembly-remove");}
                case 315 -> {
                    verifySlotLayout();
                    require(screen.access().validation().complete(),"optional muzzle removal remains complete");
                    candidate=screen.access().detached().getFirst().instanceId();
                    click("assembly-slot-mod_reciever-mod_barrel-mod_muzzle");
                }
                case 320 -> click("assembly-candidate-"+candidate);
                case 330 -> {require(screen.access().tree().equals(initial),"removed muzzle reinstalled with same instance");closeChooserIfOpen();}
                case 345 -> require(screen.modularUI.getElementById("assembly-back")==null,"no hierarchy navigation");
                case 360 -> click("assembly-slot-mod_reciever");
                case 375 -> {click("assembly-remove");}
                case 390 -> {
                    require(screen.access().detached().getFirst().children().size()==3,"receiver removal keeps barrel handguard optic subtree");
                    capture("07-receiver-detached.png");
                    click("assembly-slot-mod_reciever");
                }
                case 395 -> click("assembly-candidate-"+screen.access().detached().getFirst().instanceId());
                case 405 -> {
                    require(screen.access().tree().equals(initial)&&screen.access().validation().complete(),"receiver subtree reinstalled intact");
                    closeChooserIfOpen();
                    screen.select(List.of("mod_reciever","mod_scope"));
                    screen.access().preview(screen.access().candidates().getFirst().instanceId());
                    mc.options.guiScale().set(mc.options.guiScale().get()==3?2:3);mc.resizeDisplay();
                }
                case 435 -> {
                    require(screen.access().preview().isEmpty(),"resize clears uncommitted preview");
                    require(screen.modularUI.getElementById("assembly-undo").getSizeWidth()>0,"resized GUI controls");capture("08-gui-scale.png");
                }
                default -> {}
            }
        } catch(Throwable e) {
            e.printStackTrace();try {if(report!=null)Files.writeString(report,"FAIL: "+e+"\n"+String.join("\n",evidence));}catch(IOException ignored){}
            finished=true;mc.stop();
        }
    }
    private static void closeChooserIfOpen() { if(screen.chooserOpen())screen.keyPressed(256,0,0); }
    private static void rememberSlotLayout() {
        slotElements.clear();slotCoordinates.clear();
        stableViewport=screen.viewport();stablePage=stableViewport.getParent();
        for(var slot:screen.access().visibleSlots()) {
            String id="assembly-slot-"+String.join("-",slot.path());
            var element=screen.modularUI.getElementById(id);
            slotElements.put(id,element);slotCoordinates.put(id,new SlotLayout.Point(element.getPositionX(),element.getPositionY()));
        }
    }
    private static void verifySlotLayout() {
        require(screen.viewport()==stableViewport&&stableViewport.getParent()==stablePage,"leaf removal preserves viewport and page instances");
        require(!screen.chooserOpen(),"leaf removal closes chooser");
        for(var entry:slotElements.entrySet()) {
            var element=screen.modularUI.getElementById(entry.getKey());
            require(element==entry.getValue(),"leaf removal preserves card "+entry.getKey());
            require(slotCoordinates.get(entry.getKey()).equals(new SlotLayout.Point(element.getPositionX(),element.getPositionY())),"leaf removal has zero slot movement "+entry.getKey());
        }
    }
    private static void collectPaths(AssemblyNode node,List<String> path) {
        node.children().forEach((slot,child)->{var next=new ArrayList<>(path);next.add(slot);allPartPaths.add(List.copyOf(next));collectPaths(child,next);});
    }
    private static void click(String id) {
        if(id.startsWith("assembly-slot-")) {
            var card=screen.modularUI.getElementById(id);
            double x=card.getPositionX()+card.getSizeWidth()/2,y=card.getPositionY()+card.getSizeHeight()/2;
            screen.mouseMoved(x,y);
            var toggle=screen.modularUI.getElementById(id.replace("assembly-slot-","assembly-toggle-"));
            require(toggle.isVisible(),"hover reveals dropdown bar");
            screen.mouseClicked(x,y,0);screen.mouseReleased(x,y,0);
            require(!screen.chooserOpen(),"slot card does not expand chooser");
            id=id.replace("assembly-slot-","assembly-toggle-");
        }
        var b=screen.modularUI.getElementById(id);if(b==null)throw new IllegalStateException("Missing button "+id);
        double x=b.getPositionX()+b.getSizeWidth()/2,y=b.getPositionY()+b.getSizeHeight()/2;
        screen.mouseMoved(x,y);screen.mouseClicked(x,y,0);screen.mouseReleased(x,y,0);
        if(id.equals("assembly-remove")||id.startsWith("assembly-candidate-"))
            require(!screen.chooserOpen(),"assembly action closes chooser immediately");
    }
    private static void require(boolean value,String evidenceLine) {if(!value)throw new IllegalStateException(evidenceLine);evidence.add(evidenceLine);}
    private static void capture(String name) {
        var mc=Minecraft.getInstance();
        try {Files.deleteIfExists(mc.gameDirectory.toPath().resolve("screenshots").resolve(name));}catch(IOException e){throw new UncheckedIOException(e);}
        Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->System.out.println(message.getString()));
    }
}
