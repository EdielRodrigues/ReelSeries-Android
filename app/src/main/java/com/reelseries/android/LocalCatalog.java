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
 public LocalCatalog(Context c){super(c,"reel_local_v10.db",null,2);}
 @Override public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE media (id TEXT PRIMARY KEY,nome TEXT NOT NULL,categoria TEXT,capa TEXT,url TEXT,sinopse TEXT,grupo TEXT,origem TEXT,series_id TEXT,episode_id TEXT,parent_series_id TEXT,season_number INTEGER,episode_number INTEGER)");db.execSQL("CREATE INDEX idx_media_nome ON media(nome)");db.execSQL("CREATE INDEX idx_media_cat ON media(categoria)");}
 @Override public void onUpgrade(SQLiteDatabase db,int a,int b){
  if(a<2){for(String col:new String[]{"series_id TEXT","episode_id TEXT","parent_series_id TEXT","season_number INTEGER","episode_number INTEGER"})db.execSQL("ALTER TABLE media ADD COLUMN "+col);}
 }
 public static String hash(String s){try{byte[] b=MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8"));StringBuilder x=new StringBuilder();for(int i=0;i<16;i++)x.append(String.format(Locale.US,"%02x",b[i]&255));return x.toString();}catch(Exception e){return Integer.toHexString(s.hashCode());}}
 public static String first(JSONObject o,String... keys){for(String k:keys){String v=o.optString(k,"");if(!v.isEmpty()&&!"null".equalsIgnoreCase(v))return v;}return "";}
 /** Accepts legacy arrays and the keyed items object exported for each RTDB catalog. */
 public int importJson(InputStream stream,Progress progress)throws Exception{
  JsonReader reader=new JsonReader(new InputStreamReader(stream,"UTF-8"));
  SQLiteDatabase db=getWritableDatabase();int count=0;boolean transaction=false;
  try{
   android.util.JsonToken t=reader.peek();
   if(t==android.util.JsonToken.BEGIN_OBJECT){
    reader.beginObject();boolean found=false;
    while(reader.hasNext()){
     String k=reader.nextName();
     if(k.equals("catalogo")||k.equals("items")||k.equals("filmes")||k.equals("data")||k.equals("registros")){
      android.util.JsonToken nested=reader.peek();
      if(nested==android.util.JsonToken.BEGIN_ARRAY||nested==android.util.JsonToken.BEGIN_OBJECT){found=true;break;}
     }
     reader.skipValue();
    }
    if(!found)throw new IOException("JSON sem catálogo: esperado items ou lista de registros");
   }else if(t!=android.util.JsonToken.BEGIN_ARRAY)throw new IOException("Formato JSON não reconhecido");
   boolean keyed=reader.peek()==android.util.JsonToken.BEGIN_OBJECT;
   if(keyed)reader.beginObject();else reader.beginArray();
   db.beginTransaction();transaction=true;
   while(reader.hasNext()){
    String key=keyed?reader.nextName():"";
    JSONObject o=readObject(reader);if(o==null)continue;
    String name=first(o,"nome","titulo","title","name","tvg-name");if(name.isEmpty())continue;
    String url=first(o,"video","url","link","stream");
    String id=first(o,"id","_id","key");if(id.isEmpty())id=key.isEmpty()?"json_"+hash(name+"|"+url):key;
    saveXtream(db,id,name,first(o,"categoria","category","tipo","group-title"),first(o,"capa","poster","imagem","image","logo","tvg-logo"),url,first(o,"sinopse","descricao","description","overview"),first(o,"grupo"),first(o,"origem","origin").isEmpty()?"JSON":first(o,"origem","origin"),first(o,"series_id"),first(o,"episode_id"),first(o,"parent_series_id"),o.optInt("season_number"),o.optInt("episode_number"));
    count++;
    if(count%100==0){db.setTransactionSuccessful();db.endTransaction();transaction=false;progress.update(count);db.beginTransaction();transaction=true;}
   }
   if(keyed)reader.endObject();else reader.endArray();
   db.setTransactionSuccessful();db.endTransaction();transaction=false;progress.update(count);return count;
  }finally{if(transaction)db.endTransaction();reader.close();}
 }
 private JSONObject readObject(JsonReader r)throws IOException{if(r.peek()!=android.util.JsonToken.BEGIN_OBJECT){r.skipValue();return null;}JSONObject o=new JSONObject();r.beginObject();while(r.hasNext()){String k=r.nextName();android.util.JsonToken t=r.peek();try{if(t==android.util.JsonToken.STRING||t==android.util.JsonToken.NUMBER)o.put(k,r.nextString());else if(t==android.util.JsonToken.BOOLEAN)o.put(k,r.nextBoolean());else if(t==android.util.JsonToken.NULL){r.nextNull();}else r.skipValue();}catch(org.json.JSONException e){throw new IOException(e);}}r.endObject();return o;}
 public int importM3u(InputStream input,String origin,Progress progress)throws Exception{BufferedReader r=new BufferedReader(new InputStreamReader(input,"UTF-8"));SQLiteDatabase db=getWritableDatabase();int count=0;boolean transaction=false;String line,name="",group="",logo="",id="";try{db.beginTransaction();transaction=true;while((line=r.readLine())!=null){line=line.trim();if(line.startsWith("#EXTINF:")){name=line.contains(",")?line.substring(line.lastIndexOf(',')+1).trim():"";group=attribute(line,"group-title");logo=attribute(line,"tvg-logo");id=attribute(line,"tvg-id");}else if(!line.isEmpty()&&!line.startsWith("#")&&(line.startsWith("http://")||line.startsWith("https://"))){if(name.isEmpty())name="Canal "+(count+1);String key="m3u_"+hash(origin+"|"+(id.isEmpty()?name:id)+"|"+line);save(db,key,name,group.isEmpty()?"IPTV":group,logo,line,"",group,origin);count++;name="";group="";logo="";id="";if(count%100==0){db.setTransactionSuccessful();db.endTransaction();transaction=false;progress.update(count);db.beginTransaction();transaction=true;}}}db.setTransactionSuccessful();db.endTransaction();transaction=false;progress.update(count);return count;}finally{if(transaction)db.endTransaction();r.close();}}
 private static String attribute(String line,String key){String token=key+"=\"";int a=line.indexOf(token);if(a<0)return "";a+=token.length();int b=line.indexOf('"',a);return b<0?"":line.substring(a,b);}
 public void save(SQLiteDatabase db,String id,String name,String category,String cover,String url,String synopsis,String group,String origin){android.content.ContentValues v=new android.content.ContentValues();v.put("id",id);v.put("nome",name);v.put("categoria",category);v.put("capa",cover);v.put("url",url);v.put("sinopse",synopsis);v.put("grupo",group);v.put("origem",origin);// Preserve metadata when refreshing an existing record through legacy editors.
  if(db.update("media",v,"id=?",new String[]{id})==0)db.insertWithOnConflict("media",null,v,SQLiteDatabase.CONFLICT_IGNORE);}
 public void saveXtream(SQLiteDatabase db,String id,String name,String category,String cover,String url,String synopsis,String group,String origin,String seriesId,String episodeId,String parentId,int season,int episode){
  save(db,id,name,category,cover,url,synopsis,group,origin);
  android.content.ContentValues extra=new android.content.ContentValues();
  if(seriesId!=null&&!seriesId.isEmpty())extra.put("series_id",seriesId);
  if(episodeId!=null&&!episodeId.isEmpty())extra.put("episode_id",episodeId);
  if(parentId!=null&&!parentId.isEmpty())extra.put("parent_series_id",parentId);
  if(season>0)extra.put("season_number",season);
  if(episode>0)extra.put("episode_number",episode);
  if(extra.size()>0)db.update("media",extra,"id=?",new String[]{id});
 }
 public void addStream(String name,String url){save(getWritableDatabase(),"stream_"+hash(url),name,"IPTV ao vivo","",url,"","","M3U8");}
 public ArrayList<JSONObject> search(String q,String cat,int limit){ArrayList<JSONObject> list=new ArrayList<>();String sql="SELECT id,nome,categoria,capa,url,sinopse FROM media WHERE nome LIKE ?"+(cat.equals("Todas")?"":" AND categoria=?")+" ORDER BY nome LIMIT "+Math.max(1,Math.min(300,limit));String[] args=cat.equals("Todas")?new String[]{"%"+q+"%"}:new String[]{"%"+q+"%",cat};try(Cursor c=getReadableDatabase().rawQuery(sql,args)){while(c.moveToNext()){JSONObject o=new JSONObject();try{for(int i=0;i<6;i++)o.put(c.getColumnName(i),c.getString(i));list.add(o);}catch(Exception ignored){}}}return list;}
 private static boolean isChannel(String cat,String group,String name){
  String c=cat==null?"":cat.toLowerCase(java.util.Locale.ROOT);
  String g=group==null?"":group.toLowerCase(java.util.Locale.ROOT);
  String n=name==null?"":name.toLowerCase(java.util.Locale.ROOT);
  return c.contains("iptv")||c.contains("canal")||c.contains("tv ao vivo")||c.equals("tv")||c.contains("televis")||g.contains("iptv")||n.matches(".*\\b(fhd|hd|sd)\\b.*")&&(c.contains("tv")||c.contains("live"));
 }
 private static String channelBrand(String name,String group){
  String n=name==null?"":name.trim();
  String lower=n.toLowerCase(java.util.Locale.ROOT);
  String[] brands={"Globo","SBT","Record","Band","TNT","SporTV","Premiere","Telecine","HBO","ESPN","Discovery","Cartoon Network","Nickelodeon","Universal","Warner","CNN","Fox","Multishow","GNT","AXN","Sony","Paramount","Disney","History","Animal Planet","National Geographic","RedeTV","TV Cultura"};
  for(String b:brands)if(lower.contains(b.toLowerCase(java.util.Locale.ROOT)))return b;
  String source=n.isEmpty()?(group==null?"":group.trim()):n;
  source=source.replaceFirst("(?i)^(br|brasil|tv|canal)\\s*[-:|]\\s*","").trim();
  String[] parts=source.split("[\\s|:–-]+",2);
  return parts.length==0||parts[0].isEmpty()?"Outros":parts[0];
 }
 public java.util.LinkedHashMap<String,Integer> channelGroups(){
  java.util.TreeMap<String,Integer> counts=new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
  try(Cursor c=getReadableDatabase().rawQuery("SELECT nome,categoria,grupo FROM media",null)){
   while(c.moveToNext()){
    String name=c.getString(0),cat=c.getString(1),group=c.getString(2);
    if(!isChannel(cat,group,name))continue;
    String brand=channelBrand(name,group);counts.put(brand,counts.containsKey(brand)?counts.get(brand)+1:1);
   }
  }
  java.util.LinkedHashMap<String,Integer> result=new java.util.LinkedHashMap<>();result.putAll(counts);return result;
 }
 // Pastas abrangem TODO o catálogo salvo: filmes, séries e canais IPTV.
 // Cada registro entra em uma pasta; categorias não IPTV mantêm seu nome real.
 private static String folderFor(String name,String category,String group){
  if(isChannel(category,group,name))return "TV • "+channelBrand(name,group);
  String cat=category==null?"":category.trim();
  if(cat.isEmpty())return "Sem categoria";
  return cat;
 }
 public java.util.LinkedHashMap<String,Integer> catalogFolders(){
  java.util.TreeMap<String,Integer> counts=new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
  try(Cursor c=getReadableDatabase().rawQuery("SELECT nome,categoria,grupo FROM media",null)){
   while(c.moveToNext()){
    String folder=folderFor(c.getString(0),c.getString(1),c.getString(2));
    Integer current=counts.get(folder);counts.put(folder,current==null?1:current+1);
   }
  }
  return new java.util.LinkedHashMap<>(counts);
 }
 public ArrayList<JSONObject> catalogFolderFeed(String folder,String query,int offset,int limit){
  ArrayList<JSONObject> result=new ArrayList<>();int found=0;
  String q=query==null?"":query.toLowerCase(java.util.Locale.ROOT);
  try(Cursor c=getReadableDatabase().rawQuery("SELECT id,nome,categoria,capa,url,sinopse,grupo FROM media ORDER BY nome COLLATE NOCASE",null)){
   while(c.moveToNext()){
    String name=c.getString(1);
    if(!folderFor(name,c.getString(2),c.getString(6)).equalsIgnoreCase(folder) ||
       !name.toLowerCase(java.util.Locale.ROOT).contains(q))continue;
    if(found++<offset)continue;
    if(result.size()>=limit)break;
    JSONObject o=new JSONObject();try{for(int i=0;i<6;i++)o.put(c.getColumnName(i),c.getString(i));result.add(o);}catch(Exception ignored){}
   }
  }
  return result;
 }
 public ArrayList<JSONObject> channelGroupFeed(String brand,String query,int offset,int limit){
  ArrayList<JSONObject> out=new ArrayList<>();int found=0;
  try(Cursor c=getReadableDatabase().rawQuery("SELECT id,nome,categoria,capa,url,sinopse,grupo FROM media WHERE LOWER(nome) LIKE ? ORDER BY nome COLLATE NOCASE",new String[]{"%"+brand.toLowerCase(java.util.Locale.ROOT)+"%"})){
   while(c.moveToNext()){
    String name=c.getString(1);
    if(!isChannel(c.getString(2),c.getString(6),name)||!channelBrand(name,c.getString(6)).equalsIgnoreCase(brand)||!name.toLowerCase(java.util.Locale.ROOT).contains(query.toLowerCase(java.util.Locale.ROOT)))continue;
    if(found++<offset)continue;
    if(out.size()>=limit)break;
    JSONObject o=new JSONObject();try{for(int i=0;i<6;i++)o.put(c.getColumnName(i),c.getString(i));out.add(o);}catch(Exception ignored){}
   }
  }
  return out;
 }
 public ArrayList<JSONObject> feed(String query,String mode,String category,int offset,int limit){
  ArrayList<JSONObject> result=new ArrayList<>();String c="LOWER(COALESCE(categoria,''))", n="LOWER(COALESCE(nome,''))";
  String tv="("+c+" LIKE '%iptv%' OR "+c+" LIKE '%canal%' OR "+c+" LIKE '%tv ao vivo%' OR "+c+"='tv' OR "+c+" LIKE '%televis%')";
  String series="("+c+" LIKE '%serie%' OR "+c+" LIKE '%série%' OR "+c+" LIKE '%novela%' OR "+c+" LIKE '%episod%' OR "+n+" LIKE '%episodio%' OR "+n+" LIKE '%episódio%')";
  String where=" WHERE "+n+" LIKE ?";ArrayList<String> args=new ArrayList<>();args.add("%"+query.toLowerCase(Locale.ROOT)+"%");
  if(!"Todas".equals(category)){where+=" AND categoria=?";args.add(category);}
  if("TV / IPTV".equals(mode))where+=" AND "+tv;
  else if("Séries".equals(mode))where+=" AND "+series+" AND NOT "+tv;
  else if("Filmes".equals(mode))where+=" AND NOT "+series+" AND NOT "+tv+" AND ("+c+" LIKE '%filme%' OR "+c+" LIKE '%movie%' OR "+c+" LIKE '%cinema%')";
  String sql="SELECT id,nome,categoria,capa,url,sinopse FROM media"+where+" ORDER BY nome COLLATE NOCASE LIMIT ? OFFSET ?";
  args.add(String.valueOf(Math.max(1,Math.min(limit,500))));args.add(String.valueOf(Math.max(0,offset)));
  try(Cursor cur=getReadableDatabase().rawQuery(sql,args.toArray(new String[0]))){while(cur.moveToNext()){JSONObject o=new JSONObject();try{for(int i=0;i<6;i++)o.put(cur.getColumnName(i),cur.getString(i));result.add(o);}catch(Exception ignored){}}}return result;
 }
 public ArrayList<JSONObject> episodesForParent(String parentId){
  ArrayList<JSONObject> result=new ArrayList<>();
  if(parentId==null||!parentId.startsWith("xtream_"))return result;
  try(Cursor c=getReadableDatabase().rawQuery("SELECT id,nome,categoria,capa,url,sinopse,season_number,episode_number,episode_id,parent_series_id FROM media WHERE parent_series_id=? OR grupo=? ORDER BY season_number,episode_number,nome",new String[]{parentId,"xtream_parent:"+parentId})){
   while(c.moveToNext()){JSONObject o=new JSONObject();try{for(int i=0;i<6;i++)o.put(c.getColumnName(i),c.getString(i));result.add(o);}catch(Exception ignored){}}
  }
  return result;
 }
 public ArrayList<JSONObject> episodeCandidates(String seriesName){
  ArrayList<JSONObject> result=new ArrayList<>();
  if(seriesName==null||seriesName.trim().isEmpty())return result;
  String lookup=seriesName.trim().replaceAll("\\s*\\(\\d{4}\\)\\s*$","").replaceAll("\\s+(?:19|20)\\d{2}\\s*$","").trim();
  String escaped=lookup.replace("\\","\\\\").replace("%","\\%").replace("_","\\_");
  String sql="SELECT id,nome,categoria,capa,url,sinopse FROM media WHERE nome LIKE ? ESCAPE '\\' ORDER BY nome";
  try(Cursor c=getReadableDatabase().rawQuery(sql,new String[]{escaped+"%"})){
   while(c.moveToNext()){
    JSONObject o=new JSONObject();try{for(int i=0;i<6;i++)o.put(c.getColumnName(i),c.getString(i));result.add(o);}catch(Exception ignored){}
   }
  }
  return result;
 }
 public ArrayList<JSONObject> page(int offset,int limit){
  ArrayList<JSONObject> result=new ArrayList<>();
  try(Cursor c=getReadableDatabase().rawQuery("SELECT id,nome,categoria,capa,url,sinopse FROM media ORDER BY nome LIMIT ? OFFSET ?",new String[]{String.valueOf(limit),String.valueOf(offset)})){
   while(c.moveToNext()){
    JSONObject o=new JSONObject();
    try{for(int i=0;i<6;i++)o.put(c.getColumnName(i),c.getString(i));result.add(o);}catch(Exception ignored){}
   }
  }
  return result;
 }
 public ArrayList<String> categories(){ArrayList<String> a=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT DISTINCT categoria FROM media WHERE categoria IS NOT NULL AND categoria != '' ORDER BY categoria",null)){while(c.moveToNext())a.add(c.getString(0));}return a;}
 public int count(){try(Cursor c=getReadableDatabase().rawQuery("SELECT count(*) FROM media",null)){return c.moveToFirst()?c.getInt(0):0;}}
 public LinkedHashMap<String,Integer> statistics(){LinkedHashMap<String,Integer> m=new LinkedHashMap<>();String[][] checks={{"Total de títulos","SELECT COUNT(*) FROM media"},{"Filmes","SELECT COUNT(*) FROM media WHERE LOWER(categoria) LIKE '%filme%'"},{"Séries","SELECT COUNT(*) FROM media WHERE LOWER(categoria) LIKE '%série%' OR LOWER(categoria) LIKE '%serie%'"},{"IPTV / canais","SELECT COUNT(*) FROM media WHERE LOWER(categoria) LIKE '%iptv%' OR LOWER(categoria) LIKE '%canal%'"},{"Sem vídeo","SELECT COUNT(*) FROM media WHERE url IS NULL OR TRIM(url)=''"},{"Sem capa","SELECT COUNT(*) FROM media WHERE capa IS NULL OR TRIM(capa)=''"},{"Sem categoria","SELECT COUNT(*) FROM media WHERE categoria IS NULL OR TRIM(categoria)=''"},{"Sem sinopse","SELECT COUNT(*) FROM media WHERE sinopse IS NULL OR TRIM(sinopse)=''"},{"Importados por JSON","SELECT COUNT(*) FROM media WHERE origem='JSON'"}};SQLiteDatabase db=getReadableDatabase();for(String[] check:checks){try(Cursor c=db.rawQuery(check[1],null)){m.put(check[0],c.moveToFirst()?c.getInt(0):0);}}return m;}
 private String diagnosticWhere(String kind){switch(kind){case "sem_video":return "url IS NULL OR TRIM(url)=''";case "sem_capa":return "capa IS NULL OR TRIM(capa)=''";case "sem_categoria":return "categoria IS NULL OR TRIM(categoria)=''";default:return "1=0";}}
 public int diagnosticCount(String kind){try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM media WHERE "+diagnosticWhere(kind),null)){return c.moveToFirst()?c.getInt(0):0;}}
 public ArrayList<JSONObject> diagnostic(String kind,int offset,int limit){ArrayList<JSONObject> result=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT id,nome,categoria,capa,url,sinopse FROM media WHERE "+diagnosticWhere(kind)+" ORDER BY nome LIMIT ? OFFSET ?",new String[]{String.valueOf(limit),String.valueOf(offset)})){while(c.moveToNext()){JSONObject o=new JSONObject();try{for(int i=0;i<6;i++)o.put(c.getColumnName(i),c.getString(i));result.add(o);}catch(Exception ignored){}}}return result;}
 public int exportJson(OutputStream out,Progress progress)throws Exception{android.util.JsonWriter w=new android.util.JsonWriter(new OutputStreamWriter(out,"UTF-8"));w.beginObject();w.name("catalogo");w.beginArray();int count=0;try(Cursor c=getReadableDatabase().rawQuery("SELECT id,nome,categoria,capa,url,sinopse,grupo,origem FROM media ORDER BY id",null)){while(c.moveToNext()){w.beginObject();for(int i=0;i<c.getColumnCount();i++){w.name(c.getColumnName(i));String v=c.getString(i);if(v==null)w.nullValue();else w.value(v);}w.endObject();count++;if(count%250==0){w.flush();progress.update(count);}}}w.endArray();w.endObject();w.flush();progress.update(count);return count;}
 public interface Progress{void update(int count);}
}
