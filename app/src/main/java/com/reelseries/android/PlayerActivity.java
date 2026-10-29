package com.reelseries.android;
import android.app.*;import android.os.*;import android.content.*;import android.content.pm.ActivityInfo;import android.view.*;import android.widget.*;
import androidx.media3.common.*;import androidx.media3.exoplayer.ExoPlayer;import androidx.media3.ui.PlayerView;import androidx.media3.ui.AspectRatioFrameLayout;
import org.json.*;import java.security.MessageDigest;import java.nio.charset.StandardCharsets;
public class PlayerActivity extends Activity {
 private ExoPlayer player;private PlayerView playerView;private long savedPosition=0;private String url;private boolean playWhenReady=true;
 private JSONArray playlist;private int playlistIndex=-1;private boolean restored=false;private boolean episodeFinished=false;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final Runnable periodic=new Runnable(){@Override public void run(){saveProgress();handler.postDelayed(this,5000);}};
 private String progressKey(String link){try{byte[] hash=MessageDigest.getInstance("SHA-256").digest(link.getBytes(StandardCharsets.UTF_8));StringBuilder b=new StringBuilder("p_");for(int i=0;i<12;i++)b.append(String.format(java.util.Locale.US,"%02x",hash[i]&255));return b.toString();}catch(Exception e){return "p_"+link.hashCode();}}
 private long stored(String link){return getSharedPreferences("reel_playback_progress",MODE_PRIVATE).getLong(progressKey(link),0);}
 private void saveProgress(){if(player==null||url==null||episodeFinished)return;long duration=player.getDuration(),pos=player.getCurrentPosition();if(pos<0)return;
  if(duration>0&&pos>=duration-15000){getSharedPreferences("reel_playback_progress",MODE_PRIVATE).edit().remove(progressKey(url)).apply();return;}
  getSharedPreferences("reel_playback_progress",MODE_PRIVATE).edit().putLong(progressKey(url),pos).apply();}
 private void markComplete(){if(url!=null)getSharedPreferences("reel_playback_progress",MODE_PRIVATE).edit().remove(progressKey(url)).apply();}
 @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(0xff000000);getWindow().setNavigationBarColor(0xff000000);getWindow().getDecorView().setSystemUiVisibility(4|1024|4096);setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
 url=getIntent().getStringExtra("url");if(url==null||!url.startsWith("http")){finish();return;}
 try{String raw=getIntent().getStringExtra("playlist");if(raw!=null){playlist=new JSONArray(raw);playlistIndex=getIntent().getIntExtra("playlistIndex",-1);}}catch(Exception ignored){playlist=null;}
 FrameLayout root=new FrameLayout(this);root.setBackgroundColor(0xff000000);playerView=new PlayerView(this);playerView.setUseController(true);playerView.setControllerShowTimeoutMs(4500);playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);root.addView(playerView,new FrameLayout.LayoutParams(-1,-1));setContentView(root);
 if(b!=null){savedPosition=b.getLong("position",0);playWhenReady=b.getBoolean("playing",true);restored=true;playlistIndex=b.getInt("playlistIndex",playlistIndex);url=b.getString("url",url);}
 }
 @Override protected void onStart(){super.onStart();player=new ExoPlayer.Builder(this).build();playerView.setPlayer(player);
 player.addListener(new Player.Listener(){
  @Override public void onPlayerError(PlaybackException e){if(!isFinishing())new AlertDialog.Builder(PlayerActivity.this).setTitle("Não foi possível reproduzir").setMessage("O servidor ou o formato do vídeo pode estar indisponível.\n\n"+e.getErrorCodeName()).setPositiveButton("Voltar",(d,w)->finish()).show();}
  @Override public void onPlaybackStateChanged(int state){if(state==Player.STATE_ENDED){episodeFinished=true;markComplete();playNext();}}
 });
 player.setMediaItem(MediaItem.fromUri(url));player.prepare();long pos=restored?savedPosition:stored(url);player.seekTo(Math.max(0,pos));player.setPlayWhenReady(playWhenReady);handler.postDelayed(periodic,5000);
 }
 private void playNext(){if(playlist==null||playlistIndex<0||playlistIndex+1>=playlist.length())return;
  int next=playlistIndex+1;JSONObject o=playlist.optJSONObject(next);if(o==null)return;String nextUrl=o.optString("url","");if(!nextUrl.startsWith("http"))return;
  playlistIndex=next;url=nextUrl;savedPosition=stored(url);restored=false;playWhenReady=true;episodeFinished=false;
  player.setMediaItem(MediaItem.fromUri(url));player.prepare();player.seekTo(savedPosition);player.setPlayWhenReady(true);
  Toast.makeText(this,"Próximo: "+o.optString("name","Episódio "+(next+1)),Toast.LENGTH_SHORT).show();
 }
 @Override protected void onStop(){handler.removeCallbacks(periodic);if(player!=null){if(!episodeFinished)saveProgress();savedPosition=player.getCurrentPosition();playWhenReady=player.getPlayWhenReady();restored=true;playerView.setPlayer(null);player.release();player=null;}super.onStop();}
 @Override protected void onSaveInstanceState(Bundle b){b.putLong("position",player!=null?player.getCurrentPosition():savedPosition);b.putBoolean("playing",player!=null?player.getPlayWhenReady():playWhenReady);b.putInt("playlistIndex",playlistIndex);b.putString("url",url);super.onSaveInstanceState(b);}
}
