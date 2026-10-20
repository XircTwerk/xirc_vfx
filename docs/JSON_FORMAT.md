# Effect JSON version 1

The JSON is the canonical editable and runtime definition. `EffectCodec` compiles it into immutable records; `Project` retains the original JSON. There is no conversion to Photon NBT and no opaque binary payload. Editor selection, camera, pause and panel arrangement are session state; playback position and entity attachment are separate runtime instance settings.

## Root

| Field | Meaning / default | Constraints |
|---|---|---|
| schemaVersion | Required format version | Exactly 1 |
| id | Required stable namespaced identifier | `namespace:path`; lowercase; no traversal |
| name | Display name, defaults to id | Up to 512 characters |
| author, description | Informational text | Up to 512 characters |
| duration | Emission duration in seconds; 5 | 0.05–300 |
| autoDuration | Full effect playback length; false | Boolean; derives the maximum layer start + emission duration + maximum lifetime, plus one simulation tick |
| pivot | Local point placed at the playback anchor; [0,0,0] | Each component -128 to 128; subtracted once after parent offsets |
| loop | Repeat emission; false | Boolean |
| seed | Deterministic seed; 0 | Signed 32-bit integer |
| emitters | Required ordered emitter list | 1–64; unique IDs |

## Emitters

| Field | Meaning / default | Constraints |
|---|---|---|
| id | Required stable object identifier | Unique `[a-z][a-z0-9_]{0,63}` |
| parent | Parent emitter id; empty | Must exist; no cycles; translation, rotation and uniform scale |
| kind | `particle` | particle, beam, trail, slash, ring_mesh, cube_mesh, sphere_mesh, lightning |
| space | `local` | local follows playback anchor; world preserves spawn position |
| offset | Parent-relative translation; [0,0,0] blocks | Each component −128–128 |
| start | Delay within emission cycle; 0 seconds | 0–300 |
| duration | Emitter emission interval; authored root duration | 0.05–300; manual root duration caps emission; full-length mode includes every layer |
| rate | Births per second; 30 | 0–2000 |
| bursts | List of `{time,count}` relative to start | Up to 64; count 1–2000; sum ≤4000 per emitter |
| lifetime | Particle life; 1 second | 0.05–30; constant or range |
| opacity | Multiply color/gradient alpha; 1 | 0-1 |
| spin | Additional rotation speed; 0 degrees/second | -3600 to 3600 |
| aspect | Particle sprite width divided by height; 1 | 0.05-20; rejected on other kinds |
| size | Base sprite height / ribbon width; 0.1 blocks (aspect multiplies sprite width) | 0.001–10; constant, range or curve |
| speed | Initial speed; 1 block/second | −50–50; constant, range or curve |
| rotation | Sprite angle; 0 degrees | −36000–36000; constant, range or curve |
| color | RGBA; [0.7,0.3,1,1] | RGB/RGBA array or gradient; channels 0–1 |
| shape | `point` | point, sphere, ring, spiral, cone, box |
| radius | Spawn radius; 0.5 blocks | 0–64 |
| meshScale | Geometric radius/width multiplier; 1 | 0.001–8; constant, range or normalized-age curve; mesh/lightning kinds only |
| spread | Direction perturbation; 0.2 | 0–1 |
| direction | Normalized starting direction; [0,1,0] | Each component −1–1; zero allowed for spherical bursts |
| force | Acceleration; [0,0,0] blocks/second² | Each component −100–100 |
| drag | Exponential damping; 0 per second | 0–20 |
| noise | Deterministic sinusoidal wander acceleration; 0 | 0–20; not Perlin turbulence |
| collision | Approximate solid-block collision; false | Endpoint sample; may tunnel at high speeds |
| bounce | Velocity reversal multiplier on collision; 0.5 | 0–1; not normal-based physical reflection |
| texture | `xirc_vfx_studio:textures/effect/soft.png` | Namespaced PNG resource under textures/ |
| glow | Emissive base world material; true | Full bright; bloom is separate; no terrain lighting |
| blend | `alpha` | alpha or additive; world material choice |
| bloom | Effect-only world bloom intensity; 0 | 0–4; zero disables that layer's bloom contribution |
| textureAnimation | Optional sprite sheet description | columns/rows 1–64; frames 1–4096 ≤ cells; fps 0.1–120; loop boolean; particle/beam/trail only |
| fade | Final fraction of particle life fading; 0.3 | 0–1; multiplies gradient alpha |
| length | Beam length / spiral height; 3 blocks | 0.01–128 |
| pixelSize | Procedural mesh pixel scale; 1 | Integer 1-8; values above 1 require a mesh kind |
| trailPoints | Recent per-particle positions retained; 12 | Integer 2–64 |

