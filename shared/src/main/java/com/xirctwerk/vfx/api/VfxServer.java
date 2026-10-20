package com.xirctwerk.vfx.api;

import com.xirctwerk.vfx.core.*;
import com.xirctwerk.vfx.core.Effect.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import java.util.*;
import java.util.function.*;

/** Server-thread API. No client classes are referenced here. Optional clients are skipped. */
public final class VfxServer {
    private VfxServer(){}
    private static BiConsumer<ServerPlayer,PlaybackMessage> sender;
    private static Predicate<ServerPlayer> supported;
    private static BiPredicate<ServerPlayer,Vec> tracking;
    public static void installTransport(Predicate<ServerPlayer> support,BiConsumer<ServerPlayer,PlaybackMessage> send,BiPredicate<ServerPlayer,Vec> tracked){supported=Objects.requireNonNull(support);sender=Objects.requireNonNull(send);tracking=Objects.requireNonNull(tracked);}
    public static Handle play(ServerLevel level,ResourceLocation effect,Vec position,double range,long seed,Map<String,Double> parameters){return play(level,effect,position,range,seed,parameters,new Color(1,1,1,1),null,Vec.ZERO);}
    public static Handle attach(Entity entity,ResourceLocation effect,Vec offset,double range,long seed,Map<String,Double> parameters){
        if(!(entity.level() instanceof ServerLevel level)||entity.isRemoved())throw new IllegalArgumentException("Attachment requires a live server entity");
        return play(level,effect,new Vec(entity.getX(),entity.getY(),entity.getZ()),range,seed,parameters,new Color(1,1,1,1),entity.getUUID(),offset);
    }
    public static Handle play(ServerLevel level,ResourceLocation effect,Vec position,double range,long seed,Map<String,Double> parameters,Color tint,UUID entity,Vec offset){
        if(!level.getServer().isSameThread())throw new IllegalStateException("VfxServer requires the server thread");
        if(!Double.isFinite(range)||range<1||range>256)throw new IllegalArgumentException("Delivery range must be 1..256 blocks");
        if(sender==null)throw new IllegalStateException("Loader networking has not initialized");
        var definition=Effects.get(effect);Parameters.override(definition.json(),parameters);
        var message=new PlaybackMessage(UUID.randomUUID(),false,effect.toString(),definition.digest(),level.dimension().location().toString(),position,seed,parameters,tint,entity,offset);
        List<ServerPlayer> recipients=level.players().stream().filter(p->p.distanceToSqr(position.x(),position.y(),position.z())<=range*range&&supported.test(p)&&tracking.test(p,position)).toList();
        recipients.forEach(player->sender.accept(player,message));return new Handle(level,message,recipients.stream().map(Entity::getUUID).toList());
    }
    public static final class Handle {
        private final ServerLevel level;private final PlaybackMessage message;private final List<UUID> recipients;private boolean stopped;
        private Handle(ServerLevel level,PlaybackMessage message,List<UUID> recipients){this.level=level;this.message=message;this.recipients=recipients;}
        public int recipients(){return recipients.size();}
        public void stop(){if(!level.getServer().isSameThread())throw new IllegalStateException("VfxServer requires the server thread");if(stopped)return;stopped=true;for(UUID id:recipients){var player=level.getServer().getPlayerList().getPlayer(id);if(player!=null&&supported.test(player))sender.accept(player,message.stopped());}}
    }
}
