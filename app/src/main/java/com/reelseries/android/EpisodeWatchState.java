package com.reelseries.android;
import android.content.Context;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
/** Histórico individual de episódios concluídos, independente do ponto de retomada. */
public final class EpisodeWatchState {
 private EpisodeWatchState(){}
 public static String key(String url){
  if(url==null||url.isEmpty())return "";
  try{byte[] hash=MessageDigest.getInstance("SHA-256").digest(url.getBytes(StandardCharsets.UTF_8));StringBuilder b=new StringBuilder("w_");for(int i=0;i<16;i++)b.append(String.format(Locale.US,"%02x",hash[i]&255));return b.toString();}
  catch(Exception e){return "w_"+url.hashCode();}
 }
 /** Progresso do episódio em milissegundos, separado por perfil e URL. */
 public static void saveProgress(Context c,String url,long position,long duration){
  String k=key(url);if(k.isEmpty()||position<2000||duration<=0)return;
  if(position>=duration-15000 || position*100>=duration*95){setWatched(c,url,true);clearProgress(c,url);return;}
  UserMediaState.prefs(c,"reel_episode_progress").edit().putLong(k+"_pos",Math.max(0,position)).putLong(k+"_dur",duration).apply();
 }
 public static long position(Context c,String url){String k=key(url);return k.isEmpty()?0:UserMediaState.prefs(c,"reel_episode_progress").getLong(k+"_pos",0);}
 public static long duration(Context c,String url){String k=key(url);return k.isEmpty()?0:UserMediaState.prefs(c,"reel_episode_progress").getLong(k+"_dur",0);}
 public static int percent(Context c,String url){if(isWatched(c,url))return 100;long d=duration(c,url),p=position(c,url);return d<=0?0:(int)Math.max(0,Math.min(99,p*100/d));}
 public static void clearProgress(Context c,String url){String k=key(url);if(!k.isEmpty())UserMediaState.prefs(c,"reel_episode_progress").edit().remove(k+"_pos").remove(k+"_dur").apply();}
 public static boolean isWatched(Context c,String url){String k=key(url);return !k.isEmpty()&&UserMediaState.prefs(c,"reel_watched_episodes").getBoolean(k,false);}
 public static void setWatched(Context c,String url,boolean watched){String k=key(url);if(!k.isEmpty()){UserMediaState.prefs(c,"reel_watched_episodes").edit().putBoolean(k,watched).apply();if(watched)clearProgress(c,url);}}
}
