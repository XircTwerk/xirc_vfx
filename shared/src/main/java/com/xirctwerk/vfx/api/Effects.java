package com.xirctwerk.vfx.api;

import com.google.gson.JsonObject;
import com.xirctwerk.vfx.core.*;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Common-side registry. Register the same authored definitions on server and client during setup. */
public final class Effects {
    private Effects(){}
    public record Definition(JsonObject json,String digest){public Definition{json=json.deepCopy();} @Override public JsonObject json(){return json.deepCopy();}}
    private static final Map<ResourceLocation,Definition> entries=new LinkedHashMap<>();
    static {for(String name:IntegrationPresets.NAMES){var json=IntegrationPresets.create(name);register(ResourceLocation.parse(json.get("id").getAsString()),json);}for(String name:Templates.ALL_NAMES){var json=Templates.create(name);register(ResourceLocation.parse(json.get("id").getAsString()),json);}}
    public static synchronized void register(ResourceLocation id,JsonObject authored){
        Objects.requireNonNull(id);EffectCodec.compile(authored);if(!id.toString().equals(authored.get("id").getAsString()))throw new IllegalArgumentException("Registry ID must match authored effect ID");
        if(entries.size()>=1024)throw new IllegalStateException("1024 JSON effects already registered");
        if(entries.putIfAbsent(id,new Definition(authored,DefinitionHash.of(authored)))!=null)throw new IllegalArgumentException("Duplicate JSON effect: "+id);
    }
    public static synchronized Definition get(ResourceLocation id){var entry=entries.get(id);if(entry==null)throw new IllegalArgumentException("Unknown JSON effect: "+id+". Register it on both sides during mod setup.");return entry;}
    public static synchronized Set<ResourceLocation> ids(){return Set.copyOf(entries.keySet());}
}
