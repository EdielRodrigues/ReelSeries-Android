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
import com.google.android.gms.cast.MediaStatus;
import androidx.mediarouter.media.MediaRouter;
import androidx.mediarouter.media.MediaRouteSelector;
import android.graphics.Canvas;
import android.graphics.Paint;
import org.json.*;import java.security.MessageDigest;import java.nio.charset.StandardCharsets;
public class PlayerActivity extends Activity {
 private ExoPlayer player;private PlayerView playerView;private long savedPosition=0;private String url;private boolean playWhenReady=true;
 private CastContext castContext;
 private String castError="";
 private SessionManagerListener<CastSession> castListener;
 private boolean casting=false;
 private MediaRouter mediaRouter;
 private MediaRouter.Callback routeCallback;
 private String displayName="";
 private AlertDialog tvDialog;
 private RemoteMediaClient observedRemote;
 private boolean changingCastEpisode=false;
 private boolean loadingNextCast=false;
 private boolean remoteWasPlaying=false;
 private int lastAdvancedIndex=-1;
 private SeekBar tvSeek;
 private TextView tvTime;
 private TextView tvEpisodeTitle;
 private boolean seekingTv=false;
 private long remoteDuration=0;
 private int nearEndChecks=0;
 private int lastAutoIndex=-1;
 private final Runnable tvUpdater=new Runnable(){@Override public void run(){
  try{
   if(castContext!=null){CastSession session=castContext.getSessionManager().getCurrentCastSession();
    if(session!=null&&session.isConnected()){
     RemoteMediaClient r=session.getRemoteMediaClient();
     if(r!=null){long pos=Math.max(0,r.getApproximateStreamPosition());
      com.google.android.gms.cast.MediaInfo info=r.getMediaInfo();
      long duration=info==null?0:Math.max(0,info.getStreamDuration());
      if(tvSeek!=null&&!seekingTv){tvSeek.setMax(1000);tvSeek.setProgress(duration>0?(int)Math.min(1000,pos*1000/duration):0);}
      if(tvTime!=null)tvTime.setText(formatTime(pos)+" / "+formatTime(duration));
      if(tvEpisodeTitle!=null)tvEpisodeTitle.setText(displayName);
      if(duration>10000 && pos>=duration-1700 && r.isPlaying() && playlist!=null && playlistIndex>=0 && playlistIndex+1<playlist.length() && lastAutoIndex!=playlistIndex){
       nearEndChecks++;if(nearEndChecks>=2){lastAutoIndex=playlistIndex;nearEndChecks=0;markComplete();playNext();}
      }else if(duration>0&&pos<duration-3000){nearEndChecks=0;}
     }
    }
   }
  }catch(Exception e){android.util.Log.w("ReelSeriesCast","Atualização da TV",e);}
  handler.postDelayed(this,1000);
 }};
 private String formatTime(long ms){long seconds=Math.max(0,ms/1000);return String.format(java.util.Locale.US,"%02d:%02d:%02d",seconds/3600,(seconds/60)%60,seconds%60);}
 private TextView castStateLabel;
 private View castIcon;
 private void updateCastState(){
  if(castContext==null)return;
  CastSession active=castContext.getSessionManager().getCurrentCastSession();
  boolean connected=active!=null&&active.isConnected();
  casting=connected;
  if(castStateLabel!=null){castStateLabel.setText(connected?"● Conectado":"");castStateLabel.setVisibility(connected?View.VISIBLE:View.GONE);}
  if(castIcon!=null)castIcon.invalidate();
 }
 private final RemoteMediaClient.Callback castPlaybackCallback=new RemoteMediaClient.Callback(){
  @Override public void onStatusUpdated(){
   if(observedRemote==null||!casting||changingCastEpisode||loadingNextCast)return;
   MediaStatus status=observedRemote.getMediaStatus();
   if(status==null)return;
   if(status.getPlayerState()==MediaStatus.PLAYER_STATE_PLAYING){remoteWasPlaying=true;return;}
   if(status.getPlayerState()==MediaStatus.PLAYER_STATE_IDLE &&
      status.getIdleReason()==MediaStatus.IDLE_REASON_FINISHED && remoteWasPlaying &&
      playlist!=null && playlistIndex>=0 && playlistIndex!=lastAdvancedIndex && playlistIndex!=lastAutoIndex){
    lastAdvancedIndex=playlistIndex;lastAutoIndex=playlistIndex;
    remoteWasPlaying=false;
    loadingNextCast=true;
    handler.post(()->{
     try{
      if(casting){markComplete();playNext();}
     }finally{handler.postDelayed(()->loadingNextCast=false,1200);}
    });
   }
  }
 };
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
 // Botão Cast próprio: não usa o diálogo de MediaRouteButton que fechava a Activity.
 try{
  castContext=CastContext.getSharedInstance(this);
  View castButton=new View(this){
   private final Paint paint=new Paint(3);
   @Override protected void onDraw(Canvas canvas){
    super.onDraw(canvas);
    float d=getResources().getDisplayMetrics().density;
    float scale=Math.min(getWidth()/(48f*d),getHeight()/(48f*d));
    float x=getWidth()/2f-12*d*scale,y=getHeight()/2f-11*d*scale;
    paint.setColor(casting?0xff48dca5:0xffffffff);
    paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2.2f*d*scale);
    canvas.drawLine(x+3*d*scale,y+2*d*scale,x+23*d*scale,y+2*d*scale,paint);
    canvas.drawLine(x+23*d*scale,y+2*d*scale,x+23*d*scale,y+20*d*scale,paint);
    canvas.drawLine(x+3*d*scale,y+2*d*scale,x+3*d*scale,y+8*d*scale,paint);
    canvas.drawArc(x-5*d*scale,y+11*d*scale,x+10*d*scale,y+26*d*scale,270,90,false,paint);
    canvas.drawArc(x+1*d*scale,y+16*d*scale,x+8*d*scale,y+23*d*scale,270,90,false,paint);
    paint.setStyle(Paint.Style.FILL);canvas.drawCircle(x+3*d*scale,y+20*d*scale,1.8f*d*scale,paint);
   }
  };
  castIcon=castButton;
  castButton.setContentDescription("Transmitir para TV (Google Cast)");
  castButton.setClickable(true);
  FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams((int)(58*getResources().getDisplayMetrics().density),(int)(58*getResources().getDisplayMetrics().density),Gravity.TOP|Gravity.CENTER_HORIZONTAL);
  cp.setMargins(0,(int)(8*getResources().getDisplayMetrics().density),0,0);
  root.addView(castButton,cp);
  castStateLabel=new TextView(this);
  castStateLabel.setTextColor(0xff48dca5);castStateLabel.setTextSize(11);castStateLabel.setGravity(Gravity.CENTER);
  castStateLabel.setBackgroundColor(0xaa101820);
  FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams((int)(105*getResources().getDisplayMetrics().density),(int)(24*getResources().getDisplayMetrics().density),Gravity.TOP|Gravity.CENTER_HORIZONTAL);
  sp.topMargin=(int)(55*getResources().getDisplayMetrics().density);
  root.addView(castStateLabel,sp);castStateLabel.setVisibility(View.GONE);
  castButton.setOnClickListener(v->showCastDevices());
  castListener=new SessionManagerListener<CastSession>(){
   @Override public void onSessionStarted(CastSession session,String id){sendToTv(session);updateCastState();handler.postDelayed(()->showTvControls(),650);}
   @Override public void onSessionResumed(CastSession session,boolean wasSuspended){casting=true;observeRemote(session.getRemoteMediaClient());pauseLocal();updateCastState();handler.post(()->showTvControls());}
   @Override public void onSessionEnded(CastSession session,int error){casting=false;updateCastState();stopObservingRemote();if(tvDialog!=null){tvDialog.dismiss();tvDialog=null;}Toast.makeText(PlayerActivity.this,"TV desconectada",Toast.LENGTH_SHORT).show();}
   @Override public void onSessionStarting(CastSession session){}
   @Override public void onSessionStartFailed(CastSession session,int error){Toast.makeText(PlayerActivity.this,"Não foi possível conectar à TV",Toast.LENGTH_SHORT).show();}
   @Override public void onSessionEnding(CastSession session){}
   @Override public void onSessionResuming(CastSession session,String id){}
   @Override public void onSessionResumeFailed(CastSession session,int error){casting=false;updateCastState();}
   @Override public void onSessionSuspended(CastSession session,int reason){}
  };
 }catch(Exception e){
  castError=e.getClass().getSimpleName()+": "+String.valueOf(e.getMessage());
  android.util.Log.e("ReelSeriesCast","Falha ao inicializar Google Cast",e);
 }

 if(b!=null){savedPosition=b.getLong("position",0);playWhenReady=b.getBoolean("playing",true);restored=true;playlistIndex=b.getInt("playlistIndex",playlistIndex);url=b.getString("url",url);}
 }
 private void showCastDevices(){
  if(castContext!=null){
   CastSession current=castContext.getSessionManager().getCurrentCastSession();
   if(current!=null&&current.isConnected()){showTvControls();return;}
  }
  if(castContext==null){new AlertDialog.Builder(this).setTitle("Google Cast")
   .setMessage("Não foi possível iniciar o Google Cast. "+castError).setPositiveButton("OK",null).show();return;}
  try{
   MediaRouter router=MediaRouter.getInstance(this);
   MediaRouteSelector selector=castContext.getMergedSelector();
   java.util.ArrayList<MediaRouter.RouteInfo> routes=new java.util.ArrayList<>();
   java.util.ArrayList<String> names=new java.util.ArrayList<>();
   for(MediaRouter.RouteInfo route:router.getRoutes()){
    if(route.isEnabled() && !route.isDefault() && route.matchesSelector(selector)){
     routes.add(route);names.add(String.valueOf(route.getName()));
    }
   }
   if(routes.isEmpty()){
    new AlertDialog.Builder(this).setTitle("Google Cast")
     .setMessage("Nenhum Chromecast encontrado. Confirme que o aparelho está ligado e conectado à mesma rede Wi-Fi. Aguarde alguns segundos e tente novamente.")
     .setPositiveButton("OK",null).show();return;
   }
   new AlertDialog.Builder(this).setTitle("Transmitir para TV")
    .setItems(names.toArray(new String[0]),(dialog,which)->{
      try{routes.get(which).select();}
      catch(Exception e){Toast.makeText(this,"Falha ao conectar: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }).setNegativeButton("Cancelar",null).show();
  }catch(Exception e){
   android.util.Log.e("ReelSeriesCast","Falha ao listar aparelhos",e);
   new AlertDialog.Builder(this).setTitle("Google Cast")
    .setMessage("Erro ao procurar aparelhos: "+e.getClass().getSimpleName()+": "+e.getMessage())
    .setPositiveButton("OK",null).show();
  }
 }
 private void showTvControls(){
  if(isFinishing()||castContext==null)return;
  CastSession session=castContext.getSessionManager().getCurrentCastSession();
  if(session==null||!session.isConnected())return;
  if(tvDialog!=null&&tvDialog.isShowing())return;
  String device="Televisão";
  try{if(session.getCastDevice()!=null)device=session.getCastDevice().getFriendlyName();}catch(Exception ignored){}
  final String tvName=device;
  LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);
  int pad=(int)(18*getResources().getDisplayMetrics().density);
  layout.setPadding(pad,pad,pad,pad);
  TextView status=new TextView(this);status.setText("📺 "+tvName+"\nConectado ao Google Cast\n\n"+displayName);
  status.setTextSize(17);layout.addView(status);
  tvEpisodeTitle=new TextView(this);tvEpisodeTitle.setText(displayName);tvEpisodeTitle.setTextSize(15);layout.addView(tvEpisodeTitle);
  tvSeek=new SeekBar(this);tvSeek.setMax(1000);layout.addView(tvSeek,new LinearLayout.LayoutParams(-1,-2));
  tvTime=new TextView(this);tvTime.setText("00:00:00 / 00:00:00");tvTime.setGravity(Gravity.CENTER);layout.addView(tvTime);
  tvSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
   public void onStartTrackingTouch(SeekBar b){seekingTv=true;}
   public void onProgressChanged(SeekBar b,int progress,boolean fromUser){if(fromUser){RemoteMediaClient r=session.getRemoteMediaClient();if(r!=null&&r.getMediaInfo()!=null){long duration=r.getMediaInfo().getStreamDuration();if(tvTime!=null)tvTime.setText(formatTime(duration*progress/1000)+" / "+formatTime(duration));}}}
   public void onStopTrackingTouch(SeekBar b){RemoteMediaClient r=session.getRemoteMediaClient();if(r!=null&&r.getMediaInfo()!=null){long duration=r.getMediaInfo().getStreamDuration();if(duration>0)r.seek(duration*b.getProgress()/1000);}seekingTv=false;}
  });
  LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER);
  Button back=new Button(this);back.setText("-10s");controls.addView(back);
  Button play=new Button(this);play.setText("Pausar / Continuar");controls.addView(play);
  Button next=new Button(this);next.setText("+10s");controls.addView(next);
  layout.addView(controls);
  LinearLayout episodesRow=new LinearLayout(this);episodesRow.setGravity(Gravity.CENTER);
  Button previousEpisode=new Button(this);previousEpisode.setText("◀ Episódio anterior");episodesRow.addView(previousEpisode,new LinearLayout.LayoutParams(0,-2,1));
  Button nextEpisode=new Button(this);nextEpisode.setText("Próximo episódio ▶");episodesRow.addView(nextEpisode,new LinearLayout.LayoutParams(0,-2,1));
  layout.addView(episodesRow);
  previousEpisode.setEnabled(playlist!=null&&playlistIndex>0);
  nextEpisode.setEnabled(playlist!=null&&playlistIndex>=0&&playlistIndex+1<playlist.length());
  previousEpisode.setOnClickListener(v->{if(playEpisodeAt(playlistIndex-1)){if(tvDialog!=null)tvDialog.dismiss();handler.postDelayed(()->showTvControls(),350);}});
  nextEpisode.setOnClickListener(v->{if(playEpisodeAt(playlistIndex+1)){if(tvDialog!=null)tvDialog.dismiss();handler.postDelayed(()->showTvControls(),350);}});
  back.setOnClickListener(v->{RemoteMediaClient r=session.getRemoteMediaClient();if(r!=null)r.seek(Math.max(0,r.getApproximateStreamPosition()-10000));});
  next.setOnClickListener(v->{RemoteMediaClient r=session.getRemoteMediaClient();if(r!=null)r.seek(r.getApproximateStreamPosition()+10000);});
  play.setOnClickListener(v->{RemoteMediaClient r=session.getRemoteMediaClient();if(r!=null){if(r.isPlaying())r.pause();else r.play();}});
  tvDialog=new AlertDialog.Builder(this).setTitle("Controle da TV")
   .setView(layout)
   .setNegativeButton("Fechar tela",(d,w)->{})
   .setNeutralButton("Desconectar",(d,w)->new AlertDialog.Builder(this)
    .setTitle("Desconectar da TV?")
    .setMessage("A transmissão será encerrada em "+tvName+".")
    .setNegativeButton("Cancelar",(a,b)->handler.post(()->showTvControls()))
    .setPositiveButton("Desconectar",(a,b)->{
     try{castContext.getSessionManager().endCurrentSession(true);casting=false;}
     catch(Exception e){Toast.makeText(this,"Erro ao desconectar: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }).show())
   .create();
  tvDialog.setOnDismissListener(d->{tvDialog=null;tvSeek=null;tvTime=null;tvEpisodeTitle=null;seekingTv=false;});
  tvDialog.show();
 }
 private void observeRemote(RemoteMediaClient remote){
  if(remote==observedRemote)return;
  stopObservingRemote();
  observedRemote=remote;
  if(remote!=null)remote.registerCallback(castPlaybackCallback);
 }
 private void stopObservingRemote(){
  if(observedRemote!=null){observedRemote.unregisterCallback(castPlaybackCallback);observedRemote=null;}
 }
 private void pauseLocal(){if(player!=null){saveProgress();player.pause();}}
 private void sendToTv(CastSession session){
  if(session==null||!session.isConnected()||url==null)return;
  RemoteMediaClient remote=session.getRemoteMediaClient();if(remote==null)return;
  observeRemote(remote);
  remoteWasPlaying=false;
  MediaMetadata metadata=new MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE);
  metadata.putString(MediaMetadata.KEY_TITLE,displayName);
  String type=url.toLowerCase(java.util.Locale.ROOT).contains(".m3u8")?"application/x-mpegURL":url.toLowerCase(java.util.Locale.ROOT).contains(".mpd")?"application/dash+xml":"video/mp4";
  MediaInfo info=new MediaInfo.Builder(url).setStreamType(MediaInfo.STREAM_TYPE_BUFFERED).setContentType(type).setMetadata(metadata).build();
  long position=changingCastEpisode?stored(url):(player!=null?Math.max(0,player.getCurrentPosition()):stored(url));
  pauseLocal();casting=true;updateCastState();
  remote.load(info,new MediaLoadOptions.Builder().setAutoplay(true).setPlayPosition(position).build());
  changingCastEpisode=false;
  Toast.makeText(this,"Enviando para a TV",Toast.LENGTH_SHORT).show();
 }
 @Override protected void onStart(){super.onStart();
 if(castContext!=null&&castListener!=null){
  castContext.getSessionManager().addSessionManagerListener(castListener,CastSession.class);
  try{mediaRouter=MediaRouter.getInstance(this);routeCallback=new MediaRouter.Callback(){};
   mediaRouter.addCallback(castContext.getMergedSelector(),routeCallback,MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY);
  }catch(Exception e){android.util.Log.e("ReelSeriesCast","Erro na busca",e);}
 }
handler.removeCallbacks(tvUpdater);handler.post(tvUpdater);
player=new ExoPlayer.Builder(this).build();playerView.setPlayer(player);
 player.addListener(new Player.Listener(){
  @Override public void onPlayerError(PlaybackException e){if(!isFinishing())new AlertDialog.Builder(PlayerActivity.this).setTitle("Não foi possível reproduzir").setMessage("O servidor ou o formato do vídeo pode estar indisponível.\n\n"+e.getErrorCodeName()).setPositiveButton("Voltar",(d,w)->finish()).show();}
  @Override public void onPlaybackStateChanged(int state){if(state==Player.STATE_ENDED&&!casting){markComplete();playNext();}}
 });
 player.setMediaItem(MediaItem.fromUri(url));player.prepare();long pos=restored?savedPosition:stored(url);player.seekTo(Math.max(0,pos));player.setPlayWhenReady(!casting&&playWhenReady);handler.postDelayed(periodic,5000);
 }
 private boolean playEpisodeAt(int index){
  if(playlist==null||index<0||index>=playlist.length())return false;
  JSONObject o=playlist.optJSONObject(index);if(o==null)return false;
  String link=o.optString("url",o.optString("video",""));if(!link.startsWith("http"))return false;
  if(index!=playlistIndex){playlistIndex=index;url=link;displayName=o.optString("name","Episódio "+(index+1));savedPosition=0;restored=false;playWhenReady=true;
   if(castContext!=null){CastSession s=castContext.getSessionManager().getCurrentCastSession();if(s!=null&&s.isConnected()){
    changingCastEpisode=true;sendToTv(s);Toast.makeText(this,"Na TV: "+displayName,Toast.LENGTH_SHORT).show();return true;
   }}
   if(player!=null){player.setMediaItem(MediaItem.fromUri(url));player.prepare();player.setPlayWhenReady(true);}return true;
  }return false;
 }
 private void playNext(){playEpisodeAt(playlistIndex+1);}
 @Override protected void onStop(){if(mediaRouter!=null&&routeCallback!=null){mediaRouter.removeCallback(routeCallback);routeCallback=null;}if(castContext!=null&&castListener!=null)castContext.getSessionManager().removeSessionManagerListener(castListener,CastSession.class);handler.removeCallbacks(periodic);handler.removeCallbacks(tvUpdater);if(player!=null){saveProgress();savedPosition=player.getCurrentPosition();playWhenReady=player.getPlayWhenReady();restored=true;playerView.setPlayer(null);player.release();player=null;}super.onStop();}
 @Override protected void onDestroy(){stopObservingRemote();super.onDestroy();}
 @Override protected void onSaveInstanceState(Bundle b){b.putLong("position",player!=null?player.getCurrentPosition():savedPosition);b.putBoolean("playing",player!=null?player.getPlayWhenReady():playWhenReady);b.putInt("playlistIndex",playlistIndex);b.putString("url",url);super.onSaveInstanceState(b);}
}
