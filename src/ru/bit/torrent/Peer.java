package ru.bit.torrent;

import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Map;

/**
 * Peer class contains all the states and methods associated with a "peer" as defined in the BitTorrent protocol.
 * 
 * @author Dylan Murray
 * @author Charles Zuppe
 * @author Jonas Schreiber
 *
 */
public class Peer {
	
	 /**
     * Key used to retrieve the peer id of the peer.
     */
    public final static ByteBuffer KEY_PEERID = ByteBuffer.wrap(new byte[]
    { 'p', 'e', 'e', 'r', ' ', 'i', 'd'});

    /**
     * Key used to retrieve the IP address of the peer.
     */
    public final static ByteBuffer KEY_IP = ByteBuffer.wrap(new byte[]
    { 'i', 'p'});
    
    /**
     * Key used to retrieve the port that the peer is listening on.
     */
    public final static ByteBuffer KEY_PORT = ByteBuffer.wrap(new byte[]
    { 'p', 'o', 'r', 't'});
    
    /**
     * The peer id of the peer.
     */
    private byte[] peerID;
    
    /**
     * The IP address of the peer.
     */
    private String ip;
    
    /**
     * The port that the peer is listening on.
     */
    private int port;
    
    /**
     * Bitfield shows what pieces this peer has to offer.
     */
    private byte[] bitfield;
	
    /**
     * Boolean value to describe whether or not the client is choking this peer.
     */
    private boolean amChoking;
    
    /**
     * Boolean value to describe whether or not the client is interested in this peer.
     */
    private boolean amInterested;
    
    /**
     * Boolean value to describe whether or not the peer is choking this client.
     */
    private boolean peerChoking;
    
    /**
     * Boolean value used to describe whether or not the peer is interested in the client.
     */
    private boolean peerInterested;
    
    /**
     * Boolean value used to describe whether the peer is currently connected.
     */
    private boolean amConnected;
    
    /**
     * The socket the client communicates with this peer through. Isn't initialized until connect() is called.
     */
    private Socket socket;
    
    /**
     * An array list of ints which correspond to the pieces available to us in the bitfield, 
     * for this particular peer.
     */
    public ArrayList<Integer> availablePieces;

    /**
     * A MessageHandler Thread attached to the Peer
     */
    private Thread messageHandler;
 
    /**
     * a MessageHandler Object which is needed to pass along information from PerformanceAnalyzer
     */
    private MessageHandler mH;

	/**
	 * Used in calculating the peer's download speed
	 */
    private Long start;
	
	/**
	 * Used in calculating the peer's download speed
	 */
	private Long end;
    /**
     * Integer value used to describe the peer's download speed. This metric is used in PerformanceAnalyzer.
     */
		
    private int downloadSpeed;
    
    /**
     * Integer value used to describe the peer's upload speed. This metric is used in PerformanceAnalyzer.
     */
    private int uploadSpeed;
    
	/**
     * Flag to whether or not we have sent this peer a handshake.
     */
    public boolean receivedHandshake = false;
    
    /**
     * Flag to whether or not we sent this per an uninterested message.
     */
    public boolean sentUninterested = false;
    
    /**
     * Needed for determining current rarest piece;
     */
    @SuppressWarnings("unused")
	private PerformanceAnalyzer pa;

	/**
     * Accepts a dictionary containing peer info that creates a unique peer object.
     * @param peerMap
     */
	public Peer(Map<ByteBuffer, Object> peerMap) {
		
		//Temporary byte buffers converted into byte[]'s that contain peer info.
		ByteBuffer bbPeerID = (ByteBuffer)peerMap.get(KEY_PEERID);
		ByteBuffer bbPeerIP = (ByteBuffer)peerMap.get(KEY_IP);
		this.port = (Integer)peerMap.get(KEY_PORT);
		this.peerID = bbPeerID.array();
		this.ip = new String(bbPeerIP.array());
		this.availablePieces = new ArrayList<Integer>();

		//Peer status initialized to choked and uninterested.
		this.amChoking = true;
		this.amInterested = false;
		this.peerChoking = true;
		this.peerInterested = false;
		this.amConnected = false;
		this.pa = null;
		this.start = System.currentTimeMillis();
	}
	
	/**
	 * Constructor to be used when getting new peers from the Server Socket.
	 * @param port
	 * @param peerID
	 * @param ip
	 */
	public Peer(int port, String ip, byte[] peerID){
		this.port = port;
		this.peerID = peerID;
		this.ip = ip;
		this.availablePieces = new ArrayList<Integer>();

		//Peer status initialized to choked and uninterested.
		this.amChoking = true;
		this.amInterested = false;
		this.peerChoking = true;
		this.peerInterested = false;
		this.amConnected = false;
	}
	
	/**
	 * Allows client to establish a TCP connection with this peer.
	 * @return	true if successful connection or false if otherwise.
	 */
	public boolean connect(){
		try {
			socket = new Socket(this.ip, this.port);
			socket.setSoTimeout(120000);
		} catch (Exception e) {
			System.err.println("Unable to establish TCP connection to peer: " + this.toString());
			e.printStackTrace();
		}
		return false;
	}
	
	/**
	 * Closes the socket to the peer.
	 * @throws IOException
	 */
	public void disconnect(){
		try {
			socket.close();
			this.amConnected = false;
		} catch (IOException e) {
			//won't happen
		} catch (NullPointerException npe) {
			//may happen. No need to concern user
		}
	}
	
