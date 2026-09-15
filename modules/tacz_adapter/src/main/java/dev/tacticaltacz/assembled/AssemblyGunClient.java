package dev.tacticaltacz.assembled;
import dev.weaponassembly.api.*;
import dev.weaponassemblyui.session.*;
import dev.weaponassemblyui.client.WorkbenchScreen;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.*;

/** Client projection, hover plans and input only. No optimistic inventory writes or local undo. */
@EventBusSubscriber(modid="tacz",value=Dist.CLIENT)
public final class AssemblyGunClient {
    private static UUID request=AssemblyGunProtocol.EMPTY,token=AssemblyGunProtocol.EMPTY;
    private static boolean opening,pending;
    private static WorkbenchScreen screen;
    private static Host host;
    private static ItemStack quoted=ItemStack.EMPTY;
    private static int ticks;
    @SubscribeEvent public static void opening(ScreenEvent.Opening event){
        var mc=Minecraft.getInstance();
        if(event.getNewScreen() instanceof com.tacz.guns.client.gui.GunRefitScreen&&mc.player!=null&&AssembledWeapons.isGun(mc.player.getMainHandItem())){
            event.setCanceled(true);open();
        }
    }
    public static void open(){opening=true;screen=null;send(0,"",List.of());}
    private static void send(int action,String source,List<String> path){request=UUID.randomUUID();pending=true;PacketDistributor.sendToServer(new AssemblyGunProtocol.Request(request,token,action,source,path));}
    public static void receive(AssemblyGunProtocol.View view){
        var mc=Minecraft.getInstance();if(!view.requestId().equals(request)||mc.player==null||(!opening&&mc.screen!=screen))return;
        pending=false;token=view.token();
        if(token.equals(AssemblyGunProtocol.EMPTY)||!AssembledWeapons.isGun(view.held())){
            opening=false;if(mc.screen==screen)mc.setScreen(null);screen=null;host=null;
            mc.player.displayClientMessage(Component.translatable("tactical_tacz_adapter.assembly_workbench.unavailable"),true);return;
        }
        var weapon=AssembledWeapons.from(view.held());
        try{
            if(host==null||opening||!weapon.isGun(quoted)){opening=true;host=new Host();}host.accept(view);quoted=view.held();
            if(opening){
                var model=com.tacz.guns.api.TimelessAPI.getClientGunIndex(weapon.GUN).orElseThrow().getDefaultDisplay().getGunModel();
                if(!(model instanceof AssemblyGunModel assembled))throw new IllegalStateException("Assembly model type unavailable");
                screen=new WorkbenchScreen(host,assembled.geometry(),assembled.materials(),id->Component.translatable("item."+weapon.ITEMS.get(id).replace(':','.')).getString(),id->{var item=net.minecraft.resources.ResourceLocation.parse(weapon.ITEMS.get(id));return item.withPath("textures/item/"+item.getPath()+".png");},view.held().getHoverName().getString()+" · "+Component.translatable("tactical_tacz_adapter.assembly_workbench.title").getString());
                opening=false;mc.setScreen(screen);
            }
            if(!view.result().isEmpty())mc.player.displayClientMessage(Component.translatable("tactical_tacz_adapter.assembly_workbench."+view.result()),true);
        }catch(IllegalArgumentException bad){opening=false;screen=null;host=null;mc.setScreen(null);mc.player.displayClientMessage(Component.translatable("tactical_tacz_adapter.assembly_workbench.unavailable"),true);}
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();
        if(screen!=null&&mc.screen!=screen){screen=null;host=null;token=AssemblyGunProtocol.EMPTY;pending=false;opening=false;return;}
        if(screen!=null&&mc.player!=null&&!pending&&++ticks%20==0&&!ItemStack.matches(quoted,mc.player.getMainHandItem()))send(0,"",List.of());
    }
    private static final class Host implements WorkbenchAccess {
        private AssemblySession view;
        private Map<UUID,String> sources=Map.of();
        void accept(AssemblyGunProtocol.View response){
            var weapon=AssembledWeapons.from(response.held());
            var selected=view==null?List.<String>of():view.selectedPath();var stock=new ArrayList<AssemblyNode>();var ids=new HashMap<UUID,String>();
            for(var choice:response.choices()){var node=weapon.project(choice.stack());stock.add(node);ids.put(node.instanceId(),choice.id());}
            view=new AssemblySession(weapon.CATALOG,weapon.project(response.held()),stock,new WeaponStats.Context(0,0));sources=Map.copyOf(ids);
            while(!view.select(selected)&&!selected.isEmpty())selected=selected.subList(0,selected.size()-1);
        }
        public AssemblyCatalog catalog(){return view.catalog();}
        public AssemblyNode tree(){return view.tree();}
        public List<AssemblyNode> stock(){return view.stock();}
        public List<AssemblyNode> detached(){return List.of();}
        public List<String> selectedPath(){return view.selectedPath();}
        public Optional<AssemblySession.Preview> preview(){return view.preview();}
        public List<AssemblyEngine.Issue> feedback(){return view.feedback();}
        public WeaponStats.Values stats(){return view.stats();}
        public AssemblyEngine.Validation validation(){return view.validation();}
        public boolean busy(){return pending;}
        public boolean canUndo(){return false;}
        public boolean canReset(){return false;}
        public Optional<AssemblyNode> nodeAt(List<String> path){return view.nodeAt(path);}
        public List<AssemblySession.SlotView> slots(List<String> path){return view.slots(path);}
        public boolean select(List<String> path){return view.select(path);}
        public List<AssemblyNode> candidates(){return pending?List.of():view.candidates();}
        public void clearPreview(){view.clearPreview();}
        public AssemblySession.Preview preview(UUID id){return view.preview(id);}
        public AssemblyEngine.Result install(UUID id){
            var proposal=view.preview(id).plan();
            if(!proposal.success())return proposal;
            view.clearPreview();
            if(!pending&&proposal.success()&&sources.containsKey(id))send(1,sources.get(id),selectedPath());return unchanged();
        }
        public AssemblyEngine.Result remove(){if(!pending&&!selectedPath().isEmpty())send(2,"",selectedPath());return unchanged();}
        private AssemblyEngine.Result unchanged(){return new AssemblyEngine.Result(tree(),tree(),Optional.empty(),List.of());}
        public boolean undo(){return false;}
        public void reset(){}
    }
    private AssemblyGunClient(){}
}
