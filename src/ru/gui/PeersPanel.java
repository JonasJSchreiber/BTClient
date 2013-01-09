package ru.gui;

import java.awt.BorderLayout;

import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

public class PeersPanel extends JPanel implements ListSelectionListener{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1047552531788769310L;
	@SuppressWarnings("rawtypes")
	JList peerList;
	@SuppressWarnings("rawtypes")
	DefaultListModel listModel;
	PeerDisplay peerDisplay;

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public PeersPanel(PeerDisplay peerDisplay){
		super(new BorderLayout());
		this.peerDisplay = peerDisplay;
		listModel = new DefaultListModel();
		peerList = new JList(listModel);
		peerList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		peerList.setSelectedIndex(-1);
		peerList.addListSelectionListener(this);
		peerList.setVisibleRowCount(30);
		JScrollPane listScrollPane = new JScrollPane(peerList);
		add(listScrollPane, BorderLayout.CENTER);
	}

	@Override
	public void valueChanged(ListSelectionEvent e) {
		if(e.getValueIsAdjusting() == false){
			if(peerList.getSelectedIndex() == -1){
				//do nothing
			} else {
				peerDisplay.displayInfo();
			}
		}
	}

}
