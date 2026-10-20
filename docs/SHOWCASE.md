# Spline Serpent: authored effect breakdown

Author: XircTwerk. Original Studio JSON, procedural sprites and meshes; no copied reference effect or texture. Open Advanced presets > Spline Serpent. Tools > Paths edits the head/body trajectory. Motion edits the orbit and velocity alignment. World shows the real material pipeline; display bloom is still not a substitute for world inspection.

This composition shows how a few reusable motion rules can produce a coherent moving visual, rather than treating each particle as unrelated noise. The shared spline has five 3D points: [-2,0,0], [-1,1,1], [0,0,-1], [1,1,0], [2,.5,0]. Births at different times occupy different parts of that curve. Everything has a finite lifetime and the fixed seed reproduces the motion.

| Layer | Timing | Contribution and editing guidance |
|---|---|---|
| serpent_ribbon | Emits 0–3s; each birth lasts 2s | Cyan path-following ribbons make the body readable. Width shrinks .06 to .006 over life; 32 history points retain the recent curve. Increase lifetime for a slower traversal or edit the spline points for the silhouette. |
| serpent_glints | Emits 0–3s; lifetime 1.8s | White highlights trace the same spline slightly faster. Small scatter keeps them from perfectly overlapping. Velocity alignment and .25 speed stretching make their orientation follow the actual path. |
| orbital_wake | Emits 0–3s; lifetime 1.5s | Violet motes follow the spline with an additional [.2,.25,.2] orbit at 540 degrees/s. This separates their motion from the body without introducing incoherent random velocity. |
| gate_ring | One birth at 0s; lifetime 3s | Thin upright ring marks the starting point at [-2,0,0]. Its stable shape provides a contrast to the moving streams. |
| arrival_pulse | One birth at 2s; lifetime .8s | Expanding horizontal ring supplies a clear secondary beat. Mesh scale grows .05 to 1.5. |
| arrival_pulse_dust | 80 births at 2s; lifetime .8s | Small particles break the pulse's perfect edge and spread outward with damping. |
| arrival_core | One birth at 2s; lifetime .35s | A brief bright sphere marks the path endpoint [2,.5,0]. Its short duration prevents the highlight from obscuring the body. |

Markers label emission, full body and tail at 0, 2 and 3 seconds. The Serpent and Wake folders allow contribution audition without changing saved JSON. Mute the glints to assess the body silhouette; then isolate the wake to see the difference made by orbit displacement. Use Tools > Preview > Silhouette to check shape without authored hue, and World to assess bloom.

Suggested variations: reduce wake orbit speed for a calmer effect; move path points into a shallow S for a blade trace; remove scatter and use a constant width for a technical energy conduit. Save a checkpoint before variations and use A/B snapshots to compare. These suggestions use implemented controls; they do not require a shader graph, sub-emitter system or model adapter.

## Prismatic Orrery

Load Advanced presets > Prismatic Orrery. The non-emitting orrery_pivot owns the effect-seconds rotation, scale and position keys. Three armillary rings and two satellite cores inherit it; clockwork_dust reveals the changing frame and ignition_stars adds the opening beat. At 2 seconds the hierarchy expands, at 4 it reaches its raised crest, and at 6 it contracts. Select the parent and open Inspector > Transform > Rotation, scale and layer animation. Scrub seconds, edit rotation, or switch position interpolation to spline. Inspect display and World preview; meshes rotate through their full vertex basis.
