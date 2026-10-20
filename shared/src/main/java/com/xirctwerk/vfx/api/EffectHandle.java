package com.xirctwerk.vfx.api;

import com.xirctwerk.vfx.core.Effect.*;

/** Client-thread instance controls. Authored JSON remains unchanged. */
public interface EffectHandle {
    void stop();
    boolean alive();
    EffectHandle move(Vec position);
    EffectHandle rotate(Vec degrees);
    EffectHandle scale(double scale);
    EffectHandle tint(Color multiplier);
    EffectHandle pause(boolean paused);
    EffectHandle fadeOut(double seconds);
    default EffectHandle parameter(String name,double value){throw new UnsupportedOperationException("Named bindings require a JSON effect handle");}
}
