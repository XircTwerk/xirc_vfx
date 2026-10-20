package com.xirctwerk.vfx.api;

import com.xirctwerk.vfx.core.Effect.*;
import com.xirctwerk.vfx.core.Geometry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import java.util.function.Supplier;

/** Client-thread API. Call from your mod's client code; no Nichirin or animation dependency. */
public final class Vfx {
    private Vfx() {}
    public enum Capability {JSON_EFFECTS,CODE_MESHES,ENDPOINT_TRAILS,SPLINE_MOTION,CURL_FLOW,ATTRACTOR,VORTEX,SHARED_WIND,SPRITE_SHEETS,VELOCITY_ALIGNED_SPRITES,MESH_PIXELIZATION,EFFECT_BLOOM,NAMED_PARAMETERS,HIERARCHICAL_TRANSFORMS,LAYER_TRANSFORM_ANIMATION,PLAYBACK_HANDLES,ENTITY_ATTACHMENTS,SERVER_JSON_PLAYBACK,BUILTIN_EXAMPLES,PER_EFFECT_PROFILING}
    private static final Set<Capability> CAPABILITIES=Set.of(Capability.JSON_EFFECTS,Capability.CODE_MESHES,Capability.ENDPOINT_TRAILS,Capability.SPLINE_MOTION,Capability.CURL_FLOW,Capability.ATTRACTOR,Capability.VORTEX,Capability.SHARED_WIND,Capability.SPRITE_SHEETS,Capability.VELOCITY_ALIGNED_SPRITES,Capability.MESH_PIXELIZATION,Capability.EFFECT_BLOOM,Capability.NAMED_PARAMETERS,Capability.HIERARCHICAL_TRANSFORMS,Capability.LAYER_TRANSFORM_ANIMATION,Capability.PLAYBACK_HANDLES,Capability.ENTITY_ATTACHMENTS,Capability.SERVER_JSON_PLAYBACK,Capability.BUILTIN_EXAMPLES,Capability.PER_EFFECT_PROFILING);
    /** Declared client API support, not a guarantee about shader packs or the current frame's budget. */
    public static Set<Capability> capabilities(){return CAPABILITIES;}
    public record Style(Color color,int pixelSize,double bloom) {
        public Style(Color color,int pixelSize){this(color,pixelSize,0);}
        public Style {Objects.requireNonNull(color);if(pixelSize<1||pixelSize>8) throw new IllegalArgumentException("pixelSize must be 1..8");if(!Double.isFinite(bloom)||bloom<0||bloom>4)throw new IllegalArgumentException("bloom must be 0..4");}
    }
    public record Frame(double ageSeconds,double progress,long seed,Vec origin) {}
    @FunctionalInterface public interface Visual {void render(Frame frame,Canvas canvas);}
    @FunctionalInterface public interface Canvas {void quad(Geometry.Quad quad,Color color);}
    public interface Handle extends EffectHandle {}
    public static final class Instance implements Handle {
        public final Supplier<Vec> anchor;public final Visual visual;public final Style style;public final double duration;public final long seed;
        private boolean stopped;private int ticks;private final com.xirctwerk.vfx.core.PlaybackControls controls=new com.xirctwerk.vfx.core.PlaybackControls();
        private Instance(Supplier<Vec> anchor,double duration,long seed,Style style,Visual visual) {this.anchor=anchor;this.duration=duration;this.seed=seed;this.style=style;this.visual=visual;}
        public void stop() {thread();stopped=true;controls.stop();}public boolean alive() {return !stopped&&controls.alive()&&ticks*.05<duration;}
        public Instance move(Vec p){thread();controls.move(p.add(anchor.get().scale(-1)));return this;}
        public Instance rotate(Vec p){thread();controls.rotate(p);return this;}
        public Instance scale(double n){thread();controls.scale(n);return this;}
        public Instance tint(Color c){thread();controls.tint(c);return this;}
        public Instance pause(boolean p){thread();controls.pause(p);return this;}
        public Instance fadeOut(double n){thread();controls.fadeOut(n);return this;}
        public void render(float partial,Canvas canvas){var f=frame(partial);visual.render(f,(q,c)->{var pose=controls.pose();Vec o=f.origin();canvas.quad(new Geometry.Quad(pose.vector(q.a().add(o.scale(-1))).add(o),pose.vector(q.b().add(o.scale(-1))).add(o),pose.vector(q.c().add(o.scale(-1))).add(o),pose.vector(q.d().add(o.scale(-1))).add(o)),controls.color(c));});}
        public Frame frame(float partial) {double age=Math.min(duration,(ticks+(controls.paused()?0:partial))*.05);return new Frame(age,age/duration,seed,Objects.requireNonNull(anchor.get()).add(controls.offset()));}
    }
    private static final List<Instance> active=new ArrayList<>();private static Object world;
    private static final Map<ResourceLocation,Visual> registry=new LinkedHashMap<>();
    /** Register trusted Java visuals once during client setup; duplicate IDs are rejected. */
    public static void register(ResourceLocation id,Visual visual) {thread();Objects.requireNonNull(id);Objects.requireNonNull(visual);if(registry.size()>=1024) throw new IllegalStateException("Visual registry is full");if(registry.putIfAbsent(id,visual)!=null) throw new IllegalArgumentException("Duplicate visual ID: "+id);}
    public static Handle play(ResourceLocation id,Supplier<Vec> origin,double duration,long seed,Style style) {thread();Visual visual=registry.get(id);if(visual==null) throw new IllegalArgumentException("Unknown visual ID: "+id);return play(origin,duration,seed,style,visual);}
    public static Handle play(Supplier<Vec> origin,double duration,long seed,Style style,Visual visual) {
        thread();syncWorld();if(Minecraft.getInstance().level==null) throw new IllegalStateException("Join a world first");
        if(!Double.isFinite(duration)||duration<.05||duration>300) throw new IllegalArgumentException("duration must be .05..300 seconds");
        if(active.size()>=256) throw new IllegalStateException("256 code visuals already active");
        Instance instance=new Instance(Objects.requireNonNull(origin),duration,seed,Objects.requireNonNull(style),Objects.requireNonNull(visual));active.add(instance);return instance;
    }
    public static Handle slash(Supplier<Vec> origin,Vec direction,double radius,double width,double duration,Style style) {
        return play(origin,duration,0,style,(frame,canvas)->Geometry.create("slash",frame.origin(),direction,radius,width,0,frame.progress()).forEach(q->canvas.quad(q,fade(style.color(),frame.progress()))));
    }
    /** Immutable arbitrary quad mesh; vertices are local to the supplied moving origin. */
    public static Handle mesh(Supplier<Vec> origin,List<Geometry.Quad> quads,double duration,Style style) {
        if(quads.size()>4096||quads.isEmpty()) throw new IllegalArgumentException("Mesh must have 1..4096 quads");List<Geometry.Quad> mesh=List.copyOf(quads);
        return play(origin,duration,0,style,(f,c)->mesh.forEach(q->c.quad(new Geometry.Quad(q.a().add(f.origin()),q.b().add(f.origin()),q.c().add(f.origin()),q.d().add(f.origin())),fade(style.color(),f.progress()))));
    }
    public static Handle play(com.xirctwerk.vfx.core.Project project,Vec position) throws java.io.IOException {return com.xirctwerk.vfx.client.VfxClient.INSTANCE.play(project,position);}
    public static Handle playJson(ResourceLocation id,Vec position,long seed,Map<String,Double> parameters) throws java.io.IOException {
        return com.xirctwerk.vfx.client.VfxClient.INSTANCE.play(new com.xirctwerk.vfx.core.Project(com.xirctwerk.vfx.core.Parameters.override(Effects.get(id).json(),parameters)),position,seed);
    }
    public static Color fade(Color c,double progress) {return new Color(c.r(),c.g(),c.b(),c.a()*Math.min(1,(1-progress)*3));}
    public static List<Instance> instances() {thread();syncWorld();return List.copyOf(active);}
    public static void tick() {syncWorld();if(Minecraft.getInstance().isPaused()) return;active.forEach(i->{i.controls.tick();if(!i.controls.paused())i.ticks++;});active.removeIf(i->!i.alive());}
    public static void clear() {thread();active.forEach(i->i.stopped=true);active.clear();RibbonTrail.clearAll();}
    private static void syncWorld() {Object next=Minecraft.getInstance().level;if(world!=next) {clear();world=next;}}
    public static void checkThread(){thread();}
    static void thread() {if(!Minecraft.getInstance().isSameThread()) throw new IllegalStateException("VFX API requires the Minecraft client thread");}
}
