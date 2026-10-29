package com.aalamstudio.sdk;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.IOException;

public class MainActivity extends Activity {
    private static final int GOLD = Color.parseColor("#D4AF37");
    private static final int GREEN = Color.parseColor("#7CFC7C");

    private LinearLayout list;
    private TextView progress;
    private volatile boolean downloading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(dp(20), dp(12), dp(20), dp(12));

        TextView title = new TextView(this);
        title.setText("AALAM SDK");
        title.setTextColor(GOLD);
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("SDK  •  NDK  •  JDK");
        sub.setTextColor(Color.LTGRAY);
        sub.setPadding(0, 0, 0, dp(12));
        root.addView(sub);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        progress = new TextView(this);
        progress.setTextColor(Color.LTGRAY);
        progress.setTextSize(13);
        progress.setPadding(0, dp(6), 0, dp(6));
        root.addView(progress);

        TextView foot = new TextView(this);
        foot.setText("Aalam Compiler is app se SDK files leta hai.");
        foot.setTextColor(Color.GRAY);
        foot.setTextSize(12);
        root.addView(foot);

        setContentView(root);
        render();
    }

    /** List dobara banao (download ke baad ya status badalne par). */
    private void render() {
        list.removeAllViews();

        // ---- Android platform (android.jar) ----
        boolean fromGoogle = GooglePlatform.isInstalled(this);
        int bundled = countFiles(SdkProvider.ROOT + "/platform");
        if (fromGoogle) {
            addRow("✓ Android platform (android.jar)   —   Google se download hua (" + GooglePlatform.LABEL + ")", true);
        } else if (bundled > 0) {
            addRow("✓ Android platform (android.jar)   —   " + bundled + " files (app ke saath)", true);
        } else {
            addRow("○ Android platform (android.jar)   —   khali", false);
        }
        if (!fromGoogle) {
            Button b = new Button(this);
            b.setText(downloading ? "Download chal raha hai..." : "Google se download karo (" + GooglePlatform.LABEL + ")");
            b.setEnabled(!downloading);
            b.setTextColor(Color.BLACK);
            b.setBackgroundColor(GOLD);
            b.setAlpha(downloading ? 0.4f : 1f);
            b.setOnClickListener(v -> askLicense());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(8);
            list.addView(b, lp);
        }

        // ---- baaki components ----
        String[][] parts = {
                {"androidx", "AndroidX libraries"},
                {"engine", "Aalam Game Engine (source)"},
                {"ndk", "NDK (aane wala hai)"},
                {"jdk", "JDK (aane wala hai)"}
        };
        for (String[] p : parts) {
            int n = countFiles(SdkProvider.ROOT + "/" + p[0]);
            addRow((n > 0 ? "✓ " : "○ ") + p[1] + "   —   " + (n > 0 ? n + " files" : "khali"), n > 0);
        }
    }

    private void addRow(String text, boolean ok) {
        TextView row = new TextView(this);
        row.setText(text);
        row.setTextColor(ok ? GREEN : Color.GRAY);
        row.setTextSize(15);
        row.setPadding(0, dp(6), 0, dp(6));
        list.addView(row);
    }

    private void askLicense() {
        if (downloading) return;
        new AlertDialog.Builder(this)
                .setTitle("Android SDK License")
                .setMessage("Ye file Google ke official server se download hogi (lagbhag 63 MB, WiFi par karo).\n\n"
                        + "Download karne ka matlab hai ki tum Android Software Development Kit License "
                        + "Agreement maante ho:\nhttps://developer.android.com/studio/terms")
                .setPositiveButton("Maan leta hoon, download karo", (d, w) -> startDownload())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startDownload() {
        downloading = true;
        render();
        new Thread(() -> {
            try {
                GooglePlatform.install(this, (msg, pct) -> runOnUiThread(() ->
                        progress.setText(pct >= 0 ? msg + "  " + pct + "%" : msg)));
                runOnUiThread(() -> progress.setText("✓ android.jar install ho gaya (" + GooglePlatform.LABEL + ")"));
            } catch (Exception e) {
                final String why = String.valueOf(e.getMessage());
                runOnUiThread(() -> progress.setText("✗ Download fail: " + why));
            } finally {
                downloading = false;
                runOnUiThread(this::render);
            }
        }).start();
    }

    private int countFiles(String assetPath) {
        try {
            String[] kids = getAssets().list(assetPath);
            if (kids == null) return 0;
            int n = 0;
            for (String k : kids) {
                if (k.startsWith(".")) continue;
                String full = assetPath + "/" + k;
                String[] sub = getAssets().list(full);
                if (sub != null && sub.length > 0) n += countFiles(full);
                else n++;
            }
            return n;
        } catch (IOException e) {
            return 0;
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
