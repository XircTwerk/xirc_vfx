package com.xirctwerk.vfx.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TextHistoryTest {
    @Test void largePasteCanUndoAndRedoWithoutNativeTextBufferLimits(){var h=new TextHistory();String json="{\"draft\":\""+"x".repeat(20000)+"\"}";h.reset(json);h.record("",false);h.record(json,false);assertEquals("",h.undo());assertEquals(json,h.redo());assertEquals("",h.undo());assertEquals(json,h.undo());}
    @Test void typingCoalescesAndNewEditsInvalidateRedo(){var h=new TextHistory();h.reset("{}");h.record("{",false);h.record("{x",true);h.record("{xy",true);assertEquals("{}",h.undo());assertEquals("{xy",h.redo());h.undo();h.record("invalid JSON",false);assertEquals("invalid JSON",h.redo());}
    @Test void historyIsBoundedAndResetDropsPreviousProjectText(){var h=new TextHistory();for(int i=0;i<200;i++)h.record(Integer.toString(i),false);for(int i=0;i<100;i++)h.undo();assertEquals("149",h.current());h.reset("new project");assertEquals("new project",h.undo());}
}
