package com.example.supuestopitidoalpasar100latidosporsegundo;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;

public class HeartRateWorker extends Worker {

    public HeartRateWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d("HeartRateWorker", "Analizando datos de frecuencia cardíaca...");

        try {
            analyzeHeartRateData();
            return Result.success();
        } catch (Exception e) {
            Log.e("HeartRateWorker", "Error al analizar los datos", e);
            return Result.retry();
        }
    }

    private void analyzeHeartRateData() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("sensor_data")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(200)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    ArrayList<Long> heartRates = new ArrayList<>();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        Long heartRate = document.getLong("heart_rate");
                        if (heartRate != null) {
                            heartRates.add(heartRate);
                        }
                    }

                    if (!heartRates.isEmpty()) {
                        long sum = 0;
                        long min = Long.MAX_VALUE;
                        long max = Long.MIN_VALUE;
                        for (long hr : heartRates) {
                            sum += hr;
                            if (hr < min) min = hr;
                            if (hr > max) max = hr;
                        }
                        double average = sum / (double) heartRates.size();

                        double variance = 0;
                        for (long hr : heartRates) {
                            variance += Math.pow(hr - average, 2);
                        }
                        double stdDev = Math.sqrt(variance / heartRates.size());

                        saveExtendedAnalysis(average, min, max, stdDev);
                    }
                })
                .addOnFailureListener(e -> Log.e("HeartRateWorker", "Error al obtener los datos", e));
    }

    private String formatTimestamp(long timestamp) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
        return sdf.format(new java.util.Date(timestamp));
    }

    private void saveExtendedAnalysis(double average, long min, long max, double stdDev) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        long timestamp = System.currentTimeMillis();

        Map<String, Object> data = new HashMap<>();
        data.put("avg_heart_rate", average);
        data.put("min_heart_rate", min);
        data.put("max_heart_rate", max);
        data.put("timestamp_readable", formatTimestamp(timestamp));
    }
}
