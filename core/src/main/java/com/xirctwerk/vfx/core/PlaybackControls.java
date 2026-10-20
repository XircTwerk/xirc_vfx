package com.xirctwerk.vfx.core;

import static com.xirctwerk.vfx.core.Effect.*;

/** Per-instance state: never edits the authored definition. Time is simulation seconds. */
public final class PlaybackControls {
    private Vec offset=Vec.ZERO,rotation=Vec.ZERO;
    private double scale=1,fadeDuration,fadeAge;
    private Color tint=new Color(1,1,1,1);
    private boolean paused,stopped,fading;
    public static Vec finite(Vec v){if(v==null||!Double.isFinite(v.x())||!Double.isFinite(v.y())||!Double.isFinite(v.z())||Math.abs(v.x())>30_000_000||Math.abs(v.y())>30_000_000||Math.abs(v.z())>30_000_000)throw new IllegalArgumentException("Use finite XYZ coordinates within +/-30000000");return v;}
    public void move(Vec value){offset=finite(value);}
    public void rotate(Vec value){rotation=finite(value);}
    public void scale(double value){if(!Double.isFinite(value)||value<.01||value>8)throw new IllegalArgumentException("Instance scale must be .01..8");scale=value;}
    public void tint(Color c){if(c==null)throw new IllegalArgumentException("Tint is required");for(double x:new double[]{c.r(),c.g(),c.b(),c.a()})if(!Double.isFinite(x)||x<0||x>1)throw new IllegalArgumentException("Tint channels must be 0..1");tint=c;}
    public void pause(boolean value){paused=value;}
    public void stop(){stopped=true;}
    public void fadeOut(double seconds){if(!Double.isFinite(seconds)||seconds<0||seconds>300)throw new IllegalArgumentException("Fade duration must be 0..300 seconds");fading=true;fadeDuration=seconds;fadeAge=0;if(seconds==0)stop();}
    public void tick(){if(!paused&&fading){fadeAge+=Simulation.DT;if(fadeAge+1e-9>=fadeDuration)stop();}}
    public boolean alive(){return !stopped;}
    public boolean paused(){return paused;}
    public Vec offset(){return offset;}
    public Transforms.Pose pose(){return Transforms.Pose.euler(Vec.ZERO,rotation,scale);}
    public Color color(Color original){double alpha=fading?Math.max(0,1-fadeAge/Math.max(1e-9,fadeDuration)):1;return new Color(original.r()*tint.r(),original.g()*tint.g(),original.b()*tint.b(),original.a()*tint.a()*alpha);}
}
