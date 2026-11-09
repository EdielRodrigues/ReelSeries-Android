package com.reelseries.android;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.util.*;
import java.net.*;
import java.io.*;

public class ContinueWatchingActivity extends Activity {
 private final ArrayList<JSONObject> items=new ArrayList<>();
 private LinearLayout list;
 private final int bg=0xff0c0c10;
 private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
 private String time(long ms){long s=Math.max(0,ms/1000);return String.format(Locale.getDefault(),"%02d:%02d:%02d",s/3600,(s/60)%60,s%60);}
 @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().setStatusBarColor(bg);LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(bg);root.setPadding(dp(14),dp(12),dp(14),0);setContentView(root);
  TextView title=new TextView(this);title.setText("‹   CONTINUAR ASSISTINDO");title.setTextColor(0xffef4356);title.setTextSize(22);title.setTypeface(null,1);title.setPadding(0,dp(8),0,dp(14));root.addView(title);title.setOnClickListener(v->finish());
  ScrollView scroll=new ScrollView(this);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));list=new LinearLayout(this);list.setOrientation(1);scroll.addView(list);}
 @Override protected void onResume(){super.onResume();refresh();}
 private void refresh(){items.clear();list.removeAllViews();SharedPreferences prefs=getSharedPreferences("reel_continue_history",MODE_PRIVATE);
  for(Map.Entry<String,?> e:prefs.getAll().entrySet()){if(!(e.getValue() instanceof String))continue;try{JSONObject o=new JSONObject((String)e.getValue());if(o.optString("url").startsWith("http")&&o.optLong("position")>=5000)items.add(o);}catch(Exception ignored){}}
  Collections.sort(items,(a,b)->Long.compare(b.optLong("updated"),a.optLong("updated")));
  if(items.isEmpty()){TextView empty=text("Nenhum filme ou episódio em andamento.\nAssista por alguns segundos e volte aqui.",16,0xffb9c3d2);list.addView(empty);return;}
  for(JSONObject item:items)addCard(item);
 }
 private TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);return t;}
 private void addCard(JSONObject item){String name=item.optString("name","Vídeo"),url=item.optString("url"),poster=item.optString("capa","");long pos=item.optLong("position",0),duration=item.optLong("duration",0);
  LinearLayout card=new LinearLayout(this);card.setOrientation(0);card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(dp(9),dp(9),dp(9),dp(9));card.setBackground(rounded(0xff202027,12));LinearLayout.LayoutParams cardParams=new LinearLayout.LayoutParams(-1,-2);cardParams.bottomMargin=dp(10);list.addView(card,cardParams);
  ImageView cover=new ImageView(this);cover.setScaleType(ImageView.ScaleType.CENTER_CROP);cover.setBackgroundColor(0xff303039);card.addView(cover,new LinearLayout.LayoutParams(dp(92),dp(134)));loadPoster(poster,cover);
  LinearLayout right=new LinearLayout(this);right.setOrientation(1);right.setPadding(dp(12),0,0,0);card.addView(right,new LinearLayout.LayoutParams(0,-2,1));TextView title=text(name,16,-1);title.setTypeface(null,1);title.setMaxLines(3);right.addView(title);
  TextView elapsed=text("Parou em: "+time(pos),13,0xffb9c3d2);elapsed.setPadding(0,dp(7),0,dp(3));right.addView(elapsed);
  TextView total=text(duration>0?"Duração total: "+time(duration):"Duração total: indisponível",12,0xffb9c3d2);right.addView(total);
  if(duration>0){ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(1000);bar.setProgress((int)Math.min(1000,pos*1000/duration));bar.setProgressTintList(android.content.res.ColorStateList.valueOf(0xffe6384b));bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff484851));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(6));bp.topMargin=dp(9);right.addView(bar,bp);}
  Button play=new Button(this);play.setText("▶  Continuar");play.setAllCaps(false);play.setTextColor(0xffffffff);play.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xffe6384b));right.addView(play,new LinearLayout.LayoutParams(-1,dp(48)));play.setOnClickListener(v->{Intent i=new Intent(this,PlayerActivity.class);i.putExtra("url",url);i.putExtra("name",name);i.putExtra("capa",poster);i.putExtra("resume_position",pos);i.putExtra("adult",item.optBoolean("adult",false));AdultProtection.require(this,item.optBoolean("adult",false),()->startActivity(i));});
  TextView remove=text("Remover do histórico",12,0xffff8c9b);remove.setGravity(Gravity.RIGHT);remove.setPadding(0,dp(8),0,0);right.addView(remove);remove.setOnClickListener(v->new AlertDialog.Builder(this).setMessage("Remover este título da lista?").setNegativeButton("Cancelar",null).setPositiveButton("Remover",(d,w)->{String key=findKey(url);if(key!=null)prefsRemove(key);refresh();}).show());
 }
 private String findKey(String url){SharedPreferences p=getSharedPreferences("reel_continue_history",MODE_PRIVATE);for(Map.Entry<String,?> e:p.getAll().entrySet())try{if(url.equals(new JSONObject(String.valueOf(e.getValue())).optString("url")))return e.getKey();}catch(Exception ignored){}return null;}
 private void prefsRemove(String key){getSharedPreferences("reel_continue_history",MODE_PRIVATE).edit().remove(key).apply();getSharedPreferences("reel_playback_progress",MODE_PRIVATE).edit().remove(key).apply();}
 private android.graphics.drawable.GradientDrawable rounded(int color,int radius){android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
 private void loadPoster(String url,ImageView image){if(!url.startsWith("https://"))return;new Thread(()->{HttpURLConnection c=null;try{c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(4500);c.setReadTimeout(5000);try(InputStream in=c.getInputStream()){BitmapFactory.Options opt=new BitmapFactory.Options();opt.inSampleSize=2;Bitmap bmp=BitmapFactory.decodeStream(in,null,opt);if(bmp!=null)runOnUiThread(()->{if(!isFinishing())image.setImageBitmap(bmp);});}}catch(Exception ignored){}finally{if(c!=null)c.disconnect();}}).start();}
}
