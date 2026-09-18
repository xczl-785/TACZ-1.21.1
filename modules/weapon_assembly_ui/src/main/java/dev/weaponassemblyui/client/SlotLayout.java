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
        private Map<List<String>,Point> lastSnapshot=Map.of();
        private Bounds lastArea;
        private double lastWidth,lastHeight;
        private boolean reflow;

        /** Request one deterministic full placement after an interaction boundary. */
        void reflow() { reflow=true; }

        public Map<List<String>, Point> update(List<Anchor> anchors, Bounds area, double width,
                double height) {
            // A transient invalid native layout must never replace the last usable frame.
            if (!valid(area, width, height) || anchors.stream().anyMatch(a -> !finite(a.point)))
                return snapshot();
            if(reflow) {
                positions.clear();mounts.clear();targets.clear();destinations.clear();
                reflow=false;
            }
            boolean structureChanged=anchors.size()!=mounts.size()||anchors.stream().anyMatch(a->!mounts.containsKey(a.path));
            boolean changed=structureChanged||anchors.stream().anyMatch(a->!a.point.equals(mounts.get(a.path)));
            boolean resized=!area.equals(lastArea)||width!=lastWidth||height!=lastHeight;
            if(!changed&&!resized)return snapshot();
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
                if (mount != null && !mount.equals(anchor.point))
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
                // rehome only excluded cards before solving in the new coordinate domain.
                var reserved=new ArrayList<Point>();
                for(var p:positions.values())if(inside(p,area,width,height))reserved.add(p);
                for(var entry:positions.entrySet())if(!inside(entry.getValue(),area,width,height)) {
                    var p=freeSite(clamp(entry.getValue(),area,width,height),null,area,width,height,reserved);
                    entry.setValue(p);targets.put(entry.getKey(),p);destinations.put(entry.getKey(),p);reserved.add(p);
                }
            }
            if(changed && !structureChanged && !resized) {
                // Camera motion is continuous even when projected mounts cross. Do not let the
                // sequential free-site search send one member of a collision group to a distant
                // temporary site: keep that group at its last legal positions while every other
                // card follows the current projection immediately.
                var frozen=new HashSet<List<String>>();
                for(int i=0;i<ordered.size();i++)for(int j=0;j<i;j++) {
                    var a=ordered.get(i).path;var b=ordered.get(j).path;
                    if(overlaps(clamp(targets.get(a),area,width,height),clamp(targets.get(b),area,width,height),width,height,GAP-.00001)) {
                        frozen.add(a);frozen.add(b);
                    }
                }
                boolean expanded;
                do {
                    expanded=false;
                    for(var anchor:ordered) {
                        if(frozen.contains(anchor.path))continue;
                        var desired=clamp(targets.get(anchor.path),area,width,height);
                        boolean blockedByFrozen=false;
                        for(var blocked:frozen)if(overlaps(desired,positions.get(blocked),width,height,GAP-.00001)) {
                            blockedByFrozen=true;break;
                        }
                        if(blockedByFrozen){frozen.add(anchor.path);expanded=true;}
                    }
                } while(expanded);
                destinations.clear();
                for(var anchor:ordered)destinations.put(anchor.path,frozen.contains(anchor.path)?positions.get(anchor.path):
                        clamp(targets.get(anchor.path),area,width,height));
            } else if(changed || resized) {
                var reserved=new LinkedHashMap<List<String>,Point>();
                var priority=new ArrayList<>(ordered);
                priority.sort(Comparator.comparingInt((Anchor a)->a.point.equals(previousMounts.get(a.path))?0:1)
                        .thenComparing(a->orderKeys.get(a.path)));
                for(var anchor:priority) {
                    var desired=anchor.point.equals(previousMounts.get(anchor.path))&&destinations.containsKey(anchor.path)?destinations.get(anchor.path):
                            clamp(targets.get(anchor.path),area,width,height);
                    desired=clamp(desired,area,width,height);
                    var destination=free(desired,reserved.values(),width,height)?desired:
                            freeSite(desired,null,area,width,height,reserved.values());
                    reserved.put(anchor.path,destination);
                }
                destinations.clear();destinations.putAll(reserved);
            }
            // The current camera frame owns the visible result. Cards snap to the resolved
            // non-overlapping destinations instead of integrating velocity across later frames.
            // A collision-adjusted destination also becomes the next frame's stable baseline;
            // otherwise the card keeps retrying its obstructed pre-collision target and visibly
            // flips between the old site and whichever temporary site the greedy pass found.
            positions.clear();positions.putAll(destinations);
            targets.clear();targets.putAll(destinations);
            return snapshot();
        }
        private Map<List<String>, Point> snapshot() {
            if(!lastSnapshot.equals(positions))lastSnapshot=Collections.unmodifiableMap(new LinkedHashMap<>(positions));
            return lastSnapshot;
        }
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
