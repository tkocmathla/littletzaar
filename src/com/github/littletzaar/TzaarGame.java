package com.github.littletzaar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;
import java.util.Stack;

import android.content.Context;
import android.graphics.Point;
import android.util.Log;

/**
 * This class implements the Tzaar AI and game mechanics.
 * 
 * @author mgrimm
 */
public class TzaarGame {
	// Difficulty constants
	public static final int DIFFICULTY_NONE   = 0;
	public static final int DIFFICULTY_EASY   = 1;
	public static final int DIFFICULTY_MEDIUM = 2;
	public static final int DIFFICULTY_HARD   = 3;
	
	// Move number constants
	public static final int MOVE_FIRST  = 0;
	public static final int MOVE_SECOND = 1;
	
	// Move type constants
	public static final int MOVE_UNSET   = -1;
	public static final int MOVE_CAPTURE = 0;
	public static final int MOVE_STACK   = 1;
	
	// Number of simulations for the AI to play at each difficulty level
	public static final int NUM_SIMS_EASY   = 5;
	public static final int NUM_SIMS_MEDIUM = 20;
	public static final int NUM_SIMS_HARD   = 50;
	
	// Maximum time allowed for AI to play at each difficulty level (seconds)
	public static final int MAX_TIME_EASY   = 5;
	public static final int MAX_TIME_MEDIUM = 10;
	public static final int MAX_TIME_HARD   = 15;	
	
	// Current game board
	// TODO: MAKE THIS PRIVATE
	protected GameBoard board = new GameBoard();
	
	// Tracks move history
	// TODO: MAKE THIS PRIVATE
	protected Stack<Turn> turns = new Stack<Turn>();

	// Current player color (initialized by constructor)
	private int mPlayerColor;

	// Current start positions (initialized by constructor)
	private int mStartPositions;
	
	// Current difficulty (initialized by constructor)
	private int mDifficulty;

	// Current move number
	private int mMoveNumber = MOVE_FIRST;

	// Total turn count
	private int mTurnCount = 0;
		
	/* 
	 * Direction offsets for traversing the board.
	 * 
	 * even column (index 0):
	 * 		n is row - 1
	 * 		ne is row - 1, col + 1
	 * 		se is col + 1
	 * 		s is row + 1
	 * 		sw is col - 1
	 * 		nw is row - 1, col - 1
	 * 
	 * odd column (index 1):
	 * 		n is row - 1
	 * 		ne is col + 1
	 * 		se is row + 1, col + 1
	 * 		s is row + 1
	 * 		sw is row + 1, col - 1
	 * 		nw is col - 1
	 * 
	 *  Points are defined as (deltaCol,deltaRow), in the following order:
	 *  	0: north
	 *  	1: northeast
	 *  	2: southeast
	 *  	3: south
	 *  	4: southwest
	 *  	5: northwest
	 */
	private Point[][] mDirections = new Point[2][6];
	
	// Holds temporary valid moves list for canStack()
	private ArrayList<Move> tempMoves = new ArrayList<Move>();
		
	/**
	 * Constructor initializes the game.
	 * 
	 * @param context
	 * @param playerColor
	 * @param difficulty
	 * @param startPositions
	 */
	public TzaarGame(Context context, int playerColor, int difficulty, int startPositions) {
		mPlayerColor = playerColor;
		mDifficulty = difficulty;
		mStartPositions = startPositions;
		init();
	}
	
	/**
	 * Copy constructor.
	 * 
	 * @param that
	 */
	public TzaarGame(TzaarGame that) {
		this.board = new GameBoard(that.board);
		this.turns.addAll(that.turns);
		this.mPlayerColor = that.mPlayerColor;
		this.mStartPositions = that.mStartPositions;
		this.mDifficulty = that.mDifficulty;
		this.mMoveNumber = that.mMoveNumber;
		this.mTurnCount = that.mTurnCount;
		init();
	}
	
