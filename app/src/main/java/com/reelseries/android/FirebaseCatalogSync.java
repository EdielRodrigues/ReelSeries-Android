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
  int id=ctx.getResources().getIdentifier("firebase_catalog_url","string",ctx.getPackageName());
  String url=id==0?"":ctx.getString(id).trim();
  if(!url.startsWith("https://"))throw new IOException("Configure firebase_catalog_url com a URL HTTPS do Realtime Database");
  while(url.endsWith("/"))url=url.substring(0,url.length()-1);
  return url;
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
 public static int upload(Context ctx,LocalCatalog db,String origin,String token,LocalCatalog.Progress progress)throws Exception{
  SQLiteDatabase sql=db.getReadableDatabase();int total=0;int offset=0;
  // SQLite keyset pagination: no full 66k catalog in memory.
  String last="";while(true){
   String query="SELECT id,nome,categoria,capa,url,sinopse,grupo,origem FROM media WHERE id>?"+(origin==null?"":" AND origem=?")+" ORDER BY id LIMIT 100";
   String[] args=origin==null?new String[]{last}:new String[]{last,origin};
   JSONObject patch=new JSONObject();int count=0;
   try(Cursor c=sql.rawQuery(query,args)){
    while(c.moveToNext()){String id=c.getString(0);patch.put(id,item(c));last=id;count++;}
   }
   if(count==0)break;
   request(endpoint(ctx,"items",token),"PATCH",patch.toString());total+=count;progress.update(total);
   if(count<100)break;
  }
  return total;
 }
 public static void uploadOne(Context ctx,LocalCatalog db,String id,String token)throws Exception{
  try(Cursor c=db.getReadableDatabase().rawQuery("SELECT id,nome,categoria,capa,url,sinopse,grupo,origem FROM media WHERE id=?",new String[]{id})){
   if(!c.moveToFirst())throw new IOException("Título não encontrado no banco local");
   request(endpoint(ctx,"items/"+URLEncoder.encode(id,"UTF-8"),token),"PUT",item(c).toString());
  }
 }
 public static void deleteOne(Context ctx,String id,String token)throws Exception{
  request(endpoint(ctx,"items/"+URLEncoder.encode(id,"UTF-8"),token),"DELETE",null);
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
