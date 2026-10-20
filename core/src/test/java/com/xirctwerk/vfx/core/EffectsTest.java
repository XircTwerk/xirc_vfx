package com.xirctwerk.vfx.core;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class EffectsTest {
    @Test void mutingIsImmediatePreservesTimeAndLeavesParentTransformsIntact() {
        var json=Templates.create("sparks");var layers=json.getAsJsonArray("emitters");var parent=layers.get(0).getAsJsonObject();parent.add("offset",JsonParser.parseString("[2,0,0]"));parent.addProperty("radius",0);parent.addProperty("speed",0);var child=parent.deepCopy();child.addProperty("id","child");child.addProperty("parent","main");child.add("offset",JsonParser.parseString("[0,1,0]"));layers.add(child);var effect=EffectCodec.compile(json);var sim=new Simulation(effect);sim.tick(Effect.Vec.ZERO,Simulation.NO_COLLISION);assertTrue(sim.particles().stream().anyMatch(p->p.emitter.id().equals("main")));double time=sim.time();sim.setMuted(java.util.Set.of("main"));assertEquals(time,sim.time());assertTrue(sim.particles().stream().allMatch(p->p.emitter.id().equals("child")));sim.tick(Effect.Vec.ZERO,Simulation.NO_COLLISION);assertTrue(sim.particles().stream().allMatch(p->p.emitter.id().equals("child")));assertEquals(new Effect.Vec(2,1,0),sim.particles().getLast().position);sim.setMuted(java.util.Set.of());sim.tick(Effect.Vec.ZERO,Simulation.NO_COLLISION);assertTrue(sim.particles().stream().anyMatch(p->p.emitter.id().equals("main")));assertEquals(effect,EffectCodec.compile(json));
    }
    @Test void longLiveGesturePreservesHistoryAndRejectsInvalidUpdates() {
        var original=Templates.create("magic_aura");var project=new Project(original);
        project.edit(o->o.addProperty("description","Earlier edit"));var before=project.json();
        project.beginEdit();
        for(int i=0;i<100;i++) {double size=.1+i*.01;project.edit(o->o.getAsJsonArray("emitters").get(0).getAsJsonObject().addProperty("size",size));}
        var valid=project.json();
        assertThrows(IllegalArgumentException.class,()->project.edit(o->o.getAsJsonArray("emitters").get(0).getAsJsonObject().addProperty("size",-1)));
        assertEquals(valid,project.json());project.endEdit();
        assertTrue(project.undo());assertEquals(before,project.json());
        assertTrue(project.undo());assertEquals(original,project.json());
        assertTrue(project.redo());assertEquals(before,project.json());
        assertTrue(project.redo());assertEquals(valid,project.json());
    }
    @TempDir Path directory;
    @Test void everyTemplateSemanticallyRoundTripsAndRuns() {
        for(String name:Templates.ALL_NAMES) {
            var json=Templates.create(name); var effect=EffectCodec.compile(json);
            assertEquals(effect,EffectCodec.compile(EffectCodec.parse(EffectCodec.GSON.toJson(json))));
            var sim=new Simulation(effect); for(int i=0;i<10;i++) sim.tick(Effect.Vec.ZERO,Simulation.NO_COLLISION);
            assertFalse(sim.particles().isEmpty(),name); assertEquals(0,sim.dropped());
        }
    }
    @Test void deterministicSeedAndFixedTicks() {
        Effect e=EffectCodec.compile(Templates.create("explosion")); var a=new Simulation(e);var b=new Simulation(e);
        for(int i=0;i<15;i++) { a.tick(Effect.Vec.ZERO,Simulation.NO_COLLISION); b.tick(Effect.Vec.ZERO,Simulation.NO_COLLISION); }
        assertEquals(a.particles().size(),b.particles().size());
        for(int i=0;i<a.particles().size();i++) assertEquals(a.particles().get(i).position,b.particles().get(i).position);
    }
    @Test void invalidDataHasActionablePaths() {
        var json=Templates.create("fire"); json.addProperty("shaderCode","evil");
        assertTrue(assertThrows(EffectCodec.InvalidEffect.class,()->EffectCodec.compile(json)).getMessage().contains("$.shaderCode"));
        assertThrows(EffectCodec.InvalidEffect.class,()->EffectCodec.parse("{\"schemaVersion\":1,\"schemaVersion\":1}"));
        assertThrows(EffectCodec.InvalidEffect.class,()->EffectCodec.parse("{schemaVersion:1}"));
        assertThrows(EffectCodec.InvalidEffect.class,()->EffectCodec.parse("{\"schemaVersion\":1} trailing"));
        assertThrows(EffectCodec.InvalidEffect.class,()->EffectCodec.resource("x:../../secret","texture"));
        var wrong=Templates.create("fire");wrong.addProperty("schemaVersion",2);assertThrows(EffectCodec.InvalidEffect.class,()->EffectCodec.compile(wrong));
    }
    @Test void advancedDataSurvivesSimpleEditsUndoRedo() {
        var p=new Project(Templates.create("fire")); var original=p.json();
        assertThrows(IllegalArgumentException.class,()->p.simple(0,"size",new JsonPrimitive(.3),false));
        p.simple(0,"rate",new JsonPrimitive(25),false);
        assertEquals(original.getAsJsonArray("emitters").get(0).getAsJsonObject().get("color"),p.json().getAsJsonArray("emitters").get(0).getAsJsonObject().get("color"));
        assertTrue(p.undo());assertEquals(original,p.json());assertTrue(p.redo());assertEquals(25,p.effect().emitters().getFirst().rate());
    }
    @Test void hierarchyAndBudgetValidated() {
        var j=Templates.create("fire"); var e=j.getAsJsonArray("emitters").get(0).getAsJsonObject();e.addProperty("parent","main");assertThrows(EffectCodec.InvalidEffect.class,()->EffectCodec.compile(j));
        e.remove("parent");e.addProperty("rate",2000);e.addProperty("lifetime",30);j.addProperty("duration",30);assertThrows(EffectCodec.InvalidEffect.class,()->EffectCodec.compile(j));
    }
    @Test void rangesGradientsAndCurvesKeepSemantics() {
        var j=Templates.create("fire"); var emitter=j.getAsJsonArray("emitters").get(0).getAsJsonObject();var range=new JsonObject();range.addProperty("min",1);range.addProperty("max",2);emitter.add("lifetime",range);
        Effect e=EffectCodec.compile(j);assertEquals(.09,e.emitters().getFirst().size().at(.5,0),1e-8);assertEquals(1,e.emitters().getFirst().lifetime().min());assertEquals(2,e.emitters().getFirst().lifetime().max());
        range.addProperty("min",3);assertThrows(EffectCodec.InvalidEffect.class,()->EffectCodec.compile(j));
    }
    @Test void packagesRoundTripAndMissingResourcesFail() throws Exception {
        var files=new EffectFiles(directory);var json=Templates.create("fire");files.save("fire",json);assertEquals(json,files.load("fire"));
        assertThrows(java.io.IOException.class,()->files.export("fire",json,id->null));
        files.export("fire",json,id->new byte[]{1,2,3});var pack=files.readPackage("fire");assertEquals(json,pack.effect());assertEquals(1,pack.resources().size());
        assertThrows(IllegalArgumentException.class,()->files.save("../escape",json));
        files.storeResources(pack.resources());assertArrayEquals(new byte[]{1,2,3},new EffectFiles(directory).resource("xirc_vfx_studio:textures/effect/soft.png"));
    }
    @Test void unsafeZipNeverExtracted() throws Exception {
        var files=new EffectFiles(directory);try(var zip=new ZipOutputStream(Files.newOutputStream(directory.resolve("exports/unsafe.xvfx")))) { zip.putNextEntry(new ZipEntry("../outside"));zip.write(new byte[]{1});zip.closeEntry(); }
        assertThrows(java.io.IOException.class,()->files.readPackage("unsafe"));assertFalse(Files.exists(directory.resolve("outside")));
    }
    @Test void manifestMustUseExactVersionAndDeclaredDependencies() throws Exception {
        var files=new EffectFiles(directory);var json=Templates.create("fire");
        try(var zip=new ZipOutputStream(Files.newOutputStream(directory.resolve("exports/bad_manifest.xvfx")))) {
            for(var entry:java.util.Map.of("effect.json",EffectCodec.GSON.toJson(json),"manifest.json","{\"packageVersion\":1.5,\"effect\":\"effect.json\",\"resources\":[]}").entrySet()) {zip.putNextEntry(new ZipEntry(entry.getKey()));zip.write(entry.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();}
        }
        assertThrows(java.io.IOException.class,()->files.readPackage("bad_manifest"));
    }
    @Test void worldAndLocalAttachmentHaveDifferentSemantics() {
        var j=Templates.create("magic_circle"); var e=j.getAsJsonArray("emitters").get(0).getAsJsonObject();e.addProperty("rate",20);e.addProperty("space","world");
        var world=new Simulation(EffectCodec.compile(j));world.tick(new Effect.Vec(10,0,0),Simulation.NO_COLLISION);var p=world.particles().getFirst();assertEquals(p.position,p.world(new Effect.Vec(20,0,0)));
        e.addProperty("space","local");var local=new Simulation(EffectCodec.compile(j));local.tick(new Effect.Vec(10,0,0),Simulation.NO_COLLISION);var q=local.particles().getFirst();assertEquals(q.position.add(new Effect.Vec(20,0,0)),q.world(new Effect.Vec(20,0,0)));
    }
    @Test void fractionalLoopIntervalsDoNotLoseEmissionTime() {
        var j=Templates.create("magic_circle");j.addProperty("duration",.075);var e=j.getAsJsonArray("emitters").get(0).getAsJsonObject();e.addProperty("rate",400);e.addProperty("lifetime",.1);
        var sim=new Simulation(EffectCodec.compile(j));for(int i=0;i<3;i++) sim.tick(Effect.Vec.ZERO,Simulation.NO_COLLISION);
        assertEquals(40,sim.particles().size());
    }
    @Test void loopingBudgetIncludesParticlesFromPreviousCycles() {
        var j=Templates.create("magic_circle");j.addProperty("duration",1);var e=j.getAsJsonArray("emitters").get(0).getAsJsonObject();e.addProperty("rate",2000);e.addProperty("lifetime",30);
        assertThrows(EffectCodec.InvalidEffect.class,()->EffectCodec.compile(j));
    }
    @Test void zeroToPositiveSpeedCurveCanAccelerate() {
        var j=Templates.create("magic_circle");var e=j.getAsJsonArray("emitters").get(0).getAsJsonObject();e.add("speed",Templates.curve(0,2));e.addProperty("rate",20);
        var sim=new Simulation(EffectCodec.compile(j));sim.tick(Effect.Vec.ZERO,Simulation.NO_COLLISION);var p=sim.particles().getFirst();double initialY=p.position.y();sim.tick(Effect.Vec.ZERO,Simulation.NO_COLLISION);assertTrue(p.position.y()>initialY);
    }
}
