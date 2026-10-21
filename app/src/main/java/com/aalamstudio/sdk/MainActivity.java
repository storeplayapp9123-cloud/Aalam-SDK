package com.aalamstudio.sdk;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.IOException;

public class MainActivity extends Activity {
    private static final int GOLD = Color.parseColor("#D4AF37");

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

        String[][] parts = {
                {"platform", "Android platform (android.jar)"},
                {"androidx", "AndroidX libraries"},
                {"engine", "Aalam Game Engine (source)"},
                {"ndk", "NDK (aane wala hai)"},
                {"jdk", "JDK (aane wala hai)"}
        };

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        for (String[] p : parts) {
            int n = countFiles(SdkProvider.ROOT + "/" + p[0]);
            TextView row = new TextView(this);
            row.setText((n > 0 ? "✓ " : "○ ") + p[1] + "   —   "
                    + (n > 0 ? n + " files" : "khali"));
            row.setTextColor(n > 0 ? Color.parseColor("#7CFC7C") : Color.GRAY);
            row.setTextSize(15);
            row.setPadding(0, dp(6), 0, dp(6));
            list.addView(row);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView foot = new TextView(this);
        foot.setText("Aalam Compiler is app se SDK files leta hai.");
        foot.setTextColor(Color.GRAY);
        foot.setTextSize(12);
        root.addView(foot);

        setContentView(root);
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
