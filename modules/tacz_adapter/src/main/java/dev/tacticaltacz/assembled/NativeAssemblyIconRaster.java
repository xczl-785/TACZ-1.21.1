package dev.tacticaltacz.assembled;

import dev.weaponassembly.api.AssemblyNode;
import dev.weaponmodels.AssemblyMaterials;
import dev.weaponmodels.ModelGeometry;
import dev.weaponmodels.ModelGeometry.Point;
import java.util.*;
import java.util.function.Function;

/** CPU thumbnail baking from the same neutral geometry/materials as the workbench. No game state. */
public final class NativeAssemblyIconRaster {
    public record Texture(int width,int height,int[] argb) {
        public Texture { if(width<1||height<1||argb.length!=width*height)throw new IllegalArgumentException("Invalid texture"); }
        int sample(double u,double v){return argb[Math.floorMod((int)Math.floor(v*height),height)*width+Math.floorMod((int)Math.floor(u*width),width)];}
    }
    private static final int[] CHANNELS={16,8,0};
    private record Face(ModelGeometry.Triangle triangle,Point offset,AssemblyMaterials.Material material){}
    /** Identity, ammunition and animation do not affect the neutral appearance. Slot order is stable. */
    public static String appearanceKey(AssemblyNode node){
        var key=new StringBuilder(node.definitionId()).append('(');
        new TreeMap<>(node.children()).forEach((slot,child)->key.append(slot).append('=').append(appearanceKey(child)).append(';'));
        return key.append(')').toString();
    }
    private static void collect(AssemblyNode root,AssemblyNode node,List<String> path,Map<String,ModelGeometry> models,AssemblyMaterials materials,List<Face> faces){
        var model=Objects.requireNonNull(models.get(node.definitionId()),"Missing icon geometry: "+node.definitionId());
        var offset=ModelGeometry.origin(root,models,path).orElseThrow();
        for(var mesh:model.meshes())for(var triangle:mesh.triangles())faces.add(new Face(triangle,offset,materials.resolve(node.definitionId(),triangle.region())));
        new TreeMap<>(node.children()).forEach((slot,child)->{var next=new ArrayList<>(path);next.add(slot);collect(root,child,next,models,materials,faces);});
    }
    // +Z muzzle projects left; viewing from the opposite side, never mirroring the bitmap.
    private static double horizontal(Point p){return .22*p.x()-.9755*p.z();}
    private static double depth(Point p){return -.9755*p.x()-.22*p.z();}
    public static int[] bake(AssemblyNode root,Map<String,ModelGeometry> models,AssemblyMaterials materials,Function<String,Texture> textures,int size){
        if(size<16||size>1024)throw new IllegalArgumentException("Invalid icon size");
        var faces=new ArrayList<Face>();collect(root,root,List.of(),models,materials,faces);
        int[] pixels=new int[size*size];if(faces.isEmpty())return pixels;
        double minX=Double.POSITIVE_INFINITY,minY=minX,maxX=-minX,maxY=-minX;
        for(var face:faces)for(var local:face.triangle.vertices()){
            var p=local.add(face.offset);double x=horizontal(p),y=-p.y();
            minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);
        }
        double scale=size*.90/Math.max(1e-6,Math.max(maxX-minX,maxY-minY)),cx=(minX+maxX)/2,cy=(minY+maxY)/2;
        double[] zbuffer=new double[pixels.length];Arrays.fill(zbuffer,Double.NEGATIVE_INFINITY);
        var white=new Texture(1,1,new int[]{0xffffffff});
        for(var face:faces){
            var triangle=face.triangle;double[] x=new double[3],y=new double[3],z=new double[3];
            for(int i=0;i<3;i++){var p=triangle.vertices().get(i).add(face.offset);x[i]=(horizontal(p)-cx)*scale+size/2.;y[i]=(-p.y()-cy)*scale+size/2.;z[i]=depth(p);}
            double det=(y[1]-y[2])*(x[0]-x[2])+(x[2]-x[1])*(y[0]-y[2]);if(Math.abs(det)<1e-9)continue;
            int x0=Math.max(0,(int)Math.floor(Math.min(x[0],Math.min(x[1],x[2])))),x1=Math.min(size-1,(int)Math.ceil(Math.max(x[0],Math.max(x[1],x[2]))));
            int y0=Math.max(0,(int)Math.floor(Math.min(y[0],Math.min(y[1],y[2])))),y1=Math.min(size-1,(int)Math.ceil(Math.max(y[0],Math.max(y[1],y[2]))));
            var a=triangle.vertices().get(1).subtract(triangle.vertices().get(0));var b=triangle.vertices().get(2).subtract(triangle.vertices().get(0));
            double nx=a.y()*b.z()-a.z()*b.y(),ny=a.z()*b.x()-a.x()*b.z(),nz=a.x()*b.y()-a.y()*b.x();
            double light=.65+.35*Math.abs((nx*.8+ny*.5+nz*.33)/Math.max(1e-9,Math.sqrt(nx*nx+ny*ny+nz*nz)));
            var material=face.material;var texture=material.texture().isEmpty()?white:textures.apply(material.texture());
            for(int py=y0;py<=y1;py++)for(int px=x0;px<=x1;px++){
                double w0=((y[1]-y[2])*(px+.5-x[2])+(x[2]-x[1])*(py+.5-y[2]))/det;
                double w1=((y[2]-y[0])*(px+.5-x[2])+(x[0]-x[2])*(py+.5-y[2]))/det,w2=1-w0-w1;
                if(w0< -1e-7||w1< -1e-7||w2< -1e-7)continue;
                int index=py*size+px;double distance=w0*z[0]+w1*z[1]+w2*z[2];if(distance<=zbuffer[index])continue;
                var uv=triangle.uv();double u=(w0*uv.get(0).u()+w1*uv.get(1).u()+w2*uv.get(2).u())*material.textureScale(),v=(w0*uv.get(0).v()+w1*uv.get(1).v()+w2*uv.get(2).v())*material.textureScale();
                int texel=texture.sample(u,v);if((texel>>>24)<26)continue;
                int color=0xff000000;for(int shift:CHANNELS)color|=Math.clamp((int)(((texel>>>shift)&255)*((material.color()>>>shift)&255)/255.*light),0,255)<<shift;
                pixels[index]=color;zbuffer[index]=distance;
            }
        }
        return pixels;
    }
    /** Visible GUI pixel bounds relative to the standard 16px icon center. */
    public static float[] bounds(int[] pixels,int size){
        int minX=size,minY=size,maxX=-1,maxY=-1;
        for(int y=0;y<size;y++)for(int x=0;x<size;x++)if((pixels[y*size+x]>>>24)!=0){minX=Math.min(minX,x);minY=Math.min(minY,y);maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);}
        if(maxX<0)return new float[]{-8,-8,8,8};
        return new float[]{16f*minX/size-8,16f*minY/size-8,16f*(maxX+1)/size-8,16f*(maxY+1)/size-8};
    }
    private NativeAssemblyIconRaster(){}
}
