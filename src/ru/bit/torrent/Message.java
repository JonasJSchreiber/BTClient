package ru.bit.torrent;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Message class represents a Peer-to-Peer message in the protocol (Unchoke, Interested, Piece, etc.). 
 * Has static methods to encode and decode to/from Input/Output streams. Handles all p2p communication.
 * 
 *
 * @author Dylan Murray
 * @author Charles Zuppe
 * @author Jonas Schreiber
 *
 */
public class Message {
	
	public static final byte CHOKE = (byte)0;
	
	public static final byte UNCHOKE = (byte)1;
	
	public static final byte INTERESTED = (byte)2;
	
	public static final byte UNINTERESTED = (byte)3;
	
	public static final byte HAVE = (byte)4;
	
	public static final byte BITFIELD = (byte)5;
	
	public static final byte REQUEST = (byte)6;
	
	public static final byte PIECE = (byte)7;
	
	public static final byte CANCEL = (byte)8;
	
	public static final byte PORT = (byte)9;
	
	//KEEPALIVE messages don't actually have an id - this byte identification is for local convenience only.
	public static final byte KEEPALIVE = (byte)10;
	
	public byte id;
	
	public Message(byte id) {
		this.id = id;
	}
	
	/**
	 * Sends a handshake out and expects a handshake back. If it does not receive a handshake back or if the
	 * peer ID/ infoHash is not as expected, it will return false.
	 * 
	 * @param in
	 * @param out
	 * @param handshake
	 * @param infoHash
	 * @return
	 */
//	public static boolean doHandshake(DataInputStream in, DataOutputStream out, byte[] handshake, byte[] infoHash) throws IOException{
//		sendHandshake(out, handshake);
//		receiveHandshake(in, infoHash);
//		return true;
//	}

	public static void sendHandshake(DataOutputStream out, byte[] handshake) throws IOException{
		out.write(handshake);
		out.flush();

	}

	public static boolean receiveHandshake(DataInputStream in, byte[] infoHash) throws IOException {
		//Verifies the infohash of responded handshake.
				byte[] input = new byte[68];
		        in.readFully(input);	        
				for (int i = 28; i < 48; i++)

				{
					if (input[i] != infoHash[i-28])

					{
						return false;


					}
				}
				return true;
	}
	
	/**
	 * Method used to decode incoming message from peer. Returns a Message object with all relevant information.
	 * 
	 * @param in
	 * @return
	 * @throws IOException 
	 */
	public static Message decode(DataInputStream in) {
		//declare local variables
		int index;
		int begin;
		int length;
		byte[] bitfield;
		byte[] block;
		
		int msgLength;
		try {
			msgLength = in.readInt();
			
			
			//Check to see if it's keep alive
			if(msgLength == 0){
				//System.out.println("Keep Alive Message received.");
				return new Message(Message.KEEPALIVE);
			}
			
			//We checked for Keep Alive, so the next byte will be the id. Find out what message we're dealing with.
			switch(in.readByte()){
			case CHOKE:
				return new Message(CHOKE);
			case UNCHOKE:
				return new Message(UNCHOKE);
			case INTERESTED:
				return new Message(INTERESTED);
			case UNINTERESTED:
				return new Message(UNINTERESTED);
			case HAVE:
				return new HaveMessage(in.readInt());
			case BITFIELD:
				bitfield = new byte[msgLength - 1]; //full message length - the one byte id.
				in.readFully(bitfield);
				return new BitfieldMessage(bitfield);
			case REQUEST:
				index = in.readInt();
				begin = in.readInt();
				length = in.readInt();
				return new RequestMessage(index, begin, length);
			case PIECE:
				index = in.readInt();
				begin = in.readInt();
				block = new byte[msgLength - 9];
				in.readFully(block);
				return new PieceMessage(index, begin, block);
			case CANCEL:
				//TODO: implement a cancel message
				break;
			case PORT:
				//TODO: implement a port message
				break;
			default:
				System.out.println("Unrecognized message type.");
			}
		} catch (IOException e) {
//			System.out.println("Message threw an IOException");
		}
//		System.out.println("Message wasn't read, returning null");
		return null;
	}
	
	/**
	 * Takes a message passed to it by the client, encodes it, and sents it off to the peer.
	 * @param out
	 * @param message
	 * @throws IOException 
	 */
	public static void encode(DataOutputStream out, Message message) throws IOException{
		byte[] toSend = message.generateByteArray();
		out.write(toSend);
		out.flush();
	}
	
	/**
	 * Generates the generic message in byte array format. (ie Choke, Unchoke, Interested, Not interested. Special messages will override).
	 * @return
	 */
	public byte[] generateByteArray(){
		ByteBuffer bb = ByteBuffer.allocate(5);
		bb.putInt(1);
		bb.put(this.id);
		return bb.array();
	}
	
	public String toString(){
		switch(this.id){
		case CHOKE:
			return "Choke Message";
		case UNCHOKE:
			return "Unchoke Message";
		case INTERESTED:
			return "Interested Message";
		case UNINTERESTED:
			return "Uninterested Message";
		case HAVE:
			return "Have Message";
		case BITFIELD:
			return "BitField Message";
		case REQUEST:
			return "Request Message";
		case PIECE:
			return "Piece Message";
		case CANCEL:
			return "Cancel Message";
		case PORT:
			return "Port Message";
		default:
			return "KeepAlive Message";
		}
	}
}
