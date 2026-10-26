package com.sally.nonelauncher.runtime;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** UI for importing and inspecting local Java runtime archives. */
public final class RuntimeActivity extends Activity {
    private static final int PICK_RUNTIME_ZIP = 4102;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private RuntimeManager manager;
    private LinearLayout list;
    private TextView status;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        manager = new RuntimeManager(this);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(20)); root.setBackgroundColor(Color.rgb(13, 17, 23));
        TextView title = text("Java Runtime Manager", 25, Color.WHITE); title.setTypeface(null, 1); root.addView(title);
        TextView description = text("Bundled Java 17 ARM64 runtime. It is installed automatically on first open. Use TEST JAVA to check whether a runtime can execute on this device. Minecraft launch is not wired up yet.", 14, Color.rgb(170, 183, 198));
        description.setPadding(0, dp(8), 0, dp(14)); root.addView(description);
        Button importButton = button("IMPORT ANOTHER RUNTIME ZIP");
        importButton.setOnClickListener(v -> chooseArchive()); root.addView(importButton, matchWrap());
        Button refresh = button("RESCAN RUNTIMES"); refresh.setOnClickListener(v -> refreshList()); root.addView(refresh, matchWrap());
        status = text("Runtime storage: app-private directory", 12, Color.rgb(117, 185, 255)); status.setPadding(0, dp(12), 0, dp(8)); root.addView(status);
        ScrollView scroll = new ScrollView(this); list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        Button back = button("BACK"); back.setOnClickListener(v -> finish()); root.addView(back, matchWrap());
        setContentView(root);
        if (manager.scan().isEmpty()) installBundledRuntimeInBackground();
        else refreshList();
    }

    private void installBundledRuntimeInBackground() {
        status.setText("Installing bundled Java 17 ARM64 runtime. This may take a minute…");
        worker.execute(() -> {
            try {
                RuntimeManager.RuntimeInfo info = manager.installBundledRuntime();
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    status.setText("Bundled runtime installed: " + info.name);
                    refreshList();
                });
            } catch (IOException | RuntimeException error) {
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    status.setText("Bundled runtime install failed: " + (error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()));
                    refreshList();
                });
            }
        });
    }

    private void chooseArchive() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/zip", "application/x-zip-compressed", "application/octet-stream"});
        startActivityForResult(intent, PICK_RUNTIME_ZIP);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_RUNTIME_ZIP || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        String name = "java-runtime-" + System.currentTimeMillis();
        if (data.getClipData() == null && data.getData() != null) {
            String segment = uri.getLastPathSegment(); if (segment != null && segment.contains(":")) segment = segment.substring(segment.lastIndexOf(':') + 1);
            if (segment != null && !segment.trim().isEmpty()) name = segment;
        }
        final String importName = name;
        status.setText("Importing runtime archive safely…");
        worker.execute(() -> {
            try {
                RuntimeManager.RuntimeInfo info = manager.importZip(uri, importName);
                runOnUiThread(() -> { status.setText("Imported: " + info.name); refreshList(); });
            } catch (IOException | RuntimeException error) {
                runOnUiThread(() -> status.setText("Import failed: " + (error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage())));
            }
        });
    }

    private void refreshList() {
        if (list == null) return;
        list.removeAllViews();
        List<RuntimeManager.RuntimeInfo> runtimes = manager.scan();
        if (runtimes.isEmpty()) {
            TextView empty = text("No valid runtime found. The bundled ARM64 runtime may not have installed. You can import a ZIP containing release and bin/java files.", 14, Color.LTGRAY);
            empty.setPadding(0, dp(16), 0, dp(16)); list.addView(empty);
        }
        for (RuntimeManager.RuntimeInfo runtime : runtimes) {
            LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(14), dp(12), dp(14), dp(12));
            card.setBackgroundColor(Color.rgb(25, 33, 44));
            TextView name = text(runtime.name, 17, Color.WHITE); name.setTypeface(null, 1); card.addView(name);
            card.addView(text("Java: " + runtime.version + "  •  Architecture: " + runtime.architecture, 13, Color.rgb(170, 183, 198)));
            card.addView(text("Executable: bin/java\nStored in app-private storage", 12, Color.rgb(117, 185, 255)));
            Button test = button("TEST JAVA");
            test.setOnClickListener(v -> testRuntime(runtime));
            card.addView(test, matchWrap());
            LinearLayout.LayoutParams params = matchWrap(); params.bottomMargin = dp(10); list.addView(card, params);
        }
        if (status != null && !runtimes.isEmpty()) status.setText(runtimes.size() + " runtime(s) detected · use TEST JAVA to verify execution");
    }


    private void testRuntime(RuntimeManager.RuntimeInfo runtime) {
        status.setText("Testing " + runtime.name + " with java -version…");
        worker.execute(() -> {
            RuntimeExecutor.TestResult result = new RuntimeExecutor(this).testVersion(runtime);
            runOnUiThread(() -> {
                if (isFinishing()) return;
                status.setText((result.success ? "JAVA TEST PASSED · " : "JAVA TEST FAILED · ") + runtime.name + "\n" + result.output);
            });
        });
    }

    private Button button(String label) { Button b = new Button(this); b.setText(label); return b; }
    private TextView text(String value, int size, int color) { TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); return t; }
    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(-1, -2); }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
    @Override protected void onDestroy() { super.onDestroy(); worker.shutdownNow(); }
}
