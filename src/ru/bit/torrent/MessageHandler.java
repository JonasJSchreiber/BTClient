package ru.bit.torrent;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

/**
 * This class is used to facilitate communication between a "Manager" and each one of its' peers; that is, for every Peer that
 * Manager wants to connect to, a new MessageHandler is created. MessageHandler is responsible for requesting and sending
 * all the various methods and pieces of the file.
 * 
 * @author Dylan Murray
 * @author Jonas Schreiber
 * @author Charles Zuppe
 *
 */
public class MessageHandler implements Runnable{
	
	/**
	 * The Manager to which this MessageHandler is communicating with.
	 */
	private Manager manager;
	
	/**
	 * The Peer to which this MessageHandler is communicating with.
	 */
	private Peer peer;
	
	/**
	 * The output stream we'll use to interact with a peer.
	 */
	private DataOutputStream out;
	
	/**
	 * The input stream we'll use to interact with a peer.
	 */
	private DataInputStream in;
	
	/**
	 * The TorrenInfo object corresponding to the torrent we're dealing with.
	 */
	private TorrentInfo torrentInfo;
	
	/**
	 * The subdivisions of pieces. 
	 */
	private ArrayList<PieceMessage> partialPieces;
	
	/**
	 * Subdivision size
	 */
	private int subdivisionSize;
	
	/**
	 * Current Piece (so that the decode on the last piece doesn't think it is still standard pieceLength), is set to -1
	 * when we are not currently in the middle of a piece reception.
	 */
	private int currentPieceIndex;
	
	/**
	 * Used in calculating the peer's download speed
	 */

	/**
	 * Message sender responsible for sending messages to peer. Gets instantiated after handshaking with peer (after we're
	 * connected).
	 */
	private MessageSender messageSender;
	
	
	@SuppressWarnings("unused")
	private Thread speedCalcForPeer;
	
	/**
	 * @param manager
	 * @param peer
	 * @param torrentInfo
	 */
	public MessageHandler(Manager manager, Peer peer, TorrentInfo torrentInfo){
		this.manager = manager;
		this.peer = peer;
		this.torrentInfo = torrentInfo;
		this.partialPieces = new ArrayList<PieceMessage>();
		this.subdivisionSize = 16384;
		this.currentPieceIndex = -1;
	}
	
	/**
	 * Manages all p2p I/O. (ie sends/ verifies handshake and sends/receives messages)
	 * 
	 * @param p
	 * @throws Exception 
	 */
	private void managePeerConnection() throws Exception{	        
		//Send/ receive handshake
        if(!manageHandshake()){
        	//handshake failed - quit connection to this peer.
        	return;
        }
        
        //After handshake, instantiates a message sender;
        this.messageSender = new MessageSender(peer, out);
        Thread thread = new Thread(this.messageSender);
        thread.start();
        
        //Keep connection with peer up sending/receiving messages until the user initiates the shutdown sequence
		while(!manager.stopThreads){
        	try{
        		if(!peer.amConnected()){
        			disposeCurrPiece();
        			closeConnections();
        			return;
        		}
        		processMessage(Message.decode(in), peer);
        		Thread.sleep(10);
        	} catch (EOFException eofe){
        		System.err.println("EOFException from peer " + this.peer + "... disconnecting from peer.");
        		disposeCurrPiece();
        		closeConnections();
        		return;
        	}
        }
        
        //close streams/ Socket
        System.out.println("Closing streams and socket for Peer: " + peer.toString());
        closeConnections();
    }
	
	/**
	 * Closes input/output streams and closes the socket.
	 * @throws IOException 
	 */
	private void closeConnections() throws IOException {
		if(!peer.amChoking()){
			manager.numUnchoked--;
		}
		messageSender.quit();
		//Sleep for a second while message Sender finishes sending whatever it's in the middle of.
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		in.close();
		out.close();
		peer.disconnect();
		manager.peers.remove(peer);
	}

