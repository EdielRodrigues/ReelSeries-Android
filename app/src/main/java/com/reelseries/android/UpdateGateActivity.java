package com.reelseries.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.TextView;
import org.json.JSONObject;

/** Launcher: checks updates before opening login or Home. */
public class UpdateGateActivity extends Activity {
 private boolean finishedCheck=false;
 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  getWindow().setStatusBarColor(0xff0b0b0e);
  TextView wait=new TextView(this);
  wait.setText("REEL SÉRIES\n\nVerificando atualizações...");
  wait.setTextColor(Color.WHITE);wait.setTextSize(20);wait.setGravity(Gravity.CENTER);
  wait.setBackgroundColor(0xff0b0b0e);setContentView(wait);
  new Thread(()->{
   try{
    JSONObject release=FirebaseCatalogSync.apkRelease(this);
    int latest=release.optInt("versionCode",0);
    String url=release.optString("url","").trim();
    int installed=getPackageManager().getPackageInfo(getPackageName(),0).versionCode;
    android.util.Log.i("ReelSeriesUpdate","Inicial: instalada="+installed+", publicada="+latest+", urlValida="+url.startsWith("https://"));
    if(latest>installed&&url.startsWith("https://")){
     runOnUiThread(()->showUpdate(latest,url));
    }else runOnUiThread(this::proceed);
   }catch(Exception error){
    android.util.Log.e("ReelSeriesUpdate","Erro na verificacao inicial",error);
    runOnUiThread(()->new AlertDialog.Builder(this)
     .setTitle("Não foi possível verificar atualizações")
     .setMessage("Verifique sua conexão com a internet.\n\n"+error.getMessage())
     .setNegativeButton("Continuar",(d,w)->proceed())
     .setPositiveButton("Tentar novamente",(d,w)->checkAgain())
     .setCancelable(false).show());
   }
  },"reel-update-gate").start();
 }
 private void checkAgain(){
  if(isFinishing()||isDestroyed())return;
  Intent i=new Intent(this,UpdateGateActivity.class);
  startActivity(i);finish();
 }
 private void showUpdate(int version,String url){
  if(isFinishing()||isDestroyed())return;
  new AlertDialog.Builder(this)
   .setTitle("Atualização disponível")
   .setMessage("Uma nova versão do Reel Séries está disponível (código "+version+"). Deseja baixar agora?")
   .setNegativeButton("Depois",(d,w)->proceed())
   .setPositiveButton("Baixar atualização",(d,w)->{
    try{
     DownloadManager.Request req=new DownloadManager.Request(Uri.parse(url));
     req.setTitle("Atualização Reel Séries");
     req.setDescription("Baixando APK da nova versão");
     req.setMimeType("application/vnd.android.package-archive");
     req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
     ((DownloadManager)getSystemService(Context.DOWNLOAD_SERVICE)).enqueue(req);
     proceed();
    }catch(Exception e){
     new AlertDialog.Builder(this).setTitle("Falha ao iniciar download")
      .setMessage(e.getMessage()).setPositiveButton("Continuar",(x,y)->proceed()).show();
    }
   }).setCancelable(false).show();
 }
 private void proceed(){
  if(finishedCheck||isFinishing()||isDestroyed())return;
  finishedCheck=true;
  Class<?> next=com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser()==null
   ?ProfileActivity.class:MainActivity.class;
  startActivity(new Intent(this,next));finish();
 }
}
