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
 /** Three independent RTDB catalogs; original category names remain unchanged. */
 private static String catalogPath(String category){
  String c=category==null?"":category.toLowerCase(java.util.Locale.ROOT);
  if(c.contains("iptv")||c.contains("tv ao vivo")||c.contains("canal")||c.equals("tv")||c.contains("televis"))return "catalogo_3_iptv";
  if(c.contains("serie")||c.contains("série")||c.contains("novela")||c.contains("episod"))return "catalogo_1_series";
  return "catalogo_2_filmes";
 }
 private static String catalogEndpoint(Context ctx,String folder,String id,String token)throws Exception{
  return base(ctx)+"/"+folder+"/items"+(id==null?"":"/"+URLEncoder.encode(id,"UTF-8"))
    +".json"+(token==null?"":"?auth="+URLEncoder.encode(token,"UTF-8"));
 }
 private static JSONObject item(Cursor c)throws Exception{
  JSONObject o=new JSONObject();for(int i=0;i<c.getColumnCount();i++)o.put(c.getColumnName(i),c.isNull(i)?JSONObject.NULL:c.getString(i));return o;
 }
 /** Envia os registros locais em lotes, atualizando também registros existentes. */
 public static int upload(Context ctx,LocalCatalog db,String origin,String token,LocalCatalog.Progress progress)throws Exception{
  SQLiteDatabase sql=db.getReadableDatabase();int sent=0;String last="";
  while(true){
   String query="SELECT id,nome,categoria,capa,url,sinopse,grupo,origem,series_id,episode_id,parent_series_id,season_number,episode_number FROM media WHERE id>?"+(origin==null?"":" AND origem=?")+" ORDER BY id LIMIT 50";
   String[] args=origin==null?new String[]{last}:new String[]{last,origin};
   java.util.LinkedHashMap<String,JSONObject> batches=new java.util.LinkedHashMap<>();int scanned=0;
   try(Cursor c=sql.rawQuery(query,args)){
    while(c.moveToNext()){
     String id=c.getString(0);last=id;scanned++;
     String folder=catalogPath(c.getString(2));
     JSONObject patch=batches.get(folder);if(patch==null){patch=new JSONObject();batches.put(folder,patch);}
     patch.put(id,item(c));
    }
   }
   if(scanned==0)break;
   for(java.util.Map.Entry<String,JSONObject> batch:batches.entrySet())
    request(catalogEndpoint(ctx,batch.getKey(),null,token),"PATCH",batch.getValue().toString());
   sent+=scanned;progress.update(sent);
   if(scanned<50)break;
  }
  return sent;
 }
 public static void uploadOne(Context ctx,LocalCatalog db,String id,String token)throws Exception{
  try(Cursor c=db.getReadableDatabase().rawQuery("SELECT id,nome,categoria,capa,url,sinopse,grupo,origem,series_id,episode_id,parent_series_id,season_number,episode_number FROM media WHERE id=?",new String[]{id})){
   if(!c.moveToFirst())throw new IOException("Título não encontrado no banco local");
   request(catalogEndpoint(ctx,catalogPath(c.getString(2)),id,token),"PUT",item(c).toString());
  }
 }
 /** Lists direct child keys without downloading the full catalog. */
 public static java.util.ArrayList<String> listFolders(Context ctx,String path,String token)throws Exception{
  validatePath(path);
  String node=path.isEmpty()?"/":"/"+encodedPath(path);
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
 /** Delete large nodes recursively when Firebase rejects a single write. */
 private static boolean oversized(IOException e){
  String m=String.valueOf(e.getMessage()).toLowerCase(java.util.Locale.ROOT);
  return m.contains("maximum size")||m.contains("too large")||m.contains("exceeds the maximum");
 }
 private static String encodedPath(String path)throws Exception{
  StringBuilder out=new StringBuilder();
  for(String segment:path.split("/")){
   if(out.length()>0)out.append('/');
   out.append(URLEncoder.encode(segment,"UTF-8").replace("+","%20"));
  }
  return out.toString();
 }
 private static void deleteNode(Context ctx,String path,String token,LocalCatalog.Progress progress,int[] removed,int depth)throws Exception{
  if(depth>32)throw new IOException("Pasta muito profunda: "+path);
  validatePath(path);
  String address=base(ctx)+"/"+encodedPath(path)+".json?auth="+URLEncoder.encode(token,"UTF-8");
  try{
   request(address,"DELETE",null);removed[0]++;progress.update(removed[0]);return;
  }catch(IOException ex){if(!oversized(ex))throw ex;}
  // A node can be too large even if it has a single child. Descend to that child.
  java.util.ArrayList<String> children=listFolders(ctx,path,token);
  if(children.isEmpty())throw new IOException("Nó grande demais para apagar diretamente: "+path);
  for(String child:children)deleteNode(ctx,path+"/"+child,token,progress,removed,depth+1);
  request(address,"DELETE",null);
 }
 /** A batch contains up to 200 direct children, represented by null in one PATCH. */
 private static final int DELETE_BATCH=200;
 public interface DeleteProgress {void update(int done,int total,String folder);}
 private static void deleteChildren(Context ctx,String path,String token,ArrayList<String> keys,DeleteProgress progress,int[] done,int total)throws Exception{
  String url=base(ctx)+"/"+encodedPath(path)+".json?auth="+URLEncoder.encode(token,"UTF-8");
  for(int start=0;start<keys.size();){
   int end=Math.min(start+DELETE_BATCH,keys.size());
   // Build a PATCH that removes 200 child records in one HTTP request.
   JSONObject patch=new JSONObject();
   for(int j=start;j<end;j++)patch.put(keys.get(j),JSONObject.NULL);
   try{
    request(url,"PATCH",patch.toString());
    done[0]+=end-start;progress.update(done[0],total,path);
   }catch(IOException ex){
    if(!oversized(ex))throw ex;
    // Large children can exceed RTDB's write limit even with a small PATCH.
    // Recurse only for the oversized batch; don't repeat successful batches.
    for(int j=start;j<end;j++){
     String child=path+"/"+keys.get(j);
     deleteNode(ctx,child,token,n->{},new int[]{0},0);
     done[0]++;progress.update(done[0],total,path);
    }
   }
   start=end;
  }
 }
 public static int deleteFolderBatched(Context ctx,String path,String token,LocalCatalog.Progress progress)throws Exception{
  return deleteFolderBatched(ctx,path,token,(done,total,folder)->progress.update(done));
 }
 public static int deleteFolderBatched(Context ctx,String path,String token,DeleteProgress progress)throws Exception{
  validatePath(path);
  if(path.isEmpty()||path.equals("catalogo_unico")||path.equals("catalogo_unico/meta")||path.equals("__meta")||path.equals("admins")||path.equals("config"))
   throw new IOException("Pasta protegida; selecione uma subpasta");
  ArrayList<String> keys=listFolders(ctx,path,token);int[] done={0};
  progress.update(0,keys.size(),path);
  if(keys.isEmpty()){
   // Empty nodes need no child-by-child requests.
   request(base(ctx)+"/"+encodedPath(path)+".json?auth="+URLEncoder.encode(token,"UTF-8"),"DELETE",null);
   return 0;
  }
  deleteChildren(ctx,path,token,keys,progress,done,keys.size());
  return done[0];
 }
 public static void deleteAll(Context ctx,String token)throws Exception{
  deleteAll(ctx,token,(done,total,folder)->{});
 }
 public static int deleteAll(Context ctx,String token,DeleteProgress progress)throws Exception{
  String[] paths={"catalogo_1_series/items","catalogo_2_filmes/items","catalogo_3_iptv/items","catalogo_unico/items"};
  ArrayList<ArrayList<String>> folders=new ArrayList<>();int total=0;
  for(String path:paths){ArrayList<String> keys=listFolders(ctx,path,token);folders.add(keys);total+=keys.size();}
  final int expected=total;int[] done={0};progress.update(0,expected,"Preparando exclusão total");
  for(int i=0;i<paths.length;i++){
   String path=paths[i];ArrayList<String> keys=folders.get(i);
   deleteChildren(ctx,path,token,keys,progress,done,expected);
  }
  markChanged(ctx,token);
  return done[0];
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
  String response;
  try{response=request(endpoint(ctx,"meta/apk_update",null),"GET",null);}
  catch(IOException publicError){
   com.google.firebase.auth.FirebaseUser user=com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
   if(user==null)throw publicError;
   com.google.firebase.auth.GetTokenResult token=com.google.android.gms.tasks.Tasks.await(user.getIdToken(false),15,java.util.concurrent.TimeUnit.SECONDS);
   response=request(endpoint(ctx,"meta/apk_update",token.getToken()),"GET",null);
  }
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
  int count=0;
  for(String folder:new String[]{"catalogo_1_series","catalogo_2_filmes","catalogo_3_iptv"})
   count=downloadFolder(ctx,db,folder,count,progress);
  // Compatibility for installations that have not yet migrated.
  if(count==0)count=downloadFolder(ctx,db,"catalogo_unico",count,progress);
  return count;
 }
 /** Download only one of the three independent RTDB catalogs. */
 public static int downloadCategory(Context ctx,LocalCatalog db,String folder,LocalCatalog.Progress progress)throws Exception{
  if(!folder.equals("catalogo_1_series")&&!folder.equals("catalogo_2_filmes")&&!folder.equals("catalogo_3_iptv"))throw new IOException("Catálogo inválido");
  return downloadFolder(ctx,db,folder,0,progress);
 }
 private static int downloadFolder(Context ctx,LocalCatalog db,String folder,int count,LocalCatalog.Progress progress)throws Exception{
  String baseUrl=base(ctx)+"/"+folder+"/items.json?orderBy=%22%24key%22&limitToFirst=101";
  String last=null;
  for(int pages=0;pages<5000;pages++){
   String u=baseUrl+(last==null?"":"&startAt="+URLEncoder.encode(JSONObject.quote(last),"UTF-8"));
   String response=request(u,"GET",null);
   if(response.equals("null")||response.trim().isEmpty())break;
   JSONObject items=new JSONObject(response);ArrayList<String> keys=new ArrayList<>();Iterator<String> it=items.keys();while(it.hasNext())keys.add(it.next());Collections.sort(keys);
   if(keys.isEmpty())break;
   SQLiteDatabase sql=db.getWritableDatabase();sql.beginTransaction();
   try{
    for(String key:keys){if(last!=null&&key.equals(last))continue;JSONObject o=items.optJSONObject(key);if(o==null)continue;
     String id=o.optString("id",key);String incomingUrl=o.optString("url","");
     if(incomingUrl.trim().isEmpty())try(Cursor old=sql.rawQuery("SELECT url FROM media WHERE id=?",new String[]{id})){
      if(old.moveToFirst()&&!old.isNull(0))incomingUrl=old.getString(0);
     }
     db.saveXtream(sql,id,o.optString("nome"),o.optString("categoria"),o.optString("capa"),incomingUrl,o.optString("sinopse"),o.optString("grupo"),o.optString("origem","Firebase"),o.optString("series_id"),o.optString("episode_id"),o.optString("parent_series_id"),o.optInt("season_number"),o.optInt("episode_number"));count++;
    }
    sql.setTransactionSuccessful();
   }finally{sql.endTransaction();}
   String next=keys.get(keys.size()-1);if(next.equals(last))break;last=next;progress.update(count);
   if(keys.size()<101)break;
  }
  return count;
 }
}
