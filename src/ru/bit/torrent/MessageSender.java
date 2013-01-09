package ru.bit.torrent;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * MessageSender is a thread instantiated in every MessageHandler who's duty is to send out all the messages in a given
 * peer's queue. This allows for true simultaneous sending and receiving.
 * 
 * @author Dylan Murray
 */
public class MessageSender implements Runnable {
	
	/**
	 * The peer to which this message sender is sending messages to.
	 */
	Peer peer;
	
	/**
	 * The dataoutput stream connected to our peer.
	 */
	DataOutputStream out;
	
	/**
	 * Boolean to tell whether or not this sender should be active.
	 */
	boolean active = true;
	
	/**
	 * Used in calculating the peer's download speed
	 */
	private Long start;
	
	/**
	 * Used in calculating the peer's download speed
	 */
	private Long end;

	/**
	 * Used in calculating the peer's download speed
	 */
	private int totalBytesFromPeer;
	
	/**
	 * Used in calculating the peer's upload speed
	 */
	private int totalBytesToPeer;
	
	public MessageSender(Peer peer, DataOutputStream out){
		this.peer = peer;
		this.out = out;
		this.totalBytesFromPeer = 0;
		this.totalBytesToPeer = 0;
		this.start = System.currentTimeMillis();
	}
	
	public void quit(){
		this.active = false;
	}
	
	@Override
	public void run() {
		//while we're not in exit mode
		while(active){
			//if there are no message to be sent, sleep for a second
			if(peer.messageQueue.size() == 0){
				try {
					Thread.sleep(1000 * 1);
					this.end = System.currentTimeMillis();
					publishUploadSpeed();
					publishDownloadSpeed();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} 
			//if there are messages to be sent, send the first message that was added to the queue (FIFO)
			else {
				try {
					if(this.peer.messageQueue.get(0).id != (Message.PIECE) && this.peer.messageQueue.get(0).id != (Message.REQUEST)){
						System.out.println("Sending " + this.peer.messageQueue.get(0) + " to " + this.peer);
					}
					Message.encode(out, this.peer.messageQueue.remove(0));
				} catch (IOException e) {
					e.printStackTrace();
					System.err.println("The peer has disconnected from us!");
					peer.disconnect();
					quit();
				}
			}
			this.end = System.currentTimeMillis();
			publishUploadSpeed();
			publishDownloadSpeed();
		}
	}
	
	public void resetStart() {
		this.start = System.currentTimeMillis();
	}

	public void resetEnd() {
		this.end = System.currentTimeMillis();
	}

	public void addTotalBytesFromPeer(int numBytes) {
		this.totalBytesFromPeer += numBytes;
	}

	public void addToTotalBytesToPeer(int numBytes) {
		this.totalBytesToPeer += numBytes;
	}
	
	public void publishDownloadSpeed(){
		double difference = (end - start);
		int downloadSpeed = (int) (totalBytesToPeer/difference);
		this.peer.setDownloadSpeed(downloadSpeed);
	}
	
	public void publishUploadSpeed(){
		double difference = (end - start);
		int uploadSpeed = (int) (totalBytesFromPeer/difference);
		this.peer.setUploadSpeed(uploadSpeed);
	}
	
	public void resetTotalBytesToPeer() {
		this.totalBytesToPeer = 0;
	}
	
	public void resetTotalBytesFromPeer() {
		this.totalBytesFromPeer = 0;
	}
	

}
