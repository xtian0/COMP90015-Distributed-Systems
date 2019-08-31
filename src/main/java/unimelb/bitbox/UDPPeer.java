package unimelb.bitbox;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.logging.Logger;

import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;
import unimelb.bitbox.util.HostPort;
import unimelb.bitbox.util.Document;

public class UDPPeer {
	private static Logger log = Logger.getLogger(UDPPeer.class.getName());
	private FileSystemManager fileSystemManager;
	public static BlockingQueue<UDPDataPack> sendQueue;
	public static BlockingQueue<UDPDataPack> waitQueue;
	//private HashMap<HostPort, Queue<DataPack>> peerPacket = new HashMap<HostPort, Queue<DataPack>>();
	private DatagramSocket socket;
	
	public UDPPeer(FileSystemManager fileSystemManager) {
		this.fileSystemManager = fileSystemManager;
		sendQueue = new LinkedBlockingDeque<UDPDataPack>();
		waitQueue = new LinkedBlockingDeque<UDPDataPack>();
	}
	
	public void startUDP() {
		// udp starts
		log.info("starting up UDP mode...");
		try {
			this.socket = new DatagramSocket(PeerMaster.udpPort);
			
			// set up packets for initial connection
			for(String host:PeerMaster.peerArray) {
				HostPort peer = new HostPort(host);
				HostPort myHostPort = new HostPort(PeerMaster.myHost, PeerMaster.udpPort);
				RespondOnReq getDoc = new RespondOnReq(myHostPort);
				Document doc = getDoc.getDoc();
				UDPDataPack dp = new UDPDataPack(peer, doc);
				try {
					sendQueue.put(dp);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			
			Thread recT = new Thread(()->recPackets(socket));
			Thread sndT = new Thread(()->sndPackets(socket));
			Thread waitT = new Thread(()->processWaitQueue());
			Thread eventT = new Thread(()->packEvents());
			recT.start();
			sndT.start();
			waitT.start();
			eventT.start();
			
		} catch (SocketException e) {
			e.printStackTrace();
		}
	}
	
	private void recPackets(DatagramSocket socket) {
		while(true) {
			byte[] in = new byte[65535];
			DatagramPacket rec = new DatagramPacket(in, in.length);
			try {
				socket.receive(rec);
			} catch (IOException e) {
				e.printStackTrace();
			}
			String data = new String(in, 0, rec.getLength());
			Document docRec = Document.parse(data);
			String host = rec.getAddress().toString().substring(1);
			HostPort hostPort = new HostPort(host, rec.getPort());
			log.info("receiving document: " + docRec.toJson() + " from :" + hostPort.toString());
			UDPRequest handleReq = new UDPRequest(fileSystemManager, docRec, hostPort);
			handleReq.start();
		}
	}
	
	private void sndPackets(DatagramSocket socket) {
		while(true) {
			if(!sendQueue.isEmpty()) {
				try {
					// send the packet in the send queue
					UDPDataPack packtoSend = sendQueue.poll();
					socket.send(packtoSend.getDatagramPacket());
					log.info("send "+ packtoSend.getDocument().toJson() +" to: " + packtoSend.getHostPort().toString() 
							+ " for " + packtoSend.getRetry() + " times");
					// set the timer and retry counter for the packet sent
					long startTime = System.currentTimeMillis();
					packtoSend.setStartTime(startTime);
					packtoSend.incRetry();
					
					// move the packet to wait queue
					waitQueue.put(packtoSend);
				} catch (IOException e) {
					e.printStackTrace();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}
	
	private void processWaitQueue() {
		while(true) {
			try {
				for(UDPDataPack dp: waitQueue) {
					long endTime = System.currentTimeMillis();
					if(!dp.maxRetry()) {
						if(dp.isTimeOut(endTime)) {
							log.info("timeout of datapacket " + dp.getCmd() +", put to send queue");
							sendQueue.put(dp);
							waitQueue.remove(dp);
						}
					}else {
						waitQueue.remove(dp);
					}	
				}
				Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	
	private void packEvents() {
		while(true) {
			if(!PeerMaster.peerList.isEmpty()) {
				for(HostPort peer : PeerMaster.peerList) {
					if(!PeerMaster.peerEventQ.get(peer).isEmpty()) {
						try {
							FileSystemEvent event = PeerMaster.peerEventQ.get(peer).poll();
							RespondOnReq getDoc = new RespondOnReq(event);
							UDPDataPack eventPack = new UDPDataPack(peer, getDoc.getDoc());
							log.info("put event " + event.toString() + "to send queue");
							sendQueue.put(eventPack);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}
			}
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
}