`lightning` is an original bounded 24-segment animated ribbon. Direction points toward its endpoint; radius is length, size is width, and rotation varies the deterministic pattern. The lateral shape changes in 18 discrete phases over particle lifetime. It uses the same world/display topology and optional pixelization as meshes. `meshScale` animates the actual mesh extent, allowing growing shockwaves, charging cores and collapsing spheres; it does not change spawn positions. Ranges sample once per birth.

Editor Mute/Solo are temporary audition state, not effect JSON. Mute removes that layer's current particles; unmute resumes future births. Solo allows the selected set of layers while muting the rest. Hidden parents still provide their complete transform to visible children. Show all layers clears audition state. These controls affect only the editor's display/world preview and do not change authored data or ordinary playback instances.

Spawn shapes use emitter-local axes transformed by the hierarchy; identity transforms retain the original world-axis behavior. Cone currently perturbs direction and origin within a sphere rather than providing a mathematical cone volume. Spiral angle advances at 4 radians/second; spread controls angular scatter; height grows through the effect cycle. World-space trail particles retain old positions as attachments move. Beams use direction and length; speed moves the beam origin.

## Values and interpolation

Sprite sheets use `textureAnimation: { "columns":4, "rows":4, "frames":16, "fps":20, "loop":false }`. Frames are row-major, starting at the PNG's top left. Omitted columns/rows default to 1; omitted frames default to all cells, FPS to 20 and loop to true. Non-looping playback clamps to the final frame. Use equal-size PNG cells. Sprite sheets are rejected for untextured mesh kinds; functions/expressions remain unsupported. The bundled original `nova_sheet.png` is a 4×4 example.

Bloom captures only opted-in layers with world depth testing, then applies three Gaussian blur resolutions and additive composition. It is not dynamic lighting and is shown in World preview; the orthographic display does not run the post compositor. Alpha suits smoke; additive suits luminous overlaps. Existing documents default to alpha with bloom disabled. Position/atlas age interpolation does not change simulation physics or authored timing.

```json
{
  "lifetime": {"min": 1, "max": 2},
  "size": {"curve": [{"time": 0, "value": 0.2}, {"time": 1, "value": 0.01}]},
  "color": {"gradient": [{"time": 0, "color": [0.7, 0.1, 1, 1]}, {"time": 1, "color": [0.2, 0.4, 1, 0]}]}
}
```

The optional editor Timeline writes these same curve/gradient objects. Adding a key samples the existing track at the cursor; editing a key changes only that property. Constant tracks gain endpoint keys at 0 and 1. Random ranges require explicit conversion in the property editor. There is no separate timeline format or auto-key behavior; ordinary inspector edits retain their existing meaning. Layer sequence timings remain seconds, while property keys remain normalized particle age. Preview scrubbing replays the fixed-step simulation, including existing collisions in World view; moving attachments and terrain changes are not a recorded replay.

Ranges sample uniformly once per birth; `min <= max`. Curves and gradients use normalized particle age 0–1, strictly increasing key times, and linear interpolation. Values outside the first/last key clamp to the nearest endpoint. Up to 64 keys. Curved speed scales the current velocity relative to its sampled initial speed (or 1 when initial speed is zero); forces and drag still affect motion. Rotation is in the billboard plane. Lifetime curves are rejected because a moving death time would be ambiguous.

Full-length mode retains the authored root duration and does not rewrite layer durations. Missing layer durations still default to the authored root value. The derived runtime cycle can reach 630.05 seconds; scrubbing includes a bounded lifetime margin. Looping full-length playback waits for tails. Manual mode retains the previous emission cutoff semantics. World placement is editor instance state, not absolute coordinates embedded in JSON; the canonical local pivot is shared by display, world and code-launched playback.

## Resources and packages

Bundled, original procedural PNG sprites: `soft.png`, `spark.png`, `ring.png`, `nova_sheet.png` under `xirc_vfx_studio:textures/effect/`. Additional textures come from Minecraft resource packs. Missing textures block world playback/export with the offending identifier.

`.xvfx` is a ZIP containing `manifest.json`, `effect.json` and `assets/<namespace>/textures/...png`. `manifest.json` has `packageVersion:1`, `effect:"effect.json"`, `author`, and a resource identifier list. Import validates paths, duplication, compressed/uncompressed sizes and texture dimensions. Resources become client dynamic textures and are stored in the Studio `resources/` directory for future sessions and re-export. Importing another package with the same resource ID replaces its local binding. Resource paths remain confined to the Studio root.

