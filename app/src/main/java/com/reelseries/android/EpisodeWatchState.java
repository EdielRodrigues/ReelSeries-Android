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
 public static boolean isWatched(Context c,String url){String k=key(url);return !k.isEmpty()&&UserMediaState.prefs(c,"reel_watched_episodes").getBoolean(k,false);}
 public static void setWatched(Context c,String url,boolean watched){String k=key(url);if(!k.isEmpty())UserMediaState.prefs(c,"reel_watched_episodes").edit().putBoolean(k,watched).apply();}
}
