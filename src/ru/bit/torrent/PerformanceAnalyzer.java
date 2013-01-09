package ru.bit.torrent;

import java.util.ArrayList;

public class PerformanceAnalyzer implements Runnable {
	
	private Manager manager;
	
	private int totalNumPieces;
	
	private boolean peersPopulated;
	
	public PerformanceAnalyzer(Manager manager, int totalNumPieces) {
		this.manager = manager;
		this.totalNumPieces = totalNumPieces;
		this.peersPopulated = false;
		waitForPeersToPopulate();
		prioritizePieces();
	}

	/**
	 * Iterates over all peers to determine which pieces they have
	 * and keeps count of how many peers have the piece at that index.
	 * Then, it arranges those indexes from lowest to highest
	 * so, if only 1 peer has piece index 3 and 5 peers have the pieces
	 * at index 0, 1, 2, and 4, the resulting array would be
	 * [3, 0, 1, 2, 4]
	 */
	public void prioritizePieces() {
		while (!peersPopulated)
			try {
				Thread.sleep(100L);
			} catch (InterruptedException e) {}
		ArrayList<Integer> unsortedNeededPieces = manager.getUnsortedNeededPieces();
		int[] pieceIndexCounts = new int[totalNumPieces];
		for (Peer p : manager.peers)
		{
			if (p.amConnected())
			{
				for (int i = 0; i < p.availablePieces.size(); i++ )
				{
					if(unsortedNeededPieces.contains(p.availablePieces.get(i))) //if we need the piece that the peer has
							pieceIndexCounts[p.availablePieces.get(i)]++; //iterate that index in pieceIndexCounts		
				}
			}
		}

		//to ease sorting operations, I am using an arraylist here.
		ArrayList<Integer> piecesByRarity = new ArrayList<Integer>();
		int currentlyAddingPiecesWithXPeers = 0;
		while (piecesByRarity.size() < unsortedNeededPieces.size()) {
			for (int i = 0; i < pieceIndexCounts.length; i++)
			{
				if (pieceIndexCounts[i] == currentlyAddingPiecesWithXPeers && unsortedNeededPieces.contains(i))
				{
					piecesByRarity.add(i);
					pieceIndexCounts[i] = -1; //speed boost, so that we don't check this piece again.
				}
			}
			currentlyAddingPiecesWithXPeers++;
		}
		
		System.out.print("The pieces, ordered by rarity, are: ");
		for (int i : piecesByRarity)
			System.out.print(i + ", ");
		System.out.println();
		
		manager.piecesByRarity = piecesByRarity;
	}
	
	/**
	 * Waits while peers' available pieces populates, before trying to prioritize them.
	 */
	public void waitForPeersToPopulate() {
		while (!peersPopulated) {
			while (manager.peers.size() < 1)
				try {
					Thread.sleep(10L);
				} catch (InterruptedException e) {}
			for (Peer p : manager.peers)
			{
				if (p.availablePieces.size() > 0)
				{
					this.peersPopulated = true;
					break;
				}
			}
		}
	}
	
	/**
	 * Iterates through peer list and determines the peer with the slowest upload speed. 
	 * Depending on whether we have the full torrent file downloaded or not
	 * it will choke the peer with the slowest upload speed or the slowest download speed. 
	 * 
	 * Returns the peer that was choked.
	 */
	private Peer chokeSlowestPeer() {
		Peer slowest = null;
		boolean notnull = true;
		if (!manager.isDownloadComplete()) //We aren't yet ourselves a Seed, therefore we choke the peer who is uploading the slowest
		{
			for (Peer p : manager.peers)
			{
				if (notnull && p.amConnected() && !p.amChoking())
				{
					slowest = p;
					notnull = false;
				}
				else
					if (slowest != null && p.amConnected() && p.getUploadSpeed() < slowest.getUploadSpeed() && !p.amChoking())
					{
						System.out.println("Peer: " + p.toString() + " is currently uploading to us at: " + p.getUploadSpeed() + "kB/s");
						slowest = p;
					}
			}
		}
		else //We are a Seed, therefore we choke the peer with the slowest download speed
		{
			for (Peer p : manager.peers)
			{
				if (notnull && p.amConnected() && !p.amChoking())
				{
					slowest = p;
					notnull = false;
				}
				else if (slowest != null && p.amConnected() && !p.amChoking() && (p.getDownloadSpeed() < slowest.getDownloadSpeed()))
				{
					System.out.println("Peer: " + p.toString() + " is currently downloading from us at: " + p.getDownloadSpeed() + "kB/s");
					slowest = p;
				}
			}
		}
		if (slowest != null && !slowest.amChoking() && manager.numUnchoked >= 3)
		{
			System.out.println("Choking Peer: " + slowest.toString());
			slowest.messageQueue.add(new Message(Message.CHOKE));
			slowest.setChoking(true);
			manager.numUnchoked--;
			return slowest;
//			slowest.disconnect(); //No need to disconnect right?
		}
		return null;
	}
	
	/**
	 * From a pool of currently choked peers, choose one to unchoke.
	 */
	private void unchokeRandom(Peer dontUnchoke){
		ArrayList<Peer> chokedPeers = new ArrayList<Peer>();
		for(int i=0; i < manager.peers.size(); i++){
			if(manager.peers.get(i).amChoking() && manager.peers.get(i).isInterested() && !manager.peers.get(i).equals(dontUnchoke)){
				chokedPeers.add(manager.peers.get(i));
			}
		}
		
		//Credit this random number code to StackOverflow.com
		int min = 0;
		int max = chokedPeers.size()-1;
		int randomIndex = min + (int)(Math.random() * ((max - min) + 1));

		//TODO: only thing i'm concerned about is that this message won't get sent until end of processMessage cycle
		//If we're choking this peer, it means we're waiting to read from socket; socket waits can be up to
		//120 seconds while we need to do this unchoke/choke business every 30 seconds.. could be an issue
		if(manager.numUnchoked < 6){
			System.out.println("Unchoking " + chokedPeers.get(randomIndex)+ " at random.");
			chokedPeers.get(randomIndex).messageQueue.add(new Message(Message.UNCHOKE));
			chokedPeers.get(randomIndex).setChoking(false);
			manager.numUnchoked++;
		}

	}

	/**
	 * Run method has two Thread.sleep() calls. This is because during debugging, you
	 * don't want to have to wait for 30 seconds for it to do something. Also, when it 
	 * is fully completed, the sooner during runtime that the performance analysis is 
	 * started, the better it will be for the program's overall performance. However,
	 * it still needs a few seconds to accumulate some data, in order to complete this 
	 * analysis effectively. 
	 */
	public void run() {
		while(!manager.stopThreads)
		{
			try {
				Thread.sleep(30000L);
			} catch (InterruptedException e) {
				//This, in all likelihood will be called. It is best not to print a stack trace and worry the user.
			}
			//We want to make sure we don't unchoke the peer we just choked, so keep track of it.
			Peer ret = chokeSlowestPeer();
			unchokeRandom(ret);
			prioritizePieces();
		}
	}
}
