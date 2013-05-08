package com.github.littletzaar;

import java.util.Random;

/**
 * This class implements the game board.
 * 
 * @author mgrimm
 */
public class GameBoard {
	// Initial piece position constants
	public static final int POSITIONS_FIXED = 0;
	public static final int POSITIONS_RANDOM = 1;
		
	// Player/piece color constants
	public static final byte COLOR_UNSET = -1;
	public static final byte COLOR_BLACK = 0;
	public static final byte COLOR_WHITE = 1;
	
	// Dimensions of the game board
	public final static int COLS = 9;
	public final static int ROWS = 9;
	
	// Illegal space
	public final static byte NULL = -1;
	
	// Empty space
	public final static byte NONE = 127;
	
	// Types
	public final static byte TOTT   = 0;
	public final static byte TZARRA = 2;
	public final static byte TZAAR  = 4;
	
	// Convenience combinations
	public final static byte BTO = COLOR_BLACK | TOTT;
	public final static byte BTA = COLOR_BLACK | TZARRA;
	public final static byte BTZ = COLOR_BLACK | TZAAR;
	public final static byte WTO = COLOR_WHITE | TOTT; 
	public final static byte WTA = COLOR_WHITE | TZARRA; 
	public final static byte WTZ = COLOR_WHITE | TZAAR;
	
	// Radius from hex center to outer vertex (also, side length)
	private float mOuterHexRadius = 0;
	
	// Flat-side-up orientation: hex height
	private float mOuterHexHeight = 0;
	
	// Flat-side-up orientation: hex width
	private float mOuterHexWidth = 0;
	
	// X-coordinate of distance between outer vertices 2 edges apart
	//      +-----+
	//     /       \
	//    +         +
	//     \       /
	//      +-----+ 
	//    |<- s ->|
	//
	private float mOuterHexSide = 0;
	
	// Top-left x- and y-coordinates of hexagon (NW vertex)
	private float mOuterHexX = 0;
	private float mOuterHexY = 0;
	
	// X- and y-coordinates of center of hexagon
	private float mOuterHexCenterX = 0;
	private float mOuterHexCenterY = 0;
	
	private float mInnerHexRadius = 0;
	private float mInnerHexHeight = 0;
	private float mInnerHexWidth = 0;
	private float mInnerHexSide = 0;
	
	// Fixed board layout with piece colors and types (no height assigned)
	private final byte[][] mFixedBoard = {
		{NULL, NULL, BTO, BTO, BTO,  BTO, WTO, NULL, NULL},  // col 0
		{NULL, WTO,  BTA, BTA, BTA,  WTA, WTO, NULL, NULL},  // col 1
		{NULL, WTO,  WTA, BTZ, BTZ,  WTZ, WTA, WTO,  NULL},  // col 2
		{WTO,  WTA,  WTZ, BTO, WTO,  WTZ, WTA, WTO,  NULL},  // col 3
		{WTO,  WTA,  WTZ, WTO, NULL, BTO, BTZ, BTA,  BTO },  // col 4
		{BTO,  BTA,  BTZ, BTO, WTO,  BTZ, BTA, BTO,  NULL},  // col 5
		{NULL, BTO,  BTA, BTZ, WTZ,  WTZ, BTA, BTO,  NULL},  // col 6
		{NULL, BTO,  BTA, WTA, WTA,  WTA, BTO, NULL, NULL},  // col 7
		{NULL, NULL, BTO, WTO, WTO,  WTO, WTO, NULL, NULL}   // col 8
	};

	// Current game board 
	private byte[][] mBoard;
	
	// Piece counters
	private int mBtoCount = 0;
	private int mBtaCount = 0;
	private int mBtzCount = 0;
	private int mWtoCount = 0;
	private int mWtaCount = 0;
	private int mWtzCount = 0;
	
	/**
	 * 
	 * @param piece
	 * @return
	 */
	public static final byte extractPieceColor(byte piece) {
		return (byte) (piece & 1);
	}
	
	/**
	 * 
	 * @param piece
	 * @return
	 */
	public static final byte extractPieceType(byte piece) {
		return (byte) (piece & 6);
	}
	
	/**
	 * 
	 * @param piece
	 * @return
	 */
	public static final byte extractPieceColorAndType(byte piece) {
		return (byte) (piece & 7);
	}
	
