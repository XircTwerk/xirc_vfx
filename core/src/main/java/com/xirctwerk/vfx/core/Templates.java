package com.xirctwerk.vfx.core;

import com.google.gson.*;
import java.util.List;

public final class Templates {
    public static JsonObject newProject(String slug) {
        var json=EffectCodec.parse("{\"schemaVersion\":1,\"id\":\"xirc_vfx_studio:new_effect\",\"name\":\"New effect\",\"author\":\"XircTwerk\",\"duration\":5,\"emitters\":[{\"id\":\"main\",\"rate\":20,\"lifetime\":1,\"speed\":0,\"size\":0.15,\"color\":[1,1,1,1]}]}");
        json.addProperty("id","xirc_vfx_studio:"+slug);json.addProperty("name",slug.replace('_',' '));EffectCodec.compile(json);return json;
    }
    public static final List<String> NAMES=List.of("fire","smoke","sparks","magic_aura","spiral","magic_circle","projectile_trail","beam","explosion","slash","pixel_slash","mesh_ring","mesh_sphere");
    public static final List<String> ADVANCED_NAMES=List.of("singularity_collapse","crimson_impact","astral_gate","void_cleave","storm_lance","solar_eruption","celestial_array","arcane_overdrive","spline_serpent","vortex_forge","orbital_lattice","prismatic_orrery");
    public static final List<String> ALL_NAMES=java.util.stream.Stream.concat(NAMES.stream(),ADVANCED_NAMES.stream()).toList();
    private static JsonArray vector(double... values) { JsonArray a=new JsonArray(); for(double n:values) a.add(n); return a; }
    public static JsonObject create(String name) {
        if(ADVANCED_NAMES.contains(name)) return AdvancedPresets.create(name);
        if(!NAMES.contains(name)) throw new IllegalArgumentException("Unknown template: "+name);
        JsonObject root=new JsonObject(); root.addProperty("schemaVersion",1); root.addProperty("id","xirc_vfx_studio:"+name); root.addProperty("name",title(name)); root.addProperty("author","XircTwerk"); root.addProperty("duration",5); root.addProperty("loop",!name.equals("explosion")); root.addProperty("seed",42);
        JsonObject e=new JsonObject(); e.addProperty("id","main"); e.addProperty("kind","particle"); e.addProperty("shape","cone"); e.addProperty("radius",0.35); e.addProperty("spread",0.3); e.addProperty("rate",40); e.addProperty("lifetime",1.2); e.addProperty("size",0.14); e.addProperty("speed",1); e.addProperty("fade",0.5); e.addProperty("glow",true); e.addProperty("texture","xirc_vfx_studio:textures/effect/soft.png"); e.add("direction",vector(0,1,0)); e.add("color",vector(1,0.4,0.05,1));
        switch(name) {
            case "slash", "pixel_slash", "mesh_ring", "mesh_sphere" -> {
                e.addProperty("kind",name.equals("mesh_ring")?"ring_mesh":name.equals("mesh_sphere")?"sphere_mesh":"slash");e.addProperty("shape","point");e.addProperty("spread",0);e.addProperty("radius",1.3);e.addProperty("rate",2);e.addProperty("lifetime",.6);e.addProperty("size",.28);e.addProperty("speed",0);e.addProperty("pixelSize",name.equals("pixel_slash")?6:1);e.add("direction",vector(0,0,1));e.add("color",vector(.12,.65,1,.8));
            }
            case "fire" -> { e.add("force",vector(0,0.5,0)); e.addProperty("noise",0.2); e.add("color",gradient(new double[]{1,0.8,0.2,1},new double[]{1,0.12,0.01,0})); e.add("size",curve(0.16,0.02)); }
            case "smoke" -> { e.addProperty("glow",false); e.addProperty("speed",0.6); e.addProperty("lifetime",3); e.addProperty("noise",0.25); e.addProperty("rate",18); e.add("color",vector(0.55,0.57,0.6,0.4)); e.add("size",curve(0.18,0.65)); }
            case "sparks" -> { e.addProperty("shape","sphere"); e.addProperty("spread",1); e.addProperty("rate",35); e.addProperty("speed",3); e.addProperty("size",0.04); e.addProperty("collision",true); e.addProperty("lifetime",1); e.add("force",vector(0,-4,0)); e.add("color",vector(1,0.75,0.18,1)); }
            case "magic_aura" -> { e.addProperty("shape","ring"); e.addProperty("radius",1); e.addProperty("spread",0.1); e.addProperty("speed",0.3); e.addProperty("lifetime",2.5); e.add("color",gradient(new double[]{0.7,0.15,1,0.8},new double[]{0.2,0.5,1,0})); }
            case "spiral" -> { e.addProperty("shape","spiral"); e.addProperty("radius",0.9); e.addProperty("spread",0.05); e.addProperty("rate",50); e.addProperty("speed",0.2); e.addProperty("lifetime",1.3); e.addProperty("length",2); e.add("color",vector(0.65,0.2,1,0.9)); }
            case "magic_circle" -> { e.addProperty("shape","ring"); e.addProperty("radius",1.5); e.addProperty("spread",0); e.addProperty("speed",0); e.addProperty("size",0.07); e.addProperty("rate",90); e.addProperty("lifetime",0.6); e.add("color",vector(0.2,0.8,1,0.9)); }
            case "projectile_trail" -> { e.addProperty("kind","trail"); e.addProperty("shape","point"); e.addProperty("space","world"); e.addProperty("speed",0.5); e.addProperty("rate",20); e.addProperty("size",0.08); e.addProperty("trailPoints",16); e.addProperty("lifetime",1); e.add("direction",vector(0,0,1)); e.add("color",gradient(new double[]{0.3,0.8,1,1},new double[]{0.1,0.2,1,0})); }
            case "beam" -> { e.addProperty("kind","beam"); e.addProperty("shape","point"); e.addProperty("spread",0); e.addProperty("speed",0); e.addProperty("rate",20); e.addProperty("lifetime",0.1); e.addProperty("length",4); e.addProperty("size",0.07); e.add("direction",vector(0,1,0)); e.add("color",vector(0.4,0.65,1,0.7)); }
            case "explosion" -> { root.addProperty("duration",1); e.addProperty("shape","sphere"); e.addProperty("radius",0.2); e.addProperty("spread",1); e.add("direction",vector(0,0,0)); e.addProperty("speed",4); e.addProperty("rate",0); e.addProperty("lifetime",1.5); e.addProperty("drag",2); e.add("size",curve(0.16,0.03)); JsonObject burst=new JsonObject(); burst.addProperty("time",0); burst.addProperty("count",160); JsonArray bursts=new JsonArray(); bursts.add(burst); e.add("bursts",bursts); }
        }
        JsonArray emitters=new JsonArray(); emitters.add(e); root.add("emitters",emitters); EffectCodec.compile(root); return root;
    }
    public static String title(String name) { StringBuilder s=new StringBuilder(); for(String part:name.split("_")) { if(!s.isEmpty()) s.append(' '); s.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)); } return s.toString(); }
    public static JsonObject curve(double from,double to) { JsonArray keys=new JsonArray(); for(int i=0;i<2;i++) { JsonObject k=new JsonObject(); k.addProperty("time",i); k.addProperty("value",i==0?from:to); keys.add(k); } JsonObject o=new JsonObject(); o.add("curve",keys); return o; }
    public static JsonObject gradient(double[] from,double[] to) { JsonArray keys=new JsonArray(); for(int i=0;i<2;i++) { JsonObject k=new JsonObject(); k.addProperty("time",i); k.add("color",vector(i==0?from:to)); keys.add(k); } JsonObject o=new JsonObject(); o.add("gradient",keys); return o; }
    public static void main(String[] args) throws Exception { java.nio.file.Path path=java.nio.file.Path.of(args[0]); java.nio.file.Files.createDirectories(path); for(String name:ALL_NAMES) java.nio.file.Files.writeString(path.resolve(name+".json"),EffectCodec.GSON.toJson(create(name))); }
}
