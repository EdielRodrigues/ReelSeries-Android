package com.reelseries.android;

import android.app.*;
import android.os.*;
import android.content.*;
import android.net.Uri;
import android.graphics.Color;
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
 private void screen(String title){ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(0xff0b101b);root=new LinearLayout(this);root.setPadding(24,28,24,36);root.setOrientation(1);scroll.addView(root);setContentView(scroll);TextView h=new TextView(this);h.setText("REEL SÉRIES • ADMIN\n"+title);h.setTextSize(24);h.setTextColor(0xffff4564);root.addView(h);status=new TextView(this);status.setTextColor(Color.WHITE);status.setPadding(0,20,0,20);root.addView(status);}
 private EditText field(String hint){EditText e=new EditText(this);e.setHint(hint);e.setTextColor(-1);e.setHintTextColor(0xffaaaaaa);root.addView(e);return e;}
 private void button(String label,Runnable action){Button b=new Button(this);b.setText(label);b.setAllCaps(false);root.addView(b);b.setOnClickListener(v->action.run());}
 private void msg(String s){runOnUiThread(()->{if(!isFinishing()&&status!=null)status.setText(s);});}
 private void login(){authorized=false;screen("Acesso protegido");if(auth.getCurrentUser()!=null){verify();return;}EditText email=field("E-mail"),pass=field("Senha");pass.setInputType(129);button("Entrar",()->{msg("Autenticando...");auth.signInWithEmailAndPassword(email.getText().toString().trim(),pass.getText().toString()).addOnSuccessListener(x->verify()).addOnFailureListener(e->msg(e.getMessage()));});}
 private void verify(){if(auth.getCurrentUser()==null){login();return;}msg("Verificando administrador...");firestore.collection("admins").document(auth.getCurrentUser().getUid()).get().addOnSuccessListener(d->{if(d.exists()&&Boolean.TRUE.equals(d.getBoolean("active"))&&"admin".equals(d.getString("role"))){authorized=true;dashboard();seedBundledCatalog();}else{msg("Conta sem autorização.");button("Trocar conta",()->{auth.signOut();login();});}}).addOnFailureListener(e->msg("Erro de autorização: "+e.getMessage()));}
 private TextView panelText(String text,int size){TextView t=new TextView(this);t.setText(text);t.setTextColor(-1);t.setTextSize(size);t.setPadding(18,15,18,15);return t;}
 private void dashboard(){if(!authorized){login();return;}screen("PAINEL PREMIUM V13");msg("Banco interno SQLite • Firebase somente para acesso administrativo");
 LinearLayout stats=new LinearLayout(this);stats.setOrientation(0);root.addView(stats);TextView total=panelText("REGISTROS LOCAIS\n"+catalog.count(),20);total.setBackgroundColor(0xff192233);stats.addView(total,new LinearLayout.LayoutParams(0,-2,1));TextView apk=panelText("FONTE\nAndroid",18);apk.setBackgroundColor(0xff192233);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,-2,1);ap.leftMargin=10;stats.addView(apk,ap);
 button("＋ Adicionar filme, série ou canal",this::manual);
 button("⇧ Importar arquivo JSON",()->pick(JSON));button("⇧ Importar arquivo M3U",()->pick(M3U));
 button("🔗 Adicionar link M3U (playlist)",()->urlDialog(false));button("▶ Adicionar link M3U8 (stream)",()->urlDialog(true));
 button("↻ Atualizar lista IPTV por URL",()->urlDialog(false));
 button("⌕ Ver todos os títulos e gerenciar",()->browseTitles(0));
 button("⌕ Pesquisar e gerenciar títulos",this::manageTitles);
 button("◉ Estatísticas avançadas",this::statistics);
 button("⚠ Diagnóstico: sem vídeo",()->diagnostic("sem_video",0));
 button("▧ Diagnóstico: sem capa",()->diagnostic("sem_capa",0));
 button("≡ Diagnóstico: categorias vazias",()->diagnostic("sem_categoria",0));
 button("⇩ Exportar backup JSON do catálogo",this::exportCatalog);
 button("↻ Atualizar painel",this::dashboard);
 button("♟ Gerenciar acesso do administrador",()->msg("O acesso é validado em admins/{uid} no Firebase. Gerenciamento de outros usuários requer regras e telas próprias."));
 button("Sair da conta",()->{auth.signOut();login();});}
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
 private void importUrl(String url){if(busy)return;busy=true;msg("Baixando lista M3U...");worker.execute(()->{HttpURLConnection c=null;try{URL u=new URL(url);if(!u.getProtocol().equals("http")&&!u.getProtocol().equals("https"))throw new IOException("Protocolo inválido");c=(HttpURLConnection)u.openConnection();c.setConnectTimeout(12000);c.setReadTimeout(20000);c.setInstanceFollowRedirects(true);int code=c.getResponseCode();if(code<200||code>=300)throw new IOException("Servidor retornou HTTP "+code);try(InputStream in=c.getInputStream()){int n=catalog.importM3u(in,url,count->msg("Lista: "+count+" registros salvos..."));msg("Lista importada no celular: "+n+" registros.");}}catch(Exception e){msg("Erro ao importar URL: "+e.getMessage());}finally{if(c!=null)c.disconnect();busy=false;}});}
 private void manual(){screen("Adicionar conteúdo local");EditText id=field("Nome do filme ou canal"),cat=field("Categoria"),cover=field("URL da capa"),url=field("URL do vídeo MP4/M3U8"),syn=field("Sinopse");button("Salvar no celular",()->{String name=id.getText().toString().trim();if(name.isEmpty()){msg("Informe o nome");return;}String link=url.getText().toString().trim();catalog.save(catalog.getWritableDatabase(),"manual_"+LocalCatalog.hash(name+"|"+link),name,cat.getText().toString(),cover.getText().toString(),link,syn.getText().toString(),"","manual");msg("Salvo no armazenamento interno.");});button("Voltar",this::dashboard);}
 @Override public void onBackPressed(){if(authorized)dashboard();else super.onBackPressed();}
 @Override protected void onDestroy(){worker.shutdownNow();catalog.close();super.onDestroy();}
}
