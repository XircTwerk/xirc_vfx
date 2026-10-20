package com.xirctwerk.vfx.api;

import com.xirctwerk.vfx.core.*;
import com.xirctwerk.vfx.core.Effect.*;
import com.xirctwerk.vfx.client.VfxClient;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import java.io.IOException;

/** Opt-in, runnable examples included in the library itself. No items or gameplay hooks added. */
public final class IntegrationExamples {
    private IntegrationExamples(){}
    public static final List<String> NAMES=List.of("weapon_trail","projectile_impact","charged_attack","persistent_aura");
    private static final List<Demo> demos=new ArrayList<>();
    private static final Set<EffectHandle> owned=new HashSet<>();
    private static <T extends EffectHandle> T own(T handle){owned.add(handle);return handle;}
    private static Object world;
    private static final class Demo {String name;int ticks;Vec origin,direction;Vfx.Handle handle;RibbonTrail trail;Object level;}
    private static Project project(String name){return new Project(Effects.get(ResourceLocation.parse("xirc_vfx:example/"+name)).json());}
    public static Vfx.Handle start(String name)throws IOException{
        Vfx.checkThread();if(!NAMES.contains(name))throw new IllegalArgumentException("Examples: "+NAMES);var mc=Minecraft.getInstance();if(mc.player==null)throw new IOException("Join a world first");if(demos.size()>=8)throw new IOException("Eight examples are already running; use /xvfx demo stop");
        var d=new Demo();d.name=name;d.level=mc.level;d.origin=VfxClient.vec(mc.player.position()).add(new Vec(0,1,0));d.direction=VfxClient.vec(mc.player.getLookAngle());
        switch(name){
            case "weapon_trail"->{d.origin=d.origin.add(d.direction.scale(3)).add(new Vec(0,.4,0));d.handle=VfxClient.INSTANCE.play(project("weapon_trail"),d.origin).rotate(new Vec(0,-mc.player.getYRot(),0)).scale(.75);d.trail=new RibbonTrail();}
            case "projectile_impact"->d.handle=VfxClient.INSTANCE.play(project("projectile"),d.origin).scale(.45);
            case "charged_attack"->d.handle=VfxClient.INSTANCE.play(project("charged_attack"),d.origin.add(d.direction.scale(6))).scale(.4).parameter("power",1.5).tint(new Color(.45,.75,1,1));
            case "persistent_aura"->{d.handle=Attachments.attach(project("aura"),mc.player,new Vec(0,.2,0),true,Attachments.MissingTarget.FADE_OUT);d.handle.scale(.55);}
        }
        own(d.handle);demos.add(d);world=mc.level;return d.handle;
    }
    public static void tick(){
        var mc=Minecraft.getInstance();if(world!=mc.level){stop();world=mc.level;}if(mc.isPaused())return;owned.removeIf(handle->!handle.alive());
        for(var it=demos.iterator();it.hasNext();){var d=it.next();try{
            d.ticks++;
            if(d.name.equals("weapon_trail")&&d.ticks<=16){double angle=-1.4+d.ticks*.175;Vec side=new Vec(d.direction.z(),0,-d.direction.x());Vec tip=d.origin.add(d.direction.scale(2*Math.cos(angle))).add(side.scale(2*Math.sin(angle)));d.trail.capture(d.origin,tip,new RibbonTrail.Profile(450,48,new Color(.03,.05,.2,1),new Color(.1,.5,1,1),new Color(.8,1,1,1),.9));}
            if(d.name.equals("projectile_impact")){d.handle.move(d.origin.add(d.direction.scale(d.ticks*.2)));if(d.ticks>=30){d.handle.stop();own(VfxClient.INSTANCE.play(project("impact"),d.origin.add(d.direction.scale(6))).parameter("power",3));it.remove();continue;}}
            if(d.name.equals("charged_attack")&&d.ticks==30){own(VfxClient.INSTANCE.play(project("impact"),d.origin.add(d.direction.scale(6))).scale(.6).parameter("radius",2));d.handle.fadeOut(.4);}
            if((!d.handle.alive()||d.name.equals("weapon_trail")&&d.ticks>=30)){if(d.trail!=null){d.trail.close();d.handle.fadeOut(.2);}it.remove();}
        }catch(Exception error){d.handle.stop();if(d.trail!=null)d.trail.close();it.remove();org.slf4j.LoggerFactory.getLogger("XircVfxExamples").warn("Stopped example: {}",error.getMessage());}}
    }
    public static void stop(){Vfx.checkThread();owned.forEach(EffectHandle::stop);owned.clear();for(var d:demos)if(d.trail!=null)d.trail.close();demos.clear();}
}
