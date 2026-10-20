package com.xirctwerk.vfx.core;

import com.google.gson.*;
import java.util.*;

/** Atomic JSON-preserving authoring operations. Commit results through Project for validation/undo. */
public final class Authoring {
    private Authoring() {}
    public static final List<String> NUMERIC=List.of("size","speed","rotation","lifetime","meshScale","opacity","spin","aspect","start","duration","rate","radius","length","spread","drag","noise","bloom","fade","bounce");
    public static JsonObject layer(JsonObject json,String id){for(var item:json.getAsJsonArray("emitters")){var e=item.getAsJsonObject();if(e.get("id").getAsString().equals(id))return e;}throw new IllegalArgumentException("Layer no longer exists: "+id);}
    public static Set<String> ids(JsonObject json){var ids=new LinkedHashSet<String>();for(var item:json.getAsJsonArray("emitters"))ids.add(item.getAsJsonObject().get("id").getAsString());return ids;}
    public static void set(JsonObject json,Set<String> selection,String field,JsonElement value){if(selection.isEmpty())throw new IllegalArgumentException("Select layers first.");for(String id:selection)layer(json,id).add(field,value.deepCopy());}
    public static Set<String> selectedRoots(JsonObject json,Set<String> selection){
        var roots=new LinkedHashSet<String>();
        for(String id:selection){var e=layer(json,id);String parent=e.has("parent")?e.get("parent").getAsString():"";var visited=new HashSet<String>();boolean inherited=false;
            while(!parent.isEmpty()){if(!visited.add(parent))throw new IllegalArgumentException("Cannot transform a cyclic hierarchy.");if(selection.contains(parent)){inherited=true;break;}var p=layer(json,parent);parent=p.has("parent")?p.get("parent").getAsString():"";}
            if(!inherited)roots.add(id);
        }return Collections.unmodifiableSet(roots);
    }
    public static void setTransform(JsonObject json,Set<String> selection,String field,JsonElement value){
        if(!Set.of("rotation","scale").contains(field))throw new IllegalArgumentException("Choose static transform rotation or scale.");
        if(selection.isEmpty())throw new IllegalArgumentException("Select layers first.");
        for(String id:selectedRoots(json,selection)){var layer=layer(json,id);if(!layer.has("transform"))layer.add("transform",new JsonObject());layer.getAsJsonObject("transform").add(field,value.deepCopy());}
    }
    public static double defaultNumber(String field){return switch(field){case "size"->.1;case "lifetime","speed","meshScale","opacity","aspect"->1;case "duration"->5;case "rate"->30;case "radius","bounce"->.5;case "spread"->.2;case "length"->3;case "fade"->.3;default->0;};}
    public static void relative(JsonObject json,Set<String> selection,String field,double add,double multiply){
        if(!NUMERIC.contains(field)||!Double.isFinite(add)||!Double.isFinite(multiply))throw new IllegalArgumentException("Choose a supported numeric field and finite operands.");
        for(String id:selection){var e=layer(json,id);var value=e.has(field)?e.get(field).deepCopy():new JsonPrimitive(field.equals("duration")&&json.has("duration")?json.get("duration").getAsDouble():defaultNumber(field));e.add(field,transform(value,add,multiply));}
    }
    private static JsonElement transform(JsonElement value,double add,double multiply){
        if(value.isJsonPrimitive()&&value.getAsJsonPrimitive().isNumber())return new JsonPrimitive(value.getAsDouble()*multiply+add);
        if(!value.isJsonObject())throw new IllegalArgumentException("Relative editing requires a number, random range or numeric curve.");
        var o=value.getAsJsonObject();if(o.has("curve")){for(var k:o.getAsJsonArray("curve")){var key=k.getAsJsonObject();key.addProperty("value",key.get("value").getAsDouble()*multiply+add);}}
        else if(o.has("min")&&o.has("max")){double a=o.get("min").getAsDouble()*multiply+add,b=o.get("max").getAsDouble()*multiply+add;o.addProperty("min",Math.min(a,b));o.addProperty("max",Math.max(a,b));}
        else throw new IllegalArgumentException("Unsupported numeric structure.");return o;
    }
    public static Set<String> duplicate(JsonObject json,Set<String> selection){
        var all=ids(json);var rename=new LinkedHashMap<String,String>();var copies=new ArrayList<JsonObject>();
        for(String id:selection){var copy=layer(json,id).deepCopy();String stem=id.length()>52?id.substring(0,52):id;String next=stem+"_copy";int i=2;while(all.contains(next))next=stem+"_copy_"+i++;all.add(next);rename.put(id,next);copy.addProperty("id",next);copies.add(copy);}
        for(var copy:copies){if(copy.has("parent")&&rename.containsKey(copy.get("parent").getAsString()))copy.addProperty("parent",rename.get(copy.get("parent").getAsString()));json.getAsJsonArray("emitters").add(copy);}return new LinkedHashSet<>(rename.values());
    }
    public static void remove(JsonObject json,Set<String> selection){var a=json.getAsJsonArray("emitters");for(int i=a.size()-1;i>=0;i--)if(selection.contains(a.get(i).getAsJsonObject().get("id").getAsString()))a.remove(i);}
    /** Insert an identity parent without rebaking authored offsets, curves or descendants. */
    public static String group(JsonObject json,Set<String> selection){
        EffectCodec.compile(json);if(selection.isEmpty())throw new IllegalArgumentException("Select layers to group first.");
        var all=ids(json);if(all.size()>=64)throw new IllegalArgumentException("A transform group requires one free layer slot (maximum 64 layers).");
        var roots=new LinkedHashSet<String>();String common=null;
        for(String id:selection){var e=layer(json,id);String parent=e.has("parent")?e.get("parent").getAsString():"",ancestor=parent;boolean nested=false;
            while(!ancestor.isEmpty()){if(selection.contains(ancestor)){nested=true;break;}var p=layer(json,ancestor);ancestor=p.has("parent")?p.get("parent").getAsString():"";}
            if(nested)continue;
            if(common!=null&&!common.equals(parent))throw new IllegalArgumentException("Group layers under the same existing parent; grouping different parent spaces would require rebaking their animations.");
            common=parent;roots.add(id);
        }
        String id="group";for(int i=1;all.contains(id);i++)id="group_"+i;
        var group=new JsonObject();group.addProperty("id",id);group.addProperty("rate",0);group.addProperty("speed",0);group.addProperty("spread",0);group.addProperty("size",.01);group.addProperty("lifetime",.05);group.addProperty("duration",.05);
        if(common!=null&&!common.isEmpty())group.addProperty("parent",common);
        var first=layer(json,roots.iterator().next());if(first.has("folder"))group.add("folder",first.get("folder").deepCopy());
        for(String root:roots)layer(json,root).addProperty("parent",id);
        json.getAsJsonArray("emitters").add(group);return id;
    }
    public static void vary(JsonObject json,Set<String> selection,Set<String> fields,long seed,double amount){
        if(amount<0||amount>1||!Double.isFinite(amount))throw new IllegalArgumentException("Variation must be between 0 and 1.");var random=new Random(seed);
        // Sort so selection click order cannot affect deterministic variations.
        for(String id:new TreeSet<>(selection))for(String field:new TreeSet<>(fields))relative(json,Set.of(id),field,0,1+(random.nextDouble()*2-1)*amount);
    }
    public static void pasteTrack(JsonObject json,Set<String> selection,String field,JsonElement value){if(!Keyframes.TRACKS.contains(field))throw new IllegalArgumentException("Choose a supported animation track.");set(json,selection,field,value);}
    public static void translate(JsonObject json,Set<String> selected,double x,double y,double z){
        for(String id:selected){var e=layer(json,id);String parent=e.has("parent")?e.get("parent").getAsString():"";boolean inherited=false;while(!parent.isEmpty()){if(selected.contains(parent)){inherited=true;break;}var p=layer(json,parent);parent=p.has("parent")?p.get("parent").getAsString():"";}if(inherited)continue;
            var a=e.has("offset")?e.getAsJsonArray("offset"):JsonParser.parseString("[0,0,0]").getAsJsonArray();var v=new JsonArray();v.add(a.get(0).getAsDouble()+x);v.add(a.get(1).getAsDouble()+y);v.add(a.get(2).getAsDouble()+z);e.add("offset",v);}
    }
    public static JsonObject clip(String track,JsonElement value){var result=new JsonObject();result.addProperty("clipVersion",1);result.addProperty("track",track);result.add("value",value.deepCopy());validateClip(result);return result;}
    public static void validateClip(JsonObject clip){
        if(!clip.keySet().equals(Set.of("clipVersion","track","value"))||!clip.get("clipVersion").isJsonPrimitive()||!clip.getAsJsonPrimitive("clipVersion").isNumber()||clip.get("clipVersion").getAsDouble()!=1||!clip.get("track").isJsonPrimitive()||!clip.getAsJsonPrimitive("track").isString()||!Keyframes.TRACKS.contains(clip.get("track").getAsString()))throw new IllegalArgumentException("Use clipVersion 1 with one supported track and value.");
        var fixture=Templates.newProject("clip_validation");var e=fixture.getAsJsonArray("emitters").get(0).getAsJsonObject();e.addProperty("kind","sphere_mesh");e.add(clip.get("track").getAsString(),clip.get("value"));EffectCodec.compile(fixture);
    }
}
