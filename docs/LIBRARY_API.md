# Xirc's VFX integration API — 0.3

## Dependency and compatibility

Java 21, Minecraft 1.21.1. Standard Maven repository (public, no token):

```groovy
repositories {
    maven { url = 'https://raw.githubusercontent.com/XircTwerk/xirc_vfx/maven' }
    mavenCentral()
    maven { url = 'https://maven.fabricmc.net/' } // Fabric integrations
}
// Fabric Loom:
dependencies { modImplementation 'com.xirctwerk.vfx:xirc_vfx-fabric:0.3.0' }
// NeoForge: implementation 'com.xirctwerk.vfx:xirc_vfx-neoforge:0.3.0'
// Java tools: implementation 'com.xirctwerk.vfx:xirc_vfx-core:0.3.0'
```

POMs and Gradle module metadata declare dependencies; pure core automatically brings Gson. Loader jars include the core and nested UI libraries. Do not add the pure-core jar alongside a loader jar. Declare the mod ID `xirc_vfx` in your loader manifest. Fabric's full `modImplementation` runtime variant supplies development UI dependencies through Maven metadata. Studio demonstrates an alternative: mapped compile-only access plus loading the complete library mod from the run's mods folder (runtime staging configuration is non-transitive).

Version policy: 0.x minor versions may change Java API signatures; recompile when moving between minor versions. Patch releases within 0.3 preserve public API source/binary compatibility unless a security correction is explicitly documented. `com.xirctwerk.vfx.api` and `core` are the supported integration surfaces; `client` classes are implementation details. JSON schemaVersion 1 stays unchanged. Wire protocol v1 is versioned separately. Pin versions; no dynamic `+` dependencies.

Maintainership: XircTwerk only. Original code MIT; required third-party notices retained. To stage publication: `gradlew.bat :core:test build stageRelease stageMaven`. `build/maven` contains the standard Maven repository with binary/source jars, POMs, module metadata and checksums. Released version directories are immutable; publish a new version for changed bytes.

## Handles

```java
var origin = new Effect.Vec(0, 65, 0);
Vfx.Handle handle = Vfx.playJson(
    ResourceLocation.parse("xirc_vfx:example/impact"), origin, 42,
    Map.of("power", 2.5, "radius", 2.0));
handle.move(new Effect.Vec(2, 65, 0));
handle.rotate(new Effect.Vec(0, 90, 0)); // XYZ Euler degrees
handle.scale(1.5);                     // uniform .01..8
handle.tint(new Effect.Color(.5, .8, 1, 1)); // multiplies every rendered layer
handle.pause(true);
handle.pause(false);
handle.fadeOut(.3);                   // seconds on the simulation clock
// handle.stop(); handle.alive();
```

`Vfx.play(Project, Vec)` also returns a handle. Code visuals (`play`, `slash`, `mesh`) return handles supporting the same spatial, tint, pause and fade controls. Their ordinary Java callback owns arbitrary numeric controls; `parameter()` explicitly rejects code callbacks. JSON handles accept only authored named numeric bindings; unknown names, nonfinite/out-of-range values and excessive budgets fail without replacing the running instance. Updating a named parameter restarts that instance with the same seed; other instances and source JSON stay unchanged.

Tint multiplies RGB and alpha while preserving authored gradients. Rotation/scale use the same simulation hierarchy as authored transforms, including collision positions. World-space particles retain their birth transforms/positions when an anchor moves; local-space particles follow it. Move changes the anchor, rather than rewriting JSON. Pause freezes age and fade; stop is immediate. Level changes stop instances. Releasing a Java reference does not stop a looping effect: its owner must stop it.

## Attachments

```java
var handle = Attachments.attach(project, projectile,
    new Effect.Vec(0, .2, 0), true, Attachments.MissingTarget.FADE_OUT);
```

Any loaded client entity can be the target: player, projectile or a weapon owner's entity. Offset is entity-local when followYaw is true. A weapon's animated blade endpoints belong to its animation adapter; feed them into `RibbonTrail.capture` (the weapon-trail demo shows this without a required animation mod).

Missing-target policies: STOP removes immediately; FADE_OUT unpauses and fades at the last known anchor over .25 seconds; DETACH keeps the last anchor until the owner stops the handle or the effect finishes. Leaving the world always stops it. No entity ID reuse can bind an old attachment to a new entity.

