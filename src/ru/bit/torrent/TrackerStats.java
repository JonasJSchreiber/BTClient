package ru.bit.torrent;

import java.io.*;

public class TrackerStats {

	/**
	 * The file that contains the statistics.
	 * Will take the form: <download file> + "stats.txt" * 
	 */
	private File file;
	
	/**
	 * Integer corresponding to the total number of bytes uploaded
	 */
	private int uploaded;
	
	/**
	 * Integer corresponding to the total number of bytes downloaded
	 */
	private int downloaded;
	
	/**
	 * Integer corresponding to the total number of bytes our client
	 * has yet to download
	 */
	private int left;
	
	/**
	 * The tracker instantiated by Manager
	 */
	private Tracker tracker;
	
	/**
	 * Creates a new TrackerStats object. Depending on whether or not the 
	 * file containing statistics exists, this will do different things. 
	 * @param fileName
	 * @param torrentInfo
	 */
	public TrackerStats(String fileName, Tracker tracker) {
		File file = new File(fileName + "stats.txt");
		this.file = file;
		this.tracker = tracker;
		if (file.exists())
		{
			try {
				readStats();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		else 
		{
			this.uploaded = 0;
			this.downloaded = tracker.getDownloaded();
			this.left = tracker.getLeft();
		}
	}
	
	/**
	 * Reads statistics from the stats file into buffers.
	 * @throws IOException
	 */
	public void readStats() throws IOException {
		BufferedReader br = new BufferedReader(new FileReader(this.file));
		String uploadedStr = br.readLine();
		this.uploaded = Integer.parseInt(uploadedStr.substring(10, uploadedStr.length()));
		tracker.setUploaded(this.uploaded);
		this.downloaded = 0;
		this.left = tracker.getLeft();
	}
	
	/**
	 * Adds bytes to the number of bytes uploaded. 
	 * @param numBytes
	 */
	public int getUploaded() {
		return this.uploaded;
	}
	
	public void addToUploaded(int numBytes) {
		this.uploaded += numBytes;
	}
	
	public void incrementDownloaded(int numBytes) {
		this.downloaded += numBytes;
		this.left -= numBytes;
	}
	
	public void setDownloaded(int numBytes) {
		this.downloaded = numBytes;
	}
	
	public int getDownloaded() {
		return this.downloaded;
	}
	/**
	 * Writes the buffers back to a file
	 * @throws IOException
	 */
	public void close() throws IOException {
		this.uploaded = tracker.getUploaded();
		this.downloaded += tracker.getDownloaded();
		this.left -= tracker.getLeft();
		OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(this.file));
		out.write("uploaded: " + this.uploaded + "\n");
		out.write("downloaded: " + this.downloaded + "\n");
		out.write("left: " + this.left + "\n");
		out.flush();
		out.close();
	}
}
