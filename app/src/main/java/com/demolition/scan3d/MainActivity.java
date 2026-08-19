package com.demolition.scan3d;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.Image;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Surface;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.CameraIntrinsics;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.NotYetAvailableException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class MainActivity extends AppCompatActivity implements GLSurfaceView.Renderer {
  private GLSurfaceView gl;
  private Session session;
  private boolean installRequested = false;
  private final BackgroundRenderer bg = new BackgroundRenderer();
  private final PointRenderer points = new PointRenderer();
  private TextView status;
  private int width = 1;
  private int height = 1;
  private final java.util.ArrayList<float[]> accumulatedPoints = new java.util.ArrayList<>();
  private static final int MAX_ACCUMULATED_POINTS = 200000;
  private final java.util.HashSet<String> pointKeys = new java.util.HashSet<>();
  private long lastDepthTs = -1;
  private final float[] proj = new float[16];
  private final float[] view = new float[16];

  @Override protected void onCreate(Bundle b) {
    super.onCreate(b);
    FrameLayout root = new FrameLayout(this);
    gl = new GLSurfaceView(this);
    gl.setEGLContextClientVersion(2);
    gl.setPreserveEGLContextOnPause(true);
    gl.setRenderer(this);
    gl.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    root.addView(gl);
    status = new TextView(this);
    status.setText("3D-01 · 카메라를 천천히 움직이세요");
    status.setTextSize(16);
    status.setPadding(28,28,28,28);
    status.setBackgroundColor(0x88000000);
    status.setTextColor(0xffffffff);
    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.WRAP_CONTENT,Gravity.TOP);
    root.addView(status,lp);
    setContentView(root);
  }

  @Override protected void onResume() {
    super.onResume();
    if (session == null) {
      try {
        if (ArCoreApk.getInstance().requestInstall(this,!installRequested) == ArCoreApk.InstallStatus.INSTALL_REQUESTED) { installRequested=true; return; }
        if (ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
          ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.CAMERA},7); return;
        }
        session = new Session(this);
        if (!session.isDepthModeSupported(Config.DepthMode.RAW_DEPTH_ONLY)) {
          status.setText("이 기기/카메라는 Raw Depth를 지원하지 않습니다");
          session.close(); session=null; return;
        }
        Config config=session.getConfig();
        config.setDepthMode(Config.DepthMode.RAW_DEPTH_ONLY);
        config.setFocusMode(Config.FocusMode.AUTO);
        session.configure(config);
      } catch (Exception e) { status.setText("ARCore 시작 오류: "+e.getClass().getSimpleName()); return; }
    }
    try { session.resume(); } catch (CameraNotAvailableException e) { status.setText("카메라 사용 불가"); return; }
    gl.onResume();
  }

  @Override protected void onPause(){ super.onPause(); gl.onPause(); if(session!=null)session.pause(); }
  @Override protected void onDestroy(){ if(session!=null)session.close(); super.onDestroy(); }

  @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){
    super.onRequestPermissionsResult(requestCode,permissions,grantResults);
    if(requestCode==7 && grantResults.length>0 && grantResults[0]==PackageManager.PERMISSION_GRANTED) recreate();
  }

  @Override public void onSurfaceCreated(GL10 gl10,EGLConfig config){ GLES20.glClearColor(0f,0f,0f,1f); bg.create(); points.create(); }
  @Override public void onSurfaceChanged(GL10 gl10,int w,int h){ width=w; height=h; GLES20.glViewport(0,0,w,h); }

  @Override public void onDrawFrame(GL10 gl10){
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT);
    if(session==null)return;
    try {
      int rotation=getWindowManager().getDefaultDisplay().getRotation();
      int arRotation;
      switch(rotation){
        case Surface.ROTATION_90: arRotation=Surface.ROTATION_90; break;
        case Surface.ROTATION_180: arRotation=Surface.ROTATION_180; break;
        case Surface.ROTATION_270: arRotation=Surface.ROTATION_270; break;
        case Surface.ROTATION_0:
        default: arRotation=Surface.ROTATION_0; break;
      }

      // ARCore owns display rotation/cropping. No extra 180-degree compensation.
      session.setDisplayGeometry(arRotation,width,height);
      session.setCameraTextureName(bg.textureId());
      Frame frame=session.update();
      Camera cam=frame.getCamera();
      bg.draw(frame);

      if(cam.getTrackingState()!=TrackingState.TRACKING){
        runOnUiThread(()->status.setText("추적 중… 휴대폰을 천천히 움직이세요")); return;
      }

      try(Image depth=frame.acquireRawDepthImage16Bits(); Image confidence=frame.acquireRawDepthConfidenceImage()){
        if(depth.getTimestamp()!=lastDepthTs){
          lastDepthTs=depth.getTimestamp();
          FloatBuffer xyz=makePoints(cam,depth,confidence);
          FloatBuffer accumulated=accumulatePoints(xyz);
          points.update(accumulated);
          final int n=accumulated.limit()/3;
          runOnUiThread(()->status.setText("3D-01 · Raw Depth 정상 · 포인트 "+n+"개"));
        }
      } catch(NotYetAvailableException e){ runOnUiThread(()->status.setText("Depth 준비 중… 주변을 천천히 비춰주세요")); }

      cam.getProjectionMatrix(proj,0,0.1f,20f);
      cam.getViewMatrix(view,0);
      points.draw(view,proj);
    } catch(Throwable t){ runOnUiThread(()->status.setText("오류: "+t.getClass().getSimpleName())); }
  }

  private FloatBuffer makePoints(Camera cam,Image depth,Image confidence){
    int w=depth.getWidth(), h=depth.getHeight(), step=1;
    Image.Plane depthPlane=depth.getPlanes()[0], confPlane=confidence.getPlanes()[0];
    ByteBuffer db=depthPlane.getBuffer().order(ByteOrder.LITTLE_ENDIAN), cb=confPlane.getBuffer();
    int dr=depthPlane.getRowStride(), dp=depthPlane.getPixelStride(), cr=confPlane.getRowStride(), cp=confPlane.getPixelStride();

    // Raw Depth is produced at the GPU/texture aspect ratio. ARCore's Raw Depth
    // reference implementation therefore unprojects it with texture intrinsics,
    // scaled to the actual depth image resolution.
    CameraIntrinsics intr=cam.getTextureIntrinsics();
    float[] focal=intr.getFocalLength(), principal=intr.getPrincipalPoint();
    int[] dim=intr.getImageDimensions();
    float sx=(float)w/dim[0], sy=(float)h/dim[1];
    float fx=focal[0]*sx, fy=focal[1]*sy, cx=principal[0]*sx, cy=principal[1]*sy;
    FloatBuffer out=ByteBuffer.allocateDirect((w/step+1)*(h/step+1)*3*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    Pose pose=cam.getPose();
    float[] cameraPoint=new float[3], worldPoint=new float[3];
    for(int y=0;y<h;y+=step){
      for(int x=0;x<w;x+=step){
        int ci=y*cr+x*cp;
        if(ci<0||ci>=cb.limit())continue;
        int confidenceValue=cb.get(ci)&0xff;
        if(confidenceValue<64)continue;
        int di=y*dr+x*dp;
        if(di<0||di+1>=db.limit())continue;
        int mm=db.getShort(di)&0xffff;
        if(mm<250||mm>8000)continue;
        float distance=mm/1000f;
        cameraPoint[0]=(x-cx)*distance/fx;
        cameraPoint[1]=-(y-cy)*distance/fy;
        cameraPoint[2]=-distance;
        pose.transformPoint(cameraPoint,0,worldPoint,0);
        out.put(worldPoint[0]); out.put(worldPoint[1]); out.put(worldPoint[2]);
      }
    }
    out.flip(); return out;
  }

  private FloatBuffer accumulatePoints(FloatBuffer xyz){
    while(xyz.remaining()>=3 && accumulatedPoints.size()<MAX_ACCUMULATED_POINTS){
      float x=xyz.get(), y=xyz.get(), z=xyz.get();
      int qx=Math.round(x*100), qy=Math.round(y*100), qz=Math.round(z*100);
      String key=qx+"_"+qy+"_"+qz;
      if(pointKeys.add(key))accumulatedPoints.add(new float[]{x,y,z});
    }
    FloatBuffer result=ByteBuffer.allocateDirect(accumulatedPoints.size()*3*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    for(float[] p:accumulatedPoints){ result.put(p[0]); result.put(p[1]); result.put(p[2]); }
    result.flip(); return result;
  }
}
