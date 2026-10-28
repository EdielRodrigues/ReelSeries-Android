package com.reelseries.android;
import android.app.*;import android.os.*;import android.view.*;import android.widget.*;import android.media.*;import android.net.*;
public class PlayerActivity extends Activity {
 VideoView video; int position=0;
 @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setFlags(1024,1024);getWindow().setNavigationBarColor(0xff000000);
 android.widget.FrameLayout frame=new android.widget.FrameLayout(this);frame.setBackgroundColor(0xff000000);video=new VideoView(this);frame.addView(video,new android.widget.FrameLayout.LayoutParams(-1,-1));setContentView(frame);
 String url=getIntent().getStringExtra("url");if(url==null||!url.startsWith("http")){finish();return;}
 MediaController controls=new MediaController(this);controls.setAnchorView(video);video.setMediaController(controls);
 video.setOnErrorListener((mp,what,extra)->{new AlertDialog.Builder(this).setTitle("Vídeo indisponível").setMessage("Este link ou formato não é compatível com o player Android. Alguns provedores exigem autenticação ou outro formato.").setPositiveButton("Voltar",(d,w)->finish()).show();return true;});
 video.setVideoURI(Uri.parse(url));video.setOnPreparedListener(mp->{video.seekTo(position);video.start();});}
 @Override protected void onPause(){position=video.getCurrentPosition();video.pause();super.onPause();}
 @Override protected void onResume(){super.onResume();if(position>0)video.seekTo(position);}
}
