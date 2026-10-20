package com.xirctwerk.vfx.fabric;

import com.xirctwerk.vfx.api.*;
import com.xirctwerk.vfx.network.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

public final class XircCommon implements ModInitializer {
    public void onInitialize(){
        if(Boolean.getBoolean("xirc.serverSmoke"))org.slf4j.LoggerFactory.getLogger("XircVfx").info("XIRC_SMOKE_COMMON_INITIALIZED");
        Effects.ids();PayloadTypeRegistry.playS2C().register(VfxPayload.TYPE,VfxPayload.CODEC);
        VfxServer.installTransport(p->ServerPlayNetworking.canSend(p,VfxPayload.TYPE),(p,m)->ServerPlayNetworking.send(p,new VfxPayload(m)),(p,v)->PlayerLookup.tracking(p.serverLevel(),net.minecraft.core.BlockPos.containing(v.x(),v.y(),v.z())).contains(p));
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->VfxServerCommands.register(dispatcher));
    }
}
