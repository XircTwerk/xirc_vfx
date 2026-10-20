# Target and feature capabilities

## Target status

| Target | Planned | Implemented | Compiled | Runtime smoke tested |
|---|---|---|---|---|
| Minecraft 1.21.1 Fabric | Yes | Yes | Yes | Yes: editor and world rendering |
| Minecraft 1.21.1 NeoForge | Yes | Yes | Yes | Yes: editor and world rendering |
| Minecraft 1.21.1 Forge | Later assessment | No | No | No |
| Minecraft 1.20.1 Fabric / Forge | Long-term brief | No | No | No |
| Minecraft 1.20.1 NeoForge | Assess applicability | No | No | No |
| Newer Minecraft / loaders | Individually assess | No | No | No |

Selected starting targets are Fabric Loader 0.19.5 + Fabric API 0.116.17+1.21.1 and NeoForge 21.1.256. Java 21. Official Mojang mappings on both. This is an independent client mod; no Photon version determines its runtime capabilities.

## Definition and runtime

| Capability | v1 status / limits |
|---|---|
| Canonical JSON, version, metadata, IDs | Implemented; strict parse + runtime validation + published Draft 2020-12 schema |
| Multiple emitters and hierarchy | Implemented; translation, rotation and uniform scale hierarchy; 64 emitters; global particle budgets retained |
| Camera-facing particles | Implemented; original procedural PNGs / resource-pack textures |
| Beams / trails | Implemented as textured camera-facing ribbons; per-particle trails |
| Transform / simulation space | Translation, rotation, uniform scale, authored pivot and local/world space; effect-seconds transform keys |
| Rate, delayed emissions, bursts | Implemented; emitter start/duration sequence timing |
| Size/speed/rotation values | Constants, random ranges, normalized-age curves; linear, step, smooth and bounded Bezier easing |
| Color values | RGB/RGBA constants and gradients with the same segment easing |
| Named parameters | Up to 32 validated numeric controls bound to scalar layer fields; compiled copies preserve authored bases |
| Lifetime values | Constants and random ranges |
| Shapes | Point, sphere volume, ring, spiral, box, approximate cone |
| Forces / noise / collision | Constant acceleration and shared wind, exponential drag, deterministic wander, coherent analytic curl, attraction/repulsion and axial vortex/pull; approximate endpoint block collision |
| Spline and orbit motion | Bounded inline 3D Catmull-Rom points, closed/open paths, lifetime traversal/scatter and elliptical orbit displacement; display/world shared simulation |
| Velocity-aligned sprites | Projected actual motion alignment and bounded speed stretching in display and world |
| Texture / material | Explicit PNG references; alpha/additive, emissive base, bounded world bloom and row-major sprite sheets |
| Seed | Fixed seeded Java Random; fixed 20 Hz simulation |
| Resource package | Bounded `.xvfx` ZIP export/import; persistent local PNG resources |
| Pixelization | Procedural geometry only: scale 1-8, nearest-upscaled framebuffer and world-grid snap; separate effect-only bloom; no arbitrary shader graph or dynamic lights |
| Mesh primitives | Slash, ring, cube, sphere; unlit colored geometry; filled display and world rendering, optional display wireframe |
| Code API / animated endpoint trails | Client Java callbacks and arbitrary quad meshes; smooth Catmull-Rom base/tip ribbons; see CODE_API.md |
| Animated mesh extents / lightning | Mesh scale constants/ranges/curves and bounded deterministic animated lightning ribbons; shared display/world geometry |
| Mesh assets / audio / sub-emitters | Not implemented; rejected unsupported fields |
| Expressions / executable generated content | Not accepted |
| Photon `.fx` import | Legacy emitter-list and FX version 1 compressed NBT → validated Studio JSON; explicit approximation review; no Photon export/runtime dependency |
| Multiplayer synchronization | Not implemented; local-client visibility |

## Editor and authoring

