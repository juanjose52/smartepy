package com.example.supuestopitidoalpasar100latidosporsegundo;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class CodigoActivity extends Activity {
    private TextView cedulaValue;
    private EditText codigoInput;
    private Button backBtn, continueBtn;
    private FirebaseFirestore db;
    private SharedPreferences prefs;
    private String cedula;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        cedula = getIntent().getStringExtra("cedula");
        prefs  = getSharedPreferences("device_prefs", MODE_PRIVATE);
        db     = FirebaseFirestore.getInstance();

        setContentView(R.layout.activity_codigo);

        // ① INICIALIZAR VIEWS
        cedulaValue = findViewById(R.id.cedulaValue);
        codigoInput = findViewById(R.id.codigoInput);
        backBtn     = findViewById(R.id.backButton);
        continueBtn = findViewById(R.id.codigoContinue);

        // ② CONFIGURAR VALORES
        cedulaValue.setText(cedula);

        // ③ LISTENERS
        backBtn.setOnClickListener(v -> finish());

        continueBtn.setOnClickListener(v -> {
            String codigo = codigoInput.getText().toString().trim();
            if (codigo.length() != 3) {
                Toast.makeText(this, "Ingresa un código de 3 dígitos", Toast.LENGTH_SHORT).show();
                return;
            }
            validarUsuario();
        });
    }

    private void validarUsuario() {
        db.collection("usuarios")
                .document(cedula)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        Toast.makeText(this, "Cédula no registrada", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    // Opción A: leer directo
                    String correcto = doc.getString("info.codigo");

                    // Opción B alternativa:
                    // Map<String,Object> info = (Map<String,Object>)doc.get("info");
                    // String correcto = info!=null ? String.valueOf(info.get("codigo")) : null;

                    if (correcto != null && correcto.equals(codigoInput.getText().toString().trim())) {
                        prefs.edit().putString("device_id", cedula).apply();
                        startActivity(new Intent(this, MainActivity.class));
                        finish();
                    } else {
                        Toast.makeText(this, "Código incorrecto", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("CodigoActivity", "Error validando usuario", e);
                    Toast.makeText(this, "Error de conexión", Toast.LENGTH_SHORT).show();
                });
    }

}

