# Quick start

In 0.5.0, **File → Presets → Advanced presets** adds eight multi-layer compositions. **File → Import Photon .fx** reviews files copied into `config/xirc_vfx_studio/imports/`; open the diagnosed approximation, edit and save JSON. See [Photon import](PHOTON_IMPORT.md) for mapping, limits and resource-pack textures.

Press **V** to open the display immediately. The current effect and editing session remain available when you reopen Studio.

- **File**: presets, saved projects, Save, packages, folder access, external-agent instructions and world utilities. Ctrl+S opens Save. An asterisk marks an unsaved project.
- **Layers**: select, add, duplicate or delete emitters. At least one must remain; parent references must stay valid.
- **Inspector**: expand Appearance, Emission, Motion, Shape / mesh, Behavior or Effect. Drag numbers or Ctrl+click to type; string fields apply with Enter. Hover for units and explanations. The inspector scrolls normally.
- **Curve / range**: constants, deterministic random ranges and drag-point curves for size/speed/rotation. Lifetime supports constants and ranges. Add inserts a midpoint key. Apply validates the effect and changes only the selected property; Cancel discards its draft.
- **Edit gradient**: color/opacity keys and normalized times. A checkerboard shows transparency. Add interpolates a midpoint color. Palette tint retains gradient timing, brightness and alpha.
- **Edit events**: timed bursts, in seconds after the layer starts. Edit time/count and Apply. Invalid duration or particle budgets leave the current project unchanged.
- **JSON**: the complete definition. Apply validates before replacement. Drafts survive hiding/reopening the tab. Reload project explicitly replaces the draft; a notice identifies inspector changes made since the draft was opened. Save applies a current valid draft; divergent draft/inspector changes require an explicit Apply or Reload.
- **Pause**, **Restart**, **Center**: preview playback and camera controls. Left-drag orbits; right-drag pans; wheel zooms.
- **World**: toggle live preview beside your player while editing. A checked box marks world mode. Hold either mouse button in the view to look around; Center faces the effect. Join a world first.
- **Edit**: Undo/Redo or Ctrl+Z / Ctrl+Y. A continuous numeric drag or applied visual draft is one entry.
- **View**: JSON, layout reset, display background and replay settings. Drag tabs/dividers to dock or resize panels. Layout is stored outside effect JSON in studio-layout.ini.

Visual property editors preserve every unrelated authored field. Changing a property's value type is explicit and takes effect only on Apply. The canonical format remains version 1 JSON; no imported code, expressions or shaders are executed.

Projects live in `<Minecraft instance>/config/xirc_vfx_studio/effects/`. Packages import using `/xvfx import <package_name>`. Display meshes are filled; View offers wireframe inspection. World also shows pixelization. See [code API](CODE_API.md) for Java visuals and animated endpoint trails.
