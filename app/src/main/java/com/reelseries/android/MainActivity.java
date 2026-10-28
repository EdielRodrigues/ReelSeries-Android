package com.reelseries.android;

import android.app.*;import android.os.*;import android.content.*;import android.graphics.*;import android.graphics.drawable.*;import android.view.*;import android.widget.*;import android.text.*;import org.json.*;import java.io.*;import java.util.*;import java.util.concurrent.*;import java.net.*;

public class MainActivity extends Activity {
 final ArrayList<JSONObject> all=new ArrayList<>(), shown=new ArrayList<>();
 final ExecutorService pool=Executors.newFixedThreadPool(3); final android.util.LruCache<String,Bitmap> cache=new android.util.LruCache<String,Bitmap>(40);
 GridView grid; BaseAdapter adapter; TextView status; EditText search; Spinner categories; String selected="Todas";
 int bg=Color.rgb(12,16,27), fg=Color.WHITE; final ArrayList<String> catNames=new ArrayList<>();
 int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
 @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(bg);getWindow().setNavigationBarColor(Color.BLACK);
 LinearLayout layout=new LinearLayout(this);layout.setOrientation(1);layout.setBackgroundColor(bg);layout.setPadding(dp(12),dp(12),dp(12),0);
 TextView title=new TextView(this);title.setText("REEL SÉRIES");title.setTextSize(27);title.setTextColor(Color.rgb(255,68,90));title.setTypeface(null,1);layout.addView(title);
 status=new TextView(this);status.setText("Carregando catálogo...");status.setTextColor(0xffbbbbbb);layout.addView(status);
 search=new EditText(this);search.setSingleLine(true);search.setHint("Pesquisar filmes e séries");search.setTextColor(fg);search.setHintTextColor(0xffaaaaaa);layout.addView(search);
 categories=new Spinner(this);layout.addView(categories);catNames.add("Todas");categories.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,catNames));
 grid=new GridView(this);grid.setNumColumns(2);grid.setVerticalSpacing(dp(12));grid.setHorizontalSpacing(dp(10));grid.setClipToPadding(false);grid.setPadding(0,dp(10),0,dp(12));layout.addView(grid,new LinearLayout.LayoutParams(-1,0,1));setContentView(layout);
 adapter=new BaseAdapter(){public int getCount(){return shown.size();}public Object getItem(int p){return shown.get(p);}public long getItemId(int p){return p;}
 public View getView(int p,View old,android.view.ViewGroup parent){LinearLayout box=new LinearLayout(MainActivity.this);box.setOrientation(1);box.setBackgroundColor(0xff202737);box.setPadding(dp(5),dp(5),dp(5),dp(7));
 ImageView cover=new ImageView(MainActivity.this);cover.setBackgroundColor(0xff30394b);cover.setScaleType(ImageView.ScaleType.CENTER_CROP);box.addView(cover,new LinearLayout.LayoutParams(-1,dp(205)));
 TextView label=new TextView(MainActivity.this);JSONObject item=shown.get(p);label.setText(item.optString("nome"));label.setTextColor(fg);label.setTextSize(13);label.setMaxLines(2);label.setEllipsize(android.text.TextUtils.TruncateAt.END);box.addView(label);
 loadCover(item.optString("capa"),cover);return box;}};grid.setAdapter(adapter);
 grid.setOnItemClickListener((a,v,p,id)->details(shown.get(p)));
 search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int af){}public void onTextChanged(CharSequence s,int st,int before,int count){filter();}public void afterTextChanged(Editable e){}});
 categories.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){}public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){selected=catNames.get(pos);filter();}});
 pool.execute(()->{try{ByteArrayOutputStream out=new ByteArrayOutputStream();InputStream in=getAssets().open("catalogo.json");byte[] buf=new byte[32768];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);in.close();JSONArray arr=new JSONArray(out.toString("UTF-8"));ArrayList<JSONObject> loaded=new ArrayList<>();TreeSet<String> cats=new TreeSet<>();for(int i=0;i<arr.length();i++){JSONObject o=arr.getJSONObject(i);loaded.add(o);cats.add(o.optString("categoria"));}runOnUiThread(()->{all.addAll(loaded);catNames.addAll(cats);((ArrayAdapter)categories.getAdapter()).notifyDataSetChanged();filter();});}catch(Exception e){runOnUiThread(()->status.setText("Erro no catálogo: "+e.getMessage()));}});
 }
 void filter(){String q=search.getText().toString().toLowerCase(java.util.Locale.ROOT).trim();shown.clear();for(JSONObject o:all){if(!selected.equals("Todas")&&!selected.equals(o.optString("categoria")))continue;if(!o.optString("nome").toLowerCase(java.util.Locale.ROOT).contains(q))continue;shown.add(o);if(shown.size()>=300)break;}status.setText(shown.size()+" títulos exibidos • "+all.size()+" registros");adapter.notifyDataSetChanged();}
 void details(JSONObject o){String name=o.optString("nome"),url=o.optString("url");String msg=o.optString("episodio")+"\n\n"+o.optString("sinopse");AlertDialog.Builder b=new AlertDialog.Builder(this).setTitle(name).setMessage(msg).setNegativeButton("Fechar",null);if(url.startsWith("http"))b.setPositiveButton("Reproduzir",(d,w)->{Intent i=new Intent(this,PlayerActivity.class);i.putExtra("url",url);i.putExtra("name",name);startActivity(i);});b.show();}
 void loadCover(String url,ImageView image){image.setTag(url);Bitmap hit=cache.get(url);if(hit!=null){image.setImageBitmap(hit);return;}image.setImageDrawable(null);if(!url.startsWith("https://"))return;pool.execute(()->{try{HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(4500);c.setReadTimeout(5000);c.setInstanceFollowRedirects(true);InputStream in=c.getInputStream();BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=2;Bitmap bitmap=BitmapFactory.decodeStream(in,null,opts);in.close();c.disconnect();if(bitmap!=null){cache.put(url,bitmap);runOnUiThread(()->{if(url.equals(image.getTag()))image.setImageBitmap(bitmap);});}}catch(Exception ignored){}});}
 @Override protected void onDestroy(){pool.shutdownNow();super.onDestroy();}
}
