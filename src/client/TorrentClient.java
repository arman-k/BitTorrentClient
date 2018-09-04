/**
 * This is the main class of BitTorrentClient.
 * This will use all the other classes and their methods to implement a working BitTorrent Client.
 * This is still under construction.
 */

package client;

import java.util.Scanner;

import tools.Util;
import torrent.TorrentManager;

public class TorrentClient 
{
	TorrentManager torrentManager;
	Scanner sc;
	
	/**
	 * The main driver function of the BitTorrentClient.
	 * @param args	Any arguments from the user.
	 */
	public static void main(String args[])
	{
		TorrentClient torrentClient = new TorrentClient();
		torrentClient.initialize();
		int input;
		
		System.out.println("BitTorrent Client Version 0.001");
		while (true)
		{
			System.out.println("Please enter the number corresponding to what you want to do: ");
			System.out.println(
					"(1) Load a new torrent\n"
					+ "(2) Print metainfo of a torrent\n"
					+ "(3) Start downloading a torrent\n"
					+ "(4) Exit \n");
			
			input = torrentClient.sc.nextInt();
			switch (input)
			{
				case (1):
					torrentClient.loadTorrent();
					break;
				case (2):
					torrentClient.chooseTorrentMetainfo();
					break;
				case (3):
					torrentClient.chooseTorrentToDownload();
					break;
				case (4):
					System.out.println("Exiting...");
					torrentClient.exit();
					return;
			}
		}
	}
	
	public void loadTorrent()
	{
		System.out.println("Enter .torrent file to load: ");
		sc.nextLine();
		String filename = sc.nextLine();
		torrentManager.addTorrent(filename);
		System.out.println("Torrent \"" + filename + "\" added!");
	}
	
	public void chooseTorrentMetainfo()
	{
		System.out.println("List of torrents loaded: ");
		torrentManager.printTorrents();
		System.out.println("Please enter which torrent's metainfo you would like to see: ");
		int input = sc.nextInt();
		torrentManager.printMetainfo(input);
	}
	
	public void chooseTorrentToDownload()
	{
		System.out.println("List of torrents loaded: ");
		torrentManager.printTorrents();
		System.out.println("Please enter which torrent you would like to download: ");
		int input = sc.nextInt();
		torrentManager.startTorrent(input);
	}
	
	public void exit()
	{
		torrentManager.exit();
		System.out.println("Shutting down client...");
	}
	
	public void initialize()
	{
		System.out.println("Initializing client...");
		System.out.println("Peer id for this session: " + Util.URLEncode(Environment.getInstance().getPeerId().array()));
		torrentManager = new TorrentManager();
		sc = new Scanner(System.in);
	}
}
