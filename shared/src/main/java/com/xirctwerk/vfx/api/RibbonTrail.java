package com.xirctwerk.vfx.api;

import com.xirctwerk.vfx.core.Effect.*;
import com.xirctwerk.vfx.core.Geometry;
import net.minecraft.Util;
import java.util.*;

/** Nichirin-style frame-sampled base/tip ribbons. Capture animated endpoints in world space. */
public final class RibbonTrail implements AutoCloseable {
    public record Profile(long lifetimeMillis,int maxSamples,Color shadow,Color body,Color highlight,double opacity) {
        public Profile {if(lifetimeMillis<55||lifetimeMillis>5000||maxSamples<2||maxSamples>120||!Double.isFinite(opacity)||opacity<0||opacity>1) throw new IllegalArgumentException("Invalid trail profile");Objects.requireNonNull(shadow);Objects.requireNonNull(body);Objects.requireNonNull(highlight);}
    }
    private record Sample(Vec base,Vec tip,long time,Profile profile) {}
    private static final Set<RibbonTrail> active=new LinkedHashSet<>();
    private final Deque<Sample> samples=new ArrayDeque<>();private boolean closed;
    public RibbonTrail() {Vfx.thread();if(active.size()>=256) throw new IllegalStateException("256 trails already allocated");active.add(this);}
    public void capture(Vec base,Vec tip,Profile profile) {
        Vfx.thread();if(closed) throw new IllegalStateException("Trail closed");Objects.requireNonNull(profile);for(Vec v:List.of(base,tip)) if(!Double.isFinite(v.x())||!Double.isFinite(v.y())||!Double.isFinite(v.z())) throw new IllegalArgumentException("Trail endpoints must be finite");long now=Util.getMillis();prune(now);
        Sample last=samples.peekLast();if(last!=null&&distance(last.base,base)+distance(last.tip,tip)<.00008) return;
        samples.addLast(new Sample(base,tip,now,profile));while(samples.size()>profile.maxSamples()) samples.removeFirst();
    }
    public void render(Vfx.Canvas canvas) {
        long now=Util.getMillis();prune(now);Sample[] a=samples.toArray(Sample[]::new);
        for(int i=0;i+1<a.length;i++) {Sample p=a[i],q=a[i+1];if(distance(p.base,q.base)>9||distance(p.tip,q.tip)>16) continue;
            Sample before=a[Math.max(0,i-1)],after=a[Math.min(a.length-1,i+2)];
            for(int step=0;step<4;step++) {double t=step/4.,end=(step+1)/4.;
                Vec b0=Geometry.catmull(before.base,p.base,q.base,after.base,t),b1=Geometry.catmull(before.base,p.base,q.base,after.base,end);
                Vec t0=Geometry.catmull(before.tip,p.tip,q.tip,after.tip,t),t1=Geometry.catmull(before.tip,p.tip,q.tip,after.tip,end);
                double life=Math.max(0,1-(now-(p.time+(q.time-p.time)*(t+end)/2))/q.profile.lifetimeMillis());
                Profile f=q.profile;double alpha=life*life*f.opacity;
                Vec s0=lerp(b0,t0,.18),s1=lerp(b1,t1,.18),h0=lerp(b0,t0,.78),h1=lerp(b1,t1,.78);
                canvas.quad(new Geometry.Quad(b0,b1,s1,s0),opacity(f.shadow,alpha));canvas.quad(new Geometry.Quad(s0,s1,h1,h0),opacity(f.body,alpha));canvas.quad(new Geometry.Quad(h0,h1,t1,t0),opacity(f.highlight,alpha));
            }
        }
    }
    private void prune(long now) {samples.removeIf(s->now-s.time>s.profile.lifetimeMillis);}
    private static double distance(Vec a,Vec b) {double length=a.add(b.scale(-1)).length();return length*length;}
    private static Vec lerp(Vec a,Vec b,double t) {return a.scale(1-t).add(b.scale(t));}
    private static Color opacity(Color c,double a) {return new Color(c.r(),c.g(),c.b(),c.a()*a);}
    public void close() {Vfx.thread();closed=true;samples.clear();active.remove(this);}
    public static List<RibbonTrail> trails() {return List.copyOf(active);}
    static void clearAll() {for(var trail:List.copyOf(active)) trail.close();}
}
