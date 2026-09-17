package dev.weaponassemblyui.client;

import java.util.*;

/** Screen-space layout only. Slot paths, not installed-item identities, own positions. */
public final class SlotLayout {
    public record Point(double x, double y) {}
    public record Anchor(List<String> path, Point point) {
        public Anchor { path = List.copyOf(path); }
    }
    public record Bounds(double x, double y, double width, double height) {}
    private static final double GAP = 6;
    private SlotLayout() {}

    /** One weapon-screen session. Coordinates are unscaled design pixels. */
    public static final class State {
        private final Map<List<String>, Point> positions = new LinkedHashMap<>();
        private final Map<List<String>,String> orderKeys=new HashMap<>();
        private final Map<List<String>, Point> mounts = new HashMap<>();
        private final Map<List<String>, Point> targets = new HashMap<>();
        private final Map<List<String>, Point> destinations = new LinkedHashMap<>();
        private final Map<List<String>,Point> passingPlaces=new HashMap<>();
        private Set<List<String>> lastPinned=Set.of();
        private Map<List<String>,Point> lastSnapshot=Map.of();
        private boolean settled;
        private Bounds lastArea;
        private double lastWidth,lastHeight;

        public Map<List<String>, Point> update(List<Anchor> anchors, Bounds area, double width,
                double height, Set<List<String>> pinned, double seconds) {
            // A transient invalid native layout must never replace the last usable frame.
            if (!valid(area, width, height) || anchors.stream().anyMatch(a -> !finite(a.point)))
                return snapshot();
            double dt = Double.isFinite(seconds) ? Math.max(0, Math.min(.05, seconds)) : 0;
            boolean changed=anchors.size()!=mounts.size()||anchors.stream().anyMatch(a->!a.point.equals(mounts.get(a.path)));
            boolean resized=!area.equals(lastArea)||width!=lastWidth||height!=lastHeight;
            if(!changed&&!resized&&lastPinned.equals(pinned)&&(settled||dt==0))return snapshot();
            var before=lastSnapshot;
            lastArea=area;lastWidth=width;lastHeight=height;
            var previousMounts=new HashMap<>(mounts);
            var paths = new HashSet<List<String>>();
            for (var anchor : anchors) {
                paths.add(anchor.path);orderKeys.computeIfAbsent(anchor.path,p->String.join("/",p));
            }
            orderKeys.keySet().retainAll(paths);
            positions.keySet().retainAll(paths); mounts.keySet().retainAll(paths); targets.keySet().retainAll(paths); destinations.keySet().retainAll(paths);
            var ordered = new ArrayList<>(anchors);
            ordered.sort(Comparator.comparing(a -> orderKeys.get(a.path)));
            // Reserve survivors before admitting any new slots. Removing a slot never compacts its neighbors.
            for (var anchor : ordered) {
                var old = positions.get(anchor.path);
                if (old == null) continue;
                var mount = mounts.get(anchor.path);
                var target = targets.getOrDefault(anchor.path, old);
                if (pinned.contains(anchor.path)) target = old;
                else if (mount != null && !mount.equals(anchor.point))
                    target = new Point(target.x + anchor.point.x - mount.x, target.y + anchor.point.y - mount.y);
                targets.put(anchor.path, target);
                mounts.put(anchor.path, anchor.point);
            }
            var mountBounds=mountBounds(anchors);
            for (var anchor : ordered) {
                if (positions.containsKey(anchor.path)) continue;
                var site = nearestFree(anchor.point, mountBounds, area, width, height, positions.values());
                positions.put(anchor.path, site); targets.put(anchor.path, site); mounts.put(anchor.path, anchor.point);
            }
            if(resized) {
                // A narrower window can exclude previous positions. Keep in-bounds survivors and
                // rehome only excluded cards before running motion in the new coordinate domain.
                var reserved=new ArrayList<Point>();
                for(var p:positions.values())if(inside(p,area,width,height))reserved.add(p);
                for(var entry:positions.entrySet())if(!inside(entry.getValue(),area,width,height)) {
                    var p=freeSite(clamp(entry.getValue(),area,width,height),null,area,width,height,reserved);
                    entry.setValue(p);targets.put(entry.getKey(),p);destinations.put(entry.getKey(),p);reserved.add(p);
                }
            }
            if(changed || resized || !lastPinned.equals(pinned)) {
                passingPlaces.clear();
                var reserved=new LinkedHashMap<List<String>,Point>();
                var priority=new ArrayList<>(ordered);
                priority.sort(Comparator.comparingInt((Anchor a)->pinned.contains(a.path)?0:
                        a.point.equals(previousMounts.get(a.path))?1:2).thenComparing(a->orderKeys.get(a.path)));
                for(var anchor:priority) {
                    var desired=pinned.contains(anchor.path)?positions.get(anchor.path):
                            anchor.point.equals(previousMounts.get(anchor.path))&&destinations.containsKey(anchor.path)?destinations.get(anchor.path):
                            clamp(targets.get(anchor.path),area,width,height);
                    desired=clamp(desired,area,width,height);
                    var destination=free(desired,reserved.values(),width,height)?desired:
                            freeSite(desired,null,area,width,height,reserved.values());
                    reserved.put(anchor.path,destination);
                }
                destinations.clear();destinations.putAll(reserved);lastPinned=Set.copyOf(pinned);
            }
            // Bounded continuous movement, with swept collision checks (including the dropdown footprint).
            // Collision response moves locally; changes of side have a continuous, obstacle-free path.
            for (var anchor : ordered) {
                if (pinned.contains(anchor.path)) continue;
                var old = positions.get(anchor.path);
                var obstacles = new ArrayList<Point>();
                positions.forEach((path, point) -> { if (!path.equals(anchor.path)) obstacles.add(point); });
                var finalGoal = destinations.get(anchor.path);
                var passing = passingPlaces.get(anchor.path);
                if(passing!=null&&distanceSquared(old,passing)<.0025&&free(finalGoal,obstacles,width,height)) {
                    passingPlaces.remove(anchor.path);passing=null;
                }
                if(passing==null&&!free(finalGoal,obstacles,width,height)) {
                    // When two touching cards exchange places, neither endpoint is initially free.
                    // The earlier path yields into a safe passing place; the other can then vacate its endpoint.
                    boolean yields=positions.entrySet().stream().filter(e->!e.getKey().equals(anchor.path))
                            .filter(e->overlaps(finalGoal,e.getValue(),width,height,GAP-.00001))
                            .allMatch(e->orderKeys.get(anchor.path).compareTo(orderKeys.get(e.getKey()))<0);
                    if(yields) {
                        var reserved=new ArrayList<>(obstacles);
                        destinations.forEach((path,point)->{if(!path.equals(anchor.path))reserved.add(point);});
                        double dx=finalGoal.x-old.x,dy=finalGoal.y-old.y;
                        Point preferred=Math.abs(dx)>=Math.abs(dy)?new Point(old.x,old.y+height+GAP):new Point(old.x+width+GAP,old.y);
                        passing=findFreeSite(clamp(preferred,area,width,height),null,area,width,height,reserved);
                        if(passing!=null)passingPlaces.put(anchor.path,passing);
                    }
                }
                var goal = passing==null?finalGoal:passing;
                double distance = Math.sqrt(distanceSquared(old,goal));
                if (dt == 0) continue;
                if(distance < .05 && distance<=650*dt && free(goal,obstacles,width,height)) {
                    positions.put(anchor.path,goal);continue;
                }
                double travel = Math.min(distance*(1-Math.exp(-18*dt)),650*dt);
                var wanted = towards(old,goal,travel);
                var next = slide(old, wanted, obstacles, width, height);
                if (distanceSquared(next,wanted) > .000001) {
                    // A mount can cross another mount during rotation. Slide alone would lock their order.
                    // Route only the blocked card around nearby rectangles; other cards stay reserved.
                    var route = route(old,goal,area,obstacles,width,height);
                    // If a dense cluster temporarily disconnects the free area, keep the last valid
                    // position instead of asymptotically sliding along a wall toward an unreachable goal.
                    next=old;
                    if(!route.isEmpty()) {
                        double remaining=travel;
                        for(var waypoint:route) {
                            var step=slide(next,towards(next,waypoint,remaining),obstacles,width,height);
                            remaining-=Math.sqrt(distanceSquared(next,step));next=step;
                            if(remaining<.000001||distanceSquared(next,waypoint)>.000001)break;
                        }
                    }
                }
                positions.put(anchor.path, next);
            }
            var result=snapshot();settled=dt>0&&before.equals(result);
            return result;
        }
        private Map<List<String>, Point> snapshot() {
            if(!lastSnapshot.equals(positions))lastSnapshot=Collections.unmodifiableMap(new LinkedHashMap<>(positions));
            return lastSnapshot;
        }
    }

