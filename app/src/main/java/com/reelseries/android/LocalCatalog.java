package com.reelseries.android;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.JsonReader;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;

public class LocalCatalog extends SQLiteOpenHelper {
 public LocalCatalog(Context c){super(c,"reel_local_v10.db",null,1);}
 @Override public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE media (id TEXT PRIMARY KEY,nome TEXT NOT NULL,categoria TEXT,capa TEXT,url TEXT,sinopse TEXT,grupo TEXT,origem TEXT)");db.execSQL("CREATE INDEX idx_media_nome ON media(nome)");db.execSQL("CREATE INDEX idx_media_cat ON media(categoria)");}
 @Override public void onUpgrade(SQLiteDatabase db,int a,int b){}
 public static String hash(String s){try{byte[] b=MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8"));StringBuilder x=new StringBuilder();for(int i=0;i<16;i++)x.append(String.format(Locale.US,"%02x",b[i]&255));return x.toString();}catch(Exception e){return Integer.toHexString(s.hashCode());}}
 public static String first(JSONObject o,String... keys){for(String k:keys){String v=o.optString(k,"");if(!v.isEmpty()&&!"null".equalsIgnoreCase(v))return v;}return "";}
 public int importJson(InputStream stream,Progress progress)throws Exception {JsonReader reader=new JsonReader(new InputStreamReader(stream,"UTF-8"));reader.setLenient(true);int count=0;SQLiteDatabase db=getWritableDatabase();boolean transaction=false;try{
 android.util.JsonToken t=reader.peek();if(t==android.util.JsonToken.BEGIN_OBJECT){reader.beginObject();boolean found=false;while(reader.hasNext()){String k=reader.nextName();if(k.equals("catalogo")||k.equals("items")||k.equals("filmes")||k.equals("data")||k.equals("registros")){if(reader.peek()==android.util.JsonToken.BEGIN_ARRAY){found=true;break;}else reader.skipValue();}else reader.skipValue();}if(!found)throw new IOException("JSON sem lista catalogo/items/filmes/data/registros");}else if(t!=android.util.JsonToken.BEGIN_ARRAY)throw new IOException("Formato JSON não reconhecido");
 reader.beginArray();db.beginTransaction();transaction=true;while(reader.hasNext()){JSONObject o=readObject(reader);if(o==null)continue;String name=first(o,"nome","titulo","title","name","tvg-name");if(name.isEmpty())continue;String url=first(o,"video","url","link","stream");String id=first(o,"id","_id","key");if(id.isEmpty())id="json_"+hash(name+"|"+url+"|"+first(o,"episodio","episode"));save(db,id,name,first(o,"categoria","category","tipo","group-title"),first(o,"capa","poster","imagem","image","logo","tvg-logo"),url,first(o,"sinopse","descricao","description","overview"),"","JSON");count++;if(count%100==0){db.setTransactionSuccessful();db.endTransaction();transaction=false;progress.update(count);db.beginTransaction();transaction=true;}}
 db.setTransactionSuccessful();db.endTransaction();transaction=false;progress.update(count);return count;
 }finally{if(transaction)db.endTransaction();reader.close();}}
 private JSONObject readObject(JsonReader r)throws IOException{if(r.peek()!=android.util.JsonToken.BEGIN_OBJECT){r.skipValue();return null;}JSONObject o=new JSONObject();r.beginObject();while(r.hasNext()){String k=r.nextName();android.util.JsonToken t=r.peek();try{if(t==android.util.JsonToken.STRING||t==android.util.JsonToken.NUMBER)o.put(k,r.nextString());else if(t==android.util.JsonToken.BOOLEAN)o.put(k,r.nextBoolean());else if(t==android.util.JsonToken.NULL){r.nextNull();}else r.skipValue();}catch(org.json.JSONException e){throw new IOException(e);}}r.endObject();return o;}
 public int importM3u(InputStream input,String origin,Progress progress)throws Exception{BufferedReader r=new BufferedReader(new InputStreamReader(input,"UTF-8"));SQLiteDatabase db=getWritableDatabase();int count=0;boolean transaction=false;String line,name="",group="",logo="",id="";try{db.beginTransaction();transaction=true;while((line=r.readLine())!=null){line=line.trim();if(line.startsWith("#EXTINF:")){name=line.contains(",")?line.substring(line.lastIndexOf(',')+1).trim():"";group=attribute(line,"group-title");logo=attribute(line,"tvg-logo");id=attribute(line,"tvg-id");}else if(!line.isEmpty()&&!line.startsWith("#")&&(line.startsWith("http://")||line.startsWith("https://"))){if(name.isEmpty())name="Canal "+(count+1);String key="m3u_"+hash(origin+"|"+(id.isEmpty()?name:id)+"|"+line);save(db,key,name,group.isEmpty()?"IPTV":group,logo,line,"",group,origin);count++;name="";group="";logo="";id="";if(count%100==0){db.setTransactionSuccessful();db.endTransaction();transaction=false;progress.update(count);db.beginTransaction();transaction=true;}}}db.setTransactionSuccessful();db.endTransaction();transaction=false;progress.update(count);return count;}finally{if(transaction)db.endTransaction();r.close();}}
 private static String attribute(String line,String key){String token=key+"=\"";int a=line.indexOf(token);if(a<0)return "";a+=token.length();int b=line.indexOf('"',a);return b<0?"":line.substring(a,b);}
 public void save(SQLiteDatabase db,String id,String name,String category,String cover,String url,String synopsis,String group,String origin){android.content.ContentValues v=new android.content.ContentValues();v.put("id",id);v.put("nome",name);v.put("categoria",category);v.put("capa",cover);v.put("url",url);v.put("sinopse",synopsis);v.put("grupo",group);v.put("origem",origin);db.insertWithOnConflict("media",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
 public void addStream(String name,String url){save(getWritableDatabase(),"stream_"+hash(url),name,"IPTV ao vivo","",url,"","","M3U8");}
 public ArrayList<JSONObject> search(String q,String cat,int limit){ArrayList<JSONObject> list=new ArrayList<>();String sql="SELECT id,nome,categoria,capa,url,sinopse FROM media WHERE nome LIKE ?"+(cat.equals("Todas")?"":" AND categoria=?")+" ORDER BY nome LIMIT "+Math.max(1,Math.min(300,limit));String[] args=cat.equals("Todas")?new String[]{"%"+q+"%"}:new String[]{"%"+q+"%",cat};try(Cursor c=getReadableDatabase().rawQuery(sql,args)){while(c.moveToNext()){JSONObject o=new JSONObject();try{for(int i=0;i<6;i++)o.put(c.getColumnName(i),c.getString(i));list.add(o);}catch(Exception ignored){}}}return list;}
 public ArrayList<String> categories(){ArrayList<String> a=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT DISTINCT categoria FROM media WHERE categoria IS NOT NULL AND categoria != '' ORDER BY categoria",null)){while(c.moveToNext())a.add(c.getString(0));}return a;}
 public int count(){try(Cursor c=getReadableDatabase().rawQuery("SELECT count(*) FROM media",null)){return c.moveToFirst()?c.getInt(0):0;}}
 public interface Progress{void update(int count);}
}
