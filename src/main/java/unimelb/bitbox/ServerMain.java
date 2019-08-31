package unimelb.bitbox;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Logger;
import java.util.ArrayList;

import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.FileSystemObserver;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;

import unimelb.bitbox.TCPServer;
import unimelb.bitbox.TCPClient;
import unimelb.bitbox.util.HostPort;

public class ServerMain implements FileSystemObserver {
	private static Logger log = Logger.getLogger(ServerMain.class.getName());
	protected FileSystemManager fileSystemManager;
	//Queue<FileSystemEvent> eventQueue = new LinkedList<FileSystemEvent>();
	
	public ServerMain() throws NumberFormatException, IOException, NoSuchAlgorithmException {
		fileSystemManager=new FileSystemManager(PeerMaster.path,this);
	
		// check desired running mode for the peer
		if(!PeerMaster.mode.equals("tcp") && !PeerMaster.mode.equals("udp")) {
			// mode is not udp or tcp
			log.info("mode is not correctly set, peer not starting.");
			
		}else {
			if(PeerMaster.mode.equals("tcp")) {
				log.info("Peer is starting in TCP mode");
				
				// initialize Server part
				TCPServer newServer = new TCPServer(this.fileSystemManager);
				newServer.start();
				
				// initialize Client part
				TCPClient newClient = new TCPClient(this.fileSystemManager);
				newClient.start();
			}else {
				log.info("Peer is starting in UDP mode.");
				UDPPeer udpPeer = new UDPPeer(fileSystemManager);
				udpPeer.startUDP();
			}
			
			// listening for client
			SecureServer ss = new SecureServer(PeerMaster.clientPort);
			ss.start();
			
			// wait some time for connection, generate Sync events
			try {
				Thread.sleep(3000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			SyncEvent sync = new SyncEvent(fileSystemManager);
			sync.start();
			
		}	
		
	}

	@Override
	public void processFileSystemEvent(FileSystemEvent fileSystemEvent) {
		// push file system event to queue
		PeerMaster.eventToPeer(fileSystemEvent);
	}
	
}
