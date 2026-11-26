package io.fitos.app;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.IBinder;

/** Keeps listening to the step counter while FitOS is closed, so each day's total is exact. */
public class StepService extends Service implements SensorEventListener {
    static volatile boolean running = false;
    private SensorManager sensors;

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        Notification n = Notifs.builder(this, Notifs.CH_TRACK)
                .setContentTitle("FitOS is counting your steps")
                .setContentText("Tap to open FitOS")
                .setOngoing(true).build();
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(11, n, 256 /* FOREGROUND_SERVICE_TYPE_HEALTH */);
            else startForeground(11, n);
        } catch (Exception e) { stopSelf(); return START_NOT_STICKY; }
        if (sensors == null) {
            sensors = (SensorManager) getSystemService(SENSOR_SERVICE);
            Sensor s = sensors == null ? null : sensors.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
            if (s == null) { stopSelf(); return START_NOT_STICKY; }
            sensors.registerListener(this, s, SensorManager.SENSOR_DELAY_NORMAL);
        }
        running = true;
        return START_STICKY;
    }

    private long lastWidget = 0;
    @Override public void onSensorChanged(SensorEvent e) {
        StepStore.record(this, e.values[0]);
        long now = System.currentTimeMillis();
        if (now - lastWidget > 60000) { lastWidget = now; FitosWidget.updateAll(this); }
    }
    @Override public void onAccuracyChanged(Sensor s, int a) {}

    @Override public void onDestroy() {
        if (sensors != null) sensors.unregisterListener(this);
        running = false;
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent i) { return null; }
}
