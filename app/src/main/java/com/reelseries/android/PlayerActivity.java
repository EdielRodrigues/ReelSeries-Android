package com.reelseries.android;
import android.app.*;import android.os.*;import android.content.*;import android.content.pm.ActivityInfo;import android.view.*;import android.widget.*;
import androidx.media3.common.*;import androidx.media3.exoplayer.ExoPlayer;import androidx.media3.exoplayer.DefaultLoadControl;import androidx.media3.exoplayer.DefaultRenderersFactory;import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;import androidx.media3.datasource.DefaultHttpDataSource;import androidx.media3.ui.PlayerView;import androidx.media3.ui.AspectRatioFrameLayout;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.google.android.gms.cast.MediaLoadOptions;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.common.images.WebImage;
import androidx.mediarouter.media.MediaRouter;
import androidx.mediarouter.media.MediaRouteSelector;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.net.Uri;
import org.json.*;import java.security.MessageDigest;import java.nio.charset.StandardCharsets;
public class PlayerActivity extends Activity {
 // TV ao vivo: buffer moderado, sem seek de progresso salvo e com reconexao conservadora.
 private int liveReconnects=0;
 private long liveLastProgressAt=0, liveLastPosition=-1, liveStableSince=0;
 private boolean liveWatchActive=false;
 private final Runnable liveWatchdog=new Runnable(){@Override public void run(){
  if(!liveWatchActive||player==null||isFinishing())return;
  if(!isXtreamLive(url)||casting||!player.getPlayWhenReady()){handler.postDelayed(this,5000);return;}
  long now=android.os.SystemClock.elapsedRealtime();
  int state=player.getPlaybackState();long position=player.getCurrentPosition();
  if(state==Player.STATE_READY&&player.isPlaying()&&position>liveLastPosition+500){
   liveLastPosition=position;liveLastProgressAt=now;
   if(liveStableSince==0)liveStableSince=now;
   // Só zera tentativas após reprodução realmente estável.
   if(now-liveStableSince>60000)liveReconnects=0;
  }else if(state==Player.STATE_READY&&position>liveLastPosition+500){
   liveLastPosition=position;liveLastProgressAt=now;
  }else if(state!=Player.STATE_READY){liveStableSince=0;}
  // Aguarda tempo suficiente para a origem entregar dados antes de tentar recuperar.
  if(state!=Player.STATE_IDLE&&now-liveLastProgressAt>75000){
   if(liveReconnects<2){
    liveReconnects++;liveLastProgressAt=now;liveLastPosition=-1;liveStableSince=0;
    android.widget.Toast.makeText(PlayerActivity.this,"Recuperando TV ao vivo ("+liveReconnects+"/2)...",android.widget.Toast.LENGTH_SHORT).show();
    // Reabre a conexão de rede em vez de apenas seekTo em um stream ao vivo.
    player.stop();player.seekToDefaultPosition();player.prepare();player.setPlayWhenReady(true);
   }else{
    liveWatchActive=false;
    new AlertDialog.Builder(PlayerActivity.this).setTitle("Canal sem resposta")
     .setMessage("A transmissão ficou sem avançar mesmo após as tentativas de recuperação. Teste outro canal ou confira a origem IPTV.")
     .setPositiveButton("Voltar",(d,w)->finish()).show();return;
   }
  }
  handler.postDelayed(this,5000);
 }};
 private void beginLiveWatch(){handler.removeCallbacks(liveWatchdog);liveWatchActive=isXtreamLive(url);liveReconnects=0;liveLastPosition=-1;liveStableSince=0;liveLastProgressAt=android.os.SystemClock.elapsedRealtime();if(liveWatchActive)handler.postDelayed(liveWatchdog,5000);}
 private boolean liveHlsRetried=false;private String liveFirstError="";private ExoPlayer player;private PlayerView playerView;private FrameLayout playerRoot;private WebView webPlayer;private View webFullscreenView;private WebChromeClient.CustomViewCallback webFullscreenCallback;private boolean usingWeb=false;private long savedPosition=0;private String url;private boolean playWhenReady=true;
 private CastContext castContext;
 private String castError="";
 private boolean castLoadPending=false;
 private long castLoadToken=0;
 private SessionManagerListener<CastSession> castListener;
 private boolean casting=false;
 private MediaRouter mediaRouter;
 private MediaRouter.Callback routeCallback;
 private String displayName="";
 private String posterUrl="";
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
      if(pos>=5000 && pos%10000<1100){saveHistory(pos,duration);EpisodeWatchState.saveProgress(PlayerActivity.this,url,pos,duration);}
      if(duration>=60000 && pos>=duration*95/100)EpisodeWatchState.setWatched(PlayerActivity.this,url,true);
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
  if(castStateLabel!=null){castStateLabel.setText(connected?"● TV conectada":"");castStateLabel.setVisibility(connected?View.VISIBLE:View.GONE);}
  if(castIcon instanceof ImageView){((ImageView)castIcon).setImageResource(connected?R.drawable.ic_cast_conectado:R.drawable.ic_cast_desconectado);castIcon.setContentDescription(connected?"TV conectada":"Conectar à TV");}
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
 private long stored(String link){return UserMediaState.prefs(this,"reel_playback_progress").getLong(progressKey(link),0);}
 private void saveHistory(long pos,long duration){
  if(url==null||!url.startsWith("http")||isXtreamLive(url)||pos<2000)return;
  try{android.content.SharedPreferences pref=UserMediaState.prefs(this,"reel_continue_history");
   org.json.JSONObject entry=new org.json.JSONObject();entry.put("url",url);entry.put("name",displayName);entry.put("position",pos);entry.put("duration",duration);entry.put("updated",System.currentTimeMillis());entry.put("capa",posterUrl==null?"":posterUrl);entry.put("adult",getIntent().getBooleanExtra("adult",false));
   String key=progressKey(url);if(duration>0&&pos>=duration-15000)pref.edit().remove(key).apply();
   else pref.edit().putString(key,entry.toString()).commit();
  }catch(Exception ignored){}
 }
 private void saveProgress(){if(player==null||url==null||isXtreamLive(url))return;long duration=player.getDuration(),pos=player.getCurrentPosition();if(pos<2000)return;
  if(duration>0&&pos>=duration-15000){markComplete();return;}
  if(duration>=60000 && pos>=duration*95/100)EpisodeWatchState.setWatched(PlayerActivity.this,url,true);
  saveHistory(pos,duration);EpisodeWatchState.saveProgress(this,url,pos,duration);
  UserMediaState.prefs(this,"reel_playback_progress").edit().putLong(progressKey(url),pos).apply();}
 private void rememberCastMedia(){if(url!=null&&url.startsWith("http"))getSharedPreferences("reel_cast_last",MODE_PRIVATE).edit().putString("url",url).putString("name",displayName).putString("capa",posterUrl).apply();}
 private void markComplete(){if(url!=null){EpisodeWatchState.setWatched(this,url,true);EpisodeWatchState.clearProgress(this,url);UserMediaState.prefs(this,"reel_playback_progress").edit().remove(progressKey(url)).apply();UserMediaState.prefs(this,"reel_continue_history").edit().remove(progressKey(url)).apply();}}
 @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(0xff000000);getWindow().setNavigationBarColor(0xff000000);getWindow().getDecorView().setSystemUiVisibility(4|1024|4096);setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
 displayName=getIntent().getStringExtra("name");if(displayName==null)displayName="Reel Séries";posterUrl=getIntent().getStringExtra("capa");if(posterUrl==null)posterUrl="";
 url=getIntent().getStringExtra("url");
 if(url==null||!url.startsWith("http")){
  android.content.SharedPreferences last=getSharedPreferences("reel_cast_last",MODE_PRIVATE);
  url=last.getString("url","");displayName=last.getString("name","Reel Séries");posterUrl=last.getString("capa","");
  getIntent().putExtra("cast_notification_resume",true);
 }
 if(url==null||!url.startsWith("http")){finish();return;}
 rememberCastMedia();
 try{String raw=getIntent().getStringExtra("playlist");if(raw!=null){playlist=new JSONArray(raw);playlistIndex=getIntent().getIntExtra("playlistIndex",-1);}}catch(Exception ignored){playlist=null;}
 FrameLayout root=new FrameLayout(this);playerRoot=root;root.setBackgroundColor(0xff000000);playerView=new PlayerView(this);playerView.setUseController(true);playerView.setControllerShowTimeoutMs(4500);playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);root.addView(playerView,new FrameLayout.LayoutParams(-1,-1));setContentView(root);
 // Botão Cast próprio: não usa o diálogo de MediaRouteButton que fechava a Activity.
 try{
  castContext=CastContext.getSharedInstance(this);
  ImageView castButton=new ImageView(this);
  castButton.setImageResource(R.drawable.ic_cast_desconectado);
  castButton.setScaleType(ImageView.ScaleType.FIT_CENTER);
  castButton.setPadding(dpCast(9),dpCast(9),dpCast(9),dpCast(9));
  castIcon=castButton;
  castButton.setContentDescription("Transmitir para TV (Google Cast)");
  castButton.setClickable(true);
  FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams((int)(48*getResources().getDisplayMetrics().density),(int)(48*getResources().getDisplayMetrics().density),Gravity.TOP|Gravity.END);
  cp.setMargins(0,(int)(8*getResources().getDisplayMetrics().density),(int)(14*getResources().getDisplayMetrics().density),0);
  root.addView(castButton,cp);
  castStateLabel=new TextView(this);
  castStateLabel.setTextColor(0xff48dca5);castStateLabel.setTextSize(11);castStateLabel.setGravity(Gravity.CENTER);
  castStateLabel.setBackgroundColor(0xaa101820);
  FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams((int)(105*getResources().getDisplayMetrics().density),(int)(24*getResources().getDisplayMetrics().density),Gravity.TOP|Gravity.END);
  sp.topMargin=(int)(57*getResources().getDisplayMetrics().density);sp.rightMargin=(int)(6*getResources().getDisplayMetrics().density);
  root.addView(castStateLabel,sp);castStateLabel.setVisibility(View.GONE);
  castButton.setOnClickListener(v->showTransmissionOptions());
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
 private void showTransmissionOptions(){
  final String[] options={"Google Cast / Chromecast","Smart TV compatível (Google Cast)","Espelhamento Smart View / Android","Player externo"};
  new AlertDialog.Builder(this).setTitle("Transmitir para televisão").setItems(options,(d,which)->{
   switch(which){
    case 0:case 1:showCastDevices();break;
    case 2:new AlertDialog.Builder(this).setTitle("Espelhamento de tela").setMessage("No Samsung, abra o painel rápido e selecione Smart View. Ou tente as configurações de transmissão do Android.").setNegativeButton("Cancelar",null).setPositiveButton("Abrir configurações",(dd,w)->{try{startActivity(new Intent("android.settings.CAST_SETTINGS"));}catch(Exception e){startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));}}).show();break;
    case 3:openExternalPlayer(false);break;
   }
  }).setNegativeButton("Cancelar",null).show();
 }
 private void openExternalPlayer(boolean dlna){
  if(url==null||!url.startsWith("http"))return;
  try{
   Intent i=new Intent(Intent.ACTION_VIEW);
   i.setDataAndType(android.net.Uri.parse(url),url.toLowerCase(java.util.Locale.ROOT).contains(".m3u8")?"application/vnd.apple.mpegurl":"video/*");
   i.addCategory(Intent.CATEGORY_DEFAULT);
   startActivity(Intent.createChooser(i,dlna?"Escolher player com DLNA / UPnP":"Abrir vídeo em outro player"));
  }catch(Exception e){new AlertDialog.Builder(this).setTitle("Player não disponível").setMessage("Instale um player compatível. Para DLNA, o aplicativo externo precisa oferecer transmissão para a TV. Links protegidos ou autenticados podem não funcionar.").setPositiveButton("OK",null).show();}
 }
  private void requestCastNotificationPermission(){
   if(android.os.Build.VERSION.SDK_INT>=33 &&
      checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED){
    requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},1027);
   }
  }
 private void showCastDevices(){
   requestCastNotificationPermission();
  if(usingWeb){Toast.makeText(this,"Cast direto só funciona com URL de mídia compatível; para sites use espelhamento ou player externo.",Toast.LENGTH_LONG).show();return;}
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
 private int dpCast(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
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
  TextView status=new TextView(this);status.setText("📺 "+tvName+"  •  Conectado ao Google Cast");
  status.setTextSize(14);layout.addView(status);
  tvEpisodeTitle=new TextView(this);tvEpisodeTitle.setText(displayName);tvEpisodeTitle.setTextSize(16);tvEpisodeTitle.setTypeface(null,1);layout.addView(tvEpisodeTitle);
  tvSeek=new SeekBar(this);tvSeek.setMax(1000);layout.addView(tvSeek,new LinearLayout.LayoutParams(-1,-2));
  tvTime=new TextView(this);tvTime.setText("00:00:00 / 00:00:00");tvTime.setGravity(Gravity.CENTER);layout.addView(tvTime);
  tvSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
   public void onStartTrackingTouch(SeekBar b){seekingTv=true;}
   public void onProgressChanged(SeekBar b,int progress,boolean fromUser){if(fromUser){RemoteMediaClient r=session.getRemoteMediaClient();if(r!=null&&r.getMediaInfo()!=null){long duration=r.getMediaInfo().getStreamDuration();if(tvTime!=null)tvTime.setText(formatTime(duration*progress/1000)+" / "+formatTime(duration));}}}
   public void onStopTrackingTouch(SeekBar b){RemoteMediaClient r=session.getRemoteMediaClient();if(r!=null&&r.getMediaInfo()!=null){long duration=r.getMediaInfo().getStreamDuration();if(duration>0)r.seek(duration*b.getProgress()/1000);}seekingTv=false;}
  });
  LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER);controls.setOrientation(LinearLayout.HORIZONTAL);
  Button back=new Button(this);back.setText("-10s");back.setTextSize(12);back.setAllCaps(false);
  Button play=new Button(this);play.setText("Pausar / Play");play.setTextSize(12);play.setAllCaps(false);
  Button next=new Button(this);next.setText("+10s");next.setTextSize(12);next.setAllCaps(false);
  controls.addView(back,new LinearLayout.LayoutParams(0,dpCast(46),1));
  controls.addView(play,new LinearLayout.LayoutParams(0,dpCast(46),1.5f));
  controls.addView(next,new LinearLayout.LayoutParams(0,dpCast(46),1));
  layout.addView(controls);
  LinearLayout episodesRow=new LinearLayout(this);episodesRow.setGravity(Gravity.CENTER);
  Button previousEpisode=new Button(this);previousEpisode.setText("◀ Anterior");previousEpisode.setTextSize(12);previousEpisode.setAllCaps(false);
  Button nextEpisode=new Button(this);nextEpisode.setText("Próximo ▶");nextEpisode.setTextSize(12);nextEpisode.setAllCaps(false);
  episodesRow.addView(previousEpisode,new LinearLayout.LayoutParams(0,dpCast(48),1));
  episodesRow.addView(nextEpisode,new LinearLayout.LayoutParams(0,dpCast(48),1));
  layout.addView(episodesRow);
  // Controle de volume da televisão conectada (Google Cast, 0 a 100%).
  LinearLayout volumeRow=new LinearLayout(this);volumeRow.setOrientation(LinearLayout.HORIZONTAL);volumeRow.setGravity(Gravity.CENTER_VERTICAL);
  TextView volumeLabel=new TextView(this);volumeLabel.setText("Volume da TV");volumeLabel.setTextSize(13);
  volumeRow.addView(volumeLabel,new LinearLayout.LayoutParams(dpCast(105),-2));
  SeekBar tvVolume=new SeekBar(this);tvVolume.setMax(100);
  volumeRow.addView(tvVolume,new LinearLayout.LayoutParams(0,dpCast(48),1));
  TextView volumeValue=new TextView(this);volumeValue.setMinWidth(dpCast(42));volumeValue.setGravity(Gravity.RIGHT);volumeRow.addView(volumeValue);
  try{int current=(int)Math.round(session.getVolume()*100.0);current=Math.max(0,Math.min(100,current));tvVolume.setProgress(current);volumeValue.setText(current+"%");}
  catch(Exception ignored){tvVolume.setProgress(50);volumeValue.setText("50%");}
  tvVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
   public void onStartTrackingTouch(SeekBar b){}
   public void onProgressChanged(SeekBar b,int value,boolean fromUser){volumeValue.setText(value+"%");if(fromUser){try{session.setVolume(value/100.0);}catch(Exception e){Toast.makeText(PlayerActivity.this,"A TV não aceitou o ajuste de volume",Toast.LENGTH_SHORT).show();}}}
   public void onStopTrackingTouch(SeekBar b){}
  });
  layout.addView(volumeRow);

  previousEpisode.setEnabled(playlist!=null&&playlistIndex>0);
  nextEpisode.setEnabled(playlist!=null&&playlistIndex>=0&&playlistIndex+1<playlist.length());
  if(playlist==null||playlistIndex<0){TextView hint=new TextView(this);hint.setText("Para trocar episódios, abra a série pela lista de temporadas e aguarde o carregamento dos episódios.");hint.setTextSize(12);layout.addView(hint);}
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
  if(tvDialog.getWindow()!=null){tvDialog.getWindow().setLayout(Math.min(getResources().getDisplayMetrics().widthPixels-dpCast(28),dpCast(500)), -2);}
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
 private boolean isLiveCastLink(String link){
  if(link==null)return false;
  String lower=link.toLowerCase(java.util.Locale.ROOT);
  return isXtreamLive(link)||lower.matches("https?://[^?#]+/live/[^?#]+([?#].*)?")||(lower.contains(".ts")||lower.contains(".m3u8")) && (lower.contains("/live/")||lower.contains("/tv/"));
 }
 private String castMimeType(String link){
  String lower=link.toLowerCase(java.util.Locale.ROOT);
  if(lower.contains(".m3u8"))return "application/vnd.apple.mpegurl";
  if(lower.contains(".mpd"))return "application/dash+xml";
  if(lower.matches("https?://[^?#]+\\.ts([?#].*)?"))return "video/mp2t";
  if(lower.contains(".mp4"))return "video/mp4";
  return isLiveCastLink(link)?"video/mp2t":"video/mp4";
 }
 private String hlsCandidate(String link){
  if(link==null)return null;
  if(link.toLowerCase(java.util.Locale.ROOT).matches("https?://[^?#]+/live/[^?#]+\\.ts([?#].*)?"))
   return link.replaceFirst("(?i)\\.ts(?=([?#]|$))",".m3u8");
  return null;
 }
 private boolean isVerifiedHls(String link){
  java.net.HttpURLConnection connection=null;
  try{
   connection=(java.net.HttpURLConnection)new java.net.URL(link).openConnection();
   connection.setConnectTimeout(6500);connection.setReadTimeout(6500);
   connection.setRequestProperty("Accept","application/vnd.apple.mpegurl, application/x-mpegURL, */*");
   connection.setRequestProperty("User-Agent","ReelSeries/Android");
   connection.setInstanceFollowRedirects(true);
   if(connection.getResponseCode()!=200)return false;
   java.io.InputStream input=connection.getInputStream();
   byte[] bytes=new byte[512];int count=input.read(bytes);input.close();
   if(count<=0)return false;
   String header=new String(bytes,0,count,java.nio.charset.StandardCharsets.UTF_8).trim();
   return header.startsWith("#EXTM3U");
  }catch(Exception ignored){return false;}
  finally{if(connection!=null)connection.disconnect();}
 }
 private void showLiveCastUnavailable(){
  if(isFinishing())return;
  new AlertDialog.Builder(this).setTitle("TV ao vivo: formato incompatível")
   .setMessage("O canal funciona no celular, mas o Chromecast não conseguiu reproduzir o link diretamente. Não é possível transformar MPEG-TS em HLS apenas mudando a extensão. Você pode espelhar a tela pelo Smart View para assistir na televisão.")
   .setNegativeButton("Fechar",null)
   .setPositiveButton("Abrir espelhamento",(d,w)->{
    try{startActivity(new Intent("android.settings.CAST_SETTINGS"));}
    catch(Exception e){try{startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));}catch(Exception ignored){}}
   }).show();
 }
 private void sendToTv(CastSession session){
  if(session==null||!session.isConnected()||url==null||usingWeb)return;
  RemoteMediaClient remote=session.getRemoteMediaClient();if(remote==null)return;
  final String originalUrl=url;
  final String host=Uri.parse(originalUrl).getHost();
  if(host!=null&&(host.equalsIgnoreCase("localhost")||host.equals("127.0.0.1")||host.equals("0.0.0.0"))){
   new AlertDialog.Builder(this).setTitle("Link não acessível pelo Chromecast")
    .setMessage("Este endereço aponta para o próprio celular. O Chromecast tradicional precisa de um link de mídia acessível pela rede.")
    .setPositiveButton("Entendi",null).show();return;
  }
  final boolean live=isLiveCastLink(originalUrl);
  final String candidate=live?hlsCandidate(originalUrl):null;
  if(candidate!=null){
   Toast.makeText(this,"Verificando formato do canal para Google Cast...",Toast.LENGTH_SHORT).show();
   new Thread(()->{
    boolean available=isVerifiedHls(candidate);
    runOnUiThread(()->{
     if(isFinishing()||!originalUrl.equals(url)||!session.isConnected())return;
     if(available){
      loadCastMedia(session,remote,candidate,true);
     }else{
      startConvertedLiveCast(session,remote,originalUrl);
     }
    });
   },"ReelCastHlsCheck").start();
  }else if(live && !originalUrl.toLowerCase(java.util.Locale.ROOT).contains(".m3u8")){
   startConvertedLiveCast(session,remote,originalUrl);
  }else loadCastMedia(session,remote,originalUrl,live);
 }
 private static final String CAST_CONVERTER_URL="https://reelseries-cast-converter.onrender.com";
 private void startConvertedLiveCast(CastSession session,RemoteMediaClient remote,String source){
  com.google.firebase.auth.FirebaseUser user=com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
  if(user==null){Toast.makeText(this,"Faça login para transmitir TV ao vivo",Toast.LENGTH_LONG).show();return;}
  Toast.makeText(this,"Preparando TV ao vivo pelo conversor...",Toast.LENGTH_LONG).show();
  user.getIdToken(true).addOnCompleteListener(task->{
   if(!task.isSuccessful()||task.getResult()==null){
    Toast.makeText(this,"Não foi possível autenticar o conversor",Toast.LENGTH_LONG).show();return;
   }
   final String idToken=task.getResult().getToken();
   new Thread(()->{
    String playlist=null;String error=null;
    java.net.HttpURLConnection c=null;
    try{
     c=(java.net.HttpURLConnection)new java.net.URL(CAST_CONVERTER_URL+"/api/streams").openConnection();
     c.setRequestMethod("POST");c.setConnectTimeout(12000);c.setReadTimeout(20000);
     c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");
     c.setRequestProperty("Authorization","Bearer "+idToken);
     byte[] body=new org.json.JSONObject().put("url",source).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
     try(java.io.OutputStream out=c.getOutputStream()){out.write(body);}
     int code=c.getResponseCode();
     if(code!=200){
      String detail="";
      try(java.io.InputStream err=c.getErrorStream()){
       if(err!=null){byte[] buf=new byte[1024];int n=err.read(buf);if(n>0){
        String raw=new String(buf,0,n,java.nio.charset.StandardCharsets.UTF_8);
        detail=new org.json.JSONObject(raw).optString("detail","");
       }}
      }catch(Exception ignored){}
      error="Servidor HTTP "+code+(detail.isEmpty()?"":" — "+detail);
     }
     else{
      java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
      try(java.io.InputStream in=c.getInputStream()){
       byte[] buf=new byte[2048];int n;while((n=in.read(buf))!=-1 && bytes.size()<8192)bytes.write(buf,0,n);
      }
      playlist=new org.json.JSONObject(bytes.toString("UTF-8")).getString("playlist_url");
      // FFmpeg precisa gerar os primeiros segmentos antes do Cast.
      boolean ready=false;
      for(int attempt=0;attempt<12 && !ready;attempt++){
       Thread.sleep(2000);
       ready=isVerifiedHls(playlist);
      }
      if(!ready){error="O FFmpeg não gerou HLS em 24 segundos";playlist=null;}
     }
    }catch(Exception e){error=e.getClass().getSimpleName();}
    finally{if(c!=null)c.disconnect();}
    final String result=playlist,reason=error;
    runOnUiThread(()->{
     if(isFinishing()||!source.equals(url)||session==null||!session.isConnected())return;
     if(result!=null){loadCastMedia(session,remote,result,true);}
     else new AlertDialog.Builder(this).setTitle("Conversor de TV ao vivo")
      .setMessage("Não foi possível preparar este canal. "+(reason==null?"":reason)+". Confira a mensagem do servidor e os logs do Render.")
      .setPositiveButton("Entendi",null).show();
    });
   },"ReelCastConverter").start();
  });
 }
 private void loadCastMedia(CastSession session,RemoteMediaClient remote,String castUrl,boolean live){
  observeRemote(remote);remoteWasPlaying=false;nearEndChecks=0;
  MediaMetadata metadata=new MediaMetadata(live?MediaMetadata.MEDIA_TYPE_TV_SHOW:MediaMetadata.MEDIA_TYPE_MOVIE);
  metadata.putString(MediaMetadata.KEY_TITLE,displayName);
  metadata.putString(MediaMetadata.KEY_SUBTITLE,"Reel Séries • Preparando reprodução");
  // O receptor padrão do Google Cast exibe a capa enviada como metadado.
  // Não é possível substituir todo o fundo dele sem um receiver personalizado.
  if(posterUrl!=null&&(posterUrl.startsWith("https://")||posterUrl.startsWith("http://"))){
   try{metadata.addImage(new WebImage(Uri.parse(posterUrl)));}
   catch(Exception e){android.util.Log.w("ReelSeriesCast","Capa indisponível",e);}
  }
  MediaInfo info=new MediaInfo.Builder(castUrl)
   .setStreamType(live?MediaInfo.STREAM_TYPE_LIVE:MediaInfo.STREAM_TYPE_BUFFERED)
   .setContentType(castMimeType(castUrl)).setMetadata(metadata).build();
  long position=live?0:(changingCastEpisode?stored(url):(player!=null?Math.max(0,player.getCurrentPosition()):stored(url)));
  casting=true;updateCastState();
  castLoadPending=true;final long token=++castLoadToken;
  Toast.makeText(this,"Conectado à TV. Aguardando o vídeo iniciar...",Toast.LENGTH_LONG).show();
  // LOAD aceito não significa vídeo reproduzindo: verificar estado remoto.
  handler.postDelayed(()->{
   if(token!=castLoadToken||!castLoadPending||isFinishing()||!session.isConnected())return;
   RemoteMediaClient current=session.getRemoteMediaClient();
   MediaStatus state=current==null?null:current.getMediaStatus();
   if(state!=null&&state.getPlayerState()==MediaStatus.PLAYER_STATE_PLAYING){castLoadPending=false;return;}
   castLoadPending=false;
   if(live){if(player!=null)player.play();showLiveCastUnavailable();return;}
   new AlertDialog.Builder(this).setTitle("TV conectada, mas vídeo não iniciou")
    .setMessage("A TV recebeu a conexão Google Cast, mas não confirmou a reprodução em 20 segundos. Confira se o link de vídeo abre na TV e se o formato é compatível (MP4/HLS). Alguns links Xtream exigem autenticação ou não são acessíveis diretamente pela televisão.")
    .setPositiveButton("Tentar novamente",(d,w)->sendToTv(session))
    .setNegativeButton("Continuar no celular",(d,w)->{if(player!=null)player.play();})
    .show();
  },live?35000:20000);
  remote.load(info,new MediaLoadOptions.Builder().setAutoplay(true).setPlayPosition(position).build())
   .setResultCallback(result->runOnUiThread(()->{
    if(!result.getStatus().isSuccess()){
     castLoadPending=false;castLoadToken++;
     android.util.Log.e("ReelSeriesCast","Falha no carregamento Cast: "+result.getStatus().getStatusCode());
     if(live)showLiveCastUnavailable();
     else Toast.makeText(this,"Não foi possível carregar o vídeo na TV",Toast.LENGTH_LONG).show();
     if(player!=null)player.play();
    }else{
     // A successful LOAD does not mean the receiver has started playing.
     if(!live)pauseLocal();
     else handler.postDelayed(()->{
      if(isFinishing()||!session.isConnected())return;
      RemoteMediaClient r=session.getRemoteMediaClient();
      MediaStatus status=r==null?null:r.getMediaStatus();
      if(status!=null&&status.getPlayerState()==MediaStatus.PLAYER_STATE_PLAYING)castLoadPending=false;
      if(status!=null&&status.getPlayerState()==MediaStatus.PLAYER_STATE_PLAYING){pauseLocal();return;}
      // O timeout único de 20 segundos apresenta o diagnóstico, sem duplicar janelas.
     },18000);
    }
   }));
  changingCastEpisode=false;
  Toast.makeText(this,live?"Enviando TV ao vivo para a TV":"Enviando para a TV",Toast.LENGTH_SHORT).show();
 }
 @Override protected void onStart(){super.onStart();
 if(castContext!=null&&castListener!=null){
  castContext.getSessionManager().addSessionManagerListener(castListener,CastSession.class);
  try{mediaRouter=MediaRouter.getInstance(this);routeCallback=new MediaRouter.Callback(){};
   mediaRouter.addCallback(castContext.getMergedSelector(),routeCallback,MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY);
  }catch(Exception e){android.util.Log.e("ReelSeriesCast","Erro na busca",e);}
 }
// Quando a conexão foi iniciada na tela inicial, enviar automaticamente o conteúdo escolhido.
if(castContext!=null){
 try{CastSession alreadyConnected=castContext.getSessionManager().getCurrentCastSession();
  if(alreadyConnected!=null&&alreadyConnected.isConnected()){
   casting=true;updateCastState();
   handler.postDelayed(()->{
    if(!isFinishing()&&castContext!=null){CastSession active=castContext.getSessionManager().getCurrentCastSession();
     if(active!=null&&active.isConnected()&&url!=null){
      if(getIntent().getBooleanExtra("cast_notification_resume",false)){
       observeRemote(active.getRemoteMediaClient());pauseLocal();showTvControls();
      }else sendToTv(active);
     }
    }
   },350);
  }
 }catch(Exception e){android.util.Log.w("ReelSeriesCast","Falha no envio automático",e);}
}
handler.removeCallbacks(tvUpdater);handler.post(tvUpdater);
if(webPlayer!=null){webPlayer.onResume();handler.removeCallbacks(webProgressTick);handler.postDelayed(webProgressTick,5000);return;}
if(isWebLink(url)){openWebPlayer(url);return;}
player=newConfiguredPlayer();playerView.setPlayer(player);
 player.addListener(new Player.Listener(){
  @Override public void onPlayerError(PlaybackException e){if(isFinishing())return; if(retryLiveAsHls(e)){beginLiveWatch();return;}liveWatchActive=false;handler.removeCallbacks(liveWatchdog); new AlertDialog.Builder(PlayerActivity.this).setTitle(isXtreamLive(url)?"Não foi possível reproduzir TV ao vivo":"Não foi possível reproduzir").setMessage(playbackDiagnostic(e)).setPositiveButton("Voltar",(d,w)->finish()).show();}
  @Override public void onPlaybackStateChanged(int state){if(state==Player.STATE_ENDED&&!casting){markComplete();playNext();}}
 });
 player.setMediaItem(buildMediaItem(url));player.prepare();long pos=restored?savedPosition:Math.max(stored(url),getIntent().getLongExtra("resume_position",0));if(!isXtreamLive(url))player.seekTo(Math.max(0,pos));player.setPlayWhenReady(!casting&&playWhenReady);beginLiveWatch();handler.postDelayed(periodic,5000);
 }
 private boolean playEpisodeAt(int index){
  if(playlist==null||index<0||index>=playlist.length())return false;
  JSONObject o=playlist.optJSONObject(index);if(o==null)return false;
  String link=o.optString("url",o.optString("video",""));if(!link.startsWith("http"))return false;
  if(index!=playlistIndex){if(casting && castContext!=null){try{CastSession cs=castContext.getSessionManager().getCurrentCastSession();if(cs!=null && cs.getRemoteMediaClient()!=null){RemoteMediaClient rc=cs.getRemoteMediaClient();saveHistory(rc.getApproximateStreamPosition(),rc.getMediaInfo()==null?0:rc.getMediaInfo().getStreamDuration());}}catch(Exception ignored){}}saveProgress();lastAdvancedIndex=-1;lastAutoIndex=-1;playlistIndex=index;url=link;displayName=o.optString("name","Episódio "+(index+1));rememberCastMedia();savedPosition=0;restored=false;getIntent().removeExtra("resume_position");playWhenReady=true;
   if(castContext!=null){CastSession s=castContext.getSessionManager().getCurrentCastSession();if(s!=null&&s.isConnected()){
    changingCastEpisode=true;sendToTv(s);Toast.makeText(this,"Na TV: "+displayName,Toast.LENGTH_SHORT).show();return true;
   }}
   if(isWebLink(url)){openWebPlayer(url);return true;}
   closeWebPlayer();
   if(player==null){player=newConfiguredPlayer();playerView.setPlayer(player);}
   player.setMediaItem(buildMediaItem(url));player.prepare();player.setPlayWhenReady(true);return true;
  }return false;
 }
 private void playNext(){playEpisodeAt(playlistIndex+1);}
 private boolean isWebLink(String link){
  if(link==null)return false;
  String l=link.toLowerCase(java.util.Locale.ROOT);
  if(l.contains("/embed/")||l.contains("/movie/")||l.contains("youtube.com/watch")||l.contains("youtu.be/")||l.contains("/iframe/"))return true;
  if(l.matches(".*\\.(m3u8|mpd|mp4|m4v|mkv|webm|mov|mp3|aac|ts|mpg|mpeg|3gp|avi)([?#].*)?$"))return false;
  return l.matches(".*\\.(html?|php)([?#].*)?$");
 }
 // Some Xtream servers redirect HTTPS channel URLs to HTTP CDN endpoints.
 // Allow redirects across protocols for playback only; retain normal server-side authorization.
 private ExoPlayer newConfiguredPlayer(){
  DefaultHttpDataSource.Factory http=new DefaultHttpDataSource.Factory()
   .setAllowCrossProtocolRedirects(true)
   .setConnectTimeoutMs(20000)
   .setReadTimeoutMs(20000);
  DefaultMediaSourceFactory media=new DefaultMediaSourceFactory(http);
  // Streams ao vivo precisam de latência controlada; 60s de buffer podem
  // aumentar atraso e não corrigem um fluxo que deixa de entregar pacotes.
  boolean live=isXtreamLive(url);
  // FHD: preserva a resolucao entregue pelo servidor, sem limite artificial de 720p.
  // Um pouco mais de margem reduz rebuffering em canais 1080p, sem latencia excessiva.
  DefaultLoadControl buffers=new DefaultLoadControl.Builder()
   .setBufferDurationsMs(live?12000:15000,live?45000:60000,live?3500:1500,live?6000:3500).build();
  // Alguns dispositivos precisam alternar para outro decoder quando o primeiro falha.
  DefaultRenderersFactory renderers=new DefaultRenderersFactory(this)
   .setEnableDecoderFallback(true);
  return new ExoPlayer.Builder(this,renderers)
   .setMediaSourceFactory(media).setLoadControl(buffers).build();
 }
 private boolean isXtreamLive(String link){return link!=null&&link.toLowerCase(java.util.Locale.ROOT).matches("https?://[^?#]+/live/[^?#]+\\.(ts|m3u8)([?#].*)?");}
 private String playbackDiagnostic(PlaybackException error){
  StringBuilder message=new StringBuilder();
  if(isXtreamLive(url))message.append("A transmissão de TV ao vivo falhou. A API pode listar canais mesmo quando o endereço de vídeo está indisponível.\n\n");
  else message.append("Não foi possível reproduzir o vídeo.\n\n");
  if(!liveFirstError.isEmpty())message.append("Tentativa MPEG-TS: ").append(liveFirstError).append("\n");
  message.append("Última tentativa: ").append(error.getErrorCodeName());
  Throwable cause=error;
  while(cause!=null){
   if(cause instanceof androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException){
    int status=((androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)cause).responseCode;
    message.append(" (HTTP ").append(status).append(")");
    if(status==401||status==403)message.append("\nAcesso ao canal negado pelo servidor. Confira se a conta permite TV ao vivo e qual URL de streaming o fornecedor utiliza.");
    else if(status==404)message.append("\nO endereço de reprodução do canal não foi encontrado no servidor.");
    else if(status==502)message.append("\nO gateway do servidor não conseguiu entregar o canal (502). Verifique o canal no provedor.");
    else if(status>=500)message.append("\nFalha no servidor de streaming.");
    else if(status>=300&&status<400)message.append("\nO servidor devolveu um redirecionamento HTTP. Verifique se o destino é acessível e permitido.");
    break;
   }
   cause=cause.getCause();
  }
  message.append("\n\nFilmes e séries não foram alterados.");
  return message.toString();
 }
 // Xtream live servers may expose HLS when the MPEG-TS endpoint returns an unsupported response.
 private boolean retryLiveAsHls(PlaybackException error){
  if(liveHlsRetried||url==null)return false;
  String lower=url.toLowerCase(java.util.Locale.ROOT);
  if(!lower.matches("https?://[^?#]+/live/[^?#]+\\.ts([?#].*)?"))return false;
  if(error.errorCode!=PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED && error.errorCode!=PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED && error.errorCode!=PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS)return false;
  liveHlsRetried=true;liveFirstError=error.getErrorCodeName();
  Throwable c=error;while(c!=null){if(c instanceof androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException){liveFirstError+=" (HTTP "+((androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)c).responseCode+")";break;}c=c.getCause();}
  String hls=url.replaceFirst("(?i)\\.ts(?=([?#]|$))",".m3u8");
  android.widget.Toast.makeText(this,"Testando transmissão HLS do canal...",android.widget.Toast.LENGTH_SHORT).show();
  player.setMediaItem(buildMediaItem(hls));player.prepare();player.setPlayWhenReady(true);
  return true;
 }
 private MediaItem buildMediaItem(String link){
  MediaItem.Builder b=new MediaItem.Builder().setUri(Uri.parse(link));
  String l=link.toLowerCase(java.util.Locale.ROOT);
  if(l.contains(".m3u8"))b.setMimeType(MimeTypes.APPLICATION_M3U8);
  else if(isXtreamLive(link)&&l.contains(".ts"))b.setMimeType("video/mp2t");
  else if(l.contains(".mpd"))b.setMimeType(MimeTypes.APPLICATION_MPD);
  return b.build();
 }
 private final Runnable webProgressTick=new Runnable(){@Override public void run(){
  if(webPlayer==null||!usingWeb)return;
  try{webPlayer.evaluateJavascript("(function(){var v=document.querySelector('video');if(!v||!isFinite(v.currentTime)||!isFinite(v.duration))return '';return JSON.stringify({p:Math.floor(v.currentTime*1000),d:Math.floor(v.duration*1000)});})()",result->{
   try{if(result==null||result.equals("null")||result.equals("\"\""))return;
    String json=new org.json.JSONTokener(result).nextValue().toString();
    if(json.startsWith("{")){JSONObject data=new JSONObject(json);long pos=data.optLong("p",0),dur=data.optLong("d",0);
     if(pos>2000&&dur>0&&!isXtreamLive(url)){
      UserMediaState.prefs(PlayerActivity.this,"reel_playback_progress").edit().putLong(progressKey(url),pos).apply();saveHistory(pos,dur);EpisodeWatchState.saveProgress(PlayerActivity.this,url,pos,dur);
     }
    }
   }catch(Exception ignored){}
  });}catch(Exception ignored){}
  handler.postDelayed(this,5000);
 }};
 private void restoreWebPosition(){
  if(webPlayer==null||url==null||isXtreamLive(url))return;
  long pos=Math.max(stored(url),getIntent().getLongExtra("resume_position",0));
  if(pos<=0)return;
  final double seconds=pos/1000.0;
  // Apenas vídeo HTML5 acessível no documento principal. Não tenta acessar iframes de outros domínios.
  webPlayer.evaluateJavascript("(function(){var v=document.querySelector('video');if(v&&v.readyState>=1&&isFinite(v.duration)){v.currentTime="+seconds+";return true;}return false;})()",null);
 }
 private void exitWebFullscreen(){
  if(webFullscreenView!=null){
   if(playerRoot!=null)playerRoot.removeView(webFullscreenView);
   webFullscreenView=null;
   if(webFullscreenCallback!=null){webFullscreenCallback.onCustomViewHidden();webFullscreenCallback=null;}
   setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
   if(webPlayer!=null)webPlayer.setVisibility(View.VISIBLE);
  }
 }
 private void openWebPlayer(String link){
  if(playerRoot==null||isFinishing())return;
  if(player!=null){player.pause();playerView.setPlayer(null);player.release();player=null;}
  closeWebPlayer();usingWeb=true;playerView.setVisibility(View.GONE);
  webPlayer=new WebView(this);
  WebSettings settings=webPlayer.getSettings();
  settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);
  settings.setMediaPlaybackRequiresUserGesture(false);
  webPlayer.setBackgroundColor(0xff000000);
  webPlayer.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView view,String loaded){handler.postDelayed(()->restoreWebPosition(),1500);}});
  webPlayer.setWebChromeClient(new WebChromeClient(){
   @Override public void onShowCustomView(View view,CustomViewCallback callback){
    if(webFullscreenView!=null){callback.onCustomViewHidden();return;}
    webFullscreenView=view;webFullscreenCallback=callback;
    webPlayer.setVisibility(View.GONE);
    playerRoot.addView(view,new FrameLayout.LayoutParams(-1,-1));
    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
   }
   @Override public void onHideCustomView(){exitWebFullscreen();}
  });
  playerRoot.addView(webPlayer,0,new FrameLayout.LayoutParams(-1,-1));
  webPlayer.loadUrl(link);
  handler.removeCallbacks(webProgressTick);handler.postDelayed(webProgressTick,5000);
 }
 private void closeWebPlayer(){
  handler.removeCallbacks(webProgressTick);exitWebFullscreen();
  if(webPlayer!=null){WebView w=webPlayer;webPlayer=null;
   if(playerRoot!=null)playerRoot.removeView(w);
   w.stopLoading();w.loadUrl("about:blank");w.destroy();
  }
  usingWeb=false;if(playerView!=null)playerView.setVisibility(View.VISIBLE);
 }
 @Override protected void onStop(){if(casting && castContext!=null){try{CastSession cs=castContext.getSessionManager().getCurrentCastSession();if(cs!=null && cs.getRemoteMediaClient()!=null){RemoteMediaClient rc=cs.getRemoteMediaClient();saveHistory(rc.getApproximateStreamPosition(),rc.getMediaInfo()==null?0:rc.getMediaInfo().getStreamDuration());}}catch(Exception ignored){}}if(mediaRouter!=null&&routeCallback!=null){mediaRouter.removeCallback(routeCallback);routeCallback=null;}if(castContext!=null&&castListener!=null)castContext.getSessionManager().removeSessionManagerListener(castListener,CastSession.class);handler.removeCallbacks(periodic);handler.removeCallbacks(tvUpdater);liveWatchActive=false;handler.removeCallbacks(liveWatchdog);if(webPlayer!=null){handler.removeCallbacks(webProgressTick);webPlayer.onPause();}if(player!=null){saveProgress();savedPosition=player.getCurrentPosition();playWhenReady=player.getPlayWhenReady();restored=true;playerView.setPlayer(null);player.release();player=null;}super.onStop();}
 @Override protected void onDestroy(){closeWebPlayer();stopObservingRemote();super.onDestroy();}
 @Override protected void onSaveInstanceState(Bundle b){b.putLong("position",player!=null?player.getCurrentPosition():savedPosition);b.putBoolean("playing",player!=null?player.getPlayWhenReady():playWhenReady);b.putInt("playlistIndex",playlistIndex);b.putString("url",url);super.onSaveInstanceState(b);}
}
