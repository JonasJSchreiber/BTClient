package ru.gui;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;

public class NeededPiecesPanel extends JPanel{
	/**
	 * 
	 */
	private static final long serialVersionUID = -5466568088260848536L;
	PeerDisplay peerDisplay;
	JLabel needLabel;
	JTextArea needPieces;
	
	public NeededPiecesPanel(PeerDisplay peerDisplay){
		this.peerDisplay = peerDisplay;
		this.needLabel = new JLabel("<html><b>Pieces Needed: </b></html>");
		this.needPieces = new JTextArea("");
		this.needPieces.setLineWrap(true);
		this.needPieces.setWrapStyleWord(true);
		labelLayOut();
	}
	
	void labelLayOut(){
		setLayout(new GridBagLayout());
		GridBagConstraints gc = new GridBagConstraints();
		
		gc.gridx = 0;
		gc.gridy = 0;
		add(needLabel, gc);
		
		gc.gridx = 1;
		gc.gridy = 0;
		add(needPieces, gc);
	}
}
