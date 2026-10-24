package com.cleantv.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;
import java.util.*;

public class MainActivity extends Activity {
    static int lastRow = 1, lastCol = 0;
    static class App { ComponentName cn; String pkg; CharSequence label; Drawable img; boolean banner; }

    final List<List<View>> rows = new ArrayList<>();
    List<View> dockRow = new ArrayList<>();
    PackageManager pm; float d; int tileW, tileH, pad;
    ScrollView scroll; FrameLayout stage; View glass; TextView dockName, smallName, greeting;
    boolean glassPlaced;

    int dp(float v) { return Math.round(v * d); }
    FrameLayout.LayoutParams lp(int w, int h, int x, int y) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(w, h); p.leftMargin = x; p.topMargin = y; return p;
    }
    GradientDrawable shape(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(radiusDp)); return g;
    }
    TextView text(float sp, int color) {
        TextView t = new TextView(this); t.setTextSize(sp); t.setTextColor(color);
        t.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL)); return t;
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        pm = getPackageManager(); d = getResources().getDisplayMetrics().density;
        int cols = getResources().getInteger(R.integer.columns);
        int sw = getResources().getDisplayMetrics().widthPixels;
        int margin = dp(48), gap = dp(20);
        tileW = (sw - 2 * margin - (cols + 1) * gap) / cols; tileH = tileW * 9 / 16; pad = dp(9);

        List<App> all = loadApps(), dock = new ArrayList<>();
        for (String p : getResources().getStringArray(R.array.dock_apps)) {
            for (Iterator<App> it = all.iterator(); it.hasNext(); ) {
                App a = it.next();
                if (a.pkg.equals(p)) { if (dock.size() < cols) { dock.add(a); it.remove(); } break; }
            }
        }
        Collections.sort(all, (x, y) -> x.label.toString().compareToIgnoreCase(y.label.toString()));

        // top bar: greeting, clock, date (left); pills (right)
        FrameLayout top = new FrameLayout(this); top.setBackgroundColor(Color.BLACK);
        LinearLayout left = new LinearLayout(this); left.setOrientation(LinearLayout.VERTICAL);
        greeting = text(14, 0xAAFFFFFF);
        TextClock clock = new TextClock(this); clock.setFormat24Hour("HH:mm"); clock.setFormat12Hour("HH:mm");
        clock.setTextSize(40); clock.setTextColor(Color.WHITE); clock.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        TextClock date = new TextClock(this); date.setFormat24Hour("EEEE, d MMMM"); date.setFormat12Hour("EEEE, d MMMM");
        date.setTextSize(14); date.setTextColor(0xAAFFFFFF);
        left.addView(greeting); left.addView(clock); left.addView(date);
        FrameLayout.LayoutParams ll = new FrameLayout.LayoutParams(-2, -2); ll.leftMargin = margin; ll.topMargin = dp(10);
        top.addView(left, ll);
        LinearLayout pills = new LinearLayout(this);
        TextView pInputs = pill("Inputs", 0, this::showInputs);
        TextView pWifi = pill(wifiName(), 1, () -> startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        TextView pSet = pill("Settings", 2, () -> startActivity(new Intent(Settings.ACTION_SETTINGS)));
        for (TextView p : new TextView[]{pInputs, pWifi, pSet}) {
            LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(-2, -2); pl.leftMargin = dp(12); pills.addView(p, pl);
        }
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.TOP);
        pp.rightMargin = margin; pp.topMargin = dp(28); top.addView(pills, pp);
        rows.add(Arrays.<View>asList(pInputs, pWifi, pSet));

        // stage: dock band + dock tiles + grid
        stage = new FrameLayout(this); stage.setClipChildren(false);
        int bandTop = dp(240), bandPad = dp(14), bandH = tileH + 2 * bandPad;
        View band = new View(this); band.setBackground(shape(0xFF1C1C1E, 26));
        stage.addView(band, lp(cols * tileW + (cols + 1) * gap, bandH, margin, bandTop));
        for (int i = 0; i < dock.size(); i++)
            dockRow.add(addTile(dock.get(i), margin + gap + i * (tileW + gap), bandTop + bandPad));
        if (!dockRow.isEmpty()) rows.add(dockRow);
        int gridTop = bandTop + bandH + dp(36), rowStep = tileH + dp(46);
        for (int i = 0; i < all.size(); i++) {
            int r = i / cols, c = i % cols;
            View t = addTile(all.get(i), margin + gap + c * (tileW + gap), gridTop + r * rowStep);
            if (c == 0) rows.add(new ArrayList<View>());
            rows.get(rows.size() - 1).add(t);
        }
        dockName = text(40, Color.WHITE); dockName.setVisibility(View.INVISIBLE);
        stage.addView(dockName, lp(-2, dp(60), margin + gap, dp(165)));
        smallName = text(16, Color.WHITE); smallName.setGravity(Gravity.CENTER); smallName.setSingleLine(true);
        smallName.setVisibility(View.INVISIBLE); stage.addView(smallName, lp(tileW, -2, 0, 0));
        glass = new View(this); GradientDrawable gd = shape(0x33FFFFFF, 16); gd.setStroke(dp(2), 0x88FFFFFF);
        glass.setBackground(gd); glass.setAlpha(0f); stage.addView(glass, lp(tileW + 2 * pad, tileH + 2 * pad, 0, 0));

        int stageH = gridTop + ((all.size() + cols - 1) / cols) * rowStep + dp(100);
        scroll = new ScrollView(this); scroll.setVerticalScrollBarEnabled(false); scroll.setFocusable(false);
        scroll.addView(stage, new ViewGroup.LayoutParams(-1, stageH));
        FrameLayout root = new FrameLayout(this); root.setBackgroundColor(Color.BLACK);
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        root.addView(top, new FrameLayout.LayoutParams(-1, dp(112)));
        setContentView(root);
        root.post(() -> focusAt(lastRow, lastCol));
    }

    View addTile(final App a, int x, int y) {
        ImageView v = new ImageView(this);
        v.setImageDrawable(a.img); v.setBackgroundColor(0xFF2C2C2E);
        v.setScaleType(a.banner ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.CENTER_INSIDE);
        if (!a.banner) v.setPadding(dp(14), dp(14), dp(14), dp(14));
        final int rad = dp(14);
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline o) { o.setRoundRect(0, 0, view.getWidth(), view.getHeight(), rad); }
        });
        v.setClipToOutline(true); v.setFocusable(true); v.setClickable(true);
        v.setOnClickListener(vw -> launch(a));
        v.setOnFocusChangeListener((vw, f) -> onTileFocus(vw, f, a));
        stage.addView(v, lp(tileW, tileH, x, y));
        return v;
    }

    void onTileFocus(View v, boolean f, App a) {
        if (!f) { v.animate().scaleX(1f).scaleY(1f).setDuration(120).start(); return; }
        for (int r = 0; r < rows.size(); r++) { int c = rows.get(r).indexOf(v); if (c >= 0) { lastRow = r; lastCol = c; } }
        v.animate().scaleX(1.08f).scaleY(1.08f).setDuration(160).start();
        float gx = v.getX() - pad, gy = v.getY() - pad;
        if (!glassPlaced) { glass.setX(gx); glass.setY(gy); glassPlaced = true; glass.animate().alpha(1f).setDuration(150).start(); }
        else glass.animate().x(gx).y(gy).alpha(1f).setDuration(260).setInterpolator(new OvershootInterpolator(1.2f)).start();
        if (dockRow.contains(v)) {
            dockName.setText(a.label); dockName.setVisibility(View.VISIBLE); smallName.setVisibility(View.INVISIBLE);
            scroll.smoothScrollTo(0, 0);
        } else {
            dockName.setVisibility(View.INVISIBLE); smallName.setText(a.label);
            smallName.setX(v.getX()); smallName.setY(v.getY() + tileH + dp(10)); smallName.setVisibility(View.VISIBLE);
            scroll.smoothScrollTo(0, Math.max(0, (int) v.getY() - dp(150)));
        }
    }

    TextView pill(String t, final int idx, final Runnable act) {
        final TextView p = text(15, Color.WHITE); p.setText(t); p.setGravity(Gravity.CENTER);
        p.setPadding(dp(18), dp(8), dp(18), dp(8)); p.setFocusable(true); p.setClickable(true);
        p.setBackground(shape(0x26FFFFFF, 40));
        p.setOnClickListener(v -> act.run());
        p.setOnFocusChangeListener((v, f) -> {
            p.setBackground(shape(f ? 0x66FFFFFF : 0x26FFFFFF, 40));
            p.animate().scaleX(f ? 1.06f : 1f).scaleY(f ? 1.06f : 1f).setDuration(120).start();
            if (f) {
                lastRow = 0; lastCol = idx; glass.animate().alpha(0f).setDuration(100).start();
                dockName.setVisibility(View.INVISIBLE); smallName.setVisibility(View.INVISIBLE); scroll.smoothScrollTo(0, 0);
            }
        });
        return p;
    }

    // Deterministic D-pad: one row per Up/Down, ends of rows clamp, no fall-through.
    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        if (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT
                || k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                int r = lastRow, c = lastCol;
                if (k == KeyEvent.KEYCODE_DPAD_LEFT) c = Math.max(0, c - 1);
                else if (k == KeyEvent.KEYCODE_DPAD_RIGHT) c = c + 1;
                else if (k == KeyEvent.KEYCODE_DPAD_UP) r = Math.max(0, r - 1);
                else r = r + 1;
                focusAt(r, c);
            }
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    void focusAt(int r, int c) {
        r = Math.max(0, Math.min(r, rows.size() - 1));
        c = Math.max(0, Math.min(c, rows.get(r).size() - 1));
        rows.get(r).get(c).requestFocus();
    }

    @Override protected void onNewIntent(Intent i) {   // Home pressed while we are showing
        super.onNewIntent(i); focusAt(rows.size() > 1 ? 1 : 0, 0); scroll.smoothScrollTo(0, 0);
    }
    @Override protected void onResume() {
        super.onResume();
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        greeting.setText(h < 12 ? "Good morning" : h < 18 ? "Good afternoon" : "Good evening");
    }
    @Override public void onBackPressed() { }

    List<App> loadApps() {
        List<App> out = new ArrayList<>(); Set<String> seen = new HashSet<>();
        for (String c : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            for (ResolveInfo ri : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(c), 0)) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
                App a = new App(); a.pkg = pkg; a.cn = new ComponentName(pkg, ri.activityInfo.name);
                a.label = ri.loadLabel(pm);
                Drawable bn = ri.activityInfo.loadBanner(pm);
                a.banner = bn != null; a.img = bn != null ? bn : ri.activityInfo.loadIcon(pm);
                out.add(a);
            }
        }
        return out;
    }

    void launch(App a) {
        try {
            startActivity(new Intent(Intent.ACTION_MAIN).setComponent(a.cn)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
        } catch (Exception e) { Toast.makeText(this, "Can't open " + a.label, Toast.LENGTH_SHORT).show(); }
    }

    String wifiName() {
        try {
            String s = ((WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE)).getConnectionInfo().getSSID();
            if (s != null && !s.contains("unknown")) return s.replace("\"", "");
        } catch (Exception ignored) { }
        return "Wi-Fi";
    }

    void showInputs() {
        final List<String> names = new ArrayList<>(); final List<Intent> intents = new ArrayList<>();
        TvInputManager tm = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
        if (tm != null) for (TvInputInfo i : tm.getTvInputList()) {
            if (i.isPassthroughInput()) {
                names.add(i.loadLabel(this).toString());
                intents.add(new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(i.getId())));
            }
        }
        Intent live = pm.getLaunchIntentForPackage(getString(R.string.live_tv_package));
        if (live != null) { names.add("Live TV"); intents.add(live); }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog)
                .setItems(names.toArray(new String[0]), (dlg, w) -> {
                    try { startActivity(intents.get(w)); }
                    catch (Exception e) { Toast.makeText(this, "Can't open input", Toast.LENGTH_SHORT).show(); }
                }).show();
    }
}
