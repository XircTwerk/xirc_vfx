package com.xirctwerk.vfx.api.render;

import com.xirctwerk.vfx.core.Material;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.*;

/** Explicit client-wide world post stack. No gameplay hooks; close/clear on owner teardown. */
public final class RenderEffects {
    private RenderEffects(){}
    private static Material world;private static RenderPipeline pipeline;private static TextureTarget source;private static String error="";private static long started,frames;
    public static long frames(){return frames;}
    public static void world(Material material){RenderSystem.assertOnRenderThread();world=material;started=System.nanoTime();error="";if(material==null)release();}
    public static Material world(){return world;}
    public static String error(){return error;}
    public static double gpuMillis(){return pipeline==null?0:pipeline.gpuMillis();}
    public static void renderWorld(){var mc=Minecraft.getInstance();if(mc.level==null){world=null;release();return;}if(world==null)return;var main=mc.getMainRenderTarget();if(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)!=main.frameBufferId)return;
        try(var state=new RenderState()){if(main.width>4096||main.height>4096||(long)main.width*main.height>8_388_608)return;if(source==null)source=new TextureTarget(main.width,main.height,true,Minecraft.ON_OSX);else if(source.width!=main.width||source.height!=main.height)source.resize(main.width,main.height,Minecraft.ON_OSX);if(pipeline==null)pipeline=new RenderPipeline();
            GL11.glDisable(GL11.GL_SCISSOR_TEST);GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,main.frameBufferId);GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,source.frameBufferId);GL30.glBlitFramebuffer(0,0,main.width,main.height,0,0,main.width,main.height,GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT,GL11.GL_NEAREST);
            double seconds=(System.nanoTime()-started)/1e9;int output=pipeline.render(source.getColorTextureId(),source.getDepthTextureId(),main.width,main.height,world,seconds,(seconds%5)/5);pipeline.blit(output,main);frames++;
        }catch(RuntimeException e){error=e.getMessage();org.slf4j.LoggerFactory.getLogger("XircRender").error("World material disabled",e);world=null;release();}}
    private static void release(){try(var state=new RenderState()){if(source!=null){source.destroyBuffers();source=null;}if(pipeline!=null){pipeline.close();pipeline=null;}}}
}
