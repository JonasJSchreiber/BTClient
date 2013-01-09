package ru.bit.torrent;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;

import ru.gui.PeerDisplay;

/**
 * This class handles the overall flow of the application.  When the tracker returns peers, it checks, 
 * configures, and starts them. When pieces are completed by a peer it validates and saves.  It keeps track of
 * which pieces we have and thus facilitates which pieces should be requested, and when to choke or unchoke peers. 
 * Also has a Tracker with which it interacts.
 * 
 * @author Dylan Murray
 * @author Jonas Schreiber
 * @author Charles Zuppe
 *  
 */    
public class Manager {

	/**
	 * The path where user wants to ultimately download the file to.
	 */
	private String dlPath;

	/**
	 * The TorrentInfo object corresponding the torrent specified in the command line.
	 */
	private TorrentInfo torrentInfo;

	/**
	 * The peerID the user will use to connect to tracker.
	 */
	private byte[] peerID;

	/**
	 * Creates a tracker object through which we can communicate with.
	 */
	private Tracker tracker;

	/**
	 * The list of valid peers (currently RUBT11) that have a piece of the file we're trying to download.
	 */
	public ArrayList<Peer> peers = new ArrayList<Peer>();

	/**
	 * A boolean array used to store all the pieces we download. This ensures that even when we download pieces out of order
	 * we have a way of organizing them.
	 */
	public boolean[] myPieces;
	
	/**
	 * Pieces in progress - any pieces that are currently being downloaded from a peer will be kept track of here.
	 */
	public boolean[] piecesInProg;
    
    /**
     *  FileAccess provides this Random Access
     */
    public FileAccess fa;
    
    /**
     *  A Runnable class which accepts quit messages.
     */
	@SuppressWarnings("unused")
	private Thread rc;
	
	/**
	 * A Thread object corresponding to the Tracker class
	 * This must be on its own separate Thread, as it sleeps
	 * for the min interval specified by the tracker. Therefore,
	 * to make the Tracker shut down in a timely fashion, Manager
	 * must have a handle on it, to call in the exit() method. 
	 */
	private Thread trackerThread;

	/**
	 * Objects used to listen for connecting peers.
	 */
	private SocketListener socketListener;
	
	/**
	 * A TrackerStats object, which contains the statistics
	 * relevant to the tracker for use between sessions
	 */
	private TrackerStats trackerStats;
	
	/**
	 * A boolean which lets threads know when to stop
	 */
	public boolean stopThreads;

	/**
	 * An object which contains Performance Analysis utilities
	 */
	public PerformanceAnalyzer pa;
	
	/**
	 * A Thread that the analysis is handled on. Executes every 30 seconds
	 */
	private Thread paThread;
	
	/**
	 * A Thread to run GUI to keep track of peer status.
	 */
	private Thread peerDisplay;
	
	/**
	 * The port which the SocketListener has found to be open. 
	 */
	private int listeningPort;
	
	/**
	 * Keeps track of how many peers we have unchoked.
	 */
	public int numUnchoked = 0;

	/**
	 * An array of pieces ordered by rarity/
	 */
	public ArrayList<Integer> piecesByRarity;
	/**
	 * Constructor for Manager class. Initializes instance variables, sets up Threads
	 * facilitates the connection to tracker and peers, and downloads the file.
	 * 
	 * @param dlPath
	 * @param torrentInfo
	 * @param peerID
	 */
	public Manager(String dlPath, TorrentInfo torrentInfo, byte[] peerID) throws Exception {
		this.dlPath = dlPath;
		this.torrentInfo = torrentInfo;
		this.peerID = peerID;
		this.myPieces = new boolean[torrentInfo.piece_hashes.length];
		this.piecesInProg = new boolean[torrentInfo.piece_hashes.length];
		this.fa = new FileAccess(this,  torrentInfo);
		this.listeningPort = 0;

		this.socketListener = new SocketListener(this, this.torrentInfo); 			//SocketListener Thread Setup
		Thread socketListenerThread = new Thread(socketListener);
		socketListenerThread.setName("Socket Listener");
		Runtime.getRuntime().addShutdownHook(socketListenerThread);
		socketListenerThread.start();
		
		Thread.sleep(1000L);														//Give second to find open port

		this.tracker = new Tracker(this, torrentInfo, peerID ); 					//Tracker Thread Setup
		Thread trackerThread = new Thread(this.tracker);
		Runtime.getRuntime().addShutdownHook(trackerThread);
		this.trackerThread = trackerThread;
		this.trackerThread.setName("Tracker Thread");
		this.trackerStats = new TrackerStats(dlPath, this.tracker);
		setTrackerStatsDownloaded();
		trackerThread.start();
		
		populatePeers(this.tracker.getPeerList());									//Peers Setup
		connectToPeers();
		
		this.pa = new PerformanceAnalyzer(this, torrentInfo.piece_hashes.length); 	//Performance Analyzer Thread Setup
		this.paThread = new Thread(pa);
		this.paThread.setName("Performance Analyzer");
		Runtime.getRuntime().addShutdownHook(paThread);
		paThread.start();
		
		this.peerDisplay = new Thread(new PeerDisplay(this));
		peerDisplay.setName("Peer Display");
		Runtime.getRuntime().addShutdownHook(peerDisplay);
		peerDisplay.start();
				
		Thread runtimeCommands = new Thread(new RuntimeCommands(this)); 			//RuntimeCommands Thread Setup
		runtimeCommands.setName("Runtime Commands");
		Runtime.getRuntime().addShutdownHook(runtimeCommands);
		//TODO: So, runtimeCommands isn't actually running on it's own thread- you're just calling run(); you'd need to call start()
		runtimeCommands.run();
		
		this.stopThreads = false;
	}

