package com.xirctwerk.vfx.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.xirctwerk.vfx.core.Effect.*;
import java.util.*;
import com.google.gson.*;

class LibraryApiTest {
    @Test void overridesPreserveAuthoredCurvesAndRejectUnknownOrOutOfRange(){
        var authored=IntegrationPresets.create("charged_attack");var saved=authored.deepCopy();var changed=Parameters.override(authored,Map.of("power",3.,"radius",2.));
        assertEquals(saved,authored);assertNotEquals(saved,changed);assertEquals(3,EffectCodec.compile(changed).emitters().getFirst().bloom());
        assertThrows(IllegalArgumentException.class,()->Parameters.override(authored,Map.of("typo",1.)));
        assertThrows(IllegalArgumentException.class,()->Parameters.override(authored,Map.of("power",5.)));
        assertThrows(IllegalArgumentException.class,()->Parameters.override(authored,Map.of("power",Double.NaN)));
    }
    @Test void allBuiltInIntegrationDefinitionsValidate(){for(String name:IntegrationPresets.NAMES){var json=IntegrationPresets.create(name);assertEquals("xirc_vfx:example/"+name,EffectCodec.compile(json).id());assertEquals(2,json.getAsJsonArray("parameters").size());}}
    @Test void fadeAndPauseUseSimulationClock(){var c=new PlaybackControls();c.tint(new Color(.5,1,.25,.8));c.fadeOut(.1);c.pause(true);c.tick();assertEquals(.8,c.color(new Color(1,1,1,1)).a(),1e-8);c.pause(false);c.tick();assertEquals(.4,c.color(new Color(1,1,1,1)).a(),1e-8);c.tick();assertFalse(c.alive());}
    @Test void instanceRotationAndScaleAffectBirthsWithoutChangingDefinition(){var json=Templates.newProject("transform_api");var emitter=json.getAsJsonArray("emitters").get(0).getAsJsonObject();emitter.add("offset",JsonParser.parseString("[1,0,0]"));emitter.addProperty("rate",20);var effect=EffectCodec.compile(json);var sim=new Simulation(effect);var controls=new PlaybackControls();controls.rotate(new Vec(0,0,90));controls.scale(2);sim.setInstancePose(controls.pose());sim.tick(Vec.ZERO,Simulation.NO_COLLISION);var point=sim.particles().getFirst().position;assertEquals(0,point.x(),1e-6);assertEquals(2,point.y(),1e-6);assertEquals(1,effect.emitters().getFirst().offset().x());}
    @Test void explicitSeedOverridesAuthoredSeedDeterministically(){var effect=EffectCodec.compile(Templates.create("sparks"));var a=new Simulation(effect,1234567890123L);var b=new Simulation(effect,1234567890123L);var c=new Simulation(effect,5);for(int i=0;i<10;i++){a.tick(Vec.ZERO,Simulation.NO_COLLISION);b.tick(Vec.ZERO,Simulation.NO_COLLISION);c.tick(Vec.ZERO,Simulation.NO_COLLISION);}assertEquals(a.particles().getFirst().position,b.particles().getFirst().position);assertNotEquals(a.particles().getFirst().position,c.particles().getFirst().position);}
    @Test void digestIgnoresObjectOrderButPreservesArrayOrder(){var a=JsonParser.parseString("{\"b\":[1,2],\"a\":3}").getAsJsonObject();var b=JsonParser.parseString("{\"a\":3,\"b\":[1,2]}").getAsJsonObject();assertEquals(DefinitionHash.of(a),DefinitionHash.of(b));b.add("b",JsonParser.parseString("[2,1]"));assertNotEquals(DefinitionHash.of(a),DefinitionHash.of(b));}
    private PlaybackMessage message(){return new PlaybackMessage(UUID.randomUUID(),false,"test:effect","a".repeat(64),"minecraft:overworld",new Vec(1,2,3),Long.MAX_VALUE,Map.of("power",2.),new Color(.5,1,1,1),UUID.randomUUID(),new Vec(0,1,0));}
    @Test void boundedProtocolRoundTripsPlayAndStop(){var m=message();assertEquals(m,PlaybackMessage.decode(m.encode()));assertEquals(m.stopped(),PlaybackMessage.decode(m.stopped().encode()));assertTrue(m.encode().length<PlaybackMessage.MAX_BYTES);}
    @Test void malformedAndOversizedPacketsAreRejected(){assertThrows(IllegalArgumentException.class,()->PlaybackMessage.decode(new byte[8193]));assertThrows(IllegalArgumentException.class,()->PlaybackMessage.decode(new byte[]{1,2}));var bytes=message().encode();assertThrows(IllegalArgumentException.class,()->PlaybackMessage.decode(Arrays.copyOf(bytes,bytes.length+1)));bytes[0]=2;assertThrows(IllegalArgumentException.class,()->PlaybackMessage.decode(bytes));}
    @Test void rejectsNonfiniteAndUnboundedRuntimeInputs(){var c=new PlaybackControls();assertThrows(IllegalArgumentException.class,()->c.move(new Vec(Double.NaN,0,0)));assertThrows(IllegalArgumentException.class,()->c.scale(9));assertThrows(IllegalArgumentException.class,()->c.tint(new Color(2,0,0,1)));assertThrows(IllegalArgumentException.class,()->c.fadeOut(-1));}
}
