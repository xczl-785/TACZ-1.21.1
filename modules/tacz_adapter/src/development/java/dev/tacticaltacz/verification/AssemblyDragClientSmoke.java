package dev.tacticaltacz.verification;

import java.nio.file.*;
import java.util.*;
import dev.tacticalinventory.client.GearInventoryScreen;
import dev.tacticalinventory.client.ui.*;
import dev.tacticalinventory.network.*;
import dev.tacticalinventory.presentation.*;
import dev.itemfoundation.api.assembly.*;
import dev.itemfoundation.api.inventory.Orientation;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Real screen and pointer routing using server-exported projection; no simulated inventory writes. */
@EventBusSubscriber(modid="tacz",value=Dist.CLIENT)
public final class AssemblyDragClientSmoke {
    private static boolean queued;private static int ticks;
    private static GearInventoryScreen screen;private static GearScreenModel model;private static GearInspectionElement view;
    static void queue(){queued=true;}
    private static Object field(String name)throws Exception {var f=GearInventoryScreen.class.getDeclaredField(name);f.setAccessible(true);return f.get(screen);}
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError("Assembly drag: "+message);}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception {
        if(!queued||!Boolean.getBoolean("tacticaltacz.clientSmoke"))return;
        var mc=Minecraft.getInstance();ticks++;
        if(ticks==1) {
            var accept=AssemblyDefinitions.class.getDeclaredMethod("acceptClient",String.class);accept.setAccessible(true);
            accept.invoke(null,Files.readString(Path.of("../tacz-adapter-smoke/assembly-drag-definitions.json")));
            var projection=InventoryProjection.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                    com.google.gson.JsonParser.parseString(Files.readString(Path.of("../tacz-adapter-smoke/assembly-drag-projection.json")))).getOrThrow();
            screen=new GearInventoryScreen(new InventoryStatePayload(projection,InventoryFeedback.READY,true));mc.setScreen(screen);
            model=(GearScreenModel)field("model");
            var host=projection.regions().stream().flatMap(r->r.fixedItem().stream()).findFirst().orElseThrow();
            var open=GearInventoryScreen.class.getDeclaredMethod("openAssembly",UUID.class);open.setAccessible(true);open.invoke(screen,host.entryId());
            view=(GearInspectionElement)((GearOverlayHost)field("overlays")).modal();
            // Leave the pocket exposed, just as dragging the title bar does.
            view.overlay.layout(l->l.positionType(dev.vfyjxf.taffy.style.TaffyPosition.ABSOLUTE).left(mc.getWindow().getGuiScaledWidth()-model.design().px(dev.itemfoundation.client.api.InventoryInspectionElement.WIDTH+20)).top(model.design().px(20)));
        }
        if(ticks==25) {
            require(view.overlay.isVisible(),"inspection revealed after layout");
            var slot=view.slots().stream().filter(s->s.installed().isPresent()).findFirst().orElseThrow();
            var entries=model.projection().regions().stream().flatMap(r->r.entries().stream()).toList();
            var compatible=entries.stream().filter(e->slot.compatible(e.stack())).findFirst().orElseThrow();
            var incompatible=entries.stream().filter(e->!slot.compatible(e.stack())).findFirst().orElseThrow();
            var check=GearInventoryScreen.class.getDeclaredMethod("canInstall",GearInspectionElement.class,GearInspectionElement.Slot.class);check.setAccessible(true);
            var grid=((GearUiTargets)field("targets")).grids().stream().filter(g->g.region()!=null&&g.region().regionId().equals("player:pocket")).findFirst().orElseThrow();
            var bounds=grid.placementBounds(compatible.position(),compatible.orientedFootprint());
            double sourceX=bounds.left()+grid.cellSize()*.25,sourceY=bounds.top()+grid.cellSize()*.25;
            require(!view.contains(sourceX,sourceY),"fixture pocket is exposed outside window");
            require(view.hitTest(sourceX,sourceY)==null,"transparent inspection wrapper does not capture exposed grid wheel events");
            screen.mouseClicked(sourceX,sourceY,0);screen.mouseDragged(sourceX+20,sourceY+20,0,20,20);
            require(model.selection()!=null&&model.selection().entryId().equals(compatible.entryId()),"backpack drag starts while inspection is open: point="+sourceX+","+sourceY+" cell="+grid.cellAt(sourceX,sourceY)+" pressed="+field("pressedItem")+" selection="+model.selection()+" pointer="+((GearPointerInteraction)field("pointerInteraction")).active()+" pending="+model.interactions().pending());
            screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE,0,0);
            screen.keyReleased(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE,0,0);
            model.selection(GearScreenModel.Selection.grid("player:pocket",compatible.entryId(),Orientation.DEFAULT));
            require((boolean)check.invoke(screen,view,slot),"occupied compatible slot remains green");
            model.selection(GearScreenModel.Selection.grid("player:pocket",incompatible.entryId(),Orientation.DEFAULT));
            require(!(boolean)check.invoke(screen,view,slot),"incompatible slot is red");model.selection(null);
            var cell=slot.cell();double x=cell.getPositionX()+cell.getSizeWidth()/2,y=cell.getPositionY()+cell.getSizeHeight()/2;
            require(view.slotAt(x,y)!=null,"visible slot hit target");
            screen.mouseClicked(x,y,0);screen.mouseDragged(x+20,y+20,0,20,20);
            require(field("mountedDrag")!=null&&(boolean)field("mountedDragging"),"slot starts drag of real installed item");
            screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE,0,0);
            screen.keyReleased(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE,0,0);
            require(field("mountedDrag")==null,"escape cancels without transfer");
            var viewport=view.viewport();require(view.slotAt(x,viewport.getPositionY()-1)==null,"clipped area cannot target hidden slot");
            Files.writeString(Path.of("assembly-drag.pass"),"ASSEMBLY_DRAG_CLIENT PASS\n");
            System.out.println("ASSEMBLY_DRAG_CLIENT PASS: server projection, compatible occupied green, incompatible red, slot pointer drag, cancel and clipping");
            // This title-screen fixture must perform the same gun-data synchronization as a real join.
            var wireCache=com.google.gson.JsonParser.parseString(Files.readString(Path.of("../tacz-adapter-smoke/development-gun-cache.json"))).getAsJsonObject();
            var cache=new java.util.EnumMap<com.tacz.guns.resource.network.DataType,Map<net.minecraft.resources.ResourceLocation,String>>(com.tacz.guns.resource.network.DataType.class);
            wireCache.entrySet().forEach(type->{var rows=new HashMap<net.minecraft.resources.ResourceLocation,String>();type.getValue().getAsJsonObject().entrySet().forEach(row->rows.put(net.minecraft.resources.ResourceLocation.parse(row.getKey()),row.getValue().getAsString()));cache.put(com.tacz.guns.resource.network.DataType.valueOf(type.getKey()),rows);});
            com.tacz.guns.resource.network.CommonNetworkCache.INSTANCE.fromNetwork(cache);
            com.tacz.guns.client.resource.ClientIndexManager.reload();
            dev.tacticalinventory.verification.DevelopmentItemsScreen.verify();
        }
    }
}
