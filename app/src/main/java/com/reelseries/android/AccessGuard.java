package com.reelseries.android;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.*;
import java.util.*;

/** Full-screen access gate, installed on every Activity, including playback. */
public final class AccessGuard implements Application.ActivityLifecycleCallbacks {
 private final Application app;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private Activity current;
 private Dialog gate;
 private String uid="";
 private ListenerRegistration registration;
 private boolean admin=false, adminKnown=false, profileKnown=false, blocked=false, vipEnabled=false;
 private String reason="";
 private long expiry=0;
 private String warnedKey="";
 private final Runnable tick=new Runnable(){public void run(){if(current!=null)render(current);handler.postDelayed(this,15000);}};
 private AccessGuard(Application a){app=a;a.registerActivityLifecycleCallbacks(this);
  FirebaseAuth.getInstance().addAuthStateListener(auth->connect(auth.getCurrentUser()));
  handler.post(tick);
 }
 public static void install(Application a){new AccessGuard(a);}
 private void connect(FirebaseUser user){
  String next=user==null?"":user.getUid();
  if(next.equals(uid))return;
  if(registration!=null){registration.remove();registration=null;}
  uid=next;admin=false;adminKnown=false;profileKnown=false;blocked=false;vipEnabled=false;expiry=0;reason="";
  dismiss();
  if(next.isEmpty()){if(current!=null)render(current);return;}
  FirebaseFirestore fs=FirebaseFirestore.getInstance();
  fs.collection("admins").document(next).get().addOnCompleteListener(t->{
   if(!next.equals(uid))return;
   admin=t.isSuccessful()&&t.getResult()!=null&&t.getResult().exists()&&
    Boolean.TRUE.equals(t.getResult().getBoolean("active"))&&
    "admin".equals(t.getResult().getString("role"));
   adminKnown=true;if(current!=null)render(current);
  });
  registration=fs.collection("usuarios").document(next).addSnapshotListener((doc,error)->{
   if(!next.equals(uid))return;
   if(error!=null){profileKnown=false;if(current!=null)render(current);return;}
   if(doc==null){profileKnown=false;if(current!=null)render(current);return;}
   profileKnown=true;
   blocked=doc.exists()&&Boolean.TRUE.equals(doc.getBoolean("bloqueado"));
   vipEnabled=doc.exists()&&Boolean.TRUE.equals(doc.getBoolean("vipAtivo"));
   com.google.firebase.Timestamp until=doc.exists()?doc.getTimestamp("vipAte"):null;
   expiry=until==null?0:until.toDate().getTime();
   if(current!=null)render(current);
  });
 }
 private void render(Activity a){
  if(a.isFinishing()||a.isDestroyed())return;
  FirebaseUser u=FirebaseAuth.getInstance().getCurrentUser();
  if(u==null){
   // No anonymous access to the catalog, player or administration.
   if(!(a instanceof ProfileActivity)){
    Intent login=new Intent(a,ProfileActivity.class);
    login.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
    a.startActivity(login);
    a.finish();
   }
   return;
  }
  if(!u.getUid().equals(uid)){connect(u);return;}
  // Never expose content while authorization is being checked.
  boolean pending=!adminKnown||(!admin&&!profileKnown);
  // Access requires an active subscription with an explicit future expiry.
  boolean expired=!vipEnabled||expiry<=0||System.currentTimeMillis()>=expiry;
  if(admin){dismiss();return;}
  if(pending||blocked||expired){
   reason=pending?"Verificando acesso...":blocked?"Sua conta foi bloqueada pela administração.":"Sua assinatura está vencida ou sem validade ativa. Solicite a renovação ao administrador.";
   showGate(a,pending);
  }else{
   dismiss();
   long left=expiry-System.currentTimeMillis();
   if(expiry>0&&left>0&&left<=3L*24*60*60*1000)warn(a,left);
  }
 }
 private void showGate(Activity a,boolean pending){
  if(a instanceof BlockedActivity){((BlockedActivity)a).updateReason(reason,pending);return;}
  Intent intent=new Intent(a,BlockedActivity.class);
  intent.putExtra("reason",reason);intent.putExtra("pending",pending);
  intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
  a.startActivity(intent);
  if(a instanceof PlayerActivity)a.finish();
 }
 private void warn(Activity a,long left){
  long days=(left+24L*60*60*1000-1)/(24L*60*60*1000);
  String key=uid+":"+java.text.DateFormat.getDateInstance().format(new Date())+":"+days;
  if(key.equals(warnedKey))return;
  warnedKey=key;
  new AlertDialog.Builder(a).setTitle("Sua assinatura está vencendo")
   .setMessage("Faltam "+days+" dia"+(days==1?"":"s")+" para vencer sua assinatura. Entre em contato com o administrador para renovar.")
   .setPositiveButton("Entendi",null).show();
 }
 private void dismiss(){
  if(current instanceof BlockedActivity && !uid.isEmpty()){
   ((BlockedActivity)current).unlock();
  }
 }
 @Override public void onActivityResumed(Activity a){current=a;render(a);}
 @Override public void onActivityPaused(Activity a){if(current==a){current=null;dismiss();}}
 @Override public void onActivityCreated(Activity a,Bundle b){}
 @Override public void onActivityStarted(Activity a){}
 @Override public void onActivityStopped(Activity a){}
 @Override public void onActivitySaveInstanceState(Activity a,Bundle b){}
 @Override public void onActivityDestroyed(Activity a){if(current==a){current=null;dismiss();}}
}
