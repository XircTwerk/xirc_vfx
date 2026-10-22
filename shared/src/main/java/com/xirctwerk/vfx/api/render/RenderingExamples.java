package com.xirctwerk.vfx.api.render;

import com.xirctwerk.vfx.core.Material;
import java.util.*;

/** Runnable examples shipped with the library, without gameplay hooks or a separate example mod. */
public final class RenderingExamples {
    private RenderingExamples(){}
    public static void hologramWorld(){RenderEffects.world(Material.compile(Material.preset("hologram")));}
    public static void stopWorld(){RenderEffects.world(null);}
    /** Caller owns and closes the graph. Demonstrates independent pass state and image dependencies. */
    public static RenderGraph copyThrough(RenderPipeline pipeline){return new RenderGraph(List.of(new RenderGraph.Pass("copy","source",new RenderGraph.State(RenderGraph.Blend.OPAQUE,false,false,false),(input,output)->pipeline.blit(input.getColorTextureId(),output))));}
}
