package com.reelseries.android;

import android.app.*;import android.os.*;import android.content.*;import android.graphics.*;import android.graphics.drawable.*;import android.view.*;import android.widget.*;import android.text.*;import org.json.*;import java.io.*;import java.util.*;import java.util.concurrent.*;import java.net.*;

public class MainActivity extends Activity {
 final ArrayList<JSONObject> all=new ArrayList<>(), shown=new ArrayList<>(); final java.util.HashMap<String,JSONObject> cloud=new java.util.HashMap<>();
 final ExecutorService pool=Executors.newFixedThreadPool(3); final android.util.LruCache<String,Bitmap> cache=new android.util.LruCache<String,Bitmap>(16);
 GridView grid; BaseAdapter adapter; TextView status; EditText search; Spinner categories; String selected="Todas", mode="Todos", channelGroup=""; int pageOffset=0; boolean loadingPage=false, endReached=false, bundledPaging=false; TextView moreButton, previousButton, pageLabel; int currentPage=0; static final int PAGE_SIZE=300; final ArrayList<JSONObject> localPages=new ArrayList<>(); int requestVersion=0; int localCatalogCount=0; boolean catalogLoadedOnce=false; android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper()); Runnable pending;
 final ArrayList<JSONObject> heroItems=new ArrayList<>(); int heroIndex=0; boolean heroActive=false;
 LinearLayout continueSectionView,continueCardsView;
 Runnable heroTicker; android.widget.LinearLayout heroDots;
 // Ten different highlights every 30 minutes; six-second slide interval.
 static final long HERO_ROTATE_MS=30L*60L*1000L;
 static final long HERO_SLIDE_MS=6000L;
 final ArrayList<JSONObject> heroCandidates=new ArrayList<>();
 long heroBatchStart=0L;
 Runnable heroBatchTicker;

 int bg=Color.rgb(10,10,13), fg=Color.WHITE; final ArrayList<String> catNames=new ArrayList<>();
 int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
 @Override public void onCreate(Bundle b){super.onCreate(b);checkApkUpdate();getWindow().setStatusBarColor(bg);getWindow().setNavigationBarColor(0xff0b0b0e);
 LinearLayout layout=new LinearLayout(this);layout.setOrientation(1);layout.setBackgroundColor(bg);
 LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(12),dp(8),dp(12),dp(8));layout.addView(header);
 TextView menu=navText("☰",25);header.addView(menu,new LinearLayout.LayoutParams(dp(39),dp(48)));menu.setOnClickListener(v->showChannelSidebar());
 TextView brand=navText("REEL  SÉRIES",19);brand.setSingleLine(true);brand.setEllipsize(android.text.TextUtils.TruncateAt.END);brand.setGravity(Gravity.CENTER_VERTICAL);brand.setTextColor(0xfff04458);brand.setTypeface(null,1);header.addView(brand,new LinearLayout.LayoutParams(0,dp(48),1));
 TextView searchIcon=navText("⌕",30);header.addView(searchIcon,new LinearLayout.LayoutParams(dp(39),dp(48)));searchIcon.setOnClickListener(v->{search.requestFocus();((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(search,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);});
 TextView profile=navText("◉",24);header.addView(profile,new LinearLayout.LayoutParams(dp(39),dp(48)));profile.setOnClickListener(v->startActivity(new Intent(this,ProfileActivity.class)));
 TextView admin=navText("⚙",22);header.addView(admin,new LinearLayout.LayoutParams(dp(39),dp(48)));admin.setOnClickListener(v->startActivity(new Intent(this,AdminActivity.class)));
 ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);layout.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));LinearLayout content=new LinearLayout(this);content.setOrientation(1);content.setPadding(dp(15),0,dp(15),dp(14));scroll.addView(content);
 android.widget.FrameLayout hero=new android.widget.FrameLayout(this);hero.setBackgroundColor(0xff19191f);content.addView(hero,new LinearLayout.LayoutParams(-1,dp(390)));
 ImageView heroImage=new ImageView(this);heroImage.setScaleType(ImageView.ScaleType.CENTER_CROP);hero.addView(heroImage,new android.widget.FrameLayout.LayoutParams(-1,-1));
 View overlay=new View(this);overlay.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x120b0b0e,0x770b0b0e,0xff0b0b0e}));hero.addView(overlay,new android.widget.FrameLayout.LayoutParams(-1,-1));
 LinearLayout heroCopy=new LinearLayout(this);heroCopy.setOrientation(1);heroCopy.setPadding(dp(14),dp(10),dp(14),dp(16));android.widget.FrameLayout.LayoutParams hp=new android.widget.FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);hero.addView(heroCopy,hp);
 TextView badge=navText("●  EM DESTAQUE",12);badge.setTypeface(null,1);badge.setTextColor(0xffff455e);heroCopy.addView(badge);
 TextView heroTitle=navText("REEL SÉRIES",32);heroTitle.setTypeface(null,1);heroTitle.setMaxLines(2);heroCopy.addView(heroTitle);
 TextView heroDescription=navText("Seu catálogo de filmes, séries e canais em um só lugar.",14);heroDescription.setTextColor(0xffc6c9d2);heroDescription.setMaxLines(3);heroCopy.addView(heroDescription);
 LinearLayout actions=new LinearLayout(this);actions.setPadding(0,dp(8),0,0);heroCopy.addView(actions);
 TextView watch=actionText("▶  Assistir",true);actions.addView(watch,new LinearLayout.LayoutParams(0,dp(49),1));TextView more=actionText("ⓘ Detalhes",false);LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(0,dp(49),1);mp.leftMargin=dp(8);actions.addView(more,mp);
 final JSONObject[] featured={null};watch.setOnClickListener(v->{if(featured[0]!=null){AdultProtection.require(this,AdultProtection.adult(featured[0]),()->{String url=featured[0].optString("url",featured[0].optString("video"));if(url.startsWith("http")){Intent i=new Intent(this,PlayerActivity.class);i.putExtra("url",url);i.putExtra("name",featured[0].optString("nome"));i.putExtra("adult",AdultProtection.adult(featured[0]));startActivity(i);}else details(featured[0]);});}});more.setOnClickListener(v->{if(featured[0]!=null)details(featured[0]);});
 heroDots=new LinearLayout(this);heroDots.setGravity(Gravity.CENTER);heroDots.setPadding(0,dp(8),0,dp(2));content.addView(heroDots,new LinearLayout.LayoutParams(-1,dp(25)));
 final float[] touchX={0};hero.setOnTouchListener((v,e)->{if(e.getAction()==android.view.MotionEvent.ACTION_DOWN){touchX[0]=e.getX();return true;}if(e.getAction()==android.view.MotionEvent.ACTION_UP){float delta=e.getX()-touchX[0];if(Math.abs(delta)>dp(45)){showHero((heroIndex+(delta<0?1:-1)+heroItems.size())%Math.max(1,heroItems.size()),heroImage,heroTitle,heroDescription,featured);return true;}return false;}return true;});
 heroTicker=new Runnable(){public void run(){if(!heroActive)return;refreshHeroBatchIfNeeded(heroImage,heroTitle,heroDescription,featured);if(heroItems.size()>1){showHero((heroIndex+1)%heroItems.size(),heroImage,heroTitle,heroDescription,featured);}}};
 heroBatchTicker=new Runnable(){public void run(){if(!heroActive)return;refreshHeroBatchIfNeeded(heroImage,heroTitle,heroDescription,featured);scheduleHeroBatchCheck();}};
 LinearLayout continueSection=new LinearLayout(this);continueSection.setOrientation(1);
 LinearLayout.LayoutParams cs=new LinearLayout.LayoutParams(-1,-2);cs.topMargin=dp(8);content.addView(continueSection,cs);
 TextView continueHeading=navText("Continuar assistindo   ›",20);continueHeading.setTypeface(null,1);continueHeading.setPadding(0,dp(5),0,dp(9));continueSection.addView(continueHeading);
 continueHeading.setOnClickListener(v->startActivity(new Intent(this,ContinueWatchingActivity.class)));
 LinearLayout continueCards=new LinearLayout(this);continueCards.setOrientation(0);continueSection.addView(continueCards);
 continueSectionView=continueSection;continueCardsView=continueCards;refreshContinueCards(continueSection,continueCards);
 search=new EditText(this);search.setSingleLine(true);search.setHint("Pesquisar filme, série ou episódio");search.setTextColor(fg);search.setHintTextColor(0xff9fa5b3);search.setBackground(rounded(0xff1b1b22,12));search.setPadding(dp(12),0,dp(12),0);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(48));sp.topMargin=dp(20);content.addView(search,sp);
 LinearLayout sort=new LinearLayout(this);sort.setGravity(Gravity.CENTER_VERTICAL);sort.setPadding(0,dp(10),0,0);content.addView(sort);TextView heading=navText("Explore o catálogo",21);heading.setTypeface(null,1);sort.addView(heading,new LinearLayout.LayoutParams(0,-2,1));categories=new Spinner(this);sort.addView(categories,new LinearLayout.LayoutParams(dp(155),dp(48)));catNames.add("Todas");categories.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,catNames));
 status=navText("Carregando catálogo...",12);status.setTextColor(0xffaeb6c7);content.addView(status);
 grid=new GridView(this);grid.setNumColumns(3);grid.setVerticalSpacing(dp(12));grid.setHorizontalSpacing(dp(9));grid.setClipToPadding(false);grid.setPadding(0,dp(10),0,dp(12));grid.setNestedScrollingEnabled(true);content.addView(grid,new LinearLayout.LayoutParams(-1,dp(460)));moreButton=actionText("CARREGAR MAIS TÍTULOS  ↓",false);LinearLayout.LayoutParams moreParams=new LinearLayout.LayoutParams(-1,dp(52));moreParams.topMargin=dp(10);LinearLayout pager=new LinearLayout(this);pager.setGravity(Gravity.CENTER_VERTICAL);content.addView(pager,moreParams);
  previousButton=actionText("‹ ANTERIOR",false);pager.addView(previousButton,new LinearLayout.LayoutParams(0,dp(52),1));
  pageLabel=navText("Página 1",12);pageLabel.setGravity(Gravity.CENTER);pager.addView(pageLabel,new LinearLayout.LayoutParams(dp(85),dp(52)));
  pager.addView(moreButton,new LinearLayout.LayoutParams(0,dp(52),1));
  previousButton.setOnClickListener(v->openPage(currentPage-1));moreButton.setOnClickListener(v->openPage(currentPage+1));setContentView(layout);
 LinearLayout bottom=new LinearLayout(this);bottom.setGravity(Gravity.CENTER);bottom.setBackgroundColor(0xff15151b);layout.addView(bottom,new LinearLayout.LayoutParams(-1,dp(62)));
 String[] tabs={"⌂\nInício","▶\nFilmes","▣\nSéries","▤\nTV","◉\nPerfil"};for(int t=0;t<tabs.length;t++){final int idx=t;TextView tab=navText(tabs[t],12);tab.setGravity(Gravity.CENTER);tab.setTextColor(idx==0?0xfff04458:0xffb6b6c0);bottom.addView(tab,new LinearLayout.LayoutParams(0,-1,1));tab.setOnClickListener(v->{if(idx==4){startActivity(new Intent(this,ProfileActivity.class));}else chooseCategory(idx==0?"Todos":idx==1?"Filmes":idx==2?"Séries":"TV / IPTV");});}
 adapter=new BaseAdapter(){public int getCount(){return shown.size();}public Object getItem(int p){return shown.get(p);}public long getItemId(int p){return p;}
 public View getView(int p,View old,android.view.ViewGroup parent){
 LinearLayout box;ImageView cover;TextView label;
 if(old instanceof LinearLayout && ((LinearLayout)old).getChildCount()==2){box=(LinearLayout)old;cover=(ImageView)box.getChildAt(0);label=(TextView)box.getChildAt(1);}
 else{box=new LinearLayout(MainActivity.this);box.setOrientation(1);box.setBackground(rounded(0xff202027,12));box.setPadding(dp(5),dp(5),dp(5),dp(7));cover=new ImageView(MainActivity.this);cover.setBackgroundColor(0xff34343e);cover.setScaleType(ImageView.ScaleType.CENTER_CROP);box.addView(cover,new LinearLayout.LayoutParams(-1,dp(165)));label=new TextView(MainActivity.this);label.setTextColor(fg);label.setTextSize(12);label.setMaxLines(2);label.setEllipsize(android.text.TextUtils.TruncateAt.END);box.addView(label);}
 JSONObject item=shown.get(p);label.setText(item.optString("nome"));loadCover(item.optString("capa"),cover);return box;
 }};grid.setAdapter(adapter);
 grid.setOnItemClickListener((a,v,p,id)->details(shown.get(p)));
 search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int af){}public void onTextChanged(CharSequence s,int st,int before,int count){if(pending!=null)handler.removeCallbacks(pending);pending=()->resetFeed();handler.postDelayed(pending,220);}public void afterTextChanged(Editable e){}});
 categories.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){}public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){selected=catNames.get(pos);resetFeed();}});
 // Somente o banco SQLite sincronizado do Firebase alimenta o catálogo.
 // O JSON embutido no APK não é mais lido nem usado como fallback.
 loadSavedFirebaseCatalog(heroImage,heroTitle,heroDescription,featured);

 }
 void loadSavedFirebaseCatalog(ImageView heroImage,TextView heroTitle,TextView heroDescription,JSONObject[] featured){
  pool.execute(()->{
   final ArrayList<JSONObject> candidates=new ArrayList<>();
   final ArrayList<String> cats;
   final int total;
   try(LocalCatalog db=new LocalCatalog(this)){
    total=db.count();cats=db.categories();
    // Uma página pequena basta para o destaque, sem carregar milhares de títulos na RAM.
    for(JSONObject item:db.page(0,180)){
     String cover=item.optString("capa","");String category=item.optString("categoria","").toLowerCase(java.util.Locale.ROOT);
     if(cover.startsWith("https://")&&!category.contains("episod")&&!category.contains("canal")&&!category.contains("iptv"))candidates.add(item);
    }
   }catch(Exception e){runOnUiThread(()->{if(status!=null)status.setText("Erro ao abrir catálogo salvo: "+e.getMessage());});return;}
   runOnUiThread(()->{
    if(isFinishing())return;
    localCatalogCount=total;catalogLoadedOnce=true;
    catNames.clear();catNames.add("Todas");for(String c:cats)if(!catNames.contains(c))catNames.add(c);
    ((ArrayAdapter)categories.getAdapter()).notifyDataSetChanged();
    heroCandidates.clear();heroCandidates.addAll(candidates);heroBatchStart=0;
    refreshHeroBatchIfNeeded(heroImage,heroTitle,heroDescription,featured);
    heroActive=true;
    if(heroTicker!=null){handler.removeCallbacks(heroTicker);handler.postDelayed(heroTicker,HERO_SLIDE_MS);}
    scheduleHeroBatchCheck();
    resetFeed();
    if(total==0&&status!=null)status.setText("Aguardando primeira sincronização do Firebase...");
   });
  });
 }
 void showHero(int index,ImageView picture,TextView title,TextView description,JSONObject[] selectedItem){
  if(heroItems.isEmpty())return;
  heroIndex=(index+heroItems.size())%heroItems.size();JSONObject item=heroItems.get(heroIndex);selectedItem[0]=item;
  picture.animate().alpha(0.35f).setDuration(160).withEndAction(()->{
   String cover=item.optString("backdrop","");if(!cover.startsWith("http"))cover=item.optString("capa","");loadCover(cover,picture);
   title.setText(item.optString("nome","REEL SÉRIES"));String synopsis=item.optString("sinopse","");description.setText(synopsis.isEmpty()?"Assista agora no Reel Séries.":synopsis);
   picture.animate().alpha(1f).setDuration(380).start();
  }).start();
  if(heroDots!=null){heroDots.removeAllViews();for(int i=0;i<heroItems.size();i++){final int n=i;TextView dot=navText(i==heroIndex?"●":"●",11);dot.setGravity(Gravity.CENTER);dot.setTextColor(i==heroIndex?0xffff5269:0xff677084);heroDots.addView(dot,new LinearLayout.LayoutParams(dp(23),dp(20)));dot.setOnClickListener(v->showHero(n,picture,title,description,selectedItem));}}
  if(heroTicker!=null&&heroActive){handler.removeCallbacks(heroTicker);handler.postDelayed(heroTicker,HERO_SLIDE_MS);}
 }
 // Rotate by batches without repeating titles until the candidate list is exhausted.
 void refreshHeroBatchIfNeeded(ImageView picture,TextView title,TextView description,JSONObject[] selectedItem){
  if(heroCandidates.isEmpty())return;
  long now=System.currentTimeMillis();
  if(heroBatchStart>0 && now>=heroBatchStart && now-heroBatchStart<HERO_ROTATE_MS)return;
  long batchNumber=now/HERO_ROTATE_MS;
  int count=heroCandidates.size(), batchSize=Math.min(10,count);
  long cycle=(batchNumber*10L)/count;
  int offset=(int)((batchNumber*10L)%count);
  ArrayList<Integer> order=new ArrayList<>();for(int i=0;i<count;i++)order.add(i);
  java.util.Collections.shuffle(order,new java.util.Random(0x5245454cL+cycle));
  heroItems.clear();for(int i=0;i<batchSize;i++)heroItems.add(heroCandidates.get(order.get((offset+i)%count)));
  heroBatchStart=batchNumber*HERO_ROTATE_MS;
  showHero(0,picture,title,description,selectedItem);
 }
 void scheduleHeroBatchCheck(){
  if(heroBatchTicker==null)return;
  handler.removeCallbacks(heroBatchTicker);
  long next=((System.currentTimeMillis()/HERO_ROTATE_MS)+1)*HERO_ROTATE_MS;
  handler.postDelayed(heroBatchTicker,Math.max(1000L,next-System.currentTimeMillis()+200L));
 }
 @Override protected void onPause(){super.onPause();heroActive=false;if(heroTicker!=null)handler.removeCallbacks(heroTicker);if(heroBatchTicker!=null)handler.removeCallbacks(heroBatchTicker);}
 void checkApkUpdate(){
  if(!FirebaseCatalogSync.configured(this))return;
  pool.execute(()->{
   try{
    JSONObject release=FirebaseCatalogSync.apkRelease(this);
    int next=release.optInt("versionCode",0);String link=release.optString("url","");
    int installed=getPackageManager().getPackageInfo(getPackageName(),0).versionCode;
    if(next<=installed||!link.startsWith("https://"))return;
    runOnUiThread(()->{
     if(isFinishing())return;
     new AlertDialog.Builder(this).setTitle("Atualização disponível")
      .setMessage("Nova versão do Reel Séries disponível. A instalação mantém o app existente se a assinatura digital for a mesma. O Android pedirá sua confirmação.")
      .setNegativeButton("Depois",null).setPositiveButton("Atualizar",(d,w)->{
       try{android.app.DownloadManager.Request req=new android.app.DownloadManager.Request(android.net.Uri.parse(link));
        req.setTitle("Atualização Reel Séries");req.setDescription("Toque no download concluído para instalar o APK");
        req.setMimeType("application/vnd.android.package-archive");
        req.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        ((android.app.DownloadManager)getSystemService(DOWNLOAD_SERVICE)).enqueue(req);
        Toast.makeText(this,"Baixando APK. Abra a notificação para instalar.",Toast.LENGTH_LONG).show();
       }catch(Exception e){new AlertDialog.Builder(this).setMessage("Falha ao baixar: "+e.getMessage()).setPositiveButton("OK",null).show();}
      }).show();
    });
   }catch(Exception e){android.util.Log.w("ReelSeriesUpdate","Não foi possível consultar atualização",e);}
  });
 }
 void refreshContinueCards(LinearLayout section,LinearLayout cards){
  cards.removeAllViews();
  ArrayList<JSONObject> recent=new ArrayList<>();
  for(java.util.Map.Entry<String,?> entry:getSharedPreferences("reel_continue_history",MODE_PRIVATE).getAll().entrySet()){
   if(!(entry.getValue() instanceof String))continue;
   try{JSONObject item=new JSONObject((String)entry.getValue());
    if(item.optLong("position")>=5000&&item.optString("url").startsWith("http"))recent.add(item);
   }catch(Exception ignored){}
  }
  java.util.Collections.sort(recent,(a,b)->Long.compare(b.optLong("updated"),a.optLong("updated")));
  section.setVisibility(recent.isEmpty()?View.GONE:View.VISIBLE);
  for(int i=0;i<Math.min(2,recent.size());i++){
   JSONObject item=recent.get(i);String name=item.optString("name","Vídeo"),url=item.optString("url");
   long pos=item.optLong("position"),duration=item.optLong("duration");
   LinearLayout tile=new LinearLayout(this);tile.setOrientation(1);tile.setBackground(rounded(0xff222229,10));
   LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);if(i>0)tp.leftMargin=dp(9);cards.addView(tile,tp);
   ImageView poster=new ImageView(this);poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
   poster.setBackgroundColor(0xff303039);tile.addView(poster,new LinearLayout.LayoutParams(-1,dp(108)));
   loadCover(item.optString("capa",""),poster);
   ProgressBar progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
   progress.setMax(1000);progress.setProgress(duration>0?(int)Math.min(1000,pos*1000/duration):0);
   progress.setProgressTintList(android.content.res.ColorStateList.valueOf(0xffef3b50));
   progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff494951));
   tile.addView(progress,new LinearLayout.LayoutParams(-1,dp(4)));
   TextView label=navText(name,12);label.setMaxLines(1);label.setEllipsize(android.text.TextUtils.TruncateAt.END);
   label.setPadding(dp(8),dp(8),dp(8),dp(10));tile.addView(label);
   tile.setOnClickListener(v->{Intent intent=new Intent(this,PlayerActivity.class);
    intent.putExtra("url",url);intent.putExtra("name",name);
    intent.putExtra("capa",item.optString("capa",""));intent.putExtra("resume_position",pos);
    startActivity(intent);});
  }
 }
 android.graphics.drawable.GradientDrawable rounded(int color,int radius){
  android.graphics.drawable.GradientDrawable shape=new android.graphics.drawable.GradientDrawable();
  shape.setColor(color);shape.setCornerRadius(dp(radius));return shape;
 }
 TextView navText(String value,int size){TextView v=new TextView(this);v.setText(value);v.setTextColor(Color.WHITE);v.setTextSize(size);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
 TextView actionText(String value,boolean primary){TextView v=navText(value,14);v.setGravity(Gravity.CENTER);v.setTypeface(null,1);v.setTextColor(Color.WHITE);android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(primary?0xffe6384b:0xff34343d);bg.setCornerRadius(dp(12));v.setBackground(bg);return v;}
 void chooseCategory(String q){channelGroup="";mode=q;selected="Todas";if(categories!=null&&categories.getSelectedItemPosition()!=0)categories.setSelection(0);resetFeed();}
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
    // Uma atualização corretiva única reprocessa os campos alternativos de URL.
    boolean first=!pref.getBoolean("download_complete",false) || !pref.getBoolean("url_fields_fixed_v1",false);
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
    pref.edit().putString("revision",revision).putBoolean("download_complete",true).putBoolean("url_fields_fixed_v1",true)
     .putLong("last_check",System.currentTimeMillis()).apply();
    runOnUiThread(()->{
     if(isFinishing()||categories==null)return;
     for(String c:cats)if(!catNames.contains(c))catNames.add(c);
     ((ArrayAdapter)categories.getAdapter()).notifyDataSetChanged();
     getSharedPreferences("reel_folder_cache_v1",MODE_PRIVATE).edit().remove("folders").apply();
      invalidateSavedPages();
     // Atualiza o feed; o próximo menu reconstruirá e salvará as pastas uma única vez.
     localCatalogCount=n;resetFeed();
     if(status!=null)status.setText("Catálogo salvo no celular • "+n+" registros sincronizados");
    });
   }catch(Exception e){runOnUiThread(()->{if(status!=null)status.setText("Catálogo local disponível • atualização pendente: "+e.getMessage());});}
   finally{cloudBusy.set(false);}
  });
 }

 void forceCloudUpdate(){
  if(com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser()==null){Toast.makeText(this,"Entre na sua conta para receber atualizações.",Toast.LENGTH_LONG).show();return;}
  if(cloudBusy.get()){Toast.makeText(this,"Atualização já em andamento.",Toast.LENGTH_SHORT).show();return;}
  getSharedPreferences("reel_cloud_cache_v194",MODE_PRIVATE).edit().remove("last_check").apply();
  Toast.makeText(this,"Buscando atualizações do Firebase...",Toast.LENGTH_SHORT).show();loadCloud();
 }
 // Painel lateral: início, perfil e grupos reais do catálogo IPTV local.
 private void showChannelSidebar(){
  final android.app.Dialog drawer=new android.app.Dialog(this);
  LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setBackgroundColor(0xff171e2a);panel.setPadding(dp(16),dp(16),dp(12),dp(12));
  TextView title=navText("REEL SÉRIES",19);title.setTypeface(null,1);title.setTextColor(0xffff5269);panel.addView(title);
  TextView home=navText("⌂  Início",17);home.setPadding(dp(10),dp(20),0,dp(18));panel.addView(home);home.setOnClickListener(v->{drawer.dismiss();channelGroup="";chooseCategory("Todos");});
  if(com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser()!=null){
   TextView receive=navText("↓  Receber atualizações do Firebase",16);
   receive.setPadding(dp(10),dp(12),0,dp(12));receive.setTextColor(0xff7ee7bd);panel.addView(receive);
   receive.setOnClickListener(v->{drawer.dismiss();forceCloudUpdate();});
  }
  TextView profile=navText("♙  Meu perfil",17);profile.setPadding(dp(10),dp(10),0,dp(20));panel.addView(profile);profile.setOnClickListener(v->{drawer.dismiss();startActivity(new Intent(this,ProfileActivity.class));});
  TextView groupsTitle=navText("CATÁLOGO POR PASTAS",12);groupsTitle.setTextColor(0xffff5269);groupsTitle.setPadding(dp(10),dp(12),0,dp(10));panel.addView(groupsTitle);
  ScrollView scroll=new ScrollView(this);panel.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);scroll.addView(list);
  TextView wait=navText("Carregando pastas pela primeira vez...",13);list.addView(wait);
  drawer.setContentView(panel);
  android.view.Window win=drawer.getWindow();if(win!=null){win.setBackgroundDrawableResource(android.R.color.transparent);win.setGravity(Gravity.START|Gravity.TOP);win.setLayout(Math.min(dp(310),getResources().getDisplayMetrics().widthPixels*85/100),-1);win.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);android.view.WindowManager.LayoutParams a=win.getAttributes();a.dimAmount=0.45f;win.setAttributes(a);}
  drawer.show();if(win!=null)win.setLayout(Math.min(dp(310),getResources().getDisplayMetrics().widthPixels*85/100),-1);
  android.content.SharedPreferences pref=getSharedPreferences("reel_folder_cache_v1",MODE_PRIVATE);
  String saved=pref.getString("folders","");
  if(!saved.isEmpty()){
   try{
    JSONObject json=new JSONObject(saved);java.util.LinkedHashMap<String,Integer> counts=new java.util.LinkedHashMap<>();
    java.util.Iterator<String> keys=json.keys();while(keys.hasNext()){String key=keys.next();counts.put(key,json.optInt(key));}
    renderFolderItems(list,drawer,counts);return;
   }catch(Exception ignored){}
  }
  pool.execute(()->{
   final java.util.LinkedHashMap<String,Integer> counts;
   try(LocalCatalog db=new LocalCatalog(this)){counts=db.catalogFolders();}
   catch(Exception e){runOnUiThread(()->wait.setText("Não foi possível listar as pastas"));return;}
   if(!counts.isEmpty()){
    JSONObject json=new JSONObject();try{for(java.util.Map.Entry<String,Integer> entry:counts.entrySet())json.put(entry.getKey(),entry.getValue());}catch(Exception ignored){}
    pref.edit().putString("folders",json.toString()).apply();
   }
   runOnUiThread(()->{if(drawer.isShowing())renderFolderItems(list,drawer,counts);});
  });
 }
 private void renderFolderItems(LinearLayout list,android.app.Dialog drawer,java.util.LinkedHashMap<String,Integer> counts){
  list.removeAllViews();
  if(counts.isEmpty()){list.addView(navText("Aguardando catálogo do Firebase",13));return;}
  java.util.ArrayList<String> names=new java.util.ArrayList<>(counts.keySet());java.util.Collections.sort(names,String.CASE_INSENSITIVE_ORDER);
  for(String group:names){
   TextView item=navText("▱  "+group+"    ("+counts.get(group)+")",15);
   item.setPadding(dp(10),dp(12),dp(6),dp(12));list.addView(item);
   item.setOnClickListener(v->AdultProtection.require(this,AdultProtection.adult(group),()->{drawer.dismiss();channelGroup=group;mode="Todos";selected="Todas";if(categories!=null&&categories.getSelectedItemPosition()!=0)categories.setSelection(0);if(search!=null)search.setText("");resetFeed();}));
  }
 }
  void resetFeed(){if(search==null||grid==null||adapter==null)return;requestVersion++;currentPage=0;pageOffset=0;endReached=false;loadingPage=false;bundledPaging=false;localPages.clear();shown.clear();adapter.notifyDataSetChanged();updateGridHeight();loadNextPage();}
  void openPage(int target){if(loadingPage||target<0||(target>currentPage&&endReached))return;currentPage=target;requestVersion++;pageOffset=target*PAGE_SIZE;endReached=false;localPages.clear();shown.clear();adapter.notifyDataSetChanged();updateGridHeight();loadNextPage();}
 boolean matches(JSONObject o,String query){String name=o.optString("nome","");String cat=o.optString("categoria","");String c=cat.toLowerCase(java.util.Locale.ROOT);String n=name.toLowerCase(java.util.Locale.ROOT);if(!n.contains(query))return false;if(!selected.equals("Todas")&&!selected.equals(cat))return false;boolean tv=c.contains("iptv")||c.contains("canal")||c.contains("tv ao vivo")||c.equals("tv")||c.contains("televis");boolean series=SeriesHelper.isSeries(o);if(mode.equals("TV / IPTV"))return tv;if(mode.equals("Séries"))return series&&!tv;if(mode.equals("Filmes"))return !tv&&!series&&(c.contains("filme")||c.contains("movie")||c.contains("cinema"));return true;}
  // Cache persistente por pasta, filtro, busca e pagina (nao e apagado ao fechar o app).
  java.io.File pageCacheFile(String group,String q,String modeName,String category,int offset){
   String key=getSharedPreferences("reel_page_cache_v1",MODE_PRIVATE).getInt("generation",0)
    +"|"+group+"|"+q+"|"+modeName+"|"+category+"|"+offset+"|"+PAGE_SIZE;
   try{
    byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(key.getBytes("UTF-8"));
    StringBuilder hex=new StringBuilder();for(byte b:digest)hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
    java.io.File folder=new java.io.File(getFilesDir(),"reel_pages_v1");
    if(!folder.exists())folder.mkdirs();
    return new java.io.File(folder,hex.toString()+".json");
   }catch(Exception e){return null;}
  }
  JSONObject readSavedPage(java.io.File file){
   if(file==null||!file.isFile())return null;
   try(java.io.FileInputStream in=new java.io.FileInputStream(file);
       java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){
    byte[] buffer=new byte[8192];int n;
    while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
    return new JSONObject(out.toString("UTF-8"));
   }catch(Exception e){return null;}
  }
  void savePage(java.io.File file,ArrayList<JSONObject> page,boolean hasMore){
   if(file==null)return;
   try{
    JSONObject payload=new JSONObject();JSONArray items=new JSONArray();
    for(JSONObject item:page)items.put(item);
    payload.put("items",items);payload.put("next",hasMore);
    java.io.File temp=new java.io.File(file.getParentFile(),file.getName()+".tmp");
    try(java.io.FileOutputStream out=new java.io.FileOutputStream(temp)){
     out.write(payload.toString().getBytes("UTF-8"));out.getFD().sync();
    }
    if(!temp.renameTo(file)){temp.delete();}
   }catch(Exception e){android.util.Log.w("ReelPageCache","Falha ao salvar pagina",e);}
  }
  void invalidateSavedPages(){
   getSharedPreferences("reel_page_cache_v1",MODE_PRIVATE).edit()
    .putInt("generation",getSharedPreferences("reel_page_cache_v1",MODE_PRIVATE).getInt("generation",0)+1).apply();
   pool.execute(()->{
    java.io.File folder=new java.io.File(getFilesDir(),"reel_pages_v1");
    java.io.File[] files=folder.listFiles();
    if(files!=null)for(java.io.File f:files)if(f.isFile())f.delete();
   });
  }
  void loadNextPage(){
   if(loadingPage||pool.isShutdown())return;
   loadingPage=true;final int version=requestVersion,offset=pageOffset;
   final String q=search.getText().toString().toLowerCase(java.util.Locale.ROOT).trim(),m=mode,cat=selected,groupFilter=channelGroup;
   moreButton.setVisibility(View.VISIBLE);moreButton.setText("CARREGANDO...");
   if(previousButton!=null)previousButton.setVisibility(currentPage>0?View.VISIBLE:View.INVISIBLE);
   if(pageLabel!=null)pageLabel.setText("Página "+(currentPage+1));
   pool.execute(()->{
    try{
     java.io.File file=pageCacheFile(groupFilter,q,m,cat,offset);
     JSONObject saved=readSavedPage(file);
     ArrayList<JSONObject> page=new ArrayList<>();
     boolean hasNext;
     if(saved!=null&&saved.optJSONArray("items")!=null){
      JSONArray items=saved.optJSONArray("items");
      for(int i=0;i<items.length();i++){JSONObject item=items.optJSONObject(i);if(item!=null)page.add(item);}
      hasNext=saved.optBoolean("next",false);
     }else{
      try(LocalCatalog db=new LocalCatalog(this)){
       page=groupFilter.isEmpty()?db.feed(q,m,cat,offset,PAGE_SIZE+1):db.catalogFolderFeed(groupFilter,q,offset,PAGE_SIZE+1);
      }
      hasNext=page.size()>PAGE_SIZE;
      if(hasNext)page.remove(page.size()-1);
      savePage(file,page,hasNext);
     }
     final boolean next=hasNext;
     final ArrayList<JSONObject> result=page;
     runOnUiThread(()->{if(version!=requestVersion)return;
      localPages.clear();localPages.addAll(result);pageOffset=offset;
      endReached=!next;loadingPage=false;filter();
      moreButton.setVisibility(next?View.VISIBLE:View.INVISIBLE);
      moreButton.setText("PRÓXIMA ›");
      if(previousButton!=null)previousButton.setVisibility(currentPage>0?View.VISIBLE:View.INVISIBLE);
      if(pageLabel!=null)pageLabel.setText("Página "+(currentPage+1));
      if(grid!=null)grid.setSelection(0);
     });
    }catch(Exception ex){runOnUiThread(()->{if(version==requestVersion){loadingPage=false;status.setText("Erro: "+ex.getMessage());moreButton.setVisibility(View.VISIBLE);moreButton.setText("TENTAR NOVAMENTE");}});}
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
 void filter(){if(adapter==null)return;String q=search.getText().toString().toLowerCase(java.util.Locale.ROOT).trim();shown.clear();java.util.HashSet<String> ids=new java.util.HashSet<>(),grouped=new java.util.HashSet<>();ArrayList<JSONObject> combined=new ArrayList<>(localPages);if(combined.isEmpty() && pageOffset==0 && localCatalogCount==0){int added=0;for(JSONObject fallback:all){if(matches(fallback,q)){combined.add(fallback);if(++added>=60)break;}}}for(JSONObject o:combined){if(!matches(o,q))continue;String id=o.optString("id","");if(!id.isEmpty()&&!ids.add(id))continue;if(SeriesHelper.isSeries(o)){String k=SeriesHelper.key(o);if(!grouped.add(k))continue;}shown.add(o);}status.setText("Página "+(currentPage+1)+" • "+shown.size()+" títulos • "+localCatalogCount+" salvos no celular");adapter.notifyDataSetChanged();updateGridHeight();}
 void updateGridHeight(){
  if(grid==null)return;
  int rows=Math.max(1,(shown.size()+2)/3);
  int height=dp(10)+rows*dp(215)+Math.max(0,rows-1)*dp(12)+dp(12);
  android.view.ViewGroup.LayoutParams params=grid.getLayoutParams();
  if(params!=null&&params.height!=height){params.height=height;grid.setLayoutParams(params);}
 }
 @Override protected void onResume(){super.onResume();if(continueSectionView!=null)refreshContinueCards(continueSectionView,continueCardsView);heroActive=true;if(heroBatchTicker!=null){handler.removeCallbacks(heroBatchTicker);handler.post(heroBatchTicker);}if(heroTicker!=null){handler.removeCallbacks(heroTicker);handler.postDelayed(heroTicker,HERO_SLIDE_MS);}if(search!=null){String wanted=getIntent().getStringExtra("reel_search_title");if(wanted!=null&&!wanted.isEmpty()){getIntent().removeExtra("reel_search_title");search.setText(wanted);search.setSelection(search.length());}if(!catalogLoadedOnce)resetFeed();if(FirebaseCatalogSync.configured(this))loadCloud();}}
 @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(search!=null){String wanted=intent.getStringExtra("reel_search_title");if(wanted!=null&&!wanted.isEmpty()){intent.removeExtra("reel_search_title");search.setText(wanted);search.setSelection(search.length());}}}
 void details(JSONObject o){AdultProtection.require(this,AdultProtection.adult(o),()->{Intent i=new Intent(this,DetailsActivity.class);i.putExtra("item",o.toString());startActivity(i);});}
 void loadCover(String url,ImageView image){if(url.equals(image.getTag()) && image.getDrawable()!=null)return;image.setTag(url);Bitmap hit=cache.get(url);if(hit!=null){image.setImageBitmap(hit);return;}image.setImageDrawable(null);if(!url.startsWith("https://"))return;pool.execute(()->{try{HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(4500);c.setReadTimeout(5000);c.setInstanceFollowRedirects(true);InputStream in=c.getInputStream();BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=4;Bitmap bitmap=BitmapFactory.decodeStream(in,null,opts);in.close();c.disconnect();if(bitmap!=null){cache.put(url,bitmap);runOnUiThread(()->{if(url.equals(image.getTag()))image.setImageBitmap(bitmap);});}}catch(Exception ignored){}});}
 @Override protected void onDestroy(){if(heroTicker!=null)handler.removeCallbacks(heroTicker);if(heroBatchTicker!=null)handler.removeCallbacks(heroBatchTicker);pool.shutdownNow();super.onDestroy();}
}
