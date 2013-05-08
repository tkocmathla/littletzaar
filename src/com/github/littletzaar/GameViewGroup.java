package com.github.littletzaar;

import java.util.ArrayList;
import java.util.EmptyStackException;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;

import com.github.littletzaar.util.PieceId;

/**
 * This class implements a custom view group (or layout) for drawing the game.
 * 
 * Method call hierarchy for drawing:
 *   - onMeasure
 *   - onSizeChanged
 *   - onLayout
 *   - onMeasure
 *   - onLayout
 *   - dispatchDraw
 *   
 * @author mgrimm
 */
public class GameViewGroup extends ViewGroup {
	/**
	 * This task allows the AI to search for a move in a separate thread.
	 */
	public class MoveSearchTask extends AsyncTask<TzaarGame, Void, Move> {
		@Override
		protected Move doInBackground(TzaarGame... tzaar) {
			if (mAiRunning == false) { 
				throw new RuntimeException("You must flag the AI as running (mAiRunning = true) before dispatching it.");
			}
			
			Move move = null;
			if (tzaar != null && tzaar.length > 0) {
				move = tzaar[0].findMove(mTzaar.whoseTurn(), mTzaar.getMoveNumber());
			}
			
			return move;
		}
		
		@Override
		protected void onPostExecute(Move nextMove) {
			if (nextMove == null) {
				throw new RuntimeException("AI failed to find next move!");
			}
			
			Log.i("MoveSearchTask.onPostExecute", String.format("AI moving from (%d,%d) to (%d,%d)",
					nextMove.from.x, nextMove.from.y, nextMove.to.x, nextMove.to.y));
			
			// Swap the piece image IDs
			mPieceId[nextMove.to.x][nextMove.to.y] = mPieceId[nextMove.from.x][nextMove.from.y];
			mPieceId[nextMove.from.x][nextMove.from.y] = 0;
			
			// Make the move on the board
			mTzaar.move(nextMove);
			mAiRunning = false;
			invalidate();
		}
	}
	
	// Background thread object for the AI 
	AsyncTask<TzaarGame, Void, Move> aiThread = null;
	
	// Translucent colors 
	public static final int COLOR_PATH_RED = Color.argb(50, 255, 0, 0);
	public static final int COLOR_PATH_BLUE = Color.argb(50, 0, 0, 255);
	public static final int COLOR_VALID_GREEN = Color.argb(100, 0, 255, 0);

	// True if the board has already been one-time initialized
	private boolean mInitialized = false;
	
	// Dimensions of entire view (set by onLayout)
	private int mHeight = 0;
	private int mWidth = 0;
	
	// Screen density scale (initialized by constructor)
	private float mDensity;
	
	// Graphics transformation matrix
	private Matrix mMatrix = new Matrix();
	
	// Drawing constants
	private final int mBackgroundColor = Color.WHITE; 
	private final int mGridColor = Color.GRAY;
	private int mGridLineWidth = 0;

	// Drawing objects (initialized by initBoard)
	private Canvas mCachedCanvas;
	private Bitmap mCachedBitmap;
	private Paint mPaintGrid = new Paint();
	private Paint mPaintPath = new Paint();
	private Paint mPaintHeight = new Paint();
	private Paint mPaintMoves = new Paint();
	private Paint mPaintCurMove = new Paint();
	private Paint mPaintStats = new Paint();
	private Paint mPaintColLabels = new Paint();
	
	// Array to map piece IDs to the game board 
	private int mPieceId[][] = new int[GameBoard.COLS][GameBoard.ROWS];
		
	// Tzaar AI (initialized by constructor)
	private TzaarGame mTzaar;
	
	// Valid moves from current space
	private ArrayList<Move> mValidMoves = new ArrayList<Move>();
	
	// The current move
	private Move mCurMove = null;
	
	// True if the AI is currently searching for a move
	private boolean mAiRunning = false;
	
	/**
	 * Constructor initializes Tzaar AI and game board with default values.
	 * 
	 * @param context
	 */
	public GameViewGroup(Context context) {
		super(context);
		mTzaar = new TzaarGame(context, GameBoard.COLOR_WHITE, TzaarGame.DIFFICULTY_NONE, GameBoard.POSITIONS_FIXED);
		mDensity = getContext().getResources().getDisplayMetrics().density;
		Log.v("GameViewGroup(context)", "");
	}
	
