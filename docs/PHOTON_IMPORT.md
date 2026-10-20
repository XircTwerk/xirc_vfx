# Opening Photon effects

Studio 0.5.0 independently reads user-supplied gzip-compressed standard NBT `.fx` data. It understands the legacy `emitters` list and the supplied Photon FX `_version:1` layout. It does not depend on Photon/LDLib or load their classes, shaders, assets or source. The user's explicit import request authorizes this compatibility reader; the independent engine and canonical JSON policy remain intact.

1. Choose **File → Import Photon .fx** (also available from File → Open).
2. Use **Browse .fx...**, or enter an absolute path and select **Review file**. Files copied into Studio's imports folder remain available as a list.
3. The green **Converted successfully** message means the file is ready. Click **Open approximation**, above the scrollable differences report, to accept the listed differences. Diagnostics are conversion warnings, not parse failures. No project changes occur during review or failed validation.
4. Edit normally, undo the import if needed, and save Studio JSON. The original `.fx` is never overwritten.

External textures default to bundled sprite substitution so imported effects can open without the original mod. Disable this option to retain resource identifiers; install a Minecraft resource pack containing those PNGs before opening. Only the explicitly chosen source file is read outside Studio; resources remain inside their intended root. The importer does not search adjacent directories, copy reference assets or extract shader code. Custom shader materials always require substitution/rebuilding.

## Semantic mapping

| Input | Studio conversion |
|---|---|
| Particle, beam, trail objects | Corresponding visual kinds |
| Empty objects | Zero-emission parent layers; translation hierarchy retained |
| Position / parent IDs | Local offset and parent; rotations/nonuniform scale diagnosed |
| Tick duration, delay, lifetime / emission rate / speed | Seconds, births per second, blocks per second |
| Constants / lifetime and speed random constants | Constants or sorted ranges |
| Bézier values | 17 sampled linear keys for age animation; emitter-time/random curves use deterministic initial/midpoint approximations, diagnosed |
| Curve lower/upper bounds | Applied to normalized Bézier values |
| ARGB colors / lifetime channel gradients | RGBA / merged interpolated gradient keys |
| Start size / size over life | Square sprite using X, diagnosed when anisotropic or animated |
| Beam end / width / color | Direction and length / sampled width / color; one ribbon for duration |
| Burst time/count | Seconds/count; probability, cycles and interval differences diagnosed |
| Basic shape / texture material | Studio shape / resource-pack PNG reference or explicit substitution |
| Physics gravity / collision / bounce | Acceleration / endpoint collision / bounce, with differences diagnosed |
| Bloom | Effect-only world bloom plus emissive color; upstream tint/algorithm differences diagnosed |
| Whole-sheet UV animation | Converted when the original atlas reference is retained; approximate uniform FPS and row-major frames; functions/row selection diagnosed |
| Active unsupported modules | Actionable diagnostics; rebuild in Studio or trusted code API |
| Values outside Studio bounds | Constrained values explicitly reported; malformed functions/expressions rejected |

Unsupported features include sub-effects, shader/material graphs, custom UV animation functions, inherited transform rotation, anisotropic scaling, orbital velocity, trails attached to particles, noise remapping, per-emitter looping, and advanced collision semantics. Imported trails use Studio's per-particle history. Mixed looping uses the first layer's root loop value and reports the difference. Blend/depth/culling settings use Studio's rendering pipeline and are diagnosed. Every import requires review; there is no lossless claim.

Imports are limited to 4 MiB compressed, 8 MiB expanded, 32 nesting levels, 100,000 NBT nodes, 16,384 entries per list/array and 64 layers. Duplicate compound keys, non-finite numbers, malformed arrays, symlink paths and traversal are rejected. Canonical validation and the existing 8192-particle/65536-segment budgets run before opening. Imported Java, expressions and custom shaders never execute.

## Command-line review

Directory audit is read-only:

```powershell
.\gradlew.bat :core:importPhoton "-Pphoton=D:\path\to\photon\fx"
```

Write a **new** JSON file after explicitly opting into approximation:

```powershell
.\gradlew.bat :core:importPhoton "-Pphoton=D:\path\to\effect.fx" "-Poutput=D:\path\to\effect.json" -Papproximate=true
.\gradlew.bat :core:validateEffect "-Peffect=D:\path\to\effect.json"
```

Output paths must not already exist; source files are untouched. Without `approximate=true`, conversion diagnostics cause an error before writing. `PhotonImporter.Result.requireExact()` provides the same strict behavior for code callers.

The supplied `examples/cursed_fate/photon/fx` reference library was audited locally: **294/294** files produced validated approximations. Those reference files and their assets are not bundled or committed. This establishes parsing/validation coverage, not visual equivalence to Photon.
