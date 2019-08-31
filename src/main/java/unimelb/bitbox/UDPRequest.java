package unimelb.bitbox;

import java.util.ArrayList;
import java.util.logging.Logger;

import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.HostPort;

public class UDPRequest extends Thread{
	private static Logger log = Logger.getLogger(UDPRequest.class.getName());
	private FileSystemManager fileSystemManager; 
	private Document request;
	private String command;
	private HostPort hostPort;
	
	
	public UDPRequest(FileSystemManager fileSystemManager,Document request) {
		this.fileSystemManager = fileSystemManager;
		this.request = request;
		this.command = request.getString("command");
	}

	public UDPRequest(FileSystemManager fileSystemManager,Document request, HostPort hostPort) {
		this.fileSystemManager = fileSystemManager;
		this.request = request;
		this.command = request.getString("command");
		this.hostPort = hostPort;
	}
	
	
	@Override
	public void run() {
		log.info("get from peer: " + command);
		switch(command) {
		case "HANDSHAKE_REQUEST":
			// process handshake request;
			processHandshakeRequest();
			break;
		case "HANDSHAKE_RESPONSE":
			processResponse();
			processHandshakeResponse();
			break;
		case "CONNECTION_REFUSED":
			processConnectionRefused();
			processResponse();
			break;
		case "FILE_CREATE_REQUEST":
			processFileCreate();
			break;
		case "FILE_CREATE_RESPONSE":
			processResponse();
			break;
		case "FILE_DELETE_REQUEST":
			processFileDelete();
			break;
		case "FILE_DELETE_RESPONSE":
			processResponse();
			break;
		case "FILE_MODIFY_REQUEST":
			processFileModify();
			break;
		case "FILE_MODIFY_RESPONSE":
			processResponse();
			break;
		case "DIRECTORY_CREATE_REQUEST":
			processDirectoryCreateRequest();
			break;
		case "DIRECTORY_CREATE_RESPONSE":
			processResponse();
			break;
		case "DIRECTORY_DELETE_REQUEST":
			processDirectoryDeleteRequest();
			break;
		case "DIRECTORY_DELETE_RESPONSE":
			processResponse();
			break;
		case "FILE_BYTES_REQUEST":
			processFileByteRequest();
			break;
		case "FILE_BYTES_RESPONSE":
			processFileByteResponse();
			processResponse();
			break;
		default:
			processInvalid();
			break;
		}

	}
	
	// remove corresponding request from wait queue if response is received
	private void processResponse() {
		for(UDPDataPack dp : UDPPeer.waitQueue) {
			if(dp.validate_Response(hostPort, request)) {
				log.info("valid response found");
				UDPPeer.waitQueue.remove(dp);
			}
		}
	}
	
	// hand handshake request
	private void processHandshakeRequest() {
		Document response = new Document();
		if(PeerMaster.isPeerFull()) {
			// incoming full, connection refused
			response = Protocol.CONNECTION_REFUSED(PeerMaster.peerListToDoc());
		}else {
			// can take more incoming
			Document inPeerDoc = (Document) request.get("hostPort");
			HostPort inPeer= new HostPort(inPeerDoc);
			HostPort myHostPort = new HostPort(PeerMaster.myHost, PeerMaster.udpPort);
			
			response = Protocol.HANDSHAKE_RESPONSE(myHostPort);
			// add peer to Peer list
			PeerMaster.addPeer(inPeer);
			
			/*
			if(hostPort.equals(inPeer)) {
				response = Protocol.HANDSHAKE_RESPONSE(myHostPort);
				// add peer to Peer list
				PeerMaster.addPeer(inPeer);
			} else {
				response = Protocol.INVALID_PROTOCOL("HostPort advertised is different from HostPort sending message");
			}
			*/
		}
		
		// add response to send queue
		UDPDataPack toSend = new UDPDataPack(hostPort, response);
		UDPPeer.sendQueue.offer(toSend);
	}
	
	// add peer connected if handshake response
	private void processHandshakeResponse() {
		Document inPeerDoc = (Document) request.get("hostPort");
		HostPort inPeer= new HostPort(inPeerDoc);
		if(hostPort.equals(inPeer)) {
			PeerMaster.addPeer(inPeer);
		}
	}
	
