package com.reelseries.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import com.google.firebase.firestore.WriteBatch;
import com.google.firebase.firestore.DocumentReference;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;

/** Native administrator panel. Firestore rules must enforce admin permissions. */
public class AdminActivity extends Activity {
    private static final int BG=0xff0b101b, CARD=0xff182232, ACCENT=0xffff4564, WHITE=0xfff7f8fc, MUTED=0xffaab6c8;
    private final FirebaseAuth auth=FirebaseAuth.getInstance();
    private final FirebaseFirestore db=FirebaseFirestore.getInstance();
    private LinearLayout root, list;
    private TextView status;
    private EditText id, title, category, cover, video, synopsis;
    private boolean authorized=false;
    private static final int PICK_JSON=814;
    private boolean importing=false;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    @Override public void onCreate(Bundle b){super.onCreate(b); showLogin();}
    private void base(String heading,String subtitle){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(18),dp(24),dp(18),dp(28));
        scroll.addView(root);setContentView(scroll);
        TextView brand=text("REEL SÉRIES  /  ADMIN",12,ACCENT,true);root.addView(brand);
        TextView h=text(heading,27,WHITE,true);h.setPadding(0,dp(10),0,dp(5));root.addView(h);
        TextView sub=text(subtitle,13,MUTED,false);root.addView(sub);
        status=text("",13,WHITE,false);status.setPadding(dp(12),dp(10),dp(12),dp(10));status.setBackground(shape(CARD,10));
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.setMargins(0,dp(18),0,dp(12));root.addView(status,sp);
    }
    private TextView text(String s,int size,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);if(bold)v.setTypeface(null,Typeface.BOLD);return v;}
    private void info(String s){if(status!=null)status.setText(s);}
    private void section(String s){TextView v=text(s,18,WHITE,true);v.setPadding(0,dp(20),0,dp(10));root.addView(v);}
    private EditText input(String hint,boolean multi){EditText e=new EditText(this);e.setSingleLine(!multi);e.setHint(hint);e.setTextColor(WHITE);e.setHintTextColor(MUTED);e.setTextSize(15);e.setPadding(dp(14),dp(12),dp(14),dp(12));e.setBackground(shape(CARD,12));if(multi){e.setMinLines(3);e.setGravity(Gravity.TOP);}LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(6),0,dp(8));root.addView(e,p);return e;}
    private void button(String label,boolean primary,View.OnClickListener listener){TextView v=text(label,15,WHITE,true);v.setGravity(Gravity.CENTER);v.setPadding(dp(12),dp(15),dp(12),dp(15));v.setBackground(shape(primary?ACCENT:CARD,12));v.setOnClickListener(listener);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(6),0,dp(6));root.addView(v,p);}
    private void showLogin(){authorized=false;base("Administração","Gerencie seu catálogo com segurança.");if(auth.getCurrentUser()!=null){verifyAdmin();return;}EditText email=input("E-mail",false);EditText password=input("Senha",false);password.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);button("ENTRAR",true,v->{String e=email.getText().toString().trim(),p=password.getText().toString();if(e.isEmpty()||p.isEmpty()){info("Preencha e-mail e senha.");return;}info("Entrando...");auth.signInWithEmailAndPassword(e,p).addOnSuccessListener(r->verifyAdmin()).addOnFailureListener(err->info("Falha no login: "+err.getMessage()));});}
    private void verifyAdmin(){if(auth.getCurrentUser()==null){showLogin();return;}info("Verificando permissão...");db.collection("admins").document(auth.getCurrentUser().getUid()).get().addOnSuccessListener(doc->{if(doc.exists()&&Boolean.TRUE.equals(doc.getBoolean("active"))&&"admin".equals(doc.getString("role"))){authorized=true;dashboard();}else{info("Esta conta não possui permissão de administrador.");button("TROCAR CONTA",false,v->{auth.signOut();showLogin();});}}).addOnFailureListener(err->info("Falha ao consultar permissão: "+err.getMessage()));}
    private void dashboard(){if(!authorized){showLogin();return;}base("Painel de controle","Administrador conectado • Firebase Firestore");info("Gerencie títulos cadastrados em catalogo_admin. O catálogo local do APK não é alterado por esta tela.");button("＋ ADICIONAR FILME OU SÉRIE",true,v->editor(null));button("⇧ IMPORTAR ARQUIVO JSON",true,v->chooseJson());button("↻ ATUALIZAR LISTA",false,v->dashboard());section("Títulos cadastrados no Firebase");list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);root.addView(list);loadList();section("Conta");button("SAIR DA CONTA",false,v->{auth.signOut();showLogin();});}
    private void chooseJson(){
        if(!authorized||auth.getCurrentUser()==null){info("Entre como administrador.");return;}
        if(importing){info("Aguarde a importação atual terminar.");return;}
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/json","text/plain","application/octet-stream"});
        startActivityForResult(intent,PICK_JSON);
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==PICK_JSON&&resultCode==RESULT_OK&&data!=null&&data.getData()!=null){
            Uri uri=data.getData();
            new AlertDialog.Builder(this).setTitle("Importar catálogo JSON")
                .setMessage("Os registros serão enviados para catalogo_admin no Firebase. IDs existentes serão atualizados. A operação pode gerar custos no Firestore. Continuar?")
                .setNegativeButton("Cancelar",null)
                .setPositiveButton("Importar",(d,w)->parseJson(uri)).show();
        }
    }
    private void parseJson(Uri uri){
        if(importing)return;
        importing=true;info("Lendo arquivo JSON...");
        Executors.newSingleThreadExecutor().execute(()->{
            try(InputStream input=getContentResolver().openInputStream(uri)){
                if(input==null)throw new Exception("Arquivo não disponível.");
                ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[32768];int n;
                while((n=input.read(buffer))!=-1){out.write(buffer,0,n);if(out.size()>40*1024*1024)throw new Exception("JSON maior que 40 MB. Divida o arquivo.");}
                Object parsed=new JSONTokener(out.toString("UTF-8")).nextValue();
                JSONArray records;
                if(parsed instanceof JSONArray)records=(JSONArray)parsed;
                else if(parsed instanceof JSONObject){JSONObject obj=(JSONObject)parsed;
                    records=obj.optJSONArray("catalogo");if(records==null)records=obj.optJSONArray("items");
                    if(records==null)records=obj.optJSONArray("filmes");if(records==null)records=obj.optJSONArray("data");
                    if(records==null)throw new Exception("Formato não reconhecido: esperado array JSON ou objeto com catalogo/items/filmes/data.");
                }else throw new Exception("O arquivo precisa conter uma lista JSON.");
                List<ImportItem> items=new ArrayList<>();int skipped=0;
                for(int i=0;i<records.length();i++){
                    JSONObject obj=records.optJSONObject(i);if(obj==null){skipped++;continue;}
                    String name=first(obj,"nome","titulo","title","name").trim();
                    if(name.isEmpty()){skipped++;continue;}
                    String key=first(obj,"id","_id","key").trim();
                    if(key.isEmpty())key="import_"+sha256(name+"|"+first(obj,"episodio","episode")+"|"+first(obj,"url","video","link"));
                    if(key.contains("/")||key.length()>500){skipped++;continue;}
                    Map<String,Object> fields=new HashMap<>();fields.put("nome",name);
                    fields.put("categoria",first(obj,"categoria","category","tipo"));
                    fields.put("capa",first(obj,"capa","poster","imagem","image"));
                    fields.put("video",first(obj,"video","url","link","stream"));
                    fields.put("sinopse",first(obj,"sinopse","descricao","description","overview"));
                    String episode=first(obj,"episodio","episode");if(!episode.isEmpty())fields.put("episodio",episode);
                    fields.put("updatedAt",FieldValue.serverTimestamp());
                    items.add(new ImportItem(key,fields));
                }
                int rejected=skipped;
                ui.post(()->{if(items.isEmpty()){importing=false;info("Nenhum registro válido. Ignorados: "+rejected);return;}info("Arquivo validado: "+items.size()+" títulos. Ignorados: "+rejected+". Enviando lotes...");uploadBatches(items,0,rejected);});
            }catch(Exception e){ui.post(()->{importing=false;info("Erro ao importar: "+e.getMessage());});}
        });
    }
    private static class ImportItem{
        final String id;final Map<String,Object> data;
        ImportItem(String id,Map<String,Object> data){this.id=id;this.data=data;}
    }
    private String first(JSONObject obj,String... keys){for(String key:keys){String v=obj.optString(key,"");if(!v.isEmpty()&&!"null".equalsIgnoreCase(v))return v;}return "";}
    private String sha256(String value){try{byte[] bytes=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder sb=new StringBuilder();for(int i=0;i<12;i++)sb.append(String.format(java.util.Locale.US,"%02x",bytes[i]&0xff));return sb.toString();}catch(Exception e){return Integer.toHexString(value.hashCode());}}
    private void uploadBatches(List<ImportItem> items,int offset,int rejected){
        if(isFinishing()){importing=false;return;}
        if(offset>=items.size()){importing=false;info("Importação concluída: "+items.size()+" registros enviados. Ignorados: "+rejected+".");new AlertDialog.Builder(this).setTitle("Importação concluída").setMessage(items.size()+" registros enviados ao Firebase. Volte ao catálogo para visualizar as alterações.").setPositiveButton("OK",(d,w)->dashboard()).show();return;}
        int end=Math.min(offset+200,items.size());WriteBatch batch=db.batch();
        for(int i=offset;i<end;i++){ImportItem item=items.get(i);DocumentReference ref=db.collection("catalogo_admin").document(item.id);batch.set(ref,item.data);}
        info("Enviando "+end+" de "+items.size()+" títulos...");
        batch.commit().addOnSuccessListener(v->uploadBatches(items,end,rejected))
            .addOnFailureListener(e->{importing=false;info("Falha após "+offset+" registros. Parte dos lotes anteriores pode ter sido salva. Execute novamente para continuar: "+e.getMessage());});
    }
    private void loadList(){list.removeAllViews();TextView loading=text("Carregando títulos...",14,MUTED,false);list.addView(loading);db.collection("catalogo_admin").limit(60).get().addOnSuccessListener(result->{list.removeAllViews();if(result.isEmpty()){list.addView(text("Nenhum título cadastrado no Firebase.",14,MUTED,false));return;}for(DocumentSnapshot doc:result.getDocuments()){String name=doc.getString("nome");TextView item=text((name==null?doc.getId():name)+"\n"+doc.getId(),15,WHITE,true);item.setPadding(dp(14),dp(15),dp(14),dp(15));item.setBackground(shape(CARD,12));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(4),0,dp(5));list.addView(item,p);item.setOnClickListener(v->editor(doc.getId()));}info("Mostrando até 60 títulos do Firestore.");}).addOnFailureListener(e->{list.removeAllViews();list.addView(text("Não foi possível listar: "+e.getMessage(),14,ACCENT,false));});}
    private void editor(String key){if(!authorized){showLogin();return;}base(key==null?"Novo título":"Editar título","Preencha os dados e salve no Firebase.");id=input("ID único do título",false);title=input("Título",false);category=input("Categoria (filme, série, novela...)",false);cover=input("URL HTTPS da capa",false);video=input("URL HTTPS do vídeo",false);synopsis=input("Sinopse",true);if(key!=null){id.setText(key);id.setEnabled(false);fetch(key);}button("SALVAR TÍTULO",true,v->save());if(key!=null)button("EXCLUIR TÍTULO",false,v->confirmDelete(key));button("← VOLTAR AO PAINEL",false,v->dashboard());}
    private void fetch(String key){info("Carregando título...");db.collection("catalogo_admin").document(key).get().addOnSuccessListener(d->{if(!d.exists()){info("Título não encontrado.");return;}title.setText(nvl(d.getString("nome")));category.setText(nvl(d.getString("categoria")));cover.setText(nvl(d.getString("capa")));video.setText(nvl(d.getString("video")));synopsis.setText(nvl(d.getString("sinopse")));info("Título carregado.");}).addOnFailureListener(e->info("Erro: "+e.getMessage()));}
    private String nvl(String s){return s==null?"":s;}
    private boolean validUrl(String s){return s.isEmpty()||s.startsWith("https://");}
    private void save(){String key=id.getText().toString().trim(),name=title.getText().toString().trim(),c=cover.getText().toString().trim(),v=video.getText().toString().trim();if(key.isEmpty()||name.isEmpty()||key.contains("/")){info("Informe um ID válido (sem /) e o título.");return;}if(!validUrl(c)||!validUrl(v)){info("Use URLs HTTPS para capa e vídeo.");return;}Map<String,Object> data=new HashMap<>();data.put("nome",name);data.put("categoria",category.getText().toString().trim());data.put("capa",c);data.put("video",v);data.put("sinopse",synopsis.getText().toString().trim());data.put("updatedAt",FieldValue.serverTimestamp());info("Salvando...");db.collection("catalogo_admin").document(key).set(data).addOnSuccessListener(x->{info("Título salvo com sucesso.");new AlertDialog.Builder(this).setMessage("Título salvo no Firestore.").setPositiveButton("Voltar ao painel",(d,w)->dashboard()).setNegativeButton("Continuar",null).show();}).addOnFailureListener(e->info("Falha ao salvar: "+e.getMessage()));}
    private void confirmDelete(String key){new AlertDialog.Builder(this).setTitle("Excluir título?").setMessage("Esta ação remove o título "+key+" do catálogo administrativo no Firestore.").setNegativeButton("Cancelar",null).setPositiveButton("Excluir",(d,w)->{info("Excluindo...");db.collection("catalogo_admin").document(key).delete().addOnSuccessListener(x->dashboard()).addOnFailureListener(e->info("Falha ao excluir: "+e.getMessage()));}).show();}
    @Override public void onBackPressed(){if(authorized)dashboard();else super.onBackPressed();}
}
