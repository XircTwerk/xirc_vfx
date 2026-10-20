package com.xirctwerk.vfx.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.xirctwerk.vfx.core.Effect.Vec;

class GeometryTest {
    @Test void lightningHasStableEndpointsAndAnimatedDeterministicTopology() {
        Vec origin=new Vec(1,2,3),direction=new Vec(0,0,1);var first=Geometry.create("lightning",origin,direction,4,.08,20,.1);var next=Geometry.create("lightning",origin,direction,4,.08,20,.3);
        assertEquals(24,first.size());assertEquals(first,Geometry.create("lightning",origin,direction,4,.08,20,.1));assertNotEquals(first,next);
        assertTrue(first.getFirst().a().add(first.getFirst().d()).scale(.5).add(origin.scale(-1)).length()<1e-8);
        assertTrue(first.getLast().b().add(first.getLast().c()).scale(.5).add(origin.add(direction.scale(4)).scale(-1)).length()<1e-8);
    }
    @Test void primitivesHaveBoundedTopologyAndRespectOrigin() {
        Vec origin=new Vec(10,20,30);
        for(String kind:new String[]{"slash","ring_mesh","cube_mesh","sphere_mesh"}) {
            var quads=Geometry.create(kind,origin,Vec.ZERO,2,.4,45,1);
            assertEquals(switch(kind) {case "slash"->24;case "ring_mesh"->32;case "cube_mesh"->6;default->128;},quads.size());
            for(var q:quads) for(Vec v:new Vec[]{q.a(),q.b(),q.c(),q.d()}) assertTrue(v.add(origin.scale(-1)).length()<=Math.sqrt(12)+1e-8);
        }
    }
    @Test void slashRevealAndRingClosure() {
        assertTrue(Geometry.create("slash",Vec.ZERO,new Vec(0,0,1),1,.2,0,0).isEmpty());
        assertEquals(12,Geometry.create("slash",Vec.ZERO,new Vec(0,0,1),1,.2,0,1./6).size());
        var ring=Geometry.create("ring_mesh",Vec.ZERO,new Vec(0,0,1),1,.2,0,1);
        assertTrue(ring.getFirst().d().add(ring.getLast().c().scale(-1)).length()<1e-8);
    }
    @Test void splineJoinsCapturedEndpoints() {
        Vec a=new Vec(-1,2,3),b=new Vec(1,4,2),c=new Vec(3,1,5),d=new Vec(8,2,0);
        assertEquals(b,Geometry.catmull(a,b,c,d,0));assertEquals(c,Geometry.catmull(a,b,c,d,1));
    }
    @Test void geometryDefinitionsValidateBudgetsAndPixelBounds() {
        var json=Templates.create("pixel_slash");assertEquals(6,EffectCodec.compile(json).emitters().getFirst().pixelSize());
        var e=json.getAsJsonArray("emitters").get(0).getAsJsonObject();e.addProperty("pixelSize",9);assertThrows(IllegalArgumentException.class,()->EffectCodec.compile(json));
        e.addProperty("pixelSize",6);e.addProperty("kind","sphere_mesh");e.addProperty("rate",2000);e.addProperty("lifetime",30);assertThrows(IllegalArgumentException.class,()->EffectCodec.compile(json));
    }
}
