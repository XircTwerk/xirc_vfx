package com.xirctwerk.vfx.client;

import com.mojang.brigadier.*;
import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.builder.*;
import com.mojang.brigadier.context.CommandContext;
import com.xirctwerk.vfx.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import java.util.function.BiConsumer;

public final class StudioCommands {
    private interface Action<S> { void run(CommandContext<S> context) throws Exception; }
    private static <S> Command<S> execute(BiConsumer<S,Component> feedback,Action<S> action) { return c->{ try { action.run(c);return 1; } catch(Exception e) { feedback.accept(c.getSource(),Component.literal("Studio: "+e.getMessage()));return 0; } }; }
    private static <S> LiteralArgumentBuilder<S> lit(String name) { return LiteralArgumentBuilder.literal(name); }
    private static <S,T> RequiredArgumentBuilder<S,T> arg(String name,ArgumentType<T> type) { return RequiredArgumentBuilder.argument(name,type); }
    private static <S> RequiredArgumentBuilder<S,String> name() { return StudioCommands.<S,String>arg("name",StringArgumentType.word()).suggests((c,b)->{ try { VfxClient.INSTANCE.files.list().forEach(b::suggest); }catch(Exception ignored){} return b.buildFuture(); }); }
    private static Effect.Vec me() { var player=Minecraft.getInstance().player;if(player==null) throw new IllegalStateException("Join a world first.");return VfxClient.vec(player.position()); }
    public static <S> void register(CommandDispatcher<S> dispatcher,BiConsumer<S,Component> feedback) {
        var studio=VfxClient.INSTANCE;
        LiteralArgumentBuilder<S> root=StudioCommands.<S>lit("xvfx").executes(execute(feedback,c->Minecraft.getInstance().execute(studio::open)));
        root.then(StudioCommands.<S>lit("editor").executes(execute(feedback,c->Minecraft.getInstance().execute(studio::open))));
        root.then(StudioCommands.<S>lit("stop").executes(execute(feedback,c->{studio.stop();feedback.accept(c.getSource(),Component.literal("Stopped all Studio effects."));})));
        root.then(StudioCommands.<S>lit("list").executes(execute(feedback,c->feedback.accept(c.getSource(),Component.literal(String.join(", ",studio.files.list()))))));
        var play=StudioCommands.<S>name().executes(execute(feedback,c->studio.play(StringArgumentType.getString(c,"name"),me())));
        play.then(StudioCommands.<S,Double>arg("x",DoubleArgumentType.doubleArg(-30000000,30000000)).then(StudioCommands.<S,Double>arg("y",DoubleArgumentType.doubleArg(-2048,2048)).then(StudioCommands.<S,Double>arg("z",DoubleArgumentType.doubleArg(-30000000,30000000)).executes(execute(feedback,c->studio.play(StringArgumentType.getString(c,"name"),new Effect.Vec(DoubleArgumentType.getDouble(c,"x"),DoubleArgumentType.getDouble(c,"y"),DoubleArgumentType.getDouble(c,"z"))))))));
        root.then(StudioCommands.<S>lit("play").then(play));
        root.then(StudioCommands.<S>lit("me").then(StudioCommands.<S>name().executes(execute(feedback,c->{me();studio.attach(StringArgumentType.getString(c,"name"),Minecraft.getInstance().player.getId());}))));
        root.then(StudioCommands.<S>lit("attach").then(StudioCommands.<S>name().then(StudioCommands.<S,Integer>arg("entity_id",IntegerArgumentType.integer(0)).executes(execute(feedback,c->studio.attach(StringArgumentType.getString(c,"name"),IntegerArgumentType.getInteger(c,"entity_id")))))));
        root.then(StudioCommands.<S>lit("block").then(StudioCommands.<S>name().then(StudioCommands.<S,Integer>arg("x",IntegerArgumentType.integer(-30000000,30000000)).then(StudioCommands.<S,Integer>arg("y",IntegerArgumentType.integer(-2048,2048)).then(StudioCommands.<S,Integer>arg("z",IntegerArgumentType.integer(-30000000,30000000)).executes(execute(feedback,c->studio.block(StringArgumentType.getString(c,"name"),new BlockPos(IntegerArgumentType.getInteger(c,"x"),IntegerArgumentType.getInteger(c,"y"),IntegerArgumentType.getInteger(c,"z"))))))))));
        root.then(StudioCommands.<S>lit("import").then(StudioCommands.<S,String>arg("name",StringArgumentType.word()).executes(execute(feedback,c->{String name=StringArgumentType.getString(c,"name");studio.importPackage(name);feedback.accept(c.getSource(),Component.literal("Imported "+name+".xvfx"));}))));
        dispatcher.register(root);
    }
}
