package com.xirctwerk.vfx.client;

import com.google.gson.*;
import com.xirctwerk.vfx.core.*;
import imgui.ImGui;
import imgui.flag.*;
import imgui.type.ImBoolean;
import java.util.function.*;

/** Optional dockable timeline. All commits validate through Project; no second animation format. */
final class TimelineEditor {
    final ImBoolean open=new ImBoolean(false);
    private String track="size",layerId="",error="";
    private int key=-1;
    private float age=.5f;
    private boolean gesture;
    private boolean requestMap;
    void smokeMap(){requestMap=true;}
    float addX,addY,keyX,keyY;
    void smokeOpen(){open.set(true);track="size";key=-1;age=.37f;}
    void smokeColor(){track="color";key=0;}

    void finish(Project project){if(gesture){project.endEdit();gesture=false;}}
    private void edit(Project project,Consumer<JsonObject> change,Runnable changed,boolean continuous){
        try {if(!continuous)finish(project);else if(!gesture){project.beginEdit();gesture=true;}project.edit(change);changed.run();error="";}
        catch(RuntimeException ex){error=ex.getMessage();}
    }
    private static JsonObject layer(JsonObject json,int index){return json.getAsJsonArray("emitters").get(index).getAsJsonObject();}
    void render(Project project,int selected,IntConsumer select,Runnable changed,DoubleConsumer seek,double seconds,double horizon,boolean seeking,BiConsumer<String,JsonElement> editCurve){
        if(!open.get()){finish(project);return;}
        ImGui.setNextWindowSize(Math.min(740,ImGui.getIO().getDisplaySizeX()-24),Math.min(450,ImGui.getIO().getDisplaySizeY()-40),ImGuiCond.FirstUseEver);
        if(ImGui.begin("Timeline",open)) {
            var json=project.json();var e=layer(json,selected);String id=e.get("id").getAsString();if(!id.equals(layerId)){finish(project);layerId=id;key=-1;}
            float[] clock={(float)seconds};ImGui.setNextItemWidth(Math.max(110,ImGui.getContentRegionAvailX()-150));
            if(ImGui.sliderFloat("Preview seconds",clock,0,(float)horizon,"%.2f s")){finish(project);seek.accept(clock[0]);}
            EditorHints.tip("Pause and replay the preview at this second. Scrubbing uses fixed simulation ticks and leaves authored JSON unchanged.");EditorHints.limits(0,horizon);
            if(json.has("markers")){var markers=json.getAsJsonArray("markers");for(int i=0;i<markers.size();i++){ImGui.pushID("marker:"+i);var marker=markers.get(i).getAsJsonObject();if(EditorHints.smallButton(marker.get("name").getAsString()+" @ "+marker.get("time").getAsString()+"s"))seek.accept(marker.get("time").getAsDouble());EditorHints.tip("Seek to this named effect-clock beat. Edit or remove markers in Tools > Animation.");ImGui.popID();}}
            if(seeking)ImGui.textDisabled("Rebuilding preview... (20 Hz)");
            if(ImGui.beginTabBar("Timeline modes")) {
                if(ImGui.beginTabItem("Particle keyframes")) {
                    ImGui.textWrapped("Particle lifetime: birth to death (0 to 100 percent). Drag diamonds to retime. Selected keys expose interpolation controls.");
                    ImGui.setNextItemWidth(130);
                    if(ImGui.beginCombo("Track",track)){for(String name:Keyframes.TRACKS)if(!name.equals("meshScale")||Geometry.supports(project.effect().emitters().get(selected).kind()))if(ImGui.selectable(name,track.equals(name))){finish(project);track=name;key=-1;}ImGui.endCombo();}
                    if(track.equals("meshScale")&&!Geometry.supports(project.effect().emitters().get(selected).kind())){track="size";key=-1;}
                    ImGui.sameLine();if(EditorHints.button("Edit curve...")){finish(project);editCurve.accept(track,e.has(track)?e.get(track):Keyframes.sample(e,track,0));}
                    float[] cursor={age*100};ImGui.setNextItemWidth(Math.max(100,ImGui.getContentRegionAvailX()-90));if(ImGui.sliderFloat("Key cursor",cursor,0,100,"%.1f%%"))age=cursor[0]/100;EditorHints.tip("Choose where to insert a key within each particle life, from birth to death. This is separate from Preview seconds.");EditorHints.limits(0,100);
                    lanes(e,project,selected,changed);
                    if(EditorHints.button("Add key at cursor")){finish(project);String property=track;edit(project,o->{var l=layer(o,selected);Keyframes.put(l,property,age,Keyframes.sample(l,property,age));},changed,false);key=nearest(Keyframes.keys(layer(project.json(),selected),track),age);}
                    addX=(ImGui.getItemRectMinX()+ImGui.getItemRectMaxX())/2;addY=(ImGui.getItemRectMinY()+ImGui.getItemRectMaxY())/2;
                    ImGui.sameLine();if(EditorHints.button("Delete key")&&key>=0){int index=key;String property=track;edit(project,o->Keyframes.remove(layer(o,selected),property,index),changed,false);key=-1;}
                    var keys=Keyframes.keys(layer(project.json(),selected),track);
                    if(key>=0&&key<keys.size()) {
                        var k=keys.get(key).getAsJsonObject();double time=k.get("time").getAsDouble();
                        float[] t={(float)time*100};ImGui.setNextItemWidth(130);float lo=key==0?0:keys.get(key-1).getAsJsonObject().get("time").getAsFloat()*100+.002f,hi=key==keys.size()-1?100:keys.get(key+1).getAsJsonObject().get("time").getAsFloat()*100-.002f;
                        if(ImGui.sliderFloat("Key time (%)",t,lo,hi,"%.2f",imgui.flag.ImGuiSliderFlags.AlwaysClamp)){int index=key;String property=track;double next=Math.max(lo,Math.min(hi,t[0]))/100.;edit(project,o->Keyframes.move(layer(o,selected),property,index,next),changed,true);age=(float)next;}EditorHints.tip("Retime the selected key in percent of particle lifetime. Limits are constrained by its neighbouring keys.");
                        ImGui.sameLine();
                        if(track.equals("color"))ImGui.setNextItemWidth(Math.max(90,ImGui.getContentRegionAvailX()-145));
                        if(track.equals("color")){var a=k.getAsJsonArray("color");float[] color=new float[4];for(int i=0;i<4;i++)color[i]=i<a.size()?a.get(i).getAsFloat():1;if(ImGui.colorEdit4("Key color / opacity",color,imgui.flag.ImGuiColorEditFlags.Float)){var value=new JsonArray();for(float n:color)value.add(n);edit(project,o->Keyframes.put(layer(o,selected),"color",time,value),changed,true);}}
                        else {float[] value={k.get("value").getAsFloat()};ImGui.setNextItemWidth(130);float min=track.equals("speed")?-50:track.equals("rotation")?-36000:.001f,max=track.equals("speed")?50:track.equals("rotation")?36000:track.equals("meshScale")?8:10;
                            if(ImGui.sliderFloat("Key value",value,min,max,"%.3f",imgui.flag.ImGuiSliderFlags.AlwaysClamp)){String property=track;edit(project,o->Keyframes.put(layer(o,selected),property,time,new JsonPrimitive(value[0])),changed,true);}}
                        EditorHints.tip(track.equals("color")?EditorHints.description("color",false):EditorHints.description(track,false));
                        var limits=EditorHints.bounds(track);EditorHints.limits(limits.min(),limits.max());ImGui.textDisabled("Key time: "+EditorHints.rangeText(lo,hi));
                        if(key<keys.size()-1&&EasingEditor.render(k)){int index=key;String property=track;var easing=k.has("easing")?k.get("easing").deepCopy():null;edit(project,o->{var target=layer(o,selected).getAsJsonObject(property).getAsJsonArray(property.equals("color")?"gradient":"curve").get(index).getAsJsonObject();if(easing==null)target.remove("easing");else target.add("easing",easing);},changed,true);}
                    }else ImGui.textDisabled("Select a diamond, or add a key. Random ranges require explicit conversion in Edit curve.");
                    ImGui.endTabItem();
                }
                if(ImGui.beginTabItem("Composition map",requestMap?ImGuiTabItemFlags.SetSelected:0)) {
                    requestMap=false;
                    timingMap(project,selected,select,seek,seconds);
                    ImGui.endTabItem();
                }
                if(ImGui.beginTabItem("Layer sequence")) {
                    ImGui.textWrapped("Emission times use seconds. Click a layer to select it; bars show start and duration. Burst events remain in Emission > Bursts.");
                    float width=Math.max(80,ImGui.getContentRegionAvailX()-145),x=ImGui.getCursorScreenPosX()+140;
                    var effects=project.effect().emitters();double end=Math.max(.05,project.effect().duration());
                    for(int i=0;i<effects.size();i++){var item=effects.get(i);ImGui.pushID(i);if(ImGui.selectable(item.id(),i==selected,0,130,20)){finish(project);select.accept(i);key=-1;}float y=ImGui.getItemRectMinY()+4;var d=ImGui.getWindowDrawList();float a=x+(float)(Math.min(end,item.start())/end)*width,b=x+(float)(Math.min(end,item.start()+item.duration())/end)*width;d.addRectFilled(a,y,Math.max(a+2,b),y+12,ImGui.getColorU32(i==selected?ImGuiCol.ButtonHovered:ImGuiCol.Button));d.addLine(x+(float)(Math.min(end,seconds)/end)*width,y-4,x+(float)(Math.min(end,seconds)/end)*width,y+16,ImGui.getColorU32(ImGuiCol.PlotLines));ImGui.popID();}
                    float[] start={(float)project.effect().emitters().get(selected).start()},duration={(float)project.effect().emitters().get(selected).duration()};
                    if(ImGui.sliderFloat("Layer start (s)",start,0,300,"%.3f",imgui.flag.ImGuiSliderFlags.AlwaysClamp)){edit(project,o->layer(o,selected).addProperty("start",start[0]),changed,true);}EditorHints.tip(EditorHints.description("start",false));EditorHints.limits(0,300);
                    if(ImGui.sliderFloat("Layer duration (s)",duration,.05f,300,"%.3f",imgui.flag.ImGuiSliderFlags.AlwaysClamp)){edit(project,o->layer(o,selected).addProperty("duration",duration[0]),changed,true);}EditorHints.tip(EditorHints.description("duration",false));EditorHints.limits(.05,300);
                    ImGui.endTabItem();
                }
                ImGui.endTabBar();
            }
            if(!error.isEmpty())ImGui.textWrapped(error);
            if(!ImGui.isAnyItemActive())finish(project);
        }ImGui.end();if(!open.get())finish(project);
    }
    private void timingMap(Project project,int selected,IntConsumer select,DoubleConsumer seek,double seconds) {
        ImGui.textWrapped("Emission bars, particle tails and burst beats share the effect clock. Click a row to select its layer and seek. Looping layers repeat on the next cycle.");
        var effect=project.effect();double end=effect.duration();
        for(var emitter:effect.emitters())if(emitter.start()<effect.duration())end=Math.max(end,Math.min(effect.duration(),emitter.start()+emitter.duration())+emitter.lifetime().max());
        float x=ImGui.getCursorScreenPosX(),y=ImGui.getCursorScreenPosY(),width=Math.max(200,ImGui.getContentRegionAvailX()),left=x+130,span=Math.max(40,width-142);
        var draw=ImGui.getWindowDrawList();
        for(int tick=0;tick<=8;tick++){float px=left+span*tick/8;draw.addText(px,y,ImGui.getColorU32(ImGuiCol.TextDisabled),String.format(java.util.Locale.ROOT,"%.1fs",end*tick/8));}
        ImGui.dummy(width,20);
        for(int index=0;index<effect.emitters().size();index++){
            var emitter=effect.emitters().get(index);ImGui.pushID("map:"+index);float rowY=ImGui.getCursorScreenPosY();
            ImGui.invisibleButton("Timing row",width,26);boolean hovered=ImGui.isItemHovered();
            if(hovered&&ImGui.isMouseClicked(0)){finish(project);select.accept(index);seek.accept(Math.max(0,Math.min(end,(ImGui.getMousePosX()-left)/span*end)));}
            int text=ImGui.getColorU32(index==selected?ImGuiCol.PlotHistogram:ImGuiCol.Text);
            String label=emitter.id().length()>17?emitter.id().substring(0,16)+"…":emitter.id();draw.addText(x,rowY+6,text,label);
            double cutoff=Math.min(effect.duration(),emitter.start()+emitter.duration());boolean active=emitter.start()<cutoff;
            float start=left+(float)(Math.min(end,emitter.start())/end)*span,stop=left+(float)(cutoff/end)*span,tail=left+(float)((cutoff+emitter.lifetime().max())/end)*span;
            if(active){
            draw.addRectFilled(start,rowY+5,Math.max(start+1,stop),rowY+20,ImGui.getColorU32(ImGuiCol.ButtonHovered));
            draw.addRectFilled(stop,rowY+9,Math.max(stop+1,tail),rowY+16,ImGui.getColorU32(ImGuiCol.Button));
            for(var burst:emitter.bursts()){if(emitter.start()+burst.time()>=cutoff)continue;float px=left+(float)((emitter.start()+burst.time())/end)*span;draw.addLine(px,rowY+2,px,rowY+23,ImGui.getColorU32(ImGuiCol.PlotHistogram),2);}
            }else draw.addText(left,rowY+6,ImGui.getColorU32(ImGuiCol.TextDisabled),"Beyond playback cutoff");
            float playhead=left+(float)(Math.min(end,seconds)/end)*span;draw.addLine(playhead,rowY,playhead,rowY+26,ImGui.getColorU32(ImGuiCol.PlotLines));
            if(hovered)ImGui.setTooltip(emitter.id()+"\nEmission: "+(active?emitter.start()+"–"+cutoff:"none before cutoff")+" s\nMaximum tail: "+(active?cutoff+emitter.lifetime().max():0)+" s\nBursts: "+emitter.bursts().size()+"\nTail is a conservative lifetime bound; fades and collisions can shorten visibility.");
            ImGui.popID();
        }
        var json=project.json();if(json.has("markers"))for(var item:json.getAsJsonArray("markers")){var marker=item.getAsJsonObject();double time=marker.get("time").getAsDouble();if(EditorHints.smallButton(marker.get("name").getAsString()+" · "+time+" s"))seek.accept(time);}
        ImGui.textDisabled("Thick bar = emission · thin bar = maximum tail · gold line = burst · cursor = preview time");
    }
    private static int nearest(JsonArray keys,double time){for(int i=0;i<keys.size();i++)if(Math.abs(keys.get(i).getAsJsonObject().get("time").getAsDouble()-time)<.00001)return i;return -1;}
    private void lanes(JsonObject layer,Project project,int selected,Runnable changed){
        float x=ImGui.getCursorScreenPosX(),y=ImGui.getCursorScreenPosY(),w=Math.max(150,ImGui.getContentRegionAvailX());
        float left=x+85,right=x+w-12,span=Math.max(1,right-left);var d=ImGui.getWindowDrawList();
        for(int row=0;row<Keyframes.TRACKS.size();row++){
            String property=Keyframes.TRACKS.get(row);float cy=y+row*20+10;var keys=Keyframes.keys(layer,property);
            d.addText(x,cy-7,ImGui.getColorU32(ImGuiCol.Text),property);d.addLine(left,cy,right,cy,ImGui.getColorU32(ImGuiCol.Border));
            for(int i=0;i<keys.size();i++){float cx=left+keys.get(i).getAsJsonObject().get("time").getAsFloat()*span;int color=ImGui.getColorU32(property.equals(track)&&key==i?ImGuiCol.PlotHistogram:ImGuiCol.ButtonHovered);d.addQuadFilled(cx,cy-5,cx+5,cy,cx,cy+5,cx-5,cy,color);if(property.equals(track)&&key==i){keyX=cx;keyY=cy;}}
        }
        d.addLine(left+age*span,y,left+age*span,y+100,ImGui.getColorU32(ImGuiCol.PlotLines));
        ImGui.invisibleButton("Keyframe lanes",w,100);EditorHints.tip("Click a diamond to select a key, drag it horizontally to retime, or click a track to position the lifetime cursor.");
        if(ImGui.isItemHovered()&&ImGui.isMouseClicked(0)){int row=Math.max(0,Math.min(4,(int)((ImGui.getMousePosY()-y)/20)));track=Keyframes.TRACKS.get(row);var keys=Keyframes.keys(layer,track);key=-1;float distance=9;for(int i=0;i<keys.size();i++){float dx=Math.abs(ImGui.getMousePosX()-(left+keys.get(i).getAsJsonObject().get("time").getAsFloat()*span));if(dx<distance){distance=dx;key=i;}}age=Math.max(0,Math.min(1,(ImGui.getMousePosX()-left)/span));}
        if(ImGui.isItemActive()&&ImGui.isMouseDragging(0)&&key>=0){var keys=Keyframes.keys(layer,track);if(key<keys.size()){double lo=key==0?0:keys.get(key-1).getAsJsonObject().get("time").getAsDouble()+.00002,hi=key==keys.size()-1?1:keys.get(key+1).getAsJsonObject().get("time").getAsDouble()-.00002;double time=Math.max(lo,Math.min(hi,(ImGui.getMousePosX()-left)/span));int index=key;String property=track;edit(project,o->Keyframes.move(layer(o,selected),property,index,time),changed,true);age=(float)time;}}
    }
}
