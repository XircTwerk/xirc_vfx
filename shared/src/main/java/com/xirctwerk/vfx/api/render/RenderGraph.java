package com.xirctwerk.vfx.api.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.*;
import org.lwjgl.opengl.GL11;

/** Trusted Java render passes with named dependencies. Inputs must precede consumers. */
public final class RenderGraph implements AutoCloseable {
    public enum Blend { OPAQUE, ALPHA, ADDITIVE }
    public record State(Blend blend,boolean depthTest,boolean depthWrite,boolean cull){public State{Objects.requireNonNull(blend);}}
    @FunctionalInterface public interface Draw {void draw(RenderTarget input,RenderTarget output);}
    public record Pass(String name,String input,State state,Draw draw){public Pass{if(name==null||!name.matches("[a-z][a-z0-9_]{0,63}"))throw new IllegalArgumentException("Invalid pass name");Objects.requireNonNull(input);Objects.requireNonNull(state);Objects.requireNonNull(draw);}}
    private final RenderTargets targets=new RenderTargets();private final List<Pass> passes;
    public RenderGraph(List<Pass> passes){this.passes=List.copyOf(passes);if(passes.size()>8)throw new IllegalArgumentException("Use at most 8 passes");var names=new HashSet<>(Set.of("source"));for(var pass:passes){if(!names.contains(pass.input())||!names.add(pass.name()))throw new IllegalArgumentException("Pass inputs must exist earlier; no duplicate names or cycles.");}}
    public RenderTarget render(RenderTarget source){RenderSystem.assertOnRenderThread();var images=new HashMap<String,RenderTarget>();images.put("source",source);RenderTarget result=source;for(var p:passes)try(var scope=new RenderState()){var input=images.get(p.input());var output=targets.acquire(p.name(),source.width,source.height,p.state().depthTest()||p.state().depthWrite());output.setClearColor(0,0,0,0);GL11.glDisable(GL11.GL_SCISSOR_TEST);RenderSystem.colorMask(true,true,true,true);output.clear(net.minecraft.client.Minecraft.ON_OSX);if(output.useDepth&&source.useDepth)targets.copyDepth(p.name(),source);output.bindWrite(true);var s=p.state();if(s.depthTest())RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();RenderSystem.depthMask(s.depthWrite());if(s.cull())RenderSystem.enableCull();else RenderSystem.disableCull();if(s.blend()==Blend.OPAQUE)RenderSystem.disableBlend();else{RenderSystem.enableBlend();if(s.blend()==Blend.ALPHA)RenderSystem.defaultBlendFunc();else RenderSystem.blendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE);}p.draw().draw(input,output);images.put(p.name(),output);result=output;}return result;}
    @Override public void close(){targets.close();}
}
