package dev.tacticaltacz.assembled;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.resource.pojo.model.*;
import dev.weaponmodels.*;
import dev.weaponassembly.api.AssemblyNode;
import dev.itemfoundation.api.assembly.AssemblyTrees;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;

/** Data-selected mesh renderer; precompiles geometry once per resource load. */
public final class AssemblyGunModel extends BedrockGunModel {
    private final AssembledWeapon weapon;
    public final AssemblyPresentation presentation;
    private final Map<String,ModelGeometry> models;
    private final AssemblyMaterials materials;
    private final Map<String,List<Batch>> batches=new HashMap<>();
    private Map<String,List<ModelGeometry.Point>> origins=Map.of();
    public int renderedTriangles;
    private record Batch(RenderType type,int color,float[] vertices,int triangles) {}
    public AssemblyGunModel(BedrockModelPOJO pojo,BedrockVersion version,AssembledWeapon weapon){
        super(pojo,version);this.weapon=weapon;
        var resources=Minecraft.getInstance().getResourceManager();
        try(var geometry=resources.openAsReader(ResourceLocation.parse(weapon.asset("manifest.json")));
            var library=resources.openAsReader(ResourceLocation.parse(weapon.asset("library.json")));
            var binding=resources.openAsReader(ResourceLocation.parse(weapon.asset("materials.json")))){
            models=ModelGeometry.load(geometry);materials=AssemblyMaterials.load(library,binding,models);
        }catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
        try(var reader=resources.openAsReader(ResourceLocation.parse(weapon.asset("markers.json")))){
            presentation=new AssemblyPresentation(weapon,models,WeaponPresentation.load(reader));
        }catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
        models.forEach((id,model)->{
            batches.put(id,compile(id,model));
            setFunctionalRenderer("part_"+id,bone->(poses,ignored,context,light,overlay)->draw(id,poses,context,light,overlay));
        });
    }
    public void preparePresentation(ItemStack stack,float aiming){
        presentation.apply(stack,getIronSightPath().getLast(),modelMap.get("lefthand").getModelRenderer(),modelMap.get("righthand").getModelRenderer(),getRootNode(),aiming);
    }
    public Map<String,ModelGeometry> geometry(){return models;}
    public AssemblyMaterials materials(){return materials;}
    public int expectedTriangles(){return batches.values().stream().flatMap(List::stream).mapToInt(Batch::triangles).sum();}
    private List<Batch> compile(String id,ModelGeometry model){
        var grouped=new LinkedHashMap<String,List<ModelGeometry.Triangle>>();
        for(var mesh:model.meshes())for(var triangle:mesh.triangles())grouped.computeIfAbsent(triangle.region(),k->new ArrayList<>()).add(triangle);
        var result=new ArrayList<Batch>();float scale=weapon.meshScale/16;
        grouped.forEach((region,triangles)->{
            var material=materials.resolve(id,region);var packed=new float[triangles.size()*4*8];int offset=0,count=0;
            for(var triangle:triangles){
                var points=new Vector3f[3];
                for(int i=0;i<3;i++){var v=triangle.vertices().get(i);points[i]=new Vector3f(v.x()*scale,-v.y()*scale,-v.z()*scale);}
                var normal=new Vector3f(points[1]).sub(points[0]).cross(new Vector3f(points[2]).sub(points[0]));
                if(normal.lengthSquared()<1e-12f)continue;normal.normalize();
                for(int i:new int[]{0,1,2,2}){var p=points[i];var uv=triangle.uv().get(i);
                    packed[offset++]=p.x;packed[offset++]=p.y;packed[offset++]=p.z;packed[offset++]=uv.u()*material.textureScale();packed[offset++]=uv.v()*material.textureScale();packed[offset++]=normal.x;packed[offset++]=normal.y;packed[offset++]=normal.z;
                }count++;
            }
            String texture=material.texture().isEmpty()?weapon.GUN.getNamespace()+":textures/gun/white.png":material.texture();
            result.add(new Batch(RenderType.entityCutoutNoCull(ResourceLocation.parse(texture)),material.color(),Arrays.copyOf(packed,offset),count));
        });return List.copyOf(result);
    }
    @Override public void render(PoseStack poses,ItemStack stack,ItemDisplayContext context,RenderType type,int light,int overlay){
        renderedTriangles=0;var visible=new HashMap<String,List<ModelGeometry.Point>>();
        try {var tree=weapon.project(stack);collect(stack,tree,tree,List.of(),visible);}
        catch(IllegalArgumentException invalid){visible.put(weapon.ROOT,List.of(new ModelGeometry.Point(0,0,0)));}
        origins=visible;super.render(poses,stack,context,type,light,overlay);reticle(poses,stack,context,overlay);
    }
    private void collect(ItemStack stack,AssemblyNode root,AssemblyNode node,List<String> path,Map<String,List<ModelGeometry.Point>> out){
        ModelGeometry.origin(root,models,path).ifPresent(p->out.computeIfAbsent(node.definitionId(),k->new ArrayList<>()).add(p));
        var state=AssemblyTrees.state(AssemblyTrees.at(stack,path));
        node.children().forEach((slot,child)->{
            if(state.in(slot).filter(dev.itemfoundation.api.assembly.AssemblyState.Installed::enabled).isEmpty())return;
            var next=new ArrayList<>(path);next.add(slot);collect(stack,root,child,next,out);
        });
    }
    private void reticle(PoseStack poses,ItemStack stack,ItemDisplayContext context,int overlay){
        if(!context.firstPerson()||Minecraft.getInstance().player==null||!com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator.fromLocalPlayer(Minecraft.getInstance().player).isAim())return;
        var aim=presentation.aim(stack);if(aim.isEmpty()||!aim.get().kind().equals("optic"))return;
        poses.pushPose();getRootNode().translateAndRotateAndScale(poses);
        float scale=weapon.meshScale/16;
        poses.scale(scale,-scale,-scale);poses.mulPose(aim.get().axis().matrix());
        var consumer=Minecraft.getInstance().renderBuffers().bufferSource().getBuffer(RenderType.entityTranslucent(ResourceLocation.parse(weapon.GUN.getNamespace()+":textures/gun/white.png")));
        float radius=.045f;
        for(int i=0;i<12;i++){
            double a=i*Math.PI/6,b=(i+1)*Math.PI/6;
            float[][] points={{0,0},{(float)Math.cos(a)*radius,(float)Math.sin(a)*radius},{(float)Math.cos(b)*radius,(float)Math.sin(b)*radius},{(float)Math.cos(b)*radius,(float)Math.sin(b)*radius}};
            for(var v:points)consumer.addVertex(poses.last(),v[0],v[1],-.02f).setColor(255,45,30,255).setUv(.5f,.5f).setOverlay(overlay).setLight(15728880).setNormal(poses.last(),0,0,-1);
        }
        poses.popPose();
    }
    private void draw(String id,PoseStack poses,ItemDisplayContext context,int light,int overlay){
        var placements=origins.get(id);if(placements==null)return;
        for(var origin:placements){
            poses.pushPose();float scale=weapon.meshScale/16;poses.translate(origin.x()*scale,-origin.y()*scale,-origin.z()*scale);
            for(var batch:batches.get(id)){
                var consumer=Minecraft.getInstance().renderBuffers().bufferSource().getBuffer(batch.type());var v=batch.vertices();int color=batch.color();
                for(int i=0;i<v.length;i+=8)consumer.addVertex(poses.last(),v[i],v[i+1],v[i+2]).setColor((color>>16)&255,(color>>8)&255,color&255,255).setUv(v[i+3],v[i+4]).setOverlay(overlay).setLight(light).setNormal(poses.last(),v[i+5],v[i+6],v[i+7]);
                renderedTriangles+=batch.triangles();
            }
            poses.popPose();

        }
    }
}
