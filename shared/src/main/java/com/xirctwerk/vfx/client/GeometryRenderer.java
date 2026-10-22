package com.xirctwerk.vfx.client;

import com.xirctwerk.vfx.api.*;
import com.xirctwerk.vfx.core.*;
import com.xirctwerk.vfx.core.Effect.*;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.*;
import net.minecraft.client.renderer.GameRenderer;
import org.lwjgl.opengl.GL11;
import java.util.*;

/** Nichirin-derived two-resolution geometry pipeline, isolated from buffered sprite rendering. */
final class GeometryRenderer {
    private record Batch(Object owner,int material) {}
    private static Object currentOwner;
    private record Colored(Geometry.Quad quad,Color color,Object owner,int pixel) {Colored(Geometry.Quad quad,Color color){this(quad,color,currentOwner,1);}}
    private static final Map<Integer,TextureTarget> targets=new HashMap<>();
    private static int count;
    private static final List<Colored> bloomGeometry=new ArrayList<>();
    static boolean hasBloom(){return !bloomGeometry.isEmpty();}
    static java.util.Set<Integer> bloomPixels(){var pixels=new java.util.TreeSet<Integer>();for(var item:bloomGeometry)pixels.add(item.pixel());return pixels;}
    static void render(PoseStack pose,Camera camera) {
        Minecraft mc=Minecraft.getInstance();if(mc.level==null) {release();return;}
        Map<Batch,List<Colored>> groups=new LinkedHashMap<>();count=0;bloomGeometry.clear();
        for(var playback:VfxClient.INSTANCE.playbacks()) for(var p:playback.simulation().particles()) if(Geometry.supports(p.emitter.kind())) {
            currentOwner=playback;long generated=System.nanoTime();
            var point=p.interpolated(VfxClient.INSTANCE.renderPartial(playback));var origin=p.emitter.space().equals("local")?point.add(playback.anchor().get()):point;
            for(var q:Geometry.create(p,origin)) add(groups,p.emitter.pixelSize(),q,playback.color(p.color()),p.emitter.blend().equals("additive"),p.emitter.bloom());
            VfxPerformance.record(playback,0,0,System.nanoTime()-generated);
        }
        float partial=mc.getTimer().getGameTimeDeltaPartialTick(true);
        for(var instance:Vfx.instances()) if(instance.alive()) {
            try {currentOwner=instance;long generated=System.nanoTime();instance.render(partial,(q,c)->add(groups,instance.style.pixelSize(),q,c,false,instance.style.bloom()));VfxPerformance.record(instance,0,0,System.nanoTime()-generated);}
            catch(RuntimeException error) {instance.stop();org.slf4j.LoggerFactory.getLogger("XircVfx").error("Stopped failing code visual",error);}
        }
        for(var trail:RibbonTrail.trails()) try {currentOwner=trail;long generated=System.nanoTime();trail.render((q,c)->add(groups,1,q,c));VfxPerformance.record(trail,0,0,System.nanoTime()-generated);} catch(RuntimeException error) {trail.close();org.slf4j.LoggerFactory.getLogger("XircVfx").error("Closed failing or over-budget trail",error); }
        if(groups.isEmpty()) return;
        var main=mc.getMainRenderTarget();var oldShader=RenderSystem.getShader();
        boolean blend=GL11.glIsEnabled(GL11.GL_BLEND),depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE),mask=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        try {
            for(var entry:groups.entrySet()) {
                int pixel=entry.getKey().material()/2;boolean additive=(entry.getKey().material()&1)!=0;long started=System.nanoTime();
                TextureTarget target=null;
                if(pixel>1) {
                    int width=Math.max(1,(main.width+pixel-1)/pixel),height=Math.max(1,(main.height+pixel-1)/pixel);
                    target=targets.get(pixel);
                    if(target==null) {target=new TextureTarget(width,height,true,Minecraft.ON_OSX);targets.put(pixel,target);}
                    else if(target.width!=width||target.height!=height) target.resize(width,height,Minecraft.ON_OSX);
                    target.setFilterMode(GL11.GL_NEAREST);target.setClearColor(0,0,0,0);target.clear(Minecraft.ON_OSX);target.copyDepthFrom(main);target.bindWrite(true);
                } else main.bindWrite(true);
                draw(pose,camera,entry.getValue(),pixel>1,additive);
                VfxPerformance.record(entry.getKey().owner(),entry.getValue().size(),1,System.nanoTime()-started);
                if(pixel>1) {main.bindWrite(true);RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();target.blitToScreen(main.viewWidth,main.viewHeight,false);}
            }
        } finally {
            main.bindWrite(true);RenderSystem.setShader(()->oldShader);RenderSystem.depthMask(mask);
            if(blend) RenderSystem.enableBlend();else RenderSystem.disableBlend();
            if(depth) RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();
            if(cull) RenderSystem.enableCull();else RenderSystem.disableCull();
        }
    }
    private static void add(Map<Batch,List<Colored>> groups,int pixel,Geometry.Quad q,Color color) {add(groups,pixel,q,color,false);}
    private static void add(Map<Batch,List<Colored>> groups,int pixel,Geometry.Quad q,Color color,boolean additive) {add(groups,pixel,q,color,additive,0);}
    private static void add(Map<Batch,List<Colored>> groups,int pixel,Geometry.Quad q,Color color,boolean additive,double bloom) {
        if(count>=Math.min(65536,VfxPerformance.limits().quads()*2)) throw new IllegalStateException("Frame geometry exceeds 65536 quads");
        for(Vec v:List.of(q.a(),q.b(),q.c(),q.d())) if(!Double.isFinite(v.x())||!Double.isFinite(v.y())||!Double.isFinite(v.z())) throw new IllegalArgumentException("Mesh vertices must be finite");
        for(double c:new double[]{color.r(),color.g(),color.b(),color.a()}) if(!Double.isFinite(c)||c<0||c>1) throw new IllegalArgumentException("Mesh color channels must be 0..1");
        if(bloom>0)bloomGeometry.add(new Colored(q,new Color(color.r()*bloom/4,color.g()*bloom/4,color.b()*bloom/4,color.a()),currentOwner,pixel));
        count++;groups.computeIfAbsent(new Batch(currentOwner,pixel*2+(additive?1:0)),k->new ArrayList<>()).add(new Colored(q,color));
    }
    private static void draw(PoseStack pose,Camera camera,List<Colored> quads,boolean snap,boolean additive) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);RenderSystem.enableBlend();if(additive)RenderSystem.blendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE);else RenderSystem.defaultBlendFunc();RenderSystem.enableDepthTest();RenderSystem.depthMask(false);RenderSystem.disableCull();
        var buffer=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);
        // Approximate back-to-front order improves intersecting translucent meshes.
        var position=camera.getPosition();quads.sort(Comparator.comparingDouble((Colored q)->distance(q.quad,position.x,position.y,position.z)).reversed());
        for(var item:quads) for(Vec v:List.of(item.quad.a(),item.quad.b(),item.quad.c(),item.quad.d())) {
            double x=v.x(),y=v.y(),z=v.z();if(snap) {x=Math.round(x*16)/16.;y=Math.round(y*16)/16.;z=Math.round(z*16)/16.;}
            Color c=item.color;buffer.addVertex(pose.last().pose(),(float)(x-position.x),(float)(y-position.y),(float)(z-position.z)).setColor((float)c.r(),(float)c.g(),(float)c.b(),(float)c.a());
        }
        BufferUploader.invalidate(); // Fullscreen passes bind a foreign VAO between bloom groups.
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }
    static void renderBloom(PoseStack pose,Camera camera,int pixel) {
        var owners=new LinkedHashMap<Object,List<Colored>>();for(var item:bloomGeometry)if(item.pixel()==pixel)owners.computeIfAbsent(item.owner(),k->new ArrayList<>()).add(item);
        for(var entry:owners.entrySet()){long started=System.nanoTime();draw(pose,camera,entry.getValue(),pixel>1,false);VfxPerformance.record(entry.getKey(),entry.getValue().size(),1,System.nanoTime()-started);}
    }
    private static double distance(Geometry.Quad q,double x,double y,double z) {Vec v=q.a().add(q.b()).add(q.c()).add(q.d()).scale(.25);return Math.pow(v.x()-x,2)+Math.pow(v.y()-y,2)+Math.pow(v.z()-z,2);}
    static void release() {targets.values().forEach(TextureTarget::destroyBuffers);targets.clear();bloomGeometry.clear();}
}
