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
import android.os.PowerManager;
import android.os.Vibrator;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import com.google.firebase.firestore.FirebaseFirestore;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import android.content.res.AssetFileDescriptor;
import android.os.VibrationEffect;
import org.tensorflow.lite.Interpreter;

public class HeartRateService extends Service implements SensorEventListener {

    private Interpreter tflite;
    private SensorManager sensorManager;
    private Sensor heartRateSensor, accelerometer;
    private float accelX, accelY, accelZ;
    private MediaPlayer mediaPlayer;
    private Vibrator vibrator;
    private boolean isBeeping = false;
    private long lastSaveTime = 0;
    private static final long MIN_INTERVAL = 10 * 1000; // 10s
    private PowerManager.WakeLock wakeLock;
    private String deviceId;
    private int userAge;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("HeartRateService", "Servicio iniciado");

        loadModel();

        SharedPreferences prefs = getSharedPreferences("device_prefs", MODE_PRIVATE);
        deviceId = prefs.getString("device_id", "unknown_device");
        userAge = prefs.getInt("user_age", -1);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        heartRateSensor   = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
        accelerometer     = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        vibrator          = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        mediaPlayer       = MediaPlayer.create(this, R.raw.beep_sound);

        startForeground(1, createNotification());
        startMonitoring();
    }

    private void loadModel() {
        try (AssetFileDescriptor fd = getAssets().openFd("modelo_epilepsia_corregido1.tflite");
             FileInputStream is = fd.createInputStream()
        ) {
            FileChannel channel = is.getChannel();
            MappedByteBuffer buf = channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    fd.getStartOffset(),
                    fd.getDeclaredLength()
            );
            tflite = new Interpreter(buf);
            Log.d("HeartRateService", "Modelo TFLite cargado con éxito.");
        } catch (IOException e) {
            Log.e("HeartRateService", "Error cargando modelo", e);
        }
    }

    /** Empaqueta tus 4 features en un tensor [1][1][4] y ejecuta la inferencia */
    private void makePrediction(int heartRate, float ax, float ay, float az) {
        // Asegúrate de que la entrada esté en la forma [1, 1, 4]
        float[][][] input = new float[1][1][4];
        input[0][0][0] = heartRate;
        input[0][0][1] = ax;
        input[0][0][2] = ay;
        input[0][0][3] = az;


        float[][] output = new float[1][1];
        tflite.run(input, output);

        if (output[0][0] > 0.5f) {
            triggerAlert();
        }
    }

    private Notification createNotification() {
        String id = "HeartRateServiceChannel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(id, "Monitoreo Cardíaco", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
        return new NotificationCompat.Builder(this, id)
                .setContentTitle("Monitoreo en 2º plano")
                .setContentText("Detectando cambios en el ritmo cardíaco")
                .setSmallIcon(R.drawable.ic_heart)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .build();
    }

    private void startMonitoring() {
        if (heartRateSensor   != null) sensorManager.registerListener(this, heartRateSensor,   SensorManager.SENSOR_DELAY_FASTEST);
        if (accelerometer     != null) sensorManager.registerListener(this, accelerometer,     SensorManager.SENSOR_DELAY_FASTEST);
    }

    private void triggerAlert() {
        if (isBeeping) return;
        isBeeping = true;
        mediaPlayer.start();
        if (vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
            else
                vibrator.vibrate(500);
        }
        new Handler().postDelayed(() -> isBeeping = false, 2000);
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        long now = System.currentTimeMillis();
        if (e.sensor.getType() == Sensor.TYPE_HEART_RATE) {
            int hr = (int)e.values[0];
            if (now - lastSaveTime >= MIN_INTERVAL) {
                lastSaveTime = now;
                makePrediction(hr, accelX, accelY, accelZ);
                sendSensorData(hr, accelX, accelY, accelZ);
            }
        } else if (e.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            accelX = e.values[0];
            accelY = e.values[1];
            accelZ = e.values[2];
        }
    }

    private void sendSensorData(int hr, float x, float y, float z) {
        if (hr == 0) return;
        Map<String,Object> d = new HashMap<>();
        d.put("heart_rate", hr);
        Map<String,Float> acc = new HashMap<>();
        acc.put("x", x); acc.put("y", y); acc.put("z", z);
        d.put("accelerometer", acc);
        d.put("timestamp_readable", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
        FirebaseFirestore.getInstance()
                .collection("usuarios")
                .document(deviceId)
                .collection("sensor_data")
                .add(d);
    }

    @Override public void onAccuracyChanged(Sensor s, int i) {}
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (wakeLock==null||!wakeLock.isHeld()) {
            PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HRService::lock");
            wakeLock.acquire();
        }
        return START_STICKY;
    }
    @Override public void onDestroy() {
        super.onDestroy();
        sensorManager.unregisterListener(this);
        if (wakeLock!=null&&wakeLock.isHeld()) wakeLock.release();
    }
    @Nullable @Override public IBinder onBind(Intent i) { return null; }
}
