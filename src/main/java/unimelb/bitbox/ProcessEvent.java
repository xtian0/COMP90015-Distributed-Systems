package unimelb.bitbox;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.util.logging.Logger;
import java.net.Socket;

import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;

public class ProcessEvent extends Thread{
	private static Logger log = Logger.getLogger(ProcessEvent.class.getName());
	private FileSystemManager fileSystemManager; 
	private FileSystemEvent eventToHandle;
	private Socket socket;
	private BufferedReader reader;
	private PrintWriter writer;
    
    
    public ProcessEvent(FileSystemManager fileSystemManager,FileSystemEvent eventToHandle,Socket socket,
    		BufferedReader reader, PrintWriter writer) {
    	// initialize
    	this.fileSystemManager = fileSystemManager;
    	this.eventToHandle = eventToHandle;
    	this.socket = socket;
    	this.reader = reader;
    	this.writer = writer;
    	//this.isComplete = false;

    }
    
    @Override
    public void run() {
    	log.info("Event Process for " + eventToHandle.toString());
    	Document request = new Document();
    	// handling event
    	switch(eventToHandle.event) {
    	case FILE_CREATE:
    		//processFileCreate();
    		request = Protocol.FILE_CREATE_REQUEST(this.eventToHandle.fileDescriptor, this.eventToHandle.pathName);
    		break;
		case FILE_DELETE:
			//processFileDelete();
			request = Protocol.FILE_DELETE_REQUEST(this.eventToHandle.fileDescriptor, this.eventToHandle.pathName);
			break;
		case FILE_MODIFY:
			//processFileModify();
			request = Protocol.FILE_MODIFY_REQUEST(this.eventToHandle.fileDescriptor, this.eventToHandle.pathName);
			break;
		case DIRECTORY_CREATE:
			//processDirectoryCreate();
			request = Protocol.DIRECTORY_CREATE_REQUEST(this.eventToHandle.pathName);
			break;
		case  DIRECTORY_DELETE:
			//processDirectoryDelete();
			request = Protocol.DIRECTORY_DELETE_REQUEST(this.eventToHandle.pathName);
			break;
    	}
    	
    	writer.println(request.toJson());
    }
   
}
