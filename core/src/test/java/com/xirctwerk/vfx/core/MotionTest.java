package com.xirctwerk.vfx.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import com.google.gson.*;
import static com.xirctwerk.vfx.core.Effect.*;

class MotionTest {
    @Test void splineEndpointsAndLoopSeamAreContinuous(){var points=List.of(new Vec(0,0,0),new Vec(1,2,0),new Vec(3,0,0));assertEquals(points.getFirst(),Motion.sample(points,0,false));assertEquals(points.getLast(),Motion.sample(points,1,false));assertEquals(Motion.sample(points,0,true),Motion.sample(points,1,true));assertTrue(Motion.sample(points,.999999,true).add(Motion.sample(points,0,true).scale(-1)).length()<.0001);}
    @Test void curlIsFiniteAndDivergenceFree(){var p=new Vec(.7,.2,-.9);double h=.00001;var dx=Motion.curl(p.add(new Vec(h,0,0)),2,1).add(Motion.curl(p.add(new Vec(-h,0,0)),2,1).scale(-1));var dy=Motion.curl(p.add(new Vec(0,h,0)),2,1).add(Motion.curl(p.add(new Vec(0,-h,0)),2,1).scale(-1));var dz=Motion.curl(p.add(new Vec(0,0,h)),2,1).add(Motion.curl(p.add(new Vec(0,0,-h)),2,1).scale(-1));assertEquals(0,(dx.x()+dy.y()+dz.z())/(2*h),1e-7);assertTrue(Double.isFinite(Motion.curl(p,2,1).length()));}
    @Test void windAndPathsChangeTheSharedSimulationDeterministically(){var json=Templates.newProject("path_test");var layer=json.getAsJsonArray("emitters").get(0).getAsJsonObject();layer.addProperty("rate",0);layer.addProperty("lifetime",2);layer.addProperty("spread",0);layer.add("bursts",JsonParser.parseString("[{\"time\":0,\"count\":1}]"));layer.add("path",JsonParser.parseString("[[0,0,0],[2,2,0],[4,0,0]]"));json.add("wind",JsonParser.parseString("[1,0,0]"));var effect=EffectCodec.compile(json);var a=new Simulation(effect);var b=new Simulation(effect);for(int i=0;i<20;i++){a.tick(Vec.ZERO,Simulation.NO_COLLISION);b.tick(Vec.ZERO,Simulation.NO_COLLISION);}assertEquals(a.particles().getFirst().position,b.particles().getFirst().position);assertTrue(a.particles().getFirst().position.y()>1.9);assertTrue(a.particles().getFirst().position.x()>1.9);}
    @Test void invalidAxesPathsAndKindSpecificSettingsAreRejected(){var json=Templates.newProject("motion_validation");var layer=json.getAsJsonArray("emitters").get(0).getAsJsonObject();layer.addProperty("vortex",1);layer.add("vortexAxis",JsonParser.parseString("[0,0,0]"));assertThrows(EffectCodec.InvalidEffect.class,()->EffectCodec.compile(json));layer.remove("vortexAxis");layer.add("path",JsonParser.parseString("[[0,0,0]]"));assertThrows(EffectCodec.InvalidEffect.class,()->EffectCodec.compile(json));layer.remove("path");layer.addProperty("kind","beam");layer.addProperty("stretch",1);assertThrows(EffectCodec.InvalidEffect.class,()->EffectCodec.compile(json));}
}
