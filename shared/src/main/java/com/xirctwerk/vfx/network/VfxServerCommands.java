package com.xirctwerk.vfx.network;

import com.xirctwerk.vfx.api.*;
import com.xirctwerk.vfx.core.Effect.*;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.Map;

/** Explicit operator-only network example; never reacts to normal combat automatically. */
public final class VfxServerCommands {
    private VfxServerCommands(){}
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher){
        dispatcher.register(Commands.literal("xvfx-server").requires(s->s.hasPermission(2)).then(Commands.literal("demo").executes(c->{
            var source=c.getSource();var p=source.getPosition();
            var handle=VfxServer.play(source.getLevel(),ResourceLocation.parse("xirc_vfx:example/impact"),new Vec(p.x,p.y+1,p.z+3),64,42,Map.of("power",2.5,"radius",2.));
            source.sendSuccess(()->Component.literal("Xirc's VFX: sent example to "+handle.recipients()+" supported clients in range."),false);return 1;
        })));
    }
}
