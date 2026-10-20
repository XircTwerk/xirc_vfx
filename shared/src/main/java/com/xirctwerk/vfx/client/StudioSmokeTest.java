package com.xirctwerk.vfx.client;

import com.xirctwerk.vfx.core.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.slf4j.LoggerFactory;

/** Opt-in development harness. Never runs unless -PxircSmokeTest is passed to Gradle. */
final class StudioSmokeTest {
    private static int state,ticks;
    private static com.xirctwerk.vfx.api.RibbonTrail trail;
    private static final org.slf4j.Logger LOG=LoggerFactory.getLogger("XircSmokeTest");
    static void tick() {
        if(!Boolean.getBoolean("xirc.smokeTest")) return;
        Minecraft mc=Minecraft.getInstance();mc.options.pauseOnLostFocus=false;ticks++;
        try {
            if(state==0&&mc.screen!=null&&mc.getOverlay()==null&&ticks>80) {mc.options.guiScale().set(3);mc.resizeDisplay();mc.setScreen(new StudioScreen());state=1;ticks=0;}
            else if(state==1&&ticks==40) {var screen=(StudioScreen)mc.screen;screen.smokeWorkspace();screen.smokeJsonSave();LOG.info("XIRC_SMOKE_JSON_SAVE_PASSED");capture("studio-workspace.png");}
            else if(state==1&&ticks==60&&mc.screen instanceof StudioScreen screen) screen.smokeEditor(false);
            else if(state==1&&ticks==90&&mc.screen instanceof StudioScreen screen) {screen.smokeInteractions();LOG.info("XIRC_SMOKE_EDITOR_INPUT_PASSED");}
            else if(state==1&&ticks==92&&mc.screen instanceof StudioScreen screen) screen.smokeDrag(0);
            else if(state==1&&ticks==94&&mc.screen instanceof StudioScreen screen) screen.smokeDrag(1);
            else if(state==1&&ticks==96&&mc.screen instanceof StudioScreen screen) screen.smokeDrag(2);
            else if(state==1&&ticks==98&&mc.screen instanceof StudioScreen screen) {screen.smokeDrag(3);LOG.info("XIRC_SMOKE_IMGUI_PASSED");}
            else if(state==1&&ticks==100) capture("studio-simple.png");
            else if(state==1&&ticks==102&&mc.screen instanceof StudioScreen screen)screen.smokeHints(false);
            else if(state==1&&ticks==108&&mc.screen instanceof StudioScreen screen){screen.smokeHints(true);LOG.info("XIRC_SMOKE_CONTROL_HINTS_PASSED");capture("studio-control-hints.png");}
            else if(state==1&&ticks==110&&mc.screen instanceof StudioScreen screen) screen.smokeProperty(0);
            else if(state==1&&ticks==114&&mc.screen instanceof StudioScreen screen) screen.smokeProperty(1);
            else if(state==1&&ticks==116&&mc.screen instanceof StudioScreen screen) screen.smokeProperty(2);
            else if(state==1&&ticks==118&&mc.screen instanceof StudioScreen screen) {screen.smokeProperty(3);capture("studio-curve-editor.png");}
            else if(state==1&&ticks==122&&mc.screen instanceof StudioScreen screen) screen.smokeProperty(4);
            else if(state==1&&ticks==124&&mc.screen instanceof StudioScreen screen) screen.smokeProperty(5);
            else if(state==1&&ticks==128&&mc.screen instanceof StudioScreen screen) {screen.smokeProperty(6);LOG.info("XIRC_SMOKE_PROPERTY_EDITOR_PASSED");}
            else if(state==1&&ticks==130&&mc.screen instanceof StudioScreen screen) screen.smokeGradient(false);
            else if(state==1&&ticks==133) capture("studio-gradient-editor.png");
            else if(state==1&&ticks==134&&mc.screen instanceof StudioScreen screen) screen.smokeGradient(true);
            else if(state==1&&ticks==136&&mc.screen instanceof StudioScreen screen) screen.smokeBursts(0);
            else if(state==1&&ticks==139&&mc.screen instanceof StudioScreen screen) screen.smokeBursts(1);
            else if(state==1&&ticks==141&&mc.screen instanceof StudioScreen screen) screen.smokeBursts(2);
            else if(state==1&&ticks==143) capture("studio-burst-editor.png");
            else if(state==1&&ticks==144&&mc.screen instanceof StudioScreen screen) screen.smokeBursts(3);
            else if(state==1&&ticks==146&&mc.screen instanceof StudioScreen screen) screen.smokeBursts(4);
            else if(state==1&&ticks==148&&mc.screen instanceof StudioScreen screen) {screen.smokeBursts(5);LOG.info("XIRC_SMOKE_BURST_EDITOR_PASSED");}
            else if(state==1&&ticks>=150&&ticks<=162&&(ticks-150)%2==0&&mc.screen instanceof StudioScreen screen)screen.smokeTimeline((ticks-150)/2);
            else if(state==1&&ticks==161)capture("studio-timeline-key.png");
            else if(state==1&&ticks==166&&mc.screen instanceof StudioScreen screen){screen.smokeTimeline(7);LOG.info("XIRC_SMOKE_TIMELINE_PASSED");capture("studio-timeline.png");}
            else if(state==1&&ticks==168&&mc.screen instanceof StudioScreen screen)screen.smokeTimeline(9);
            else if(state==1&&ticks==170)capture("studio-timeline-color.png");
            else if(state==1&&ticks==172&&mc.screen instanceof StudioScreen screen)screen.smokeTimeline(10);
            else if(state==1&&ticks==173)capture("studio-composition-map.png");
            else if(state==1&&ticks==174&&mc.screen instanceof StudioScreen screen)screen.smokeTimeline(8);
            else if(state==1&&ticks==180&&mc.screen instanceof StudioScreen screen) screen.smokeEditor(true);
            else if(state==1&&ticks==185) capture("studio-advanced.png");
            else if(state==1&&ticks>=190&&ticks<=222&&(ticks-190)%2==0&&mc.screen instanceof StudioScreen screen){screen.smokeAuthoring((ticks-190)/2);if(ticks==222)LOG.info("XIRC_SMOKE_AUTHORING_SHORTCUTS_PASSED");}
            else if(state==1&&ticks==224&&mc.screen instanceof StudioScreen screen)screen.smokeTools(0);
            else if(state==1&&ticks==228&&mc.screen instanceof StudioScreen screen)screen.smokeTools(1);
            else if(state==1&&ticks==230&&mc.screen instanceof StudioScreen screen)screen.smokeTools(2);
            else if(state==1&&ticks==232&&mc.screen instanceof StudioScreen screen){screen.smokeTools(3);capture("studio-batch-tools.png");}
            else if(state==1&&ticks==234&&mc.screen instanceof StudioScreen screen)screen.smokeTools(4);
            else if(state==1&&ticks==240)capture("studio-bezier-editor.png");
            else if(state==1&&ticks==242&&mc.screen instanceof StudioScreen screen)screen.smokeTools(5);
            else if(state==1&&ticks==246&&mc.screen instanceof StudioScreen screen){screen.smokeTools(6);LOG.info("XIRC_SMOKE_WORKBENCH_PASSED");}
            else if(state==1&&ticks==248&&mc.screen instanceof StudioScreen screen)screen.smokeTools(7);
            else if(state==1&&ticks==250&&mc.screen instanceof StudioScreen screen)screen.smokeTools(8);
            else if(state==1&&ticks==252&&mc.screen instanceof StudioScreen screen)screen.smokeTools(9);
            else if(state==1&&ticks==254&&mc.screen instanceof StudioScreen screen)screen.smokeTools(10);
            else if(state==1&&ticks==255)capture("studio-parameters.png");
            else if(state==1&&ticks==256&&mc.screen instanceof StudioScreen screen){screen.smokeTools(11);LOG.info("XIRC_SMOKE_PARAMETERS_PASSED");}
            else if(state==1&&ticks==258&&mc.screen instanceof StudioScreen screen)screen.smokeTools(6);
            else if(state==1&&ticks>=260&&ticks<540&&(ticks-260)%35==0&&mc.screen instanceof StudioScreen screen)screen.smokeAdvanced((ticks-260)/35);
            else if(state==1&&ticks>=260&&ticks<540&&(ticks-260)%35==20)capture("advanced-"+Templates.ADVANCED_NAMES.get((ticks-260)/35)+".png");
            else if(state==1&&ticks==541&&mc.screen instanceof StudioScreen screen)screen.smokePhoton(4);
            else if(state==1&&ticks==545)capture("studio-guide.png");
            else if(state==1&&ticks==548&&mc.screen instanceof StudioScreen screen){screen.smokePhoton(5);LOG.info("XIRC_SMOKE_GUIDE_PASSED");}
            else if(state==1&&ticks==550&&mc.screen instanceof StudioScreen screen) {photonFixture();screen.smokePhoton(0);}
            else if(state==1&&ticks==560)capture("studio-photon-import.png");
            else if(state==1&&ticks==570&&mc.screen instanceof StudioScreen screen)screen.smokePhoton(1);
            else if(state==1&&ticks==572&&mc.screen instanceof StudioScreen screen)screen.smokePhoton(2);
            else if(state==1&&ticks==574&&mc.screen instanceof StudioScreen screen) {screen.smokePhoton(3);LOG.info("XIRC_SMOKE_PHOTON_IMPORT_PASSED");}
            else if(state==1&&ticks==583)capture("studio-photon-visible.png");
            else if(state==1&&ticks==584&&mc.screen instanceof StudioScreen screen){screen.smokePhoton(6);LOG.info("XIRC_SMOKE_PHOTON_VISIBLE_PASSED");}
            else if(state==1&&ticks==590) {
                state=2;ticks=0;
                var settings=new LevelSettings("Xirc runtime smoke test",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel("xirc-smoke-"+System.currentTimeMillis(),settings,new WorldOptions(42,false,false),registries->registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),new TitleScreen());
            } else if(state==2&&mc.player!=null&&mc.level!=null&&ticks>100) {
                mc.setScreen(null);mc.player.setYRot(0);mc.player.setXRot(0);
                var dispatcher=new com.mojang.brigadier.CommandDispatcher<Object>();StudioCommands.register(dispatcher,(source,message)->LOG.info("Command feedback: {}",message.getString()));
                Object source=new Object();
                for(String command:new String[]{"xvfx list","xvfx play magic_aura","xvfx me sparks","xvfx attach projectile_trail "+mc.player.getId()}) if(dispatcher.execute(command,source)!=1) throw new IllegalStateException("Command failed: "+command);
                var ground=mc.player.blockPosition().below();
                if(dispatcher.execute("xvfx block fire "+ground.getX()+" "+ground.getY()+" "+ground.getZ(),source)!=1) throw new IllegalStateException("Block attachment failed.");
                if(dispatcher.execute("xvfx stop",source)!=1) throw new IllegalStateException("Stop failed.");
                studioRoundTrip();
                LOG.info("XIRC_SMOKE_COMMANDS_AND_PACKAGE_PASSED");
                var center=VfxClient.vec(mc.player.position()).add(new Effect.Vec(0,1,3));
                VfxClient.INSTANCE.play(new Project(Templates.create("magic_aura")),center);
                VfxClient.INSTANCE.play(new Project(Templates.create("beam")),center.add(new Effect.Vec(1,0,0)));
                VfxClient.INSTANCE.play(new Project(Templates.create("projectile_trail")),center.add(new Effect.Vec(-1,0,0)));
                state=3;ticks=0;
            } else if(state==3&&ticks==100) {
                if(VfxClient.INSTANCE.playbacks().size()!=3) throw new IllegalStateException("Expected three live world effects.");
                capture("studio-world.png");LOG.info("XIRC_SMOKE_WORLD_RENDERED: 3 active effects; particle count={}",VfxClient.INSTANCE.playbacks().stream().mapToInt(p->p.simulation().particles().size()).sum());
            } else if(state==3&&ticks==105) {
                var center=VfxClient.vec(mc.player.position()).add(new Effect.Vec(0,1,4));
                VfxClient.INSTANCE.play(new Project(Templates.create("pixel_slash")),center);
                VfxClient.INSTANCE.play(new Project(Templates.create("mesh_sphere")),center.add(new Effect.Vec(-2,0,0)));
                var style=new com.xirctwerk.vfx.api.Vfx.Style(new Effect.Color(1,.4,.1,.8),1,1.2);
                com.xirctwerk.vfx.api.Vfx.play(()->center.add(new Effect.Vec(2,0,0)),10,42,style,(f,c)->Geometry.create("cube_mesh",f.origin(),new Effect.Vec(0,0,1),.5,.1,f.ageSeconds()*90,1).forEach(q->c.quad(q,style.color())));
                trail=new com.xirctwerk.vfx.api.RibbonTrail();
            } else if(state==3&&ticks>105&&ticks<155) {
                var center=VfxClient.vec(mc.player.position()).add(new Effect.Vec(0,2,4));double angle=ticks*.12;
                trail.capture(center.add(new Effect.Vec(Math.cos(angle),0,Math.sin(angle))),center.add(new Effect.Vec(2*Math.cos(angle),.5,2*Math.sin(angle))),new com.xirctwerk.vfx.api.RibbonTrail.Profile(600,60,new Effect.Color(.02,.1,.3,1),new Effect.Color(.1,.5,1,1),new Effect.Color(.8,1,1,1),.85));
                if(ticks==142) {capture("studio-geometry.png");LOG.info("XIRC_SMOKE_GEOMETRY_RENDERED");}
            } else if(state==3&&ticks==160) {mc.setScreen(new StudioScreen());((StudioScreen)mc.screen).smokeEditor(false);((StudioScreen)mc.screen).smokeWorldPreview();state=4;ticks=0;}
            else if(state==4&&ticks==40) {
                if(VfxClient.INSTANCE.editorSimulation().particles().isEmpty()) throw new IllegalStateException("World preview emitted no particles");
                capture("studio-world-preview.png");
            } else if(state==4&&ticks==45&&mc.screen instanceof StudioScreen screen)screen.smokeWorldSeek(false);
            else if(state==4&&ticks==50&&mc.screen instanceof StudioScreen screen){screen.smokeWorldSeek(true);LOG.info("XIRC_SMOKE_WORLD_SCRUB_PASSED");}
            else if(state==4&&ticks==54&&mc.screen instanceof StudioScreen screen){screen.smokePlacement();LOG.info("XIRC_SMOKE_PIVOT_PLACEMENT_PASSED");}
            else if(state==4&&ticks==58&&mc.screen instanceof StudioScreen screen){screen.smokePlacementFinish();capture("studio-world-pivot.png");}
            else if(state==4&&ticks==60) {
                ((StudioScreen)mc.screen).smokeWorldFinish();LOG.info("XIRC_SMOKE_WORLD_PREVIEW_PASSED");
                VfxClient.INSTANCE.stop();mc.setScreen(new StudioScreen());((StudioScreen)mc.screen).smokeAdvanced(0);((StudioScreen)mc.screen).smokeWorldPreview();((StudioScreen)mc.screen).smokeAdvanced(0);state=5;ticks=0;
            }
            else if(state==5&&ticks>0&&ticks<560&&ticks%70==0&&mc.screen instanceof StudioScreen screen)screen.smokeAdvanced(ticks/70);
            else if(state==5&&ticks<560&&ticks%70==22) {if(VfxClient.INSTANCE.editorSimulation().particles().isEmpty())throw new IllegalStateException("Advanced world preset emitted no particles");capture("world-advanced-"+Templates.ADVANCED_NAMES.get(ticks/70)+".png");}
            else if(state==5&&ticks==310&&mc.screen instanceof StudioScreen screen)screen.smokeLayerAudition(0);
            else if(state==5&&ticks==312&&mc.screen instanceof StudioScreen screen)screen.smokeLayerAudition(1);
            else if(state==5&&ticks==314&&mc.screen instanceof StudioScreen screen)screen.smokeLayerAudition(2);
            else if(state==5&&ticks==316&&mc.screen instanceof StudioScreen screen)screen.smokeLayerAudition(3);
            else if(state==5&&ticks==318&&mc.screen instanceof StudioScreen screen)screen.smokeLayerAudition(4);
            else if(state==5&&ticks==322&&mc.screen instanceof StudioScreen screen){screen.smokeLayerAudition(5);LOG.info("XIRC_SMOKE_LAYER_AUDITION_PASSED");}
            else if(state==5&&ticks==570) {if(BloomRenderer.frames()==0)throw new IllegalStateException("Bloom post pass never rendered");LOG.info("XIRC_SMOKE_BLOOM_PASSED: {} frames",BloomRenderer.frames());LOG.info("XIRC_SMOKE_ADVANCED_PRESETS_PASSED");((StudioScreen)mc.screen).smokeWorldFinish();mc.setScreen(new StudioScreen());((StudioScreen)mc.screen).smokeMotion(0);state=6;ticks=0;}
            else if(state==6&&ticks==6&&mc.screen instanceof StudioScreen screen)screen.smokeMotion(1);
            else if(state==6&&ticks==8&&mc.screen instanceof StudioScreen screen)screen.smokeMotion(2);
            else if(state==6&&ticks==10&&mc.screen instanceof StudioScreen screen)screen.smokeMotion(3);
            else if(state==6&&ticks==12&&mc.screen instanceof StudioScreen screen)screen.smokeMotion(4);
            else if(state==6&&ticks==14&&mc.screen instanceof StudioScreen screen){capture("studio-path-editor.png");screen.smokeMotion(5);}
            else if(state==6&&ticks==16&&mc.screen instanceof StudioScreen screen)screen.smokeDiagnostics(0);
            else if(state==6&&ticks==18&&mc.screen instanceof StudioScreen screen){screen.smokeDiagnostics(1);capture("studio-budget-warnings.png");}
            else if(state==6&&ticks==19&&mc.screen instanceof StudioScreen screen)screen.smokeDiagnostics(2);
            else if(state==6&&ticks==20)capture("studio-opacity-heatmap.png");
            else if(state==6&&ticks==21&&mc.screen instanceof StudioScreen screen)screen.smokeDiagnostics(3);
            else if(state==6&&ticks==22)capture("studio-range-heatmap.png");
            else if(state==6&&ticks==23&&mc.screen instanceof StudioScreen screen)screen.smokeDiagnostics(4);
            else if(state==6&&ticks==24)capture("studio-speed-heatmap.png");
            else if(state==6&&ticks==25&&mc.screen instanceof StudioScreen screen)screen.smokeMotion(6);
            else if(state==6&&ticks==28&&mc.screen instanceof StudioScreen screen)screen.smokeDiagnostics(5);
            else if(state==6&&ticks==30)capture("studio-layer-contribution.png");
            else if(state==6&&ticks==32&&mc.screen instanceof StudioScreen screen)screen.smokeDiagnostics(6);
            else if(state==6&&ticks==40)capture("studio-spline-serpent.png");
            else if(state==6&&ticks==45&&mc.screen instanceof StudioScreen screen)screen.smokeMotion(7);
            else if(state==6&&ticks==80&&mc.screen instanceof StudioScreen screen){screen.smokeMotion(8);capture("world-spline-serpent.png");}
            else if(state==6&&ticks==90&&mc.screen instanceof StudioScreen screen){screen.smokeMotion(9);LOG.info("XIRC_SMOKE_MOTION_PASSED");mc.setScreen(new StudioScreen());((StudioScreen)mc.screen).smokeTransforms(0);state=8;ticks=0;}
            else if(state==8&&ticks==6&&mc.screen instanceof StudioScreen screen)screen.smokeTransforms(10);
            else if(state==8&&ticks==8&&mc.screen instanceof StudioScreen screen)screen.smokeTransforms(11);
            else if(state==8&&ticks==10&&mc.screen instanceof StudioScreen screen)screen.smokeTransforms(12);
            else if(state==8&&ticks==14&&mc.screen instanceof StudioScreen screen)screen.smokeTransforms(1);
            else if(state==8&&ticks==16&&mc.screen instanceof StudioScreen screen)screen.smokeTransforms(2);
            else if(state==8&&ticks==18&&mc.screen instanceof StudioScreen screen)screen.smokeTransforms(3);
            else if(state==8&&ticks==20&&mc.screen instanceof StudioScreen screen)screen.smokeTransforms(4);
            else if(state==8&&ticks==22)capture("studio-transform-editor.png");
            else if(state==8&&ticks==26&&mc.screen instanceof StudioScreen screen)screen.smokeTransforms(5);
            else if(state==8&&ticks==30)capture("studio-prismatic-orrery.png");
            else if(state==8&&ticks==35&&mc.screen instanceof StudioScreen screen)screen.smokeTransforms(6);
            else if(state==8&&ticks==60&&mc.screen instanceof StudioScreen screen){screen.smokeTransforms(7);capture("world-prismatic-orrery.png");}
            else if(state==8&&ticks==70&&mc.screen instanceof StudioScreen screen){screen.smokeTransforms(8);LOG.info("XIRC_SMOKE_TRANSFORMS_PASSED");cloudFixture();state=7;ticks=0;}
            else if(state==7) {
                mc.player.getAbilities().flying=true;mc.player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);mc.player.setPos(0,200,0);mc.player.setYRot(0);mc.player.setXRot(25);
                if(ticks==1||ticks==61||ticks==121){mc.options.graphicsMode().set(ticks==121?GraphicsStatus.FABULOUS:GraphicsStatus.FANCY);mc.options.cloudStatus().set(ticks==1?CloudStatus.FAST:CloudStatus.FANCY);mc.levelRenderer.allChanged();}
                if(ticks==50||ticks==110||ticks==170){
                    String mode=ticks==50?"fast":ticks==110?"fancy":"fabulous";
                    try(var pixels=Screenshot.takeScreenshot(mc.getMainRenderTarget())){int color=pixels.getPixelRGBA(pixels.getWidth()/2,pixels.getHeight()/2);int r=color&255,g=(color>>>8)&255,b=(color>>>16)&255;if(r<180||b<180||g>60)throw new IllegalStateException("Foreground VFX/cloud composite failed in "+mode+": "+Integer.toHexString(color));}
                    capture("world-clouds-"+mode+".png");LOG.info("XIRC_SMOKE_CLOUD_{}_PASSED",mode.toUpperCase(java.util.Locale.ROOT));
                }
                if(ticks==180){LOG.info("XIRC_SMOKE_CLOUDS_PASSED");LOG.info("XIRC_SMOKE_COMPLETE");mc.stop();state=8;}
            }
        } catch(Exception e) {if(mc.screen instanceof StudioScreen screen)screen.restoreSmokeClipboard();LOG.error("XIRC_SMOKE_FAILED",e);mc.stop();state=4;}
    }
    private static void cloudFixture(){
        var mc=Minecraft.getInstance();mc.setScreen(null);VfxClient.INSTANCE.stop();com.xirctwerk.vfx.api.Vfx.clear();mc.options.hideGui=true;
        mc.player.getAbilities().flying=true;mc.player.setPos(0,200,0);mc.player.setYRot(0);mc.player.setXRot(25);
        var eye=mc.player.getEyePosition();var center=VfxClient.vec(eye.add(mc.player.getViewVector(1).scale(8)));
        var color=new Effect.Color(1,0,1,1);var style=new com.xirctwerk.vfx.api.Vfx.Style(color,1);
        com.xirctwerk.vfx.api.Vfx.play(()->center,30,0,style,(frame,canvas)->canvas.quad(new Geometry.Quad(center.add(new Effect.Vec(-1,-1,0)),center.add(new Effect.Vec(-1,1,0)),center.add(new Effect.Vec(1,1,0)),center.add(new Effect.Vec(1,-1,0))),color));
    }
    private static void photonFixture() throws Exception {
        var json=com.google.gson.JsonParser.parseString("{\"_version\":1,\"fx\":{\"mainFX\":{\"fxObjects\":[{\"_type\":\"particle\",\"config\":{\"duration\":40,\"emission\":{\"emissionRate\":{\"_type\":\"Constant\",\"number\":2}}}}]},\"subFXs\":{}}}");
        try(var out=new java.io.DataOutputStream(new java.util.zip.GZIPOutputStream(java.nio.file.Files.newOutputStream(VfxClient.INSTANCE.files.root().resolve("imports/smoke_reference.fx"))))) {out.writeByte(10);out.writeUTF("");nbt(out,json);}
    }
    private static void nbt(java.io.DataOutputStream out,com.google.gson.JsonElement e) throws Exception {
        if(e.isJsonObject()){for(var entry:e.getAsJsonObject().entrySet()){var value=entry.getValue();out.writeByte(value.isJsonObject()?10:value.isJsonArray()?9:value.getAsJsonPrimitive().isString()?8:6);out.writeUTF(entry.getKey());nbt(out,value);}out.writeByte(0);}
        else if(e.isJsonArray()){out.writeByte(10);out.writeInt(e.getAsJsonArray().size());for(var item:e.getAsJsonArray())nbt(out,item);}
        else if(e.getAsJsonPrimitive().isString())out.writeUTF(e.getAsString());else out.writeDouble(e.getAsDouble());
    }
    private static void studioRoundTrip() throws Exception {
        var studio=VfxClient.INSTANCE;var json=Templates.create("magic_aura");studio.files.save("smoke_roundtrip",json);
        if(!studio.files.load("smoke_roundtrip").equals(json)) throw new IllegalStateException("Save/load mismatch.");
        studio.files.export("smoke_package",json,studio::resource);studio.importPackage("smoke_package");studio.checkResources(EffectCodec.compile(studio.files.load("smoke_package")));
    }
    private static void capture(String file) {var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,file,mc.getMainRenderTarget(),message->LOG.info("Screenshot {}: {}",file,message.getString()));}
}
