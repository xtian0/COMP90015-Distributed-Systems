package unimelb.bitbox;

import java.util.logging.Logger;

import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.HostPort;

public class SyncEvent extends Thread{
	private Logger log = Logger.getLogger(SyncEvent.class.getName());
	private FileSystemManager fileSystemManager;
	private long interval;
	
	public SyncEvent(FileSystemManager fileSystemManager) {
		this.fileSystemManager = fileSystemManager;
		this.interval = PeerMaster.syncInterval;
	}

	@Override
	public void run() {
		while(true) {
			log.info("Generating Sync Events");
			
			if(!this.fileSystemManager.generateSyncEvents().isEmpty()) {
				// if there is event, add to queue
				PeerMaster.eventToPeer(this.fileSystemManager.generateSyncEvents());
				log.info("Current connections: ");
				for(HostPort peer: PeerMaster.peerList) {
					log.info("  --" + peer.toString());
				}
			}
			try {
				Thread.sleep(this.interval * 1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
}
