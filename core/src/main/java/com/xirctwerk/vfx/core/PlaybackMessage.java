package com.xirctwerk.vfx.core;

import java.io.*;
import java.util.*;
import static com.xirctwerk.vfx.core.Effect.*;

/** Bounded protocol v1. Only registered definition IDs and values cross the network. */
public record PlaybackMessage(UUID instance,boolean stop,String effect,String digest,String dimension,Vec position,long seed,Map<String,Double> parameters,Color tint,UUID entity,Vec offset) {
    public static final int MAX_BYTES=8192;
    public PlaybackMessage {
        Objects.requireNonNull(instance);Objects.requireNonNull(parameters);Objects.requireNonNull(offset);
        for(String id:List.of(effect,dimension))if(id.length()>128||!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")||id.contains(".."))throw new IllegalArgumentException("Invalid network resource ID");
        if(!digest.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Expected SHA-256 definition digest");
        PlaybackControls.finite(position);PlaybackControls.finite(offset);if(offset.length()>128)throw new IllegalArgumentException("Attachment offset exceeds 128 blocks");
        var controls=new PlaybackControls();controls.tint(tint);
        if(parameters.size()>32)throw new IllegalArgumentException("At most 32 parameter values");
        parameters.forEach((name,value)->{if(name==null||!name.matches("[a-z][a-z0-9_]{0,31}")||value==null||!Double.isFinite(value)||Math.abs(value)>1_000_000)throw new IllegalArgumentException("Invalid network parameter");});parameters=Map.copyOf(parameters);
    }
    public PlaybackMessage stopped(){return new PlaybackMessage(instance,true,effect,digest,dimension,position,seed,parameters,tint,entity,offset);}
    public byte[] encode(){try{
        var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);out.writeByte(1);uuid(out,instance);out.writeBoolean(stop);out.writeUTF(effect);out.writeUTF(digest);out.writeUTF(dimension);vec(out,position);out.writeLong(seed);out.writeByte(parameters.size());
        for(var e:new TreeMap<>(parameters).entrySet()){out.writeUTF(e.getKey());out.writeDouble(e.getValue());}
        for(double c:new double[]{tint.r(),tint.g(),tint.b(),tint.a()})out.writeDouble(c);out.writeBoolean(entity!=null);if(entity!=null)uuid(out,entity);vec(out,offset);out.flush();if(bytes.size()>MAX_BYTES)throw new IllegalArgumentException("Payload exceeds 8 KiB");return bytes.toByteArray();
    }catch(IOException impossible){throw new AssertionError(impossible);}}
    public static PlaybackMessage decode(byte[] bytes){
        if(bytes==null||bytes.length>MAX_BYTES)throw new IllegalArgumentException("Payload exceeds 8 KiB");
        try{var in=new DataInputStream(new ByteArrayInputStream(bytes));if(in.readUnsignedByte()!=1)throw new IllegalArgumentException("Unsupported VFX protocol");
            UUID instance=uuid(in);boolean stop=in.readBoolean();String effect=in.readUTF(),digest=in.readUTF(),dimension=in.readUTF();Vec position=vec(in);long seed=in.readLong();int count=in.readUnsignedByte();if(count>32)throw new IllegalArgumentException("At most 32 parameters");var values=new LinkedHashMap<String,Double>();
            for(int i=0;i<count;i++){String key=in.readUTF();if(values.putIfAbsent(key,in.readDouble())!=null)throw new IllegalArgumentException("Duplicate network parameter");}
            Color tint=new Color(in.readDouble(),in.readDouble(),in.readDouble(),in.readDouble());UUID entity=in.readBoolean()?uuid(in):null;Vec offset=vec(in);if(in.available()!=0)throw new IllegalArgumentException("Trailing payload bytes");return new PlaybackMessage(instance,stop,effect,digest,dimension,position,seed,values,tint,entity,offset);
        }catch(IOException error){throw new IllegalArgumentException("Truncated/invalid VFX payload",error);}
    }
    private static void uuid(DataOutputStream out,UUID id)throws IOException{out.writeLong(id.getMostSignificantBits());out.writeLong(id.getLeastSignificantBits());}
    private static UUID uuid(DataInputStream in)throws IOException{return new UUID(in.readLong(),in.readLong());}
    private static void vec(DataOutputStream out,Vec v)throws IOException{out.writeDouble(v.x());out.writeDouble(v.y());out.writeDouble(v.z());}
    private static Vec vec(DataInputStream in)throws IOException{return new Vec(in.readDouble(),in.readDouble(),in.readDouble());}
}
