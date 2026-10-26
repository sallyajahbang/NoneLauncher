package com.sally.nonelauncher.game;
import android.app.Activity; import android.os.Bundle; import android.view.WindowManager;
public final class GameActivity extends Activity {
 @Override public void onCreate(Bundle b){super.onCreate(b); getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN); setContentView(new GameView(this));}
}
