package com.xirctwerk.vfx.api.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL20;
import java.util.*;

/** Trusted Java-code shader creation. Never pass imported/user document source to this API. */
public final class ShaderProgram implements AutoCloseable {
    public enum Type { FLOAT, INT, VEC2, VEC3, VEC4, MAT3, MAT4, SAMPLER }
    private int program;private final Map<String,Type> types;
    public ShaderProgram(String vertex,String fragment,Map<String,Type> uniforms){RenderSystem.assertOnRenderThread();types=Map.copyOf(uniforms);int v=compile(GL20.GL_VERTEX_SHADER,vertex),f=0,p=0;try{f=compile(GL20.GL_FRAGMENT_SHADER,fragment);p=GL20.glCreateProgram();GL20.glAttachShader(p,v);GL20.glAttachShader(p,f);GL20.glLinkProgram(p);if(GL20.glGetProgrami(p,GL20.GL_LINK_STATUS)==0)throw new IllegalArgumentException("Shader link failed: "+GL20.glGetProgramInfoLog(p));for(int i=0;i<GL20.glGetProgrami(p,GL20.GL_ACTIVE_UNIFORMS);i++){var size=org.lwjgl.BufferUtils.createIntBuffer(1);var type=org.lwjgl.BufferUtils.createIntBuffer(1);String name=GL20.glGetActiveUniform(p,i,size,type);Type declared=types.get(name);if(declared==null)throw new IllegalArgumentException("Declare active uniform: "+name);int expected=switch(declared){case FLOAT->GL20.GL_FLOAT;case INT->GL20.GL_INT;case VEC3->GL20.GL_FLOAT_VEC3;case MAT3->GL20.GL_FLOAT_MAT3;case MAT4->GL20.GL_FLOAT_MAT4;case VEC2->GL20.GL_FLOAT_VEC2;case VEC4->GL20.GL_FLOAT_VEC4;case SAMPLER->GL20.GL_SAMPLER_2D;};if(size.get(0)!=1||type.get(0)!=expected)throw new IllegalArgumentException("Shader uniform type mismatch: "+name);}program=p;}catch(RuntimeException e){if(p!=0)GL20.glDeleteProgram(p);throw e;}finally{GL20.glDeleteShader(v);if(f!=0)GL20.glDeleteShader(f);}}
    private static int compile(int type,String source){if(source==null||source.length()>131072)throw new IllegalArgumentException("Shader source exceeds 128 KiB.");int id=GL20.glCreateShader(type);GL20.glShaderSource(id,source);GL20.glCompileShader(id);if(GL20.glGetShaderi(id,GL20.GL_COMPILE_STATUS)==0){String error=GL20.glGetShaderInfoLog(id);GL20.glDeleteShader(id);throw new IllegalArgumentException("Shader compile failed: "+error);}return id;}
    public void bind(){RenderSystem.assertOnRenderThread();if(program==0)throw new IllegalStateException("Shader is closed.");GlStateManager._glUseProgram(program);}
    private int location(String name,Type type){if(types.get(name)!=type)throw new IllegalArgumentException("Uniform "+name+" requires "+types.get(name));if(GL20.glGetInteger(GL20.GL_CURRENT_PROGRAM)!=program)throw new IllegalStateException("Bind this shader before setting uniforms.");return GL20.glGetUniformLocation(program,name);}
    public void scalar(String name,float value){if(!Float.isFinite(value))throw new IllegalArgumentException("Uniform must be finite");GL20.glUniform1f(location(name,Type.FLOAT),value);}
    public void integer(String name,int value){GL20.glUniform1i(location(name,Type.INT),value);}
    public void vector3(String name,float x,float y,float z){for(float v:new float[]{x,y,z})if(!Float.isFinite(v))throw new IllegalArgumentException("Uniform must be finite");GL20.glUniform3f(location(name,Type.VEC3),x,y,z);}
    public void matrix3(String name,float[] values){matrix(values,9);GL20.glUniformMatrix3fv(location(name,Type.MAT3),false,values);}
    public void matrix4(String name,float[] values){matrix(values,16);GL20.glUniformMatrix4fv(location(name,Type.MAT4),false,values);}
    private static void matrix(float[] values,int size){if(values==null||values.length!=size)throw new IllegalArgumentException("Use a column-major matrix with "+size+" values");for(float value:values)if(!Float.isFinite(value))throw new IllegalArgumentException("Matrix must be finite");}
    public void vector2(String name,float x,float y){if(!Float.isFinite(x)||!Float.isFinite(y))throw new IllegalArgumentException("Uniform must be finite");GL20.glUniform2f(location(name,Type.VEC2),x,y);}
    public void vector4(String name,float x,float y,float z,float w){for(float v:new float[]{x,y,z,w})if(!Float.isFinite(v))throw new IllegalArgumentException("Uniform must be finite");GL20.glUniform4f(location(name,Type.VEC4),x,y,z,w);}
    public void sampler(String name,int unit){if(unit<0||unit>1)throw new IllegalArgumentException("Use texture unit 0 or 1");GL20.glUniform1i(location(name,Type.SAMPLER),unit);}
    @Override public void close(){RenderSystem.assertOnRenderThread();if(program!=0){GL20.glDeleteProgram(program);program=0;}}
}
