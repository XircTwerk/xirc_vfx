# Effect authoring for Xirc's VFX Studio

This directory is the installed mod's local effect workspace. Author: XircTwerk / Xirc.

Read `schema/effect.schema.json` and `docs/AGENT_AUTHORING.md` before editing. Write only bounded schemaVersion 1 effect JSON in `effects/<lowercase_name>.json`. Preserve unrelated authored fields, curves, gradients, ranges and emitters. Do not generate executable Java, commands, expressions or shaders as effect data. The editor will validate on Load and supports Undo for edits made within the editor.

The user uses Codex or Claude Code externally. No provider credentials belong here. Describe visual-reference uncertainty; still images cannot establish timing or motion. Respect the supported capabilities in `docs/JSON_FORMAT.md`. Use the schema to validate; if the source project is available, also run its `:core:validateEffect` task. Load the result in Minecraft to assess appearance.

Templates are installed in `effects/`. Packages belong in `exports/`; import using `/xvfx import <name>`.
