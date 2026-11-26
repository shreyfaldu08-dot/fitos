package io.fitos.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Calendar;
import java.util.Locale;

/** Shows a reminder, but skips it when the person has already done the thing. */
public class ReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || "android.intent.action.MY_PACKAGE_REPLACED".equals(action)) {
            Reminders.reschedule(c);
            return;
        }
        String kind = intent.getStringExtra("kind");
        if (kind == null) return;
        try {
            JSONObject cfg = new JSONObject(c.getSharedPreferences("fitos", Context.MODE_PRIVATE).getString("reminders", "{}"));
            JSONObject st = new JSONObject(c.getSharedPreferences("fitos", Context.MODE_PRIVATE).getString("status", "{}"));
            boolean todayStatus = StepStore.today().equals(st.optString("date"));
            switch (kind) {
                case "workout": {
                    JSONArray names = cfg.optJSONArray("names");
                    int dow = (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7;   // Monday = 0
                    String name = names == null ? "" : names.optString(dow, "");
                    if (name.isEmpty() || (todayStatus && st.optBoolean("workout"))) return;
                    Notifs.remind(c, 21, "Time to train", "Today: " + name + ". Tap to start your workout.");
                    break;
                }
                case "meals":
                    if (todayStatus && st.optInt("meals") >= 2) return;
                    Notifs.remind(c, 22, "Log today’s meals", "A quick log keeps your calories and protein on track.");
                    break;
                case "water": {
                    int water = todayStatus ? st.optInt("water") : 0;
                    int goal = st.optInt("waterGoal", 2500);
                    if (water >= goal) return;
                    Notifs.remind(c, 23, "Time for a glass of water",
                            String.format(Locale.US, "%.1f of %.1f L so far today", water / 1000.0, goal / 1000.0));
                    break;
                }
                case "tasks": {
                    if (!todayStatus) return;
                    int total = st.optInt("tasksTotal"), left = st.optInt("tasksLeft");
                    if (total == 0 || left == 0) return;
                    Notifs.remind(c, 25, "Your checklist", left + " of " + total + " daily tasks still to do. You’ve got this!");
                    break;
                }
                case "weekly": {
                    if (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) return;
                    String sum = st.optString("weekSummary", "");
                    Notifs.remind(c, 26, "📊 Your weekly report is ready", sum.isEmpty() ? "Open FitOS to see how your week went." : sum);
                    break;
                }
                case "task": {
                    String tid = intent.getStringExtra("tid"), title = intent.getStringExtra("title"), days = intent.getStringExtra("days");
                    if (tid == null) return;
                    if (days != null && !days.isEmpty()) {
                        JSONArray d = new JSONArray(days);
                        int dow = (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7;   // Monday = 0
                        boolean today = false;
                        for (int k = 0; k < d.length(); k++) if (d.optInt(k, -1) == dow) today = true;
                        if (!today) return;
                    }
                    if (todayStatus) {
                        JSONArray done = st.optJSONArray("doneIds");
                        if (done != null) for (int k = 0; k < done.length(); k++) if (tid.equals(done.optString(k))) return;
                    }
                    Notifs.remind(c, 300 + Math.abs(tid.hashCode() % 500), "⏰ " + (title == null ? "Task" : title), "Tap to open your checklist and tick it off.");
                    break;
                }
                case "weigh":
                    Notifs.remind(c, 24, "Morning weigh-in", "Step on the scale before breakfast for a fair trend.");
                    break;
                default:
            }
        } catch (Exception ignored) {}
    }
}
