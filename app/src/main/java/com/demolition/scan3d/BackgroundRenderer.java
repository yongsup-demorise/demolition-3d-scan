package com.demolition.scan3d;

import android.opengl.GLES11Ext;
import android.opengl.GLES20;

import com.google.ar.core.Frame;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

class BackgroundRenderer {

 private int tex, prog, pos, uv;
 private FloatBuffer vb, tb;

 // ARCore display UV coordinate convention.
 private final float[] uvs = {
         0,0,
         1,0,
         0,1,
         1,1
 };

 int textureId() {
  return tex;
 }

 void create() {

  int[] t = new int[1];
  GLES20.glGenTextures(1, t, 0);
  tex = t[0];

  GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex);
  GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
  GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

  prog = link(
          "attribute vec4 a;" +
                  "attribute vec2 u;" +
                  "varying vec2 v;" +
                  "void main(){gl_Position=a;v=u;}",
          "#extension GL_OES_EGL_image_external : require\n" +
                  "precision mediump float;" +
                  "uniform samplerExternalOES s;" +
                  "varying vec2 v;" +
                  "void main(){gl_FragColor=texture2D(s,v);}" 
  );

  pos = GLES20.glGetAttribLocation(prog, "a");
  uv = GLES20.glGetAttribLocation(prog, "u");

  // Keep ARCore's transformed texture coordinates untouched. Correct only the
  // screen-space left/right vertex assignment. This changes one axis only and
  // preserves the verified vertical orientation from ROT=0 -> AR=2.
  vb = fb(new float[]{
          1,-1,
          -1,-1,
          1,1,
          -1,1
  });

  tb = fb(uvs);
 }

 void draw(Frame f) {

  if (f.hasDisplayGeometryChanged()) {
    FloatBuffer input = fb(uvs);
    FloatBuffer output = ByteBuffer.allocateDirect(8 * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer();

    f.transformDisplayUvCoords(input, output);
    output.rewind();
    tb = output;
  }

  GLES20.glDisable(GLES20.GL_DEPTH_TEST);
  GLES20.glDepthMask(false);
  GLES20.glUseProgram(prog);

  GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
  GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex);

  GLES20.glEnableVertexAttribArray(pos);
  GLES20.glVertexAttribPointer(pos, 2, GLES20.GL_FLOAT, false, 0, vb);

  GLES20.glEnableVertexAttribArray(uv);
  GLES20.glVertexAttribPointer(uv, 2, GLES20.GL_FLOAT, false, 0, tb);

  GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
  GLES20.glDepthMask(true);
 }

 static FloatBuffer fb(float[] a) {
  FloatBuffer b = ByteBuffer.allocateDirect(a.length * 4)
          .order(ByteOrder.nativeOrder())
          .asFloatBuffer();
  b.put(a);
  b.flip();
  return b;
 }

 static int sh(int type, String s) {
  int x = GLES20.glCreateShader(type);
  GLES20.glShaderSource(x, s);
  GLES20.glCompileShader(x);
  return x;
 }

 static int link(String v, String f) {
  int p = GLES20.glCreateProgram();
  GLES20.glAttachShader(p, sh(GLES20.GL_VERTEX_SHADER, v));
  GLES20.glAttachShader(p, sh(GLES20.GL_FRAGMENT_SHADER, f));
  GLES20.glLinkProgram(p);
  return p;
 }
}
