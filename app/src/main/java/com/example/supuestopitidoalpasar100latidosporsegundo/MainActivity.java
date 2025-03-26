package com.example.supuestopitidoalpasar100latidosporsegundo;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.firebase.FirebaseApp;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.util.concurrent.TimeUnit;
import android.os.Build;


public class MainActivity extends Activity {

    private TextView heartRateText;
    private Button startButton;
    private static final int SENSOR_PERMISSION_CODE = 100;
    private boolean isMonitoring = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FirebaseApp.initializeApp(this);

        heartRateText = findViewById(R.id.heartRateText);
        startButton = findViewById(R.id.startButton);

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isMonitoring) {
                    stopMonitoring();
                } else {
                    checkPermissionsAndStart();
                }
            }
        });

        // Configurar WorkManager para ejecutar el análisis cada 15 minutos
        PeriodicWorkRequest heartRateWorkRequest =
                new PeriodicWorkRequest.Builder(HeartRateWorker.class, 15, TimeUnit.MINUTES)
                        .setInitialDelay(1, TimeUnit.MINUTES)
                        .build();
        WorkManager.getInstance(this).enqueue(heartRateWorkRequest);

        // Solicitar permisos al inicio
        requestPermissions();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            String packageName = getPackageName();

            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + packageName));
                startActivity(intent);
            }
        }
    }

    private void checkPermissionsAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED) {
            startMonitoring();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BODY_SENSORS}, SENSOR_PERMISSION_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == SENSOR_PERMISSION_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startMonitoring();
        }
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12+
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.FOREGROUND_SERVICE,
                    Manifest.permission.FOREGROUND_SERVICE_HEALTH,
                    Manifest.permission.BODY_SENSORS,
                    Manifest.permission.ACTIVITY_RECOGNITION,
                    Manifest.permission.HIGH_SAMPLING_RATE_SENSORS
            }, SENSOR_PERMISSION_CODE);
        }
    }

    private void startMonitoring() {
        startService(new Intent(this, HeartRateService.class));
        isMonitoring = true;
        startButton.setText("Detener");
    }

    private void stopMonitoring() {
        stopService(new Intent(this, HeartRateService.class));
        isMonitoring = false;
        startButton.setText("Iniciar");
    }
}