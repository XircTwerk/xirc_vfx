package com.xirctwerk.vfx.core;

import java.util.*;

/** Advisory artist thresholds; never changes hard validation or silently drops quality. */
public final class BudgetWarnings {
    private BudgetWarnings() {}
    public record Limits(int particles,int geometry) {
        public Limits {
            if(particles<1||particles>Simulation.MAX_PARTICLES||geometry<1||geometry>65536)
                throw new IllegalArgumentException("Warning thresholds must be within particle 1..8192 and geometry 1..65536 budgets");
        }
    }
    public static List<String> inspect(Simulation simulation,Limits limits) {
        var warnings=new ArrayList<String>();
        double particles=EffectCodec.estimatedParticles(simulation.effect());
        double geometry=EffectCodec.estimatedGeometry(simulation.effect());
        if(particles>=limits.particles())warnings.add("Estimated particles "+(int)Math.ceil(particles)+" reach warning threshold "+limits.particles()+". Reduce rate, bursts or lifetime.");
        if(simulation.particles().size()>=limits.particles())warnings.add("Live particles reach warning threshold. Inspect high-count layers below.");
        if(geometry>=limits.geometry())warnings.add("Estimated procedural quads "+(int)Math.ceil(geometry)+" reach warning threshold "+limits.geometry()+". Reduce mesh births, lifetime or trail points.");
        if(simulation.dropped()>0)warnings.add(simulation.dropped()+" births were dropped by the runtime hard budget. Reduce overlapping playback or particle demand.");
        return List.copyOf(warnings);
    }
}
