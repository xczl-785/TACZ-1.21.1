package dev.weaponmodels;

import com.google.gson.*;
import dev.firearms.assembly.AssemblyNode;
import java.io.Reader;
import java.util.*;

/** Small, explicit geometry contract. Geometry never decides gameplay compatibility. */
public record ModelGeometry(Point attachmentOrigin, Map<String, Point> slots, List<Box> boxes, List<Mesh> meshes) {
    public record Uv(float u,float v) {
        public Uv { if(!Float.isFinite(u)||!Float.isFinite(v))throw new IllegalArgumentException("Non-finite UV"); }
    }
    public record Triangle(List<Point> vertices,List<Uv> uv,String region) {
        public Triangle { vertices=List.copyOf(vertices);uv=List.copyOf(uv);Objects.requireNonNull(region);
            if(vertices.size()!=3||uv.size()!=3||region.isBlank())throw new IllegalArgumentException("Expected triangle with UV and region"); }
        public Triangle(List<Point> vertices) { this(vertices,Collections.nCopies(3,new Uv(0,0)),"body"); }
    }
    public record Mesh(String name,List<Triangle> triangles) {
        public Mesh { Objects.requireNonNull(name);triangles=List.copyOf(triangles); }
    }
    public record Point(float x, float y, float z) {
        public Point { if (!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z)) throw new IllegalArgumentException("Non-finite geometry"); }
        public Point add(Point p) { return new Point(x+p.x,y+p.y,z+p.z); }
        public Point subtract(Point p) { return new Point(x-p.x,y-p.y,z-p.z); }
    }
    public record Box(Point min, Point max) {
        public Box { if(min.x>=max.x||min.y>=max.y||min.z>=max.z) throw new IllegalArgumentException("Empty geometry box"); }
    }
    public ModelGeometry { Objects.requireNonNull(attachmentOrigin);slots=Map.copyOf(slots);boxes=List.copyOf(boxes);meshes=List.copyOf(meshes); }
    public ModelGeometry(Point attachmentOrigin,Map<String,Point> slots,List<Box> boxes) { this(attachmentOrigin,slots,boxes,List.of()); }
    /** Local mesh translation for a specific occurrence, including non-zero attachment origins. */
    public static Optional<Point> origin(AssemblyNode root,Map<String,ModelGeometry> models,List<String> path) {
        if(root==null)return Optional.empty();
        Point anchor=new Point(0,0,0);var node=root;
        for(String slot:path) {
            var geometry=models.get(node.definitionId());var child=node.children().get(slot);
            if(geometry==null||child==null||!geometry.slots.containsKey(slot))return Optional.empty();
            anchor=anchor.subtract(geometry.attachmentOrigin).add(geometry.slots.get(slot));node=child;
        }
        var geometry=models.get(node.definitionId());
        return geometry==null?Optional.empty():Optional.of(anchor.subtract(geometry.attachmentOrigin));
    }
    public static double zoomAfter(double current,double wheelDelta) {
        return Math.clamp(current*Math.exp(wheelDelta*.12),.45,2.5);
    }
    /** Orthographic camera in GUI coordinates; shared by mesh vertices and slot anchors. */
    public static Point project(Point p,double yaw,double pitch,double zoom,float x,float y,float width,float height) {
        return projectAtScale(p,yaw,pitch,(float)(Math.min(width/21,height/16)*zoom),x,y,width,height);
    }
    public static Point projectAtScale(Point p,double yaw,double pitch,float scale,float x,float y,float width,float height) {
        double cy=Math.cos(yaw),sy=Math.sin(yaw),cp=Math.cos(pitch),sp=Math.sin(pitch);
        double rx=p.x()*cy+p.z()*sy,rz=-p.x()*sy+p.z()*cy;
        double ry=(p.y()+1)*cp-rz*sp;rz=(p.y()+1)*sp+rz*cp;
        return new Point(x+width*.5f+(float)rx*scale,y+height*.5f-(float)ry*scale,20+(float)rz*.2f);
    }
    public static Map<String,ModelGeometry> load(Reader reader) {
        var root=JsonParser.parseReader(reader).getAsJsonObject();
        int version=root.get("schemaVersion").getAsInt();
        if(version<1||version>3) throw new IllegalArgumentException("Unsupported geometry schema");
        var result=new LinkedHashMap<String,ModelGeometry>();
        for(var entry:root.getAsJsonArray("models")) {
            var model=entry.getAsJsonObject();var slots=new LinkedHashMap<String,Point>();
            model.getAsJsonObject("slots").entrySet().forEach(e->slots.put(e.getKey(),point(e.getValue())));
            var boxes=new ArrayList<Box>();
            for(var b:model.getAsJsonArray("boxes")) { var pair=b.getAsJsonArray();boxes.add(new Box(point(pair.get(0)),point(pair.get(1)))); }
            var meshes=new ArrayList<Mesh>();
            if(version>=2)for(var m:model.getAsJsonArray("meshes")) {
                var mesh=m.getAsJsonObject();var triangles=new ArrayList<Triangle>();
                for(var t:mesh.getAsJsonArray("triangles")) {
                    var triangle=t.getAsJsonObject();var vertices=new ArrayList<Point>();
                    for(var v:triangle.getAsJsonArray("vertices")) {
                        vertices.add(point(v));
                    }
                    var uv=new ArrayList<Uv>();
                    if(version>=3)for(var value:triangle.getAsJsonArray("uv")) {
                        var pair=value.getAsJsonArray();if(pair.size()!=2)throw new IllegalArgumentException("Expected UV pair");
                        uv.add(new Uv(pair.get(0).getAsFloat(),pair.get(1).getAsFloat()));
                    }
                    else uv.addAll(Collections.nCopies(3,new Uv(0,0)));
                    triangles.add(new Triangle(vertices,uv,version>=3?triangle.get("region").getAsString():mesh.get("name").getAsString()));
                }
                meshes.add(new Mesh(mesh.get("name").getAsString(),triangles));
            }
            var id=model.get("definitionId").getAsString();
            if(result.putIfAbsent(id,new ModelGeometry(point(model.get("attachmentOrigin")),slots,boxes,meshes))!=null) throw new IllegalArgumentException("Duplicate geometry: "+id);
        }
        return Map.copyOf(result);
    }
    private static Point point(JsonElement value) {
        var a=value.getAsJsonArray();if(a.size()!=3) throw new IllegalArgumentException("Expected three coordinates");
        return new Point(a.get(0).getAsFloat(),a.get(1).getAsFloat(),a.get(2).getAsFloat());
    }
}
