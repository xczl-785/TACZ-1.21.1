package dev.weaponassemblyui.client;

import dev.weaponmodels.*;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.*;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.math.Size;
import dev.itemfoundation.client.api.*;
import dev.vfyjxf.taffy.style.TaffyPosition;
import dev.firearms.assembly.*;
import dev.weaponassemblyui.session.*;
import net.minecraft.resources.ResourceLocation;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Function;

/** Standard LDLib2 workbench. State and submission belong to the supplied host. */
public final class WorkbenchScreen extends ModularUIScreen {
    private final WorkbenchAccess host;
    private final Map<String,ModelGeometry> geometry;
    private final AssemblyMaterials materials;
    private final Function<String,String> partName;
    private final String titleText;
    private final Runnable modeAction;
    private final Function<String,ResourceLocation> partImage;
    private final WorkbenchModelBackend modelBackend;
    private final InventoryRootElement root;
    private UiDesign design;
    private double canvasWidth,canvasHeight;
    private final WorkbenchTimings timings=new WorkbenchTimings();
    private UIElement page;
    private AssemblyViewport viewport;
    private Chooser chooser;
    private final List<Label> labels=new ArrayList<>();
    private final List<UIElement> controls=new ArrayList<>();
    private List<SlotLayout.Anchor> lastAnchors=List.of();
    private Map<List<String>,SlotLayout.Point> cardPositions=Map.of();
    private static final float cardSize=WorkbenchSlotMetrics.CARD;
    private static final float toggleSize=WorkbenchSlotMetrics.TOGGLE;
    private final SlotLayout.State slotLayout=new SlotLayout.State();
    private boolean lastBusy;
    private List<String> hoveredPath;
    private InventoryTextElement stats,delta,status,previewNotice;
    private InventoryButtonElement undo,reset;
    private InventoryButtonElement materialMode,modeButton;
    private boolean whiteModel;
    private AssemblyNode lastTree;
    private int oldWidth=-1,oldHeight=-1;
    private boolean backgroundPress,dragged;
    private double pressX,pressY;
    private record Label(List<String> path,AttachmentCard button,SlotToggle toggle) {}
    public WorkbenchScreen(WorkbenchAccess host,Map<String,ModelGeometry> geometry,Function<String,String> names,String title) {
        this(host,geometry,AssemblyMaterials.white(),names,id->null,title,null,null);
    }
    public WorkbenchScreen(WorkbenchAccess host,Map<String,ModelGeometry> geometry,AssemblyMaterials materials,Function<String,String> names,String title) {
        this(host,geometry,materials,names,id->null,title,null,null);
    }
    public WorkbenchScreen(WorkbenchAccess host,Map<String,ModelGeometry> geometry,AssemblyMaterials materials,Function<String,String> names,Function<String,ResourceLocation> images,String title) {
        this(host,geometry,materials,names,images,title,null,null);
    }
    public WorkbenchScreen(WorkbenchAccess host,Map<String,ModelGeometry> geometry,AssemblyMaterials materials,Function<String,String> names,Function<String,ResourceLocation> images,String title,Runnable modeAction) {
        this(host,geometry,materials,names,images,title,modeAction,null);
    }
    public WorkbenchScreen(WorkbenchAccess host,Map<String,ModelGeometry> geometry,AssemblyMaterials materials,Function<String,String> names,Function<String,ResourceLocation> images,String title,Runnable modeAction,WorkbenchModelBackend modelBackend) {
        super(createUi(),Component.literal(title));this.host=Objects.requireNonNull(host);this.geometry=Map.copyOf(geometry);this.modeAction=modeAction;
        this.materials=Objects.requireNonNull(materials);
        partName=Objects.requireNonNull(names);partImage=Objects.requireNonNull(images);this.modelBackend=modelBackend;titleText=title;root=(InventoryRootElement)modularUI.ui.rootElement;
    }
    private static ModularUI createUi() {
        var root=new InventoryRootElement();root.layout(l->l.positionType(TaffyPosition.RELATIVE));
        var ui=ModularUI.of(UI.of(root,s->Size.of(Math.max(1,s.getWidth()),Math.max(1,s.getHeight())))).shouldCloseOnEsc(true);
        ui.getTaffyTree().disableRounding();return ui;
    }
    private static String tr(String key,Object...args) { return Component.translatable("weapon_assembly_ui."+key,args).getString(); }
    private String slotName(String slot) {
        var key="weapon_assembly_ui.slot."+slot;
        if(net.minecraft.client.resources.language.I18n.exists(key))return Component.translatable(key).getString();
        key="weapon_assembly_ui.slot."+slot.replaceFirst("_\\d+$", "");
        return net.minecraft.client.resources.language.I18n.exists(key)?Component.translatable(key).getString():tr("attachment_slot");
    }
    private String pathName(List<String> path) { return path.isEmpty()?tr("root"):slotName(path.getLast()); }
    private void build() {
        boolean reopen=chooser!=null;
        host.clearPreview();
        root.clearAllChildren();controls.clear();labels.clear();chooser=null;hoveredPath=null;lastAnchors=List.of();cardPositions=Map.of();
        design=new UiDesign(Math.min(width/1280f,height/800f));
        canvasWidth=width/design.scale();canvasHeight=height/design.scale();
        double right=canvasWidth-1280,bottom=canvasHeight-800;
        page=new UIElement();page.layout(l->l.widthPercent(100).heightPercent(100));
        var backdrop=new WorkbenchBackdrop();design.place(backdrop,0,0,canvasWidth,canvasHeight);page.addChild(backdrop);
        if(viewport==null)viewport=new AssemblyViewport(()->host.preview().filter(p->p.plan().success()).map(p->p.plan().after()).orElse(host.tree()),host::tree,geometry,materials,modelBackend);
        viewport.whiteModel(whiteModel);
        design.place(viewport,8,8,canvasWidth-16,canvasHeight-16);viewport.framing(design.px(92),design.px(192));page.addChild(viewport);
        var leaders=new UIElement() {
            @Override public void drawBackgroundAdditional(GUIContext context) {
                super.drawBackgroundAdditional(context);drawLeaders(context.graphics);
            }
        };
        leaders.setAllowHitTest(false);design.place(leaders,0,100,canvasWidth,canvasHeight-300);page.addChild(leaders);
        text(page,titleText,20,UiDesign.TEXT,28,24,650,32);
        previewNotice=text(page,"",18,InventoryUiTheme.WINDOW_SELECTED_BORDER,460,24,450,35);
        if(host.temporaryPreset())text(page,tr("temporary_preset"),16,InventoryUiTheme.WINDOW_SELECTED_BORDER,690,65,560,32).setId("assembly-preset-notice");
        if(modeAction!=null){
            modeButton=button(page,tr(host.temporaryPreset()?"exit_preset":"edit_preset"),!host.busy(),()->{if(!host.busy())modeAction.run();},1012+right,24,220,36);
            modeButton.setId("assembly-preset-mode");
        }
        var panel=design.surface(UiDesign.SURFACE);design.place(panel,12,638+bottom,350,150);page.addChild(panel);
        stats=text(panel,"",18,UiDesign.TEXT,14,14,310,78);
        delta=text(panel,"",16,UiDesign.MUTED,14,100,315,30);
        status=text(page,"",16,UiDesign.MUTED,400,614+bottom,canvasWidth-412,64);
        materialMode=button(page,tr("white_model"),true,()->{whiteModel=!whiteModel;viewport.whiteModel(whiteModel);refreshReadout();},400,723+bottom,180,40);
        materialMode.setId("assembly-material-mode");
        button(page,tr("camera_reset"),true,()->{viewport.resetCamera();slotLayout.reflow();},758+right,723+bottom,150,40).setId("assembly-camera-reset");
        undo=button(page,tr("undo"),host.canUndo(),()->{host.undo();reconcile(true);},920+right,723+bottom,130,40);undo.setId("assembly-undo");
        reset=button(page,tr("reset"),host.canReset(),()->{host.reset();reconcile(true);},1062+right,723+bottom,170,40);reset.setId("assembly-reset");
        oldWidth=width;oldHeight=height;lastTree=host.tree();syncLabels();refreshReadout();root.addSurface(page,InventoryRootElement.Layer.PAGE);
        if(reopen&&!host.selectedPath().isEmpty()){chooser=new Chooser();root.addSurface(chooser,InventoryRootElement.Layer.MENU);}
    }
    private InventoryTextElement text(UIElement parent,String value,int size,int color,double x,double y,double w,double h) {
        var t=design.text(value,size,color);design.place(t,x,y,w,h);t.setAllowHitTest(false);parent.addChild(t);return t;
    }
    private InventoryButtonElement button(UIElement parent,String label,boolean enabled,Runnable action,double x,double y,double w,double h) {
        var b=new InventoryButtonElement(design,label,enabled,e->action.run());design.place(b,x,y,w,h);parent.addChild(b);if(parent==page)controls.add(b);return b;
    }
    @Override public void init() {
        // Build the complete tree before LDLib2 performs its first screen layout. Building from
        // render() leaves the initial native layout empty, so newly visible cards can expose their
        // zero-position layout while the first real projection is being applied.
        build();
        super.init();
        viewport.beginFrame();
        positionLabels(-1,-1);
    }
    private void syncLabels() {
        var slots=host.visibleSlots();
        var paths=new HashSet<List<String>>();
        for(var slot:slots)paths.add(slot.path());
        for(var iterator=labels.iterator();iterator.hasNext();) {
            var label=iterator.next();
            if(paths.contains(label.path()))continue;
            controls.remove(label.button());controls.remove(label.toggle());
            label.button().removeSelf();label.toggle().removeSelf();iterator.remove();
        }
        if(hoveredPath!=null&&!paths.contains(hoveredPath))hoveredPath=null;
        for(var slot:slots) {
            String name=slot.installed().map(n->partName.apply(n.definitionId())).orElse(slotName(slot.definition().id()));
            ResourceLocation image=slot.installed().map(n->partImage.apply(n.definitionId())).orElse(null);
            var existing=labels.stream().filter(l->l.path().equals(slot.path())).findFirst();
            if(existing.isPresent()) {
                existing.get().button().content(name,image);
                existing.get().button().setActive(!host.busy());existing.get().toggle().setActive(!host.busy());
                continue;
            }
            var b=new AttachmentCard(design,name,image,!host.busy(),()->{});
            b.setVisible(false);
            design.place(b,0,0,cardSize,cardSize);page.addChild(b);controls.add(b);
            b.setId("assembly-slot-"+String.join("-",slot.path()));
            b.addEventListener(UIEvents.MOUSE_ENTER,e->{hoveredPath=slot.path();refreshReadout();});
            b.addEventListener(UIEvents.MOUSE_LEAVE,e->{if(slot.path().equals(hoveredPath))hoveredPath=null;refreshReadout();});
            var toggle=new SlotToggle(slot.path());
            design.place(toggle,0,0,toggleSize,cardSize);toggle.setVisible(false);page.addChild(toggle);controls.add(toggle);
            toggle.setId("assembly-toggle-"+String.join("-",slot.path()));
            labels.add(new Label(slot.path(),b,toggle));
        }
    }
    public void select(List<String> path) { if(!host.busy()&&host.select(path)){closeChooserOnly();chooser=new Chooser();root.addSurface(chooser,InventoryRootElement.Layer.MENU);} }
    private void closeChooserOnly() { if(chooser!=null){chooser.removeSelf();chooser=null;} }
    private void closeChooser() { closeChooserOnly();host.clearPreview();host.select(List.of());refreshReadout(); }
    private void reconcile(boolean force) {
        boolean treeChanged=lastTree!=host.tree();
        if(treeChanged||lastBusy!=host.busy()||force) {
            if(treeChanged||force)slotLayout.reflow();
            lastTree=host.tree();lastBusy=host.busy();syncLabels();
            if(chooser!=null) {
                if(!chooser.path.equals(host.selectedPath())||labels.stream().noneMatch(l->l.path().equals(host.selectedPath())))closeChooserOnly();
                else chooser.refresh();
            }
        }
        refreshReadout();
    }
    private void refreshReadout() {
        viewport.selection(hoveredPath!=null&&chooser==null?hoveredPath:host.selectedPath().isEmpty()?null:host.selectedPath(),host.preview().isPresent());
        var candidate=host.preview();
        if(host.statsExplanation().isPresent()){stats.text(host.statsExplanation().orElseThrow());delta.text("");}
        else {
        var current=host.stats();
        stats.text(tr("stats",String.format(Locale.ROOT,"%.2f",current.weightKg()),String.format(Locale.ROOT,"%.1f",current.ergonomics()),String.format(Locale.ROOT,"%.1f",current.recoilVertical())));
        delta.text(candidate.flatMap(AssemblySession.Preview::stats).map(v->tr("delta",signed(v.weightKg()-current.weightKg()),signed(v.ergonomics()-current.ergonomics()),signed(v.recoilVertical()-current.recoilVertical()))).orElse(""));
        }
        previewNotice.text(candidate.map(p->tr(p.plan().success()?"preview":"preview_rejected")).orElse(""));
        var errors=candidate.filter(p->!p.plan().success()).map(p->p.plan().errors()).orElse(host.feedback());
        if(host.busy())status.text(tr("pending"));
        else if(!errors.isEmpty())status.text(tr("rejected")+": "+issue(errors.getFirst()));
        else if(!host.validation().complete())status.text(tr("incomplete")+": "+pathName(host.validation().missingRequired().getFirst().path()));
        else if(!host.temporaryPreset()&&!host.detached().isEmpty())status.text(tr("detached",host.detached().size()));
        else status.text("");
        undo.setActive(host.canUndo());reset.setActive(host.canReset());
        if(modeButton!=null)modeButton.setActive(!host.busy());
        materialMode.setText(Component.literal(whiteModel?tr("show_materials"):tr("white_model")));
    }
    private String issue(AssemblyEngine.Issue issue) { return tr("error."+issue.code().name())+" · "+pathName(issue.path()); }
    private static String signed(double v) { return String.format(Locale.ROOT,"%+.2f",v); }
    private void positionLabels(int mouseX,int mouseY) {
        if(!viewport.projectionReady())return;
        var anchors=new ArrayList<SlotLayout.Anchor>();
        for(var label:labels) {
            var path=label.path();
            var p=viewport.projectSlot(host.tree(),path.subList(0,path.size()-1),path.getLast());
            label.button().selected(path.equals(host.selectedPath()));
            label.button().setVisible(p.isPresent());
            p.ifPresent(v->anchors.add(new SlotLayout.Anchor(path,new SlotLayout.Point(v.x()/design.scale(),v.y()/design.scale()))));
        }
        var previousPositions=cardPositions;
        cardPositions=slotLayout.update(anchors,new SlotLayout.Bounds(12,104,canvasWidth-24,canvasHeight-316),cardSize+toggleSize,cardSize);
        lastAnchors=List.copyOf(anchors);
        for(var label:labels) {
            var p=cardPositions.get(label.path());if(p==null)continue;
            if(!p.equals(previousPositions.get(label.path()))||label.button().getSizeWidth()==0)
                label.button().layout(l->l.left(design.px(p.x())).top(design.px(p.y())));
        }
        for(var label:labels) {
            var p=cardPositions.get(label.path());if(p==null){label.toggle().setVisible(false);continue;}
            float x=design.px(p.x()+cardSize),y=design.px(p.y());
            // The card and adjoining bar share one continuous hover region.
            boolean over=UIElement.isMouseOverRect(design.px(p.x()),y,design.px(cardSize+toggleSize),design.px(cardSize),mouseX,mouseY)
                    &&(chooser==null||!chooser.contains(mouseX,mouseY));
            label.toggle().setVisible(label.button().isVisible()&&(over||(chooser!=null&&label.path().equals(host.selectedPath()))));
            if(!p.equals(previousPositions.get(label.path()))||label.toggle().getSizeWidth()==0)
                label.toggle().layout(l->l.left(x).top(y));
        }
        if(chooser!=null) {
            var p=cardPositions.get(host.selectedPath());
            float x=p==null?design.px(12):design.px(p.x());
            float y=p==null?design.px(112):design.px(p.y()+cardSize+8);
            if(y+design.px(chooser.panelHeight)>height-design.px(12)&&p!=null) {
                y=design.px(p.y()-chooser.panelHeight-8);
                if(y<design.px(12)) {
                    // A tall menu must not cover the same bar needed to collapse it.
                    y=design.px(p.y());x=design.px(p.x()+cardSize+toggleSize+8);
                    if(x+design.px(chooser.panelWidth)>width-design.px(12))x=design.px(p.x()-chooser.panelWidth-8);
                }
            }
            x=Math.clamp(x,design.px(12),Math.max(design.px(12),width-design.px(chooser.panelWidth+12)));
            y=Math.clamp(y,design.px(12),Math.max(design.px(12),height-design.px(chooser.panelHeight+12)));
            float fx=x,fy=y;chooser.layout(l->l.left(fx).top(fy));
        }
    }
    private void drawLeaders(GuiGraphics g) {
        for(var anchor:lastAnchors) {
            var p=cardPositions.get(anchor.path());if(p==null)continue;
            double ax=anchor.point().x(),ay=anchor.point().y();
            // Connect to the closest card edge instead of drawing through its picture.
            double bx=Math.clamp(ax,p.x(),p.x()+cardSize),by=Math.clamp(ay,p.y(),p.y()+cardSize);
            int color=anchor.path().equals(host.selectedPath())?InventoryUiTheme.WINDOW_SELECTED_BORDER:0x88798589;
            float x1=design.px(ax),y1=design.px(ay),x2=design.px(bx),y2=design.px(by);
            double length=Math.hypot(x2-x1,y2-y1);if(length<1)continue;
            float half=Math.max(.4f,design.px(.6));
            g.pose().pushPose();
            try {
                g.pose().translate(x1,y1,0);
                g.pose().mulPose(com.mojang.math.Axis.ZP.rotation((float)Math.atan2(y2-y1,x2-x1)));
                InventorySurfaceTexture.fill(g,0,-half,(float)length,half,color);
            } finally {g.pose().popPose();}
            InventorySurfaceTexture.fill(g,x1-design.px(2),y1-design.px(2),x1+design.px(2),y1+design.px(2),color);
        }
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void tick() {super.tick();if(width!=oldWidth||height!=oldHeight)build();else reconcile(false);}
    @Override public void render(GuiGraphics g,int mx,int my,float partial) {
        if(width!=oldWidth||height!=oldHeight)build();
        viewport.beginFrame();
        long started=System.nanoTime();positionLabels(mx,my);long layoutTime=System.nanoTime()-started;
        super.render(g,mx,my,partial);timings.record(layoutTime,viewport.lastRenderNanos,viewport.lastTriangleCount);
    }
    private boolean overControl(double x,double y) {
        if(chooser!=null&&chooser.contains(x,y))return true;
        return controls.stream().anyMatch(c->c.isVisible()&&UIElement.isMouseOverRect(c.getPositionX(),c.getPositionY(),c.getSizeWidth(),c.getSizeHeight(),x,y));
    }
    @Override public void mouseMoved(double x,double y) {
        if(viewport!=null)positionLabels((int)x,(int)y);
        super.mouseMoved(x,y);
    }
    @Override public boolean mouseClicked(double x,double y,int b) {
        modularUI.refreshHoveredElementAtScreen((float)x,(float)y);
        backgroundPress=b==0&&!overControl(x,y);dragged=false;pressX=x;pressY=y;
        return backgroundPress||super.mouseClicked(x,y,b);
    }
    @Override public boolean mouseDragged(double x,double y,int b,double dx,double dy) {
        if(backgroundPress&&b==0){if(Math.hypot(x-pressX,y-pressY)>design.px(6))dragged=true;if(dragged)viewport.rotate(dx/design.scale(),dy/design.scale());return true;}
        return super.mouseDragged(x,y,b,dx,dy);
    }
    @Override public boolean mouseReleased(double x,double y,int b) {
        if(backgroundPress&&b==0){backgroundPress=false;if(dragged)slotLayout.reflow();else if(Math.hypot(x-pressX,y-pressY)<=design.px(6))closeChooser();return true;}
        return super.mouseReleased(x,y,b);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical) {
        // Tarkov-style assembly canvas has no user zoom or pan; only the candidate list scrolls.
        if(chooser!=null&&chooser.scroll.isVisible()&&UIElement.isMouseOverRect(chooser.scroll.getPositionX(),chooser.scroll.getPositionY(),
                chooser.scroll.getSizeWidth(),chooser.scroll.getSizeHeight(),x,y))return super.mouseScrolled(x,y,horizontal,vertical);
        return false;
    }
    @Override public boolean keyPressed(int key,int scan,int mods) {
        if(key==256&&chooser!=null){closeChooser();return true;}return super.keyPressed(key,scan,mods);
    }
    @Override public void removed(){host.clearPreview();super.removed();}
    public WorkbenchAccess access(){return host;}
    public AssemblyViewport viewport(){return viewport;}
    public boolean chooserOpen(){return chooser!=null;}
    private final class SlotToggle extends com.lowdragmc.lowdraglib2.gui.ui.elements.Button {
        private final List<String> path;
        SlotToggle(List<String> path) {
            this.path=path;noText();setActive(!host.busy());
            buttonStyle(s->s.baseTexture(com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture.EMPTY)
                    .hoverTexture(com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture.EMPTY)
                    .pressedTexture(com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture.EMPTY));
            setOnClick(e->{if(chooser!=null&&path.equals(host.selectedPath()))closeChooser();else WorkbenchScreen.this.select(path);});
        }
        @Override public void drawBackgroundAdditional(GUIContext context) {
            super.drawBackgroundAdditional(context);
            var g=context.graphics;
            float x=getPositionX(),y=getPositionY(),w=getSizeWidth(),h=getSizeHeight();
            InventorySurfaceTexture.fill(g,x,y,x+w,y+h,0xffaab5b8);
            InventorySurfaceTexture.border(g,x,y,x+w,y+h,design.px(1),0xffd4dcde);
            boolean expanded=chooser!=null&&path.equals(host.selectedPath());
            for(int row=0;row<5;row++) {
                float half=design.px(expanded?row+1:5-row);
                float top=y+h/2+design.px(row-2);
                InventorySurfaceTexture.fill(g,x+w/2-half,top,x+w/2+half,top+design.px(1),0xff142126);
            }
        }
    }
    private final class Chooser extends InventoryWindowElement {
        static final int CELL=WorkbenchSlotMetrics.CHOOSER_CELL,GAP=WorkbenchSlotMetrics.CHOOSER_GAP;
        int panelWidth=ChooserLayout.forCount(0).width();
        int panelHeight=ChooserLayout.forCount(0).height();
        private final List<String> path=List.copyOf(host.selectedPath());
        private final AttachmentCard empty;
        private final InventoryScrollView scroll;
        private final UIElement content;
        private final Map<UUID,AttachmentCard> cards=new LinkedHashMap<>();
        Chooser() {
            super(design,Kind.MENU);setId("assembly-chooser");
            layout(l->l.width(design.px(panelWidth)).height(design.px(panelHeight)));
            style(s->s.backgroundTexture(new InventorySurfaceTexture(design,0xf0080c0f,0xff536068,false)));
            empty=new AttachmentCard(design,"",null,!host.busy(),()->{
                if(host.busy())return;
                if(host.nodeAt(host.selectedPath()).isPresent())host.remove();else host.clearPreview();
                closeChooser();reconcile(true);
            });
            empty.setId("assembly-remove");
            empty.style(s->s.tooltips(Component.literal(tr("remove"))));
            empty.addEventListener(UIEvents.MOUSE_ENTER,e->{host.clearPreview();refreshReadout();});
            design.place(empty,ChooserLayout.PADDING,ChooserLayout.PADDING,CELL,CELL);addChild(empty);
            scroll=new InventoryScrollView(design);scroll.setId("assembly-candidates");
            scroll.viewPort.layout(l->l.paddingAll(0));
            scroll.viewPort.style(s->s.backgroundTexture(com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture.EMPTY));
            scroll.scrollerStyle(s->s.mode(com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode.VERTICAL).verticalScrollDisplay(com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay.AUTO).adaptiveWidth(false).adaptiveHeight(false));
            InventoryScrollView.sizeVerticalScroller(scroll.verticalScroller,design);InventoryScrollView.styleScroller(scroll.verticalScroller);addChild(scroll);
            content=new UIElement();scroll.addScrollViewChild(content);
            refresh();
        }
        void refresh() {
            empty.setActive(!host.busy());empty.selected(host.nodeAt(host.selectedPath()).isEmpty());
            // Pending hosts intentionally expose no candidates. Keep the last confirmed cards and viewport.
            if(host.busy()) {
                cards.values().forEach(c->c.setActive(false));return;
            }
            var candidates=host.candidates();
            var ids=new HashSet<UUID>();for(var candidate:candidates)ids.add(candidate.instanceId());
            for(var it=cards.entrySet().iterator();it.hasNext();) {
                var entry=it.next();if(ids.contains(entry.getKey()))continue;
                entry.getValue().removeSelf();it.remove();
            }
            var metrics=ChooserLayout.forCount(candidates.size());
            panelWidth=metrics.width();panelHeight=metrics.height();
            layout(l->l.width(design.px(panelWidth)).height(design.px(panelHeight)));
            scroll.setVisible(!candidates.isEmpty());
            int scrollWidth=metrics.contentWidth()+(metrics.scrollable()?ChooserLayout.SCROLLER_WIDTH:0);
            int scrollHeight=metrics.visibleRows()*CELL+Math.max(0,metrics.visibleRows()-1)*GAP;
            design.place(scroll,ChooserLayout.PADDING,metrics.candidateTop(),scrollWidth,scrollHeight);
            content.layout(l->l.width(design.px(metrics.contentWidth())).height(design.px(metrics.contentHeight())));
            int columns=metrics.columns();
            int i=0;
            for(var candidate:candidates) {
                var b=cards.get(candidate.instanceId());
                if(b==null) {
                    b=new AttachmentCard(design,partName.apply(candidate.definitionId()),partImage.apply(candidate.definitionId()),true,()->{
                        if(!host.busy()){var result=host.install(candidate.instanceId());if(result.success())closeChooser();reconcile(true);}
                    });
                    content.addChild(b);cards.put(candidate.instanceId(),b);
                    b.setId("assembly-candidate-"+candidate.instanceId());
                    Runnable preview=()->{if(!host.busy()){host.preview(candidate.instanceId());refreshReadout();}};
                    b.addEventListener(UIEvents.MOUSE_ENTER,e->preview.run());b.addEventListener(UIEvents.FOCUS,e->preview.run());
                    b.addEventListener(UIEvents.MOUSE_LEAVE,e->{host.clearPreview();refreshReadout();});b.addEventListener(UIEvents.BLUR,e->{host.clearPreview();refreshReadout();});
                }
                b.setActive(true);
                b.content(partName.apply(candidate.definitionId()),partImage.apply(candidate.definitionId()));
                design.place(b,(i%columns)*(CELL+GAP),(i/columns)*(CELL+GAP),CELL,CELL);i++;
            }
        }
    }
}
