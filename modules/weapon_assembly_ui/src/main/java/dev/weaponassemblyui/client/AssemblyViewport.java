package dev.weaponassemblyui.client;

import dev.firearms.presentation.*;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.firearms.assembly.AssemblyNode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import java.util.*;
import java.util.function.Supplier;
import static dev.firearms.presentation.ModelGeometry.*;

/** Orthographic 3D mesh viewport. The host owns input arbitration and standard slot buttons. */
public final class AssemblyViewport extends UIElement {
    public record ScreenPoint(float x,float y) {}
    private final Supplier<AssemblyNode> source;
    private final Supplier<AssemblyNode> framingSource;
    private final Map<String,ModelGeometry> models;
    private final AssemblyMaterials materials;
    private final WorkbenchModelBackend modelBackend;
    private boolean whiteModel;
    // Authored weapons point along +Z; -90 degrees presents a level, muzzle-left side view.
    private final WorkbenchCamera camera=new WorkbenchCamera();
    private float frameTop,frameBottom;
    private double cy,sy,cp,sp;
    private double rotationYaw=Double.NaN,rotationPitch=Double.NaN;
    private final Map<Triangle,PreparedTriangle> prepared=new IdentityHashMap<>();
    private long lightingVersion;
    private static final class PreparedTriangle {
        final double nx,ny,nz,exponent;
        final int whiteLight;
        final AssemblyMaterials.Material material;
        long lightingVersion=-1;
        int red,green,blue;
        PreparedTriangle(double nx,double ny,double nz,int whiteLight,double exponent,AssemblyMaterials.Material material) {
            this.nx=nx;this.ny=ny;this.nz=nz;this.whiteLight=whiteLight;this.exponent=exponent;this.material=material;
        }
    }
    public long lastRenderNanos;
    public int lastTriangleCount;
    private List<String> selectedPath=List.of();
    private boolean highlighted,preview;
    private Point center=new Point(0,0,0);
    private float fitScale=1;
    private AssemblyNode fittedTree;
    private AssemblyFrameState frameState;
    private WorkbenchViewportFrame activeFrame;
    private static final Point ZERO=new Point(0,0,0);
    // Outward winding; both face directions are emitted because GUI Y points down.
    private static final int[][] FACES={{0,3,2,1},{4,5,6,7},{0,4,7,3},{1,2,6,5},{0,1,5,4},{3,7,6,2}};
    public AssemblyViewport(Supplier<AssemblyNode> source,Map<String,ModelGeometry> models) {
        this(source,source,models,AssemblyMaterials.white(),null);
    }
    public AssemblyViewport(Supplier<AssemblyNode> source,Map<String,ModelGeometry> models,AssemblyMaterials materials) {
        this(source,source,models,materials,null);
    }
    public AssemblyViewport(Supplier<AssemblyNode> source,Map<String,ModelGeometry> models,AssemblyMaterials materials,WorkbenchModelBackend modelBackend) {
        this(source,source,models,materials,modelBackend);
    }
    public AssemblyViewport(Supplier<AssemblyNode> source,Supplier<AssemblyNode> framingSource,Map<String,ModelGeometry> models,AssemblyMaterials materials,WorkbenchModelBackend modelBackend) {
        this.source=Objects.requireNonNull(source);this.framingSource=Objects.requireNonNull(framingSource);this.models=Map.copyOf(models);
        this.materials=Objects.requireNonNull(materials);
        this.modelBackend=modelBackend;
        this.materials.all().stream().map(AssemblyMaterials.Material::texture).filter(v->!v.isEmpty()).distinct()
                .map(net.minecraft.resources.ResourceLocation::parse).forEach(AssemblyTextureQuality::prepare);
        fitAssembly(framingSource.get(),true);
        for(var entry:this.models.entrySet())for(var mesh:entry.getValue().meshes())for(var triangle:mesh.triangles()) {
            var a=triangle.vertices().get(1).subtract(triangle.vertices().get(0));
            var b=triangle.vertices().get(2).subtract(triangle.vertices().get(0));
            double nx=a.y()*b.z()-a.z()*b.y(),ny=a.z()*b.x()-a.x()*b.z(),nz=a.x()*b.y()-a.y()*b.x();
            double length=Math.sqrt(nx*nx+ny*ny+nz*nz);
            if(length>0){nx/=length;ny/=length;nz/=length;}
            int light=Math.clamp((int)(255*(.68+.32*Math.abs(nx*.3+ny*.85+nz*.43))),0,255);
            var material=materials.resolve(entry.getKey(),triangle.region());
            prepared.put(triangle,new PreparedTriangle(nx,ny,nz,light,4+124*Math.pow(1-material.roughness(),2),material));
        }
        setId("assembly-viewport");setAllowHitTest(false);
    }
    /** The path identifies a physical occurrence, so identical definitions do not share highlighting. */
    public void selection(List<String> path,boolean preview) {
        highlighted=path!=null;selectedPath=path==null?List.of():List.copyOf(path);this.preview=preview;
    }
    public void rotate(double dx,double dy) { camera.rotate(dx,dy); }
    public void framing(float top,float bottom) {
        frameTop=top;frameBottom=bottom;
    }
    public void resetCamera() { camera.reset();fittedTree=null;fitAssembly(framingSource.get(),true); }
    private float frameCenterX(){return getPositionX()+getSizeWidth()/2;}
    private float frameCenterY(){return getPositionY()+frameTop+(getSizeHeight()-frameTop-frameBottom)/2;}
    private void rotation() {
        if(rotationYaw==camera.yaw&&rotationPitch==camera.pitch)return;
        rotationYaw=camera.yaw;rotationPitch=camera.pitch;lightingVersion++;
        cy=Math.cos(camera.yaw);sy=Math.sin(camera.yaw);cp=Math.cos(camera.pitch);sp=Math.sin(camera.pitch);
    }
    public void whiteModel(boolean value) { whiteModel=value; }
    public boolean whiteModel() { return whiteModel; }
    private void fitAssembly(AssemblyNode root,boolean reset) {
        if(root==fittedTree)return;
        fittedTree=root;var frame=AssemblyFraming.fit(root,models);
        if(frameState==null)frameState=new AssemblyFrameState(frame);
        else if(reset)frameState.reset(frame);
        else frameState.accept(frame);
    }
    public Optional<ScreenPoint> projectSlot(List<String> ownerPath,String slot) {
        return projectSlot(source.get(),ownerPath,slot);
    }
    public boolean projectionReady() {
        return getSizeWidth()>0 && getSizeHeight()>0 && Float.isFinite(getSizeWidth()) && Float.isFinite(getSizeHeight());
    }
    /** Slot UI can stay anchored to the committed assembly while a replacement is previewed. */
    public Optional<ScreenPoint> projectSlot(AssemblyNode root,List<String> ownerPath,String slot) {
        if(!projectionReady())return Optional.empty();
        AssemblyNode node=root;
        var origin=ModelGeometry.origin(root,models,ownerPath);
        if(origin.isEmpty())return Optional.empty();
        for(String path:ownerPath)node=node.children().get(path);
        var geometry=models.get(node.definitionId());
        if(!geometry.slots().containsKey(slot))return Optional.empty();
        Point projected=project(origin.get().add(geometry.slots().get(slot)));
        return Optional.of(new ScreenPoint(projected.x(),projected.y()));
    }
    private Point project(Point p) {
        var projected=frame().project(p);
        return new Point(projected.x(),projected.y(),20+projected.depth()*.2f);
    }
    /** Freezes one camera snapshot for model geometry, slot projection and leader lines. */
    public void beginFrame() {
        fitAssembly(framingSource.get(),false);
        var stable=frameState.current();center=stable.center();fitScale=stable.fitScale();
        rotation();
        double scale=Math.min(getSizeWidth()/24,(getSizeHeight()-frameTop-frameBottom)/8);
        activeFrame=new WorkbenchViewportFrame(center,frameCenterX(),frameCenterY(),fitScale,scale,camera.yaw,camera.pitch);
    }
    private WorkbenchViewportFrame frame() {
        if(activeFrame==null)beginFrame();
        return activeFrame;
    }
    @Override public void drawBackgroundAdditional(GUIContext context) {
        super.drawBackgroundAdditional(context);
        var root=source.get();if(root==null||getSizeWidth()<=0||getSizeHeight()<=0)return;
        long started=System.nanoTime();lastTriangleCount=0;var viewportFrame=frame();
        var graphics=context.graphics;graphics.flush();
        graphics.enableScissor((int)getPositionX(),(int)getPositionY(),(int)Math.ceil(getPositionX()+getSizeWidth()),(int)Math.ceil(getPositionY()+getSizeHeight()));
        try {
            RenderSystem.enableDepthTest();RenderSystem.depthMask(true);
            if(modelBackend!=null&&!whiteModel)modelBackend.render(context,viewportFrame);
            else drawNode(context,root,ZERO,List.of());
            graphics.flush();
        } finally {
            // Keep local mesh occlusion, then release depth so ordinary controls and menus can cover it.
            RenderSystem.clear(256,Minecraft.ON_OSX);
            graphics.disableScissor();
            lastRenderNanos=System.nanoTime()-started;
        }
    }
    private void drawNode(GUIContext context,AssemblyNode node,Point origin,List<String> path) {
        if(path.size()>8)return;
        var geometry=models.get(node.definitionId());if(geometry==null)return;
        Point local=origin.subtract(geometry.attachmentOrigin());
        for(var box:geometry.boxes()) drawBox(context,box,local,highlighted&&path.equals(selectedPath));
        for(var mesh:geometry.meshes())for(var triangle:mesh.triangles())drawTriangle(context,triangle,local,highlighted&&path.equals(selectedPath));
        node.children().forEach((slot,child)-> {
            var anchor=geometry.slots().get(slot);
            if(anchor!=null) { var childPath=new ArrayList<>(path);childPath.add(slot);drawNode(context,child,local.add(anchor),childPath); }
        });
    }
    private void drawTriangle(GUIContext context,Triangle triangle,Point origin,boolean selected) {
        lastTriangleCount++;
        var data=prepared.get(triangle);var material=whiteModel?AssemblyMaterials.WHITE:data.material;
        var points=triangle.vertices();
        int red=data.whiteLight,green=red,blue=red;
        if(material!=AssemblyMaterials.WHITE) {
            if(data.lightingVersion!=lightingVersion) {
                double rx=data.nx*cy+data.nz*sy,rz=-data.nx*sy+data.nz*cy;
                double ry=data.ny*cp-rz*sp;rz=data.ny*sp+rz*cp;
                if(rz<0){rx=-rx;ry=-ry;rz=-rz;}
                double diffuse=.52+.48*Math.max(0,-.3*rx+.6*ry+.742*rz);
                double highlight=Math.pow(Math.max(0,-.161*rx+.321*ry+.933*rz),data.exponent)*material.specular()*.6;
                red=channel((material.color()>>16)&255,diffuse,highlight);
                green=channel((material.color()>>8)&255,diffuse,highlight);blue=channel(material.color()&255,diffuse,highlight);
                data.red=red;data.green=green;data.blue=blue;data.lightingVersion=lightingVersion;
            }
            red=data.red;green=data.green;blue=data.blue;
        }
        if(selected){if(preview){red=Math.min(255,red+45);green=(int)(green*.85);blue=(int)(blue*.55);}else{red=(int)(red*.65);green=Math.min(255,green+30);blue=Math.min(255,blue+55);}}
        var vertices=context.graphics.bufferSource().getBuffer(AssemblyMeshRenderTypes.forTexture(material.texture()));var pose=context.graphics.pose().last().pose();
        for(int i=0;i<points.size();i++) {
            var p=points.get(i);
            var projected=frame().project(p.add(origin));
            var vertex=vertices.addVertex(pose,projected.x(),projected.y(),20+projected.depth()*.2f);
            if(!material.texture().isEmpty())vertex.setUv(triangle.uv().get(i).u()*material.textureScale(),triangle.uv().get(i).v()*material.textureScale());
            vertex.setColor(red,green,blue,255);
        }
    }
    private static int channel(int value,double diffuse,double highlight) { return Math.clamp((int)(value*diffuse+255*highlight),0,255); }
    private void drawBox(GUIContext context,Box box,Point origin,boolean selected) {
        Point a=box.min(),b=box.max();
        Point[] points={new Point(a.x(),a.y(),a.z()),new Point(b.x(),a.y(),a.z()),new Point(b.x(),b.y(),a.z()),new Point(a.x(),b.y(),a.z()),
            new Point(a.x(),a.y(),b.z()),new Point(b.x(),a.y(),b.z()),new Point(b.x(),b.y(),b.z()),new Point(a.x(),b.y(),b.z())};
        for(int i=0;i<points.length;i++)points[i]=project(points[i].add(origin));
        var vertices=context.graphics.bufferSource().getBuffer(RenderType.gui());var pose=context.graphics.pose().last().pose();
        for(int f=0;f<FACES.length;f++) {
            int brightness=114+(f%3)*17;
            int color=selected
                ? (preview ? 0xff000000|((brightness+62)<<16)|((brightness+33)<<8)|(brightness-28)
                           : 0xff000000|((brightness-36)<<16)|((brightness+39)<<8)|(brightness+65))
                : 0xff000000|(brightness<<16)|((brightness+9)<<8)|(brightness+14);
            for(int i:FACES[f]) {var p=points[i];vertices.addVertex(pose,p.x(),p.y(),p.z()).setColor(color);}
            for(int j=3;j>=0;j--) {var p=points[FACES[f][j]];vertices.addVertex(pose,p.x(),p.y(),p.z()).setColor(color);}
        }
    }
}
