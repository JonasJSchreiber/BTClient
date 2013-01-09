package ru.bit.torrent;

import java.io.*;
import java.util.Arrays;

/**
 * This class is used to interact with the file we're downloading to/ uploading from - it contains methods 
 * to read and write specific pieces from the file.
 * 
 * @author Jonas Schreiber
 * @author Charles Zuppe
 * @author Dylan Murray
 *
 */
public class FileAccess {
	
	/** 
	 * The RAF used to write to and read from file
	 */
	private RandomAccessFile raf;
	
	/**
	 * The file which the torrent ultimately creates
	 */
	private File file;
	
	Manager manager;
	
	TorrentInfo torrentInfo;
	
	/**
	 * Constructor for FileAccess
	 * @param manager
	 * @param torrentInfo
	 * @throws Exception
	 */
	public FileAccess(Manager manager, TorrentInfo torrentInfo) throws Exception
	{
		this.file = new File(manager.getDlPath());
		this.torrentInfo = torrentInfo;
		this.manager = manager;
		//Very important to see whether file already exists or is being created anew, as different steps will be taken in each case.
		if (!file.exists())
		{
			System.out.println("File doesn't exist, creating new file.");
			this.raf = new RandomAccessFile(file, "rw");
			raf.setLength(torrentInfo.file_length);
			writeNulls();
		}
		else 
		{
			this.raf = new RandomAccessFile(file, "rw");
			boolean[] myPieces = tallyPieces();
			StringBuffer sb = new StringBuffer();
			sb.append("The pieces this client already has are: ");
			for(int i=0; i < myPieces.length; i++){
				if(myPieces[i] == true)
					sb.append(i + ", ");
			}
			System.out.println(sb);
			manager.setMyPieces(myPieces);
		}
	}
	
	/**
	 * Utilizes RandomAccessFile to write a piece 
	 * @param pieceIndex
	 * @param pieceBytes
	 * @return
	 */
	public synchronized boolean writePiece(int pieceIndex, byte[] pieceBytes)
	{
		try {
			raf.seek(pieceIndex * torrentInfo.piece_length);
			raf.write(pieceBytes);
		} catch (IOException e) {
			return false;
		}
		return true;
	}
	
	/**
	 * Utilizes RandomAccessFile to read a piece
	 * @param pieceIndex
	 * @return
	 */
	public synchronized byte[] readPiece(int pieceIndex)
	{
		try {
			raf.seek(pieceIndex * torrentInfo.piece_length);
			byte[] piece = new byte[torrentInfo.piece_length];
			raf.read(piece, 0, torrentInfo.piece_length);
			return piece;
		} catch (IOException e) {
			return null;
		}
	}
	
	/**
	 * Called upoon creation of a new torrent file. Ensures that RandomAccessFile will not 
	 * try to access parts of the file that haven't been written. Also utilized in the 
	 * TallyPieces() method. 
	 * @throws IOException 
	 */
	public void writeNulls() throws IOException
	{
		byte[] nullBytes = new byte[]{ 0, 0, 0, 0,};
		for (int i = 0; i < torrentInfo.file_length; i += torrentInfo.piece_length)
		{
			raf.seek(i);
			raf.write(nullBytes);
		}
	}
	
	/**
	 * Called upon resuming a torrent download. This is used to determine which pieces have already
	 * been downloaded and written to file. A boolean array is created, length <number of pieces>, 
	 * and that in turn can be used to set manager's myPieces field. 
	 * @return
	 * @throws IOException
	 */
	public boolean[] tallyPieces() throws IOException
	{
		boolean[] myPieces = new boolean[torrentInfo.piece_hashes.length];
		byte[] comparator = new byte[]{ 0, 0, 0, 0,};
		byte[] temp = new byte[4];
		int i = 0;
		for (i = 0; i < torrentInfo.piece_hashes.length; i += 1)
		{
			raf.seek(i * torrentInfo.piece_length);
			raf.read(temp, 0, 4);
			if (Arrays.equals(comparator, temp))
			{
				myPieces[i] = false;
//				System.out.println("tallyPieces has found that piece: " + i + " is " + myPieces[i]);
			}
			else
			{
				byte[] piece;
				if (i == torrentInfo.piece_hashes.length - 1)
				{
					piece = new byte[torrentInfo.file_length % torrentInfo.piece_length];
//					System.out.println("Last Piece, piece index: " + i + " is " + piece.length + " bytes long.");
				}
				else
					piece = new byte[torrentInfo.piece_length];
				raf.seek(i * torrentInfo.piece_length);
				raf.read(piece, 0, piece.length);
				PieceMessage tempPM = new PieceMessage(i, 0, piece);
				if(manager.verifyPiece(tempPM))
					myPieces[i] = true;
				else
					myPieces[i] = false;
//				System.out.println("tallyPieces has found that piece: " + i + " is " + myPieces[i]);
			}
		}
		return myPieces;
	}
	
	public void close() throws IOException {
		raf.close();
	}
}
