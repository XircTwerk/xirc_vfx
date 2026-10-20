package com.xirctwerk.vfx.core;

import com.google.gson.*;

/** Original layered compositions. No reference textures, shaders or effect payloads are bundled. */
final class AdvancedPresets {
    private final JsonObject root=new JsonObject();
    private final JsonArray layers=new JsonArray();
    private AdvancedPresets(String name,double duration) {
        root.addProperty("schemaVersion",1);root.addProperty("id","xirc_vfx_studio:"+name);
        root.addProperty("name",Templates.title(name));root.addProperty("author","XircTwerk");
        root.addProperty("duration",duration);root.addProperty("loop",false);root.addProperty("seed",1847);
        root.add("emitters",layers);
    }
    static JsonObject create(String name) {
        var p=new AdvancedPresets(name,4);
        double[] violet={.65,.12,1,1},cyan={.15,.85,1,1},red={1,.035,.13,1},gold={1,.65,.06,1},white={.85,.95,1,1};
        switch(name) {
            case "singularity_collapse" -> {
                p.describe("An unstable violet core, spiraling accretion, crossing orbital cages, then a delayed collapse and debris shockwave.");
                p.mesh("event_horizon","sphere_mesh",0,1.7,.7,.06,new double[]{.12,.01,.3,1},0,0,1,1).add("meshScale",Templates.curve(.08,1.3));
                p.layer("accretion","particle",0,1.6,130,1.1,.065,-1.8,violet,"spiral",1.5).addProperty("length",1.2);
                for(int i=0;i<3;i++) p.mesh("orbit_"+i,"ring_mesh",0,1.7,1.6,.055,cyan,i==0?0:1,i==1?0:1,i==2?1:0,1).add("rotation",Templates.curve(i*60,i*60+260));
                p.mesh("collapse_flash","sphere_mesh",1.7,.35,1.1,.15,white,0,1,0,2).add("meshScale",Templates.curve(1.4,.02));
                p.burst("ejecta",1.75,230,violet,5.5,1.5,.12);
                p.shock("shockwave",1.8,cyan,2.8,1.1);
            }
            case "crimson_impact" -> {
                p.describe("Three red claw cuts converge into a white-hot impact, followed by angular fragments, a ground ring and lingering smoke.");
                for(int i=0;i<3;i++) {var e=p.mesh("claw_"+i,"slash",i*.13,.55,1.8,.12,red,0,0,1,3);e.addProperty("rotation",-50+i*50);e.add("offset",v((i-1)*.45,.4,0));}
                p.mesh("impact_core","sphere_mesh",.45,.25,.45,.2,white,0,1,0,1);
                p.burst("scarlet_fragments",.5,180,red,6,1.1,.07).addProperty("texture","xirc_vfx_studio:textures/effect/spark.png");
                p.burst("white_sparks",.5,65,white,8,.55,.035);
                p.shock("impact_ring",.5,red,2.2,.8);
                var smoke=p.layer("after_smoke","particle",.6,.6,35,2.4,.4,.5,new double[]{.15,.03,.055,.7},"sphere",.4);smoke.addProperty("glow",false);smoke.add("size",Templates.curve(.2,.85));smoke.addProperty("noise",.5);
            }
            case "astral_gate" -> {
                p.describe("An upright portal of counter-rotating sigils, a luminous perimeter, spiraling motes and a delayed central beam.");
                for(int i=0;i<3;i++) {var e=p.mesh("sigil_"+i,"ring_mesh",0,3.5,1.1+i*.25,.04,i%2==0?cyan:violet,0,0,1,1);e.add("rotation",Templates.curve(i*40,(i%2==0?1:-1)*300));}
                for(int i=0;i<8;i++) {double a=i*Math.PI/4;var e=p.mesh("rune_"+i,"cube_mesh",.1,3.2,.11,.08,cyan,0,0,1,2);e.add("offset",v(Math.cos(a)*1.75,Math.sin(a)*1.75,0));e.add("rotation",Templates.curve(i*45,i*45+180));}
                p.layer("portal_dust","particle",0,3.4,80,1,.07,.4,violet,"sphere",1.3).addProperty("noise",.8);
                p.beam("gate_ray",1.1,1.5,white,.08,4,0,0,1);
                p.burst("opening_stars",.1,100,cyan,2,.8,.06);
            }
            case "void_cleave" -> {
                p.describe("A five-stroke crossing slash sequence with violet edges, bright cores, thrown trails and a delayed ring impact.");
                for(int i=0;i<5;i++) {var e=p.mesh("cut_"+i,"slash",i*.22,.7,1.6+i*.12,.13,violet,0,0,1,2);e.addProperty("rotation",-65+i*37);var core=p.mesh("edge_"+i,"slash",i*.22,.35,1.6+i*.12,.035,white,0,0,1,1);core.addProperty("rotation",-65+i*37);}
                var trails=p.burst("ribbon_shards",.8,35,cyan,4,1.3,.055);trails.addProperty("kind","trail");trails.addProperty("trailPoints",18);trails.addProperty("noise",1.2);
                p.shock("cut_release",1, violet,2.5,.8);
                p.burst("cut_dust",1,110,violet,2,1.5,.06);
            }
            case "storm_lance" -> {
                p.describe("A cyan lance with nested beam widths, offset electrical branches, an accelerating spiral wake and a bright muzzle discharge.");
                p.beam("lance_halo",.15,2.6,cyan,.3,5,0,0,1);
                p.beam("lance_core",.15,2.6,white,.07,5.2,0,0,1);
                for(int i=0;i<6;i++) {var e=p.mesh("arc_"+i,"lightning",.2+i*.06,1.9,2,.045,cyan,(i%2==0?.4:-.4),.18,1,1);e.add("offset",v(0,(i%2==0?.18:-.18),i*.65));}
                var wake=p.layer("helix_wake","trail",0,2.8,70,.8,.04,2,cyan,"spiral",.4);wake.addProperty("length",1.5);wake.add("direction",v(0,0,1));wake.addProperty("trailPoints",12);
                p.burst("muzzle",.15,90,white,3,.65,.055);
                var tip=p.burst("tip_fragments",2.8,120,cyan,4,.9,.07);tip.add("offset",v(0,0,5));
            }
            case "solar_eruption" -> {
                p.describe("A charging golden sphere erupts into nested ground shockwaves, molten rising ribbons, sparks and slow dark smoke.");
                p.mesh("sun_core","sphere_mesh",0,1,.7,.12,gold,0,1,0,2).add("rotation",Templates.curve(0,240));
                p.shock("inner_wave",1,gold,1.7,.8);p.shock("outer_wave",1.2,red,3,1.1);
                p.burst("eruption",1,260,gold,6,1.5,.09).add("force",v(0,-4,0));
                var ribbons=p.layer("molten_ribbons","trail",.9,.8,40,1.6,.065,3,red,"ring",.7);ribbons.addProperty("trailPoints",24);ribbons.add("force",v(0,-2,0));
                var smoke=p.layer("ash_cloud","particle",1.2,1,45,2.4,.25,.7,new double[]{.18,.12,.08,.65},"sphere",.7);smoke.addProperty("glow",false);smoke.add("size",Templates.curve(.2,.9));smoke.addProperty("noise",.8);
            }
            case "celestial_array" -> {
                p.describe("A suspended constellation of eight star nodes, a radial beam lattice, orbiting dust and a final outward starburst.");
                p.mesh("central_star","sphere_mesh",0,3,.25,.06,white,0,1,0,1);
                p.mesh("celestial_circle","ring_mesh",0,3,2,.035,violet,0,0,1,1).add("rotation",Templates.curve(0,180));
                for(int i=0;i<8;i++) {double a=i*Math.PI/4;double x=Math.cos(a),y=Math.sin(a);var e=p.mesh("star_"+i,"sphere_mesh",.08*i,2.5,.12,.04,cyan,0,1,0,2);e.add("offset",v(x*2,y*2,0));p.beam("spoke_"+i,.08*i,2.5,violet,.025,2,x,y,0);}
                p.layer("stardust","particle",0,2.8,90,1.4,.035,.2,white,"sphere",2).addProperty("noise",.4);
                p.burst("nova",2.8,200,cyan,4,1,.08);
            }
            case "arcane_overdrive" -> {
                p.describe("A three-tier violet ritual, rising spiral ribbons, counter-rotating cubes and a pulsing vertical energy pillar.");
                for(int i=0;i<3;i++) {var e=p.mesh("ritual_"+i,"ring_mesh",0,3,1.6-i*.35,.05,i%2==0?violet:cyan,0,1,0,2);e.add("offset",v(0,i*.45,0));e.add("rotation",Templates.curve(i*30,(i%2==0?1:-1)*360));}
                for(int i=0;i<2;i++) p.mesh("rotor_"+i,"cube_mesh",.2,2.8,.65,.04,cyan,0,1,0,2).add("rotation",Templates.curve(i*45,(i%2==0?1:-1)*210));
                var helix=p.layer("ascending_ribbons","trail",0,3,80,1.2,.05,.7,violet,"spiral",.8);helix.addProperty("length",2.5);helix.addProperty("trailPoints",18);
                p.beam("pillar",.7,2,cyan,.18,3,0,1,0);
                for(int i=0;i<3;i++) p.burst("pulse_"+i,.7+i*.6,65,white,2,.6,.035);
            }
            case "prismatic_orrery" -> {
                p.root.addProperty("duration",8);p.describe("A nested clockwork constellation: one animated parent carries crossing rings, orbiting cores, luminous paths and a rotating dust halo. Edit its seconds-based transform in Tools > Transforms.");
                var pivot=p.layer("orrery_pivot","particle",0,8,0,.05,.01,0,white,"point",0);pivot.addProperty("spread",0);pivot.addProperty("folder","Orrery");
                pivot.add("transform",JsonParser.parseString("{\"keys\":[{\"time\":0},{\"time\":2,\"rotation\":[25,90,30],\"scale\":1.2},{\"time\":4,\"rotation\":[-25,180,60],\"position\":[0,0.5,0]},{\"time\":6,\"rotation\":[25,270,90],\"scale\":0.8},{\"time\":8,\"rotation\":[0,360,120]}]}"));
                for(int i=0;i<3;i++){var ring=p.mesh("armillary_"+i,"ring_mesh",0,7.9,1.3+i*.3,.035,i%2==0?cyan:gold,i==0?1:0,i==1?1:0,i==2?1:0,1);ring.addProperty("parent","orrery_pivot");ring.addProperty("folder","Orrery");ring.addProperty("size",.035);ring.addProperty("fade",.05);}
                for(int i=0;i<2;i++){var core=p.mesh("satellite_"+i,"sphere_mesh",0,7.9,.2,.04,i==0?violet:gold,0,1,0,1);core.addProperty("parent","orrery_pivot");core.add("offset",v(i==0?-1.7:1.7,0,0));core.addProperty("bloom",.8);core.addProperty("size",.04);}
                var halo=p.layer("clockwork_dust","particle",0,7,55,1.4,.045,0,cyan,"ring",1.8);halo.addProperty("parent","orrery_pivot");halo.addProperty("spread",0);halo.addProperty("orbitSpeed",180);halo.add("orbitRadius",v(.08,.08,.08));
                var stars=p.burst("ignition_stars",0,90,white,1.4,1.2,.04);stars.addProperty("parent","orrery_pivot");
                p.root.add("markers",JsonParser.parseString("[{\"name\":\"expansion\",\"time\":2},{\"name\":\"crest\",\"time\":4},{\"name\":\"contraction\",\"time\":6}]"));
            }
            case "spline_serpent" -> {
                p.describe("A cyan spectral serpent follows an editable 3D spline, with streaming ribbons, aligned glints and an orbiting violet wake. See docs/SHOWCASE.md for its layer breakdown.");
                var path=JsonParser.parseString("[[-2,0,0],[-1,1,1],[0,0,-1],[1,1,0],[2,0.5,0]]");
                var ribbon=p.layer("serpent_ribbon","trail",0,3,45,2,.06,0,cyan,"point",0);ribbon.add("path",path.deepCopy());ribbon.addProperty("spread",0);ribbon.addProperty("trailPoints",32);ribbon.addProperty("fade",.15);ribbon.addProperty("folder","Serpent");
                var head=p.layer("serpent_glints","particle",0,3,16,1.8,.16,0,white,"point",0);head.add("path",path.deepCopy());head.addProperty("spread",0);head.addProperty("pathScatter",.08);head.addProperty("alignVelocity",true);head.addProperty("stretch",.25);head.addProperty("folder","Serpent");
                var wake=p.layer("orbital_wake","particle",0,3,60,1.5,.035,0,violet,"point",0);wake.add("path",path.deepCopy());wake.add("orbitRadius",v(.2,.25,.2));wake.addProperty("orbitSpeed",540);wake.addProperty("pathScatter",.04);wake.addProperty("spread",0);wake.addProperty("folder","Wake");
                p.mesh("gate_ring","ring_mesh",0,3,1,.025,cyan,0,0,1,1).add("offset",v(-2,0,0));
                p.shock("arrival_pulse",2,cyan,1.2,.8);
                p.mesh("arrival_core","sphere_mesh",2,.35,.25,.02,white,0,1,0,1).add("offset",v(2,.5,0));
                p.root.add("markers",JsonParser.parseString("[{\"name\":\"emission\",\"time\":0},{\"name\":\"full body\",\"time\":2},{\"name\":\"tail\",\"time\":3}]"));
            }
            case "vortex_forge" -> {
                p.describe("A coherent inward vortex feeds a hot core. Curl turbulence breaks symmetry; orbiting sparks and low rings reveal the field volume.");
                var cloud=p.layer("coherent_flow","particle",0,3,120,2,.1,0,new double[]{.25,.08,.8,.8},"ring",2);cloud.addProperty("vortex",8);cloud.addProperty("vortexPull",3);cloud.addProperty("curl",.4);cloud.addProperty("curlFrequency",1.3);cloud.addProperty("drag",.4);cloud.add("force",v(0,.8,0));cloud.addProperty("spread",0);
                var filaments=p.layer("flow_filaments","trail",0,3,24,1.5,.025,0,cyan,"ring",2);filaments.addProperty("vortex",12);filaments.addProperty("vortexPull",4);filaments.addProperty("drag",.2);filaments.addProperty("trailPoints",28);filaments.addProperty("spread",0);
                p.mesh("forge_core","sphere_mesh",0,3,.3,.03,white,0,1,0,1);
                p.mesh("field_ring","ring_mesh",0,3,2,.025,violet,0,1,0,1);
                var sparks=p.layer("orbital_sparks","particle",0,3,22,1,.05,0,gold,"point",0);sparks.add("orbitRadius",v(1.6,.3,1.6));sparks.addProperty("orbitSpeed",300);sparks.addProperty("alignVelocity",true);sparks.addProperty("stretch",.3);sparks.addProperty("spread",0);
                p.shock("forge_release",2.8,gold,1.5,.7);
            }
            case "orbital_lattice" -> {
                p.describe("Three intersecting elliptical ribbon streams form a moving orbital lattice around a pulsing core.");
                for(int i=0;i<3;i++){var orbit=p.layer("lattice_"+i,"trail",0,3,22,2,.035,0,i==1?violet:cyan,"point",0);orbit.add("orbitRadius",i==0?v(1.8,1.1,0):i==1?v(0,1.4,1.8):v(1.8,0,1.4));orbit.addProperty("orbitSpeed",(i%2==0?1:-1)*(150+i*30));orbit.addProperty("spread",0);orbit.addProperty("trailPoints",32);}
                p.mesh("lattice_core","sphere_mesh",0,3,.2,.04,white,0,1,0,1).add("meshScale",Templates.curve(.5,1.5));
                for(int i=0;i<3;i++){var satellite=p.mesh("satellite_"+i,"cube_mesh",0,3,.09,.02,gold,0,1,0,2);satellite.add("orbitRadius",i==0?v(1.8,1.1,0):i==1?v(0,1.4,1.8):v(1.8,0,1.4));satellite.addProperty("orbitSpeed",(i%2==0?1:-1)*(150+i*30));}
            }
            default -> throw new IllegalArgumentException("Unknown advanced preset: "+name);
        }
        for(var item:p.layers){var e=item.getAsJsonObject();if(!e.has("glow")||e.get("glow").getAsBoolean()){e.addProperty("bloom",e.get("kind").getAsString().equals("beam")?.8:1.2);e.addProperty("blend","additive");}}
        if(name.equals("crimson_impact")||name.equals("solar_eruption")) {
            var e=p.layer("animated_nova","particle",name.equals("crimson_impact")?.45:1,.05,0,.8,3.5,0,white,"point",0);e.addProperty("bloom",1.5);e.addProperty("blend","additive");e.addProperty("texture","xirc_vfx_studio:textures/effect/nova_sheet.png");e.addProperty("size",3.5);e.add("textureAnimation",JsonParser.parseString("{\"columns\":4,\"rows\":4,\"frames\":16,\"fps\":20,\"loop\":false}"));event(e,0,1);
        }
        EffectCodec.compile(p.root);return p.root;
    }
    private void describe(String text) {root.addProperty("description",text);}
    private JsonObject layer(String id,String kind,double start,double duration,double rate,double life,double size,double speed,double[] color,String shape,double radius) {
        var e=new JsonObject();e.addProperty("id",id);e.addProperty("kind",kind);e.addProperty("start",start);e.addProperty("duration",duration);e.addProperty("rate",rate);e.addProperty("lifetime",life);e.addProperty("speed",speed);e.addProperty("shape",shape);e.addProperty("radius",radius);e.addProperty("spread",shape.equals("sphere")?1:.08);e.addProperty("fade",.35);e.addProperty("glow",true);e.add("size",Templates.curve(size,Math.max(.001,size*.1)));e.add("color",Templates.gradient(color,new double[]{color[0]*.5,color[1]*.5,color[2]*.7,0}));e.add("direction",v(0,1,0));layers.add(e);return e;
    }
    private JsonObject mesh(String id,String kind,double start,double life,double radius,double width,double[] color,double x,double y,double z,int pixel) {
        var e=layer(id,kind,start,.05,0,life,width,0,color,"point",radius);e.addProperty("pixelSize",pixel);e.addProperty("spread",0);e.add("direction",v(x,y,z));event(e,0,1);return e;
    }
    private JsonObject burst(String id,double start,int count,double[] color,double speed,double life,double size) {var e=layer(id,"particle",start,.05,0,life,size,speed,color,"sphere",.15);e.add("direction",v(0,0,0));e.addProperty("drag",1);event(e,0,count);return e;}
    private void shock(String id,double start,double[] color,double radius,double life) {mesh(id,"ring_mesh",start,life,radius,.12,color,0,1,0,2).add("meshScale",Templates.curve(.05,1.5));var e=burst(id+"_dust",start,80,color,3,life,.055);e.addProperty("shape","ring");e.addProperty("radius",radius);}
    private JsonObject beam(String id,double start,double duration,double[] color,double size,double length,double x,double y,double z) {var e=layer(id,"beam",start,duration,20,.15,size,0,color,"point",0);e.add("direction",v(x,y,z));e.addProperty("spread",0);e.addProperty("length",length);return e;}
    private static void event(JsonObject e,double time,int count) {var b=new JsonObject();b.addProperty("time",time);b.addProperty("count",count);var a=new JsonArray();a.add(b);e.add("bursts",a);}
    private static JsonArray v(double... values) {var a=new JsonArray();for(double n:values)a.add(n);return a;}
}