	/**
	 * Looks through Peer's available pieces and returns the index of piece our client still needs.
	 * If the peer doesn't have any more pieces we need, returns -1.
	 * 
	 * @param completedPieces	A byte array representing all the pieces we've got so far.
	 * @return
	 */
	public int getNeededPieceIndex(boolean[] completedPieces, boolean[] piecesInProg){
		for(int i= 0; i < availablePieces.size(); i++){
			if(completedPieces[availablePieces.get(i)] == false && piecesInProg[availablePieces.get(i)] == false){
				return availablePieces.get(i);
			}
		}
		System.out.println("Peer: " + this.toString() + " has no pieces the client needs.");
		return -1;
	}
	
	/**
	 * Populates the list of available pieces this peer has from the bitfield he's sent.
	 */
	private void parseBitfield(){  
		StringBuffer sb = new StringBuffer();
		sb.append("Peer " + this.toString() + " currently has pieces: ");
        for(int bitIndex = 0; bitIndex < this.bitfield.length * 8 ; bitIndex++){
            if(getBit(this.bitfield, bitIndex) == 1){
            	sb.append(bitIndex + ", ");
            	this.availablePieces.add(bitIndex);
            }

        }
        System.out.println(sb);
	}
	
	/**
	 * Adds a single index to this peer's available piece list; typically will be called in response to a Have message.
	 */
	public void addAvailablePiece(int pieceIndex){
		this.availablePieces.add(pieceIndex);
	}
	
	/**
	 * Gets a single bit from a byte array.
	 * @param data
	 * @param pos
	 * @return
	 */
	private static int getBit(byte[] data, int pos) {
        int posByte = pos/8; 
        int posBit = pos%8;
        byte valByte = data[posByte];
        int valInt = valByte>>(8-(posBit+1)) & 0x0001;
        return valInt;
	}
	
	public byte[] getPeerID() {
		return peerID;
	}

	public String getIp() {
		return ip;
	}

	public int getPort() {
		return port;
	}
	
	public Socket getSocket() {
		return socket;
	}
	
    public boolean amChoking(){
    	return amChoking;
    }
    
    public boolean amInterested(){
    	return amInterested;
    }
    
    public boolean isChoking(){
    	return peerChoking;
    }
    
    public boolean isInterested(){
    	return peerInterested;
    }
    
    public boolean amConnected(){
    	return amConnected;
    }
    
    public void setConnected(boolean bool){
    	amConnected = bool;
    }
    
    /**
     * Sets whether or not the client is choking the peer this method is being invoked on.
     * @param bool
     */
    public void setChoking(boolean bool){
    	amChoking = bool;
    	if (!bool)
    	{
    		this.mH.resetUploadStats();
    	}
    }
    
    /**
     * Sets whether or not the client is interested in the peer this method is invoked on.
     * @param bool
     */
    public void setInterested(boolean bool){
    	amInterested = bool;
    }
    
    /**
     * Sets whether or not the client is choking the peer this method is being invoked on.
     * @param bool
     */
    public void setPeerChoking(boolean bool){
    	peerChoking = bool;
    }
    
    /**
     * Sets whether or not the client is interested in the peer this method is invoked on.
     * @param bool
     */
    public void setPeerInterested(boolean bool){
    	peerInterested = bool;
    }
    
    /**
     * Sets the bitfield of this peer.
     */
    public void setBitfield(byte[] bitfield){
    	this.bitfield = bitfield;
    	parseBitfield();
    }
    
    /**
     * Needed for determining Piece Rarity
     * @return
     */
//	public byte[] getBitfield() {
//		return bitfield;
//	}

	/**
	 * Sets PeerID from SocketListener
	 * @param peerID
	 */
    public void setPeerID(byte[] peerID) {
		this.peerID = peerID;
	}

    /**
     * Sets peer IP from SocketListener
     * @param ip
     */
	public void setIp(String ip) {
		this.ip = ip;
	}

	/**
	 * Sets peer port from SocketListener
	 * @param port
	 */
	public void setPort(int port) {
		this.port = port;
	}
	
	/**
	 * Relays peers Socket from SocketListener
	 * @param socket
	 */

	public void setSocket(Socket socket) {
		this.socket = socket;
	}
	
	public Thread getMessageHandler() {
		return messageHandler;
	}

	public void setMessageHandler(Thread messageHandler) {
		this.messageHandler = messageHandler;
	}

	public void setmH(MessageHandler mH) {
		this.mH = mH;
	}
	
    public int getDownloadSpeed() {
		return downloadSpeed;
	}

	public void setDownloadSpeed(int downloadSpeed) {
		this.downloadSpeed = downloadSpeed;
	}
	
	public int getUploadSpeed() {
		return uploadSpeed;
	}

	public void setUploadSpeed(int uploadSpeed) {
		this.uploadSpeed = uploadSpeed;
	}
	
	public void setPa(PerformanceAnalyzer pa) {
		this.pa = pa;
	}

	/**
	 * Returns String corresponding to Peer's ID
	 */
	public String toString(){
		return new String(this.peerID);
	}
	
	public Long getEnd() {
		return end;
	}

	public void setEnd(Long end) {
		this.end = end;
	}

	public Long getStart() {
		return start;
	}

	public void setStart(Long start) {
		this.start = start;
	}

	/**
	 * An ArrayList of Messages that represents of a queue of messages that need to be sent out to this peer.
	 */
	public ArrayList<Message> messageQueue = new ArrayList<Message>();
}
