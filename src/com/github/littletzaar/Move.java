package com.github.littletzaar;

import android.graphics.Point;

/**
 * A move consists of a source cell and a destination cell. The x,y coordinates
 * of the Point objects represent indexes into the board array. 
 * 
 * @author mgrimm
 */
public class Move {
	public Point from = null;
	public Point to = null;
	
	// Move type (capture or stack)
	public int type = TzaarGame.MOVE_UNSET;
	
	Move(int type, Point from, Point to) {
		this.from = from;
		this.to = to;
		this.type = type;
	}
	
	Move(int type, int fromCol, int fromRow) {
		this.from = new Point(fromCol, fromRow);
		this.type = type;
	}
	
	Move(int type, int fromCol, int fromRow, int toCol, int toRow) {
		this.from = new Point(fromCol, fromRow);
		this.to = new Point(toCol, toRow);
		this.type = type;
	}
	
	public String toString() {
		String out = "";
		
		if (from != null)
			out += String.format("Move(from " + from.toString());
		if (to != null)
			out += String.format(" to " + to.toString());
			
		out += ")";
		
		return out;
	}
	
	public boolean equals(Object object) {
		boolean equals = false;
		
		if (object != null && object instanceof Move) {
			Move that = (Move) object;
			
			if (this.from != null && that.from != null) {
				equals = this.from.x == that.from.x && this.from.y == that.from.y;
				
				if (this.to != null)
					equals &= that.to != null && this.to.x == that.to.x && this.to.y == that.to.y;
				else
					equals &= that.to == null;
			}
			else if (this.from == null && that.from == null && this.to == null && that.to == null) {
				equals = true;
			}
		}
		
		return equals;
	}
}
