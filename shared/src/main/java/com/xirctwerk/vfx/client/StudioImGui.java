package com.xirctwerk.vfx.client;

import imgui.ImGui;
import imgui.ImFontConfig;
import imgui.flag.*;
import imgui.glfw.ImGuiImplGlfw;
import imgui.gl3.ImGuiImplGl3;
import imgui.internal.ImGuiContext;
import net.minecraft.client.Minecraft;
import java.io.IOException;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import org.lwjgl.glfw.GLFW;

/** Own context and callback forwarding: never installs callbacks over Minecraft's callbacks. */
final class StudioImGui {
    private static StudioImGui instance;
    private final ImGuiContext context;
    private final ImGuiImplGlfw glfw = new ImGuiImplGlfw();
    private final ImGuiImplGl3 gl = new ImGuiImplGl3();
    private final long window;
    private static final int MAX_LAYOUT_BYTES=262_144;
    private float testX, testY;
    private boolean testPointer, testDown;
    private boolean focused;

    private StudioImGui() {
        var previous=ImGui.getCurrentContext();
        context=ImGui.createContext();
        window=Minecraft.getInstance().getWindow().getWindow();
        focused=GLFW.glfwGetWindowAttrib(window,GLFW.GLFW_FOCUSED)!=0;
        try {
            var io=ImGui.getIO();
            io.addConfigFlags(ImGuiConfigFlags.DockingEnable);
            // Bound local preference reads ourselves instead of allowing unbounded native file IO.
            io.setIniFilename(null);
            loadLayout();
            try(var stream=StudioImGui.class.getResourceAsStream("/assets/xirc_vfx_studio/fonts/Inter.ttf")) {
                if(stream==null) throw new IOException("Missing Inter font");
                var config=new ImFontConfig();config.setOversampleH(2);config.setOversampleV(2);
                io.setFontDefault(io.getFonts().addFontFromMemoryTTF(stream.readAllBytes(),17,config));config.destroy();
            }
            ImGui.styleColorsDark();
            var s=ImGui.getStyle();
            s.setWindowPadding(12,12);s.setFramePadding(7,5);s.setItemSpacing(8,7);
            s.setWindowRounding(6);s.setFrameRounding(4);s.setGrabRounding(4);s.setPopupRounding(6);
            s.setWindowBorderSize(1);s.setFrameBorderSize(0);s.setScrollbarSize(12);
            s.setColor(ImGuiCol.WindowBg,.075f,.084f,.105f,1);
            s.setColor(ImGuiCol.ChildBg,.075f,.084f,.105f,1);
            s.setColor(ImGuiCol.PopupBg,.075f,.084f,.105f,1);
            s.setColor(ImGuiCol.Border,.18f,.20f,.25f,1);
            s.setColor(ImGuiCol.FrameBg,.13f,.15f,.19f,1);
            s.setColor(ImGuiCol.Button,.18f,.22f,.31f,1);
            s.setColor(ImGuiCol.ButtonHovered,.28f,.36f,.52f,1);
            s.setColor(ImGuiCol.Header,.18f,.22f,.31f,1);
            s.setColor(ImGuiCol.CheckMark,.55f,.66f,1,1);
            s.setColor(ImGuiCol.SliderGrab,.55f,.66f,1,1);
            s.setColor(ImGuiCol.TabActive,.24f,.29f,.43f,1);
            if(!glfw.init(window,false)) throw new IllegalStateException("ImGui GLFW initialization failed");
            gl.init("#version 150");
        } catch(IOException ex) {throw new IllegalStateException("Cannot initialize Studio font",ex);}
        finally {ImGui.setCurrentContext(previous);}
    }
    static StudioImGui get() {if(instance==null) instance=new StudioImGui();return instance;}
    void withContext(Runnable action) {var previous=ImGui.getCurrentContext();ImGui.setCurrentContext(context);try {action.run();}finally {ImGui.setCurrentContext(previous);}}
    void frame(Runnable draw) {withContext(()->{
        boolean nowFocused=GLFW.glfwGetWindowAttrib(window,GLFW.GLFW_FOCUSED)!=0;
        if(nowFocused!=focused) {glfw.windowFocusCallback(window,nowFocused);focused=nowFocused;}
        gl.newFrame();glfw.newFrame();
        if(testPointer) {ImGui.getIO().addMousePosEvent(testX,testY);ImGui.getIO().addMouseButtonEvent(0,testDown);}
        ImGui.newFrame();draw.run();ImGui.render();gl.renderDrawData(ImGui.getDrawData());
    });}
    void mouse(int button,boolean down) {withContext(()->glfw.mouseButtonCallback(window,button,down?1:0,0));}
    void wheel(double x,double y) {withContext(()->glfw.scrollCallback(window,x,y));}
    void key(int key,int scan,int action,int mods) {withContext(()->{
        var io=ImGui.getIO();boolean ctrl=(mods&GLFW.GLFW_MOD_CONTROL)!=0;
        int mapped=key>=GLFW.GLFW_KEY_A&&key<=GLFW.GLFW_KEY_Z?ImGuiKey.A+key-GLFW.GLFW_KEY_A:switch(key){
            case GLFW.GLFW_KEY_LEFT->ImGuiKey.LeftArrow;case GLFW.GLFW_KEY_RIGHT->ImGuiKey.RightArrow;
            case GLFW.GLFW_KEY_UP->ImGuiKey.UpArrow;case GLFW.GLFW_KEY_DOWN->ImGuiKey.DownArrow;
            case GLFW.GLFW_KEY_HOME->ImGuiKey.Home;case GLFW.GLFW_KEY_END->ImGuiKey.End;
            case GLFW.GLFW_KEY_BACKSPACE->ImGuiKey.Backspace;case GLFW.GLFW_KEY_DELETE->ImGuiKey.Delete;
            case GLFW.GLFW_KEY_ENTER->ImGuiKey.Enter;case GLFW.GLFW_KEY_TAB->ImGuiKey.Tab;default->ImGuiKey.None;};
        // GLFW's backend polls native modifier state. Screen-delivered chords already carry
        // authoritative modifier flags; enqueue those before the key, without polling them away.
        if(!ctrl||mapped==ImGuiKey.None)glfw.keyCallback(window,key,scan,action,mods);
        io.addKeyEvent(ImGuiKey.ModCtrl,ctrl);
        io.addKeyEvent(ImGuiKey.ModShift,(mods&GLFW.GLFW_MOD_SHIFT)!=0);
        io.addKeyEvent(ImGuiKey.ModAlt,(mods&GLFW.GLFW_MOD_ALT)!=0);
        io.addKeyEvent(ImGuiKey.ModSuper,(mods&GLFW.GLFW_MOD_SUPER)!=0);
        if(ctrl&&mapped!=ImGuiKey.None&&action!=GLFW.GLFW_REPEAT){io.addKeyEvent(mapped,action!=GLFW.GLFW_RELEASE);io.setKeyEventNativeData(mapped,key,scan);}
    });}
    void character(char c) {withContext(()->glfw.charCallback(window,c));}
    boolean textInput() {boolean[] result={false};withContext(()->result[0]=ImGui.getIO().getWantTextInput());return result[0];}
    private Path layoutPath() throws IOException {
        Path root=VfxClient.INSTANCE.files.root().toRealPath();
        Path path=root.resolve("studio-layout.ini");
        if(Files.isSymbolicLink(path)) throw new IOException("Studio layout must not be a symbolic link.");
        return path;
    }
    private void loadLayout() {
        try {
            Path path=layoutPath();if(!Files.exists(path)) return;
            try(var input=Files.newInputStream(path)) {
                byte[] bytes=input.readNBytes(MAX_LAYOUT_BYTES+1);
                if(bytes.length>MAX_LAYOUT_BYTES) throw new IOException("Studio layout exceeds 256 KiB. Reset its layout file.");
                ImGui.loadIniSettingsFromMemory(new String(bytes,StandardCharsets.UTF_8));
            }
        } catch(IOException ex) {org.slf4j.LoggerFactory.getLogger("XircStudio").warn("Using default editor layout: {}",ex.getMessage());}
    }
    private void saveLayout() {
        Path temporary=null;
        try {
            Path path=layoutPath();byte[] bytes=ImGui.saveIniSettingsToMemory().getBytes(StandardCharsets.UTF_8);
            if(bytes.length>MAX_LAYOUT_BYTES) throw new IOException("Studio layout exceeds 256 KiB.");
            temporary=Files.createTempFile(path.getParent(),"studio-layout-",".tmp");Files.write(temporary,bytes);
            try {Files.move(temporary,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException ex) {Files.move(temporary,path,StandardCopyOption.REPLACE_EXISTING);}
        } catch(IOException ex) {org.slf4j.LoggerFactory.getLogger("XircStudio").warn("Could not save editor layout: {}",ex.getMessage());}
        finally {if(temporary!=null) try {Files.deleteIfExists(temporary);}catch(IOException ignored) {}}
    }
    void release() {withContext(()->{ImGui.getIO().clearInputKeys();ImGui.getIO().clearInputMouse();saveLayout();});testPointer=false;}
    void testPointer(float x,float y,boolean down) {if(!Boolean.getBoolean("xirc.smokeTest")) throw new IllegalStateException("Test input is disabled");testX=x;testY=y;testDown=down;testPointer=true;}
}
