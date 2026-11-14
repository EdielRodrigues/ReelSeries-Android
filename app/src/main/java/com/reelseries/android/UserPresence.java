package com.reelseries.android;
import android.app.*;
import android.os.*;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.*;
import java.util.*;
/** Best-effort foreground presence, independent from login/session authorization. */
public final class UserPresence implements Application.ActivityLifecycleCallbacks {
 private final Handler handler=new Handler(Looper.getMainLooper());
 private int started=0;
 private String uid="";
 private final Runnable heartbeat=new Runnable(){public void run(){if(started>0){write(true);handler.postDelayed(this,60000);}}};
 public static void install(Application app){app.registerActivityLifecycleCallbacks(new UserPresence());}
 private void write(boolean active){
  FirebaseUser user=FirebaseAuth.getInstance().getCurrentUser();if(user==null)return;
  Map<String,Object> data=new HashMap<>();
  data.put("ultimaAtividade",FieldValue.serverTimestamp());data.put("appEmUso",active);
  FirebaseFirestore.getInstance().collection("usuarios").document(user.getUid()).update(data)
   .addOnFailureListener(e->android.util.Log.w("ReelPresence","Presença indisponível",e));
 }
 @Override public void onActivityStarted(Activity a){if(++started==1){handler.removeCallbacks(heartbeat);write(true);handler.postDelayed(heartbeat,60000);}}
 @Override public void onActivityStopped(Activity a){started=Math.max(0,started-1);if(started==0){handler.removeCallbacks(heartbeat);write(false);}}
 @Override public void onActivityCreated(Activity a,Bundle b){}
 @Override public void onActivityResumed(Activity a){}
 @Override public void onActivityPaused(Activity a){}
 @Override public void onActivitySaveInstanceState(Activity a,Bundle b){}
 @Override public void onActivityDestroyed(Activity a){}
}
