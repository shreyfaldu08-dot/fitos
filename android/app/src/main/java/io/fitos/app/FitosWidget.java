package io.fitos.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;
import org.json.JSONObject;
import java.text.NumberFormat;
import java.util.Locale;

/** Home-screen widget: calories left, water (+250 ml without opening the app), steps and today's tasks. */
public class FitosWidget extends AppWidgetProvider {
    static final String ACTION_WATER = "io.fitos.WIDGET_WATER";

    @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) { for (int id : ids) m.updateAppWidget(id, build(c)); }

    @Override public void onReceive(Context c, Intent i) {
        super.onReceive(c, i);
        if (ACTION_WATER.equals(i.getAction())) {
            SharedPreferences p = c.getSharedPreferences("fitos", Context.MODE_PRIVATE);
            try {
                JSONObject pend = new JSONObject(p.getString("pendingWater", "{}"));
                String day = StepStore.today();
                pend.put(day, pend.optInt(day, 0) + 250);
                p.edit().putString("pendingWater", pend.toString()).apply();
            } catch (Exception ignored) {}
            updateAll(c);
        }
    }

    static void updateAll(Context c) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            int[] ids = m.getAppWidgetIds(new ComponentName(c, FitosWidget.class));
            if (ids != null && ids.length > 0) for (int id : ids) m.updateAppWidget(id, build(c));
        } catch (Exception ignored) {}
    }

    private static String n(double v) { return NumberFormat.getIntegerInstance(Locale.getDefault()).format(Math.round(v)); }

    static RemoteViews build(Context c) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget);
        SharedPreferences p = c.getSharedPreferences("fitos", Context.MODE_PRIVATE);
        String day = StepStore.today();
        try {
            JSONObject st = new JSONObject(p.getString("status", "{}"));
            JSONObject pend = new JSONObject(p.getString("pendingWater", "{}"));
            boolean today = day.equals(st.optString("date"));
            double goal = st.optDouble("kcalGoal", 0), eaten = today ? st.optDouble("kcalEaten", 0) : 0;
            double water = (today ? st.optDouble("water", 0) : 0) + pend.optInt(day, 0), wGoal = st.optDouble("waterGoal", 0);
            double steps = Math.max(today ? st.optDouble("steps", 0) : 0, new JSONObject(StepStore.json(c)).optDouble(day, 0));
            int tTotal = today ? st.optInt("tasksTotal", 0) : 0, tLeft = today ? st.optInt("tasksLeft", 0) : tTotal;
            if (goal > 0) {
                double left = goal - eaten;
                v.setTextViewText(R.id.w_kcal, n(Math.abs(left)));
                v.setTextViewText(R.id.w_kcal_lab, left >= 0 ? "kcal left" : "kcal over");
                v.setProgressBar(R.id.w_bar, 100, (int) Math.min(100, Math.round(eaten / goal * 100)), false);
            } else {
                v.setTextViewText(R.id.w_kcal, "Open");
                v.setTextViewText(R.id.w_kcal_lab, "FitOS to start");
            }
            v.setTextViewText(R.id.w_water, "💧 " + String.format(Locale.US, "%.1f", water / 1000) + (wGoal > 0 ? " / " + String.format(Locale.US, "%.1f", wGoal / 1000) : "") + " L");
            v.setTextViewText(R.id.w_steps, "👟 " + n(steps));
            v.setTextViewText(R.id.w_tasks, tTotal > 0 ? "Tasks " + (tTotal - tLeft) + "/" + tTotal : "No tasks");
        } catch (Exception ignored) {}
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        v.setOnClickPendingIntent(R.id.w_root, PendingIntent.getActivity(c, 900, open, flags));
        Intent add = new Intent(c, FitosWidget.class).setAction(ACTION_WATER);
        v.setOnClickPendingIntent(R.id.w_add_water, PendingIntent.getBroadcast(c, 901, add, flags));
        return v;
    }
}
