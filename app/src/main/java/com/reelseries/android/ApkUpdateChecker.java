package com.reelseries.android;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.widget.*;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Checks the public APK release independently of Firebase Authentication. */
public final class ApkUpdateChecker {
 private static final ExecutorService executor=Executors.newSingleThreadExecutor();
 private static long lastCheck=0L;
 private static boolean busy=false;
 private static int lastDialogVersion=0;
 private ApkUpdateChecker(){}
 public static void check(Activity activity){
  if(activity==null||activity.isFinishing()||!FirebaseCatalogSync.configured(activity))return;
  synchronized(ApkUpdateChecker.class){
   long now=System.currentTimeMillis();
   if(busy||now-lastCheck<5000L)return;
   busy=true;lastCheck=now;
  }
  executor.execute(()->{
   try{
    JSONObject release=FirebaseCatalogSync.apkRelease(activity);
    int latest=release.optInt("versionCode",0);
    String url=release.optString("url","").trim();
    int installed=activity.getPackageManager().getPackageInfo(activity.getPackageName(),0).versionCode;
    if(latest<=installed||!url.startsWith("https://"))return;
    synchronized(ApkUpdateChecker.class){if(lastDialogVersion==latest)return;}
    activity.runOnUiThread(()->{
     if(activity.isFinishing()||activity.isDestroyed())return;
     synchronized(ApkUpdateChecker.class){if(lastDialogVersion==latest)return;lastDialogVersion=latest;}
     LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);
     int pad=(int)(16*activity.getResources().getDisplayMetrics().density);
     body.setPadding(pad,pad,pad,pad);
     ImageView art=new ImageView(activity);art.setImageResource(R.drawable.reelseries_update_art);
     art.setAdjustViewBounds(true);art.setScaleType(ImageView.ScaleType.FIT_CENTER);
     body.addView(art,new LinearLayout.LayoutParams(-1,(int)(220*activity.getResources().getDisplayMetrics().density)));
     TextView description=new TextView(activity);
     description.setText("Nova versão disponível (código "+latest+"). Atualize o Reel Séries para continuar com a versão mais recente.");
     description.setTextSize(15);description.setPadding(0,pad,0,0);body.addView(description);
     new AlertDialog.Builder(activity).setTitle("Atualização do Reel Séries")
      .setView(body).setNegativeButton("Depois",(dialog,which)->{synchronized(ApkUpdateChecker.class){lastDialogVersion=0;}})
      .setPositiveButton("Atualizar agora",(dialog,which)->{
       try{
        DownloadManager.Request request=new DownloadManager.Request(Uri.parse(url));
        request.setTitle("Reel Séries - atualização");
        request.setDescription("Abra o download concluído para instalar");
        request.setMimeType("application/vnd.android.package-archive");
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        ((DownloadManager)activity.getSystemService(Context.DOWNLOAD_SERVICE)).enqueue(request);
        Toast.makeText(activity,"Baixando atualização. Abra a notificação ao concluir.",Toast.LENGTH_LONG).show();
       }catch(Exception e){new AlertDialog.Builder(activity).setMessage("Não foi possível baixar: "+e.getMessage()).setPositiveButton("OK",null).show();}
      }).show();
    });
   }catch(Exception e){
    android.util.Log.e("ReelSeriesUpdate","Falha na consulta ao Firebase: atualizacoes/apk_update",e);
    activity.runOnUiThread(()->{if(!activity.isFinishing()&&!activity.isDestroyed())Toast.makeText(activity,"Falha ao verificar atualização: "+e.getMessage(),Toast.LENGTH_LONG).show();});
   }
   finally{synchronized(ApkUpdateChecker.class){busy=false;}}
  });
 }
}
