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
 private LinearLayout root;private TextView status;private boolean authorized=false,busy=false;
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
  tileRow(tile("▤",java.text.NumberFormat.getIntegerInstance(new java.util.Locale("pt","BR")).format(catalog.count()),"Registros locais",()->browseTitles(0)),tile("☷","M3U / M3U8","Importação",()->urlDialog(false)));
  tileRow(tile("▣","Catálogo","Gerenciamento",this::manageTitles),tile("⚙","Sistema","Configurações",this::settingsPanel));
  section("Catálogo online");button("☁  Enviar catálogo completo ao Firebase",()->syncAll());button("↓  Receber atualizações do Firebase",this::downloadCloud);button("☁  Ver configuração do Firebase",()->msg(FirebaseCatalogSync.configured(this)?"Firebase configurado • sincronização disponível":"Configure firebase_catalog_url em res/values/firebase_catalog.xml"));
  section("Importar playlist");
  button("🔗  Adicionar link de playlist M3U",()->urlDialog(false));
  button("📺  Conectar Xtream Codes (servidor, login e senha)",this::iptvCredentialsDialog);
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
  section("Backup e segurança");button("⇩  Exportar backup JSON",this::exportCatalog);button("↻  Restaurar backup JSON e enviar ao Firebase",()->pick(JSON));button("▣  Escolher pasta do Firebase para excluir",()->browseFirebaseFolders(""));button("⚠  Excluir TODO o catálogo do Firebase e deste aparelho",this::confirmDeleteAll);button("♟  Permissões administrativas",()->new AlertDialog.Builder(this).setTitle("Acesso administrativo").setMessage("O acesso é validado no Firestore em admins/{uid}, com active=true e role=admin.").setPositiveButton("OK",null).show());button("↻  Atualizar painel",this::dashboard);button("Sair da conta",()->{auth.signOut();login();});button("←  Voltar",this::dashboard);button("←  Voltar ao Meu Perfil",()->finish());
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
 private void importFile(Uri uri,int type){if(busy||!authorized)return;busy=true;msg("Importando para SQLite local...");worker.execute(()->{try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("Arquivo não disponível");String origin=type==JSON?"JSON":"arquivo:"+uri.toString();int n=type==JSON?catalog.importJson(in,c->msg("JSON: "+c+" registros locais...")):catalog.importM3u(in,origin,c->msg("M3U: "+c+" canais locais..."));msg("Importados "+n+" registros. Enviando ao Firebase...");syncOrigin(origin);}catch(Exception e){msg("Falha na importação: "+e.getMessage());}finally{busy=false;}});}
 private void urlDialog(boolean stream){EditText input=new EditText(this);input.setSingleLine(false);input.setHint(stream?"https://servidor/canal.m3u8":"https://servidor/lista.m3u");new AlertDialog.Builder(this).setTitle(stream?"Adicionar stream M3U8":"Importar playlist M3U").setView(input).setNegativeButton("Cancelar",null).setPositiveButton("Continuar",(d,w)->{String url=input.getText().toString().trim();if(!url.startsWith("https://")&&!url.startsWith("http://")){msg("Use uma URL HTTP ou HTTPS válida.");return;}if(stream)streamName(url);else importUrl(url);}).show();}
 private void iptvCredentialsDialog(){
  if(!authorized){msg("Entre como administrador para importar IPTV.");return;}
  LinearLayout form=new LinearLayout(this);form.setOrientation(1);form.setPadding(dp(18),dp(8),dp(18),dp(8));
  EditText server=new EditText(this);server.setSingleLine(true);server.setHint("Servidor: https://scut.lat");server.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);form.addView(server);server.setText("https://scut.lat");
  EditText username=new EditText(this);username.setSingleLine(true);username.setHint("Login IPTV");form.addView(username);
  EditText password=new EditText(this);password.setSingleLine(true);password.setHint("Senha IPTV");password.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);form.addView(password);
  TextView hint=panelText("Use o endereço de servidor fornecido pelo serviço. HTTP envia as credenciais sem criptografia; prefira HTTPS quando disponível. O aplicativo não grava estes dados no código.",13);form.addView(hint);
  new AlertDialog.Builder(this).setTitle("Conectar Xtream Codes API").setView(form).setNegativeButton("Cancelar",null).setPositiveButton("Importar",(d,w)->{
   try{
    String host=server.getText().toString().trim();String user=username.getText().toString().trim();String pass=password.getText().toString();
    URL u=new URL(host);String scheme=u.getProtocol();if((!scheme.equalsIgnoreCase("http")&&!scheme.equalsIgnoreCase("https"))||u.getHost().isEmpty()||user.isEmpty()||pass.isEmpty())throw new Exception();
    String base=scheme+"://"+u.getAuthority();
    importXtream(base,user,pass);
   }catch(Exception ex){msg("Preencha o endereço completo (http:// ou https://), login e senha válidos.");}
  }).show();
 }
 private void importXtream(String base,String user,String pass){
  if(busy||!authorized){msg("Aguarde a operação atual ou entre como administrador.");return;}
  busy=true;msg("Testando autenticação Xtream Codes...");
  worker.execute(()->{
   try{
    String query="username="+java.net.URLEncoder.encode(user,"UTF-8")+"&password="+java.net.URLEncoder.encode(pass,"UTF-8");
    String api=base+"/player_api.php?"+query;
    org.json.JSONObject login=xtreamJson(api);
    org.json.JSONObject account=login.optJSONObject("user_info");
    if(account==null || !"1".equals(String.valueOf(account.opt("auth"))))throw new IOException("Login não autorizado pela API Xtream. Confirme os dados com o fornecedor.");
    String statusAccount=account.optString("status","");
    if(!statusAccount.isEmpty()&&!"Active".equalsIgnoreCase(statusAccount))throw new IOException("Conta IPTV não está ativa: "+statusAccount);
    msg("Autenticação Xtream confirmada. Obtendo categorias...");
    java.util.HashMap<String,String> live=xtreamCategories(api+"&action=get_live_categories");
    java.util.HashMap<String,String> vod=xtreamCategories(api+"&action=get_vod_categories");
    java.util.HashMap<String,String> series=xtreamCategories(api+"&action=get_series_categories");
    String origin="xtream_"+LocalCatalog.hash(base+"|"+user);
    int total=0;
    total+=xtreamImport(api+"&action=get_live_streams",base,user,pass,"live",live,origin);
    total+=xtreamImport(api+"&action=get_vod_streams",base,user,pass,"movie",vod,origin);
    total+=xtreamImport(api+"&action=get_series",base,user,pass,"series",series,origin);
    msg("Xtream: "+total+" registros importados. Enviando ao Firebase...");
    if(total>0)syncOrigin(origin);
   }catch(Exception e){msg("Xtream Codes: "+xtreamSafeError(e));}
   finally{busy=false;}
  });
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
 private String xtreamValue(android.util.JsonReader r)throws Exception{
  android.util.JsonToken t=r.peek();if(t==android.util.JsonToken.STRING||t==android.util.JsonToken.NUMBER)return r.nextString();if(t==android.util.JsonToken.BOOLEAN)return String.valueOf(r.nextBoolean());r.skipValue();return "";
 }
 private int xtreamImport(String api,String base,String user,String pass,String type,java.util.HashMap<String,String> categories,String origin)throws Exception{
  java.net.HttpURLConnection c=xtreamConnect(api);int count=0;
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
    catalog.save(db,key,name,cat,cover,url,description,cat,origin);count++;
    if(count%150==0){db.setTransactionSuccessful();db.endTransaction();transaction=false;msg("Xtream "+type+": "+count+" registros...");db.beginTransaction();transaction=true;}
   }
   r.endArray();db.setTransactionSuccessful();db.endTransaction();transaction=false;
   return count;
  }finally{if(transaction)db.endTransaction();c.disconnect();}
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
     msg("Pasta: /"+path+" • "+total+" itens • exibindo "+(total==0?0:from+1)+"–"+to+". Exclusão em lotes de até 500.");
     if(!path.isEmpty()&&!path.equals("catalogo_unico")&&!path.equals("catalogo_unico/meta")&&!path.equals("__meta")&&!path.equals("admins")&&!path.equals("config"))button("⚠ Excluir esta pasta",()->confirmDeleteFolder(path));
     for(String key:visible){String child=path.isEmpty()?key:path+"/"+key;button("▣ "+key,()->browseFirebaseFolders(child));}
     if(from>0)button("← Página anterior",()->browseFirebaseFolders(path,Math.max(0,from-FIREBASE_PAGE_SIZE)));
     if(to<total)button("Próxima página →",()->browseFirebaseFolders(path,to));
    });
   }catch(Exception e){if(navigationId==folderNavigationId)msg("Não foi possível listar pastas: "+e.getMessage());}
  }),e->msg("Falha de autenticação: "+e));
 }
 private void confirmDeleteFolder(String path){
  if(!authorized||busy)return;
  if(path.isEmpty()||path.equals("catalogo_unico")||path.equals("catalogo_unico/meta")){msg("Pasta protegida");return;}
  EditText confirm=new EditText(this);confirm.setHint("Digite EXCLUIR");
  new AlertDialog.Builder(this).setTitle("Excluir pasta do Firebase?")
   .setMessage("Pasta: /"+path+"\n\nEsta exclusão é permanente e ocorre em lotes. Faça backup antes. Digite EXCLUIR para confirmar.")
   .setView(confirm).setNegativeButton("Cancelar",null).setPositiveButton("Excluir",(d,w)->{
    if(!"EXCLUIR".equals(confirm.getText().toString().trim())){msg("Confirmação incorreta. Nada excluído.");return;}
    busy=true;msg("Iniciando exclusão da pasta /"+path);
    FirebaseCatalogSync.withToken(auth,token->worker.execute(()->{
     try{int count=FirebaseCatalogSync.deleteFolderBatched(this,path,token,n->msg("Excluindo "+path+": "+n+" registros..."));
      if(path.equals("catalogo_unico/items")){catalog.getWritableDatabase().delete("media",null,null);getSharedPreferences("reel_admin_catalog",MODE_PRIVATE).edit().putBoolean("asset_6760_loaded",true).apply();}
      FirebaseCatalogSync.markChanged(this,token);msg("Exclusão concluída: /"+path+" ("+count+" registros). Atualize outros aparelhos.");
     }catch(Exception e){msg("Exclusão parcial ou falhou: "+e.getMessage());}finally{busy=false;}
    }),e->{busy=false;msg("Autenticação: "+e);});
   }).show();
 }
 private void confirmDeleteAll(){
  if(!authorized||busy)return;
  new AlertDialog.Builder(this).setTitle("Excluir TODO o catálogo?")
   .setMessage("Esta ação remove todos os títulos do Firebase e deste aparelho. Faça um backup JSON antes. Contas, administradores e configurações não serão apagados.")
   .setNegativeButton("Cancelar",null).setPositiveButton("Continuar",(d,w)->{
    EditText confirm=new EditText(this);confirm.setHint("Digite EXCLUIR TUDO");
    new AlertDialog.Builder(this).setTitle("Confirmação final")
     .setMessage("Digite EXCLUIR TUDO para confirmar a exclusão permanente.")
     .setView(confirm).setNegativeButton("Cancelar",null)
     .setPositiveButton("Excluir",(a,b)->{
      if(!"EXCLUIR TUDO".equals(confirm.getText().toString().trim())){msg("Exclusão cancelada: confirmação incorreta.");return;}
      busy=true;msg("Excluindo catálogo do Firebase...");
      FirebaseCatalogSync.withToken(auth,token->worker.execute(()->{
       try{
        FirebaseCatalogSync.deleteAll(this,token);
        catalog.getWritableDatabase().delete("media",null,null);
        getSharedPreferences("reel_admin_catalog",MODE_PRIVATE).edit().putBoolean("asset_6760_loaded",true).apply();
        getSharedPreferences("reel_cloud_cache_v194",MODE_PRIVATE).edit().clear().apply();
        msg("Catálogo apagado do Firebase e deste aparelho. Outros aparelhos precisam atualizar os dados.");
       }catch(Exception e){msg("Exclusão não concluída: "+e.getMessage());}
       finally{busy=false;}
      }),e->{busy=false;msg("Falha de autenticação: "+e);});
     }).show();
   }).show();
 }
 private void syncAll(){if(!authorized||busy)return;msg("Preparando envio completo ao Firebase...");FirebaseCatalogSync.withToken(auth,token->worker.execute(()->{try{int n=FirebaseCatalogSync.upload(this,catalog,null,token,c->msg("Firebase: "+c+" títulos enviados"));FirebaseCatalogSync.markChanged(this,token);msg("Catálogo único sincronizado: "+n+" registros.");}catch(Exception e){msg("Firebase: "+e.getMessage());}}),e->msg("Autenticação Firebase: "+e));}
 private void syncOrigin(String origin){if(!authorized)return;FirebaseCatalogSync.withToken(auth,token->worker.execute(()->{try{int n=FirebaseCatalogSync.upload(this,catalog,origin,token,c->msg("Firebase: "+c+" registros enviados"));FirebaseCatalogSync.markChanged(this,token);msg("Importação sincronizada no Firebase: "+n+" registros.");}catch(Exception e){msg("Importado localmente, mas não enviado ao Firebase: "+e.getMessage());}}),e->msg("Importado localmente; falha de autenticação Firebase: "+e));}
 private void syncOne(String id){if(!authorized)return;FirebaseCatalogSync.withToken(auth,token->worker.execute(()->{try{FirebaseCatalogSync.uploadOne(this,catalog,id,token);FirebaseCatalogSync.markChanged(this,token);msg("Título sincronizado no Firebase.");}catch(Exception e){msg("Salvo localmente; falha no Firebase: "+e.getMessage());}}),e->msg("Falha Firebase: "+e));}
 private void syncDelete(String id){if(!authorized)return;FirebaseCatalogSync.withToken(auth,token->worker.execute(()->{try{FirebaseCatalogSync.deleteOne(this,id,token);FirebaseCatalogSync.markChanged(this,token);}catch(Exception e){msg("Excluído localmente; falha ao excluir do Firebase: "+e.getMessage());}}),e->msg("Falha Firebase: "+e));}
 @Override public void onBackPressed(){finish();}
 @Override protected void onDestroy(){worker.shutdownNow();catalog.close();super.onDestroy();}
}
