package com.xirctwerk.vfx.api.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.*;

/** Scope every library pass so Minecraft's renderer and other mods retain their GL state. */
public final class RenderState implements AutoCloseable {
    private final int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),vao=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
    private final net.minecraft.client.renderer.ShaderInstance shader=RenderSystem.getShader();
    private final int arrayBuffer=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING),depthFunction=GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
    private final int[] viewport=new int[4],textures=new int[2];
    private final boolean blend=GL11.glIsEnabled(GL11.GL_BLEND),depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE),scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),depthMask=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
    private final int sr=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),dr=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),sa=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),da=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA),er=GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB),ea=GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
    private final float[] clear=new float[4];private final java.nio.ByteBuffer mask=org.lwjgl.BufferUtils.createByteBuffer(4);
    public RenderState(){RenderSystem.assertOnRenderThread();GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE,clear);GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK,mask);for(int i=0;i<2;i++){RenderSystem.activeTexture(GL13.GL_TEXTURE0+i);textures[i]=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);}RenderSystem.activeTexture(active);}
    @Override public void close(){com.mojang.blaze3d.vertex.BufferUploader.invalidate();GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);RenderSystem.viewport(viewport[0],viewport[1],viewport[2],viewport[3]);RenderSystem.setShader(()->shader);GlStateManager._glUseProgram(program);GL30.glBindVertexArray(vao);GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER,arrayBuffer);RenderSystem.depthFunc(depthFunction);for(int i=0;i<2;i++){RenderSystem.activeTexture(GL13.GL_TEXTURE0+i);RenderSystem.bindTexture(textures[i]);}RenderSystem.activeTexture(active);RenderSystem.blendFuncSeparate(sr,dr,sa,da);GL20.glBlendEquationSeparate(er,ea);RenderSystem.depthMask(depthMask);RenderSystem.clearColor(clear[0],clear[1],clear[2],clear[3]);RenderSystem.colorMask(mask.get(0)!=0,mask.get(1)!=0,mask.get(2)!=0,mask.get(3)!=0);if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();if(scissor)GL11.glEnable(GL11.GL_SCISSOR_TEST);else GL11.glDisable(GL11.GL_SCISSOR_TEST);}
}