	private void processConnectionRefused() {
		@SuppressWarnings("unchecked")
		ArrayList<Document> peerList = (ArrayList<Document>) request.get("peers");
		if(!peerList.isEmpty()) {
			Document toConnect = peerList.get(0);
			
			HostPort peer = new HostPort(toConnect);
			HostPort myHostPort = new HostPort(PeerMaster.myHost, PeerMaster.udpPort);
			RespondOnReq getDoc = new RespondOnReq(myHostPort);
			Document doc = getDoc.getDoc();
			UDPDataPack dp = new UDPDataPack(peer, doc);
			try {
				UDPPeer.sendQueue.put(dp);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			
		}
		
	}
	
	// process file create request
	private void processFileCreate() {
		log.info("Start Processing File Create Request: " + this.request.getString("pathName"));
		RespondOnReq requestOperator = new RespondOnReq(this.fileSystemManager);
		Document result = requestOperator.fileCreateResponse(this.request);

		// send result to remote peer
		UDPDataPack toSend = new UDPDataPack(hostPort, result);
		UDPPeer.sendQueue.offer(toSend);
		
		// send file byte request if file create successfully
		if(result.getBoolean("status") && !requestOperator.hasShortcut) {
			requestFileByte(result);
		}
	}
	
	// process file delete request
	private void processFileDelete() {
		log.info("Start Processing File Delete: " + this.request.getString("pathName"));
		RespondOnReq requestOperator = new RespondOnReq(this.fileSystemManager);
		Document result = requestOperator.fileDeleteResponse(this.request);
		if(result.getBoolean("status")) {
			log.info("Success delete file: " + this.request.getString("pathName"));
		}
		
		// send result to remote peer
		UDPDataPack toSend = new UDPDataPack(hostPort, result);
		UDPPeer.sendQueue.offer(toSend);		
	}
	
	// process file modify request
	private void processFileModify() {
		log.info("Start Processing File Modify: " + this.request.getString("pathName"));
		RespondOnReq requestOperator = new RespondOnReq(this.fileSystemManager);
		Document result = requestOperator.fileModifyResponse(this.request);
		
		// send result to remote peer
		UDPDataPack toSend = new UDPDataPack(hostPort, result);
		UDPPeer.sendQueue.offer(toSend);		

		// if success, request data bytes
		if(result.getBoolean("status") && !requestOperator.hasShortcut) {
			requestFileByte(result);
		}
	}
	
	// 
	private void processDirectoryCreateRequest() {
		log.info("Start Processing Directory Create: " + this.request.getString("pathName"));
		RespondOnReq requestOperator = new RespondOnReq(this.fileSystemManager);
		Document result = requestOperator.directoryCreateResponse(this.request);
		if(result.getBoolean("status")) {
			log.info("Success create directory: " + this.request.getString("pathName"));
		}
		
		UDPDataPack toSend = new UDPDataPack(hostPort, result);
		UDPPeer.sendQueue.offer(toSend);
	}
	
	private void processDirectoryDeleteRequest() {
		log.info("Start Processing Directory Delete: " + this.request.getString("pathName"));
		RespondOnReq requestOperator = new RespondOnReq(this.fileSystemManager);
		Document result = requestOperator.directoryDeleteResponse(this.request);
		if(result.getBoolean("status")) {
			log.info("Success delete directory: " + this.request.getString("pathName"));
		}
		
		UDPDataPack toSend = new UDPDataPack(hostPort, result);
		UDPPeer.sendQueue.offer(toSend);
	}
	
	// File byte response
	private void processFileByteResponse() {
		log.info("Start Processing File Byte Response: " + this.request.getString("pathName") +" from position "
				+ this.request.getLong("position") + " of length " + this.request.getLong("length"));
		RespondOnReq requestOperator = new RespondOnReq(this.fileSystemManager);
		Document result = requestOperator.fileByteRequest(this.request);
		long length = result.getLong("length");
		if(length !=0) {
			// send result to remote peer
			log.info("Requesting file byte from peer " + result.getString("pathName") + 
					"postion " + result.getLong("position") + "& length " + result.getLong("length"));
			// send result to send queue
			UDPDataPack toSend = new UDPDataPack(hostPort, result);
			UDPPeer.sendQueue.offer(toSend);
		}else {
			log.info("No more file byte request to send");
		}
	}
	
	// file byte request
	private void processFileByteRequest() {
		log.info("Start Processing File Byte Request: " + this.request.getString("pathName") + " from position "
				+ this.request.getLong("position") + " of length " + this.request.getLong("length"));
		boolean readStatus = false;
		while(!readStatus) {
			// send the request to fileOperator to read file
			RespondOnReq requestOperator = new RespondOnReq(this.fileSystemManager);
			Document result = requestOperator.fileByteResponse(this.request);
			// if the file read success
			readStatus = result.getBoolean("status");
			log.info("read file : " + readStatus + " for "+ this.request.getString("pathName") + " from position "
					+ this.request.getLong("position") + " of length " + this.request.getLong("length") + 
					"with information " + result.getString("message"));
			if(readStatus) {
				log.info("success for file read: "+ this.request.getString("pathName") + " from position "
						+ this.request.getLong("position") + " of length " + this.request.getLong("length") + 
						"with information " + result.getString("message"));
				// success reading file
				// send the FILE BYTE to remote peer
				UDPDataPack toSend = new UDPDataPack(hostPort, result);
				UDPPeer.sendQueue.offer(toSend);
			}
		}
	}
	
	// request file bytes
	private void requestFileByte(Document result) {
		// proceed to send byte request
		Document fileDescriptor = (Document) request.get("fileDescriptor");
		long fileSize = fileDescriptor.getLong("fileSize");
		long position = 0;
		long length = 0;
		if(fileSize >= PeerMaster.blockSize) {
			length = PeerMaster.blockSize;
		}else {
			length = fileSize;
		}
		
		// send FILE_BYTES_REQUEST to remote peer
		Document docToSend = Protocol.FILE_BYTES_REQUEST(this.request, position, length);

		log.info("Initial sending file byte request: " + result.getString("pathName") + 
				" postion " + position + "& length " + length);
		
		UDPDataPack toSend = new UDPDataPack(hostPort, docToSend);
		UDPPeer.sendQueue.offer(toSend);		
	}
	
	private void processInvalid() {
		log.info("Invalid Protocol received");
	}
	
}