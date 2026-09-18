package dev.tacticaltacz.workbench;

import com.google.gson.*;
import dev.tacticaltacz.assembled.*;
import dev.weaponassembly.api.*;
import dev.weaponmodels.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Standalone line-protocol adapter. All compatibility decisions stay in AssemblyEngine. */
public final class TaczWorkbenchWorker {
    private static final Gson JSON=new Gson();
    private final Map<String,AssembledWeapon> weapons=new TreeMap<>();
    private final Map<String,Map<String,ModelGeometry>> geometry=new HashMap<>();
    private final Map<String,AssemblyMaterials> materials=new HashMap<>();

    private TaczWorkbenchWorker(){for(var weapon:AssembledWeapons.all())if(weapon.nativeRig)weapons.put(weapon.PROFILE,weapon);}

    private Object catalog(){
        var rows=new ArrayList<Object>();
        for(var w:weapons.values())rows.add(Map.of(
            "id",w.PROFILE,"name",w.GUN.getPath().replace('_',' ').toUpperCase(Locale.ROOT),
            "nativeRig",w.nativeRig,"root",w.ROOT,"presetNodes",count(w.PRESET)));
        return Map.of("weapons",rows,"contract","TaCZ production AssemblyEngine + generated preview geometry");
    }

    private static int count(AssemblyNode node){int n=1;for(var child:node.children().values())n+=count(child);return n;}
    private static UUID id(String path,String definition){return UUID.nameUUIDFromBytes((path+"\0"+definition).getBytes(StandardCharsets.UTF_8));}
    private static List<String> path(String value){return value.isBlank()?List.of():List.of(value.split("/"));}
    private static String join(String base,String slot){return base.isEmpty()?slot:base+"/"+slot;}

    private static Map<String,String> selection(AssemblyNode root){
        var out=new TreeMap<String,String>();select(root,"",out);return out;
    }
    private static void select(AssemblyNode node,String base,Map<String,String> out){
        node.children().forEach((slot,child)->{String p=join(base,slot);out.put(p,child.definitionId());select(child,p,out);});
    }
    private static AssemblyNode restore(AssembledWeapon w,JsonObject request){
        if(!request.has("selection"))return w.PRESET;
        var selected=new TreeMap<String,String>();request.getAsJsonObject("selection").entrySet().forEach(e->selected.put(e.getKey(),e.getValue().getAsString()));
        var root=restoreNode(w,w.ROOT,"",selected);var validation=w.ENGINE.validate(root);
        if(!validation.valid())throw new IllegalArgumentException("Invalid observation tree: "+validation.errors());return root;
    }
    private static AssemblyNode restoreNode(AssembledWeapon w,String definition,String base,Map<String,String> selected){
        var children=new TreeMap<String,AssemblyNode>();
        for(var slot:w.CATALOG.require(definition).slots()){
            String p=join(base,slot.id()),child=selected.get(p);if(child==null)continue;
            if(!slot.allowedParts().contains(child))throw new IllegalArgumentException("Incompatible selection at "+p+": "+child);
            children.put(slot.id(),restoreNode(w,child,p,selected));
        }
        return new AssemblyNode(id(base.isEmpty()?"root":base,definition),definition,children);
    }
    private static AssemblyNode template(AssemblyNode node,String definition,String target){
        if(node.definitionId().equals(definition))return reidentify(node,target);
        for(var child:node.children().values()){var found=template(child,definition,target);if(found!=null)return found;}return null;
    }
    private static AssemblyNode reidentify(AssemblyNode node,String base){
        var children=new TreeMap<String,AssemblyNode>();node.children().forEach((slot,child)->children.put(slot,reidentify(child,join(base,slot))));
        // A catalog candidate is a new instance, even when replacing the same definition.
        return new AssemblyNode(UUID.randomUUID(),node.definitionId(),children);
    }
    private static AssemblyNode edit(AssembledWeapon w,AssemblyNode tree,JsonObject edit){
        String raw=edit.get("path").getAsString(),definition=edit.get("definition").getAsString();var p=path(raw);
        var parent=tree;for(String slot:p.subList(0,p.size()-1)){parent=parent.children().get(slot);if(parent==null)throw new IllegalArgumentException("Missing parent: "+raw);}
        boolean occupied=parent.children().containsKey(p.getLast());AssemblyEngine.Result result;
        if(definition.isEmpty())result=w.ENGINE.remove(tree,p);
        else {var part=template(w.PRESET,definition,raw);if(part==null)part=AssemblyNode.leaf(UUID.randomUUID(),definition);result=occupied?w.ENGINE.replace(tree,p,part):w.ENGINE.install(tree,p,part);}
        if(!result.success())throw new IllegalArgumentException(result.errors().toString());return result.after();
    }

    private Map<String,ModelGeometry> geometry(AssembledWeapon w){return geometry.computeIfAbsent(w.PROFILE,k->NativeAssemblyView.geometry(w));}
    private AssemblyMaterials materials(AssembledWeapon w){return materials.computeIfAbsent(w.PROFILE,k->NativeAssemblyView.materials(w,geometry(w)));}

