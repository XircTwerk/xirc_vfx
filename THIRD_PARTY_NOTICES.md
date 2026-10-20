# Third-party notices

Xirc's VFX is authored and maintained by XircTwerk. These required notices describe included components, not project coauthors.

- The effect bloom blur shader includes MIT-licensed Shimmer code, copyright Low-Drag-MC. Its original header and [full license](shared/src/main/resources/third_party/shimmer-LICENSE.txt) are retained. [Upstream Shimmer](https://github.com/Low-Drag-MC/Shimmer).
- imgui-java and Dear ImGui runtime dependencies retain their MIT licenses in `shared/src/main/resources/third_party/` and their nested jars.
- Inter font retains its SIL Open Font License in `shared/src/main/resources/assets/xirc_vfx_studio/fonts/OFL.txt`.
- Gson is an external Apache-2.0 dependency of pure Java tooling; Minecraft provides Gson for loader distributions.
- LDLib2 informed independent architecture work; its Java implementation and runtime are not included. Photon supplies optional user import data and creative reference; no Photon/LDLib source, binary payloads or user reference assets are distributed.

See [rendering provenance](docs/RENDERING_REFERENCES.md) for the existing shader's exact upstream revision. The main MIT LICENSE covers original XircTwerk code, not a relicensing of third-party components.
