package com.github.littletzaar;

/**
 * A turn consists of a player making one or two moves. The first turn of the 
 * game, made by the white player, consists of a single capturing move. Every
 * other turn in the game has two moves.
 * 
 * @author mgrimm
 */
public class Turn {
	// Player color
	private int player;
	
	// First and (optionally) second move
	private Move first = null;
	private Move second = null;
	
	// Pointer to the previous move 
	private Move prev = null;
	
	Turn(int player) {
		this.player = player;
	}
	
	Turn(int player, Move first) {
		this.player = player;
		this.first = first;
		prev = this.first;
	}
	
	Turn(int player, Move first, Move second) {
		this.player = player;
		this.first = first;
		this.second = second;
		prev = this.second;
	}
	
	public int getPlayer() {
		return player;
	}
	
	public Move getFirst() {
		return first;
	}
	
	public void setFirst(Move first) {
		this.first = first;
		prev = this.first;
	}
	
	public Move getSecond() {
		return second;
	}
	
	public void setSecond(Move second) {
		this.second = second;
		prev = this.second;
	}
	
	public Move getPrev() {
		return prev;
	}
}
