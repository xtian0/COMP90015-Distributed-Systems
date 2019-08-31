package unimelb.bitbox;

import unimelb.bitbox.util.HostPort;
import unimelb.bitbox.util.FileSystemManager;

import java.util.LinkedList;
import java.util.Queue;

import unimelb.bitbox.ConnectToPeer;

public class TCPClient extends Thread{
	private FileSystemManager fileSystemManager;
	
	public TCPClient(FileSystemManager fileSystemManager) {
		this.fileSystemManager = fileSystemManager;
	}
	
	@Override
	public void run() {
		for(String host: PeerMaster.peerArray) {
			HostPort peer = new HostPort(host);
			PeerMaster.peerToConnect.offer(peer);
		}
		// wait sometime for server to start
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		//Initial Connection to all peers
		while(true) {
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			if(!PeerMaster.peerToConnect.isEmpty()) {
				HostPort peer = PeerMaster.peerToConnect.poll();
				ConnectToPeer newConnection = new ConnectToPeer(this.fileSystemManager, peer);
				newConnection.start();
			}
		}
	}
	
}
