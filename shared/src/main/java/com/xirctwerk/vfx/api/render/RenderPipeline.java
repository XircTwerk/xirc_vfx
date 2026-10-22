package com.xirctwerk.vfx.api.render;

import com.xirctwerk.vfx.core.Material;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.*;
import java.util.*;

/** Reusable, bounded ping-pong image pipeline. Owns its targets; callers retain source ownership. */
public final class RenderPipeline implements AutoCloseable {
    private TextureTarget[] buffers;private TextureTarget previewTarget;private ShaderProgram shader,preview;private int vao,query;private boolean pending;private double gpuMillis;private final List<Integer> intermediate=new ArrayList<>();
    private static final String VERTEX="#version 150\nout vec2 uv;void main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);uv=p;gl_Position=vec4(p*2.0-1.0,0,1);}";
    private static final String FRAGMENT="""
        #version 150
        uniform sampler2D Source;uniform sampler2D Depth;uniform mat4 InverseProjection;uniform float HasDepth;
        uniform float Operation;uniform float Amount;uniform float Speed;uniform float Scale;uniform float Time;uniform vec2 Size;uniform vec4 Tint;
        in vec2 uv;out vec4 fragColor;
        vec3 viewPosition(vec2 p){float depth=texture(Depth,p).r;vec4 position=InverseProjection*vec4(p*2.-1.,depth*2.-1.,1);return position.xyz/position.w;}
        float noise(vec2 p){return fract(sin(dot(floor(p),vec2(12.9898,78.233)))*43758.5453);}
        void main(){vec2 p=uv;int op=int(Operation);float a=Amount;
            if(op==3)p+=vec2(sin(p.y*Scale*6.283+Time*Speed),cos(p.x*Scale*6.283+Time*Speed))*.025*a;
            if(op==4)p=fract(p+vec2(Time*Speed*.1,Time*Speed*.03)*a);
            if(op==7){float grid=max(1.,floor(1.+a*(Scale-1.)));p=(floor(gl_FragCoord.xy/grid)*grid+grid*.5)/Size;}
            vec4 c=texture(Source,p);
            if(op==0)c*=smoothstep(mix(-.04,1.04,a)-.03,mix(-.04,1.04,a)+.03,noise(p*Scale*8.));
            if(op==1||op==2){vec2 d=vec2(1.)/Size*max(1.,Scale*.25);float edge=0.;float z=texture(Depth,p).r;for(int i=0;i<4;i++){vec2 o=vec2(i==0?d.x:i==1?-d.x:0.,i==2?d.y:i==3?-d.y:0.);vec4 n=texture(Source,p+o);edge=max(edge,max(abs(c.a-n.a),abs(z-texture(Depth,p+o).r)*40.));}float e=clamp(edge*a,0.,1.);if(op==2&&HasDepth>.5){vec3 position=viewPosition(p);vec3 normal=normalize(cross(dFdx(position),dFdy(position)));float facing=clamp(abs(dot(normal,normalize(-position))),0.,1.);e=texture(Depth,p).r<.999999?pow(1.-facing,mix(1.,8.,Scale/64.))*a:0.;}else if(op==2)e=clamp(edge*3.*a,0.,1.);c.rgb=mix(c.rgb,Tint.rgb,e*Tint.a);c.a=max(c.a,e*Tint.a);}
            if(op==5)c.rgb=mix(c.rgb,c.rgb*mix(vec3(1.),Tint.rgb,clamp(p.y,0.,1.)),a);
            if(op==6){vec2 d=vec2(a*Scale/Size.x,0.);c.r=texture(Source,p+d).r;c.b=texture(Source,p-d).b;}
            fragColor=c;
        }
        """;
    private static final String PREVIEW="""
        #version 150
        uniform float Shape;uniform float Time;uniform vec2 Size;uniform mat4 Projection;in vec2 uv;out vec4 fragColor;
        float field(vec3 p){if(Shape<.5)return abs(p.z)-.02;if(Shape<1.5)return length(p)-.8;vec3 q=abs(p)-vec3(.6);return length(max(q,0.))+min(max(q.x,max(q.y,q.z)),0.);}
        vec3 rotate(vec3 p){float c=cos(Time*.3),s=sin(Time*.3);return vec3(c*p.x+s*p.z,p.y,-s*p.x+c*p.z);}
        void main(){vec2 p=(uv*2.-1.)*vec2(Size.x/Size.y,1.);vec3 ro=vec3(0,0,3),rd=normalize(vec3(p,-2.));float t=0.;for(int i=0;i<72;i++){vec3 q=rotate(ro+rd*t);float d=field(q);if(d<.002||t>6.)break;t+=d;}if(t>6.)discard;vec3 hit=rotate(ro+rd*t);if(Shape<.5&&max(abs(hit.x),abs(hit.y))>.85)discard;vec4 clip=Projection*vec4(rd*t,1.);gl_FragDepth=clip.z/clip.w*.5+.5;vec2 e=vec2(.002,0);vec3 n=normalize(vec3(field(hit+e.xyy)-field(hit-e.xyy),field(hit+e.yxy)-field(hit-e.yxy),field(hit+e.yyx)-field(hit-e.yyx)));float light=.25+.75*max(0.,dot(n,normalize(vec3(.5,.8,1))));vec3 color=mix(vec3(.15,.4,1),vec3(.9,.3,.8),uv.y);float lines=.85+.15*sin(hit.y*35.);fragColor=vec4(color*light*lines,1);}
        """;
    public double gpuMillis(){return gpuMillis;}
    /** IDs are valid until the next render; ping-pong buffers expose the latest two passes. */
    public List<Integer> intermediateTextures(){return List.copyOf(intermediate);}
    private void prepare(int w,int h){RenderSystem.assertOnRenderThread();if(w<1||h<1||w>4096||h>4096||(long)w*h>8_388_608)throw new IllegalArgumentException("Render targets exceed 4096 per axis / 8 megapixels.");if(vao==0)vao=GL30.glGenVertexArrays();if(shader==null){shader=new ShaderProgram(VERTEX,FRAGMENT,Map.ofEntries(Map.entry("Source",ShaderProgram.Type.SAMPLER),Map.entry("Depth",ShaderProgram.Type.SAMPLER),Map.entry("Operation",ShaderProgram.Type.FLOAT),Map.entry("Amount",ShaderProgram.Type.FLOAT),Map.entry("Speed",ShaderProgram.Type.FLOAT),Map.entry("Scale",ShaderProgram.Type.FLOAT),Map.entry("Time",ShaderProgram.Type.FLOAT),Map.entry("Size",ShaderProgram.Type.VEC2),Map.entry("Tint",ShaderProgram.Type.VEC4),Map.entry("InverseProjection",ShaderProgram.Type.MAT4),Map.entry("HasDepth",ShaderProgram.Type.FLOAT)));if(GL.getCapabilities().OpenGL33||GL.getCapabilities().GL_ARB_timer_query)query=GL15.glGenQueries();}if(buffers==null)buffers=new TextureTarget[]{new TextureTarget(w,h,false,Minecraft.ON_OSX),new TextureTarget(w,h,false,Minecraft.ON_OSX)};else for(var b:buffers)if(b.width!=w||b.height!=h)b.resize(w,h,Minecraft.ON_OSX);for(var b:buffers)b.setFilterMode(GL11.GL_NEAREST);}
    private static void settings(){RenderSystem.disableDepthTest();RenderSystem.depthMask(false);RenderSystem.disableBlend();RenderSystem.disableCull();GL11.glDisable(GL11.GL_SCISSOR_TEST);RenderSystem.colorMask(true,true,true,true);}
    public int preview(int width,int height,int shape,double seconds){if(shape<0||shape>2||!Double.isFinite(seconds))throw new IllegalArgumentException("Invalid preview arguments");try(var state=new RenderState()){prepare(width,height);if(preview==null)preview=new ShaderProgram(VERTEX,PREVIEW,Map.of("Shape",ShaderProgram.Type.FLOAT,"Time",ShaderProgram.Type.FLOAT,"Size",ShaderProgram.Type.VEC2,"Projection",ShaderProgram.Type.MAT4));settings();if(previewTarget==null)previewTarget=new TextureTarget(width,height,true,Minecraft.ON_OSX);else if(previewTarget.width!=width||previewTarget.height!=height)previewTarget.resize(width,height,Minecraft.ON_OSX);previewTarget.setFilterMode(GL11.GL_NEAREST);previewTarget.setClearColor(0,0,0,0);RenderSystem.depthMask(true);previewTarget.clear(Minecraft.ON_OSX);previewTarget.bindWrite(true);RenderSystem.enableDepthTest();RenderSystem.depthFunc(GL11.GL_ALWAYS);GL30.glBindVertexArray(vao);preview.bind();preview.scalar("Shape",shape);preview.scalar("Time",(float)(seconds%10000));preview.vector2("Size",width,height);preview.matrix4("Projection",previewProjection(width,height).get(new float[16]));GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);return previewTarget.getColorTextureId();}}
    private static org.joml.Matrix4f previewProjection(int width,int height){return new org.joml.Matrix4f().perspective((float)(2*Math.atan(.5)),width/(float)height,.05f,100);}
    public int render(int source,int depth,int width,int height,Material material,double seconds,double normalizedAge){return render(source,depth,width,height,material,seconds,normalizedAge,RenderSystem.getProjectionMatrix());}
    public int render(int source,int depth,int width,int height,Material material,double seconds,double normalizedAge,org.joml.Matrix4fc projection){if(source<=0||material==null||!Double.isFinite(seconds)||!Double.isFinite(normalizedAge))throw new IllegalArgumentException("Invalid render arguments");try(var state=new RenderState()){prepare(width,height);boolean ownPreview=previewTarget!=null&&source==previewTarget.getColorTextureId();if(ownPreview&&depth==0)depth=previewTarget.getDepthTextureId();var inverse=new org.joml.Matrix4f(ownPreview?previewProjection(width,height):projection).invert();float[] inverseValues=inverse.get(new float[16]);settings();GL30.glBindVertexArray(vao);intermediate.clear();if(pending&&GL15.glGetQueryObjecti(query,GL15.GL_QUERY_RESULT_AVAILABLE)!=0){gpuMillis=GL33.glGetQueryObjectui64(query,GL15.GL_QUERY_RESULT)/1_000_000.;pending=false;}boolean timing=query!=0&&!pending&&GL15.glGetQueryi(GL33.GL_TIME_ELAPSED,GL15.GL_CURRENT_QUERY)==0;if(timing)GL15.glBeginQuery(GL33.GL_TIME_ELAPSED,query);
        try{int texture=source;int i=0;for(var node:material.nodes()){if(!node.enabled())continue;var target=buffers[(i++)%2];if(target.getColorTextureId()==texture)target=buffers[i%2];target.bindWrite(true);shader.bind();RenderSystem.activeTexture(GL13.GL_TEXTURE0);RenderSystem.bindTexture(texture);RenderSystem.activeTexture(GL13.GL_TEXTURE1);RenderSystem.bindTexture(Math.max(0,depth));shader.sampler("Source",0);shader.sampler("Depth",1);shader.scalar("HasDepth",depth>0?1:0);shader.matrix4("InverseProjection",inverseValues);shader.scalar("Operation",Material.OPERATIONS.indexOf(node.operation()));shader.scalar("Amount",(float)node.at(normalizedAge));shader.scalar("Speed",(float)node.speed());shader.scalar("Scale",(float)node.scale());shader.scalar("Time",(float)(seconds%10000));shader.vector2("Size",width,height);var c=node.color();shader.vector4("Tint",(float)c.r(),(float)c.g(),(float)c.b(),(float)c.a());GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);texture=target.getColorTextureId();}if(i>0)intermediate.add(texture);if(i>1)for(var b:buffers)if(b.getColorTextureId()!=texture)intermediate.add(b.getColorTextureId());return texture;}finally{if(timing){GL15.glEndQuery(GL33.GL_TIME_ELAPSED);pending=true;}}}}
    public void blit(int texture,RenderTarget target){try(var state=new RenderState()){prepare(target.width,target.height);settings();target.bindWrite(true);GL30.glBindVertexArray(vao);shader.bind();RenderSystem.activeTexture(GL13.GL_TEXTURE0);RenderSystem.bindTexture(texture);shader.sampler("Source",0);shader.scalar("Operation",-1);shader.scalar("Amount",0);GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);}}
    @Override public void close(){try(var state=new RenderState()){RenderSystem.assertOnRenderThread();if(buffers!=null)for(var b:buffers)b.destroyBuffers();buffers=null;if(previewTarget!=null){previewTarget.destroyBuffers();previewTarget=null;}if(shader!=null)shader.close();if(preview!=null)preview.close();shader=preview=null;if(vao!=0)GL30.glDeleteVertexArrays(vao);if(query!=0)GL15.glDeleteQueries(query);vao=query=0;pending=false;}}
}
