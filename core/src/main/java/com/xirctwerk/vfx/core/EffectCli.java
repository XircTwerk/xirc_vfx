package com.xirctwerk.vfx.core;
import java.nio.file.*;
public final class EffectCli {
    public static void main(String[] args) throws Exception {
        if(args.length!=1) throw new IllegalArgumentException("Supply -Peffect=<absolute JSON path>");
        Path path=Path.of(args[0]);
        if(Files.size(path)>EffectCodec.MAX_JSON_BYTES) throw new IllegalArgumentException("JSON exceeds 1 MiB");
        var effect=EffectCodec.compile(EffectCodec.parse(Files.readString(path)));
        System.out.println("Valid: "+effect.id()+" / "+effect.emitters().size()+" emitters / "+effect.duration()+" seconds");
    }
}
