package io.fitos.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/** Turns the phone's cumulative step counter into steps per calendar day. */
final class StepStore {
    private StepStore() {}

    static String today() { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()); }

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("steps", Context.MODE_PRIVATE); }

    static synchronized void record(Context c, float counter) {
        SharedPreferences p = prefs(c);
        float last = p.getFloat("last", -1f);
        String day = today();
        try {
            JSONObject days = new JSONObject(p.getString("days", "{}"));
            if (last >= 0) {
                float delta = counter >= last ? counter - last : counter;   // counter resets after reboot
                if (delta > 0 && delta < 100000) days.put(day, days.optDouble(day, 0) + delta);
            }
            if (days.length() > 150) {
                List<String> keys = new ArrayList<>();
                for (Iterator<String> it = days.keys(); it.hasNext(); ) keys.add(it.next());
                Collections.sort(keys);
                for (int i = 0; i < keys.size() - 150; i++) days.remove(keys.get(i));
            }
            p.edit().putFloat("last", counter).putString("days", days.toString()).apply();
        } catch (Exception ignored) {}
    }

    static synchronized void resetBaseline(Context c) { prefs(c).edit().remove("last").apply(); }

    static String json(Context c) { return prefs(c).getString("days", "{}"); }
}
