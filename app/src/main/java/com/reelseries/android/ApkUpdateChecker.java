package com.reelseries.android;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.database.Cursor;
import androidx.core.content.FileProvider;
import java.io.File;
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
     ProgressBar progress=new ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal);
     progress.setMax(100);progress.setVisibility(android.view.View.GONE);
     LinearLayout.LayoutParams progressParams=new LinearLayout.LayoutParams(-1,(int)(12*activity.getResources().getDisplayMetrics().density));
     progressParams.topMargin=pad;body.addView(progress,progressParams);
     TextView progressText=new TextView(activity);progressText.setTextSize(13);body.addView(progressText);
     AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Atualização do Reel Séries")
      .setView(body).setNegativeButton("Depois",(d,w)->{synchronized(ApkUpdateChecker.class){lastDialogVersion=0;}})
      .setPositiveButton("Atualizar agora",null).create();
     final File[] downloadedApk=new File[1];
     dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
      if(Boolean.TRUE.equals(v.getTag())){
       File apk=downloadedApk[0];
       if(apk!=null)install(activity,apk);
       return;
      }
      v.setEnabled(false);
      progress.setVisibility(android.view.View.VISIBLE);
      progressText.setText("Iniciando download...");
      try{
       File apk=new File(activity.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS),"reelseries-update-"+latest+".apk");
       if(apk.exists()&&!apk.delete())throw new IllegalStateException("Não foi possível substituir download anterior");
       DownloadManager.Request request=new DownloadManager.Request(Uri.parse(url));
       request.setTitle("Reel Séries - atualização");
       request.setDescription("Atualização em andamento no aplicativo");
       request.setMimeType("application/vnd.android.package-archive");
       request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
       request.setDestinationUri(Uri.fromFile(apk));
       DownloadManager dm=(DownloadManager)activity.getSystemService(Context.DOWNLOAD_SERVICE);
       long downloadId=dm.enqueue(request);
       Handler handler=new Handler(Looper.getMainLooper());
       Runnable[] watcher=new Runnable[1];
       watcher[0]=()->{
        if(activity.isFinishing()||activity.isDestroyed()||!dialog.isShowing())return;
        try(Cursor c=dm.query(new DownloadManager.Query().setFilterById(downloadId))){
         if(c==null||!c.moveToFirst())throw new IllegalStateException("Download não encontrado");
         int status=c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
         long total=c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
         long done=c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
         if(total>0){int pct=(int)Math.min(100,done*100/total);progress.setIndeterminate(false);progress.setProgress(pct);progressText.setText("Baixando: "+pct+"%");}
         else{progress.setIndeterminate(true);progressText.setText("Baixando atualização...");}
         if(status==DownloadManager.STATUS_SUCCESSFUL){
          if(!apk.isFile()||apk.length()==0)throw new IllegalStateException("APK não encontrado após o download");
          progress.setIndeterminate(false);progress.setProgress(100);
          progressText.setText("Download concluído. Toque em INSTALAR AGORA.");
          android.widget.Button button=dialog.getButton(AlertDialog.BUTTON_POSITIVE);
          button.setText("Instalar agora");button.setTag(Boolean.TRUE);downloadedApk[0]=apk;button.setEnabled(true);
          return;
         }
         if(status==DownloadManager.STATUS_FAILED)throw new IllegalStateException("Falha no download (código "+c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))+")");
        }catch(Exception e){progressText.setText("Erro: "+e.getMessage());v.setEnabled(true);return;}
        handler.postDelayed(watcher[0],600);
       };
       handler.post(watcher[0]);
      }catch(Exception e){progressText.setText("Erro: "+e.getMessage());v.setEnabled(true);}
     }));
     dialog.show();
    });
   }catch(Exception e){
    android.util.Log.e("ReelSeriesUpdate","Falha na consulta ao Firebase: atualizacoes/apk_update",e);
    activity.runOnUiThread(()->{if(!activity.isFinishing()&&!activity.isDestroyed())Toast.makeText(activity,"Falha ao verificar atualização: "+e.getMessage(),Toast.LENGTH_LONG).show();});
   }
   finally{synchronized(ApkUpdateChecker.class){busy=false;}}
  });
 }
 private static void install(Activity activity,File apk){
  try{
   if(android.os.Build.VERSION.SDK_INT>=26&&!activity.getPackageManager().canRequestPackageInstalls()){
    Toast.makeText(activity,"Autorize a instalação de atualizações do Reel Séries e toque em INSTALAR AGORA novamente.",Toast.LENGTH_LONG).show();
    Intent settings=new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+activity.getPackageName()));
    activity.startActivity(settings);return;
   }
   Uri uri=FileProvider.getUriForFile(activity,activity.getPackageName()+".updateprovider",apk);
   Intent intent=new Intent(Intent.ACTION_VIEW);
   intent.setDataAndType(uri,"application/vnd.android.package-archive");
   intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
   activity.startActivity(intent);
  }catch(Exception e){new AlertDialog.Builder(activity).setTitle("Não foi possível instalar").setMessage(e.getMessage()).setPositiveButton("OK",null).show();}
 }

}
