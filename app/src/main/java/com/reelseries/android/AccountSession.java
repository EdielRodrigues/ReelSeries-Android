package com.reelseries.android;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Cooperative per-device session leases. Server-side rules cap map size. */
public final class AccountSession {
 private static final long LEASE_MS=2L*60*60*1000;
 private static final long HEARTBEAT_MS=10L*60*1000;
 private AccountSession(){}
 public interface Callback {void done(boolean allowed,String message);}
 private static SharedPreferences prefs(Context ctx){return ctx.getApplicationContext().getSharedPreferences("reel_device_session",Context.MODE_PRIVATE);}
 public static String deviceId(Context ctx){
  SharedPreferences p=prefs(ctx);String id=p.getString("device_id","");
  if(id.isEmpty()){id=UUID.randomUUID().toString().replace("-","");p.edit().putString("device_id",id).apply();}
  return id;
 }
 public static void ensure(Context ctx,boolean force,Callback cb){
  FirebaseUser user=FirebaseAuth.getInstance().getCurrentUser();
  if(user==null){cb.done(false,"Faça login para continuar.");return;}
  String uid=user.getUid(),id=deviceId(ctx);SharedPreferences pref=prefs(ctx);
  long now=System.currentTimeMillis();
  // Always check the server when resuming; do not trust a local heartbeat to authorize access.
  DocumentReference ref=FirebaseFirestore.getInstance().collection("usuarios").document(uid);
  ref.get(com.google.firebase.firestore.Source.SERVER).addOnSuccessListener(doc->{
   if(doc.exists()){claim(ref,uid,id,ctx,cb);return;}
   Map<String,Object> initial=new HashMap<>();initial.put("uid",uid);initial.put("email",user.getEmail()==null?"":user.getEmail());initial.put("ultimoAcesso",com.google.firebase.firestore.FieldValue.serverTimestamp());
   ref.set(initial,SetOptions.merge()).addOnSuccessListener(v->claim(ref,uid,id,ctx,cb))
    .addOnFailureListener(e->cb.done(false,"Não foi possível criar perfil: "+e.getMessage()));
  }).addOnFailureListener(e->cb.done(false,"Não foi possível verificar o acesso: "+e.getMessage()));
 }
 private static void claim(DocumentReference ref,String uid,String id,Context ctx,Callback cb){
  FirebaseFirestore.getInstance().runTransaction(tx->{
   com.google.firebase.firestore.DocumentSnapshot doc=tx.get(ref);
   if(Boolean.TRUE.equals(doc.getBoolean("bloqueado")))throw new IllegalStateException("Conta bloqueada.");
   Long n=doc.getLong("maxAcessos");int limit=n==null?1:Math.max(1,n.intValue());
   Map<String,Object> sessions=new HashMap<>();Object raw=doc.get("sessoesAtivas");
   if(raw instanceof Map){for(Map.Entry<?,?> e:((Map<?,?>)raw).entrySet()){
    if(e.getKey() instanceof String && e.getValue() instanceof Number && System.currentTimeMillis()-((Number)e.getValue()).longValue()<LEASE_MS)
     sessions.put((String)e.getKey(),e.getValue());
   }}
   if(!sessions.containsKey(id) && sessions.size()>=limit)throw new IllegalStateException("Todos os "+limit+" acesso(s) estão em uso. Saia da conta no outro aparelho ou solicite outro acesso ao administrador.");
   sessions.put(id,System.currentTimeMillis());tx.update(ref,"sessoesAtivas",sessions);return true;
  }).addOnSuccessListener(ok->{prefs(ctx).edit().putString("uid",uid).putLong("heartbeat",System.currentTimeMillis()).apply();cb.done(true,"");})
   .addOnFailureListener(e->cb.done(false,e.getMessage()==null?"Não foi possível autorizar este aparelho.":e.getMessage()));
 }
 public static void release(Context ctx,Runnable finished){
  FirebaseUser user=FirebaseAuth.getInstance().getCurrentUser();
  prefs(ctx).edit().remove("heartbeat").remove("uid").apply();
  if(user==null){finished.run();return;}
  String id=deviceId(ctx);
  DocumentReference ref=FirebaseFirestore.getInstance().collection("usuarios").document(user.getUid());
  FirebaseFirestore.getInstance().runTransaction(tx->{
   com.google.firebase.firestore.DocumentSnapshot d=tx.get(ref);
   Map<String,Object> map=new HashMap<>();Object raw=d.get("sessoesAtivas");
   if(raw instanceof Map)for(Map.Entry<?,?> e:((Map<?,?>)raw).entrySet())if(e.getKey() instanceof String)map.put((String)e.getKey(),e.getValue());
   map.remove(id);tx.update(ref,"sessoesAtivas",map);return null;
  }).addOnCompleteListener(t->finished.run());
 }
 public static boolean isListed(Context ctx,com.google.firebase.firestore.DocumentSnapshot doc){
  Object raw=doc.get("sessoesAtivas");
  if(!(raw instanceof Map))return false;
  Map<?,?> map=(Map<?,?>)raw;String id=deviceId(ctx);
  if(!map.containsKey(id))return false;
  Long limit=doc.getLong("maxAcessos");int max=limit==null?1:Math.max(1,limit.intValue());
  if(map.size()<=max)return true;
  // If ADM reduced the limit, keep the oldest sessions and revoke the excess.
  java.util.ArrayList<String> keys=new java.util.ArrayList<>();
  for(Object key:map.keySet())if(key instanceof String)keys.add((String)key);
  java.util.Collections.sort(keys,(a,b)->{
   Object av=map.get(a),bv=map.get(b);
   long aa=av instanceof Number?((Number)av).longValue():Long.MAX_VALUE;
   long bb=bv instanceof Number?((Number)bv).longValue():Long.MAX_VALUE;
   int c=Long.compare(aa,bb);return c!=0?c:a.compareTo(b);
  });
  return keys.indexOf(id)>=0&&keys.indexOf(id)<max;
 }
}
