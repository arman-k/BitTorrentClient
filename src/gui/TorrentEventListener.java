/**
 * Wahhahha - finally, a stupid implementation of the observer pattern!
 */
package gui;

public interface TorrentEventListener 
{
	/*
	 * All status event listeners should respond to these events
	 */
	public void onStatusUpdated(int torrentIndex, String status);
	public void onProgressMade(int torrentIndex, Double progress);
}
