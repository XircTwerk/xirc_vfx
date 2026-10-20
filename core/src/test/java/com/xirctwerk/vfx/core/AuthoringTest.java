package com.xirctwerk.vfx.core;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuthoringTest {
    private static JsonObject layer(JsonObject json){return json.getAsJsonArray("emitters").get(0).getAsJsonObject();}
    @Test void fullLengthIncludesLateLayersAndParticleTails(){
        var json=Templates.newProject("full_length");json.addProperty("duration",.1);var e=layer(json);e.addProperty("start",1);e.addProperty("duration",.1);e.addProperty("lifetime",.5);
        var clipped=new Simulation(EffectCodec.compile(json));for(int i=0;i<40;i++)clipped.tick(Effect.Vec.ZERO,Simulation.NO_COLLISION);assertTrue(clipped.particles().isEmpty());
        json.addProperty("autoDuration",true);var effect=EffectCodec.compile(json);assertEquals(1.65,effect.duration(),1e-9);var sim=new Simulation(effect);int peak=0;for(int i=0;i<40;i++){sim.tick(Effect.Vec.ZERO,Simulation.NO_COLLISION);peak=Math.max(peak,sim.particles().size());}assertTrue(peak>0);assertTrue(sim.finished());assertEquals(.1,json.get("duration").getAsDouble());
        assertEquals(effect,EffectCodec.compile(EffectCodec.parse(EffectCodec.GSON.toJson(json))));
    }
    @Test void fullLengthLoopWaitsForTailsAndPreservesMissingLayerDurationDefault(){
        var json=Templates.newProject("loop");json.addProperty("duration",.05);json.addProperty("autoDuration",true);json.addProperty("loop",true);var effect=EffectCodec.compile(json);assertEquals(.05,effect.emitters().getFirst().duration());assertEquals(1.1,effect.duration(),1e-9);
        var sim=new Simulation(effect);int peak=0;for(int i=0;i<100;i++){sim.tick(Effect.Vec.ZERO,Simulation.NO_COLLISION);peak=Math.max(peak,sim.particles().size());}assertTrue(peak<=1);assertFalse(sim.finished());
    }
    @Test void pivotIsAppliedOnceAcrossParentHierarchyAndSimulationSpaces(){
        var json=Templates.newProject("pivot");json.add("pivot",JsonParser.parseString("[1,1,1]"));var parent=layer(json);parent.addProperty("rate",0);parent.add("offset",JsonParser.parseString("[2,0,0]"));var child=parent.deepCopy();child.addProperty("id","child");child.addProperty("parent","main");child.addProperty("rate",20);child.add("offset",JsonParser.parseString("[0,3,0]"));json.getAsJsonArray("emitters").add(child);
        var anchor=new Effect.Vec(10,20,30);var sim=new Simulation(EffectCodec.compile(json));sim.tick(anchor,Simulation.NO_COLLISION);var particle=sim.particles().getFirst();assertEquals(new Effect.Vec(1,2,-1),particle.position);assertEquals(new Effect.Vec(11,22,29),particle.world(anchor));
        child.addProperty("space","world");sim=new Simulation(EffectCodec.compile(json));sim.tick(anchor,Simulation.NO_COLLISION);assertEquals(new Effect.Vec(11,22,29),sim.particles().getFirst().position);
    }
    @Test void opacityAndSpinChangeSimulationWithoutReplacingGradients(){
        var json=Templates.newProject("materials");var e=layer(json);e.addProperty("rotation",30);e.addProperty("spin",90);e.addProperty("opacity",.25);e.addProperty("fade",0);e.addProperty("aspect",3);e.add("color",JsonParser.parseString("[1,0.5,0,0.8]"));var sim=new Simulation(EffectCodec.compile(json));sim.tick(Effect.Vec.ZERO,Simulation.NO_COLLISION);var p=sim.particles().getFirst();p.age=.5;assertEquals(75,p.angle(),1e-9);assertEquals(.2,p.color().a(),1e-9);assertEquals(3,p.emitter.aspect());assertTrue(SceneBounds.live(sim.particles()).max().x()>=p.position.x()+.22);
        e.addProperty("opacity",2);assertThrows(EffectCodec.InvalidEffect.class,()->EffectCodec.compile(json));e.addProperty("opacity",1);e.addProperty("kind","beam");assertThrows(EffectCodec.InvalidEffect.class,()->EffectCodec.compile(json));
    }
    @Test void newProjectsAreIndependentAndUndoRestoresPreviousScene(){
        var before=Templates.create("astral_gate");var project=new Project(before);var fresh=Templates.newProject("fresh_scene");project.replace(fresh);assertEquals("XircTwerk",project.json().get("author").getAsString());fresh.addProperty("name","external mutation");assertNotEquals(fresh,project.json());project.undo();assertEquals(before,project.json());assertThrows(EffectCodec.InvalidEffect.class,()->Templates.newProject("../escape"));
    }
}