	/**
	 * 
	 * @param piece
	 * @return
	 */
	public static final int extractPieceHeight(byte piece) {
		return piece >> 3;
	}	
	
	/**
	 * Constructor initializes the game board.
	 * 
	 * @param context
	 */
	public GameBoard() {
		init();
	}
	
	/**
	 * Copy constructor.
	 */
	public GameBoard(GameBoard that) {
		this.mBoard = new byte[COLS][ROWS];
		for (int i = 0; i < COLS; ++i)
			System.arraycopy(that.mBoard[i], 0, this.mBoard[i], 0, that.mBoard[i].length);
		
		this.mBtoCount = that.mBtoCount;
		this.mBtaCount = that.mBtaCount;
		this.mBtzCount = that.mBtzCount;
		this.mWtoCount = that.mWtoCount;
		this.mWtaCount = that.mWtaCount;
		this.mWtzCount = that.mWtzCount;
	}

	/**
	 * Initializes the game board.
	 * 
	 * Since the fixed board only contains encodings for piece type and color,
	 * we encode the initial stack height as the board is built by adding 8 to
	 * the base value.
	 */
	private void init() {
		mBoard = new byte[COLS][ROWS];
		
		for (int col = 0; col < COLS; ++col) {
			for (int row = 0; row < ROWS; ++row) {
				if (mFixedBoard[col][row] == NULL || mFixedBoard[col][row] == NONE) {
					mBoard[col][row] = mFixedBoard[col][row];
				}
				else {
					mBoard[col][row] = (byte) (mFixedBoard[col][row] + 8);
					incrementPieceCount(getPieceColorAndType(col, row));
				}
			}
		}
	}
	
	/**
	 * Randomize the pieces on the board.
	 */
	public void randomize() {
		Random rand = new Random();
		
		for (int col = 0; col < GameBoard.COLS; ++col) {
			for (int row = 0; row < GameBoard.ROWS; ++row) {
				// Skip illegal spaces
				if (mBoard[col][row] == GameBoard.NULL)
					continue;
					
				// Get a random (valid) space that hasn't been swapped yet
				int rcol, rrow;
				do {
					rcol = col + rand.nextInt(GameBoard.COLS - col);
					rrow = row + rand.nextInt(GameBoard.ROWS - row);
				} while (mBoard[rcol][rrow] == GameBoard.NULL);
				
				// Swap the spaces
				byte temp = mBoard[rcol][rrow];
				mBoard[rcol][rrow] = mBoard[col][row];
				mBoard[col][row] = temp;
			}
		}
	}
	
	/**
	 * Calculates all the various outer and inner hex constants needed, based
	 * on the given screen width.
	 * 
	 * @param width
	 * @param yPad
	 */
	public void calculateHexConstants(int width, int yPad) {
		mOuterHexRadius = width / 2;
		mOuterHexHeight = (float) Math.sqrt(3) * mOuterHexRadius;
		mOuterHexWidth  = 2 * mOuterHexRadius;
		mOuterHexSide   = (float) 1.5 * mOuterHexRadius;
		
		mInnerHexRadius = mOuterHexRadius / 4;
		mInnerHexHeight = mOuterHexHeight / 4;
		mInnerHexWidth  = 2 * mInnerHexRadius;
		mInnerHexSide   = (float) 1.5 * mInnerHexRadius;

		// Define the starting coordinates of the main hex
		mOuterHexX = (width / 2) - (mOuterHexHeight / 2);
		mOuterHexY = mOuterHexSide - mOuterHexRadius + yPad;
		
		mOuterHexCenterX = getHexCenterX();
		mOuterHexCenterY = getHexCenterY();
	}
	
	/**
	 * Calculates the line endpoints for the outer hexagon's NW -> SW side. The
	 * hexagon is then drawn by repeating this 6 times, rotating 60 degrees each
	 * time.
	 * 
	 * @return array of points
	 */
	public float[] getOuterHexPoints() {
		float pts[] = new float[4];
		
		// NW -> SW side
		pts[0] = mOuterHexX;
		pts[1] = mOuterHexY;
		pts[2] = pts[0];
		pts[3] = pts[1] + mOuterHexRadius;

		return pts;
	}
	
