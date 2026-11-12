package com.reelseries.android;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.net.*;
import java.io.*;
import java.util.*;

public class FavoritesActivity extends Activity {
 private LinearLayout list;
 private final int bg=0xff0c0c10;
 private LocalCatalog catalog;
 private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
 private TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);return t;}
 @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().setStatusBarColor(bg);getWindow().setNavigationBarColor(bg);catalog=new LocalCatalog(this);
  LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(bg);root.setPadding(dp(16),dp(14),dp(16),dp(10));setContentView(root);
  TextView heading=text("‹  MINHA LISTA",24,0xffef4356);heading.setTypeface(null,1);heading.setPadding(0,dp(10),0,dp(18));root.addView(heading);heading.setOnClickListener(v->finish());
  ScrollView scroll=new ScrollView(this);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));list=new LinearLayout(this);list.setOrientation(1);scroll.addView(list);
 }
 @Override protected void onResume(){super.onResume();refresh();}
 private final java.util.concurrent.ExecutorService imagePool=java.util.concurrent.Executors.newFixedThreadPool(3);
 private final java.util.concurrent.ExecutorService queryPool=java.util.concurrent.Executors.newSingleThreadExecutor();
 private final android.util.LruCache<String,Bitmap> posterCache=new android.util.LruCache<String,Bitmap>(32);
 private int refreshId=0;
 private void refresh(){
  final int generation=++refreshId;
  list.removeAllViews();
  SharedPreferences prefs=UserMediaState.prefs(this,"reel_favorites");
  ArrayList<String> names=new ArrayList<>();
  for(Map.Entry<String,?> e:prefs.getAll().entrySet())if(Boolean.TRUE.equals(e.getValue()))names.add(e.getKey());
  Collections.sort(names,String.CASE_INSENSITIVE_ORDER);
  if(names.isEmpty()){
   TextView empty=text("Sua lista está vazia.\nAbra um filme ou série e toque em ♡ Adicionar aos favoritos.",16,0xffb7c2d1);
   empty.setPadding(0,dp(30),0,0);list.addView(empty);return;
  }
  TextView count=text(names.size()+" títulos salvos • carregando...",14,0xffb7c2d1);
  count.setPadding(0,0,0,dp(16));list.addView(count);
  queryPool.execute(()->{
   ArrayList<JSONObject> results=new ArrayList<>();
   for(String name:names){
    JSONObject found=null;
    try{for(JSONObject candidate:catalog.search(name,"Todas",300)){
     if(name.equalsIgnoreCase(candidate.optString("nome"))){found=candidate;break;}
    }}catch(Exception e){android.util.Log.w("ReelFavorites","Busca do favorito falhou",e);}
    results.add(found);
   }
   runOnUiThread(()->{
    if(isFinishing()||isDestroyed()||generation!=refreshId)return;
    list.removeAllViews();
    TextView label=text(names.size()+" títulos salvos",14,0xffb7c2d1);
    label.setPadding(0,0,0,dp(16));list.addView(label);
    for(int i=0;i<names.size();i++)addCard(names.get(i),results.get(i));
   });
  });
 }
 private void addCard(String name,JSONObject item){
  LinearLayout card=new LinearLayout(this);card.setOrientation(0);card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(dp(10),dp(10),dp(10),dp(10));card.setBackground(rounded(0xff202027,12));
  LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.bottomMargin=dp(12);list.addView(card,cp);
  ImageView cover=new ImageView(this);cover.setBackgroundColor(0xff303039);cover.setScaleType(ImageView.ScaleType.CENTER_CROP);card.addView(cover,new LinearLayout.LayoutParams(dp(98),dp(145)));
  LinearLayout info=new LinearLayout(this);info.setOrientation(1);info.setPadding(dp(12),0,0,0);card.addView(info,new LinearLayout.LayoutParams(0,-2,1));
  TextView title=text(name,17,-1);title.setTypeface(null,1);title.setMaxLines(3);info.addView(title);
  if(item!=null){String category=item.optString("categoria","");TextView cat=text(category,13,0xffef6a79);cat.setPadding(0,dp(7),0,dp(5));info.addView(cat);
   String synopsis=item.optString("sinopse","");if(!synopsis.isEmpty()){TextView desc=text(synopsis,12,0xffcbd5e1);desc.setMaxLines(3);info.addView(desc);}
   loadPoster(LocalCatalog.first(item,"capa","tvg-logo","tvg_logo","logo","imagem","image","poster","backdrop"),cover);
   Button details=new Button(this);details.setAllCaps(false);details.setText("▶  Ver detalhes");details.setTextColor(0xffffffff);details.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xffe6384b));info.addView(details);JSONObject selected=item;
   details.setOnClickListener(v->openDetails(selected));card.setOnClickListener(v->openDetails(selected));
  }else{TextView missing=text("Título não encontrado no catálogo local.",12,0xffcbd5e1);info.addView(missing);}
  TextView remove=text("♥  Remover da lista",13,0xffff8195);remove.setPadding(0,dp(10),0,dp(6));info.addView(remove);
  remove.setOnClickListener(v->new AlertDialog.Builder(this).setMessage("Remover "+name+" da Minha Lista?").setNegativeButton("Cancelar",null).setPositiveButton("Remover",(d,w)->{UserMediaState.prefs(this,"reel_favorites").edit().remove(name).apply();refresh();}).show());
 }
 private void openDetails(JSONObject item){AdultProtection.require(this,AdultProtection.adult(item),()->{Intent i=new Intent(this,DetailsActivity.class);i.putExtra("item",item.toString());startActivity(i);});}
 private android.graphics.drawable.GradientDrawable rounded(int color,int radius){android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
 private void loadPoster(String url,ImageView image){
  if(!url.startsWith("https://")&&!url.startsWith("http://"))return;
  image.setTag(url);
  Bitmap cached=posterCache.get(url);
  if(cached!=null){image.setImageBitmap(cached);return;}
  imagePool.execute(()->{
   HttpURLConnection c=null;
   try{
    c=(HttpURLConnection)new URL(url).openConnection();
    c.setConnectTimeout(4500);c.setReadTimeout(5000);c.setInstanceFollowRedirects(true);
    try(InputStream in=c.getInputStream()){
     BitmapFactory.Options opt=new BitmapFactory.Options();opt.inSampleSize=4;
     Bitmap b=BitmapFactory.decodeStream(in,null,opt);
     if(b!=null){posterCache.put(url,b);runOnUiThread(()->{
      if(!isFinishing()&&!isDestroyed()&&url.equals(image.getTag()))image.setImageBitmap(b);
     });}
    }
   }catch(Exception e){android.util.Log.w("ReelFavorites","Falha na capa",e);}
   finally{if(c!=null)c.disconnect();}
  });
 }
 @Override protected void onDestroy(){refreshId++;queryPool.shutdownNow();imagePool.shutdownNow();if(catalog!=null)catalog.close();super.onDestroy();}
}
