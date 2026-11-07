package com.reelseries.android;

import android.content.Context;
import android.os.Build;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

/** Xtream credentials are device-local and protected with Android Keystore AES/GCM. */
public final class XtreamCredentials {
 private static final String ALIAS="reel_xtream_admin_v1";
 private XtreamCredentials(){}
 private static SecretKey key()throws Exception{
  KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
  if(store.containsAlias(ALIAS))return (SecretKey)store.getKey(ALIAS,null);
  KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
  generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
   .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());
  return generator.generateKey();
 }
 public static void save(Context context,String password)throws Exception{
  Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());
  context.getSharedPreferences("xtream_admin",Context.MODE_PRIVATE).edit()
   .putString("secret",Base64.encodeToString(cipher.doFinal(password.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP))
   .putString("iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)).apply();
 }
 public static String load(Context context)throws Exception{
  android.content.SharedPreferences p=context.getSharedPreferences("xtream_admin",Context.MODE_PRIVATE);
  String secret=p.getString("secret","");if(secret.isEmpty())return "";
  Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
  cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(p.getString("iv",""),Base64.NO_WRAP)));
  return new String(cipher.doFinal(Base64.decode(secret,Base64.NO_WRAP)),StandardCharsets.UTF_8);
 }
}
