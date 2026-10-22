package com.xirctwerk.vfx.client;

import com.google.gson.*;
import com.xirctwerk.vfx.core.*;
import imgui.ImGui;
import imgui.flag.*;
import imgui.type.*;
import java.util.*;
import java.util.function.*;

/** Optional tools workspace; main inspector remains focused on the selected visual. */
final class EditorTools {
    record Frame(Project project,Set<String> selected,Simulation simulation,EffectFiles files,
                 BiConsumer<Consumer<JsonObject>,Boolean> edit,Consumer<Set<String>> select,DoubleConsumer seek,
                 Consumer<String> command,double horizon,double speed,DoubleConsumer rate){}
    final ImBoolean open=new ImBoolean(false);
    final ImString search=new ImString(128);
    String requestedTab="";
    private String activeTab="";
    private final ImString folder=new ImString("group",128),clipName=new ImString("my_clip",128),parameterName=new ImString("power",64),markerName=new ImString("impact",128),bookmarkName=new ImString("view",128);
    private String field="size",track="size",error="";
    private final Set<Integer> keys=new LinkedHashSet<>();
    private final Set<String> variations=new LinkedHashSet<>(List.of("size","speed")),bookmarks=new LinkedHashSet<>();
    private final float[] add={0},multiply={1},translate={0,0,0},shift={0},stretch={1},variation={.15f};
    private final ImInt seed=new ImInt(42);
    private final ImInt maxInstances=new ImInt(16),maxParticles=new ImInt(8192),maxQuads=new ImInt(32768);
    private final ImInt particleWarning=new ImInt(4096),geometryWarning=new ImInt(32768);
    private JsonObject clipboard,a,b;
    private List<String> clips=List.of(),checkpoints=List.of();
    private boolean refresh=true;
    final PathEditor paths=new PathEditor();
    final TransformEditor transforms=new TransformEditor();
    float relativeX,relativeY,parameterX,parameterY;
    int diagnosticFrames;
    void smokeDiagnostics(){open.set(true);requestedTab="Diagnostics";particleWarning.set(1);geometryWarning.set(1);}
    void smokeOpen(){open.set(true);requestedTab="Layers";field="size";add[0]=.1f;multiply[0]=1;}
    @FunctionalInterface private interface Action{void run() throws Exception;}
    private void attempt(Action action){try{action.run();error="";}catch(Exception ex){error=ex.getMessage();}}
    private void edit(Frame f,Consumer<JsonObject> edit){attempt(()->f.edit.accept(edit,false));}
    void render(Frame f){
        if(!open.get())return;
        ImGui.setNextWindowSize(Math.min(650,ImGui.getIO().getDisplaySizeX()-32),Math.min(550,ImGui.getIO().getDisplaySizeY()-40),ImGuiCond.FirstUseEver);
        if(ImGui.begin("Tools",open)){
            ImGui.textDisabled(f.selected.size()+" selected · changes validate and undo together");
            if(ImGui.beginTabBar("Tool categories")){
                if(tab("Layers")){layers(f);ImGui.endTabItem();}
                if(tab("Animation")){animation(f);ImGui.endTabItem();}
                if(tab("Transforms")){transforms.render(f);ImGui.endTabItem();}
                if(tab("Paths")){paths.render(f);ImGui.endTabItem();}
                if(tab("Parameters")){parameters(f);ImGui.endTabItem();}
                if(tab("Preview")){preview(f);ImGui.endTabItem();}
                if(tab("Diagnostics")){diagnostics(f);ImGui.endTabItem();}
                if(tab("Library / recovery")){library(f);ImGui.endTabItem();}
                if(tab("Search")){palette(f);ImGui.endTabItem();}
                ImGui.endTabBar();
            }
            if(!error.isEmpty())ImGui.textWrapped(error);
        }ImGui.end();
    }
    private void layers(Frame f){
        ImGui.textWrapped("Ctrl-click layers to select several. Relative edits preserve curve keys, easing and random ranges.");
        if(EditorHints.button("Select all"))f.select.accept(Authoring.ids(f.project.json()));ImGui.sameLine();if(EditorHints.button("Isolate / restore"))f.command.accept("isolate");
        choose("Property",Authoring.NUMERIC,field,value->field=value);
        ImGui.sliderFloat("Add",add,-36000,36000,"%.3f",imgui.flag.ImGuiSliderFlags.AlwaysClamp);EditorHints.tip("Add this amount to every scalar, range endpoint or curve value of the selected property.");EditorHints.limits(-36000,36000);
        ImGui.sliderFloat("Multiply",multiply,-100,100,"%.3f",imgui.flag.ImGuiSliderFlags.AlwaysClamp);EditorHints.limits(-100,100);
        if(EditorHints.button("Apply relative edit"))edit(f,o->Authoring.relative(o,f.selected,field,add[0],multiply[0]));relativeX=(ImGui.getItemRectMinX()+ImGui.getItemRectMaxX())/2;relativeY=(ImGui.getItemRectMinY()+ImGui.getItemRectMaxY())/2;
        ImGui.separator();ImGui.sliderFloat3("Move by (blocks)",translate,-128,128,"%.3f",imgui.flag.ImGuiSliderFlags.AlwaysClamp);EditorHints.limits(-128,128);
        if(EditorHints.button("Move selection"))edit(f,o->Authoring.translate(o,f.selected,translate[0],translate[1],translate[2]));EditorHints.tip("Translate selected hierarchy roots once; selected descendants inherit the movement.");
        ImGui.inputText("Folder",folder);if(EditorHints.button("Assign folder"))edit(f,o->Authoring.set(o,f.selected,"folder",new JsonPrimitive(folder.get())));
        if(EditorHints.button("Duplicate selected"))f.command.accept("duplicate");ImGui.sameLine();if(EditorHints.button("Delete selected"))f.command.accept("delete");
        if(ImGui.collapsingHeader("Controlled variations")){
            for(String name:List.of("size","speed","rotation","lifetime","radius","rate","bloom","spin")){var enabled=new ImBoolean(variations.contains(name));if(ImGui.checkbox(name,enabled)){if(enabled.get())variations.add(name);else variations.remove(name);}}
            ImGui.sliderFloat("Variation fraction",variation,0,1);EditorHints.limits(0,1);ImGui.inputInt("Variation seed",seed);
            if(EditorHints.button("Generate variation"))edit(f,o->Authoring.vary(o,f.selected,variations,seed.get(),variation[0]));EditorHints.tip("Deterministic multiplicative variation of checked properties only. Invalid results are rejected atomically; undo restores the composition.");
        }
    }
    private void animation(Frame f){
        choose("Track",Keyframes.TRACKS,track,value->{track=value;keys.clear();});
        var layer=Authoring.layer(f.project.json(),f.selected.iterator().next());
        if(EditorHints.button("Copy track"))attempt(()->clipboard=Authoring.clip(track,layer.has(track)?layer.get(track):Keyframes.sample(layer,track,0)));
        ImGui.sameLine();if(EditorHints.button("Paste track")&&clipboard!=null){String property=clipboard.get("track").getAsString();edit(f,o->Authoring.pasteTrack(o,f.selected,property,clipboard.get("value")));}
        EditorHints.tip("Copies the complete constant, curve or gradient, including interpolation. Pasting replaces that one property on selected layers.");
        var points=Keyframes.keys(layer,track);keys.removeIf(i->i>=points.size());
        if(EditorHints.button("Select all keys")){keys.clear();for(int i=0;i<points.size();i++)keys.add(i);}
        for(int i=0;i<points.size();i++){ImGui.pushID(i);var enabled=new ImBoolean(keys.contains(i));if(ImGui.checkbox("Key "+(i+1)+" · "+points.get(i).getAsJsonObject().get("time").getAsString(),enabled)){if(enabled.get())keys.add(i);else keys.remove(i);}ImGui.popID();}
        ImGui.sliderFloat("Shift lifetime",shift,-1,1,"%.3f",imgui.flag.ImGuiSliderFlags.AlwaysClamp);EditorHints.limits(-1,1);ImGui.sliderFloat("Stretch",stretch,.01f,10,"%.3f",imgui.flag.ImGuiSliderFlags.AlwaysClamp);EditorHints.limits(.01,10);
        if(EditorHints.button("Retime selected keys"))edit(f,o->Keyframes.retime(Authoring.layer(o,layer.get("id").getAsString()),track,keys,shift[0],stretch[0]));
        ImGui.separator();ImGui.inputText("Marker name",markerName);
        if(EditorHints.button("Mark current second"))edit(f,o->{if(!o.has("markers"))o.add("markers",new JsonArray());var marker=new JsonObject();marker.addProperty("name",markerName.get());marker.addProperty("time",Math.min(631,f.simulation.time()));o.getAsJsonArray("markers").add(marker);});
        var json=f.project.json();if(json.has("markers")){var markers=json.getAsJsonArray("markers");for(int i=0;i<markers.size();i++){ImGui.pushID(i);var marker=markers.get(i).getAsJsonObject();if(ImGui.selectable(marker.get("name").getAsString()+" · "+marker.get("time").getAsString()+" s"))f.seek.accept(marker.get("time").getAsDouble());ImGui.sameLine();int index=i;if(EditorHints.smallButton("Remove marker"))edit(f,o->o.getAsJsonArray("markers").remove(index));ImGui.popID();}}
    }
    private void parameters(Frame f){
        ImGui.textWrapped("Expose named numeric controls that bind scalar properties on selected layers. Curves remain authored separately.");
        var json=f.project.json();if(json.has("parameters")){var parameters=json.getAsJsonArray("parameters");for(int i=0;i<parameters.size();i++){ImGui.pushID(i);var p=parameters.get(i).getAsJsonObject();float[] value={p.get("value").getAsFloat()};int index=i;
            if(ImGui.sliderFloat(p.has("label")?p.get("label").getAsString():p.get("name").getAsString(),value,p.get("min").getAsFloat(),p.get("max").getAsFloat()))attempt(()->f.edit.accept(o->o.getAsJsonArray("parameters").get(index).getAsJsonObject().addProperty("value",Math.max(p.get("min").getAsDouble(),Math.min(p.get("max").getAsDouble(),value[0]))),true));if(i==0){parameterX=(ImGui.getItemRectMinX()+ImGui.getItemRectMaxX())/2;parameterY=(ImGui.getItemRectMinY()+ImGui.getItemRectMaxY())/2;}EditorHints.limits(p.get("min").getAsDouble(),p.get("max").getAsDouble());
            if(EditorHints.smallButton("Remove binding"))edit(f,o->o.getAsJsonArray("parameters").remove(index));ImGui.popID();}}
        ImGui.separator();ImGui.inputText("Parameter name",parameterName);choose("Bound property",Authoring.NUMERIC,field,value->field=value);
        if(EditorHints.button("Expose on selected layers"))edit(f,o->Parameters.expose(o,f.selected,parameterName.get(),field));
    }
    private void preview(Frame f){
        if(EditorHints.button("Previous frame"))f.seek.accept(Math.max(0,f.simulation.time()-Simulation.DT));ImGui.sameLine();if(EditorHints.button("Next frame"))f.seek.accept(Math.min(f.horizon,f.simulation.time()+Simulation.DT));
        float[] speed={(float)f.speed};if(ImGui.sliderFloat("Playback speed",speed,.1f,4,"%.2fx"))f.rate.accept(speed[0]);EditorHints.limits(.1,4);
        for(String view:List.of("front","side","top","isometric")){if(EditorHints.button(view))f.command.accept("view:"+view);ImGui.sameLine();}ImGui.newLine();
        ImGui.inputText("Bookmark name",bookmarkName);if(EditorHints.button("Save camera bookmark")){bookmarks.add(bookmarkName.get());f.command.accept("bookmark:"+bookmarkName.get());}
        for(String name:bookmarks)if(ImGui.selectable("Camera: "+name))f.command.accept("recall:"+name);
        ImGui.separator();if(EditorHints.button("Capture A"))a=f.project.json();ImGui.sameLine();if(EditorHints.button("Capture B"))b=f.project.json();
        if(EditorHints.button("Preview A")&&a!=null)replace(f,a);ImGui.sameLine();if(EditorHints.button("Preview B")&&b!=null)replace(f,b);EditorHints.tip("Restore a captured definition for A/B comparison. Each switch is undoable; both snapshots stay available this editor session.");
        if(EditorHints.button("Compare selected contribution"))f.command.accept("contribution");EditorHints.tip("Split the display: composition on the left, selected layers on the right, using the same particles, clock and camera. Click again to restore the full view.");
        if(EditorHints.button("Normal"))f.command.accept("normal");ImGui.sameLine();if(EditorHints.button("Silhouette"))f.command.accept("silhouette");ImGui.sameLine();if(EditorHints.button("Size heatmap"))f.command.accept("heatmap");
        for(String mode:List.of("opacity","speed","range")){if(EditorHints.button(mode+" heatmap"))f.command.accept("heatmap:"+mode);ImGui.sameLine();}ImGui.newLine();
        ImGui.textWrapped("Heatmaps: size uses 0–2 blocks; speed 0–20 blocks/s; range shows each particle within its authored size bounds; opacity ignores transparency to reveal faint layers. Diagnostic color views apply to the display preview. Use World to inspect actual materials and depth.");
    }
    private void diagnostics(Frame f){
        diagnosticFrames++;
        ImGui.separator();ImGui.text("World playback budgets");
        ImGui.sliderInt("Maximum instances",maxInstances.getData(),1,16);EditorHints.limits(1,16);
        ImGui.sliderInt("Maximum live particles",maxParticles.getData(),1,8192);EditorHints.limits(1,8192);
        ImGui.sliderInt("Maximum estimated quads",maxQuads.getData(),1,32768);EditorHints.limits(1,32768);
        if(EditorHints.button("Apply playback budgets"))attempt(()->com.xirctwerk.vfx.api.VfxPerformance.configure(new com.xirctwerk.vfx.api.VfxPerformance.Limits(maxInstances.get(),maxParticles.get(),maxQuads.get())));
        EditorHints.tip("Applies session limits and stops current world playbacks. Definitions retain their authored values.");
        ImGui.text("World renderer: per-effect CPU cost and actual submitted draw batches");
        ImGui.textWrapped("Network: "+VfxNetworkClient.accepted()+" accepted / "+VfxNetworkClient.rejected()+" skipped. "+VfxNetworkClient.lastRejection());
        for(var row:com.xirctwerk.vfx.api.VfxPerformance.snapshot())ImGui.textWrapped(String.format(java.util.Locale.ROOT,"%s | %d particles | %d quads | %d draws | tick %.1f us | render %.1f us",row.effect(),row.particles(),row.quads(),row.drawCalls(),row.simulationMicros(),row.renderCpuMicros()));
        ImGui.textWrapped("Quads/draws include repeated bloom submissions. CPU submission times exclude GPU execution and deferred driver work.");
        ImGui.separator();

        ImGui.sliderInt("Particle warning threshold",particleWarning.getData(),1,Simulation.MAX_PARTICLES);EditorHints.limits(1,Simulation.MAX_PARTICLES);
        ImGui.sliderInt("Geometry warning threshold",geometryWarning.getData(),1,65536);EditorHints.limits(1,65536);
        EditorHints.tip("Session advisory thresholds; validation and runtime hard budgets remain unchanged.");
        particleWarning.set(Math.max(1,Math.min(Simulation.MAX_PARTICLES,particleWarning.get())));geometryWarning.set(Math.max(1,Math.min(65536,geometryWarning.get())));
        var sim=f.simulation;var effect=sim.effect();
        for(String warning:BudgetWarnings.inspect(sim,new BudgetWarnings.Limits(particleWarning.get(),geometryWarning.get())))ImGui.textWrapped("Warning: "+warning);float usage=sim.particles().size()/(float)Simulation.MAX_PARTICLES;ImGui.progressBar(usage,-1,0,sim.particles().size()+" / "+Simulation.MAX_PARTICLES+" particles");
        ImGui.text("Dropped births: "+sim.dropped());ImGui.text("Validated particle estimate: "+(int)Math.ceil(EffectCodec.estimatedParticles(effect)));ImGui.text("Estimated procedural quads: "+(int)Math.ceil(EffectCodec.estimatedGeometry(effect)));
        for(var e:effect.emitters()){long count=sim.particles().stream().filter(p->p.emitter.id().equals(e.id())).count();ImGui.text(e.id()+": "+count+" live");}
        if(EditorHints.button("Show bounds"))f.command.accept("bounds");EditorHints.tip("Toggle the authored scene-bound box in the display preview.");
        ImGui.textWrapped("Counts and estimates are CPU diagnostics, not GPU timings. Geometry and particle hard budgets remain enforced.");
    }
    private void library(Frame f){
        if(refresh){attempt(()->{clips=f.files.clips();checkpoints=f.files.checkpoints();});refresh=false;}
        if(EditorHints.button("Refresh lists"))refresh=true;
        ImGui.inputText("Clip filename",clipName);if(EditorHints.button("Save copied track")&&clipboard!=null)attempt(()->{f.files.saveClip(clipName.get(),clipboard);refresh=true;});
        for(String name:clips)if(ImGui.selectable("Clip: "+name))attempt(()->{clipboard=f.files.loadClip(name);String property=clipboard.get("track").getAsString();f.edit.accept(o->Authoring.pasteTrack(o,f.selected,property,clipboard.get("value")),false);});
        ImGui.separator();if(EditorHints.button("Save checkpoint"))attempt(()->{f.files.checkpoint(f.project.json(),false);refresh=true;});
        for(String name:checkpoints)if(ImGui.selectable("Recover: "+name))attempt(()->replace(f,f.files.recover(name)));
        ImGui.textWrapped("Recovery replaces the project through validation and undo. Authored effect files are untouched; the automatic draft is separate.");
    }
    private void palette(Frame f){
        ImGui.inputText("Find property or action",search);String query=search.get().toLowerCase(Locale.ROOT);
        for(String action:List.of("isolate","duplicate","delete","normal","silhouette","heatmap","bounds","contribution"))if(action.contains(query)&&ImGui.selectable(action))f.command.accept(action);
        for(String property:Authoring.NUMERIC)if(property.toLowerCase(Locale.ROOT).contains(query))ImGui.textWrapped(property+" — "+EditorHints.description(property,false));
        ImGui.textWrapped("The same search filters inspector properties. Clear it to restore every control.");
    }
    private static void replace(Frame f,JsonObject next){f.edit.accept(o->{o.entrySet().clear();for(var entry:next.entrySet())o.add(entry.getKey(),entry.getValue().deepCopy());},false);}
    private static void choose(String label,List<String> choices,String current,Consumer<String> select){if(ImGui.beginCombo(label,current)){for(String item:choices)if(ImGui.selectable(item,current.equals(item)))select.accept(item);ImGui.endCombo();}}
    private boolean tab(String name){int flags=requestedTab.equals(name)?ImGuiTabItemFlags.SetSelected:0;if(flags!=0)requestedTab="";boolean active=ImGui.beginTabItem(name,flags);if(active&&!name.equals(activeTab)){ImGui.setScrollY(0);activeTab=name;}return active;}
}
