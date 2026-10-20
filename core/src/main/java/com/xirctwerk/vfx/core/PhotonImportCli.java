package com.xirctwerk.vfx.core;

import java.nio.file.*;
import java.util.*;

/** Read-only directory audit, or explicit conversion to a new JSON file. */
public final class PhotonImportCli {
    public static void main(String[] args) throws Exception {
        if(args.length==0)throw new IllegalArgumentException("Provide -Pphoton=<.fx file or directory>. Add -Poutput=<new JSON file> and -Papproximate=true to write an approximation.");
        Path input=Path.of(args[0]).toAbsolutePath().normalize();checkPath(input);
        if(Files.isDirectory(input)) {
            int converted=0,failed=0;Map<String,String> failures=new TreeMap<>();
            try(var paths=Files.list(input)) {for(Path path:paths.filter(p->p.getFileName().toString().endsWith(".fx")).sorted().limit(1000).toList())try {var result=read(path);converted++;System.out.println(path.getFileName()+": "+result.effect().getAsJsonArray("emitters").size()+" layers, "+result.diagnostics().size()+" diagnostics");}catch(Exception e){failed++;failures.put(path.getFileName().toString(),e.getMessage());}}
            System.out.println("PHOTON_AUDIT converted="+converted+" failed="+failed);failures.forEach((file,error)->System.out.println(file+": "+error));return;
        }
        var result=read(input);result.diagnostics().forEach(System.out::println);
        if(args.length>1&&!args[1].isEmpty()) {var json=args.length>2&&args[2].equals("true")?result.effect():result.requireExact();Path output=Path.of(args[1]).toAbsolutePath().normalize();checkPath(output);Files.writeString(output,EffectCodec.GSON.toJson(json),StandardOpenOption.CREATE_NEW);System.out.println("Converted: "+output);}
        else System.out.println("Reviewed "+result.effect().getAsJsonArray("emitters").size()+" layers; no files written.");
    }
    private static PhotonImporter.Result read(Path path) throws Exception {checkPath(path);if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw new IllegalArgumentException("Expected a regular .fx file.");try(var in=Files.newInputStream(path)){return PhotonImporter.read(in.readNBytes(BoundedNbt.MAX_COMPRESSED+1),path.getFileName().toString().replaceFirst("\\.fx$",""),true);}}
    private static void checkPath(Path path) throws Exception {for(Path p=path;p!=null;p=p.getParent())if(Files.isSymbolicLink(p))throw new IllegalArgumentException("Symlink paths are not allowed: "+p);}
}
