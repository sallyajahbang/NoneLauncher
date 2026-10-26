package com.sally.nonelauncher;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.*;

/** NoneLauncher launcher UI. No Pojav UI/classes are used here. */
public final class LauncherActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setGravity(Gravity.CENTER);
        root.setPadding(48, 48, 48, 48); root.setBackgroundColor(Color.rgb(20,20,20));
        TextView title = new TextView(this); title.setText("NoneLauncher"); title.setTextColor(Color.WHITE); title.setTextSize(28); title.setGravity(Gravity.CENTER);
        TextView sub = new TextView(this); sub.setText("Standalone Android launcher foundation"); sub.setTextColor(Color.LTGRAY); sub.setGravity(Gravity.CENTER); sub.setPadding(0,12,0,32);
        Button runtime = new Button(this); runtime.setText("JAVA RUNTIME MANAGER");
        runtime.setOnClickListener(v -> startActivity(new Intent(this, com.sally.nonelauncher.runtime.RuntimeActivity.class)));
        Button play = new Button(this); play.setText("OPEN INPUT DIAGNOSTICS"); play.setOnClickListener(v -> startActivity(new Intent(this, com.sally.nonelauncher.game.GameActivity.class)));
        root.addView(title, new LinearLayout.LayoutParams(-1, -2)); root.addView(sub, new LinearLayout.LayoutParams(-1,-2));
        root.addView(runtime, new LinearLayout.LayoutParams(-1, -2));
        root.addView(play, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
    }
}
