package com.reelseries.android;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;

/** Tela inicial: conclui a consulta antes de liberar a Home. */
public class UpdateGateActivity extends Activity {
 private boolean completed=false;
 private boolean resumed=false;
 private boolean showing=false;
 private boolean checking=false;
 private int pendingVersion=0;
 private String pendingUrl=null;
 private String pendingError=null;
 private LinearLayout root;

 private int dp(int value){return (int)(value*getResources().getDisplayMetrics().density+.5f);}
 private GradientDrawable shape(int color,int radius){
  GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;
 }
 private TextView text(String value,int size,int color,boolean bold){
  TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);
  if(bold)t.setTypeface(null,Typeface.BOLD);t.setGravity(Gravity.CENTER);return t;
 }
 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  getWindow().setStatusBarColor(0xff0b0b0e);
  getWindow().setNavigationBarColor(0xff0b0b0e);
  root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
  root.setGravity(Gravity.CENTER);root.setPadding(dp(24),dp(24),dp(24),dp(24));
  root.setBackgroundColor(0xff0b0b0e);
  setContentView(root);
  renderChecking();
  check();
 }
 @Override protected void onResume(){
  super.onResume();resumed=true;showPending();
 }
 @Override protected void onPause(){resumed=false;super.onPause();}
 private void renderChecking(){
  root.removeAllViews();
  TextView title=text("REEL SÉRIES",28,0xfff24b64,true);
  root.addView(title);
  TextView sub=text("Verificando atualizações...",16,Color.WHITE,false);
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(18);
  root.addView(sub,lp);
  ProgressBar progress=new ProgressBar(this);
  LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(dp(40),dp(40));pp.topMargin=dp(22);
  root.addView(progress,pp);
 }
 private void check(){
  if(checking||completed)return;
  checking=true;pendingVersion=0;pendingUrl=null;pendingError=null;
  new Thread(()->{
   try{
    JSONObject data=FirebaseCatalogSync.apkRelease(this);
    int latest=data.optInt("versionCode",0);
    String url=data.optString("url","").trim();
    int installed=getPackageManager().getPackageInfo(getPackageName(),0).versionCode;
    android.util.Log.i("ReelSeriesUpdate","Instalado="+installed+" publicado="+latest);
    runOnUiThread(()->{
     checking=false;
     if(completed||isFinishing()||isDestroyed())return;
     if(latest>installed){
      if(url.startsWith("https://")){pendingVersion=latest;pendingUrl=url;}
      else pendingError="A versão "+latest+" foi publicada sem um link HTTPS válido.";
     }else{proceed();return;}
     showPending();
    });
   }catch(Exception e){
    android.util.Log.e("ReelSeriesUpdate","Erro ao consultar atualizacoes/apk_update",e);
    runOnUiThread(()->{
     checking=false;
     if(completed||isFinishing()||isDestroyed())return;
     pendingError="Falha ao consultar o Firebase:\n"+e.getMessage();
     showPending();
    });
   }
  },"reel-update-first").start();
 }
 private void showPending(){
  if(!resumed||showing||completed||isFinishing()||isDestroyed())return;
  if(pendingError!=null){
   showing=true;
   new AlertDialog.Builder(this).setTitle("Verificação de atualizações")
    .setMessage(pendingError)
    .setNegativeButton("Continuar",(d,w)->{showing=false;proceed();})
    .setPositiveButton("Tentar novamente",(d,w)->{showing=false;renderChecking();check();})
    .setCancelable(false).show();
  }else if(pendingUrl!=null){
   showing=true;showUpdate(pendingVersion,pendingUrl);
  }
 }
 private void showUpdate(int version,String url){
  LinearLayout content=new LinearLayout(this);
  content.setOrientation(LinearLayout.VERTICAL);
  content.setPadding(dp(20),dp(18),dp(20),dp(8));
  content.setGravity(Gravity.CENTER_HORIZONTAL);
  ImageView cover=new ImageView(this);
  cover.setImageResource(R.drawable.reelseries_update_art);
  cover.setAdjustViewBounds(true);cover.setScaleType(ImageView.ScaleType.FIT_CENTER);
  content.addView(cover,new LinearLayout.LayoutParams(-1,dp(180)));
  TextView title=text("NOVA ATUALIZAÇÃO",21,0xfff24b64,true);
  LinearLayout.LayoutParams tlp=new LinearLayout.LayoutParams(-1,-2);tlp.topMargin=dp(12);
  content.addView(title,tlp);
  TextView description=text("Uma nova versão do Reel Séries está disponível.\nCódigo da versão: "+version,15,0xffe7e7ef,false);
  LinearLayout.LayoutParams dlp=new LinearLayout.LayoutParams(-1,-2);dlp.topMargin=dp(12);
  content.addView(description,dlp);
  AlertDialog dialog=new AlertDialog.Builder(this)
   .setView(content)
   .setNegativeButton("Depois",(d,w)->{showing=false;proceed();})
   .setPositiveButton("Baixar APK",(d,w)->{
    showing=false;
    try{
     DownloadManager.Request req=new DownloadManager.Request(Uri.parse(url));
     req.setTitle("Atualização Reel Séries");
     req.setDescription("Abra o download concluído para instalar o APK");
     req.setMimeType("application/vnd.android.package-archive");
     req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
     ((DownloadManager)getSystemService(Context.DOWNLOAD_SERVICE)).enqueue(req);
     Toast.makeText(this,"Download iniciado. Abra a notificação para instalar.",Toast.LENGTH_LONG).show();
    }catch(Exception e){Toast.makeText(this,"Falha no download: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    proceed();
   }).setCancelable(false).create();
  dialog.setOnShowListener(d->{
   dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(0xfff24b64);
   dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(0xffb7c2d1);
  });
  dialog.show();
 }
 private void proceed(){
  if(completed||isFinishing()||isDestroyed())return;
  completed=true;
  Class<?> next=com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser()==null
   ?ProfileActivity.class:MainActivity.class;
  startActivity(new Intent(this,next));
  finish();
 }
 @Override public void onBackPressed(){
  // Evita que o botao Voltar leve a Home antes da decisao sobre atualizacao.
  if(completed)super.onBackPressed();
 }
}
