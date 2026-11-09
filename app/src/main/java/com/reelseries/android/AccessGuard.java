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
  if(u==null){dismiss();return;}
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
  if(gate!=null&&gate.isShowing()&&gate.getOwnerActivity()==a){
   TextView label=gate.findViewById(911003);
   if(label!=null)label.setText(reason);
   return;
  }
  dismiss();
  // Close the player/details before displaying the blocking screen on the home page.
  if(!(a instanceof MainActivity)&&!(a instanceof ProfileActivity)){
   a.startActivity(new Intent(a,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));
   a.finish();return;
  }
  Dialog d=new Dialog(a,android.R.style.Theme_Black_NoTitleBar_Fullscreen);
  d.setOwnerActivity(a);d.setCancelable(false);d.setCanceledOnTouchOutside(false);
  LinearLayout panel=new LinearLayout(a);panel.setOrientation(1);panel.setGravity(Gravity.CENTER);
  panel.setPadding(32,40,32,40);panel.setBackgroundColor(0xff09090c);
  TextView title=new TextView(a);title.setText("REEL SÉRIES");title.setTextColor(0xffef4356);
  title.setTextSize(30);title.setGravity(Gravity.CENTER);title.setTypeface(null,1);panel.addView(title);
  TextView message=new TextView(a);message.setId(911003);message.setText(reason);message.setTextSize(19);
  message.setTextColor(Color.WHITE);message.setGravity(Gravity.CENTER);message.setPadding(0,35,0,35);panel.addView(message);
  Button retry=new Button(a);retry.setText("Verificar novamente");panel.addView(retry);
  retry.setOnClickListener(v->{FirebaseUser user=FirebaseAuth.getInstance().getCurrentUser();if(user!=null){uid="";connect(user);}});
  Button signout=new Button(a);signout.setText("Sair da conta");panel.addView(signout);
  signout.setOnClickListener(v->{FirebaseAuth.getInstance().signOut();dismiss();a.startActivity(new Intent(a,ProfileActivity.class));});
  Button whatsapp=new Button(a);
  whatsapp.setText("✆  RENOVAR PELO WHATSAPP");
  whatsapp.setAllCaps(false);
  whatsapp.setTextSize(17);
  whatsapp.setTextColor(Color.WHITE);
  android.graphics.drawable.GradientDrawable green=new android.graphics.drawable.GradientDrawable();
  green.setColor(0xff128c4a);green.setCornerRadius(22f*a.getResources().getDisplayMetrics().density);
  whatsapp.setBackground(green);
  LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(-1,(int)(54*a.getResources().getDisplayMetrics().density));
  wp.topMargin=(int)(18*a.getResources().getDisplayMetrics().density);
  panel.addView(whatsapp,wp);
  whatsapp.setOnClickListener(v->{
   String text="Olá! Sou cliente do Reel Séries. Minha assinatura está vencida ou meu acesso foi bloqueado. Gostaria de renovar meu plano. Pode me ajudar?";
   String url="https://wa.me/5543988402261?text="+android.net.Uri.encode(text);
   try{a.startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(url)));}
   catch(Exception e){Toast.makeText(a,"Não foi possível abrir o WhatsApp. Contato: (43) 98840-2261",Toast.LENGTH_LONG).show();}
  });
  if(pending){signout.setVisibility(View.GONE);whatsapp.setVisibility(View.GONE);}
  d.setContentView(panel);gate=d;d.show();
  if(d.getWindow()!=null)d.getWindow().setLayout(-1,-1);
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
 private void dismiss(){if(gate!=null){try{gate.dismiss();}catch(Exception ignored){}gate=null;}}
 @Override public void onActivityResumed(Activity a){current=a;render(a);}
 @Override public void onActivityPaused(Activity a){if(current==a){current=null;dismiss();}}
 @Override public void onActivityCreated(Activity a,Bundle b){}
 @Override public void onActivityStarted(Activity a){}
 @Override public void onActivityStopped(Activity a){}
 @Override public void onActivitySaveInstanceState(Activity a,Bundle b){}
 @Override public void onActivityDestroyed(Activity a){if(current==a){current=null;dismiss();}}
}
