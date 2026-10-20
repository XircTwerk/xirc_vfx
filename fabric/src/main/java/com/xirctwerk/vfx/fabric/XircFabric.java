package com.xirctwerk.vfx.fabric;

import com.xirctwerk.vfx.client.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class XircFabric implements ClientModInitializer {
    @Override public void onInitializeClient() {
        var studio=VfxClient.INSTANCE;studio.initialize(FabricLoader.getInstance().getGameDir());
        ClientTickEvents.END_CLIENT_TICK.register(mc->studio.tick());
        ClientCommandRegistrationCallback.EVENT.register((dispatcher,access)->StudioCommands.register(dispatcher,(source,message)->source.sendFeedback(message)));
        // Immediate, color-only VFX must follow clouds and the Fabulous transparency combine.
        WorldRenderEvents.LAST.register(context->VfxRenderer.render(context.matrixStack(),context.camera()));
    }
}
