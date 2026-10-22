package com.xirctwerk.vfx.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.*;
import org.lwjgl.opengl.*;
import java.nio.charset.StandardCharsets;
import java.io.*;

/** Independent effect-only bloom orchestration. Gaussian fragment shader reused from MIT Shimmer. */
final class BloomRenderer {
    private static TextureTarget source;
    private static final TextureTarget[] horizontal=new TextureTarget[3],vertical=new TextureTarget[3];
    private static int blur,combine,vao;
    private static boolean failed;
    private static long frames;
    private static int pixelGrid=1,screenWidth,screenHeight;
    static long frames() {return frames;}
    static void bindSource() {if(source!=null)source.bindWrite(true);}
    static void render(PoseStack pose,Camera camera) {
        var mc=Minecraft.getInstance();if(mc.level==null){release();return;}
        if(failed||(!GeometryRenderer.hasBloom()&&VfxClient.INSTANCE.playbacks().stream().noneMatch(p->p.simulation().particles().stream().anyMatch(v->v.emitter.bloom()>0))))return;
        var main=mc.getMainRenderTarget();if(main.width>4096||main.height>4096)return;
        var shader=RenderSystem.getShader();int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),oldVao=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        if(draw!=main.frameBufferId)return; // Respect foreign/off-screen targets; do not leak a post pass into the game window.
        int[] viewport=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);
        int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);int[] textures=new int[3];for(int i=0;i<3;i++){RenderSystem.activeTexture(GL13.GL_TEXTURE0+i);textures[i]=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);}RenderSystem.activeTexture(active);
        boolean blend=GL11.glIsEnabled(GL11.GL_BLEND),depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE),scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),mask=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int srcRgb=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),dstRgb=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),srcAlpha=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),dstAlpha=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        int equationRgb=GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB),equationAlpha=GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
        float[] clear=new float[4];GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE,clear);
        var colorMask=org.lwjgl.BufferUtils.createByteBuffer(4);GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK,colorMask);
        try {
            prepare(main.width,main.height);GL11.glDisable(GL11.GL_SCISSOR_TEST);RenderSystem.colorMask(true,true,true,true);
            var grids=GeometryRenderer.bloomPixels();grids.add(1);screenWidth=main.width;screenHeight=main.height;
            for(int grid:grids){pixelGrid=grid;com.mojang.blaze3d.vertex.BufferUploader.invalidate();
            source.setClearColor(0,0,0,0);source.clear(Minecraft.ON_OSX);source.copyDepthFrom(main);source.bindWrite(true);
            if(grid==1)VfxRenderer.renderParticles(pose,camera,true);GeometryRenderer.renderBloom(pose,camera,grid);
            RenderSystem.disableDepthTest();RenderSystem.depthMask(false);RenderSystem.disableCull();RenderSystem.disableBlend();
            GL30.glBindVertexArray(vao);
            for(int i=0;i<3;i++) {
                fullscreen(horizontal[i],source.getColorTextureId(),blur,1,0,1);
                fullscreen(vertical[i],horizontal[i].getColorTextureId(),blur,0,1,1);
                main.bindWrite(true);RenderSystem.enableBlend();RenderSystem.blendFuncSeparate(GL11.GL_ONE,GL11.GL_ONE,GL11.GL_ZERO,GL11.GL_ONE);GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD,GL14.GL_FUNC_ADD);
                fullscreen(null,vertical[i].getColorTextureId(),combine,0,0,4f/3f);RenderSystem.disableBlend();
            }
            }
            frames++;
        }catch(Exception e){failed=true;org.slf4j.LoggerFactory.getLogger("XircBloom").error("Bloom disabled after a render failure; base effects remain available",e);}
        finally {
            com.mojang.blaze3d.vertex.BufferUploader.invalidate();
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);RenderSystem.viewport(viewport[0],viewport[1],viewport[2],viewport[3]);
            RenderSystem.setShader(()->shader);GlStateManager._glUseProgram(program);GL30.glBindVertexArray(oldVao);
            for(int i=0;i<3;i++){RenderSystem.activeTexture(GL13.GL_TEXTURE0+i);RenderSystem.bindTexture(textures[i]);}RenderSystem.activeTexture(active);
            RenderSystem.blendFuncSeparate(srcRgb,dstRgb,srcAlpha,dstAlpha);GL20.glBlendEquationSeparate(equationRgb,equationAlpha);RenderSystem.depthMask(mask);RenderSystem.clearColor(clear[0],clear[1],clear[2],clear[3]);
            RenderSystem.colorMask(colorMask.get(0)!=0,colorMask.get(1)!=0,colorMask.get(2)!=0,colorMask.get(3)!=0);
            if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();if(scissor)GL11.glEnable(GL11.GL_SCISSOR_TEST);else GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
    }
    private static void prepare(int width,int height)throws IOException {
        if(blur==0){String vertex="#version 150\nout vec2 texCoord;void main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);texCoord=p;gl_Position=vec4(p*2.0-1.0,0,1);}";
            try(var in=BloomRenderer.class.getResourceAsStream("/assets/xirc_vfx_studio/shaders/post/shimmer_blur.fsh")){if(in==null)throw new IOException("Missing bundled Shimmer blur shader");blur=program(vertex,new String(in.readAllBytes(),StandardCharsets.UTF_8));}
            combine=program(vertex,"#version 150\nuniform sampler2D DiffuseSampler;uniform float Gain;uniform float PixelGrid;uniform vec2 ScreenSize;in vec2 texCoord;out vec4 fragColor;void main(){fragColor=vec4(texture(DiffuseSampler,PixelGrid>1.0?(floor(gl_FragCoord.xy/PixelGrid)*PixelGrid+PixelGrid*0.5)/ScreenSize:texCoord).rgb*Gain,0);}");vao=GL30.glGenVertexArrays();
        }
        if(source==null)source=new TextureTarget(width,height,true,Minecraft.ON_OSX);else if(source.width!=width||source.height!=height)source.resize(width,height,Minecraft.ON_OSX);
        for(int i=0;i<3;i++){int w=Math.max(1,(width+(1<<(i+1))-1)>>(i+1)),h=Math.max(1,(height+(1<<(i+1))-1)>>(i+1));
            if(horizontal[i]==null){horizontal[i]=new TextureTarget(w,h,false,Minecraft.ON_OSX);vertical[i]=new TextureTarget(w,h,false,Minecraft.ON_OSX);}else if(horizontal[i].width!=w||horizontal[i].height!=h){horizontal[i].resize(w,h,Minecraft.ON_OSX);vertical[i].resize(w,h,Minecraft.ON_OSX);}
            horizontal[i].setFilterMode(GL11.GL_LINEAR);vertical[i].setFilterMode(GL11.GL_LINEAR);
        }source.setFilterMode(GL11.GL_LINEAR);
    }
    private static void fullscreen(TextureTarget target,int texture,int program,float x,float y,float gain) {
        if(target!=null)target.bindWrite(true);GlStateManager._glUseProgram(program);RenderSystem.activeTexture(GL13.GL_TEXTURE0);RenderSystem.bindTexture(texture);GL20.glUniform1i(GL20.glGetUniformLocation(program,"DiffuseSampler"),0);
        GL20.glUniform1f(GL20.glGetUniformLocation(program,"PixelGrid"),pixelGrid);GL20.glUniform2f(GL20.glGetUniformLocation(program,"ScreenSize"),screenWidth,screenHeight);
        GL20.glUniform1f(GL20.glGetUniformLocation(program,"Gain"),gain);GL20.glUniform2f(GL20.glGetUniformLocation(program,"BlurDir"),x,y);GL20.glUniform1i(GL20.glGetUniformLocation(program,"Radius"),8);
        if(target!=null)GL20.glUniform2f(GL20.glGetUniformLocation(program,"OutSize"),target.width,target.height);GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
    }
    static void smokeGrid()throws IOException {try(var state=new com.xirctwerk.vfx.api.render.RenderState();var pipeline=new com.xirctwerk.vfx.api.render.RenderPipeline()){
        prepare(64,64);int input=pipeline.preview(64,64,1,.4);var output=new TextureTarget(64,64,false,Minecraft.ON_OSX);try{
            RenderSystem.disableBlend();RenderSystem.disableDepthTest();RenderSystem.disableCull();GL11.glDisable(GL11.GL_SCISSOR_TEST);RenderSystem.colorMask(true,true,true,true);GL30.glBindVertexArray(vao);pixelGrid=4;screenWidth=screenHeight=64;
            fullscreen(output,input,combine,0,0,1);var data=org.lwjgl.BufferUtils.createByteBuffer(64*64*4);GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,output.frameBufferId);GL11.glReadPixels(0,0,64,64,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,data);int lit=0;
            for(int y=0;y<64;y++)for(int x=0;x<64;x++){int i=(y*64+x)*4,b=((y/4*4)*64+x/4*4)*4;for(int c=0;c<3;c++){if(data.get(i+c)!=data.get(b+c))throw new IllegalStateException("Bloom halo escaped its pixel grid");if(data.get(i+c)!=0)lit++;}}if(lit==0)throw new IllegalStateException("Bloom grid test produced an empty image");
        }finally{output.destroyBuffers();}}
    }
    private static int program(String vertex,String fragment)throws IOException {
        int v=compile(GL20.GL_VERTEX_SHADER,vertex),f=0,p=0;try{f=compile(GL20.GL_FRAGMENT_SHADER,fragment);p=GL20.glCreateProgram();GL20.glAttachShader(p,v);GL20.glAttachShader(p,f);GL20.glLinkProgram(p);if(GL20.glGetProgrami(p,GL20.GL_LINK_STATUS)==0)throw new IOException(GL20.glGetProgramInfoLog(p));return p;}catch(IOException e){if(p!=0)GL20.glDeleteProgram(p);throw e;}finally{GL20.glDeleteShader(v);if(f!=0)GL20.glDeleteShader(f);}
    }
    private static int compile(int type,String text)throws IOException {int id=GL20.glCreateShader(type);GL20.glShaderSource(id,text);GL20.glCompileShader(id);if(GL20.glGetShaderi(id,GL20.GL_COMPILE_STATUS)==0){String error=GL20.glGetShaderInfoLog(id);GL20.glDeleteShader(id);throw new IOException(error);}return id;}
    static void release(){if(source!=null){source.destroyBuffers();source=null;}for(int i=0;i<3;i++)if(horizontal[i]!=null){horizontal[i].destroyBuffers();vertical[i].destroyBuffers();horizontal[i]=vertical[i]=null;}if(blur!=0)GL20.glDeleteProgram(blur);if(combine!=0)GL20.glDeleteProgram(combine);if(vao!=0)GL30.glDeleteVertexArrays(vao);blur=combine=vao=0;failed=false;}
}
