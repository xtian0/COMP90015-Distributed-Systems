package unimelb.bitbox;

import java.util.logging.Logger;
import java.util.Queue;
import java.util.LinkedList;
import java.util.ArrayList;
import java.io.*;
import java.net.Socket;

import unimelb.bitbox.util.HostPort;
import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.Protocol;

public class ConnectToPeer extends Thread {
	private static Logger log = Logger.getLogger(ConnectToPeer.class.getName());
	private FileSystemManager fileSystemManager;
	private HostPort targetPeer;
	private Queue<HostPort> peersAvailable; 
	private Socket mySocket;
	private BufferedReader myReader;
	private PrintWriter myWriter;
	private HostPort localHostPort;
	
	
	public ConnectToPeer(FileSystemManager fileSystemManager, HostPort targetPeer) {
		this.fileSystemManager = fileSystemManager;
		this.targetPeer = targetPeer;
		this.peersAvailable = new LinkedList<HostPort>();
		this.peersAvailable.offer(targetPeer);
		
		String localHost = PeerMaster.myHost;
		int localPort = PeerMaster.myPort;
		this.localHostPort = new HostPort(localHost, localPort);
	}
	
	@Override
	public void run() {
		boolean result = this.Connect();
		while(result) {
			// if a peer is found to connect, go to handle events and request
			
			//handle response
			try {
				if(this.myReader.ready()) {
					Document response = Document.parse(this.myReader.readLine());
					ProcessRequest rp = 
							new ProcessRequest(this.fileSystemManager, response, this.mySocket, this.myReader, this.myWriter);
					rp.start();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			// handle event
			if(!PeerMaster.peerEventQ.isEmpty() && !PeerMaster.peerEventQ.get(this.targetPeer).isEmpty()) {
				FileSystemEvent newEvent = PeerMaster.peerEventQ.get(this.targetPeer).poll();
				ProcessEvent ep = new ProcessEvent(this.fileSystemManager, newEvent, this.mySocket, this.myReader, this.myWriter);
				ep.start();
			}
			
			// disconnnect this peer if it is in disconnect list
			if(PeerMaster.inDisconList(this.targetPeer)) {
				result = false;
			}
		}
		PeerMaster.removePeer(this.targetPeer);
		log.info(this.targetPeer.toString() + " is disconnected");
	}
	
	public boolean Connect(){
		while(!peersAvailable.isEmpty()){
			// get the head of queue for the peer to connect
			HostPort peer = peersAvailable.poll();
			try {
				Socket socket = new Socket(peer.host, peer.port);
				BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF8"));
				PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF8"),true);
				
				Document hskRequest = Protocol.HANDSHAKE_REQUEST(this.localHostPort);
				writer.println(hskRequest.toJson());
				writer.flush();
				log.info("Trying to Handshake with peer " + peer.host + ":" + peer.port);
				
				Document hskResponse = Document.parse(reader.readLine());
				log.info("Server respond handshake with: " + hskResponse.toJson());

				// parsing returned document command
				switch(hskResponse.getString("command")) {
					// Handshake successfully
				    case "HANDSHAKE_RESPONSE":
				    	Document serverhost = (Document) hskResponse.get("hostPort");
				    	HostPort hostReturned = new HostPort(serverhost);
				    	// this.targetPeer = new HostPort(targetHost,targetPort);
				    	this.targetPeer = hostReturned;
				    	this.mySocket = socket;
				    	this.myReader = reader;
				    	this.myWriter = writer;
				    	PeerMaster.addPeer(this.targetPeer);
				    	log.info("peer " + targetPeer.toString() + " is connected!");
				    	return true;
				    // Connection is denied, retrieve possible target peers
				    case "CONNECTION_REFUSED":
				    	log.info("Connection rejected by peer " + peer.toString());
				    	@SuppressWarnings("unchecked")
				    	ArrayList<Document> peersOnList = (ArrayList<Document>) hskResponse.get("peers");
				    	
				    	// loop through all peers returned from server
				    	for(Document potentialPeer:peersOnList) {
				    		HostPort potentialHost = new HostPort(potentialPeer.getString("host")
				    				,potentialPeer.getInteger("port"));
				    		// add the target peer into peerAvailable
				    		peersAvailable.offer(potentialHost);	
				    	}
				    	socket.close();
				    // otherwise
				    default:
				    	log.info("Invalid, Closing connection for " + peer.toString());
				    	socket.close();
				}
			} catch(IOException e) {
				log.info(e.toString());
				return false;
			}	
		}
		return false;
	}

}