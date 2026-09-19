package dev.tacticaltacz.assembled;
import dev.firearms.assembly.*;
import dev.firearms.workbench.*;
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
    private static final AssemblyWorkbenchLifecycle lifecycle=new AssemblyWorkbenchLifecycle();
    private static UUID token=AssemblyGunProtocol.EMPTY;
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
    public static void open(){
        var mc=Minecraft.getInstance();
        token=AssemblyGunProtocol.EMPTY;host=null;screen=null;quoted=ItemStack.EMPTY;
        var request=lifecycle.open(mc.screen);
        PacketDistributor.sendToServer(new AssemblyGunProtocol.Request(request,token,0,"",List.of()));
    }
    private static void send(int action,String source,List<String> path){
        lifecycle.exchange(Minecraft.getInstance().screen).ifPresent(request->
                PacketDistributor.sendToServer(new AssemblyGunProtocol.Request(request,token,action,source,path)));
    }
    private static WorkbenchScreen createScreen(AssembledWeapon weapon,WorkbenchAccess access,java.util.function.Supplier<ItemStack> quotedStack,String name,Runnable modeAction){
        var display=com.tacz.guns.api.TimelessAPI.getClientGunIndex(weapon.GUN).orElseThrow().getDefaultDisplay();
        var displayModel=display.getGunModel();
        var geometry=displayModel instanceof AssemblyGunModel assembled?assembled.geometry():NativeAssemblyView.geometry(weapon);
        var materials=displayModel instanceof AssemblyGunModel assembled?assembled.materials():NativeAssemblyView.materials(weapon,geometry);
        java.util.function.Supplier<Map<UUID,ItemStack>> payloads=access instanceof Host nativeHost?nativeHost::candidatePayloads:Map::of;
        var backend=displayModel==null?null:new NativeWorkbenchScene(weapon,display.createWorkbenchGunModel(),display.getModelTexture(),display.enablesTransparency(),quotedStack,
                ()->access.preview().filter(p->p.plan().success()).map(p->p.plan().after()).orElse(access.tree()),payloads);
        return new WorkbenchScreen(access,geometry,materials,
                id->weapon.nativeRig?weapon.createPart(id).getHoverName().getString():Component.translatable("item."+weapon.ITEMS.get(id).replace(':','.')).getString(),
                weapon::partIcon,
                name,modeAction,backend);
    }
    private static void editPreset(){
        var mc=Minecraft.getInstance();
        if(mc.player==null||mc.screen!=screen||host==null||lifecycle.pending()||lifecycle.temporary())return;
        // Copy only a confirmed view of the current held item. Refresh first if it changed.
        if(!ItemStack.matches(quoted,mc.player.getMainHandItem())){open();return;}
        var weapon=AssembledWeapons.from(quoted);
        var draft=AssemblySession.preset(host.catalog(),host.tree(),new WeaponStats.Context(0,0),host.statsExplanation());
        var base=quoted.copy();var next=createScreen(weapon,draft,()->base,quoted.getHoverName().getString(),AssemblyGunClient::open);
        if(!lifecycle.preset(screen,next))return;
        token=AssemblyGunProtocol.EMPTY;host=null;quoted=ItemStack.EMPTY;screen=next;
        mc.setScreen(next);
    }
    public static void receive(AssemblyGunProtocol.View view){
        var mc=Minecraft.getInstance();
        if(mc.player==null||!lifecycle.receive(view.requestId(),mc.screen))return;
        boolean opening=lifecycle.opening();token=view.token();
        if(token.equals(AssemblyGunProtocol.EMPTY)||!AssembledWeapons.isGun(view.held())){
            if(mc.screen instanceof WorkbenchScreen)mc.setScreen(null);clear();
            mc.player.displayClientMessage(Component.translatable("tactical_tacz_adapter.assembly_workbench.unavailable"),true);return;
        }
        var weapon=AssembledWeapons.from(view.held());
        try{
            if(host==null||opening||!weapon.isGun(quoted)){opening=true;host=new Host();}
            host.accept(view);quoted=view.held().copy();
            if(weapon.nativeRig&&view.result().equals("committed"))com.tacz.guns.resource.modifier.AttachmentPropertyManager.postChangeEvent(mc.player,mc.player.getMainHandItem());
            if(opening){
                screen=createScreen(weapon,host,()->quoted,view.held().getHoverName().getString(),AssemblyGunClient::editPreset);
                lifecycle.real(screen);mc.setScreen(screen);
            }
            if(!view.result().isEmpty())mc.player.displayClientMessage(Component.translatable("tactical_tacz_adapter.assembly_workbench."+view.result()),true);
        }catch(IllegalArgumentException bad){clear();mc.setScreen(null);mc.player.displayClientMessage(Component.translatable("tactical_tacz_adapter.assembly_workbench.unavailable"),true);}
    }
    private static void clear(){lifecycle.close();screen=null;host=null;token=AssemblyGunProtocol.EMPTY;quoted=ItemStack.EMPTY;ticks=0;}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();
        if(!lifecycle.watch(mc.screen)||mc.player==null){clear();return;}
        // A temporary preset has no server quote, inventory source or refresh channel.
        if(lifecycle.temporary()||lifecycle.opening())return;
        if(screen!=null&&!lifecycle.pending()&&++ticks%20==0&&!ItemStack.matches(quoted,mc.player.getMainHandItem()))send(0,"",List.of());
    }
    private static final class Host implements WorkbenchAccess {
        private AssemblySession view;
        private boolean nativeRig;
        private Map<UUID,String> sources=Map.of();
        private Map<UUID,ItemStack> payloads=Map.of();
        void accept(AssemblyGunProtocol.View response){
            var weapon=AssembledWeapons.from(response.held());nativeRig=weapon.nativeRig;
            var selected=view==null?List.<String>of():view.selectedPath();var stock=new ArrayList<AssemblyNode>();var ids=new HashMap<UUID,String>();var stacks=new HashMap<UUID,ItemStack>();
            for(var choice:response.choices()){var node=weapon.project(choice.stack());stock.add(node);ids.put(node.instanceId(),choice.id());stacks.put(node.instanceId(),choice.stack().copy());}
            view=new AssemblySession(weapon.CATALOG,weapon.project(response.held()),stock,new WeaponStats.Context(0,0));sources=Map.copyOf(ids);payloads=Map.copyOf(stacks);
            while(!view.select(selected)&&!selected.isEmpty())selected=selected.subList(0,selected.size()-1);
        }
        public AssemblyCatalog catalog(){return view.catalog();}
        Map<UUID,ItemStack> candidatePayloads(){return payloads;}
        public AssemblyNode tree(){return view.tree();}
        public List<AssemblyNode> stock(){return view.stock();}
        public List<AssemblyNode> detached(){return List.of();}
        public List<String> selectedPath(){return view.selectedPath();}
        public Optional<AssemblySession.Preview> preview(){return view.preview();}
        public List<AssemblyEngine.Issue> feedback(){return view.feedback();}
        public WeaponStats.Values stats(){return view.stats();}
        // Presence suppresses misleading converted stats in preset mode; an empty value keeps that
        // behavior without occupying the workbench with a fixed explanatory sentence.
        public Optional<String> statsExplanation(){return nativeRig?Optional.of(""):Optional.empty();}
        public AssemblyEngine.Validation validation(){return view.validation();}
        public boolean busy(){return lifecycle.pending();}
        public boolean canUndo(){return false;}
        public boolean canReset(){return false;}
        public Optional<AssemblyNode> nodeAt(List<String> path){return view.nodeAt(path);}
        public List<AssemblySession.SlotView> slots(List<String> path){return view.slots(path);}
        public boolean select(List<String> path){return view.select(path);}
        public List<AssemblyNode> candidates(){return lifecycle.pending()?List.of():view.candidates();}
        public void clearPreview(){view.clearPreview();}
        public AssemblySession.Preview preview(UUID id){return view.preview(id);}
        public AssemblyEngine.Result install(UUID id){
            var proposal=view.preview(id).plan();
            if(!proposal.success())return proposal;
            view.clearPreview();
            if(!lifecycle.pending()&&proposal.success()&&sources.containsKey(id))send(1,sources.get(id),selectedPath());return unchanged();
        }
        public AssemblyEngine.Result remove(){if(!lifecycle.pending()&&!selectedPath().isEmpty())send(2,"",selectedPath());return unchanged();}
        private AssemblyEngine.Result unchanged(){return new AssemblyEngine.Result(tree(),tree(),Optional.empty(),List.of());}
        public boolean undo(){return false;}
        public void reset(){}
    }
    private AssemblyGunClient(){}
}
