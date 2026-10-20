package com.xirctwerk.vfx.core;

import java.util.*;
import static com.xirctwerk.vfx.core.Effect.*;

/** Bounded analytic fields and Catmull-Rom paths; no expressions, scripts or imported execution. */
public final class Motion {
    private Motion(){}
    public record Settings(double curl,double frequency,Vec attractor,double attraction,double falloff,Vec axis,double vortex,double pull,
                           Vec orbit,double orbitSpeed,List<Vec> path,boolean pathLoop,double cycles,double scatter,boolean align,double stretch){
        public static final Settings NONE=new Settings(0,1,Vec.ZERO,0,1,new Vec(0,1,0),0,0,Vec.ZERO,0,List.of(),false,1,0,false,0);
        public Settings{path=List.copyOf(path);}
        public Vec displacement(double age,double lifetime){if(orbit.equals(Vec.ZERO)&&path.isEmpty())return Vec.ZERO;double a=Math.toRadians(age*orbitSpeed);Vec ellipse=new Vec(Math.cos(a)*orbit.x(),Math.sin(a)*orbit.y(),Math.sin(a)*orbit.z());return ellipse.add(sample(path,Math.max(0,age/lifetime)*cycles,pathLoop));}
        public Vec acceleration(Vec relative,double time){
            if(attraction==0&&vortex==0&&pull==0&&curl==0)return Vec.ZERO;
            Vec delta=attractor.add(relative.scale(-1));double distance=delta.length();Vec result=distance<1e-9?Vec.ZERO:delta.normalize().scale(attraction/Math.pow(1+distance,falloff));
            Vec normal=axis.normalize();if((vortex!=0||pull!=0)&&normal.length()>1e-9){Vec radial=relative.add(attractor.scale(-1));double dot=radial.x()*normal.x()+radial.y()*normal.y()+radial.z()*normal.z();radial=radial.add(normal.scale(-dot));if(radial.length()>1e-9)result=result.add(Geometry.cross(normal,radial.normalize()).scale(vortex)).add(radial.normalize().scale(-pull));}
            return curl==0?result:result.add(Motion.curl(relative,time,frequency).scale(curl));
        }
        public double stretchFactor(Vec velocity){return Math.min(16,1+velocity.length()*stretch);}
    }
    /** Curl of a bounded three-octave vector potential. Its divergence is zero analytically. */
    public static Vec curl(Vec p,double time,double frequency){
        Vec result=Vec.ZERO;for(int i=0;i<3;i++){double f=frequency*(1<<i),a=1.0/(1<<i),phase=time*.7+i*2.17;result=result.add(new Vec(-Math.cos(p.z()*f+phase),-Math.cos(p.x()*f+phase*1.3),-Math.cos(p.y()*f-phase*.8)).scale(a));}return result;
    }
    public static Vec sample(List<Vec> points,double progress,boolean loop){
        if(points.isEmpty())return Vec.ZERO;if(points.size()==1)return points.getFirst();
        double t=loop?progress-Math.floor(progress):Math.max(0,Math.min(1,progress));int spans=loop?points.size():points.size()-1;double position=t*spans;int index=Math.min(spans-1,(int)Math.floor(position));double fraction=position-index;
        return Geometry.catmull(point(points,index-1,loop),point(points,index,loop),point(points,index+1,loop),point(points,index+2,loop),fraction);
    }
    private static Vec point(List<Vec> points,int i,boolean loop){return points.get(loop?Math.floorMod(i,points.size()):Math.max(0,Math.min(points.size()-1,i)));}
}
