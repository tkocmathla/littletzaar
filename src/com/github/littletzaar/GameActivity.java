package com.github.littletzaar;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

// TODO: Check savedInstanceState and recreate saved activity if possible
//       (including in-progress game state)
public class GameActivity extends Activity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Log.v("GameActivity.onCreate", "Enter");
		super.onCreate(savedInstanceState);
		
		// Extract the new game options
		Bundle extras = getIntent().getExtras();
		String color = extras.getString("player_color");
		String difficulty = extras.getString("difficulty");
		String positions = extras.getString("start_positions");
		
		// Set up the board view
		setContentView(R.layout.activity_game);
		GameViewGroup view = (GameViewGroup) findViewById(R.id.game_view);
		view.setColor(color);
		view.setDifficulty(difficulty);
		view.setPositions(positions);
		
		Log.v("GameActivity.onCreate", "Exit");
	}
	
	@Override 
	protected void onPause() {
		super.onPause();
		GameViewGroup view = (GameViewGroup) findViewById(R.id.game_view);
		if (view.aiThread != null)
			view.aiThread.cancel(true);
	}
	
	@Override
	protected void onStop() {
		super.onStop();
		GameViewGroup view = (GameViewGroup) findViewById(R.id.game_view);
		if (view.aiThread != null)
			view.aiThread.cancel(true);		
	}
	
	@Override
	public void onBackPressed() {
		// Prompt the user to exit the game
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(R.string.exit_game);
		
		builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {				
			@Override
			public void onClick(DialogInterface dialog, int which) {
				// Kill the AI thread
				GameViewGroup view = (GameViewGroup) findViewById(R.id.game_view);
				if (view.aiThread != null)
					view.aiThread.cancel(true);
				
				Intent intent = new Intent(view.getContext(), MainActivity.class);
				startActivity(intent);
			}
		});
		builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {				
			@Override
			public void onClick(DialogInterface dialog, int which) {
				// No-op
			}
		});
		
		AlertDialog dialog = builder.create();
		dialog.show();
	}
}
