package com.xirctwerk.vfx.client;

import com.xirctwerk.vfx.api.*;
import com.xirctwerk.vfx.core.*;
import com.xirctwerk.vfx.core.Effect.*;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.decoration.ArmorStand;
import java.util.*;

/** Opt-in actual client/server integration assertions, called by the main smoke harness. */
final class LibrarySmokeTest {
    private static int ticks;private static VfxClient.Playback handle;
    private static EffectHandle stopped,fading,detached,aura;private static ArmorStand entity;
    private static volatile VfxServer.Handle network;private static volatile Throwable serverError;
    private static long accepted;
    private static Vec origin;
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger("XircLibrarySmoke");
    private static void require(boolean pass,String message){if(!pass)throw new IllegalStateException(message);}
    static boolean tick()throws Exception{
        var mc=Minecraft.getInstance();ticks++;
        if(ticks==1){
            VfxClient.INSTANCE.stop();mc.setScreen(null);mc.player.setXRot(0);origin=VfxClient.vec(mc.player.position()).add(new Vec(0,1,3));
            var json=IntegrationPresets.create("projectile");handle=VfxClient.INSTANCE.play(new Project(json),origin,123);
            handle.pause(true).move(origin.add(new Vec(1,0,0))).rotate(new Vec(0,45,0)).scale(.75).tint(new Color(.5,1,.25,.8));
        }
        if(ticks==5){require(handle.simulation().time()==0,"Paused JSON handle advanced");require(handle.anchor().get().equals(origin.add(new Vec(1,0,0))),"Handle move failed");handle.pause(false).parameter("power",2);}
        if(ticks==10){require(handle.simulation().time()>0,"Handle did not resume");require(handle.simulation().effect().emitters().getFirst().bloom()==2,"Handle parameter failed");require(handle.color(new Color(1,1,1,1)).r()==.5,"Handle tint failed");handle.fadeOut(.1);}
        if(ticks==14){require(!handle.alive(),"Fade did not stop JSON handle");LOG.info("XIRC_SMOKE_HANDLES_PASSED");}
        if(ticks==15){
            entity=new ArmorStand(mc.level,origin.x(),origin.y(),origin.z());mc.level.addEntity(entity);
            var project=new Project(Templates.newProject("attachment_smoke"));
            stopped=Attachments.attach(project,entity,new Vec(0,1,0),true,Attachments.MissingTarget.STOP);
            fading=Attachments.attach(project,entity,Vec.ZERO,false,Attachments.MissingTarget.FADE_OUT);
            detached=Attachments.attach(project,entity,Vec.ZERO,false,Attachments.MissingTarget.DETACH);
        }
        if(ticks==20)entity.discard();
        if(ticks==28){require(!stopped.alive()&&!fading.alive()&&detached.alive(),"Attachment missing-target policies failed");detached.stop();LOG.info("XIRC_SMOKE_ATTACHMENTS_PASSED");}
        if(ticks==29)RenderSmokeTest.run();
        if(ticks==30)Vfx.slash(()->origin,new Vec(0,0,1),2,.2,1,new Vfx.Style(new Color(.3,.7,1,1),2,1)).move(origin).rotate(new Vec(0,20,0)).scale(1.2).tint(new Color(1,.5,1,1)).pause(false).fadeOut(.5);
        if(ticks==40){VfxClient.INSTANCE.stop();IntegrationExamples.start("weapon_trail");}
        if(ticks==60){require(!VfxPerformance.snapshot().isEmpty(),"Renderer profile contains no effects");require(VfxPerformance.snapshot().stream().anyMatch(r->r.drawCalls()>0&&r.renderCpuMicros()>0),"Renderer did not record actual submissions");}
        if(ticks==50)StudioSmokeTest.capture("example-weapon-trail.png");
        if(ticks==108)StudioSmokeTest.capture("example-projectile-impact.png");
        if(ticks==140)StudioSmokeTest.capture("example-charged-attack.png");
        if(ticks==180)StudioSmokeTest.capture("example-persistent-aura.png");
        if(ticks==220)StudioSmokeTest.capture("example-server-playback.png");
        if(ticks==70)VfxClient.INSTANCE.stop();
        if(ticks==75)IntegrationExamples.start("projectile_impact");
        if(ticks==110){require(VfxClient.INSTANCE.playbacks().stream().anyMatch(p->p.simulation().effect().id().endsWith("/impact")),"Projectile example did not produce impact");IntegrationExamples.stop();require(VfxClient.INSTANCE.playbacks().stream().noneMatch(p->p.alive()),"Example stop leaked its child impact");VfxClient.INSTANCE.stop();}
        if(ticks==115)IntegrationExamples.start("charged_attack");
        if(ticks==155){require(VfxClient.INSTANCE.playbacks().stream().anyMatch(p->p.simulation().effect().id().endsWith("/impact")),"Charged example did not produce impact");VfxClient.INSTANCE.stop();}
        if(ticks==160)aura=IntegrationExamples.start("persistent_aura");
        if(ticks==200){require(aura.alive(),"Persistent aura stopped unexpectedly");VfxClient.INSTANCE.stop();require(!aura.alive(),"Example stop leaked a handle");LOG.info("XIRC_SMOKE_BUILTIN_EXAMPLES_PASSED");
            var old=VfxPerformance.limits();VfxPerformance.configure(new VfxPerformance.Limits(1,1,1));boolean rejected=false;try{Vfx.play(new Project(Templates.create("sparks")),origin);}catch(java.io.IOException expected){rejected=true;}require(rejected,"Configured playback budget was ignored");VfxPerformance.configure(old);LOG.info("XIRC_SMOKE_PROFILING_BUDGETS_PASSED");
        }
        if(ticks==205){accepted=VfxNetworkClient.accepted();var server=mc.getSingleplayerServer();require(server!=null,"Network smoke requires integrated server");server.execute(()->{try{network=VfxServer.play(server.overworld(),ResourceLocation.parse("xirc_vfx:example/projectile"),origin,64,987654321L,Map.of("power",2.));}catch(Throwable e){serverError=e;}});}
        if(ticks==225){if(serverError!=null)throw new IllegalStateException("Server send failed",serverError);require(network!=null&&network.recipients()==1,"Network transport did not discover supported client");require(VfxNetworkClient.accepted()>accepted,"Actual clientbound packet was not accepted: "+VfxNetworkClient.lastRejection());require(VfxClient.INSTANCE.playbacks().stream().anyMatch(p->p.simulation().effect().id().endsWith("/projectile")),"Network packet did not spawn effect");mc.getSingleplayerServer().execute(()->network.stop());}
        if(ticks==240){require(VfxClient.INSTANCE.playbacks().isEmpty(),"Server stop did not stop client instance");var id=ResourceLocation.parse("xirc_vfx:example/impact");var definition=Effects.get(id);long rejected=VfxNetworkClient.rejected();VfxNetworkClient.receive(new PlaybackMessage(UUID.randomUUID(),false,id.toString(),"0".repeat(64),mc.level.dimension().location().toString(),origin,1,Map.of(),new Color(1,1,1,1),null,Vec.ZERO));require(VfxNetworkClient.rejected()==rejected+1&&VfxClient.INSTANCE.playbacks().isEmpty(),"Mismatched definitions were accepted");
            var missing=Templates.newProject("missing_resource_smoke");missing.addProperty("id","xirc_vfx:missing_resource_smoke");missing.getAsJsonArray("emitters").get(0).getAsJsonObject().addProperty("texture","xirc_vfx:textures/absent_smoke.png");var missingId=ResourceLocation.parse("xirc_vfx:missing_resource_smoke");Effects.register(missingId,missing);
            rejected=VfxNetworkClient.rejected();VfxNetworkClient.receive(new PlaybackMessage(UUID.randomUUID(),false,missingId.toString(),Effects.get(missingId).digest(),mc.level.dimension().location().toString(),origin,1,Map.of(),new Color(1,1,1,1),null,Vec.ZERO));require(VfxNetworkClient.rejected()==rejected+1&&VfxNetworkClient.lastRejection().contains("Missing "),"Missing texture was not rejected before spawning");
            accepted=VfxNetworkClient.accepted();serverError=null;mc.getSingleplayerServer().execute(()->{try{var player=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();network=VfxServer.attach(player,ResourceLocation.parse("xirc_vfx:example/projectile"),new Vec(0,1,0),64,88,Map.of("power",1.));}catch(Throwable error){serverError=error;}});
        }
        if(ticks==260){if(serverError!=null)throw new IllegalStateException("Network attachment failed",serverError);require(VfxNetworkClient.accepted()>accepted,"Network attachment was not accepted");require(VfxClient.INSTANCE.playbacks().stream().anyMatch(p->p.anchor().get().add(VfxClient.vec(mc.player.position()).scale(-1)).length()<2),"Network attachment did not follow the player");mc.getSingleplayerServer().execute(()->network.stop());}
        if(ticks==275){require(VfxClient.INSTANCE.playbacks().isEmpty(),"Server attachment stop leaked playback");LOG.info("XIRC_SMOKE_NETWORK_PASSED");LOG.info("XIRC_SMOKE_LIBRARY_API_PASSED");mc.setScreen(new StudioScreen());}
        try{if(ticks==280)((StudioScreen)mc.screen).smokeMaterials(0);
        if(ticks==300){((StudioScreen)mc.screen).smokeMaterials(1);StudioSmokeTest.capture("studio-materials-shaders.png");}
        if(ticks==310){((StudioScreen)mc.screen).smokeMaterials(2);mc.setScreen(null);LOG.info("XIRC_SMOKE_MATERIAL_WORKSPACE_PASSED");return true;}}
        catch(Exception e){throw new IllegalStateException("Material workspace smoke failed",e);}
        return false;
    }
}
