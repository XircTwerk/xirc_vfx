package com.xirctwerk.vfx.core;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.xirctwerk.vfx.core.Effect.*;
import java.util.*;

class TransformsTest {
    private static JsonObject fixture(String space){
        var json=Templates.newProject("transform_test");json.addProperty("duration",3);
        var parent=json.getAsJsonArray("emitters").get(0).getAsJsonObject();parent.addProperty("rate",0);parent.addProperty("lifetime",3);parent.addProperty("speed",0);parent.addProperty("spread",0);parent.addProperty("space",space);parent.add("bursts",JsonParser.parseString("[{\"time\":0,\"count\":1}]"));
        return json;
    }
    private static JsonObject layer(JsonObject json){return json.getAsJsonArray("emitters").get(0).getAsJsonObject();}
    private static void near(Vec expected,Vec actual){assertEquals(expected.x(),actual.x(),1e-8);assertEquals(expected.y(),actual.y(),1e-8);assertEquals(expected.z(),actual.z(),1e-8);}
    @Test void affineCompositionAndInversePreserveRotatedScaledPoints(){
        var parent=Transforms.Pose.euler(new Vec(1,2,3),new Vec(23,41,90),2);
        var child=Transforms.Pose.euler(new Vec(3,-2,1),new Vec(-18,7,45),.5);var point=new Vec(-4,2,5);
        near(parent.point(child.point(point)),parent.compose(child).point(point));
        near(point,parent.inversePoint(parent.point(point)));
        near(point,parent.inverseVector(parent.vector(point)));
    }
    @Test void hierarchyRotatesTranslationsDirectionsAndVisualSizes(){
        var json=fixture("local");var root=layer(json);root.add("transform",JsonParser.parseString("{\"rotation\":[0,0,90],\"scale\":2}"));
        var child=root.deepCopy();child.addProperty("id","child");child.addProperty("parent",root.get("id").getAsString());child.remove("transform");child.add("offset",JsonParser.parseString("[1,0,0]"));child.add("direction",JsonParser.parseString("[1,0,0]"));json.getAsJsonArray("emitters").add(child);
        var effect=EffectCodec.compile(json);var simulation=new Simulation(effect);simulation.tick(Vec.ZERO,Simulation.NO_COLLISION);
        var p=simulation.particles().get(1);near(new Vec(0,2,0),p.position);near(new Vec(0,1,0),p.direction());assertEquals(.3,p.size(),1e-8);
    }
    @Test void localParticlesFollowSecondsAnimationAndWorldParticlesKeepBirthPose(){
        for(String space:List.of("local","world")){
            var json=fixture(space);layer(json).add("transform",JsonParser.parseString("{\"keys\":[{\"time\":0},{\"time\":1,\"position\":[2,0,0],\"scale\":2}]}"));
            var simulation=new Simulation(EffectCodec.compile(json));for(int i=0;i<21;i++)simulation.tick(Vec.ZERO,Simulation.NO_COLLISION);
            var p=simulation.particles().getFirst();near(new Vec(space.equals("local")?2:0,0,0),p.position);assertEquals(space.equals("local")?.3:.15,p.size(),1e-8);
            assertEquals(space.equals("local")?2:1,p.transformScale(),1e-8);
        }
    }
    @Test void fullTurnsAndBezierTimingAreRetained(){
        var track=new Transforms.Track(Vec.ZERO,1,List.of(new Transforms.Key(0,Vec.ZERO,Vec.ZERO,1,new Easing("step",0,0,1,1)),new Transforms.Key(2,new Vec(4,0,0),new Vec(0,0,720),2,Easing.LINEAR)));
        near(Vec.ZERO,track.at(Vec.ZERO,1).position());near(new Vec(4,0,0),track.at(Vec.ZERO,2).position());
        near(new Vec(1,0,0),track.at(Vec.ZERO,2).unit(new Vec(1,0,0)));
    }
    @Test void movingEmitterSplineUsesTheSameBoundedCatmullBasis(){
        var keys=List.of(new Transforms.Key(0,new Vec(0,0,0),Vec.ZERO,1,Easing.LINEAR),new Transforms.Key(1,new Vec(1,2,0),Vec.ZERO,1,Easing.LINEAR),new Transforms.Key(2,new Vec(3,0,1),Vec.ZERO,1,Easing.LINEAR));
        var track=new Transforms.Track(Vec.ZERO,1,keys,"spline");
        near(Geometry.catmull(keys.get(0).position(),keys.get(0).position(),keys.get(1).position(),keys.get(2).position(),.5),track.at(Vec.ZERO,.5).position());
        near(keys.get(0).position(),track.at(Vec.ZERO,-1).position());near(keys.getLast().position(),track.at(Vec.ZERO,20).position());
    }
    @Test void meshRollRotatesVerticesEvenWhenItsNormalIsUnchanged(){
        var json=fixture("local");var e=layer(json);e.addProperty("kind","slash");e.add("direction",JsonParser.parseString("[0,0,1]"));e.add("transform",JsonParser.parseString("{\"rotation\":[0,0,90],\"scale\":2}"));
        var sim=new Simulation(EffectCodec.compile(json));for(int i=0;i<4;i++)sim.tick(Vec.ZERO,Simulation.NO_COLLISION);var p=sim.particles().getFirst();
        var original=Geometry.create("slash",Vec.ZERO,p.emitter.direction(),p.emitter.radius(),p.size()/2,p.angle(),p.normalizedAge());
        var actual=Geometry.create(p,Vec.ZERO);var pose=Transforms.Pose.euler(Vec.ZERO,new Vec(0,0,90),2);
        near(pose.point(original.getFirst().a()),actual.getFirst().a());near(new Vec(0,0,1),p.direction());
    }
    @Test void groupingAnimatedSiblingsPreservesEveryPoseAndUndo(){
        var json=fixture("local");var root=layer(json);root.add("transform",JsonParser.parseString("{\"keys\":[{\"time\":0},{\"time\":1,\"rotation\":[20,70,90]}]}"));
        var child=root.deepCopy();child.addProperty("id","child");child.addProperty("parent",root.get("id").getAsString());child.remove("transform");child.add("offset",JsonParser.parseString("[1,2,3]"));json.getAsJsonArray("emitters").add(child);
        var sibling=child.deepCopy();sibling.addProperty("id","sibling");json.getAsJsonArray("emitters").add(sibling);var project=new Project(json);var before=project.json();
        project.edit(o->Authoring.group(o,Set.of("child","sibling")));
        for(double time:List.of(0.,.5,1.))for(String id:List.of("child","sibling")){
            var a=Transforms.sample(EffectCodec.compile(before),time).get(id);var b=Transforms.sample(project.effect(),time).get(id);near(a.position(),b.position());near(a.x(),b.x());assertEquals(a.scale(),b.scale(),1e-8);
        }
        assertTrue(project.undo());assertEquals(before,project.json());
        project.edit(o->Authoring.group(o,Set.of(root.get("id").getAsString(),"child")));assertEquals(root.get("id").getAsString(),Authoring.layer(project.json(),"child").get("parent").getAsString());
    }
    @Test void batchTransformsAffectSelectedHierarchyRootsOnceAndRetainTracks(){
        var json=fixture("local");var parent=layer(json);parent.add("transform",JsonParser.parseString("{\"keys\":[{\"time\":0},{\"time\":1,\"position\":[1,0,0]}]}"));var keys=parent.getAsJsonObject("transform").get("keys").deepCopy();
        var child=parent.deepCopy();child.addProperty("id","child");child.addProperty("parent",parent.get("id").getAsString());child.remove("transform");json.getAsJsonArray("emitters").add(child);
        var ids=Set.of(parent.get("id").getAsString(),"child");assertEquals(Set.of(parent.get("id").getAsString()),Authoring.selectedRoots(json,ids));
        var project=new Project(json);project.edit(o->Authoring.setTransform(o,ids,"scale",new JsonPrimitive(2)));
        assertEquals(2,Transforms.sample(project.effect(),.5).get("child").scale(),1e-8);assertEquals(keys,layer(project.json()).getAsJsonObject("transform").get("keys"));assertFalse(Authoring.layer(project.json(),"child").has("transform"));
    }
    @Test void transformedFramingContainsAnimatedMeshVerticesWithoutCountingPointSpawnAsVolume(){
        var effect=EffectCodec.compile(Templates.create("prismatic_orrery"));var box=SceneBounds.authored(effect);assertTrue(box.max().x()<4,"Point-spawn mesh radius must not be counted twice");
        var simulation=new Simulation(effect);for(int tick=0;tick<160;tick++){
            simulation.tick(Vec.ZERO,Simulation.NO_COLLISION);
            for(var p:simulation.particles())if(Geometry.supports(p.emitter.kind()))for(var quad:Geometry.create(p,p.position))for(var v:List.of(quad.a(),quad.b(),quad.c(),quad.d())){
                assertTrue(v.x()>=box.min().x()&&v.x()<=box.max().x());assertTrue(v.y()>=box.min().y()&&v.y()<=box.max().y());assertTrue(v.z()>=box.min().z()&&v.z()<=box.max().z());
            }
        }
    }
    @Test void invalidTracksAndExcessiveInheritedScaleAreRejectedAtomically(){
        var project=new Project(fixture("local"));var before=project.json();
        for(String value:List.of("{\"scale\":0}","{\"keys\":[{\"time\":1},{\"time\":0}]}","{\"keys\":[{\"time\":0},{\"time\":632}]}","{\"keys\":[{\"time\":0,\"code\":\"bad\"},{\"time\":1}]}")){
            assertThrows(EffectCodec.InvalidEffect.class,()->project.edit(o->layer(o).add("transform",JsonParser.parseString(value))));assertEquals(before,project.json());
        }
        var json=fixture("local");var root=layer(json);root.add("transform",JsonParser.parseString("{\"scale\":8}"));
        var child=root.deepCopy();child.addProperty("id","child");child.addProperty("parent",root.get("id").getAsString());child.add("transform",JsonParser.parseString("{\"scale\":8,\"keys\":[{\"time\":0},{\"time\":1,\"scale\":2}]}"));json.getAsJsonArray("emitters").add(child);
        assertThrows(EffectCodec.InvalidEffect.class,()->EffectCodec.compile(json));
    }
}
