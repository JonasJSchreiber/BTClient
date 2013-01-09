package ru.bit.torrent;


import java.io.*;
import java.net.*;
import java.nio.*;
import java.util.*;

/**
 * @author Dylan Murray
 * @author Charles Zuppe
 * @author Jonas Schreiber
 * 
 */

public class Tracker implements Runnable {
	
	/**
	 * The manager object that needs to be updated with the current peers from the tracker.
	 */
	private Manager manager;
	
	/**
	 * The TorrentInfo object containing all necessary data to connect to the tracker.
	 */
	private TorrentInfo torrentInfo;
	
	/**
	 * The URL of the tracker with given queries.
	 */
	private URL trackerURL;
	
	/**
	 * The peerID the user will use to connect to tracker.
	 */
	private byte[] peerID;
	
	/**
	 * The port number the user will listen on. Conventionally, we'll start by trying to connect to port 6881, then 6882,
	 * then 6883, etc, until port 6889 where we will give up trying to connect.
	 */
	private int port;
	
	/**
	 * The total amount uploaded so far, encoded in base ten ASCII.
	 */
	private int uploaded;
	
	/**
	 * Downloaded, initialized at 0.
	 */
	private int downloaded = 0;
	
	/**
	 * Amount of the file left to download, initialized at 0.
	 */
	private int left;
	
	/**
	 * This is an optional key which maps to started , completed , or stopped (or empty , which is the same as not being present. 
	 * If not present, this is one of the announcements done at regular intervals. An announcement using started is sent when a 
	 * download first begins, and one using completed is sent when the download is complete. No completed is sent if the file was 
	 * complete when started. Downloaders send an announcement using stopped when they cease downloading.
	 */
	private byte[] event;
	
	/**
	 * Bencoded dictionary containing the keys: interval and peers.
	 */
	private byte[] trackerResponse;
	
	/**
	 * The base dictionary of the tracker response. Takes two keys: interval and peers.
	 */
	private Map<ByteBuffer, Object> trackerResponseMap;
	
	/**
	 * Maps to the number of seconds the downloader should wait between regular re-requests.
	 */
	private int interval;
	
	@SuppressWarnings("unused")
	private int pieces;
	
	@SuppressWarnings("unused")
	private int pieceLength;
	/**
	 * Maps to a list of dictionaries corresponding to peers, 
	 * each of which contains the keys peer id , IP , and port , 
	 * which map to the peer's self-selected ID, IP address or DNS name as a string, 
	 * and port number, respectively.
	 */
	private ArrayList< Peer > peers = new ArrayList<Peer>();
	
	 /**
     * Key used to retrieve the interval.
     */
    public final static ByteBuffer KEY_INTERVAL = ByteBuffer.wrap(new byte[]
    { 'i', 'n', 't', 'e', 'r', 'v', 'a', 'l' });

    /**
     * Key used to retrieve the peers.
     */
    public final static ByteBuffer KEY_PEERS = ByteBuffer.wrap(new byte[]
    { 'p', 'e', 'e', 'r', 's',});
    
    public final static byte[] EVENT_STARTED = new byte[]{'s', 't', 'a', 'r', 't', 'e', 'd'};
    
    public final static byte[] EVENT_STOPPED = new byte[]{'s', 't', 'o', 'p', 'p', 'e', 'd'};
    
    public final static byte[] EVENT_COMPLETED = new byte[]{'c', 'o', 'm', 'p', 'l', 'e', 't', 'e', 'd'};
	
	
	/**
	 * Constructor takes the torrent and user info passed to it and communicates with the Tracker.
	 * @param torrentInfo TorrentInfo object containing all information provided by torrent file.
	 * @param peerID      byte[] containing a generated user peerID.
	 */
	public Tracker(Manager manager, TorrentInfo torrentInfo, byte[] peerID){
		this.port = manager.getListeningPort();
		System.out.println("SocketListener is listening on port: " + this.port);
		this.torrentInfo = torrentInfo;
		this.peerID = peerID;
		this.manager = manager;
		this.left = calculateLeft2();
		this.pieces = torrentInfo.piece_length;
		this.event = EVENT_STARTED;
		
		try {
			generateTrackerURL();
			getTrackerResponse();
			decodeTrackerResponse();
		} catch (IOException e) {
			System.err.println("Unable to connect to tracker.");
			e.printStackTrace();
		}catch( BencodingException be){
			System.err.println("Unable to decode tracker response.");
			be.printStackTrace();
		}
		
		this.event = null;
	}
	
