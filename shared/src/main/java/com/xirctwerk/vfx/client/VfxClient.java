package com.xirctwerk.vfx.client;

import com.xirctwerk.vfx.core.*;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;

public final class VfxClient {
    public static final String MOD_ID="xirc_vfx";
    public static final VfxClient INSTANCE=new VfxClient();
    public EffectFiles files;
    private final List<Playback> active=new ArrayList<>();
    private Playback editorPreview;private Supplier<Boolean> editorPaused=()->false;
    private final Map<String,ResourceLocation> importedTextures=new HashMap<>();
    private Object level;
    private StudioScreen editor;
    public static final class Playback implements com.xirctwerk.vfx.api.Vfx.Handle {
        private Simulation simulation;
        private final Supplier<Effect.Vec> source;
        private final Supplier<Boolean> valid;
        private final PlaybackControls controls=new PlaybackControls();
        private com.google.gson.JsonObject authored;
        private final Map<String,Double> overrides=new HashMap<>();
        private long seed;
        private long simulationNanos;
        private Playback(Simulation simulation,Supplier<Effect.Vec> anchor,Supplier<Boolean> valid){this.simulation=simulation;this.source=anchor;this.valid=valid;this.seed=simulation.effect().seed();}
        public Simulation simulation(){return simulation;}
        public Supplier<Effect.Vec> anchor(){return ()->source.get().add(controls.offset());}
        public Supplier<Boolean> valid(){return valid;}
        public Effect.Color color(Effect.Color original){return controls.color(original);}
        public long simulationNanos(){return simulationNanos;}
        public boolean paused(){return controls.paused();}
        public void stop(){com.xirctwerk.vfx.api.Vfx.checkThread();controls.stop();}
        public boolean alive(){return controls.alive()&&valid.get()&&!simulation.finished();}
        public Playback move(Effect.Vec position){com.xirctwerk.vfx.api.Vfx.checkThread();controls.move(position.add(source.get().scale(-1)));return this;}
        public Playback rotate(Effect.Vec degrees){com.xirctwerk.vfx.api.Vfx.checkThread();controls.rotate(degrees);return this;}
        public Playback scale(double value){com.xirctwerk.vfx.api.Vfx.checkThread();controls.scale(value);return this;}
        public Playback tint(Effect.Color value){com.xirctwerk.vfx.api.Vfx.checkThread();controls.tint(value);return this;}
        public Playback pause(boolean value){com.xirctwerk.vfx.api.Vfx.checkThread();controls.pause(value);return this;}
        public Playback fadeOut(double seconds){com.xirctwerk.vfx.api.Vfx.checkThread();controls.fadeOut(seconds);return this;}
        public Playback parameter(String name,double value){
            com.xirctwerk.vfx.api.Vfx.checkThread();if(authored==null)throw new UnsupportedOperationException("This effect has no retained authored JSON");
            var next=new HashMap<>(overrides);next.put(name,value);var definition=Parameters.override(authored,next);var effect=EffectCodec.compile(definition);
            INSTANCE.checkBudget(effect,this);
            try{INSTANCE.checkResources(effect);}catch(IOException e){throw new IllegalArgumentException(e.getMessage(),e);}
            // Parameter edits restart only this instance so old sampled particles never mix definitions.
            simulation=new Simulation(effect,seed);overrides.clear();overrides.putAll(next);return this;
        }
    }
    float renderPartial(Playback playback){return playback.paused()||playback==editorPreview&&editorPaused.get()?1:Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);}
    private String editorTitle="Xirc's VFX";
    public String editorTitle(){return editorTitle;}
    public void setEditorTitle(String title){if(title==null||title.isBlank()||title.length()>128)throw new IllegalArgumentException("Editor title must contain 1..128 characters");editorTitle=title;}
    public synchronized void initialize(Path gameDirectory) {if(files==null)useProjectDirectory(gameDirectory.resolve("config").resolve(MOD_ID));}
    /** Host applications may retain their existing project directory; assets and schema stay canonical. */
    public synchronized void useProjectDirectory(Path directory) {
        try {
            files=new EffectFiles(directory);
            var existing=files.list(); for(String name:Templates.ALL_NAMES) if(!existing.contains(name)) files.save(name,Templates.create(name));
            for(String doc:List.of("AGENTS.md","schema/effect.schema.json","docs/AGENT_AUTHORING.md","docs/JSON_FORMAT.md")) {
                try(InputStream in=VfxClient.class.getResourceAsStream("/studio_authoring/"+doc)) { if(in!=null) files.writeAuthoringDocument(doc,in); }
            }
        } catch(IOException e) { throw new IllegalStateException("Cannot initialize Studio projects",e); }
    }
    public void open() { if(editor==null) editor=new StudioScreen();Minecraft.getInstance().setScreen(editor); }
    public void checkResources(Effect effect) throws IOException {
        var manager=Minecraft.getInstance().getResourceManager();
        for(var e:effect.emitters()) {
            if(!importedTextures.containsKey(e.texture())) {
                byte[] stored=files.resource(e.texture());if(stored!=null) registerTexture(e.texture(),stored);
            }
            if(!importedTextures.containsKey(e.texture())&&manager.getResource(ResourceLocation.parse(e.texture())).isEmpty()) throw new IOException("Missing "+e.texture()+". Add its resource pack or choose a bundled texture.");
        }
    }
    public byte[] resource(String id) throws IOException {
        // Package imports are retained as data so they can be re-exported without reading GPU textures.
        if(packageResources.containsKey(id)) return packageResources.get(id).clone();
        byte[] stored=files.resource(id);if(stored!=null) return stored;
        var resource=Minecraft.getInstance().getResourceManager().getResource(ResourceLocation.parse(id));
        if(resource.isEmpty()) return null;
        try(InputStream in=resource.get().open()) { byte[] bytes=in.readNBytes(4_194_305);if(bytes.length>4_194_304) throw new IOException("Texture exceeds 4 MiB: "+id);return bytes; }
    }
    private final Map<String,byte[]> packageResources=new HashMap<>();
    public void importPackage(String name) throws IOException {
        EffectFiles.Package pack=files.readPackage(name);
        // Decode all assets before replacing the current project or texture bindings.
        Map<String,NativeImage> images=new HashMap<>();
        try {
            for(var asset:pack.resources().entrySet()) {
                byte[] bytes=asset.getValue();
                if(bytes.length<24||bytes[0]!=(byte)137||bytes[1]!=80||bytes[2]!=78||bytes[3]!=71) throw new IOException("Not a PNG: "+asset.getKey());
                var header=java.nio.ByteBuffer.wrap(bytes);int w=header.getInt(16),h=header.getInt(20);
                if(w<=0||h<=0||w>2048||h>2048||(long)w*h>4_194_304) throw new IOException("Texture dimensions exceed 2048 x 2048: "+asset.getKey());
                images.put(asset.getKey(),NativeImage.read(new ByteArrayInputStream(bytes)));
            }
            if(importedTextures.size()+images.keySet().stream().filter(k->!importedTextures.containsKey(k)).count()>64) throw new IOException("Imported texture limit reached (64). Restart Minecraft to clear package textures.");
            files.save(name,pack.effect());
            files.storeResources(pack.resources());
            for(var asset:images.entrySet()) {
                ResourceLocation old=importedTextures.get(asset.getKey()); if(old!=null) Minecraft.getInstance().getTextureManager().release(old);
                var texture=Minecraft.getInstance().getTextureManager().register("xirc_import",new DynamicTexture(asset.getValue())); importedTextures.put(asset.getKey(),texture);
            }
            packageResources.putAll(pack.resources()); images.clear();
        } finally { images.values().forEach(NativeImage::close); }
    }
    private void registerTexture(String id,byte[] bytes) throws IOException {
        if(importedTextures.size()>=64) throw new IOException("Imported texture limit reached (64).");
        if(bytes.length<24||bytes[0]!=(byte)137||bytes[1]!=80||bytes[2]!=78||bytes[3]!=71) throw new IOException("Not a PNG: "+id);
        var header=java.nio.ByteBuffer.wrap(bytes);int w=header.getInt(16),h=header.getInt(20);if(w<=0||h<=0||w>2048||h>2048) throw new IOException("Texture dimensions exceed 2048 x 2048: "+id);
        NativeImage image=NativeImage.read(new ByteArrayInputStream(bytes));
        importedTextures.put(id,Minecraft.getInstance().getTextureManager().register("xirc_import",new DynamicTexture(image)));packageResources.put(id,bytes.clone());
    }
    public ResourceLocation texture(String id) {
        if(!importedTextures.containsKey(id)) try {byte[] bytes=files.resource(id);if(bytes!=null) registerTexture(id,bytes);}catch(IOException ignored) { /* Playback/export reports errors explicitly; preview may show Minecraft's missing-texture marker. */ }
        return importedTextures.getOrDefault(id,ResourceLocation.parse(id));
    }
    public List<Playback> playbacks() {if(editorPreview==null) return Collections.unmodifiableList(active);var result=new ArrayList<>(active);result.add(editorPreview);return Collections.unmodifiableList(result);}
    public Simulation editorSimulation() {return editorPreview==null?null:editorPreview.simulation();}
    public void stopEditorPreview() {editorPreview=null;}
    public Effect.Vec editorAnchor(){return editorPreview==null?null:editorPreview.anchor().get();}
    public void previewBesidePlayer(Effect effect,StudioScreen owner,Effect.Vec offset,Supplier<Boolean> paused) throws IOException {
        previewAt(effect,owner,()->vec(Minecraft.getInstance().player.position()).add(offset),paused);
    }
    public void previewWorld(Effect effect,StudioScreen owner,Effect.Vec point,Supplier<Boolean> paused) throws IOException {
        previewAt(effect,owner,()->point,paused);
    }
    private void previewAt(Effect effect,StudioScreen owner,Supplier<Effect.Vec> anchor,Supplier<Boolean> paused) throws IOException {
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null) throw new IOException("Join a world first.");
        checkResources(effect);
        try{checkBudget(effect,editorPreview);}catch(IllegalArgumentException error){throw new IOException(error.getMessage(),error);}
        if(level!=mc.level) {active.forEach(p->p.controls.stop());active.clear();editorPreview=null;BloomRenderer.release();level=mc.level;}
        var player=mc.player;var world=mc.level;editorPaused=paused;
        if(editorPreview==null||!editorPreview.simulation().effect().equals(effect)) editorPreview=new Playback(new Simulation(effect),anchor,()->Minecraft.getInstance().screen==owner&&Minecraft.getInstance().level==world&&!player.isRemoved());
        else editorPreview=new Playback(editorPreview.simulation(),anchor,editorPreview.valid());
    }
    public Playback play(String name,Effect.Vec position) throws IOException { return play(new Project(files.load(name)),position); }
    public Playback play(Project project,Effect.Vec position) throws IOException {return play(project,position,project.effect().seed());}
    public Playback play(Project project,Effect.Vec position,long seed) throws IOException {return play(project,()->position,()->Minecraft.getInstance().level!=null,seed);}
    public Playback play(Project project,Supplier<Effect.Vec> anchor,Supplier<Boolean> valid,long seed) throws IOException {
        com.xirctwerk.vfx.api.Vfx.checkThread();var playback=start(project.effect(),anchor,valid);playback.authored=project.json();playback.seed=seed;playback.simulation=new Simulation(project.effect(),seed);return playback;
    }
    public void attach(String name,int id) throws IOException {
        var world=Minecraft.getInstance().level; if(world==null) throw new IOException("Join a world first."); Entity entity=world.getEntity(id);
        if(entity==null) throw new IOException("No loaded entity with ID "+id+". Use /xvfx me <name> for yourself.");
        start(EffectCodec.compile(files.load(name)),()->vec(entity.position()),()->!entity.isRemoved()&&Minecraft.getInstance().level==world);
    }
    public void block(String name,BlockPos pos) throws IOException {
        var world=Minecraft.getInstance().level;if(world==null||world.getBlockState(pos).isAir()) throw new IOException("Choose a loaded, non-air block.");
        start(EffectCodec.compile(files.load(name)),()->new Effect.Vec(pos.getX()+.5,pos.getY()+1,pos.getZ()+.5),()->Minecraft.getInstance().level==world&&!world.getBlockState(pos).isAir());
    }
    private Playback start(Effect effect,Supplier<Effect.Vec> anchor,Supplier<Boolean> valid) throws IOException {
        if(Minecraft.getInstance().level==null) throw new IOException("Join a world first.");
        if(active.size()+(editorPreview==null?0:1)>=com.xirctwerk.vfx.api.VfxPerformance.limits().instances()) throw new IOException("16 effects are already playing. Use /xvfx stop.");checkResources(effect);
        // A new world is captured at start so switching worlds never carries old instances over.
        if(level!=Minecraft.getInstance().level) { active.forEach(p->p.controls.stop());active.clear();editorPreview=null; level=Minecraft.getInstance().level; }
        try{checkBudget(effect,null);}catch(IllegalArgumentException error){throw new IOException(error.getMessage(),error);}
        Playback playback=new Playback(new Simulation(effect),anchor,valid);active.add(playback);return playback;
    }
    private void checkBudget(Effect effect,Playback replacing){
        var others=playbacks().stream().filter(p->p!=replacing).toList();var limits=com.xirctwerk.vfx.api.VfxPerformance.limits();
        if(others.size()>=limits.instances()||others.stream().mapToDouble(p->EffectCodec.estimatedParticles(p.simulation.effect())).sum()+EffectCodec.estimatedParticles(effect)>limits.particles())throw new IllegalArgumentException("Playback exceeds configured instance/particle budget. Stop effects or lower emission/lifetime.");
        if(others.stream().mapToDouble(p->EffectCodec.estimatedGeometry(p.simulation.effect())).sum()+EffectCodec.estimatedGeometry(effect)>limits.quads())throw new IllegalArgumentException("Playback exceeds configured geometry budget. Stop effects or reduce mesh emissions.");
    }
    public void stop() { com.xirctwerk.vfx.api.IntegrationExamples.stop();active.forEach(Playback::stop);active.clear();editorPreview=null;com.xirctwerk.vfx.api.Vfx.clear(); }
    public void tick() {
        StudioSmokeTest.tick();
        com.xirctwerk.vfx.api.Vfx.tick();
        VfxNetworkClient.tick();com.xirctwerk.vfx.api.IntegrationExamples.tick();
        var mc=Minecraft.getInstance();if(mc.level!=level) { active.forEach(p->p.controls.stop());active.clear();editorPreview=null;GeometryRenderer.release();level=mc.level; }
        if(mc.level==null||mc.isPaused()) return;
        int budget=com.xirctwerk.vfx.api.VfxPerformance.limits().particles()-(editorPreview==null?0:(int)Math.ceil(EffectCodec.estimatedParticles(editorPreview.simulation().effect())));
        for(Iterator<Playback> it=active.iterator();it.hasNext();) {
            Playback p=it.next();if(!p.alive()) {p.controls.stop(); it.remove();continue; }
            p.controls.tick();if(!p.controls.alive()){it.remove();continue;}if(p.controls.paused()){budget-=p.simulation.particles().size();p.simulationNanos=0;continue;}
            p.simulation.setInstancePose(p.controls.pose());long started=System.nanoTime();
            var anchor=p.anchor().get(); final var world=mc.level;
            p.simulation.tick(anchor,point->!world.getBlockState(BlockPos.containing(point.x(),point.y(),point.z())).getCollisionShape(world,BlockPos.containing(point.x(),point.y(),point.z())).isEmpty(),budget);
            p.simulationNanos=System.nanoTime()-started;budget-=p.simulation.particles().size();if(p.simulation.finished()) it.remove();
        }
        tickEditorPreview(com.xirctwerk.vfx.api.VfxPerformance.limits().particles()-active.stream().mapToInt(p->p.simulation().particles().size()).sum());
    }
    /** Bounded replay for editor scrubbing; ordinary playback instances are untouched. */
    void advanceEditorPreview(double target,int maxTicks) {
        if(editorPreview==null||Minecraft.getInstance().level==null)return;
        var world=Minecraft.getInstance().level;
        int budget=com.xirctwerk.vfx.api.VfxPerformance.limits().particles()-active.stream().mapToInt(p->p.simulation().particles().size()).sum();
        for(int i=0;i<maxTicks&&editorPreview.simulation().time()+Simulation.DT/2<target;i++)
            editorPreview.simulation().tick(editorPreview.anchor().get(),point->!world.getBlockState(BlockPos.containing(point.x(),point.y(),point.z())).getCollisionShape(world,BlockPos.containing(point.x(),point.y(),point.z())).isEmpty(),budget);
    }
    private void tickEditorPreview(int budget) {
        if(editorPreview==null) return;if(!editorPreview.valid().get()) {editorPreview=null;return;}if(editorPaused.get()) return;
        var simulation=editorPreview.simulation();if(simulation.finished()) {var muted=simulation.muted();simulation=new Simulation(simulation.effect());simulation.setMuted(muted);editorPreview=new Playback(simulation,editorPreview.anchor(),editorPreview.valid());}
        var world=Minecraft.getInstance().level;simulation.tick(editorPreview.anchor().get(),point->!world.getBlockState(BlockPos.containing(point.x(),point.y(),point.z())).getCollisionShape(world,BlockPos.containing(point.x(),point.y(),point.z())).isEmpty(),budget);
    }
    public static Effect.Vec vec(net.minecraft.world.phys.Vec3 p) { return new Effect.Vec(p.x,p.y,p.z); }
}
