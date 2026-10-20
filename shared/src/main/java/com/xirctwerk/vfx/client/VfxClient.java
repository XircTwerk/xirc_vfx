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
    public record Playback(Simulation simulation,Supplier<Effect.Vec> anchor,Supplier<Boolean> valid) {}
    float renderPartial(Playback playback){return playback==editorPreview&&editorPaused.get()?1:Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);}
    private String editorTitle="Xirc's VFX";
    public String editorTitle(){return editorTitle;}
    public void setEditorTitle(String title){if(title==null||title.isBlank()||title.length()>128)throw new IllegalArgumentException("Editor title must contain 1..128 characters");editorTitle=title;}
    public void initialize(Path gameDirectory) {useProjectDirectory(gameDirectory.resolve("config").resolve(MOD_ID));}
    /** Host applications may retain their existing project directory; assets and schema stay canonical. */
    public void useProjectDirectory(Path directory) {
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
        if(active.size()>=16||active.stream().mapToDouble(p->EffectCodec.estimatedParticles(p.simulation.effect())).sum()+EffectCodec.estimatedParticles(effect)>8192||active.stream().mapToDouble(p->EffectCodec.estimatedGeometry(p.simulation.effect())).sum()+EffectCodec.estimatedGeometry(effect)>32768) throw new IOException("World preview exceeds the playback budget. Stop other effects first.");
        if(level!=mc.level) {active.clear();editorPreview=null;BloomRenderer.release();level=mc.level;}
        var player=mc.player;var world=mc.level;editorPaused=paused;
        if(editorPreview==null||!editorPreview.simulation().effect().equals(effect)) editorPreview=new Playback(new Simulation(effect),anchor,()->Minecraft.getInstance().screen==owner&&Minecraft.getInstance().level==world&&!player.isRemoved());
        else editorPreview=new Playback(editorPreview.simulation(),anchor,editorPreview.valid());
    }
    public void play(String name,Effect.Vec position) throws IOException { start(EffectCodec.compile(files.load(name)),()->position,()->Minecraft.getInstance().level!=null); }
    public void play(Project project,Effect.Vec position) throws IOException { start(project.effect(),()->position,()->Minecraft.getInstance().level!=null); }
    public void attach(String name,int id) throws IOException {
        var world=Minecraft.getInstance().level; if(world==null) throw new IOException("Join a world first."); Entity entity=world.getEntity(id);
        if(entity==null) throw new IOException("No loaded entity with ID "+id+". Use /xvfx me <name> for yourself.");
        start(EffectCodec.compile(files.load(name)),()->vec(entity.position()),()->!entity.isRemoved()&&Minecraft.getInstance().level==world);
    }
    public void block(String name,BlockPos pos) throws IOException {
        var world=Minecraft.getInstance().level;if(world==null||world.getBlockState(pos).isAir()) throw new IOException("Choose a loaded, non-air block.");
        start(EffectCodec.compile(files.load(name)),()->new Effect.Vec(pos.getX()+.5,pos.getY()+1,pos.getZ()+.5),()->Minecraft.getInstance().level==world&&!world.getBlockState(pos).isAir());
    }
    private void start(Effect effect,Supplier<Effect.Vec> anchor,Supplier<Boolean> valid) throws IOException {
        if(Minecraft.getInstance().level==null) throw new IOException("Join a world first.");
        if(active.size()+(editorPreview==null?0:1)>=16) throw new IOException("16 effects are already playing. Use /xvfx stop.");checkResources(effect);
        // A new world is captured at start so switching worlds never carries old instances over.
        if(level!=Minecraft.getInstance().level) { active.clear();editorPreview=null; level=Minecraft.getInstance().level; }
        double reserved=playbacks().stream().mapToDouble(p->EffectCodec.estimatedParticles(p.simulation.effect())).sum();
        if(reserved+EffectCodec.estimatedParticles(effect)>Simulation.MAX_PARTICLES) throw new IOException("Combined effects exceed the 8192 particle budget. Stop effects or lower Amount/lifetime before playing this effect.");
        if(playbacks().stream().mapToDouble(p->EffectCodec.estimatedGeometry(p.simulation.effect())).sum()+EffectCodec.estimatedGeometry(effect)>32768) throw new IOException("Combined mesh geometry exceeds the 32768 quad playback budget. Stop effects or reduce rate/lifetime.");
        active.add(new Playback(new Simulation(effect),anchor,valid));
    }
    public void stop() { active.clear();editorPreview=null;com.xirctwerk.vfx.api.Vfx.clear(); }
    public void tick() {
        StudioSmokeTest.tick();
        com.xirctwerk.vfx.api.Vfx.tick();
        var mc=Minecraft.getInstance();if(mc.level!=level) { active.clear();editorPreview=null;GeometryRenderer.release();level=mc.level; }
        if(mc.level==null||mc.isPaused()) return;
        int budget=Simulation.MAX_PARTICLES-(editorPreview==null?0:(int)Math.ceil(EffectCodec.estimatedParticles(editorPreview.simulation().effect())));
        for(Iterator<Playback> it=active.iterator();it.hasNext();) {
            Playback p=it.next();if(!p.valid.get()) { it.remove();continue; }
            var anchor=p.anchor.get(); final var world=mc.level;
            p.simulation.tick(anchor,point->!world.getBlockState(BlockPos.containing(point.x(),point.y(),point.z())).getCollisionShape(world,BlockPos.containing(point.x(),point.y(),point.z())).isEmpty(),budget);
            budget-=p.simulation.particles().size();if(p.simulation.finished()) it.remove();
        }
        tickEditorPreview(Simulation.MAX_PARTICLES-active.stream().mapToInt(p->p.simulation().particles().size()).sum());
    }
    /** Bounded replay for editor scrubbing; ordinary playback instances are untouched. */
    void advanceEditorPreview(double target,int maxTicks) {
        if(editorPreview==null||Minecraft.getInstance().level==null)return;
        var world=Minecraft.getInstance().level;
        int budget=Simulation.MAX_PARTICLES-active.stream().mapToInt(p->p.simulation().particles().size()).sum();
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
