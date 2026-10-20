# Xirc's VFX library instructions

Author and maintainer: XircTwerk / Xirc. Original project code is MIT licensed.

- Build an independent mod. Photon provides creative direction and optional user-supplied import data. Do not copy its source, assets or license headers or make its binary format canonical. Independently implemented bounded `.fx` → Studio JSON conversion must report semantic differences before opening approximations. No Photon / LDLib runtime dependency.
- Current implementation targets Minecraft 1.21.1 Fabric and NeoForge, Java 21, official Mojang mappings. Keep shared code in `shared`, pure Java effect logic in `core`, and loader wiring in the loader modules.
- JSON is canonical. Read `schema/effect.schema.json`, `docs/JSON_FORMAT.md` and `docs/CAPABILITIES.md` before authoring new effect fields. Update the schema generator and runtime validation together.
- Retain authored JSON in `Project`; simple edits and mode switches must preserve unrelated ranges, curves, gradients, hierarchy and resource references. Validate before replacing a project. Maintain undo.
- Unsupported properties must produce actionable validation errors. Never execute imported Java, shell, shader code or expressions.
- Never add credentials to source, JSON, logs or exported packages. AI authoring uses external Codex/Claude Code, not an in-game API.
- Keep imports bounded and resource paths inside their intended root. Reject traversal, duplicate keys, symlinks, oversized resources and archive expansion abuse.
- Verify relevant changes using `gradlew.bat :core:test build`; run opted-in runtime smoke checks for client renderer/editor changes when the host can run Minecraft.
- For effect-only edits run `gradlew.bat :core:validateEffect -Peffect=<absolute file>` and report the focused changes and any visual uncertainty.
- Distinguish planned, implemented, compiled and runtime-tested features. Compilation alone does not establish visual correctness or usability.