	/**
	 * Performs a handshake, returns false if unsuccessful.
	 * @return
	 * @throws IOException 
	 */
	private boolean manageHandshake() throws IOException{
		try {
			System.out.println("Sending handshake to peer: " + peer + "...");
			Message.sendHandshake(out, generateHandshake());
			if(!peer.receivedHandshake){
				System.out.println("Receiving handshake from peer: " + peer);
				Message.receiveHandshake(in, this.torrentInfo.info_hash.array());
				peer.receivedHandshake = true;
			}
//			if(!Message.doHandshake(in, out, generateHandshake(), this.torrentInfo.info_hash.array())){
//				//TODO: Handle a bad handshake/ unresponsive peer. Currently just quits program.
//				System.err.println("Handshake to peer: " + peer.toString() + " unsuccessful. Info hashes did not match.");
//				return false;
//			}
		} catch (IOException e) {
			System.err.println("Handshake to peer: " + peer.toString() + " failed on IOException.");
			return false;
		}
        System.out.println("Handshake success! -- " + peer);
        System.out.println("Sending Bitfield Message to " + peer);
        sendBitfield();
        return true;
	}
	
	/**
	 * Generates the byte sequences needed to handshake a peer. Formatted as such:
	 * 		
	 * Handshaking between peers begins with byte nineteen followed by the string 'BitTorrent protocol'. 
	 * After the fixed headers are 8 reserved bytes which are set to 0. Next is the 20-byte SHA-1 hash of the 
	 * bencoded form of the info value from the metainfo (.torrent) file. The next 20-bytes are the peer id generated 
	 * by the client. The info_hash should be the same as sent to the tracker, and the peer_id is the same as sent to 
	 * the tracker. If the info_hash is different between two peers, then the connection is dropped.
	 * 
	 * All integers are encoded as 4-bytes big-endian (e.g. 1,234 should be encoded as (hex) 00 00 04 d2).
	 * The peer_id should be randomly-generated for each process your program generates. (Meaning that it should probably 
	 * change in some way each time the client is run.)  Stick to alphanumerics for easier debugging.
	 * 
	 * @return	Returns the byte[] representing a p2p handshake.
	 */
	private byte[] generateHandshake() {
		int i;
		byte[] handshake = new byte[68];
		handshake[0] = (byte) 19;
		String protocol = "BitTorrent protocol";
		for (i = 1; i < 20; i++)
			handshake[i] = (byte) protocol.charAt(i-1);
		for (i = 20; i < 28; i++)
			handshake[i] = 0;
		for (Byte b : torrentInfo.info_hash.array()){
			handshake[i] = b;
			i += 1;
		}
		for (Byte b : manager.getPeerID()){
			handshake[i] = b;
			i += 1;
		}
		return handshake;
	}
	
	/**
	 * Calculates the length of a piece to request (it'll typically just be the regular piece length, but if it's the last
	 * piece it's going to be a bit smaller.
	 * 
	 * @param pieceIndex
	 * @return
	 */
	private int calcPieceLength(int pieceIndex){
		int pieceLength;
		if(pieceIndex == torrentInfo.piece_hashes.length-1){
			pieceLength = torrentInfo.file_length % torrentInfo.piece_length;
		} else {
			pieceLength = torrentInfo.piece_length;
		}
		return pieceLength;
	}
	
