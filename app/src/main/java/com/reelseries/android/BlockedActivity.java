package com.reelseries.android;
import android.app.*;import android.content.*;import android.graphics.Color;import android.graphics.drawable.GradientDrawable;import android.net.Uri;import android.os.*;import android.view.*;import android.widget.*;
public class BlockedActivity extends Activity {
 TextView status; Button signout,whatsapp; boolean unlocking=false;
 @Override public void onCreate(Bundle b){super.onCreate(b);
  getWindow().setStatusBarColor(0xff09090c);getWindow().setNavigationBarColor(0xff09090c);
  LinearLayout panel=new LinearLayout(this);panel.setOrientation(1);panel.setGravity(Gravity.CENTER);
  panel.setPadding(dp(25),dp(40),dp(25),dp(40));panel.setBackgroundColor(0xff09090c);
  TextView title=new TextView(this);title.setText("REEL SÉRIES");title.setTextColor(0xffef4356);title.setTextSize(30);title.setTypeface(null,1);title.setGravity(Gravity.CENTER);panel.addView(title);
  TextView lock=new TextView(this);lock.setText("🔒");lock.setTextSize(45);lock.setGravity(Gravity.CENTER);lock.setPadding(0,dp(22),0,0);panel.addView(lock);
  status=new TextView(this);status.setTextColor(Color.WHITE);status.setTextSize(19);status.setGravity(Gravity.CENTER);status.setPadding(0,dp(18),0,dp(24));panel.addView(status);
  Button retry=new Button(this);retry.setText("Verificar novamente");panel.addView(retry,new LinearLayout.LayoutParams(-1,dp(54)));
  retry.setOnClickListener(v->{com.google.firebase.auth.FirebaseUser u=com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();if(u!=null){status.setText("Verificando acesso...");com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("usuarios").document(u.getUid()).get().addOnFailureListener(e->status.setText("Não foi possível verificar. Tente novamente."));}});
  signout=new Button(this);signout.setText("Sair da conta");LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(54));sp.topMargin=dp(10);panel.addView(signout,sp);
  signout.setOnClickListener(v->{com.google.firebase.auth.FirebaseAuth.getInstance().signOut();unlocking=true;startActivity(new Intent(this,ProfileActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));finish();});
  whatsapp=new Button(this);whatsapp.setText("✆  RENOVAR PELO WHATSAPP");whatsapp.setAllCaps(false);whatsapp.setTextColor(Color.WHITE);whatsapp.setTextSize(17);
  GradientDrawable green=new GradientDrawable();green.setColor(0xff128c4a);green.setCornerRadius(dp(20));whatsapp.setBackground(green);
  LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(-1,dp(58));wp.topMargin=dp(18);panel.addView(whatsapp,wp);
  whatsapp.setOnClickListener(v->{String text="Olá! Sou cliente do Reel Séries. Minha assinatura está vencida ou meu acesso foi bloqueado. Gostaria de renovar meu plano. Pode me ajudar?";
   try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://wa.me/5543988402261?text="+Uri.encode(text))));}
   catch(Exception e){Toast.makeText(this,"Contato: (43) 98840-2261",Toast.LENGTH_LONG).show();}});
  setContentView(panel);updateReason(getIntent().getStringExtra("reason"),getIntent().getBooleanExtra("pending",true));
 }
 int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
 public void updateReason(String reason,boolean pending){if(status==null)return;status.setText(reason==null?"Verificando acesso...":reason);signout.setVisibility(pending?View.GONE:View.VISIBLE);whatsapp.setVisibility(pending?View.GONE:View.VISIBLE);}
 public void unlock(){if(unlocking||isFinishing())return;unlocking=true;startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));finish();}
 @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);updateReason(intent.getStringExtra("reason"),intent.getBooleanExtra("pending",true));}
 @Override public void onBackPressed(){} // No back navigation into the catalog while blocked.
}