	/**
	 * Populates the direction arrays.
	 */
	private void init() {
		// Initialize even-column directions
		mDirections[0] = new Point[6];
		mDirections[0][0] = new Point( 0, -1);  // n
		mDirections[0][1] = new Point( 1, -1);  // ne
		mDirections[0][2] = new Point( 1,  0);  // se
		mDirections[0][3] = new Point( 0,  1);  // s
		mDirections[0][4] = new Point(-1,  0);  // sw
		mDirections[0][5] = new Point(-1, -1);  // nw
		
		// Initialize odd-column directions
		mDirections[1] = new Point[6];
		mDirections[1][0] = new Point( 0, -1);  // n
		mDirections[1][1] = new Point( 1,  0);  // ne
		mDirections[1][2] = new Point( 1,  1);  // se
		mDirections[1][3] = new Point( 0,  1);  // s
		mDirections[1][4] = new Point(-1,  1);  // sw
		mDirections[1][5] = new Point(-1,  0);  // nw
	}
	
	/**
	 * Implements the AI move search algorithm(s).
	 * 
	 * @return best move
	 */
	public Move findMove(int playerColor, int moveNumber) {
		Random rand = new Random();
		Move move = null;
		
		if (mDifficulty == DIFFICULTY_NONE) {
			// Randomly select the next move
			ArrayList<Move> moves = new ArrayList<Move>();
			getValidMoves(playerColor, moveNumber, moves);
			move = moves.get(rand.nextInt(moves.size()));
		}
		else {
			// Play random simulations using each of the possible starting moves
			int numSimulations = 0;
			int maxSeconds = 0;
			
			switch (mDifficulty) {
				case DIFFICULTY_EASY:
					numSimulations = NUM_SIMS_EASY;
					maxSeconds = MAX_TIME_EASY;
					break;
				case DIFFICULTY_MEDIUM:
					numSimulations = NUM_SIMS_MEDIUM;
					maxSeconds = MAX_TIME_MEDIUM;
					break;
				case DIFFICULTY_HARD:
					numSimulations = NUM_SIMS_HARD;
					maxSeconds = MAX_TIME_HARD;
					break;
			}

			// Set up the turn timer
			long nowTime = System.currentTimeMillis() / 1000;
			long endTime = nowTime + maxSeconds;
			
			// 
			int bestWinCount = 0;
			Move bestMove = null;
			
			// 
			ArrayList<Move> validMoves = new ArrayList<Move>();
			ArrayList<Move> startMoves = new ArrayList<Move>();
			getValidMoves(playerColor, moveNumber, startMoves);
			Collections.shuffle(startMoves);
			
			for (Move startMove : startMoves) {
				long moveStartTime = System.currentTimeMillis();
				
				// Track number of times player won using this start move
				int winCount = 0;
				
				// Initialize the best move to the start move
				if (bestMove == null) {
					bestMove = startMove;
				}
				
				for (int i = 0; i < numSimulations; ++i) {
					nowTime = System.currentTimeMillis() / 1000;
					if (nowTime > endTime) {
						break;
					}
					
					// Copy the game and make the initial move
					TzaarGame testGame = new TzaarGame(this);
					testGame.move(startMove);
					
					// Play out the game on the test board 
					while (true) {
						testGame.getValidMoves(testGame.whoseTurn(), testGame.getMoveNumber(), validMoves);
						if (validMoves.size() > 0) {
							Move randMove = validMoves.get(rand.nextInt(validMoves.size()));
							testGame.move(randMove);
							
							// Check if either player is in a winning state
							if (testGame.isWinningState(GameBoard.COLOR_UNSET)) {
								break;
							}
						}
						else {
							break;
						}
					}
					
					// Increment win counter if we won
					if (testGame.isWinningState(playerColor)) {
						++winCount;
					}
				}
				
				// Record the best move so far
				if (winCount > bestWinCount) {
					bestWinCount = winCount;
					bestMove = startMove;
				}
				
				if (nowTime > endTime) {
					break;
				}
								
				long moveFinishTime = System.currentTimeMillis();
				Log.v("Instrumentation", "Total simulation time for this move: " + (moveFinishTime - moveStartTime) + " ms");
			}

			move = bestMove;
		}
		
		return move;
	}
	
