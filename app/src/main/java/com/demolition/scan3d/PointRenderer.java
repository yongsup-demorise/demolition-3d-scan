package com.demolition.scan3d;
import android.opengl.*; import java.nio.*;
class PointRenderer{
 private int prog,vbo,count,mvpLoc; private final float[] tmp=new float[16],mvp=new float[16];
 void create(){prog=BackgroundRenderer.link("uniform mat4 m;attribute vec3 p;void main(){gl_Position=m*vec4(p,1.0);gl_PointSize=4.0;}","precision mediump float;void main(){gl_FragColor=vec4(0.1,1.0,0.2,1.0);}");mvpLoc=GLES20.glGetUniformLocation(prog,"m");int[]b=new int[1];GLES20.glGenBuffers(1,b,0);vbo=b[0];}
 void update(FloatBuffer b){count=b.remaining()/3;GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,vbo);GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER,b.remaining()*4,b,GLES20.GL_DYNAMIC_DRAW);GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,0);}
 void draw(float[]view,float[]proj){if(count==0)return;Matrix.multiplyMM(tmp,0,proj,0,view,0);System.arraycopy(tmp,0,mvp,0,16);GLES20.glEnable(GLES20.GL_DEPTH_TEST);GLES20.glUseProgram(prog);GLES20.glUniformMatrix4fv(mvpLoc,1,false,mvp,0);int p=GLES20.glGetAttribLocation(prog,"p");GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,vbo);GLES20.glEnableVertexAttribArray(p);GLES20.glVertexAttribPointer(p,3,GLES20.GL_FLOAT,false,12,0);GLES20.glDrawArrays(GLES20.GL_POINTS,0,count);GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,0);}
}