    private static Point slide(Point old, Point wanted, Collection<Point> obstacles, double width, double height) {
        // Axis-separated swept AABB motion cannot tunnel through another card even after a long frame.
        double x = wanted.x;
        for (var p : obstacles) {
            if (old.y + height + GAP <= p.y+1e-7 || p.y + height + GAP <= old.y+1e-7) continue;
            if (x > old.x && old.x + width + GAP <= p.x+1e-7) x = Math.min(x, p.x-width-GAP);
            if (x < old.x && p.x + width + GAP <= old.x+1e-7) x = Math.max(x, p.x+width+GAP);
        }
        double y = wanted.y;
        for (var p : obstacles) {
            if (x + width + GAP <= p.x+1e-7 || p.x + width + GAP <= x+1e-7) continue;
            if (y > old.y && old.y + height + GAP <= p.y+1e-7) y = Math.min(y, p.y-height-GAP);
            if (y < old.y && p.y + height + GAP <= old.y+1e-7) y = Math.max(y, p.y+height+GAP);
        }
        var result=new Point(x,y);
        return free(result,obstacles,width,height)?result:old;
    }

    private static Bounds mountBounds(List<Anchor> anchors) {
        double left=Double.POSITIVE_INFINITY,top=left,right=Double.NEGATIVE_INFINITY,bottom=right;
        for(var a:anchors) {
            left=Math.min(left,a.point.x);right=Math.max(right,a.point.x);
            top=Math.min(top,a.point.y);bottom=Math.max(bottom,a.point.y);
        }
        return new Bounds(left,top,Math.max(1,right-left),Math.max(1,bottom-top));
    }
    private static Point nearestFree(Point anchor, Bounds mounts, Bounds area, double width, double height, Collection<Point> occupied) {
        // A rotated or unusually proportioned weapon can project beyond the workbench. There is then no complete perimeter
        // to reserve; place new cards in local free space while existing cards keep their offsets.
        if(mounts.x<area.x||mounts.x+mounts.width>area.x+area.width||mounts.y<area.y||mounts.y+mounts.height>area.y+area.height) {
            double sign=anchor.y<=area.y+area.height/2?-1:1;
            return freeSite(clamp(new Point(anchor.x-width/2,anchor.y+sign*130-height/2),area,width,height),anchor,area,width,height,occupied);
        }
        // Distribute around the projected weapon, not above/below the center of the UI area.
        // Reserve a central band around its mounts so filling an upper row cannot spill onto the gun.
        double left=mounts.x-35,right=mounts.x+mounts.width+35;
        double top=mounts.y-45,bottom=mounts.y+mounts.height+55;
        var preferred=List.of(new Point(anchor.x-width/2,top-height),
                new Point(anchor.x-width/2,bottom),
                new Point(left-width,anchor.y-height/2),new Point(right,anchor.y-height/2));
        Point best=null;double bestScore=Double.POSITIVE_INFINITY;
        for(int side=0;side<4;side++) {
            // Restrict each search to its own side of the weapon; keep card and dropdown outside the band.
            Bounds strip=switch(side) {
                case 0 -> new Bounds(area.x,area.y,area.width,Math.min(area.height,top-area.y));
                case 1 -> new Bounds(area.x,Math.max(area.y,bottom),area.width,area.y+area.height-Math.max(area.y,bottom));
                case 2 -> new Bounds(area.x,area.y,Math.min(area.width,left-area.x),area.height);
                default -> new Bounds(Math.max(area.x,right),area.y,area.x+area.width-Math.max(area.x,right),area.height);
            };
            if(!valid(strip,width,height))continue;
            var p=findFreeSite(clamp(preferred.get(side),strip,width,height),null,strip,width,height,occupied);
            if(p==null)continue;
            var center=new Point(p.x+width/2,p.y+height/2);
            double score=distanceSquared(center,anchor);
            // Soft balance keeps similarly placed mounts from all choosing the same upper row.
            int count=0;
            for(var old:occupied) {
                boolean same=switch(side) {
                    case 0 -> old.y+height<=top+.001;
                    case 1 -> old.y>=bottom-.001;
                    case 2 -> old.x+width<=left+.001;
                    default -> old.x>=right-.001;
                };
                if(same)count++;
            }
            score+=count*width*height*.75;
            if(score<bestScore){bestScore=score;best=p;}
        }
        if(best!=null)return best;
        // Extreme projection/density can leave no exterior strip; retain access using remaining free space.
        return freeSite(clamp(preferred.get(0),area,width,height),anchor,area,width,height,occupied);
    }
    private static Point freeSite(Point preferred, Point anchor, Bounds area, double width, double height, Collection<Point> occupied) {
        var site=findFreeSite(preferred,anchor,area,width,height,occupied);
        if(site==null)throw new IllegalArgumentException("Slot cards exceed layout capacity");
        return site;
    }
    private static Point findFreeSite(Point preferred, Point anchor, Bounds area, double width, double height, Collection<Point> occupied) {
        var candidates = new ArrayList<Point>();
        candidates.add(preferred);
        // Obstacle edges provide exact compact local placements, including tightly packed coincident mounts.
        var xs = new TreeSet<Double>(); var ys = new TreeSet<Double>();
        xs.add(preferred.x); xs.add(area.x); xs.add(area.x+area.width-width);
        ys.add(preferred.y); ys.add(area.y); ys.add(area.y+area.height-height);
        for (var p : occupied) {
            xs.add(p.x-width-GAP); xs.add(p.x+width+GAP);
            ys.add(p.y-height-GAP); ys.add(p.y+height+GAP);
        }
        for (double x : xs) for (double y : ys) candidates.add(new Point(x,y));
        Point best = null; double score = Double.POSITIVE_INFINITY;
        for (var p : candidates) {
            if (!inside(p,area,width,height) || !free(p,occupied,width,height)) continue;
            double candidate = distanceSquared(p,preferred);
            // Keep the card away from its mount unless constrained by the available screen area.
            if (anchor != null) {
                double centerDistance = Math.sqrt(distanceSquared(new Point(p.x+width/2,p.y+height/2),anchor));
                candidate += Math.pow(Math.max(0,100-centerDistance),2)*4;
            }
            if (candidate < score) { best = p; score = candidate; }
        }
        return best;
    }

