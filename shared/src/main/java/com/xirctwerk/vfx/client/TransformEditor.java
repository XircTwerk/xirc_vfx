package com.xirctwerk.vfx.client;



import com.google.gson.*;

import com.xirctwerk.vfx.core.*;

import imgui.ImGui;

import java.util.*;

import java.util.function.Consumer;



/** Whole-layer transforms, deliberately separate from normalized particle-age curves. */

final class TransformEditor {

    private int selectedKey;

    float scaleX,scaleY,groupX,groupY;

    int frames;

    private String layerId="",error="";

    private void edit(EditorTools.Frame frame,Consumer<JsonObject> change,boolean continuous){

        try{frame.edit().accept(change,continuous);error="";}catch(RuntimeException ex){error=ex.getMessage();}

    }

    private static JsonObject transform(JsonObject layer){

        if(!layer.has("transform"))layer.add("transform",new JsonObject());return layer.getAsJsonObject("transform");

    }

    private static JsonArray vector(double x,double y,double z){var array=new JsonArray();array.add(x);array.add(y);array.add(z);return array;}

    private static float[] vector(JsonObject object,String field){if(!object.has(field))return new float[3];var a=object.getAsJsonArray(field);return new float[]{a.get(0).getAsFloat(),a.get(1).getAsFloat(),a.get(2).getAsFloat()};}

