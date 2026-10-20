package com.xirctwerk.vfx.core;

import java.util.*;
import static com.xirctwerk.vfx.core.Effect.*;

/** Bounded procedural mesh primitives shared by code, JSON playback and editor preview. */
public final class Geometry {
    private Geometry() {}
    public record Quad(Vec a, Vec b, Vec c, Vec d) {}
    public static boolean supports(String kind) { return Set.of("slash","ring_mesh","cube_mesh","sphere_mesh","lightning").contains(kind); }
    public static List<Quad> create(String kind, Vec origin, Vec direction, double radius, double width, double rotation, double progress) {
        if(!supports(kind)) throw new IllegalArgumentException("Unknown geometry: "+kind);
        Vec normal=direction.normalize();if(normal.length()<1e-9) normal=new Vec(0,0,1);
        Vec right=cross(Math.abs(normal.y())>.95?new Vec(1,0,0):new Vec(0,1,0),normal).normalize();
        Vec up=cross(normal,right);double angle=Math.toRadians(rotation);
        Vec r=right.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
        Vec u=up.scale(Math.cos(angle)).add(right.scale(-Math.sin(angle)));
        List<Quad> out=new ArrayList<>();
        if(kind.equals("lightning")) {
            Vec last=origin;int phase=(int)Math.floor(progress*18);
            for(int i=1;i<=24;i++) {double t=i/24.;double jitter=i==24?0:Math.sin(i*127.1+phase*37.7+rotation)*Math.cos(i*31.3-phase*11.9)*radius*.12;
                Vec next=origin.add(normal.scale(radius*t)).add(r.scale(jitter)).add(u.scale(i==24?0:Math.sin(i*71.9+phase*17.1)*radius*.045));
                Vec side=cross(next.add(last.scale(-1)).normalize(),u).normalize().scale(width*.5);
                out.add(new Quad(last.add(side),next.add(side),next.add(side.scale(-1)),last.add(side.scale(-1))));last=next;
            }
        } else if(kind.equals("slash")||kind.equals("ring_mesh")) {
            int count=kind.equals("slash")?24:32;double span=kind.equals("slash")?Math.PI:2*Math.PI;
            double reveal=kind.equals("slash")?Math.max(0,Math.min(1,progress*3)):1;
            for(int i=0;i<count;i++) {double t0=(double)i/count,t1=Math.min((double)(i+1)/count,reveal);if(t0>=reveal) break;
                double a=span*(t0-.5),b=span*(t1-.5),inner=Math.max(0,radius-width);
                out.add(new Quad(point(origin,r,u,inner,a),point(origin,r,u,inner,b),point(origin,r,u,radius,b),point(origin,r,u,radius,a)));
            }
        } else if(kind.equals("cube_mesh")) {
            Vec[] v=new Vec[8];for(int i=0;i<8;i++) v[i]=origin.add(r.scale((i&1)==0?-radius:radius)).add(u.scale((i&2)==0?-radius:radius)).add(normal.scale((i&4)==0?-radius:radius));
            for(int[] f:new int[][]{{0,1,3,2},{4,6,7,5},{0,4,5,1},{2,3,7,6},{0,2,6,4},{1,5,7,3}}) out.add(new Quad(v[f[0]],v[f[1]],v[f[2]],v[f[3]]));
        } else {
            for(int y=0;y<8;y++) for(int x=0;x<16;x++) out.add(new Quad(sphere(origin,r,u,normal,radius,x,y),sphere(origin,r,u,normal,radius,x+1,y),sphere(origin,r,u,normal,radius,x+1,y+1),sphere(origin,r,u,normal,radius,x,y+1)));
        }
        return List.copyOf(out);
    }
    public static List<Quad> create(Simulation.Particle particle,Vec origin){
        var e=particle.emitter;
        var local=create(e.kind(),Vec.ZERO,e.direction(),e.radius()*particle.meshScale(),particle.size()/particle.transformScale()*particle.meshScale(),particle.angle(),particle.normalizedAge());
        return local.stream().map(q->new Quad(particle.visualPoint(origin,q.a()),particle.visualPoint(origin,q.b()),particle.visualPoint(origin,q.c()),particle.visualPoint(origin,q.d()))).toList();
    }
    private static Vec point(Vec o,Vec r,Vec u,double radius,double a) {return o.add(r.scale(Math.cos(a)*radius)).add(u.scale(Math.sin(a)*radius));}
    private static Vec sphere(Vec o,Vec r,Vec u,Vec n,double radius,int x,int y) {double a=x*Math.PI/8,b=y*Math.PI/8-Math.PI/2;return o.add(r.scale(radius*Math.cos(b)*Math.cos(a))).add(u.scale(radius*Math.cos(b)*Math.sin(a))).add(n.scale(radius*Math.sin(b)));}
    public static Vec cross(Vec a,Vec b) {return new Vec(a.y()*b.z()-a.z()*b.y(),a.z()*b.x()-a.x()*b.z(),a.x()*b.y()-a.y()*b.x());}
    public static Vec catmull(Vec a,Vec b,Vec c,Vec d,double t) {double t2=t*t,t3=t2*t;return b.scale(2).add(c.add(a.scale(-1)).scale(t)).add(a.scale(2).add(b.scale(-5)).add(c.scale(4)).add(d.scale(-1)).scale(t2)).add(a.scale(-1).add(b.scale(3)).add(c.scale(-3)).add(d).scale(t3)).scale(.5);}
}
