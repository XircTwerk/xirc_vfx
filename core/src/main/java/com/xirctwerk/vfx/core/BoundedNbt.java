package com.xirctwerk.vfx.core;

import com.google.gson.*;
import java.io.*;
import java.util.zip.GZIPInputStream;

/** Independent data reader for gzip-compressed standard big-endian NBT. No game or Photon classes. */
final class BoundedNbt {
    static final int MAX_COMPRESSED=4*1024*1024,MAX_EXPANDED=8*1024*1024,MAX_NODES=100_000;
    private final DataInputStream in;
    private int nodes;
    private BoundedNbt(byte[] data) {in=new DataInputStream(new ByteArrayInputStream(data));}
    static JsonObject read(byte[] bytes) throws IOException {
        if(bytes.length>MAX_COMPRESSED) throw new IOException("Photon file exceeds 4 MiB.");
        if(bytes.length<2||(bytes[0]&255)!=31||(bytes[1]&255)!=139) throw new IOException("Expected a gzip-compressed .fx NBT file.");
        byte[] data;
        try(var gzip=new GZIPInputStream(new ByteArrayInputStream(bytes))) {data=gzip.readNBytes(MAX_EXPANDED+1);}
        if(data.length>MAX_EXPANDED) throw new IOException("Photon expanded data exceeds 8 MiB.");
        var reader=new BoundedNbt(data);
        if(reader.in.readUnsignedByte()!=10) throw new IOException("NBT root must be a compound.");
        reader.in.readUTF();var root=reader.tag(10,0,"$").getAsJsonObject();
        if(reader.in.read()!=-1) throw new IOException("Trailing NBT data is not allowed.");
        return root;
    }
    private JsonElement tag(int type,int depth,String path) throws IOException {
        if(depth>32||++nodes>MAX_NODES) throw new IOException(path+": NBT nesting/node limit exceeded.");
        return switch(type) {
            case 1 -> new JsonPrimitive(in.readByte());case 2 -> new JsonPrimitive(in.readShort());
            case 3 -> new JsonPrimitive(in.readInt());case 4 -> new JsonPrimitive(in.readLong());
            case 5 -> number(in.readFloat(),path);case 6 -> number(in.readDouble(),path);
            case 8 -> new JsonPrimitive(in.readUTF());
            case 7,9,11,12 -> {
                int element=type==9?in.readUnsignedByte():type==7?1:type==11?3:4;
                int count=in.readInt();if(count<0||count>16_384||count>MAX_NODES-nodes) throw new IOException(path+": Invalid or oversized NBT array.");
                if(type==9&&(element<0||element>12||(element==0&&count!=0))) throw new IOException(path+": Invalid NBT list type.");
                var a=new JsonArray();for(int i=0;i<count;i++) a.add(tag(element,depth+1,path+"["+i+"]"));yield a;
            }
            case 10 -> {
                var o=new JsonObject();for(int t;(t=in.readUnsignedByte())!=0;) {
                    String key=in.readUTF();if(key.length()>512||o.has(key)) throw new IOException(path+": Oversized or duplicate NBT key: "+key);
                    o.add(key,tag(t,depth+1,path+"."+key));
                }yield o;
            }
            default -> throw new IOException(path+": Unsupported NBT tag "+type+".");
        };
    }
    private static JsonPrimitive number(double value,String path) throws IOException {if(!Double.isFinite(value)) throw new IOException(path+": Non-finite NBT number.");return new JsonPrimitive(value);}
}
