package unimelb.bitbox;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.logging.Logger;

import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;
import unimelb.bitbox.util.HostPort;;

public class RespondOnReq {
	private static Logger log = Logger.getLogger(RespondOnReq.class.getName());
	private FileSystemManager fileSystemManager;
	public boolean hasShortcut = false;
	private FileSystemEvent event;
	private HostPort hostPort;
	
	
	public RespondOnReq(FileSystemManager fileSystemManager) {
		this.fileSystemManager=fileSystemManager;
	}
	
	public RespondOnReq(FileSystemEvent event) {
		this.event = event;
	}
	
	public RespondOnReq(HostPort hostPort) {
		this.hostPort = hostPort;
	}
	
	
	// generate documents for events
	public Document getDoc() {
		Document doc = new Document();
		// HandShake
		if(hostPort!=null) {
			doc = Protocol.HANDSHAKE_REQUEST(hostPort);
		}else {
			// Events
			switch(event.event.toString()) {
			case "FILE_CREATE":
				doc = Protocol.FILE_CREATE_REQUEST(event.fileDescriptor, event.pathName);
				break;
			case "FILE_DELETE":
				doc = Protocol.FILE_DELETE_REQUEST(event.fileDescriptor, event.pathName);
				break;
			case "FILE_MODIFY":
				doc = Protocol.FILE_MODIFY_REQUEST(event.fileDescriptor, event.pathName);
				break;
			case "DIRECTORY_CREATE":
				doc = Protocol.DIRECTORY_CREATE_REQUEST(event.pathName);
				break;
			case "DIRECTORY_DELETE":
				doc = Protocol.DIRECTORY_DELETE_REQUEST(event.pathName);
				break;
			}
		}
		
		return doc;
	}
	
