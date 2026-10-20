package com.xirctwerk.vfx.api;

import com.xirctwerk.vfx.client.VfxClient;
import java.util.*;

/** Client CPU timings and submitted batches. GPU timings are deliberately not inferred. */
public final class VfxPerformance {
    private VfxPerformance(){}
    public record Limits(int instances,int particles,int quads){
        public Limits{if(instances<1||instances>16||particles<1||particles>8192||quads<1||quads>32768)throw new IllegalArgumentException("Limits: instances 1..16, particles 1..8192, geometry quads 1..32768");}
    }
    public record Row(String effect,int particles,int quads,int drawCalls,double simulationMicros,double renderCpuMicros){}
    private static Limits limits=new Limits(16,8192,32768);
    private static final Map<Object,Mutable> frame=new LinkedHashMap<>();
    private static final class Mutable{String name;int particles,quads,calls;long simulation,render;}
    public static Limits limits(){return limits;}
    public static void configure(Limits value){Vfx.checkThread();Objects.requireNonNull(value);VfxClient.INSTANCE.stop();limits=value;}
    public static void beginFrame(){frame.clear();for(var p:VfxClient.INSTANCE.playbacks()){var row=row(p);row.name=p.simulation().effect().id();row.particles=p.simulation().particles().size();row.simulation=p.simulationNanos();}}
    private static Mutable row(Object owner){return frame.computeIfAbsent(owner,o->{var r=new Mutable();r.name=o instanceof VfxClient.Playback p?p.simulation().effect().id():o.getClass().getSimpleName();return r;});}
    public static void record(Object owner,int quads,int calls,long nanos){var r=row(owner);r.quads+=quads;r.calls+=calls;r.render+=Math.max(0,nanos);}
    public static List<Row> snapshot(){Vfx.checkThread();return frame.values().stream().map(r->new Row(r.name,r.particles,r.quads,r.calls,r.simulation/1000.,r.render/1000.)).toList();}
}
