package com.reelseries.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.text.InputFilter;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import org.json.JSONObject;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.Locale;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Proteção local por PIN de quatro dígitos, sem armazenar o PIN em texto claro. */
public final class AdultProtection {
 private static final String PREFS="reel_adult_protection";
 private static final String HASH="pin_hash";
 private static final String SALT="pin_salt";
 private static final int ITERATIONS=120000;
 private static long unlockedUntil=0L;
 private static String unlockedUid="";
 private static String attemptUid="";
 private static int failures=0;
 private static long blockedUntil=0L;
 private AdultProtection(){}

 private static String norm(String value){
  return Normalizer.normalize(value==null?"":value,Normalizer.Form.NFD)
   .replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT);
 }
 public static boolean adult(String label){
  String s=" "+norm(label).replaceAll("[^a-z0-9]+"," ").trim()+" ";
  return s.contains(" adulto ")||s.contains(" adultos ")||s.contains(" adult ")||
   s.contains(" xxx ")||s.contains(" erotico ")||s.contains(" erotica ")||
   s.contains(" pornografia ")||s.contains(" porno ")||s.contains(" 18 ")||
   s.contains(" 18plus ")||s.contains(" 18 anos ");
 }
 public static boolean adult(JSONObject item){
  return item!=null&&(adult(item.optString("categoria"))||adult(item.optString("grupo"))||
   adult(item.optString("group"))||adult(item.optString("group_title"))||
   adult(item.optString("nome"))&&adult(item.optString("categoria")));
 }
 private static boolean valid(String pin){return pin!=null&&pin.matches("[0-9]{4}");}
 private static byte[] hexDecode(String s){
  if(s==null||s.length()%2!=0)throw new IllegalArgumentException("Hex inválido");
  byte[] b=new byte[s.length()/2];
  for(int i=0;i<b.length;i++){
   int a=Character.digit(s.charAt(i*2),16),c=Character.digit(s.charAt(i*2+1),16);
   if(a<0||c<0)throw new IllegalArgumentException("Hex inválido");
   b[i]=(byte)((a<<4)|c);
  }
  return b;
 }
 private static String hex(byte[] bytes){
  char[] digits="0123456789abcdef".toCharArray();char[] out=new char[bytes.length*2];
  for(int i=0;i<bytes.length;i++){out[i*2]=digits[(bytes[i]>>4)&15];out[i*2+1]=digits[bytes[i]&15];}
  return new String(out);
 }
 private static byte[] hash(String pin,byte[] salt){
  PBEKeySpec spec=new PBEKeySpec(pin.toCharArray(),salt,ITERATIONS,256);
  try{return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}
  catch(Exception e){throw new IllegalStateException("Não foi possível validar o PIN",e);}
  finally{spec.clearPassword();}
 }
 private static String uid(){
  com.google.firebase.auth.FirebaseUser user=com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
  return user==null?"":user.getUid();
 }
 private static SharedPreferences prefs(Context ctx){
  return UserMediaState.prefs(ctx,PREFS);
 }
 private static void syncAccount(){
  String current=uid();
  if(!current.equals(attemptUid)){
   attemptUid=current;unlockedUid="";unlockedUntil=0;failures=0;blockedUntil=0;
  }
 }
 private static boolean verify(Context ctx,String pin){
  if(!valid(pin))return false;
  SharedPreferences p=prefs(ctx);
  String stored=p.getString(HASH,null),salt=p.getString(SALT,null);
  if(stored==null||salt==null)return "0000".equals(pin); // PIN inicial da versão anterior
  try{return MessageDigest.isEqual(hash(pin,hexDecode(salt)),hexDecode(stored));}
  catch(Exception e){return false;}
 }
 private static void save(Context ctx,String pin){
  byte[] salt=new byte[16];new SecureRandom().nextBytes(salt);
  if(!prefs(ctx).edit().putString(SALT,hex(salt)).putString(HASH,hex(hash(pin,salt))).commit())
   throw new IllegalStateException("Falha ao salvar o PIN");
  unlockedUntil=0;unlockedUid="";failures=0;blockedUntil=0;
 }
 public static boolean unlocked(){syncAccount();return !uid().isEmpty()&&uid().equals(unlockedUid)&&SystemClock.elapsedRealtime()<unlockedUntil;}
 public static void lock(){unlockedUntil=0;unlockedUid="";}
 private static boolean allowed(Activity a){
  syncAccount();
  long remain=blockedUntil-SystemClock.elapsedRealtime();
  if(remain>0){Toast.makeText(a,"Aguarde "+((remain+999)/1000)+" segundos para tentar novamente.",Toast.LENGTH_LONG).show();return false;}
  return true;
 }
 private static boolean check(Activity a,String pin,EditText field){
  if(!allowed(a))return false;
  if(verify(a,pin)){failures=0;return true;}
  failures++;
  if(failures>=5){blockedUntil=SystemClock.elapsedRealtime()+30000;failures=0;}
  field.setText("");field.setError("Essa senha está incorreta.");
  Toast.makeText(a,"Essa senha está incorreta.",Toast.LENGTH_SHORT).show();
  return false;
 }
 private static EditText input(Activity a,String hint){
  EditText field=new EditText(a);field.setSingleLine(true);field.setHint(hint);
  field.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);
  field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});
  return field;
 }
 public static void require(Activity a,boolean restricted,Runnable next){
  syncAccount();
  if(!restricted||unlocked()){next.run();return;}
  EditText pin=input(a,"PIN de 4 dígitos");
  LinearLayout layout=new LinearLayout(a);layout.setOrientation(LinearLayout.VERTICAL);
  int pad=(int)(20*a.getResources().getDisplayMetrics().density);
  layout.setPadding(pad,pad,pad,0);layout.addView(pin);
  AlertDialog dialog=new AlertDialog.Builder(a).setTitle("🔒 Controle de proteção adulto")
   .setMessage("Digite a senha para acessar este conteúdo.")
   .setView(layout).setNegativeButton("Cancelar",null).setPositiveButton("Desbloquear",null).create();
  dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
   if(check(a,pin.getText().toString(),pin)){
    unlockedUid=uid();unlockedUntil=SystemClock.elapsedRealtime()+5*60*1000L;
    dialog.dismiss();next.run();
   }
  }));
  dialog.show();
 }
 public static void changePin(Activity a){
  syncAccount();
  LinearLayout layout=new LinearLayout(a);layout.setOrientation(LinearLayout.VERTICAL);
  int pad=(int)(20*a.getResources().getDisplayMetrics().density);
  layout.setPadding(pad,pad,pad,0);
  EditText old=input(a,"PIN atual (padrão: 0000)");
  EditText next=input(a,"Novo PIN de 4 dígitos");
  EditText confirm=input(a,"Confirmar novo PIN");
  layout.addView(old);layout.addView(next);layout.addView(confirm);
  AlertDialog dialog=new AlertDialog.Builder(a).setTitle("Alterar PIN de conteúdo adulto")
   .setMessage("Informe o PIN atual e escolha outro PIN com 4 números.")
   .setView(layout).setNegativeButton("Cancelar",null).setPositiveButton("Salvar novo PIN",null).create();
  dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
   if(!check(a,old.getText().toString(),old))return;
   String value=next.getText().toString();
   if(!valid(value)){next.setError("Digite exatamente 4 números.");return;}
   if(!value.equals(confirm.getText().toString())){confirm.setError("Os PINs não coincidem.");return;}
   if(value.equals(old.getText().toString())){next.setError("Escolha um PIN diferente do atual.");return;}
   try{save(a,value);dialog.dismiss();Toast.makeText(a,"PIN alterado com sucesso. O anterior não funciona mais.",Toast.LENGTH_LONG).show();}
   catch(Exception e){Toast.makeText(a,"Não foi possível salvar o PIN.",Toast.LENGTH_LONG).show();}
  }));
  dialog.show();
 }
}
