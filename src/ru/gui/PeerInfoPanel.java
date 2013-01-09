package ru.gui;

import java.awt.GridLayout;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;

public class PeerInfoPanel extends JPanel {
	
	/**
	 *  
	 */
	private static final long serialVersionUID = -3500137738076799182L;
	PeerDisplay peerDisplay;
	JLabel connected;
	JLabel areChoking;
	JLabel isChoking;
	JLabel isInterested;
	JLabel areInterested;
	JLabel uploadSpeed;
	JLabel downloadSpeed;
	JTextArea theirPieces;
	
	public PeerInfoPanel(PeerDisplay peerDisplay){
		this.peerDisplay = peerDisplay;
		connected = new JLabel("<html><b>Connected:</b> </html>");
		areChoking = new JLabel("<html><b>Choked:</b></html>");
		isChoking = new JLabel("<html><b>Is Choking:</b></html>");
		isInterested = new JLabel("<html><b>They're Interested:</b></html>");
		areInterested = new JLabel("<html><b>We're Interested:</b></html>");
		theirPieces = new JTextArea("Pieces:");
		theirPieces.setLineWrap(true);
		theirPieces.setWrapStyleWord(true);
		uploadSpeed = new JLabel("<html><b>Upload Speed:</b> </html>");
		downloadSpeed = new JLabel("<html><b>Download Speed:</b> </html>");
		
		labelLayOut();
	}
	
	void labelLayOut(){
		setLayout(new GridLayout(4,1,2,2));
		add(connected);
		add(areChoking);
		add(isChoking);
		add(isInterested);
		add(areInterested);
		add(theirPieces);
		add(uploadSpeed);
		add(downloadSpeed);
	}

}
