package com.xirctwerk.vfx.core;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.StringReader;
import java.util.*;
import static com.xirctwerk.vfx.core.Effect.*;

/** Rejects unsupported data instead of silently dropping fields. No expressions or code are evaluated. */
public final class EffectCodec {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public static final int MAX_JSON_BYTES = 1_048_576;
    public static class InvalidEffect extends IllegalArgumentException {
        public InvalidEffect(String path, String message) { super(path + ": " + message); }
    }
    private static InvalidEffect bad(String path, String message) { return new InvalidEffect(path, message); }
    public static JsonObject parse(String text) {
        JsonObject json=parseObject(text);compile(json);return json;
    }
    public static JsonObject parseObject(String text) {
        if (text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_JSON_BYTES) throw bad("$", "JSON exceeds 1 MiB; split the project.");
        try {
            JsonReader reader = new JsonReader(new StringReader(text));
            reader.setLenient(false);
            JsonElement parsed = read(reader, "$", 0);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw bad("$", "Trailing content after JSON.");
            JsonObject json = obj(parsed,"$");
            return json;
        } catch (InvalidEffect e) { throw e; }
        catch (Exception e) { throw bad("$", "Invalid JSON: " + e.getMessage()); }
    }
    private static JsonElement read(JsonReader r, String p, int depth) throws java.io.IOException {
        if(depth>32) throw bad(p,"Nesting exceeds 32 levels.");
        switch(r.peek()) {
            case BEGIN_OBJECT: {
                JsonObject o=new JsonObject(); r.beginObject();
                while(r.hasNext()) { String key=r.nextName(); if(o.has(key)) throw bad(p+"."+key,"Duplicate field."); o.add(key,read(r,p+"."+key,depth+1)); }
                r.endObject(); return o;
            }
            case BEGIN_ARRAY: {
                JsonArray a=new JsonArray(); r.beginArray();
                while(r.hasNext()) { if(a.size()>=8192) throw bad(p,"Array exceeds 8192 entries."); a.add(read(r,p+"["+a.size()+"]",depth+1)); }
                r.endArray(); return a;
            }
            case STRING: return new JsonPrimitive(r.nextString());
            case NUMBER: { String n=r.nextString(); try { return new JsonPrimitive(new java.math.BigDecimal(n)); } catch(Exception e) { throw bad(p,"Invalid number."); } }
            case BOOLEAN: return new JsonPrimitive(r.nextBoolean());
            case NULL: r.nextNull(); return JsonNull.INSTANCE;
            default: throw bad(p,"Expected JSON value.");
        }
    }
    public static Effect compile(JsonObject o) {
        if(o.has("parameters"))return compile(Parameters.resolve(o));
        fields(o,"$","schemaVersion","id","name","author","description","duration","loop","seed","emitters","pivot","autoDuration","markers","wind");
        if(o.has("markers")){var markers=array(o.get("markers"),"$.markers",0,64);for(int i=0;i<markers.size();i++){String p="$.markers["+i+"]";var marker=obj(markers.get(i),p);fields(marker,p,"name","time");if(string(marker,"name",null,p).length()>128)throw bad(p+".name","Use at most 128 characters.");if(!marker.has("time"))throw bad(p+".time","Time is required.");number(marker,"time",0,0,631,p);}}
        if(integer(o,"schemaVersion",-1,1,1,"$")!=1) throw bad("$.schemaVersion","Use schema version 1.");
        String id=string(o,"id",null,"$"); resource(id,"$.id");
        String name=string(o,"name",id,"$");
        string(o,"author","Xirc","$"); string(o,"description","","$");
        double duration=number(o,"duration",5,0.05,300,"$");
        final double authoredDuration=duration;
        boolean autoDuration=bool(o,"autoDuration",false,"$");
        boolean loop=bool(o,"loop",false,"$");
        long seed=(long)number(o,"seed",0,-2147483648d,2147483647d,"$");
        if(o.has("seed") && o.get("seed").getAsDouble()!=seed) throw bad("$.seed","Seed must be an integer.");
        JsonArray array=array(o.get("emitters"),"$.emitters",1,64);
        if(autoDuration){duration=.05;for(int i=0;i<array.size();i++){String p="$.emitters["+i+"]";var e=obj(array.get(i),p);double end=number(e,"start",0,0,300,p)+number(e,"duration",authoredDuration,.05,300,p)+value(e,"lifetime",1,.05,30,p).max();duration=Math.max(duration,end+Simulation.DT);}}
        List<Emitter> emitters=new ArrayList<>(); Set<String> ids=new HashSet<>(); double peak=0,segments=0;
        for(int i=0;i<array.size();i++) {
            String p="$.emitters["+i+"]"; JsonObject e=obj(array.get(i),p);
            fields(e,p,"id","parent","kind","space","offset","start","duration","rate","bursts","lifetime","size","speed","rotation","color","shape","radius","spread","direction","force","drag","noise","collision","bounce","texture","glow","fade","length","trailPoints","pixelSize","meshScale","bloom","blend","textureAnimation","opacity","spin","aspect","folder","curl","curlFrequency","attractor","attraction","falloff","vortexAxis","vortex","vortexPull","orbitRadius","orbitSpeed","path","pathLoop","pathCycles","pathScatter","alignVelocity","stretch","transform");
            if(string(e,"folder","",p).length()>64)throw bad(p+".folder","Folder names use at most 64 characters.");
            String eid=string(e,"id",null,p);
            if(!eid.matches("[a-z][a-z0-9_]{0,63}")||!ids.add(eid)) throw bad(p+".id","Use a unique lowercase identifier.");
            String parent=string(e,"parent","",p);
            String kind=choice(e,"kind","particle",p,"particle","beam","trail","slash","ring_mesh","cube_mesh","sphere_mesh","lightning");
            if(e.has("meshScale")&&!Geometry.supports(kind))throw bad(p+".meshScale","Mesh scaling requires slash, lightning or a mesh visual.");
            if(integer(e,"pixelSize",1,1,8,p)>1&&!Geometry.supports(kind)) throw bad(p+".pixelSize","Pixelization currently requires a procedural mesh kind.");
            if(e.has("aspect")&&!kind.equals("particle"))throw bad(p+".aspect","Aspect ratio is supported only for particle sprites.");
            if((e.has("alignVelocity")||e.has("stretch"))&&!kind.equals("particle"))throw bad(p,"Velocity alignment and stretching require particle sprites.");
            String space=choice(e,"space","local",p,"local","world");
            double start=number(e,"start",0,0,300,p);
            double ed=number(e,"duration",authoredDuration,0.05,300,p);
            double rate=number(e,"rate",30,0,2000,p);
            Value life=value(e,"lifetime",1,0.05,30,p), size=value(e,"size",0.1,0.001,10,p), speed=value(e,"speed",1,-50,50,p), rotation=value(e,"rotation",0,-36000,36000,p);
            List<Burst> bursts=new ArrayList<>(); int burstTotal=0;
            if(e.has("bursts")) {
                JsonArray bs=array(e.get("bursts"),p+".bursts",0,64);
                for(int j=0;j<bs.size();j++) { String bp=p+".bursts["+j+"]"; JsonObject b=obj(bs.get(j),bp); fields(b,bp,"time","count"); int count=integer(b,"count",1,1,2000,bp); burstTotal+=count; bursts.add(new Burst(number(b,"time",0,0,ed,bp),count)); }
            }
            if(burstTotal>4000) throw bad(p+".bursts","Total burst count exceeds 4000.");
            double maxLife=Math.max(life.min(),life.max());
            double estimate=rate*(loop?maxLife:Math.min(maxLife,Math.min(ed,duration)))+burstTotal*(loop?Math.max(1,Math.ceil(maxLife/duration)):1);
            peak+=estimate;segments+=estimate*(kind.equals("trail")?integer(e,"trailPoints",12,2,64,p):kind.equals("sphere_mesh")?128:Geometry.supports(kind)?32:1);
            String texture=string(e,"texture","xirc_vfx_studio:textures/effect/soft.png",p); resource(texture,p+".texture");
            if(!texture.substring(texture.indexOf(':')+1).startsWith("textures/")||!texture.endsWith(".png")) throw bad(p+".texture","Use a textures/...png resource identifier.");
            emitters.add(new Emitter(eid,parent,kind,space,vec(e,"offset",Vec.ZERO,-128,128,p),start,ed,rate,List.copyOf(bursts),life,size,speed,rotation,gradient(e,p),choice(e,"shape","point",p,"point","sphere","ring","spiral","cone","box"),number(e,"radius",0.5,0,64,p),number(e,"spread",0.2,0,1,p),vec(e,"direction",new Vec(0,1,0),-1,1,p),vec(e,"force",Vec.ZERO,-100,100,p),number(e,"drag",0,0,20,p),number(e,"noise",0,0,20,p),bool(e,"collision",false,p),number(e,"bounce",0.5,0,1,p),texture,bool(e,"glow",true,p),number(e,"fade",0.3,0,1,p),number(e,"length",3,0.01,128,p),integer(e,"trailPoints",12,2,64,p),integer(e,"pixelSize",1,1,8,p),value(e,"meshScale",1,.001,8,p),number(e,"bloom",0,0,4,p),choice(e,"blend","alpha",p,"alpha","additive"),animation(e,p),number(e,"opacity",1,0,1,p),number(e,"spin",0,-3600,3600,p),number(e,"aspect",1,.05,20,p),motion(e,p),transform(e,p)));
        }
        if(peak>8192) throw bad("$.emitters","Estimated live particles exceed 8192; reduce rate, lifetime or bursts.");
        if(segments>65536) throw bad("$.emitters","Estimated ribbon segments exceed 65536; reduce trailPoints, rate or lifetime.");
        for(Emitter e:emitters) {
            Set<String> visited=new HashSet<>(); Emitter node=e;double minScale=e.transform().minimumScale(),maxScale=e.transform().maximumScale();
            while(!node.parent().isEmpty()) {
                if(!visited.add(node.id())) throw bad("$.emitters."+e.id()+".parent","Hierarchy contains a cycle.");
                String pid=node.parent(); node=emitters.stream().filter(x->x.id().equals(pid)).findFirst().orElseThrow(()->bad("$.emitters."+e.id()+".parent","Unknown parent: "+pid));
                minScale*=node.transform().minimumScale();maxScale*=node.transform().maximumScale();
            }
            if(minScale<.0001-1e-12||maxScale>64+1e-12)throw bad("$.emitters."+e.id()+".transform.scale","Combined hierarchy scale must remain between 0.0001 and 64; reduce nesting or scale.");
        }
        return new Effect(id,name,duration,loop,seed,List.copyOf(emitters),vec(o,"pivot",Vec.ZERO,-128,128,"$"),vec(o,"wind",Vec.ZERO,-100,100,"$"));
    }
    private static Transforms.Track transform(JsonObject e,String p){
        if(!e.has("transform"))return Transforms.Track.IDENTITY;
        p+=".transform";var o=obj(e.get("transform"),p);fields(o,p,"rotation","scale","keys","pathInterpolation");
        var keys=new ArrayList<Transforms.Key>();double previous=-1;
        if(o.has("keys")){var a=array(o.get("keys"),p+".keys",2,64);for(int i=0;i<a.size();i++){
            String path=p+".keys["+i+"]";var key=obj(a.get(i),path);fields(key,path,"time","position","rotation","scale","easing");
            if(!key.has("time"))throw bad(path+".time","Effect-clock time in seconds is required.");
            double time=number(key,"time",0,0,631,path);if(time<=previous+1e-6)throw bad(path+".time","Transform keys must be ordered and separated by more than 0.000001 seconds.");previous=time;
            keys.add(new Transforms.Key(time,vec(key,"position",Vec.ZERO,-128,128,path),vec(key,"rotation",Vec.ZERO,-36000,36000,path),number(key,"scale",1,.01,8,path),easing(key,path)));
        }}
        return new Transforms.Track(vec(o,"rotation",Vec.ZERO,-36000,36000,p),number(o,"scale",1,.01,8,p),keys,choice(o,"pathInterpolation","linear",p,"linear","spline"));
    }
    private static Motion.Settings motion(JsonObject e,String p){
        var points=new ArrayList<Vec>();if(e.has("path")){var a=array(e.get("path"),p+".path",2,64);for(int i=0;i<a.size();i++){var v=array(a.get(i),p+".path["+i+"]",3,3);points.add(new Vec(num(v.get(0),-128,128,p+".path["+i+"][0]"),num(v.get(1),-128,128,p+".path["+i+"][1]"),num(v.get(2),-128,128,p+".path["+i+"][2]")));}}
        Vec axis=vec(e,"vortexAxis",new Vec(0,1,0),-1,1,p);double vortex=number(e,"vortex",0,-100,100,p),pull=number(e,"vortexPull",0,-100,100,p);if((vortex!=0||pull!=0)&&axis.length()<1e-9)throw bad(p+".vortexAxis","Use a nonzero vortex axis.");
        return new Motion.Settings(number(e,"curl",0,0,50,p),number(e,"curlFrequency",1,.01,10,p),vec(e,"attractor",Vec.ZERO,-128,128,p),number(e,"attraction",0,-100,100,p),number(e,"falloff",1,0,4,p),axis,vortex,pull,vec(e,"orbitRadius",Vec.ZERO,0,64,p),number(e,"orbitSpeed",0,-3600,3600,p),points,bool(e,"pathLoop",false,p),number(e,"pathCycles",1,.05,10,p),number(e,"pathScatter",0,0,8,p),bool(e,"alignVelocity",false,p),number(e,"stretch",0,0,4,p));
    }
    private static Effect.TextureAnimation animation(JsonObject e,String p) {
        if(!e.has("textureAnimation"))return Effect.TextureAnimation.STILL;
        if(Geometry.supports(string(e,"kind","particle",p)))throw bad(p+".textureAnimation","Sprite sheets require particle, beam or trail visuals.");
        JsonObject a=obj(e.get("textureAnimation"),p+".textureAnimation");String path=p+".textureAnimation";fields(a,path,"columns","rows","frames","fps","loop");
        int columns=integer(a,"columns",1,1,64,path),rows=integer(a,"rows",1,1,64,path),frames=integer(a,"frames",columns*rows,1,4096,path);
        if(frames>columns*rows)throw bad(path+".frames","Frame count exceeds sheet dimensions.");
        return new Effect.TextureAnimation(columns,rows,frames,number(a,"fps",20,.1,120,path),bool(a,"loop",true,path));
    }
    public static double estimatedParticles(Effect effect) {
        double sum=0;
        for(Emitter e:effect.emitters()) { double life=Math.max(e.lifetime().min(),e.lifetime().max());int bursts=e.bursts().stream().mapToInt(Burst::count).sum();sum+=e.rate()*(effect.loop()?life:Math.min(life,Math.min(e.duration(),effect.duration())))+bursts*(effect.loop()?Math.max(1,Math.ceil(life/effect.duration())):1); }
        return sum;
    }
    public static double estimatedGeometry(Effect effect) {
        double sum=0;
        for(Emitter e:effect.emitters()) if(Geometry.supports(e.kind())) {double life=Math.max(e.lifetime().min(),e.lifetime().max());double births=e.rate()*(effect.loop()?life:Math.min(life,Math.min(e.duration(),effect.duration())))+e.bursts().stream().mapToInt(Burst::count).sum()*(effect.loop()?Math.max(1,Math.ceil(life/effect.duration())):1);sum+=(births+1)*(e.kind().equals("sphere_mesh")?128:32);}
        return sum;
    }
    public static void resource(String id,String p) {
        if(id==null||!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]*[a-z0-9_-]")||id.contains("..")||id.contains("//")) throw bad(p,"Use namespace:path with no traversal, backslashes or absolute paths.");
    }
    private static JsonObject obj(JsonElement e,String p) { if(e==null||!e.isJsonObject()) throw bad(p,"Expected an object."); return e.getAsJsonObject(); }
    private static JsonArray array(JsonElement e,String p,int min,int max) { if(e==null||!e.isJsonArray()) throw bad(p,"Expected an array."); JsonArray a=e.getAsJsonArray(); if(a.size()<min||a.size()>max) throw bad(p,"Expected "+min+".."+max+" entries."); return a; }
    private static void fields(JsonObject o,String p,String... names) { Set<String> allowed=Set.of(names); for(String key:o.keySet()) if(!allowed.contains(key)) throw bad(p+"."+key,"Unknown or unsupported field; consult schema/effect.schema.json."); }
    private static String string(JsonObject o,String key,String def,String p) { if(!o.has(key)) { if(def==null) throw bad(p+"."+key,"Required field is missing."); return def; } JsonElement e=o.get(key); if(!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isString()) throw bad(p+"."+key,"Expected text."); String s=e.getAsString(); if(s.length()>512) throw bad(p+"."+key,"Text exceeds 512 characters."); return s; }
    private static boolean bool(JsonObject o,String key,boolean def,String p) { if(!o.has(key)) return def; JsonElement e=o.get(key); if(!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isBoolean()) throw bad(p+"."+key,"Expected true or false."); return e.getAsBoolean(); }
    private static double num(JsonElement e,double min,double max,String p) { if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isNumber()) throw bad(p,"Expected a number."); double n=e.getAsDouble(); if(!Double.isFinite(n)||n<min||n>max) throw bad(p,"Value must be between "+min+" and "+max+"."); return n; }
    private static double number(JsonObject o,String key,double def,double min,double max,String p) { return o.has(key)?num(o.get(key),min,max,p+"."+key):def; }
    private static int integer(JsonObject o,String key,int def,int min,int max,String p) { double n=number(o,key,def,min,max,p); if(n!=Math.rint(n)) throw bad(p+"."+key,"Expected an integer."); return (int)n; }
    private static String choice(JsonObject o,String key,String def,String p,String... choices) { String s=string(o,key,def,p); if(!Set.of(choices).contains(s)) throw bad(p+"."+key,"Choose one of: "+String.join(", ",choices)); return s; }
    private static Vec vec(JsonObject o,String key,Vec def,double min,double max,String p) { if(!o.has(key)) return def; JsonArray a=array(o.get(key),p+"."+key,3,3); return new Vec(num(a.get(0),min,max,p+"."+key+"[0]"),num(a.get(1),min,max,p+"."+key+"[1]"),num(a.get(2),min,max,p+"."+key+"[2]")); }
    private static Value value(JsonObject o,String key,double def,double min,double max,String p) {
        if(!o.has(key)) return Value.constant(def); JsonElement element=o.get(key); String vp=p+"."+key;
        if(element.isJsonPrimitive()) return Value.constant(num(element,min,max,vp));
        JsonObject v=obj(element,vp);
        if(v.has("min")||v.has("max")) { fields(v,vp,"min","max"); if(!v.has("min")||!v.has("max")) throw bad(vp,"A range requires min and max."); double a=number(v,"min",def,min,max,vp),b=number(v,"max",def,min,max,vp); if(a>b) throw bad(vp,"min must not exceed max."); return new Value(a,b,List.of()); }
        fields(v,vp,"curve"); JsonArray keys=array(v.get("curve"),vp+".curve",2,64); List<Key> result=new ArrayList<>(); double last=-1;
        for(int i=0;i<keys.size();i++) { String kp=vp+".curve["+i+"]"; JsonObject k=obj(keys.get(i),kp); fields(k,kp,"time","value","easing"); if(!k.has("time")||!k.has("value")) throw bad(kp,"time and value are required."); double t=number(k,"time",0,0,1,kp); if(t<=last) throw bad(kp,"Key times must be strictly increasing."); last=t; result.add(new Key(t,number(k,"value",def,min,max,kp),easing(k,kp))); }
        if(key.equals("lifetime")) throw bad(vp,"Lifetime supports constants and ranges; use curves for size, speed or rotation.");
        return new Value(result.stream().mapToDouble(Key::value).min().orElseThrow(),result.stream().mapToDouble(Key::value).max().orElseThrow(),List.copyOf(result));
    }
    public static Easing easing(JsonObject key,String path){
        if(!key.has("easing"))return Easing.LINEAR;
        String p=path+".easing";var e=obj(key.get("easing"),p);fields(e,p,"mode","controls");String mode=choice(e,"mode","linear",p,"linear","step","smooth","bezier");
        if(!mode.equals("bezier")){if(e.has("controls"))throw bad(p+".controls","Controls require bezier mode.");return new Easing(mode,.25,.25,.75,.75);}
        var c=array(e.get("controls"),p+".controls",4,4);return new Easing(mode,num(c.get(0),0,1,p+".controls[0]"),num(c.get(1),0,1,p+".controls[1]"),num(c.get(2),0,1,p+".controls[2]"),num(c.get(3),0,1,p+".controls[3]"));
    }
    private static Color color(JsonElement e,String p) { JsonArray a=array(e,p,3,4); return new Color(num(a.get(0),0,1,p+"[0]"),num(a.get(1),0,1,p+"[1]"),num(a.get(2),0,1,p+"[2]"),a.size()==4?num(a.get(3),0,1,p+"[3]"):1); }
    private static Gradient gradient(JsonObject o,String p) {
        if(!o.has("color")) return new Gradient(List.of(new ColorKey(0,new Color(0.7,0.3,1,1))));
        JsonElement c=o.get("color"); String cp=p+".color";
        if(c.isJsonArray()) return new Gradient(List.of(new ColorKey(0,color(c,cp))));
        JsonObject g=obj(c,cp); fields(g,cp,"gradient"); JsonArray a=array(g.get("gradient"),cp+".gradient",2,64); List<ColorKey> keys=new ArrayList<>(); double last=-1;
        for(int i=0;i<a.size();i++) { String kp=cp+".gradient["+i+"]"; JsonObject k=obj(a.get(i),kp); fields(k,kp,"time","color","easing"); if(!k.has("time")) throw bad(kp,"time is required."); double t=number(k,"time",0,0,1,kp); if(t<=last) throw bad(kp,"Key times must be strictly increasing."); last=t; keys.add(new ColorKey(t,color(k.get("color"),kp+".color"),easing(k,kp))); }
        return new Gradient(List.copyOf(keys));
    }
}
