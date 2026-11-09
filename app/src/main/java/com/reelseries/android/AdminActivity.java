package com.reelseries.android;

import android.app.*;
import android.os.*;
import android.content.*;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import javax.net.ssl.SSLException;
import android.view.*;
import android.widget.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class AdminActivity extends Activity {
 private static final int JSON=81,M3U=82,EXPORT=83;
 private final FirebaseAuth auth=FirebaseAuth.getInstance();
 private final FirebaseFirestore firestore=FirebaseFirestore.getInstance();
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private LinearLayout root;private TextView status;private ProgressBar deleteBar;private TextView deleteCounter;private boolean authorized=false,busy=false;
 private LocalCatalog catalog;
 @Override public void onCreate(Bundle b){super.onCreate(b);catalog=new LocalCatalog(this);login();}
 private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
 private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
 private void screen(String title){
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(0xff0b101b);
  root=new LinearLayout(this);root.setPadding(dp(18),dp(22),dp(18),dp(36));root.setOrientation(1);scroll.addView(root);setContentView(scroll);
  TextView brand=new TextView(this);brand.setText("▣  REEL SÉRIES  /  ADMIN");brand.setTextColor(0xfffb536c);brand.setTextSize(14);brand.setTypeface(null,1);root.addView(brand);
  TextView h=new TextView(this);h.setText(title);h.setTextSize(27);h.setTypeface(null,1);h.setTextColor(Color.WHITE);h.setPadding(0,dp(8),0,dp(14));root.addView(h);
  status=new TextView(this);status.setTextColor(0xffc9d3e3);status.setTextSize(14);status.setPadding(dp(14),dp(13),dp(14),dp(13));status.setBackground(shape(0xff192438,14));root.addView(status);
 }
 private EditText field(String hint){EditText e=new EditText(this);e.setHint(hint);e.setTextColor(-1);e.setHintTextColor(0xff99a5ba);e.setTextSize(15);e.setSingleLine(false);e.setPadding(dp(14),dp(12),dp(14),dp(12));e.setBackground(shape(0xff1b273a,12));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(10);root.addView(e,lp);return e;}
 private void button(String label,Runnable action){TextView b=new TextView(this);b.setText(label);b.setTextSize(15);b.setTypeface(null,1);b.setTextColor(Color.WHITE);b.setGravity(Gravity.CENTER_VERTICAL);b.setPadding(dp(17),dp(15),dp(12),dp(15));b.setMinHeight(dp(54));b.setBackground(shape(0xff202d42,13));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(9);root.addView(b,lp);b.setOnClickListener(v->action.run());}
 private void section(String title){TextView t=new TextView(this);t.setText(title.toUpperCase(java.util.Locale.ROOT));t.setTextSize(13);t.setTypeface(null,1);t.setTextColor(0xffff627a);t.setPadding(0,dp(23),0,dp(5));root.addView(t);}
 private void msg(String s){runOnUiThread(()->{if(!isFinishing()&&status!=null)status.setText(s);});}
 private void showDeletionProgress(String title){
  runOnUiThread(()->{
   screen(title);
   status.setText("Preparando exclusão...");
   deleteCounter=new TextView(this);deleteCounter.setTextColor(Color.WHITE);deleteCounter.setTextSize(18);
   deleteCounter.setPadding(0,dp(18),0,dp(8));deleteCounter.setText("0 itens excluídos");root.addView(deleteCounter);
   deleteBar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
   deleteBar.setMax(1000);deleteBar.setProgress(0);
   deleteBar.setProgressTintList(android.content.res.ColorStateList.valueOf(0xffef4356));
   root.addView(deleteBar,new LinearLayout.LayoutParams(-1,dp(12)));
   button("← Voltar ao painel",()->{if(!busy)settingsPanel();else msg("Aguarde a conclusão da exclusão.");});
  });
 }
 private void deletionProgress(int done,int total,String folder){
  runOnUiThread(()->{
   if(isFinishing())return;
   if(deleteCounter!=null)deleteCounter.setText(done+" / "+total+" itens excluídos"+(total>0?" • "+(done*100/total)+"%":""));
   if(deleteBar!=null){deleteBar.setIndeterminate(total==0);if(total>0)deleteBar.setProgress((int)((long)done*1000/total));}
   if(status!=null)status.setText("Excluindo: "+folder+" • lotes de até 200 registros");
  });
 }
 private void login(){authorized=false;screen("Acesso protegido");if(auth.getCurrentUser()!=null){verify();return;}EditText email=field("E-mail"),pass=field("Senha");pass.setInputType(129);button("Entrar",()->{msg("Autenticando...");auth.signInWithEmailAndPassword(email.getText().toString().trim(),pass.getText().toString()).addOnSuccessListener(x->verify()).addOnFailureListener(e->msg(e.getMessage()));});}
 private void verify(){if(auth.getCurrentUser()==null){login();return;}msg("Verificando administrador...");firestore.collection("admins").document(auth.getCurrentUser().getUid()).get().addOnSuccessListener(d->{if(d.exists()&&Boolean.TRUE.equals(d.getBoolean("active"))&&"admin".equals(d.getString("role"))){authorized=true;dashboard();seedBundledCatalog();}else{msg("Conta sem autorização.");button("Trocar conta",()->{auth.signOut();login();});}}).addOnFailureListener(e->msg("Erro de autorização: "+e.getMessage()));}
 private TextView panelText(String text,int size){TextView t=new TextView(this);t.setText(text);t.setTextColor(-1);t.setTextSize(size);t.setPadding(18,15,18,15);return t;}
 private LinearLayout tile(String icon,String value,String subtitle,Runnable action){
  LinearLayout tile=new LinearLayout(this);tile.setOrientation(1);tile.setPadding(dp(16),dp(16),dp(12),dp(16));tile.setBackground(shape(0xff08090d,18));tile.setMinimumHeight(dp(136));
  TextView glyph=new TextView(this);glyph.setText(icon);glyph.setTextColor(0xffa8afb9);glyph.setTextSize(22);tile.addView(glyph);
  TextView main=new TextView(this);main.setText(value);main.setTextColor(Color.WHITE);main.setTypeface(null,1);main.setTextSize(20);main.setPadding(0,dp(10),0,dp(6));tile.addView(main);
  TextView detail=new TextView(this);detail.setText(subtitle);detail.setTextColor(0xffaeb1b8);detail.setTextSize(13);tile.addView(detail);
  tile.setOnClickListener(v->action.run());return tile;
 }
 private void tileRow(LinearLayout left,LinearLayout right){LinearLayout row=new LinearLayout(this);row.setOrientation(0);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.topMargin=dp(10);root.addView(row,rp);LinearLayout.LayoutParams a=new LinearLayout.LayoutParams(0,-2,1);row.addView(left,a);LinearLayout.LayoutParams b=new LinearLayout.LayoutParams(0,-2,1);b.leftMargin=dp(10);row.addView(right,b);}
 private void dashboard(){if(!authorized){login();return;}
  screen("Central Administrativa");msg("Painel Premium V19.2  •  SQLite + Firebase Realtime Database");
  android.content.SharedPreferences xp=getSharedPreferences("xtream_admin",MODE_PRIVATE);
  if(xp.getBoolean("auto",false)&&!xp.getString("host","").isEmpty()&&System.currentTimeMillis()-xp.getLong("last_sync",0)>86400000L){
   xp.edit().putLong("last_sync",System.currentTimeMillis()).apply();xtreamStart("all");
  }
  tileRow(tile("▤",java.text.NumberFormat.getIntegerInstance(new java.util.Locale("pt","BR")).format(catalog.count()),"Registros locais",()->browseTitles(0)),tile("☷","M3U / M3U8","Importação",()->urlDialog(false)));
  tileRow(tile("▣","Catálogo","Gerenciamento",this::manageTitles),tile("⚙","Sistema","Configurações",this::settingsPanel));
  section("Usuários e permissões");button("👥  Administração de perfis • bloquear / desbloquear",this::manageUsers);button("🛡  Contas administrativas",this::manageAdmins);button("＋  Criar cliente",this::createCustomer);
  section("Catálogo online");button("☁  Enviar catálogo completo ao Firebase",()->syncAll());button("↓  Receber atualizações do Firebase",this::downloadCloud);button("☁  Ver configuração do Firebase",()->msg(FirebaseCatalogSync.configured(this)?"Firebase configurado • sincronização disponível":"Configure firebase_catalog_url em res/values/firebase_catalog.xml"));
  section("Sincronização por catálogo");
  button("↓  Receber somente Séries + episódios",()->downloadCategory("catalogo_1_series"));
  button("↓  Receber somente Filmes",()->downloadCategory("catalogo_2_filmes"));
  button("↓  Receber somente TV ao vivo",()->downloadCategory("catalogo_3_iptv"));
  section("Importar playlist");
  button("🔗  Adicionar link de playlist M3U",()->urlDialog(false));
  button("📺  Conexões Xtream • gerenciar e sincronizar",this::xtreamPanel);
  button("🔗  Adicionar IPTV por link get.php",this::iptvLinkDialog);
  button("⇧  Importar arquivo M3U ou JSON",()->new AlertDialog.Builder(this).setItems(new String[]{"Arquivo M3U","Arquivo JSON"},(d,w)->pick(w==0?M3U:JSON)).show());
  button("▶  Adicionar transmissão M3U8",()->urlDialog(true));
  section("Gerenciamento rápido");
  button("＋  Adicionar filme, série ou canal",this::manual);
  button("⌕  Pesquisar e editar títulos",this::manageTitles);
  button("▦  Ver todos os títulos",()->browseTitles(0));
  button("◉  Estatísticas e diagnósticos",this::statistics);
  button("⚙  Configurações e segurança",this::settingsPanel);
 }
 private void settingsPanel(){screen("Configurações do sistema");msg("Reel Séries • Administração local e acesso Firebase");
  section("Firebase Realtime Database");button("☁  Enviar catálogo completo",()->syncAll());button("↓  Receber atualizações do Firebase",this::downloadCloud);
  section("Atualização do aplicativo");button("⬆  Publicar link do novo APK no Firebase",this::publishApkDialog);
  section("TMDB • capas e informações");button("✦  Configurar servidor TMDB",this::configureTmdbServer);button("✦  Verificar capas faltantes (simulação)",()->startTmdbSync(true));button("✦  Atualizar capas e metadados ausentes",()->startTmdbSync(false));
  section("Importação");button("↻  Atualizar playlist por URL",()->urlDialog(false));button("⇧  Importar arquivo JSON",()->pick(JSON));button("⇧  Importar arquivo M3U",()->pick(M3U));
  section("Diagnósticos");button("⚠  Títulos sem vídeo",()->diagnostic("sem_video",0));button("▧  Títulos sem capa",()->diagnostic("sem_capa",0));button("≡  Categorias vazias",()->diagnostic("sem_categoria",0));
  section("Usuários e permissões");button("👥  Administração de perfis • bloquear / desbloquear",this::manageUsers);button("🛡  Contas administrativas",this::manageAdmins);button("＋  Criar cliente",this::createCustomer);
  section("Backup e segurança");button("⇩  Exportar backup JSON",this::exportCatalog);button("↻  Restaurar backup JSON e enviar ao Firebase",()->pick(JSON));button("▣  Escolher pasta do Firebase para excluir",()->browseFirebaseFolders(""));button("⚠  Excluir TODO o catálogo do Firebase e deste aparelho",this::confirmDeleteAll);button("♟  Permissões administrativas",()->new AlertDialog.Builder(this).setTitle("Acesso administrativo").setMessage("O acesso é validado no Firestore em admins/{uid}, com active=true e role=admin.").setPositiveButton("OK",null).show());button("↻  Atualizar painel",this::dashboard);button("Sair da conta",()->{auth.signOut();login();});button("←  Voltar",this::dashboard);button("←  Voltar ao Meu Perfil",()->finish());
 }
 private void manageUsers(){manageUsersPage(null);}
 private void manageUsersPage(com.google.firebase.firestore.DocumentSnapshot cursor){
  if(!authorized){login();return;}
  screen("Administração de perfis");
  msg("Contas cadastradas no aplicativo • 50 por página. Contas ADM são protegidas.");
  button("＋ Criar novo cliente",this::createCustomer);
  com.google.firebase.firestore.Query query=firestore.collection("usuarios")
   .orderBy(com.google.firebase.firestore.FieldPath.documentId()).limit(50);
  if(cursor!=null)query=query.startAfter(cursor);
  query.get().addOnSuccessListener(result->{
   if(isFinishing())return;
   if(result.isEmpty()){button("Nenhum perfil encontrado",()->{});}
   for(com.google.firebase.firestore.DocumentSnapshot doc:result.getDocuments()){
    String uid=doc.getId(),email=doc.getString("email"),name=doc.getString("nome");
    boolean blocked=Boolean.TRUE.equals(doc.getBoolean("bloqueado"));
    String display=(name==null||name.isEmpty()?"Usuário":name)+" • "+(email==null?"Sem e-mail":email);
    button((blocked?"🔴 BLOQUEADO  ":"🟢 ATIVO  ")+display,()->userDetail(uid,display,blocked));
   }
   if(result.size()==50)button("Próximos 50 perfis →",()->manageUsersPage(result.getDocuments().get(result.size()-1)));
   button("↻ Atualizar perfis",this::manageUsers);
   button("← Voltar ao ADM",this::dashboard);
  }).addOnFailureListener(e->msg("Não foi possível listar os perfis: "+e.getMessage()));
 }
 private void manageAdmins(){
  if(!authorized){login();return;}
  screen("Contas administrativas");msg("Contas ADM são protegidas contra bloqueio.");
  firestore.collection("admins").limit(100).get().addOnSuccessListener(result->{
   if(isFinishing())return;
   for(com.google.firebase.firestore.DocumentSnapshot d:result.getDocuments()){
    String role=d.getString("role");
    button("🛡 "+d.getId()+" • "+(role==null?"administrador":role)+
     (Boolean.TRUE.equals(d.getBoolean("active"))?" • ativo":" • inativo"),
     ()->new AlertDialog.Builder(this).setTitle("Conta administrativa protegida")
      .setMessage("O bloqueio de administradores não é permitido por esta tela.").setPositiveButton("OK",null).show());
   }
   button("← Voltar ao ADM",this::dashboard);
  }).addOnFailureListener(e->msg("Erro ao listar ADM: "+e.getMessage()));
 }
 private void userDetail(String uid,String display,boolean ignored){
  if(!authorized)return;
  firestore.collection("usuarios").document(uid).get().addOnSuccessListener(doc->{
   if(!doc.exists()){msg("Perfil não encontrado.");return;}
   boolean blocked=Boolean.TRUE.equals(doc.getBoolean("bloqueado"));
   com.google.firebase.Timestamp expiry=doc.getTimestamp("vipAte");
   screen("Gerenciar cliente");
   msg(display+"\nID: "+uid+"\nAcesso: "+(blocked?"BLOQUEADO":"ATIVO")+
    "\nAssinatura: "+(expiry==null?"sem vencimento":new java.text.SimpleDateFormat("dd/MM/yyyy",java.util.Locale.forLanguageTag("pt-BR")).format(expiry.toDate())));
   button("📅 Renovar 30 dias",()->renewCustomer(uid,30));
   button("📅 Renovar 60 dias",()->renewCustomer(uid,60));
   button("📅 Renovar 90 dias",()->renewCustomer(uid,90));
   button("📅 Renovar 180 dias",()->renewCustomer(uid,180));
   button("📅 Renovar 365 dias (1 ano)",()->renewCustomer(uid,365));
   button("↺ Resetar assinatura",()->resetSubscription(uid));
   String email=doc.getString("email");
   if(email!=null&&!email.isEmpty())button("✉ Enviar redefinição de senha",()->passwordReset(email));
   button(blocked?"🔓 Desbloquear usuário":"🔒 Bloquear usuário",()->changeBlocked(uid,!blocked));
   button("↻ Atualizar perfil",()->userDetail(uid,display,false));
   button("← Voltar aos perfis",this::manageUsers);
  }).addOnFailureListener(e->msg("Erro ao carregar perfil: "+e.getMessage()));
 }
 private void protectedCustomer(String uid,Runnable operation){
  if(!authorized||auth.getCurrentUser()==null)return;
  if(uid.equals(auth.getCurrentUser().getUid())){msg("Sua própria conta está protegida.");return;}
  firestore.collection("admins").document(uid).get()
   .addOnSuccessListener(doc->{if(doc.exists()){msg("Contas ADM não podem ser alteradas por esta tela.");return;}operation.run();})
   .addOnFailureListener(e->msg("Não foi possível verificar proteção ADM: "+e.getMessage()));
 }
 private void changeBlocked(String uid,boolean blocked){
  protectedCustomer(uid,()->new AlertDialog.Builder(this)
   .setTitle(blocked?"Bloquear cliente?":"Desbloquear cliente?")
   .setMessage("Confirma a alteração de acesso deste usuário?")
   .setNegativeButton("Cancelar",null).setPositiveButton("Confirmar",(d,w)->{
    java.util.Map<String,Object> updates=new java.util.HashMap<>();
    updates.put("bloqueado",blocked);
    updates.put("alteradoEm",com.google.firebase.firestore.FieldValue.serverTimestamp());
    updates.put("alteradoPor",auth.getCurrentUser().getUid());
    firestore.collection("usuarios").document(uid).update(updates)
     .addOnSuccessListener(v->manageUsers())
     .addOnFailureListener(e->msg("Erro ao alterar bloqueio: "+e.getMessage()));
   }).show());
 }
 private void renewCustomer(String uid,int days){
  protectedCustomer(uid,()->firestore.collection("usuarios").document(uid).get()
   .addOnSuccessListener(doc->{
    com.google.firebase.Timestamp previous=doc.getTimestamp("vipAte");
    long now=System.currentTimeMillis();
    long start=previous==null?now:Math.max(now,previous.toDate().getTime());
    java.util.Calendar cal=java.util.Calendar.getInstance();
    cal.setTimeInMillis(start);cal.add(java.util.Calendar.DAY_OF_YEAR,days);
    java.util.Date until=cal.getTime();
    String formatted=new java.text.SimpleDateFormat("dd/MM/yyyy",java.util.Locale.forLanguageTag("pt-BR")).format(until);
    new AlertDialog.Builder(this).setTitle("Renovar "+days+" dias?")
     .setMessage("Novo vencimento: "+formatted+"\nA renovação soma os dias ao vencimento atual, se ainda estiver ativo.")
     .setNegativeButton("Cancelar",null).setPositiveButton("Confirmar",(d,w)->{
      java.util.Map<String,Object> updates=new java.util.HashMap<>();
      updates.put("vipAtivo",true);
      updates.put("vipAte",new com.google.firebase.Timestamp(until));
      updates.put("alteradoEm",com.google.firebase.firestore.FieldValue.serverTimestamp());
      updates.put("alteradoPor",auth.getCurrentUser().getUid());
      firestore.collection("usuarios").document(uid).update(updates)
       .addOnSuccessListener(v->{msg("Assinatura renovada até "+formatted);manageUsers();})
       .addOnFailureListener(e->msg("Falha na renovação: "+e.getMessage()));
     }).show();
   }).addOnFailureListener(e->msg("Falha ao consultar vencimento: "+e.getMessage())));
 }
 private void resetSubscription(String uid){
  protectedCustomer(uid,()->new AlertDialog.Builder(this).setTitle("Resetar assinatura?")
   .setMessage("Remove a validade VIP deste cliente. Não exclui a conta nem o histórico.")
   .setNegativeButton("Cancelar",null).setPositiveButton("Resetar",(d,w)->{
    java.util.Map<String,Object> changes=new java.util.HashMap<>();
    changes.put("vipAtivo",false);
    changes.put("vipAte",com.google.firebase.firestore.FieldValue.delete());
    changes.put("alteradoEm",com.google.firebase.firestore.FieldValue.serverTimestamp());
    changes.put("alteradoPor",auth.getCurrentUser().getUid());
    firestore.collection("usuarios").document(uid).update(changes)
     .addOnSuccessListener(v->{msg("Assinatura resetada.");manageUsers();})
     .addOnFailureListener(e->msg("Erro ao resetar assinatura: "+e.getMessage()));
   }).show());
 }
 private void passwordReset(String email){
  new AlertDialog.Builder(this).setTitle("Redefinir senha")
   .setMessage("Enviar um link de redefinição para "+email+"?")
   .setNegativeButton("Cancelar",null).setPositiveButton("Enviar",(d,w)->{
    auth.sendPasswordResetEmail(email)
     .addOnSuccessListener(v->msg("Solicitação de redefinição enviada."))
     .addOnFailureListener(e->msg("Falha ao enviar: "+e.getMessage()));
   }).show();
 }
 private void createCustomer(){
  if(!authorized)return;
  LinearLayout layout=new LinearLayout(this);layout.setOrientation(1);
  int pad=dp(18);layout.setPadding(pad,0,pad,0);
  EditText name=new EditText(this);name.setHint("Nome do cliente");layout.addView(name);
  EditText email=new EditText(this);email.setHint("E-mail do cliente");email.setSingleLine(true);
  email.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);layout.addView(email);
  EditText pass=new EditText(this);pass.setHint("Senha temporária (mínimo 6 caracteres)");
  pass.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);layout.addView(pass);
  AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Criar cliente")
   .setMessage("A conta será criada sem desconectar o administrador.")
   .setView(layout).setNegativeButton("Cancelar",null).setPositiveButton("Criar cliente",null).create();
  dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
   String n=name.getText().toString().trim(),mail=email.getText().toString().trim(),pwd=pass.getText().toString();
   if(n.isEmpty()){name.setError("Informe o nome.");return;}
   if(!android.util.Patterns.EMAIL_ADDRESS.matcher(mail).matches()){email.setError("E-mail inválido.");return;}
   if(pwd.length()<6){pass.setError("Use pelo menos 6 caracteres.");return;}
   dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
   try{
    com.google.firebase.FirebaseApp secondary;
    try{secondary=com.google.firebase.FirebaseApp.getInstance("reel_customer_creation");}
    catch(IllegalStateException e){
     secondary=com.google.firebase.FirebaseApp.initializeApp(getApplicationContext(),
      com.google.firebase.FirebaseApp.getInstance().getOptions(),"reel_customer_creation");
    }
    com.google.firebase.auth.FirebaseAuth secondaryAuth=com.google.firebase.auth.FirebaseAuth.getInstance(secondary);
    secondaryAuth.createUserWithEmailAndPassword(mail,pwd).addOnSuccessListener(result->{
     String uid=result.getUser().getUid();secondaryAuth.signOut();
     java.util.Map<String,Object> profile=new java.util.HashMap<>();
     profile.put("uid",uid);profile.put("nome",n);profile.put("email",mail);
     profile.put("vipAtivo",false);profile.put("bloqueado",false);
     profile.put("criadoEm",com.google.firebase.firestore.FieldValue.serverTimestamp());
     firestore.collection("usuarios").document(uid).set(profile)
      .addOnSuccessListener(x->{dialog.dismiss();msg("Cliente criado: "+mail);manageUsers();})
      .addOnFailureListener(e->{dialog.dismiss();msg("Conta criada no Authentication, mas perfil não salvo: "+e.getMessage());});
    }).addOnFailureListener(e->{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
     msg("Não foi possível criar cliente: "+e.getMessage());});
   }catch(Exception e){dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);msg("Erro ao criar cliente: "+e.getMessage());}
  }));
  dialog.show();
 }
 private void configureTmdbServer(){
  EditText f=new EditText(this);f.setSingleLine(true);f.setHint("https://seu-servico.onrender.com");f.setText(getPreferences(MODE_PRIVATE).getString("tmdb_server",""));
  new AlertDialog.Builder(this).setTitle("Servidor TMDB no Render").setMessage("Informe apenas a URL do servidor. A chave TMDB fica no Render, nunca no APK.").setView(f).setNegativeButton("Cancelar",null).setPositiveButton("Salvar",(d,w)->{String u=f.getText().toString().trim().replaceAll("/+$","");if(!u.startsWith("https://")){msg("Use HTTPS");return;}getPreferences(MODE_PRIVATE).edit().putString("tmdb_server",u).apply();msg("Servidor TMDB configurado");}).show();
 }
 private void startTmdbSync(boolean dryRun){
  if(!authorized||busy)return;String server=getPreferences(MODE_PRIVATE).getString("tmdb_server","");if(!server.startsWith("https://")){msg("Configure primeiro a URL do servidor TMDB");return;}
  new AlertDialog.Builder(this).setTitle(dryRun?"Simular sincronização TMDB":"Atualizar campos ausentes pelo TMDB")
   .setMessage("O servidor verificará as pastas do Firebase. Capas existentes e links de vídeo não serão alterados. Faça backup antes de executar.")
   .setNegativeButton("Cancelar",null).setPositiveButton("Continuar",(d,w)->{
    busy=true;msg("Autenticando...");FirebaseCatalogSync.withToken(auth,token->worker.execute(()->{
     try{
      java.net.HttpURLConnection c=(java.net.HttpURLConnection)new java.net.URL(server+"/sync").openConnection();c.setConnectTimeout(20000);c.setReadTimeout(20000);c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Authorization","Bearer "+token);c.setRequestProperty("Content-Type","application/json");
      byte[] payload=("{\"dryRun\":"+dryRun+",\"metadata\":true}").getBytes("UTF-8");try(java.io.OutputStream out=c.getOutputStream()){out.write(payload);}int statusCode=c.getResponseCode();java.io.InputStream in=statusCode<300?c.getInputStream():c.getErrorStream();byte[] bytes=new byte[8192];int n=in.read(bytes);String response=n>0?new String(bytes,0,n,"UTF-8"):"";c.disconnect();
      if(statusCode>=300)throw new IOException("HTTP "+statusCode+" "+response);
      org.json.JSONObject job=new org.json.JSONObject(response);String id=job.getString("id");msg("Sincronização iniciada: "+id);
      for(int i=0;i<3600;i++){
       Thread.sleep(2500);java.net.HttpURLConnection poll=(java.net.HttpURLConnection)new java.net.URL(server+"/sync/"+id).openConnection();poll.setConnectTimeout(15000);poll.setReadTimeout(15000);poll.setRequestProperty("Authorization","Bearer "+token);
       try(java.io.InputStream stream=poll.getInputStream()){java.io.ByteArrayOutputStream buffer=new java.io.ByteArrayOutputStream();byte[] b=new byte[2048];int m;while((m=stream.read(b))!=-1)buffer.write(b,0,m);org.json.JSONObject j=new org.json.JSONObject(buffer.toString("UTF-8"));String state=j.optString("state");msg((dryRun?"Simulação":"TMDB")+": "+j.optInt("scanned")+" vistos, "+j.optInt("updated")+" atualizados, "+j.optInt("unmatched")+" sem correspondência, "+j.optInt("errors")+" erros • "+j.optString("path")+" • "+state);if(!"running".equals(state))break;}finally{poll.disconnect();}
      }
     }catch(Exception e){msg("TMDB: "+e.getMessage());}finally{busy=false;}
    }),(error)->{busy=false;msg(error);});
   }).show();
 }
 private void publishApkDialog(){
  if(!authorized||busy)return;
  LinearLayout form=new LinearLayout(this);form.setOrientation(1);form.setPadding(dp(16),dp(8),dp(16),0);
  EditText url=new EditText(this);url.setSingleLine(true);url.setInputType(17);url.setHint("https://.../reelseries.apk");form.addView(url);
  EditText version=new EditText(this);version.setInputType(2);version.setHint("versionCode do APK novo (ex.: 100200)");form.addView(version);
  new AlertDialog.Builder(this).setTitle("Publicar atualização do Reel Séries")
   .setMessage("Informe o link HTTPS direto do APK já compilado. O novo APK precisa ter a mesma assinatura e um versionCode maior. Não cole link de página do GitHub Actions.")
   .setView(form).setNegativeButton("Cancelar",null).setPositiveButton("Publicar",(d,w)->{
    String link=url.getText().toString().trim();int code;
    try{code=Integer.parseInt(version.getText().toString().trim());}catch(Exception e){msg("Informe um versionCode numérico");return;}
    if(!link.startsWith("https://")){msg("Informe um link HTTPS");return;}
    busy=true;msg("Publicando atualização no Firebase...");
    FirebaseCatalogSync.withToken(auth,token->worker.execute(()->{
     try{FirebaseCatalogSync.publishApk(this,token,link,code);msg("Atualização publicada. Os usuários verão o aviso ao abrir o aplicativo.");}
     catch(Exception e){msg("Erro ao publicar atualização: "+e.getMessage());}
     finally{busy=false;}
    }),e->{busy=false;msg("Erro de autenticação: "+e);});
   }).show();
 }
 private void seedBundledCatalog(){
  worker.execute(()->{
   try{
    android.content.SharedPreferences prefs=getSharedPreferences("reel_admin_catalog",MODE_PRIVATE);
    if(prefs.getBoolean("asset_6760_loaded",false))return;
    runOnUiThread(()->msg("Sincronizando catálogo original do APK com o administrador..."));
    try(InputStream in=getAssets().open("catalogo.json")){
     int n=catalog.importJson(in,c->msg("Preparando catálogo interno: "+c+" títulos..."));
     prefs.edit().putBoolean("asset_6760_loaded",true).apply();
     runOnUiThread(()->{if(!isFinishing()){dashboard();msg("Catálogo original disponível no administrador: "+n+" registros. Para os demais títulos, importe seu JSON completo.");}});
    }
   }catch(Exception e){msg("Não foi possível carregar o catálogo original: "+e.getMessage());}
  });
 }
 private void statistics(){screen("Estatísticas avançadas");msg("Analisando banco local...");worker.execute(()->{java.util.LinkedHashMap<String,Integer> data=catalog.statistics();runOnUiThread(()->{if(isFinishing())return;msg("Informações do catálogo armazenado neste aparelho.");for(java.util.Map.Entry<String,Integer> e:data.entrySet())root.addView(panelText(e.getKey()+"  •  "+e.getValue(),18));button("Ver títulos sem vídeo",()->diagnostic("sem_video",0));button("Ver títulos sem capa",()->diagnostic("sem_capa",0));button("Voltar ao painel",this::dashboard);});});}
 private void diagnostic(String kind,int offset){screen("Diagnóstico • "+kind.replace('_',' '));msg("Consultando registros...");worker.execute(()->{java.util.ArrayList<org.json.JSONObject> items=catalog.diagnostic(kind,offset,50);int total=catalog.diagnosticCount(kind);runOnUiThread(()->{if(isFinishing())return;msg(total+" registros encontrados • página "+(offset/50+1));for(org.json.JSONObject item:items){String label=item.optString("nome")+" • "+item.optString("categoria");button(label,()->editLocal(item));}if(offset>0)button("← Anterior",()->diagnostic(kind,offset-50));if(offset+50<total)button("Próxima →",()->diagnostic(kind,offset+50));button("Voltar ao painel",this::dashboard);});});}
 private void exportCatalog(){
  if(!authorized||busy){msg("Aguarde a operação atual ou entre como administrador.");return;}
  Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
  i.addCategory(Intent.CATEGORY_OPENABLE);
  i.setType("application/json");
  i.putExtra(Intent.EXTRA_TITLE,"reelseries-backup.json");
  try{startActivityForResult(i,EXPORT);}
  catch(Exception e){msg("Não foi possível abrir a opção Salvar arquivo: "+e.getMessage());}
 }
 private void writeBackup(Uri uri){
  if(busy)return;
  busy=true;msg("Buscando títulos no Firebase e preparando o backup...");
  worker.execute(()->{
   String aviso="";
   try{
    int recebidos=FirebaseCatalogSync.download(this,catalog,c->msg("Recebidos do Firebase: "+c));
    msg("Firebase: "+recebidos+" registros recebidos.");
   }catch(Exception e){
    aviso=" ATENÇÃO: falha ao sincronizar Firebase ("+e.getMessage()+").";
   }
   try{
    if(catalog.count()<=0)throw new IOException("Não existem títulos locais e o Firebase não foi sincronizado.");
    int n;
    try(OutputStream out=getContentResolver().openOutputStream(uri,"w")){
     if(out==null)throw new IOException("Não foi possível abrir o arquivo escolhido");
     n=catalog.exportJson(out,c->msg("Exportados: "+c));
    }
    msg("Backup salvo no local escolhido: "+n+" títulos."+aviso);
   }catch(Exception e){msg("Erro ao salvar backup: "+e.getMessage()+aviso);}
   finally{busy=false;}
  });
 }
 private void browseTitles(int offset){
  screen("Todos os filmes, séries e canais");msg("Carregando registros "+(offset+1)+" a "+(offset+50)+"...");
  worker.execute(()->{
   java.util.ArrayList<org.json.JSONObject> items=catalog.page(offset,50);
   int total=catalog.count();
   runOnUiThread(()->{
    if(isFinishing())return;
    msg("Total: "+total+" • Página "+(offset/50+1));
    for(org.json.JSONObject item:items){
     String label=item.optString("nome")+" • "+item.optString("categoria");
     button(label,()->editLocal(item));
    }
    if(offset>0)button("← Página anterior",()->browseTitles(Math.max(0,offset-50)));
    if(offset+50<total)button("Próxima página →",()->browseTitles(offset+50));
    button("Pesquisar títulos",this::manageTitles);
    button("Voltar ao painel",this::dashboard);
   });
  });
 }
 private void manageTitles(){screen("Pesquisar títulos locais");EditText q=field("Pesquisar filme, série, episódio ou canal");button("Pesquisar",()->{String term=q.getText().toString().trim();worker.execute(()->{java.util.ArrayList<org.json.JSONObject> items=catalog.search(term,"Todas",60);runOnUiThread(()->{if(isFinishing())return;screen("Resultados da pesquisa");msg(items.size()+" resultados (máximo 60)");for(org.json.JSONObject item:items){String name=item.optString("nome"),cat=item.optString("categoria");button(name+" • "+cat,()->editLocal(item));}button("Nova pesquisa",this::manageTitles);button("Voltar",this::dashboard);});});});button("Voltar",this::dashboard);}
 private void editLocal(org.json.JSONObject item){screen("Editar conteúdo local");EditText name=field("Nome"),cat=field("Categoria"),cover=field("URL da capa"),url=field("URL do vídeo"),syn=field("Sinopse");name.setText(item.optString("nome"));cat.setText(item.optString("categoria"));cover.setText(item.optString("capa"));url.setText(item.optString("url"));syn.setText(item.optString("sinopse"));button("Salvar alterações",()->{String n=name.getText().toString().trim();if(n.isEmpty()){msg("Informe o nome");return;}catalog.save(catalog.getWritableDatabase(),item.optString("id"),n,cat.getText().toString(),cover.getText().toString(),url.getText().toString(),syn.getText().toString(),"","edição local");msg("Alterações salvas. Sincronizando Firebase...");syncOne(item.optString("id"));});button("Excluir título",()->new AlertDialog.Builder(this).setTitle("Excluir este título?").setMessage(item.optString("nome")).setNegativeButton("Cancelar",null).setPositiveButton("Excluir",(d,w)->{catalog.getWritableDatabase().delete("media","id=?",new String[]{item.optString("id")});syncDelete(item.optString("id"));manageTitles();}).show());button("Voltar à pesquisa",this::manageTitles);}
 private void pick(int type){if(busy){msg("Aguarde a importação terminar.");return;}Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");startActivityForResult(i,type);}
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;if(request==EXPORT){if(authorized)writeBackup(data.getData());return;}if(request!=JSON&&request!=M3U)return;Uri uri=data.getData();new AlertDialog.Builder(this).setTitle("Importar no celular").setMessage("O arquivo será importado neste aparelho e enviado ao Firebase. Registros com o mesmo ID serão atualizados. Continuar?").setNegativeButton("Cancelar",null).setPositiveButton("Importar",(d,w)->importFile(uri,request)).show();}
 private void importFile(Uri uri,int type){if(busy||!authorized)return;busy=true;msg("Importando para SQLite local...");worker.execute(()->{try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("Arquivo não disponível");String origin=type==JSON?"JSON":"arquivo:"+uri.toString();int n=type==JSON?catalog.importJson(in,c->msg("JSON: "+c+" registros locais...")):catalog.importM3u(in,origin,c->msg("M3U: "+c+" canais locais..."));msg("Importados "+n+" registros. Enviando ao Firebase...");syncOrigin(type==JSON?null:origin);}catch(Exception e){msg("Falha na importação: "+e.getMessage());}finally{busy=false;}});}
 private void urlDialog(boolean stream){EditText input=new EditText(this);input.setSingleLine(false);input.setHint(stream?"https://servidor/canal.m3u8":"https://servidor/lista.m3u");new AlertDialog.Builder(this).setTitle(stream?"Adicionar stream M3U8":"Importar playlist M3U").setView(input).setNegativeButton("Cancelar",null).setPositiveButton("Continuar",(d,w)->{String url=input.getText().toString().trim();if(!url.startsWith("https://")&&!url.startsWith("http://")){msg("Use uma URL HTTP ou HTTPS válida.");return;}if(stream)streamName(url);else importUrl(url);}).show();}
 private String xtreamPref(String key){return getSharedPreferences("xtream_admin",MODE_PRIVATE).getString(key,"");}
 private void xtreamPanel(){
  if(!authorized){login();return;}
  screen("Conexões Xtream");
  String savedHost=xtreamPref("host"),savedUser=xtreamPref("user");
  msg(savedHost.isEmpty()?"Nenhuma conexão salva.":"Conexão salva • "+savedHost+" • "+savedUser+" (verifique o status)");
  section("Servidor e credenciais");
  EditText server=field("https://servidor:porta");server.setSingleLine(true);server.setText(savedHost);
  EditText username=field("Usuário IPTV");username.setSingleLine(true);username.setText(savedUser);
  EditText password=field("Senha IPTV");password.setSingleLine(true);password.setInputType(129);
  try{password.setText(XtreamCredentials.load(this));}catch(Exception e){msg("Senha protegida indisponível. Informe novamente.");}
  button("✓  Salvar e testar conexão",()->{
   if(busy)return;
   try{
    String base=xtreamBase(server.getText().toString()),user=username.getText().toString().trim(),pass=password.getText().toString();
    if(user.isEmpty()||pass.isEmpty())throw new IOException("Informe usuário e senha");
    busy=true;msg("Verificando conexão Xtream...");
    worker.execute(()->{try{xtreamAuthenticate(base,user,pass);
     XtreamCredentials.save(this,pass);
     getSharedPreferences("xtream_admin",MODE_PRIVATE).edit().putString("host",base).putString("user",user).apply();
     msg("● CONECTADA • Conta validada e salva neste aparelho.");
    }catch(Exception e){msg("Não conectada: "+xtreamSafeError(e));}finally{busy=false;}});
   }catch(Exception e){msg("Conexão inválida: "+e.getMessage());}
  });
  button("☷  VISUALIZAR CATÁLOGO ENVIADO",()->xtreamSentCatalog(0,""));
  section("Sincronizar catálogo");
  button("▣  SÉRIES + TEMPORADAS + EPISÓDIOS",()->xtreamStart("series"));
  button("▤  FILMES",()->xtreamStart("movie"));
  button("▣  TV AO VIVO",()->xtreamStart("live"));
  button("↻  SINCRONIZAR TUDO",()->xtreamStart("all"));
  section("Atualização automática");
  android.widget.Switch automatic=new android.widget.Switch(this);
  automatic.setText("Atualizar ao abrir o ADM (intervalo mínimo de 24 horas)");automatic.setTextColor(Color.WHITE);
  automatic.setChecked(getSharedPreferences("xtream_admin",MODE_PRIVATE).getBoolean("auto",false));
  automatic.setOnCheckedChangeListener((v,checked)->getSharedPreferences("xtream_admin",MODE_PRIVATE).edit().putBoolean("auto",checked).apply());
  root.addView(automatic);
  button("←  Voltar ao ADM",this::dashboard);
 }
 private void xtreamSentCatalog(int offset,String filter){
  if(!authorized){login();return;}
  String host=xtreamPref("host"),user=xtreamPref("user");
  screen("Catálogo Xtream enviado");
  if(host.isEmpty()||user.isEmpty()){msg("Salve uma conexão Xtream primeiro.");button("Voltar",this::xtreamPanel);return;}
  String origin="xtream_"+LocalCatalog.hash(host+"|"+user);
  long last=getSharedPreferences("xtream_admin",MODE_PRIVATE).getLong("last_upload_success",0);
  if(last==0)msg("Ainda não há envio ao Firebase confirmado neste aparelho. A lista abaixo mostra os registros locais, não comprova a publicação.");
  else msg("Último envio concluído: "+android.text.format.DateFormat.format("dd/MM/yyyy HH:mm",last)+". Itens abaixo pertencem à conexão selecionada; não são uma auditoria individual do servidor.");
  EditText search=field("Pesquisar série, temporada, episódio, filme ou canal");search.setSingleLine(true);search.setText(filter);
  button("Pesquisar",()->xtreamSentCatalog(0,search.getText().toString().trim()));
  worker.execute(()->{
   java.util.ArrayList<org.json.JSONObject> results=new java.util.ArrayList<>();int total=0,series=0,episodes=0,movies=0,live=0;
   String pattern="%"+filter+"%";
   try(android.database.Cursor c=catalog.getReadableDatabase().rawQuery("SELECT nome,categoria,url,id FROM media WHERE origem=? AND nome LIKE ? ORDER BY nome COLLATE NOCASE LIMIT 100 OFFSET ?",new String[]{origin,pattern,String.valueOf(offset)})){
    while(c.moveToNext()){org.json.JSONObject o=new org.json.JSONObject();o.put("nome",c.getString(0));o.put("categoria",c.getString(1));o.put("url",c.getString(2));o.put("id",c.getString(3));results.add(o);}
   }catch(Exception e){msg("Falha ao consultar catálogo: "+e.getMessage());}
   try(android.database.Cursor c=catalog.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM media WHERE origem=? AND nome LIKE ?",new String[]{origin,pattern})){if(c.moveToFirst())total=c.getInt(0);}
   catch(Exception ignored){}
   final int count=total;
   runOnUiThread(()->{
    if(isFinishing())return;
    msg("Registros encontrados: "+count+" • Exibindo "+(results.isEmpty()?0:offset+1)+"–"+(offset+results.size()));
    for(org.json.JSONObject o:results){
     String name=o.optString("nome"),cat=o.optString("categoria"),url=o.optString("url");
     String kind=url.contains("/series/")?"EPISÓDIO":url.contains("/movie/")?"FILME":url.contains("/live/")?"TV AO VIVO":"SÉRIE";
     button(kind+" • "+name,()->new AlertDialog.Builder(this).setTitle(name).setMessage("Tipo: "+kind+"\nCategoria: "+cat+"\nID local: "+o.optString("id")+"\n"+(url.isEmpty()?"Sem vídeo vinculado":"Vídeo vinculado ao registro")).setPositiveButton("OK",null).show());
    }
    if(offset>0)button("← Página anterior",()->xtreamSentCatalog(Math.max(0,offset-100),filter));
    if(offset+results.size()<count)button("Próximos 100 →",()->xtreamSentCatalog(offset+100,filter));
    button("← Voltar às conexões",this::xtreamPanel);
   });
  });
 }
 private String xtreamBase(String raw)throws Exception{
  URL u=new URL(raw.trim());String scheme=u.getProtocol();
  if((!scheme.equalsIgnoreCase("https")&&!scheme.equalsIgnoreCase("http"))||u.getHost().isEmpty())throw new IOException("Use endereço HTTP ou HTTPS válido");
  String path=u.getPath().replaceAll("/+$","");if(path.endsWith("/player_api.php"))path=path.substring(0,path.length()-15);
  return scheme+"://"+u.getAuthority()+path;
 }
 private String xtreamAuthenticate(String base,String user,String pass)throws Exception{
  String api=base+"/player_api.php?username="+java.net.URLEncoder.encode(user,"UTF-8")+"&password="+java.net.URLEncoder.encode(pass,"UTF-8");
  org.json.JSONObject login=xtreamJson(api),account=login.optJSONObject("user_info");
  if(account==null||!"1".equals(String.valueOf(account.opt("auth"))))throw new IOException("Usuário ou senha não autorizados pela API");
  String st=account.optString("status","");if(!st.isEmpty()&&!"Active".equalsIgnoreCase(st))throw new IOException("Conta não ativa: "+st);
  return api;
 }
 private void xtreamStart(String type){
  if(!authorized||busy){msg("Aguarde a operação atual.");return;}
  String base=xtreamPref("host"),user=xtreamPref("user"),pass;
  try{pass=XtreamCredentials.load(this);}catch(Exception e){msg("Não foi possível recuperar a senha salva. Salve a conexão novamente.");return;}
  if(base.isEmpty()||user.isEmpty()||pass.isEmpty()){msg("Primeiro salve e teste uma conexão.");return;}
  busy=true;msg("Conectando e iniciando importação de "+type+"...");
  worker.execute(()->{
   try{
    String api=xtreamAuthenticate(base,user,pass);
    String origin="xtream_"+LocalCatalog.hash(base+"|"+user);
    java.util.ArrayList<String> errors=new java.util.ArrayList<>();int total=0;
    String[] types=type.equals("all")?new String[]{"live","movie","series"}:new String[]{type};
    for(String current:types){
     String action=current.equals("live")?"get_live_streams":current.equals("movie")?"get_vod_streams":"get_series";
     String catAction=current.equals("live")?"get_live_categories":current.equals("movie")?"get_vod_categories":"get_series_categories";
     try{
      msg("Buscando "+current+" na API...");
      total+=xtreamImport(api+"&action="+action,base,user,pass,current,xtreamCategoriesOptional(api+"&action="+catAction),origin);
     }catch(Exception ex){errors.add(current+": "+xtreamSafeError(ex));}
    }
    final int count=total;
    if(count>0){msg("Importados "+count+" registros. Enviando ao Firebase...");
     final java.util.concurrent.CountDownLatch latch=new java.util.concurrent.CountDownLatch(1);
     FirebaseCatalogSync.withToken(auth,token->new Thread(()->{try{int n=FirebaseCatalogSync.upload(this,catalog,origin,token,c->msg("Firebase: "+c+" enviados"));FirebaseCatalogSync.markChanged(this,token);getSharedPreferences("xtream_admin",MODE_PRIVATE).edit().putLong("last_upload_success",System.currentTimeMillis()).apply();msg("Firebase sincronizado: "+n+" registros.");}catch(Exception e){msg("Falha Firebase: "+e.getMessage());}finally{latch.countDown();}},"xtream-upload").start(),e->{msg("Falha Firebase: "+e);latch.countDown();});
     latch.await();
    }
    if(errors.isEmpty())msg("Importação concluída: "+count+" registros processados. Confira o resultado da sincronização no Firebase.");
    else msg("Importação parcial ("+count+"): "+android.text.TextUtils.join("; ",errors));
    getSharedPreferences("xtream_admin",MODE_PRIVATE).edit().putLong("last_sync",System.currentTimeMillis()).apply();
   }catch(Exception e){msg("Falha Xtream: "+xtreamSafeError(e));}finally{busy=false;}
  });
 }
 private void iptvCredentialsDialog(){xtreamPanel();}
 private void importXtream(String base,String user,String pass){
  try{XtreamCredentials.save(this,pass);getSharedPreferences("xtream_admin",MODE_PRIVATE).edit().putString("host",base).putString("user",user).apply();xtreamStart("all");}
  catch(Exception e){msg("Não foi possível salvar a conexão: "+e.getMessage());}
 }
 private String xtreamSafeError(Exception e){String m=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();return m.replaceAll("(?i)(username|password)=[^&\\s]+","$1=***");}
 private java.net.HttpURLConnection xtreamConnect(String url)throws Exception{
  java.net.HttpURLConnection c=(java.net.HttpURLConnection)new URL(url).openConnection();
  c.setConnectTimeout(15000);c.setReadTimeout(60000);c.setInstanceFollowRedirects(true);
  c.setRequestProperty("Accept","application/json, */*");c.setRequestProperty("User-Agent","ReelSeries-Android/19");
  int code=c.getResponseCode();if(code<200||code>=300){c.disconnect();throw new IOException("API retornou HTTP "+code+". Verifique se player_api.php está autorizado neste servidor.");}
  return c;
 }
 private org.json.JSONObject xtreamJson(String url)throws Exception{
  java.net.HttpURLConnection c=xtreamConnect(url);
  try(java.io.InputStream in=c.getInputStream()){
   java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] b=new byte[4096];int n;
   while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>1048576)throw new IOException("Resposta de autenticação grande demais");}
   return new org.json.JSONObject(out.toString("UTF-8"));
  }finally{c.disconnect();}
 }
 private java.util.HashMap<String,String> xtreamCategories(String url)throws Exception{
  java.util.HashMap<String,String> map=new java.util.HashMap<>();java.net.HttpURLConnection c=xtreamConnect(url);
  try(java.io.InputStream in=c.getInputStream();android.util.JsonReader r=new android.util.JsonReader(new java.io.InputStreamReader(in,"UTF-8"))){
   if(r.peek()!=android.util.JsonToken.BEGIN_ARRAY)throw new IOException("Categorias Xtream não retornaram uma lista JSON");
   r.beginArray();while(r.hasNext()){String id="",name="";r.beginObject();while(r.hasNext()){String key=r.nextName();if(key.equals("category_id"))id=xtreamValue(r);else if(key.equals("category_name"))name=xtreamValue(r);else r.skipValue();}r.endObject();if(!id.isEmpty())map.put(id,name);}
   r.endArray();return map;
  }finally{c.disconnect();}
 }
 private java.util.HashMap<String,String> xtreamCategoriesOptional(String url){
  try{return xtreamCategories(url);}catch(Exception e){msg("Categorias indisponíveis; importando sem nomes de categoria: "+xtreamSafeError(e));return new java.util.HashMap<>();}
 }
 private String xtreamValue(android.util.JsonReader r)throws Exception{
  android.util.JsonToken t=r.peek();if(t==android.util.JsonToken.STRING||t==android.util.JsonToken.NUMBER)return r.nextString();if(t==android.util.JsonToken.BOOLEAN)return String.valueOf(r.nextBoolean());r.skipValue();return "";
 }
 private int xtreamImport(String api,String base,String user,String pass,String type,java.util.HashMap<String,String> categories,String origin)throws Exception{
  java.net.HttpURLConnection c=xtreamConnect(api);int count=0;
  java.util.ArrayList<String[]> pendingSeries=new java.util.ArrayList<>();
  android.database.sqlite.SQLiteDatabase db=catalog.getWritableDatabase();boolean transaction=false;
  try(java.io.InputStream in=c.getInputStream();android.util.JsonReader r=new android.util.JsonReader(new java.io.InputStreamReader(in,"UTF-8"))){
   if(r.peek()!=android.util.JsonToken.BEGIN_ARRAY)throw new IOException("Lista "+type+" não retornou JSON válido");
   r.beginArray();db.beginTransaction();transaction=true;
   while(r.hasNext()){
    if(r.peek()!=android.util.JsonToken.BEGIN_OBJECT){r.skipValue();continue;}
    String id="",name="",category="",cover="",extension="",description="";
    r.beginObject();while(r.hasNext()){
     String key=r.nextName();
     if(key.equals("stream_id")||key.equals("series_id"))id=xtreamValue(r);
     else if(key.equals("name")||key.equals("title"))name=xtreamValue(r);
     else if(key.equals("category_id"))category=xtreamValue(r);
     else if(key.equals("stream_icon")||key.equals("cover"))cover=xtreamValue(r);
     else if(key.equals("container_extension"))extension=xtreamValue(r);
     else if(key.equals("plot"))description=xtreamValue(r);
     else r.skipValue();
    }r.endObject();
    if(id.isEmpty()||name.isEmpty())continue;
    String url="";
    if(type.equals("live"))url=base+"/live/"+java.net.URLEncoder.encode(user,"UTF-8")+"/"+java.net.URLEncoder.encode(pass,"UTF-8")+"/"+id+".ts";
    if(type.equals("movie")){if(extension.isEmpty())extension="mp4";url=base+"/movie/"+java.net.URLEncoder.encode(user,"UTF-8")+"/"+java.net.URLEncoder.encode(pass,"UTF-8")+"/"+id+"."+extension.replaceAll("[^a-zA-Z0-9]","");}
    // Series require get_series_info and episode selection; do not invent a playable URL.
    String cat=categories.get(category);if(cat==null||cat.isEmpty())cat=type.equals("live")?"TV / IPTV":type.equals("movie")?"Filmes":"Séries";
    String key="xtream_"+LocalCatalog.hash(base+"|"+user+"|"+type+"|"+id);
    if(type.equals("series"))catalog.saveXtream(db,key,name,cat,cover,url,description,cat,origin,id,"","",0,0);else catalog.save(db,key,name,cat,cover,url,description,cat,origin);count++;
    if(type.equals("series"))pendingSeries.add(new String[]{id,name,cat,cover,description,key});
    if(count%150==0){db.setTransactionSuccessful();db.endTransaction();transaction=false;msg("Xtream "+type+": "+count+" registros...");db.beginTransaction();transaction=true;}
   }
   r.endArray();db.setTransactionSuccessful();db.endTransaction();transaction=false;
   // Consultar episódios fora da transação da listagem para não bloquear SQLite durante a rede.
   if(type.equals("series")){
    int episodes=0,failed=0,checked=0;
    for(String[] entry:pendingSeries){
     checked++;
     try{episodes+=xtreamSeriesEpisodesRetry(base,user,pass,entry,origin);}
     catch(Exception e){failed++;if(failed<=3)msg("Série sem episódios: "+entry[1]+" ("+xtreamSafeError(e)+")");}
     if(checked%100==0)msg("Xtream séries: "+episodes+" episódios salvos; "+failed+" consultas falharam");
    }
    msg("Xtream: "+episodes+" episódios de séries salvos; "+failed+" séries com falha.");
    count+=episodes;
   }
   return count;
  }finally{if(transaction)db.endTransaction();c.disconnect();}
 }
 private int xtreamSeriesEpisodesRetry(String base,String user,String pass,String[] series,String origin)throws Exception{
  Exception last=null;for(int attempt=1;attempt<=3;attempt++){try{return xtreamSeriesEpisodes(base,user,pass,series,origin);}catch(Exception e){last=e;if(attempt<3)Thread.sleep(600L*attempt);}}throw last;
 }
 private int xtreamSeriesEpisodes(String base,String user,String pass,String[] series,String origin)throws Exception{
  String api=base+"/player_api.php?username="+java.net.URLEncoder.encode(user,"UTF-8")
   +"&password="+java.net.URLEncoder.encode(pass,"UTF-8")
   +"&action=get_series_info&series_id="+java.net.URLEncoder.encode(series[0],"UTF-8");
  java.net.HttpURLConnection connection=xtreamConnect(api);
  org.json.JSONObject info;
  try(java.io.InputStream in=connection.getInputStream()){
   java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
   while((n=in.read(buffer))!=-1){out.write(buffer,0,n);if(out.size()>64*1024*1024)throw new IOException("Lista de episódios grande demais");}
   info=new org.json.JSONObject(out.toString("UTF-8"));
  }finally{connection.disconnect();}
  org.json.JSONObject seasons=info.optJSONObject("episodes");
  if(seasons==null){org.json.JSONArray flat=info.optJSONArray("episodes");if(flat!=null){seasons=new org.json.JSONObject();seasons.put("1",flat);}else throw new IOException("API não retornou episódios para "+series[1]);}
  int saved=0;
  android.database.sqlite.SQLiteDatabase db=catalog.getWritableDatabase();
  db.beginTransaction();
  try{
   java.util.Iterator<String> keys=seasons.keys();
   while(keys.hasNext()){
    String season=keys.next();org.json.JSONArray episodes=seasons.optJSONArray(season);if(episodes==null)continue;
    for(int i=0;i<episodes.length();i++){
     org.json.JSONObject ep=episodes.optJSONObject(i);if(ep==null)continue;
     String episodeId=ep.optString("id",ep.optString("episode_id","")).trim();if(episodeId.isEmpty()||episodeId.contains("/")||episodeId.contains("\\")||episodeId.contains(".."))continue;
     String ext=ep.optString("container_extension",ep.optJSONObject("info")==null?"mp4":ep.optJSONObject("info").optString("container_extension","mp4")).replaceAll("[^a-zA-Z0-9]","");if(ext.isEmpty())ext="mp4";
     String seasonNum=ep.optString("season",season).replaceAll("[^0-9]","");if(seasonNum.isEmpty())seasonNum="1";
     String episodeNum=ep.optString("episode_num",String.valueOf(i+1)).replaceAll("[^0-9]","");if(episodeNum.isEmpty())episodeNum=String.valueOf(i+1);
     String url=base+"/series/"+java.net.URLEncoder.encode(user,"UTF-8")+"/"+java.net.URLEncoder.encode(pass,"UTF-8")+"/"+java.net.URLEncoder.encode(episodeId,"UTF-8")+"."+ext;
     String name=series[1]+" S"+seasonNum+"E"+episodeNum;
     String episodeTitle=ep.optString("title","").trim();if(!episodeTitle.isEmpty())name+=" - "+episodeTitle;
     String key="xtream_"+LocalCatalog.hash(base+"|"+user+"|episode|"+series[0]+"|"+episodeId);
     catalog.saveXtream(db,key,name,series[2],series[3],url,series[4],"xtream_parent:"+series[5],origin,series[0],episodeId,series[5],Integer.parseInt(seasonNum),Integer.parseInt(episodeNum));saved++;
    }
   }
   db.setTransactionSuccessful();
  }finally{db.endTransaction();}
  return saved;
 }
 private void iptvLinkDialog(){
  if(!authorized){msg("Entre como administrador para importar IPTV.");return;}
  EditText input=new EditText(this);input.setSingleLine(false);input.setMinLines(3);input.setHint("https://servidor/get.php?username=...&password=...&type=m3u_plus&output=ts");
  LinearLayout form=new LinearLayout(this);form.setOrientation(1);form.setPadding(dp(18),dp(8),dp(18),0);
  TextView help=panelText("Cole o link completo fornecido pelo seu serviço IPTV. Os dados de acesso não ficam gravados no código do aplicativo.",14);form.addView(help);form.addView(input);
  new AlertDialog.Builder(this).setTitle("Adicionar IPTV por link").setView(form).setNegativeButton("Cancelar",null).setPositiveButton("Importar",(d,w)->{
   String link=input.getText().toString().trim();
   try{URL parsed=new URL(link);String protocol=parsed.getProtocol();if(!protocol.equalsIgnoreCase("https")&&!protocol.equalsIgnoreCase("http"))throw new Exception();
    if(parsed.getHost().isEmpty()||!parsed.getPath().toLowerCase(java.util.Locale.ROOT).endsWith("get.php"))throw new Exception();
    String q=parsed.getQuery();if(q==null||!q.contains("username=")||!q.contains("password="))throw new Exception();
    importUrl(link);
   }catch(Exception ex){msg("Informe uma URL válida get.php com username e password.");}
  }).show();
 }
 private void streamName(String url){EditText name=new EditText(this);name.setHint("Nome do canal");new AlertDialog.Builder(this).setTitle("Nome do stream").setView(name).setNegativeButton("Cancelar",null).setPositiveButton("Salvar",(d,w)->{String n=name.getText().toString().trim();if(n.isEmpty())n="Canal ao vivo";catalog.addStream(n,url);msg("Stream salvo. Sincronizando Firebase...");syncOne("stream_"+LocalCatalog.hash(url));}).show();}
 private String explainNetworkError(Exception e,String url){
  String detail=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();
  if(e instanceof SSLException || detail.toLowerCase(java.util.Locale.ROOT).contains("tls") || detail.toLowerCase(java.util.Locale.ROOT).contains("ssl"))
   return "Falha na conexão segura (TLS/HTTPS). O servidor pode não aceitar HTTPS nesta porta ou ter configuração TLS incorreta. Confirme com o fornecedor o endereço e o protocolo corretos (HTTP ou HTTPS). Não é seguro ignorar a validação do certificado. Detalhe: "+detail;
  if(e instanceof java.net.UnknownHostException)return "Servidor não encontrado. Confira o endereço da playlist e a conexão com a internet. Detalhe: "+detail;
  if(e instanceof java.net.SocketTimeoutException)return "O servidor demorou para responder. Tente novamente ou consulte o fornecedor. Detalhe: "+detail;
  if(e instanceof java.net.ConnectException)return "Não foi possível conectar ao servidor. Verifique endereço, porta e disponibilidade. Detalhe: "+detail;
  return "Falha ao importar playlist. Verifique o endereço, acesso e formato retornado pelo servidor. Detalhe: "+detail.replaceAll("(?i)(username|password)=[^&\\s]+", "$1=***");
 }
 private void importUrl(String url){
  if(busy||!authorized){msg("Aguarde a operação atual ou entre como administrador.");return;}
  busy=true;msg("Conectando à playlist M3U...");
  worker.execute(()->{HttpURLConnection c=null;
   try{
    URL u=new URL(url);String protocol=u.getProtocol().toLowerCase(java.util.Locale.ROOT);
    if(!protocol.equals("http")&&!protocol.equals("https"))throw new IOException("Use HTTP ou HTTPS.");
    c=(HttpURLConnection)u.openConnection();c.setConnectTimeout(15000);c.setReadTimeout(30000);c.setInstanceFollowRedirects(true);
    c.setRequestProperty("User-Agent","ReelSeries-Android/19");c.setRequestProperty("Accept","application/x-mpegURL, application/vnd.apple.mpegurl, audio/x-mpegurl, text/plain, */*");
    int code=c.getResponseCode();if(code<200||code>=300)throw new IOException("Servidor retornou HTTP "+code);
    String type=c.getContentType();msg("Conexão estabelecida. Importando playlist"+(type==null?"":" ("+type+")")+"...");
    try(InputStream in=c.getInputStream()){
     String origin="iptv_"+LocalCatalog.hash(url);
     int n=catalog.importM3u(in,origin,count->msg("Importando M3U: "+count+" registros..."));
     msg("Importação concluída: "+n+" registros locais. Sincronizando com Firebase...");
     if(n>0)syncOrigin(origin);
    }
   }catch(Exception e){msg(explainNetworkError(e,url));}
   finally{if(c!=null)c.disconnect();busy=false;}
  });
 }
 private void manual(){screen("Adicionar conteúdo local");EditText id=field("Nome do filme ou canal"),cat=field("Categoria"),cover=field("URL da capa"),url=field("URL do vídeo MP4/M3U8"),syn=field("Sinopse");button("Salvar no celular",()->{String name=id.getText().toString().trim();if(name.isEmpty()){msg("Informe o nome");return;}String link=url.getText().toString().trim();catalog.save(catalog.getWritableDatabase(),"manual_"+LocalCatalog.hash(name+"|"+link),name,cat.getText().toString(),cover.getText().toString(),link,syn.getText().toString(),"","manual");msg("Salvo. Enviando ao Firebase...");syncOne("manual_"+LocalCatalog.hash(name+"|"+link));});button("Voltar",this::dashboard);}

 private void downloadCategory(String folder){
  if(!authorized||busy)return;
  busy=true;msg("Buscando catálogo "+folder+" no Firebase...");
  worker.execute(()->{try{
   int n=FirebaseCatalogSync.downloadCategory(this,catalog,folder,c->msg("Recebidos: "+c));
   msg("Catálogo atualizado: "+n+" registros. Abra novamente a lista.");
  }catch(Exception e){msg("Falha ao receber "+folder+": "+e.getMessage());}finally{busy=false;}});
 }
 private void downloadCloud(){
  if(!authorized||busy)return;
  busy=true;msg("Buscando atualizações do Firebase...");
  worker.execute(()->{try{
   int n=FirebaseCatalogSync.download(this,catalog,c->msg("Recebidos do Firebase: "+c));
   msg("Atualização concluída: "+n+" registros. Abra novamente o catálogo para visualizar.");
  }catch(Exception e){msg("Falha ao atualizar: "+e.getMessage());}finally{busy=false;}});
 }
 private static final int FIREBASE_PAGE_SIZE=60;
 private int folderNavigationId=0;
 private void browseFirebaseFolders(String path){browseFirebaseFolders(path,0);}
 private void browseFirebaseFolders(String path,int offset){
  if(!authorized||busy)return;
  final int navigationId=++folderNavigationId;
  screen("Escolher pasta do Firebase");
  msg("Carregando nomes, sem baixar o conteúdo dos episódios...");
  button("← Voltar",()->{int n=path.lastIndexOf('/');if(path.isEmpty())settingsPanel();else browseFirebaseFolders(n<0?"":path.substring(0,n));});
  // Network and sorting always happen outside Android's main thread.
  FirebaseCatalogSync.withToken(auth,token->worker.execute(()->{
   try{
    java.util.ArrayList<String> keys=FirebaseCatalogSync.listFolders(this,path,token);
    final int from=Math.min(offset,keys.size());
    final int to=Math.min(from+FIREBASE_PAGE_SIZE,keys.size());
    final java.util.ArrayList<String> visible=new java.util.ArrayList<>(keys.subList(from,to));
    final int total=keys.size();
    runOnUiThread(()->{
     if(isFinishing()||navigationId!=folderNavigationId)return;
     msg("Pasta: /"+path+" • "+total+" itens • exibindo "+(total==0?0:from+1)+"–"+to+". Exclusão progressiva de registros grandes.");
     if(!path.isEmpty()&&!path.equals("catalogo_unico")&&!path.equals("catalogo_unico/meta")&&!path.equals("__meta")&&!path.equals("admins")&&!path.equals("config"))button("⚠ Excluir esta pasta",()->confirmDeleteFolder(path));
     for(String key:visible){String child=path.isEmpty()?key:path+"/"+key;button("▣ "+key,()->browseFirebaseFolders(child));}
     if(from>0)button("← Página anterior",()->browseFirebaseFolders(path,Math.max(0,from-FIREBASE_PAGE_SIZE)));
     if(to<total)button("Próxima página →",()->browseFirebaseFolders(path,to));
    });
   }catch(Exception e){if(navigationId==folderNavigationId)msg("Não foi possível listar pastas: "+e.getMessage());}
  }),e->msg("Falha de autenticação: "+e));
 }
 // Uma unica confirmacao com a senha da conta administradora atual.
 // Nunca armazenar a senha nem usar o PIN adulto para autorizar exclusoes.
 private void confirmAdminDeletion(String title,String description,Runnable action){
  if(!authorized||busy||auth.getCurrentUser()==null){msg("Acesso administrativo necessário.");return;}
  String email=auth.getCurrentUser().getEmail();
  if(email==null||email.trim().isEmpty()){
   msg("A conta atual não tem e-mail. Entre com uma conta administradora de e-mail e senha.");return;
  }
  EditText password=new EditText(this);
  password.setHint("Senha da conta administradora");
  password.setSingleLine(true);
  password.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
  LinearLayout container=new LinearLayout(this);container.setOrientation(LinearLayout.VERTICAL);
  container.setPadding(dp(20),dp(10),dp(20),0);container.addView(password);
  AlertDialog dialog=new AlertDialog.Builder(this).setTitle(title)
   .setMessage(description+"\n\nDigite sua senha de administrador para confirmar.")
   .setView(container).setNegativeButton("Cancelar",null)
   .setPositiveButton("Confirmar exclusão",null).create();
  dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
   String secret=password.getText().toString();
   if(secret.isEmpty()){password.setError("Informe sua senha.");return;}
   if(!authorized||busy||auth.getCurrentUser()==null){dialog.dismiss();msg("Acesso administrativo indisponível.");return;}
   dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
   com.google.firebase.auth.AuthCredential credential=com.google.firebase.auth.EmailAuthProvider.getCredential(email,secret);
   auth.getCurrentUser().reauthenticate(credential)
    .addOnSuccessListener(ok->{password.setText("");dialog.dismiss();if(authorized&&!busy)action.run();})
    .addOnFailureListener(error->{password.setText("");password.setError("Essa senha está incorreta ou não foi possível confirmar.");
     dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
     msg("Não foi possível confirmar a senha do administrador. Nada foi excluído.");});
  }));
  dialog.show();
 }
 private void confirmDeleteFolder(String path){
  if(!authorized||busy)return;
  // Pastas do sistema e a raiz continuam protegidas contra exclusao acidental.
  if(path.isEmpty()||path.equals("catalogo_unico")||path.equals("catalogo_unico/meta")||
     path.equals("__meta")||path.equals("admins")||path.equals("config")){
   msg("Esta pasta contém dados do sistema e não pode ser excluída.");return;
  }
  confirmAdminDeletion("Excluir pasta do Firebase?",
   "Pasta: /"+path+"\nEsta exclusão é permanente.",()->{
    busy=true;showDeletionProgress("Excluindo pasta do Firebase");
    FirebaseCatalogSync.withToken(auth,token->worker.execute(()->{
     try{int count=FirebaseCatalogSync.deleteFolderBatched(this,path,token,(done,total,folder)->deletionProgress(done,total,folder));
      if(path.equals("catalogo_unico/items")){catalog.getWritableDatabase().delete("media",null,null);getSharedPreferences("reel_admin_catalog",MODE_PRIVATE).edit().putBoolean("asset_6760_loaded",true).apply();}
      FirebaseCatalogSync.markChanged(this,token);msg("Exclusão concluída: /"+path+" ("+count+" registros). Atualize outros aparelhos.");
     }catch(Exception e){msg("Exclusão parcial ou falhou: "+e.getMessage());}finally{busy=false;}
    }),e->{busy=false;msg("Autenticação: "+e);});
   });
 }
 private void confirmDeleteAll(){
  if(!authorized||busy)return;
  confirmAdminDeletion("Excluir TODO o catálogo?",
   "Remove todos os títulos do Firebase e deste aparelho. Contas, administradores e configurações não serão apagados.",()->{
    busy=true;showDeletionProgress("Excluindo todo o catálogo");
    FirebaseCatalogSync.withToken(auth,token->worker.execute(()->{
     try{
      FirebaseCatalogSync.deleteAll(this,token,(done,total,folder)->deletionProgress(done,total,folder));
      catalog.getWritableDatabase().delete("media",null,null);
      getSharedPreferences("reel_admin_catalog",MODE_PRIVATE).edit().putBoolean("asset_6760_loaded",true).apply();
      getSharedPreferences("reel_cloud_cache_v194",MODE_PRIVATE).edit().clear().apply();
      msg("Catálogo apagado do Firebase e deste aparelho. Outros aparelhos precisam atualizar os dados.");
     }catch(Exception e){msg("Exclusão não concluída: "+e.getMessage());}
     finally{busy=false;}
    }),e->{busy=false;msg("Falha de autenticação: "+e);});
   });
 }
 private void syncAll(){if(!authorized||busy)return;msg("Preparando envio completo ao Firebase...");FirebaseCatalogSync.withToken(auth,token->worker.execute(()->{try{int n=FirebaseCatalogSync.upload(this,catalog,null,token,c->msg("Firebase: "+c+" títulos enviados"));FirebaseCatalogSync.markChanged(this,token);msg("Três catálogos sincronizados: "+n+" registros.");}catch(Exception e){msg("Firebase: "+e.getMessage());}}),e->msg("Autenticação Firebase: "+e));}
 private void syncOrigin(String origin){if(!authorized)return;FirebaseCatalogSync.withToken(auth,token->worker.execute(()->{try{int n=FirebaseCatalogSync.upload(this,catalog,origin,token,c->msg("Firebase: "+c+" registros enviados"));FirebaseCatalogSync.markChanged(this,token);msg("Importação sincronizada no Firebase: "+n+" registros.");}catch(Exception e){msg("Importado localmente, mas não enviado ao Firebase: "+e.getMessage());}}),e->msg("Importado localmente; falha de autenticação Firebase: "+e));}
 private void syncOne(String id){if(!authorized)return;FirebaseCatalogSync.withToken(auth,token->worker.execute(()->{try{FirebaseCatalogSync.uploadOne(this,catalog,id,token);FirebaseCatalogSync.markChanged(this,token);msg("Título sincronizado no Firebase.");}catch(Exception e){msg("Salvo localmente; falha no Firebase: "+e.getMessage());}}),e->msg("Falha Firebase: "+e));}
 private void syncDelete(String id){if(!authorized)return;FirebaseCatalogSync.withToken(auth,token->worker.execute(()->{try{FirebaseCatalogSync.deleteOne(this,id,token);FirebaseCatalogSync.markChanged(this,token);}catch(Exception e){msg("Excluído localmente; falha ao excluir do Firebase: "+e.getMessage());}}),e->msg("Falha Firebase: "+e));}
 @Override public void onBackPressed(){finish();}
 @Override protected void onDestroy(){worker.shutdownNow();catalog.close();super.onDestroy();}
}
