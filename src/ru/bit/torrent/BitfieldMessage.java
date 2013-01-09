package ru.bit.torrent;

import java.nio.ByteBuffer;

/**
 * @author Dylan Murray
 * @author Charles Zuppe
 * @author Jonas Schreiber
 *
 */
public class BitfieldMessage extends Message{
	
	byte[] bitfield;
	
	public BitfieldMessage(byte[] bitfield){
		super(Message.BITFIELD);
		this.bitfield = bitfield;
	}
	
	public BitfieldMessage(boolean[] pieces){
		super(Message.BITFIELD);
		this.bitfield = convert(pieces);
	}
	
	/**
	 * @author Robert Moore
	 * Converts an {@code boolean[]} to a {@code byte[]} where each bit of the
	 * {@code byte[]} contains a 1 bit for a {@code true} value, and a 0 bit for
	 * a {@code false} value. The {@code byte[]} will contain the 0th index
	 * {@code boolean} value in the most significant bit of the 0th byte.
	 * 
	 * @param bools
	 *            an array of boolean values
	 * @return a {@code byte[]} containing the boolean values of {@code bools}
	 *         as bits.
	 */
	public static byte[] convert(boolean[] pieces) {
		
		int length = pieces.length / 8;
		int mod = pieces.length % 8;
		if(mod != 0){
			++length;
		}
		byte[] retVal = new byte[length];
		int boolIndex = 0;
		for (int byteIndex = 0; byteIndex < retVal.length; ++byteIndex) {
			for (int bitIndex = 7; bitIndex >= 0; --bitIndex) {
				// Another bad idea
				if (boolIndex >= pieces.length) {
					return retVal;
				}
				if (pieces[boolIndex++]) {
					retVal[byteIndex] |= (byte) (1 << bitIndex);
				}
			}
		}
		return retVal;
	}
	//end @author Robert Moore
	
	@Override
	public byte[] generateByteArray(){
		ByteBuffer bb = ByteBuffer.allocate(5+bitfield.length);
		bb.putInt(1 + bitfield.length);
		bb.put(this.id);
		bb.put(this.bitfield);	
		return bb.array();
	}
}
