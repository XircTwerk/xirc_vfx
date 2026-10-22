package com.xirctwerk.vfx.client;

import com.xirctwerk.vfx.core.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Vector3f;

/** Buffered sprites/ribbons plus the independent procedural geometry and code-visual pass. */
public final class VfxRenderer {
    public static void render(PoseStack pose,Camera camera) {
        com.xirctwerk.vfx.api.VfxPerformance.beginFrame();
        renderParticles(pose,camera,false);
        GeometryRenderer.render(pose,camera);
        BloomRenderer.render(pose,camera);
        com.xirctwerk.vfx.api.render.RenderEffects.renderWorld();
        renderPivot(pose,camera);
    }
    private static void renderPivot(PoseStack pose,Camera camera){
        var studio=VfxClient.INSTANCE;var anchor=studio.editorAnchor();if(anchor==null)return;
        var c=camera.getPosition();var start=new Vector3f((float)(anchor.x()-c.x),(float)(anchor.y()-c.y),(float)(anchor.z()-c.z));
        var buffers=Minecraft.getInstance().renderBuffers().bufferSource();var type=SpriteMaterial.get(studio.texture("xirc_vfx_studio:textures/effect/spark.png"),true,false,false);var buffer=buffers.getBuffer(type);
        ribbon(pose,buffer,new Vector3f(start).add(-.25f,0,0),new Vector3f(start).add(.25f,0,0),.018f,new Effect.Color(1,.25,.25,1),LightTexture.FULL_BRIGHT,new Effect.Uv(0,0,1,1));
        ribbon(pose,buffer,new Vector3f(start),new Vector3f(start).add(0,.5f,0),.018f,new Effect.Color(.25,1,.35,1),LightTexture.FULL_BRIGHT,new Effect.Uv(0,0,1,1));
        ribbon(pose,buffer,new Vector3f(start).add(0,0,-.25f),new Vector3f(start).add(0,0,.25f),.018f,new Effect.Color(.3,.6,1,1),LightTexture.FULL_BRIGHT,new Effect.Uv(0,0,1,1));buffers.endBatch(type);
    }
    static void renderParticles(PoseStack pose,Camera camera,boolean bloom) {
        var studio=VfxClient.INSTANCE; var mc=Minecraft.getInstance(); if(mc.level==null||studio.playbacks().isEmpty()) return;
        MultiBufferSource.BufferSource buffers=mc.renderBuffers().bufferSource();
        var c=camera.getPosition();Vector3f right=new Vector3f(1,0,0).rotate(camera.rotation()),up=new Vector3f(0,1,0).rotate(camera.rotation());
        for(var playback:studio.playbacks()) {
            int quads=0,draws=0,batchQuads=0;RenderType currentType=null;long started=System.nanoTime();
            float partial=studio.renderPartial(playback);
            var anchor=playback.anchor().get();
            for(var p:playback.simulation().particles()) {
                if(Geometry.supports(p.emitter.kind())) continue;
                if(bloom&&p.emitter.bloom()<=0)continue;
                var interpolated=p.interpolated(partial);var world=p.emitter.space().equals("local")?interpolated.add(anchor):interpolated;Vector3f pos=new Vector3f((float)(world.x()-c.x),(float)(world.y()-c.y),(float)(world.z()-c.z));
                var type=SpriteMaterial.get(studio.texture(p.emitter.texture()),bloom||p.emitter.glow(),p.emitter.blend().equals("additive"),bloom);
                if(currentType!=null&&currentType!=type){buffers.endBatch(currentType);if(batchQuads>0)draws++;batchQuads=0;}currentType=type;int before=quads;
                VertexConsumer buffer=buffers.getBuffer(type);var color=playback.color(p.color());if(bloom)color=new Effect.Color(color.r()*p.emitter.bloom()/4,color.g()*p.emitter.bloom()/4,color.b()*p.emitter.bloom()/4,color.a());float size=(float)p.size();
                int light=(bloom||p.emitter.glow())?LightTexture.FULL_BRIGHT:LevelRenderer.getLightColor(mc.level,net.minecraft.core.BlockPos.containing(world.x(),world.y(),world.z()));
                if(p.emitter.kind().equals("beam")) {
                    var d=p.direction().normalize().scale(p.emitter.length()*p.transformScale());Vector3f end=new Vector3f(pos).add((float)d.x(),(float)d.y(),(float)d.z());quads+=ribbon(pose,buffer,pos,end,size,color,light,p.emitter.textureAnimation().uv(p.interpolatedAge(partial)));
                } else if(p.emitter.kind().equals("trail")&&!p.history.isEmpty()) {
                    Vector3f last=pos;int i=0;
                    for(var point:p.history) {
                        var v=p.emitter.space().equals("local")?point.add(anchor):point;Vector3f next=new Vector3f((float)(v.x()-c.x),(float)(v.y()-c.y),(float)(v.z()-c.z));
                        quads+=ribbon(pose,buffer,last,next,size*(1-(float)i/p.emitter.trailPoints()),color,light,p.emitter.textureAnimation().uv(p.interpolatedAge(partial)));last=next;i++;
                    }
                } else {
                    var velocity=p.renderVelocity();double angle=p.angle();if(p.emitter.motion().align()&&velocity.length()>1e-6)angle+=Math.toDegrees(Math.atan2(velocity.x()*up.x+velocity.y()*up.y+velocity.z()*up.z,velocity.x()*right.x+velocity.y()*right.y+velocity.z()*right.z));
                    float radians=(float)Math.toRadians(angle),co=(float)Math.cos(radians),si=(float)Math.sin(radians);
                    Vector3f r=new Vector3f(right).mul(co).add(new Vector3f(up).mul(si)).mul(size*.5f*(float)(p.emitter.aspect()*p.emitter.motion().stretchFactor(velocity)));
                    Vector3f u=new Vector3f(up).mul(co).sub(new Vector3f(right).mul(si)).mul(size*.5f);
                    var uv=p.emitter.textureAnimation().uv(p.interpolatedAge(partial));quads++;quad(pose,buffer,new Vector3f(pos).sub(r).sub(u),new Vector3f(pos).sub(r).add(u),new Vector3f(pos).add(r).add(u),new Vector3f(pos).add(r).sub(u),color,light,uv);
                }
                batchQuads+=quads-before;
            }
            if(currentType!=null){buffers.endBatch(currentType);if(batchQuads>0)draws++;}
            com.xirctwerk.vfx.api.VfxPerformance.record(playback,quads,draws,System.nanoTime()-started);
        }
        // Flush only our texture types, leaving other mods' buffered geometry untouched.
    }
    private static int ribbon(PoseStack pose,VertexConsumer b,Vector3f start,Vector3f end,float size,Effect.Color color,int light,Effect.Uv uv) {
        Vector3f along=new Vector3f(end).sub(start);if(along.lengthSquared()<1e-9) return 0;
        Vector3f toward=new Vector3f(start).add(end).mul(-.5f);Vector3f side=along.cross(toward,new Vector3f());if(side.lengthSquared()<1e-9) side.set(1,0,0);side.normalize().mul(size*.5f);
        quad(pose,b,new Vector3f(start).sub(side),new Vector3f(end).sub(side),new Vector3f(end).add(side),new Vector3f(start).add(side),color,light,uv);return 1;
    }
    private static void quad(PoseStack p,VertexConsumer b,Vector3f a,Vector3f c,Vector3f d,Vector3f e,Effect.Color color,int light) { vertex(p,b,a,0,1,color,light);vertex(p,b,c,0,0,color,light);vertex(p,b,d,1,0,color,light);vertex(p,b,e,1,1,color,light); }
    private static void quad(PoseStack p,VertexConsumer b,Vector3f a,Vector3f c,Vector3f d,Vector3f e,Effect.Color color,int light,Effect.Uv uv) {vertex(p,b,a,(float)uv.u0(),(float)uv.v1(),color,light);vertex(p,b,c,(float)uv.u0(),(float)uv.v0(),color,light);vertex(p,b,d,(float)uv.u1(),(float)uv.v0(),color,light);vertex(p,b,e,(float)uv.u1(),(float)uv.v1(),color,light);}
    private static void vertex(PoseStack p,VertexConsumer b,Vector3f v,float u,float w,Effect.Color c,int light) { b.addVertex(p.last().pose(),v.x,v.y,v.z).setColor((float)c.r(),(float)c.g(),(float)c.b(),(float)c.a()).setUv(u,w).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p.last(),0,1,0); }
}
