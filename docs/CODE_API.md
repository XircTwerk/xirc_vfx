# Code-driven visuals

In 0.6.0, `new Vfx.Style(color, pixelSize, bloom)` enables effect-only world bloom for trusted code geometry (strength 0–4). The two-argument constructor retains bloom disabled. Generated quads are reused for the bloom mask, so callbacks still execute once per rendered frame. The post pass restores framebuffer, shader and viewport state. Bloom supports the tested vanilla main framebuffer, not arbitrary shader-pack targets.

The client API lives in `com.xirctwerk.vfx.api`. Install the matching Fabric or NeoForge Studio jar and compile your client integration against Studio's classes. The API is currently source-level, versioned with the mod; stable binary compatibility and a published Maven artifact are not promised yet. Call it on Minecraft's client thread, after joining a world.

## Slash and arbitrary procedural visuals

```java
import com.xirctwerk.vfx.api.Vfx;
import com.xirctwerk.vfx.core.Effect.Color;
import com.xirctwerk.vfx.core.Effect.Vec;

var style = new Vfx.Style(new Color(.1, .65, 1, .85), 6);
// Capture the specific entity, rather than looking up whichever player is current later.
var entity = Minecraft.getInstance().player;
Vfx.Handle slash = Vfx.slash(
    () -> new Vec(entity.getX(), entity.getY() + 1, entity.getZ()),
    new Vec(0, 0, 1), 2, .35, .65, style);
```

`pixelSize=1` renders smoothly; 2–8 uses a smaller framebuffer, nearest upscaling and a world-aligned 1/16-block geometry grid. This is the Nichirin pixelization approach using Minecraft's built-in color and blit shaders. It does not pixelate the world. Meshes are unlit vertex-colored translucent quads; there are no UV textures, material graphs or imported shader programs in this API yet.

`Vfx.register(ResourceLocation, Visual)` registers a namespaced code visual during client setup; duplicate IDs are rejected. `Vfx.play(id, origin, durationSeconds, seed, style)` plays a registered visual. Registry entries survive world changes. The registry is capped at 1024 entries.

`Vfx.play(origin, durationSeconds, seed, style, visual)` supplies a `Frame` containing age, normalized progress, seed and current origin. Submit world-space quads through its `Canvas`. Animate vertices, colors or procedural primitives in ordinary trusted Java code. A supplier can follow an entity or any other moving anchor; `Handle.stop()` stops it. Duration is .05–300 seconds. Visual callbacks that fail or exceed the frame geometry budget are stopped and logged.

`Vfx.mesh(origin, quads, duration, style)` snapshots an arbitrary immutable local-coordinate quad mesh, with 1–4096 quads, and translates it with the anchor. `Geometry.create` provides slash, ring, cube and sphere primitives; `Geometry.Quad` lets another mod provide its own topology. OBJ/glTF loading, textured meshes, skeletal skinning and mesh asset packages remain future work.

## Animated blade and arm trails

```java
import com.xirctwerk.vfx.api.RibbonTrail;

var trail = new RibbonTrail();
var profile = new RibbonTrail.Profile(340, 32,
    new Color(.02, .1, .3, 1), new Color(.1, .5, 1, 1),
    new Color(.8, 1, 1, 1), .9);
// Each rendered animation frame: transform the animated blade/arm endpoints to WORLD coordinates.
trail.capture(baseWorld, tipWorld, profile);
// When the integration no longer owns the trail:
trail.close();
```

The animation adapter belongs to the calling mod; Studio has no AzureLib, player-animation or Nichirin dependency. Give each blade/arm its own trail. Sampling retains endpoint pairs, rejects tiny movement, smooths them with four uniform Catmull–Rom subdivisions, fades samples over wall-clock milliseconds and draws shadow/body/highlight bands. Large discontinuities are skipped. Changes in profile retain old samples until they fade. Trails always render at full resolution and ignore the pixel grid. Pausing the game freezes effect simulation; trails still fade with wall time.

Code effects are capped at 256 instances and allocated trails at 256. A frame accepts at most 65536 geometry quads, with JSON world playbacks reserving at most 32768 estimated quads. An overflowing code visual is stopped; overflowing trails are closed and logged. Close trail handles when no longer used. World changes clear effects and close trails; framebuffer resources are released. These limits are engineering bounds, not performance benchmarks.

The API is client-side. Server triggers and resource synchronization from Nichirin have not been ported. Your mod must deliver its own client event before invoking Studio; effects are not automatically visible to other players.

## In-game authoring

Starter templates include Slash, Pixel Slash, Mesh Ring and Mesh Sphere. Inspector → Shape / mesh → Visual chooses the eight renderer kinds and exposes pixel size and radius. Size, direction, rotation, timing and gradients drive procedural meshes. Display shows filled generated topology, with optional View → Wireframe meshes; World also shows framebuffer pixelization. Custom code callbacks and arbitrary mesh topology cannot be imported from JSON. Advanced presets demonstrate layered compositions of these primitives.

## Capability discovery and motion

```java
if (Vfx.capabilities().contains(Vfx.Capability.SPLINE_MOTION)) {
    // Select an authored spline variant supported by this Studio API.
}
```

`Vfx.capabilities()` returns an immutable set of implemented client API features: JSON effects, trusted code meshes, endpoint trails, spline motion, coherent curl, attraction/vortex, shared wind, sprite sheets, projected velocity alignment, mesh pixelization, effect bloom and named parameters. It reports declared engine support, not whether a shader pack, framebuffer size or current frame budget permits a particular pass. There is no network-sync capability in this release.

JSON launched through the client uses the same spline/orbit/field simulation as the editor. A path is a bounded list of 3D points; it never contains code. Set speed, force, spread, wind and field strengths to zero for an exact path, or combine them deliberately for drift and turbulence. `Geometry.catmull` and `Motion.sample` are pure Java helpers for trusted integrations that need the same interpolation in a custom visual.

`HIERARCHICAL_TRANSFORMS` and `LAYER_TRANSFORM_ANIMATION` report JSON hierarchy and effect-seconds track support. `Transforms.sample(effect, seconds)` evaluates poses before pivot subtraction; Simulation applies the pivot once. `Geometry.create(particle, origin)` preserves complete mesh orientation. The previous Emitter constructor remains available with identity transforms.
