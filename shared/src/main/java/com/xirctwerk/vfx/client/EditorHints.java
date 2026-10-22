package com.xirctwerk.vfx.client;

import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.type.ImBoolean;
import java.util.Locale;

/** Presentation metadata only; canonical effect validation remains in the core codec. */
final class EditorHints {
    record Bounds(double min,double max) {}
    private static int ranges,tooltips;
    static int ranges(){return ranges;}
    static int tooltips(){return tooltips;}
    static Bounds bounds(String key){if(com.xirctwerk.vfx.core.Authoring.NUMERIC.contains(key)){var b=com.xirctwerk.vfx.core.Parameters.bounds(key);return new Bounds(b.min(),b.max());}return switch(key){
        case "size"->new Bounds(.001,10);case "speed"->new Bounds(-50,50);
        case "rotation"->new Bounds(-36000,36000);case "spin"->new Bounds(-3600,3600);
        case "lifetime"->new Bounds(.05,30);case "duration"->new Bounds(.05,300);
        case "start"->new Bounds(0,300);case "rate"->new Bounds(0,2000);
        case "radius"->new Bounds(0,64);case "spread","fade","opacity","bounce"->new Bounds(0,1);
        case "aspect"->new Bounds(.05,20);case "length"->new Bounds(.01,128);
        case "drag","noise"->new Bounds(0,20);case "bloom"->new Bounds(0,4);
        case "meshScale"->new Bounds(.001,8);case "pixelSize"->new Bounds(1,8);
        case "trailPoints"->new Bounds(2,64);case "seed"->new Bounds(Integer.MIN_VALUE,Integer.MAX_VALUE);
        case "offset","pivot"->new Bounds(-128,128);case "force"->new Bounds(-100,100);
        case "direction","vortexAxis"->new Bounds(-1,1);case "color"->new Bounds(0,1);
        case "curl"->new Bounds(0,50);case "curlFrequency"->new Bounds(.01,10);case "attractor"->new Bounds(-128,128);
        case "attraction","vortex","vortexPull","wind"->new Bounds(-100,100);case "falloff","stretch"->new Bounds(0,4);
        case "orbitRadius"->new Bounds(0,64);case "orbitSpeed"->new Bounds(-3600,3600);case "pathCycles"->new Bounds(.05,10);case "pathScatter"->new Bounds(0,8);
        default->null;};}
    private static String number(double n){return n==Math.rint(n)?String.format(Locale.ROOT,"%.0f",n):String.format(Locale.ROOT,"%.5f",n).replaceFirst("0+$","").replaceFirst("\\.$","");}
    static String rangeText(double min,double max){return "Min: "+number(min)+"    Max: "+number(max);}
    static void limits(double min,double max){
        if(!ImGui.isItemVisible())return;
        // Draw a compact caption without changing LastItemData: hover, gesture ownership and
        // native pointer coordinates must still belong to the actual numeric control.
        int fontSize=Math.max(11,(int)(ImGui.getFontSize()*.78f));float size=fontSize,x=ImGui.getCursorScreenPosX(),y=ImGui.getCursorScreenPosY();
        String text=rangeText(min,max);boolean wrap=text.length()*size*.62f>ImGui.getContentRegionAvailX();var draw=ImGui.getWindowDrawList();int color=ImGui.getColorU32(ImGuiCol.TextDisabled);
        if(wrap){draw.addText(ImGui.getFont(),fontSize,x,y,color,"Min: "+number(min));draw.addText(ImGui.getFont(),fontSize,x,y+size+2,color,"Max: "+number(max));}
        else draw.addText(ImGui.getFont(),fontSize,x,y,color,text);
        ImGui.setCursorPosY(ImGui.getCursorPosY()+(wrap?2:1)*(size+3));ranges++;
    }
    static void limits(String key){var b=bounds(key);if(b!=null)limits(b.min,b.max);}
    static void tip(String text){if(!ImGui.isItemHovered())return;ImGui.beginTooltip();ImGui.pushTextWrapPos(380);ImGui.textUnformatted(text);ImGui.popTextWrapPos();ImGui.endTooltip();tooltips++;}
    static boolean button(String label){boolean pressed=ImGui.button(label);tip(action(label));return pressed;}
    static boolean button(String label,float width,float height){boolean pressed=ImGui.button(label,width,height);tip(action(label));return pressed;}
    static boolean smallButton(String label){boolean pressed=ImGui.smallButton(label);tip(action(label));return pressed;}
    static boolean checkbox(String label,boolean value){boolean pressed=ImGui.checkbox(label,value);tip(action(label));return pressed;}
    static boolean checkbox(String label,ImBoolean value){boolean pressed=ImGui.checkbox(label,value);tip(action(label));return pressed;}
    static boolean section(String label,int flags){boolean open=ImGui.collapsingHeader(label,flags);tip(switch(label){
        case "Appearance"->"Size, color, transparency and rotation. Curves animate individual particles over their lifetimes.";
        case "Material"->"Texture, lighting, blend mode and world bloom. World preview shows the actual material compositor.";
        case "Transform"->"Layer offsets, parent rotation/scale, seconds animation, simulation space and the effect's local pivot.";
        case "Emission"->"Birth rate, lifetime, delayed start, emission duration and one-shot burst events.";
        case "Motion"->"Velocity, direction, spread, acceleration, drag and deterministic wander.";
        case "Shape / mesh"->"Choose the visual and spawn shape, then adjust geometry extent and mesh pixelization.";
        case "Collision"->"Enable block collision and tune how much velocity is retained after a bounce.";
        case "Sprite animation"->"Play equal-sized cells from a PNG sprite sheet, left to right and top to bottom.";
        case "Project"->"Project metadata, emission cutoff or full-length playback, looping and deterministic seed.";
        default->"Expand or collapse these editing controls.";});return open;}
    static String description(String key,boolean root){return switch(key){
        case "size"->"Sprite height or ribbon width, in blocks. Aspect multiplies sprite width. Curves animate size over particle life.";
        case "color"->"RGB and opacity channels display 0 to 255; JSON stores 0 to 1. Edit a gradient to change color and opacity over particle life.";
        case "opacity"->"Multiply every color/gradient alpha by this factor: 0 hides the layer, 1 preserves its authored alpha.";
        case "rate"->"New particles per second. Zero disables continuous births; burst events can still emit.";
        case "lifetime"->"How many seconds each particle survives. A random range is sampled separately for each birth.";
        case "start"->"Delay in seconds from the beginning of the effect cycle before this layer emits.";
        case "duration"->root?"Manual emission cutoff, in seconds. Existing particles finish their lifetimes. Full effect length derives a complete cycle instead.":"How many seconds this layer emits after its start delay. Manual project duration can cut emission short.";
        case "speed"->"Initial velocity in blocks per second. Negative values reverse the configured direction. Curves scale velocity over particle life.";
        case "direction"->"Forward vector [X, Y, Z], normalized during playback. Also controls beam direction and mesh orientation.";
        case "force"->"Emitter-local acceleration [X, Y, Z], in blocks per second squared. Parent transforms rotate/scale it; shared Wind stays on world axes.";
        case "offset"->"Translation [X, Y, Z] in blocks relative to the parent layer. Root layers are relative to the playback pivot.";
        case "pivot"->"Local point placed at the playback anchor, in blocks. Subtracted once after parent transforms. World left-click placement moves the anchor.";
        case "space"->"Local particles follow a moving playback anchor. World particles retain their spawn positions when the anchor moves.";
        case "parent"->"Parent layer identifier. Parent translation, rotation and scale are inherited even if that layer is muted. Leave empty for a root layer.";
        case "rotation"->"Particle/mesh angle in degrees. A curve changes the angle over normalized particle life.";
        case "spin"->"Additional particle/mesh rotation, in degrees per second. Negative values reverse the spin.";
        case "aspect"->"Particle sprite width divided by height: 1 is square, 2 is twice as wide, 0.5 is half as wide.";
        case "kind"->"Choose a billboard particle, beam, trail or generated mesh. Mesh-only and sprite-only controls depend on this selection.";
        case "shape"->"Where particles spawn: point, sphere volume, ring, spiral, box or approximate cone. This does not change the particle's visual kind.";
        case "radius"->"Spawn-shape radius or procedural mesh extent, in blocks. For lightning this is the bolt length.";
        case "length"->"Beam length or spiral height, in blocks.";
        case "spread"->"Directional scatter from 0 (focused) to 1 (wide). Also affects cone and spiral spawning.";
        case "drag"->"Velocity damping per second. Zero preserves velocity; larger values slow particles faster.";
        case "noise"->"Deterministic wander acceleration. Zero gives a clean path; larger values add motion variation.";
        case "curl"->"Strength of a coherent, divergence-free analytic curl field. Unlike per-particle wander, nearby particles move through the same evolving flow.";
        case "curlFrequency"->"Spatial frequency of the curl field. Small values create broad swirls; larger values create fine turbulence.";
        case "attractor"->"Target/field center [X,Y,Z] in blocks relative to this emitter. Used by attraction and vortex fields.";
        case "attraction"->"Acceleration toward the target. Negative values repel; zero disables attraction.";
        case "falloff"->"Attraction weakens by (1 + distance) raised to this exponent. Zero produces constant acceleration.";
        case "vortexAxis"->"Normalized spin axis for the vortex. Use a nonzero vector when vortex spin or pull is enabled.";
        case "vortex"->"Tangential vortex acceleration around the field center. Negative values reverse rotation.";
        case "vortexPull"->"Radial acceleration toward the vortex axis. Negative values push particles outward.";
        case "orbitRadius"->"Elliptical orbit displacement: X is cosine radius; Y and Z are sine radii, defining the orbit plane.";
        case "orbitSpeed"->"Orbit angular speed in degrees per second. Negative values reverse motion.";
        case "wind"->"Shared acceleration [X,Y,Z] in blocks per second squared, added to every layer's forces.";
        case "pathCycles"->"Path traversals per particle lifetime. A non-looping path holds its end after one traversal.";
        case "pathScatter"->"Maximum random positional offset from the path, in blocks. Sampled once per particle birth.";
        case "pathLoop"->"Connect the last path point back to the first with a smooth closed spline.";
        case "alignVelocity"->"Align a particle sprite's long axis with its actual projected motion, including spline/orbit displacement.";
        case "stretch"->"Add width from movement speed. The resulting stretch multiplier is bounded to 1..16 for stable geometry budgets.";
        case "collision"->"Test particles against world blocks. Use World preview to inspect collision against actual terrain.";
        case "bounce"->"Velocity retained after a collision: 0 stops the particle, 1 preserves its speed.";
        case "pixelSize"->"Mesh pixel scale: 1 is smooth, 2 to 8 makes chunkier pixels. Visible in world playback, not the display compositor.";
        case "meshScale"->"Multiply procedural mesh extent. Use a curve for growing shockwaves or shrinking cores; spawn positions remain unchanged.";
        case "trailPoints"->"Number of recent positions retained for each trail. Higher counts lengthen trails and increase geometry use.";
        case "texture"->"PNG resource identifier, such as xirc_vfx_studio:textures/effect/soft.png. External textures need a resource pack or imported package.";
        case "glow"->"Full-bright base material. This does not light terrain; use Bloom for an additional world glow halo.";
        case "bloom"->"World bloom intensity. Zero disables the halo. Use World preview to see the actual post-processing result.";
        case "blend"->"Alpha suits smoke and silhouettes. Additive makes overlapping luminous layers brighter.";
        case "fade"->"Final fraction of particle life spent fading out: 0 disables this fade, 0.5 fades during the last half.";
        case "bursts"->"Timed one-shot birth events, in seconds after this layer starts. Counts and timing are validated before applying.";
        case "autoDuration"->"Include every layer's emission and maximum particle lifetime. Looping waits for the full tail before replaying. The manual duration is preserved.";
        case "loop"->"Repeat the effect cycle. Full-length mode waits for particle tails; manual mode may overlap particles from consecutive cycles.";
        case "seed"->"Deterministic signed 32-bit random seed. The same definition and seed reproduce the same sampled values.";
        case "name"->"Human-readable project name. This does not change the saved filename or resource identifier.";
        case "author"->"Author credit saved with the effect.";
        case "description"->"Notes saved with the effect, such as intended timing or resource-pack requirements.";
        default->"Drag to edit, or Ctrl+click a numeric field to type an exact value.";};}
    static String action(String label){return switch(label){
        case "Select all"->"Select every layer for batch editing. Ctrl-click individual layers to adjust the selection.";
        case "Isolate / restore"->"Temporarily solo selected layers, then restore the previous Mute/Solo combination.";
        case "Apply relative edit"->"Apply value * Multiply + Add to selected scalar values, random ranges and curve keys. Validation and undo cover the entire selection.";
        case "Move selection"->"Translate selected hierarchy roots in blocks. Selected descendants inherit movement once.";
        case "Assign folder"->"Group selected layers in a collapsible folder. Existing parent hierarchy remains intact.";
        case "Duplicate selected"->"Copy selected layers and remap parent links between their copies.";
        case "Delete selected"->"Delete selected layers atomically. Invalid remaining parent or parameter references reject the edit.";
        case "Generate variation"->"Apply seeded variation only to checked properties on selected layers. Invalid results leave the project unchanged.";
        case "Copy track","Paste track"->"Copy or replace a complete animation property, preserving keys and interpolation.";
        case "Select all keys","Retime selected keys"->"Select keys, then shift/stretch their lifetime times without reordering or crossing neighbours.";
        case "Interpolation..."->"Edit interpolation to the next key: linear, step, smooth or Bezier with draggable handles.";
        case "Mark current second"->"Save a named beat at the current effect-clock second. Markers do not execute runtime events.";
        case "Expose on selected layers"->"Create one named numeric control bound to scalar fields on selected layers. Ranges and curves require separate authoring.";
        case "Previous frame","Next frame"->"Pause and move one fixed simulation frame (0.05 seconds).";
        case "Capture A","Capture B","Preview A","Preview B"->"Capture or restore an editor-session project snapshot. Restoring is validated and undoable.";
        case "Save copied track"->"Save the copied animation property as a bounded reusable clip, under Studio clips/.";
        case "Save checkpoint"->"Save a validated recovery copy separately from authored effect files.";
        case "Save camera bookmark"->"Remember this display camera angle, zoom and pan for the current editor session.";
        case "Silhouette","Size heatmap","Normal"->"Change display diagnostic coloring. Authored colors and real world materials are unchanged.";
        case "Done"->"Close these controls; the property draft is committed only when you Apply it.";
        case "Play","Pause"->"Resume or freeze the editor preview. Space also toggles playback when a text field is not focused.";
        case "Restart"->"Restart the editor simulation from the beginning while keeping the current layer audition settings.";
        case "Fit"->"Frame current particles, or authored bounds before emission. This only changes the display camera.";
        case "World"->"Preview with the real world renderer. Left click a block to place the pivot; drag to look around.";
        case "Beside player"->"Restore the moving preview next to your player instead of the fixed, clicked world position.";
        case "Duplicate"->"Duplicate the selected layer with a unique identifier. Undo restores the previous project.";
        case "Delete"->"Remove the selected layer. Invalid parent references are rejected. Undo restores a successful deletion.";
        case "Add layer"->"Add an editable sparks emitter to the current composition.";
        case "Show all layers"->"Clear temporary Mute and Solo settings without changing saved effect JSON.";
        case "JSON"->"Open the complete authored definition. Unapplied JSON drafts are retained when switching views.";
        case "Keyframes"->"Open the timeline to add, retime and edit lifetime keys, sequence layers, or scrub the preview.";
        case "Apply JSON"->"Validate the entire draft before replacing the project. Ctrl+Enter also applies; scene undo restores the previous definition.";
        case "Reload project"->"Replace the JSON draft with the current project, discarding unapplied draft text.";
        case "Edit curve...","Curve / range...","Edit value"->"Open a validated draft for a constant, random range or lifetime curve. Other authored properties are preserved.";
        case "Gradient...","Edit gradient"->"Edit color and opacity keys over particle life. Apply commits only this property.";
        case "Edit events"->"Edit timed burst counts. Times are seconds after the layer starts.";
        case "Add key at cursor"->"Insert a key at the lifetime cursor, sampling the existing curve or gradient. At most 64 keys per track.";
        case "Add key"->"Add a key in the largest gap, interpolating its initial value or color.";
        case "Add event"->"Add a birth event. Set its time and particle count before applying.";
        case "Delete key","Remove selected"->"Remove the selected key/event. Curves and gradients retain at least two keys.";
        case "Apply"->"Validate and commit this property draft as one undoable edit.";
        case "Cancel"->"Close this property draft without applying it.";
        case "Animated sprite sheet"->"Enable a row-major PNG atlas. Match the columns, rows and frame count to the texture.";
        case "Loop frames"->"Repeat the sprite sheet. When off, animation holds the final frame.";
        case "Create project"->"Start a fresh neutral emitter. No files are overwritten; scene undo restores the previous project.";
        case "Save effect"->"Save the validated project under the entered filename.";
        case "Open Photon .fx file...","Browse .fx..."->"Choose a user-supplied Photon file for bounded conversion and limitation review.";
        case "Review file"->"Convert the selected file and show approximation diagnostics before opening it.";
        case "Open imports folder"->"Open the directory where Photon reference files can be selected for conversion.";
        case "Use bundled sprites for external textures"->"Substitute bundled sprites for missing external textures. This changes fidelity and can omit atlas animation.";
        case "Choose another file"->"Return to choosing a Photon file without changing the current project.";
        case "Copy conversion report"->"Copy the import's semantic differences to the clipboard.";
        case "Open approximation","Open converted effect"->"Open the validated conversion as editable Studio JSON. The source Photon file is untouched.";
        case "Close"->"Close this dialog.";
        default->"Activate this control. Numeric controls also accept Ctrl+click for precise typing.";};}
}