	/**
	 * Iterate through the list of peers, and create a new list that only meets the specified requirements.
	 * @param trackerPeers
	 */
	public void populatePeers(ArrayList<Peer> trackerPeers){
		System.out.println("PeerList returned from tracker: (new valid peers accentuated with **)");
		//Iterate through the list of peers, and add them to new list if they match the specified requirements.
		for (int currPeerIndex = 0; currPeerIndex < trackerPeers.size(); currPeerIndex++){
			Peer currPeer = trackerPeers.get(currPeerIndex);
			String id = new String(currPeer.getPeerID());
			System.out.print(currPeerIndex + ". " + currPeer + " on Port:" + currPeer.getPort());
			//Rob's filter
			if(id.substring(0, 6).equals("RUBT11") && (currPeer.getIp().equals("128.6.5.130") || currPeer.getIp().equals("128.6.5.131"))){
				//If we already have this peer in our list, don't add it, we only want new peers.
				if(!Tracker.containsPeer(this.peers, currPeer)){
					this.peers.add(trackerPeers.get(currPeerIndex));
					System.out.print("**");
				}
			}
			System.out.println("");
		}
	}
	
	/**
	 * Sets up a TCP connection with each valid peer (creates sockets).
	 */
	public void connectToPeers() {
		for(Peer p: peers){
			if(!p.amConnected()){
				MessageHandler mH = new MessageHandler(this, p, torrentInfo);
				Thread messageHandler = new Thread(mH);
				messageHandler.setName(p.toString());
				Runtime.getRuntime().addShutdownHook(messageHandler);
				p.setMessageHandler(messageHandler);
				p.setmH(mH);
				messageHandler.start();
				p.setConnected(true);
			}
		}	
	}
	
	/**
     * Checks to see if we've successfully received all pieces of the file so we can send Event Complete to Tracker.
     * @return
     */
    public boolean isDownloadComplete(){
        for(int i=0; i < myPieces.length; i++){
            if(myPieces[i] == false){
                return false;
            }
        }
        tracker.setEvent(Tracker.EVENT_COMPLETED);
        return true;
    }

