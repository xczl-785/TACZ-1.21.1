package dev.weaponmodels;

import com.google.gson.*;
import org.joml.*;
import java.io.Reader;
import java.util.*;
import java.lang.Math;

/** Local markers use the same art frame as their mesh. No engine or player state. */
public record WeaponPresentation(Map<String, Part> parts) {
    public WeaponPresentation { parts = Map.copyOf(parts); }
    public record Vec(double x, double y, double z) {
        public static final Vec ZERO = new Vec(0,0,0);
        public Vec { if (!Double.isFinite(x+y+z)) throw new IllegalArgumentException("Non-finite marker"); }
        public Vec add(Vec b){return new Vec(x+b.x,y+b.y,z+b.z);}
        public Vec subtract(Vec b){return new Vec(x-b.x,y-b.y,z-b.z);}
        public Vec scale(double s){return new Vec(x*s,y*s,z*s);}
        public double distance(Vec b){var d=subtract(b);return Math.sqrt(d.x*d.x+d.y*d.y+d.z*d.z);}
        public Vector3f vector(){return new Vector3f((float)x,(float)y,(float)z);}
        public static Vec of(Vector3f v){return new Vec(v.x,v.y,v.z);}
    }
    /** Defensive immutable rigid frame; XYZ degrees mean Rz * Ry * Rx. */
    public static final class Frame {
        public static final Frame IDENTITY = at(Vec.ZERO,Vec.ZERO);
        private final Matrix4f matrix;
        private Frame(Matrix4f matrix){this.matrix=new Matrix4f(matrix);}
        public static Frame at(Vec p, Vec degrees){return new Frame(new Matrix4f().translation(p.vector()).rotateZYX((float)Math.toRadians(degrees.z),(float)Math.toRadians(degrees.y),(float)Math.toRadians(degrees.x)));}
        public Frame then(Frame local){return new Frame(new Matrix4f(matrix).mul(local.matrix));}
        public Vec transform(Vec local){return Vec.of(matrix.transformPosition(local.vector(),new Vector3f()));}
        public Vec position(){return transform(Vec.ZERO);}
        public Matrix4f matrix(){return new Matrix4f(matrix);}
    }
    public record Occurrence(String path,String definition,Frame frame) {}
    public record Contact(String role, Vec position, int priority) {
        public Contact { if(!Set.of("left","right").contains(role))throw new IllegalArgumentException("Unknown contact role"); }
    }
    public record Marker(String id,String kind,String group,Vec position,Vec rotation,double eyeDistance,double zoom,double modelFov,int priority) {
        public Marker {
            if(id==null||id.isBlank()||!Set.of("optic","rear","front").contains(kind))throw new IllegalArgumentException("Invalid sight marker");
            if(!Double.isFinite(eyeDistance+zoom+modelFov)||eyeDistance<0||(!kind.equals("front")&&eyeDistance<=0)||zoom<1||modelFov<10||modelFov>120)throw new IllegalArgumentException("Invalid eye distance/zoom/FOV");
            if(!kind.equals("optic")&&(group==null||group.isBlank()))throw new IllegalArgumentException("Iron sight needs a pairing group");
        }
    }
    public record Part(List<Marker> sights,List<Contact> contacts) {
        public Part { sights=List.copyOf(sights);contacts=List.copyOf(contacts);
            if(sights.stream().map(Marker::id).distinct().count()!=sights.size())throw new IllegalArgumentException("Duplicate sight mode");
            if(contacts.stream().map(Contact::role).distinct().count()!=contacts.size())throw new IllegalArgumentException("Duplicate contact role"); }
    }
    public record Aim(String id,Frame axis,double eyeDistance,double zoom,double modelFov,int priority,String kind) {
        public Aim(String id,Frame axis,double eyeDistance,double zoom,double modelFov,int priority){this(id,axis,eyeDistance,zoom,modelFov,priority,"optic");}
        public Frame eye(){return axis.then(Frame.at(new Vec(0,0,-eyeDistance),Vec.ZERO));}
    }
    public record Result(List<Aim> aims,Map<String,Vec> contacts) {
        public Result { aims=List.copyOf(aims);contacts=Map.copyOf(contacts); }
        public Optional<Aim> select(String id){return aims.stream().filter(a->a.id.equals(id)).findFirst().or(()->aims.stream().findFirst());}
    }
    private record Located(String id,Marker marker,Frame frame) {}
    public Result resolve(List<Occurrence> occurrences) {
        var markers=new ArrayList<Located>();var contactPoints=new TreeMap<String,Vec>();var priorities=new HashMap<String,Integer>();
        for(var o:occurrences.stream().sorted(Comparator.comparing(Occurrence::path)).toList()) {
            var part=parts.get(o.definition);if(part==null)continue;
            for(var m:part.sights)markers.add(new Located(o.path+"/"+m.id,m,o.frame.then(Frame.at(m.position,m.rotation))));
            for(var c:part.contacts)if(c.priority>priorities.getOrDefault(c.role,Integer.MIN_VALUE)) {
                priorities.put(c.role,c.priority);contactPoints.put(c.role,o.frame.transform(c.position));
            }
        }
        var aims=new ArrayList<Aim>();
        for(var m:markers) {
            if(m.marker.kind.equals("front"))continue;
            if(m.marker.kind.equals("rear")) {
                // Match the front in the rear's frame: ahead, collinear within 0.5 art units.
                var inverse=m.frame.matrix().invert();
                var fronts=markers.stream().filter(f->f.marker.kind.equals("front")&&f.marker.group.equals(m.marker.group))
                        .filter(f->{var p=inverse.transformPosition(f.frame.position().vector(),new Vector3f());var forward=inverse.transformDirection(f.frame.matrix().transformDirection(new Vector3f(0,0,1)),new Vector3f());return p.z>1&&Math.hypot(p.x,p.y)<=.5&&forward.z>.995;}).toList();
                if(fronts.size()!=1)continue; // Ambiguous or incomplete irons are not an aiming mode.
            }
            aims.add(new Aim(m.id,m.frame,m.marker.eyeDistance,m.marker.zoom,m.marker.modelFov,m.marker.priority,m.marker.kind));
        }
        aims.sort(Comparator.comparingInt(Aim::priority).reversed().thenComparing(Aim::id));
        return new Result(aims,contactPoints);
    }
    public static List<Occurrence> occurrences(dev.weaponassembly.api.AssemblyNode root,Map<String,ModelGeometry> models){
        var result=new ArrayList<Occurrence>(); collect(root,root,models,List.of(),result);return List.copyOf(result);
    }
    private static void collect(dev.weaponassembly.api.AssemblyNode root,dev.weaponassembly.api.AssemblyNode node,Map<String,ModelGeometry> models,List<String> path,List<Occurrence> out){
        if(path.size()>dev.weaponassembly.api.AssemblyEngine.MAX_DEPTH||out.size()>=dev.weaponassembly.api.AssemblyEngine.MAX_NODES)throw new IllegalArgumentException("Presentation assembly limit");
        var p=ModelGeometry.origin(root,models,path).orElseThrow(()->new IllegalArgumentException("Missing geometry path: "+path));
        out.add(new Occurrence(String.join("/",path),node.definitionId(),Frame.at(new Vec(p.x(),p.y(),p.z()),Vec.ZERO)));
        node.children().forEach((slot,child)->{var next=new ArrayList<>(path);next.add(slot);collect(root,child,models,next,out);});
    }
    public static WeaponPresentation load(Reader reader) {
        var json=JsonParser.parseReader(reader).getAsJsonObject();
        if(json.get("schemaVersion").getAsInt()!=1)throw new IllegalArgumentException("Unsupported presentation markers");
        var parts=new TreeMap<String,Part>();
        for(var e:json.getAsJsonObject("parts").entrySet()) {
            var p=e.getValue().getAsJsonObject();var sights=new ArrayList<Marker>();var contacts=new ArrayList<Contact>();
            for(var v:p.getAsJsonArray("sights")) {var m=v.getAsJsonObject();sights.add(new Marker(m.get("id").getAsString(),m.get("kind").getAsString(),m.get("group").getAsString(),vec(m.get("position")),vec(m.get("rotation")),m.get("eyeDistance").getAsDouble(),m.get("zoom").getAsDouble(),m.get("modelFov").getAsDouble(),m.get("priority").getAsInt()));}
            for(var v:p.getAsJsonArray("contacts")){var c=v.getAsJsonObject();contacts.add(new Contact(c.get("role").getAsString(),vec(c.get("position")),c.get("priority").getAsInt()));}
            parts.put(e.getKey(),new Part(sights,contacts));
        }
        return new WeaponPresentation(parts);
    }
    private static Vec vec(JsonElement element){var a=element.getAsJsonArray();if(a.size()!=3)throw new IllegalArgumentException("Expected vector");return new Vec(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble());}
}