	/**
	 * Constructor initializes Tzaar AI and game board with default values.
	 * 
	 * @param context
	 * @param attrs
	 */
	public GameViewGroup(Context context, AttributeSet attrs) {
		super(context, attrs);
		mTzaar = new TzaarGame(context, GameBoard.COLOR_WHITE, TzaarGame.DIFFICULTY_NONE, GameBoard.POSITIONS_FIXED);
		mDensity = getContext().getResources().getDisplayMetrics().density;
		Log.v("GameViewGroup(context, attrs)", "");
	}
	
	/**
	 * Constructor initializes Tzaar AI and game board with default values.
	 * 
	 * @param context
	 * @param attrs
	 * @param defStyle
	 */
	public GameViewGroup(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		mTzaar = new TzaarGame(context, GameBoard.COLOR_WHITE, TzaarGame.DIFFICULTY_NONE, GameBoard.POSITIONS_FIXED);
		mDensity = getContext().getResources().getDisplayMetrics().density;
		Log.v("GameViewGroup(context, attrs, defStyle)", "");
	}
	
	/**
	 * Performs one-time initialization of game board. Must be called after
	 * onSizeChanged().
	 */
	public void initBoard() {
		Log.v("GameViewGroup.initBoard", "Enter");
		
	    if (mCachedBitmap != null) 
	    	mCachedBitmap.recycle();
	    
	    mCachedBitmap = Bitmap.createBitmap(mWidth, mHeight, Bitmap.Config.ARGB_8888);
	    mCachedCanvas = new Canvas(mCachedBitmap);
		
		// Set the grid line paintbrush properties
		mPaintGrid.setColor(mGridColor);
		mPaintGrid.setStrokeWidth(mGridLineWidth);

		// Set the move path paintbrush properties
		mPaintPath.setStrokeWidth(dipsToPixels(10));
		mPaintPath.setStyle(Paint.Style.STROKE);
		
		// Set the stack height text paintbrush properties
		mPaintHeight.setColor(Color.argb(255, 255, 25, 25)); // red
		mPaintHeight.setTextSize(dipsToPixels(18.0f));
		mPaintHeight.setStrokeWidth(dipsToPixels(1.0f));
		mPaintHeight.setStyle(Paint.Style.FILL_AND_STROKE);
		mPaintHeight.setShadowLayer(dipsToPixels(1.0f), 0, 0, Color.BLACK);
		
		// Set the valid moves indicator paintbrush properties
		mPaintMoves.setColor(COLOR_VALID_GREEN);
		mPaintMoves.setStrokeWidth(dipsToPixels(4));
		mPaintMoves.setStyle(Paint.Style.FILL);
		
		// Set the current move paintbrush properties
		mPaintCurMove.setColor(COLOR_PATH_RED);
		mPaintCurMove.setStrokeWidth(dipsToPixels(4));
		mPaintCurMove.setStyle(Paint.Style.FILL);
		
		// Set the stats paintbrush properties
		mPaintStats.setColor(Color.BLACK);
		mPaintStats.setTextSize(dipsToPixels(24.0f));
		mPaintStats.setStrokeWidth(dipsToPixels(1.0f));
		mPaintStats.setStyle(Paint.Style.FILL);
		
		// Set the column label paintbrush properties
		mPaintColLabels.setColor(Color.GRAY);
		mPaintColLabels.setTextSize(dipsToPixels(12.0f));
		mPaintColLabels.setStrokeWidth(dipsToPixels(1.0f));
		mPaintColLabels.setStyle(Paint.Style.FILL);
		mPaintColLabels.setTextAlign(Align.CENTER);
		
		// Calculate the hex constants based on the current view width
	    mTzaar.board.calculateHexConstants(mWidth, dipsToPixels(40));
		
		// Set the background color
		mCachedCanvas.drawColor(mBackgroundColor);
		mGridLineWidth = dipsToPixels(1);

		// Draw the outer hex
		final float[] outerPts = mTzaar.board.getOuterHexPoints();
		mCachedCanvas.save();
		for (int i = 0; i < 6; ++i) {
			mCachedCanvas.drawLines(outerPts, mPaintGrid);
			mCachedCanvas.rotate(-60, mTzaar.board.getOuterHexCenterX(), mTzaar.board.getOuterHexCenterY());
		}
		mCachedCanvas.restore();
		
		// Draw the inner grid lines
		final float[] innerPts = mTzaar.board.getGridPoints(mTzaar.board.getOuterHexX(), mTzaar.board.getOuterHexY());
		mCachedCanvas.save();
		mCachedCanvas.drawLines(innerPts, mPaintGrid);		
		mCachedCanvas.rotate(-120, mTzaar.board.getOuterHexCenterX(), mTzaar.board.getOuterHexCenterY());
		mCachedCanvas.drawLines(innerPts, mPaintGrid);
		mCachedCanvas.rotate(-120, mTzaar.board.getOuterHexCenterX(), mTzaar.board.getOuterHexCenterY());
		mCachedCanvas.drawLines(innerPts, mPaintGrid);
		mCachedCanvas.restore();
		
		// Draw the column labels
		mCachedCanvas.drawText("A5", mTzaar.board.getVertexX(0), mTzaar.board.getVertexY(0, 2) - dipsToPixels(25.0f), mPaintColLabels);
		mCachedCanvas.drawText("B6", mTzaar.board.getVertexX(1), mTzaar.board.getVertexY(1, 1) - dipsToPixels(25.0f), mPaintColLabels);
		mCachedCanvas.drawText("C7", mTzaar.board.getVertexX(2), mTzaar.board.getVertexY(2, 1) - dipsToPixels(25.0f), mPaintColLabels);
		mCachedCanvas.drawText("D8", mTzaar.board.getVertexX(3), mTzaar.board.getVertexY(3, 0) - dipsToPixels(25.0f), mPaintColLabels);
		mCachedCanvas.drawText("E8", mTzaar.board.getVertexX(4), mTzaar.board.getVertexY(4, 0) - dipsToPixels(25.0f), mPaintColLabels);
		mCachedCanvas.drawText("F8", mTzaar.board.getVertexX(5), mTzaar.board.getVertexY(5, 0) - dipsToPixels(25.0f), mPaintColLabels);
		mCachedCanvas.drawText("G7", mTzaar.board.getVertexX(6), mTzaar.board.getVertexY(6, 1) - dipsToPixels(25.0f), mPaintColLabels);
		mCachedCanvas.drawText("H6", mTzaar.board.getVertexX(7), mTzaar.board.getVertexY(7, 1) - dipsToPixels(25.0f), mPaintColLabels);
		mCachedCanvas.drawText("I5", mTzaar.board.getVertexX(8), mTzaar.board.getVertexY(8, 2) - dipsToPixels(25.0f), mPaintColLabels);

		// Add extra y padding to the bottom labels to account for the height of the text itself
		mCachedCanvas.drawText("A1", mTzaar.board.getVertexX(0), mTzaar.board.getVertexY(0, 6) + dipsToPixels(35.0f), mPaintColLabels);
		mCachedCanvas.drawText("B1", mTzaar.board.getVertexX(1), mTzaar.board.getVertexY(1, 6) + dipsToPixels(35.0f), mPaintColLabels);
		mCachedCanvas.drawText("C1", mTzaar.board.getVertexX(2), mTzaar.board.getVertexY(2, 7) + dipsToPixels(35.0f), mPaintColLabels);
		mCachedCanvas.drawText("D1", mTzaar.board.getVertexX(3), mTzaar.board.getVertexY(3, 7) + dipsToPixels(35.0f), mPaintColLabels);
		mCachedCanvas.drawText("E1", mTzaar.board.getVertexX(4), mTzaar.board.getVertexY(4, 8) + dipsToPixels(35.0f), mPaintColLabels);
		mCachedCanvas.drawText("F1", mTzaar.board.getVertexX(5), mTzaar.board.getVertexY(5, 7) + dipsToPixels(35.0f), mPaintColLabels);
		mCachedCanvas.drawText("G1", mTzaar.board.getVertexX(6), mTzaar.board.getVertexY(6, 7) + dipsToPixels(35.0f), mPaintColLabels);
		mCachedCanvas.drawText("H1", mTzaar.board.getVertexX(7), mTzaar.board.getVertexY(7, 6) + dipsToPixels(35.0f), mPaintColLabels);
		mCachedCanvas.drawText("I1", mTzaar.board.getVertexX(8), mTzaar.board.getVertexY(8, 6) + dipsToPixels(35.0f), mPaintColLabels);
		
		// Draw the divider line between the game board and the stats 
		float lineY = mTzaar.board.getOuterHexY() + mTzaar.board.getOuterHexRadius() + (mTzaar.board.getInnerHexRadius() * 3.5f);
		mCachedCanvas.drawLine(dipsToPixels(15), lineY, getWidth() - dipsToPixels(15), lineY, mPaintGrid);

		// Draw the piece counter images
		int bmpLeft1 = getWidth() / 2 + dipsToPixels(25);
		int bmpLeft2 = getWidth() / 2 + dipsToPixels(95); 
		Bitmap bmpWto = BitmapFactory.decodeResource(getResources(), R.drawable.white_tott_small);
		Bitmap bmpWta = BitmapFactory.decodeResource(getResources(), R.drawable.white_tzarra_small);
		Bitmap bmpWtz = BitmapFactory.decodeResource(getResources(), R.drawable.white_tzaar_small);
		Bitmap bmpBto = BitmapFactory.decodeResource(getResources(), R.drawable.black_tott_small);
		Bitmap bmpBta = BitmapFactory.decodeResource(getResources(), R.drawable.black_tzarra_small);
		Bitmap bmpBtz = BitmapFactory.decodeResource(getResources(), R.drawable.black_tzaar_small);
		
        mCachedCanvas.drawBitmap(bmpWto, bmpLeft1, getHeight() - dipsToPixels(85), null);
        mCachedCanvas.drawBitmap(bmpWta, bmpLeft1, getHeight() - dipsToPixels(60), null);
        mCachedCanvas.drawBitmap(bmpWtz, bmpLeft1, getHeight() - dipsToPixels(35), null);
        mCachedCanvas.drawBitmap(bmpBto, bmpLeft2, getHeight() - dipsToPixels(85), null);
        mCachedCanvas.drawBitmap(bmpBta, bmpLeft2, getHeight() - dipsToPixels(60), null);
        mCachedCanvas.drawBitmap(bmpBtz, bmpLeft2, getHeight() - dipsToPixels(35), null);
		
		// Randomize the game board pieces if necessary 
		if (mTzaar.getStartPositions() == GameBoard.POSITIONS_RANDOM) {
			mTzaar.board.randomize();
		}
		
		// Finally, populate the piece images array
		int wtoCount = 0;
		int wtaCount = 0;
		int wtzCount = 0;
		int btoCount = 0;
		int btaCount = 0;
		int btzCount = 0;
		for (int col = 0; col < GameBoard.COLS; ++col) {
			for (int row = 0; row < GameBoard.ROWS; ++row) {
				switch (mTzaar.board.getPieceColorAndType(col, row)) {
					case GameBoard.WTO:
						mPieceId[col][row] = PieceId.WTO_ID[wtoCount++];
						break;
					case GameBoard.WTA:
						mPieceId[col][row] = PieceId.WTA_ID[wtaCount++];
						break;
					case GameBoard.WTZ:
						mPieceId[col][row] = PieceId.WTZ_ID[wtzCount++];
						break;
					case GameBoard.BTO:
						mPieceId[col][row] = PieceId.BTO_ID[btoCount++];
						break;
					case GameBoard.BTA:
						mPieceId[col][row] = PieceId.BTA_ID[btaCount++];
						break;
					case GameBoard.BTZ:
						mPieceId[col][row] = PieceId.BTZ_ID[btzCount++];
						break;
					default:
						break;						
				}
			}
		}
	
		mInitialized = true;
		Log.v("GameViewGroup.initBoard", "Exit");
	}
	
