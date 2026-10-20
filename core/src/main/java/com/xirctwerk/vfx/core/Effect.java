package com.xirctwerk.vfx.core;

import java.util.List;
import java.util.Random;

/** Immutable compiled definition. Units are blocks, seconds and degrees. */
public record Effect(String id, String name, double duration, boolean loop, long seed, List<Emitter> emitters, Vec pivot,Vec wind) {
    public Effect(String id,String name,double duration,boolean loop,long seed,List<Emitter> emitters,Vec pivot){this(id,name,duration,loop,seed,emitters,pivot,Vec.ZERO);}
    public Effect(String id,String name,double duration,boolean loop,long seed,List<Emitter> emitters){this(id,name,duration,loop,seed,emitters,Vec.ZERO);}
    public record Vec(double x, double y, double z) {
        public static final Vec ZERO = new Vec(0, 0, 0);
        public Vec add(Vec v) { return new Vec(x + v.x, y + v.y, z + v.z); }
        public Vec scale(double n) { return new Vec(x * n, y * n, z * n); }
        public double length() { return Math.sqrt(x*x+y*y+z*z); }
        public Vec normalize() { return length() < 1e-9 ? ZERO : scale(1/length()); }
    }
    /** Segment-local easing; bounded controls keep values within the authored endpoint range. */
    public record Easing(String mode,double x1,double y1,double x2,double y2) {
        public static final Easing LINEAR=new Easing("linear",.25,.25,.75,.75);
        private static double cubic(double t,double a,double b){double u=1-t;return 3*u*u*t*a+3*u*t*t*b+t*t*t;}
        public double at(double fraction){double t=Math.max(0,Math.min(1,fraction));return switch(mode){
            case "step"->t>=1?1:0;case "smooth"->t*t*(3-2*t);
            case "bezier"->{double lo=0,hi=1;for(int i=0;i<32;i++){double mid=(lo+hi)/2;if(cubic(mid,x1,x2)<t)lo=mid;else hi=mid;}yield cubic((lo+hi)/2,y1,y2);}
            default->t;};}
    }
    public record Key(double time, double value,Easing easing) {public Key(double time,double value){this(time,value,Easing.LINEAR);}}
    public record Value(double min, double max, List<Key> curve) {
        public static Value constant(double n) { return new Value(n, n, List.of()); }
        public double sample(Random r) { return curve.isEmpty()?min + r.nextDouble() * (max - min):curve.getFirst().value(); }
        public double at(double t, double sampled) {
            if (curve.isEmpty()) return sampled;
            if (t <= curve.getFirst().time) return curve.getFirst().value;
            for (int i = 1; i < curve.size(); i++) {
                Key a = curve.get(i-1), b = curve.get(i);
                if (t <= b.time) return a.value + (b.value-a.value)*a.easing.at((t-a.time)/(b.time-a.time));
            }
            return curve.getLast().value;
        }
    }
    public record Color(double r, double g, double b, double a) {
        public Color mix(Color c, double t) { return new Color(r+(c.r-r)*t,g+(c.g-g)*t,b+(c.b-b)*t,a+(c.a-a)*t); }
        public int argb() { return ((int)(a*255)<<24)|((int)(r*255)<<16)|((int)(g*255)<<8)|(int)(b*255); }
    }
    public record ColorKey(double time, Color color,Easing easing) {public ColorKey(double time,Color color){this(time,color,Easing.LINEAR);}}
    public record Gradient(List<ColorKey> keys) {
        public Color at(double t) {
            if (t <= keys.getFirst().time) return keys.getFirst().color;
            for(int i=1;i<keys.size();i++) {
                var a=keys.get(i-1); var b=keys.get(i);
                if(t<=b.time) return a.color.mix(b.color,a.easing.at((t-a.time)/(b.time-a.time)));
            }
            return keys.getLast().color;
        }
    }
    public record Burst(double time, int count) {}
    public record Uv(double u0,double v0,double u1,double v1) {}
    public record TextureAnimation(int columns,int rows,int frames,double fps,boolean loop) {
        public static final TextureAnimation STILL=new TextureAnimation(1,1,1,1,false);
        public Uv uv(double age) {int frame=(int)Math.floor(Math.max(0,age)*fps);frame=loop?frame%frames:Math.min(frames-1,frame);int x=frame%columns,y=frame/columns;return new Uv(x/(double)columns,y/(double)rows,(x+1)/(double)columns,(y+1)/(double)rows);}
    }
    public record Emitter(String id, String parent, String kind, String space, Vec offset, double start,
                          double duration, double rate, List<Burst> bursts, Value lifetime, Value size,
                          Value speed, Value rotation, Gradient color, String shape, double radius,
                          double spread, Vec direction, Vec force, double drag, double noise,
                          boolean collision, double bounce, String texture, boolean glow, double fade,
                          double length, int trailPoints, int pixelSize, Value meshScale,
                          double bloom,String blend,TextureAnimation textureAnimation,double opacity,double spin,double aspect,Motion.Settings motion,Transforms.Track transform) {
        public Emitter(String id, String parent, String kind, String space, Vec offset, double start,
                          double duration, double rate, List<Burst> bursts, Value lifetime, Value size,
                          Value speed, Value rotation, Gradient color, String shape, double radius,
                          double spread, Vec direction, Vec force, double drag, double noise,
                          boolean collision, double bounce, String texture, boolean glow, double fade,
                          double length, int trailPoints, int pixelSize, Value meshScale,
                          double bloom,String blend,TextureAnimation textureAnimation,double opacity,double spin,double aspect,Motion.Settings motion){this(id,parent,kind,space,offset,start,duration,rate,bursts,lifetime,size,speed,rotation,color,shape,radius,spread,direction,force,drag,noise,collision,bounce,texture,glow,fade,length,trailPoints,pixelSize,meshScale,bloom,blend,textureAnimation,opacity,spin,aspect,motion,Transforms.Track.IDENTITY);}
    }
}
