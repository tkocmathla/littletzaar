package com.github.littletzaar;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

public class MainActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);		
	}

	/**
	 * On-click event handler that launches the "New Game Setup" activity.
	 * 
	 * @param view
	 */
	public void startNewGameSetup(View view) {
		Intent intent = new Intent(this, NewGameSetupActivity.class);
		startActivity(intent);
	}
}
