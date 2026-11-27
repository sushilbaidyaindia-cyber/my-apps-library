package com.sushilbaidyaindia.myappslibrary;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.core.view.ViewCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * My Apps Library - one place to install and update all of Sushil Baidya's apps.
 * The list comes from GitHub (apps.json in the "my-apps" repository); a built-in list is the fallback.
 */
public class MainActivity extends Activity {

    private static final String OWNER = "sushilbaidyaindia-cyber";
    private static final String LIST_URL = "https://raw.githubusercontent.com/" + OWNER + "/my-apps/main/apps.json";
    private static final String UA = "MyAppsLibrary/1.0 (Android)";

    private static final int BG = Color.parseColor("#0F1720");
    private static final int CARD = Color.parseColor("#1A2633");
    private static final int TEXT = Color.parseColor("#F2F5F7");
    private static final int MUTED = Color.parseColor("#B5C2CD");
    private static final int ACCENT = Color.parseColor("#4FC3F7");

    static class AppInfo {
        String name, desc, repo, apk, pkg;
        String url;                 // direct download link found in the latest release
        long latestTime;            // when the latest APK was published (ms)
        boolean checked, failed;
        String note = "";
        boolean busy;
        TextView status;
        Button main, open;
    }

    private volatile List<AppInfo> apps = new ArrayList<>();
    private LinearLayout list;
    private TextView topStatus;

    // ------------------------------------------------------------------ built-in list

    private static AppInfo app(String name, String desc, String repo, String apk, String pkg) {
        AppInfo a = new AppInfo();
        a.name = name;
        a.desc = desc;
        a.repo = repo.contains("/") ? repo : OWNER + "/" + repo;
        a.apk = apk;
        a.pkg = pkg;
        return a;
    }

    private static List<AppInfo> defaults() {
        List<AppInfo> l = new ArrayList<>();
        l.add(app("AI Smart Tube", "Videos, live television and radio, with downloads and playlists.",
                "AI-Smart-Tube-", "AI-Smart-Tube.apk", "com.aismarttube.app"));
        l.add(app("Bangla Panjika", "Bengali almanac: date, tithi, nakshatra, festivals and important days.",
                "bangla-panjika", "Bangla-Panjika.apk", "com.sushilbaidyaindia.banglapanjika"));
        l.add(app("Smart Reader", "Reads text from pictures and documents aloud.",
                "smart-reader", "Smart-Reader.apk", "com.sushilbaidyaindia.smartreader"));
        l.add(app("Tuner and Metronome", "Pitch feedback and steady beats for practice.",
                "tuner-metronome", "tuner-metronome.apk", "com.sangeetsahayak.app"));
        l.add(app("Swaralay", "Audio workshop: play, cut, mix and record sound.",
                "swaralay", "Swaralay.apk", "com.sushil.swaralay"));
        l.add(app("My Apps Library", "This app. Install it again here to get its newest version.",
                "my-apps-library", "My-Apps-Library.apk", "com.sushilbaidyaindia.myappslibrary"));
        return l;
    }

