package com.xirctwerk.vfx.core;

import java.util.*;
import static com.xirctwerk.vfx.core.Effect.*;

/** Fixed 20 Hz simulation, shared by the editor and world renderer. */
public final class Simulation {
    public static final int MAX_PARTICLES=8192;
    public static final double DT=0.05;
    public interface Collision { boolean solid(Vec point); }
    public static final Collision NO_COLLISION=p->false;
    public static final class Particle {
        public final Emitter emitter;
        public Vec position, previous, velocity;
        public final double lifetime, size, speed, rotation, sampledMeshScale;
        public double age;
        private Transforms.Pose frame;
        public final Deque<Vec> history=new ArrayDeque<>();
        private Particle(Emitter e,Vec p,Vec v,double life,double size,double speed,double rotation,double meshScale,Transforms.Pose frame) {
            this.frame=frame;this.emitter=e; this.position=p; this.previous=p; this.velocity=v; this.lifetime=life; this.size=size; this.speed=speed; this.rotation=rotation;this.sampledMeshScale=meshScale;
        }
        public double normalizedAge() { return Math.min(1,age/lifetime); }
        public double size() { return emitter.size().at(normalizedAge(),size)*frame.scale(); }
        public double transformScale(){return frame.scale();}
        public Vec direction(){return frame.unit(emitter.direction());}
        public Vec visualPoint(Vec origin,Vec local){return origin.add(frame.vector(local));}
        public double angle() { return emitter.rotation().at(normalizedAge(),rotation)+emitter.spin()*age; }
        public Vec renderVelocity(){return position.add(previous.scale(-1)).scale(1/DT);}
        public double meshScale() { return emitter.meshScale().at(normalizedAge(),sampledMeshScale); }
        public Vec interpolated(double partial) {return previous.add(position.add(previous.scale(-1)).scale(Math.max(0,Math.min(1,partial))));}
        public double interpolatedAge(double partial) {return Math.max(0,age-DT*(1-Math.max(0,Math.min(1,partial))));}
        public Color color() {
            Color c=emitter.color().at(normalizedAge()); double fade=emitter.fade();
            double alpha=fade<=0?1:Math.min(1,(1-normalizedAge())/fade);
            return new Color(c.r(),c.g(),c.b(),c.a()*alpha*emitter.opacity());
        }
        public Vec world(Vec anchor) { return emitter.space().equals("local")?position.add(anchor):position; }
    }
    private final Effect effect;
    private final Random random;
    private final List<Particle> particles=new ArrayList<>();
    private final Map<String,Double> carry=new HashMap<>();
    private Map<String,Transforms.Pose> poses=Map.of(),birthPoses=Map.of();
    private double time;
    private long ticks;
    private int dropped;
    private int capacity=MAX_PARTICLES;
    private Set<String> muted=Set.of();
    public Set<String> muted() {return muted;}
    public void setMuted(Set<String> ids) {muted=Set.copyOf(ids);particles.removeIf(p->muted.contains(p.emitter.id()));muted.forEach(carry::remove);}
    public Simulation(Effect effect) { this(effect,effect.seed()); }
    public Simulation(Effect effect,long seed) { this.effect=effect; random=new Random(seed); }
    private Transforms.Pose instancePose=Transforms.Pose.IDENTITY;
    public void setInstancePose(Transforms.Pose pose){instancePose=Objects.requireNonNull(pose);}
    public List<Particle> particles() { return Collections.unmodifiableList(particles); }
    public double time() { return time; }
    public int dropped() { return dropped; }
    public boolean finished() { return !effect.loop()&&time>=effect.duration()&&particles.isEmpty(); }
    public Effect effect() { return effect; }
    public void tick(Vec anchor,Collision collision) {
        tick(anchor,collision,MAX_PARTICLES);
    }
    public void tick(Vec anchor,Collision collision,int budget) {
        capacity=Math.max(0,Math.min(MAX_PARTICLES,budget));
        while(particles.size()>capacity) { particles.removeLast(); dropped++; }
        double before=time; time=(++ticks)*DT;
        poses=posesAt(time);
        for(Iterator<Particle> it=particles.iterator();it.hasNext();) {
            Particle p=it.next(); p.age+=DT;
            if(p.age>=p.lifetime) { it.remove(); continue; }
            Emitter e=p.emitter; p.previous=p.position;
            var current=poses.get(e.id());
            if(e.space().equals("local")&&!p.frame.equals(current)){
                var old=p.frame;p.position=current.point(old.inversePoint(p.position));p.velocity=current.vector(old.inverseVector(p.velocity));
                var history=new ArrayList<Vec>();for(var point:p.history)history.add(current.point(old.inversePoint(point)));p.history.clear();p.history.addAll(history);p.frame=current;
            }
            p.history.addFirst(p.position); while(p.history.size()>e.trailPoints()) p.history.removeLast();
            Vec n=new Vec(Math.sin(p.age*5+p.rotation),Math.cos(p.age*7+p.rotation),Math.sin(p.age*3-p.rotation)).scale(e.noise());
            Vec origin=current.position();if(e.space().equals("world"))origin=origin.add(anchor);
            var field=e.motion().acceleration(p.frame.inverseVector(p.position.add(origin.scale(-1))),before);
            p.velocity=p.velocity.add(p.frame.vector(e.force().add(n).add(field)).add(effect.wind()).scale(DT)).scale(Math.exp(-e.drag()*DT));
            double base=Math.abs(p.speed)>1e-8?p.speed:1;
            double multiplier=e.speed().curve().isEmpty()?1:e.speed().at(p.normalizedAge(),p.speed)/base;
            Vec next=p.position.add(p.velocity.scale(DT*multiplier)).add(p.frame.vector(e.motion().displacement(p.age,p.lifetime).add(e.motion().displacement(p.age-DT,p.lifetime).scale(-1))));
            Vec world=e.space().equals("local")?next.add(anchor):next;
            if(e.collision()&&collision.solid(world)) p.velocity=p.velocity.scale(-e.bounce()); else p.position=next;
        }
        if(!effect.loop()&&before>=effect.duration()) return;
        birthPoses=posesAt(before);
        if(!effect.loop()) emitInterval(before,Math.min(time,effect.duration()),anchor);
        else {
            double cursor=before;
            while(cursor<time-1e-9) {
                double cycle=Math.floor((cursor+1e-9)/effect.duration())*effect.duration();
                double end=Math.min(time,cycle+effect.duration());
                emitInterval(Math.max(0,cursor-cycle),end-cycle,anchor);
                if(end>=cycle+effect.duration()-1e-9) carry.clear();
                cursor=end;
            }
        }
    }
    private void emitInterval(double a,double b,Vec anchor) {
        for(Emitter e:effect.emitters()) {
            if(muted.contains(e.id()))continue;
            double overlap=Math.max(0,Math.min(b,e.start()+e.duration())-Math.max(a,e.start()));
            if(overlap>0) {
                double count=carry.getOrDefault(e.id(),0d)+overlap*e.rate(); int whole=(int)Math.floor(count+1e-9); carry.put(e.id(),count-whole);
                for(int j=0;j<Math.min(whole,MAX_PARTICLES);j++) spawn(e,anchor,a);
            }
            for(Burst burst:e.bursts()) { double at=e.start()+burst.time(); if(at>=a-1e-9&&at<b-1e-9) for(int j=0;j<burst.count();j++) spawn(e,anchor,a); }
        }
    }
    private Map<String,Transforms.Pose> posesAt(double seconds){
        var sampled=Transforms.sample(effect,effect.loop()?seconds%effect.duration():seconds);
        sampled.replaceAll((id,p)->instancePose.compose(new Transforms.Pose(p.position().add(effect.pivot().scale(-1)),p.x(),p.y(),p.z(),p.scale())));return sampled;
    }
    private void spawn(Emitter e,Vec anchor,double t) {
        if(particles.size()>=capacity) { dropped++; return; }
        double angle=random.nextDouble()*Math.PI*2, y=random.nextDouble()*2-1;
        Vec radial=new Vec(Math.sqrt(1-y*y)*Math.cos(angle),y,Math.sqrt(1-y*y)*Math.sin(angle));
        Vec point=switch(e.shape()) {
            case "sphere" -> radial.scale(e.radius()*Math.cbrt(random.nextDouble()));
            case "ring" -> new Vec(Math.cos(angle)*e.radius(),0,Math.sin(angle)*e.radius());
            case "spiral" -> new Vec(Math.cos(t*4+angle*e.spread())*e.radius(),(t%effect.duration())/effect.duration()*e.length(),Math.sin(t*4+angle*e.spread())*e.radius());
            case "box" -> new Vec(random.nextDouble()*2-1,random.nextDouble()*2-1,random.nextDouble()*2-1).scale(e.radius());
            case "cone" -> radial.scale(e.radius()*random.nextDouble()*e.spread());
            default -> Vec.ZERO;
        };
        double speed=e.speed().sample(random);
        Vec direction=e.direction().normalize().add(radial.scale(e.spread())).normalize();
        var frame=(e.space().equals("local")?poses:birthPoses).get(e.id());Vec position=point;
        double lifetime=e.lifetime().sample(random);
        position=position.add(e.motion().displacement(0,lifetime));if(!e.motion().path().isEmpty()&&e.motion().scatter()>0)position=position.add(radial.scale(e.motion().scatter()*random.nextDouble()));
        position=frame.point(position);if(e.space().equals("world"))position=position.add(anchor);
        double baseline=speed==0&&!e.speed().curve().isEmpty()?1:speed;
        particles.add(new Particle(e,position,frame.vector(direction.scale(baseline)),lifetime,e.size().sample(random),speed,e.rotation().sample(random),(e.meshScale().min()==e.meshScale().max()?e.meshScale().min():e.meshScale().sample(random)),frame));
    }
}
