package unimelb.bitbox;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.logging.Logger;

import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.HostPort;

public class UDPDataPack {
	private Logger log = Logger.getLogger(ProcessRequest.class.getName());
	private HostPort hostPort;
	private Document document;
	private DatagramPacket datagramPacket;
	private long startTime;
	private int retryTimes;
	
	public UDPDataPack(HostPort hostPort, Document document) {
		this.hostPort = hostPort;
		this.document = document;
		this.retryTimes = 0;
		
		packDoc();
	}
	
	// pack the document into a datagram packet
	public void packDoc() {
		InetAddress targetHost;
		try {
			targetHost = InetAddress.getByName(hostPort.host);
			int targetPort = hostPort.port;
			byte[] output = document.toJson().getBytes();
			this.datagramPacket = new DatagramPacket(output, output.length, targetHost, targetPort);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
	}
	
	// set the start time for the packet
	public void setStartTime(long startTime) {
		this.startTime = startTime;
	}
	
	// increase retry times
	public void incRetry() {
		this.retryTimes += 1;
	}
	
	// check if maximum retry is met
	public boolean maxRetry() {
		// if it is a response, do not need to retry;
		if(this.getCmd().contains("RESPONSE")) {
			return true;
		}
		// if an invalid protocol, do not need to retry;
		if(this.getCmd().contains("INVALID")) {
			return true;
		}
		return retryTimes > PeerMaster.udpRetries;
	}
	
	// check if timeout is met
	public boolean isTimeOut(long endTime) {
		return (endTime-startTime) > PeerMaster.udpTimeout;
	}
	
	// check if response is for this packet
	public boolean validate_Response(HostPort rspHostPort, Document rspDoc) {
		String docCmd = document.getString("command");
		String rspCmd = rspDoc.getString("command");
		
		// HANDSHAKE
		// RESPONSE
		if(docCmd.equals("HANDSHAKE_REQUEST") && rspCmd.equals("HANDSHAKE_RESPONSE")) {
			Document hpDoc = (Document) rspDoc.get("hostPort");
			HostPort hp = new HostPort(hpDoc);
			if (!rspHostPort.equals(hp)) {
				log.info("Found unmached HANDSHAKE Response: " + rspDoc.toJson() + 
						"from unmatched host: " + rspHostPort.toString());
			} else if(this.hostPort.equals(hp)) {
				log.info("response detected");
				return true;
			}	
		}
		// CONNECTION_REFUSED
		if(docCmd.equals("HANDSHAKE_REQUEST") && rspCmd.equals("CONNECTION_REFUSED")) {
			if(this.hostPort.equals(rspHostPort)) {
				log.info("CONNECTION REFUSED FROM: " + rspHostPort.toString());
				return true;
			}
		}
		
		// FILE_CREATE
		if(docCmd.equals("FILE_CREATE_REQUEST") && rspCmd.equals("FILE_CREATE_RESPONSE")) {
			Document docFD = (Document) document.get("fileDescriptor");
			Document rspFD = (Document) document.get("fileDescriptor");
			String docPathName = document.getString("pathName");
			String rspPathName = rspDoc.getString("pathName");
			if(hostPort.equals(rspHostPort) && docFD.toJson().equals(rspFD.toJson()) && docPathName.equals(rspPathName)) {
				return true;
			}
		}
		
		// FILE_DELETE
		if(docCmd.equals("FILE_DELETE_REQUEST") && rspCmd.equals("FILE_DELETE_RESPONSE")) {
			Document docFD = (Document) document.get("fileDescriptor");
			Document rspFD = (Document) document.get("fileDescriptor");
			String docPathName = document.getString("pathName");
			String rspPathName = rspDoc.getString("pathName");
			if(hostPort.equals(rspHostPort) && docFD.toJson().equals(rspFD.toJson()) && docPathName.equals(rspPathName)) {
				return true;
			}
		}
		
		// FILE_MODIFY
		if(docCmd.equals("FILE_MODIFY_REQUEST") && rspCmd.equals("FILE_MODIFY_RESPONSE")) {
			Document docFD = (Document) document.get("fileDescriptor");
			Document rspFD = (Document) document.get("fileDescriptor");
			String docPathName = document.getString("pathName");
			String rspPathName = rspDoc.getString("pathName");
			if(hostPort.equals(rspHostPort) && docFD.toJson().equals(rspFD.toJson()) && docPathName.equals(rspPathName)) {
				return true;
			}
		}
		
		// DIRECTORY_CREATE
		if(docCmd.equals("DIRECTORY_CREATE_REQUEST") && rspCmd.equals("DIRECTORY_CREATE_RESPONSE")) {
			String docPathName = document.getString("pathName");
			String rspPathName = rspDoc.getString("pathName");
			if(hostPort.equals(rspHostPort) && docPathName.equals(rspPathName)) {
				return true;
			}
		}
		
		// DIRECTORY_DELETE
		if(docCmd.equals("DIRECTORY_DELETE_REQUEST") && rspCmd.equals("DIRECTORY_DELETE_RESPONSE")) {
			String docPathName = document.getString("pathName");
			String rspPathName = rspDoc.getString("pathName");
			if(hostPort.equals(rspHostPort) && docPathName.equals(rspPathName)) {
				return true;
			}
		}
		
		// FILE_BYTES
		if(docCmd.equals("FILE_BYTES_REQUEST") && rspCmd.equals("FILE_BYTES_RESPONSE")) {
			Document docFD = (Document) document.get("fileDescriptor");
			Document rspFD = (Document) rspDoc.get("fileDescriptor");
			String docPathName = document.getString("pathName");
			String rspPathName = rspDoc.getString("pathName");
			long docPosition = document.getLong("position");
			long rspPosition = rspDoc.getLong("position");
			long docLength = document.getLong("length");
			long rspLength = document.getLong("length");
			if(hostPort.equals(rspHostPort) && docFD.toJson().equals(rspFD.toJson()) && docPathName.equals(rspPathName)
					 && docPosition == rspPosition && docLength == rspLength) {
				return true;
			}
		}
		
		return false;
	}
	
	/** get values
	 *  from data pack
	*/
	public DatagramPacket getDatagramPacket() {
		return this.datagramPacket;
	}
	public HostPort getHostPort() {
		return this.hostPort;
	}
	public Document getDocument() {
		return this.document;
	}
	public int getRetry() {
		return this.retryTimes;
	}
	public String getCmd() {
		return this.document.getString("command");
	}

}
