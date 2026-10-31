package com.reelseries.android;

import android.app.*;import android.os.*;import android.content.*;import android.graphics.*;import android.graphics.drawable.*;import android.view.*;import android.widget.*;import android.text.*;import org.json.*;import java.io.*;import java.util.*;import java.util.concurrent.*;import java.net.*;

public class MainActivity extends Activity {
 final ArrayList<JSONObject> all=new ArrayList<>(), shown=new ArrayList<>(); final java.util.HashMap<String,JSONObject> cloud=new java.util.HashMap<>();
 final ExecutorService pool=Executors.newFixedThreadPool(3); final android.util.LruCache<String,Bitmap> cache=new android.util.LruCache<String,Bitmap>(16);
 GridView grid; BaseAdapter adapter; TextView status; EditText search; Spinner categories; String selected="Todas", mode="Todos"; int pageOffset=0; boolean loadingPage=false, endReached=false, bundledPaging=false; TextView moreButton; final ArrayList<JSONObject> localPages=new ArrayList<>(); int requestVersion=0; android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper()); Runnable pending;
 int bg=Color.rgb(12,16,27), fg=Color.WHITE; final ArrayList<String> catNames=new ArrayList<>();
 int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
 @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(bg);getWindow().setNavigationBarColor(Color.BLACK);
 LinearLayout layout=new LinearLayout(this);layout.setOrientation(1);layout.setBackgroundColor(bg);
 LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(16),dp(10),dp(16),dp(10));layout.addView(header);
 TextView menu=navText("☰",25);header.addView(menu,new LinearLayout.LayoutParams(dp(44),dp(48)));menu.setOnClickListener(v->new AlertDialog.Builder(this).setItems(new String[]{"Início","Filmes","Séries","Canais IPTV","Meu perfil","Administração"},(d,w)->{if(w==4)startActivity(new Intent(this,ProfileActivity.class));else if(w==5)startActivity(new Intent(this,AdminActivity.class));else chooseCategory(w==0?"Todos":w==1?"Filmes":w==2?"Séries":"TV / IPTV");}).show());
 TextView brand=navText("▣  REEL\n     SÉRIES",18);brand.setTextColor(0xfff04458);brand.setTypeface(null,1);header.addView(brand,new LinearLayout.LayoutParams(0,dp(58),1));
 TextView searchIcon=navText("⌕",30);header.addView(searchIcon,new LinearLayout.LayoutParams(dp(44),dp(48)));searchIcon.setOnClickListener(v->{search.requestFocus();((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(search,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);});
 TextView profile=navText("♟",25);header.addView(profile,new LinearLayout.LayoutParams(dp(44),dp(48)));profile.setOnClickListener(v->startActivity(new Intent(this,ProfileActivity.class)));
 TextView admin=navText("⚙",25);header.addView(admin,new LinearLayout.LayoutParams(dp(44),dp(48)));admin.setOnClickListener(v->startActivity(new Intent(this,AdminActivity.class)));
 ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);layout.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));LinearLayout content=new LinearLayout(this);content.setOrientation(1);content.setPadding(dp(15),0,dp(15),dp(14));scroll.addView(content);
 android.widget.FrameLayout hero=new android.widget.FrameLayout(this);hero.setBackgroundColor(0xff24202a);content.addView(hero,new LinearLayout.LayoutParams(-1,dp(330)));
 ImageView heroImage=new ImageView(this);heroImage.setScaleType(ImageView.ScaleType.CENTER_CROP);hero.addView(heroImage,new android.widget.FrameLayout.LayoutParams(-1,-1));
 View overlay=new View(this);overlay.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x150b101b,0x660b101b,0xff0b101b}));hero.addView(overlay,new android.widget.FrameLayout.LayoutParams(-1,-1));
 LinearLayout heroCopy=new LinearLayout(this);heroCopy.setOrientation(1);heroCopy.setPadding(dp(14),dp(10),dp(14),dp(16));android.widget.FrameLayout.LayoutParams hp=new android.widget.FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);hero.addView(heroCopy,hp);
 TextView badge=navText("DESTAQUE",13);badge.setTypeface(null,1);badge.setTextColor(0xffff455e);heroCopy.addView(badge);
 TextView heroTitle=navText("REEL SÉRIES",32);heroTitle.setTypeface(null,1);heroTitle.setMaxLines(2);heroCopy.addView(heroTitle);
 TextView heroDescription=navText("Seu catálogo de filmes, séries e canais em um só lugar.",14);heroDescription.setTextColor(0xffc6c9d2);heroDescription.setMaxLines(3);heroCopy.addView(heroDescription);
 LinearLayout actions=new LinearLayout(this);actions.setPadding(0,dp(8),0,0);heroCopy.addView(actions);
 TextView watch=actionText("▶  Assistir",true);actions.addView(watch,new LinearLayout.LayoutParams(0,dp(49),1));TextView more=actionText("ⓘ Informações",false);LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(0,dp(49),1);mp.leftMargin=dp(8);actions.addView(more,mp);
 final JSONObject[] featured={null};watch.setOnClickListener(v->{if(featured[0]!=null){String url=featured[0].optString("url",featured[0].optString("video"));if(url.startsWith("http")){Intent i=new Intent(this,PlayerActivity.class);i.putExtra("url",url);i.putExtra("name",featured[0].optString("nome"));startActivity(i);}else details(featured[0]);}});more.setOnClickListener(v->{if(featured[0]!=null)details(featured[0]);});
 TextView categoryTitle=navText("CATÁLOGO COMPLETO",12);categoryTitle.setTextColor(0xffff455e);categoryTitle.setTypeface(null,1);categoryTitle.setPadding(0,dp(20),0,dp(8));content.addView(categoryTitle);
 LinearLayout chips=new LinearLayout(this);chips.setOrientation(0);HorizontalScrollView chipScroll=new HorizontalScrollView(this);chipScroll.setHorizontalScrollBarEnabled(false);chipScroll.addView(chips);content.addView(chipScroll);
 String[] labels={"Todos","Filmes","Séries","TV / IPTV"};String[] queries={"Todos","Filmes","Séries","TV / IPTV"};for(int k=0;k<labels.length;k++){final String q=queries[k];TextView chip=actionText(labels[k],false);chip.setPadding(dp(16),dp(10),dp(16),dp(10));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,dp(43));cp.rightMargin=dp(7);chips.addView(chip,cp);chip.setOnClickListener(v->chooseCategory(q));}
 search=new EditText(this);search.setSingleLine(true);search.setHint("Pesquisar filme, série ou episódio");search.setTextColor(fg);search.setHintTextColor(0xff9fa5b3);search.setBackgroundColor(0xff171e2a);search.setPadding(dp(12),0,dp(12),0);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(48));sp.topMargin=dp(14);content.addView(search,sp);
 LinearLayout sort=new LinearLayout(this);sort.setGravity(Gravity.CENTER_VERTICAL);sort.setPadding(0,dp(10),0,0);content.addView(sort);TextView heading=navText("Em destaque",23);heading.setTypeface(null,1);sort.addView(heading,new LinearLayout.LayoutParams(0,-2,1));categories=new Spinner(this);sort.addView(categories,new LinearLayout.LayoutParams(dp(155),dp(48)));catNames.add("Todas");categories.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,catNames));
 status=navText("Carregando catálogo...",12);status.setTextColor(0xffaeb6c7);content.addView(status);
 grid=new GridView(this);grid.setNumColumns(3);grid.setVerticalSpacing(dp(12));grid.setHorizontalSpacing(dp(9));grid.setClipToPadding(false);grid.setPadding(0,dp(10),0,dp(12));grid.setNestedScrollingEnabled(true);content.addView(grid,new LinearLayout.LayoutParams(-1,dp(460)));moreButton=actionText("CARREGAR MAIS TÍTULOS  ↓",false);LinearLayout.LayoutParams moreParams=new LinearLayout.LayoutParams(-1,dp(52));moreParams.topMargin=dp(10);content.addView(moreButton,moreParams);moreButton.setOnClickListener(v->loadNextPage());setContentView(layout);
 LinearLayout bottom=new LinearLayout(this);bottom.setGravity(Gravity.CENTER);bottom.setBackgroundColor(0xff101622);layout.addView(bottom,new LinearLayout.LayoutParams(-1,dp(62)));
 String[] tabs={"⌂\nInício","▶\nFilmes","▣\nSéries","▤\nLista","⌕\nBuscar"};for(int t=0;t<tabs.length;t++){final int idx=t;TextView tab=navText(tabs[t],12);tab.setGravity(Gravity.CENTER);bottom.addView(tab,new LinearLayout.LayoutParams(0,-1,1));tab.setOnClickListener(v->{if(idx==4){search.requestFocus();((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(search,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);}else chooseCategory(idx==0?"Todos":idx==1?"Filmes":idx==2?"Séries":"TV / IPTV");});}
 adapter=new BaseAdapter(){public int getCount(){return shown.size();}public Object getItem(int p){return shown.get(p);}public long getItemId(int p){return p;}
 public View getView(int p,View old,android.view.ViewGroup parent){
 LinearLayout box;ImageView cover;TextView label;
 if(old instanceof LinearLayout && ((LinearLayout)old).getChildCount()==2){box=(LinearLayout)old;cover=(ImageView)box.getChildAt(0);label=(TextView)box.getChildAt(1);}
 else{box=new LinearLayout(MainActivity.this);box.setOrientation(1);box.setBackgroundColor(0xff1c2432);box.setPadding(dp(5),dp(5),dp(5),dp(7));cover=new ImageView(MainActivity.this);cover.setBackgroundColor(0xff30394b);cover.setScaleType(ImageView.ScaleType.CENTER_CROP);box.addView(cover,new LinearLayout.LayoutParams(-1,dp(165)));label=new TextView(MainActivity.this);label.setTextColor(fg);label.setTextSize(12);label.setMaxLines(2);label.setEllipsize(android.text.TextUtils.TruncateAt.END);box.addView(label);}
 JSONObject item=shown.get(p);label.setText(item.optString("nome"));loadCover(item.optString("capa"),cover);return box;
 }};grid.setAdapter(adapter);
 grid.setOnItemClickListener((a,v,p,id)->details(shown.get(p)));
 search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int af){}public void onTextChanged(CharSequence s,int st,int before,int count){if(pending!=null)handler.removeCallbacks(pending);pending=()->resetFeed();handler.postDelayed(pending,220);}public void afterTextChanged(Editable e){}});
 categories.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){}public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){selected=catNames.get(pos);resetFeed();}});
 pool.execute(()->{try{ByteArrayOutputStream out=new ByteArrayOutputStream();InputStream in=getAssets().open("catalogo.json");byte[] buf=new byte[32768];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);in.close();JSONArray arr=new JSONArray(out.toString("UTF-8"));ArrayList<JSONObject> loaded=new ArrayList<>();TreeSet<String> cats=new TreeSet<>();for(int i=0;i<arr.length();i++){JSONObject o=arr.getJSONObject(i);loaded.add(o);cats.add(o.optString("categoria"));}runOnUiThread(()->{all.addAll(loaded);catNames.addAll(cats);if(!loaded.isEmpty()){JSONObject item=loaded.get(0);featured[0]=item;heroTitle.setText(item.optString("nome","REEL SÉRIES"));heroDescription.setText(item.optString("sinopse","Assista a filmes e séries."));loadCover(item.optString("capa"),heroImage);}((ArrayAdapter)categories.getAdapter()).notifyDataSetChanged();resetFeed();});}catch(Exception e){runOnUiThread(()->status.setText("Erro no catálogo: "+e.getMessage()));}});
 }
 TextView navText(String value,int size){TextView v=new TextView(this);v.setText(value);v.setTextColor(Color.WHITE);v.setTextSize(size);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
 TextView actionText(String value,boolean primary){TextView v=navText(value,14);v.setGravity(Gravity.CENTER);v.setTypeface(null,1);v.setTextColor(primary?0xff11151d:Color.WHITE);android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(primary?Color.WHITE:0xff30333c);bg.setCornerRadius(dp(12));v.setBackground(bg);return v;}
 void chooseCategory(String q){mode=q;selected="Todas";if(categories!=null&&categories.getSelectedItemPosition()!=0)categories.setSelection(0);resetFeed();}
 // Persistent SQLite cache: check at most once per 24h, never redownload on every open.
 final java.util.concurrent.atomic.AtomicBoolean cloudBusy=new java.util.concurrent.atomic.AtomicBoolean(false);
 static final long CLOUD_INTERVAL_MS=24L*60*60*1000;
 void loadCloud(){
  if(!FirebaseCatalogSync.configured(this)||!cloudBusy.compareAndSet(false,true))return;
  pool.execute(()->{
   try{
    android.content.SharedPreferences pref=getSharedPreferences("reel_cloud_cache_v194",MODE_PRIVATE);
    long now=System.currentTimeMillis(),last=pref.getLong("last_check",0);
    // Also handle clock changes and future timestamps safely.
    if(last>0 && now>=last && now-last<CLOUD_INTERVAL_MS)return;
    String revision=FirebaseCatalogSync.revision(this);
    String previous=pref.getString("revision","");
    boolean first=!pref.getBoolean("download_complete",false);
    if(!first && revision.equals(previous)){
     pref.edit().putLong("last_check",System.currentTimeMillis()).apply();return;
    }
    runOnUiThread(()->{if(status!=null)status.setText("Atualizando catálogo do Firebase...");});
    int n;
    java.util.ArrayList<String> cats;
    try(LocalCatalog db=new LocalCatalog(this)){
     n=FirebaseCatalogSync.download(this,db,c->runOnUiThread(()->{if(status!=null)status.setText("Salvando no celular: "+c+" títulos");}));
     cats=db.categories();
    }
    // Commit the timestamp ONLY after a successful complete download.
    pref.edit().putString("revision",revision).putBoolean("download_complete",true)
     .putLong("last_check",System.currentTimeMillis()).apply();
    runOnUiThread(()->{
     if(isFinishing()||categories==null)return;
     for(String c:cats)if(!catNames.contains(c))catNames.add(c);
     ((ArrayAdapter)categories.getAdapter()).notifyDataSetChanged();
     resetFeed();
     if(status!=null)status.setText("Catálogo salvo no celular • "+n+" registros sincronizados");
    });
   }catch(Exception e){runOnUiThread(()->{if(status!=null)status.setText("Catálogo local disponível • atualização pendente: "+e.getMessage());});}
   finally{cloudBusy.set(false);}
  });
 }

 void resetFeed(){if(search==null||grid==null||adapter==null)return;requestVersion++;pageOffset=0;endReached=false;loadingPage=false;bundledPaging=false;localPages.clear();shown.clear();adapter.notifyDataSetChanged();updateGridHeight();loadNextPage();}
 boolean matches(JSONObject o,String query){String name=o.optString("nome","");String cat=o.optString("categoria","");String c=cat.toLowerCase(java.util.Locale.ROOT);String n=name.toLowerCase(java.util.Locale.ROOT);if(!n.contains(query))return false;if(!selected.equals("Todas")&&!selected.equals(cat))return false;boolean tv=c.contains("iptv")||c.contains("canal")||c.contains("tv ao vivo")||c.equals("tv")||c.contains("televis");boolean series=SeriesHelper.isSeries(o);if(mode.equals("TV / IPTV"))return tv;if(mode.equals("Séries"))return series&&!tv;if(mode.equals("Filmes"))return !tv&&!series&&(c.contains("filme")||c.contains("movie")||c.contains("cinema"));return true;}
 void loadNextPage(){
  if(loadingPage||endReached||pool.isShutdown())return;
  if(mode.equals("Séries") && !all.isEmpty()){loadSeriesPage();return;}
  loadingPage=true;final int version=requestVersion,offset=pageOffset;
  final String q=search.getText().toString().toLowerCase(java.util.Locale.ROOT).trim(),m=mode,cat=selected;
  moreButton.setVisibility(View.VISIBLE);moreButton.setText("CARREGANDO TÍTULOS...");
  pool.execute(()->{
   try{
    ArrayList<JSONObject> page=new ArrayList<>();boolean useBundled=bundledPaging;
    if(!useBundled){try(LocalCatalog db=new LocalCatalog(this)){page=db.feed(q,m,cat,offset,60);}
     if(offset==0 && page.isEmpty() && !all.isEmpty())useBundled=true;}
    boolean hasMore;
    if(useBundled){
     page.clear();int found=0;
     for(JSONObject item:all){if(!matches(item,q))continue;
      if(found++<offset)continue;
      if(page.size()<60)page.add(item);else break;
     }
     hasMore=found>offset+page.size();
    }else hasMore=page.size()==60;
    final ArrayList<JSONObject> result=page;final boolean next=hasMore,fromBundled=useBundled;
    runOnUiThread(()->{if(version!=requestVersion)return;
     bundledPaging=fromBundled;pageOffset=offset+result.size();localPages.addAll(result);
     endReached=!next;loadingPage=false;filter();
     moreButton.setVisibility(endReached?View.GONE:View.VISIBLE);
     moreButton.setText("CARREGAR MAIS 60 TÍTULOS  ↓");
    });
   }catch(Exception ex){runOnUiThread(()->{if(version==requestVersion){loadingPage=false;status.setText("Erro: "+ex.getMessage());moreButton.setText("TENTAR NOVAMENTE");}});}
  });
 }
 void loadSeriesPage(){
  if(loadingPage||endReached)return;loadingPage=true;
  final int version=requestVersion,offset=pageOffset;
  final String q=search.getText().toString().toLowerCase(java.util.Locale.ROOT).trim(),cat=selected;
  moreButton.setText("CARREGANDO SÉRIES...");
  pool.execute(()->{
   ArrayList<JSONObject> page=new ArrayList<>();HashSet<String> seen=new HashSet<>();int skipped=0;
   for(JSONObject o:all){
    if(!SeriesHelper.isSeries(o))continue;
    String key=SeriesHelper.key(o);if(key.isEmpty()||!seen.add(key))continue;
    if(!cat.equals("Todas")&&!cat.equals(o.optString("categoria")))continue;
    if(!key.contains(q))continue;
    if(skipped++<offset)continue;
    if(page.size()<60)page.add(o);else break;
   }
   final boolean hasMore=skipped>offset+page.size();
   runOnUiThread(()->{if(version!=requestVersion)return;
    localPages.addAll(page);pageOffset=offset+page.size();
    endReached=!hasMore;loadingPage=false;filter();
    moreButton.setVisibility(endReached?View.GONE:View.VISIBLE);
    moreButton.setText("PRÓXIMAS 60 SÉRIES  ↓");
   });
  });
 }
 void refreshLocal(){resetFeed();}
 void filter(){if(adapter==null)return;String q=search.getText().toString().toLowerCase(java.util.Locale.ROOT).trim();shown.clear();java.util.HashSet<String> ids=new java.util.HashSet<>(),grouped=new java.util.HashSet<>();ArrayList<JSONObject> combined=new ArrayList<>(localPages);if(combined.isEmpty() && pageOffset==0){int added=0;for(JSONObject fallback:all){if(matches(fallback,q)){combined.add(fallback);if(++added>=60)break;}}}for(JSONObject o:combined){if(!matches(o,q))continue;String id=o.optString("id","");if(!id.isEmpty()&&!ids.add(id))continue;if(SeriesHelper.isSeries(o)){String k=SeriesHelper.key(o);if(!grouped.add(k))continue;}shown.add(o);}status.setText(shown.size()+" títulos exibidos • "+all.size()+" no APK");adapter.notifyDataSetChanged();updateGridHeight();}
 void updateGridHeight(){
  if(grid==null)return;
  int rows=Math.max(1,(shown.size()+2)/3);
  int height=dp(10)+rows*dp(215)+Math.max(0,rows-1)*dp(12)+dp(12);
  android.view.ViewGroup.LayoutParams params=grid.getLayoutParams();
  if(params!=null&&params.height!=height){params.height=height;grid.setLayoutParams(params);}
 }
 @Override protected void onResume(){super.onResume();if(search!=null){resetFeed();if(FirebaseCatalogSync.configured(this))loadCloud();}}
 void details(JSONObject o){Intent i=new Intent(this,DetailsActivity.class);i.putExtra("item",o.toString());startActivity(i);}
 void loadCover(String url,ImageView image){if(url.equals(image.getTag()) && image.getDrawable()!=null)return;image.setTag(url);Bitmap hit=cache.get(url);if(hit!=null){image.setImageBitmap(hit);return;}image.setImageDrawable(null);if(!url.startsWith("https://"))return;pool.execute(()->{try{HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(4500);c.setReadTimeout(5000);c.setInstanceFollowRedirects(true);InputStream in=c.getInputStream();BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=4;Bitmap bitmap=BitmapFactory.decodeStream(in,null,opts);in.close();c.disconnect();if(bitmap!=null){cache.put(url,bitmap);runOnUiThread(()->{if(url.equals(image.getTag()))image.setImageBitmap(bitmap);});}}catch(Exception ignored){}});}
 @Override protected void onDestroy(){pool.shutdownNow();super.onDestroy();}
}
