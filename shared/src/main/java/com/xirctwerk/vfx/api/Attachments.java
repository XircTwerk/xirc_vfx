package com.xirctwerk.vfx.api;

import com.xirctwerk.vfx.core.*;
import com.xirctwerk.vfx.core.Effect.*;
import com.xirctwerk.vfx.client.VfxClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import java.io.IOException;

/** Entity-local anchor offsets; callers may use players, weapons' owners or projectiles. */
public final class Attachments {
    private Attachments(){}
    public enum MissingTarget { STOP, FADE_OUT, DETACH }
    public static Vfx.Handle attach(Project project,Entity target,Vec offset,boolean followYaw,MissingTarget missing) throws IOException {
        return attach(project,target,offset,followYaw,missing,project.effect().seed());
    }
    public static Vfx.Handle attach(Project project,Entity target,Vec offset,boolean followYaw,MissingTarget missing,long seed) throws IOException {
        Vfx.checkThread();PlaybackControls.finite(offset);java.util.Objects.requireNonNull(missing);var world=Minecraft.getInstance().level;
        if(world==null||target==null||target.level()!=world||target.isRemoved())throw new IOException("Attachment requires a loaded entity in the current world");
        Vec[] last={VfxClient.vec(target.position()).add(offset)};boolean[] lost={false};VfxClient.Playback[] handle={null};
        var result=VfxClient.INSTANCE.play(project,()->{
            if(!lost[0])last[0]=VfxClient.vec(target.position()).add(followYaw?Transforms.Pose.euler(Vec.ZERO,new Vec(0,-target.getYRot(),0),1).vector(offset):offset);return last[0];
        },()->{
            if(Minecraft.getInstance().level!=world)return false;
            if(!lost[0]&&(target.isRemoved()||world.getEntity(target.getId())!=target)){
                lost[0]=true;if(missing==MissingTarget.FADE_OUT&&handle[0]!=null)handle[0].pause(false).fadeOut(.25);
            }
            return !lost[0]||missing!=MissingTarget.STOP;
        },seed);handle[0]=result;return result;
    }
}
