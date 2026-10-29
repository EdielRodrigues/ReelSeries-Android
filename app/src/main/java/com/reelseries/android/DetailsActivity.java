package com.reelseries.android;
import android.app.*;import android.os.*;import android.content.*;import android.graphics.*;import android.graphics.drawable.*;import android.view.*;import android.widget.*;import org.json.*;import java.net.*;import java.io.*;
public class DetailsActivity extends Activity {
 int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);} TextView text(String s,int size,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setPadding(0,dp(7),0,dp(7));return v;}
 @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(0xff0c101b);getWindow().setNavigationBarColor(0xff0c101b);JSONObject o;try{o=new JSONObject(getIntent().getStringExtra("item"));}catch(Exception e){finish();return;}
 ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(0xff0c101b);LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(20),dp(16),dp(20),dp(28));scroll.addView(root);setContentView(scroll);
 TextView back=text("‹  Voltar ao catálogo",17,0xffe5e7eb);root.addView(back);back.setOnClickListener(v->finish());
 ImageView cover=new ImageView(this);cover.setScaleType(ImageView.ScaleType.FIT_CENTER);cover.setBackgroundColor(0xff202738);root.addView(cover,new LinearLayout.LayoutParams(-1,dp(260)));
 TextView title=text(o.optString("nome","Sem título"),27,0xffffffff);title.setTypeface(null,1);root.addView(title);
 TextView category=text("CATEGORIA  •  "+o.optString("categoria","Não informada"),13,0xffff657a);root.addView(category);
 String episode=o.optString("episodio","").trim();if(!episode.isEmpty())root.addView(text("EPISÓDIO  •  "+episode,14,0xffd1d5db));
 String url=o.optString("url","");Button play=new Button(this);play.setAllCaps(false);play.setText("▶  Assistir agora");play.setTextSize(18);play.setTextColor(0xffffffff);play.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xffe33d59));root.addView(play,new LinearLayout.LayoutParams(-1,dp(58)));
 play.setEnabled(url.startsWith("http"));if(!url.startsWith("http"))play.setText("Vídeo indisponível");play.setOnClickListener(v->{Intent i=new Intent(this,PlayerActivity.class);i.putExtra("url",url);i.putExtra("name",o.optString("nome"));startActivity(i);});
 Button favorite=new Button(this);favorite.setAllCaps(false);String favKey=o.optString("nome");android.content.SharedPreferences prefs=getSharedPreferences("reel_favorites",MODE_PRIVATE);Runnable updateFav=()->favorite.setText(prefs.getBoolean(favKey,false)?"♥  Remover dos favoritos":"♡  Adicionar aos favoritos");updateFav.run();favorite.setOnClickListener(v->{prefs.edit().putBoolean(favKey,!prefs.getBoolean(favKey,false)).apply();updateFav.run();});root.addView(favorite);
 root.addView(text("SINOPSE",17,0xffffffff));String synopsis=o.optString("sinopse","").trim();root.addView(text(synopsis.isEmpty()?"Sinopse não informada.":synopsis,15,0xffcbd5e1));
 if(SeriesHelper.episode(o))showSeasons(root,o);
 root.addView(text("INFORMAÇÕES",17,0xffffffff));root.addView(text("Título: "+o.optString("nome")+"\nCategoria: "+o.optString("categoria")+(episode.isEmpty()?"":"\nEpisódio: "+episode)+"\nFonte: catálogo Reel Séries",14,0xff9ca3af));
 String img=o.optString("capa","");if(img.startsWith("https://")){new Thread(()->{try{HttpURLConnection c=(HttpURLConnection)new URL(img).openConnection();c.setConnectTimeout(5000);c.setReadTimeout(5000);BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=2;try(InputStream in=c.getInputStream()){Bitmap bmp=BitmapFactory.decodeStream(in,null,opts);if(bmp!=null)runOnUiThread(()->{if(!isFinishing())cover.setImageBitmap(bmp);});}c.disconnect();}catch(Exception ignored){}}).start();}
 }
 private void showSeasons(LinearLayout root,JSONObject selected){
  TextView heading=text("TEMPORADAS E EPISÓDIOS",20,0xffffffff);heading.setTypeface(null,1);root.addView(heading);
  TextView progress=text("Organizando episódios da série...",14,0xffaab6c8);root.addView(progress);
  Spinner seasons=new Spinner(this);root.addView(seasons,new LinearLayout.LayoutParams(-1,dp(52)));
  LinearLayout episodes=new LinearLayout(this);episodes.setOrientation(1);root.addView(episodes);
  new Thread(()->{
   java.util.TreeMap<Integer,java.util.TreeMap<Integer,JSONObject>> grouped=new java.util.TreeMap<>();
   String wanted=SeriesHelper.key(selected);
   try{
    try(InputStream in=getAssets().open("catalogo.json")){byte[] buf=new byte[32768];ByteArrayOutputStream out=new ByteArrayOutputStream();int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);JSONArray arr=new JSONArray(out.toString("UTF-8"));for(int i=0;i<arr.length();i++){JSONObject v=arr.optJSONObject(i);addEpisode(grouped,wanted,v);}}
    LocalCatalog db=new LocalCatalog(this);int count=db.count();for(int offset=0;offset<count;offset+=250){for(JSONObject v:db.page(offset,250))addEpisode(grouped,wanted,v);}
    runOnUiThread(()->{if(isFinishing())return;progress.setText(grouped.isEmpty()?"Nenhum episódio encontrado.":grouped.size()+" temporada(s) encontrada(s)");
     java.util.ArrayList<Integer> keys=new java.util.ArrayList<>(grouped.keySet());java.util.ArrayList<String> labels=new java.util.ArrayList<>();for(Integer k:keys)labels.add("Temporada "+k+" • "+grouped.get(k).size()+" episódios");
     seasons.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,labels));
     seasons.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){}public void onItemSelected(android.widget.AdapterView<?> p,View v,int index,long id){episodes.removeAllViews();if(index>=keys.size())return;for(java.util.Map.Entry<Integer,JSONObject> entry:grouped.get(keys.get(index)).entrySet()){
      JSONObject item=entry.getValue();TextView row=text("▶  Episódio "+entry.getKey(),16,0xffffffff);row.setPadding(dp(14),dp(12),dp(14),dp(12));row.setBackgroundColor(0xff1d2838);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(6);episodes.addView(row,lp);row.setOnClickListener(x->{String url=item.optString("url",item.optString("video",""));if(!url.startsWith("http")){new AlertDialog.Builder(DetailsActivity.this).setMessage("Vídeo indisponível neste episódio.").setPositiveButton("OK",null).show();return;}Intent intent=new Intent(DetailsActivity.this,PlayerActivity.class);intent.putExtra("url",url);intent.putExtra("name",SeriesHelper.name(item)+" • T"+keys.get(index)+" E"+entry.getKey());startActivity(intent);});
     }}});
     int wantedSeason=SeriesHelper.numbers(selected)[0];int idx=keys.indexOf(wantedSeason);if(idx>=0)seasons.setSelection(idx);
    });
   }catch(Exception e){runOnUiThread(()->progress.setText("Não foi possível carregar episódios: "+e.getMessage()));}
  }).start();
 }
 private static void addEpisode(java.util.TreeMap<Integer,java.util.TreeMap<Integer,JSONObject>> map,String wanted,JSONObject v){if(v==null||!SeriesHelper.key(v).equals(wanted))return;int[] n=SeriesHelper.numbers(v);if(n==null||n[0]<1||n[1]<1)return;map.computeIfAbsent(n[0],k->new java.util.TreeMap<>()).put(n[1],v);}

}
