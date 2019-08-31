package unimelb.bitbox;

import java.io.*;
import java.net.Socket;
import java.net.ServerSocket;
import java.util.logging.Logger;
import unimelb.bitbox.util.FileSystemManager;

public class TCPServer extends Thread {
	private static Logger log = Logger.getLogger(TCPServer.class.getName());
	
	private FileSystemManager fileSystemManager;
	
	public TCPServer(FileSystemManager fileSystemManager) {
		this.fileSystemManager = fileSystemManager;
	}
	
	@Override
	public void run() {
		
		try {
			@SuppressWarnings("resource")
			ServerSocket socket = new ServerSocket(PeerMaster.myPort);
			while (true) {
				Socket clientSocket = socket.accept();
				log.info("Client " + clientSocket.getInetAddress().getHostAddress() + ":" +
						clientSocket.getPort() +" trying to connect");
				
				// Start a new thread for a connection
				ConnectFromPeer sp = new ConnectFromPeer(fileSystemManager, clientSocket);
				sp.start();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

}