Limits: JSON 1 MiB, nesting 32, texture file 4 MiB, PNG dimensions 2048², package 20 MiB, exported resources total 16 MiB, 64 imported texture bindings per session. No meshes, scripts, audio or shader code in v1 packages.

## Performance and compatibility

20 fixed simulation ticks/second. Effects may retain live particles after emission stops. Validation conservatively estimates live particles from rates, maximum lifetimes and bursts, including particles surviving from previous loop cycles; estimates above 8192 fail. The client enforces 8192 actual live particles across at most 16 concurrent playback instances. Budget drops are counted in simulations; defaults are conservative starting limits, not benchmark-derived recommendations.

Unknown fields and unsupported features are rejected with their JSON path. There is no extension bag that quietly ignores functionality. Future schema versions need explicit migrations operating on preserved JSON, semantic round-trip tests, and a documented capability matrix. Version 1 is the first format; no older-format migration is implemented. Do not relabel incompatible documents with `schemaVersion:1`.

## Authoring additions in 0.8

Numeric curve and gradient keys optionally contain `easing`, describing the segment **from that key to the next**. Omission is linear. Modes are `linear`, `step`, `smooth` (smoothstep), and `bezier`. Bezier requires exactly four normalized controls `[x1,y1,x2,y2]`, each 0..1. Other modes reject controls. The cubic's X coordinate is inverted with a bounded binary search, so timing controls are meaningful; bounded Y controls keep interpolated values between segment endpoints. Key edits preserve easing and unrelated authored fields.

```json
{"curve":[{"time":0,"value":0.1,"easing":{"mode":"bezier","controls":[0.25,0.1,0.25,1]}},{"time":1,"value":1}]}
```

An emitter's optional `folder` is editor grouping metadata, a string up to 64 characters. It does not imply a new transform hierarchy. Folder selection can translate a set; selected descendants inherit their selected ancestor's movement once. Group selected siblings in Tools > Transforms to create an identity parent without rebaking their authored tracks.

Root `markers` is an optional array of at most 64 `{name,time}` objects. Names are at most 128 characters; time is an effect-clock second from 0..631. Markers label authoring beats without executing runtime events.

Root `parameters` is an optional array of up to 32 named numeric controls. Each contains a unique lowercase `name` (1..32 characters), optional `label` (up to 128), required numeric `min`, `max`, `value`, and 1..64 `targets`. Require min < max and a value inside the range. Numbers are finite and within +/-1000000. Each target contains `layer`, `property`, optional `scale` (1) and `offset` (0). Resolution computes `value * scale + offset`, with no expression evaluation. The complete mapped parameter range must fit the target field's canonical bounds. Bindings support the numeric fields exposed in Tools, reject ranges/curves, reject duplicate target bindings and unknown layers, and still validate particle budgets for the selected value. Compilation resolves a copy; Project preserves the authored base values and all bindings.

```json
{"name":"power","label":"Power","min":0.1,"max":3,"value":1,"targets":[{"layer":"main","property":"size"}]}
```

Animation clips are a separate bounded editor file: `{clipVersion:1,track,value}`. The track is size, speed, rotation, color or meshScale; the value is validated with the canonical effect codec, including interpolation. Clips contain no executable content. Local `clips/` and `checkpoints/` libraries have 128-file limits, 1 MiB per document and confined, symlink-rejecting paths. Automatic recovery writes only `checkpoints/draft.json`; it never overwrites a saved effect. Recovery preserves the last applied project, not an unapplied JSON text draft. Camera bookmarks and A/B snapshots are editor-session state.

## Coherent motion and paths

Root `wind` is shared acceleration [X,Y,Z], each -100..100 blocks/s², default zero. It adds to each emitter's force and optional motion field. These controls use the same fixed-step simulation in display, world and code-launched JSON effects:

| Emitter field | Default / bounds | Semantics |
|---|---|---|
| curl | 0; 0..50 | Analytic three-octave divergence-free flow strength |
| curlFrequency | 1; .01..10 | Spatial frequency of the coherent field |
| attractor | [0,0,0]; components -128..128 | Field center relative to the emitter's hierarchy offset |
| attraction | 0; -100..100 | Acceleration toward the center; negative repels |
| falloff | 1; 0..4 | Strength divides by (1 + distance)^falloff |
| vortexAxis | [0,1,0]; components -1..1 | Normalized spin axis; nonzero when vortex/pull is active |
| vortex, vortexPull | 0; -100..100 | Tangential and inward radial acceleration around that axis |
| orbitRadius | [0,0,0]; components 0..64 | Displacement [rx*cos(a), ry*sin(a), rz*sin(a)] |
| orbitSpeed | 0; -3600..3600 | Degrees per second; negative reverses the orbit |
| path | Absent; 2..64 XYZ points, components -128..128 | Catmull-Rom positions sampled over each particle lifetime |
| pathLoop | false | Smooth closed path, including last-to-first segment |
| pathCycles | 1; .05..10 | Traversals per lifetime; open paths hold their endpoint |
| pathScatter | 0; 0..8 | Bounded positional scatter sampled once at birth |
| alignVelocity | false; particle sprites only | Align long sprite axis with actual projected movement |
| stretch | 0; 0..4; particle sprites only | Width multiplier min(16, 1 + speed * stretch) |

