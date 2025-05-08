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
import android.os.Looper;
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
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import android.content.res.AssetFileDescriptor;
import android.os.VibrationEffect;
import org.tensorflow.lite.Interpreter;

public class HeartRateService extends Service implements SensorEventListener {

    // --- 1) Campos nuevos para modo prueba con HR + acelerómetro ---
    private static final long TEST_INTERVAL_MS = 10 * 1000;
    private boolean isTestMode = false;
    // Cada elemento: {heartRate, accelX, accelY, accelZ}
    private List<float[]> syntheticData = Arrays.asList(
            new float[]{80f, -0.055f, 1.355f, 1.389f},
            new float[]{123f, -0.298f, 0.138f, 0.400f},
            new float[]{178f, 1.066f, -1.101f, 0.812f},
            new float[]{130f, 0.368f, -1.105f, -0.788f},
            new float[]{172f, -1.409f, 0.025f, 0.140f},
            new float[]{169f, -1.008f, -1.274f, 1.115f},
            new float[]{140f, 0.491f, -1.151f, -0.269f},
            new float[]{156f, -1.246f, 0.566f, -1.429f},
            new float[]{175f, -1.315f, -0.555f, -1.261f},
            new float[]{179f, -0.854f, -0.090f, 1.003f},
            new float[]{143f, -0.055f, 1.355f, 1.389f},
            new float[]{169f, -0.765f, -1.011f, -0.662f},
            new float[]{123f, -1.375f, -0.186f, -0.691f},
            new float[]{168f, -0.518f, 1.418f, 0.882f},
            new float[]{141f, 1.294f, -0.486f, -0.776f},
            new float[]{111f, -0.171f, -1.347f, 1.341f},
            new float[]{172f, 1.221f, -0.446f, -1.414f},
            new float[]{112f, 1.455f, 0.925f, 0.211f},
            new float[]{166f, 1.044f, -1.338f, 0.828f},
            new float[]{140f, -1.010f, -1.362f, 0.822f},
            new float[]{127f, -1.214f, -0.970f, 0.588f},
            new float[]{175f, 0.930f, -1.196f, 0.833f},
            new float[]{121f, 0.540f, 0.826f, -0.832f},
            new float[]{152f, 1.134f, 1.387f, 0.848f},
            new float[]{123f, -0.082f, 0.757f, 0.855f},
            new float[]{115f, -0.044f, 1.490f, 0.668f},
            new float[]{110f, 1.407f, 0.923f, -0.477f},
            new float[]{132f, 0.038f, -0.537f, 0.242f},
            new float[]{166f, -0.338f, 0.115f, -0.469f},
            new float[]{150f, -0.566f, 1.443f, 0.641f},
            new float[]{155f, 0.749f, -0.088f, -1.230f},
            new float[]{113f, -1.297f, -0.157f, -1.146f},
            new float[]{144f, 1.416f, 0.862f, 1.386f},
            new float[]{111f, 0.846f, -0.809f, 0.700f},
            new float[]{179f, 1.267f, 0.576f, 1.390f},
            new float[]{159f, -1.117f, 1.145f, -0.854f},
            new float[]{113f, -0.632f, 0.226f, 1.339f},
            new float[]{153f, 0.797f, 0.061f, -0.810f},
            new float[]{142f, -0.048f, 1.237f, -0.154f},
            new float[]{126f, 0.756f, -0.431f, 0.501f},
            new float[]{163f, -0.252f, -0.352f, 0.030f},
            new float[]{133f, 1.332f, 0.871f, 1.198f},
            new float[]{122f, -0.775f, 1.448f, -1.352f},
            new float[]{152f, -1.031f, 0.956f, -1.111f},
            new float[]{114f, -0.609f, 0.046f, -1.471f},
            new float[]{162f, -1.232f, -0.622f, -1.451f},
            new float[]{163f, 1.326f, 1.083f, 1.476f},
            new float[]{149f, 1.097f, -0.982f, 0.413f},
            new float[]{122f, 0.665f, 1.405f, -1.418f},
            new float[]{110f, -1.244f, -0.922f, -0.605f},
            new float[]{137f, -0.785f, -0.583f, -0.450f},
            new float[]{158f, 0.964f, 0.664f, -1.468f},
            new float[]{130f, -0.064f, 0.233f, -0.841f},
            new float[]{166f, 0.611f, 0.588f, -0.423f},
            new float[]{157f, -1.024f, -0.933f, 1.319f},
            new float[]{160f, -0.874f, 1.318f, -1.492f},
            new float[]{164f, 1.016f, 0.427f, -0.176f},
            new float[]{136f, 1.405f, -0.555f, 0.877f},
            new float[]{137f, 0.851f, -0.866f, -1.308f},
            new float[]{131f, -0.074f, -1.002f, -1.260f},
            new float[]{179f, -1.236f, 1.296f, -0.656f},
            new float[]{171f, 1.492f, 0.412f, -0.288f},
            new float[]{175f, -0.680f, 0.270f, -0.385f},
            new float[]{119f, 0.752f, 1.006f, 0.573f},
            new float[]{153f, -1.325f, 1.380f, -1.054f},
            new float[]{157f, 0.234f, -0.341f, -0.959f},
            new float[]{160f, -0.444f, 0.647f, 0.994f},
            new float[]{151f, -0.247f, -0.381f, -1.061f},
            new float[]{149f, -1.004f, 0.589f, 0.862f},
            new float[]{171f, -0.485f, -0.493f, -0.752f},
            new float[]{124f, -0.524f, -1.425f, -0.172f},
            new float[]{155f, 0.740f, 0.615f, -1.335f},
            new float[]{125f, 1.250f, 1.211f, -0.599f},
            new float[]{155f, -0.836f, 1.387f, -0.990f},
            new float[]{144f, 1.155f, -0.817f, -0.987f},
            new float[]{120f, -1.141f, -0.838f, 0.114f},
            new float[]{179f, -0.564f, 1.306f, -1.247f},
            new float[]{161f, 0.584f, -0.867f, -1.205f},
            new float[]{125f, 0.388f, 0.223f, 1.146f},
            new float[]{155f, -0.490f, -1.158f, 0.286f},
            new float[]{122f, 1.220f, 0.677f, 1.305f},
            new float[]{125f, -0.159f, 0.016f, -0.761f},
            new float[]{149f, 0.069f, 0.499f, -1.477f},
            new float[]{175f, 0.355f, 0.060f, 0.623f},
            new float[]{173f, 1.423f, -1.162f, 0.426f},
            new float[]{146f, -0.866f, 0.183f, -1.449f},
            new float[]{161f, -0.050f, -0.951f, 0.903f},
            new float[]{123f, 0.829f, -0.653f, -0.660f},
            new float[]{167f, 0.577f, 0.812f, 0.805f},
            new float[]{110f, -0.511f, -0.393f, -0.230f},
            new float[]{125f, 1.046f, -0.372f, -0.837f},
            new float[]{126f, -0.529f, -0.918f, 0.409f},
            new float[]{145f, 1.213f, 0.167f, -1.325f},
            new float[]{114f, 1.288f, -0.308f, -1.204f},
            new float[]{140f, 0.546f, -0.371f, -1.377f},
            new float[]{124f, 0.605f, 0.741f, -0.280f},
            new float[]{126f, -1.052f, -1.071f, -1.154f},
            new float[]{128f, -1.197f, 0.088f, 0.849f},
            new float[]{124f, -0.050f, 0.818f, 0.374f},
            new float[]{122f, -0.478f, 0.713f, -0.206f},
            new float[]{127f, 1.107f, 0.485f, 1.427f}

            // …añade tantas muestras como necesites
    );
    private int syntheticIndex = 0;
    private Handler testHandler;
    private Runnable testRunnable = new Runnable() {
        @Override
        public void run() {
            if (syntheticIndex < syntheticData.size()) {
                float[] sample = syntheticData.get(syntheticIndex++);
                int hr    = (int) sample[0];
                float ax  = sample[1], ay = sample[2], az = sample[3];
                makePrediction(hr, ax, ay, az);
                sendSensorData(hr, ax, ay, az);
                testHandler.postDelayed(this, TEST_INTERVAL_MS);
            } else {
                testHandler.removeCallbacks(this);
            }
        }
    };

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

        testHandler = new Handler(Looper.getMainLooper());  // inicializamos el handler
        startForeground(1, createNotification());
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (wakeLock==null||!wakeLock.isHeld()) {
            PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HRService::lock");
            wakeLock.acquire();
        }
        isTestMode = intent != null && intent.getBooleanExtra("test_mode", false);

        if (isTestMode) {
            syntheticIndex = 0;
            testHandler.post(testRunnable);
        } else {
            startMonitoring();
        }
        return START_STICKY;
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
        if (heartRateSensor != null)
            sensorManager.registerListener(this, heartRateSensor, SensorManager.SENSOR_DELAY_FASTEST);
        if (accelerometer != null)
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);
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
    public void onSensorChanged(android.hardware.SensorEvent e) {
        if (isTestMode) return;  // ignorar sensores reales en modo prueba

        long now = System.currentTimeMillis();
        if (e.sensor.getType() == Sensor.TYPE_HEART_RATE) {
            int hr = (int) e.values[0];
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

    @Override
    public void onAccuracyChanged(Sensor s, int i) {}

    @Override
    public void onDestroy() {
        super.onDestroy();
        sensorManager.unregisterListener(this);
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        testHandler.removeCallbacks(testRunnable);
    }
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
