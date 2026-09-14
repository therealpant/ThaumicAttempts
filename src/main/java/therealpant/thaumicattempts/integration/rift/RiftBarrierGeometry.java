package therealpant.thaumicattempts.integration.rift;

import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.List;

/** Local well coordinates shared by the rune surface, moving front and its contact effects. */
public final class RiftBarrierGeometry {
    public static final int SIDES = 12;
    public static final double RADIUS = 5.75D;
    public static final double APOTHEM = RADIUS * Math.cos(Math.PI / SIDES);
    // Transformed model corners reach 123.96 px; rounded up to one model pixel.
    public static final double MODEL_HEIGHT = 124.0D / 16.0D;
    public static final double HEIGHT = MODEL_HEIGHT + 1.0D;
    public static final double FLIGHT_END = 1.12D;
    private static final Vec3d[] NORMALS = new Vec3d[SIDES + 2];
    static {
        for (int i=0;i<SIDES;i++) {
            double angle=2*Math.PI*(i+.5D)/SIDES;
            NORMALS[i]=new Vec3d(Math.cos(angle),0,Math.sin(angle));
        }
        NORMALS[SIDES]=new Vec3d(0,1,0);
        NORMALS[SIDES+1]=new Vec3d(0,-1,0);
    }

    private RiftBarrierGeometry() {}

    public static Vec3d normal(int face) { return NORMALS[face]; }

    private static double separation(Vec3d point,int face) {
        if (face==SIDES) return point.y-HEIGHT;
        if (face==SIDES+1) return -point.y;
        Vec3d normal=NORMALS[face];
        return (point.x-.5D)*normal.x+(point.z-.5D)*normal.z-APOTHEM;
    }

    public static boolean contains(Vec3d point,double tolerance) {
        for (int face=0;face<NORMALS.length;face++)
            if (separation(point,face)>tolerance) return false;
        return true;
    }

    /** First actual surface crossing, including entry from a rift point above the roof. */
    public static Contact intersect(Vec3d from,Vec3d to) {
        double closest=Double.POSITIVE_INFINITY;
        Contact result=null;
        for (int face=0;face<NORMALS.length;face++) {
            double a=separation(from,face),b=separation(to,face);
            if (Math.abs(a-b)<1.0E-9D) continue;
            double fraction=a/(a-b);
            if (fraction<0 || fraction>1 || fraction>=closest) continue;
            Vec3d point=from.add(to.subtract(from).scale(fraction));
            if (contains(point,1.0E-6D)) {
                closest=fraction;
                result=new Contact(point,face,fraction);
            }
        }
        return result;
    }

    public static final class Contact {
        public final Vec3d point;
        public final int face;
        public final double fraction;
        private Contact(Vec3d point,int face,double fraction) {
            this.point=point; this.face=face; this.fraction=fraction;
        }
    }

    /** Samples the moving front, rather than projecting effects onto the nominal target. */
    public static List<ArcContact> arcContacts(Vec3d start,Vec3d target) {
        List<ArcContact> result=new ArrayList<>();
        for (int sample=0;sample<=24;sample++) {
            double angular=-1.0D+sample/12.0D;
            Vec3d previous=start;
            for (int step=1;step<=64;step++) {
                double motion=FLIGHT_END*step/64.0D;
                Vec3d next=new Arc(start,target,motion).point(angular,0);
                Contact hit=intersect(previous,next);
                if (hit!=null) {
                    double at=FLIGHT_END*(step-1+hit.fraction)/64.0D;
                    boolean duplicate=false;
                    for (ArcContact existing:result) if (existing.face==hit.face
                            && existing.point.squareDistanceTo(hit.point)<.09D
                            && Math.abs(existing.motion-at)<.08D) { duplicate=true; break; }
                    if (!duplicate) result.add(new ArcContact(hit.point,hit.face,at));
                    break;
                }
                previous=next;
            }
        }
        return result;
    }

    public static final class ArcContact {
        public final Vec3d point;
        public final int face;
        public final double motion;
        private ArcContact(Vec3d point,int face,double motion) {
            this.point=point; this.face=face; this.motion=motion;
        }
    }

    public static final class Arc {
        public final Vec3d origin,forward,side;
        public final double radius,halfAngle;
        public Arc(Vec3d start,Vec3d target,double progress) {
            Vec3d delta=target.subtract(start);
            double distance=Math.sqrt(delta.lengthSquared());
            forward=distance<1.0E-6D ? new Vec3d(1,0,0) : delta.scale(1.0D/distance);
            Vec3d up=new Vec3d(0,1,0);
            Vec3d vertical=up.subtract(forward.scale(forward.y));
            if (vertical.lengthSquared()<1.0E-6D) vertical=new Vec3d(1,0,0);
            side=forward.crossProduct(vertical).normalize();
            origin=start.add(delta.scale(.78D*progress));
            radius=distance*.22D*progress;
            halfAngle=Math.toRadians(9.0D+24.0D*Math.min(1,progress));
        }
        public Vec3d point(double angular,double radialOffset) {
            double angle=angular*halfAngle;
            return origin.add(forward.scale(Math.cos(angle)*Math.max(0,radius+radialOffset)))
                    .add(side.scale(Math.sin(angle)*Math.max(0,radius+radialOffset)));
        }
    }
}
