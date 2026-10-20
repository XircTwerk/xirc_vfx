# Library extraction verification

Xirc's VFX 0.1.0 extracts the implemented engine, renderer, code API, authoring utilities, presets and optional editor from Studio. It does not claim completion of features previously listed as planned.

- `:core:test build stageRelease`: 70 semantic/schema/security/core tests passed; both Minecraft 1.21.1 loader artifacts and pure Java core compiled.
- The standalone Fabric runtime suite reached XIRC_SMOKE_COMPLETE. The standalone packaged NeoForge suite reached XIRC_SMOKE_COMPLETE after the input harness was paced against actual ImGui render frames. Earlier NeoForge attempts failed differing simulated input interactions; assertions were retained.
- The host project-directory API is synchronized and default initialization does not overwrite a directory already selected by a host application. Studio retains config/xirc_vfx_studio; standalone library storage defaults to config/xirc_vfx.
- Loader metadata lists XircTwerk as the only project author. All required bundled third-party notices remain present. Pure core, loader and sources jars include the project MIT license.

Studio binary-consumer verification and paired runtime results are recorded in its integration documentation. The separate Studio application contains no engine or editor implementation classes; its loader adapters consume versioned library artifacts.


Post-release consumer verification: Studio 0.9.0 passed both Fabric and NeoForge integration suites through XIRC_SMOKE_COMPLETE using the separate 0.1.0 library jars. Fabric loaded the full release mod and nested UI dependencies from mods/; NeoForge loaded both packaged jars. Anonymous public downloads matched the tested core/Fabric/NeoForge binaries by SHA-256. Studio's forced build and three binary-consumer tests passed against the default public release endpoint, with its no-vendoring boundary check passing. Screenshots of both editors retained Studio's host branding.
