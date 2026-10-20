package com.xirctwerk.vfx.core;

import com.networknt.schema.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class SchemaTest {
    @Test void workbenchFieldsAndEasingMatchRuntime() throws Exception {
        var mapper=new ObjectMapper();var schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(Files.readString(Path.of("../schema/effect.schema.json")));
        var json=Templates.newProject("workbench_schema");var id=Authoring.ids(json).iterator().next();var layer=Authoring.layer(json,id);layer.addProperty("folder","energy");Parameters.expose(json,java.util.Set.of(id),"power","size");layer.add("rotation",EffectCodec.parseObject("{\"curve\":[{\"time\":0,\"value\":0,\"easing\":{\"mode\":\"bezier\",\"controls\":[0.25,0,0.75,1]}},{\"time\":1,\"value\":90}]}").deepCopy());json.add("markers",com.google.gson.JsonParser.parseString("[{\"name\":\"impact\",\"time\":1}]"));
        EffectCodec.compile(json);assertTrue(schema.validate(mapper.readTree(EffectCodec.GSON.toJson(json))).isEmpty());
        layer.getAsJsonObject("rotation").getAsJsonArray("curve").get(0).getAsJsonObject().getAsJsonObject("easing").addProperty("mode","step");assertThrows(EffectCodec.InvalidEffect.class,()->EffectCodec.compile(json));assertFalse(schema.validate(mapper.readTree(EffectCodec.GSON.toJson(json))).isEmpty());
    }
    @Test void authoringFieldsAgreeWithRuntimeBounds() throws Exception {
        var mapper=new ObjectMapper();var schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(Files.readString(Path.of("../schema/effect.schema.json")));
        var json=Templates.newProject("schema_authoring");json.addProperty("autoDuration",true);json.add("pivot",EffectCodec.parseObject("{\"v\":[1,2,3]}").get("v"));var e=json.getAsJsonArray("emitters").get(0).getAsJsonObject();e.addProperty("opacity",.5);e.addProperty("spin",120);e.addProperty("aspect",2);
        EffectCodec.compile(json);assertTrue(schema.validate(mapper.readTree(EffectCodec.GSON.toJson(json))).isEmpty());
        e.addProperty("aspect",21);assertThrows(EffectCodec.InvalidEffect.class,()->EffectCodec.compile(json));assertFalse(schema.validate(mapper.readTree(EffectCodec.GSON.toJson(json))).isEmpty());e.addProperty("aspect",2);e.addProperty("kind","beam");assertThrows(EffectCodec.InvalidEffect.class,()->EffectCodec.compile(json));assertFalse(schema.validate(mapper.readTree(EffectCodec.GSON.toJson(json))).isEmpty());
    }
    @Test void schemaAndRuntimeAgreeOnExpandedLayerLimit() throws Exception {
        var schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(Files.readString(Path.of("../schema/effect.schema.json")));var mapper=new ObjectMapper();
        var json=Templates.create("sparks");var layers=json.getAsJsonArray("emitters");var original=layers.get(0).getAsJsonObject().deepCopy();layers.remove(0);
        for(int i=0;i<64;i++){var e=original.deepCopy();e.addProperty("id","layer_"+i);e.addProperty("rate",0);layers.add(e);}
        assertEquals(64,EffectCodec.compile(json).emitters().size());assertTrue(schema.validate(mapper.readTree(EffectCodec.GSON.toJson(json))).isEmpty());
        var extra=original.deepCopy();extra.addProperty("id","too_many");layers.add(extra);assertThrows(EffectCodec.InvalidEffect.class,()->EffectCodec.compile(json));assertFalse(schema.validate(mapper.readTree(EffectCodec.GSON.toJson(json))).isEmpty());
    }
    @Test void publishedSchemaAcceptsEveryTemplateAndRejectsUnsupportedFields() throws Exception {
        var mapper=new ObjectMapper();
        var schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(Files.readString(Path.of("../schema/effect.schema.json")));
        for(String name:Templates.ALL_NAMES) {
            var json=Templates.create(name);
            assertTrue(schema.validate(mapper.readTree(EffectCodec.GSON.toJson(json))).isEmpty(),name);
            json.addProperty("unknown",true);
            assertFalse(schema.validate(mapper.readTree(EffectCodec.GSON.toJson(json))).isEmpty(),name);
        }
    }
}
