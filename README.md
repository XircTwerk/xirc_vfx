# Xirc's VFX

A reusable Minecraft **1.21.1 Fabric / NeoForge** visual-effects library by **XircTwerk**. Java 21, MIT licensed original code. Author and maintainer: **XircTwerk**.

The library owns the JSON engine, deterministic simulation, transform hierarchy, curves/gradients, spline and motion fields, sprites, beams, trails, procedural meshes/lightning, pixelization, bloom, code-driven visuals, bounded Photon conversion, project/package utilities, presets and optional ImGui authoring editor. Xirc's VFX Studio is a separate consumer that adds its editor shortcut and application identity.

## Install

Use the matching `xirc_vfx-fabric-0.1.0.jar` or `xirc_vfx-neoforge-0.1.0.jar` from [Releases](https://github.com/XircTwerk/xirc_vfx/releases). Fabric also needs Fabric API. The library registers rendering/ticking once. It does not take the V key; a host mod can open the optional editor explicitly. `/xvfx` supplies local authoring/playback commands.

## Gradle dependency

The public release assets form a version-pinned Ivy repository (no GitHub token required):

```groovy
repositories {
    ivy {
        url = 'https://github.com/XircTwerk/xirc_vfx/releases/download'
        patternLayout { artifact '[revision]/[artifact]-[revision](-[classifier]).[ext]' }
        metadataSources { artifact() }
        content { includeGroup 'com.xirctwerk.vfx' }
    }
}
// Fabric Loom: compile against the mapped API.
dependencies { modCompileOnly 'com.xirctwerk.vfx:xirc_vfx-fabric:0.1.0' }
// NeoForge: compileOnly 'com.xirctwerk.vfx:xirc_vfx-neoforge:0.1.0'
// Stage the complete matching release mod for development runtime loading:
configurations { vfxRuntime }
dependencies { vfxRuntime 'com.xirctwerk.vfx:xirc_vfx-fabric:0.1.0' }
tasks.named('runClient') {
    doFirst { copy { from configurations.vfxRuntime; into file('run/mods') } }
}
// Use the neoforge artifact and your configured game directory on NeoForge.
// Pure Java tools: implementation 'com.xirctwerk.vfx:xirc_vfx-core:0.1.0'
// The pure core also requires com.google.code.gson:gson:2.10.1.
```

Declare `xirc_vfx >=0.1.0` as a required client dependency in your loader metadata. The loader artifact includes the engine and optional editor plus nested UI runtime dependencies; the pure core jar has no Minecraft/UI dependency. Artifact-only Ivy metadata does not infer transitive dependencies for the standalone pure core. Loading the complete loader jar from the run directory also ensures its nested ImGui libraries are discovered in development; a mapped compile dependency alone does not do that. Replace an older library jar when upgrading.

## Client API

Call on the Minecraft client thread after the library initializes:

```java
var project = new com.xirctwerk.vfx.core.Project(
    com.xirctwerk.vfx.core.Templates.create("prismatic_orrery"));
com.xirctwerk.vfx.client.VfxClient.INSTANCE.play(project,
    new com.xirctwerk.vfx.core.Effect.Vec(0, 65, 0));
// Optional authoring UI:
com.xirctwerk.vfx.client.VfxClient.INSTANCE.open();
```

Trusted Java callbacks and endpoint trails use `com.xirctwerk.vfx.api.Vfx` and `RibbonTrail`; see [Code API](docs/CODE_API.md). JSON remains canonical; [format](docs/JSON_FORMAT.md), [schema](schema/effect.schema.json), [capabilities](docs/CAPABILITIES.md), [Photon conversion](docs/PHOTON_IMPORT.md). This is client-side; no multiplayer synchronization is claimed.

Existing `xirc_vfx_studio:` effect/texture identifiers remain valid for compatibility. New effects may use your own namespace. A host can call `useProjectDirectory(Path)` before opening the editor to retain its existing projects; default library storage is `config/xirc_vfx`. `setEditorTitle(String)` supplies an application's display name.

## Build

```powershell
.\gradlew.bat :core:test build
```

On Linux use `bash gradlew`. Both loader jars and source jars are built from the shared implementation. Core semantic/schema/security tests run without Minecraft. The opt-in `-PxircSmokeTest -PxircPackagedSmokeTest` runtime harness exercises the optional editor, world renderer, transforms, bloom, imports and cloud composition on an actual client.

## Licensing

Original project code and original generated sprites/presets: copyright 2026 **XircTwerk**, MIT. Bundled third-party components retain their own licenses and copyright notices; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). This attribution does not designate third-party authors as authors or maintainers of Xirc's VFX.
