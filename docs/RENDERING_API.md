# Rendering and shader API — 0.3

The client library provides reusable image passes as well as VFX. All GL calls belong on Minecraft's render thread. These APIs are in `com.xirctwerk.vfx.api.render`; the pure Java material model is `com.xirctwerk.vfx.core.Material`.

## Authoring in Studio

Open **View → Materials / Shaders**. Start with a material preset or add operations. The preview supports a plane, raymarched sphere and cube, a five-second playback clock, before/after, the final image and the latest two intermediate buffers. Expand a node to change its bounded parameters, tint, enabled state, amount keys and stack order. Undo/redo retain up to 50 revisions. Save/export writes `materials/<name>.json` beneath the Studio data folder; Load validates before replacing the current document.

**Apply stack to Minecraft world** explicitly enables full-scene post processing, including visible VFX and bloom. Closing the workspace/editor or leaving the world releases it. It affects the current world image, not individual mesh surfaces. Materials do not automatically activate from an imported effect. A Java integration can opt into `RenderEffects.world(material)` and must call `RenderEffects.world(null)` when finished. There is one client-wide world stack; owners must coordinate its use.

## Canonical material JSON

Material documents are separate from effect JSON. `schema/material.schema.json` and `Material.compile` define the same bounded contract. The effect schema and its version remain unchanged.

```json
{
  "schemaVersion": 1,
  "nodes": [
    {"operation": "gradient", "amount": 0.65, "color": [0.2, 0.5, 1, 1]},
    {"operation": "dissolve", "scale": 12,
     "keys": [{"time": 0, "value": 0}, {"time": 1, "value": 1}]},
    {"operation": "pixelate", "amount": 1, "scale": 4}
  ]
}
```

Every node reads the preceding RGBA image. `operation` is required; optional fields: `amount` 0..1 (default .5), `speed` -10..10 (1), `scale` 1..64 (8), `color` four RGBA channels 0..1 ([.55,.25,1,1]), `enabled` boolean (true), `keys` up to 64 strictly increasing `{time,value}` pairs in 0..1. Keys override amount and interpolate linearly. At most 16 nodes; material files are capped at 64 KiB. Unknown fields, duplicate keys, nonfinite values, traversal and symlinks are rejected.

| Operation | Meaning |
|---|---|
| dissolve | Procedural cell mask; amount is the erosion threshold |
| outline | Alpha/depth discontinuity tinted with color |
| fresnel | View-angle rim from normals reconstructed from scene depth; alpha-edge fallback without depth |
| distortion | Animated sinusoidal UV displacement |
| scroll | Wrapped UV motion; negative speed reverses it |
| gradient | Vertical tint multiplication |
| chromatic | Horizontal red/blue separation |
| pixelate | Screen-aligned nearest sampling; amount=1 and scale=4 gives four-pixel blocks |

These are image materials, not a replacement for Minecraft's block/entity material system. Texture input is the source texture passed by the caller. Alpha acts as the mask; dissolve adds procedural noise. Imported GLSL, arbitrary expressions, dynamic lights, a visual branching node graph and physical surface shaders are not implemented. They are not silently accepted by JSON.

## Reusable image pipeline

```java
// Create once on the render thread and close on teardown.
var pipeline = new RenderPipeline();
var material = Material.compile(Material.preset("spectral_shield"));
int output = pipeline.render(sourceTexture, depthTexture, width, height,
                             material, elapsedSeconds, normalizedAge);
pipeline.blit(output, destinationRenderTarget);
// pipeline.close();
```

The source may be an effect capture, a texture or a world image. Pass 0 for absent depth. Primitive previews retain their own perspective depth. For external depth captures, an overload accepts the matching projection matrix; the default uses Minecraft's current projection. Inputs remain caller-owned. The pipeline owns two nearest-filtered ping-pong color targets (up to 4096 per axis and eight megapixels), never samples its own output target and restores framebuffer, viewport, GL program/VAO, texture units 0/1, blend, depth, culling, scissor, write masks and clear color. `intermediateTextures()` exposes only the latest two buffers, valid until the next render/resize. The asynchronous `gpuMillis()` timer avoids waiting for GPU completion; zero means no result yet. Shader compiler/linker messages are actionable exceptions; Studio displays failures and disables a failing world stack.

`RenderTargets` is a separate named pool capped at eight targets / 128 MiB. `acquire(name,w,h,depth)` supports reuse and resize; `copyDepth` explicitly copies scene depth. Close the pool to release GPU memory.

`RenderGraph` composes trusted Java callbacks with named image dependencies. Inputs must precede consumers; duplicate names, forward references and cycles are rejected. Every pass has explicit opaque/alpha/additive blending, depth-test/write and culling state, a cleared owned target and an independent `RenderState` scope. Up to eight passes. The reserved input `source` is caller-owned. See the library's `RenderingExamples.copyThrough` for a working example.

## Trusted mod shaders

`ShaderProgram(vertexSource, fragmentSource, uniformTypes)` accepts source supplied by installed Java mod code. This API is deliberately absent from document loading. It compiles/links with diagnostics, checks declared FLOAT/INT/VEC2/VEC3/VEC4/MAT3/MAT4/SAMPLER types, rejects nonfinite uniform values, and requires binding the program before updating a uniform. Matrices use finite column-major float arrays (9 or 16 values). Texture units 0/1 are supported. Use `RenderState` around custom GL work; bind your own vertex layout or use a fullscreen triangle. Source is bounded at 128 KiB per stage. Close programs on teardown; never feed imported JSON/files into this API.

`RenderingExamples` ships inside the library, including a hologram world stack and a graph example. No separate integration mod or gameplay hooks are required.

## Pixelization and bloom

Procedural mesh bloom is captured separately for each authored pixel size. Geometry uses its existing world-grid snap; the final halo samples on the same screen pixel grid. Smooth layers remain smooth, so a pixelized slash does not pixelate an unrelated smooth aura. A final `pixelate` material can deliberately pixelate the complete composed world image, including bloom.

## Verification

Core tests cover material presets, bounded parsing, schema agreement, animation, file safety and visual-kind transitions/undo. Opt-in native smoke tests compile all built-ins, render three preview primitives, check exact 4×4 pixel blocks, reject a broken trusted shader, check GL state restoration and open/save/load/close the actual Materials workspace with the world stack enabled. These checks do not establish compatibility with every external shader pack.
