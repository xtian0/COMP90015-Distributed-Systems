package unimelb.bitbox;

import java.io.*;
import java.net.Socket;
import java.util.logging.Logger;

import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.HostPort;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;
import unimelb.bitbox.util.FileSystemManager;

public class ConnectFromPeer extends Thread {
	private static Logger log = Logger.getLogger(ConnectFromPeer.class.getName());
	private FileSystemManager fileSystemManager;
	private HostPort localHostPort;
	private HostPort targetPeer;
	private Socket socket;
	private BufferedReader myReader;
	private PrintWriter myWriter;
	
	private boolean isHandshake;
	
	public ConnectFromPeer(FileSystemManager fileSystemManager, Socket socket) {
		this.fileSystemManager = fileSystemManager;
		this.socket = socket;
		this.isHandshake = false;
		
		String localHost = Configuration.getConfigurationValue("advertisedName");
		int localPort = Integer.parseInt(Configuration.getConfigurationValue("port"));
		this.localHostPort = new HostPort(localHost, localPort);
	}
	
	@Override
	public void run() {
		try {
			// Input stream Output Stream
			BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
			PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

			Document handShakeReque = Document.parse(reader.readLine());

			if (!handShakeReque.containsKey("command")) {
				// not exist cmd message
				Document output = Protocol.INVALID_PROTOCOL("message must contain a command field as string");
				writer.println(output.toJson());
				socket.close();
			}else if (!handShakeReque.containsKey("hostPort")) {
				// Cannot Identify peer, Exit.
				Document output = Protocol.INVALID_PROTOCOL("message must contain host and port for handshake");
				writer.println(output.toJson());
				socket.close();
			}else {
				// Otherwise
				if (!handShakeReque.getString("command").equals("HANDSHAKE_REQUEST")) {
					// Not Valid Command
					Document output = Protocol.INVALID_PROTOCOL("handshake is required");
					writer.println(output.toJson());
					socket.close();
				} else {
					// HANDSHAKE_REQUEST is received
					Document hostPort = (Document)handShakeReque.get("hostPort");
					HostPort currentClient= new HostPort(hostPort);
					
					if(PeerMaster.isPeerFull()){
						// peer full
						Document handShakeRspon = Protocol.CONNECTION_REFUSED(PeerMaster.peerListToDoc());
						writer.println(handShakeRspon.toJson());
						socket.close();
					}else if(PeerMaster.containPeer(currentClient)) {
						// receive handshake after handshake
						Document handShakeRspon = Protocol.INVALID_PROTOCOL("handshake request after successful handshake");
						writer.println(handShakeRspon.toJson());
						socket.close();
					}
					else {
						// handshake response
						log.info("Handshake success with client " + socket.getInetAddress().getHostAddress()
								+ ":" + socket.getPort());
						PeerMaster.addPeer(currentClient);
						Document handShakeRspon = Protocol.HANDSHAKE_RESPONSE(this.localHostPort);
						writer.println(handShakeRspon.toJson());
						this.targetPeer = currentClient;
						//PeerMaster.numPeersConnection++;
						this.isHandshake = true;
						this.myReader = reader;
						this.myWriter = writer;
					}
				}
			}

		} catch (IOException e) {
			e.printStackTrace();
		}
		
		
		// if handshake success, process event
		while(this.isHandshake) {
			// handle request
			try {
				if(this.myReader.ready()) {
					Document request = Document.parse(this.myReader.readLine());
					if(request != null) {
						ProcessRequest requestprocessor = 
								new ProcessRequest(this.fileSystemManager, request, this.socket, this.myReader, this.myWriter);
						requestprocessor.start();
					}	
				}
				
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			// handle events
			if(!PeerMaster.peerEventQ.isEmpty() && !PeerMaster.peerEventQ.get(this.targetPeer).isEmpty()) {
				//FileSystemEvent newEvent = FILE_CREATE;
				FileSystemEvent newEvent = PeerMaster.peerEventQ.get(this.targetPeer).poll();
				ProcessEvent ep = new ProcessEvent(this.fileSystemManager, newEvent, this.socket,this.myReader, this.myWriter);
				ep.start();
			}
		}
		// disconnnect this peer if it is in disconnect list
		if(PeerMaster.inDisconList(this.targetPeer)) {
			this.isHandshake = false;
		}
		PeerMaster.removePeer(this.targetPeer);
		log.info(this.targetPeer.toString() + " is disconnected");
	}

}