    // ------------------------------------------------------------------ screen

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        sv.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(24), dp(16), dp(24));
        sv.addView(root, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // first thing on the screen: the creator
        TextView creator = text("Creator: Sushil Baidya", 18, ACCENT, true);
        creator.setPadding(0, 0, 0, dp(4));
        root.addView(creator);

        TextView title = text("My Apps Library", 30, TEXT, true);
        ViewCompat.setAccessibilityHeading(title, true);
        root.addView(title);

        TextView sub = text("Install and update all my apps from one place.", 16, MUTED, false);
        sub.setPadding(0, dp(4), 0, dp(12));
        root.addView(sub);

        topStatus = text("Starting…", 15, MUTED, false);
        topStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        root.addView(topStatus);

        Button refresh = button("Refresh");
        refresh.setContentDescription("Refresh the list and check for updates");
        refresh.setOnClickListener(v -> refresh());
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        rp.setMargins(0, dp(8), 0, dp(12));
        root.addView(refresh, rp);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);

        setContentView(sv);

        apps = defaults();
        render();
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        for (AppInfo a : apps) update(a);     // an install may just have finished
        if (topStatus != null && !checking && System.currentTimeMillis() - lastCheck > 2 * 60 * 1000L) refresh();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private Button button(String s) {
        Button bt = new Button(this);
        bt.setText(s);
        bt.setAllCaps(false);
        bt.setTextSize(16);
        bt.setTextColor(Color.parseColor("#06202E"));
        GradientDrawable g = new GradientDrawable();
        g.setColor(ACCENT);
        g.setCornerRadius(dp(10));
        bt.setBackground(g);
        bt.setMinHeight(dp(48));
        return bt;
    }

    private void render() {
        list.removeAllViews();
        for (final AppInfo a : apps) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(16), dp(14), dp(16), dp(16));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(CARD);
            bg.setCornerRadius(dp(14));
            card.setBackground(bg);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cp.setMargins(0, 0, 0, dp(12));

            TextView name = text(a.name, 21, TEXT, true);
            ViewCompat.setAccessibilityHeading(name, true);
            card.addView(name);

            TextView desc = text(a.desc, 15, MUTED, false);
            desc.setPadding(0, dp(2), 0, dp(6));
            card.addView(desc);

            a.status = text("Checking…", 15, TEXT, false);
            a.status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
            card.addView(a.status);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(10), 0, 0);

            a.main = button("Download and install");
            a.main.setOnClickListener(v -> download(a));
            row.addView(a.main, new LinearLayout.LayoutParams(0, dp(52), 1f));

            a.open = button("Open");
            a.open.setContentDescription("Open " + a.name);
            a.open.setVisibility(View.GONE);
            a.open.setOnClickListener(v -> openApp(a));
            LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(dp(96), dp(52));
            op.setMargins(dp(10), 0, 0, 0);
            row.addView(a.open, op);

            card.addView(row);
            list.addView(card, cp);
            update(a);
        }
    }

    // ------------------------------------------------------------------ state of each app

    private PackageInfo installed(AppInfo a) {
        try {
            return getPackageManager().getPackageInfo(a.pkg, 0);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        } catch (Throwable t) {
            return null;
        }
    }

    private String when(long ms) {
        return new SimpleDateFormat("d MMM yyyy, h:mm a", Locale.ENGLISH).format(new java.util.Date(ms));
    }

    private void update(AppInfo a) {
        if (a.status == null || a.main == null) return;
        if (a.busy) return;
        PackageInfo pi = installed(a);
        String st;
        String label;
        if (pi == null) {
            st = "Not installed" + (a.checked && !a.failed ? ". Latest build: " + when(a.latestTime) : "");
            if (a.failed && !a.note.isEmpty()) st += ". " + a.note;
            label = "Download and install";
        } else {
            String ver = pi.versionName == null ? "" : " (version " + pi.versionName + ")";
            if (!a.checked) {
                st = "Installed" + ver;
                label = "Reinstall latest";
            } else if (a.failed) {
                st = "Installed" + ver + ". " + (a.note.isEmpty() ? "Could not check for updates." : a.note);
                label = "Reinstall latest";
            } else if (a.latestTime > pi.lastUpdateTime + 60000L) {
                st = "Update available" + ver + ". Latest build: " + when(a.latestTime);
                label = "Update";
            } else {
                st = "Installed and up to date" + ver;
                label = "Reinstall latest";
            }
        }
        if (pi != null) st += "\nInstalled on this phone: " + when(pi.lastUpdateTime);
        if (a.checked && !a.failed && a.latestTime > 0) st += "\nLatest build on GitHub: " + when(a.latestTime);
        a.status.setText(st);
        a.main.setText(label);
        a.main.setEnabled(true);
        a.main.setContentDescription(label + " " + a.name);
        a.open.setVisibility(pi == null ? View.GONE : View.VISIBLE);
    }

    private void openApp(AppInfo a) {
        Intent i = getPackageManager().getLaunchIntentForPackage(a.pkg);
        if (i == null) {
            Toast.makeText(this, a.name + " cannot be opened.", Toast.LENGTH_SHORT).show();
        } else {
            startActivity(i);
        }
    }

    // ------------------------------------------------------------------ network: list and latest releases

    private volatile boolean checking = false;
    private volatile long lastCheck = 0;

    /** fallback when the GitHub API refuses (too many requests): the date of the file itself */
    private long fileTime(String link) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(link).openConnection();
        c.setRequestMethod("HEAD");
        c.setInstanceFollowRedirects(true);
        c.setConnectTimeout(12000);
        c.setReadTimeout(12000);
        c.setRequestProperty("User-Agent", UA);
        int code = c.getResponseCode();
        long t = c.getLastModified();
        c.disconnect();
        if (code != 200) throw new Exception("HTTP " + code);
        if (t <= 0) throw new Exception("no date");
        return t;
    }

    private void refresh() {
        if (checking) return;
        checking = true;
        lastCheck = System.currentTimeMillis();
        topStatus.setText("Checking for the latest versions…");
        new Thread(() -> {
            // 1. the app list (optional file on GitHub)
            try {
                String js = httpGet(LIST_URL, 8000);
                JSONArray arr = new JSONObject(js).getJSONArray("apps");
                final List<AppInfo> fresh = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    fresh.add(app(o.getString("name"), o.optString("description", ""),
                            o.getString("repo"), o.getString("apk"), o.getString("package")));
                }
                if (!fresh.isEmpty()) {
                    final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
                    runOnUiThread(() -> {
                        apps = fresh;
                        render();
                        latch.countDown();
                    });
                    latch.await(5, java.util.concurrent.TimeUnit.SECONDS);
                }
            } catch (Throwable ignored) {
                // no list online: the built-in one stays
            }
            // 2. the latest release of every app
            final List<AppInfo> snapshot = new ArrayList<>(apps);
            int ok = 0;
            for (final AppInfo a : snapshot) {
                try {
                    String js = httpGet("https://api.github.com/repos/" + a.repo + "/releases/latest", 12000);
                    JSONObject rel = new JSONObject(js);
                    JSONArray as = rel.getJSONArray("assets");
                    JSONObject pick = null;
                    for (int i = 0; i < as.length(); i++) {
                        JSONObject x = as.getJSONObject(i);
                        if (a.apk.equalsIgnoreCase(x.optString("name"))) { pick = x; break; }
                        if (pick == null && x.optString("name").toLowerCase(Locale.ROOT).endsWith(".apk")) pick = x;
                    }
                    if (pick == null) throw new Exception("No APK in the latest release.");
                    a.url = pick.getString("browser_download_url");
                    a.latestTime = parseTime(pick.optString("updated_at", rel.optString("published_at")));
                    a.failed = false;
                    a.note = "";
                    ok++;
                } catch (Throwable t) {
                    String m = String.valueOf(t.getMessage());
                    boolean fixed = false;
                    if (!m.contains("404")) {
                        try {
                            a.latestTime = fileTime("https://github.com/" + a.repo + "/releases/latest/download/" + a.apk);
                            a.url = null;
                            a.failed = false;
                            a.note = "";
                            ok++;
                            fixed = true;
                        } catch (Throwable t2) { }
                    }
                    if (!fixed) {
                        a.failed = true;
                        a.note = m.contains("404") ? "No release published yet." : "Could not check for updates (" + m + ").";
                    }
                }
                a.checked = true;
                runOnUiThread(() -> update(a));
            }
            checking = false;
            final int done = ok;
            final int total = snapshot.size();
            runOnUiThread(() -> topStatus.setText(done == total
                    ? "Up to date. Tap an app to install or update it."
                    : "Checked " + done + " of " + total + " apps. Tap an app to install or update it."));
        }).start();
    }

    private static long parseTime(String s) {
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            return f.parse(s).getTime();
        } catch (Throwable t) {
            return 0L;
        }
    }

    private static String httpGet(String u, int timeout) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(timeout);
        c.setReadTimeout(timeout);
        c.setRequestProperty("User-Agent", UA);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        int code = c.getResponseCode();
        if (code != 200) throw new Exception("HTTP " + code);
        InputStream in = c.getInputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        c.disconnect();
        return out.toString("UTF-8");
    }

    // ------------------------------------------------------------------ download and install

    private void download(final AppInfo a) {
        if (a.busy) return;
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            topStatus.setText("Please allow this app to install apps, then come back and tap again.");
            Toast.makeText(this, "Allow \"Install unknown apps\" for My Apps Library, then press Back.", Toast.LENGTH_LONG).show();
            Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()));
            startActivity(i);
            return;
        }
        a.busy = true;
        a.main.setEnabled(false);
        a.main.setText("Starting download…");
        final String link = a.url != null ? a.url
                : "https://github.com/" + a.repo + "/releases/latest/download/" + a.apk;
        new Thread(() -> {
            File dir = new File(getExternalFilesDir(null), "apk");
            if (!dir.exists()) dir.mkdirs();
            File out = new File(dir, a.apk.replaceAll("[^A-Za-z0-9._-]", "_"));
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(link).openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(30000);
                c.setInstanceFollowRedirects(true);
                c.setRequestProperty("User-Agent", UA);
                int code = c.getResponseCode();
                if (code != 200) throw new Exception(code == 404 ? "This app has no published APK yet (HTTP 404)." : "Server answered HTTP " + code);
                long total = c.getContentLengthLong();
                InputStream in = c.getInputStream();
                OutputStream os = new FileOutputStream(out);
                byte[] buf = new byte[32 * 1024];
                long got = 0;
                int last = -1, n;
                while ((n = in.read(buf)) > 0) {
                    os.write(buf, 0, n);
                    got += n;
                    final int pct = total > 0 ? (int) (got * 100 / total) : -1;
                    if (pct >= 0 && pct != last) {
                        last = pct;
                        runOnUiThread(() -> a.main.setText("Downloading " + pct + "%"));
                        if (pct % 25 == 0) {
                            runOnUiThread(() -> a.status.announceForAccessibility("Downloading " + a.name + ", " + pct + " percent"));
                        }
                    }
                }
                os.flush();
                os.close();
                in.close();
                if (out.length() < 100 * 1024) throw new Exception("The downloaded file is too small to be an app.");
                runOnUiThread(() -> {
                    a.busy = false;
                    a.status.setText("Downloaded. Confirm the installation on the next screen.");
                    a.main.setEnabled(true);
                    install(a, out);
                });
            } catch (final Throwable t) {
                out.delete();
                runOnUiThread(() -> {
                    a.busy = false;
                    a.status.setText("Download failed: " + t.getMessage());
                    a.status.announceForAccessibility("Download failed");
                    a.main.setEnabled(true);
                    a.main.setText("Try again");
                });
            } finally {
                if (c != null) c.disconnect();
            }
        }).start();
    }

    private void install(AppInfo a, File apk) {
        try {
            Uri u = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", apk);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(u, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Throwable t) {
            a.status.setText("Could not start the installer: " + t.getMessage());
            update(a);
        }
    }
}
