package ru.bit.torrent;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Random;

/**
 * RUBTClient is used to read in and verify command line arguments, initialize our torrent info, and
 * create a Manager object to start the application.
 * 
 * @author Dylan Murray
 * @author Jonas Schreiber
 * @author Charles Zuppe
 * 
 */
public class RUBTClient {
	/**
	 * The path of the .torrent file.
	 */
	private String torrentPath;

	/**
	 * The designated file name.
	 */
	private String fileName;

	/**
	 * The TorrentInfo object corresponding the torrent specified in the command line.
	 */
	private TorrentInfo torrentInfo;

	/**
	 * The peerID the user will use to connect to tracker.
	 */
	private byte[] peerID;

	/**
	 * RUBitTorrent Constructor, takes the arguments given at command line.
	 * @param torrentPath  The path of the torrent file.
	 * @param dlPath       The path where you want to save downloaded file to.
	 */
	public RUBTClient(String torrentPath, String fileName){
		this.torrentPath = torrentPath;
		this.fileName = fileName;
	}

	/**
	 * Main method.
	 * 
	 * @param args
	 * 	args[0]: .torrent path
	 *  args[1]: designated file name
	 */
	public static void main(String[] args){
		if(args.length != 2){
			System.err.println("Incorrect number of arguments.");
			System.err.println("Usage: RUBitTorrent <.torent path> <designated file name>");
			return;
		}
		RUBTClient rubt = new RUBTClient(args[0], args[1]);
		rubt.go();
	}

	/**
	 * Starts the program. (No longer weighed down by static method main)
	 */
	@SuppressWarnings("unused")
	private void go(){
		//Generates a peerID for user.
		generatePeerID();

		//Creates the TorrentInfo object for rubt, or ends the program if unsuccessful.
		if(!initializeTorrentInfo(torrentPath)){
			return;
		}
		
		try{
			System.out.println("Enter \"quit\" to exit at any time.");
			Thread.sleep(2000L);
			Manager manager = new Manager(this.fileName, this.torrentInfo, this.peerID);
		}catch(IOException e){
			System.err.println("Couldn't connect to peer.");
		} catch (Exception e) {
			e.printStackTrace();
		}
		return;
	}

	/**
	 * Generates a random Peer ID.
	 */
	private void generatePeerID(){
		Random r = new Random(System.currentTimeMillis());
		byte[] peerId = new byte[20];  
		peerId[0] = 'G';
		peerId[1] = 'R';
		peerId[2] = 'O';
		peerId[3] = 'U';
		peerId[4] = 'P';
		peerId[5] = '0';
		peerId[6] = '9';
		for(int i = 7; i < 20; ++i){
			peerId[i] = (byte)('A' + r.nextInt(26));
		}
		this.peerID = peerId;
	}

	/**
	 * Creates the TorrentInfo object.
	 * @param filename name of the .torrent file
	 * @return true if successful or false if exception caught
	 */
	private boolean initializeTorrentInfo(String filename){
		File file = new File(filename);
		//Open a FileInputStream to given torrent file.
		FileInputStream fis;
		try {
			fis = new FileInputStream(file);
		} catch (FileNotFoundException e) {
			System.err.println("File not found.");
			e.printStackTrace();
			return false;
		}

		//The byte[] buffer to be used to create TorrentInfo.
		byte[] torrentFileBytes = new byte[(int)file.length()];

		//Opens DataInputStream to extract raw bytes from torrent file.
		try {
			new DataInputStream(fis).readFully(torrentFileBytes);
			fis.close();
		} catch (IOException e) {
			System.err.println("Unable to read bytes from file.");
			e.printStackTrace();
			return false;
		}

		//Create TorrentInfo   
		try {
			torrentInfo = new TorrentInfo(torrentFileBytes);
		} catch (BencodingException e) {
			System.err.println(e.toString());
			e.printStackTrace();
			return false;
		}
		return true;
	} 

	public byte[] getPeerID() {
		return peerID;
	}

	public String getFileName() {
		return fileName;
	}

	
}