	/**
	 * Calculates the line endpoints for the inner grid lines. The grid is 
	 * covered by these same lines 3 times, rotated 120 degrees each time. 
	 * 
	 * @param startX x-coordinate of northwest corner of main hexagon
	 * @param startY y-coordinate of northwest corner of main hexagon
	 * @return array of points 
	 */
	public float[] getGridPoints(float startX, float startY) {
		float pts[] = new float[32]; // 8 lines, 4 points each
		float h, x, y, theta;        // triangle variables (h = hypotenuse)
		
		int j = 0;
		for (int i = 1; i < (COLS - 1); ++i) {
			h = mInnerHexRadius * i;
			x = (mInnerHexHeight / 2 ) * i;
			theta = (float) Math.acos(x / h);
			y = h * (float) Math.sin(theta);
			
			// Reduce the y-coordinate for the right half of the board
			if (i > 4) {
				float ystep = y / i;
				y -= ystep * 2 * (i - 4);
			}
			
			pts[j++] = startX + x;
			pts[j++] = startY - y;
			
			// Draw the center line in two segments
			if (i == 4) {
				// End coordinates for line segment 1
				pts[j++] = startX + x;
				pts[j++] = startY + mInnerHexRadius;
				
				// Start coordinates for line segment 2
				pts[j++] = startX + x;
				pts[j++] = startY + (3 * mInnerHexRadius);
			}
			
			pts[j++] = startX + x;
			pts[j++] = startY + mOuterHexRadius + y;
		}
		
		return pts;
	}
	
	/**
	 * Calculates the center x coordinate of the board.
	 * 
	 * @return center x coordinate
	 */
	public float getHexCenterX() {
		return mOuterHexX + (mOuterHexHeight / 2);
	}
	
	/**
	 * Calculates the center y coordinate of the board.
	 * 
	 * @return center y coordinate
	 */
	public float getHexCenterY() {
		return mOuterHexY + (mOuterHexRadius / 2);
	}
	
	/**
	 * Calculates the array row under the given y-coordinate and column. 
	 * 
	 * @param y y-coordinate
	 * @param col column number
	 * @return row under coordinates, or -1 if out of range
	 */
	public int getRow(float y, int col) {
		if (col < 0 || col > (COLS - 1))
			return -1;
		
		// Y-coordinate of the north point of the board
		float outerHexTop = mOuterHexY - (mInnerHexRadius * 2);
		
		// Incoming y-coordinate, mapped to the board coordinates
		float y2 = y - outerHexTop; 
		
		// Offset to account for odd columns 
		float offset = (col % 2) * (mInnerHexRadius / 2);
		
		int row = (int) Math.round((y2 - offset) / mInnerHexRadius);
		if (row < 0 || row > (ROWS - 1)) 
			return -1;
		else
			return row;
	}
	
	/**
	 * Calculates the array column under the given x-coordinate.
	 * 
	 * @param x x-coordinate
	 * @return column under x-coordinate, or -1 if out of range
	 */
	public int getColumn(float x) {
		// Incoming x-coordinate, mapped to the board coordinates
		float x2 = x - mOuterHexX;
		
		int col = (int) Math.round(x2 / (mInnerHexHeight / 2));
		if (col < 0 || col > (COLS - 1))
			return -1;
		else
			return col;
	}
	
	/**
	 * Calculates the x-coordinate for the given column.
	 * 
	 * Note that this is the same expression as getColumn(), solved for x 
	 * instead of col.
	 * 
	 * @param col column 
	 * @return x-coordinate
	 */
	public float getVertexX(int col) {
		if (col < 0 || col > (COLS - 1)) 
			throw new IllegalArgumentException(String.format("Invalid column value (%d)!", col));
		
		float x = mOuterHexX;
		x += col * (mInnerHexHeight / 2);
		return x;
	}
	