	/**
	 * Generates the tracker URL from the torrent info and a URLEncoder.
	 * @throws IOException The URL was improperly created.
	 */
	private void generateTrackerURL() throws IOException {
	  //String buffer we'll store the url extension in. Initialized to base URL.
	  StringBuffer urlExtension = new StringBuffer( (torrentInfo.announce_url).toString() );
	  
	  //Generate the info_hash parameter and add to URL.
	  urlExtension.append("?info_hash=");
	  for (byte b : torrentInfo.info_hash.array()) {  
	    urlExtension.append("%");
	    urlExtension.append(String.format("%02X", b));   
	  } 

	  //Add peerID parameter to URL.
	  urlExtension.append("&peer_id=");
	  for (byte b: peerID){
	    urlExtension.append("%");
	    urlExtension.append(String.format("%02X", b));
	  }
	  
	  //Wait for the SocketListener to find a free port 
	  while(manager.getListeningPort() == 0)
	  {
		  try {
			  Thread.sleep(1000L);
		  }
		  catch (InterruptedException e) { }
	  }
	  this.port = manager.getListeningPort();
	  //Add port parameter to URL.
	  urlExtension.append("&port=");
	  urlExtension.append( port );

	  //Add uploaded parameter to URL.
	  urlExtension.append("&uploaded=");
	  urlExtension.append( uploaded );

	  //Add downloaded parameter to URL.
	  urlExtension.append("&downloaded=");
	  urlExtension.append( downloaded );
	  
	  //Add left parameter to URL.
	  urlExtension.append("&left=");
	  urlExtension.append( left );

	  //Add event parameter to URL.
	  if(this.event != null){
		  urlExtension.append("&event=");
		  for (byte b: event){
			  urlExtension.append("%");
			  urlExtension.append(String.format("%02X", b));
		  }
	  }

	  //Finally, set value.
	  this.trackerURL = new URL(urlExtension.toString());
	}
	
	/**
	 * Connects to the tracker, and returns the Bencoded dictionary
	 * containing the keys: interval and peers.
	 * @throws IOException
	 */
	private void getTrackerResponse() throws IOException {
	  //Sends GET Request to the tracker.
		URLConnection tracker = trackerURL.openConnection();
		
		DataInputStream trackerDIS = new DataInputStream(tracker.getInputStream());
		this.trackerResponse = new byte[trackerDIS.available()];	
		trackerDIS.readFully(this.trackerResponse);
		trackerDIS.close();
				
	}
	
	/**
	 * Decodes the trackerResponseMap using the Bencoder2 in order to get the interval and peers.
	 * @throws BencodingException 
	 * @throws IOException 
	 */
	@SuppressWarnings("unchecked")
	private void decodeTrackerResponse() throws BencodingException, IOException{
		
		this.trackerResponseMap = (Map<ByteBuffer, Object>)Bencoder2.decode(this.trackerResponse);
		this.interval = (Integer)(this.trackerResponseMap.get(KEY_INTERVAL));
		
		// peerMapList is a list of dictionaries corresponding to peers, 
		// each of which contains the keys peer id , IP , and port , 
		// which map to the peer's self-selected ID, IP address or DNS name as a string, 
		// and port number, respectively.
		
		ArrayList< Map<ByteBuffer, Object>> peerMapList = (ArrayList< Map<ByteBuffer, Object>>)this.trackerResponseMap.get(KEY_PEERS);
		
		//Loops through the list of peer dictionaries and uses the info to construct a list of Peer objects.
		for(int mapIndex = 0; mapIndex < peerMapList.size(); mapIndex++){
			Peer newPeer = new Peer(peerMapList.get(mapIndex));
			if(!Tracker.containsPeer( peers, newPeer )){
				this.peers.add(newPeer);
			}
		}
	}
	
