# Low-Drag-MC rendering references

Requested by XircTwerk. Credit: **KilaBash / Low-Drag-MC and contributors**.

| Upstream / inspected revision | Studied work | Studio result / provenance |
|---|---|---|
| [Shimmer](https://github.com/Low-Drag-MC/Shimmer/tree/49cb20e7406de63027f8666d6dde9b73d8444f80), MIT | PostProcessing, depth-tested effect masks, multi-resolution bloom, separable Gaussian shader | Direct reuse of `seperable_blur.fsh`; full MIT notice retained. Original Studio framebuffer orchestration uses three blur scales and effect-only additive composition. |
| [LDLib2](https://github.com/Low-Drag-MC/LDLib2/tree/ae11d71607ada1364de154634a2652a73b66ba0c), LGPL-3.0 | RenderTargetScope, ShaderTexture and material/editor architecture | Independent implementation: post-pass snapshots/restores actual draw/read framebuffer, viewport, program, VAO, texture bindings, blend factors/equations, depth/cull/scissor state, write masks and clear color. Foreign framebuffer targets are respected by skipping the world bloom pass. No LGPL source/runtime included. |

Studio retains its own JSON, pure Java core and Fabric/NeoForge wiring. Neither library determines Studio's runtime capabilities. Repository clones used for research remain in ignored build directories, not the distributable source/assets.

## Implemented material capabilities

- Bloom for selected JSON particle, beam, trail and mesh layers; bloom for trusted code geometry via `Vfx.Style(color, pixelSize, bloom)`. Code callbacks run once; the emitted quads are reused for the mask.
- Alpha/additive material choice. Additive is useful for luminous overlaps; alpha retains smoke/opaque silhouettes.
- Bounded PNG flipbook animation: at most 64×64 cells and 4096 frames, FPS 0.1–120, looping or clamped playback. Display and world use the same UV selection. The original nova atlas demonstrates the workflow.
- Interpolated positions and sprite-sheet age between 20 Hz simulation steps, including frozen editor pause state.
- Photon bloom/recognized additive factors and whole-sheet animation mapping with diagnostics. Substituted atlases do not receive meaningless cropped animation; preserve the original resource reference to map animation.

Bloom is a world material pass, not a display overlay or terrain-lighting system. It uses the vanilla main framebuffer, at dimensions up to 4096×4096. It does not run in foreign/off-screen targets. Dynamic colored lights, arbitrary shader graphs, imported executable shaders, full Photon material compatibility and shader-pack interoperability are not implemented or established by these changes. Display preview does not reproduce the world bloom compositor. New work should extend the tested frame/material boundaries, with separate compatibility checks.
