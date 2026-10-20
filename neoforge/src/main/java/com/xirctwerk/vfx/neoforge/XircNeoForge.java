package com.xirctwerk.vfx.neoforge;

import com.xirctwerk.vfx.client.VfxClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;

/** Client entry point is never loaded on a dedicated server. */
@Mod(value=VfxClient.MOD_ID,dist=Dist.CLIENT)
public final class XircNeoForge {
    public XircNeoForge() { VfxClient.INSTANCE.initialize(FMLPaths.GAMEDIR.get()); }
}
