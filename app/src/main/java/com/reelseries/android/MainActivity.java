package com.reelseries.android;

import android.app.*;import android.os.*;import android.content.*;import android.graphics.*;import android.graphics.drawable.*;import android.view.*;import android.widget.*;import android.text.*;import org.json.*;import java.io.*;import java.util.*;import java.util.concurrent.*;import java.net.*;

public class MainActivity extends Activity {
 final ArrayList<JSONObject> all=new ArrayList<>(), shown=new ArrayList<>(); final java.util.HashMap<String,JSONObject> cloud=new java.util.HashMap<>();
 final ExecutorService pool=Executors.newFixedThreadPool(3); final java.util.concurrent.ExecutorService catalogQueries=Executors.newFixedThreadPool(2); final android.util.LruCache<String,Bitmap> cache=new android.util.LruCache<String,Bitmap>(24);
 GridView grid; BaseAdapter adapter; TextView status; EditText search; Spinner categories; String selected="Todas", mode="Todos", channelGroup=""; int pageOffset=0; boolean loadingPage=false, endReached=false, bundledPaging=false; TextView moreButton, previousButton, pageLabel; int currentPage=0; static final int PAGE_SIZE=60; final ArrayList<JSONObject> localPages=new ArrayList<>(); int requestVersion=0; int localCatalogCount=0; boolean catalogLoadedOnce=false; boolean pageLoadFailed=false; android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper()); Runnable pending;
 android.widget.FrameLayout featuredHero; ImageView featuredCover;
 final ArrayList<JSONObject> heroItems=new ArrayList<>(); int heroIndex=0; boolean heroActive=false;
 LinearLayout continueSectionView,continueCardsView;
 Runnable heroTicker; android.widget.LinearLayout heroDots;
 // Ten different highlights every 30 minutes; six-second slide interval.
 static final long HERO_ROTATE_MS=30L*60L*1000L;
 static final long HERO_SLIDE_MS=6000L;
 final ArrayList<JSONObject> heroCandidates=new ArrayList<>();
 long heroBatchStart=0L;
 Runnable heroBatchTicker;

 private com.google.firebase.firestore.ListenerRegistration accessSubscription;
 private String observedAccessUid="";
 private void observeAccountAccess(){
  com.google.firebase.auth.FirebaseUser user=com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
  String next=user==null?"":user.getUid();
  if(next.equals(observedAccessUid)&&accessSubscription!=null)return;
  if(accessSubscription!=null){accessSubscription.remove();accessSubscription=null;}
  observedAccessUid=next;
  if(next.isEmpty())return;
  accessSubscription=com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("usuarios").document(next)
   .addSnapshotListener((doc,error)->{
    if(isFinishing()||error!=null||doc==null||!next.equals(observedAccessUid))return;
    // The global guard verifies the ADM role. This listener keeps the home page
    // responsive to changes even when the client stays on MainActivity.
    if(sessionReady && !AccountSession.isListed(this,doc)){
    sessionReady=false;
    com.google.firebase.auth.FirebaseAuth.getInstance().signOut();
    startActivity(new Intent(this,ProfileActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));finish();return;
   }
   processCustomerUpdate(doc);
   if(Boolean.TRUE.equals(doc.getBoolean("bloqueado"))){
     com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("admins").document(next).get()
      .addOnSuccessListener(adm->{if(!adm.exists())openBlockedScreen("Sua conta foi bloqueada pela administração.");});
    }else{
     com.google.firebase.Timestamp until=doc.getTimestamp("vipAte");
     boolean active=Boolean.TRUE.equals(doc.getBoolean("vipAtivo"));
     if(!active||until==null||until.toDate().getTime()<=System.currentTimeMillis()){
      com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("admins").document(next).get()
       .addOnSuccessListener(adm->{if(!adm.exists())openBlockedScreen("Sua assinatura venceu ou não está ativa. Solicite a renovação.");});
     }
    }
   });
 }
 private void openBlockedScreen(String reason){
  if(isFinishing())return;
  Intent i=new Intent(this,BlockedActivity.class);
  i.putExtra("reason",reason);i.putExtra("pending",false);
  i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
  startActivity(i);
 }
 int bg=Color.rgb(10,10,13), fg=Color.WHITE; final ArrayList<String> catNames=new ArrayList<>();
 int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
 @Override public void onCreate(Bundle b){super.onCreate(b);
 if(com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser()==null){
  startActivity(new Intent(this,ProfileActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));finish();return;
 }
 getWindow().setStatusBarColor(bg);getWindow().setNavigationBarColor(0xff0b0b0e);
 // Aplicar as margens reais das barras do Android (inclusive recortes de tela).
 androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(),false);
 LinearLayout layout=new LinearLayout(this);layout.setOrientation(1);layout.setBackgroundColor(bg);
 androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(layout,(view,insets)->{
  androidx.core.graphics.Insets bars=insets.getInsets(
   androidx.core.view.WindowInsetsCompat.Type.systemBars() |
   androidx.core.view.WindowInsetsCompat.Type.displayCutout());
  view.setPadding(bars.left,bars.top,bars.right,bars.bottom);
  return insets;
 });
 androidx.core.view.ViewCompat.requestApplyInsets(layout);
 int screenDp=(int)(getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density);
 int headerIconDp=screenDp<370?34:39;
 LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(12),dp(8),dp(12),dp(8));layout.addView(header);
 TextView menu=navText("☰",25);header.addView(menu,new LinearLayout.LayoutParams(dp(headerIconDp),dp(48)));menu.setOnClickListener(v->showChannelSidebar());
 TextView brand=navText("REEL  SÉRIES",screenDp<370?16:19);brand.setSingleLine(true);brand.setEllipsize(android.text.TextUtils.TruncateAt.END);brand.setGravity(Gravity.CENTER_VERTICAL);brand.setTextColor(0xfff04458);brand.setTypeface(null,1);header.addView(brand,new LinearLayout.LayoutParams(0,dp(48),1));
 homeCastIcon=new ImageView(this);
 ImageView homeCast=homeCastIcon;
 homeCast.setImageResource(R.drawable.ic_cast_desconectado);
 homeCast.setScaleType(ImageView.ScaleType.FIT_CENTER);
 homeCast.setPadding(dp(7),dp(12),dp(7),dp(12));
 homeCast.setContentDescription("Conectar à TV pelo Google Cast");
 homeCast.setClickable(true);
 homeCast.setOnClickListener(v->{
  if(homeCastDialogOpening)return;
  homeCastDialogOpening=true;
  handler.postDelayed(()->homeCastDialogOpening=false,900);
  showHomeCastDevices();
 });
 header.addView(homeCast,new LinearLayout.LayoutParams(dp(headerIconDp),dp(48)));
 TextView searchIcon=navText("⌕",30);header.addView(searchIcon,new LinearLayout.LayoutParams(dp(headerIconDp),dp(48)));searchIcon.setOnClickListener(v->{search.requestFocus();((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(search,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);});
 TextView profile=navText("◉",24);header.addView(profile,new LinearLayout.LayoutParams(dp(headerIconDp),dp(48)));profile.setOnClickListener(v->startActivity(new Intent(this,ProfileActivity.class)));
 TextView admin=navText("⚙",22);
 admin.setVisibility(View.GONE);
 header.addView(admin,new LinearLayout.LayoutParams(dp(headerIconDp),dp(48)));
 admin.setOnClickListener(v->startActivity(new Intent(this,AdminActivity.class)));
 com.google.firebase.auth.FirebaseUser adminUser=com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
 if(adminUser!=null){
  final String adminUid=adminUser.getUid();
  com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("admins").document(adminUid).get()
   .addOnSuccessListener(doc->{
    if(isFinishing()||isDestroyed())return;
    com.google.firebase.auth.FirebaseUser current=com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
    boolean allowed=current!=null&&adminUid.equals(current.getUid())&&doc.exists()
     &&Boolean.TRUE.equals(doc.getBoolean("active"))&&"admin".equals(doc.getString("role"));
    admin.setVisibility(allowed?View.VISIBLE:View.GONE);
   })
   .addOnFailureListener(error->admin.setVisibility(View.GONE));
 }
 androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipe=new androidx.swiperefreshlayout.widget.SwipeRefreshLayout(this);
 swipe.setColorSchemeColors(0xffef4356);swipe.setProgressBackgroundColorSchemeColor(0xff202027);
 layout.addView(swipe,new LinearLayout.LayoutParams(-1,0,1));
 ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);swipe.addView(scroll);
 swipe.setOnChildScrollUpCallback((parent,child)->scroll.canScrollVertically(-1));
 swipe.setOnRefreshListener(()->{
  refreshByPull();
  // Stops spinner after a bounded wait even if the network is slow.
  handler.postDelayed(()->swipe.setRefreshing(false),1800);
 });
 LinearLayout content=new LinearLayout(this);content.setOrientation(1);content.setPadding(dp(15),0,dp(15),dp(14));scroll.addView(content);
 android.widget.FrameLayout hero=new android.widget.FrameLayout(this);hero.setBackgroundColor(0xff19191f);content.addView(hero,new LinearLayout.LayoutParams(-1,dp(390)));featuredHero=hero;
 ImageView heroImage=new ImageView(this);heroImage.setScaleType(ImageView.ScaleType.FIT_CENTER);featuredCover=heroImage;hero.addView(heroImage,new android.widget.FrameLayout.LayoutParams(-1,dp(390),Gravity.TOP));
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
 cloudProgress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
 cloudProgress.setIndeterminate(true);cloudProgress.setVisibility(View.GONE);
 cloudProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(0xffef4356));
 content.addView(cloudProgress,new LinearLayout.LayoutParams(-1,dp(5)));
 grid=new GridView(this);grid.setNumColumns(3);grid.setVerticalSpacing(dp(12));grid.setHorizontalSpacing(dp(9));grid.setClipToPadding(false);grid.setPadding(0,dp(10),0,dp(12));grid.setNestedScrollingEnabled(true);content.addView(grid,new LinearLayout.LayoutParams(-1,dp(460)));moreButton=actionText("CARREGAR MAIS TÍTULOS  ↓",false);LinearLayout.LayoutParams moreParams=new LinearLayout.LayoutParams(-1,dp(52));moreParams.topMargin=dp(10);LinearLayout pager=new LinearLayout(this);pager.setGravity(Gravity.CENTER_VERTICAL);content.addView(pager,moreParams);
  previousButton=actionText("‹ ANTERIOR",false);pager.addView(previousButton,new LinearLayout.LayoutParams(0,dp(52),1));
  pageLabel=navText("Página 1",12);pageLabel.setGravity(Gravity.CENTER);pager.addView(pageLabel,new LinearLayout.LayoutParams(dp(85),dp(52)));
  pager.addView(moreButton,new LinearLayout.LayoutParams(0,dp(52),1));
  previousButton.setOnClickListener(v->openPage(currentPage-1));moreButton.setOnClickListener(v->{if(pageLoadFailed){pageLoadFailed=false;loadNextPage();}else openPage(currentPage+1);});setContentView(layout);
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
 @Override protected void onPause(){handler.removeCallbacks(homeCastStatusUpdater);super.onPause();heroActive=false;if(heroTicker!=null)handler.removeCallbacks(heroTicker);if(heroBatchTicker!=null)handler.removeCallbacks(heroBatchTicker);}
 void checkApkUpdate(){if(com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser()!=null)ApkUpdateChecker.check(this); } // Consulta atualizacoes/apk_update no Firebase
 void refreshContinueCards(LinearLayout section,LinearLayout cards){
  cards.removeAllViews();
  ArrayList<JSONObject> recent=new ArrayList<>();
  for(java.util.Map.Entry<String,?> entry:UserMediaState.prefs(this,"reel_continue_history").getAll().entrySet()){
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
 void chooseCategory(String q){
  channelGroup="";mode=q;selected="Todas";
  if(search!=null && search.length()>0){search.setText("");return;}
  if(categories!=null&&categories.getSelectedItemPosition()!=0){categories.setSelection(0);return;}
  resetFeed();
 }
 // Persistent SQLite cache: check at most once per 24h, never redownload on every open.
 final java.util.concurrent.atomic.AtomicBoolean cloudBusy=new java.util.concurrent.atomic.AtomicBoolean(false);
 ProgressBar cloudProgress;
 static final long CLOUD_INTERVAL_MS=24L*60*60*1000;
 void loadCloud(){
  if(!FirebaseCatalogSync.configured(this)||!cloudBusy.compareAndSet(false,true))return;
  runOnUiThread(()->{if(cloudProgress!=null){cloudProgress.setIndeterminate(true);cloudProgress.setProgress(0);cloudProgress.setVisibility(View.VISIBLE);}});
  pool.execute(()->{
   try{
    android.content.SharedPreferences pref=getSharedPreferences("reel_cloud_cache_v194",MODE_PRIVATE);
    long now=System.currentTimeMillis(),last=pref.getLong("last_check",0);
    // Also handle clock changes and future timestamps safely.
    if(last>0 && now>=last && now-last<CLOUD_INTERVAL_MS){
     runOnUiThread(()->{if(status!=null)status.setText("Catálogo já verificado hoje • próxima consulta após 24 horas");});
     return;
    }
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
     if(status!=null)status.setText("Catálogo salvo no celular • "+n+" registros sincronizados • 100%");
     if(cloudProgress!=null){cloudProgress.setIndeterminate(false);cloudProgress.setProgress(100);}
    });
   }catch(Exception e){runOnUiThread(()->{if(status!=null)status.setText("Catálogo local disponível • atualização pendente: "+e.getMessage());});}
   finally{cloudBusy.set(false);runOnUiThread(()->{
    if(cloudProgress!=null && cloudProgress.isIndeterminate())cloudProgress.setVisibility(View.GONE);
   });}
  });
 }

 void forceCloudUpdate(){
  if(com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser()==null){Toast.makeText(this,"Entre na sua conta para receber atualizações.",Toast.LENGTH_LONG).show();return;}
  if(cloudBusy.get()){Toast.makeText(this,"Atualização já em andamento.",Toast.LENGTH_SHORT).show();return;}
  android.content.SharedPreferences cloudPrefs=getSharedPreferences("reel_cloud_cache_v194",MODE_PRIVATE);
  long last=cloudPrefs.getLong("last_check",0),now=System.currentTimeMillis();
  if(last>0&&now>=last&&now-last<CLOUD_INTERVAL_MS){
   Toast.makeText(this,"Catálogo já verificado hoje. Próxima atualização após 24 horas.",Toast.LENGTH_LONG).show();return;
  }
  Toast.makeText(this,"Buscando atualizações do Firebase...",Toast.LENGTH_SHORT).show();loadCloud();
 }
 // Painel lateral: início, perfil e grupos reais do catálogo IPTV local.
 private void showChannelSidebar(){
  final android.app.Dialog drawer=new android.app.Dialog(this);
  LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setBackgroundColor(0xff15151b);panel.setPadding(dp(16),dp(18),dp(14),dp(12));
  TextView title=navText("REEL SÉRIES",19);title.setTypeface(null,1);title.setTextColor(0xffff5269);panel.addView(title);
  TextView home=navText("⌂  Início",17);home.setPadding(dp(10),dp(20),0,dp(18));panel.addView(home);home.setOnClickListener(v->{drawer.dismiss();channelGroup="";chooseCategory("Todos");});
  if(com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser()!=null){
   TextView receive=navText("↓  Receber atualizações do Firebase",16);
   receive.setPadding(dp(10),dp(12),0,dp(12));receive.setTextColor(0xff7ee7bd);panel.addView(receive);
   receive.setOnClickListener(v->{drawer.dismiss();forceCloudUpdate();});
  }
  TextView groupsTitle=navText("EXPLORAR PASTAS",13);groupsTitle.setTypeface(null,1);groupsTitle.setTextColor(0xffef4356);groupsTitle.setPadding(dp(10),dp(12),0,dp(10));panel.addView(groupsTitle);
  EditText folderSearch=new EditText(this);folderSearch.setSingleLine(true);folderSearch.setHint("Buscar pasta...");folderSearch.setTextColor(Color.WHITE);folderSearch.setHintTextColor(0xff9d9da8);folderSearch.setTextSize(15);folderSearch.setPadding(dp(12),0,dp(12),0);folderSearch.setBackground(rounded(0xff272730,12));LinearLayout.LayoutParams fs=new LinearLayout.LayoutParams(-1,dp(46));fs.bottomMargin=dp(10);panel.addView(folderSearch,fs);
  ScrollView scroll=new ScrollView(this);panel.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);scroll.addView(list);
  TextView wait=navText("Carregando pastas pela primeira vez...",13);list.addView(wait);
  final java.util.LinkedHashMap<String,Integer>[] folderData=new java.util.LinkedHashMap[]{null};
  final Runnable[] refresh={null};
  refresh[0]=()->{if(folderData[0]!=null&&drawer.isShowing())renderFolderItems(list,drawer,folderData[0],folderSearch.getText().toString());};
  folderSearch.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){handler.removeCallbacks(refresh[0]);handler.postDelayed(refresh[0],180);}public void afterTextChanged(Editable e){}});
  drawer.setContentView(panel);
  android.view.Window win=drawer.getWindow();if(win!=null){win.setBackgroundDrawableResource(android.R.color.transparent);win.setGravity(Gravity.START|Gravity.TOP);win.setLayout(Math.min(dp(300),getResources().getDisplayMetrics().widthPixels*82/100),-1);win.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);android.view.WindowManager.LayoutParams a=win.getAttributes();a.dimAmount=0.45f;win.setAttributes(a);}
  drawer.show();if(win!=null)win.setLayout(Math.min(dp(300),getResources().getDisplayMetrics().widthPixels*82/100),-1);
  android.content.SharedPreferences pref=getSharedPreferences("reel_folder_cache_v1",MODE_PRIVATE);
  String saved=pref.getString("folders","");
  if(!saved.isEmpty()){
   try{
    JSONObject json=new JSONObject(saved);java.util.LinkedHashMap<String,Integer> counts=new java.util.LinkedHashMap<>();
    java.util.Iterator<String> keys=json.keys();while(keys.hasNext()){String key=keys.next();counts.put(key,json.optInt(key));}
    folderData[0]=counts;renderFolderItems(list,drawer,counts,"");return;
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
   runOnUiThread(()->{if(drawer.isShowing()){folderData[0]=counts;renderFolderItems(list,drawer,counts,folderSearch.getText().toString());}});
  });
 }
 private int folderRenderVersion=0;
 private void renderFolderItems(LinearLayout list,android.app.Dialog drawer,java.util.LinkedHashMap<String,Integer> counts,String query){
  final int version=++folderRenderVersion;
  list.removeAllViews();
  if(counts.isEmpty()){list.addView(navText("Aguardando catálogo do Firebase",13));return;}
  String term=query==null?"":query.trim().toLowerCase(java.util.Locale.ROOT);
  java.util.ArrayList<String> names=new java.util.ArrayList<>();
  for(String name:counts.keySet())if(name.toLowerCase(java.util.Locale.ROOT).contains(term))names.add(name);
  java.util.Collections.sort(names,String.CASE_INSENSITIVE_ORDER);
  if(names.isEmpty()){TextView empty=navText("Nenhuma pasta encontrada",14);empty.setPadding(dp(10),dp(20),0,0);list.addView(empty);return;}
  // Constrói a lista em blocos pequenos, sem congelar a thread de interface.
  final int[] index={0};
  Runnable batch=new Runnable(){public void run(){
   if(version!=folderRenderVersion||!drawer.isShowing())return;
   int end=Math.min(index[0]+18,names.size());
   for(;index[0]<end;index[0]++){
    final String group=names.get(index[0]);
    TextView item=navText("▱  "+group+"   ("+counts.get(group)+")",14);
    item.setSingleLine(true);item.setEllipsize(android.text.TextUtils.TruncateAt.END);
    item.setPadding(dp(12),dp(12),dp(8),dp(12));
    item.setBackground(rounded(index[0]%2==0?0xff202027:0xff191920,8));
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(48));lp.bottomMargin=dp(5);list.addView(item,lp);
    item.setOnClickListener(v->AdultProtection.require(MainActivity.this,AdultProtection.adult(group),()->{
     drawer.dismiss();channelGroup=group;mode="Todos";selected="Todas";
     if(categories!=null&&categories.getSelectedItemPosition()!=0)categories.setSelection(0);
     if(search!=null&&search.length()>0){search.setText("");}else resetFeed();
    }));
   }
   if(index[0]<names.size())handler.postDelayed(this,12);
  }};
  handler.post(batch);
 }
  void resetFeed(){if(search==null||grid==null||adapter==null)return;pageLoadFailed=false;requestVersion++;currentPage=0;pageOffset=0;endReached=false;loadingPage=false;bundledPaging=false;localPages.clear();shown.clear();adapter.notifyDataSetChanged();updateGridHeight();loadNextPage();}
  void openPage(int target){if(loadingPage||target<0||(target>currentPage&&endReached))return;pageLoadFailed=false;currentPage=target;requestVersion++;pageOffset=target*PAGE_SIZE;endReached=false;localPages.clear();shown.clear();adapter.notifyDataSetChanged();updateGridHeight();loadNextPage();}
 boolean matches(JSONObject o,String query){String name=o.optString("nome","");String cat=o.optString("categoria","");String c=cat.toLowerCase(java.util.Locale.ROOT);String n=name.toLowerCase(java.util.Locale.ROOT);if(!n.contains(query))return false;if(!selected.equals("Todas")&&!selected.equals(cat))return false;boolean tv=c.contains("iptv")||c.contains("canal")||c.contains("tv ao vivo")||c.equals("tv")||c.contains("televis");boolean series=SeriesHelper.isSeries(o);if(mode.equals("TV / IPTV"))return tv;if(mode.equals("Séries"))return series&&!tv;if(mode.equals("Filmes"))return !tv&&!series&&(c.contains("filme")||c.contains("movie")||c.contains("cinema"));return true;}
  // Cache persistente por pasta, filtro, busca e pagina (nao e apagado ao fechar o app).
  java.io.File pageCacheFile(String group,String q,String modeName,String category,int offset){
   String key=getSharedPreferences("reel_page_cache_v2",MODE_PRIVATE).getInt("generation",0)
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
   if(loadingPage||catalogQueries.isShutdown())return;
   loadingPage=true;final int version=requestVersion,offset=pageOffset;
   final String q=search.getText().toString().toLowerCase(java.util.Locale.ROOT).trim(),m=mode,cat=selected,groupFilter=channelGroup;
   moreButton.setVisibility(View.VISIBLE);moreButton.setText("CARREGANDO...");status.setText("Carregando "+(groupFilter.isEmpty()?m:groupFilter)+" • página "+(currentPage+1)+" (60 títulos por página)...");
   if(previousButton!=null)previousButton.setVisibility(currentPage>0?View.VISIBLE:View.INVISIBLE);
   if(pageLabel!=null)pageLabel.setText("Página "+(currentPage+1));
   // Se uma consulta antiga ficar presa, a tela nao fica bloqueada para sempre.
   handler.postDelayed(()->{
    if(version==requestVersion&&loadingPage){
     loadingPage=false;pageLoadFailed=true;
     status.setText("O catálogo demorou para responder. Toque em TENTAR NOVAMENTE.");
     moreButton.setVisibility(View.VISIBLE);moreButton.setText("TENTAR NOVAMENTE");
    }
   },15000);
   catalogQueries.execute(()->{
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
      boolean finished=false;
      // Paginação sobre títulos visíveis e únicos, não sobre linhas brutas
      // (um servidor pode cadastrar milhares de episódios com a mesma série).
      try(LocalCatalog db=new LocalCatalog(this)){
       java.util.HashSet<String> uniqueIds=new java.util.HashSet<>();
       java.util.HashSet<String> uniqueSeries=new java.util.HashSet<>();
       int matched=0,scanOffset=0;
       final int chunk=400;
       while(!finished){
        ArrayList<JSONObject> batch=groupFilter.isEmpty()
         ?db.feed(q,m,cat,scanOffset,chunk)
         :db.catalogFolderFeed(groupFilter,q,scanOffset,chunk);
        if(batch.isEmpty())break;
        scanOffset+=batch.size();
        for(JSONObject item:batch){
         if(groupFilter.isEmpty()&&!matches(item,q))continue;
         String id=item.optString("id","");
         if(!id.isEmpty()&&!uniqueIds.add(id))continue;
         if(SeriesHelper.isSeries(item)){
          String seriesKey=SeriesHelper.key(item);
          if(!uniqueSeries.add(seriesKey))continue;
         }
         if(matched>=offset){
          // O 61º título indica que existe uma próxima página.
          if(page.size()==PAGE_SIZE){finished=true;break;}
          page.add(item);
         }
         matched++;
        }
        if(batch.size()<chunk)break;
       }
      }
      hasNext=finished;
      savePage(file,page,hasNext);
     }
     final boolean next=hasNext;
     final ArrayList<JSONObject> result=page;
     runOnUiThread(()->{if(version!=requestVersion||pageLoadFailed)return;
      localPages.clear();localPages.addAll(result);pageOffset=offset;
      endReached=!next;loadingPage=false;pageLoadFailed=false;filter();
      moreButton.setVisibility(next?View.VISIBLE:View.INVISIBLE);
      moreButton.setText("PRÓXIMA ›");
      if(previousButton!=null)previousButton.setVisibility(currentPage>0?View.VISIBLE:View.INVISIBLE);
      if(pageLabel!=null)pageLabel.setText("Página "+(currentPage+1));
      if(grid!=null)grid.setSelection(0);
     });
    }catch(Exception ex){runOnUiThread(()->{if(version==requestVersion){loadingPage=false;pageLoadFailed=true;status.setText("Não foi possível carregar. Toque em TENTAR NOVAMENTE.");moreButton.setVisibility(View.VISIBLE);moreButton.setText("TENTAR NOVAMENTE");}});}
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
 void refreshByPull(){
  if(status!=null)status.setText("Atualizando catálogo...");
  if(continueSectionView!=null)refreshContinueCards(continueSectionView,continueCardsView);
  // Consulta novamente o banco local e verifica se há revisão nova no Firebase.
  resetFeed();
  if(FirebaseCatalogSync.configured(this)){
   getSharedPreferences("reel_cloud_cache_v194",MODE_PRIVATE).edit().remove("last_check").apply();
   loadCloud();
  }
  Toast.makeText(this,"Atualizando catálogo...",Toast.LENGTH_SHORT).show();
 }
 void refreshLocal(){resetFeed();}
 void filter(){if(adapter==null)return;String q=search.getText().toString().toLowerCase(java.util.Locale.ROOT).trim();shown.clear();java.util.HashSet<String> ids=new java.util.HashSet<>(),grouped=new java.util.HashSet<>();ArrayList<JSONObject> combined=new ArrayList<>(localPages);if(combined.isEmpty() && pageOffset==0 && localCatalogCount==0){int added=0;for(JSONObject fallback:all){if(matches(fallback,q)){combined.add(fallback);if(++added>=60)break;}}}for(JSONObject o:combined){if(channelGroup.isEmpty()&&!matches(o,q))continue;String id=o.optString("id","");if(!id.isEmpty()&&!ids.add(id))continue;if(SeriesHelper.isSeries(o)){String k=SeriesHelper.key(o);if(!grouped.add(k))continue;}shown.add(o);}status.setText("Página "+(currentPage+1)+" • "+shown.size()+" títulos • "+localCatalogCount+" salvos no celular");adapter.notifyDataSetChanged();updateGridHeight();}
 void updateGridHeight(){
  if(grid==null)return;
  // Recicla os cartoes dentro da grade em vez de criar 100 views ao mesmo tempo.
  int height=dp(530);
  android.view.ViewGroup.LayoutParams params=grid.getLayoutParams();
  if(params!=null&&params.height!=height){params.height=height;grid.setLayoutParams(params);}
 }
 @Override protected void onResume(){super.onResume();verifyDeviceSession();checkCustomerUpdateOnOpen();handler.removeCallbacks(homeCastStatusUpdater);handler.post(homeCastStatusUpdater);
 if(com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser()==null){
  startActivity(new Intent(this,ProfileActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));finish();return;
 }
 observeAccountAccess();if(continueSectionView!=null)refreshContinueCards(continueSectionView,continueCardsView);heroActive=true;if(heroBatchTicker!=null){handler.removeCallbacks(heroBatchTicker);handler.post(heroBatchTicker);}if(heroTicker!=null){handler.removeCallbacks(heroTicker);handler.postDelayed(heroTicker,HERO_SLIDE_MS);}if(search!=null){String wanted=getIntent().getStringExtra("reel_search_title");if(wanted!=null&&!wanted.isEmpty()){getIntent().removeExtra("reel_search_title");search.setText(wanted);search.setSelection(search.length());}if(!catalogLoadedOnce&&!loadingPage)resetFeed();if(FirebaseCatalogSync.configured(this))loadCloud();}}
 @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(search!=null){String wanted=intent.getStringExtra("reel_search_title");if(wanted!=null&&!wanted.isEmpty()){intent.removeExtra("reel_search_title");search.setText(wanted);search.setSelection(search.length());}}}
 void details(JSONObject o){AdultProtection.require(this,AdultProtection.adult(o),()->{Intent i=new Intent(this,DetailsActivity.class);i.putExtra("item",o.toString());startActivity(i);});}
 void fitFeaturedImage(ImageView image,Bitmap bmp){
  if(image!=featuredCover||featuredHero==null||bmp==null||bmp.getWidth()<=0)return;
  int width=featuredHero.getWidth();
  if(width<=0)width=getResources().getDisplayMetrics().widthPixels-dp(32);
  int imageHeight=Math.max(dp(150),Math.round((float)width*bmp.getHeight()/bmp.getWidth()));
  android.widget.FrameLayout.LayoutParams coverLp=new android.widget.FrameLayout.LayoutParams(-1,imageHeight,Gravity.TOP);
  image.setLayoutParams(coverLp);
  android.view.ViewGroup.LayoutParams heroLp=featuredHero.getLayoutParams();
  if(heroLp!=null){heroLp.height=Math.max(dp(390),imageHeight+dp(265));featuredHero.setLayoutParams(heroLp);}
 }
 void loadCover(String url,ImageView image){if(url.equals(image.getTag()) && image.getDrawable()!=null)return;image.setTag(url);Bitmap hit=cache.get(url);if(hit!=null){image.setImageBitmap(hit);fitFeaturedImage(image,hit);return;}image.setImageDrawable(null);if(!url.startsWith("https://")&&!url.startsWith("http://"))return;pool.execute(()->{try{HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(4500);c.setReadTimeout(5000);c.setInstanceFollowRedirects(true);InputStream in=c.getInputStream();BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=4;Bitmap bitmap=BitmapFactory.decodeStream(in,null,opts);in.close();c.disconnect();if(bitmap!=null){cache.put(url,bitmap);runOnUiThread(()->{if(url.equals(image.getTag())){image.setImageBitmap(bitmap);fitFeaturedImage(image,bitmap);}});}}catch(Exception ignored){}});}
 private boolean sessionReady=false;
 private boolean sessionChecking=false;
 private final java.util.concurrent.atomic.AtomicBoolean remoteUpdateBusy=new java.util.concurrent.atomic.AtomicBoolean(false);
 private void verifyDeviceSession(){
  if(sessionChecking || com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser()==null)return;
  sessionChecking=true;
  AccountSession.ensure(this,false,(allowed,reason)->{
   sessionChecking=false;if(isFinishing())return;
   if(!allowed){sessionReady=false;new AlertDialog.Builder(this).setTitle("Acesso não autorizado")
    .setMessage(reason).setCancelable(false).setPositiveButton("Voltar ao login",(d,w)->{
     com.google.firebase.auth.FirebaseAuth.getInstance().signOut();
     startActivity(new Intent(this,ProfileActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));finish();}).show();return;}
   sessionReady=true;checkCustomerUpdateOnOpen();
  });
 }
 private void checkCustomerUpdateOnOpen(){
  com.google.firebase.auth.FirebaseUser user=com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
  if(user==null)return;
  com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("usuarios").document(user.getUid()).get()
   .addOnSuccessListener(this::processCustomerUpdate);
 }
 private void processCustomerUpdate(com.google.firebase.firestore.DocumentSnapshot doc){
  if(!sessionReady||doc==null||!doc.exists()||isFinishing())return;
  Object obj=doc.get("atualizacaoPendente");if(!(obj instanceof java.util.Map))return;
  java.util.Map<?,?> command=(java.util.Map<?,?>)obj;
  String id=String.valueOf(command.get("id")),folder=String.valueOf(command.get("categoria"));
  if(!folder.equals("catalogo_1_series")&&!folder.equals("catalogo_2_filmes")&&!folder.equals("catalogo_3_iptv"))return;
  android.content.SharedPreferences pref=getSharedPreferences("reel_cloud_cache_v194",MODE_PRIVATE);
  if(id.equals(pref.getString("last_customer_command","")))return;
  long now=System.currentTimeMillis(),last=pref.getLong("last_check",0);
  if(last>0 && now>=last && now-last<CLOUD_INTERVAL_MS)return;
  if(!remoteUpdateBusy.compareAndSet(false,true))return;
  if(!cloudBusy.compareAndSet(false,true)){remoteUpdateBusy.set(false);return;}
  pool.execute(()->{
   try{
    int n;java.util.ArrayList<String> cats;
    try(LocalCatalog db=new LocalCatalog(this)){
     n=FirebaseCatalogSync.downloadCategory(this,db,folder,c->runOnUiThread(()->{if(status!=null)status.setText("Atualização solicitada pelo ADM: "+c+" registros");}));
     cats=db.categories();
    }
    pref.edit().putString("last_customer_command",id).putLong("last_check",System.currentTimeMillis()).apply();
    runOnUiThread(()->{if(isFinishing())return;for(String c:cats)if(!catNames.contains(c))catNames.add(c);
     if(categories!=null&&categories.getAdapter()!=null)((ArrayAdapter)categories.getAdapter()).notifyDataSetChanged();
     getSharedPreferences("reel_folder_cache_v1",MODE_PRIVATE).edit().remove("folders").apply();invalidateSavedPages();resetFeed();
     if(status!=null)status.setText("Atualização do ADM concluída: "+n+" registros");});
   }catch(Exception e){runOnUiThread(()->{if(status!=null)status.setText("Atualização do ADM pendente: "+e.getMessage());});}
   finally{cloudBusy.set(false);remoteUpdateBusy.set(false);}
  });
 }
 @Override protected void onDestroy(){if(heroTicker!=null)handler.removeCallbacks(heroTicker);if(heroBatchTicker!=null)handler.removeCallbacks(heroBatchTicker);catalogQueries.shutdownNow();pool.shutdownNow();if(accessSubscription!=null)accessSubscription.remove();super.onDestroy();}


 private boolean homeCastDialogOpening=false;
 private ImageView homeCastIcon;
 private boolean homeCastLastConnected=false;
 private final Runnable homeCastStatusUpdater=new Runnable(){
  @Override public void run(){
   if(homeCastIcon==null || isFinishing())return;
   boolean connected=false;
   try{
    com.google.android.gms.cast.framework.CastContext c=com.google.android.gms.cast.framework.CastContext.getSharedInstance(MainActivity.this);
    com.google.android.gms.cast.framework.CastSession s=c.getSessionManager().getCurrentCastSession();
    connected=s!=null&&s.isConnected();
   }catch(Exception ignored){}
   if(connected!=homeCastLastConnected || homeCastIcon.getTag()==null){
    homeCastLastConnected=connected;
    homeCastIcon.setImageResource(connected?R.drawable.ic_cast_conectado:R.drawable.ic_cast_desconectado);
    homeCastIcon.setContentDescription(connected?"TV conectada. Abrir controles.":"Conectar à TV");
    homeCastIcon.setTag(Boolean.valueOf(connected));
   }
   handler.postDelayed(this,1200);
  }
 };
 private void showHomeCastDevices(){
  try{
   com.google.android.gms.cast.framework.CastContext context =
       com.google.android.gms.cast.framework.CastContext.getSharedInstance(this);
   com.google.android.gms.cast.framework.CastSession current =
       context.getSessionManager().getCurrentCastSession();
   if(current!=null && current.isConnected()){
    String device=current.getCastDevice()!=null?current.getCastDevice().getFriendlyName():"TV";
    new AlertDialog.Builder(this).setTitle("TV conectada")
      .setMessage("Conectado a "+device+". Ao abrir um conteúdo compatível, ele será enviado para a TV.")
      .setPositiveButton("OK",null)
      .setNegativeButton("Desconectar",(dialog,which)->context.getSessionManager().endCurrentSession(true))
      .show();
    return;
   }
   androidx.mediarouter.media.MediaRouter router=androidx.mediarouter.media.MediaRouter.getInstance(this);
   androidx.mediarouter.media.MediaRouteSelector selector=context.getMergedSelector();
   ArrayList<androidx.mediarouter.media.MediaRouter.RouteInfo> routes=new ArrayList<>();
   ArrayList<String> names=new ArrayList<>();
   for(androidx.mediarouter.media.MediaRouter.RouteInfo route:router.getRoutes()){
    if(route.isEnabled()&&!route.isDefault()&&route.matchesSelector(selector)){
     routes.add(route);names.add(String.valueOf(route.getName()));
    }
   }
   if(routes.isEmpty()){
    new AlertDialog.Builder(this).setTitle("Google Cast")
      .setMessage("Nenhuma TV encontrada. Confirme que o Chromecast está ligado e na mesma rede Wi-Fi.")
      .setPositiveButton("OK",null).show();
    return;
   }
   new AlertDialog.Builder(this).setTitle("Conectar à TV")
     .setItems(names.toArray(new String[0]),(dialog,which)->{
      try{routes.get(which).select();}
      catch(Exception e){
       android.util.Log.e("ReelSeriesCast","Erro ao conectar à TV",e);
       Toast.makeText(this,"Não foi possível conectar à TV.",Toast.LENGTH_LONG).show();
      }
     }).setNegativeButton("Cancelar",null).show();
  }catch(Exception e){
   android.util.Log.e("ReelSeriesCast","Falha no botão Cast inicial",e);
   new AlertDialog.Builder(this).setTitle("Google Cast")
     .setMessage("Não foi possível abrir a conexão com a TV. "+e.getClass().getSimpleName())
     .setPositiveButton("OK",null).show();
  }
 }
}
