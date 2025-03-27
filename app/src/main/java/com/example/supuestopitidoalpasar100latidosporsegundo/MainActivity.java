package com.example.supuestopitidoalpasar100latidosporsegundo;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends Activity implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor heartRateSensor;
    private Sensor accelerometer;
    private TextView heartRateText;
    private Button startButton;
    private MediaPlayer mediaPlayer;
    private Vibrator vibrator;

    private static final int HEART_RATE_LIMIT = 80;
    private static final int SENSOR_PERMISSION_CODE = 100;

    private Handler handler = new Handler();
    private Runnable sendDataRunnable;
    private long lastSendTime = 0;
    private static final long SEND_INTERVAL_MS = 1000; // Intervalo de envío en milisegundos (1 segundo)

    private int heartRate = -1;
    private float accelX, accelY, accelZ;
    private boolean isMonitoring = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Inicializa Firebase
        FirebaseApp.initializeApp(this);

        heartRateText = findViewById(R.id.heartRateText);
        startButton = findViewById(R.id.startButton);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        mediaPlayer = MediaPlayer.create(this, R.raw.beep_sound);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        // Configurar el Handler y el Runnable para enviar datos periódicamente
        sendDataRunnable = new Runnable() {
            @Override
            public void run() {
                if (isMonitoring) {
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastSendTime >= SEND_INTERVAL_MS) {
                        sendSensorData();
                        lastSendTime = currentTime;
                    }
                    handler.postDelayed(this, SEND_INTERVAL_MS);
                }
            }
        };

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isMonitoring) {
                    stopMonitoring();
                } else {
                    checkPermissionsAndStart();
                    handler.post(sendDataRunnable); // Iniciar el envío de datos
                }
            }
        });
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

    private void startMonitoring() {
        if (heartRateSensor != null && accelerometer != null) {
            sensorManager.registerListener(this, heartRateSensor, SensorManager.SENSOR_DELAY_NORMAL);
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
            isMonitoring = true;
            startButton.setText("Detener");
        } else {
            heartRateText.setText("Sensor no disponible");
        }
    }

    private void stopMonitoring() {
        sensorManager.unregisterListener(this, heartRateSensor);
        sensorManager.unregisterListener(this, accelerometer);
        isMonitoring = false;
        startButton.setText("Iniciar");
        heartRateText.setText("Monitoreo detenido");

        // Reiniciar las variables del sensor
        heartRate = -1;
        accelX = 0f;
        accelY = 0f;
        accelZ = 0f;

        // Detener el envío periódico de datos
        handler.removeCallbacks(sendDataRunnable);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_HEART_RATE) {
            heartRate = (int) event.values[0];
            heartRateText.setText("Ritmo cardíaco: " + heartRate + " BPM");

            if (heartRate > HEART_RATE_LIMIT) {
                mediaPlayer.start();
                if (vibrator != null && vibrator.hasVibrator()) {
                    vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
                }
            }
        } else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            accelX = event.values[0];
            accelY = event.values[1];
            accelZ = event.values[2];
        }
    }

    private void sendSensorData() {
        if (heartRate > 0) {
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            Map<String, Object> data = new HashMap<>();
            data.put("heart_rate", heartRate);

            // Usar los valores actualizados del acelerómetro
            Map<String, Double> accelerometerData = new HashMap<>();
            accelerometerData.put("x", (double) accelX);
            accelerometerData.put("y", (double) accelY);
            accelerometerData.put("z", (double) accelZ);
            data.put("accelerometer", accelerometerData);

            data.put("timestamp", System.currentTimeMillis());

            db.collection("sensor_data").add(data)
                    .addOnSuccessListener(documentReference -> {
                        System.out.println("Datos guardados con ID: " + documentReference.getId());
                    })
                    .addOnFailureListener(e -> {
                        System.err.println("Error al guardar los datos: " + e.getMessage());
                    });
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // No es necesario implementar
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopMonitoring();
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
    }
}

