package ru.bit.torrent;

import java.nio.ByteBuffer;

/**
 * @author Dylan Murray
 * @author Charles Zuppe
 * @author Jonas Schreiber
 *
 */
public class HaveMessage extends Message{

	public int pieceIndex;

	public HaveMessage(int pieceIndex){
		super(Message.HAVE);
		this.pieceIndex = pieceIndex;
	}
	
	/**
	 * Generates a HaveMessage object in byte array format. 
	 * @return
	 */
	@Override
	public byte[] generateByteArray(){
		ByteBuffer bb = ByteBuffer.allocate(17);
		bb.putInt(5);
		bb.put(this.id);
		bb.putInt(this.pieceIndex);
		return bb.array();
	}
}
