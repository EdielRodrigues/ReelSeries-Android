package com.reelseries.android;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Histórico, favoritos e episódios isolados pelo UID autenticado neste aparelho. */
public final class UserMediaState {
 private UserMediaState(){}
 public static SharedPreferences prefs(Context context,String category) {
  FirebaseUser user=FirebaseAuth.getInstance().getCurrentUser();
  String uid=user==null?"sem_login":user.getUid();
  String hash;
  try {
   byte[] bytes=MessageDigest.getInstance("SHA-256").digest(uid.getBytes(StandardCharsets.UTF_8));
   StringBuilder sb=new StringBuilder();
   for(int i=0;i<16;i++)sb.append(String.format(java.util.Locale.US,"%02x",bytes[i]&255));
   hash=sb.toString();
  }catch(Exception e){hash=Integer.toHexString(uid.hashCode());}
  return context.getSharedPreferences("reel_user_"+hash+"_"+category,Context.MODE_PRIVATE);
 }
}
