package com.xirctwerk.vfx.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BudgetWarningsTest {
    @Test void advisoryLimitsDoNotMutateSimulationOrDefinition() {
        var effect=EffectCodec.compile(Templates.newProject("budget_test"));
        var simulation=new Simulation(effect);
        var warnings=BudgetWarnings.inspect(simulation,new BudgetWarnings.Limits(1,1));
        assertTrue(warnings.stream().anyMatch(s->s.contains("Estimated particles")));
        assertEquals(0,simulation.time());
        assertEquals(0,simulation.dropped());
        assertSame(effect,simulation.effect());
        assertThrows(UnsupportedOperationException.class,()->warnings.add("bad"));
        assertTrue(BudgetWarnings.inspect(simulation,new BudgetWarnings.Limits(8192,65536)).isEmpty());
    }
    @Test void invalidThresholdsFailInsteadOfDisablingHardBudgets() {
        assertThrows(IllegalArgumentException.class,()->new BudgetWarnings.Limits(0,1));
        assertThrows(IllegalArgumentException.class,()->new BudgetWarnings.Limits(8193,1));
        assertThrows(IllegalArgumentException.class,()->new BudgetWarnings.Limits(1,65537));
    }
}