Path/orbit displacement adds to integrated velocity and force motion. For an exact path, set speed, spread, force, wind and motion-field strengths to zero. Closed paths wrap continuously; open endpoints clamp. Splines can curve beyond individual point coordinates, and authored framing uses conservative bounds. Attractor/vortex coordinates follow the emitter anchor for local particles; world-space particles keep their positions while the field anchor moves. Whole-layer animation uses the separate seconds-based transform track below. Surface-following paths and event sub-emitters remain unsupported.

Sub-emitters, nonuniform transform scale, curves on every arbitrary property, expressions, shader/post-processing graphs, imported mesh assets and audio are not supported in v1. Photon `.fx` imports convert user-supplied data to this JSON format with explicit diagnostics; no conversion is claimed lossless. See [Photon import](PHOTON_IMPORT.md).

## Procedural mesh kinds

`slash`, `ring_mesh`, `cube_mesh` and `sphere_mesh` generate bounded quad geometry at each particle position. Direction is the mesh normal/forward axis, rotation turns its plane, radius controls extent and size controls slash/ring width. Slashes reveal over the first third of their lifetime. Meshes use unlit vertex color and fade/gradient values; texture and glow do not change their material. Pixel size 1 is smooth; 2-8 enables nearest-upscaled rendering with world-grid snapping. Editor preview draws filled quads from the same topology with depth sorting; View → Wireframe meshes exposes topology. Pixelization is shown in world playback. Default direction/zero-vector fallback is supported. Mesh geometry participates in per-definition and combined world playback budgets. Arbitrary mesh topology is currently available through trusted Java API only.

## Whole-layer transforms

An emitter may contain `transform`, with optional `rotation` (XYZ Euler degrees, each -36000..36000), uniform `scale` (.01..8, default 1), `pathInterpolation` (`linear`, default, or `spline`) and `keys` (2..64 entries). Each key requires `time` in **effect-clock seconds**, 0..631; optional `position` (XYZ, each -128..128, default zero), `rotation` (XYZ degrees, default zero), `scale` (.01..8, default 1) and segment `easing` use the same bounded easing definition as particle curves. Times must increase with separation greater than .000001 seconds. Unknown fields are rejected.

```json
"transform": {
  "rotation": [0, 20, 0], "scale": 1,
  "pathInterpolation": "spline",
  "keys": [
    {"time": 0, "position": [-2, 0, 0]},
    {"time": 1, "position": [0, 2, 0], "rotation": [0, 90, 0], "scale": 1.2},
    {"time": 2, "position": [2, 0, 0], "rotation": [0, 180, 0]}
  ]
}
```

Local position is offset plus interpolated key position; rotation is static rotation plus interpolated key rotation; scale is static scale multiplied by interpolated key scale. Rotation applies X, then Y, then Z. Euler values interpolate as authored, so 0 to 720 retains two full turns. Values clamp before/after the key range. Spline affects position only and uses Catmull-Rom control points, which may overshoot individual positions. Easing remaps segment time for all animated components. Effect loops restart the transform clock each cycle.

Parent transforms apply to child offsets, positions, directions, spawn shapes, forces, spline/orbit displacement and geometry. The effect pivot is subtracted once after composition. Combined hierarchy scale must remain between .0001 and 64 for the entire track; validation uses conservative per-track extrema. Local particles, velocity and trail history follow changes in the hierarchy. World particles retain birth transforms, size and orientation; later births use the current emitter transform. Shared wind remains world-axis acceleration. Billboards face the camera; authored rotation/spin remain billboard-plane angles. Mesh vertices, including roll around the normal, are transformed in full.

Tools > Transforms exposes static rotation/scale, a seconds timeline with key selection and seeking, key position/rotation/scale editing, interpolation/easing, insertion and deletion. Group selected layers inserts a non-emitting identity parent without rebaking curves, gradients or resources; selected descendants inherit once. Group sibling roots under the same parent space; different parent spaces are rejected with an actionable explanation. At most 64 total layers, including transform groups. Inspector > Transform links to these controls. Authored JSON and undo remain authoritative.
