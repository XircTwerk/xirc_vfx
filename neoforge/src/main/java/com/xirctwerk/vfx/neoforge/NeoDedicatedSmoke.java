package com.xirctwerk.vfx.neoforge;

import com.xirctwerk.vfx.api.*;
import com.xirctwerk.vfx.core.Effect.Vec;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import java.util.Map;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Registered only by the explicit xirc.serverSmoke development run. */
@PrefixGameTestTemplate(false)
public final class NeoDedicatedSmoke {
    @GameTest(template="empty",templateNamespace="xirc_vfx",timeoutTicks=40)
    public static void commonApi(GameTestHelper helper){
        if(Effects.ids().isEmpty())throw new IllegalStateException("Common effect registry is empty");
        var handle=VfxServer.play(helper.getLevel(),ResourceLocation.parse("xirc_vfx:example/impact"),new Vec(0,65,0),32,1,Map.of("power",2.));
        if(handle.recipients()!=0)throw new IllegalStateException("Unexpected headless test players");handle.stop();
        org.slf4j.LoggerFactory.getLogger("XircVfx").info("XIRC_SMOKE_DEDICATED_API_PASSED");helper.succeed();
    }
}