	@Override
	public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		Log.v("GameViewGroup.onMeasure", "Enter");

		final int childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(
				MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.AT_MOST);
		final int childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(
				MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.AT_MOST);
		
		for (int i = 0; i < getChildCount(); ++i) {
			View child = getChildAt(i);
			child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
		}
		
		final int measuredWidth = MeasureSpec.getSize(widthMeasureSpec);
		final int measuredHeight = MeasureSpec.getSize(heightMeasureSpec);
		
		setMeasuredDimension(measuredWidth, measuredHeight);
		Log.v("GameViewGroup.onMeasure", "Exit");
	}
	
	@Override
	public void onLayout(boolean changed, int l, int t, int r, int b) {
		Log.v("GameViewGroup.onLayout", "Enter");
	    
		// Now that the view dimensions are known, draw the board
		if (!mInitialized) {
			initBoard();
		}

		// Set layout for each piece
		for (int col = 0; col < GameBoard.COLS; ++col) {
			for (int row = 0; row < GameBoard.ROWS; ++row) {
				final byte piece = mTzaar.board.getPiece(col, row);
				
				if (piece != GameBoard.NULL && piece != GameBoard.NONE) {			
					ImageView image = (ImageView) findViewById(mPieceId[col][row]);
					final Bitmap bitmap = ((BitmapDrawable) image.getDrawable()).getBitmap();
	
					if (image.getVisibility() != GONE) { 
						final int imgLeft = l + (int) mTzaar.board.getVertexX(col) - (bitmap.getWidth() / 2);
						final int imgTop = t + (int) mTzaar.board.getVertexY(col, row) - (bitmap.getHeight() / 2);
						final int imgRight = imgLeft + bitmap.getWidth();
						final int imgBot = imgTop + bitmap.getHeight();
						
						image.layout(imgLeft, imgTop, imgRight, imgBot);
						
						mMatrix.reset();
						mMatrix.postTranslate(imgLeft, imgTop);
						image.setScaleType(ScaleType.MATRIX);
						image.setImageMatrix(mMatrix);
					}
				}
			}
		}
		
		Log.v("GameViewGroup.onLayout", "Exit");
	}
	
	@Override
	public void onSizeChanged(int w, int h, int oldw, int oldh) {
		Log.v("GameViewGroup.onSizeChanged", "Enter");
		
	    super.onSizeChanged(w, h, oldw, oldh);
	    mWidth = w;
	    mHeight = h;
	    
	    Log.v("GameViewGroup.onSizeChanged", "Exit");
	}
	
	@Override
	public void dispatchDraw(Canvas canvas) {
		Log.v("GameViewGroup.dispatchDraw", "Enter");
				
		// Draw the game board from cache
		canvas.drawBitmap(mCachedBitmap, 0, 0, null);

		// Get the most recent turn
		Turn turn = null;
		try {
			turn = mTzaar.turns.peek();
		}
		catch (EmptyStackException ese) {
			// ignore
		}

		// Draw paths highlighting the previous move(s)
		if (turn != null) {
			drawMovePath(canvas, turn.getFirst());
			drawMovePath(canvas, turn.getSecond());
		}
		
		// Illuminate valid moves from the tapped space
		drawValidMoves(canvas);
		
		// Highlight the selected piece
		drawPieceHighlight(canvas);
		
		// Draw the pieces
		drawPieces(canvas, turn);
		
		// Draw the game statistics text
		drawStatistics(canvas);
		
		// Check for an endgame state
		boolean playerWin = mTzaar.isWinningState(mTzaar.getPlayerColor());
		boolean aiWin = mTzaar.isWinningState(mTzaar.oppositeColor(mTzaar.getPlayerColor()));
		if (playerWin || aiWin ) {
			// Display a dialog with the game result
			AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
			if (playerWin) {
				builder.setMessage(R.string.you_win);
			}
			else {
				builder.setMessage(R.string.you_lose);
			}
			
			builder.setPositiveButton(R.string.play_again, new DialogInterface.OnClickListener() {				
				@Override
				public void onClick(DialogInterface dialog, int which) {
					if (aiThread != null)
						aiThread.cancel(true);
					
					Activity activity = (Activity) getContext();
					Intent intent = new Intent(activity, NewGameSetupActivity.class);
					activity.startActivity(intent);
				}
			});
			builder.setNegativeButton(R.string.quit_game, new DialogInterface.OnClickListener() {				
				@Override
				public void onClick(DialogInterface dialog, int which) {
					if (aiThread != null)
						aiThread.cancel(true);
					
					Activity activity = (Activity) getContext();
					Intent intent = new Intent(activity, MainActivity.class);
					activity.startActivity(intent);
				}
			});
			
			AlertDialog dialog = builder.create();
			dialog.show();
		}
		else if (mAiRunning == false && mTzaar.whoseTurn() != mTzaar.getPlayerColor()) {
			// If it is the AI's turn, start the AI thread
			mAiRunning = true;
			aiThread = new MoveSearchTask().execute(mTzaar);
		}
		
		Log.v("GameViewGroup.dispatchDraw", "Exit");
	}
	
	/**
	 * 
	 */
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		Log.v("GameViewGroup.onTouchEvent", "Enter");

		if (event.getActionMasked() == MotionEvent.ACTION_UP) {
			// Get the coordinates of the event
			float x, y;
			try {
				int pid = event.getPointerId(event.getActionIndex());
				x = event.getX(pid);
				y = event.getY(pid);
			}
			catch (IllegalArgumentException iae) {
				return false;
			}
			
			// Calculate the tapped column and row on the board 
			final int col = mTzaar.board.getColumn(x);
			final int row = mTzaar.board.getRow(y, col);
			
			// If the tap was on the game board and it is the AI's turn, discard the event
			final boolean isTurn = mTzaar.whoseTurn() == mTzaar.getPlayerColor();
			
			// Ignore out-of-range events
			final boolean inRange = col >= 0 || col < GameBoard.COLS || row >= 0 || row < GameBoard.ROWS;
			
			// Ignore illegal and empty spaces
			final boolean okSpace = mTzaar.board.getPiece(col, row) != GameBoard.NULL 
					&& mTzaar.board.getPiece(col, row) != GameBoard.NONE;

			if (isTurn && inRange && okSpace) {
				// Update the current move
				if (mCurMove == null) {
					mCurMove = new Move(TzaarGame.MOVE_UNSET, col, row);
				}
				else {
					mCurMove.to = new Point(col, row);
				}
				
				// Validate the move
				final boolean isValidMove = mValidMoves.contains(mCurMove);
				
				// Tapped own color
				if (mTzaar.board.getPieceColor(col, row) == mTzaar.getPlayerColor()) {
					// Tapped the same piece again -- cancel move
					if (mCurMove.to != null && mCurMove.from.equals(mCurMove.to)) {
						mValidMoves = new ArrayList<Move>();
						mCurMove = null;
					}
					// First tap or invalid move -- show valid moves
					else if (!isValidMove) {
						mValidMoves.clear();
						mTzaar.getValidMovesFromSpace(
								mTzaar.getPlayerColor(), mTzaar.getMoveNumber(), 
								col, row, mValidMoves);
						mCurMove = new Move(TzaarGame.MOVE_UNSET, col, row);
					}
					// Second tap and the move is valid -- perform the stacking move
					else {						
						// Swap the piece image IDs
						mPieceId[col][row] = mPieceId[mCurMove.from.x][mCurMove.from.y];
						mPieceId[mCurMove.from.x][mCurMove.from.y] = 0;
						
						// Make the move on the board
						mCurMove.type = TzaarGame.MOVE_STACK;
						mTzaar.move(mCurMove);
						mValidMoves = new ArrayList<Move>();
						mCurMove = null;
					}
					
					// Force a redraw of the view
					invalidate();
				}
				// Tapped opponent color and move is valid -- perform the capturing move
				else if (isValidMove) {
					// Swap the piece image IDs
					mPieceId[col][row] = mPieceId[mCurMove.from.x][mCurMove.from.y];
					mPieceId[mCurMove.from.x][mCurMove.from.y] = 0;
					
					// Make the move on the board
					mCurMove.type = TzaarGame.MOVE_CAPTURE;
					mTzaar.move(mCurMove);
					mValidMoves = new ArrayList<Move>();
					mCurMove = null;
					
					// Force a redraw of the view
					invalidate();
				}
			}
		}
		
		Log.v("GameViewGroup.onTouchEvent", "Exit");
		return true;
	}
	
	private void drawValidMoves(Canvas canvas) {
		if (mValidMoves != null) {
			for (Move move : mValidMoves) {
				canvas.drawCircle(
						mTzaar.board.getVertexX(move.to.x), 
						mTzaar.board.getVertexY(move.to.x, move.to.y), 
						dipsToPixels(20.0f), 
						mPaintMoves);
			}
		}
	}
	
	private void drawPieceHighlight(Canvas canvas) {
		if (mCurMove != null) {
			canvas.drawCircle(
					mTzaar.board.getVertexX(mCurMove.from.x), 
					mTzaar.board.getVertexY(mCurMove.from.x, mCurMove.from.y), 
					dipsToPixels(20.0f), 
					mPaintCurMove);
		}
	}
	
	private void drawMovePath(Canvas canvas, Move move) {
		if (move != null && move.type != TzaarGame.MOVE_UNSET) {
			final float startX = mTzaar.board.getVertexX(move.from.x);
			final float startY = mTzaar.board.getVertexY(move.from.x, move.from.y);
			final float stopX  = mTzaar.board.getVertexX(move.to.x);
			final float stopY  = mTzaar.board.getVertexY(move.to.x, move.to.y);
	
			if (move.type == TzaarGame.MOVE_CAPTURE) {
				mPaintPath.setColor(COLOR_PATH_RED);
			}
			else if (move.type == TzaarGame.MOVE_STACK) {
				mPaintPath.setColor(COLOR_PATH_BLUE);
			}
			
			canvas.drawLine(startX, startY, stopX, stopY, mPaintPath);
		}
	}
	
	private void drawPieces(Canvas canvas, Turn turn) {
		for (int col = 0; col < GameBoard.COLS; ++col) {
			for (int row = 0; row < GameBoard.ROWS; ++row) {
				final byte piece = mTzaar.board.getPiece(col, row);
				
				if (piece != GameBoard.NULL && piece != GameBoard.NONE) {				
					ImageView image = (ImageView) findViewById(mPieceId[col][row]);
					
					// Reposition the image of the piece that was just moved
					if (turn != null) {
						Move lastMove = turn.getPrev();
						if (lastMove != null && lastMove.to != null 
								&& lastMove.to.x == col && lastMove.to.y == row) {
							final Bitmap bitmap = ((BitmapDrawable) image.getDrawable()).getBitmap();
							final int imgLeft = (int) (mTzaar.board.getVertexX(col) - (bitmap.getWidth() / 2));
							final int imgTop = (int) (mTzaar.board.getVertexY(col, row) - (bitmap.getHeight() / 2));
							
							image.setLeft(imgLeft);
							image.setTop(imgTop);
							image.setRight(imgLeft + bitmap.getWidth());
							image.setBottom(imgTop + bitmap.getHeight());
							
							mMatrix.reset();
							mMatrix.postTranslate(imgLeft, imgTop);
							image.setScaleType(ScaleType.MATRIX);
							image.setImageMatrix(mMatrix);
						}
					}
					image.draw(canvas);
					
					// Display stack height on stacks higher than 1
					final int height = mTzaar.board.getStackHeight(col, row);
					if (height > 1) {
						canvas.drawText(
								Integer.toString(height), 
								image.getRight() - dipsToPixels(16.0f), 
								image.getBottom() - dipsToPixels(6.0f), 
								mPaintHeight);
					}
				}
			}
		}
	}
	
	/**
	 * TODO: Do this all in a layout instead of manually
	 * @param canvas
	 */
	private void drawStatistics(Canvas canvas) {
		String turnText = "Turn: " + (mTzaar.getTurnCount() + 1) + " (" + (mTzaar.whoseTurn() == GameBoard.COLOR_BLACK ? "black" : "white") + ")";
		String moveText = "Move: " + (mTzaar.getTurnCount() == 0 ? "1/1" : (mTzaar.getMoveNumber() == TzaarGame.MOVE_FIRST ? "1/2" : "2/2"));
		String wtoText = Integer.toString(mTzaar.board.getPieceCount(GameBoard.WTO));
		String wtaText = Integer.toString(mTzaar.board.getPieceCount(GameBoard.WTA));
		String wtzText = Integer.toString(mTzaar.board.getPieceCount(GameBoard.WTZ));
		String btoText = Integer.toString(mTzaar.board.getPieceCount(GameBoard.BTO));
		String btaText = Integer.toString(mTzaar.board.getPieceCount(GameBoard.BTA));
		String btzText = Integer.toString(mTzaar.board.getPieceCount(GameBoard.BTZ));
		int leftTextX = dipsToPixels(15);
		int rightTextX = (getWidth() / 2) + leftTextX + dipsToPixels(15);
		
		mPaintStats.setTextSize(dipsToPixels(24.0f));
		canvas.drawText(turnText, leftTextX, getHeight() - dipsToPixels(70), mPaintStats);
		canvas.drawText(moveText, leftTextX, getHeight() - dipsToPixels(40), mPaintStats);

		mPaintStats.setTextSize(dipsToPixels(18.0f));
		canvas.drawText(wtoText, rightTextX + dipsToPixels(15), getHeight() - dipsToPixels(70), mPaintStats);
		canvas.drawText(wtaText, rightTextX + dipsToPixels(15), getHeight() - dipsToPixels(45), mPaintStats);
		canvas.drawText(wtzText, rightTextX + dipsToPixels(15), getHeight() - dipsToPixels(20), mPaintStats);

		canvas.drawText(btoText, rightTextX + dipsToPixels(85), getHeight() - dipsToPixels(70), mPaintStats);
		canvas.drawText(btaText, rightTextX + dipsToPixels(85), getHeight() - dipsToPixels(45), mPaintStats);
		canvas.drawText(btzText, rightTextX + dipsToPixels(85), getHeight() - dipsToPixels(20), mPaintStats);
	}
	
	/**
	 * Converts dips to pixels based on current screen density.
	 * 
	 * @param dips
	 * @return pixels
	 */
	public int dipsToPixels(float dips) {
		return (int) (dips * mDensity + 0.5f);
	}
	
	/**
	 * Mutator for color member.
	 * 
	 * @param color
	 */
	public void setColor(String color) {
		if (color.equalsIgnoreCase("black"))
			mTzaar.setPlayerColor(GameBoard.COLOR_BLACK);
		else if (color.equalsIgnoreCase("white"))
			mTzaar.setPlayerColor(GameBoard.COLOR_WHITE);
		else 
			throw new IllegalArgumentException("Illegal color value (" + color + ")!");

	}
	
	/**
	 * Mutator for difficulty member.
	 * 
	 * @param difficulty
	 */
	public void setDifficulty(String difficulty) {
		if (difficulty.equalsIgnoreCase("no challenge"))
			mTzaar.setDifficulty(TzaarGame.DIFFICULTY_NONE);
		else if (difficulty.equalsIgnoreCase("easy"))
			mTzaar.setDifficulty(TzaarGame.DIFFICULTY_EASY);
		else if (difficulty.equalsIgnoreCase("medium"))
			mTzaar.setDifficulty(TzaarGame.DIFFICULTY_MEDIUM);
		else if (difficulty.equalsIgnoreCase("hard"))
			mTzaar.setDifficulty(TzaarGame.DIFFICULTY_HARD);
		else
			throw new IllegalArgumentException("Illegal difficulty value (" + difficulty + ")!");
	}

	/**
	 * Mutator for positions member.
	 * 
	 * @param positions
	 */
	public void setPositions(String positions) {
		if (positions.equalsIgnoreCase("random"))
			mTzaar.setStartPositions(GameBoard.POSITIONS_RANDOM);
		else if (positions.equalsIgnoreCase("fixed"))
			mTzaar.setStartPositions(GameBoard.POSITIONS_FIXED);
		else
			throw new IllegalArgumentException("Illegal start positions value (" + positions + ")!");			
	}
}
