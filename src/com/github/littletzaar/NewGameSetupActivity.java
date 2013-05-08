package com.github.littletzaar;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Spinner;

public class NewGameSetupActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_new_game_setup);

		// Set the default selection for the player color spinner
		Spinner cs = (Spinner) findViewById(R.id.spinner_color);
		cs.setSelection(0); // 0 = white
		
		// Set the default selection for the difficulty spinner
		Spinner ds = (Spinner) findViewById(R.id.spinner_difficulty);
		ds.setSelection(1); // 1 = easy
		
		// Set the default selection for the start positions spinner
		Spinner ss = (Spinner) findViewById(R.id.spinner_start_positions);
		ss.setSelection(1); // 1 = fixed 
	}
	
	/**
	 * On-click event handler that launches the "Game" activity.
	 * 
	 * @param view
	 */
	public void startGame(View view) {
		Intent intent = new Intent(this, GameActivity.class);
		
		// Get the values for each game option and store in the intent extras
		Spinner cs = (Spinner) findViewById(R.id.spinner_color);
		Spinner ds = (Spinner) findViewById(R.id.spinner_difficulty);
		Spinner ss = (Spinner) findViewById(R.id.spinner_start_positions);
		
		intent.putExtra("player_color", cs.getSelectedItem().toString());
		intent.putExtra("difficulty", ds.getSelectedItem().toString());
		intent.putExtra("start_positions", ss.getSelectedItem().toString());
		
		startActivity(intent);
	}
	
}
