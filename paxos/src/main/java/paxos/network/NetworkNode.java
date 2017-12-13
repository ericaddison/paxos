package paxos.network;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import paxos.NodeFileParser;
import paxos.messages.Message;
import paxos.messages.MessageType;
import paxos.paxos.SimulatedCrashException;

/**
 * Base class to build on. This class will only worry about creating and managing TCP
 * connections to other nodes. Algorithmic pieces will act as decorators on this base class. 
 * @author eyms
 *
 */
public class NetworkNode {
	
	private static final int TIMEOUT = 500;
	private static final long WAIT_TIME = 500;
	private int id;
	private List<NodeInfo> nodes;
	private List<Integer> ports; 
	private Thread connectThread;
	private ServerSocket serverSocket;
	private boolean restart;
	private boolean running;
	private Deque<Message> selfMessages;
	private List<String[]> nodeListFileTokens;
	
	// reliability stuff
	private int avgMsgDelay = 0;	// multiplier to DELAY_TIME by which to delay messages
	private float unreliability = 0; // probability of a "crash" (don't respond to a message) 
	private static final int DELAY_TIME = 100;
	
	// Logging
	private Logger log;
	
	public NetworkNode(int id, String nodeListFileName, boolean restart, Logger log) {
		this.id = id;
		this.restart = restart;
		this.nodeListFileTokens = new ArrayList<>();
		parseNodeFile(nodeListFileName);
		this.log = log;
		this.selfMessages = new ArrayDeque<>();
		
		log.info("Created new " + this.getClass().getSimpleName() + " with:");
		log.info("\tid = " + id);
		log.info("\tnodeListFileName = " + nodeListFileName);
		log.info("\tresart = " + restart);
		log.info("\tavgMsgDelay = " + avgMsgDelay);
		log.info("\tunreliability = " + unreliability);
		log.info("Node List:");
		for(String node : getNodesInfo()){
			log.info("\t" + node);
		}
		
	}
	
	public void run(){
		networkInit();
		running = true;
	}
	
	public boolean isRunning() {
		return running;
	}
	
	public int getId() {
		return id;
	}
	
	public String[] getNodeFileTokens(int nodeId){
		return nodeListFileTokens.get(nodeId);
	}
	
	private void parseNodeFile(String filename) {
		
		nodeListFileTokens = NodeFileParser.parseNodeFile(filename);
		if(id<0 || id>=nodeListFileTokens.size())
			throw new ArrayIndexOutOfBoundsException();
		
		avgMsgDelay = Integer.parseInt(nodeListFileTokens.get(id)[NodeFileParser.MSG_DELAY_COL]);
		unreliability = Float.parseFloat(nodeListFileTokens.get(id)[NodeFileParser.UNRELIABILITY_COL]);
		
		nodes = new ArrayList<>();
		ports = new ArrayList<>();
		
		for(String[] toks : nodeListFileTokens){
			NodeInfo newNode = new NodeInfo();
			try {
				newNode.address = InetAddress.getByName(toks[NodeFileParser.IP_COL]);
			} catch (UnknownHostException e) {
				e.printStackTrace();
			}
			newNode.port = Integer.parseInt(toks[NodeFileParser.PORT_COL]);
			ports.add(newNode.port);
			nodes.add(newNode);
		}
		
		nodes.get(id).connected = true;
		
	}

	public int getPort(){
		return nodes.get(id).port;
	}
	
	public Logger getLogger(){
		return log;
	}
	
	public int getTotalNodeCount(){
		return nodes.size();
	}
	
	
	public int getConnectedNodeCount(){
		return nodes
				.stream()
				.mapToInt(NodeInfo::isConnected)
				.reduce(0, (a, b) -> a + b);
	}
	
