package com.reelseries.android;
import android.app.*;import android.os.*;import android.content.*;import android.graphics.*;import android.graphics.drawable.*;import android.view.*;import android.widget.*;import org.json.*;import java.net.*;import java.io.*;
public class DetailsActivity extends Activity {
 int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);} TextView text(String s,int size,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setPadding(0,dp(7),0,dp(7));return v;}
 @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(0xff0c0c10);getWindow().setNavigationBarColor(0xff0c0c10);JSONObject o;try{o=new JSONObject(getIntent().getStringExtra("item"));}catch(Exception e){finish();return;}
 ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(0xff0c0c10);LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(16),dp(12),dp(16),dp(28));scroll.addView(root);setContentView(scroll);
 TextView back=text("‹  Voltar ao catálogo",17,0xffe5e7eb);root.addView(back);back.setOnClickListener(v->finish());
 ImageView cover=new ImageView(this);cover.setScaleType(ImageView.ScaleType.CENTER_CROP);cover.setBackgroundColor(0xff222229);root.addView(cover,new LinearLayout.LayoutParams(-1,dp(285)));
 TextView title=text(o.optString("nome","Sem título"),29,0xffffffff);title.setTypeface(null,1);root.addView(title);
 TextView category=text("●  "+o.optString("categoria","Catálogo"),13,0xfff15c6b);root.addView(category);
 String episode=o.optString("episodio","").trim();if(!episode.isEmpty())root.addView(text("EPISÓDIO  •  "+episode,14,0xffd1d5db));
 String url=LocalCatalog.first(o,"url","video","link","stream","stream_url","video_url","play_url","source","src");Button play=new Button(this);play.setAllCaps(false);play.setText("▶  Assistir agora");play.setTextSize(18);play.setTextColor(0xffffffff);play.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xffe6384b));root.addView(play,new LinearLayout.LayoutParams(-1,dp(58)));
 play.setEnabled(url.startsWith("http"));if(!url.startsWith("http"))play.setText(SeriesHelper.isSeries(o)?"Selecione um episódio abaixo":"Vídeo indisponível");play.setOnClickListener(v->{Intent i=new Intent(this,PlayerActivity.class);i.putExtra("url",url);i.putExtra("name",o.optString("nome"));i.putExtra("capa",o.optString("capa",o.optString("backdrop","")));i.putExtra("adult",AdultProtection.adult(o));attachPlaylist(i,url);AdultProtection.require(this,AdultProtection.adult(o),()->startActivity(i));});
 Button favorite=new Button(this);favorite.setAllCaps(false);String favKey=o.optString("nome");android.content.SharedPreferences prefs=UserMediaState.prefs(this,"reel_favorites");Runnable updateFav=()->favorite.setText(prefs.getBoolean(favKey,false)?"♥  Remover dos favoritos":"♡  Adicionar aos favoritos");updateFav.run();favorite.setOnClickListener(v->{prefs.edit().putBoolean(favKey,!prefs.getBoolean(favKey,false)).apply();updateFav.run();});favorite.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff33333b));favorite.setTextColor(0xffffffff);root.addView(favorite);
 root.addView(sectionTitle("Sinopse"));String synopsis=o.optString("sinopse","").trim();root.addView(text(synopsis.isEmpty()?"Sinopse não informada.":synopsis,15,0xffcbd5e1));
 if(SeriesHelper.isSeries(o))showSeasons(root,o);
 root.addView(sectionTitle("Informações"));root.addView(text("Título: "+o.optString("nome")+"\nCategoria: "+o.optString("categoria")+(episode.isEmpty()?"":"\nEpisódio: "+episode)+"\nFonte: catálogo Reel Séries",14,0xff9ca3af));
 String img=o.optString("capa","");if(img.startsWith("https://")){new Thread(()->{try{HttpURLConnection c=(HttpURLConnection)new URL(img).openConnection();c.setConnectTimeout(5000);c.setReadTimeout(5000);BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=2;try(InputStream in=c.getInputStream()){Bitmap bmp=BitmapFactory.decodeStream(in,null,opts);if(bmp!=null)runOnUiThread(()->{if(!isFinishing())cover.setImageBitmap(bmp);});}c.disconnect();}catch(Exception ignored){}}).start();}
 }
 private TextView sectionTitle(String name){TextView t=text(name,20,0xffffffff);t.setTypeface(null,1);t.setPadding(0,dp(18),0,dp(12));return t;}
 private android.graphics.drawable.GradientDrawable round(int color,int radius){android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
 private final java.util.ArrayList<Runnable> episodeRefreshers=new java.util.ArrayList<>();
 private String watchedKey(String link){return EpisodeWatchState.key(link);}
 @Override protected void onResume(){super.onResume();for(Runnable r:new java.util.ArrayList<>(episodeRefreshers))r.run();}
 private volatile String seriesPlaylistJson=null;
 private volatile java.util.HashMap<String,Integer> seriesPlaylistIndexes=new java.util.HashMap<>();
 private void attachPlaylist(Intent intent,String episodeUrl){
  String json=seriesPlaylistJson;Integer index=seriesPlaylistIndexes.get(episodeUrl);
  if(json!=null&&index!=null){intent.putExtra("playlist",json);intent.putExtra("playlistIndex",index);}
 }
 private synchronized java.util.zip.ZipFile openEpisodeIndex() throws java.io.IOException {
  java.io.File cache=new java.io.File(getCacheDir(),"reel_episode_index_v1.zip");
  if(!cache.exists()||cache.length()==0){
   java.io.File temp=new java.io.File(getCacheDir(),"reel_episode_index_v1.tmp");
   try(InputStream in=getAssets().open("episode_index.zip");java.io.FileOutputStream out=new java.io.FileOutputStream(temp)){
    byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
   }
   if(!temp.renameTo(cache))throw new java.io.IOException("Falha ao preparar índice");
  }
  return new java.util.zip.ZipFile(cache);
 }
 private void showSeasons(LinearLayout root,JSONObject selected){
  TextView heading=sectionTitle("Temporadas e episódios");heading.setTypeface(null,1);root.addView(heading);
  TextView progress=text("Organizando episódios da série...",14,0xffaab6c8);root.addView(progress);
  Spinner seasons=new Spinner(this);root.addView(seasons,new LinearLayout.LayoutParams(-1,dp(52)));
  LinearLayout episodes=new LinearLayout(this);episodes.setOrientation(1);root.addView(episodes);
  new Thread(()->{
   java.util.TreeMap<Integer,java.util.TreeMap<Integer,JSONObject>> grouped=new java.util.TreeMap<>();
   String wanted=SeriesHelper.key(selected);
   try{
    // Índice pré-gerado por série: evita ler e analisar o catálogo inteiro (23 MB) a cada abertura.
    try{
     java.security.MessageDigest md=java.security.MessageDigest.getInstance("SHA-256");
     byte[] digest=md.digest(wanted.getBytes("UTF-8"));StringBuilder hash=new StringBuilder();
     for(int i=0;i<12;i++)hash.append(String.format(java.util.Locale.US,"%02x",digest[i]&255));
     try(java.util.zip.ZipFile archive=openEpisodeIndex()){
     java.util.zip.ZipEntry item=archive.getEntry(hash+".json");
     if(item==null)throw new java.io.FileNotFoundException(hash.toString());
     try(InputStream in=archive.getInputStream(item)){
      ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;
      while((n=in.read(buf))!=-1)out.write(buf,0,n);
      JSONArray arr=new JSONArray(out.toString("UTF-8"));
      for(int i=0;i<arr.length();i++)addEpisode(grouped,wanted,arr.optJSONObject(i));
     }
     }
    }catch(java.io.FileNotFoundException ignored){} // Série não incluída no catálogo original.
    LocalCatalog db=new LocalCatalog(this);
    // Prioritize stable Xtream parent ID; retain title matching for legacy catalog records.
    for(JSONObject v:db.episodesForParent(selected.optString("id","")))addEpisodeByParent(grouped,v);
    for(JSONObject v:db.episodeCandidates(SeriesHelper.name(selected)))addEpisode(grouped,wanted,v);
    runOnUiThread(()->{if(isFinishing())return;progress.setText(grouped.isEmpty()?"Nenhum episódio encontrado.":grouped.size()+" temporada(s) encontrada(s)");
     java.util.ArrayList<Integer> keys=new java.util.ArrayList<>(grouped.keySet());java.util.ArrayList<String> labels=new java.util.ArrayList<>();for(Integer k:keys)labels.add("Temporada "+k+" • "+grouped.get(k).size()+" episódios");
     JSONArray orderedPlaylist=new JSONArray();java.util.HashMap<String,Integer> urlIndexes=new java.util.HashMap<>();
     for(java.util.Map.Entry<Integer,java.util.TreeMap<Integer,JSONObject>> se:grouped.entrySet())for(java.util.Map.Entry<Integer,JSONObject> ep:se.getValue().entrySet()){
      JSONObject obj=ep.getValue();String link=LocalCatalog.first(obj,"url","video","link","stream","stream_url","video_url");if(!link.startsWith("http"))continue;
      JSONObject record=new JSONObject();try{record.put("url",link);record.put("name",SeriesHelper.name(obj)+" • T"+se.getKey()+" E"+ep.getKey());urlIndexes.put(link,orderedPlaylist.length());orderedPlaylist.put(record);}catch(Exception ignored){}
     }
     seriesPlaylistJson=orderedPlaylist.toString();seriesPlaylistIndexes=urlIndexes;
     seasons.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,labels));
     seasons.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){}public void onItemSelected(android.widget.AdapterView<?> p,View v,int index,long id){episodes.removeAllViews();episodeRefreshers.clear();if(index>=keys.size())return;for(java.util.Map.Entry<Integer,JSONObject> entry:grouped.get(keys.get(index)).entrySet()){
      JSONObject item=entry.getValue();String epUrl=LocalCatalog.first(item,"url","video","link","stream","stream_url","video_url");
      LinearLayout line=new LinearLayout(DetailsActivity.this);line.setGravity(Gravity.CENTER_VERTICAL);line.setBackground(round(0xff202027,12));
      TextView row=text("▶  Episódio "+entry.getKey(),16,0xffffffff);row.setPadding(dp(14),dp(12),dp(4),dp(12));line.addView(row,new LinearLayout.LayoutParams(0,-2,1));
      TextView mark=text("",12,0xff9ca3af);mark.setGravity(Gravity.CENTER);mark.setPadding(dp(7),dp(10),dp(7),dp(10));line.addView(mark,new LinearLayout.LayoutParams(dp(110),-2));
      Runnable refresh=()->{boolean done=EpisodeWatchState.isWatched(DetailsActivity.this,epUrl);mark.setText(done?"✓ Assistido":"○ Marcar");mark.setTextColor(done?0xff63e6b0:0xffcbd5e1);};episodeRefreshers.add(refresh);refresh.run();
      mark.setOnClickListener(clickedView->{EpisodeWatchState.setWatched(DetailsActivity.this,epUrl,!EpisodeWatchState.isWatched(DetailsActivity.this,epUrl));refresh.run();});
      LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(9);episodes.addView(line,lp);row.setOnClickListener(x->{String url=LocalCatalog.first(item,"url","video","link","stream","stream_url","video_url");if(!url.startsWith("http")){new AlertDialog.Builder(DetailsActivity.this).setMessage("Vídeo indisponível neste episódio.").setPositiveButton("OK",null).show();return;}Intent intent=new Intent(DetailsActivity.this,PlayerActivity.class);intent.putExtra("url",url);intent.putExtra("name",SeriesHelper.name(item)+" • T"+keys.get(index)+" E"+entry.getKey());intent.putExtra("capa",item.optString("capa",selected.optString("capa","")));
      intent.putExtra("adult",AdultProtection.adult(selected)||AdultProtection.adult(item));attachPlaylist(intent,url);
      AdultProtection.require(DetailsActivity.this,AdultProtection.adult(selected)||AdultProtection.adult(item),()->startActivity(intent));});
     }}});
     int[] selectedNumbers=SeriesHelper.numbers(selected);if(selectedNumbers!=null){int idx=keys.indexOf(selectedNumbers[0]);if(idx>=0)seasons.setSelection(idx);}
    });
   }catch(Exception e){runOnUiThread(()->progress.setText("Não foi possível carregar episódios: "+e.getMessage()));}
  }).start();
 }
 private static void addEpisodeByParent(java.util.TreeMap<Integer,java.util.TreeMap<Integer,JSONObject>> map,JSONObject v){
  if(v==null)return;int[] n=SeriesHelper.numbers(v);if(n==null||n[0]<1||n[1]<1)return;
  java.util.TreeMap<Integer,JSONObject> season=map.computeIfAbsent(n[0],k->new java.util.TreeMap<>());
  JSONObject old=season.get(n[1]);String link=LocalCatalog.first(v,"url","video","link");
  if(old==null||(!LocalCatalog.first(old,"url","video","link").startsWith("http")&&link.startsWith("http")))season.put(n[1],v);
 }
 private static void addEpisode(java.util.TreeMap<Integer,java.util.TreeMap<Integer,JSONObject>> map,String wanted,JSONObject v){if(v==null||!SeriesHelper.key(v).equals(wanted))return;int[] n=SeriesHelper.numbers(v);if(n==null||n[0]<1||n[1]<1)return;java.util.TreeMap<Integer,JSONObject> season=map.computeIfAbsent(n[0],k->new java.util.TreeMap<>());
 JSONObject old=season.get(n[1]);
 String incoming=LocalCatalog.first(v,"url","video","link","stream","stream_url","video_url");
 String existing=old==null?"":LocalCatalog.first(old,"url","video","link","stream","stream_url","video_url");
 if(old==null||(!existing.startsWith("http")&&incoming.startsWith("http")))season.put(n[1],v);}

}
