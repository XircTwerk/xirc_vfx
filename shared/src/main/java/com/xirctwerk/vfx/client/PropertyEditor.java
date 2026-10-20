package com.xirctwerk.vfx.client;

import com.google.gson.*;
import com.xirctwerk.vfx.core.Keyframes;
import imgui.ImGui;
import imgui.flag.*;
import imgui.type.ImBoolean;
import java.util.*;
import java.util.function.Consumer;

/** A bounded visual draft. Apply validates the complete effect before replacing any authored JSON. */
final class PropertyEditor {
    private String property="",error="";
    private JsonElement draft;
    private Consumer<JsonElement> apply;
    private boolean pending,cancel,showEasing;
    private int point=-1;
    private float plotMin,plotMax;
    private double eventDuration=300;
    void eventDuration(double seconds){eventDuration=seconds;}
    float pointX,pointY,applyX,applyY,addX,addY;
    private static final Set<String> NUMBERS=Set.of("size","speed","rotation","lifetime","meshScale");
    boolean isOpen() {return !property.isEmpty();}
    void cancel() {cancel=true;}
    void open(String key,JsonElement value,Consumer<JsonElement> commit) {
        property=key;draft=value.deepCopy();apply=commit;error="";pending=true;cancel=false;point=-1;
    }
    void curve() {mode("Curve");}
    void smokeEasing(){point=0;showEasing=true;keys().get(0).getAsJsonObject().add("easing",JsonParser.parseString("{\"mode\":\"bezier\",\"controls\":[0.25,0.1,0.25,1]}"));}
    private double min() {return switch(property) {case "size","meshScale"->.001;case "lifetime"->.05;case "speed"->-50;case "rotation"->-36000;default->0;};}
    private double max() {return switch(property) {case "size"->10;case "meshScale"->8;case "lifetime"->30;case "speed"->50;case "rotation"->36000;default->1;};}
    private String mode() {
        if(property.equals("bursts")) return "Events";
        if(draft.isJsonObject()) {var o=draft.getAsJsonObject();return o.has("curve")?"Curve":o.has("gradient")?"Gradient":"Random range";}
        return "Constant";
    }
    private double initial() {
        if(draft.isJsonPrimitive()) return draft.getAsDouble();
        var o=draft.getAsJsonObject();return o.has("curve")?o.getAsJsonArray("curve").get(0).getAsJsonObject().get("value").getAsDouble():o.get("min").getAsDouble();
    }
    private JsonArray color() {return draft.isJsonArray()?draft.getAsJsonArray().deepCopy():draft.getAsJsonObject().getAsJsonArray("gradient").get(0).getAsJsonObject().getAsJsonArray("color").deepCopy();}
    private void mode(String type) {
        if(type.equals(mode())) return;
        if(property.equals("color")) {
            var c=color();if(type.equals("Constant")) {draft=c;return;}
            var keys=new JsonArray();for(int t=0;t<=1;t++) {var key=new JsonObject();key.addProperty("time",t);key.add("color",c.deepCopy());keys.add(key);}var o=new JsonObject();o.add("gradient",keys);draft=o;
        } else {
            double n=initial();if(type.equals("Constant")) {draft=new JsonPrimitive(n);return;}
            var o=new JsonObject();
            if(type.equals("Random range")) {double delta=Math.max(Math.abs(n)*.25,.01);o.addProperty("min",Math.max(min(),n-delta));o.addProperty("max",Math.min(max(),n+delta));}
            else {var keys=new JsonArray();for(int t=0;t<=1;t++) {var k=new JsonObject();k.addProperty("time",t);k.addProperty("value",n);keys.add(k);}o.add("curve",keys);}
            draft=o;
        }
        point=-1;
    }
    private JsonArray keys() {return property.equals("bursts")?draft.getAsJsonArray():draft.getAsJsonObject().getAsJsonArray(property.equals("color")?"gradient":"curve");}
    private static float[] rgba(JsonArray a) {float[] c=new float[4];for(int i=0;i<4;i++) c[i]=i<a.size()?a.get(i).getAsFloat():1;return c;}
    private static JsonArray rgba(float[] color) {var a=new JsonArray();for(float c:color) a.add(c);return a;}
    private static int color32(JsonArray a) {var c=rgba(a);return ImGui.getColorU32(c[0],c[1],c[2],c[3]);}
    void render() {
        if(!isOpen()) return;
        String title="Edit "+property;
        if(pending) {ImGui.openPopup(title);pending=false;}
        var visible=new ImBoolean(true);
        ImGui.setNextWindowSize(Math.min(600,ImGui.getIO().getDisplaySizeX()-32),Math.min(530,ImGui.getIO().getDisplaySizeY()-32),ImGuiCond.Appearing);
        ImGui.setNextWindowSizeConstraints(250,200,Math.max(250,ImGui.getIO().getDisplaySizeX()-16),Math.max(200,ImGui.getIO().getDisplaySizeY()-16));
        if(ImGui.beginPopupModal(title,visible)) {
            if(cancel) {ImGui.closeCurrentPopup();property="";cancel=false;ImGui.endPopup();return;}
            if(NUMBERS.contains(property)||property.equals("color")) {
                ImGui.setNextItemWidth(220);
                if(ImGui.beginCombo("Value type",mode())) {
                    var modes=property.equals("color")?List.of("Constant","Gradient"):property.equals("lifetime")?List.of("Constant","Random range"):List.of("Constant","Random range","Curve");
                    for(String type:modes) if(ImGui.selectable(type,type.equals(mode()))) mode(type);
                    ImGui.endCombo();
                }
            }
            EditorHints.tip("Constants stay fixed; ranges sample each birth; curves and gradients interpolate over particle lifetime.");
            ImGui.textWrapped(property.equals("bursts")?"Birth events use seconds after this layer starts.":"Time runs from birth (0) to the end of a particle's life (1). Only this property changes when you apply.");
            if(mode().equals("Constant")) {
                if(property.equals("color")) {var c=rgba(color());if(ImGui.colorEdit4("Color and opacity",c)) draft=rgba(c);EditorHints.tip(EditorHints.description("color",false));EditorHints.limits(0,255);}
                else {float[] n={(float)initial()};if(ImGui.dragFloat("Value",n,.01f,(float)min(),(float)max())) draft=new JsonPrimitive(n[0]);EditorHints.tip(EditorHints.description(property,false));EditorHints.limits(min(),max());}
            } else if(mode().equals("Random range")) {
                var o=draft.getAsJsonObject();float[] a={o.get("min").getAsFloat()},b={o.get("max").getAsFloat()};
                if(ImGui.dragFloat("Minimum",a,.01f,(float)min(),b[0])) o.addProperty("min",a[0]);EditorHints.tip("Smallest value sampled for a new particle. It cannot exceed Maximum.");EditorHints.limits(min(),b[0]);
                if(ImGui.dragFloat("Maximum",b,.01f,a[0],(float)max())) o.addProperty("max",b[0]);EditorHints.tip("Largest value sampled for a new particle. It cannot be below Minimum.");EditorHints.limits(a[0],max());
                ImGui.textWrapped("Each new particle receives a deterministic random value within this range.");
            } else {
                if(mode().equals("Curve")) graph();
                else if(mode().equals("Gradient")) gradient();
                var keys=keys();
                if(EditorHints.button(property.equals("bursts")?"Add event":"Add key")) add();
                addX=(ImGui.getItemRectMinX()+ImGui.getItemRectMaxX())/2;addY=(ImGui.getItemRectMinY()+ImGui.getItemRectMaxY())/2;
                ImGui.sameLine();if(EditorHints.button("Remove selected")&&point>=0) {
                    int minimum=property.equals("bursts")?0:2;
                    if(keys.size()>minimum) {keys.remove(point);point=Math.min(point,keys.size()-1);}else error="Keep at least two keys.";
                }
                if(!property.equals("bursts")){ImGui.sameLine();if(EditorHints.button("Interpolation..."))showEasing=true;
                    if(showEasing){point=Math.max(0,Math.min(keys.size()-2,point));ImGui.openPopup("Segment interpolation");showEasing=false;}
                    ImGui.setNextWindowSize(340,260,ImGuiCond.Appearing);ImGui.setNextWindowPos(Math.max(8,(ImGui.getIO().getDisplaySizeX()-340)/2),Math.max(24,(ImGui.getIO().getDisplaySizeY()-280)/2),ImGuiCond.Appearing);if(ImGui.beginPopup("Segment interpolation")){ImGui.text("Key "+(point+1)+" to key "+(point+2));EasingEditor.render(keys.get(point).getAsJsonObject());if(EditorHints.button("Done"))ImGui.closeCurrentPopup();ImGui.endPopup();}}
                ImGui.beginChild("Keys",0,Math.max(80,Math.min(170,ImGui.getContentRegionAvailY()-85)),true);
                for(int i=0;i<keys.size();i++) {
                    ImGui.pushID(i);var k=keys.get(i).getAsJsonObject();
                    if(ImGui.selectable("Key "+(i+1),point==i,0,70,0)) point=i;ImGui.sameLine();
                    ImGui.setNextItemWidth(90);float[] t={k.get("time").getAsFloat()};
                    float lo=property.equals("bursts")?0:i==0?0:keys.get(i-1).getAsJsonObject().get("time").getAsFloat()+.0001f;
                    float hi=property.equals("bursts")?(float)eventDuration:i==keys.size()-1?1:keys.get(i+1).getAsJsonObject().get("time").getAsFloat()-.0001f;
                    if(ImGui.dragFloat("Time",t,.005f,lo,hi,"%.3f")) k.addProperty("time",t[0]);EditorHints.tip(property.equals("bursts")?"Birth time in seconds after the layer starts.":"Normalized particle age from birth (0) to death (1). Keys must remain ordered.");ImGui.sameLine();
                    if(property.equals("color")) {float[] c=rgba(k.getAsJsonArray("color"));if(ImGui.colorEdit4("##rgba",c,ImGuiColorEditFlags.NoInputs|ImGuiColorEditFlags.AlphaBar|ImGuiColorEditFlags.AlphaPreviewHalf)) {k.add("color",rgba(c));point=i;}}
                    else if(property.equals("bursts")) {int[] n={k.get("count").getAsInt()};ImGui.setNextItemWidth(100);if(ImGui.dragInt("Count",n,1,1,2000)) k.addProperty("count",n[0]);}
                    else {float[] n={k.get("value").getAsFloat()};ImGui.setNextItemWidth(100);if(ImGui.dragFloat("Value",n,.01f,(float)min(),(float)max(),"%.3f")) k.addProperty("value",n[0]);}
                    EditorHints.tip(property.equals("color")?EditorHints.description("color",false):property.equals("bursts")?"How many particles this one-shot event emits. The layer supports at most 4000 total burst births.":EditorHints.description(property,false));
                    EditorHints.limits(property.equals("color")?0:property.equals("bursts")?1:min(),property.equals("color")?255:property.equals("bursts")?2000:max());
                    ImGui.textDisabled("Time: "+EditorHints.rangeText(lo,hi));
                    ImGui.popID();
                }
                ImGui.endChild();
            }
            ImGui.separator();
            if(EditorHints.button("Apply",90,0)) {
                try {apply.accept(draft.deepCopy());ImGui.closeCurrentPopup();property="";}catch(RuntimeException ex) {error=ex.getMessage();}
            }
            applyX=(ImGui.getItemRectMinX()+ImGui.getItemRectMaxX())/2;applyY=(ImGui.getItemRectMinY()+ImGui.getItemRectMaxY())/2;
            ImGui.sameLine();if(EditorHints.button("Cancel",90,0)) {ImGui.closeCurrentPopup();property="";}
            if(!error.isEmpty()) ImGui.textWrapped(error);
            ImGui.endPopup();
        }
        if(!visible.get()) property="";
    }
    private void add() {
        var a=keys();if(a.size()>=64) {error="A property supports at most 64 keys/events.";return;}
        var key=new JsonObject();
        if(property.equals("bursts")) {key.addProperty("time",a.isEmpty()?0:a.get(a.size()-1).getAsJsonObject().get("time").getAsDouble());key.addProperty("count",10);a.add(key);point=a.size()-1;return;}
        int gap=0;double largest=-1;
        for(int i=0;i<a.size()-1;i++) {double diff=a.get(i+1).getAsJsonObject().get("time").getAsDouble()-a.get(i).getAsJsonObject().get("time").getAsDouble();if(diff>largest) {largest=diff;gap=i;}}
        if(largest<.0002) {error="Move existing keys apart before adding another.";return;}
        var left=a.get(gap).getAsJsonObject();var right=a.get(gap+1).getAsJsonObject();key.addProperty("time",(left.get("time").getAsDouble()+right.get("time").getAsDouble())/2);
        if(property.equals("color")) {var c=new JsonArray();float[] l=rgba(left.getAsJsonArray("color")),r=rgba(right.getAsJsonArray("color"));for(int i=0;i<4;i++) c.add((l[i]+r[i])/2);key.add("color",c);}
        else key.addProperty("value",(left.get("value").getAsDouble()+right.get("value").getAsDouble())/2);
        var result=new JsonArray();for(int i=0;i<a.size();i++) {result.add(a.get(i));if(i==gap) result.add(key);}draft.getAsJsonObject().add(property.equals("color")?"gradient":"curve",result);point=gap+1;
    }
    private void gradient() {
        var a=keys();float x=ImGui.getCursorScreenPosX(),y=ImGui.getCursorScreenPosY(),w=ImGui.getContentRegionAvailX();var d=ImGui.getWindowDrawList();
        for(int i=0;i<w;i+=8) for(int j=0;j<34;j+=8) d.addRectFilled(x+i,y+j,x+Math.min(w,i+8),y+Math.min(34,j+8),((i/8+j/8)%2==0)?0xff555555:0xff333333);
        var first=a.get(0).getAsJsonObject();var last=a.get(a.size()-1).getAsJsonObject();
        d.addRectFilled(x,y,x+w*first.get("time").getAsFloat(),y+34,color32(first.getAsJsonArray("color")));
        d.addRectFilled(x+w*last.get("time").getAsFloat(),y,x+w,y+34,color32(last.getAsJsonArray("color")));
        var layer=new JsonObject();layer.add("color",draft);for(int i=0;i<w;i+=3){int c=color32(Keyframes.sample(layer,"color",i/w).getAsJsonArray());d.addRectFilled(x+i,y,x+Math.min(w,i+3),y+34,c);}
        ImGui.dummy(w,40);
    }
    private void graph() {
        var a=keys();float minValue=Float.MAX_VALUE,maxValue=-Float.MAX_VALUE;
        for(var item:a) {float n=item.getAsJsonObject().get("value").getAsFloat();minValue=Math.min(minValue,n);maxValue=Math.max(maxValue,n);}
        // Keep the scale stable throughout a drag so the point doesn't chase its own autoscaling.
        if(!ImGui.isMouseDown(0)||point<0) {float pad=Math.max(.05f,(maxValue-minValue)*.2f);plotMin=Math.max((float)min(),minValue-pad);plotMax=Math.min((float)max(),maxValue+pad);if(plotMax<=plotMin) plotMax=plotMin+.05f;}
        float x=ImGui.getCursorScreenPosX(),y=ImGui.getCursorScreenPosY(),w=Math.max(50,ImGui.getContentRegionAvailX()),h=110;var d=ImGui.getWindowDrawList();
        ImGui.invisibleButton("Curve canvas",w,h);
        boolean hovered=ImGui.isItemHovered(),active=ImGui.isItemActive();EditorHints.tip("Drag a point to change its time and value. Key times stay ordered. Apply commits the draft; Cancel discards it.");
        d.pushClipRect(x,y,x+w,y+h,true);d.addRectFilled(x,y,x+w,y+h,0xff201914);for(int i=1;i<5;i++) {d.addLine(x+w*i/5,y,x+w*i/5,y+h,0xff493d32);d.addLine(x,y+h*i/5,x+w,y+h*i/5,0xff493d32);}
        float lastX=0,lastY=0;
        var layer=new JsonObject();layer.add(property,draft);for(int i=1;i<=128;i++){float left=(i-1)/128f,right=i/128f;float ly=y+h-h*(Keyframes.sample(layer,property,left).getAsFloat()-plotMin)/(plotMax-plotMin),ry=y+h-h*(Keyframes.sample(layer,property,right).getAsFloat()-plotMin)/(plotMax-plotMin);d.addLine(x+w*left,ly,x+w*right,ry,0xffffaa88,2);}
        for(int i=0;i<a.size();i++) {var k=a.get(i).getAsJsonObject();float cx=x+w*k.get("time").getAsFloat(),cy=y+h-h*(k.get("value").getAsFloat()-plotMin)/(plotMax-plotMin);
            d.addCircleFilled(cx,cy,5,i==point?0xffffffff:0xffffaa88);lastX=cx;lastY=cy;
            if(i==0) {pointX=cx;pointY=cy;}
            if(hovered&&ImGui.isMouseClicked(0)&&Math.hypot(ImGui.getMousePosX()-cx,ImGui.getMousePosY()-cy)<12) point=i;
        }
        if(active&&point>=0&&ImGui.isMouseDragging(0)) {var k=a.get(point).getAsJsonObject();double lo=point==0?0:a.get(point-1).getAsJsonObject().get("time").getAsDouble()+.0001,hi=point==a.size()-1?1:a.get(point+1).getAsJsonObject().get("time").getAsDouble()-.0001;k.addProperty("time",Math.max(lo,Math.min(hi,(ImGui.getMousePosX()-x)/w)));k.addProperty("value",Math.max(min(),Math.min(max(),plotMin+(1-(ImGui.getMousePosY()-y)/h)*(plotMax-plotMin))));}
        d.addLine(lastX,lastY,x+w,lastY,0xffffaa88,2);d.popClipRect();
        ImGui.textDisabled("Drag a point. Birth to end of life · "+String.format(Locale.ROOT,"%.3f to %.3f",plotMin,plotMax));
    }
}
