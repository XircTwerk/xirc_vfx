# Library extraction verification

Xirc's VFX 0.1.0 extracts the implemented engine, renderer, code API, authoring utilities, presets and optional editor from Studio. It does not claim completion of features previously listed as planned.

- `:core:test build stageRelease`: 70 semantic/schema/security/core tests passed; both Minecraft 1.21.1 loader artifacts and pure Java core compiled.
- The standalone Fabric runtime suite reached XIRC_SMOKE_COMPLETE. The standalone packaged NeoForge suite reached XIRC_SMOKE_COMPLETE after the input harness was paced against actual ImGui render frames. Earlier NeoForge attempts failed differing simulated input interactions; assertions were retained.
- The host project-directory API is synchronized and default initialization does not overwrite a directory already selected by a host application. Studio retains config/xirc_vfx_studio; standalone library storage defaults to config/xirc_vfx.
- Loader metadata lists XircTwerk as the only project author. All required bundled third-party notices remain present. Pure core, loader and sources jars include the project MIT license.

Studio binary-consumer verification and paired runtime results are recorded in its integration documentation. The separate Studio application contains no engine or editor implementation classes; its loader adapters consume versioned library artifacts.


Post-release consumer verification: Studio 0.9.0 passed both Fabric and NeoForge integration suites through XIRC_SMOKE_COMPLETE using the separate 0.1.0 library jars. Fabric loaded the full release mod and nested UI dependencies from mods/; NeoForge loaded both packaged jars. Anonymous public downloads matched the tested core/Fabric/NeoForge binaries by SHA-256. Studio's forced build and three binary-consumer tests passed against the default public release endpoint, with its no-vendoring boundary check passing. Screenshots of both editors retained Studio's host branding.


## Library integration features — 0.2.0

Implemented: JSON/code playback handles, validated per-instance numeric overrides, entity attachment target-loss policies, optional registered-definition server playback and stop, built-in integration demos, Maven metadata/publication staging, per-owner CPU/submission profiling and configurable session budgets. JSON schemaVersion remains 1; no authoring fields were added.

- 79 core tests pass, including nine focused parameter/immutability, transform, explicit-seed, digest, bounded-protocol and lifecycle tests. Both loader artifacts, source jars, POMs and Gradle module metadata build successfully.
- Full standalone Fabric and packaged NeoForge suites reached XIRC_SMOKE_COMPLETE after the networking/profiling changes. Assertions covered handle pause/move/tint/fade/parameters, STOP/FADE_OUT/DETACH attachment policies, all four runnable examples, budget rejection, measured render submissions, actual integrated-server packet delivery/stop and mismatched-definition rejection, alongside all existing editor/render/cloud checks.
- Fabric dedicated-server settings startup executed common initialization. NeoForge's headless GameTest server loaded the packaged library and passed one required test exercising the common registry and server API without client rendering classes. Its ordinary --initSettings mode intentionally does not initialize FML mods, so it is not used as API verification.
- Screenshot review prompted smaller/more distant charge and weapon demos. Final Studio consumer results, including child-impact cleanup, server entity attachments and missing-texture rejection, are appended after paired-artifact verification.
- Profiling reports CPU work and actual particle/geometry draw submissions, including geometry generation and repeated bloom geometry. Shared post passes and GPU completion are not attributed as per-effect GPU cost. Server playback is ID/digest based, scoped to dimensions/range and chunk tracking; it does not transfer files or provide late-join replay.
