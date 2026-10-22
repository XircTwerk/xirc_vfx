package com.xirctwerk.vfx.core;

import com.google.gson.JsonObject;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/** All imported packages stay inside the Studio root; no arbitrary extraction or resource execution. */
public final class EffectFiles {
    public interface Resources { byte[] read(String resource) throws IOException; }
    private final Path root;
    public EffectFiles(Path root) throws IOException { Files.createDirectories(root); this.root=root.toRealPath(); Files.createDirectories(this.root.resolve("effects")); Files.createDirectories(this.root.resolve("exports")); Files.createDirectories(safe("imports")); }
    public void saveMaterial(String name,JsonObject json)throws IOException {Material.compile(json);if(!name.matches("[a-z][a-z0-9_]{0,63}"))throw new IOException("Use a lowercase material filename.");Path target=safe("materials/"+name+".json");Files.createDirectories(target.getParent());String text=EffectCodec.GSON.toJson(json);if(text.length()>65536)throw new IOException("Material exceeds 64 KiB.");Path temp=Files.createTempFile(target.getParent(),"material-",".tmp");try{Files.writeString(temp,text);Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temp);}}
    public JsonObject loadMaterial(String name)throws IOException {if(!name.matches("[a-z][a-z0-9_]{0,63}"))throw new IOException("Use a lowercase material filename.");Path target=safe("materials/"+name+".json");if(!Files.isRegularFile(target,LinkOption.NOFOLLOW_LINKS)||Files.size(target)>65536)throw new IOException("Material must be a regular JSON file up to 64 KiB.");var json=EffectCodec.parseObject(Files.readString(target));Material.compile(json);return json;}
    public Path root() { return root; }
    public PhotonImporter.Result importPhotonPath(Path chosen,boolean substituteTextures) throws IOException {
        if(!chosen.isAbsolute())throw new IOException("Choose an absolute .fx file path.");
        for(Path part:chosen)if(part.toString().equals(".."))throw new IOException("Traversal is not allowed in import paths.");
        Path path=chosen.normalize();for(Path part=path;part!=null;part=part.getParent())if(Files.isSymbolicLink(part))throw new IOException("Symlink import paths are not allowed.");
        if(!path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".fx")||!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw new IOException("Select a regular Photon .fx file.");
        try(var in=Files.newInputStream(path)) {String filename=path.getFileName().toString();return PhotonImporter.read(in.readNBytes(BoundedNbt.MAX_COMPRESSED+1),filename.substring(0,filename.length()-3),substituteTextures);}
    }
    public List<String> photonFiles() throws IOException {try(var stream=Files.list(safe("imports"))) {return stream.filter(p->Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)&&p.getFileName().toString().matches("[a-zA-Z0-9_. -]{1,128}\\.fx")).map(p->p.getFileName().toString()).sorted().limit(1000).toList();}}
    public PhotonImporter.Result importPhoton(String filename,boolean substituteTextures) throws IOException {
        if(!filename.matches("[a-zA-Z0-9_. -]{1,128}\\.fx")||filename.contains(".."))throw new IOException("Choose an .fx filename inside Studio imports; paths/traversal are not allowed.");
        Path path=safe("imports/"+filename);if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw new IOException("Photon import must be a regular file, not a symlink.");
        try(var in=Files.newInputStream(path)) {return PhotonImporter.read(in.readNBytes(BoundedNbt.MAX_COMPRESSED+1),filename.substring(0,filename.length()-3),substituteTextures);}
    }
    public void writeAuthoringDocument(String relative,InputStream stream) throws IOException { Path target=safe(relative);if(Files.exists(target)) return;Files.createDirectories(target.getParent());Files.copy(stream,target); }
    public byte[] resource(String id) throws IOException {
        EffectCodec.resource(id,"texture");Path path=safe("resources/"+id.replace(':','/'));if(!Files.exists(path)) return null;
        if(Files.size(path)>4_194_304) throw new IOException("Stored texture exceeds 4 MiB: "+id);return Files.readAllBytes(path);
    }
    public void storeResources(Map<String,byte[]> assets) throws IOException {
        for(var asset:assets.entrySet()) {
            EffectCodec.resource(asset.getKey(),"texture");if(!asset.getKey().matches("[a-z0-9_.-]+:textures/[a-z0-9_./-]+\\.png")) throw new IOException("Unsupported resource path.");
            if(asset.getValue().length>4_194_304) throw new IOException("Texture exceeds 4 MiB.");Path path=safe("resources/"+asset.getKey().replace(':','/'));Files.createDirectories(path.getParent());Files.write(path,asset.getValue());
        }
    }
    private Path safe(String relative) throws IOException {
        Path result=root.resolve(relative).normalize();
        if(!result.startsWith(root)) throw new IOException("Path must stay inside Studio.");
        Path node=result;
        while(node!=null&&node.startsWith(root)) { if(Files.isSymbolicLink(node)) throw new IOException("Symbolic links are not allowed in Studio paths."); node=node.getParent(); }
        return result;
    }
    private static String fileId(String name) { if(!name.matches("[a-z0-9_-]{1,64}")) throw new IllegalArgumentException("Filename must use 1..64 lowercase letters, numbers, _ or -."); return name; }
    public Path save(String name,JsonObject json) throws IOException {
        EffectCodec.compile(json); Path target=safe("effects/"+fileId(name)+".json");
        Path temp=Files.createTempFile(safe("effects"),"save-",".tmp");
        try { Files.writeString(temp,EffectCodec.GSON.toJson(json)); Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING); } finally { Files.deleteIfExists(temp); }
        return target;
    }
    public JsonObject load(String name) throws IOException { Path path=safe("effects/"+fileId(name)+".json"); if(Files.size(path)>EffectCodec.MAX_JSON_BYTES) throw new IOException("JSON exceeds 1 MiB."); return EffectCodec.parse(Files.readString(path)); }
    public List<String> list() throws IOException { try(var stream=Files.list(safe("effects"))) { return stream.filter(p->!Files.isSymbolicLink(p)&&p.getFileName().toString().matches("[a-z0-9_-]{1,64}\\.json")).sorted(Comparator.comparingLong(EffectFiles::modified).reversed()).map(p->p.getFileName().toString().replaceFirst("\\.json$","")).toList(); } }
    /** Separate editor recovery/clip files never overwrite an authored effect. */
    public Path checkpoint(JsonObject json,boolean automatic) throws IOException {
        EffectCodec.compile(json);String name=automatic?"draft":"checkpoint_"+System.currentTimeMillis()+"_"+UUID.randomUUID().toString().substring(0,8);return writeEditorJson("checkpoints",name,json);
    }
    public List<String> checkpoints() throws IOException{return editorFiles("checkpoints");}
    public JsonObject recover(String name) throws IOException{return EffectCodec.parse(readEditorJson("checkpoints",name));}
    public void saveClip(String name,JsonObject clip) throws IOException {Authoring.validateClip(clip);writeEditorJson("clips",fileId(name),clip);}
    public JsonObject loadClip(String name) throws IOException {var clip=EffectCodec.parseObject(readEditorJson("clips",name));Authoring.validateClip(clip);return clip;}
    public List<String> clips() throws IOException{return editorFiles("clips");}
    private List<String> editorFiles(String directory) throws IOException {var dir=safe(directory);if(!Files.exists(dir))return List.of();try(var stream=Files.list(dir)){return stream.filter(p->Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)&&p.getFileName().toString().matches("[a-z0-9_-]{1,64}\\.json")).sorted(Comparator.comparingLong(EffectFiles::modified).reversed()).limit(128).map(p->p.getFileName().toString().replaceFirst("\\.json$","")).toList();}}
    private String readEditorJson(String directory,String name) throws IOException {var path=safe(directory+"/"+fileId(name)+".json");if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)>EffectCodec.MAX_JSON_BYTES)throw new IOException("Choose a regular bounded editor JSON file.");return Files.readString(path);}
    private Path writeEditorJson(String directory,String name,JsonObject json) throws IOException {
        String text=EffectCodec.GSON.toJson(json);if(text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>EffectCodec.MAX_JSON_BYTES)throw new IOException("Editor JSON exceeds 1 MiB.");var dir=safe(directory);Files.createDirectories(dir);var target=safe(directory+"/"+fileId(name)+".json");if(!Files.exists(target)&&editorFiles(directory).size()>=128)throw new IOException("Editor library has 128 files; archive older files before adding more.");var temp=Files.createTempFile(dir,"editor-",".tmp");try{Files.writeString(temp,text);Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temp);}return target;
    }
    private static long modified(Path p) { try { return Files.getLastModifiedTime(p).toMillis(); } catch(IOException e) { return 0; } }
    public Path export(String name,JsonObject json,Resources resources) throws IOException {
        Effect effect=EffectCodec.compile(json); Map<String,byte[]> assets=new TreeMap<>(); int total=0;
        for(Effect.Emitter emitter:effect.emitters()) if(!assets.containsKey(emitter.texture())) {
            byte[] bytes=resources.read(emitter.texture()); if(bytes==null) throw new IOException("Missing texture "+emitter.texture()+". Install its resource pack or choose a bundled texture.");
            if(bytes.length>4_194_304) throw new IOException("Texture exceeds 4 MiB: "+emitter.texture());
            total+=bytes.length; if(total>16_777_216) throw new IOException("Resources exceed 16 MiB."); assets.put(emitter.texture(),bytes);
        }
        Path target=safe("exports/"+fileId(name)+".xvfx"); Path temp=Files.createTempFile(safe("exports"),"export-",".tmp");
        try {
            try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(temp))) {
                write(zip,"effect.json",EffectCodec.GSON.toJson(json).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                JsonObject manifest=new JsonObject(); manifest.addProperty("packageVersion",1); manifest.addProperty("effect","effect.json"); manifest.addProperty("author","XircTwerk");
                var refs=new com.google.gson.JsonArray(); assets.keySet().forEach(refs::add); manifest.add("resources",refs);
                write(zip,"manifest.json",EffectCodec.GSON.toJson(manifest).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                for(var asset:assets.entrySet()) write(zip,"assets/"+asset.getKey().replace(':','/'),asset.getValue());
            }
            Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
        return target;
    }
    private static void write(ZipOutputStream zip,String path,byte[] data) throws IOException { zip.putNextEntry(new ZipEntry(path)); zip.write(data); zip.closeEntry(); }
    public record Package(JsonObject effect,Map<String,byte[]> resources) {}
    public Package readPackage(String name) throws IOException {
        Path path=safe("exports/"+fileId(name)+".xvfx"); if(Files.size(path)>20_971_520) throw new IOException("Package exceeds 20 MiB.");
        Map<String,byte[]> files=new HashMap<>(); int total=0;
        try(ZipInputStream zip=new ZipInputStream(Files.newInputStream(path))) {
            for(ZipEntry entry;(entry=zip.getNextEntry())!=null;) {
                String key=entry.getName(); if(entry.isDirectory()) continue;
                if(key.contains("..")||key.contains("\\")||key.startsWith("/")||(!key.equals("effect.json")&&!key.equals("manifest.json")&&!key.matches("assets/[a-z0-9_.-]+/textures/[a-z0-9_./-]+\\.png"))) throw new IOException("Unsafe or unsupported package entry: "+key);
                if(files.size()>=66||files.containsKey(key)) throw new IOException("Too many or duplicate package entries.");
                byte[] data=zip.readNBytes(4_194_305); if(data.length>4_194_304) throw new IOException("Package entry exceeds 4 MiB."); total+=data.length; if(total>20_971_520) throw new IOException("Uncompressed package exceeds 20 MiB."); files.put(key,data);
            }
        }
        if(!files.containsKey("effect.json")||!files.containsKey("manifest.json")) throw new IOException("Package needs effect.json and manifest.json.");
        JsonObject effect=EffectCodec.parse(new String(files.get("effect.json"),java.nio.charset.StandardCharsets.UTF_8));
        JsonObject manifest;
        Set<String> declared=new HashSet<>();
        try {
            manifest=EffectCodec.parseObject(new String(files.get("manifest.json"),java.nio.charset.StandardCharsets.UTF_8));
            if(!Set.of("packageVersion","effect","author","resources").containsAll(manifest.keySet())) throw new IllegalArgumentException("Unsupported manifest field.");
            if(!manifest.get("packageVersion").isJsonPrimitive()||!manifest.getAsJsonPrimitive("packageVersion").isNumber()||manifest.get("packageVersion").getAsDouble()!=1||!manifest.get("effect").isJsonPrimitive()||!manifest.getAsJsonPrimitive("effect").isString()||!manifest.get("effect").getAsString().equals("effect.json")) throw new IllegalArgumentException("Use packageVersion 1 and effect.json.");
            var refs=manifest.getAsJsonArray("resources");if(refs.size()>64) throw new IllegalArgumentException("Too many resources.");
            for(var ref:refs) { if(!ref.isJsonPrimitive()||!ref.getAsJsonPrimitive().isString()||!declared.add(ref.getAsString())) throw new IllegalArgumentException("Invalid or duplicate resource reference.");EffectCodec.resource(ref.getAsString(),"manifest.resources"); }
        } catch(Exception e) { throw new IOException("Invalid package manifest: "+e.getMessage()); }
        Map<String,byte[]> assets=new HashMap<>();
        for(Effect.Emitter e:EffectCodec.compile(effect).emitters()) { byte[] bytes=files.get("assets/"+e.texture().replace(':','/')); if(bytes==null) throw new IOException("Package is missing "+e.texture()); assets.put(e.texture(),bytes); }
        if(!declared.equals(assets.keySet())||files.size()!=assets.size()+2) throw new IOException("Manifest/resources must exactly match the effect's texture dependencies; unused or undeclared entries are unsupported.");
        return new Package(effect,Map.copyOf(assets));
    }
}
