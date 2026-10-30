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
  screen("Central Administrativa");msg("Painel Premium V19.1  •  Catálogo salvo neste aparelho");
  tileRow(tile("▤",java.text.NumberFormat.getIntegerInstance(new java.util.Locale("pt","BR")).format(catalog.count()),"Registros locais",()->browseTitles(0)),tile("☷","M3U / M3U8","Importação",()->urlDialog(false)));
  tileRow(tile("▣","Catálogo","Gerenciamento",this::manageTitles),tile("⚙","Sistema","Configurações",this::settingsPanel));
  section("Importar playlist");
  button("🔗  Adicionar link de playlist M3U",()->urlDialog(false));
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
  section("Importação");button("↻  Atualizar playlist por URL",()->urlDialog(false));button("⇧  Importar arquivo JSON",()->pick(JSON));button("⇧  Importar arquivo M3U",()->pick(M3U));
  section("Diagnósticos");button("⚠  Títulos sem vídeo",()->diagnostic("sem_video",0));button("▧  Títulos sem capa",()->diagnostic("sem_capa",0));button("≡  Categorias vazias",()->diagnostic("sem_categoria",0));
  section("Backup e segurança");button("⇩  Exportar backup JSON",this::exportCatalog);button("♟  Permissões administrativas",()->new AlertDialog.Builder(this).setTitle("Acesso administrativo").setMessage("O acesso é validado no Firestore em admins/{uid}, com active=true e role=admin.").setPositiveButton("OK",null).show());button("↻  Atualizar painel",this::dashboard);button("Sair da conta",()->{auth.signOut();login();});button("←  Voltar",this::dashboard);
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
 private void exportCatalog(){if(busy){msg("Aguarde a operação atual.");return;}Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");i.putExtra(Intent.EXTRA_TITLE,"reelseries-backup.json");startActivityForResult(i,EXPORT);}
 private void writeBackup(Uri uri){if(busy)return;busy=true;msg("Exportando backup para o arquivo selecionado...");worker.execute(()->{try(OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new IOException("Não foi possível abrir o destino");int n=catalog.exportJson(out,c->msg("Exportados: "+c));msg("Backup concluído: "+n+" títulos. Guarde o arquivo em local seguro.");}catch(Exception e){msg("Erro no backup: "+e.getMessage());}finally{busy=false;}});}
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
 private void editLocal(org.json.JSONObject item){screen("Editar conteúdo local");EditText name=field("Nome"),cat=field("Categoria"),cover=field("URL da capa"),url=field("URL do vídeo"),syn=field("Sinopse");name.setText(item.optString("nome"));cat.setText(item.optString("categoria"));cover.setText(item.optString("capa"));url.setText(item.optString("url"));syn.setText(item.optString("sinopse"));button("Salvar alterações",()->{String n=name.getText().toString().trim();if(n.isEmpty()){msg("Informe o nome");return;}catalog.save(catalog.getWritableDatabase(),item.optString("id"),n,cat.getText().toString(),cover.getText().toString(),url.getText().toString(),syn.getText().toString(),"","edição local");msg("Alterações salvas no celular.");});button("Excluir título",()->new AlertDialog.Builder(this).setTitle("Excluir este título?").setMessage(item.optString("nome")).setNegativeButton("Cancelar",null).setPositiveButton("Excluir",(d,w)->{catalog.getWritableDatabase().delete("media","id=?",new String[]{item.optString("id")});manageTitles();}).show());button("Voltar à pesquisa",this::manageTitles);}
 private void pick(int type){if(busy){msg("Aguarde a importação terminar.");return;}Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");startActivityForResult(i,type);}
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;if(request==EXPORT){if(authorized)writeBackup(data.getData());return;}if(request!=JSON&&request!=M3U)return;Uri uri=data.getData();new AlertDialog.Builder(this).setTitle("Importar no celular").setMessage("Os dados serão salvos SOMENTE neste aparelho. Continuar?").setNegativeButton("Cancelar",null).setPositiveButton("Importar",(d,w)->importFile(uri,request)).show();}
 private void importFile(Uri uri,int type){if(busy||!authorized)return;busy=true;msg("Importando para SQLite local...");worker.execute(()->{try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("Arquivo não disponível");int n=type==JSON?catalog.importJson(in,c->msg("JSON: "+c+" registros locais...")):catalog.importM3u(in,"arquivo:"+uri.toString(),c->msg("M3U: "+c+" canais locais..."));msg("Importação concluída: "+n+" registros. Volte à tela principal para visualizar.");}catch(Exception e){msg("Falha na importação: "+e.getMessage());}finally{busy=false;}});}
 private void urlDialog(boolean stream){EditText input=new EditText(this);input.setSingleLine(false);input.setHint(stream?"https://servidor/canal.m3u8":"https://servidor/lista.m3u");new AlertDialog.Builder(this).setTitle(stream?"Adicionar stream M3U8":"Importar playlist M3U").setView(input).setNegativeButton("Cancelar",null).setPositiveButton("Continuar",(d,w)->{String url=input.getText().toString().trim();if(!url.startsWith("https://")&&!url.startsWith("http://")){msg("Use uma URL HTTP ou HTTPS válida.");return;}if(stream)streamName(url);else importUrl(url);}).show();}
 private void streamName(String url){EditText name=new EditText(this);name.setHint("Nome do canal");new AlertDialog.Builder(this).setTitle("Nome do stream").setView(name).setNegativeButton("Cancelar",null).setPositiveButton("Salvar",(d,w)->{String n=name.getText().toString().trim();if(n.isEmpty())n="Canal ao vivo";catalog.addStream(n,url);msg("Stream salvo localmente: "+n);}).show();}
 private String explainNetworkError(Exception e,String url){
  String detail=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();
  if(e instanceof SSLException || detail.toLowerCase(java.util.Locale.ROOT).contains("tls") || detail.toLowerCase(java.util.Locale.ROOT).contains("ssl"))
   return "Falha na conexão segura (TLS/HTTPS). O servidor pode não aceitar HTTPS nesta porta ou ter configuração TLS incorreta. Confirme com o fornecedor o endereço e o protocolo corretos (HTTP ou HTTPS). Não é seguro ignorar a validação do certificado. Detalhe: "+detail;
  if(e instanceof java.net.UnknownHostException)return "Servidor não encontrado. Confira o endereço da playlist e a conexão com a internet. Detalhe: "+detail;
  if(e instanceof java.net.SocketTimeoutException)return "O servidor demorou para responder. Tente novamente ou consulte o fornecedor. Detalhe: "+detail;
  if(e instanceof java.net.ConnectException)return "Não foi possível conectar ao servidor. Verifique endereço, porta e disponibilidade. Detalhe: "+detail;
  return "Falha ao importar playlist: "+detail;
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
     int n=catalog.importM3u(in,url,count->msg("Importando M3U: "+count+" registros..."));
     msg("Importação concluída: "+n+" registros no armazenamento local. Volte ao catálogo para visualizar.");
    }
   }catch(Exception e){msg(explainNetworkError(e,url));}
   finally{if(c!=null)c.disconnect();busy=false;}
  });
 }
 private void manual(){screen("Adicionar conteúdo local");EditText id=field("Nome do filme ou canal"),cat=field("Categoria"),cover=field("URL da capa"),url=field("URL do vídeo MP4/M3U8"),syn=field("Sinopse");button("Salvar no celular",()->{String name=id.getText().toString().trim();if(name.isEmpty()){msg("Informe o nome");return;}String link=url.getText().toString().trim();catalog.save(catalog.getWritableDatabase(),"manual_"+LocalCatalog.hash(name+"|"+link),name,cat.getText().toString(),cover.getText().toString(),link,syn.getText().toString(),"","manual");msg("Salvo no armazenamento interno.");});button("Voltar",this::dashboard);}
 @Override public void onBackPressed(){if(authorized)dashboard();else super.onBackPressed();}
 @Override protected void onDestroy(){worker.shutdownNow();catalog.close();super.onDestroy();}
}
