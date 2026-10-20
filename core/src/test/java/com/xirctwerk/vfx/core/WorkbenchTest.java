package com.xirctwerk.vfx.core;

import com.google.gson.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorkbenchTest {
    @Test void bindingsCompileWithoutRewritingAuthoredValues(){
        var json=Templates.newProject("bindings");String id=Authoring.ids(json).iterator().next();Authoring.layer(json,id).addProperty("size",.2);Parameters.expose(json,Set.of(id),"power","size");json.getAsJsonArray("parameters").get(0).getAsJsonObject().addProperty("value",2);
        var project=new Project(json);assertEquals(2,project.effect().emitters().getFirst().size().min());assertEquals(.2,Authoring.layer(project.json(),id).get("size").getAsDouble());
        project.edit(o->o.getAsJsonArray("parameters").get(0).getAsJsonObject().addProperty("value",3));assertEquals(3,project.effect().emitters().getFirst().size().min());assertTrue(project.undo());assertEquals(json,project.json());
        var before=project.json();assertThrows(EffectCodec.InvalidEffect.class,()->project.edit(o->o.getAsJsonArray("parameters").get(0).getAsJsonObject().addProperty("max",100)));assertEquals(before,project.json());
    }
    @Test void groupedTranslationsDoNotDoubleMoveDescendants(){var json=Templates.newProject("translation");var a=json.getAsJsonArray("emitters");var first=a.get(0).getAsJsonObject();first.addProperty("id","parent");first.add("offset",JsonParser.parseString("[1,0,0]"));var child=first.deepCopy();child.addProperty("id","child");child.addProperty("parent","parent");child.add("offset",JsonParser.parseString("[2,0,0]"));a.add(child);Authoring.translate(json,Set.of("parent","child"),3,0,0);assertEquals(4,Authoring.layer(json,"parent").getAsJsonArray("offset").get(0).getAsDouble());assertEquals(2,Authoring.layer(json,"child").getAsJsonArray("offset").get(0).getAsDouble());EffectCodec.compile(json);}
    @Test void recoveryAndClipFilesAreValidatedAndConfined(@org.junit.jupiter.api.io.TempDir java.nio.file.Path root) throws Exception {var files=new EffectFiles(root);var json=Templates.newProject("recover");files.checkpoint(json,true);assertEquals(json,files.recover("draft"));var clip=Authoring.clip("size",new JsonPrimitive(.4));files.saveClip("size_clip",clip);assertEquals(clip,files.loadClip("size_clip"));assertThrows(IllegalArgumentException.class,()->files.saveClip("../escape",clip));assertThrows(IllegalArgumentException.class,()->files.recover("../escape"));}
    @Test void segmentEasingIsConsistentAndPreserved(){
        var json=Templates.newProject("ease");var e=json.getAsJsonArray("emitters").get(0).getAsJsonObject();
        e.add("size",JsonParser.parseString("{\"curve\":[{\"time\":0,\"value\":1,\"easing\":{\"mode\":\"step\"}},{\"time\":1,\"value\":2}]}"));
        var effect=EffectCodec.compile(json);assertEquals(1,effect.emitters().getFirst().size().at(.9,0));assertEquals(1,Keyframes.sample(e,"size",.9).getAsDouble());
        Keyframes.put(e,"size",0,new JsonPrimitive(1.5));assertEquals("step",Keyframes.keys(e,"size").get(0).getAsJsonObject().getAsJsonObject("easing").get("mode").getAsString());
        var ease=new Effect.Easing("bezier",.42,0,1,1);assertTrue(ease.at(.25)<.25);assertEquals(.5,new Effect.Easing("smooth",0,0,1,1).at(.5),1e-8);
    }
    @Test void relativeEditingPreservesRangesCurvesAndUndo(){
        var json=Templates.create("magic_aura");String id=Authoring.ids(json).iterator().next();var before=Authoring.layer(json,id).get("color").deepCopy();var project=new Project(json);
        project.edit(o->Authoring.relative(o,Set.of(id),"size",.1,2));assertEquals(before,Authoring.layer(project.json(),id).get("color"));assertTrue(project.undo());assertEquals(json,project.json());
        project.edit(o->Authoring.duplicate(o,Set.of(id)));assertEquals(json.getAsJsonArray("emitters").size()+1,project.effect().emitters().size());
        var valid=project.json();assertThrows(EffectCodec.InvalidEffect.class,()->project.edit(o->Authoring.relative(o,Authoring.ids(o),"size",100,1)));assertEquals(valid,project.json());
    }
    @Test void retimingAndVariationsAreAtomicAndDeterministic(){
        var json=Templates.create("magic_aura");var id=Authoring.ids(json).iterator().next();var a=json.deepCopy();var b=json.deepCopy();Authoring.vary(a,Set.of(id),Set.of("size","speed"),9,.1);Authoring.vary(b,Set.of(id),Set.of("speed","size"),9,.1);assertEquals(a,b);
        var project=new Project(json);assertThrows(IllegalArgumentException.class,()->project.edit(o->Keyframes.retime(Authoring.layer(o,id),"size",Set.of(0),-1,1)));assertEquals(json,project.json());
    }
    @Test void curveBoundsIncludeInteriorPeaks(){var json=Templates.newProject("peaks");var e=json.getAsJsonArray("emitters").get(0).getAsJsonObject();e.add("size",JsonParser.parseString("{\"curve\":[{\"time\":0,\"value\":1},{\"time\":0.5,\"value\":8},{\"time\":1,\"value\":2}]}"));assertEquals(8,EffectCodec.compile(json).emitters().getFirst().size().max());}
}