	/**
	 * Returns the opposite player color to the given color.
	 * 
	 * @param color
	 * @return opposite color
	 */
	public int oppositeColor(int color) {
		int oppositeColor = GameBoard.COLOR_UNSET;
		
		if (color == GameBoard.COLOR_BLACK) {
			oppositeColor = GameBoard.COLOR_WHITE;
		}
		else if (color == GameBoard.COLOR_WHITE) {
			oppositeColor = GameBoard.COLOR_BLACK;
		}
		else {
			throw new IllegalArgumentException(String.format("Invalid color (%d)!", color)); 
		}
		
		return oppositeColor;
	}
	
	/**
	 * Finds all valid moves from the given space for the given player. Maximum 
	 * number of moves from a single space is 6.
	 * 
	 * The piece color and height are directly extracted because the method 
	 * calls are too slow for this critical code section.
	 * 
	 * @param playerColor
	 * @param col
	 * @param row
	 * @return list of valid moves
	 */
	public void getValidMovesFromSpace(int playerColor, int moveNumber, int col, int row, ArrayList<Move> outMoves) {
		// Decode the piece attributes (inlined for performance)
		byte piece = board.getPiece(col, row);
		int pieceColor = piece & 1;
		int pieceHeight = piece >> 3;
		
		// Skip illegal, empty, and opponent spaces
		if (piece == GameBoard.NULL || piece == GameBoard.NONE || pieceColor != playerColor)
			return;
		
		// Check for valid moves in each direction
		for (int i = 0; i < mDirections[0].length; ++i) {
			// Search for a piece in the current direction
			int nextCol = col;
			int nextRow = row;
			byte nextPiece;
			int nextColor;
			int nextHeight;
			do {
				// Calculate the space coordinates
				int dirIndex = nextCol % 2;
				nextCol += mDirections[dirIndex][i].x;
				nextRow += mDirections[dirIndex][i].y;
				
				// Decode the piece attributes (inlined for performance)
				nextPiece = board.getPiece(nextCol, nextRow);
				nextColor = nextPiece & 1;
				nextHeight = nextPiece >> 3;
			} while (nextPiece == GameBoard.NONE
					&& nextCol >= 0 && nextCol < GameBoard.COLS 
					&& nextRow >= 0 && nextRow < GameBoard.ROWS);
			
			// Abort if terminal move along the path is illegal
			if (nextPiece == GameBoard.NULL || nextPiece == GameBoard.NONE)
				continue;
			
			boolean canCapture = (nextColor != playerColor) && (nextHeight <= pieceHeight);
			boolean canStack   = (nextColor == playerColor) && (moveNumber == TzaarGame.MOVE_SECOND);
			boolean isSuicide  = (nextColor == playerColor) && (board.getPieceCount((byte) (nextPiece & 7)) == 1);
			
			if (canCapture) {
				outMoves.add(new Move(MOVE_CAPTURE, col, row, nextCol, nextRow));
			}
			else if (canStack && !isSuicide) {
				outMoves.add(new Move(MOVE_STACK, col, row, nextCol, nextRow));
			}
		}
	}
	
	/**
	 * Finds all valid moves on the game board for the given player.
	 * 
	 * Maximum number of moves is (pieceCount * 6). 
	 */
	public void getValidMoves(int playerColor, int moveNumber, ArrayList<Move> outMoves) {
		outMoves.clear();
		
		for (int col = 0; col < GameBoard.COLS; ++col) {
			for (int row = 0; row < GameBoard.ROWS; ++row) {
				getValidMovesFromSpace(playerColor, moveNumber, col, row, outMoves);
			}
		}
	}
	
