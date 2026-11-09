package com.reelseries.android;
import android.app.*;import android.os.*;import android.content.*;import android.graphics.Color;import android.view.*;import android.widget.*;
import com.google.firebase.auth.*;import com.google.firebase.firestore.*;
public class ProfileActivity extends Activity {
 LinearLayout root; FirebaseAuth auth; FirebaseFirestore db; TextView identity, vip; int bg=0xff0c0c10;
 int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
 TextView label(String text,int size,int color){TextView t=new TextView(this);t.setText(text);t.setTextSize(size);t.setTextColor(color);t.setPadding(dp(6),dp(12),dp(6),dp(12));return t;}
 void button(String text,View.OnClickListener click){
 Button b=new Button(this);b.setAllCaps(false);b.setText(text);b.setTextSize(16);
 b.setTextColor(Color.WHITE);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff292932));
 LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(52));lp.bottomMargin=dp(9);
 root.addView(b,lp);b.setOnClickListener(click);
}
 @Override public void onCreate(Bundle b){super.onCreate(b);auth=FirebaseAuth.getInstance();db=FirebaseFirestore.getInstance();getWindow().setStatusBarColor(bg);ScrollView scroll=new ScrollView(this);root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(22),dp(18),dp(22),dp(28));root.setBackgroundColor(bg);scroll.addView(root);setContentView(scroll);render();}
 void render(){root.removeAllViews();TextView title=label("◉  MEU PERFIL",27,0xffef4356);title.setTypeface(null,1);root.addView(title);identity=label("Conta não conectada",17,Color.WHITE);root.addView(identity);vip=label("Assinatura: não verificada",15,0xffb7c2d1);root.addView(vip);
 FirebaseUser user=auth.getCurrentUser();if(user!=null){identity.setText(user.getEmail()==null?"Usuário conectado":user.getEmail());db.collection("usuarios").document(user.getUid()).get().addOnSuccessListener(d->{if(isFinishing())return;String name=d.getString("nome");if(name!=null&&!name.isEmpty())identity.setText(name+"\n"+user.getEmail());Boolean active=d.getBoolean("vipAtivo");vip.setText(Boolean.TRUE.equals(active)?"VIP informado como ativo no Firebase":"VIP não ativo ou não configurado");}).addOnFailureListener(e->vip.setText("Não foi possível consultar a assinatura"));button("Sair da conta",v->{auth.signOut();render();});}
 else{EditText email=new EditText(this);email.setSingleLine();email.setHint("E-mail");email.setTextColor(-1);email.setHintTextColor(0xffaaaaaa);root.addView(email);EditText pass=new EditText(this);pass.setHint("Senha");pass.setTextColor(-1);pass.setHintTextColor(0xffaaaaaa);pass.setInputType(129);root.addView(pass);TextView msg=label("",13,0xffff8b96);root.addView(msg);button("Entrar",v->auth.signInWithEmailAndPassword(email.getText().toString().trim(),pass.getText().toString()).addOnSuccessListener(r->render()).addOnFailureListener(e->msg.setText(e.getMessage())));button("Criar conta",v->auth.createUserWithEmailAndPassword(email.getText().toString().trim(),pass.getText().toString()).addOnSuccessListener(r->render()).addOnFailureListener(e->msg.setText(e.getMessage())));}
 TextView library=label("MINHA BIBLIOTECA",19,Color.WHITE);library.setTypeface(null,1);root.addView(library);button("♥  Minha lista",v->startActivity(new Intent(this,FavoritesActivity.class)));button("▶  Continuar assistindo",v->startActivity(new Intent(this,ContinueWatchingActivity.class)));button("♛  Minha assinatura",v->new AlertDialog.Builder(this).setMessage(vip.getText()).setPositiveButton("OK",null).show());TextView security=label("SEGURANÇA E PRIVACIDADE",19,Color.WHITE);security.setTypeface(null,1);root.addView(security);
 root.addView(label("Proteja as categorias adultas com um PIN de 4 números. O PIN inicial é 0000. Após alterar, o PIN anterior deixa de funcionar.",14,0xffbfc0ca));
 button("🔒  Alterar PIN de conteúdo adulto",v->AdultProtection.changePin(this));
 button("🔐  Bloquear conteúdo adulto agora",v->{AdultProtection.lock();Toast.makeText(this,"Conteúdo adulto bloqueado.",Toast.LENGTH_SHORT).show();});
 button("⚙  Configurações",v->new AlertDialog.Builder(this).setItems(new String[]{"Sobre o aplicativo","Voltar"},(d,w)->{if(w==0)new AlertDialog.Builder(this).setMessage("Reel Séries Android 19.5").setPositiveButton("OK",null).show();}).show());button("🛡  Administração",v->startActivity(new Intent(this,AdminActivity.class)));}

}
