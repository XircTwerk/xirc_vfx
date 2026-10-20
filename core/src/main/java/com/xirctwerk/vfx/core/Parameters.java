package com.xirctwerk.vfx.core;

import com.google.gson.*;
import java.util.*;

/** Named numeric bindings, never expressions. Original JSON is retained; only compilation resolves them. */
public final class Parameters {
    private Parameters(){}
    public record Bounds(double min,double max){}
    public static String binding(JsonObject json,Set<String> layers,String field){if(json.has("parameters"))for(var item:json.getAsJsonArray("parameters")){var parameter=item.getAsJsonObject();for(var target:parameter.getAsJsonArray("targets")){var t=target.getAsJsonObject();if(layers.contains(t.get("layer").getAsString())&&t.get("property").getAsString().equals(field))return parameter.get("name").getAsString();}}return null;}
    public static Bounds bounds(String field){return switch(field){
        case "size"->new Bounds(.001,10);case "speed"->new Bounds(-50,50);case "rotation"->new Bounds(-36000,36000);case "lifetime","duration"->new Bounds(.05,field.equals("lifetime")?30:300);
        case "meshScale"->new Bounds(.001,8);case "opacity","spread","fade","bounce"->new Bounds(0,1);case "spin"->new Bounds(-3600,3600);case "aspect"->new Bounds(.05,20);
        case "start"->new Bounds(0,300);case "rate"->new Bounds(0,2000);case "radius"->new Bounds(0,64);case "length"->new Bounds(.01,128);case "drag","noise"->new Bounds(0,20);case "bloom"->new Bounds(0,4);
        default->throw new IllegalArgumentException("Unsupported parameter target: "+field);};}
    private static EffectCodec.InvalidEffect bad(String p,String message){return new EffectCodec.InvalidEffect(p,message);}
    private static void fields(JsonObject o,String path,String... fields){if(!Set.of(fields).containsAll(o.keySet()))throw bad(path,"Unknown parameter property.");}
    private static String text(JsonObject o,String key,String path){var v=o.get(key);if(v==null||!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isString())throw bad(path+"."+key,"Text is required.");return v.getAsString();}
    private static double number(JsonObject o,String key,double fallback,String path){var v=o.get(key);if(v==null)return fallback;if(!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isNumber()||!Double.isFinite(v.getAsDouble())||Math.abs(v.getAsDouble())>1_000_000)throw bad(path+"."+key,"Use a finite number within +/-1000000.");return v.getAsDouble();}
    public static JsonObject resolve(JsonObject authored){
        var result=authored.deepCopy();result.remove("parameters");EffectCodec.compile(result);
        var parameters=authored.get("parameters");if(!parameters.isJsonArray()||parameters.getAsJsonArray().size()>32)throw bad("$.parameters","Use at most 32 named parameters.");
        Set<String> names=new HashSet<>(),bound=new HashSet<>();int i=0;
        for(var item:parameters.getAsJsonArray()){
            String p="$.parameters["+(i++)+"]";if(!item.isJsonObject())throw bad(p,"Expected parameter object.");var param=item.getAsJsonObject();fields(param,p,"name","label","min","max","value","targets");
            String name=text(param,"name",p);if(!name.matches("[a-z][a-z0-9_]{0,31}")||!names.add(name))throw bad(p+".name","Use a unique lowercase parameter name (1..32 characters).");
            if(param.has("label")&&text(param,"label",p).length()>128)throw bad(p+".label","Use at most 128 characters.");
            if(!param.has("min")||!param.has("max")||!param.has("value"))throw bad(p,"min, max and value are required.");double min=number(param,"min",0,p),max=number(param,"max",1,p),value=number(param,"value",0,p);
            if(min>=max||value<min||value>max)throw bad(p,"Require min < max and value within that range.");
            var targets=param.get("targets");if(targets==null||!targets.isJsonArray()||targets.getAsJsonArray().isEmpty()||targets.getAsJsonArray().size()>64)throw bad(p+".targets","Use 1..64 numeric layer bindings.");
            int j=0;for(var target:targets.getAsJsonArray()){
                String tp=p+".targets["+(j++)+"]";if(!target.isJsonObject())throw bad(tp,"Expected target object.");var t=target.getAsJsonObject();fields(t,tp,"layer","property","scale","offset");String id=text(t,"layer",tp),field=text(t,"property",tp);
                Bounds limits;try{limits=bounds(field);}catch(IllegalArgumentException ex){throw bad(tp+".property",ex.getMessage());}
                var layer=Authoring.layer(result,id);if(!bound.add(id+":"+field))throw bad(tp,"Another parameter already binds this field.");
                if(layer.has(field)&&(!layer.get(field).isJsonPrimitive()||!layer.getAsJsonPrimitive(field).isNumber()))throw bad(tp,"Bindings require scalar fields; keep curves/ranges unbound.");
                double scale=number(t,"scale",1,tp),offset=number(t,"offset",0,tp),a=min*scale+offset,b=max*scale+offset;
                if(Math.min(a,b)<limits.min()||Math.max(a,b)>limits.max())throw bad(tp,"Mapped parameter range exceeds target limits "+limits.min()+".."+limits.max()+".");
                layer.addProperty(field,value*scale+offset);
            }
        }
        return result;
    }
    /** Validated per-playback overrides. Does not mutate the source document. */
    public static JsonObject override(JsonObject authored,Map<String,Double> values){
        if(values==null||values.size()>32)throw new IllegalArgumentException("Use at most 32 parameter overrides");
        var copy=authored.deepCopy();var remaining=new HashSet<>(values.keySet());
        if(copy.has("parameters"))for(var item:copy.getAsJsonArray("parameters")){
            var p=item.getAsJsonObject();String name=p.get("name").getAsString();
            if(values.containsKey(name)){Double value=values.get(name);if(value==null||!Double.isFinite(value))throw new IllegalArgumentException("Parameter must be finite: "+name);p.addProperty("value",value);remaining.remove(name);}
        }
        if(!remaining.isEmpty())throw new IllegalArgumentException("Unknown parameters: "+remaining+". Expose them in the effect's parameters array first.");
        EffectCodec.compile(copy);return copy;
    }
    public static void expose(JsonObject json,Set<String> selected,String name,String field){
        if(selected.isEmpty())throw new IllegalArgumentException("Select layers first.");var bounds=bounds(field);var p=new JsonObject();p.addProperty("name",name);p.addProperty("label",name);p.addProperty("min",bounds.min());p.addProperty("max",bounds.max());
        var first=Authoring.layer(json,selected.iterator().next());var value=first.get(field);if(value!=null&&!value.isJsonPrimitive())throw new IllegalArgumentException("Expose a scalar field; curves and ranges stay authored separately.");
        p.addProperty("value",value==null?Authoring.defaultNumber(field):value.getAsDouble());var targets=new JsonArray();for(String id:selected){var target=new JsonObject();target.addProperty("layer",id);target.addProperty("property",field);targets.add(target);}p.add("targets",targets);
        if(!json.has("parameters"))json.add("parameters",new JsonArray());json.getAsJsonArray("parameters").add(p);
    }
}
