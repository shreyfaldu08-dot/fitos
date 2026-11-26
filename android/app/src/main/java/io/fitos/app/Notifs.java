package io.fitos.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

final class Notifs {
    static final String CH_REMIND = "reminders";
    static final String CH_TRACK = "tracking";
    private Notifs() {}

    static void channels(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CH_REMIND, "Reminders", NotificationManager.IMPORTANCE_DEFAULT));
        NotificationChannel t = new NotificationChannel(CH_TRACK, "Step counting and GPS", NotificationManager.IMPORTANCE_LOW);
        t.setShowBadge(false);
        nm.createNotificationChannel(t);
    }

    @SuppressWarnings("deprecation")
    static Notification.Builder builder(Context c, String channel) {
        channels(c);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, channel) : new Notification.Builder(c);
        return b.setSmallIcon(R.drawable.ic_stat).setColor(0xFF3A86E8).setContentIntent(openApp(c));
    }

    static PendingIntent openApp(Context c) {
        Intent i = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(c, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static boolean allowed(Context c) {
        return Build.VERSION.SDK_INT < 33
                || c.checkSelfPermission("android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED;
    }

    static void remind(Context c, int id, String title, String text) {
        if (!allowed(c)) return;
        Notification n = builder(c, CH_REMIND).setContentTitle(title).setContentText(text).setAutoCancel(true).build();
        c.getSystemService(NotificationManager.class).notify(id, n);
    }
}
