package com.example.supuestopitidoalpasar100latidosporsegundo;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class CedulaActivity extends Activity {
    private EditText cedulaInput;
    private Button continueBtn;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences("device_prefs", MODE_PRIVATE);
        // Si ya tiene device_id, saltamos al MainActivity
        if (prefs.contains("device_id")) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_cedula);
        cedulaInput = findViewById(R.id.cedulaInput);
        continueBtn = findViewById(R.id.cedulaContinue);

        continueBtn.setOnClickListener(v -> {
            String cedula = cedulaInput.getText().toString().trim();
            if (cedula.isEmpty()) {
                Toast.makeText(this, "Ingresa tu cédula", Toast.LENGTH_SHORT).show();
                return;
            }
            // Pasamos la cédula a la siguiente pantalla
            Intent i = new Intent(this, CodigoActivity.class);
            i.putExtra("cedula", cedula);
            startActivity(i);
        });
    }
}