	public Document eventFileCreate(FileSystemEvent event) {
		Document doc = Protocol.FILE_CREATE_REQUEST(event.fileDescriptor, event.pathName);
		return doc;
	}
	public Document eventFileDelete(FileSystemEvent event) {
		Document doc = Protocol.FILE_DELETE_REQUEST(event.fileDescriptor, event.pathName);
		return doc;
	}
	public Document eventFileModify(FileSystemEvent event) {
		Document doc = Protocol.FILE_MODIFY_REQUEST(event.fileDescriptor, event.pathName);
		return doc;
	}
	public Document eventDirectoryCreate(FileSystemEvent event) {
		Document doc = Protocol.DIRECTORY_CREATE_REQUEST(event.pathName);
		return doc;
	}
	public Document eventDirectoryDelete(FileSystemEvent event) {
		Document doc = Protocol.DIRECTORY_DELETE_REQUEST(event.pathName);
		return doc;
	}

	
	// server part
	public Document fileCreateResponse(Document request) {
		String command = request.getString("command");
		Document descriptor = (Document)request.get("fileDescriptor");
		String pathName = request.getString("pathName");
		String md5 = descriptor.getString("md5");
		long fileSize = descriptor.getLong("fileSize");
		long lastModified = descriptor.getLong("lastModified");
		try {
			Thread.sleep(3000);
		} catch (InterruptedException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		//valid command
		if (command.equals("FILE_CREATE_REQUEST")) {
			// confirm request is a file create request
			if (this.fileSystemManager.fileNameExists(pathName)) {
				//path name already exists, return file created false response
				log.info("Create file " + pathName + " already exists");
				return Protocol.FILE_CREATE_RESPONSE(request, "pathname already exists", false);					 
			}
			else {
				//safe pathname
				if(fileSystemManager.isSafePathName(pathName)) {
					try {
						//
						if (fileSystemManager.createFileLoader(pathName, md5, fileSize, lastModified)) {
							//file created successfully
							log.info("File loader created for " + pathName);
							if (fileSystemManager.checkShortcut(pathName)) {
								log.info("Shortcut is found for " + pathName);
								hasShortcut=true;
							}							
							return Protocol.FILE_CREATE_RESPONSE(request, "file loader ready", true);
						}
						else {
							//have problem creating the file
							log.info("Create file " + pathName + " is unsafe");
							return Protocol.FILE_CREATE_RESPONSE(request, "there was a problem creating the file", false);
						}
					} catch (NoSuchAlgorithmException e) {
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					}
					return Protocol.FILE_CREATE_RESPONSE(request, "there was a problem creating the file", false);
				}
				else {
					//unsafe pathname
					return Protocol.FILE_CREATE_RESPONSE(request, "unsafe pathname given", false);
				}
			}		 
		}
		else {
			return Protocol.INVALID_PROTOCOL("bad message");
		}
	}

	
	//continue request
	public Document fileByteRequest(Document request) {
		String command = request.getString("command");
		String pathName = request.getString("pathName");
		String encodedContent = request.getString("content");
		long position = request.getLong("position");
		long length = request.getLong("length");
		Document descriptor = (Document) request.get("fileDescriptor");
		long fileSize = descriptor.getLong("fileSize");
		long blockSize = PeerMaster.blockSize;
		if (command.equals("FILE_BYTES_RESPONSE")) {
			log.info("Writing file " + pathName + " from postion " + position + " of length " + length);
			try {
				byte[] bytes = Base64.getDecoder().decode(encodedContent);
				ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
				if(fileSystemManager.writeFile(pathName, byteBuffer, position)) {
					// file write success
					log.info("File byte for " + pathName + " is written");
				}else {
					// file write fail
					log.info("File byte for " + pathName + " write fail");
				}
				
			} catch (IOException e) {
				e.printStackTrace();
			}
			if(position + length == fileSize) {
				try {
					if(fileSystemManager.checkWriteComplete(pathName)) {
						//file load complete
						log.info("File byte for " + pathName + " checked whole file complete");
					}else {
						//file load not complete
						log.info("File byte for " + pathName + " file is not completed after full size");
					}
				} catch (NoSuchAlgorithmException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				return Protocol.FILE_BYTES_REQUEST(request, fileSize, 0);
			}else if(position + length > fileSize) {
				log.info("Overflow detected for file " + pathName + " with position " + position + " of length " + length);
				return Protocol.FILE_BYTES_REQUEST(request, fileSize, -1);
			}else if (position + length + blockSize < fileSize) {
				log.info("Request file byte " + pathName + "position " + position + "length " + length);
				return Protocol.FILE_BYTES_REQUEST(request, position+length, blockSize);
			}else {
				return Protocol.FILE_BYTES_REQUEST(request, position+length, fileSize-(position+length));
			}

		}else {
			return Protocol.INVALID_PROTOCOL("bad message");
		}
	}
	
	
	public Document fileByteResponse(Document request) {
		String command = request.getString("command");
		Document descriptor = (Document) request.get("fileDescriptor");
		String pathName = request.getString("pathName");
		String md5 = descriptor.getString("md5");
		long position = request.getLong("position");
		long length = request.getLong("length");
		String encodedContent="";
		if (command.equals("FILE_BYTES_REQUEST")) {
			log.info("Reading file " + pathName + " from postion " + position + " of length " + length);
			try {
				ByteBuffer byteBuffer = fileSystemManager.readFile(md5, position, length);
				if(byteBuffer == null) {
					log.info("File byte for " + pathName + " unsuccessful read");
					return Protocol.FILE_BYTES_RESPONSE(request, encodedContent, "unsucessfull read", false);
				}
				encodedContent = Base64.getEncoder().encodeToString(byteBuffer.array());
				return Protocol.FILE_BYTES_RESPONSE(request, encodedContent, "successful read", true);
			} catch (NoSuchAlgorithmException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			return Protocol.FILE_BYTES_RESPONSE(request, encodedContent, "unsucessfull read", false);
		}
		else {
			return Protocol.INVALID_PROTOCOL("bad message");
		}
	}
		

	
	
	public Document fileDeleteResponse(Document request) {
		String command = request.getString("command");
		String pathName = request.getString("pathName");
		Document descriptor = (Document)request.get("fileDescriptor");
		String md5 = descriptor.getString("md5");
		long lastModified = descriptor.getLong("lastModified");
		if (command.equals("FILE_DELETE_REQUEST")) {
			if (fileSystemManager.fileNameExists(pathName)) {
				if (fileSystemManager.isSafePathName(pathName)) {
					if (fileSystemManager.deleteFile(pathName, lastModified, md5)) {
						//file deleted successfully
						log.info("Delete file " + pathName + " deleted");
						return Protocol.FILE_DELETE_RESPONSE(request, "file deleted", true);
					}else {
						//having problem deleting the file
						log.info("Having problem deleting " + pathName);
						return Protocol.FILE_DELETE_RESPONSE(request, "there was a problem deleting the file", false);
					}
				}else {
					//unsafe pathname
					log.info("Delete file " + pathName + " path is not safe");
					return Protocol.FILE_DELETE_RESPONSE(request, "unsafe pathname given",false);
				}
			}else {
				//pathname does not exist
				log.info("Delete file " + pathName + " does not exist");
				return Protocol.FILE_DELETE_RESPONSE(request, "pathname does not exist",false);				
			}
		}else {
			return Protocol.INVALID_PROTOCOL("bad message");
		}
	}
	

	
	public Document fileModifyResponse(Document request) {
		String command = request.getString("command");
		String pathName = request.getString("pathName");
		Document descriptor = (Document)request.get("fileDescriptor");
		String md5 = descriptor.getString("md5");
		long lastModified = descriptor.getLong("lastModified");
		if (command.equals("FILE_MODIFY_REQUEST")) {
			if (fileSystemManager.fileNameExists(pathName, md5)) {
				//pathname already exists
				log.info("Modified file " + pathName + " has a matching content");
				return Protocol.FILE_MODIFY_RESPONSE(request, "file already exists with matching content", false);
			}
			else {
				if (fileSystemManager.isSafePathName(pathName)) {
					//safe pathName
					if (fileSystemManager.fileNameExists(pathName)) {
						//the modified file exists
						log.info("Modified file " + pathName + " exists with a different content");
						try {
							if(fileSystemManager.modifyFileLoader(pathName, md5, lastModified)) {
							//file modified successfully
								try {
									if (fileSystemManager.checkShortcut(pathName)) {
										log.info("Modified file " + pathName + " has a short cut");
										hasShortcut=true;
									}
								} catch (NoSuchAlgorithmException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
								
							return Protocol.FILE_MODIFY_RESPONSE(request, "file loader ready", true);
							}else {
								//having problem modifying the file
								log.info("Having problem modifying file " + pathName);
								return Protocol.FILE_MODIFY_RESPONSE(request, "there was a problem modifying the file", false);
							}
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						return Protocol.FILE_MODIFY_RESPONSE(request, "there was a problem modifying the file", false);
					}else {
						//pathname given does not exist
						log.info("Modified file " + pathName + " do not exist");
						return Protocol.FILE_MODIFY_RESPONSE(request, "pathname does not exist", false);
					}
				}else {
					//unsafe pathname
					log.info("Modified file " + pathName + " path name is not safe");
					return Protocol.FILE_MODIFY_RESPONSE(request, "unsafe pathname given", false);
				}
			}
		}
		else {
			return Protocol.INVALID_PROTOCOL("bad message");
		}
	}
	
	

	
	
	public Document directoryCreateResponse(Document request) {
		String command = request.getString("command");
		String pathName = request.getString("pathName");
		if (command.equals("DIRECTORY_CREATE_REQUEST")) {
			if (fileSystemManager.dirNameExists(pathName)) {
				//path already exists
				log.info("Directory " + pathName + " already exists");
				return Protocol.DIRECTORY_CREATE_RESPONSE(request, "pathname already exists", false);				
			}else {
				// path do not exists, proceed
				if(fileSystemManager.isSafePathName(pathName)) {
					if (fileSystemManager.makeDirectory(pathName)) {
						//directory created successfully
						log.info("Directory " + pathName + " created");
						return Protocol.DIRECTORY_CREATE_RESPONSE(request, "directory created", true);
					}
					else {
						//have problem creating the directory
						return Protocol.DIRECTORY_CREATE_RESPONSE(request, "there was a problem creating the directory", false);
					}
				}
				else {
					//unsafe pathname
					log.info("Directory " + pathName + " path is not safe");
					return Protocol.DIRECTORY_CREATE_RESPONSE(request, "unsafe pathname given", false);
				}
			}		 
		}
		else {
			return Protocol.INVALID_PROTOCOL("message");
		}
	}
	
	

	
	
	public Document directoryDeleteResponse(Document request) {
		String command = request.getString("command");
		String pathName = request.getString("pathName");
		if (command.equals("DIRECTORY_DELETE_REQUEST")) {
			if (fileSystemManager.dirNameExists(pathName)) {
				if (fileSystemManager.isSafePathName(pathName)) {
					if (fileSystemManager.deleteDirectory(pathName)) {
						//directory deleted successfully
						log.info("Directory " + pathName + " deleted");
						return Protocol.DIRECTORY_DELETE_RESPONSE(request, "directory deleted", true);
					}else {
						//having problem deleting the directory
						log.info("Having problem deleting directory " + pathName);
						return Protocol.DIRECTORY_DELETE_RESPONSE(request, "there was a problem deleting the directory", false);
					}
				}else {
					//unsafe pathname
					log.info("Directory " + pathName + " path is not safe");
					return Protocol.DIRECTORY_CREATE_RESPONSE(request, "unsafe pathname given",false);
				}				
			}else {
				//pathname does not exist
				log.info("Directory " + pathName + " does not exist");
				return Protocol.DIRECTORY_CREATE_RESPONSE(request, "pathname does not exist",false);
			}
		}else {
			return Protocol.INVALID_PROTOCOL("bad message");
		}
	}
	
	
	
}
