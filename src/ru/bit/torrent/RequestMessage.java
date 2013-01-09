package ru.bit.torrent;

import java.nio.ByteBuffer;

/**
 * @author Dylan Murray
 * @author Charles Zuppe
 * @author Jonas Schreiber
 *
 */
public class RequestMessage extends Message {
	
	int length;
	
	int begin;
	
	int index;
	
	public RequestMessage(int index, int begin, int length){
		super(Message.REQUEST);
		this.index = index;
		this.begin = begin;
		this.length = length;
	}
	
	/**
	 * Generates a RequestMessage object in byte array format. 
	 * @return
	 */
	@Override
	public byte[] generateByteArray(){
		ByteBuffer bb = ByteBuffer.allocate(17);
		bb.putInt(13);
		bb.put(this.id);
		bb.putInt(this.index);
		bb.putInt(this.begin);
		bb.putInt(this.length);
		return bb.array();
	}
	
	public String toString(){
		return super.toString() + " @ index: " + index + " and begin: " + begin;
	}
}
