package com.reelseries.android;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.google.firebase.auth.FirebaseAuth;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.util.*;

/** REST client: no extra firebase-database Gradle dependency required. */
public final class FirebaseCatalogSync {
 private FirebaseCatalogSync(){}
 public interface Done {void accept(String token);}
 public interface Failed {void accept(String error);}
 public static void withToken(FirebaseAuth auth,Done done,Failed fail){
  if(auth.getCurrentUser()==null){fail.accept("Faça login como administrador");return;}
  auth.getCurrentUser().getIdToken(false).addOnSuccessListener(t->done.accept(t.getToken())).addOnFailureListener(e->fail.accept(e.getMessage()));
 }
 private static String base(Context ctx)throws IOException {
  // Use the confirmed Realtime Database instance; never append .json to the host.
  return "https://smart-tv-c175a-default-rtdb.firebaseio.com";
 }
 public static boolean configured(Context c){try{base(c);return true;}catch(Exception e){return false;}}
 private static String request(String address,String method,String payload)throws IOException{
  HttpURLConnection conn=(HttpURLConnection)new URL(address).openConnection();
  conn.setConnectTimeout(15000);conn.setReadTimeout(45000);conn.setRequestMethod(method);conn.setRequestProperty("Accept","application/json");
  try{
   if(payload!=null){conn.setDoOutput(true);conn.setRequestProperty("Content-Type","application/json; charset=utf-8");try(OutputStream out=conn.getOutputStream()){out.write(payload.getBytes("UTF-8"));}}
   int code=conn.getResponseCode();InputStream stream=code>=200&&code<300?conn.getInputStream():conn.getErrorStream();
   ByteArrayOutputStream out=new ByteArrayOutputStream();if(stream!=null)try(InputStream in=stream){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>10*1024*1024)throw new IOException("Resposta Firebase grande demais");}}
   String result=out.toString("UTF-8");if(code<200||code>=300)throw new IOException("HTTP "+code+" Firebase: "+result.substring(0,Math.min(180,result.length())));
   return result;
  }finally{conn.disconnect();}
 }
 private static String endpoint(Context ctx,String suffix,String token)throws Exception{
  return base(ctx)+"/catalogo_unico/"+suffix+".json"+(token==null?"":"?auth="+URLEncoder.encode(token,"UTF-8"));
 }
 private static JSONObject item(Cursor c)throws Exception{
  JSONObject o=new JSONObject();for(int i=0;i<c.getColumnCount();i++)o.put(c.getColumnName(i),c.isNull(i)?JSONObject.NULL:c.getString(i));return o;
 }
 /** Envia somente IDs ausentes no Firebase. Nunca sobrescreve registros existentes. */
 public static int upload(Context ctx,LocalCatalog db,String origin,String token,LocalCatalog.Progress progress)throws Exception{
  // Consulta apenas as chaves: nao baixa nem modifica os 118 mil registros completos.
  String keysJson=request(endpoint(ctx,"items",token)+"&shallow=true","GET",null);
  HashSet<String> existing=new HashSet<>();
  if(!"null".equals(keysJson.trim())){
   JSONObject keys=new JSONObject(keysJson);
   Iterator<String> it=keys.keys();
   while(it.hasNext())existing.add(it.next());
  }
  SQLiteDatabase sql=db.getReadableDatabase();int sent=0;String last="";
  while(true){
   String query="SELECT id,nome,categoria,capa,url,sinopse,grupo,origem FROM media WHERE id>?"+(origin==null?"":" AND origem=?")+" ORDER BY id LIMIT 100";
   String[] args=origin==null?new String[]{last}:new String[]{last,origin};
   JSONObject patch=new JSONObject();int scanned=0;
   try(Cursor c=sql.rawQuery(query,args)){
    while(c.moveToNext()){
     String id=c.getString(0);last=id;scanned++;
     if(existing.add(id))patch.put(id,item(c));
    }
   }
   if(scanned==0)break;
   if(patch.length()>0){
    // PATCH atomico por lote. Se falhar, nao marca itens como enviados nesta execucao.
    request(endpoint(ctx,"items",token),"PATCH",patch.toString());
    sent+=patch.length();progress.update(sent);
   }
   if(scanned<100)break;
  }
  return sent;
 }
 public static void uploadOne(Context ctx,LocalCatalog db,String id,String token)throws Exception{
  try(Cursor c=db.getReadableDatabase().rawQuery("SELECT id,nome,categoria,capa,url,sinopse,grupo,origem FROM media WHERE id=?",new String[]{id})){
   if(!c.moveToFirst())throw new IOException("Título não encontrado no banco local");
   request(endpoint(ctx,"items/"+URLEncoder.encode(id,"UTF-8"),token),"PUT",item(c).toString());
  }
 }
 /** Lists direct child keys without downloading the full catalog. */
 public static java.util.ArrayList<String> listFolders(Context ctx,String path,String token)throws Exception{
  validatePath(path);
  String node=path.isEmpty()?"/":"/"+path;
  // For the root this MUST be /.json, not .com.json.
  String address=base(ctx)+node+".json?shallow=true"+(token==null||token.isEmpty()?"":"&auth="+URLEncoder.encode(token,"UTF-8"));
  String body=request(address,"GET",null);
  java.util.ArrayList<String> keys=new java.util.ArrayList<>();
  if(body.equals("null"))return keys;
  if(!body.trim().startsWith("{"))return keys;
  JSONObject obj=new JSONObject(body);java.util.Iterator<String> it=obj.keys();while(it.hasNext())keys.add(it.next());
  java.util.Collections.sort(keys,String.CASE_INSENSITIVE_ORDER);return keys;
 }
 private static void validatePath(String path)throws IOException{
  if(path==null||path.contains("..")||path.contains("#")||path.contains("$")||path.contains("[")||path.contains("]")||path.contains("\\"))throw new IOException("Caminho Firebase inválido");
  if(!path.isEmpty())for(String segment:path.split("/",-1))if(segment.isEmpty()||segment.equals(".")||segment.equals(".."))throw new IOException("Caminho Firebase inválido");
 }
 /** Remove direct children in batches of 500, including their nested data.
  * Never traverse each episode individually: doing so caused thousands of network calls.
  * If Firebase rejects a batch because of its size, retry smaller batches. */
 public static int deleteFolderBatched(Context ctx,String path,String token,LocalCatalog.Progress progress)throws Exception{
  validatePath(path);
  if(path.isEmpty()||path.equals("catalogo_unico")||path.equals("catalogo_unico/meta")||path.equals("__meta")||path.equals("admins")||path.equals("config"))throw new IOException("Pasta protegida; selecione uma subpasta");
  int total=0;
  // A shallow read returns child keys only, not the huge episode objects.
  java.util.ArrayList<String> keys=listFolders(ctx,path,token);
  if(keys.isEmpty()){
   request(base(ctx)+"/"+path+".json?auth="+URLEncoder.encode(token,"UTF-8"),"DELETE",null);
   progress.update(1);return 1;
  }
  for(int offset=0;offset<keys.size();offset+=500){
   int end=Math.min(keys.size(),offset+500);
   deleteLeafBatch(ctx,path,keys.subList(offset,end),token);
   total+=end-offset;progress.update(total);
  }
  // Remove the now-empty parent node too.
  request(base(ctx)+"/"+path+".json?auth="+URLEncoder.encode(token,"UTF-8"),"DELETE",null);
  return total;
 }
 private static void deleteLeafBatch(Context ctx,String parent,java.util.List<String> keys,String token)throws Exception{
  if(keys.isEmpty())return;
  JSONObject patch=new JSONObject();for(String key:keys)patch.put(key,JSONObject.NULL);
  try{
   request(base(ctx)+"/"+parent+".json?auth="+URLEncoder.encode(token,"UTF-8"),"PATCH",patch.toString());
  }catch(IOException e){
   // RTDB can reject a large write even when the PATCH itself is small.
   // Split it without losing the already completed batches.
   if(keys.size()<=1)throw e;
   int half=keys.size()/2;
   deleteLeafBatch(ctx,parent,keys.subList(0,half),token);
   deleteLeafBatch(ctx,parent,keys.subList(half,keys.size()),token);
  }
 }
 public static void deleteAll(Context ctx,String token)throws Exception{
  deleteFolderBatched(ctx,"catalogo_unico/items",token,n->{});
  markChanged(ctx,token);
 }
 public static void deleteOne(Context ctx,String id,String token)throws Exception{
  request(endpoint(ctx,"items/"+URLEncoder.encode(id,"UTF-8"),token),"DELETE",null);
 }
 /** Public APK release metadata. APK download itself is hosted outside Firebase RTDB. */
 public static void publishApk(Context ctx,String token,String apkUrl,int version)throws Exception{
  if(!apkUrl.startsWith("https://"))throw new IOException("Informe um link HTTPS direto para o APK");
  if(version<1)throw new IOException("Código da versão inválido");
  JSONObject info=new JSONObject();info.put("url",apkUrl);info.put("versionCode",version);info.put("publishedAt",System.currentTimeMillis());
  request(endpoint(ctx,"meta/apk_update",token),"PUT",info.toString());
 }
 public static JSONObject apkRelease(Context ctx)throws Exception{
  String response=request(endpoint(ctx,"meta/apk_update",null),"GET",null);
  return response.equals("null")?new JSONObject():new JSONObject(response);
 }
 /** Version marker: updated only after an administrator successfully changes cloud data. */
 public static void markChanged(Context ctx,String token)throws Exception{
  request(endpoint(ctx,"meta/revision",token),"PUT","{\".sv\":\"timestamp\"}");
 }
 public static String revision(Context ctx)throws Exception{
  String value=request(endpoint(ctx,"meta/revision",null),"GET",null).trim();
  return value.equals("null")?"legacy":value;
 }
 public static int download(Context ctx,LocalCatalog db,LocalCatalog.Progress progress)throws Exception{
  String baseUrl=base(ctx)+"/catalogo_unico/items.json?orderBy=%22%24key%22&limitToFirst=201";
  String last=null;int count=0;
  for(int pages=0;pages<2000;pages++){
   String u=baseUrl+(last==null?"":"&startAt="+URLEncoder.encode(JSONObject.quote(last),"UTF-8"));
   String response=request(u,"GET",null);
   if(response.equals("null")||response.trim().isEmpty())break;
   JSONObject items=new JSONObject(response);ArrayList<String> keys=new ArrayList<>();Iterator<String> it=items.keys();while(it.hasNext())keys.add(it.next());Collections.sort(keys);
   if(keys.isEmpty())break;
   int received=0;SQLiteDatabase sql=db.getWritableDatabase();sql.beginTransaction();
   try{
    for(String key:keys){if(last!=null&&key.equals(last))continue;JSONObject o=items.optJSONObject(key);if(o==null)continue;
     String id=o.optString("id",key);db.save(sql,id,o.optString("nome"),o.optString("categoria"),o.optString("capa"),o.optString("url"),o.optString("sinopse"),o.optString("grupo"),o.optString("origem","Firebase"));count++;received++;
    }
    sql.setTransactionSuccessful();
   }finally{sql.endTransaction();}
   String next=keys.get(keys.size()-1);if(next.equals(last))break;last=next;progress.update(count);
   if(keys.size()<201)break;
  }
  return count;
 }
}
