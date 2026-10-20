package com.xirctwerk.vfx.client;

import com.xirctwerk.vfx.core.*;
import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.*;
import imgui.ImGui;
import imgui.flag.*;
import imgui.type.*;

/** Native Dear ImGui editor; Project retains canonical JSON and validation. */
public final class StudioScreen extends Screen {
    private final VfxClient studio=VfxClient.INSTANCE;
    private Project project=new Project(Templates.create("magic_aura"));
    private Simulation preview=new Simulation(project.effect());
    private final NativeWorkspace nativeUi=new NativeWorkspace();
    private int selected,previewDrag=-1,px,py,pw,ph;
    private double yaw=.6,pitch=.35,zoom=1,panX,panY;
    private boolean paused,worldPreview,previewLoop=true;
    private int background;
    private double seekTarget=-1;
    private Effect.Vec worldPosition;
    private double pressX,pressY,dragDistance;
    private Effect.Vec worldOffset=new Effect.Vec(0,1,3);
    private String status="";
    private boolean statusError;
    private final Set<String> mutedLayers=new HashSet<>(),soloLayers=new HashSet<>();
    private final Set<String> selection=new LinkedHashSet<>();
    private Set<String> isolatedMute,isolatedSolo;
    private record View(double yaw,double pitch,double zoom,double panX,double panY){}
    private final Map<String,View> bookmarks=new LinkedHashMap<>();
    private double playbackSpeed=1,playbackCarry;
    private int diagnosticView,recoveryTicks;
    private boolean contributionView;
    private boolean showBounds;
    private String recoveredText="";
    private String auditionEffect=project.effect().id();
    private static final String[] GROUPS={"Appearance","Material","Transform","Emission","Motion","Shape / mesh","Collision","Sprite animation","Project"};
    public StudioScreen() {super(Component.literal(VfxClient.INSTANCE.editorTitle()));}
    @Override protected void init() {previewDrag=-1;}
    private void changed() {selected=Math.max(0,Math.min(selected,project.effect().emitters().size()-1));seekTarget=-1;if(!auditionEffect.equals(project.effect().id())){mutedLayers.clear();soloLayers.clear();auditionEffect=project.effect().id();}preview=new Simulation(project.effect());preview.setMuted(auditionMuted());if(worldPreview) {studio.stopEditorPreview();attempt(this::syncWorldPreview);}}
    private Set<String> auditionMuted() {var ids=new HashSet<String>();var live=new HashSet<String>();for(var e:project.effect().emitters())live.add(e.id());soloLayers.retainAll(live);mutedLayers.retainAll(live);for(String id:live)if(mutedLayers.contains(id)||(!soloLayers.isEmpty()&&!soloLayers.contains(id)))ids.add(id);return ids;}
    private void applyAudition() {var ids=auditionMuted();preview.setMuted(ids);if(worldPreview&&studio.editorSimulation()!=null)studio.editorSimulation().setMuted(ids);}
    private void message(String text,boolean error) {status=text;statusError=error;}
    private void history(boolean redo) {attempt(()->{nativeUi.finishGesture();if(redo?project.redo():project.undo()) {selected=Math.min(selected,project.effect().emitters().size()-1);nativeUi.strings.clear();changed();}});}
    private void syncWorldPreview() throws Exception {if(worldPosition==null)studio.previewBesidePlayer(project.effect(),this,worldOffset,()->paused||playbackSpeed!=1);else studio.previewWorld(project.effect(),this,worldPosition,()->paused||playbackSpeed!=1);studio.editorSimulation().setMuted(auditionMuted());}
    private void placeWorld(double x,double y) throws Exception {
        var camera=minecraft.gameRenderer.getMainCamera();double tan=Math.tan(Math.toRadians(minecraft.options.fov().get())/2);
        var ray=new org.joml.Vector3f((float)((2*x/width-1)*tan*width/height),(float)((1-2*y/height)*tan),-1).rotate(camera.rotation()).normalize();
        var eye=camera.getPosition();var end=eye.add(ray.x*128,ray.y*128,ray.z*128);
        var hit=minecraft.level.clip(new net.minecraft.world.level.ClipContext(eye,end,net.minecraft.world.level.ClipContext.Block.OUTLINE,net.minecraft.world.level.ClipContext.Fluid.NONE,minecraft.player));
        if(hit.getType()==net.minecraft.world.phys.HitResult.Type.MISS){message("Point at a block within 128 blocks to place the pivot.",false);return;}
        var normal=hit.getDirection().getNormal();var point=hit.getLocation().add(normal.getX()*.025,normal.getY()*.025,normal.getZ()*.025);
        double previousTime=viewedSimulation().time();worldPosition=VfxClient.vec(point);studio.stopEditorPreview();syncWorldPreview();if(paused)seekTarget=previousTime;message("Placed effect pivot. Right drag to look; left click a block to move it.",false);
    }
    private void toggleWorldPreview() {seekTarget=-1;attempt(()->{if(worldPreview) {studio.stopEditorPreview();worldPreview=false;}else {if(minecraft.player==null) throw new IllegalStateException("Join a world first.");double angle=Math.toRadians(minecraft.player.getYRot());worldOffset=new Effect.Vec(-Math.sin(angle)*3-Math.cos(angle)*.7,1,Math.cos(angle)*3-Math.sin(angle)*.7);syncWorldPreview();worldPreview=true;}});}
    private double previewHorizon() {return Math.min(631,project.effect().duration()+project.effect().emitters().stream().mapToDouble(e->e.lifetime().max()).max().orElse(0));}
    private Simulation viewedSimulation() {return worldPreview&&studio.editorSimulation()!=null?studio.editorSimulation():preview;}
    private void seek(double seconds) {nativeUi.finishGesture();paused=true;changed();seekTarget=Math.max(0,Math.min(previewHorizon(),Math.round(seconds/Simulation.DT)*Simulation.DT));}
    private void timelineChanged(){double time=viewedSimulation().time();changed();if(paused)seekTarget=time;}
    private Set<String> selectedIds(){var ids=new LinkedHashSet<String>();for(var emitter:project.effect().emitters())ids.add(emitter.id());selection.retainAll(ids);String active=project.effect().emitters().get(Math.min(selected,project.effect().emitters().size()-1)).id();if(!selection.contains(active)){selection.clear();selection.add(active);}var ordered=new LinkedHashSet<String>();ordered.add(active);ordered.addAll(selection);return Collections.unmodifiableSet(ordered);}
    private void selectLayers(Set<String> ids){selected=Math.max(0,Math.min(selected,project.effect().emitters().size()-1));nativeUi.finishGesture();selection.clear();selection.addAll(ids);selection.retainAll(Authoring.ids(project.json()));if(selection.isEmpty())selection.add(project.effect().emitters().getFirst().id());if(!selection.contains(project.effect().emitters().get(selected).id())){String first=selection.iterator().next();for(int i=0;i<project.effect().emitters().size();i++)if(project.effect().emitters().get(i).id().equals(first)){selected=i;break;}}nativeUi.strings.clear();}
    private void selectLayer(int index,boolean extend){var ids=new LinkedHashSet<>(selectedIds());String id=project.effect().emitters().get(index).id();if(!extend)ids.clear();if(extend&&ids.contains(id)&&ids.size()>1)ids.remove(id);else{ids.add(id);selected=index;}selectLayers(ids);}
    private void toolsEdit(java.util.function.Consumer<JsonObject> change,boolean continuous){if(!continuous)nativeUi.finishGesture();else if(!nativeUi.gesture.equals("tools")){nativeUi.finishGesture();project.beginEdit();nativeUi.gesture="tools";}project.edit(change);selected=Math.min(selected,project.effect().emitters().size()-1);nativeUi.strings.clear();changed();}
    private void toolsCommand(String command){attempt(()->{
        var ids=selectedIds();
        if(command.equals("duplicate")){final Set<String> copies=new LinkedHashSet<>();toolsEdit(o->copies.addAll(Authoring.duplicate(o,ids)),false);selectLayers(copies);}
        else if(command.equals("delete")){toolsEdit(o->Authoring.remove(o,ids),false);selection.clear();}
        else if(command.equals("isolate")){if(isolatedMute==null){isolatedMute=Set.copyOf(mutedLayers);isolatedSolo=Set.copyOf(soloLayers);mutedLayers.clear();soloLayers.clear();soloLayers.addAll(ids);}else{mutedLayers.clear();mutedLayers.addAll(isolatedMute);soloLayers.clear();soloLayers.addAll(isolatedSolo);isolatedMute=isolatedSolo=null;}applyAudition();}
        else if(command.equals("bounds"))showBounds=!showBounds;
        else if(command.equals("contribution"))contributionView=!contributionView;
        else if(command.startsWith("heatmap:"))diagnosticView=switch(command.substring(8)){case "opacity"->3;case "speed"->4;case "range"->5;default->2;};
        else if(command.equals("normal")||command.equals("silhouette")||command.equals("heatmap"))diagnosticView=command.equals("normal")?0:command.equals("silhouette")?1:2;
        else if(command.startsWith("bookmark:")){if(bookmarks.size()>=32&&!bookmarks.containsKey(command.substring(9)))throw new IllegalArgumentException("Keep at most 32 camera bookmarks.");bookmarks.put(command.substring(9),new View(yaw,pitch,zoom,panX,panY));}
        else if(command.startsWith("recall:")){var v=bookmarks.get(command.substring(7));if(v!=null){yaw=v.yaw;pitch=v.pitch;zoom=v.zoom;panX=v.panX;panY=v.panY;}}
        else if(command.startsWith("view:")){String view=command.substring(5);yaw=view.equals("side")?Math.PI/2:view.equals("isometric")?.6:0;pitch=view.equals("top")?Math.PI/2:view.equals("isometric")?.35:0;panX=panY=0;}
    });}
    @Override public void tick() {
        if(++recoveryTicks>=600){recoveryTicks=0;String text=project.text();if(!text.equals(recoveredText))attempt(()->{studio.files.checkpoint(project.json(),true);recoveredText=text;});}
        if(seekTarget>=0){if(worldPreview)studio.advanceEditorPreview(seekTarget,100);else for(int i=0;i<100&&preview.time()+Simulation.DT/2<seekTarget;i++)preview.tick(Effect.Vec.ZERO,Simulation.NO_COLLISION);if(viewedSimulation().time()+Simulation.DT/2>=seekTarget)seekTarget=-1;return;}
        if(!paused){playbackCarry+=playbackSpeed;int steps=Math.min(4,(int)playbackCarry);playbackCarry-=steps;if(!worldPreview){for(int i=0;i<steps;i++)preview.tick(Effect.Vec.ZERO,Simulation.NO_COLLISION);if(preview.finished()&&previewLoop)changed();}else if(playbackSpeed!=1&&studio.editorSimulation()!=null){if(studio.editorSimulation().finished()&&previewLoop)changed();studio.advanceEditorPreview(viewedSimulation().time()+steps*Simulation.DT,steps);}}}
    @Override public boolean isPauseScreen() {return false;}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float delta) {}
    @Override public void render(GuiGraphics g,int x,int y,float delta) {g.flush();nativeUi.backend.frame(nativeUi::draw);}
    @Override public void onClose() {super.onClose();}
    @Override public void removed() {nativeUi.finishGesture();nativeUi.backend.release();studio.stopEditorPreview();worldPreview=false;previewDrag=-1;}
    private boolean inPreview(double x,double y) {return (nativeUi.hovered||Boolean.getBoolean("xirc.smokeTest"))&&x>=px&&x<px+pw&&y>=py&&y<py+ph;}
    @Override public boolean mouseClicked(double x,double y,int button) {nativeUi.backend.mouse(button,true);if((button==0||button==1)&&inPreview(x,y)) {previewDrag=button;pressX=x;pressY=y;dragDistance=0;}return true;}
    @Override public boolean mouseReleased(double x,double y,int button) {nativeUi.backend.mouse(button,false);if(worldPreview&&button==0&&previewDrag==0&&dragDistance<4&&inPreview(x,y))attempt(()->placeWorld(x,y));if(previewDrag==button) previewDrag=-1;return true;}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy) {if(previewDrag==button) {dragDistance=Math.max(dragDistance,Math.hypot(x-pressX,y-pressY));if(worldPreview&&minecraft.player!=null) {minecraft.player.setYRot(minecraft.player.getYRot()+(float)(dx*nativeUi.inputScale*.15));minecraft.player.setXRot(Math.max(-89,Math.min(89,minecraft.player.getXRot()+(float)(dy*nativeUi.inputScale*.15))));minecraft.player.yRotO=minecraft.player.getYRot();minecraft.player.xRotO=minecraft.player.getXRot();}else if(button==0) {yaw-=dx*nativeUi.inputScale*.012;pitch=Math.max(-1.3,Math.min(1.3,pitch+dy*nativeUi.inputScale*.012));}else {panX+=dx;panY+=dy;}}return true;}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical) {if(inPreview(x,y)) {if(!worldPreview) zoom=Math.max(.001,Math.min(4,zoom*Math.pow(1.12,vertical)));}else nativeUi.backend.wheel(horizontal,vertical);return true;}
    @Override public boolean keyPressed(int key,int scan,int mods) {
        if((mods&GLFW.GLFW_MOD_CONTROL)!=0&&key==GLFW.GLFW_KEY_P&&!nativeUi.jsonActive){nativeUi.tools.open.set(true);return true;}
        if((nativeUi.jsonActive||nativeUi.jsonFocused)&&(mods&GLFW.GLFW_MOD_CONTROL)!=0&&(key==GLFW.GLFW_KEY_Z||key==GLFW.GLFW_KEY_Y)){nativeUi.textHistory(key==GLFW.GLFW_KEY_Y||(mods&GLFW.GLFW_MOD_SHIFT)!=0);return true;}
        nativeUi.backend.key(key,scan,GLFW.GLFW_PRESS,mods);if(nativeUi.propertyEditor.isOpen()) {if(key==GLFW.GLFW_KEY_ESCAPE) nativeUi.propertyEditor.cancel();return true;}if((mods&GLFW.GLFW_MOD_CONTROL)!=0&&nativeUi.jsonActive){
            if(key==GLFW.GLFW_KEY_ENTER||key==GLFW.GLFW_KEY_KP_ENTER){attempt(nativeUi::applyJson);return true;}
            if(key==GLFW.GLFW_KEY_F&&(mods&GLFW.GLFW_MOD_SHIFT)!=0){attempt(nativeUi::formatJson);return true;}
            if(key!=GLFW.GLFW_KEY_S&&key!=GLFW.GLFW_KEY_N)return true;
        }
        if((mods&GLFW.GLFW_MOD_CONTROL)!=0&&key==GLFW.GLFW_KEY_N){nativeUi.modal="New project";return true;}
        if((mods&GLFW.GLFW_MOD_CONTROL)!=0&&key==GLFW.GLFW_KEY_S) {attempt(nativeUi::requestSave);return true;}if(!nativeUi.backend.textInput()) {if(key==GLFW.GLFW_KEY_ESCAPE) {boolean[] popup={false};nativeUi.backend.withContext(()->popup[0]=ImGui.isPopupOpen("",ImGuiPopupFlags.AnyPopupId|ImGuiPopupFlags.AnyPopupLevel));if(!popup[0]) onClose();}else if((mods&GLFW.GLFW_MOD_CONTROL)!=0&&(key==GLFW.GLFW_KEY_Z||key==GLFW.GLFW_KEY_Y)) history(key==GLFW.GLFW_KEY_Y||(mods&GLFW.GLFW_MOD_SHIFT)!=0);else if(key==GLFW.GLFW_KEY_SPACE) paused=!paused;}return true;}
    @Override public boolean keyReleased(int key,int scan,int mods) {nativeUi.backend.key(key,scan,GLFW.GLFW_RELEASE,mods);return true;}
    @Override public boolean charTyped(char c,int mods) {nativeUi.backend.character(c);return true;}
    void smokeEditor(boolean advanced) {nativeUi.finishGesture();project=new Project(Templates.create("magic_aura"));selected=0;nativeUi.strings.clear();nativeUi.jsonOpen=advanced;nativeUi.focusJson=advanced;nativeUi.jsonBase=project.text();nativeUi.json.set(nativeUi.jsonBase);changed();}
    void smokeAdvanced(int index) {nativeUi.finishGesture();project.replace(Templates.create(Templates.ADVANCED_NAMES.get(index)));selected=0;nativeUi.strings.clear();nativeUi.jsonOpen=false;resetView();zoom=.65;if(worldPreview)worldOffset=new Effect.Vec(0,1,6);changed();if(worldPreview)resetView();}
    private JsonObject photonSmokeBefore;
    private JsonObject auditionSmokeBefore;
    void smokeLayerAudition(int stage) {
        var sim=studio.editorSimulation();String first=project.effect().emitters().getFirst().id();
        if(stage==0){auditionSmokeBefore=project.json();nativeUi.backend.testPointer(nativeUi.soloX,nativeUi.soloY,true);}
        else if(stage==1)nativeUi.backend.testPointer(nativeUi.soloX,nativeUi.soloY,false);
        else if(stage==2){if(sim.particles().isEmpty()||sim.particles().stream().anyMatch(p->!p.emitter.id().equals(first)))throw new IllegalStateException("Solo did not isolate the layer");nativeUi.backend.testPointer(nativeUi.muteX,nativeUi.muteY,true);}
        else if(stage==3)nativeUi.backend.testPointer(nativeUi.muteX,nativeUi.muteY,false);
        else if(stage==4){if(!sim.particles().isEmpty()||!project.json().equals(auditionSmokeBefore))throw new IllegalStateException("Mute or authored JSON preservation failed");mutedLayers.clear();soloLayers.clear();applyAudition();}
        else if(sim.particles().stream().noneMatch(p->!p.emitter.id().equals(first)))throw new IllegalStateException("Unmuting did not restore other layers");
    }
    void smokePhoton(int stage) throws Exception {
        if(stage==0) {photonSmokeBefore=project.json();var chosen=java.nio.file.Path.of(System.getProperty("xirc.photonSmokeFile",studio.files.root().resolve("imports/smoke_reference.fx").toString()));nativeUi.photonImport=studio.files.importPhotonPath(chosen,true);nativeUi.photonName=chosen.getFileName().toString();nativeUi.photonPath.set(chosen.toString());nativeUi.modal="Import Photon";}
        else if(stage==4){nativeUi.guideOpen.set(true);}
        else if(stage==5){nativeUi.guideOpen.set(false);}
        else if(stage==6){if(preview.particles().isEmpty())throw new IllegalStateException("Imported Photon effect never emitted");long visible=preview.particles().stream().filter(p->{var pos=project(p.position);return pos[0]>=px&&pos[0]<px+pw&&pos[1]>=py&&pos[1]<py+ph&&p.color().a()>.01;}).count();if(visible==0)throw new IllegalStateException("Imported particles are outside the fitted canvas");if(!project.undo()||!project.json().equals(photonSmokeBefore))throw new IllegalStateException("Photon import undo");changed();}
        else if(stage==1) {nativeUi.backend.withContext(()->{if(nativeUi.photonButtonY>=ImGui.getIO().getDisplaySizeY()-5)throw new IllegalStateException("Photon Open button is outside the display");});nativeUi.backend.testPointer(nativeUi.photonButtonX,nativeUi.photonButtonY,true);}
        else if(stage==2)nativeUi.backend.testPointer(nativeUi.photonButtonX,nativeUi.photonButtonY,false);
        else {if(!project.json().equals(nativeUi.photonImport.effect()))throw new IllegalStateException("Native Photon Open button did not apply the conversion");studio.files.save("smoke_photon",project.json());if(!studio.files.load("smoke_photon").equals(project.json()))throw new IllegalStateException("Photon JSON round-trip");}
    }
    void smokeWorkspace() {if(!nativeUi.ready||pw<50||ph<50) throw new IllegalStateException("Native workspace did not render");nativeUi.backend.withContext(()->{if(!ImGui.getIO().getFonts().isBuilt()||imgui.internal.ImGui.dockBuilderGetNode(nativeUi.dock).isNotValidPtr()) throw new IllegalStateException("Native font/dock initialization");});}
    long smokeRenderedFrames(){return nativeUi.backend.completedFrames();}
    void smokeInteractions() {double x=px+pw*.5,y=py+ph*.5,oldYaw=yaw;mouseClicked(x,y,0);mouseDragged(x+20,y,0,20,0);mouseReleased(x,y,0);if(yaw>=oldYaw) throw new IllegalStateException("Orbit direction");double oldX=panX;mouseClicked(x,y,1);mouseDragged(x+14,y,1,14,9);mouseReleased(x,y,1);if(panX!=oldX+14) throw new IllegalStateException("Pan direction");var before=project.json();tint(0x33aaff);var a=before.getAsJsonArray("emitters").get(0).getAsJsonObject().getAsJsonObject("color").getAsJsonArray("gradient");var b=project.json().getAsJsonArray("emitters").get(0).getAsJsonObject().getAsJsonObject("color").getAsJsonArray("gradient");for(int i=0;i<a.size();i++) if(!a.get(i).getAsJsonObject().get("time").equals(b.get(i).getAsJsonObject().get("time"))||!a.get(i).getAsJsonObject().getAsJsonArray("color").get(3).equals(b.get(i).getAsJsonObject().getAsJsonArray("color").get(3))) throw new IllegalStateException("Gradient preservation");project.replace(before);resetView();changed();}
    private JsonObject timelineSmokeBefore,timelineSmokeAdded;
    void smokeTimeline(int stage) {
        var timeline=nativeUi.timeline;
        if(stage==0){nativeUi.finishGesture();timelineSmokeBefore=project.json();paused=true;timeline.smokeOpen();}
        else if(stage==1)nativeUi.backend.testPointer(timeline.addX,timeline.addY,true);
        else if(stage==2)nativeUi.backend.testPointer(timeline.addX,timeline.addY,false);
        else if(stage==3){timelineSmokeAdded=project.json();if(timelineSmokeAdded.equals(timelineSmokeBefore))throw new IllegalStateException("Timeline Add key did not change the curve");nativeUi.backend.testPointer(timeline.keyX,timeline.keyY,true);}
        else if(stage==4)nativeUi.backend.testPointer(timeline.keyX+30,timeline.keyY,true);
        else if(stage==5)nativeUi.backend.testPointer(timeline.keyX,timeline.keyY,false);
        else if(stage==6){nativeUi.finishGesture();if(project.json().equals(timelineSmokeAdded))throw new IllegalStateException("Timeline diamond drag did not retime");if(!project.undo()||!project.json().equals(timelineSmokeAdded))throw new IllegalStateException("Timeline drag undo failed");if(!project.undo()||!project.json().equals(timelineSmokeBefore))throw new IllegalStateException("Timeline add undo failed");changed();seek(.75);}
        else if(stage==7){if(seekTarget>=0||Math.abs(viewedSimulation().time()-.75)>.001||!paused||!project.json().equals(timelineSmokeBefore))throw new IllegalStateException("Timeline scrub failed");}
        else if(stage==9)timeline.smokeColor();
        else if(stage==10)timeline.smokeMap();
        else {timeline.open.set(false);paused=false;changed();}
    }
    void smokeWorldSeek(boolean verify){if(!verify)seek(.75);else{if(seekTarget>=0||!paused||Math.abs(studio.editorSimulation().time()-.75)>.001)throw new IllegalStateException("World timeline scrub failed");paused=false;}}
    void smokeDrag(int stage) {nativeUi.smokeDrag(stage);}
    private JsonObject workflowBefore;private String workflowText,workflowClipboard;
    void smokeAuthoring(int stage){
        if(stage==0){nativeUi.finishGesture();workflowBefore=project.json();nativeUi.jsonBase=project.text();nativeUi.json.set(nativeUi.jsonBase);nativeUi.newName.set("smoke_new");nativeUi.modal="New project";}
        else if(stage==1)nativeUi.backend.testPointer(nativeUi.newProjectX,nativeUi.newProjectY,true);
        else if(stage==2)nativeUi.backend.testPointer(nativeUi.newProjectX,nativeUi.newProjectY,false);
        else if(stage==3){if(!project.effect().id().equals("xirc_vfx_studio:smoke_new")||GROUPS.length<9)throw new IllegalStateException("New project/categories failed");project.undo();if(!project.json().equals(workflowBefore))throw new IllegalStateException("New project undo");changed();nativeUi.jsonOpen=true;nativeUi.focusJson=true;nativeUi.jsonBase=project.text();nativeUi.json.set(nativeUi.jsonBase);workflowText=project.text();workflowClipboard=minecraft.keyboardHandler.getClipboard();}
        else if(stage==4)nativeUi.backend.testPointer(nativeUi.jsonTextX,nativeUi.jsonTextY,true);
        else if(stage==5)nativeUi.backend.testPointer(nativeUi.jsonTextX,nativeUi.jsonTextY,false);
        else if(stage==6){if(!nativeUi.jsonActive)throw new IllegalStateException("JSON editor did not acquire input focus");keyPressed(GLFW.GLFW_KEY_A,0,GLFW.GLFW_MOD_CONTROL);}
        else if(stage==7){keyReleased(GLFW.GLFW_KEY_A,0,GLFW.GLFW_MOD_CONTROL);keyPressed(GLFW.GLFW_KEY_C,0,GLFW.GLFW_MOD_CONTROL);}
        else if(stage==8){keyReleased(GLFW.GLFW_KEY_C,0,GLFW.GLFW_MOD_CONTROL);if(!minecraft.keyboardHandler.getClipboard().equals(workflowText))throw new IllegalStateException("JSON Ctrl+A/C failed");keyPressed(GLFW.GLFW_KEY_X,0,GLFW.GLFW_MOD_CONTROL);}
        else if(stage==9){keyReleased(GLFW.GLFW_KEY_X,0,GLFW.GLFW_MOD_CONTROL);if(!nativeUi.json.get().isEmpty())throw new IllegalStateException("JSON Ctrl+X failed");keyPressed(GLFW.GLFW_KEY_V,0,GLFW.GLFW_MOD_CONTROL);}
        else if(stage==10)keyReleased(GLFW.GLFW_KEY_V,0,GLFW.GLFW_MOD_CONTROL);
        else if(stage==11){if(!nativeUi.json.get().equals(workflowText))throw new IllegalStateException("JSON Ctrl+V failed");keyPressed(GLFW.GLFW_KEY_Z,0,GLFW.GLFW_MOD_CONTROL);}
        else if(stage==12)keyReleased(GLFW.GLFW_KEY_Z,0,GLFW.GLFW_MOD_CONTROL);
        else if(stage==13){if(!nativeUi.json.get().isEmpty()||!project.json().equals(workflowBefore))throw new IllegalStateException("JSON text undo affected project history");keyPressed(GLFW.GLFW_KEY_Y,0,GLFW.GLFW_MOD_CONTROL);}
        else if(stage==14)keyReleased(GLFW.GLFW_KEY_Y,0,GLFW.GLFW_MOD_CONTROL);
        else if(stage==15){if(!nativeUi.json.get().equals(workflowText))throw new IllegalStateException("JSON Ctrl+Y failed (active="+nativeUi.jsonActive+", focused="+nativeUi.jsonFocused+", draftLength="+nativeUi.json.get().length()+", historyLength="+nativeUi.draftHistory.current().length()+")");keyReleased(GLFW.GLFW_KEY_LEFT_CONTROL,0,0);restoreSmokeClipboard();}
        else {nativeUi.jsonOpen=false;nativeUi.jsonActive=false;nativeUi.backend.release();}
    }
    void restoreSmokeClipboard(){if(workflowClipboard!=null){minecraft.keyboardHandler.setClipboard(workflowClipboard);workflowClipboard=null;}}
    private JsonObject motionSmokeBefore;
    void smokeMotion(int stage){
        var path=nativeUi.tools.paths;
        if(stage==0){nativeUi.finishGesture();project.replace(Templates.create("spline_serpent"));selected=0;selection.clear();motionSmokeBefore=project.json();nativeUi.jsonOpen=false;nativeUi.tools.open.set(true);nativeUi.tools.requestedTab="Paths";paused=false;changed();resetView();fitView();}
        else if(stage==1)nativeUi.backend.testPointer(path.canvasX+path.canvasW*.2f,path.canvasY+path.canvasH*.75f,true);
        else if(stage==2)nativeUi.backend.testPointer(path.canvasX+path.canvasW*.5f,path.canvasY+path.canvasH*.2f,true);
        else if(stage==3)nativeUi.backend.testPointer(path.canvasX+path.canvasW*.8f,path.canvasY+path.canvasH*.7f,true);
        else if(stage==4)nativeUi.backend.testPointer(path.canvasX+path.canvasW*.8f,path.canvasY+path.canvasH*.7f,false);
        else if(stage==5){nativeUi.finishGesture();if(project.json().equals(motionSmokeBefore)||project.effect().emitters().getFirst().motion().path().size()<2)throw new IllegalStateException("Native path stroke failed (canvas="+path.canvasX+","+path.canvasY+" size="+path.canvasW+","+path.canvasH+")");if(!project.undo()||!project.json().equals(motionSmokeBefore))throw new IllegalStateException("Path stroke undo failed");changed();nativeUi.backend.release();}
        else if(stage==6){diagnosticView=0;nativeUi.tools.open.set(false);fitView();}
        else if(stage==7){worldOffset=new Effect.Vec(0,1,7);toggleWorldPreview();resetView();}
        else if(stage==8){if(studio.editorSimulation()==null||studio.editorSimulation().particles().isEmpty()||studio.editorSimulation().particles().stream().noneMatch(p->!p.emitter.motion().path().isEmpty())||!com.xirctwerk.vfx.api.Vfx.capabilities().contains(com.xirctwerk.vfx.api.Vfx.Capability.SPLINE_MOTION))throw new IllegalStateException("Motion world preview/capability query failed");}
        else onClose();
    }
    void smokeDiagnostics(int stage){
        if(stage==0)nativeUi.tools.smokeDiagnostics();
        else if(stage==1){if(nativeUi.tools.diagnosticFrames==0||BudgetWarnings.inspect(viewedSimulation(),new BudgetWarnings.Limits(1,1)).isEmpty())throw new IllegalStateException("Diagnostic warnings did not render");}
        else if(stage==5)contributionView=true;
        else if(stage==6)contributionView=false;
        else{nativeUi.tools.open.set(false);diagnosticView=stage==2?3:stage==3?5:4;}
    }
    private JsonObject transformSmokeBefore;
    void smokeTransforms(int stage){
        var editor=nativeUi.tools.transforms;
        if(stage==0){project.replace(Templates.create("prismatic_orrery"));selected=0;selection.clear();nativeUi.tools.open.set(true);nativeUi.tools.requestedTab="Transforms";nativeUi.jsonOpen=false;changed();resetView();fitView();seek(2);selectLayers(Set.of("satellite_0","satellite_1"));transformSmokeBefore=project.json();}
        else if(stage==10)nativeUi.backend.testPointer(editor.groupX,editor.groupY,true);
        else if(stage==11)nativeUi.backend.testPointer(editor.groupX,editor.groupY,false);
        else if(stage==12){nativeUi.finishGesture();if(project.json().getAsJsonArray("emitters").size()!=transformSmokeBefore.getAsJsonArray("emitters").size()+1)throw new IllegalStateException("Native transform grouping did not add a parent");if(!project.undo()||!project.json().equals(transformSmokeBefore))throw new IllegalStateException("Native grouping undo failed");selectLayers(Set.of("orrery_pivot"));changed();nativeUi.backend.release();}
        else if(stage==1){if(editor.frames==0)throw new IllegalStateException("Transform editor did not render");nativeUi.backend.testPointer(editor.scaleX,editor.scaleY,true);}
        else if(stage==2)nativeUi.backend.testPointer(editor.scaleX+30,editor.scaleY,true);
        else if(stage==3)nativeUi.backend.testPointer(editor.scaleX+30,editor.scaleY,false);
        else if(stage==4){nativeUi.finishGesture();if(project.json().equals(transformSmokeBefore))throw new IllegalStateException("Native transform scale did not edit");if(!project.undo()||!project.json().equals(transformSmokeBefore))throw new IllegalStateException("Transform edit undo was not atomic");changed();nativeUi.backend.release();}
        else if(stage==5){nativeUi.tools.open.set(false);seek(2);fitView();}
        else if(stage==6){toggleWorldPreview();worldOffset=new Effect.Vec(0,1,7);seek(2);}
        else if(stage==7){var sim=studio.editorSimulation();if(sim==null||Math.abs(sim.time()-2)>.001||sim.particles().stream().noneMatch(p->p.transformScale()>1.1)||sim.particles().stream().filter(p->Geometry.supports(p.emitter.kind())).noneMatch(p->p.direction().add(p.emitter.direction().scale(-1)).length()>.1))throw new IllegalStateException("Animated hierarchy did not reach world renderer");}
        else onClose();
    }
    private JsonObject toolsSmokeBefore,toolsSmokeFixture;
    void smokeTools(int stage){
        if(stage==0){nativeUi.finishGesture();toolsSmokeBefore=project.json();var fixture=Templates.newProject("tools_smoke");Authoring.duplicate(fixture,Authoring.ids(fixture));project.replace(fixture);toolsSmokeFixture=project.json();selected=0;selectLayers(Authoring.ids(fixture));nativeUi.tools.smokeOpen();changed();}
        else if(stage==1)nativeUi.backend.testPointer(nativeUi.tools.relativeX,nativeUi.tools.relativeY,true);
        else if(stage==2)nativeUi.backend.testPointer(nativeUi.tools.relativeX,nativeUi.tools.relativeY,false);
        else if(stage==3){nativeUi.finishGesture();if(project.effect().emitters().stream().anyMatch(e->Math.abs(e.size().min()-.25)>1e-5))throw new IllegalStateException("Tools native batch edit failed");if(!project.undo()||!project.json().equals(toolsSmokeFixture))throw new IllegalStateException("Batch edit undo was not atomic");changed();}
        else if(stage==4){nativeUi.tools.open.set(false);nativeUi.editProperty("size",Templates.curve(.1,1));nativeUi.propertyEditor.smokeEasing();}
        else if(stage==5)nativeUi.propertyEditor.cancel();
        else if(stage==7){project.replace(toolsSmokeFixture);selectLayers(Authoring.ids(project.json()));project.edit(o->Parameters.expose(o,selectedIds(),"power","size"));toolsSmokeFixture=project.json();nativeUi.tools.open.set(true);nativeUi.tools.requestedTab="Parameters";changed();}
        else if(stage==8)nativeUi.backend.testPointer(nativeUi.tools.parameterX,nativeUi.tools.parameterY,true);
        else if(stage==9)nativeUi.backend.testPointer(nativeUi.tools.parameterX+30,nativeUi.tools.parameterY,true);
        else if(stage==10)nativeUi.backend.testPointer(nativeUi.tools.parameterX+30,nativeUi.tools.parameterY,false);
        else if(stage==11){nativeUi.finishGesture();if(project.json().equals(toolsSmokeFixture)||project.effect().emitters().stream().anyMatch(e->Math.abs(e.size().min()-.15)<.01))throw new IllegalStateException("Native parameter control did not resolve bindings");if(!project.undo()||!project.json().equals(toolsSmokeFixture))throw new IllegalStateException("Parameter drag undo failed");}
        else{project.replace(toolsSmokeBefore);selection.clear();selected=0;nativeUi.tools.open.set(false);nativeUi.backend.release();changed();}
    }
    private double placementSmokeTime;
    void smokePlacement(){
        paused=true;placementSmokeTime=viewedSimulation().time();var before=project.json();double x=px+pw*.5,y=py+ph*.8;mouseClicked(x,y,0);mouseReleased(x,y,0);
        if(worldPosition==null||!worldPosition.equals(studio.editorAnchor())||!project.json().equals(before))throw new IllegalStateException("World click placement failed");
        var previous=minecraft.player.position();minecraft.player.setPos(previous.x+1,previous.y,previous.z);if(!worldPosition.equals(studio.editorAnchor()))throw new IllegalStateException("Placed pivot followed player");minecraft.player.setPos(previous.x,previous.y,previous.z);
    }
    void smokePlacementFinish(){if(seekTarget>=0||Math.abs(viewedSimulation().time()-placementSmokeTime)>.001||!paused)throw new IllegalStateException("Paused placement lost the preview time");}
    private JsonObject hintsSmokeBefore;private int hintsSmokeTips;
    void smokeHints(boolean verify){if(!verify){hintsSmokeBefore=project.json();hintsSmokeTips=EditorHints.tooltips();nativeUi.backend.testPointer(nativeUi.dragX,nativeUi.dragY,false);}else{if(EditorHints.ranges()==0||EditorHints.tooltips()<=hintsSmokeTips||!project.json().equals(hintsSmokeBefore))throw new IllegalStateException("Numeric bounds/tooltip rendering failed");nativeUi.backend.release();}}
    private JsonObject propertySmokeBefore;
    void smokeProperty(int stage) {
        var editor=nativeUi.propertyEditor;
        if(stage==0) {nativeUi.finishGesture();propertySmokeBefore=project.json();nativeUi.editProperty("size",propertySmokeBefore.getAsJsonArray("emitters").get(selected).getAsJsonObject().get("size"));editor.curve();}
        else if(stage==1) nativeUi.backend.testPointer(editor.pointX+2,editor.pointY,true);
        else if(stage==2) nativeUi.backend.testPointer(editor.pointX+40,editor.pointY-20,true);
        else if(stage==3) nativeUi.backend.testPointer(editor.pointX+40,editor.pointY-20,false);
        else if(stage==4) nativeUi.backend.testPointer(editor.applyX,editor.applyY,true);
        else if(stage==5) nativeUi.backend.testPointer(editor.applyX,editor.applyY,false);
        else {
            if(editor.isOpen()) throw new IllegalStateException("Visual property Apply failed");
            var after=project.json();var e=after.getAsJsonArray("emitters").get(selected).getAsJsonObject();
            if(!e.get("size").isJsonObject()) throw new IllegalStateException("Graphical curve was not applied");
            var points=e.getAsJsonObject("size").getAsJsonArray("curve");
            if(points.get(0).getAsJsonObject().get("value").getAsDouble()==points.get(1).getAsJsonObject().get("value").getAsDouble()) throw new IllegalStateException("Graph point did not move");
            e.remove("size");var old=propertySmokeBefore.deepCopy();old.getAsJsonArray("emitters").get(selected).getAsJsonObject().remove("size");
            if(!after.equals(old)) throw new IllegalStateException("Curve editor changed unrelated JSON");
            project.undo();if(!project.json().equals(propertySmokeBefore)) throw new IllegalStateException("Curve apply undo failed");nativeUi.backend.release();changed();
        }
    }
    private JsonObject burstSmokeBefore;
    void smokeGradient(boolean close) {
        if(close) nativeUi.propertyEditor.cancel();
        else nativeUi.editProperty("color",project.json().getAsJsonArray("emitters").get(selected).getAsJsonObject().get("color"));
    }
    void smokeBursts(int stage) {
        var editor=nativeUi.propertyEditor;
        if(stage==0) {burstSmokeBefore=project.json();nativeUi.editProperty("bursts",new JsonArray());}
        else if(stage==1) nativeUi.backend.testPointer(editor.addX,editor.addY,true);
        else if(stage==2) nativeUi.backend.testPointer(editor.addX,editor.addY,false);
        else if(stage==3) nativeUi.backend.testPointer(editor.applyX,editor.applyY,true);
        else if(stage==4) nativeUi.backend.testPointer(editor.applyX,editor.applyY,false);
        else {if(editor.isOpen()) throw new IllegalStateException("Burst Apply failed");var a=project.json().getAsJsonArray("emitters").get(selected).getAsJsonObject().getAsJsonArray("bursts");if(a==null||a.size()!=1||a.get(0).getAsJsonObject().get("count").getAsInt()!=10) throw new IllegalStateException("Native burst creation failed");project.undo();if(!project.json().equals(burstSmokeBefore)) throw new IllegalStateException("Burst undo failed");nativeUi.backend.release();changed();}
    }
    void smokeJsonSave() {
        var before=project.json();nativeUi.jsonBase=project.text();var draft=before.deepCopy();draft.addProperty("name","Draft edit");nativeUi.json.set(EffectCodec.GSON.toJson(draft));
        project.edit(o->o.addProperty("description","Inspector edit"));var inspector=project.json();String text=nativeUi.json.get();
        boolean rejected=false;try {nativeUi.requestSave();}catch(IllegalStateException expected) {rejected=true;}
        if(!rejected||!project.json().equals(inspector)||!nativeUi.json.get().equals(text)) throw new IllegalStateException("Divergent JSON draft was silently overwritten");
        nativeUi.jsonBase=project.text();draft=project.json();draft.addProperty("name","Draft edit");nativeUi.json.set(EffectCodec.GSON.toJson(draft));nativeUi.requestSave();
        if(!project.effect().name().equals("Draft edit")) throw new IllegalStateException("Save ignored a valid current JSON draft");
        nativeUi.jsonBase=project.text();nativeUi.json.set(nativeUi.jsonBase);nativeUi.strings.put("effect:author",new ImString("Typed author",2048));nativeUi.requestSave();
        if(!project.json().get("author").getAsString().equals("Typed author")) throw new IllegalStateException("Save ignored a focused text field");
        project=new Project(before);nativeUi.modal="";nativeUi.jsonBase=project.text();nativeUi.json.set(nativeUi.jsonBase);nativeUi.strings.clear();changed();
    }
    private interface Work {void run() throws Exception;}
    private void attempt(Work work) { try { work.run(); }catch(Exception e) { message(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage(),true); } }
    private void resetView() {
        yaw=.6;pitch=.35;zoom=1;panX=panY=0;
        if(worldPreview&&minecraft.player!=null) {
            minecraft.player.setYRot((float)Math.toDegrees(Math.atan2(-worldOffset.x(),worldOffset.z())));
            minecraft.player.setXRot((float)-Math.toDegrees(Math.atan2(worldOffset.y()-minecraft.player.getEyeHeight(),Math.hypot(worldOffset.x(),worldOffset.z()))));
            minecraft.player.yRotO=minecraft.player.getYRot();minecraft.player.xRotO=minecraft.player.getXRot();
        }
    }
    private void fitView() {
        if(worldPreview){resetView();return;}
        var bounds=preview.particles().isEmpty()?SceneBounds.authored(project.effect()):SceneBounds.live(preview.particles());
        double minX=Double.POSITIVE_INFINITY,maxX=Double.NEGATIVE_INFINITY,minY=Double.POSITIVE_INFINITY,maxY=Double.NEGATIVE_INFINITY;
        for(int i=0;i<8;i++){var p=new Effect.Vec((i&1)==0?bounds.min().x():bounds.max().x(),(i&2)==0?bounds.min().y():bounds.max().y(),(i&4)==0?bounds.min().z():bounds.max().z());double x=p.x()*Math.cos(yaw)-p.z()*Math.sin(yaw),y=p.y()*Math.cos(pitch)-depth(p)*Math.sin(pitch);minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);}
        double unit=Math.max(1,Math.min(pw,ph))*.22;zoom=Math.max(.001,Math.min(4,Math.min(Math.max(1,pw)*.8/Math.max(.1,maxX-minX),Math.max(1,ph)*.8/Math.max(.1,maxY-minY))/unit));panX=-(minX+maxX)/2*scale();panY=-ph*.2+(minY+maxY)/2*scale();
    }
    private void tint(int rgb) {
        var targets=selectedIds();project.edit(o->{for(String target:targets){var e=Authoring.layer(o,target);JsonElement old=e.get("color");
            if(old!=null&&old.isJsonObject()) {for(var k:old.getAsJsonObject().getAsJsonArray("gradient")) recolor(k.getAsJsonObject().getAsJsonArray("color"),rgb,true);}
            else {JsonArray c=old!=null?old.getAsJsonArray():JsonParser.parseString("[1,1,1,1]").getAsJsonArray();recolor(c,rgb,false);e.add("color",c);}
        }});changed();
    }
    private static void recolor(JsonArray color,int rgb,boolean preserveBrightness) {
        double light=preserveBrightness?Math.max(color.get(0).getAsDouble(),Math.max(color.get(1).getAsDouble(),color.get(2).getAsDouble())):1;
        for(int i=0;i<3;i++) color.set(i,new JsonPrimitive(((rgb>>(16-i*8))&255)/255.*light));
    }
    private static String defaultValue(String key) { return switch(key) {case "color"->"#B34DFF";case "size"->"0.1";case "rate"->"30";case "speed","lifetime","meshScale"->"1";case "spread"->"0.2";case "duration"->"5";case "direction","vortexAxis"->"[0,1,0]";case "radius"->"0.5";case "fade"->"0.3";case "force","offset","wind","attractor","orbitRadius"->"[0,0,0]";case "length"->"3";case "trailPoints"->"12";case "pixelSize"->"1";case "texture"->"xirc_vfx_studio:textures/effect/soft.png";case "bursts"->"[]";case "pivot"->"[0,0,0]";case "opacity","aspect","curlFrequency","falloff","pathCycles"->"1";case "spin"->"0";case "parent"->"";case "bounce"->"0.5";default->"0";}; }
    private double scale() { return Math.min(pw,ph)*.22*zoom; }
    private float displayPartial(){return paused?1:minecraft.getTimer().getGameTimeDeltaPartialTick(true);}
    private double depth(Effect.Vec p) { return p.x()*Math.sin(yaw)+p.z()*Math.cos(yaw); }
    private int[] project(Effect.Vec v) {
        double x=v.x()*Math.cos(yaw)-v.z()*Math.sin(yaw),z=depth(v),y=v.y()*Math.cos(pitch)-z*Math.sin(pitch);
        return new int[]{(int)(px+pw*.5+x*scale()+panX),(int)(py+ph*.7-y*scale()+panY)};
    }
    void smokeWorldPreview() {toggleWorldPreview();if(!worldPreview||studio.editorSimulation()==null) throw new IllegalStateException("World preview toggle failed");}
    void smokeWorldFinish() {
        float heading=minecraft.player.getYRot();double x=px+pw*.5,y=py+ph*.5;
        mouseClicked(x,y,1);mouseDragged(x+20,y,1,20,0);mouseReleased(x,y,1);
        if(minecraft.player.getYRot()<=heading) throw new IllegalStateException("World mouse look direction failed");
        var old=studio.editorSimulation();tint(0x33AAFF);
        if(old==studio.editorSimulation()||!studio.editorSimulation().effect().equals(project.effect())) throw new IllegalStateException("World preview did not update live");
        var restarted=studio.editorSimulation();changed();
        if(restarted==studio.editorSimulation()||studio.editorSimulation().time()!=0) throw new IllegalStateException("World restart failed");
        int count=studio.playbacks().size();toggleWorldPreview();
        if(studio.editorSimulation()!=null||studio.playbacks().size()!=count-1) throw new IllegalStateException("World preview cleanup failed");
        toggleWorldPreview();onClose();if(studio.editorSimulation()!=null) throw new IllegalStateException("Editor close left a preview running");
    }
    private void copyAgentPrompt() {minecraft.keyboardHandler.setClipboard("Author an effect for "+studio.editorTitle()+". Read AGENTS.md, schema/effect.schema.json and docs/AGENT_AUTHORING.md in "+studio.files.root()+". Preserve unrelated properties, curves, ranges and gradients. Write schemaVersion 1 JSON under effects and validate it. No executable shader/code imports. User request: ");message("Instructions copied for Codex or Claude Code.",false);}
    private final class NativeWorkspace {
        final StudioImGui backend=StudioImGui.get();
        final PropertyEditor propertyEditor=new PropertyEditor();
        final TimelineEditor timeline=new TimelineEditor();
        final EditorTools tools=new EditorTools();
        final TextHistory draftHistory=new TextHistory();
        boolean resetJsonInput;long lastDraftEdit;
        boolean ready,hovered,reset,jsonOpen,focusJson,jsonActive,jsonFocused;
        int jsonRevision;
        String jsonBase="";
        JsonObject savedJson;
        int dock,previewDock;
        float inputScale=1;
        JsonObject inspectorJson;
        String modal="";
        PhotonImporter.Result photonImport;String photonName="";boolean wireframe;float photonButtonX,photonButtonY,soloX,soloY,muteX,muteY;
        final ImBoolean substitutePhotonTextures=new ImBoolean(true);
        final ImBoolean guideOpen=new ImBoolean(false);
        final ImString photonPath=new ImString(4096);
        final ImString newName=new ImString("new_effect",128);
        float newProjectX,newProjectY,jsonTextX,jsonTextY;
        final ImString name=new ImString("my_effect",128),json=new ImString(EffectCodec.MAX_JSON_BYTES+1);
        final Map<String,ImString> strings=new HashMap<>();
        String gesture="";
        void layerList(){
            var json=project.json();var groups=new LinkedHashMap<String,List<Integer>>();
            for(int i=0;i<project.effect().emitters().size();i++){var layer=json.getAsJsonArray("emitters").get(i).getAsJsonObject();String folder=layer.has("folder")?layer.get("folder").getAsString():"";groups.computeIfAbsent(folder,k->new ArrayList<>()).add(i);}
            for(var group:groups.entrySet()){
                if(group.getKey().isEmpty()){for(int index:group.getValue())layerRow(index);continue;}
                var ids=new LinkedHashSet<String>();for(int index:group.getValue())ids.add(project.effect().emitters().get(index).id());
                ImGui.pushID("folder:"+group.getKey());boolean expanded=ImGui.collapsingHeader(group.getKey(),ImGuiTreeNodeFlags.DefaultOpen);if(ImGui.isItemClicked()&&ImGui.getIO().getKeyCtrl())selectLayers(ids);EditorHints.tip("Click to collapse. Ctrl-click selects the folder; right-click offers selection, Mute and Solo.");
                if(ImGui.beginPopupContextItem("Folder actions")){if(ImGui.menuItem("Select folder"))selectLayers(ids);if(ImGui.menuItem("Mute / unmute folder")){if(mutedLayers.containsAll(ids))mutedLayers.removeAll(ids);else mutedLayers.addAll(ids);applyAudition();}if(ImGui.menuItem("Solo / restore all")){if(soloLayers.equals(ids))soloLayers.clear();else{soloLayers.clear();soloLayers.addAll(ids);mutedLayers.removeAll(ids);}applyAudition();}ImGui.endPopup();}
                if(expanded){
                    for(int index:group.getValue())layerRow(index);
                }ImGui.popID();
            }
        }
        void layerRow(int index){
            var e=project.effect().emitters().get(index);if(ImGui.selectable(e.id()+"##"+index,selectedIds().contains(e.id())))selectLayer(index,ImGui.getIO().getKeyCtrl());
            EditorHints.tip("Click to select; Ctrl-click extends the selection. Inspector edits apply to selected layers atomically.");
            ImGui.pushID(e.id());var mute=new ImBoolean(mutedLayers.contains(e.id()));if(ImGui.checkbox((ImGui.getContentRegionAvailX()<150?"M":"Mute")+"###mute",mute)){if(mute.get())mutedLayers.add(e.id());else mutedLayers.remove(e.id());applyAudition();}
            if(index==0){muteX=(ImGui.getItemRectMinX()+ImGui.getItemRectMaxX())/2;muteY=(ImGui.getItemRectMinY()+ImGui.getItemRectMaxY())/2;}EditorHints.tip("Temporarily hide this layer in both previews. Saved JSON is unchanged.");ImGui.sameLine();
            var solo=new ImBoolean(soloLayers.contains(e.id()));if(ImGui.checkbox((ImGui.getContentRegionAvailX()<100?"S":"Solo")+"###solo",solo)){if(solo.get()){soloLayers.add(e.id());mutedLayers.remove(e.id());}else soloLayers.remove(e.id());applyAudition();}
            if(index==0){soloX=(ImGui.getItemRectMinX()+ImGui.getItemRectMaxX())/2;soloY=(ImGui.getItemRectMinY()+ImGui.getItemRectMaxY())/2;}EditorHints.tip("Preview only soloed layers. Multiple solos can play together.");ImGui.textDisabled(e.kind().replace('_',' '));ImGui.popID();
        }
        void drawBounds(float ratio){var box=SceneBounds.authored(project.effect());var min=box.min();var max=box.max();var corners=new Effect.Vec[8];for(int i=0;i<8;i++)corners[i]=new Effect.Vec((i&1)==0?min.x():max.x(),(i&2)==0?min.y():max.y(),(i&4)==0?min.z():max.z());for(int i=0;i<8;i++)for(int axis=1;axis<=4;axis*=2)if((i&axis)==0)nativeLine(corners[i],corners[i|axis],0xff66ddff,1,ratio);}
        void draw() {
            if(ImGui.beginMainMenuBar()) {
                ImGui.text(VfxClient.INSTANCE.editorTitle());
                if(ImGui.beginMenu("File")) {
                    if(ImGui.menuItem("New project","Ctrl+N"))modal="New project";
                    for(String item:List.of("Presets","Open","Save")) if(ImGui.menuItem(item,item.equals("Save")?"Ctrl+S":null)) {if(item.equals("Save")) attempt(this::requestSave);else modal=item;}
                    if(ImGui.menuItem("Import Photon .fx")) {photonImport=null;modal="Import Photon";}
                    if(ImGui.menuItem("Export package")) attempt(()->{studio.files.export(name.get(),project.json(),studio::resource);message("Package exported.",false);});
                    if(ImGui.menuItem("Open folder")) net.minecraft.Util.getPlatform().openFile(studio.files.root().toFile());
                    if(ImGui.menuItem("AI authoring instructions")) copyAgentPrompt();
                    if(ImGui.menuItem("Stop world effects")) {studio.stop();worldPreview=false;}
                    if(ImGui.menuItem("Copy play command")) attempt(()->{studio.files.save(name.get(),project.json());minecraft.keyboardHandler.setClipboard("/xvfx me "+name.get());});
                    ImGui.endMenu();
                }
                if(ImGui.beginMenu("Edit")) {if(ImGui.menuItem("Undo","Ctrl+Z")) history(false);if(ImGui.menuItem("Redo","Ctrl+Y")) history(true);ImGui.endMenu();}
                if(ImGui.beginMenu("Help")) {if(ImGui.menuItem("Making effects"))guideOpen.set(true);
                    if(ImGui.beginMenu("Built-in integration examples")){for(String name:com.xirctwerk.vfx.api.IntegrationExamples.NAMES)if(ImGui.menuItem(name.replace('_',' ')))attempt(()->com.xirctwerk.vfx.api.IntegrationExamples.start(name));if(ImGui.menuItem("Stop examples"))com.xirctwerk.vfx.api.IntegrationExamples.stop();ImGui.endMenu();}ImGui.endMenu();}
                if(ImGui.beginMenu("View")) {if(ImGui.menuItem("Tools / command palette","Ctrl+P",tools.open.get()))tools.open.set(!tools.open.get());if(ImGui.menuItem("JSON editor",null,jsonOpen)) openJson();if(ImGui.menuItem("Timeline",null,timeline.open.get()))timeline.open.set(!timeline.open.get());if(ImGui.menuItem("Reset layout")) reset=true;if(ImGui.menuItem("Replay display",null,previewLoop)) previewLoop=!previewLoop;if(ImGui.menuItem("Wireframe meshes",null,wireframe))wireframe=!wireframe;if(ImGui.menuItem("Change display background")) background=(background+1)%3;ImGui.endMenu();}
                ImGui.sameLine();ImGui.textDisabled(project.effect().name()+(savedJson==null||!savedJson.equals(project.json())?" *":""));ImGui.endMainMenuBar();
            }
            var vp=ImGui.getMainViewport();
            ImGui.setNextWindowPos(vp.getWorkPosX(),vp.getWorkPosY());ImGui.setNextWindowSize(vp.getWorkSizeX(),vp.getWorkSizeY()-28);
            ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding,0,0);
            ImGui.begin("Studio workspace",ImGuiWindowFlags.NoTitleBar|ImGuiWindowFlags.NoResize|ImGuiWindowFlags.NoMove|ImGuiWindowFlags.NoDocking|ImGuiWindowFlags.NoBackground|ImGuiWindowFlags.NoBringToFrontOnFocus);
            dock=ImGui.getID("Panels");
            if(reset||!ready&&imgui.internal.ImGui.dockBuilderGetNode(dock).isNotValidPtr()) {
                imgui.internal.ImGui.dockBuilderRemoveNode(dock);imgui.internal.ImGui.dockBuilderAddNode(dock,imgui.internal.flag.ImGuiDockNodeFlags.DockSpace);
                imgui.internal.ImGui.dockBuilderSetNodeSize(dock,vp.getWorkSizeX(),vp.getWorkSizeY()-28);
                var center=new ImInt(dock);var left=new ImInt();var right=new ImInt();
                imgui.internal.ImGui.dockBuilderSplitNode(center.get(),ImGuiDir.Left,.18f,left,center);
                imgui.internal.ImGui.dockBuilderSplitNode(center.get(),ImGuiDir.Right,.35f,right,center);
                imgui.internal.ImGui.dockBuilderDockWindow("Layers",left.get());imgui.internal.ImGui.dockBuilderDockWindow("Inspector",right.get());imgui.internal.ImGui.dockBuilderDockWindow("JSON",right.get());imgui.internal.ImGui.dockBuilderDockWindow("Preview",center.get());imgui.internal.ImGui.dockBuilderFinish(dock);reset=false;
            }
            var previewNode=imgui.internal.ImGui.dockBuilderGetNode(previewDock);
            if(previewNode.isValidPtr()) previewNode.setLastBgColor(worldPreview?0:ImGui.getColorU32(ImGuiCol.WindowBg));
            // Occupied central docks otherwise paint the root dockspace behind a transparent window.
            ImGui.pushStyleColor(ImGuiCol.WindowBg,.075f,.084f,.105f,worldPreview?0:1);
            ImGui.dockSpace(dock,0,0,ImGuiDockNodeFlags.PassthruCentralNode);ImGui.popStyleColor();ImGui.end();ImGui.popStyleVar();ready=true;
            if(ImGui.begin("Layers")) {
                var layers=project.effect().emitters();
                layerList();
                if(!mutedLayers.isEmpty()||!soloLayers.isEmpty())if(EditorHints.button("Show all layers")){mutedLayers.clear();soloLayers.clear();applyAudition();}
                ImGui.separator();
                if(EditorHints.button("Duplicate"))toolsCommand("duplicate");
                if(EditorHints.button("Delete"))toolsCommand("delete");
                if(EditorHints.button("Add layer")) attempt(()->{finishGesture();project.edit(o->{var a=o.getAsJsonArray("emitters");var e=Templates.create("sparks").getAsJsonArray("emitters").get(0).getAsJsonObject().deepCopy();String id="layer_"+(a.size()+1);Set<String> ids=new HashSet<>();for(var item:a) ids.add(item.getAsJsonObject().get("id").getAsString());while(ids.contains(id)) id+="_";e.addProperty("id",id);e.remove("parent");a.add(e);});selected=project.effect().emitters().size()-1;strings.clear();changed();});
            }ImGui.end();
            inspectorJson=project.json();
            if(ImGui.begin("Inspector")) {
                ImGui.text(project.effect().emitters().get(selected).id());ImGui.sameLine();if(EditorHints.smallButton("JSON")) openJson();ImGui.sameLine();if(EditorHints.smallButton("Keyframes")){finishGesture();timeline.open.set(true);}
                String[][] fields={{"size","color","opacity","fade","rotation","spin","aspect"},{"texture","glow","blend","bloom"},{"offset","space","parent","pivot"},{"rate","lifetime","start","duration","bursts"},{"speed","direction","spread","force","drag","noise","curl","curlFrequency","attractor","attraction","falloff","vortexAxis","vortex","vortexPull","orbitRadius","orbitSpeed","alignVelocity","stretch","wind"},{"kind","shape","radius","length","trailPoints","pixelSize","meshScale"},{"collision","bounce"},{},{"name","author","description","duration","autoDuration","loop","seed"}};
                for(int i=0;i<GROUPS.length;i++)if(EditorHints.section(GROUPS[i],i==0?ImGuiTreeNodeFlags.DefaultOpen:0)){
                    if(i==2){if(EditorHints.button("Rotation, scale and layer animation...")){tools.open.set(true);tools.requestedTab="Transforms";}EditorHints.tip("Open parent transforms and the separate seconds timeline for whole-layer animation.");}
                    if(i==2&&worldPreview){if(EditorHints.button("Beside player")){worldPosition=null;changed();}var anchor=studio.editorAnchor();if(anchor!=null)ImGui.textWrapped(String.format(java.util.Locale.ROOT,"World pivot: %.2f, %.2f, %.2f",anchor.x(),anchor.y(),anchor.z()));}
                    if(i==7){if(Geometry.supports(project.effect().emitters().get(selected).kind()))ImGui.textWrapped("Sprite sheets require particle, beam or trail layers.");else animationInspector();}
                    for(String field:fields[i])if((tools.search.get().isBlank()||field.toLowerCase(Locale.ROOT).contains(tools.search.get().toLowerCase(Locale.ROOT)))&&(!field.equals("meshScale")||Geometry.supports(project.effect().emitters().get(selected).kind()))&&(!Set.of("aspect","alignVelocity","stretch").contains(field)||project.effect().emitters().get(selected).kind().equals("particle")))field(field,i==8||field.equals("pivot")||field.equals("wind"));
                }
            }ImGui.end();
            ImGui.pushStyleColor(ImGuiCol.WindowBg,.075f,.084f,.105f,worldPreview?0:1);
            ImGui.setNextWindowBgAlpha(worldPreview?0:1);
            if(ImGui.begin("Preview",worldPreview?ImGuiWindowFlags.NoBackground:0)) {
                previewDock=ImGui.getWindowDockID();
                if(worldPreview) ImGui.getWindowDrawList().addRectFilled(ImGui.getWindowPosX(),ImGui.getCursorScreenPosY()-12,ImGui.getWindowPosX()+ImGui.getWindowSizeX(),ImGui.getCursorScreenPosY()+61,0xff1b1513);
                if(EditorHints.button(paused?"Play":"Pause")) paused=!paused;ImGui.sameLine();if(EditorHints.button("Restart")) changed();ImGui.sameLine();if(EditorHints.button("Fit")) fitView();ImGui.sameLine();if(EditorHints.checkbox("World",worldPreview)) toggleWorldPreview();
                ImGui.textDisabled(worldPreview?"Left click: place pivot on a block · drag: look":"Left drag: orbit · right drag: pan · wheel: zoom");
                float x=ImGui.getCursorScreenPosX(),y=ImGui.getCursorScreenPosY(),w=Math.max(1,ImGui.getContentRegionAvailX()),h=Math.max(1,ImGui.getContentRegionAvailY());
                float ratio=ImGui.getIO().getDisplaySizeX()/width;inputScale=ratio;
                px=(int)(x/ratio);py=(int)(y/ratio);pw=(int)(w/ratio);ph=(int)(h/ratio);
                ImGui.invisibleButton("Canvas",w,h,ImGuiButtonFlags.MouseButtonLeft|ImGuiButtonFlags.MouseButtonRight);hovered=ImGui.isItemHovered(ImGuiHoveredFlags.AllowWhenBlockedByActiveItem);EditorHints.tip(worldPreview?"Left click a block to place the pivot. Drag either mouse button to look.":"Left drag orbits; right drag pans; the wheel zooms. Fit frames the current effect.");
                // Native hit testing also captures clicks that arrive between rendered hover updates.
                if(ImGui.isItemActive()&&previewDrag<0) previewDrag=ImGui.isMouseDown(1)?1:0;
                if(!ImGui.isMouseDown(0)&&!ImGui.isMouseDown(1)) previewDrag=-1;
                if(!worldPreview) drawPreview(x,y,w,h,ratio);
            } else hovered=false;ImGui.end();ImGui.popStyleColor();
            jsonActive=false;jsonFocused=false;
            if(jsonOpen) {
                var opened=new ImBoolean(true);if(focusJson) {ImGui.setNextWindowFocus();focusJson=false;}
                if(ImGui.begin("JSON",opened)) {if(EditorHints.button("Apply JSON")) attempt(this::applyJson);ImGui.sameLine();if(EditorHints.button("Reload project")) {jsonBase=project.text();json.set(jsonBase);}if(!jsonBase.equals(project.text())) ImGui.textWrapped("Inspector changed. Reload to include its edits, or apply this draft to replace them.");ImGui.textDisabled("Ctrl+A/C/X/V · Ctrl+Z/Y · Ctrl+Enter apply · Ctrl+Shift+F format");
                    if(!draftHistory.current().equals(json.get()))draftHistory.reset(json.get());
                    if(resetJsonInput){imgui.internal.ImGui.clearActiveID();ImGui.setKeyboardFocusHere();resetJsonInput=false;}
                    String previousDraft=json.get();if(ImGui.inputTextMultiline("##json"+jsonRevision,json,-1,-1,ImGuiInputTextFlags.AllowTabInput)){long now=System.nanoTime();boolean typing=Math.abs(previousDraft.length()-json.get().length())==1;draftHistory.record(json.get(),typing&&now-lastDraftEdit<750_000_000L);lastDraftEdit=typing?now:0;}
                    jsonActive=ImGui.isItemActive();jsonFocused=ImGui.isWindowFocused(ImGuiFocusedFlags.RootAndChildWindows);jsonTextX=ImGui.getItemRectMinX()+35;jsonTextY=ImGui.getItemRectMinY()+35;}ImGui.end();jsonOpen=opened.get();
            }
            timeline.render(project,selected,i->selectLayer(i,false),StudioScreen.this::timelineChanged,StudioScreen.this::seek,viewedSimulation().time(),previewHorizon(),seekTarget>=0,this::editProperty);
            tools.render(new EditorTools.Frame(project,selectedIds(),viewedSimulation(),studio.files,StudioScreen.this::toolsEdit,StudioScreen.this::selectLayers,StudioScreen.this::seek,StudioScreen.this::toolsCommand,previewHorizon(),playbackSpeed,value->{playbackSpeed=value;playbackCarry=0;}));
            dialogs();propertyEditor.render();
            ImGui.setNextWindowPos(0,ImGui.getIO().getDisplaySizeY()-28);ImGui.setNextWindowSize(ImGui.getIO().getDisplaySizeX(),28);
            ImGui.begin("Status",ImGuiWindowFlags.NoTitleBar|ImGuiWindowFlags.NoResize|ImGuiWindowFlags.NoMove|ImGuiWindowFlags.NoDocking|ImGuiWindowFlags.NoScrollbar);
            if(status.isEmpty()) ImGui.textDisabled("Drag tabs to arrange panels. Ctrl+click numbers to type. Ctrl+S to save.");else {ImGui.textColored(statusError?0xff9fa7ff:0xffcae9b7,status);if(ImGui.isWindowHovered()) {ImGui.beginTooltip();ImGui.pushTextWrapPos(500);ImGui.textWrapped(status);ImGui.popTextWrapPos();ImGui.endTooltip();}}ImGui.end();
        }
        void textHistory(boolean redo){if(!draftHistory.current().equals(json.get()))draftHistory.reset(json.get());json.set(redo?draftHistory.redo():draftHistory.undo());resetJsonInput=true;jsonRevision++;lastDraftEdit=0;}
        void formatJson(){var formatted=EffectCodec.GSON.toJson(EffectCodec.parseObject(json.get()));if(!draftHistory.current().equals(json.get()))draftHistory.reset(json.get());draftHistory.record(formatted,false);json.set(formatted);resetJsonInput=true;jsonRevision++;lastDraftEdit=0;}
        void applyJson(){finishGesture();project.replace(EffectCodec.parse(json.get()));selected=Math.min(selected,project.effect().emitters().size()-1);strings.clear();changed();jsonBase=project.text();json.set(jsonBase);message("JSON validated and applied.",false);}
        void createProject(){
            if(!jsonBase.isEmpty()&&!json.get().equals(jsonBase))throw new IllegalStateException("Apply or reload your JSON draft before creating a project.");
            var next=Templates.newProject(newName.get());finishGesture();project.replace(next);selected=0;strings.clear();savedJson=null;name.set(newName.get());mutedLayers.clear();soloLayers.clear();studio.stopEditorPreview();worldPreview=false;worldPosition=null;paused=false;timeline.open.set(false);jsonBase=project.text();json.set(jsonBase);resetView();changed();fitView();message("Created "+newName.get()+". Undo restores the previous project.",false);
        }
        void requestSave() {
            finishGesture();var candidate=project.json();
            // Include typed string fields even when Ctrl+S is pressed before Enter.
            for(var field:strings.entrySet()) {
                String[] parts=field.getKey().split(":",2);String key=parts[1];
                var target=parts[0].equals("effect")?candidate:candidate.getAsJsonArray("emitters").get(Integer.parseInt(parts[0].substring(5))).getAsJsonObject();
                String current=target.has(key)?target.get(key).getAsString():key.equals("texture")?defaultValue(key):key.equals("blend")?"alpha":"";
                if(!current.equals(field.getValue().get())) target.addProperty(key,field.getValue().get());
            }
            boolean dirtyJson=!jsonBase.isEmpty()&&!json.get().equals(jsonBase);
            if(dirtyJson) {
                if(!jsonBase.equals(EffectCodec.GSON.toJson(candidate))) throw new IllegalStateException("JSON draft and inspector both changed. Apply JSON or Reload project before saving.");
                candidate=EffectCodec.parse(json.get());
            }
            var before=project.json();project.replace(candidate);
            selected=Math.min(selected,project.effect().emitters().size()-1);strings.clear();
            if(!before.equals(project.json())) changed();
            if(!jsonBase.isEmpty()) {jsonBase=project.text();json.set(jsonBase);}
            modal="Save";
        }
        void openJson() {finishGesture();jsonOpen=!jsonOpen;if(jsonOpen) {focusJson=true;if(jsonBase.isEmpty()||json.get().equals(jsonBase)) {jsonBase=project.text();json.set(jsonBase);}}}
        void field(String key,boolean root) {
            if(!root){String binding=Parameters.binding(inspectorJson,selectedIds(),key);if(binding!=null){ImGui.textWrapped(key+" is controlled by parameter '"+binding+"'.");ImGui.pushID("parameter:"+key);if(EditorHints.smallButton("Parameters...")){tools.open.set(true);tools.requestedTab="Parameters";}ImGui.popID();return;}}
            var source=root?inspectorJson:inspectorJson.getAsJsonArray("emitters").get(selected).getAsJsonObject();
            JsonElement value=source.get(key);
            if(value==null) value=switch(key) {case "glow"->new JsonPrimitive(true);case "collision","loop","autoDuration","pathLoop","alignVelocity"->new JsonPrimitive(false);case "kind"->new JsonPrimitive("particle");case "shape"->new JsonPrimitive("point");case "space"->new JsonPrimitive("local");case "texture","parent","name","author","description","blend"->new JsonPrimitive(key.equals("texture")?defaultValue(key):key.equals("blend")?"alpha":"");case "color"->JsonParser.parseString("[0.7,0.3,1,1]");default->JsonParser.parseString(defaultValue(key));};
            String label=switch(key) {case "trailPoints"->"Trail pts";case "pixelSize"->"Pixel size";case "glow"->"Emissive";case "kind"->"Visual";case "noise"->"Wander";case "autoDuration"->"Full effect length";default->Character.toUpperCase(key.charAt(0))+key.substring(1);};
            String id=(root?"effect":"layer"+selected)+":"+key;ImGui.pushID(id);ImGui.setNextItemWidth(Math.max(80,ImGui.getContentRegionAvailX()*.6f));JsonElement next=null;var bounds=EditorHints.bounds(key);
            if(root&&key.equals("duration")&&project.json().has("autoDuration")&&project.json().get("autoDuration").getAsBoolean()){ImGui.textWrapped("Full length: "+String.format(java.util.Locale.ROOT,"%.2f",project.effect().duration())+" s (layers + particle tails)");ImGui.popID();return;}
            if(value.isJsonObject()||key.equals("bursts")) {ImGui.text(label);ImGui.sameLine();if(EditorHints.smallButton(key.equals("color")?"Edit gradient":key.equals("bursts")?"Edit events":"Edit value")) editProperty(key,value);if(key.equals("color")) {for(int rgb:new int[]{0xff5533,0xffcc33,0x66dd88,0x33aaff,0xaa66ff,0xffffff}) {ImGui.pushID(rgb);float[] c={((rgb>>16)&255)/255f,((rgb>>8)&255)/255f,(rgb&255)/255f,1};if(ImGui.colorButton("Tint",c)) attempt(()->{finishGesture();tint(rgb);});EditorHints.tip("Tint the selected layer while preserving gradient key times, brightness and opacity.");ImGui.popID();ImGui.sameLine();}ImGui.newLine();ImGui.textWrapped("Tint preserves gradient times and alpha");}}
            else if(value.isJsonPrimitive()&&value.getAsJsonPrimitive().isBoolean()) {var v=new ImBoolean(value.getAsBoolean());if(ImGui.checkbox(label,v)) next=new JsonPrimitive(v.get());}
            else if(Set.of("kind","shape","space","blend").contains(key)) {var choices=switch(key) {case "kind"->List.of("particle","beam","trail","slash","ring_mesh","cube_mesh","sphere_mesh","lightning");case "shape"->List.of("point","sphere","ring","spiral","cone","box");case "blend"->List.of("alpha","additive");default->List.of("local","world");};if(ImGui.beginCombo(label,value.getAsString())) {for(String choice:choices) if(ImGui.selectable(choice,choice.equals(value.getAsString()))) next=new JsonPrimitive(choice);ImGui.endCombo();}}
            else if(value.isJsonPrimitive()&&value.getAsJsonPrimitive().isString()) {String initial=value.getAsString();var text=strings.computeIfAbsent(id,k->new ImString(initial,2048));if(ImGui.inputText(label,text,ImGuiInputTextFlags.EnterReturnsTrue)) next=new JsonPrimitive(text.get());EditorHints.tip(EditorHints.description(key,root)+"\nPress Enter to apply.");}
            else if(value.isJsonArray()) {var a=value.getAsJsonArray();float[] v=new float[key.equals("color")?4:3];for(int i=0;i<v.length;i++) v[i]=i<a.size()?a.get(i).getAsFloat():1;boolean edited=key.equals("color")?ImGui.colorEdit4(label,v):ImGui.dragFloat3(label,v,.01f,(float)bounds.min(),(float)bounds.max());if(edited) {var a2=new JsonArray();for(float n:v) a2.add(n);next=a2;}}
            else if(Set.of("seed","pixelSize","trailPoints").contains(key)) {int[] v={value.getAsInt()};if(ImGui.dragInt(label,v,1,(int)bounds.min(),(int)bounds.max())) next=new JsonPrimitive(v[0]);}
            else {float[] v={value.getAsFloat()};if(ImGui.dragFloat(label,v,key.equals("rotation")||key.equals("spin")?1:.01f,(float)bounds.min(),(float)bounds.max(),"%.3f")) next=new JsonPrimitive(v[0]);}
            EditorHints.tip(EditorHints.description(key,root)+(bounds==null?"":"\n"+EditorHints.rangeText(bounds.min(),bounds.max())+"\nDrag to edit; Ctrl+click to type."));
            if(key.equals("size")&&!root) {dragX=(ImGui.getItemRectMinX()+ImGui.getItemRectMaxX())/2;dragY=(ImGui.getItemRectMinY()+ImGui.getItemRectMaxY())/2;}
            if(bounds!=null&&!value.isJsonObject())EditorHints.limits(bounds.min(),bounds.max());
            if(!root&&Set.of("size","speed","rotation","lifetime","meshScale").contains(key)&&!value.isJsonObject()) {if(EditorHints.smallButton(key.equals("lifetime")?"Random range...":"Curve / range...")) editProperty(key,value);EditorHints.tip("Edit a constant, random range, or supported lifetime curve without changing other properties.");}
            else if(!root&&key.equals("color")&&value.isJsonArray()) {if(EditorHints.smallButton("Gradient...")) editProperty(key,value);}
            if(next!=null) {var replacement=next;attempt(()->{if(!gesture.equals(id)) {finishGesture();project.beginEdit();gesture=id;}project.edit(o->{if(root)o.add(key,replacement);else for(String target:selectedIds()){var e=Authoring.layer(o,target);e.add(key,replacement.deepCopy());if(key.equals("kind")&&!Geometry.supports(replacement.getAsString()))e.addProperty("pixelSize",1);}});changed();});}
            if(gesture.equals(id)&&!ImGui.isAnyItemActive()) finishGesture();ImGui.popID();
        }
        void finishGesture() {timeline.finish(project);project.endEdit();gesture="";}
        void animationInspector() {
            var layer=inspectorJson.getAsJsonArray("emitters").get(selected).getAsJsonObject();var active=new ImBoolean(layer.has("textureAnimation"));
            if(EditorHints.checkbox("Animated sprite sheet",active))attempt(()->{finishGesture();project.edit(o->{var e=o.getAsJsonArray("emitters").get(selected).getAsJsonObject();if(active.get())e.add("textureAnimation",JsonParser.parseString("{\"columns\":4,\"rows\":4,\"frames\":16,\"fps\":20,\"loop\":true}"));else e.remove("textureAnimation");});changed();});
            if(!layer.has("textureAnimation"))return;var animation=layer.getAsJsonObject("textureAnimation");
            for(String key:List.of("columns","rows","frames","fps","loop")) {JsonElement next=null;
                if(key.equals("loop")){var value=new ImBoolean(animation.has(key)?animation.get(key).getAsBoolean():true);if(EditorHints.checkbox("Loop frames",value))next=new JsonPrimitive(value.get());}
                else if(key.equals("fps")){float[] value={animation.has(key)?animation.get(key).getAsFloat():20};if(ImGui.dragFloat("Frames/sec",value,.1f,.1f,120))next=new JsonPrimitive(value[0]);}
                else {int[] value={animation.has(key)?animation.get(key).getAsInt():1};if(ImGui.dragInt(key,value,1,1,key.equals("frames")?Math.min(4096,(animation.has("columns")?animation.get("columns").getAsInt():1)*(animation.has("rows")?animation.get("rows").getAsInt():1)):64))next=new JsonPrimitive(value[0]);}
                EditorHints.tip(switch(key){case "columns"->"Equal-width cells across the PNG atlas.";case "rows"->"Equal-height cells down the PNG atlas.";case "frames"->"Frames to play, at most columns multiplied by rows.";case "fps"->"Sprite-sheet frames advanced per second.";default->"Repeat the atlas or hold its final frame.";});
                if(!key.equals("loop"))EditorHints.limits(key.equals("fps")?.1:1,key.equals("fps")?120:key.equals("frames")?Math.min(4096,(animation.has("columns")?animation.get("columns").getAsInt():1)*(animation.has("rows")?animation.get("rows").getAsInt():1)):64);
                if(next!=null){var value=next;attempt(()->{finishGesture();project.edit(o->o.getAsJsonArray("emitters").get(selected).getAsJsonObject().getAsJsonObject("textureAnimation").add(key,value));changed();});}
            }
            ImGui.textWrapped("Use a PNG atlas with equal-size frames, laid out left to right, then top to bottom. Frame count cannot exceed columns × rows. Nova sheet is a bundled original example.");
        }
        void editProperty(String key,JsonElement value) {
            finishGesture();int layer=selected;var targets=selectedIds();
            propertyEditor.eventDuration(project.effect().emitters().get(layer).duration());
            propertyEditor.open(key,value,next->{project.edit(o->Authoring.set(o,targets,key,next));changed();message("Applied "+key+". Other authored values preserved.",false);});
        }
        void dialogs() {
            if(!modal.isEmpty()) {ImGui.openPopup(modal);modal="";}
            for(String title:List.of("New project","Presets","Open","Save")) {
                ImGui.setNextWindowSize(460,380,ImGuiCond.FirstUseEver);
                if(ImGui.beginPopupModal(title,new ImBoolean(true))) {
                    if(title.equals("New project")){ImGui.inputText("Project name",newName);ImGui.textWrapped("Start with one neutral emitter. No files are overwritten; Undo restores the previous scene.");if(EditorHints.button("Create project"))attempt(()->{createProject();ImGui.closeCurrentPopup();});newProjectX=(ImGui.getItemRectMinX()+ImGui.getItemRectMaxX())/2;newProjectY=(ImGui.getItemRectMinY()+ImGui.getItemRectMaxY())/2;}
                    else if(title.equals("Presets")) {if(ImGui.beginTabBar("Preset categories")) {if(ImGui.beginTabItem("Presets")) {presetList(Templates.NAMES);ImGui.endTabItem();}if(ImGui.beginTabItem("Advanced presets")) {presetList(Templates.ADVANCED_NAMES);ImGui.endTabItem();}ImGui.endTabBar();}}
                    else if(title.equals("Open")) {if(EditorHints.button("Open Photon .fx file...")){photonImport=null;modal="Import Photon";ImGui.closeCurrentPopup();}attempt(()->{for(String file:studio.files.list()) if(ImGui.selectable(file)) {var next=studio.files.load(file);studio.checkResources(EffectCodec.compile(next));finishGesture();project.replace(next);selected=0;strings.clear();name.set(file);savedJson=project.json();mutedLayers.clear();soloLayers.clear();paused=false;changed();fitView();ImGui.closeCurrentPopup();}});}
                    else {ImGui.inputText("Filename",name);if(EditorHints.button("Save effect")) attempt(()->{finishGesture();studio.files.save(name.get(),project.json());savedJson=project.json();message("Saved "+name.get(),false);ImGui.closeCurrentPopup();});}
                    if(EditorHints.button("Close")) ImGui.closeCurrentPopup();ImGui.textWrapped(status);ImGui.endPopup();
                }
            }
            ImGui.setNextWindowSize(Math.min(680,ImGui.getIO().getDisplaySizeX()-24),Math.min(500,ImGui.getIO().getDisplaySizeY()-24),ImGuiCond.Always);
            if(ImGui.beginPopupModal("Import Photon",new ImBoolean(true))) {
                ImGui.textWrapped("Choose a Photon .fx file to review. Source files remain untouched.");
                ImGui.setNextItemWidth(-1);if(ImGui.inputText("##Photon path",photonPath))photonImport=null;
                if(EditorHints.button("Browse .fx..."))attempt(()->{String chosen=org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog("Open Photon effect",null,(org.lwjgl.PointerBuffer)null,"Photon .fx",false);if(chosen!=null){photonPath.set(chosen);reviewPhotonPath();}});
                ImGui.sameLine();if(EditorHints.button("Review file"))attempt(this::reviewPhotonPath);
                ImGui.sameLine();if(EditorHints.button("Open imports folder")) net.minecraft.Util.getPlatform().openFile(studio.files.root().resolve("imports").toFile());
                if(EditorHints.checkbox("Use bundled sprites for external textures",substitutePhotonTextures))photonImport=null;
                if(photonImport==null) {
                    ImGui.beginChild("Photon files",0,Math.max(90,ImGui.getContentRegionAvailY()-110),true);
                    attempt(()->{var files=studio.files.photonFiles();if(files.isEmpty())ImGui.textWrapped("No .fx files yet. Copy your files here and they will appear automatically.");for(String file:files)if(ImGui.selectable(file)) {photonName=file;photonImport=studio.files.importPhoton(file,substitutePhotonTextures.get());message("Import reviewed. Read conversion limitations before opening.",false);}});
                    ImGui.endChild();
                }else {
                    var report=photonImport;ImGui.text(photonName+" · "+photonImport.effect().getAsJsonArray("emitters").size()+" layers");
                    ImGui.textColored(.5f,.9f,.6f,1,"Converted successfully. Ready to open.");
                    if(EditorHints.button(photonImport.exact()?"Open converted effect":"Open approximation"))attempt(()->{var next=photonImport.effect();studio.checkResources(EffectCodec.compile(next));finishGesture();project.replace(next);selected=0;strings.clear();name.set(next.get("id").getAsString().substring("xirc_vfx_studio:".length()));savedJson=null;mutedLayers.clear();soloLayers.clear();paused=false;previewLoop=true;changed();fitView();message("Opened Photon conversion. Save as Studio JSON; original .fx is untouched.",false);ImGui.closeCurrentPopup();});
                    photonButtonX=(ImGui.getItemRectMinX()+ImGui.getItemRectMaxX())/2;photonButtonY=(ImGui.getItemRectMinY()+ImGui.getItemRectMaxY())/2;
                    ImGui.sameLine();if(EditorHints.button("Choose another file"))photonImport=null;
                    ImGui.textWrapped("Approximation: the differences below affect Photon fidelity. They do not block opening this editable effect.");
                    ImGui.beginChild("Conversion diagnostics",0,Math.max(90,ImGui.getContentRegionAvailY()-125),true);for(String diagnostic:report.diagnostics())ImGui.textWrapped(diagnostic);ImGui.endChild();
                    if(EditorHints.button("Copy conversion report"))minecraft.keyboardHandler.setClipboard(photonName+"\n"+String.join("\n",report.diagnostics()));

                }
                if(EditorHints.button("Close"))ImGui.closeCurrentPopup();ImGui.textWrapped(status);ImGui.endPopup();
            }
            if(guideOpen.get()) {
                ImGui.setNextWindowSize(Math.min(580,ImGui.getIO().getDisplaySizeX()-24),Math.min(430,ImGui.getIO().getDisplaySizeY()-24),ImGuiCond.FirstUseEver);
                if(ImGui.begin("Making effects",guideOpen)) {
                    ImGui.textWrapped("Build motion and contrast first. A polished effect is a short sequence, with a clear silhouette and a few deliberate layers.");
                    for(String[] lesson:new String[][]{
                        {"Impact / slash","Use a wide colored slash, then a narrow white edge. Delay a spark burst by 0.1 seconds; let debris fade after the slash. Mesh scale curves expand the impact ring instead of leaving it stationary."},
                        {"Energy / portal","Use an expanding core and counter-rotating rings. Add small, slow motes for depth. Keep the brightest layer narrow; a giant white blob hides the shape."},
                        {"Lightning","Choose Visual: lightning. Radius is bolt length; Size is width. Direction points toward the endpoint. Rotation varies the jagged pattern; use a short lifetime and repeated bursts for flicker."},
                        {"Projects and placement","File > New project (Ctrl+N) starts a fresh emitter; Undo restores the previous scene. Transform > Pivot sets the local anchor. In World preview, left click a block to place that pivot; drag to look. Transform > Beside player restores the moving preview."},
                        {"JSON shortcuts","Ctrl+A selects all; Ctrl+C/X/V copy, cut and paste; Ctrl+Z/Y undo and redo text. Ctrl+Enter validates and applies the draft, Ctrl+Shift+F formats it, and Ctrl+S saves."},
                        {"Keyframing","Inspector > Keyframes opens the Timeline. Pick a track, move Key cursor, Add key, then edit its value/color. Drag diamonds to retime; Delete key removes the selected key. Keys run from each particle birth (0%) to death (100%), with linear interpolation. Ctrl+Z undoes a whole drag."},
                        {"Preview scrubbing","Timeline > Preview seconds pauses and rebuilds display or world playback at that second. Layer sequence shows emission bars and edits layer start/duration in seconds. Property keys use lifetime percentages, not effect seconds."},
                        {"Curves and timing","Start delays sequence layers. Lifetime is how long a birth survives. Mesh scale -> Curve / range controls geometric expansion/contraction; Size controls width. Curve time runs from birth (0) to death (1). Project > Full effect length includes all layer emissions and particle tails."},
                        {"Quality check","Toggle World before saving. Display is an orthographic approximation. Emissive means full-bright, not bloom. Photon shaders and UV animation do not magically transfer; review every import report."}})
                        if(ImGui.collapsingHeader(lesson[0]))ImGui.textWrapped(lesson[1]);
                    ImGui.separator();ImGui.textWrapped("Working example: Storm Lance demonstrates procedural bolts; Singularity Collapse demonstrates an animated core and expanding release.");
                }ImGui.end();
            }
        }
        void reviewPhotonPath() throws Exception {photonImport=null;var path=java.nio.file.Path.of(photonPath.get());var reviewed=studio.files.importPhotonPath(path,substitutePhotonTextures.get());photonImport=reviewed;photonName=path.getFileName().toString();message("File reviewed. Read the conversion differences, then Open approximation.",false);}
        void presetList(List<String> names) {
            for(String preset:names) {if(ImGui.selectable(Templates.title(preset))) {finishGesture();project.replace(Templates.create(preset));selected=0;strings.clear();name.set(preset);savedJson=null;mutedLayers.clear();soloLayers.clear();paused=false;resetView();changed();fitView();ImGui.closeCurrentPopup();}if(ImGui.isItemHovered())ImGui.setTooltip(Templates.create(preset).has("description")?Templates.create(preset).get("description").getAsString():"Single-layer starting point.");}
        }
        void drawPreview(float x,float y,float w,float h,float ratio) {
            drawPreviewPass(x,y,w,h,ratio,false);
            if(contributionView){var draw=ImGui.getWindowDrawList();draw.pushClipRect(x+w/2,y,x+w,y+h,true);drawPreviewPass(x,y,w,h,ratio,true);draw.addText(x+w/2+10,y+30,0xffeeeeee,"Selected layer contribution");draw.popClipRect();draw.addLine(x+w/2,y,x+w/2,y+h,0xffeeeeee,2);draw.addText(x+10,y+30,0xffeeeeee,"Composition");}
        }
        void drawPreviewPass(float x,float y,float w,float h,float ratio,boolean contribution) {
            var d=ImGui.getWindowDrawList();d.pushClipRect(x,y,x+w,y+h,true);d.addRectFilled(x,y,x+w,y+h,switch(background) {case 1->0xffded7d4;case 2->0xff374027;default->0xff201914;});
            for(int i=-4;i<=4;i++) {nativeLine(new Effect.Vec(i,0,-4),new Effect.Vec(i,0,4),0xff493d32,1,ratio);nativeLine(new Effect.Vec(-4,0,i),new Effect.Vec(4,0,i),0xff493d32,1,ratio);}
            nativeLine(Effect.Vec.ZERO,new Effect.Vec(1,0,0),0xff878eef,2,ratio);nativeLine(Effect.Vec.ZERO,new Effect.Vec(0,1,0),0xffabd989,2,ratio);nativeLine(Effect.Vec.ZERO,new Effect.Vec(0,0,1),0xfff5b890,2,ratio);nativeLine(new Effect.Vec(-1,0,0),new Effect.Vec(-1,2,0),0xff938071,1,ratio);
            var particles=new ArrayList<>(preview.particles());if(contribution){var ids=selectedIds();particles.removeIf(p->!ids.contains(p.emitter.id()));}particles.sort(Comparator.comparingDouble(p->depth(p.position)));
            for(var p:particles) {var position=p.interpolated(displayPartial());var c=p.color();if(diagnosticView==1)c=new Effect.Color(1,1,1,c.a());else if(diagnosticView>=2){double span=p.emitter.size().max()-p.emitter.size().min();double heat=Math.max(0,Math.min(1,switch(diagnosticView){case 3->c.a();case 4->p.renderVelocity().length()/20;case 5->span<1e-9?.5:(p.size()-p.emitter.size().min())/span;default->p.size()/2;}));c=new Effect.Color(heat,1-Math.abs(heat*2-1),1-heat,1);}int color=ImGui.getColorU32((float)c.r(),(float)c.g(),(float)c.b(),(float)c.a());float thick=Math.max(1,(float)(p.size()*scale()*ratio));
                if(Geometry.supports(p.emitter.kind())) {
                    var quads=new ArrayList<>(Geometry.create(p,position));
                    quads.sort(Comparator.comparingDouble(q->depth(q.a().add(q.b()).add(q.c()).add(q.d()).scale(.25))));
                    for(var q:quads)if(wireframe){nativeLine(q.a(),q.b(),color,1,ratio);nativeLine(q.b(),q.c(),color,1,ratio);nativeLine(q.c(),q.d(),color,1,ratio);nativeLine(q.d(),q.a(),color,1,ratio);}else {var a=project(q.a());var b=project(q.b());var c2=project(q.c());var e=project(q.d());d.addQuadFilled(a[0]*ratio,a[1]*ratio,b[0]*ratio,b[1]*ratio,c2[0]*ratio,c2[1]*ratio,e[0]*ratio,e[1]*ratio,color);}
                }
                else if(p.emitter.kind().equals("beam")) nativeLine(position,position.add(p.direction().normalize().scale(p.emitter.length()*p.transformScale())),color,thick,ratio);
                else if(p.emitter.kind().equals("trail")) {Effect.Vec last=position;for(var point:p.history) {nativeLine(last,point,color,thick,ratio);last=point;}}
                else {var point=project(p.interpolated(displayPartial()));float cx=point[0]*ratio,cy=point[1]*ratio,half=thick/2;var velocity=p.renderVelocity();double angle=p.angle();if(p.emitter.motion().align()&&velocity.length()>1e-6){var endpoint=project(position.add(velocity));angle+=Math.toDegrees(Math.atan2(endpoint[1]-point[1],endpoint[0]-point[0]));}float a=(float)Math.toRadians(angle),co=(float)Math.cos(a),si=(float)Math.sin(a);float[] xs=new float[4],ys=new float[4];int[] sign={-1,-1,1,-1,1,1,-1,1};for(int i=0;i<4;i++) {xs[i]=cx+half*(sign[i*2]*(float)(p.emitter.aspect()*p.emitter.motion().stretchFactor(velocity))*co-sign[i*2+1]*si);ys[i]=cy+half*(sign[i*2]*(float)(p.emitter.aspect()*p.emitter.motion().stretchFactor(velocity))*si+sign[i*2+1]*co);}long texture=minecraft.getTextureManager().getTexture(studio.texture(p.emitter.texture())).getId();var uv=p.emitter.textureAnimation().uv(p.interpolatedAge(displayPartial()));d.addImageQuad(texture,xs[0],ys[0],xs[1],ys[1],xs[2],ys[2],xs[3],ys[3],(float)uv.u0(),(float)uv.v0(),(float)uv.u1(),(float)uv.v0(),(float)uv.u1(),(float)uv.v1(),(float)uv.u0(),(float)uv.v1(),color);}
            }
            if(showBounds)drawBounds(ratio);
            d.addText(x+10,y+10,0xffd9d2cc,String.format(Locale.ROOT,"%.2fs · %d particles",preview.time(),particles.size()));d.popClipRect();
        }
        void nativeLine(Effect.Vec a,Effect.Vec b,int color,float thickness,float ratio) {var s=project(a);var e=project(b);ImGui.getWindowDrawList().addLine(s[0]*ratio,s[1]*ratio,e[0]*ratio,e[1]*ratio,color,thickness);}
        float dragX,dragY;JsonObject smokeBefore;
    void smokeDrag(int stage) {if(stage==0) {smokeBefore=project.json();backend.testPointer(dragX,dragY,true);}else if(stage==1) backend.testPointer(dragX+35,dragY,true);else if(stage==2) backend.testPointer(dragX+35,dragY,false);else {finishGesture();if(project.json().equals(smokeBefore)) throw new IllegalStateException("Native drag did not edit");project.undo();if(!project.json().equals(smokeBefore)) throw new IllegalStateException("Native drag undo failed");backend.release();changed();}}
    }
}