    void render(EditorTools.Frame frame){

        frames++;

        if(EditorHints.button("Group selected layers under a transform")){

            final String[] grouped={null};edit(frame,o->grouped[0]=Authoring.group(o,frame.selected()),false);if(grouped[0]!=null){frame.select().accept(Set.of(grouped[0]));return;}

        }groupX=(ImGui.getItemRectMinX()+ImGui.getItemRectMaxX())/2;groupY=(ImGui.getItemRectMinY()+ImGui.getItemRectMaxY())/2;EditorHints.tip("Create a non-emitting identity parent for selected sibling layers, preserving their appearance and animation. Selected descendants inherit once. Rotate or scale the new parent to transform the group.");

        String id=frame.selected().iterator().next();if(!id.equals(layerId)){selectedKey=0;layerId=id;}

        var layer=Authoring.layer(frame.project().json(),id);var object=transform(layer);

        ImGui.textWrapped("Parent rotation and uniform scale affect child layers. Local particles follow animated transforms; world particles retain their birth transform. Keys use effect-clock seconds.");

        float[] rotation=vector(object,"rotation"),scale={object.has("scale")?object.get("scale").getAsFloat():1};

        if(ImGui.sliderFloat3("Layer rotation (XYZ degrees)",rotation,-36000,36000,"%.3f",imgui.flag.ImGuiSliderFlags.AlwaysClamp))edit(frame,o->Authoring.setTransform(o,frame.selected(),"rotation",vector(rotation[0],rotation[1],rotation[2])),true);EditorHints.limits(-36000,36000);EditorHints.tip("Euler rotation: X, then Y, then Z. Child translations and directions rotate with the parent.");

        if(ImGui.sliderFloat("Layer scale",scale,.01f,8,"%.3f",imgui.flag.ImGuiSliderFlags.AlwaysClamp))edit(frame,o->Authoring.setTransform(o,frame.selected(),"scale",new JsonPrimitive(scale[0])),true);scaleX=(ImGui.getItemRectMinX()+ImGui.getItemRectMaxX())/2;scaleY=(ImGui.getItemRectMinY()+ImGui.getItemRectMaxY())/2;EditorHints.limits(.01,8);EditorHints.tip("Uniform transform scale affects positions, sprite sizes, mesh extents and beam length. Combined hierarchy scale must stay between 0.0001 and 64.");

        ImGui.separator();ImGui.text("Animated layer: "+id);

        String mode=object.has("pathInterpolation")?object.get("pathInterpolation").getAsString():"linear";

        if(ImGui.beginCombo("Position interpolation",mode)){for(String value:List.of("linear","spline"))if(ImGui.selectable(value,mode.equals(value)))edit(frame,o->transform(Authoring.layer(o,id)).addProperty("pathInterpolation",value),false);ImGui.endCombo();}

        EditorHints.tip("Spline uses a smooth Catmull-Rom path through layer position keys. Rotation and scale retain their segment easing. Spline paths may curve beyond individual positions.");

        var keys=object.has("keys")?object.getAsJsonArray("keys"):new JsonArray();

        if(keys.isEmpty()){

            if(EditorHints.button("Animate layer transform"))edit(frame,o->{var a=new JsonArray();var first=new JsonObject();first.addProperty("time",0);a.add(first);var last=new JsonObject();last.addProperty("time",1);a.add(last);transform(Authoring.layer(o,id)).add("keys",a);},false);

        }else{

            selectedKey=Math.max(0,Math.min(selectedKey,keys.size()-1));

            timeline(frame,id,keys);

            if(ImGui.beginCombo("Transform key",(selectedKey+1)+" / "+keys.size())){for(int i=0;i<keys.size();i++)if(ImGui.selectable((i+1)+" · "+keys.get(i).getAsJsonObject().get("time").getAsString()+" s",i==selectedKey))selectedKey=i;ImGui.endCombo();}

            var key=keys.get(selectedKey).getAsJsonObject();int index=selectedKey;

            float[] time={key.get("time").getAsFloat()},position=vector(key,"position"),angles=vector(key,"rotation"),factor={key.has("scale")?key.get("scale").getAsFloat():1};

            float lo=index==0?0:keys.get(index-1).getAsJsonObject().get("time").getAsFloat()+.0001f,hi=index==keys.size()-1?631:keys.get(index+1).getAsJsonObject().get("time").getAsFloat()-.0001f;

            if(ImGui.sliderFloat("Effect second",time,lo,hi,"%.3f",imgui.flag.ImGuiSliderFlags.AlwaysClamp))editKey(frame,id,index,k->k.addProperty("time",Math.max(lo,Math.min(hi,time[0]))));EditorHints.limits(lo,hi);

            if(ImGui.sliderFloat3("Key position (blocks)",position,-128,128,"%.3f",imgui.flag.ImGuiSliderFlags.AlwaysClamp))editKey(frame,id,index,k->k.add("position",vector(position[0],position[1],position[2])));EditorHints.limits(-128,128);

            if(ImGui.sliderFloat3("Key rotation (XYZ)",angles,-36000,36000,"%.3f",imgui.flag.ImGuiSliderFlags.AlwaysClamp))editKey(frame,id,index,k->k.add("rotation",vector(angles[0],angles[1],angles[2])));EditorHints.limits(-36000,36000);

            if(ImGui.sliderFloat("Key scale multiplier",factor,.01f,8,"%.3f",imgui.flag.ImGuiSliderFlags.AlwaysClamp))editKey(frame,id,index,k->k.addProperty("scale",factor[0]));EditorHints.limits(.01,8);

            if(index<keys.size()-1&&EasingEditor.render(key)){var easing=key.has("easing")?key.get("easing").deepCopy():null;editKey(frame,id,index,k->{if(easing==null)k.remove("easing");else k.add("easing",easing);});}

            if(EditorHints.button("Seek to transform key"))frame.seek().accept(time[0]);

            ImGui.sameLine();if(EditorHints.button("Delete transform key"))edit(frame,o->{var target=transform(Authoring.layer(o,id));if(keys.size()<=2)target.remove("keys");else target.getAsJsonArray("keys").remove(index);},false);

            EditorHints.tip("Removing one of the last two keys removes the animation; static rotation and scale stay authored. Undo restores the track.");

        }

        if(EditorHints.button("Add transform key at preview time")){

            double seconds=frame.project().effect().loop()?frame.simulation().time()%frame.project().effect().duration():frame.simulation().time();

            edit(frame,o->{

                if(seconds>631)throw new IllegalArgumentException("Transform keys are limited to 631 effect-clock seconds.");

                var emitter=frame.project().effect().emitters().stream().filter(e->e.id().equals(id)).findFirst().orElseThrow();var pose=emitter.transform().at(Effect.Vec.ZERO,seconds);

                var target=transform(Authoring.layer(o,id));var a=target.has("keys")?target.getAsJsonArray("keys"):new JsonArray();

                for(var existing:a)if(Math.abs(existing.getAsJsonObject().get("time").getAsDouble()-seconds)<.0001)throw new IllegalArgumentException("A transform key already exists here; edit that key or move the preview time.");

                if(a.isEmpty()){var initial=new JsonObject();initial.addProperty("time",seconds==0?1:0);a.add(initial);}

                var k=new JsonObject();k.addProperty("time",seconds);k.add("position",vector(pose.position().x(),pose.position().y(),pose.position().z()));

                // Sample the canonical track so insertion preserves authored rotation, including full turns.

                var sampled=sampleKey(emitter.transform(),seconds);k.add("rotation",sampled.get("rotation"));k.addProperty("scale",pose.scale()/emitter.transform().scale());a.add(k);

                var sorted=new ArrayList<JsonElement>();a.forEach(sorted::add);sorted.sort(Comparator.comparingDouble(v->v.getAsJsonObject().get("time").getAsDouble()));var ordered=new JsonArray();sorted.forEach(ordered::add);target.add("keys",ordered);

            },false);

        }

        EditorHints.tip("Insert a key sampled from the current transform. Edits apply atomically to the active layer and retain unrelated particle curves and resources.");

        if(!error.isEmpty())ImGui.textWrapped(error);

    }

