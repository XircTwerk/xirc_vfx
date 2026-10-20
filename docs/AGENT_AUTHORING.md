# Create effects with Codex or Claude Code

Xirc chose external coding assistants as the authoring workflow. The mod does not call an AI API, upload project content, hold credentials or install Claude Code/Codex. The agent receives only the files and references you choose to share through that agent's normal interface.

## Text → editable effect

Give your agent this request:

> Read the Studio schema and JSON documentation. Create a purple spiral around the player with slow rising sparks. Use two emitters, a fixed seed and only supported v1 fields. Save `purple_spiral.json` in the installed Studio effects directory. Validate it. Explain which controls change width, density and speed.

Have the agent inspect a starter example first. The mod installs templates and documentation into `config/xirc_vfx_studio/`; the source repository also contains examples. In Minecraft, type `purple_spiral` into the filename box and click Load. Preview, customize, save, then copy the attachment command.

## Focused edits

> Open `purple_spiral.json`. Preserve shape, offsets, timing, texture references, gradients and every unrelated emitter. Make the spark emitter blue, reduce its rate by half, and double effect duration. Show the exact changed paths, validate and save.

Use targeted JSON property edits; avoid flattening curves or recreating the document from a simplified description. To double particle life as well as emission duration, ask explicitly. The in-game editor retains JSON and supports Undo/Redo. External file edits should be reviewed in your agent or Git before loading: the mod cannot undo changes made outside its own current project history.

## Reference → approximation

Attach the image/video to the agent, not to Minecraft. Ask it to produce an observation table (shape, palette, density, apparent size, motion, timing, trails, beam, glow), assumptions, and supported JSON. Single images provide uncertain motion/timing and scale. Videos offer more timing information but do not reveal engine settings or assets. Do not fabricate exact matching or silently replace unavailable features.

The independent v1 renderer has emissive sprites, not screen-space bloom. Recreate glowing appearances approximately with a larger soft sprite and a brighter core emitter. Explain that approximation. Use bundled textures unless you intentionally supply a resource pack. No shader code or executable expressions are accepted.

## Validation contract

1. Parse strictly valid JSON; no comments, trailing content or duplicate fields.
2. Validate against the machine-readable schema.
3. Validate semantic constraints using the source project's command, when available:

```powershell
.\gradlew.bat :core:validateEffect "-Peffect=C:\absolute\path\purple_spiral.json"
```

4. Verify referenced resources exist in the selected Minecraft resource packs or imported package.
5. Load and preview in Minecraft. Schema validity does not prove visual similarity.

On invalid output, report the path and perform at most two focused repairs, then explain remaining issues. Never run model-generated shell/Java/shader content as part of effect playback. Complexity is bounded by the schema and runtime budget.

## Repeatable visual refinement

Keep the seed fixed. Use the same background, reset the camera, restart playback, and capture the same simulation time shown at the top of the preview. For world captures, fix the player position/orientation, time of day, weather, resource packs and graphics settings. Pause preview for frame comparisons. The editor's orthographic sprite preview shares simulation but does not reproduce world lighting, depth occlusion or exact ribbon compositing.

Ask for one focused correction at a time: “Reduce spiral radius from 0.9 to 0.6; keep timing and color unchanged.” Record edited paths and compare again. This workflow supports manual assessment; it is not an automated image similarity metric.
