package com.xirctwerk.vfx.core;

import com.google.gson.*;
import java.util.*;

/** Original examples shipped with the library, usable without a separate integration mod. */
public final class IntegrationPresets {
    private IntegrationPresets(){}
    public static final List<String> NAMES=List.of("weapon_trail","projectile","impact","charged_attack","aura");
    public static JsonObject create(String name){
        String template=switch(name){case "weapon_trail"->"pixel_slash";case "projectile"->"projectile_trail";case "impact"->"crimson_impact";case "charged_attack"->"arcane_overdrive";case "aura"->"orbital_lattice";default->throw new IllegalArgumentException("Unknown built-in integration example: "+name);};
        var json=Templates.create(template);json.addProperty("id","xirc_vfx:example/"+name);json.addProperty("name","Example: "+name.replace('_',' '));json.addProperty("loop",name.equals("aura")||name.equals("projectile"));
        // These controls intentionally bind scalar radius/bloom; authored curves/gradients stay intact.
        var layers=new LinkedHashSet<String>();json.getAsJsonArray("emitters").forEach(e->layers.add(e.getAsJsonObject().get("id").getAsString()));
        if(json.has("parameters"))json.remove("parameters");
        Parameters.expose(json,layers,"radius","radius");
        Parameters.expose(json,layers,"power","bloom");
        EffectCodec.compile(json);return json;
    }
}