    private void timeline(EditorTools.Frame frame,String id,JsonArray keys){

        float x=ImGui.getCursorScreenPosX(),y=ImGui.getCursorScreenPosY(),width=Math.max(120,ImGui.getContentRegionAvailX());

        double end=Math.max(frame.project().effect().duration(),keys.get(keys.size()-1).getAsJsonObject().get("time").getAsDouble());

        float left=x+6,span=width-12;var draw=ImGui.getWindowDrawList();

        draw.addLine(left,y+18,left+span,y+18,ImGui.getColorU32(imgui.flag.ImGuiCol.Border));

        for(int i=0;i<keys.size();i++){float cx=left+(float)(keys.get(i).getAsJsonObject().get("time").getAsDouble()/end)*span;draw.addQuadFilled(cx,y+12,cx+6,y+18,cx,y+24,cx-6,y+18,ImGui.getColorU32(i==selectedKey?imgui.flag.ImGuiCol.PlotHistogram:imgui.flag.ImGuiCol.ButtonHovered));}

        double seconds=frame.project().effect().loop()?frame.simulation().time()%frame.project().effect().duration():frame.simulation().time();float play=left+(float)(Math.min(end,seconds)/end)*span;

        draw.addLine(play,y+3,play,y+32,ImGui.getColorU32(imgui.flag.ImGuiCol.PlotLines));draw.addText(left,y+34,ImGui.getColorU32(imgui.flag.ImGuiCol.TextDisabled),String.format(java.util.Locale.ROOT,"0 s                       %.2f s · effect clock",end));

        ImGui.invisibleButton("Layer transform seconds",width,54);EditorHints.tip("Click a diamond to select its transform key. Click elsewhere to seek in seconds. Edit the selected key below; particle lifetime curves remain independent.");

        if(ImGui.isItemHovered()&&ImGui.isMouseClicked(0)){double t=Math.max(0,Math.min(end,(ImGui.getMousePosX()-left)/span*end));int nearest=-1;float distance=9;

            for(int i=0;i<keys.size();i++){float dx=Math.abs(ImGui.getMousePosX()-(left+(float)(keys.get(i).getAsJsonObject().get("time").getAsDouble()/end)*span));if(dx<distance){distance=dx;nearest=i;}}

            if(nearest>=0){selectedKey=nearest;t=keys.get(nearest).getAsJsonObject().get("time").getAsDouble();}frame.seek().accept(t);

        }

    }

    private void editKey(EditorTools.Frame frame,String id,int index,Consumer<JsonObject> change){edit(frame,o->change.accept(transform(Authoring.layer(o,id)).getAsJsonArray("keys").get(index).getAsJsonObject()),true);}

    private static JsonObject sampleKey(Transforms.Track track,double time){

        var keys=track.keys();var result=new JsonObject();if(keys.isEmpty()){result.add("rotation",vector(0,0,0));return result;}

        var a=keys.getFirst();var b=a;for(var next:keys){b=next;if(time<=b.time())break;a=b;}

        double t=a==b?0:a.easing().at((time-a.time())/(b.time()-a.time()));var rotation=a.rotation().add(b.rotation().add(a.rotation().scale(-1)).scale(t));result.add("rotation",vector(rotation.x(),rotation.y(),rotation.z()));return result;

    }

}

