package com.xirctwerk.vfx.core;

import java.util.*;
import static com.xirctwerk.vfx.core.Effect.*;

/** Uniform affine hierarchy. Keys use effect-clock seconds, independently of particle age. */
public final class Transforms {
    private Transforms() {}
    public record Key(double time,Vec position,Vec rotation,double scale,Easing easing) {}
    public record Track(Vec rotation,double scale,List<Key> keys,String pathInterpolation) {
        public Track(Vec rotation,double scale,List<Key> keys){this(rotation,scale,keys,"linear");}
        public static final Track IDENTITY=new Track(Vec.ZERO,1,List.of());
        public Track {keys=List.copyOf(keys);}
        public Pose at(Vec offset,double seconds) {
            if(keys.isEmpty())return Pose.euler(offset,rotation,scale);
            Key a=keys.getFirst(),b=a;
            for(var next:keys){b=next;if(seconds<=next.time())break;a=next;}
            double t=a==b?0:a.easing().at((seconds-a.time())/(b.time()-a.time()));
            Vec position=mix(a.position(),b.position(),t);
            if(pathInterpolation.equals("spline")&&a!=b){int i=keys.indexOf(a);position=Geometry.catmull(keys.get(Math.max(0,i-1)).position(),a.position(),b.position(),keys.get(Math.min(keys.size()-1,i+2)).position(),t);}
            return Pose.euler(offset.add(position),rotation.add(mix(a.rotation(),b.rotation(),t)),scale*(a.scale()+(b.scale()-a.scale())*t));
        }
        public double maximumScale(){return scale*keys.stream().mapToDouble(Key::scale).max().orElse(1);}
        public double minimumScale(){return scale*keys.stream().mapToDouble(Key::scale).min().orElse(1);}
        public double positionRadius(){return keys.stream().mapToDouble(k->k.position().length()).max().orElse(0)*(pathInterpolation.equals("spline")?2:1);}
    }
    private static Vec mix(Vec a,Vec b,double t){return a.add(b.add(a.scale(-1)).scale(t));}
    private static double dot(Vec a,Vec b){return a.x()*b.x()+a.y()*b.y()+a.z()*b.z();}
    public record Pose(Vec position,Vec x,Vec y,Vec z,double scale) {
        public static final Pose IDENTITY=euler(Vec.ZERO,Vec.ZERO,1);
        public Vec unit(Vec v){return x.scale(v.x()).add(y.scale(v.y())).add(z.scale(v.z()));}
        public Vec vector(Vec v){return unit(v).scale(scale);}
        public Vec point(Vec v){return position.add(vector(v));}
        public Vec inverseVector(Vec v){return new Vec(dot(v,x),dot(v,y),dot(v,z)).scale(1/scale);}
        public Vec inversePoint(Vec v){return inverseVector(v.add(position.scale(-1)));}
        public Pose compose(Pose child){return new Pose(point(child.position),unit(child.x),unit(child.y),unit(child.z),scale*child.scale);}
        public static Pose euler(Vec position,Vec degrees,double scale){
            return new Pose(position,rotate(new Vec(1,0,0),degrees),rotate(new Vec(0,1,0),degrees),rotate(new Vec(0,0,1),degrees),scale);
        }
        private static Vec rotate(Vec v,Vec degrees){
            double a=Math.toRadians(degrees.x()),b=Math.toRadians(degrees.y()),c=Math.toRadians(degrees.z());
            double y=v.y()*Math.cos(a)-v.z()*Math.sin(a),z=v.y()*Math.sin(a)+v.z()*Math.cos(a);
            double x=v.x()*Math.cos(b)+z*Math.sin(b);z=-v.x()*Math.sin(b)+z*Math.cos(b);
            return new Vec(x*Math.cos(c)-y*Math.sin(c),x*Math.sin(c)+y*Math.cos(c),z);
        }
    }
    public static Map<String,Pose> sample(Effect effect,double seconds){
        var layers=new HashMap<String,Emitter>();effect.emitters().forEach(e->layers.put(e.id(),e));
        var poses=new HashMap<String,Pose>();
        for(var emitter:effect.emitters())sample(emitter,layers,poses,seconds);
        return poses;
    }
    private static Pose sample(Emitter emitter,Map<String,Emitter> layers,Map<String,Pose> poses,double seconds){
        if(poses.containsKey(emitter.id()))return poses.get(emitter.id());
        Pose pose=emitter.transform().at(emitter.offset(),seconds);
        if(!emitter.parent().isEmpty())pose=sample(layers.get(emitter.parent()),layers,poses,seconds).compose(pose);
        poses.put(emitter.id(),pose);return pose;
    }
}
