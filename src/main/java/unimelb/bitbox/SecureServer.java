package unimelb.bitbox;

import java.util.logging.Logger;
import java.io.*;
import java.net.*;
import java.security.interfaces.RSAPublicKey;

import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.HostPort;

public class SecureServer extends Thread{
	private static Logger log = Logger.getLogger(SecureServer.class.getName());
	private int port;
	
	public SecureServer(int port) {
		this.port = port;
	}
	
	@Override
	public void run() {
		try {
			ServerSocket server = new ServerSocket(port);
			while(true) {
				Socket client = server.accept();
				client.setSoTimeout(3000);
				BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(), "UTF8"));
				PrintWriter writer = new PrintWriter(new OutputStreamWriter(client.getOutputStream(), "UTF8"),true);

				//handle request from client
				byte[] tempKey=new byte[128];
				RSAPublicKey rsakey=null;
				
				// receiving auth request
				Document authRequest = Document.parse(reader.readLine());
				if(authRequest.equals(null)) {
					log.info("Not receiving auth request, close connection.");
					client.close();
				}else {
					// auth request received.
					log.info("receive auth request " + authRequest.toJson());
					String identity = authRequest.getString("identity");
					Document authResponse = new Document();
					String pubKey = searchKey(identity);

					if(!pubKey.equals("")) {
						// key found
						tempKey = Secure.skGenarate();
						rsakey = CertificateUtils.parseSSHPublicKey(pubKey);
						authResponse = Protocol.AUTH_RESPONSE(Secure.RSAEncrypt(tempKey,rsakey), true, "public key found");
					}else {
						// key not found
						authResponse = Protocol.AUTH_RESPONSE(false, "publick key not found");
					}
					writer.println(authResponse.toJson());
				}
				
				// receive encrypted messages
				Document request = Document.parse(Secure.AESDecrypt(tempKey, reader.readLine()));
				log.info("receive from client: " + request.toJson());
				String command = request.getString("command");
				Document response = new Document();
				
				switch(command) {
				case "LIST_PEERS_REQUEST":
					response = Protocol.LIST_PEERS_RESPONSE(PeerMaster.peerListToDoc());
					break;
				case "CONNECT_PEER_REQUEST":
					HostPort peerConnect = new HostPort(request.getString("host"), Math.toIntExact(request.getLong("port")));
					if(PeerMaster.containPeer(peerConnect)) {
						// peer already connected
						response = Protocol.CONNECT_PEER_RESPONSE(peerConnect, true, "connection already exists");
					} else {
						// try connect to peer
						if(PeerMaster.mode.equals("tcp")) {
							// connect for TCP
							PeerMaster.peerToConnect.offer(peerConnect);
						} else {
							// connect for UDP
							HostPort myHostPort = new HostPort(PeerMaster.myHost, PeerMaster.udpPort);
							RespondOnReq getDoc = new RespondOnReq(myHostPort);
							Document doc = getDoc.getDoc();
							UDPDataPack dp = new UDPDataPack(peerConnect, doc);
							try {
								UDPPeer.sendQueue.put(dp);
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
						}
						
						Thread.sleep(10000);
						if(PeerMaster.containPeer(peerConnect)) {
							response = Protocol.CONNECT_PEER_RESPONSE(peerConnect, true, "connected to peer");
						} else {
							response = Protocol.CONNECT_PEER_RESPONSE(peerConnect, false, "unable to connect");
						}
						
					}
					break;
				case "DISCONNECT_PEER_REQUEST":
					HostPort peerDisconnect = new HostPort(request.getString("host"), Math.toIntExact(request.getLong("port")));
					if(PeerMaster.containPeer(peerDisconnect)) {
						if(PeerMaster.mode.equals("tcp")) {
							PeerMaster.disconList.add(peerDisconnect);
						} else {
							PeerMaster.removePeer(peerDisconnect);
						}
						response = Protocol.DISCONNECT_PEER_RESPONSE(peerDisconnect, true, "disconnected from peer");
					} else {
						// the request peer is not connected
						response = Protocol.DISCONNECT_PEER_RESPONSE(peerDisconnect, false, "connection not active");
					}
					break;
				}
				
				Thread.sleep(1000);
				// send encrypted pay load to client
				writer.println(Secure.AESEncrypt(tempKey, response.toJson()));
				log.info("send to client: " + response.toJson());
				client.close();
				
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private String searchKey(String identity) {
		for(int i = 0; i < PeerMaster.keysList.length; i++) {
			String[] keys = PeerMaster.keysList[i].split(" ");
			if(keys[2].equals(identity)) {
				log.info("Key is found for " + identity);
				return PeerMaster.keysList[i];
			}
		}
		return "";
	}
	

}