## Named parameters

Use the existing JSON `parameters` array to expose `power`, `radius` or your own numeric names through validated scale/offset bindings. Author them in Studio's parameter controls, register that authored JSON, and call `playJson(..., overrides)` or `handle.parameter(name, value)`. Color is a full-effect runtime tint via `handle.tint`, independent of numeric scalar bindings. No schema change or expression language was introduced.

Register shared definitions once during common mod setup:

```java
Effects.register(ResourceLocation.parse("yourmod:charge"), authoredJson);
```

The registered ID must match the JSON ID. Definitions are defensively copied; duplicate IDs and more than 1024 entries fail. Original library templates and `xirc_vfx:example/weapon_trail`, `projectile`, `impact`, `charged_attack`, `aura` are already registered. Editable user files do not silently become a server-trusted registry.

## Multiplayer

```java
// Server thread; library installed on server and the receiving client.
var broadcast = VfxServer.play(serverLevel,
    ResourceLocation.parse("yourmod:charge"), position, 64, 12345L,
    Map.of("power", 2.0));
// VfxServer.attach(serverEntity, id, localOffset, 64, seed, overrides);
// broadcast.recipients(); broadcast.stop();
```

Packets carry a registered ID, definition digest, dimension, seed, bounded numeric values/tint and optional entity UUID/offset. The client checks definition agreement, parameter validity and texture availability before allocating playback. No JSON document, PNG, shader or Java callback travels over the network. Missing definitions/textures, digest disagreement, duplicate instances, untracked entities and exceeded budgets skip the effect with an actionable reason (`VfxNetworkClient.lastRejection`, also in diagnostics). Clients lacking the optional protocol are skipped instead of disconnected. Local-only use against servers without the library remains supported.

Delivery is dimension/range scoped (1..256 blocks) and restricted to players tracking the effect chunk. Attachments require the corresponding entity to be tracked on the client. Broadcast handles send stop only to the original supported recipients. Playback starts on receipt: equal seeds give deterministic simulation inputs, not lockstep terrain/collision or identical packet latency. There is no late-join replay, automatic resource transfer, combat hook or remote code-visual execution. Protocol bounds: 8 KiB payloads, 32 overrides, client 64 play messages/second and 64 network IDs in addition to ordinary playback budgets.

`/xvfx-server demo` is an operator-only server-triggered impact example. It is separate from the client `/xvfx` editor command.

## Examples included in the library

Use Help > Built-in integration examples or these client commands:

- `/xvfx demo weapon_trail`: animated base/tip ribbon and a slash.
- `/xvfx demo projectile_impact`: moving projectile that spawns its impact.
- `/xvfx demo charged_attack`: parameterized charge, tint and timed impact/fade.
- `/xvfx demo persistent_aura`: looping player attachment with target-loss cleanup.
- `/xvfx demo stop`: stop the running demos; `/xvfx stop` stops all library visuals.

`IntegrationExamples.java` is runnable integration source shipped in the library/source jar. `IntegrationPresets` supplies the canonical original example definitions. Nothing activates automatically and no gameplay items/attack listeners are installed.

## Performance

Tools > Diagnostics and `/xvfx profile` expose per-effect live particles, submitted quads, actual draw-batch submissions and CPU microseconds for simulation/render submission. Counts include repeated sprite and geometry bloom submissions; procedural geometry has independently attributed batches. Values describe the last rendered frame, not GPU completion or a hardware benchmark. Code visuals and ribbon trails appear as separate owners. CPU geometry generation and draw submission are measured; shared fullscreen bloom/filter/upscale overhead and GPU/driver execution are not attributed to individual effects.

`VfxPerformance.configure(new Limits(instances, particles, quads))` applies client session budgets: 1..16 JSON instances, 1..8192 combined live particles, 1..32768 estimated JSON mesh quads. The Diagnostics sliders expose these same controls. Applying limits stops current world playback to avoid violating the new budget. JSON budgets are checked before allocation/parameter replacement and particles are capped during simulation. The geometry submission ceiling is twice the configured quad budget (at most 65536), with overflowing code callbacks/trails stopped. Low settings can intentionally reject complex effects. Definitions and editor documents are never rewritten to fit a budget.

See [RENDERING_API.md](RENDERING_API.md) for render targets, trusted shaders, materials and Studio authoring.
