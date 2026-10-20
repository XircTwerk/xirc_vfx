package com.xirctwerk.vfx.core;

import java.util.*;
import static com.xirctwerk.vfx.core.Effect.*;

/** Camera framing only: never rescales or modifies authored geometry. */
public final class SceneBounds {
    public record Box(Vec min,Vec max) {}
    private Vec min=new Vec(Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY),max=new Vec(Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY);
    private void include(Vec p,double r){min=new Vec(Math.min(min.x(),p.x()-r),Math.min(min.y(),p.y()-r),Math.min(min.z(),p.z()-r));max=new Vec(Math.max(max.x(),p.x()+r),Math.max(max.y(),p.y()+r),Math.max(max.z(),p.z()+r));}
    private Box box(){if(!Double.isFinite(min.x()))include(Vec.ZERO,.5);return new Box(min,max);}
    private static double maximum(Value value){return Math.max(Math.max(Math.abs(value.min()),Math.abs(value.max())),value.curve().stream().mapToDouble(k->Math.abs(k.value())).max().orElse(0));}
    private void visual(Emitter e,Vec p,double size,double scale){double radius=Geometry.supports(e.kind())?e.radius()*scale*(e.kind().equals("cube_mesh")?Math.sqrt(3):1)+size*scale/2:size/2;include(p,Math.max(.01,radius));if(e.kind().equals("beam"))include(p.add(e.direction().normalize().scale(e.length())),size/2);}
    public static Box authored(Effect effect){var b=new SceneBounds();if(effect.emitters().stream().anyMatch(e->!e.transform().equals(Transforms.Track.IDENTITY)))return transformed(effect);var layers=new HashMap<String,Emitter>();effect.emitters().forEach(e->layers.put(e.id(),e));for(var e:effect.emitters()){Vec p=e.offset().add(effect.pivot().scale(-1));for(String parent=e.parent();!parent.isEmpty();){var ancestor=layers.get(parent);p=p.add(ancestor.offset());parent=ancestor.parent();}double spawn=switch(e.shape()){case "sphere","ring","spiral"->e.radius();case "box"->e.radius()*Math.sqrt(3);case "cone"->e.radius()*e.spread();default->0;};b.visual(e,p,maximum(e.size())*Math.max(1,e.aspect())*(e.motion().stretch()>0?16:1)+spawn*2+2*(e.motion().path().stream().mapToDouble(Vec::length).max().orElse(0)*2+e.motion().orbit().length()+e.motion().scatter()),maximum(e.meshScale()));if(e.shape().equals("spiral"))b.include(p.add(new Vec(0,e.length(),0)),spawn);}return b.box();}
    private static Box transformed(Effect effect){
        var b=new SceneBounds();var layers=new HashMap<String,Emitter>();effect.emitters().forEach(e->layers.put(e.id(),e));
        for(var emitter:effect.emitters()){
            double size=maximum(emitter.size())*Math.max(1,emitter.aspect())*(emitter.motion().stretch()>0?16:1);
            double spawn=switch(emitter.shape()){case "sphere","ring","spiral"->emitter.radius();case "box"->emitter.radius()*Math.sqrt(3);case "cone"->emitter.radius()*emitter.spread();default->0;};
            double mesh=Geometry.supports(emitter.kind())?emitter.radius()*maximum(emitter.meshScale())*(emitter.kind().equals("cube_mesh")?Math.sqrt(3):emitter.kind().equals("lightning")?1.2:1)+size*maximum(emitter.meshScale())/2:size/2;
            double radius=spawn+mesh;
            radius+=emitter.motion().path().stream().mapToDouble(Vec::length).max().orElse(0)*2+emitter.motion().orbit().length()+emitter.motion().scatter();
            if(emitter.kind().equals("beam")||emitter.shape().equals("spiral"))radius+=emitter.length();
            for(var node=emitter;node!=null;node=node.parent().isEmpty()?null:layers.get(node.parent()))radius=radius*node.transform().maximumScale()+node.offset().length()+node.transform().positionRadius();
            b.include(effect.pivot().scale(-1),Math.max(.01,radius));
        }return b.box();
    }
    public static Box live(List<Simulation.Particle> particles){if(particles.isEmpty())throw new IllegalArgumentException("No live particles to frame");var b=new SceneBounds();for(var p:particles){b.visual(p.emitter,p.position,p.size()*Math.max(1,p.emitter.aspect()*p.emitter.motion().stretchFactor(p.renderVelocity())),p.meshScale()*p.transformScale());if(p.emitter.kind().equals("beam"))b.include(p.position.add(p.direction().normalize().scale(p.emitter.length()*p.transformScale())),p.size()/2);for(var point:p.history)b.include(point,p.size()/2);}return b.box();}
}