    private Object assembly(JsonObject request){
        var w=weapons.get(request.get("weapon").getAsString());if(w==null)throw new IllegalArgumentException("Unknown weapon");
        var tree=restore(w,request);if(request.has("edit"))tree=edit(w,tree,request.getAsJsonObject("edit"));
        var models=geometry(w);var mats=materials(w);var slots=new ArrayList<Object>();slotRows(w,tree,tree,"",models,slots);
        var parts=new ArrayList<Object>();partRows(tree,tree,"",models,mats,parts);
        var validation=w.ENGINE.validate(tree);
        return Map.of("weapon",w.PROFILE,"nativeRig",w.nativeRig,"selection",selection(tree),"slots",slots,"parts",parts,
            "validation",Map.of("valid",validation.valid(),"complete",validation.complete(),"errors",issues(validation.errors()),"missing",issues(validation.missingRequired())),
            "coordinateContract",Map.of("slotAnchor","parent preview geometry coordinates","attachmentOrigin","subtracted exactly once by shared ModelGeometry.origin","nativePivot","owned by TaCZ native model and not fabricated by this browser"));
    }
    private static List<Object> issues(List<AssemblyEngine.Issue> values){return values.stream().<Object>map(i->Map.of("code",i.code().name(),"path",String.join("/",i.path()),"detail",i.detail())).toList();}
    private static void slotRows(AssembledWeapon w,AssemblyNode root,AssemblyNode node,String base,Map<String,ModelGeometry> models,List<Object> out){
        var model=models.get(node.definitionId());var ownerOrigin=ModelGeometry.origin(root,models,path(base)).orElse(new ModelGeometry.Point(0,0,0));
        for(var slot:w.CATALOG.require(node.definitionId()).slots()){
            String full=join(base,slot.id());var installed=node.children().get(slot.id());var anchor=model==null?null:model.slots().get(slot.id());
            var candidates=slot.allowedParts().stream().sorted().map(id->Map.of("id",id,"name",label(id))).toList();
            var row=new LinkedHashMap<String,Object>();row.put("path",full);row.put("slot",slot.id());row.put("required",slot.required());row.put("installed",installed==null?"":installed.definitionId());row.put("candidates",candidates);
            if(anchor!=null)row.put("worldAnchor",point(ownerOrigin.add(anchor)));out.add(row);
            if(installed!=null)slotRows(w,root,installed,full,models,out);
        }
    }
    private static String label(String id){int at=Math.max(id.lastIndexOf(':'),id.lastIndexOf('/'));return id.substring(at+1).replace('_',' ');}
    private static List<Float> point(ModelGeometry.Point p){return List.of(p.x(),p.y(),p.z());}
    private static void partRows(AssemblyNode root,AssemblyNode node,String base,Map<String,ModelGeometry> models,AssemblyMaterials materials,List<Object> out){
        var model=models.get(node.definitionId());if(model==null)throw new IllegalArgumentException("Missing preview geometry: "+node.definitionId());
        var origin=ModelGeometry.origin(root,models,path(base)).orElseThrow();var vertices=new ArrayList<Float>();
        for(var mesh:model.meshes())for(var triangle:mesh.triangles()){
            var material=materials.resolve(node.definitionId(),triangle.region());int color=material.color();
            var a=triangle.vertices().get(0);var b0=triangle.vertices().get(1);var c0=triangle.vertices().get(2);
            float ux=b0.x()-a.x(),uy=b0.y()-a.y(),uz=b0.z()-a.z(),vx=c0.x()-a.x(),vy=c0.y()-a.y(),vz=c0.z()-a.z();
            float nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx,len=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
            float light=len<1e-6f?.72f:.48f+.46f*Math.abs((nx*.72f+ny*.55f+nz*.42f)/len);
            float r=((color>>16)&255)/255f*light,g=((color>>8)&255)/255f*light,b=(color&255)/255f*light;
            for(var p:triangle.vertices()){vertices.add(p.x()+origin.x());vertices.add(p.y()+origin.y());vertices.add(p.z()+origin.z());vertices.add(r);vertices.add(g);vertices.add(b);}
        }
        out.add(Map.of("path",base,"definition",node.definitionId(),"origin",point(origin),"attachmentOrigin",point(model.attachmentOrigin()),"vertices",vertices));
        node.children().forEach((slot,child)->partRows(root,child,join(base,slot),models,materials,out));
    }

    public static void main(String[] args){
        var worker=new TaczWorkbenchWorker();try(var scanner=new Scanner(System.in,StandardCharsets.UTF_8)){
            while(scanner.hasNextLine()){try{var request=JsonParser.parseString(scanner.nextLine()).getAsJsonObject();var result=switch(request.get("op").getAsString()){case "catalog"->worker.catalog();case "assembly"->worker.assembly(request);default->throw new IllegalArgumentException("Unknown op");};System.out.println(JSON.toJson(result));}
                catch(Exception e){System.out.println(JSON.toJson(Map.of("error",e.toString())));}System.out.flush();}
        }
    }
    private TaczWorkbenchWorker(boolean unused){this();}
}
