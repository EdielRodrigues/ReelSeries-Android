package com.reelseries.android;
import android.app.*;import android.os.*;import android.content.*;import android.content.pm.ActivityInfo;import android.view.*;import android.widget.*;
import androidx.media3.common.*;import androidx.media3.exoplayer.ExoPlayer;import androidx.media3.ui.PlayerView;import androidx.media3.ui.AspectRatioFrameLayout;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.google.android.gms.cast.MediaLoadOptions;
import androidx.mediarouter.app.MediaRouteButton;
import org.json.*;import java.security.MessageDigest;import java.nio.charset.StandardCharsets;
public class PlayerActivity extends Activity {
 private ExoPlayer player;private PlayerView playerView;private long savedPosition=0;private String url;private boolean playWhenReady=true;
 private CastContext castContext;
 private String castError="";
 private SessionManagerListener<CastSession> castListener;
 private boolean casting=false;
 private String displayName="";
 private JSONArray playlist;private int playlistIndex=-1;private boolean restored=false;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final Runnable periodic=new Runnable(){@Override public void run(){saveProgress();handler.postDelayed(this,5000);}};
 private String progressKey(String link){try{byte[] hash=MessageDigest.getInstance("SHA-256").digest(link.getBytes(StandardCharsets.UTF_8));StringBuilder b=new StringBuilder("p_");for(int i=0;i<12;i++)b.append(String.format(java.util.Locale.US,"%02x",hash[i]&255));return b.toString();}catch(Exception e){return "p_"+link.hashCode();}}
 private long stored(String link){return getSharedPreferences("reel_playback_progress",MODE_PRIVATE).getLong(progressKey(link),0);}
 private void saveProgress(){if(player==null||url==null)return;long duration=player.getDuration(),pos=player.getCurrentPosition();if(pos<0)return;
  if(duration>0&&pos>=duration-15000){getSharedPreferences("reel_playback_progress",MODE_PRIVATE).edit().remove(progressKey(url)).apply();return;}
  getSharedPreferences("reel_playback_progress",MODE_PRIVATE).edit().putLong(progressKey(url),pos).apply();}
 private void markComplete(){if(url!=null)getSharedPreferences("reel_playback_progress",MODE_PRIVATE).edit().remove(progressKey(url)).apply();}
 @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(0xff000000);getWindow().setNavigationBarColor(0xff000000);getWindow().getDecorView().setSystemUiVisibility(4|1024|4096);setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
 displayName=getIntent().getStringExtra("name");if(displayName==null)displayName="Reel Séries";
 url=getIntent().getStringExtra("url");if(url==null||!url.startsWith("http")){finish();return;}
 try{String raw=getIntent().getStringExtra("playlist");if(raw!=null){playlist=new JSONArray(raw);playlistIndex=getIntent().getIntExtra("playlistIndex",-1);}}catch(Exception ignored){playlist=null;}
 FrameLayout root=new FrameLayout(this);root.setBackgroundColor(0xff000000);playerView=new PlayerView(this);playerView.setUseController(true);playerView.setControllerShowTimeoutMs(4500);playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);root.addView(playerView,new FrameLayout.LayoutParams(-1,-1));setContentView(root);
 // O botão é integrado à seleção de dispositivos Google Cast na mesma rede.
 try{
  castContext=CastContext.getSharedInstance(this);
  MediaRouteButton castButton=new MediaRouteButton(this);
  castButton.setContentDescription("Transmitir para TV (Google Cast)");
  FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams((int)(58*getResources().getDisplayMetrics().density),(int)(58*getResources().getDisplayMetrics().density),Gravity.TOP|Gravity.END);
  cp.setMargins(0,(int)(18*getResources().getDisplayMetrics().density),(int)(16*getResources().getDisplayMetrics().density),0);
  root.addView(castButton,cp);
  CastButtonFactory.setUpMediaRouteButton(getApplicationContext(),castButton);
  castListener=new SessionManagerListener<CastSession>(){
   @Override public void onSessionStarted(CastSession session,String id){sendToTv(session);}
   @Override public void onSessionResumed(CastSession session,boolean wasSuspended){casting=true;pauseLocal();}
   @Override public void onSessionEnded(CastSession session,int error){casting=false;}
   @Override public void onSessionStarting(CastSession session){}
   @Override public void onSessionStartFailed(CastSession session,int error){Toast.makeText(PlayerActivity.this,"Não foi possível conectar à TV",Toast.LENGTH_SHORT).show();}
   @Override public void onSessionEnding(CastSession session){}
   @Override public void onSessionResuming(CastSession session,String id){}
   @Override public void onSessionResumeFailed(CastSession session,int error){casting=false;}
   @Override public void onSessionSuspended(CastSession session,int reason){}
  };
 }catch(Exception e){
   castError=e.getClass().getSimpleName()+": "+String.valueOf(e.getMessage());
   android.util.Log.e("ReelSeriesCast","Falha ao inicializar Google Cast",e);
   TextView castRetry=new TextView(this);
   castRetry.setText("📺 Cast: verificar conexão");castRetry.setTextColor(0xffffffff);
   castRetry.setTextSize(12);castRetry.setGravity(Gravity.CENTER);
   castRetry.setBackgroundColor(0xaa222222);
   FrameLayout.LayoutParams rp=new FrameLayout.LayoutParams((int)(185*getResources().getDisplayMetrics().density),(int)(48*getResources().getDisplayMetrics().density),Gravity.TOP|Gravity.END);
   rp.setMargins(0,(int)(18*getResources().getDisplayMetrics().density),(int)(12*getResources().getDisplayMetrics().density),0);
   root.addView(castRetry,rp);
   castRetry.setOnClickListener(v->new AlertDialog.Builder(this)
     .setTitle("Diagnóstico Google Cast")
     .setMessage("Não foi possível inicializar o Google Cast.\n\nErro: "+castError+"\n\nVerifique se o Google Play Services está atualizado, se o Chromecast está ligado e se ambos estão na mesma rede Wi-Fi. Depois feche e abra o player novamente.")
     .setPositiveButton("OK",null).show());
 }

 if(b!=null){savedPosition=b.getLong("position",0);playWhenReady=b.getBoolean("playing",true);restored=true;playlistIndex=b.getInt("playlistIndex",playlistIndex);url=b.getString("url",url);}
 }
 private void pauseLocal(){if(player!=null){saveProgress();player.pause();}}
 private void sendToTv(CastSession session){
  if(session==null||!session.isConnected()||url==null)return;
  RemoteMediaClient remote=session.getRemoteMediaClient();if(remote==null)return;
  MediaMetadata metadata=new MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE);
  metadata.putString(MediaMetadata.KEY_TITLE,displayName);
  String type=url.toLowerCase(java.util.Locale.ROOT).contains(".m3u8")?"application/x-mpegURL":url.toLowerCase(java.util.Locale.ROOT).contains(".mpd")?"application/dash+xml":"video/mp4";
  MediaInfo info=new MediaInfo.Builder(url).setStreamType(MediaInfo.STREAM_TYPE_BUFFERED).setContentType(type).setMetadata(metadata).build();
  long position=player!=null?Math.max(0,player.getCurrentPosition()):stored(url);
  pauseLocal();casting=true;
  remote.load(info,new MediaLoadOptions.Builder().setAutoplay(true).setPlayPosition(position).build());
  Toast.makeText(this,"Enviando para a TV",Toast.LENGTH_SHORT).show();
 }
 @Override protected void onStart(){super.onStart();
 if(castContext!=null&&castListener!=null){castContext.getSessionManager().addSessionManagerListener(castListener,CastSession.class);}
player=new ExoPlayer.Builder(this).build();playerView.setPlayer(player);
 player.addListener(new Player.Listener(){
  @Override public void onPlayerError(PlaybackException e){if(!isFinishing())new AlertDialog.Builder(PlayerActivity.this).setTitle("Não foi possível reproduzir").setMessage("O servidor ou o formato do vídeo pode estar indisponível.\n\n"+e.getErrorCodeName()).setPositiveButton("Voltar",(d,w)->finish()).show();}
  @Override public void onPlaybackStateChanged(int state){if(state==Player.STATE_ENDED&&!casting){markComplete();playNext();}}
 });
 player.setMediaItem(MediaItem.fromUri(url));player.prepare();long pos=restored?savedPosition:stored(url);player.seekTo(Math.max(0,pos));player.setPlayWhenReady(!casting&&playWhenReady);handler.postDelayed(periodic,5000);
 }
 private void playNext(){if(playlist==null||playlistIndex<0||playlistIndex+1>=playlist.length())return;
  int next=playlistIndex+1;JSONObject o=playlist.optJSONObject(next);if(o==null)return;String nextUrl=o.optString("url",o.optString("video",""));if(!nextUrl.startsWith("http"))return;
  playlistIndex=next;url=nextUrl;savedPosition=stored(url);restored=false;playWhenReady=true;
  if(casting&&castContext!=null){CastSession session=castContext.getSessionManager().getCurrentCastSession();if(session!=null&&session.isConnected()){sendToTv(session);return;}casting=false;}
  player.setMediaItem(MediaItem.fromUri(url));player.prepare();player.seekTo(savedPosition);player.setPlayWhenReady(true);
  Toast.makeText(this,"Próximo: "+o.optString("name","Episódio "+(next+1)),Toast.LENGTH_SHORT).show();
 }
 @Override protected void onStop(){if(castContext!=null&&castListener!=null)castContext.getSessionManager().removeSessionManagerListener(castListener,CastSession.class);handler.removeCallbacks(periodic);if(player!=null){saveProgress();savedPosition=player.getCurrentPosition();playWhenReady=player.getPlayWhenReady();restored=true;playerView.setPlayer(null);player.release();player=null;}super.onStop();}
 @Override protected void onSaveInstanceState(Bundle b){b.putLong("position",player!=null?player.getCurrentPosition():savedPosition);b.putBoolean("playing",player!=null?player.getPlayWhenReady():playWhenReady);b.putInt("playlistIndex",playlistIndex);b.putString("url",url);super.onSaveInstanceState(b);}
}