    private static boolean free(Point p, Collection<Point> obstacles, double width, double height) {
        return obstacles.stream().noneMatch(o -> overlaps(p,o,width,height,GAP-.00001));
    }
    private static Point towards(Point from, Point to, double travel) {
        double distance=Math.sqrt(distanceSquared(from,to));
        double fraction=distance==0?0:Math.min(1,travel/distance);
        return new Point(from.x+(to.x-from.x)*fraction,from.y+(to.y-from.y)*fraction);
    }
    private record Visit(int index, double cost) {}
    /** A* on the local obstacle-edge grid in top-left configuration space. No mesh/model dependencies. */
    private static List<Point> route(Point start, Point goal, Bounds area, Collection<Point> obstacles, double width, double height) {
        var xSet=new TreeSet<Double>();var ySet=new TreeSet<Double>();
        xSet.add(start.x);xSet.add(goal.x);xSet.add(area.x);xSet.add(area.x+area.width-width);
        ySet.add(start.y);ySet.add(goal.y);ySet.add(area.y);ySet.add(area.y+area.height-height);
        for(var p:obstacles) {
            xSet.add(p.x-width-GAP);xSet.add(p.x+width+GAP);
            ySet.add(p.y-height-GAP);ySet.add(p.y+height+GAP);
        }
        var xs=xSet.stream().filter(x->x>=area.x&&x<=area.x+area.width-width).toList();
        var ys=ySet.stream().filter(y->y>=area.y&&y<=area.y+area.height-height).toList();
        int columns=xs.size(),count=columns*ys.size();
        int startX=xs.indexOf(start.x),startY=ys.indexOf(start.y),goalX=xs.indexOf(goal.x),goalY=ys.indexOf(goal.y);
        if(startX<0||startY<0||goalX<0||goalY<0)return List.of();
        int first=startY*columns+startX,last=goalY*columns+goalX;
        var points=new Point[count];var clear=new boolean[count];var distance=new double[count];var parent=new int[count];
        Arrays.fill(distance,Double.POSITIVE_INFINITY);Arrays.fill(parent,-1);
        for(int i=0;i<count;i++) {points[i]=new Point(xs.get(i%columns),ys.get(i/columns));clear[i]=free(points[i],obstacles,width,height);}
        var queue=new PriorityQueue<Visit>(Comparator.comparingDouble(Visit::cost).thenComparingInt(Visit::index));
        distance[first]=0;queue.add(new Visit(first,0));
        var closed=new boolean[count];
        while(!queue.isEmpty()) {
            int current=queue.remove().index;if(closed[current])continue;closed[current]=true;
            if(current==last) {
                var result=new ArrayList<Point>();
                while(current!=first&&parent[current]>=0){result.add(points[current]);current=parent[current];}
                Collections.reverse(result);return result;
            }
            int col=current%columns,row=current/columns;
            int[] neighbors={col>0?current-1:-1,col+1<columns?current+1:-1,row>0?current-columns:-1,row+1<ys.size()?current+columns:-1};
            for(int next:neighbors) {
                if(next<0||!clear[next]||closed[next])continue;
                var from=points[current];var to=points[next];
                // Every obstacle boundary belongs to the grid. Midpoint checks cover the open edge.
                if(!free(new Point((from.x+to.x)/2,(from.y+to.y)/2),obstacles,width,height))continue;
                double candidate=distance[current]+Math.abs(to.x-from.x)+Math.abs(to.y-from.y);
                if(candidate>=distance[next])continue;
                distance[next]=candidate;parent[next]=current;
                queue.add(new Visit(next,candidate+Math.abs(to.x-goal.x)+Math.abs(to.y-goal.y)));
            }
        }
        return List.of();
    }