	//TODO: horribly inefficient hack- would be much better to replace the arrayList of peers we use for a hashmap, this doesn't belong in tracker either but i dont know where else to put it
	/**
	 * Looks thru array list to see if the specified peer's ID has a match; returns true if we have the peer in the list and false otherwise.
	 */
	public static boolean containsPeer(ArrayList<Peer> peerList, Peer peer){
		for(Peer p: peerList){
			if(Arrays.equals(p.getPeerID(), peer.getPeerID())){
				return true;
			}
		}
		return false;
	}
	
	public ArrayList<Peer> getPeerList(){
		return peers;
	}
	
	public int getInterval(){
		return interval;
	}	
	
	/**
	 * Calculates how much of the file is left to download by iterating through the pieces.
	 * @return
	 */
	@SuppressWarnings("unused")
	private int calculateLeft(){
		int have = 0;
		//iterate thru all but last piece
		for(int i=0 ; i < manager.myPieces.length - 1; i++){
			if(manager.myPieces[i]){
				have += torrentInfo.piece_length;
			}
		}
		//if last piece
		if(manager.myPieces[manager.myPieces.length-1]){
			have += torrentInfo.file_length % torrentInfo.piece_length;
		}
		return torrentInfo.file_length - have;
	}
	
	private int calculateLeft2() {
		return (torrentInfo.file_length - this.downloaded);
	}
	
	@Override
	public void run() {
		while(!manager.stopThreads){
			try {
				//Delay for interval amount of seconds
				Thread.sleep(1000 * this.getInterval());
				System.out.println("Sending HTTP GET to Tracker.");
				generateTrackerURL();
				getTrackerResponse();
				if (manager.stopThreads)
					break;
				decodeTrackerResponse();
				manager.populatePeers(peers);
				manager.connectToPeers();

			} 
			catch (IOException ioe) {
				System.err.println("Unable to connect to tracker.");
				ioe.printStackTrace();
			}catch( BencodingException be){
				System.err.println("Unable to decode tracker response.");
				be.printStackTrace();
			} catch (InterruptedException e) {
				//This, in all likelihood will be called. It is best not to print a stack trace and worry the user.
			}
		}
	}
	
	/**
	 * Adds specified amount to total downloaded.
	 */
	public synchronized void addToDownloaded(int amount){
		this.downloaded += amount;
	}
	
	/**
	 * Adds specified amount to total uploaded.
	 */
	public synchronized void addToUploaded(int amount){
		this.uploaded += amount;
	}
	
	/**
	 * Sets the event parameter by specified byte array. Can use the keys supplied at top of class, EVENT_STOPPED, EVENT_COMPLETED,
	 * and EVENT_STARTED
	 * 
	 * @param eventStatus
	 * @return True if successful, false if otherwise
	 */
	public boolean setEvent(byte[] eventStatus){
		if(eventStatus == EVENT_STOPPED || eventStatus == EVENT_COMPLETED || eventStatus == EVENT_STARTED){
			event = eventStatus;
			return true;
		} else {
			return false;
		}
	}
	
	
	public int getUploaded(){
		return uploaded;
	}
	
	public int getDownloaded(){
		return downloaded;
	}
	
	public int getLeft(){
		//TODO: isDownloadComplete sends sets a EVENT_STOPPED if it is, which we only want to send ONCE, the FIRST time we finish download
		if (manager.isDownloadComplete())
		{
			this.left = 0;
			return 0;
		}
		else 
			return calculateLeft2();
	}

	public void setUploaded(int uploaded) {
		this.uploaded = uploaded;
	}
	
	
}
