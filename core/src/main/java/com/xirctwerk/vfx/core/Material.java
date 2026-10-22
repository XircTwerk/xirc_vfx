package com.xirctwerk.vfx.core;

import com.google.gson.*;
import java.util.*;

/** Bounded, data-only post material. Each node consumes the preceding node's RGBA image. */
public record Material(List<Node> nodes) {
    public static final List<String> OPERATIONS=List.of("dissolve","outline","fresnel","distortion","scroll","gradient","chromatic","pixelate");
    public record Node(String operation,double amount,double speed,double scale,Effect.Color color,List<Effect.Key> keys,boolean enabled){
        public Node {keys=List.copyOf(keys);}
        public double at(double time){if(keys.isEmpty())return amount;return new Effect.Value(0,1,keys).at(Math.max(0,Math.min(1,time)),amount);}
    }
    public Material {nodes=List.copyOf(nodes);if(nodes.size()>16)throw new IllegalArgumentException("Use at most 16 material nodes.");for(var n:nodes){if(n==null||!OPERATIONS.contains(n.operation())||!finite(n.amount(),0,1)||!finite(n.speed(),-10,10)||!finite(n.scale(),1,64)||n.color()==null||n.keys()==null||n.keys().size()>64)throw new IllegalArgumentException("Invalid material node.");for(double c:new double[]{n.color().r(),n.color().g(),n.color().b(),n.color().a()})if(!finite(c,0,1))throw new IllegalArgumentException("Material color must be 0..1.");double last=-1;for(var k:n.keys()){if(!finite(k.time(),0,1)||k.time()<=last||!finite(k.value(),0,1)||k.easing()==null)throw new IllegalArgumentException("Material keys must be ordered, finite and within 0..1.");last=k.time();}}}
    private static boolean finite(double n,double lo,double hi){return Double.isFinite(n)&&n>=lo&&n<=hi;}
    public static JsonObject empty(){var o=new JsonObject();o.addProperty("schemaVersion",1);o.add("nodes",new JsonArray());return o;}
    public static final List<String> PRESETS=List.of("spectral_shield","void_erosion","hologram","incineration","prismatic_motion");
    public static JsonObject preset(String name){var json=empty();List<String> operations=switch(name){case "spectral_shield"->List.of("gradient","outline","chromatic","pixelate");case "void_erosion"->List.of("distortion","dissolve","outline");case "hologram"->List.of("scroll","gradient","pixelate");case "incineration"->List.of("dissolve","outline","chromatic");case "prismatic_motion"->List.of("distortion","gradient","chromatic");default->throw new IllegalArgumentException("Unknown material preset: "+name);};for(String operation:operations){var n=node(operation);if(operation.equals("pixelate")){n.addProperty("amount",1);n.addProperty("scale",4);}if(operation.equals("dissolve")){n.add("keys",JsonParser.parseString("[{\"time\":0,\"value\":0},{\"time\":1,\"value\":1}]"));}json.getAsJsonArray("nodes").add(n);}compile(json);return json;}
    public static JsonObject node(String operation){if(!OPERATIONS.contains(operation))throw new IllegalArgumentException("Unknown material operation.");var o=new JsonObject();o.addProperty("operation",operation);o.addProperty("amount",.5);o.addProperty("speed",1);o.addProperty("scale",8);o.add("color",JsonParser.parseString("[0.55,0.25,1,1]"));return o;}
    public static Material compile(JsonObject json){
        fields(json,"$","schemaVersion","nodes");if(!json.has("schemaVersion")||number(json.get("schemaVersion"),1,1,"$.schemaVersion")!=1)throw bad("$.schemaVersion","Use material schema version 1.");
        var input=json.get("nodes");if(input==null||!input.isJsonArray()||input.getAsJsonArray().size()>16)throw bad("$.nodes","Use an ordered array of at most 16 nodes.");var nodes=new ArrayList<Node>();int i=0;
        for(var item:input.getAsJsonArray()){String path="$.nodes["+(i++)+"]";if(!item.isJsonObject())throw bad(path,"Expected node object.");var o=item.getAsJsonObject();fields(o,path,"operation","amount","speed","scale","color","keys","enabled");
            if(!o.has("operation")||!o.get("operation").isJsonPrimitive()||!o.getAsJsonPrimitive("operation").isString()||!OPERATIONS.contains(o.get("operation").getAsString()))throw bad(path+".operation","Choose a built-in material operation; imported shader code is not supported.");
            double amount=n(o,"amount",.5,0,1,path),speed=n(o,"speed",1,-10,10,path),scale=n(o,"scale",8,1,64,path);var color=new Effect.Color(.55,.25,1,1);
            if(o.has("color")){var c=o.get("color");if(!c.isJsonArray()||c.getAsJsonArray().size()!=4)throw bad(path+".color","Use four RGBA numbers in 0..1.");var a=c.getAsJsonArray();color=new Effect.Color(number(a.get(0),0,1,path+".color[0]"),number(a.get(1),0,1,path+".color[1]"),number(a.get(2),0,1,path+".color[2]"),number(a.get(3),0,1,path+".color[3]"));}
            var keys=new ArrayList<Effect.Key>();if(o.has("keys")){var a=o.get("keys");if(!a.isJsonArray()||a.getAsJsonArray().size()>64)throw bad(path+".keys","Use at most 64 amount keys.");double last=-1;for(var k:a.getAsJsonArray()){if(!k.isJsonObject())throw bad(path+".keys","Expected time/value object.");var key=k.getAsJsonObject();fields(key,path+".keys","time","value");double t=number(key.get("time"),0,1,path+".keys.time");if(t<=last)throw bad(path+".keys","Key times must increase strictly.");last=t;keys.add(new Effect.Key(t,number(key.get("value"),0,1,path+".keys.value")));}}
            boolean enabled=true;if(o.has("enabled")){var e=o.get("enabled");if(!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isBoolean())throw bad(path+".enabled","Expected boolean.");enabled=e.getAsBoolean();}
            nodes.add(new Node(o.get("operation").getAsString(),amount,speed,scale,color,List.copyOf(keys),enabled));
        }return new Material(nodes);
    }
    private static double n(JsonObject o,String k,double d,double lo,double hi,String p){return o.has(k)?number(o.get(k),lo,hi,p+"."+k):d;}
    private static double number(JsonElement v,double lo,double hi,String p){if(v==null||!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isNumber()||!finite(v.getAsDouble(),lo,hi))throw bad(p,"Use a finite number in "+lo+".."+hi+".");return v.getAsDouble();}
    private static void fields(JsonObject o,String p,String... allowed){var names=Set.of(allowed);for(String k:o.keySet())if(!names.contains(k))throw bad(p+"."+k,"Unsupported material field. Use built-in operations and typed numeric values.");}
    private static EffectCodec.InvalidEffect bad(String p,String m){return new EffectCodec.InvalidEffect(p,m);}
}
