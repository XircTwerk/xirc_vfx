package com.xirctwerk.vfx.client;

import com.google.gson.*;
import com.xirctwerk.vfx.core.EffectCodec;
import imgui.ImGui;
import imgui.flag.ImGuiCol;

/** Segment controls shared by property drafts and the timeline. No executable expressions. */
final class EasingEditor {
    private static int activeHandle,activeId;
    static boolean render(JsonObject key){
        boolean changed=false;String mode=key.has("easing")&&key.getAsJsonObject("easing").has("mode")?key.getAsJsonObject("easing").get("mode").getAsString():"linear";
        if(ImGui.beginCombo("To next key",mode)){for(String next:new String[]{"linear","step","smooth","bezier"})if(ImGui.selectable(next,next.equals(mode))){changed=true;mode=next;if(next.equals("linear"))key.remove("easing");else{var e=new JsonObject();e.addProperty("mode",next);if(next.equals("bezier"))e.add("controls",JsonParser.parseString("[0.25,0.1,0.25,1]"));key.add("easing",e);}}ImGui.endCombo();}
        EditorHints.tip("Interpolation from this key to the next. Step holds the value; smooth eases both ends; Bezier exposes draggable normalized handles.");
        if(mode.equals("bezier")){
            var controls=key.getAsJsonObject("easing").getAsJsonArray("controls");float x=ImGui.getCursorScreenPosX(),y=ImGui.getCursorScreenPosY(),w=Math.min(300,ImGui.getContentRegionAvailX()),h=90;var d=ImGui.getWindowDrawList();
            int id=ImGui.getID("Bezier handles");ImGui.invisibleButton("Bezier handles",w,h);EditorHints.tip("Drag either handle. X controls time and Y controls progress, both 0..1. Endpoint values remain bounded.");
            d.addRectFilled(x,y,x+w,y+h,ImGui.getColorU32(ImGuiCol.FrameBg));var easing=EffectCodec.easing(key,"key");
            for(int i=1;i<=64;i++){float a=(i-1)/64f,b=i/64f;d.addLine(x+w*a,y+h-h*(float)easing.at(a),x+w*b,y+h-h*(float)easing.at(b),ImGui.getColorU32(ImGuiCol.PlotLines),2);}
            for(int i=0;i<2;i++){float cx=x+w*controls.get(i*2).getAsFloat(),cy=y+h-h*controls.get(i*2+1).getAsFloat();d.addLine(x+w*i,y+h-h*i,cx,cy,ImGui.getColorU32(ImGuiCol.TextDisabled));d.addCircleFilled(cx,cy,5,ImGui.getColorU32(ImGuiCol.PlotHistogram));}
            if(ImGui.isItemActivated()){activeId=id;double best=Double.MAX_VALUE;for(int i=0;i<2;i++){double distance=Math.hypot(ImGui.getMousePosX()-x-w*controls.get(i*2).getAsFloat(),ImGui.getMousePosY()-y-h+h*controls.get(i*2+1).getAsFloat());if(distance<best){best=distance;activeHandle=i;}}}
            if(ImGui.isItemActive()&&activeId==id&&ImGui.isMouseDragging(0)){controls.set(activeHandle*2,new JsonPrimitive(Math.max(0,Math.min(1,(ImGui.getMousePosX()-x)/w))));controls.set(activeHandle*2+1,new JsonPrimitive(Math.max(0,Math.min(1,1-(ImGui.getMousePosY()-y)/h))));changed=true;}
            float[] c=new float[4];for(int i=0;i<4;i++)c[i]=controls.get(i).getAsFloat();if(ImGui.sliderFloat4("Handles x/y",c,0,1,"%.2f",imgui.flag.ImGuiSliderFlags.AlwaysClamp)){for(int i=0;i<4;i++)controls.set(i,new JsonPrimitive(c[i]));changed=true;}EditorHints.limits(0,1);
        }
        return changed;
    }
}
