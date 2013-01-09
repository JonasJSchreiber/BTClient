package ru.bit.torrent;

import java.nio.ByteBuffer;

/**
 * piece: <len=0009+X><id=7><index><begin><block>
 * 
 * The piece message is variable length, where X is the length of the block. The payload contains the following information:
 * 
 * index: integer specifying the zero-based piece index
 * begin: integer specifying the zero-based byte offset within the piece
 * block: block of data, which is a subset of the piece specified by index. 
 * 
 * @author Dylan Murray
 * @author Charles Zuppe
 * @author Jonas Schreiber
 *
 */
public class PieceMessage extends Message{
	
	int index;
	
	int begin;
	
	byte[] block;
	
	public PieceMessage(int index, int begin, byte[] block){
		super(Message.PIECE);
		this.index = index;
		this.begin = begin;
		this.block = block;
	}
	
	public String toString(){
		return super.toString() + "@ index: " + index + ", begin: " + begin;
	}
	
	@Override
	public byte[] generateByteArray(){
		ByteBuffer bb = ByteBuffer.allocate(13 + block.length);
		bb.putInt(9 + block.length);
		bb.put(Message.PIECE);
		bb.putInt(index);
		bb.putInt(begin);
		bb.put(block);
		return bb.array();
	}
}
