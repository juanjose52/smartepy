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
import org.tensorflow.lite.Interpreter;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.io.FileInputStream;
import java.io.IOException;
import android.content.res.AssetFileDescriptor;

public class HeartRateService extends Service implements SensorEventListener {

    private Interpreter tflite;  // TensorFlow Lite model interpreter

    private SensorManager sensorManager;
    private Sensor heartRateSensor;
    private Sensor accelerometer;
    private Queue<Integer> heartRateQueue = new LinkedList<>();
    private float accelX, accelY, accelZ;
    private MediaPlayer mediaPlayer;
    private Vibrator vibrator;
    private boolean isBeeping = false;

    private long lastSaveTime = 0;
    private static final long MIN_INTERVAL = 10 * 1000; // 10 seconds
    private long blockStartTime = 0;

    private PowerManager.WakeLock wakeLock;

    private String deviceId;
    private int userAge = -1;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("HeartRateService", "Servicio iniciado");

        // Cargar el modelo
        loadModel();

        SharedPreferences prefs = getSharedPreferences("device_prefs", MODE_PRIVATE);
        deviceId = prefs.getString("device_id", "unknown_device");
        userAge = prefs.getInt("user_age", -1);

        Log.d("HeartRateService", "Usando deviceId: " + deviceId);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        mediaPlayer = MediaPlayer.create(this, R.raw.beep_sound);

        startForeground(1, createNotification());
        startMonitoring();
    }

    private void loadModel() {
        try {
            // Cargar el modelo TFLite desde los assets
            AssetFileDescriptor fileDescriptor = getAssets().openFd("modelo_predictivo.tflite");
            FileInputStream inputStream = fileDescriptor.createInputStream();
            FileChannel fileChannel = inputStream.getChannel();
            MappedByteBuffer modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.getStartOffset(), fileDescriptor.getDeclaredLength());

            // Crear el intérprete de TensorFlow Lite
            tflite = new Interpreter(modelBuffer);
            Log.d("HeartRateService", "Modelo TFLite cargado con éxito.");
        } catch (IOException e) {
            Log.e("HeartRateService", "Error al cargar el modelo TFLite", e);
        }
    }

    private void makePrediction(float[] input) {
        float[][] output = new float[1][1];  // Salida del modelo

        // Ejecutar la predicción con el modelo
        tflite.run(input, output);

        // Si la salida es mayor que 0.5, activa la alerta (ajusta el umbral según el modelo)
        if (output[0][0] > 0.5) {  // Umbral ajustable dependiendo de la salida del modelo
            triggerAlert();
        }
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

            // Sonido de alerta
            if (mediaPlayer != null) {
                mediaPlayer.start();
            }

            // Vibración
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

        data.put("timestamp_readable", formatTimestamp(timestamp));

        db.collection("usuarios")
                .document(deviceId)  // Usar deviceId aquí
                .collection("sensor_data")
                .add(data)
                .addOnSuccessListener(docRef -> Log.d("HeartRateService", "Datos guardados con ID: " + docRef.getId()))
                .addOnFailureListener(e -> Log.e("HeartRateService", "Error al guardar en sensor_data", e));
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

                // Convertir los datos al formato que el modelo necesita
                float[] input = { (float) heartRate, accelX, accelY, accelZ }; // ejemplo de usar acelerómetro también

                // Realizar la predicción con el modelo
                makePrediction(input);
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