	/**
	 * Calculates the y-coordinate for the given row and column.
	 * 
	 * Note that this is the same expression as getRow(), solved for y instead
	 * of row.
	 * 
	 * @param col column
	 * @param row row
	 * @return y-coordinate
	 */
	public float getVertexY(int col, int row) {
		if (col < 0 || col > (COLS - 1) || row < 0 || row > (ROWS - 1)) 
			throw new IllegalArgumentException(String.format("Invalid column (%d) or row (%d) value!", col, row));
		
		// Y-coordinate of the north point of the board
		float outerHexTop = mOuterHexY - (mInnerHexRadius * 2);
		
		// Offset to account for odd columns 
		float offset = (col % 2) * (mInnerHexRadius / 2);
		
		float y = outerHexTop;
		y += (mInnerHexRadius * row) + offset;
		return y;
	}

	/**
	 * Gets the value of the board space at the specified row and column.
	 * 
	 * @param col
	 * @param row
	 * @return
	 */
	public byte getPiece(int col, int row) {
		if (col < 0 || col > (COLS - 1) || row < 0 || row > (ROWS - 1))
			return NULL;
		else
			return mBoard[col][row];
	}
	
	/**
	 * Gets the piece color at the specified row and column.
	 * 
	 * Since the color is encoded in bit 0, it can be retrieved by applying a
	 * bitwise AND with the bitmask 00000001.
	 * 
	 * @param col
	 * @param row
	 * @return
	 */
	public byte getPieceColor(int col, int row) {
		if (col < 0 || col > (COLS - 1) || row < 0 || row > (ROWS - 1) || mBoard[col][row] == NULL)
			return NULL;
		else
			return extractPieceColor(mBoard[col][row]);
	}
	
	/**
	 * Gets the piece type at the specified row and column.
	 * 
	 * Since the type is encoded in bits 1-2, it can be retrieved by applying a
	 * bitwise AND with the bitmask 00000110.
	 * 
	 * @param col
	 * @param row
	 * @return
	 */
	public byte getPieceType(int col, int row) {
		if (col < 0 || col > (COLS - 1) || row < 0 || row > (ROWS - 1) || mBoard[col][row] == NULL)
			return NULL;
		else
			return extractPieceType(mBoard[col][row]);
	}
	
	/**
	 * Gets the piece color and type at the specified row and column.
	 * 
	 * Since the color and type are encoded in bits 0-2, their combined value
	 * can be retrieved by applying a bitwise AND with the bitmask 00000111.
	 * 
	 * @param col
	 * @param row
	 * @return
	 */
	public byte getPieceColorAndType(int col, int row) {
		if (col < 0 || col > (COLS - 1) || row < 0 || row > (ROWS - 1) || mBoard[col][row] == NULL)
			return NULL;
		else
			return extractPieceColorAndType(mBoard[col][row]);
	}
	
	/**
	 * Gets the stack height of the specified space.
	 * 
	 * Since the height is encoded in bytes 3-7, it can be retrieved by right-
	 * shifting the byte by 3 bits.
	 * 
	 * @param col
	 * @param row
	 * @return
	 */
	public int getStackHeight(int col, int row) {
		if (col < 0 || col > (COLS - 1) || row < 0 || row > (ROWS - 1))
			return NULL;
		else
			return extractPieceHeight(mBoard[col][row]);
	}
	
	/**
	 * Makes a move on the board.
	 * 
	 * @param fromCol
	 * @param fromRow
	 * @param toCol
	 * @param toRow
	 */
	public void move(int fromCol, int fromRow, int toCol, int toRow) {
		// Validate arguments
		if (fromCol < 0 || fromCol > (COLS - 1) || fromRow < 0 || fromRow > (ROWS - 1)) { 
			throw new IllegalArgumentException(String.format("Invalid source column (%d) or row (%d) value!", fromCol, fromRow));
		}
		else if (toCol < 0 || toCol > (COLS - 1) || toRow < 0 || toRow > (ROWS - 1)) { 
			throw new IllegalArgumentException(String.format("Invalid destination column (%d) or row (%d) value!", toCol, toRow));
		}

		// Decrement the counter for the target piece type
		decrementPieceCount(extractPieceColorAndType(mBoard[toCol][toRow]));
		
		// If move is stacking, update stack height of "from" piece
		if (extractPieceColor(mBoard[fromCol][fromRow]) == extractPieceColor(mBoard[toCol][toRow])) {
			int fromHeight = extractPieceHeight(mBoard[fromCol][fromRow]);
			int toHeight = extractPieceHeight(mBoard[toCol][toRow]);
			
			// Add the stacks and left shift result to location of height bits
			// e.g., if the height is 00000010 (2), then newHeight is 00010000 (16). 
			int newHeight = (fromHeight + toHeight) << 3;
			
			// Write 0s to height bits, then set the new height
			mBoard[fromCol][fromRow] = (byte) ((mBoard[fromCol][fromRow] & 7) | newHeight);
		}
			
		// Move the piece on the board
		mBoard[toCol][toRow] = mBoard[fromCol][fromRow];
		mBoard[fromCol][fromRow] = NONE;		
	}

