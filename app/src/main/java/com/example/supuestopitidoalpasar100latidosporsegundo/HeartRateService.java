package com.example.supuestopitidoalpasar100latidosporsegundo;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import com.google.firebase.Timestamp;
import android.os.PowerManager;
import android.net.Uri;
import android.provider.Settings;




public class HeartRateService extends Service implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor heartRateSensor;
    private Sensor accelerometer;
    private Queue<Integer> heartRateQueue = new LinkedList<>();
    private float accelX, accelY, accelZ;
    private MediaPlayer mediaPlayer;
    private Vibrator vibrator;
    private boolean isBeeping = false;

    private long lastSaveTime = 0;
    private static final long MIN_INTERVAL = 10 * 1000; // 10 segundos
    private long blockStartTime = 0;

    private PowerManager.WakeLock wakeLock;

    private String deviceId;
    private int userAge = -1;
    private int frecuenciaUmbral = 80;


    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("HeartRateService", "Servicio iniciado");

        SharedPreferences prefs = getSharedPreferences("device_prefs", MODE_PRIVATE);
        deviceId = prefs.getString("device_id", "unknown_device");
        userAge = prefs.getInt("user_age", -1);

// Establecer el umbral dinámico según la edad
        if (userAge >= 1 && userAge <= 3) {
            frecuenciaUmbral = 150;
        } else if (userAge >= 4 && userAge <= 5) {
            frecuenciaUmbral = 140;
        } else if (userAge >= 6 && userAge <= 12) {
            frecuenciaUmbral = 130;
        } else if (userAge >= 13 && userAge <= 18) {
            frecuenciaUmbral = 120;
        } else if (userAge > 18 && userAge <= 60) {
            frecuenciaUmbral = 150;
        } else if (userAge > 60) {
            frecuenciaUmbral = 90;
        } else {
            frecuenciaUmbral = 80; // caso desconocido
        }

        Log.d("HeartRateService", "Edad: " + userAge + " → Umbral: " + frecuenciaUmbral + " BPM");

        Log.d("HeartRateService", "Usando deviceId: " + deviceId);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        mediaPlayer = MediaPlayer.create(this, R.raw.beep_sound);

        startForeground(1, createNotification());
        startMonitoring();
    }


    private Notification createNotification() {
        String channelId = "HeartRateServiceChannel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "Heart Rate Service", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
        return new NotificationCompat.Builder(this, channelId)
                .setContentTitle("Monitoreo en segundo plano")
                .setContentText("Detectando cambios en el ritmo cardíaco")
                .setSmallIcon(R.drawable.ic_heart)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .build();
    }

    private void startMonitoring() {
        if (heartRateSensor != null) {
            sensorManager.registerListener(this, heartRateSensor, SensorManager.SENSOR_DELAY_FASTEST);
        }
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);
            }
    }

    private void triggerAlert() {
        if (!isBeeping) {
            isBeeping = true;
            Log.d("HeartRateService", "¡ALERTA! Ritmo cardíaco elevado");

            if (mediaPlayer != null) {
                mediaPlayer.start();
            }
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(500);
                }
            }
            new Handler().postDelayed(() -> isBeeping = false, 2000);
        }
    }

    private void sendSensorData(int heartRate, float accelX, float accelY, float accelZ) {

        if (heartRate == 0) return;
        long timestamp = System.currentTimeMillis();

        Log.d("HeartRateService", "Guardando datos en Firebase...");

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Map<String, Object> data = new HashMap<>();
        data.put("heart_rate", heartRate);

        Map<String, Float> accelerometerData = new HashMap<>();
        accelerometerData.put("x", accelX);
        accelerometerData.put("y", accelY);
        accelerometerData.put("z", accelZ);
        data.put("accelerometer", accelerometerData);

        // Agregar timestamps en 3 formatos
        data.put("timestamp", timestamp);
        data.put("timestamp_readable", formatTimestamp(timestamp));
        data.put("timestamp_ts", new Timestamp(new Date(timestamp)));

        db.collection("usuarios")
                .document(deviceId)
                .collection("sensor_data")
                .add(data)
                .addOnSuccessListener(docRef -> Log.d("HeartRateService", "Datos guardados con ID: " + docRef.getId()))
                .addOnFailureListener(e -> Log.e("HeartRateService", "Error al guardar en sensor_data", e));
    }


    private void saveAnalysisResult(int averageHeartRate, long startTime, long endTime, int sampleSize) {
        Log.d("HeartRateService", "Guardando promedio con contexto: " + averageHeartRate);

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Map<String, Object> analysisData = new HashMap<>();
        analysisData.put("average_heart_rate", averageHeartRate);
        analysisData.put("start_time", startTime);
        analysisData.put("end_time", endTime);
        analysisData.put("start_time_readable", formatTimestamp(startTime));
        analysisData.put("end_time_readable", formatTimestamp(endTime));
        analysisData.put("start_time_ts", new Timestamp(new Date(startTime)));
        analysisData.put("end_time_ts", new Timestamp(new Date(endTime)));
        analysisData.put("sample_size", sampleSize);

        long now = System.currentTimeMillis();
        analysisData.put("timestamp", now);
        analysisData.put("timestamp_readable", formatTimestamp(now));
        analysisData.put("timestamp_ts", new Timestamp(new Date(now)));

        db.collection("usuarios")
                .document(deviceId)
                .collection("heart_rate_analysis")
                .add(analysisData)
                .addOnSuccessListener(docRef -> Log.d("HeartRateService", "Promedio guardado con ID: " + docRef.getId()))
                .addOnFailureListener(e -> Log.e("HeartRateService", "Error al guardar el promedio", e));
    }


    private String formatTimestamp(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        long currentTime = System.currentTimeMillis();

        if (event.sensor.getType() == Sensor.TYPE_HEART_RATE) {
            int heartRate = (int) event.values[0];
            Log.d("HeartRateService", "❤️ Ritmo cardíaco detectado: " + heartRate);

            if (currentTime - lastSaveTime >= MIN_INTERVAL) {
                lastSaveTime = currentTime;

                if (heartRateQueue.isEmpty()) {
                    blockStartTime = currentTime;
                    Log.d("HeartRateService", "🕓 Nuevo bloque iniciado en: " + blockStartTime);
                }

                heartRateQueue.add(heartRate);
                Log.d("HeartRateService", "📥 Lectura agregada. Total acumuladas: " + heartRateQueue.size());

                sendSensorData(heartRate, accelX, accelY, accelZ);

                // Calcular promedio cada 50 lecturas
                if (heartRateQueue.size() >= 25) {
                    int sum = 0;
                    for (int hr : heartRateQueue) {
                        sum += hr;
                    }
                    int averageHeartRate = sum / heartRateQueue.size();
                    long blockEndTime = currentTime;

                    Log.d("HeartRateService", "✅ Se alcanzaron 20 lecturas. Promedio: " + averageHeartRate);
                    Log.d("HeartRateService", "📤 Guardando en Firestore...");

                    saveAnalysisResult(averageHeartRate, blockStartTime, blockEndTime, heartRateQueue.size());
                    heartRateQueue.clear();
                }

                if (heartRate > frecuenciaUmbral) {
                    Log.d("HeartRateService", "🚨 Frecuencia mayor a " + frecuenciaUmbral + " BPM, activando alerta...");
                    triggerAlert();
                }
            }
        } else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            accelX = event.values[0];
            accelY = event.values[1];
            accelZ = event.values[2];
        }
    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (wakeLock == null || !wakeLock.isHeld()) {
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HeartRateService::WakeLock");
            wakeLock.acquire();
            Log.d("HeartRateService", "🔒 WakeLock adquirido desde onStartCommand");
        }

        return START_STICKY;
    }



    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d("HeartRateService", "Servicio detenido");
        sensorManager.unregisterListener(this);
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            Log.d("HeartRateService", "🔓 WakeLock liberado.");
        }

    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