	/**
	 * This method takes a message sent by a peer, and reacts to it depending on message type.
	 * @param message The message received by peer.
	 * @throws Exception 
	 */
	private void processMessage(Message message, Peer peer) throws Exception{
		if (message == null)
			return;
		if (message.toString() != null)
			//System.out.println("Received " + message.toString() + " from Peer: " + peer);
		if (manager.stopThreads)
			return;
		switch(message.id){
		case Message.CHOKE:											//Choke Message, let our peer object know that it is now choking us.
			peer.setPeerChoking(true);
			peer.setInterested(false);
			System.out.println(peer + " is now choking us.");
			//We've been disconnected before we can get the full piece; throw out the partial pieces we got.
			disposeCurrPiece();
			break;
		case Message.UNCHOKE: 										//Unchoke Message, let our peer object know that it is not choking us.
			peer.setPeerChoking(false);
			System.out.println(peer + " is no longer choking us.");
			if(currentPieceIndex == -1){
				checkPeersPieces();
			}
			break;
		case Message.INTERESTED: 									//Interested Message, let our peer object know that it is interested in us.
			peer.setPeerInterested(true);
			System.out.println(peer + " is interested in our pieces.");
			//if we're choking the peer, unchoke
			if(peer.amChoking() && manager.numUnchoked < 6){
				resetUploadStats();
				addToQueue(new Message(Message.UNCHOKE));
				peer.setChoking(false);
				manager.numUnchoked++;
			}
			break;
		case Message.UNINTERESTED: 									//Uninterested Message, let our peer object know that it is uninterested in us.
			peer.setPeerInterested(false);
			System.out.println(peer + " is no longer interested");
			break;
		case Message.HAVE:
			peer.addAvailablePiece(((HaveMessage)message).pieceIndex);
			System.out.println(peer + " has piece " + ((HaveMessage)message).pieceIndex  + ", adding to available pieces");
			if( currentPieceIndex == -1){
				checkPeersPieces();
			}
			break;
		case Message.BITFIELD:
			BitfieldMessage bitfieldMessage = (BitfieldMessage)message;
			peer.setBitfield(bitfieldMessage.bitfield); 			//Sets the peer's bitfield as shown in message.
			//If we aren't already in the middle of a piece, this should never happen since bitfield is only sent after handshake, but just to be safe
			if( currentPieceIndex == -1){
				checkPeersPieces();
			}
			break;
		case Message.REQUEST:
			if(peer.amChoking()){
				System.out.println(peer + " is currently being choked. Ignoring Request message.");
			} else {
				processRequestMessage((RequestMessage) message);
			}
			break; 
		case Message.PIECE:
			int blockLength = ((PieceMessage)message).block.length;
			this.currentPieceIndex = ((PieceMessage)message).index;
			this.messageSender.addTotalBytesFromPeer(blockLength);
			partialPieces.add((PieceMessage)message);
			manager.addToDownloaded(blockLength);
			if (partialPieces.size() * subdivisionSize >= calcPieceLength(currentPieceIndex)){ //ensures concatenation of last piece
				//we finished downloading whole piece
				concatenate();
				//no longer occupied by specific piece index.
				currentPieceIndex = -1;
				//look for new pieces we need
				checkPeersPieces();
			} else {	// We're still in the middle of getting all the parts of a piece; request the next block
				sendNextRequestMessage(((PieceMessage)message).index, ((PieceMessage)message).begin);
			}
			//System.out.println("Peer: " + this.peer.toString() + " is currently uploading to us at: " + this.peer.getUploadSpeed() + "kB/s");
			break;
		case Message.CANCEL:
			break; 													//TODO: handle CANCEL message received.
		case Message.PORT:
			break; 													//TODO: handle PORT message received.
		case Message.KEEPALIVE:
			break;
		default:
			System.err.println("Unknown message tried to be processed from peer " + this.peer + ".");
		}
	}
	
	/**
	 * Takes a Request Message and if everything checks out it sends out the piece.
	 * @throws IOException 
	 */
	private void processRequestMessage(RequestMessage message) throws IOException{
		//too big of piece
		if(message.length > 32768){
			System.err.println(peer + " request a piece larger than 32KB. Disconnecting from peer.");
			disposeCurrPiece();
			closeConnections();
			return;
		}
		//we don't have the piece.
		else if(manager.myPieces[message.index] == false){
			System.err.println(peer + " has requested a piece(" + message.index + ") that we don't have! Disconnecting from peer.");
			disposeCurrPiece();
			closeConnections();
			return;
		} 
		//TODO: Also need to handle the case in which they said us a bad offset
		//we're good to go
		else {
			//System.out.println("Sending piece " + message.index + " at offset " + message.begin + " to peer " + peer);
			byte[] wholePiece = manager.fa.readPiece(message.index);
			byte[] partialPiece = new byte[message.length];
			System.arraycopy(wholePiece, message.begin, partialPiece, 0, message.length);
			addToQueue(new PieceMessage(message.index, message.begin, partialPiece));
			this.messageSender.addToTotalBytesToPeer(partialPiece.length);
			manager.addToUploaded(partialPiece.length);
			//System.out.println("Peer: " + this.peer.toString() + " is currently downloading from us at: " + this.peer.getDownloadSpeed() + "kB/s");
		}
	}
	
	/**
	 * Disposes of any partially-downloaded pieces, and sets the inProg status of this index to false.
	 */
	private void disposeCurrPiece(){
		if(currentPieceIndex != -1){
			manager.piecesInProg[currentPieceIndex] = false;
			currentPieceIndex = -1;
		}
		partialPieces = new ArrayList<PieceMessage>();
	}
	
