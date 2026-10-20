package com.xirctwerk.vfx.client;

import com.xirctwerk.vfx.api.*;
import com.xirctwerk.vfx.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Main-thread network consumer: validates local definitions and resources before spawning. */
public final class VfxNetworkClient {
    private VfxNetworkClient(){}
    private static final Map<UUID,EffectHandle> active=new HashMap<>();
    private static Object world;
    private static long accepted,rejected,window;private static int received;
    private static String lastRejection="";
    public static long accepted(){return accepted;}
    public static long rejected(){return rejected;}
    public static String lastRejection(){return lastRejection;}
    public static void tick(){var level=Minecraft.getInstance().level;if(level!=world){active.values().forEach(EffectHandle::stop);active.clear();world=level;}active.values().removeIf(h->!h.alive());}
    public static void receive(PlaybackMessage message){
        Vfx.checkThread();tick();
        try{
            var mc=Minecraft.getInstance();if(mc.level==null||!mc.level.dimension().location().toString().equals(message.dimension()))throw new IllegalArgumentException("Effect belongs to a different dimension");
            if(message.stop()){var handle=active.remove(message.instance());if(handle!=null)handle.stop();return;}
            long now=System.nanoTime();if(now-window>1_000_000_000L){window=now;received=0;}if(++received>64)throw new IllegalArgumentException("Network effect rate limit exceeded (64/s)");
            if(active.size()>=64||active.containsKey(message.instance()))throw new IllegalArgumentException("Duplicate instance or network instance budget reached");
            var definition=Effects.get(ResourceLocation.parse(message.effect()));if(!definition.digest().equals(message.digest()))throw new IllegalArgumentException("Definition differs from server: "+message.effect()+". Install the matching mod/resources");
            var project=new Project(Parameters.override(definition.json(),message.parameters()));VfxClient.INSTANCE.checkResources(project.effect());
            EffectHandle handle;
            if(message.entity()==null)handle=VfxClient.INSTANCE.play(project,message.position(),message.seed());
            else {
                var entity=java.util.stream.StreamSupport.stream(mc.level.entitiesForRendering().spliterator(),false).filter(e->e.getUUID().equals(message.entity())).findFirst().orElseThrow(()->new IllegalArgumentException("Attachment target is not tracked by this client"));
                handle=Attachments.attach(project,entity,message.offset(),true,Attachments.MissingTarget.STOP,message.seed());
            }
            handle.tint(message.tint());active.put(message.instance(),handle);accepted++;
        }catch(Exception error){rejected++;lastRejection=String.valueOf(error.getMessage());if(rejected<5)org.slf4j.LoggerFactory.getLogger("XircVfxNetwork").warn("Skipped network effect: {}",lastRejection);}
    }
}
