package com.xirctwerk.vfx.api.render;

import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import java.util.*;

/** Named target pool with explicit ownership, depth access and a 128 MiB allocation budget. */
public final class RenderTargets implements AutoCloseable {
    private record Entry(TextureTarget target,boolean depth){}
    private final Map<String,Entry> targets=new LinkedHashMap<>();
    public TextureTarget acquire(String name,int width,int height,boolean depth){try(var scope=new RenderState()){RenderSystem.assertOnRenderThread();if(name==null||!name.matches("[a-z][a-z0-9_]{0,63}")||width<1||height<1||width>4096||height>4096)throw new IllegalArgumentException("Use a named target of 1..4096 pixels per axis.");var old=targets.get(name);long bytes=(long)width*height*(depth?8:4);for(var entry:targets.entrySet())if(!entry.getKey().equals(name))bytes+=(long)entry.getValue().target.width*entry.getValue().target.height*(entry.getValue().depth?8:4);if(bytes>128L*1024*1024||old==null&&targets.size()>=8)throw new IllegalArgumentException("Render pool exceeds 8 targets / 128 MiB.");if(old!=null&&old.depth!=depth){old.target.destroyBuffers();targets.remove(name);old=null;}if(old==null){old=new Entry(new TextureTarget(width,height,depth,Minecraft.ON_OSX),depth);targets.put(name,old);}else if(old.target.width!=width||old.target.height!=height)old.target.resize(width,height,Minecraft.ON_OSX);return old.target;}}
    public void copyDepth(String name,RenderTarget source){var entry=targets.get(name);if(entry==null||!entry.depth)throw new IllegalArgumentException("Acquire a depth-enabled target first.");try(var state=new RenderState()){entry.target.copyDepthFrom(source);}}
    public void release(String name){try(var scope=new RenderState()){RenderSystem.assertOnRenderThread();var entry=targets.remove(name);if(entry!=null)entry.target.destroyBuffers();}}
    @Override public void close(){try(var scope=new RenderState()){RenderSystem.assertOnRenderThread();for(var entry:targets.values())entry.target.destroyBuffers();targets.clear();}}
}