	/**
	 * Determines if there is at least one stacking move available to the given
	 * player. 
	 * 
	 * This method is basically the same as getValidMoves(), but returns as soon
	 * as the first stacking move is located.
	 * 
	 * @param playerColor
	 * @return true if the player can make a stacking move, false otherwise
	 */
	public boolean canStack(int playerColor) {
		boolean canStack = false;
		tempMoves.clear();
		
		for (int col = 0; col < GameBoard.COLS; ++col) {
			for (int row = 0; row < GameBoard.ROWS; ++row) {
				getValidMovesFromSpace(playerColor, MOVE_FIRST, col, row, tempMoves);
				if (tempMoves.size() > 0) {
					canStack = true;
					break;
				}
			}
		}

		return canStack;
	}	
	
	/**
	 * Makes the move on the game board and updates the various game metrics.
	 * 
	 * @param move
	 */
	public void move(Move move) {
		board.move(move.from.x, move.from.y, move.to.x, move.to.y);

		// Increment move number and turn count
		if (mTurnCount == 0) {
			turns.push(new Turn(whoseTurn(), move));
			mMoveNumber = TzaarGame.MOVE_FIRST;
			++mTurnCount;
		}
		else if (mMoveNumber == TzaarGame.MOVE_SECOND) {
			turns.peek().setSecond(move);
			mMoveNumber = TzaarGame.MOVE_FIRST;
			++mTurnCount;
		}
		else {
			turns.push(new Turn(whoseTurn(), move));
			mMoveNumber = TzaarGame.MOVE_SECOND;
		}
	}
	
	/**
	 * Determines if the current state is a winning state for the given player.
	 * 
	 * Tzaar ends when one of two conditions is met:
	 *   1. A player has zero totts, tzarras, or tzaars
	 *   2. A player cannot make a stacking move
	 * 
	 * @param playerColor
	 * @return whether the current state is a winning state for the player
	 */
	public boolean isWinningState(int playerColor) {
		boolean winningState = false;
		boolean blackHasPieces = board.getPieceCount(GameBoard.BTO) > 0
				&& board.getPieceCount(GameBoard.BTA) > 0 
				&& board.getPieceCount(GameBoard.BTZ) > 0;
		boolean whiteHasPieces = board.getPieceCount(GameBoard.WTO) > 0
				&& board.getPieceCount(GameBoard.WTA) > 0 
				&& board.getPieceCount(GameBoard.WTZ) > 0;

		boolean blackCanStack = canStack(GameBoard.COLOR_BLACK);
		boolean whiteCanStack = canStack(GameBoard.COLOR_WHITE);
		
		boolean blackWin = blackHasPieces && blackCanStack && (!whiteHasPieces || !whiteCanStack);
		boolean whiteWin = whiteHasPieces && whiteCanStack && (!blackHasPieces || !blackCanStack);
		
		if (playerColor == GameBoard.COLOR_BLACK) {
			winningState = blackWin;
		}
		else if (playerColor == GameBoard.COLOR_WHITE) {
			winningState = whiteWin;
		}
		else {
			winningState = blackWin || whiteWin;
		}
		
		return winningState;
	}
	
	/**
	 * Returns the color of the player whose turn it is.
	 * 
	 * @return player color
	 */
	public int whoseTurn() {
		int player;
		
		if ((mTurnCount % 2) == 0)
			player = GameBoard.COLOR_WHITE;
		else
			player = GameBoard.COLOR_BLACK;
		
		return player;
	}
	
	public int getPlayerColor() {
		return mPlayerColor;
	}

	public void setPlayerColor(int playerColor) {
		mPlayerColor = playerColor;
	}
	
	public int getDifficulty() {
		return mDifficulty;
	}

	public void setDifficulty(int difficulty) {
		mDifficulty = difficulty;
	}

	public int getStartPositions() {
		return mStartPositions;
	}

	public void setStartPositions(int startPositions) {
		mStartPositions = startPositions;
	}

	public int getMoveNumber() {
		return mMoveNumber;
	}

	public void setMoveNumber(int moveNumber) {
		mMoveNumber = moveNumber;
	}

	public int getTurnCount() {
		return mTurnCount;
	}

	public void setTurnCount(int turnCount) {
		mTurnCount = turnCount;
	}
}