	public List<String> getNodesInfo(){
		return nodes.stream().map(NodeInfo::toString).collect(Collectors.toList());
	}
	
	
	/**
	 * Send a message to another node by id
	 */
	public boolean sendMessage(int theirId, Message msg){
		/*
		if(!unreliabilitySimulator())
			return true;
		*/
		
		if(!isConnected(theirId)){
			log.warning("Error sending message to node " + theirId + ": node not connected");
			return false;
		}
		log.finest("Sending message \"" + msg + "\" to node " + theirId );
		
		if(theirId == id){
			selfMessages.add(msg);
			return true;
		}
		
		NodeInfo node = nodes.get(theirId);
		node.writer.println(msg.toString());
		node.writer.flush();
		return true;
	}
	
	
	/**
	 * Receive a message from another node   
	 */
	public Message receiveMessage(int theirId) throws SimulatedCrashException{
		
		if(!unreliabilitySimulator())
			throw new SimulatedCrashException();
		
		
		// receive from self
		if(theirId == id){
			Message myMsg = null;
			while(myMsg == null){
				myMsg = selfMessages.poll();
				try {
					Thread.sleep(5);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			log.finest("Received message \"" + myMsg + "\" from node " + theirId );
			return myMsg;
		}
		
		String msg = null;
		NodeInfo node = nodes.get(theirId);
		try {
			msg = node.reader.readLine();
			log.finest("Received message \"" + msg + "\" from node " + theirId );
			return Message.fromString(msg);
		} catch (SocketException e){
				log.finest("Lost connection to node " + theirId);
		} catch (IOException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e){
			log.finest("Failed to decode message \"" + msg + "\" from node " + theirId);
		} 
		
		return null;

	}	
	
	
	private boolean unreliabilitySimulator(){
		try {
			Random rand = new Random(System.currentTimeMillis());
			float randVal = rand.nextFloat();

			/*
			// generate random "crashes"
			if(randVal < unreliability){
				log.fine("Node " + id + " CRASH with " + randVal + " < " + unreliability);
				Thread.sleep(CRASH_TIME);
				return false;
			}
			*/
			
			// insert random wait time based on msgDelay
			int waitTime = DELAY_TIME * avgMsgDelay;
			Thread.sleep((long)(randVal*waitTime));
			
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		return true;
	}
	
	
//****************************************************************
//	Private Methods -- mostly network related
//****************************************************************	
	


	/**
	 * Send a message to another node
	 */
	private void sendMessage(NodeInfo node, Message msg) {
		log.finest("Sending message \"" + msg + "\" to " + node );
		node.writer.println(msg.toString());
		node.writer.flush();
	}
	
	/**
	 * Receive a message from another node   
	 */
	private Message receiveMessage(NodeInfo node){
		String msg = null;
		try {
			msg = node.reader.readLine();
			log.finest("Received message \"" + msg + "\" from " + node );
			return Message.fromString(msg);
		} catch (IOException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e){
			log.finest("Failed to decode message \"" + msg + "\" from " + node);
		}
		
		return null;

	}
	
	
	/**
	 * Private class to track node info. Just a struct. 
	 */
	class NodeInfo{
		InetAddress address;
		int port;
		boolean connected;
		Socket sock;
		PrintWriter writer;
		BufferedReader reader;
		int connectionAttempts;
		
		public NodeInfo() {}
		
		public NodeInfo(Socket sock) {
			try{
				this.sock = sock;
				address = sock.getInetAddress();
				port = sock.getPort();
				writer = new PrintWriter(sock.getOutputStream());
				reader = new BufferedReader(new InputStreamReader(sock.getInputStream()));
				connected = true;
			} catch(IOException e){
				log.warning("IOException from node " + this);
				connected = false;
			}
		}
		
		public int isConnected() {
			return connected ? 1 : 0;
		}
		
		@Override
		public String toString(){
			return address.toString() + ":" + port;
		}
	}
	
	
	/**
	 * Initialize Node connections, including listening for incoming connections
	 * And attempting to connect to other servers
	 */
	private void networkInit(){
		try {
			serverSocket = new ServerSocket(getPort());
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		startConnectionThread();
		connectToOtherServers(restart);
	}
	
	
	/**
	 * Start the infinite TCP listening thread
	 */
	private void startConnectionThread(){
		log.info("Initiating connections");
		
		// start eternal socket acceptance loop
		connectThread = new Thread(new Runnable(){
			@Override
			public void run() {
				connectionLoop();
			}
		});
		connectThread.start();
	}
	
	
	/**
	 * Infinite TCP connection listener loop
	 */
	private void connectionLoop(){
		while(true){
			log.finer("Listenining for connection on port "+ (getPort()));
			try {
				// set up connection from unknown server
				Socket sock = serverSocket.accept();
				initIncomingConnection(sock);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	
	/**
	 * Initialize a new outgoing connection. 
	 */
	private void initIncomingConnection(Socket sock) throws IOException{
		NodeInfo node = new NodeInfo(sock);
		log.finer("Initializing incoming connection with node at " + node);
		
		// find out what id they are
		
		int theirId = 0;
		try{
			Message msg = receiveMessage(node);
			node.connected = true;
			
			if(msg.getType() != MessageType.INIT){
				log.warning("Received non INIT message from " + node);
				node.connected = false;
			}
			theirId = msg.getId();
			
			if(theirId < 0 || theirId >= nodes.size()){
				log.warning("Received out-of-bounds id from " + node);
				node.connected = false;
			}
			
			if(nodes.get(theirId).connected){
				if(node.address.equals(nodes.get(theirId).address)){
					log.info("Node " + theirId + " coming back online...");
				} else {
					log.warning("Received in-use id from " + node);
					node.connected = false;
				}
			}
			
		} catch(IllegalArgumentException e){
			log.warning("Could not decode message from " + node);
			node.connected = false;
		}
		
		nodes.set(theirId, node);
		
		if(!node.connected){
			Message nogood = new Message(MessageType.NACK, "reject", 0, id);
			sendMessage(node, nogood);
			sock.close();
			return;
		}
		
		// if ok, add to list of nodes and send ack
		Message ack_msg = new Message(MessageType.INIT, "ACK", 0, id); 
		sendMessage(node, ack_msg);
	}
	
	
	/**
	 * Connect to the other nodes. If "restart==false", this will attempt to
	 * connect only to servers with id < this.serverID. If "restart==true", this will
	 * attempt to connect to all other servers.  
	 */
	private void connectToOtherServers(boolean restart){
		// create other initial connections
		int iServer = -1;
		
		// if restarting, start by assuming that you must connect to all other servers
		// this will be updated after connecting to one live server
		// otherwise, on a fresh startup, connect to all servers with serverID less than yours 
		int nConnections = restart ? (nodes.size()) : id+1;
		
		while(getConnectedNodeCount() < nConnections) {
			iServer = (iServer+1) % (restart?nConnections:id);
			NodeInfo node = nodes.get(iServer);
			if (node.connected)
				continue; // this socket and thread will be null

			try {
				log.fine("Entering connect loop for iServer = " + iServer);
				Socket sock = new Socket();
				try{
					if(node.connectionAttempts>0)
						Thread.sleep(WAIT_TIME);
					sock.connect(new InetSocketAddress(node.address, ports.get(iServer)), TIMEOUT);
					initOutgoingConnection(sock, iServer);
				} catch (ConnectException | InterruptedException e) {
					log.finer("NetworkNode connection failed to node "+iServer + ". So far " 
							+ node.connectionAttempts + " attempts.");
					node.connectionAttempts++;
					continue;
				}
				
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		log.fine("Finished in ctor connection loop");
	}

	
	/**
	 * Initialize a new incoming connection. 
	 */
	private void initOutgoingConnection(Socket sock, int iServer) throws IOException{
		NodeInfo node = new NodeInfo(sock);
		log.finer("Initializing outgoing connection with node at " + node);
		
		// send init message identifying myself
		Message msg = new Message(MessageType.INIT, "id", id, id);
		sendMessage(node, msg);
		
		// listen for ack message
		Message ack_msg = receiveMessage(node);
		
		nodes.set(ack_msg.getId(), node);
	}


	public void clearnode(int otherID) {
		log.fine("Clearing node " + otherID);
		nodes.get(otherID).connected = false;
		nodes.get(otherID).writer = null;
		nodes.get(otherID).reader = null;
		nodes.get(otherID).sock = null;
	}

	public boolean isConnected(int inode) {
		if(inode == id)
			return true;
		return nodes.get(inode).connected;
	}

}
	
	
