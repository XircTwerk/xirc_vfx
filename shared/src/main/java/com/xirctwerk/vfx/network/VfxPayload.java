package com.xirctwerk.vfx.network;

import com.xirctwerk.vfx.core.PlaybackMessage;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record VfxPayload(PlaybackMessage message) implements CustomPacketPayload {
    public static final Type<VfxPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("xirc_vfx","playback_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf,VfxPayload> CODEC=new StreamCodec<>(){
        public VfxPayload decode(RegistryFriendlyByteBuf buffer){return new VfxPayload(PlaybackMessage.decode(buffer.readByteArray(PlaybackMessage.MAX_BYTES)));}
        public void encode(RegistryFriendlyByteBuf buffer,VfxPayload value){buffer.writeByteArray(value.message.encode());}
    };
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
