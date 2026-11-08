package com.reelseries.android;

import android.app.*;
import android.content.*;
import android.text.InputType;
import android.widget.*;
import org.json.JSONObject;
import java.text.Normalizer;
import java.util.Locale;

/** PIN local para pastas e títulos de conteúdo adulto. */
public final class AdultProtection {
 private AdultProtection(){}
 private static long unlockedUntil=0L;
 private static String norm(String value){
  String s=Normalizer.normalize(value==null?"":value,Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT);
  return s;
 }
 public static boolean adult(String label){
  String s=" "+norm(label).replaceAll("[^a-z0-9]+"," ").trim()+" ";
  return s.contains(" adulto ")||s.contains(" adultos ")||s.contains(" adult ")||
   s.contains(" xxx ")||s.contains(" erotico ")||s.contains(" erotica ")||
   s.contains(" pornografia ")||s.contains(" porno ")||s.contains(" 18 ")||
   s.contains(" 18plus ")||s.contains(" +18 ");
 }
 public static boolean adult(JSONObject item){
  return item!=null&&(adult(item.optString("categoria"))||adult(item.optString("grupo"))||
   adult(item.optString("group"))||adult(item.optString("group_title")));
 }
 public static boolean unlocked(){return android.os.SystemClock.elapsedRealtime()<unlockedUntil;}
 public static void require(Activity activity,boolean restricted,Runnable next){
  if(!restricted||unlocked()){next.run();return;}
  EditText pin=new EditText(activity);pin.setSingleLine(true);pin.setHint("PIN de 4 dígitos");
  pin.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);
  pin.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(4)});
  int pad=(int)(20*activity.getResources().getDisplayMetrics().density);
  android.widget.LinearLayout box=new android.widget.LinearLayout(activity);box.setOrientation(1);box.setPadding(pad,pad,pad/2,0);box.addView(pin);
  AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("🔒 Controle de proteção adulto")
   .setMessage("Digite a senha para acessar este conteúdo.")
   .setView(box).setNegativeButton("Cancelar",null).setPositiveButton("Desbloquear",null).create();
  dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
   if("0000".equals(pin.getText().toString())){
    unlockedUntil=android.os.SystemClock.elapsedRealtime()+5*60*1000L;
    dialog.dismiss();next.run();
   }else{pin.setText("");pin.setError("Senha incorreta");}
  }));
  dialog.show();
 }
}
