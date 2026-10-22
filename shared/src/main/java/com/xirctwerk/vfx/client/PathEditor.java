package com.xirctwerk.vfx.client;

import com.google.gson.*;
import com.xirctwerk.vfx.core.*;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.type.ImBoolean;
import java.util.*;

/** Bounded 3D point editor and XY stroke capture; one stroke is one undoable canonical edit. */
final class PathEditor {
    float canvasX,canvasY,canvasW,canvasH;
    private final List<Effect.Vec> stroke=new ArrayList<>();
    private String error="";
    void render(EditorTools.Frame f){
        var layer=Authoring.layer(f.project().json(),f.selected().iterator().next());
        ImGui.textWrapped("Each particle traverses the spline over its lifetime. Draw an XY stroke or edit 3D points. Speed and forces add motion to the path.");
        if(EditorHints.button("Create path"))edit(f,o->Authoring.set(o,f.selected(),"path",JsonParser.parseString("[[0,0,0],[2,2,0],[4,0,0]]")),false);
        ImGui.sameLine();if(EditorHints.button("Remove path"))edit(f,o->{for(String id:f.selected())Authoring.layer(o,id).remove("path");},false);
        var points=layer.has("path")?layer.getAsJsonArray("path").deepCopy():new JsonArray();var drawn=read(points);
        float x=ImGui.getCursorScreenPosX(),y=ImGui.getCursorScreenPosY(),w=Math.max(80,ImGui.getContentRegionAvailX()),h=150;
        canvasX=x;canvasY=y;canvasW=w;canvasH=h;
        ImGui.invisibleButton("Draw XY path",w,h);boolean active=ImGui.isItemActive();var d=ImGui.getWindowDrawList();d.pushClipRect(x,y,x+w,y+h,true);d.addRectFilled(x,y,x+w,y+h,ImGui.getColorU32(ImGuiCol.FrameBg));d.addLine(x+w/2,y,x+w/2,y+h,ImGui.getColorU32(ImGuiCol.Border));d.addLine(x,y+h/2,x+w,y+h/2,ImGui.getColorU32(ImGuiCol.Border));
        if(ImGui.isItemActivated())stroke.clear();
        if(active&&stroke.size()<64){var p=new Effect.Vec(Math.max(-8,Math.min(8,(ImGui.getMousePosX()-x)/w*16-8)),Math.max(-4,Math.min(4,4-(ImGui.getMousePosY()-y)/h*8)),0);if(stroke.isEmpty()||p.add(stroke.getLast().scale(-1)).length()>.2)stroke.add(p);}
        var visible=active?stroke:drawn;if(visible.size()>1){for(int i=1;i<=128;i++){var a=Motion.sample(visible,(i-1)/128.,false);var b=Motion.sample(visible,i/128.,false);d.addLine(x+(float)(a.x()+8)/16*w,y+(float)(4-a.y())/8*h,x+(float)(b.x()+8)/16*w,y+(float)(4-b.y())/8*h,ImGui.getColorU32(ImGuiCol.PlotLines),2);}}
        for(var p:visible)d.addCircleFilled(x+(float)(p.x()+8)/16*w,y+(float)(4-p.y())/8*h,3,ImGui.getColorU32(ImGuiCol.PlotHistogram));d.popClipRect();EditorHints.tip("Left drag to replace the selected layers' path with an XY stroke. This view spans X -8..8 and Y -4..4 blocks. Use the table for Z or larger coordinates. Release commits once.");
        if(ImGui.isItemDeactivated()&&stroke.size()>=2){var next=write(stroke);edit(f,o->Authoring.set(o,f.selected(),"path",next),false);stroke.clear();}
        if(!points.isEmpty()){
            ImGui.beginChild("Path points",0,150,true);
            for(int i=0;i<points.size();i++){ImGui.pushID(i);var p=points.get(i).getAsJsonArray();float[] xyz={p.get(0).getAsFloat(),p.get(1).getAsFloat(),p.get(2).getAsFloat()};if(ImGui.sliderFloat3("Point "+(i+1),xyz,-128,128,"%.3f",imgui.flag.ImGuiSliderFlags.AlwaysClamp)){var next=points.deepCopy();var value=new JsonArray();for(float n:xyz)value.add(n);next.set(i,value);edit(f,o->Authoring.set(o,f.selected(),"path",next),true);}EditorHints.limits(-128,128);ImGui.popID();}ImGui.endChild();
            if(EditorHints.button("Append point")){var next=points.deepCopy();next.add(points.get(points.size()-1).deepCopy());edit(f,o->Authoring.set(o,f.selected(),"path",next),false);}ImGui.sameLine();if(EditorHints.button("Remove last point")){var next=points.deepCopy();next.remove(next.size()-1);edit(f,o->Authoring.set(o,f.selected(),"path",next),false);}
            var loop=new ImBoolean(layer.has("pathLoop")&&layer.get("pathLoop").getAsBoolean());if(ImGui.checkbox("Loop path",loop))edit(f,o->Authoring.set(o,f.selected(),"pathLoop",new JsonPrimitive(loop.get())),false);EditorHints.tip(EditorHints.description("pathLoop",false));
            for(String field:List.of("pathCycles","pathScatter")){float[] value={layer.has(field)?layer.get(field).getAsFloat():field.equals("pathCycles")?1:0};var bounds=EditorHints.bounds(field);if(ImGui.sliderFloat(field,value,(float)bounds.min(),(float)bounds.max(),"%.3f",imgui.flag.ImGuiSliderFlags.AlwaysClamp))edit(f,o->Authoring.set(o,f.selected(),field,new JsonPrimitive(value[0])),true);EditorHints.tip(EditorHints.description(field,false));EditorHints.limits(bounds.min(),bounds.max());}
        }
        if(!error.isEmpty())ImGui.textWrapped(error);
    }
    private void edit(EditorTools.Frame f,java.util.function.Consumer<JsonObject> edit,boolean continuous){try{f.edit().accept(edit,continuous);error="";}catch(Exception ex){error=ex.getMessage();}}
    private static List<Effect.Vec> read(JsonArray points){var result=new ArrayList<Effect.Vec>();for(var item:points){var p=item.getAsJsonArray();result.add(new Effect.Vec(p.get(0).getAsDouble(),p.get(1).getAsDouble(),p.get(2).getAsDouble()));}return result;}
    private static JsonArray write(List<Effect.Vec> points){var result=new JsonArray();for(var p:points){var a=new JsonArray();a.add(p.x());a.add(p.y());a.add(p.z());result.add(a);}return result;}
}
