package com.xirctwerk.vfx.neoforge;

import com.xirctwerk.vfx.client.*;
import com.mojang.blaze3d.systems.RenderSystem;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid=VfxClient.MOD_ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.GAME)
public final class NeoClientEvents {
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) { VfxClient.INSTANCE.tick(); }
    @SubscribeEvent public static void commands(RegisterClientCommandsEvent event) { StudioCommands.register(event.getDispatcher(),(source,message)->source.sendSuccess(()->message,false)); }
    // AFTER_PARTICLES precedes clouds and Fabulous composition, which can overwrite color-only VFX.
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL)return;
        // GameRenderer has already unwound LevelRenderer's camera matrix at AFTER_LEVEL.
        var view=RenderSystem.getModelViewStack();view.pushMatrix();view.mul(event.getModelViewMatrix());RenderSystem.applyModelViewMatrix();
        try {VfxRenderer.render(event.getPoseStack(),event.getCamera());}
        finally {view.popMatrix();RenderSystem.applyModelViewMatrix();}
    }
}
