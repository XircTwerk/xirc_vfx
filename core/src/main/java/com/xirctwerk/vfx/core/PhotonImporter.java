package com.xirctwerk.vfx.core;

import com.google.gson.*;
import java.io.*;
import java.util.*;

/** Converts user-supplied Photon data to validated Studio JSON. Never loads executable materials. */
public final class PhotonImporter {
    public record Result(JsonObject effect,List<String> diagnostics) {
        public boolean exact() {return diagnostics.isEmpty();}
        public JsonObject requireExact() {if(!exact()) throw new IllegalArgumentException("Photon conversion requires approximation:\n"+String.join("\n",diagnostics));return effect.deepCopy();}
    }
    private final List<String> warnings=new ArrayList<>();
    private final boolean substituteTextures;
    private PhotonImporter(boolean substituteTextures) {this.substituteTextures=substituteTextures;}
    public static Result read(byte[] bytes,String name,boolean substituteTextures) throws IOException {
        try {return new PhotonImporter(substituteTextures).convert(BoundedNbt.read(bytes),name);}
        catch(IllegalArgumentException|IllegalStateException|ClassCastException e) {throw new IOException("Photon conversion failed: "+e.getMessage(),e);}
    }
    private Result convert(JsonObject input,String name) {
        boolean legacy=!input.has("_version")&&input.has("emitters");
        if(!legacy&&(!input.has("_version")||!input.get("_version").isJsonPrimitive()||!input.getAsJsonPrimitive("_version").isNumber()||input.get("_version").getAsDouble()!=1)) throw new IllegalArgumentException("$._version: Supported layouts are the legacy emitters list and Photon FX version 1.");
        var fx=obj(input,"fx");var objects=legacy?input.getAsJsonArray("emitters"):obj(fx,"mainFX").getAsJsonArray("fxObjects");
        if(legacy)warn("$.emitters","Legacy layout converted without transform hierarchy; sub-emitter relationships require rebuilding.");
        if(objects==null||objects.isEmpty()||objects.size()>64) throw new IllegalArgumentException("$.fx.mainFX.fxObjects: Use 1–64 effect objects.");
        var sub=obj(fx,"subFXs");if(!sub.isEmpty()) warn("$.fx.subFXs","Sub-effects are not supported; only mainFX is converted.");
        String id=name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]","_");if(id.isEmpty())id="photon_import";if(id.length()>64)id=id.substring(0,64);
        var root=new JsonObject();root.addProperty("schemaVersion",1);root.addProperty("id","xirc_vfx_studio:"+id);root.addProperty("name",name.length()>512?name.substring(0,512):name);root.addProperty("description","Converted from user-supplied Photon data; review the import diagnostics.");root.addProperty("loop",false);root.addProperty("seed",42);
        var layers=new JsonArray();double duration=.05;Boolean loop=null;
        Map<String,String> ids=new HashMap<>();for(int i=0;i<objects.size();i++) {var t=obj(objects.get(i).getAsJsonObject(),"transform");if(t.has("id")) {String key=t.get("id").toString();if(ids.put(key,"layer_"+i)!=null)throw new IllegalArgumentException("Duplicate transform ID.");}}
        for(int i=0;i<objects.size();i++) {
            String path="$.fx.mainFX.fxObjects["+i+"]";var o=objects.get(i).getAsJsonObject();var c=obj(o,"config");var t=obj(o,"transform");String type=text(o,"_type","");
            if(!Set.of("particle","beam","trail","empty").contains(type)) throw new IllegalArgumentException(path+"._type: Unsupported object "+type+"; convert it to particles, beams or trails first.");
            var e=new JsonObject();e.addProperty("id","layer_"+i);e.addProperty("kind",type.equals("empty")?"particle":type);
            double delay=scalar(c.get("startDelay"),0,path+".config.startDelay"),seconds=num(c,"duration",20)/20d;
            e.addProperty("start",delay/20);e.addProperty("duration",Math.max(.05,seconds));duration=Math.max(duration,delay/20+Math.max(.05,seconds));
            boolean repeats=enabled(c,"looping");if(loop==null)loop=repeats;else if(loop!=repeats)warn(path+".config.looping","Mixed emitter loops use the root cycle; per-layer looping differs.");
            e.addProperty("space",text(c,"simulationSpace","Local").equals("World")?"world":"local");
            double[] pos=vec(obj(t,"localPosition"),0);e.add("offset",array(pos));
            if(t.has("_parentId")&&ids.containsKey(t.get("_parentId").toString()))e.addProperty("parent",ids.get(t.get("_parentId").toString()));
            if(type.equals("empty")) {e.addProperty("rate",0);e.addProperty("duration",.05);e.addProperty("lifetime",.05);warn(path,"Empty transform retained as a zero-emission parent layer.");layers.add(e);continue;}
            double[] scale=vec(obj(t,"localScale"),1);if(!uniform(scale))warn(path+".transform.localScale","Nonuniform scaling uses X for sprite width; other dimensions differ.");
            var rotation=obj(t,"localRotation");if(num(rotation,"x",0)!=0||num(rotation,"y",0)!=0||num(rotation,"z",0)!=0)warn(path+".transform.localRotation","Quaternion transform rotation is not inherited; orientation uses Studio defaults.");
            var emission=obj(c,"emission");e.addProperty("rate",scalar(emission.get("emissionRate"),0,path+".config.emission.emissionRate")*20);
            if(emission.has("bursts")) {var bursts=new JsonArray();int j=0;for(var b:emission.getAsJsonArray("bursts")) {var source=b.getAsJsonObject();var dest=new JsonObject();dest.addProperty("time",num(source,"time",0)/20);dest.addProperty("count",Math.max(1,(int)scalar(source.get("count"),1,path+".config.emission.bursts["+j+"].count")));bursts.add(dest);warn(path+".config.emission.bursts["+j+++"]","Converted time/count only; cycle, probability and interval semantics differ.");}e.add("bursts",bursts);}
            e.add("lifetime",value(c.get("startLifetime"),20,1d/20,path+".config.startLifetime",false));
            e.add("speed",value(c.get("startSpeed"),0,20,path+".config.startSpeed",false));
            var sizes=obj(c,"startSize");double size=scalar(sizes.get("x"),.1,path+".config.startSize.x")*scale[0];
            if(!Objects.equals(sizes.get("x"),sizes.get("y")))warn(path+".config.startSize","Anisotropic particle size becomes a square sprite using X.");
            var sizeLife=obj(c,"sizeOverLifetime");if(enabled(sizeLife,"enable")) {e.add("size",value(obj(sizeLife,"size").get("x"),1,size,path+".config.sizeOverLifetime.size.x",true));warn(path+".config.sizeOverLifetime","Size animation uses X only and deterministic sampled curves.");}else e.addProperty("size",Math.max(.001,size));
            var startRotation=obj(c,"startRotation");e.add("rotation",value(startRotation.get("z"),0,1,path+".config.startRotation.z",false));
            var rotateLife=obj(c,"rotationOverLifetime");if(enabled(rotateLife,"enable"))warn(path+".config.rotationOverLifetime","Angular velocity animation is not represented; only start rotation is retained.");
            e.add("color",color(c.get("startColor"),path+".config.startColor"));
            var colorLife=obj(c,"colorOverLifetime");if(enabled(colorLife,"enable"))e.add("color",gradient(colorLife.get("color"),e.getAsJsonArray("color"),path+".config.colorOverLifetime.color"));
            var shape=obj(obj(c,"shape"),"shape");String shapeType=text(shape,"_type","point").toLowerCase(Locale.ROOT);
            if(!Set.of("point","sphere","cone","box","circle","ring").contains(shapeType)) {warn(path+".config.shape.shape","Unsupported spawn shape "+shapeType+" becomes a point.");shapeType="point";}
            e.addProperty("shape",shapeType.equals("circle")?"ring":shapeType);e.addProperty("radius",num(shape,"radius",.5));e.addProperty("spread",shapeType.equals("sphere")?1:.2);e.add("direction",array(shapeType.equals("sphere")?new double[]{0,0,0}:new double[]{0,1,0}));
            if(!obj(c,"shape").isEmpty())warn(path+".config.shape","Spawn rotation, scale, position, arc and shell thickness use Studio shape defaults.");
            e.addProperty("fade",0);var renderer=obj(c,"renderer");e.addProperty("glow",enabled(renderer,"bloomEffect")||enabled(obj(c,"lights"),"enable"));
            if(enabled(renderer,"bloomEffect")){e.addProperty("bloom",1);warn(path+".config.renderer.bloomEffect","Mapped to Studio effect-only bloom; upstream bloom tint/algorithm may differ.");}
            if(!text(renderer,"renderMode","Billboard").equals("Billboard"))warn(path+".config.renderer.renderMode","Non-billboard particle orientation becomes camera-facing.");
            var material=obj(obj(c,"material"),"material");String materialType=text(material,"_type","");
            String texture=text(material,"texture","xirc_vfx_studio:textures/effect/soft.png");
            if(!materialType.equals("TextureMaterial")) {texture="xirc_vfx_studio:textures/effect/soft.png";warn(path+".config.material","Custom shader/material "+materialType+" becomes a bundled soft sprite; shader code is never loaded.");}
            else {EffectCodec.resource(texture,path+".config.material.texture");if(substituteTextures&&!texture.startsWith("xirc_vfx_studio:")) {warn(path+".config.material.texture","External texture "+texture+" replaced by a bundled sprite. Disable substitution to retain the resource-pack reference.");texture="xirc_vfx_studio:textures/effect/soft.png";}}
            e.addProperty("texture",texture);
            var blend=obj(obj(c,"material"),"blendMode");if(text(blend,"dstColorFactor","").equals("ONE")&&text(blend,"srcColorFactor","").equals("SRC_ALPHA"))e.addProperty("blend","additive");
            var uv=obj(c,"uvAnimation");if(enabled(uv,"enable")) {
                var tiles=obj(uv,"tiles");int columns=(int)num(tiles,"a",1),rows=(int)num(tiles,"b",1);
                if(texture.equals(text(material,"texture",""))&&materialType.equals("TextureMaterial")) {
                    if(columns<1||columns>64||rows<1||rows>64)throw new IllegalArgumentException(path+".config.uvAnimation.tiles: Use a sheet with at most 64 columns/rows, or enable texture substitution.");
                    var animation=new JsonObject();animation.addProperty("columns",columns);animation.addProperty("rows",rows);animation.addProperty("frames",columns*rows);
                    double life=e.get("lifetime").isJsonPrimitive()?e.get("lifetime").getAsDouble():num(e.getAsJsonObject("lifetime"),"max",1);
                    animation.addProperty("fps",Math.max(.1,Math.min(120,columns*rows*Math.max(.1,num(uv,"cycle",1))/Math.max(.05,life))));animation.addProperty("loop",num(uv,"cycle",1)>1);e.add("textureAnimation",animation);
                    warn(path+".config.uvAnimation","Whole-sheet row-major frames converted; start-frame functions, custom frame curves and row selection use an approximation.");
                }else warn(path+".config.uvAnimation","Animation omitted because the original atlas was substituted. Retain its texture reference and install the resource pack to convert sheet animation.");
            }
            if(type.equals("beam")) {
                var end=vec(obj(c,"end"),0);double length=Math.sqrt(end[0]*end[0]+end[1]*end[1]+end[2]*end[2]);
                e.addProperty("length",Math.max(.01,length));e.add("direction",length>0?array(end[0]/length,end[1]/length,end[2]/length):array(0,1,0));
                e.addProperty("spread",0);e.addProperty("speed",0);e.addProperty("rate",0);e.addProperty("lifetime",Math.max(.05,seconds));e.add("size",value(c.get("width"),.1,scale[0],path+".config.width",true));
                if(c.has("color"))e.add("color",text(c.getAsJsonObject("color"),"_type","").equals("Color")?color(c.get("color"),path+".config.color"):gradient(c.get("color"),array(1,1,1,1),path+".config.color"));
                var burst=new JsonObject();burst.addProperty("time",0);burst.addProperty("count",1);var bursts=new JsonArray();bursts.add(burst);e.add("bursts",bursts);
                warn(path+".config.emitRate","Beam uses one ribbon for its duration; repeat/UV emission semantics differ.");
            }
            var physics=obj(c,"physics");if(enabled(physics,"enable")) {e.addProperty("collision",enabled(physics,"hasCollision"));e.addProperty("bounce",scalar(physics.get("bounceRate"),.5,path+".config.physics.bounceRate"));e.add("force",array(0,-scalar(physics.get("gravity"),0,path+".config.physics.gravity")*400,0));warn(path+".config.physics","Endpoint collision/gravity converted; friction, bounce probability and collision removal differ.");}
            for(String module:List.of("sizeBySpeed","rotationBySpeed","trails","noise","subEmitters","velocityOverLifetime","inheritVelocity","lifetimeByEmitterSpeed","colorBySpeed","forceOverLifetime"))if(enabled(obj(c,module),"enable"))warn(path+".config."+module,"Active module is unsupported and omitted. Rebuild it with Studio controls/code API.");
            warn(path+".config.material","Blend, culling, discard thresholds and depth settings use Studio's material pipeline.");
            var known=Set.of("simulationSpace","renderer","sizeBySpeed","rotationBySpeed","trails","startColor","colorOverLifetime","duration","emission","startSpeed","startSize","physics","parallelUpdate","noise","subEmitters","parallelRendering","velocityOverLifetime","rotationOverLifetime","lights","inheritVelocity","uvAnimation","startLifetime","lifetimeByEmitterSpeed","shape","looping","colorBySpeed","startRotation","forceOverLifetime","sizeOverLifetime","maxParticles","material","startDelay","emitRate","width","end","color","length");
            for(String key:c.keySet())if(!known.contains(key))warn(path+".config."+key,"Unknown configuration field omitted; author its equivalent in Studio.");
            if(o.has("isSubEmitter")&&enabled(o,"isSubEmitter"))warn(path+".isSubEmitter","Sub-emitter becomes an independent main layer.");
            constrain(e,path);layers.add(e);
        }
        if(duration>300)warn("$.duration","Emission interval constrained to Studio's 300-second maximum.");
        root.addProperty("duration",Math.min(300,duration));root.addProperty("loop",Boolean.TRUE.equals(loop));root.add("emitters",layers);
        EffectCodec.compile(root);return new Result(root,List.copyOf(warnings));
    }
    private JsonElement value(JsonElement source,double fallback,double factor,String path,boolean animate) {
        if(source==null)return new JsonPrimitive(fallback*factor);if(source.isJsonPrimitive())return new JsonPrimitive(source.getAsDouble()*factor);var o=source.getAsJsonObject();String type=text(o,"_type","Constant");
        if(type.equals("Constant"))return new JsonPrimitive(num(o,"number",fallback)*factor);
        if(type.equals("RandomConstant")) {var result=new JsonObject();double a=num(o,"a",fallback)*factor,b=num(o,"b",fallback)*factor;result.addProperty("min",Math.min(a,b));result.addProperty("max",Math.max(a,b));if(a>b)warn(path,"Reversed random bounds sorted into increasing order.");return result;}
        if(type.equals("Curve")||type.equals("RandomCurve")) {warn(path,"Bezier/emitter-time or random curve sampled to a deterministic linear approximation.");if(!animate)return new JsonPrimitive(sample(o,0,fallback)*factor);var keys=new JsonArray();for(int i=0;i<=16;i++){var k=new JsonObject();k.addProperty("time",i/16d);k.addProperty("value",sample(o,i/16d,fallback)*factor);keys.add(k);}var result=new JsonObject();result.add("curve",keys);return result;}
        throw new IllegalArgumentException(path+": Unsupported numeric function "+type+". Expressions cannot be imported.");
    }
    private double scalar(JsonElement source,double fallback,String path) {var v=value(source,fallback,1,path,false);if(v.isJsonObject()) {warn(path,"Random range uses its midpoint for this property.");return (num(v.getAsJsonObject(),"min",fallback)+num(v.getAsJsonObject(),"max",fallback))/2;}return v.getAsDouble();}
    private static double sample(JsonObject o,double time,double fallback) {
        double lower=num(o,"lower",0),upper=num(o,"upper",1);
        if(o.has("curves"))return lower+(upper-lower)*bezier(o.getAsJsonArray("curves"),time,fallback);
        if(o.has("curves0")&&o.has("curves1"))return lower+(upper-lower)*(bezier(o.getAsJsonArray("curves0"),time,fallback)+bezier(o.getAsJsonArray("curves1"),time,fallback))/2;
        throw new IllegalArgumentException("Curve has no supported control points.");
    }
    private static double bezier(JsonArray curves,double time,double fallback) {
        if(curves.isEmpty())return fallback;if(curves.size()>64)throw new IllegalArgumentException("Too many curve segments.");
        JsonArray first=curves.get(0).getAsJsonArray(),last=curves.get(curves.size()-1).getAsJsonArray();if(first.size()!=8||last.size()!=8)throw new IllegalArgumentException("Curve segments need eight coordinates.");
        if(time<=first.get(0).getAsDouble())return first.get(1).getAsDouble();if(time>=last.get(6).getAsDouble())return last.get(7).getAsDouble();
        for(var segment:curves) {var s=segment.getAsJsonArray();if(s.size()!=8)throw new IllegalArgumentException("Curve segment needs eight coordinates.");if(time>s.get(6).getAsDouble())continue;double low=0,high=1;for(int i=0;i<24;i++){double t=(low+high)/2;if(cubic(s,0,t)<time)low=t;else high=t;}return cubic(s,1,(low+high)/2);}return fallback;
    }
    private static double cubic(JsonArray s,int axis,double t) {double u=1-t;return u*u*u*s.get(axis).getAsDouble()+3*u*u*t*s.get(axis+2).getAsDouble()+3*u*t*t*s.get(axis+4).getAsDouble()+t*t*t*s.get(axis+6).getAsDouble();}
    private JsonArray color(JsonElement source,String path) {if(source==null)return array(1,1,1,1);var o=source.getAsJsonObject();if(!text(o,"_type","Color").equals("Color")) {warn(path,"Random/gradient start color uses white; lifetime gradient is handled separately.");return array(1,1,1,1);}int argb=o.get("number").getAsInt();return array((argb>>>16&255)/255d,(argb>>>8&255)/255d,(argb&255)/255d,(argb>>>24&255)/255d);}
    private JsonElement gradient(JsonElement source,JsonArray base,String path) {
        if(source==null)throw new IllegalArgumentException(path+": Missing gradient.");var o=source.getAsJsonObject();if(!text(o,"_type","").equals("Gradient"))throw new IllegalArgumentException(path+": Unsupported color function; use Gradient.");
        var times=new TreeSet<Double>();times.add(0d);times.add(1d);for(String ch:List.of("r","g","b","a")) {var channel=o.getAsJsonArray(ch);if(channel==null||channel.size()%2!=0||channel.size()>128)throw new IllegalArgumentException(path+"."+ch+": Invalid gradient pairs.");for(int i=0;i<channel.size();i+=2) {double t=channel.get(i).getAsDouble();if(t<0||t>1)throw new IllegalArgumentException(path+": Gradient time outside 0–1.");times.add(t);}}
        if(times.size()>64)throw new IllegalArgumentException(path+": Gradient exceeds 64 merged keys.");var keys=new JsonArray();for(double t:times) {var k=new JsonObject();k.addProperty("time",t);var rgba=new JsonArray();int i=0;for(String ch:List.of("r","g","b","a"))rgba.add(channel(o.getAsJsonArray(ch),t)*base.get(i++).getAsDouble());k.add("color",rgba);keys.add(k);}var result=new JsonObject();result.add("gradient",keys);return result;
    }
    private static double channel(JsonArray a,double t) {if(a.isEmpty())return 1;double previous=-1;for(int i=0;i<a.size();i+=2){double at=a.get(i).getAsDouble();if(at<=previous)throw new IllegalArgumentException("Gradient channel times must increase.");previous=at;}if(t<=a.get(0).getAsDouble())return a.get(1).getAsDouble();for(int i=2;i<a.size();i+=2)if(t<=a.get(i).getAsDouble()){double from=a.get(i-2).getAsDouble(),blend=(t-from)/(a.get(i).getAsDouble()-from);return a.get(i-1).getAsDouble()*(1-blend)+a.get(i+1).getAsDouble()*blend;}return a.get(a.size()-1).getAsDouble();}
    private void warn(String path,String message) {warnings.add(path+": "+message);}
    private void constrain(JsonObject e,String path) {
        for(String key:List.of("size","lifetime","speed","rotation"))boundValue(e,key,switch(key){case "size"->.001;case "lifetime"->.05;case "speed"->-50;default->-36000;},switch(key){case "size"->10;case "lifetime"->30;case "speed"->50;default->36000;},path);
        boundValue(e,"rate",0,2000,path);boundValue(e,"radius",0,64,path);boundValue(e,"length",.01,128,path);boundValue(e,"start",0,300,path);boundValue(e,"duration",.05,300,path);boundValue(e,"bounce",0,1,path);
        for(String key:List.of("offset","force"))if(e.has(key)){var a=e.getAsJsonArray(key);double limit=key.equals("force")?100:128;for(int i=0;i<a.size();i++){double old=a.get(i).getAsDouble(),n=Math.max(-limit,Math.min(limit,old));if(n!=old){a.set(i,new JsonPrimitive(n));warn(path+"."+key+"["+i+"]","Value "+old+" constrained to Studio's ±"+limit+" limit.");}}}
    }
    private void boundValue(JsonObject e,String key,double min,double max,String path) {if(!e.has(key))return;JsonElement old=e.get(key),next=bound(old,min,max);if(!old.equals(next)){e.add(key,next);warn(path+"."+key,"Values constrained to Studio's "+min+"–"+max+" limits; appearance differs.");}}
    private static JsonElement bound(JsonElement value,double min,double max) {if(value.isJsonPrimitive())return new JsonPrimitive(Math.max(min,Math.min(max,value.getAsDouble())));var o=value.getAsJsonObject().deepCopy();if(o.has("curve"))for(var k:o.getAsJsonArray("curve"))k.getAsJsonObject().add("value",bound(k.getAsJsonObject().get("value"),min,max));else {o.add("min",bound(o.get("min"),min,max));o.add("max",bound(o.get("max"),min,max));}return o;}
    private static JsonObject obj(JsonObject o,String key) {return o.has(key)?o.getAsJsonObject(key):new JsonObject();}
    private static String text(JsonObject o,String key,String fallback) {return o.has(key)?o.get(key).getAsString():fallback;}
    private static double num(JsonObject o,String key,double fallback) {return o.has(key)?o.get(key).getAsDouble():fallback;}
    private static boolean enabled(JsonObject o,String key) {return o.has(key)&&(o.get(key).isJsonPrimitive())&&(o.getAsJsonPrimitive(key).isBoolean()?o.get(key).getAsBoolean():o.get(key).getAsInt()!=0);}
    private static double[] vec(JsonObject o,double fallback) {return new double[]{num(o,"x",fallback),num(o,"y",fallback),num(o,"z",fallback)};}
    private static boolean uniform(double[] v) {return Math.abs(v[0]-v[1])<1e-6&&Math.abs(v[0]-v[2])<1e-6;}
    private static JsonArray array(double... values) {var a=new JsonArray();for(double n:values)a.add(n);return a;}
}
