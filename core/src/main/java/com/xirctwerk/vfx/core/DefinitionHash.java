package com.xirctwerk.vfx.core;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
/** Stable digest independent of JSON object key order; arrays retain authored order. */
public final class DefinitionHash {
    private DefinitionHash(){}
    public static String of(JsonObject json){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(sorted(json).toString().getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
    private static JsonElement sorted(JsonElement element){
        if(element.isJsonObject()){var result=new JsonObject();element.getAsJsonObject().keySet().stream().sorted().forEach(k->result.add(k,sorted(element.getAsJsonObject().get(k))));return result;}
        if(element.isJsonArray()){var result=new JsonArray();element.getAsJsonArray().forEach(e->result.add(sorted(e)));return result;}return element.deepCopy();
    }
}
