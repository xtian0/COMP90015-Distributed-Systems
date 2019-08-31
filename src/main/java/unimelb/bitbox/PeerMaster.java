package unimelb.bitbox;

import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.HostPort;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;
import java.util.HashMap;

/** 
 * Master for all peer properties
 * as well as tracking events generated in peers
 */
public class PeerMaster {
	// configuration properties
	public static String path;
	public static int myPort;
	public static String myHost;
	public static String[] peerArray;
	public static int maxIncomingPeer;
	public static long blockSize;
	public static long syncInterval;
	public static String mode;
	public static int udpPort;
	public static int udpTimeout;
	public static int udpRetries;
	public static int clientPort;
	public static String[] keysList;
	
	// number of incoming connections
	public static int numPeersConnection = 0;	
	// list of peers connection
	public static ArrayList<HostPort> peerList = new ArrayList<HostPort>();
	
	// event Queue;
	public static HashMap<HostPort, Queue<FileSystemEvent>> peerEventQ = new HashMap<HostPort, Queue<FileSystemEvent>>();
	//public static Queue<FileSystemEvent> eventQueue = new LinkedList<FileSystemEvent>();
	//public static Queue<FileSystemEvent> eventQueue2 = new LinkedList<FileSystemEvent>();
	
	// disconnect list from the client commands
	public static ArrayList<HostPort> disconList = new ArrayList<HostPort>();
	
	// connect list for tcp
	public static Queue<HostPort> peerToConnect = new LinkedList<HostPort>();
	
	/***************/
	/** functions **/
	/***************/
	
	// read from configuration.properties
	public static void readConfig() {
		// Peer configurations
        path = Configuration.getConfigurationValue("path");
        myPort = Integer.parseInt(Configuration.getConfigurationValue("port"));
        myHost = Configuration.getConfigurationValue("advertisedName");
        peerArray = Configuration.getConfigurationValue("peers").split(",");
        maxIncomingPeer = Integer.parseInt(Configuration.getConfigurationValue("maximumIncommingConnections"));
        blockSize = Long.parseLong(Configuration.getConfigurationValue("blockSize"));
        syncInterval = Long.parseLong(Configuration.getConfigurationValue("syncInterval"));
        
        // UDP configurations
        mode = Configuration.getConfigurationValue("mode");
        udpPort = Integer.parseInt(Configuration.getConfigurationValue("udpPort"));
        udpTimeout = Integer.parseInt(Configuration.getConfigurationValue("udpTimeout"));
        udpRetries = Integer.parseInt(Configuration.getConfigurationValue("udpRetries"));
        
        // Client configurations
        clientPort = Integer.parseInt(Configuration.getConfigurationValue("clientPort"));
        keysList = Configuration.getConfigurationValue("authorized_keys").split(",");
	}
	
	// print all current configurations
	public static void printConfig() {
		System.out.println("path		:" + path);
		System.out.println("myPort		:" + myPort);
		System.out.println("myHost		:" + myHost);
		System.out.println("peerArray	:" + Arrays.toString(peerArray));
		System.out.println("maxIncomingPeer	:" + maxIncomingPeer);
		System.out.println("blockSize	:" + blockSize);
		System.out.println("syncInterval	:" + syncInterval);
		System.out.println("mode		:" + mode);
		System.out.println("udpPort		:" + udpPort);
		System.out.println("udpTimeout	:" + udpTimeout);
		System.out.println("udpRetries	:" + udpRetries);
		System.out.println("clientPort	:" + clientPort);
		System.out.println("keysList	:" + Arrays.toString(keysList));
	}
	
	// add a peer not already connected to peer list
	public static boolean addPeer(HostPort peerNew) {
		if(containPeer(peerNew)) {
			return false;
		} else {
			Queue<FileSystemEvent> newQ = new LinkedList<FileSystemEvent>();
			peerList.add(peerNew);
			peerEventQ.put(peerNew, newQ);
			numPeersConnection += 1;
			return true;
		}
	}
	
	// remove a peer already connected from peer list
	public static boolean removePeer(HostPort peerNew) {
		if(containPeer(peerNew)) {
			peerList.remove(peerNew);
			peerEventQ.remove(peerNew);
			numPeersConnection -= 1;
			return true;
		} else {
			
			return false;
		}
	}
	
	// check if a new peer is already connected.
	public static boolean containPeer(HostPort peerNew) {
		for(HostPort peerConnected : peerList) {
			if(peerConnected.equals(peerNew)) {
				return true;
			}
		}
		return false;
	}
	
	// check if peer is full
	public static boolean isPeerFull() {
		if(numPeersConnection >= maxIncomingPeer) {
			return true;
		} else {
			return false;
		}
	}
	
	// convert current list to an ArrayList of document
	public static ArrayList<Document> peerListToDoc(){
		ArrayList<Document> doc = new ArrayList<Document>();
		for(HostPort peer: peerList) {
			doc.add(peer.toDoc());
		}
		return doc;
	}
	
	// add event to all connected peer
	public static void eventToPeer(FileSystemEvent event) {
		for(HostPort peer:peerList) {
			peerEventQ.get(peer).offer(event);
		}
	}
	public static void eventToPeer(ArrayList<FileSystemEvent> event) {
		for(HostPort peer:peerList) {
			peerEventQ.get(peer).addAll(event);
		}
	}
	
	public static boolean inDisconList(HostPort peer) {
		if(disconList.contains(peer)) {
			disconList.remove(peer);
			return true;
		}
		return false;
	}
	

}
