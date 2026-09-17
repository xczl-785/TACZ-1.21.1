package dev.weaponassemblyui.client;

import dev.weaponassembly.api.AssemblyNode;
import dev.weaponmodels.ModelGeometry;
import dev.weaponmodels.ModelGeometry.Point;
import java.util.*;

/** Computes the fixed-center automatic fit used by the Tarkov-style workbench camera. */
final class AssemblyFraming {
    record Frame(Point center,float fitScale){}
    static Frame fit(AssemblyNode root,Map<String,ModelGeometry> models) {
        var points=new ArrayList<Point>();collect(root,root,models,List.of(),points);
        if(points.isEmpty())return new Frame(new Point(0,0,0),1);
        float minX=Float.MAX_VALUE,minY=minX,minZ=minX,maxX=-minX,maxY=-minX,maxZ=-minX;
        for(var p:points){minX=Math.min(minX,p.x());minY=Math.min(minY,p.y());minZ=Math.min(minZ,p.z());maxX=Math.max(maxX,p.x());maxY=Math.max(maxY,p.y());maxZ=Math.max(maxZ,p.z());}
        var center=new Point((minX+maxX)/2,(minY+maxY)/2,(minZ+maxZ)/2);
        return new Frame(center,18/Math.max(.01f,Math.max(maxX-minX,Math.max(maxY-minY,maxZ-minZ))));
    }
    private static void collect(AssemblyNode root,AssemblyNode node,Map<String,ModelGeometry> models,List<String> path,List<Point> points) {
        if(node==null)return;
        var geometry=models.get(node.definitionId());if(geometry==null)return;
        var offset=ModelGeometry.origin(root,models,path).orElseThrow();
        for(var box:geometry.boxes()){points.add(box.min().add(offset));points.add(box.max().add(offset));}
        for(var mesh:geometry.meshes())for(var triangle:mesh.triangles())for(var vertex:triangle.vertices())points.add(vertex.add(offset));
        node.children().forEach((slot,child)->{var next=new ArrayList<>(path);next.add(slot);collect(root,child,models,next,points);});
    }
    private AssemblyFraming(){}
}
