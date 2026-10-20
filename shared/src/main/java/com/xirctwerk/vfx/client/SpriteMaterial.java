package com.xirctwerk.vfx.client;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Translucent sprites test world depth without writing faint sprite surfaces into it. */
final class SpriteMaterial extends RenderType {
    private record Key(ResourceLocation texture,boolean glow,boolean additive,boolean bloom) {}
    private static final TransparencyStateShard ADDITIVE=new TransparencyStateShard("xirc_additive",()->{com.mojang.blaze3d.systems.RenderSystem.enableBlend();com.mojang.blaze3d.systems.RenderSystem.blendFunc(org.lwjgl.opengl.GL11.GL_SRC_ALPHA,org.lwjgl.opengl.GL11.GL_ONE);},()->{com.mojang.blaze3d.systems.RenderSystem.disableBlend();com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();});
    private static final OutputStateShard BLOOM_OUTPUT=new OutputStateShard("xirc_bloom",BloomRenderer::bindSource,()->{});
    private static final Map<Key,RenderType> CACHE=new LinkedHashMap<>(256,.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Key,RenderType> e) {return size()>256;}
    };
    private SpriteMaterial() {super("xirc_sprite",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,256,false,true,()->{},()->{});}
    static RenderType get(ResourceLocation texture,boolean glow,boolean additive,boolean bloom) {
        return CACHE.computeIfAbsent(new Key(texture,glow,additive,bloom),key->create("xirc_sprite",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,1536,false,true,
            CompositeState.builder().setShaderState(glow?RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER:RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                .setTextureState(new TextureStateShard(texture,false,false)).setTransparencyState(additive?ADDITIVE:TRANSLUCENT_TRANSPARENCY).setOutputState(bloom?BLOOM_OUTPUT:MAIN_TARGET)
                .setCullState(NO_CULL).setLightmapState(LIGHTMAP).setOverlayState(OVERLAY).setWriteMaskState(COLOR_WRITE).createCompositeState(false)));
    }
}
