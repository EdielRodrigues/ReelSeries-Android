package com.reelseries.android;

import android.content.Context;
import android.database.Cursor;
import android.util.Base64;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Only the administrator calls Xtream. Clients read sanitized EPG from RTDB. */
public final class EpgGuide {
 private static final String ROOT="https://smart-tv-c175a-default-rtdb.firebaseio.com/programacao_tv/";
 private EpgGuide(){}
 public interface Progress {void update(int count);}
 private static String read(String url,int timeout)throws Exception{
  HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
  c.setConnectTimeout(timeout);c.setReadTimeout(timeout);c.setRequestProperty("Accept","application/json");
  try{
   int code=c.getResponseCode();if(code<200||code>=300)throw new IOException("HTTP "+code);
   ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[4096];int n;
   try(InputStream in=c.getInputStream()){while((n=in.read(buf))!=-1){out.write(buf,0,n);if(out.size()>1024*1024)throw new IOException("EPG grande demais");}}
   return out.toString("UTF-8");
  }finally{c.disconnect();}
 }
 private static void put(String key,JSONObject data,String token)throws Exception{
  String endpoint=ROOT+key+".json?auth="+URLEncoder.encode(token,"UTF-8");
  HttpURLConnection c=(HttpURLConnection)new URL(endpoint).openConnection();
  c.setConnectTimeout(10000);c.setReadTimeout(10000);c.setRequestMethod("PUT");c.setDoOutput(true);
  c.setRequestProperty("Content-Type","application/json; charset=utf-8");
  try(OutputStream out=c.getOutputStream()){out.write(data.toString().getBytes("UTF-8"));}
  try{if(c.getResponseCode()<200||c.getResponseCode()>=300)throw new IOException("Firebase HTTP "+c.getResponseCode());}
  finally{c.disconnect();}
 }
 private static String decode(String input){
  if(input==null)return "";
  try{return new String(Base64.decode(input,Base64.DEFAULT),"UTF-8");}
  catch(Exception e){return input;}
 }
 private static long stamp(JSONObject o,String key){
  try{return Long.parseLong(o.optString(key,"0"))*1000L;}catch(Exception e){return 0;}
 }
 private static JSONObject guide(String json)throws Exception{
  JSONArray list=new JSONObject(json).optJSONArray("epg_listings");if(list==null)return null;
  long now=System.currentTimeMillis();JSONObject current=null,next=null;
  for(int i=0;i<list.length();i++){
   JSONObject e=list.optJSONObject(i);if(e==null)continue;
   long start=stamp(e,"start_timestamp"),end=stamp(e,"stop_timestamp");
   if(start<=0||end<=start)continue;
   if(start<=now&&end>now){if(current==null||start>current.optLong("inicio"))current=entry(e,start,end);}
   else if(start>now&&(next==null||start<next.optLong("inicio")))next=entry(e,start,end);
  }
  if(current==null&&next==null)return null;
  JSONObject result=new JSONObject();if(current!=null)result.put("agora",current);
  if(next!=null)result.put("seguir",next);
  result.put("atualizado",now);return result;
 }
 private static JSONObject entry(JSONObject e,long start,long end)throws Exception{
  JSONObject out=new JSONObject();out.put("titulo",decode(e.optString("title","")));
  out.put("inicio",start);out.put("fim",end);return out;
 }
 public static int publish(Context context,LocalCatalog catalog,String base,String user,String password,String token,Progress progress)throws Exception{
  String origin="xtream_"+LocalCatalog.hash(base+"|"+user);
  String api=base+"/player_api.php?username="+URLEncoder.encode(user,"UTF-8")+"&password="+URLEncoder.encode(password,"UTF-8")+"&action=get_short_epg&limit=12&stream_id=";
  int queried=0,published=0;
  try(Cursor c=catalog.getReadableDatabase().rawQuery("SELECT id,url FROM media WHERE origem=? AND url LIKE ?",new String[]{origin,"%/live/%"})){
   while(c.moveToNext()){
    String key=c.getString(0),url=c.getString(1);if(key==null||!key.matches("[a-zA-Z0-9_-]+"))continue;
    String stream=url.replaceFirst("\\?.*$","").replaceFirst("\\.[^.\\/]+$","");
    stream=stream.substring(stream.lastIndexOf('/')+1);
    if(!stream.matches("[0-9]+"))continue;
    queried++;
    try{JSONObject data=guide(read(api+URLEncoder.encode(stream,"UTF-8"),7000));
     if(data!=null){put(key,data,token);published++;}
    }catch(Exception ignored){/* Unsupported EPG on one channel must not interrupt other channels. */}
    if(queried%25==0)progress.update(queried);
   }
  }
  progress.update(queried);return published;
 }
 private static String format(JSONObject o){
  if(o==null)return "Programação não disponível";
  String title=o.optString("titulo","Programa sem título");
  SimpleDateFormat time=new SimpleDateFormat("HH:mm",Locale.getDefault());
  return time.format(new Date(o.optLong("inicio")))+"–"+time.format(new Date(o.optLong("fim")))+"  "+title;
 }
 public static void load(android.app.Activity activity,String key,TextView view){
  if(!key.matches("[a-zA-Z0-9_-]+")){view.setText("Programação não disponível");return;}
  new Thread(()->{
   String output="Programação não disponível";
   try{
    String json=read(ROOT+key+".json",9000);
    if(!"null".equals(json.trim())){
     JSONObject data=new JSONObject(json);
     // Do not present a stale guide as the current broadcast.
     if(System.currentTimeMillis()-data.optLong("atualizado")<6*60*60*1000L){
      JSONObject now=data.optJSONObject("agora"),next=data.optJSONObject("seguir");
      if(now!=null&&now.optLong("fim")<=System.currentTimeMillis())now=null;
      if(next!=null&&next.optLong("fim")<=System.currentTimeMillis())next=null;
      if(now!=null||next!=null)output="AGORA\n"+format(now)+"\n\nA SEGUIR\n"+format(next);
     }
    }
   }catch(Exception ignored){}
   final String result=output;
   activity.runOnUiThread(()->{if(!activity.isFinishing()&&!activity.isDestroyed())view.setText(result);});
  },"epg-client").start();
 }
}