	/**
	 * Checks what pieces the peer has and if it has any worthwhile, then request it.
	 * @throws IOException 
	 */
	private synchronized void checkPeersPieces() throws IOException{
		System.out.println("Checking out peer " + this.peer + "'s pieces");
		
		//wait for pieces to become prioritized
		boolean sentMessage = false;
		while(manager.piecesByRarity == null)
			try {
				Thread.sleep(100L);
			} catch (InterruptedException e) {}
		
		//iterate through list of prioritized pieces
		for (int i : manager.piecesByRarity)
		{
			if (sentMessage)
				break;
			if (this.peer.availablePieces.contains(i))
			{
				System.out.println("Peer " + this.peer + " has a desired piece at index " + i + "!");
				//if the peer is choking us and we haven't sent an interested message yet
				if(peer.isChoking() && !peer.amInterested()){
					System.out.println("We're currently choked - sent interested message to peer " + this.peer);
					addToQueue(new Message(Message.INTERESTED));
					peer.setInterested(true);
					sentMessage = true;
				}
				else if(!peer.isChoking()){
					System.out.println("Sent RequestMessage for first block of piece index: " + i + " to peer " + this.peer);
					addToQueue(new RequestMessage(i, 0, subdivisionSize));
					currentPieceIndex = i;
					manager.piecesInProg[currentPieceIndex] = true;
					sentMessage = true;
				} 
				else {
					//The peer is choking us and we've already sent an interested message. Nothing we can do.
					System.out.println(this.peer + " is choking us and we've already sent an interested message.");
					sentMessage = true;
				}
			}
		}
		if (!sentMessage) {
			//This peer has no pieces we need right now, send uninterested
			if(!this.peer.sentUninterested){
				addToQueue(new Message(Message.UNINTERESTED));
				this.peer.setInterested(false);
				this.peer.sentUninterested = true;
				this.peer.setDownloadSpeed(0);
				this.peer.setUploadSpeed(0);
			}
		}
	}
	
	/**
	 * Adds a message to this message handler's peer's queue
	 * @param message
	 */
	public void addToQueue(Message message){
		this.peer.messageQueue.add(message);
	}
	
	/**
	 * Takes the index and begin of PREVIOUS request message and USES it to calculate the NEXT request message.
	 * The begin passed to this method is NOT the begin that will be requested.
	 * 
	 * @param index
	 * @param begin
	 * @throws IOException 
	 */
	private void sendNextRequestMessage(int index, int begin) throws IOException{
		int newOffset = begin + subdivisionSize;
		if (!(newOffset + subdivisionSize > calcPieceLength(currentPieceIndex))) {
			//System.out.println("Sending Request Message to " + this.peer + " for piece " + index + " at offset " + newOffset);
			addToQueue(new RequestMessage(index, newOffset, subdivisionSize));
		}
		else {
			addToQueue(new RequestMessage(index, newOffset, calcPieceLength(currentPieceIndex) - newOffset));
		}
	}
	
	/**
	 * This stores the partial pieces until we have a full piece.
	 * @param partialPieces
	 */
	private void concatenate() throws Exception
	{
		int index = partialPieces.get(0).index;
		ByteBuffer bb;
		if (index != (torrentInfo.piece_hashes.length - 1))
			bb = ByteBuffer.allocate(torrentInfo.piece_length);
		else
			bb = ByteBuffer.allocate(torrentInfo.file_length % torrentInfo.piece_length);
		for (PieceMessage pm: partialPieces)
		{
			bb.put(pm.block);
		}
		byte[] pieceBytes = bb.array();
		PieceMessage temp = new PieceMessage(index, 0, pieceBytes);
		if(manager.verifyPiece(temp))				//Verify the piece we received to be valid, and save it if it is.
			manager.savePiece(temp);
		else 
			disposeCurrPiece();
		partialPieces = new ArrayList<PieceMessage>();
	}

	public void sendBitfield() throws IOException{
		Message.encode(out, new BitfieldMessage(manager.myPieces));
	}
	
	/**
	 * This is necessary because PerformanceAnalyzer and Peer don't have a handle on MessageSender but they do have a handle
	 * on MessageHandler, which has a handle on MessageSender. Therefore the call must go through the messageSender. 
	 */
	
	public void resetUploadStats() {
		this.messageSender.resetTotalBytesToPeer();
		this.messageSender.resetStart();
	}

	/**
	 * The run() method of this runnable - where the thread gets started.
	 */
	@Override
	public void run() {
		try {
			//opens sockets
			peer.connect();
			out = new DataOutputStream(peer.getSocket().getOutputStream());
			in = new DataInputStream(peer.getSocket().getInputStream());	
			managePeerConnection();
		} catch(SocketTimeoutException ste){
			System.err.println("Socket for peer: " + this.peer + " timed out. Disconnecting from peer.");
			try {
				closeConnections();
			} catch (IOException e) {
				System.err.println("meta-exception, couldnt close connections after exception was already thrown.");
				e.printStackTrace();
			}
		} catch (IOException e){
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
