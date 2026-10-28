package com.reelseries.android;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;

public class AdminActivity extends Activity {
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private LinearLayout root;
    private EditText email, password, id, title, category, cover, video, synopsis;
    private TextView message;
    private int color = 0xff0c101b;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 28, 28, 28);
        root.setBackgroundColor(color);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
        showLogin();
    }
    private void reset(String heading) {
        root.removeAllViews();
        TextView h = new TextView(this);
        h.setText(heading); h.setTextSize(24); h.setTextColor(0xffff4564);
        root.addView(h);
        message = new TextView(this); message.setTextColor(-1); root.addView(message);
    }
    private EditText field(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint); e.setHintTextColor(0xffaaaaaa); e.setTextColor(-1);
        root.addView(e); return e;
    }
    private void button(String label, View.OnClickListener action) {
        Button b = new Button(this); b.setText(label); b.setOnClickListener(action); root.addView(b);
    }
    private void showLogin() {
        reset("Administração • Reel Séries");
        if (auth.getCurrentUser() != null) { verifyAdmin(); return; }
        email = field("E-mail do administrador");
        password = field("Senha"); password.setInputType(129);
        button("Entrar", v -> auth.signInWithEmailAndPassword(email.getText().toString().trim(), password.getText().toString())
            .addOnSuccessListener(result -> verifyAdmin())
            .addOnFailureListener(error -> message.setText("Falha no login: " + error.getMessage())));
    }
    private void verifyAdmin() {
        if (auth.getCurrentUser() == null) { showLogin(); return; }
        message.setText("Verificando autorização...");
        db.collection("admins").document(auth.getCurrentUser().getUid()).get()
            .addOnSuccessListener(doc -> {
                if (doc.exists() && Boolean.TRUE.equals(doc.getBoolean("enabled"))) showEditor();
                else { message.setText("Conta sem permissão de administrador."); button("Sair", v -> {auth.signOut(); showLogin();}); }
            })
            .addOnFailureListener(error -> message.setText("Não foi possível verificar permissão: " + error.getMessage()));
    }
    private void showEditor() {
        reset("Gerenciar catálogo");
        message.setText("Alterações são gravadas no Firestore. A sincronização com o catálogo público será ativada na próxima etapa.");
        id = field("ID do título (para editar ou excluir)");
        title = field("Título"); category = field("Categoria"); cover = field("URL da capa HTTPS");
        video = field("URL do vídeo HTTPS"); synopsis = field("Sinopse");
        button("Carregar título", v -> {
            String key = id.getText().toString().trim();
            if (key.isEmpty()) { message.setText("Informe um ID."); return; }
            db.collection("catalogo_admin").document(key).get().addOnSuccessListener(doc -> {
                if (!doc.exists()) {message.setText("Título não encontrado no Firestore."); return;}
                title.setText(doc.getString("nome")); category.setText(doc.getString("categoria"));
                cover.setText(doc.getString("capa")); video.setText(doc.getString("video")); synopsis.setText(doc.getString("sinopse"));
            }).addOnFailureListener(e -> message.setText(e.getMessage()));
        });
        button("Salvar título", v -> {
            String key = id.getText().toString().trim();
            if (key.isEmpty() || title.getText().toString().trim().isEmpty()) {message.setText("Informe ID e título.");return;}
            Map<String,Object> data = new HashMap<>();
            data.put("nome", title.getText().toString().trim());
            data.put("categoria", category.getText().toString().trim());
            data.put("capa", cover.getText().toString().trim());
            data.put("video", video.getText().toString().trim());
            data.put("sinopse", synopsis.getText().toString().trim());
            data.put("updatedAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
            db.collection("catalogo_admin").document(key).set(data)
                .addOnSuccessListener(unused -> message.setText("Título salvo no Firebase."))
                .addOnFailureListener(e -> message.setText("Erro: " + e.getMessage()));
        });
        button("Excluir título", v -> {
            String key = id.getText().toString().trim();
            if (key.isEmpty()) {message.setText("Informe um ID.");return;}
            new android.app.AlertDialog.Builder(this).setTitle("Excluir título?")
                .setMessage("Excluir " + key + " do catálogo administrativo?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Excluir", (dialog, which) -> db.collection("catalogo_admin").document(key).delete()
                    .addOnSuccessListener(unused -> message.setText("Excluído do Firebase."))
                    .addOnFailureListener(e -> message.setText(e.getMessage()))).show();
        });
        button("Sair da administração", v -> {auth.signOut(); showLogin();});
    }
}