| Workflow | Status |
|---|---|
| Presets → preview → save → world | Thirteen starter presets plus twelve original multi-layer Advanced presets; Spline Serpent, Vortex Forge and Orbital Lattice demonstrate coherent motion |
| Controls and JSON | Dear ImGui workspace, nine inspector categories, visible numeric Min/Max captions and contextual tooltips, opacity/spin/sprite aspect controls, dockable panels and validated drafts; native text Ctrl+A/C/X/V/Z/Y, apply/format/save shortcuts |
| Undo/redo | 50 revisions; continuous drags and applied drafts create single entries; external edits need Git/editor history |
| World preview | Live toggle, click a block within 128 blocks to place a fixed pivot, RGB pivot marker, drag to look; actual world renderer/collision and cleanup |
| Scene preview | Shared simulation with orthographic GUI projection; camera orbit, zoom/reset, backgrounds, one-block axes, two-block reference |
| Playback controls | Play/pause, restart, scrubbing and preview loop; full-length mode derives a complete cycle from layer emissions and particle tails |
| Emitter editing | Named layer list, duplicate/delete, explicit visual/shape/space choosers; grouped numeric/vector/texture/burst/parent fields; full JSON |
| Layer audition | Temporary Mute/Solo in display and world preview; multiple solos, Show all layers; authored JSON and playback clock preserved |
| In-game guide | Help → Making effects: impact, portal, lightning, curves/timing and quality checks |
| Graphical properties | Drag-point size/speed/rotation curves, lifetime/random ranges, color/alpha gradients and timed burst tables |
| Keyframe timeline | Optional dockable tracks for size/speed/rotation/color/mesh scale; add, retime, delete and edit keys and segment easing; existing canonical curves/gradients |
| Tools workspace | Ctrl+P / View > Tools; Ctrl-click multi-selection, folders with audition controls, validated relative edits, batch translation, track copy/paste and saved clips, selected-key retiming/stretching, timeline markers, exposed parameters, frame stepping/playback rate, session camera bookmarks, undoable A/B snapshots, display silhouette/size heatmap/bounds, particle counts and budget estimates, deterministic variations and bounded checkpoint recovery |
| Emission timeline | Selectable multi-layer start/duration bars and validated timing edits; burst events use the existing event editor |
| Preview scrubbing | Display and world preview replay at 20 Hz, paused at the selected second; bounded incremental replay keeps long seeks responsive |
| New project | File > New project or Ctrl+N; neutral emitter authored by XircTwerk, validates before replacing, undo restores previous scene |
| Project browsing | Scrollable saved-effect and preset dialogs; no thumbnail file browser |
| Localization | ImGui interface and diagnostics currently English; bundled Inter uses the standard Latin glyph range |
| Agent text generation / focused editing | External Codex/Claude Code workflow, instructions and schema validation; no in-game provider |
| Reference approximation | External-agent observation/refinement guide; no internal image/video analysis or automated similarity score |

## Limits and next increments

Current hard bounds are conservative engineering limits rather than hardware-wide benchmark conclusions: 8192 live particles across at most 16 playback instances; 65536 estimated ribbon segments per definition; 64 imported texture bindings/session; JSON 1 MiB; resources 4 MiB each; package 20 MiB. Starting playback rejects a combined estimated particle budget above 8192 instead of quietly reducing existing effects.

Next increments: multi-layer emission timeline, richer project browsing, interpolated simulation/rendering, physically better collision and cone shapes, transparent sorting quality, synchronized world playback, optional controlled bloom/material graphs, then additional MC versions/loaders with separate builds and tests. No unimplemented item is claimed as supported merely because the format is JSON.

## Additional editor diagnostics

Tools > Diagnostics provides session particle/geometry warning thresholds with actionable messages; these do not change hard budgets. Tools > Preview includes opaque diagnostic heatmaps for opacity, speed (0–20 blocks/s), size (0–2 blocks) and relative authored size range. Timeline > Composition map compares emission windows, conservative maximum particle tails, burst events, markers and the playhead. Click a row to select and seek. GPU timing and exact visibility measurements remain unsupported.

Tools > Preview > Compare selected contribution toggles a display wipe comparison: the complete visible composition on the left, selected layers on the right, sharing simulation and camera. It is an orthographic authoring comparison, not a world post-processing comparison.

Tools > Transforms provides identity-parent grouping, static rotation/scale, and a separate seconds timeline for whole-layer position/rotation/scale animation with linear/spline position interpolation. Local particles follow the hierarchy; world particles retain birth transforms. Prismatic Orrery demonstrates nested animated transforms. Nonuniform scale and world transform gizmos remain unsupported. Transformed framing is conservative; it is not a culling optimizer.
