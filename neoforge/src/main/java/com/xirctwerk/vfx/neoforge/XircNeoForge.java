package com.xirctwerk.vfx.neoforge;

import com.xirctwerk.vfx.api.*;
import com.xirctwerk.vfx.client.*;
import com.xirctwerk.vfx.network.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@Mod("xirc_vfx")
public final class XircNeoForge {
    public XircNeoForge(IEventBus bus) {
        if(Boolean.getBoolean("xirc.serverSmoke"))org.slf4j.LoggerFactory.getLogger("XircVfx").info("XIRC_SMOKE_COMMON_INITIALIZED");
        Effects.ids();bus.addListener(this::network);
        if(Boolean.getBoolean("xirc.serverSmoke"))bus.addListener((net.neoforged.neoforge.event.RegisterGameTestsEvent event)->event.register(NeoDedicatedSmoke.class));
        VfxServer.installTransport(p->NetworkRegistry.hasChannel(p.connection,VfxPayload.TYPE.id()),(p,m)->PacketDistributor.sendToPlayer(p,new VfxPayload(m)),(p,v)->p.serverLevel().getChunkSource().chunkMap.getPlayers(new net.minecraft.world.level.ChunkPos(net.minecraft.core.BlockPos.containing(v.x(),v.y(),v.z())),false).contains(p));
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event)->VfxServerCommands.register(event.getDispatcher()));
        if(FMLEnvironment.dist==Dist.CLIENT)VfxClient.INSTANCE.initialize(FMLPaths.GAMEDIR.get());
    }
    private void network(RegisterPayloadHandlersEvent event){event.registrar("1").optional().playToClient(VfxPayload.TYPE,VfxPayload.CODEC,(payload,context)->context.enqueueWork(()->VfxNetworkClient.receive(payload.message())));}
}
