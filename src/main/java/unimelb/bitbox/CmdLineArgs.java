package unimelb.bitbox;

import org.kohsuke.args4j.Option;

public class CmdLineArgs {
	@Option(required = true, name = "-s", aliases = {"--server"}, usage = "ServerHostPort")
	private String hostPort;
	
	@Option(required = true, name = "-c", aliases = {"--command"}, usage = "Command")
	private String command;
	
	@Option(required = false, name = "-p", aliases = {"--peer"}, usage = "PeerHostPort")
	private String peer = "";
	
	@Option(required = true, name = "-i", usage = "Identity")
	private String identity;
	
	
	public String getServer() {
		return hostPort;
	}
	
	public String getCmd() {
		return command;
	}
	
	public String getPeer() {
		return peer;
	}
	
	public String getIdentity() {
		return identity;
	}

}
