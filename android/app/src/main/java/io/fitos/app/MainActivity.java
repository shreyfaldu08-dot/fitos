package io.fitos.app;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import org.json.JSONObject;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.provider.MediaStore;
import java.io.File;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Hosts FitOS (assets/index.html) and exposes phone features to it through
 * window.FitOSNative: step counter, GPS, reminders and backup files.
 */
public class MainActivity extends Activity implements SensorEventListener {
    private static final int REQ_STEPS = 11, REQ_LOC = 12, REQ_NOTIF = 13, REQ_SAVE = 21, REQ_OPEN = 22, REQ_FILE = 23, REQ_CAM = 14;
    private static final String PERM_ACTIVITY = "android.permission.ACTIVITY_RECOGNITION";
    private static final String PERM_FINE = "android.permission.ACCESS_FINE_LOCATION";
    private static final String PERM_COARSE = "android.permission.ACCESS_COARSE_LOCATION";
    private static final String PERM_NOTIF = "android.permission.POST_NOTIFICATIONS";

    private WebView web;
    private SensorManager sensors;
    private Sensor stepSensor;
    private boolean stepsWanted = false, stepsBackground = false, stepsRegistered = false;
    private String pendingGpsType = "Walk";
    private String pendingBackup = null;
    private boolean pendingIsBackup = true;
    private ValueCallback<Uri[]> fileCallback;
    private PermissionRequest pendingCam;
    private Uri captureUri = null;
    private boolean captureWaitingForPermission = false;
    private FileChooserParamsHolder pendingChooser = null;
    private static class FileChooserParamsHolder { WebChromeClient.FileChooserParams params; FileChooserParamsHolder(WebChromeClient.FileChooserParams p) { params = p; } }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        boolean night = (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        getWindow().setStatusBarColor(Color.parseColor(night ? "#1D57A8" : "#3A86E8"));
        Notifs.channels(this);

        sensors = (SensorManager) getSystemService(SENSOR_SERVICE);
        stepSensor = sensors != null ? sensors.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) : null;

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setTextZoom(100);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest req) {
                Uri u = req.getUrl();
                String scheme = u.getScheme() == null ? "" : u.getScheme();
                if (scheme.equals("http") || scheme.equals("https")) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) {}
                    return true;
                }
                return false;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(new Runnable() { @Override public void run() {
                    boolean wantsCam = false;
                    for (String r : request.getResources()) if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)) wantsCam = true;
                    if (!wantsCam) { request.deny(); return; }
                    if (Build.VERSION.SDK_INT < 23 || checkSelfPermission(android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        request.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
                    } else {
                        pendingCam = request;
                        requestPermissions(new String[]{android.Manifest.permission.CAMERA}, REQ_CAM);
                    }
                }});
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                if (params.isCaptureEnabled() && Build.VERSION.SDK_INT >= 29) {
                    if (checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        pendingChooser = new FileChooserParamsHolder(params);
                        captureWaitingForPermission = true;
                        requestPermissions(new String[]{android.Manifest.permission.CAMERA}, REQ_CAM);
                        return true;
                    }
                    if (launchCapture()) return true;
                }
                try {
                    startActivityForResult(params.createIntent(), REQ_FILE);
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });
        web.setBackgroundColor(night ? Color.parseColor("#0E1116") : Color.WHITE);
        web.addJavascriptInterface(new Bridge(), "FitOSNative");
        setContentView(web);

        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl("file:///android_asset/index.html#home");
    }

    private void js(final String code) {
        web.post(new Runnable() { @Override public void run() { web.evaluateJavascript(code, null); } });
    }

    private boolean granted(String p) { return checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED; }

    // ── steps ────────────────────────────────────────────────────────────
    private boolean hasStepPermission() { return Build.VERSION.SDK_INT < 29 || granted(PERM_ACTIVITY); }

    private void beginSteps() {
        if (stepSensor == null || !hasStepPermission()) return;
        if (!stepsRegistered) {
            sensors.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_UI);
            stepsRegistered = true;
        }
        if (stepsBackground && !StepService.running) startSvc(new Intent(this, StepService.class));
    }

    private void startSvc(Intent i) {
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        } catch (Exception ignored) {}
    }

    private void unregisterSteps() {
        if (stepsRegistered) { sensors.unregisterListener(this); stepsRegistered = false; }
    }

    @Override public void onSensorChanged(SensorEvent e) {
        StepStore.record(this, e.values[0]);
        js("window.onNativeSteps&&window.onNativeSteps()");
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    // ── permissions & files ─────────────────────────────────────────────
    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (code == REQ_CAM && captureWaitingForPermission) {
            captureWaitingForPermission = false;
            boolean ok = results.length > 0 && results[0] == android.content.pm.PackageManager.PERMISSION_GRANTED;
            if (!(ok && launchCapture())) {
                try { if (pendingChooser != null) startActivityForResult(pendingChooser.params.createIntent(), REQ_FILE); else throw new Exception(); }
                catch (Exception e) { if (fileCallback != null) fileCallback.onReceiveValue(null); fileCallback = null; }
            }
            pendingChooser = null;
            return;
        }
        if (code == REQ_CAM) {
            boolean ok = results.length > 0 && results[0] == android.content.pm.PackageManager.PERMISSION_GRANTED;
            if (pendingCam != null) { if (ok) pendingCam.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE}); else pendingCam.deny(); pendingCam = null; }
            return;
        }
        boolean ok = false;
        for (int r : results) if (r == PackageManager.PERMISSION_GRANTED) ok = true;
        if (code == REQ_STEPS) {
            if (ok) {
                beginSteps();
                if (stepsBackground && Build.VERSION.SDK_INT >= 33 && !granted(PERM_NOTIF)) requestPermissions(new String[]{PERM_NOTIF}, REQ_NOTIF);
            }
            js("window.onNativePermission&&window.onNativePermission('steps'," + ok + ")");
        } else if (code == REQ_LOC) {
            if (ok) startGps(); else GpsService.error = "Location permission is needed for GPS";
            js("window.onNativePermission&&window.onNativePermission('loc'," + ok + ")");
        }
    }

    private void startGps() {
        startSvc(new Intent(this, GpsService.class).putExtra("type", pendingGpsType));
    }

    /** Opens the phone's camera app to take a full-size photo (Android 10+). */
    private boolean launchCapture() {
        try {
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, "fitos-barcode-" + System.currentTimeMillis() + ".jpg");
            v.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/FitOS");
            captureUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            if (captureUri == null) return false;
            Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT, captureUri)
                    .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(i, REQ_FILE);
            return true;
        } catch (Exception e) {
            if (captureUri != null) { try { getContentResolver().delete(captureUri, null, null); } catch (Exception ignored) {} }
            captureUri = null;
            return false;
        }
    }

    @Override
    protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code == REQ_FILE) {
            if (captureUri != null) {
                final Uri shot = captureUri;
                captureUri = null;
                if (fileCallback != null) fileCallback.onReceiveValue(result == RESULT_OK ? new Uri[]{shot} : null);
                fileCallback = null;
                // the photo is only needed for reading the barcode: remove it from the gallery shortly afterwards
                new android.os.Handler(getMainLooper()).postDelayed(new Runnable() { @Override public void run() {
                    try { getContentResolver().delete(shot, null, null); } catch (Exception ignored) {}
                }}, result == RESULT_OK ? 30000 : 0);
                return;
            }
            if (fileCallback != null) fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result, data));
            fileCallback = null;
            return;
        }
        Uri uri = (result == RESULT_OK && data != null) ? data.getData() : null;
        if (code == REQ_SAVE) {
            boolean ok = false;
            if (uri != null && pendingBackup != null) {
                try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
                    out.write(pendingBackup.getBytes(StandardCharsets.UTF_8));
                    ok = true;
                } catch (Exception ignored) {}
            }
            pendingBackup = null;
            if (uri != null) js(pendingIsBackup ? "window.onNativeSaved&&window.onNativeSaved(" + ok + ")" : "window.onNativeFileSaved&&window.onNativeFileSaved(" + ok + ")");
            pendingIsBackup = true;
        } else if (code == REQ_OPEN && uri != null) {
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] chunk = new byte[16384];
                int n;
                while ((n = in.read(chunk)) > 0) {
                    buf.write(chunk, 0, n);
                    if (buf.size() > 30 * 1024 * 1024) break;
                }
                String text = new String(buf.toByteArray(), StandardCharsets.UTF_8);
                js("window.onNativeImport&&window.onNativeImport(" + JSONObject.quote(text) + ")");
            } catch (Exception e) {
                js("window.onNativeImport&&window.onNativeImport('')");
            }
        }
    }

    /** Methods the web page can call. They run on a background thread. */
    private class Bridge {
        @JavascriptInterface public boolean hasStepSensor() { return stepSensor != null; }

        @JavascriptInterface public void startSteps(final boolean background) {
            runOnUiThread(new Runnable() { @Override public void run() {
                stepsWanted = true;
                stepsBackground = background;
                if (hasStepPermission()) {
                    beginSteps();
                    if (background && Build.VERSION.SDK_INT >= 33 && !granted(PERM_NOTIF)) requestPermissions(new String[]{PERM_NOTIF}, REQ_NOTIF);
                } else requestPermissions(new String[]{PERM_ACTIVITY}, REQ_STEPS);
            }});
        }

        @JavascriptInterface public void stopBackground() {
            runOnUiThread(new Runnable() { @Override public void run() {
                stepsBackground = false;
                stopService(new Intent(MainActivity.this, StepService.class));
            }});
        }

        @JavascriptInterface public void stopSteps() {
            runOnUiThread(new Runnable() { @Override public void run() {
                stepsWanted = false;
                stepsBackground = false;
                unregisterSteps();
                stopService(new Intent(MainActivity.this, StepService.class));
            }});
        }

        @JavascriptInterface public void resetSteps() { StepStore.resetBaseline(MainActivity.this); }

        @JavascriptInterface public String stepDays() { return StepStore.json(MainActivity.this); }

        @JavascriptInterface public void gpsStart(final String type) {
            runOnUiThread(new Runnable() { @Override public void run() {
                pendingGpsType = type == null ? "Walk" : type;
                GpsService.error = "";
                if (granted(PERM_FINE) || granted(PERM_COARSE)) {
                    startGps();
                    if (Build.VERSION.SDK_INT >= 33 && !granted(PERM_NOTIF)) requestPermissions(new String[]{PERM_NOTIF}, REQ_NOTIF);
                } else requestPermissions(new String[]{PERM_FINE, PERM_COARSE}, REQ_LOC);
            }});
        }

        @JavascriptInterface public void gpsStop() {
            runOnUiThread(new Runnable() { @Override public void run() {
                stopService(new Intent(MainActivity.this, GpsService.class));
            }});
        }

        @JavascriptInterface public String gpsState() {
            try {
                JSONObject o = new JSONObject();
                o.put("on", GpsService.on);
                o.put("d", GpsService.distance);
                o.put("a", GpsService.accuracy);
                o.put("err", GpsService.error);
                o.put("type", GpsService.type);
                o.put("start", GpsService.start);
                return o.toString();
            } catch (Exception e) { return "{}"; }
        }

        @JavascriptInterface public void scheduleReminders(String json) { Reminders.schedule(MainActivity.this, json); }

        @JavascriptInterface public void requestNotifications() {
            runOnUiThread(new Runnable() { @Override public void run() {
                if (Build.VERSION.SDK_INT >= 33 && !granted(PERM_NOTIF)) requestPermissions(new String[]{PERM_NOTIF}, REQ_NOTIF);
            }});
        }

        @JavascriptInterface public void syncStatus(String json) {
            getSharedPreferences("fitos", MODE_PRIVATE).edit().putString("status", json).apply();
            FitosWidget.updateAll(MainActivity.this);
        }

        @JavascriptInterface public void saveBackup(final String name, final String json) {
            runOnUiThread(new Runnable() { @Override public void run() {
                pendingBackup = json; pendingIsBackup = true;
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("application/json").putExtra(Intent.EXTRA_TITLE, name);
                try { startActivityForResult(i, REQ_SAVE); } catch (Exception e) { js("window.onNativeSaved&&window.onNativeSaved(false)"); }
            }});
        }

        @JavascriptInterface public void saveFile(final String name, final String mime, final String content) {
            runOnUiThread(new Runnable() { @Override public void run() {
                pendingBackup = content; pendingIsBackup = false;
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                        .setType(mime).putExtra(Intent.EXTRA_TITLE, name);
                try { startActivityForResult(i, REQ_SAVE); } catch (Exception e) { pendingIsBackup = true; js("window.onNativeFileSaved&&window.onNativeFileSaved(false)"); }
            }});
        }

        @JavascriptInterface public void openAppSettings() {
            runOnUiThread(new Runnable() { @Override public void run() {
                try { startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))); } catch (Exception ignored) {}
            }});
        }

        /** Water added from the home-screen widget while the app was closed; cleared once read. */
        @JavascriptInterface public String takePendingWater() {
            android.content.SharedPreferences p = getSharedPreferences("fitos", MODE_PRIVATE);
            String s = p.getString("pendingWater", "{}");
            p.edit().remove("pendingWater").apply();
            return s;
        }

        /** Daily automatic backup: Downloads/FitOS/fitos-backup-auto.json (kept after uninstall on Android 10+). */
        @JavascriptInterface public boolean autoBackup(String json) {
            byte[] data = json.getBytes(StandardCharsets.UTF_8);
            try {
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentResolver cr = getContentResolver();
                    Uri coll = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
                    String name = "fitos-backup-auto.json";
                    Uri uri = null;
                    Cursor c = cr.query(coll, new String[]{MediaStore.MediaColumns._ID},
                            MediaStore.MediaColumns.DISPLAY_NAME + "=? AND " + MediaStore.MediaColumns.RELATIVE_PATH + "=?",
                            new String[]{name, "Download/FitOS/"}, null);
                    if (c != null) {
                        if (c.moveToFirst()) uri = ContentUris.withAppendedId(coll, c.getLong(0));
                        c.close();
                    }
                    if (uri == null) {
                        ContentValues v = new ContentValues();
                        v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                        v.put(MediaStore.MediaColumns.MIME_TYPE, "application/json");
                        v.put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/FitOS");
                        uri = cr.insert(coll, v);
                    }
                    if (uri == null) return false;
                    try (OutputStream out = cr.openOutputStream(uri, "wt")) { out.write(data); }
                } else {
                    File dir = getExternalFilesDir(null);
                    if (dir == null) return false;
                    try (FileOutputStream out = new FileOutputStream(new File(dir, "fitos-backup-auto.json"))) { out.write(data); }
                }
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface public void openBackup() {
            runOnUiThread(new Runnable() { @Override public void run() {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
                try { startActivityForResult(i, REQ_OPEN); } catch (Exception ignored) {}
            }});
        }
    }

    // ── lifecycle ────────────────────────────────────────────────────────
    @Override protected void onResume() {
        super.onResume();
        if (stepsWanted) beginSteps();
        js("window.onNativeSteps&&window.onNativeSteps()");
    }

    @Override protected void onPause() {
        super.onPause();
        unregisterSteps();   // the background service (if on) keeps counting
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}