	/**
	 * Checks the received piece's hashcode against the torrent file's to see if the piece is valid.
	 * Some of this code is attributable to Rob Moore
	 * @param block
	 */
	public boolean verifyPiece(PieceMessage piece){
		ByteBuffer all_hashes = (ByteBuffer)torrentInfo.info_map.get(TorrentInfo.KEY_PIECES);
        byte[] all_hashes_array = all_hashes.array();
        byte[] temp_buff = new byte[20];
        int index = piece.index;
        System.arraycopy(all_hashes_array,index*20,temp_buff,0,20);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            digest.update(piece.block);
            byte[] info_hash = digest.digest();
            if (Arrays.equals(temp_buff, info_hash))
                return true;
            else
            {
                System.out.println("Hash mismatch, retrying piece.");
                return false;
            }
        } catch (NoSuchAlgorithmException e) {
        	System.err.println("Error verifying the piece.");
            e.printStackTrace();
        }
        return true;
	}
	
	/**
	 * Saves a piece into our byte[] fileBytes (that will eventually be written to file).
	 * @param piece
	 * @throws IOException 
	 */
	public void savePiece(PieceMessage piece) throws IOException {
		myPieces[piece.index] = true;
		fa.writePiece(piece.index, piece.block);
		//Tell all connected peers of your new piece.
		isDownloadComplete();
		sendHaveMessages(piece.index);
	}
	
	/**
	 * Queues Have Messages to all relevant peers when a piece has been successfully downloaded.
	 * @param pieceIndex
	 * @throws IOException
	 */
	public void sendHaveMessages(int pieceIndex) throws IOException{
		for(Peer p: peers){
				p.messageQueue.add(new HaveMessage(pieceIndex));
		}
	}
	
	/**
	 * Gets the peerID.
	 * @return
	 */
	public byte[] getPeerID(){
		return peerID;
	}
	
	public boolean[] getPieces(){
		return myPieces; 
	}
	
	@SuppressWarnings("static-access")
	public void exit() throws IOException, InterruptedException {

		tracker.setEvent(tracker.EVENT_STOPPED);
		stopThreads = true;
		trackerThread.interrupt();
		socketListener.disconnect();
		paThread.interrupt();
		peerDisplay.interrupt();
		
		for (Peer p : peers)
		{
			if (p.amConnected())
				p.disconnect();

		}
		
		//Sleep long enough for our message handlers to finish up and our file access to close properly
		Thread.sleep(1000L);
		
		System.out.println("\nStats:");
		if (trackerStats.getUploaded() == 0)
			System.out.println("Uploaded:\t" + tracker.getUploaded());
		else 
			System.out.println("Uploaded:\t" + trackerStats.getUploaded());
		if (trackerStats.getDownloaded() > torrentInfo.file_length)
			trackerStats.setDownloaded(torrentInfo.file_length);
		else if (isDownloadComplete())
			trackerStats.setDownloaded(torrentInfo.file_length);
		else if (trackerStats.getDownloaded() == 0)
			trackerStats.setDownloaded(tracker.getDownloaded());
		System.out.println("Downloaded:\t" + trackerStats.getDownloaded());
		System.out.println("Left:\t\t" + (torrentInfo.file_length - trackerStats.getDownloaded()));
		fa.close();
		trackerStats.close();
		System.out.println("\nGoodbye!");

	}

	/**
	 * Used by FileAccess
	 * @return
	 */
	public String getDlPath() {
		return dlPath;
	}
	
	/**
	 * To be used in the event that the program exited 
	 * before completing the download the prior time it was run
	 * @param myPieces
	 */
	public void setMyPieces(boolean[] myPieces) {
		this.myPieces = myPieces;
	}

	/**
	 * Passes control to Tracker's add to downloaded method.
	 */
	public void addToDownloaded(int amount){
		this.tracker.addToDownloaded(amount);
	}
	
	/**
	 * Since TrackerStats does not take Manager as a param,
	 * in order to display correct amount downloaded, we must
	 * set it from Manager. 
	 */
	
	
	public void setTrackerStatsDownloaded() {
		this.trackerStats.incrementDownloaded(getNumPiecesWeHave() * this.torrentInfo.piece_length);
	}
	
	public int getNumPiecesWeHave() {
		int numPiecesWeHave = 0;
		for (boolean b : myPieces)
		{
			if (b)
				numPiecesWeHave++;
		}
		return numPiecesWeHave;
	}
	
	public int getNumPiecesWeNeed() {
		int numPiecesWeNeed = 0;
		for (int i = 0; i < torrentInfo.piece_hashes.length; i++)
		{
			if (!myPieces[i] && !piecesInProg[i])
				numPiecesWeNeed++;
		}
		return numPiecesWeNeed;
	}
	
	public ArrayList<Integer> getUnsortedNeededPieces() {
		ArrayList<Integer> unsortedNeededPieces = new ArrayList<Integer>();
		for (int i = 0; i < torrentInfo.piece_hashes.length; i++)
		{
			if (!myPieces[i]  && !piecesInProg[i])
				unsortedNeededPieces.add(i);
		}
		return unsortedNeededPieces;
	}

	/**
	 * Returns the performance analyzer
	 * @return
	 */
	public PerformanceAnalyzer getPA() {
		return pa;
	}
	
	/**
	 * Passes control to Tracker's add to uploaded method.
	 */
	
	public void addToUploaded(int amount) {
		this.tracker.addToUploaded(amount);
	}

	public int getListeningPort() {
		return listeningPort;
	}

	public void setListeningPort(int listeningPort) {
		this.listeningPort = listeningPort;
	}
}