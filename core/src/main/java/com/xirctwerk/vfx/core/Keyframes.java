package com.xirctwerk.vfx.core;

import com.google.gson.*;
import java.util.*;

/** Edits the existing normalized-age curve/gradient format without reducing authored JSON. */
public final class Keyframes {
    private Keyframes() {}
    public static final List<String> TRACKS=List.of("size","speed","rotation","color","meshScale");
    private static String arrayName(String property) {check(property);return property.equals("color")?"gradient":"curve";}
    private static String valueName(String property) {return property.equals("color")?"color":"value";}
    private static void check(String property) {if(!TRACKS.contains(property))throw new IllegalArgumentException("This property cannot be keyframed: "+property);}
    public static JsonArray keys(JsonObject layer,String property) {
        return readKeys(layer,property).deepCopy();
    }
    private static JsonArray readKeys(JsonObject layer,String property){
        String name=arrayName(property);var value=layer.get(property);
        return value!=null&&value.isJsonObject()&&value.getAsJsonObject().has(name)?value.getAsJsonObject().getAsJsonArray(name):new JsonArray();
    }
    public static JsonElement sample(JsonObject layer,String property,double time) {
        check(property);var keys=readKeys(layer,property);String name=valueName(property);
        if(keys.isEmpty()) {
            var value=layer.get(property);
            if(value!=null&&value.isJsonObject())throw new IllegalArgumentException("Random ranges cannot be converted implicitly. Use Curve / range to choose a constant or curve first.");
            return value!=null?value.deepCopy():property.equals("color")?JsonParser.parseString("[0.7,0.3,1,1]"):new JsonPrimitive(property.equals("size")?.1:property.equals("rotation")?0:1);
        }
        var a=keys.get(0).getAsJsonObject();if(time<=a.get("time").getAsDouble())return a.get(name).deepCopy();
        for(int i=1;i<keys.size();i++) {var b=keys.get(i).getAsJsonObject();if(time<=b.get("time").getAsDouble()) {
            double t=EffectCodec.easing(a,"key").at((time-a.get("time").getAsDouble())/(b.get("time").getAsDouble()-a.get("time").getAsDouble()));
            if(property.equals("color")) {var result=new JsonArray();for(int c=0;c<4;c++){var ac=a.getAsJsonArray(name);var bc=b.getAsJsonArray(name);double x=c<ac.size()?ac.get(c).getAsDouble():1,y=c<bc.size()?bc.get(c).getAsDouble():1;result.add(x+(y-x)*t);}return result;}
            double x=a.get(name).getAsDouble();return new JsonPrimitive(x+(b.get(name).getAsDouble()-x)*t);
        }a=b;}
        return a.get(name).deepCopy();
    }
    public static void put(JsonObject layer,String property,double time,JsonElement value) {
        if(!Double.isFinite(time)||time<0||time>1)throw new IllegalArgumentException("Key time must be between birth (0) and death (1).");
        var keys=keys(layer,property);
        if(keys.isEmpty()) {var initial=sample(layer,property,0);keys.add(key(property,0,initial));keys.add(key(property,1,initial));}
        boolean replaced=false;for(int i=0;i<keys.size();i++)if(Math.abs(keys.get(i).getAsJsonObject().get("time").getAsDouble()-time)<1e-6){keys.get(i).getAsJsonObject().add(valueName(property),value.deepCopy());replaced=true;break;}
        if(!replaced){if(keys.size()>=64)throw new IllegalArgumentException("At most 64 keys per track.");keys.add(key(property,time,value));}
        var sorted=new ArrayList<JsonElement>();keys.forEach(sorted::add);sorted.sort(Comparator.comparingDouble(k->k.getAsJsonObject().get("time").getAsDouble()));var result=new JsonArray();sorted.forEach(result::add);store(layer,property,result);
    }
    public static void move(JsonObject layer,String property,int index,double time) {
        var keys=keys(layer,property);var key=keys.get(index).getAsJsonObject();
        double lo=index==0?0:keys.get(index-1).getAsJsonObject().get("time").getAsDouble()+.00001,hi=index==keys.size()-1?1:keys.get(index+1).getAsJsonObject().get("time").getAsDouble()-.00001;
        if(!Double.isFinite(time)||time<lo||time>hi)throw new IllegalArgumentException("Keep keys ordered and separated from their neighbours.");
        key.addProperty("time",time);store(layer,property,keys);
    }
    public static void remove(JsonObject layer,String property,int index) {var keys=keys(layer,property);if(keys.size()<=2)throw new IllegalArgumentException("Keep at least two keys. Use Edit curve to switch to a constant.");keys.remove(index);store(layer,property,keys);}
    /** Retime a bounded key selection atomically; callers validate through Project before commit. */
    public static void retime(JsonObject layer,String property,Set<Integer> selection,double shift,double scale){
        if(selection.isEmpty())throw new IllegalArgumentException("Select keys first.");if(!Double.isFinite(shift)||!Double.isFinite(scale)||scale<=0)throw new IllegalArgumentException("Use a finite positive time scale.");
        var keys=keys(layer,property);for(int i:selection)if(i<0||i>=keys.size())throw new IllegalArgumentException("Key selection is stale.");
        double origin=selection.stream().mapToDouble(i->keys.get(i).getAsJsonObject().get("time").getAsDouble()).min().orElseThrow();
        for(int i:selection){var k=keys.get(i).getAsJsonObject();k.addProperty("time",origin+(k.get("time").getAsDouble()-origin)*scale+shift);}
        double previous=-1;for(var item:keys){double t=item.getAsJsonObject().get("time").getAsDouble();if(t<0||t>1||t<=previous+1e-6)throw new IllegalArgumentException("Retimed keys must stay ordered, separate and between 0 and 1.");previous=t;}store(layer,property,keys);
    }
    private static JsonObject key(String property,double time,JsonElement value){var key=new JsonObject();key.addProperty("time",time);key.add(valueName(property),value.deepCopy());return key;}
    private static void store(JsonObject layer,String property,JsonArray keys){var value=new JsonObject();value.add(arrayName(property),keys);layer.add(property,value);}
}