	public float getOuterHexRadius() {
		return mOuterHexRadius;
	}

	public void setOuterHexRadius(float outerHexRadius) {
		mOuterHexRadius = outerHexRadius;
	}

	public float getOuterHexHeight() {
		return mOuterHexHeight;
	}

	public void setOuterHexHeight(float outerHexHeight) {
		mOuterHexHeight = outerHexHeight;
	}

	public float getOuterHexWidth() {
		return mOuterHexWidth;
	}

	public void setOuterHexWidth(float outerHexWidth) {
		mOuterHexWidth = outerHexWidth;
	}

	public float getOuterHexSide() {
		return mOuterHexSide;
	}

	public void setOuterHexSide(float outerHexSide) {
		mOuterHexSide = outerHexSide;
	}

	public float getOuterHexX() {
		return mOuterHexX;
	}

	public void setOuterHexX(float outerHexX) {
		mOuterHexX = outerHexX;
	}

	public float getOuterHexY() {
		return mOuterHexY;
	}

	public void setOuterHexY(float outerHexY) {
		mOuterHexY = outerHexY;
	}

	public float getOuterHexCenterX() {
		return mOuterHexCenterX;
	}

	public void setOuterHexCenterX(float outerHexCenterX) {
		mOuterHexCenterX = outerHexCenterX;
	}

	public float getOuterHexCenterY() {
		return mOuterHexCenterY;
	}

	public void setOuterHexCenterY(float outerHexCenterY) {
		mOuterHexCenterY = outerHexCenterY;
	}

	public float getInnerHexRadius() {
		return mInnerHexRadius;
	}

	public void setInnerHexRadius(float innerHexRadius) {
		mInnerHexRadius = innerHexRadius;
	}

	public float getInnerHexHeight() {
		return mInnerHexHeight;
	}

	public void setInnerHexHeight(float innerHexHeight) {
		mInnerHexHeight = innerHexHeight;
	}

	public float getInnerHexWidth() {
		return mInnerHexWidth;
	}

	public void setInnerHexWidth(float innerHexWidth) {
		mInnerHexWidth = innerHexWidth;
	}

	public float getInnerHexSide() {
		return mInnerHexSide;
	}

	public void setInnerHexSide(float innerHexSide) {
		mInnerHexSide = innerHexSide;
	}

	public int getPieceCount(byte colorAndType) {
		int count = 0;
		
		switch (colorAndType) {
			case WTO:
				count = mWtoCount;
				break;
			case WTA:
				count = mWtaCount;
				break;
			case WTZ:
				count = mWtzCount;
				break;
			case BTO:
				count = mBtoCount;
				break;
			case BTA:
				count = mBtaCount;
				break;
			case BTZ:
				count = mBtzCount;
				break;
			default:
				break;
		}
		
		return count;
	}
	
	private void decrementPieceCount(byte colorAndType) {
		switch (colorAndType) {
			case WTO:
				--mWtoCount;
				break;
			case WTA:
				--mWtaCount;
				break;
			case WTZ:
				--mWtzCount;
				break;
			case BTO:
				--mBtoCount;
				break;
			case BTA:
				--mBtaCount;
				break;
			case BTZ:
				--mBtzCount;
				break;
			default:
				break;
		}
	}

	private void incrementPieceCount(byte colorAndType) {
		switch (colorAndType) {
			case WTO:
				++mWtoCount;
				break;
			case WTA:
				++mWtaCount;
				break;
			case WTZ:
				++mWtzCount;
				break;
			case BTO:
				++mBtoCount;
				break;
			case BTA:
				++mBtaCount;
				break;
			case BTZ:
				++mBtzCount;
				break;
			default:
				break;
		}
	}
}
