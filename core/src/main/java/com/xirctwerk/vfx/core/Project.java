package com.xirctwerk.vfx.core;

import com.google.gson.*;
import java.util.*;
import java.util.function.Consumer;

/** Stores original authored JSON; switching editor modes never reserializes a reduced model. */
public final class Project {
    private JsonObject json;
    private Effect compiled;
    private JsonObject editStart;
    private final Deque<JsonObject> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();
    public Project(JsonObject json) { compiled=EffectCodec.compile(json); this.json=json.deepCopy(); }
    public JsonObject json() { return json.deepCopy(); }
    public Effect effect() { return compiled; }
    public String text() { return EffectCodec.GSON.toJson(json); }
    public void edit(Consumer<JsonObject> change) { JsonObject copy=json.deepCopy(); change.accept(copy); replace(copy); }
    public void replace(JsonObject next) {
        var validated=EffectCodec.compile(next); if(next.equals(json)) return;
        if(editStart==null) {undo.push(json);while(undo.size()>50) undo.removeLast();redo.clear();}
        json=next.deepCopy();compiled=validated;
    }
    /** All validated live updates in one mouse gesture create one undo entry. */
    public void beginEdit() {endEdit();editStart=json.deepCopy();}
    public void endEdit() {if(editStart==null) return;if(!editStart.equals(json)) {undo.push(editStart);while(undo.size()>50) undo.removeLast();redo.clear();}editStart=null;}
    public boolean undo() { endEdit();if(undo.isEmpty()) return false; redo.push(json); json=undo.pop();compiled=EffectCodec.compile(json); return true; }
    public boolean redo() { endEdit();if(redo.isEmpty()) return false; undo.push(json); json=redo.pop();compiled=EffectCodec.compile(json); return true; }
    public void rename(String id,String name) { edit(o->{o.addProperty("id",id);o.addProperty("name",name);o.addProperty("author","XircTwerk");}); }
    public boolean customized(int index,String property) { JsonElement e=json.getAsJsonArray("emitters").get(index).getAsJsonObject().get(property); return e!=null&&e.isJsonObject(); }
    public void simple(int index,String property,JsonElement value,boolean replaceAdvanced) {
        if(customized(index,property)&&!replaceAdvanced) throw new IllegalArgumentException(property+" has a custom curve/gradient. Confirm replacement or edit Advanced JSON.");
        edit(o->o.getAsJsonArray("emitters").get(index).getAsJsonObject().add(property,value));
    }
}
