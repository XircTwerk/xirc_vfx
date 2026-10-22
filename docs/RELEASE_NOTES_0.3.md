# Xirc's VFX 0.3.0

- Add a reusable rendering API: typed trusted shaders, scoped GL state, bounded target pools, ordered Java pass graphs, depth access and asynchronous GPU timing.
- Add data-only image materials with dissolve, outline, screen-space Fresnel, distortion, scrolling, gradient tint, chromatic separation and pixelization. Include five presets, amount keyframes, validation/schema and bounded JSON save/load.
- Add Studio's Materials / Shaders workspace with a persistent preview beside the inspector, plane/sphere/cube surfaces, before/after, latest intermediate buffers, world post preview, undo/redo, stack reordering and diagnostics.
- Keep runnable rendering examples inside the library. Imported documents never execute shader code; custom GLSL is available only to trusted Java integrations.
- Fix visual-kind changes by removing only incompatible fields/bindings within an undoable project edit. Selecting the current visual preserves authored JSON.
- Replace bounded drag controls with clamped sliders and use canonical numeric bounds. Display normalized color channels consistently.
- Capture bloom independently for each pixel size and composite its halo on the corresponding pixel grid. Fix the vertex-buffer cache handoff between fullscreen and geometry passes.
- Add the one-line AI assistance disclaimer to the README.

Materials are image operations, including an explicit full-world post preview. Physical surface shaders and a branching visual graph are not part of this release. Effect JSON schemaVersion 1 and multiplayer wire protocol v1 remain unchanged. Studio 0.11.0 requires library 0.3.x; existing consumers should pin and recompile when adopting a new minor API line.
