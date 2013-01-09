package ru.gui;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;

public class MyPiecesPanel extends JPanel{
	/**
	 * 
	 */
	private static final long serialVersionUID = -1672797764598670774L;
	PeerDisplay peerDisplay;
	JLabel haveLabel;
	JTextArea havePieces;

	
	public MyPiecesPanel(PeerDisplay peerDisplay){
		this.peerDisplay = peerDisplay;
		this.haveLabel = new JLabel("<html><b>My Pieces: </b></html>");
		this.haveLabel.setMinimumSize(this.haveLabel.getPreferredSize());
		this.havePieces = new JTextArea("");
		this.havePieces.setLineWrap(true);
		this.havePieces.setWrapStyleWord(true);
		labelLayOut();
	}
	
	void labelLayOut(){
		setLayout(new GridBagLayout());
		GridBagConstraints gc = new GridBagConstraints();
		
		gc.gridx = 0;
		gc.gridy = 0;
		add(haveLabel, gc);
		
		gc.gridx = 1;
		gc.gridy = 0;
		add(havePieces, gc);
	}
}
