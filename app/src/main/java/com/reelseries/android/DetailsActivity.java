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
 root.addView(text("SINOPSE",17,0xffffffff));String synopsis=o.optString("sinopse","").trim();root.addView(text(synopsis.isEmpty()?"Sinopse não informada.":synopsis,15,0xffcbd5e1));
 root.addView(text("INFORMAÇÕES",17,0xffffffff));root.addView(text("Título: "+o.optString("nome")+"\nCategoria: "+o.optString("categoria")+(episode.isEmpty()?"":"\nEpisódio: "+episode)+"\nFonte: catálogo Reel Séries",14,0xff9ca3af));
 String img=o.optString("capa","");if(img.startsWith("https://")){new Thread(()->{try{HttpURLConnection c=(HttpURLConnection)new URL(img).openConnection();c.setConnectTimeout(5000);c.setReadTimeout(5000);BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=2;try(InputStream in=c.getInputStream()){Bitmap bmp=BitmapFactory.decodeStream(in,null,opts);if(bmp!=null)runOnUiThread(()->{if(!isFinishing())cover.setImageBitmap(bmp);});}c.disconnect();}catch(Exception ignored){}}).start();}
 }
}
