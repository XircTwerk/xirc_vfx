package com.xirctwerk.vfx.core;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KeyframesTest {
    private static JsonObject layer(JsonObject json){return json.getAsJsonArray("emitters").get(0).getAsJsonObject();}
    @Test void insertedKeysInterpolateAndPreserveUnrelatedAuthoredData(){
        var original=Templates.create("magic_aura");var project=new Project(original);
        project.edit(o->Keyframes.put(layer(o),"size",.5,new JsonPrimitive(.8)));
        var after=project.json();assertEquals(.8,Keyframes.sample(layer(after),"size",.5).getAsDouble(),1e-9);
        var expected=original.deepCopy();layer(expected).remove("size");var unrelated=after.deepCopy();layer(unrelated).remove("size");assertEquals(expected,unrelated);
        assertEquals(project.effect(),EffectCodec.compile(EffectCodec.parse(project.text())));
        project.undo();assertEquals(original,project.json());project.redo();assertEquals(after,project.json());
    }
    @Test void colorAndAlphaInterpolateAndInsertionDoesNotAlterExistingKeys(){
        var e=new JsonObject();e.add("color",JsonParser.parseString("{\"gradient\":[{\"time\":0,\"color\":[1,0,0,.2]},{\"time\":1,\"color\":[0,0,1,.8]}]}"));
        var original=Keyframes.keys(e,"color");var middle=Keyframes.sample(e,"color",.5).getAsJsonArray();assertEquals(.5,middle.get(0).getAsDouble(),1e-9);assertEquals(.5,middle.get(3).getAsDouble(),1e-9);
        Keyframes.put(e,"color",.5,middle);var keys=Keyframes.keys(e,"color");assertEquals(original.get(0),keys.get(0));assertEquals(original.get(1),keys.get(2));
        Keyframes.remove(e,"color",1);assertEquals(original,Keyframes.keys(e,"color"));assertThrows(IllegalArgumentException.class,()->Keyframes.remove(e,"color",0));
    }
    @Test void retimingIsBoundedAtomicAndOneGestureUndoesTogether(){
        var project=new Project(Templates.create("fire"));project.edit(o->Keyframes.put(layer(o),"rotation",.5,new JsonPrimitive(90)));var before=project.json();
        project.beginEdit();for(int i=0;i<20;i++){double t=.5+i*.01;project.edit(o->Keyframes.move(layer(o),"rotation",1,t));}project.endEdit();project.undo();assertEquals(before,project.json());
        assertThrows(IllegalArgumentException.class,()->project.edit(o->Keyframes.move(layer(o),"rotation",1,1)));assertEquals(before,project.json());
        assertThrows(IllegalArgumentException.class,()->project.edit(o->Keyframes.put(layer(o),"rotation",Double.NaN,new JsonPrimitive(1))));assertEquals(before,project.json());
    }
    @Test void rangesRequireExplicitConversionAndKeyLimitsAreEnforced(){
        var e=new JsonObject();e.add("size",JsonParser.parseString("{\"min\":.1,\"max\":.3}"));assertThrows(IllegalArgumentException.class,()->Keyframes.put(e,"size",.5,new JsonPrimitive(.2)));
        e.addProperty("size",.2);for(int i=1;i<=62;i++)Keyframes.put(e,"size",i/63.,new JsonPrimitive(.2));assertEquals(64,Keyframes.keys(e,"size").size());
        assertThrows(IllegalArgumentException.class,()->Keyframes.put(e,"size",.001,new JsonPrimitive(.2)));Keyframes.put(e,"size",0,new JsonPrimitive(.4));assertEquals(64,Keyframes.keys(e,"size").size());
        assertThrows(IllegalArgumentException.class,()->Keyframes.keys(e,"lifetime"));assertEquals(1,Keyframes.sample(new JsonObject(),"speed",.5).getAsInt());
    }
}