    static boolean valid(Bounds b, double width, double height) {
        return Double.isFinite(b.x) && Double.isFinite(b.y) && Double.isFinite(b.width) && Double.isFinite(b.height)
                && width > 0 && height > 0 && b.width >= width && b.height >= height;
    }
    private static boolean finite(Point p) { return Double.isFinite(p.x) && Double.isFinite(p.y); }
    private static boolean inside(Point p, Bounds b, double w, double h) {
        return p.x>=b.x && p.y>=b.y && p.x+w<=b.x+b.width+.00001 && p.y+h<=b.y+b.height+.00001;
    }
    private static Point clamp(Point p, Bounds b, double w, double h) {
        return new Point(Math.max(b.x,Math.min(b.x+b.width-w,p.x)), Math.max(b.y,Math.min(b.y+b.height-h,p.y)));
    }
    public static boolean overlaps(Point a, Point b, double size, double gap) { return overlaps(a,b,size,size,gap); }
    public static boolean overlaps(Point a, Point b, double width, double height, double gap) {
        return Math.abs(a.x-b.x)<width+gap && Math.abs(a.y-b.y)<height+gap;
    }
    private static double distanceSquared(Point a, Point b) {
        double dx=a.x-b.x, dy=a.y-b.y; return dx*dx+dy*dy;
    }
}
