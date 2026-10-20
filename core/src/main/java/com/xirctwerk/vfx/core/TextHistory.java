package com.xirctwerk.vfx.core;

import java.util.ArrayDeque;
import java.util.Deque;

/** Bounded JSON draft history, including large paste/cut operations and invalid drafts. */
public final class TextHistory {
    private String current="";
    private final Deque<String> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();
    public String current(){return current;}
    public void reset(String text){current=text;undo.clear();redo.clear();}
    public void record(String next,boolean merge){if(next.equals(current))return;if(!merge||undo.isEmpty())undo.push(current);current=next;redo.clear();trim(undo);}
    public String undo(){if(!undo.isEmpty()){redo.push(current);current=undo.pop();trim(redo);}return current;}
    public String redo(){if(!redo.isEmpty()){undo.push(current);current=redo.pop();trim(undo);}return current;}
    private static void trim(Deque<String> history){long chars=history.stream().mapToLong(String::length).sum();while(history.size()>50||chars>4L*EffectCodec.MAX_JSON_BYTES){chars-=history.removeLast().length();}}
}
