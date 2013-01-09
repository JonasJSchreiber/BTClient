package ru.gui;


import java.awt.GridLayout;
import java.util.HashMap;
import javax.swing.JFrame;
import ru.bit.torrent.Manager;
import ru.bit.torrent.Peer;

/**
 * Very simple GUI to show all peers and their state (connected, choking, interested, etc)
 * @author Dylan Murray
 */
public class PeerDisplay extends JFrame implements Runnable{
	
	/**
	 *  
	 */
	private static final long serialVersionUID = -4399635575171599804L;

	/**
	 * The Manager object which contains the list of Peers we need to access
	 */
	Manager manager;
	
	/**
	 * Peer hashmap.
	 */
	HashMap<String, Peer> peerMap = new HashMap<String, Peer>();
	
	/**
	 * The panel for displaying  list of peers
	 */
	PeersPanel peersPanel;
	
	/**
	 * The panel to display all selected peer's information
	 */
	PeerInfoPanel peerInfoPanel;
	
	/**
	 * Panel will just show what pieces we have.
	 */
	MyPiecesPanel myPiecesPanel;
	
	/**
	 * Panel will just show what pieces we need.
	 */
	NeededPiecesPanel neededPiecesPanel;
	
	/**
	 * The layout manger used to layout all the panels.
	 */ 
	protected GridLayout gl = new GridLayout(2, 2);

	
	public PeerDisplay(Manager manager){
		super("Peer Information");
		this.manager = manager;
		this.peerInfoPanel = new PeerInfoPanel(this);
		this.peersPanel = new PeersPanel(this);
		this.myPiecesPanel = new MyPiecesPanel(this);
		this.neededPiecesPanel = new NeededPiecesPanel(this);
		this.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		
		this.setResizable(true);
		//Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
		this.setLocation(100, 100);
		
		updateList();
		layOut();
		
		this.pack();
		this.setVisible(true);
	}

	@SuppressWarnings("unchecked")
	private void updateList() {
		for(int i=0; i < manager.peers.size(); i++){
			//if it's a new peer
			//System.err.println("MARCO");
			if(peerMap.put(new String(this.manager.peers.get(i).getPeerID()), this.manager.peers.get(i)) == null){
				//System.err.println("POLO");
				peersPanel.listModel.addElement(new String(this.manager.peers.get(i).getPeerID()));
			}
		}
	}
	
	public void displayInfo(){
		String myPieces = "";
		String needPieces = "";
		for(int i=0; i < manager.myPieces.length; i++){
			if(manager.myPieces[i]){
				myPieces = myPieces + i + ", ";
			} else {
				needPieces = needPieces + i + ", ";
			}
		}
		myPiecesPanel.havePieces.setText(myPieces);
		neededPiecesPanel.needPieces.setText(needPieces);
		
		//if nothing is selected
		if(peersPanel.peerList.getSelectedIndex() == -1){
			peerInfoPanel.connected.setText("<html><b>Connected:</b> </html>");
			peerInfoPanel.areChoking.setText("<html><b>Choked:</b> </html>");
			peerInfoPanel.areInterested.setText("<html><b>We're Interested:</b> </html>");
			peerInfoPanel.isChoking.setText("<html><b>Is Choking:</b> </html>");
			peerInfoPanel.isInterested.setText("<html><b>They're Interested:</b> </html>");
			peerInfoPanel.theirPieces.setText("Pieces: ");
			return;
		}
		 //when peer is selected
		Peer selectedPeer = peerMap.get(peersPanel.peerList.getSelectedValue());
		
		if(selectedPeer.amConnected()){
			peerInfoPanel.connected.setText("<html><b>Connected:</b> <font color=\"green\"> Yes </font></html>");
		} else {
			peerInfoPanel.connected.setText("<html><b>Connected:</b> <font color=\"red\"> No </font></html>");
		}
		
		if(selectedPeer.amChoking()){
			peerInfoPanel.areChoking.setText("<html><b>Choked:</b> <font color=\"red\"> Choked </font></html>");
		} else {
			peerInfoPanel.areChoking.setText("<html><b>Choked:</b> <font color=\"green\"> Unchoked </font></html>");
		}
		
		if(selectedPeer.amInterested()){
			peerInfoPanel.areInterested.setText("<html><b>We're Interested:</b> <font color=\"green\"> Interested </font></html>");
		} else {
			peerInfoPanel.areInterested.setText("<html><b>We're Interested:</b> <font color=\"red\"> Uninterested </font></html>");
		}
		
		if(selectedPeer.isChoking()){
			peerInfoPanel.isChoking.setText("<html><b>Is Choking Us:</b> <font color=\"red\"> Being Choked </font></html>");
		} else {
			peerInfoPanel.isChoking.setText("<html><b>Is Choking Us:</b> <font color=\"green\"> Unchoked </font></html>");
		}
		
		if(selectedPeer.isInterested()){
			peerInfoPanel.isInterested.setText("<html><b>Is Interested:</b> <font color=\"green\"> Interested </font></html>");
		} else {
			peerInfoPanel.isInterested.setText("<html><b>Is Interested:</b> <font color=\"red\"> Uninterested </font></html>");
		}
		
		String pieces = "";
		for(int i=0; i < selectedPeer.availablePieces.size(); i++){
			pieces = pieces + selectedPeer.availablePieces.get(i) + ", ";
		}
		peerInfoPanel.theirPieces.setText("Pieces: " + pieces);
		
		peerInfoPanel.uploadSpeed.setText("<html><b>Upload Speed:</b> " + selectedPeer.getUploadSpeed() + "</html>");
		peerInfoPanel.downloadSpeed.setText("<html><b>Download Speed:</b> " + selectedPeer.getDownloadSpeed() + "</html>");
	}
	
	/**
	 * Lays out all the panels using a grid bag layout.
	 */
	protected void layOut() {	
		getContentPane().setLayout(gl);
		getContentPane().add(peersPanel);
		getContentPane().add(peerInfoPanel);
		getContentPane().add(myPiecesPanel);
		getContentPane().add(neededPiecesPanel);
	}
	
	@Override
	public void run() {
		while(!manager.stopThreads){
			try {
				Thread.sleep(1000 * 2);
			} catch (InterruptedException e) {
				//very likely it will happen - doesn't hurt the program, don't print red.
			}
			updateList();
			displayInfo();
		}
		this.dispose();
	}
	
}
