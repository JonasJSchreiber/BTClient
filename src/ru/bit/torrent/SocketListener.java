package ru.bit.torrent;

import java.io.*;
import java.net.*;

/**
 * 
 * @author Jonas J. Schreiber
 * This code is used to listen on a socket (currently port 6881) and accept incoming peer connections.
 * Some of this code is attributable to the author of http://docs.oracle.com/javase/tutorial/networking/sockets/clientServer.html
 */
public class SocketListener implements Runnable {
	
	private TorrentInfo torrentInfo;
	
	private Manager manager;
	
	private ServerSocket listeningSocket;
	
	public SocketListener(Manager manager, TorrentInfo torrentInfo)
	{
		this.torrentInfo = torrentInfo;
		this.manager = manager;
	}
	
	public boolean portOpen(int port) {
		ServerSocket temp = null;
		try {
			temp = new ServerSocket(port);
		} catch(IOException e) {
			return false;
		} finally {
			if (temp != null)
				try {
					temp.close();
				} catch (IOException e) {}
		}
		return true;
	}
	
	public void disconnect() {
		try {
			listeningSocket.close();
		} catch (IOException e) {
			 //this shouldn't be called.
		} catch (NullPointerException npe) {
			// this will likely be called. Let's not print a stack trace.
		}
	}

	@Override
	public void run() {
		Peer potentialPeer = null;
		int i;
		for(i=6881; i <=65536; i++)
		{
			if(portOpen(i))
				break;
	    }
		manager.setListeningPort(i);
		ServerSocket listeningSocket = null;
		try {
			listeningSocket = new ServerSocket(i);
			this.listeningSocket = listeningSocket;
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		Socket clientSocket = null;
		while(!manager.stopThreads) //need to break at some point, and close inputstream
		{
			try {	
				clientSocket = listeningSocket.accept();
				DataInputStream dis = new DataInputStream(clientSocket.getInputStream());
				byte[] handshake = new byte[68];
				dis.readFully(handshake);
				byte[] peerID = new byte[20];
				System.arraycopy(handshake, handshake.length-20, peerID, 0, 20);
				potentialPeer = new Peer(clientSocket.getPort(), clientSocket.getInetAddress().getHostAddress(), peerID);
				potentialPeer.setSocket(clientSocket);
				potentialPeer.receivedHandshake = true;
				System.out.println("Received handshake from peer " + potentialPeer);
				byte[] peerHash = new byte[20];
				System.arraycopy(handshake, (handshake.length-41), peerHash, 0, 20);
				if(!Tracker.containsPeer(manager.peers, potentialPeer)){
					manager.peers.add(potentialPeer);
					Thread messageHandler = new Thread(new MessageHandler(manager, potentialPeer, torrentInfo));
					Runtime.getRuntime().addShutdownHook(messageHandler);
					potentialPeer.setMessageHandler(messageHandler);
					messageHandler.start();
				} else {
					System.out.println("We're already connected to this peer.");
				}
			} catch (IOException e) {
				//no print stacktrace
			}
		}
	}
}
