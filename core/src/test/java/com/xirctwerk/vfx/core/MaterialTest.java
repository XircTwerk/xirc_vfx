package com.xirctwerk.vfx.core;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MaterialTest {
    @Test void boundedFlipbookFramesLoopOrClampAndUseRowMajorUvs(){
        var sheet=new Effect.TextureAnimation(4,2,6,10,true);assertEquals(new Effect.Uv(0,0,.25,.5),sheet.uv(0));assertEquals(new Effect.Uv(.25,.5,.5,1),sheet.uv(.51));assertEquals(sheet.uv(0),sheet.uv(.61));
        var once=new Effect.TextureAnimation(4,2,6,10,false);assertEquals(sheet.uv(.51),once.uv(90));assertEquals(sheet.uv(0),once.uv(-1));
    }
    @Test void materialFieldsRoundTripAndRejectUnsupportedValues(){
        var json=Templates.create("fire");var e=json.getAsJsonArray("emitters").get(0).getAsJsonObject();e.addProperty("bloom",1.5);e.addProperty("blend","additive");e.add("textureAnimation",JsonParser.parseString("{\"columns\":4,\"rows\":4,\"frames\":16,\"fps\":20,\"loop\":false}"));
        var project=new Project(json);assertEquals(1.5,project.effect().emitters().getFirst().bloom());assertEquals("additive",project.effect().emitters().getFirst().blend());assertEquals(project.effect(),EffectCodec.compile(EffectCodec.parse(project.text())));project.edit(o->o.addProperty("description","changed"));project.undo();assertEquals(json,project.json());
        e.addProperty("bloom",5);assertThrows(EffectCodec.InvalidEffect.class,()->EffectCodec.compile(json));e.addProperty("bloom",1);e.getAsJsonObject("textureAnimation").addProperty("frames",17);assertThrows(EffectCodec.InvalidEffect.class,()->EffectCodec.compile(json));e.getAsJsonObject("textureAnimation").addProperty("frames",16);e.addProperty("blend","shader_from_disk");assertThrows(EffectCodec.InvalidEffect.class,()->EffectCodec.compile(json));
    }
    @Test void framingLargeImportedRingDoesNotChangeAuthoredScale(){
        var json=Templates.create("magic_circle");var e=json.getAsJsonArray("emitters").get(0).getAsJsonObject();e.addProperty("radius",10);var before=json.deepCopy();var effect=EffectCodec.compile(json);var bounds=SceneBounds.authored(effect);assertTrue(bounds.min().x()<=-10&&bounds.max().x()>=10);assertEquals(before,json);
        var sim=new Simulation(effect);for(int i=0;i<10;i++)sim.tick(Effect.Vec.ZERO,Simulation.NO_COLLISION);var live=SceneBounds.live(sim.particles());for(var p:sim.particles())assertTrue(p.position.x()>=live.min().x()&&p.position.x()<=live.max().x());
        var p=sim.particles().getFirst();assertEquals(p.previous,p.interpolated(0));assertEquals(p.position,p.interpolated(1));assertTrue(p.interpolatedAge(.5)<=p.age);
    }
}
