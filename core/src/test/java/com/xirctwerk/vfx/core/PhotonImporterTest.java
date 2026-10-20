package com.xirctwerk.vfx.core;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.util.zip.GZIPOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class PhotonImporterTest {
    @Test void directUserChosenFilesOpenWithoutCopyingToImports() throws Exception {
        Path chosen=directory.resolve("User effect.FX");Files.write(chosen,nbt(fixture()));var files=new EffectFiles(directory.resolve("studio"));var original=Files.readAllBytes(chosen);
        var result=files.importPhotonPath(chosen,true);assertEquals(1,EffectCodec.compile(result.effect()).emitters().size());assertArrayEquals(original,Files.readAllBytes(chosen));assertThrows(IOException.class,()->files.importPhotonPath(Path.of("relative.fx"),true));assertThrows(IOException.class,()->files.importPhotonPath(directory.resolve("a/../User effect.FX"),true));
    }
    @Test void meshScaleAnimatesGeometryAndRetainsCanonicalCurveOnUndo() {
        var json=Templates.create("mesh_ring");json.getAsJsonArray("emitters").get(0).getAsJsonObject().add("meshScale",Templates.curve(.1,2));var project=new Project(json);var sim=new Simulation(project.effect());sim.tick(Effect.Vec.ZERO,Simulation.NO_COLLISION);for(int i=0;i<9;i++)sim.tick(Effect.Vec.ZERO,Simulation.NO_COLLISION);var particle=sim.particles().getFirst();double initial=particle.meshScale();for(int i=0;i<2;i++)sim.tick(Effect.Vec.ZERO,Simulation.NO_COLLISION);assertTrue(particle.meshScale()>initial);project.edit(o->o.addProperty("description","changed"));project.undo();assertEquals(json,project.json());
    }
    @TempDir Path directory;
    private static JsonObject fixture() {
        return JsonParser.parseString("""
            {"_version":1,"fx":{"subFXs":{},"mainFX":{"fxObjects":[
              {"_type":"particle","transform":{"id":[1,2,3,4],"localPosition":{"x":1,"y":2,"z":3}},
               "config":{"duration":40,"startDelay":{"_type":"Constant","number":10},
                 "emission":{"emissionRate":{"_type":"Constant","number":2}},
                 "startLifetime":{"_type":"RandomConstant","a":10,"b":20},
                 "startSpeed":{"_type":"Constant","number":0.5},
                 "startSize":{"x":{"_type":"Constant","number":0.2},"y":{"_type":"Constant","number":0.2}},
                 "startColor":{"_type":"Color","number":-65536},
                 "material":{"material":{"_type":"TextureMaterial","texture":"other:textures/spark.png"}}}}
            ]}}}
            """).getAsJsonObject();
    }
    @Test void preservesUnitsOffsetsRangesAndRequiresAcknowledgingLoss() throws Exception {
        var result=PhotonImporter.read(nbt(fixture()),"my effect",false);var effect=EffectCodec.compile(result.effect());var e=effect.emitters().getFirst();
        assertEquals(.5,e.start());assertEquals(2,e.duration());assertEquals(40,e.rate());assertEquals(10,e.speed().min());assertEquals(.5,e.lifetime().min());assertEquals(1,e.lifetime().max());assertEquals(new Effect.Vec(1,2,3),e.offset());assertEquals("other:textures/spark.png",e.texture());
        assertEquals(new Effect.Color(1,0,0,1),e.color().at(0));assertFalse(result.exact());assertThrows(IllegalArgumentException.class,result::requireExact);
        var fallback=PhotonImporter.read(nbt(fixture()),"test",true);assertTrue(fallback.diagnostics().stream().anyMatch(s->s.contains("other:textures/spark.png")));assertEquals("xirc_vfx_studio:textures/effect/soft.png",EffectCodec.compile(fallback.effect()).emitters().getFirst().texture());
    }
    @Test void mapsBezierBoundsBeamEndpointAndEmptyParent() throws Exception {
        var input=fixture();var objects=input.getAsJsonObject("fx").getAsJsonObject("mainFX").getAsJsonArray("fxObjects");var parent=objects.get(0).getAsJsonObject();parent.addProperty("_type","empty");
        var beam=JsonParser.parseString("""
          {"_type":"beam","transform":{"_parentId":[1,2,3,4]},"config":{"duration":20,"startDelay":0,"end":{"x":3,"y":0,"z":4},"width":{"_type":"Curve","lower":0.2,"upper":0.8,"curves":[[0,0,0.33,0.33,0.66,0.66,1,1]]},"color":{"_type":"Color","number":-1}}}
          """).getAsJsonObject();objects.add(beam);
        var result=PhotonImporter.read(nbt(input),"beam",true);var e=EffectCodec.compile(result.effect()).emitters().get(1);assertEquals(5,e.length());assertEquals(new Effect.Vec(.6,0,.8),e.direction());assertEquals("layer_0",e.parent());assertEquals(.2,e.size().at(0,0),1e-6);assertEquals(.8,e.size().at(1,0),1e-6);assertEquals(1,e.bursts().getFirst().count());
    }
    @Test void rejectsTraversalDuplicateKeysMalformedAndExpansionAbuse() throws Exception {
        var files=new EffectFiles(directory);assertThrows(IOException.class,()->files.importPhoton("../secret.fx",true));assertThrows(IOException.class,()->files.importPhoton("a..b.fx",true));
        assertThrows(IOException.class,()->PhotonImporter.read(new byte[]{1,2,3},"bad",true));assertThrows(IOException.class,()->PhotonImporter.read(new byte[BoundedNbt.MAX_COMPRESSED+1],"huge",true));
        var bytes=new ByteArrayOutputStream();try(var gzip=new GZIPOutputStream(bytes)){gzip.write(new byte[BoundedNbt.MAX_EXPANDED+1]);}assertThrows(IOException.class,()->PhotonImporter.read(bytes.toByteArray(),"bomb",true));
        bytes.reset();try(var out=new DataOutputStream(new GZIPOutputStream(bytes))) {out.writeByte(10);out.writeUTF("");for(int i=0;i<2;i++){out.writeByte(3);out.writeUTF("same");out.writeInt(1);}out.writeByte(0);}
        assertTrue(assertThrows(IOException.class,()->PhotonImporter.read(bytes.toByteArray(),"duplicate",true)).getMessage().contains("duplicate"));
    }
    @Test void unsupportedFunctionsNeverExecuteAndReplacementIsAtomic() throws Exception {
        var input=fixture();var c=input.getAsJsonObject("fx").getAsJsonObject("mainFX").getAsJsonArray("fxObjects").get(0).getAsJsonObject().getAsJsonObject("config");
        c.add("startSpeed",JsonParser.parseString("{\"_type\":\"Expression\",\"code\":\"System.exit(0)\"}"));var project=new Project(Templates.create("fire"));var before=project.json();assertTrue(assertThrows(IOException.class,()->PhotonImporter.read(nbt(input),"unsafe",true)).getMessage().contains("Expressions"));assertEquals(before,project.json());
        c.add("startSpeed",JsonParser.parseString("{\"_type\":\"Constant\",\"number\":1000}"));var result=PhotonImporter.read(nbt(input),"bounded",true);assertEquals(50,EffectCodec.compile(result.effect()).emitters().getFirst().speed().min());assertTrue(result.diagnostics().stream().anyMatch(s->s.contains(".speed")&&s.contains("limits")));
        project.replace(result.effect());assertTrue(project.undo());assertEquals(before,project.json());
    }
    @Test void nestingNonfiniteNumbersAndUnsafeResourceReferencesFailBeforeOpening() throws Exception {
        var input=fixture();var nested=new JsonObject();input.add("nested",nested);for(int i=0;i<34;i++){var child=new JsonObject();nested.add("next",child);nested=child;}
        var deep=input;assertTrue(assertThrows(IOException.class,()->PhotonImporter.read(nbt(deep),"deep",true)).getMessage().contains("nesting"));
        input=fixture();input.addProperty("nan",Double.NaN);var nonfinite=input;assertTrue(assertThrows(IOException.class,()->PhotonImporter.read(nbt(nonfinite),"nan",true)).getMessage().contains("Non-finite"));
        input=fixture();input.getAsJsonObject("fx").getAsJsonObject("mainFX").getAsJsonArray("fxObjects").get(0).getAsJsonObject().getAsJsonObject("config").getAsJsonObject("material").getAsJsonObject("material").addProperty("texture","other:textures/../../secret.png");var unsafe=input;
        assertThrows(IOException.class,()->PhotonImporter.read(nbt(unsafe),"unsafe",true));
        var version=fixture();version.addProperty("_version",1.5);assertThrows(IOException.class,()->PhotonImporter.read(nbt(version),"future",true));
    }
    @Test void allAdvancedCompositionsRunToCompletionWithoutBudgetDrops() {
        for(String name:Templates.ADVANCED_NAMES) {var json=Templates.create(name);var effect=EffectCodec.compile(json);assertTrue(effect.emitters().size()>=7,name);assertFalse(effect.loop());assertEquals("XircTwerk",json.get("author").getAsString());var simulation=new Simulation(effect);int peak=0;for(int i=0;i<240;i++){simulation.tick(Effect.Vec.ZERO,Simulation.NO_COLLISION);peak=Math.max(peak,simulation.particles().size());}assertTrue(peak>=60,name);assertEquals(0,simulation.dropped(),name);assertTrue(simulation.finished(),name);}
    }
    // Synthetic standard NBT, authored here; no reference effect/source/asset is copied into tests.
    private static byte[] nbt(JsonObject json) throws IOException {var bytes=new ByteArrayOutputStream();try(var out=new DataOutputStream(new GZIPOutputStream(bytes))){out.writeByte(10);out.writeUTF("");write(out,json);}return bytes.toByteArray();}
    private static int type(JsonElement e){return e.isJsonObject()?10:e.isJsonArray()?9:e.getAsJsonPrimitive().isString()?8:6;}
    private static void write(DataOutputStream out,JsonElement e)throws IOException {if(e.isJsonObject()){for(var entry:e.getAsJsonObject().entrySet()){out.writeByte(type(entry.getValue()));out.writeUTF(entry.getKey());write(out,entry.getValue());}out.writeByte(0);}else if(e.isJsonArray()){var a=e.getAsJsonArray();out.writeByte(a.isEmpty()?0:type(a.get(0)));out.writeInt(a.size());for(var item:a)write(out,item);}else if(e.getAsJsonPrimitive().isString())out.writeUTF(e.getAsString());else out.writeDouble(e.getAsDouble());}
}
