package io.fitos.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Calendar;

/** Daily reminder alarms. Inexact, so they need no special permission. */
final class Reminders {
    static final int[] WATER_HOURS = {10, 12, 14, 16, 18, 20};
    static final int TASK_BASE = 200, MAX_TASKS = 40;
    private Reminders() {}

    static void schedule(Context c, String json) {
        c.getSharedPreferences("fitos", Context.MODE_PRIVATE).edit().putString("reminders", json).apply();
        AlarmManager am = c.getSystemService(AlarmManager.class);
        cancelAll(c, am);
        try {
            JSONObject o = new JSONObject(json);
            if (on(o, "workout")) set(c, am, 1, "workout", time(o, "workout", "18:00"));
            if (on(o, "meals")) set(c, am, 2, "meals", time(o, "meals", "21:00"));
            if (on(o, "weigh")) set(c, am, 3, "weigh", time(o, "weigh", "07:30"));
            if (on(o, "tasks")) set(c, am, 4, "tasks", time(o, "tasks", "20:00"));
            if (on(o, "weekly")) setWeekly(c, am, 5, "weekly", time(o, "weekly", "19:00"));
            if (on(o, "water")) for (int i = 0; i < WATER_HOURS.length; i++) set(c, am, 100 + i, "water", new int[]{WATER_HOURS[i], 0});
            JSONArray tt = o.optJSONArray("taskTimes");
            if (tt != null) for (int i = 0; i < tt.length() && i < MAX_TASKS; i++) {
                JSONObject t = tt.getJSONObject(i);
                String[] p = t.optString("t", "").split(":");
                if (p.length != 2) continue;
                int[] hm = {Integer.parseInt(p[0]), Integer.parseInt(p[1])};
                Intent extra = new Intent().putExtra("tid", t.optString("id")).putExtra("title", t.optString("title"))
                        .putExtra("days", t.optJSONArray("days") == null ? "" : t.optJSONArray("days").toString());
                setWith(c, am, TASK_BASE + i, "task", hm, extra);
            }
        } catch (Exception ignored) {}
    }

    static void reschedule(Context c) {
        String json = c.getSharedPreferences("fitos", Context.MODE_PRIVATE).getString("reminders", null);
        if (json != null) schedule(c, json);
    }

    private static boolean on(JSONObject o, String k) { JSONObject r = o.optJSONObject(k); return r != null && r.optBoolean("on"); }

    private static int[] time(JSONObject o, String k, String def) {
        String t = o.optJSONObject(k).optString("t", def);
        try { String[] p = t.split(":"); return new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1])}; }
        catch (Exception e) { String[] p = def.split(":"); return new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1])}; }
    }

    private static PendingIntent pending(Context c, int code, String kind, int flags) {
        Intent i = new Intent(c, ReminderReceiver.class).setAction("io.fitos.REMIND." + code).putExtra("kind", kind);
        return PendingIntent.getBroadcast(c, code, i, flags | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void setWeekly(Context c, AlarmManager am, int code, String kind, int[] hm) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
        cal.set(Calendar.HOUR_OF_DAY, hm[0]); cal.set(Calendar.MINUTE, hm[1]); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
        while (cal.getTimeInMillis() <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 7);
        PendingIntent pi = pending(c, code, kind, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), AlarmManager.INTERVAL_DAY * 7, pi);
    }

    private static void setWith(Context c, AlarmManager am, int code, String kind, int[] hm, Intent extra) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hm[0]); cal.set(Calendar.MINUTE, hm[1]); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1);
        Intent i = new Intent(c, ReminderReceiver.class).setAction("io.fitos.REMIND." + code).putExtra("kind", kind).putExtras(extra);
        PendingIntent pi = PendingIntent.getBroadcast(c, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pi);
    }

    private static void set(Context c, AlarmManager am, int code, String kind, int[] hm) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hm[0]); cal.set(Calendar.MINUTE, hm[1]); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1);
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), AlarmManager.INTERVAL_DAY,
                pending(c, code, kind, PendingIntent.FLAG_UPDATE_CURRENT));
    }

    private static void cancelAll(Context c, AlarmManager am) {
        int[] codes = {1, 2, 3, 4, 5, 100, 101, 102, 103, 104, 105};
        String[] kinds = {"workout", "meals", "weigh", "tasks", "weekly", "water", "water", "water", "water", "water", "water"};
        for (int i = 0; i < codes.length; i++) {
            PendingIntent pi = pending(c, codes[i], kinds[i], PendingIntent.FLAG_NO_CREATE);
            if (pi != null) { am.cancel(pi); pi.cancel(); }
        }
        for (int i = 0; i < MAX_TASKS; i++) {
            PendingIntent pi = pending(c, TASK_BASE + i, "task", PendingIntent.FLAG_NO_CREATE);
            if (pi != null) { am.cancel(pi); pi.cancel(); }
        }
    }
}
