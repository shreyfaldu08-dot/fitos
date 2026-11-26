package io.fitos.app;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import java.util.Locale;

/** Records a walk, run or ride with GPS, including while the phone is locked. */
public class GpsService extends Service implements LocationListener {
    static volatile boolean on = false;
    static volatile double distance = 0;
    static volatile float accuracy = -1;
    static volatile String error = "";
    static volatile long start = 0;
    static volatile String type = "Walk";

    private LocationManager lm;
    private Location last;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable updater = new Runnable() {
        @Override public void run() {
            if (!on) return;
            getSystemService(NotificationManager.class).notify(12, build());
            handler.postDelayed(this, 10000);
        }
    };

    private Notification build() {
        long mins = (System.currentTimeMillis() - start) / 60000;
        return Notifs.builder(this, Notifs.CH_TRACK)
                .setContentTitle("Recording " + type.toLowerCase(Locale.US))
                .setContentText(String.format(Locale.US, "%.2f km · %d min", distance / 1000.0, mins))
                .setOngoing(true).setOnlyAlertOnce(true).build();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getStringExtra("type") != null) type = intent.getStringExtra("type");
        if (!on) { distance = 0; last = null; accuracy = -1; error = ""; start = System.currentTimeMillis(); }
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(12, build(), 8 /* FOREGROUND_SERVICE_TYPE_LOCATION */);
            else startForeground(12, build());
        } catch (Exception e) { error = "Location permission is needed for GPS"; stopSelf(); return START_NOT_STICKY; }
        lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        try {
            if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) error = "Turn on Location in your phone settings";
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, this, Looper.getMainLooper());
            on = true;
            handler.postDelayed(updater, 10000);
        } catch (SecurityException e) {
            error = "Location permission is needed for GPS"; stopSelf();
        } catch (IllegalArgumentException e) {
            error = "This phone has no GPS"; stopSelf();
        }
        return START_NOT_STICKY;
    }

    @Override public void onLocationChanged(Location l) {
        accuracy = l.getAccuracy();
        error = "";
        if (l.getAccuracy() > 30) return;
        if (last != null) {
            float d = last.distanceTo(l);
            long dt = l.getTime() - last.getTime();
            if (dt <= 0 || d < 3) return;
            if (d / (dt / 1000f) > 30) return;
            distance += d;
        }
        last = l;
    }
    @Override public void onStatusChanged(String p, int s, Bundle b) {}
    @Override public void onProviderEnabled(String p) { error = ""; }
    @Override public void onProviderDisabled(String p) { error = "Turn on Location in your phone settings"; }

    @Override public void onDestroy() {
        handler.removeCallbacks(updater);
        try { if (lm != null) lm.removeUpdates(this); } catch (SecurityException ignored) {}
        on = false;
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent i) { return null; }
}
