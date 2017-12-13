package paxos.application;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import paxos.messages.Message;
import paxos.network.NetworkNode;
import paxos.paxos.PaxosNode;

abstract public class AbstractApp {

	private final Logger log = LogManager.getLogger(this.getClass().getCanonicalName());
	
	private NetworkNode netnode;
	private PaxosNode paxnode;
	private int id;
	
	
	public AbstractApp(int id, String nodeListFileName, String statefile) {
		this.id = id;
		
		log.info("Created new " + this.getClass().getSimpleName() + " with:");
		log.info("\tid = " + id);
		
		netnode = new NetworkNode(id, nodeListFileName, (statefile!=null), log);
		if(statefile==null)
			paxnode = new PaxosNode(this, log);
		else
			paxnode = new PaxosNode(this, log, statefile);

	}
	
	public void run(){
		log.info("Running netnode");
		netnode.run();
		
		log.info("Running paxnode");
		paxnode.run();
		
		log.info("Starting Listener loops");
		startListenerLoops();
		
		log.info("Running run_app");
		run_app();
	}
	
	
	// Main app method for derived classes
	abstract public void run_app();
	
	abstract public void processMessage(Message msg);
	
	public Logger getLog() {
		return log;
	}
	
	public int getId() {
		return id;
	}
	
	public void initiate_paxos(String value){
		paxnode.reset(value);
		paxnode.sendPrepareRequest();
	}
	
	public PaxosNode getPaxnode() {
		return paxnode;
	}
	
	public int getTotalNodeCount(){
		return netnode.getTotalNodeCount();
	}
	
	public String[] getNodeFileTokens(int i){
		return netnode.getNodeFileTokens(i);
	}
	
	public boolean isRunning(){
		return netnode.isRunning();
	}
	
	public void sendMessage(int nodeId, Message msg){
		netnode.sendMessage(nodeId, msg);
	}
	
	
	
//*************************************************8
//		Listen loop methods	
		

	private void startListenerLoops(){
		// start a listener loop for all connected nodes
		for(int inode=0; inode<netnode.getTotalNodeCount(); inode++){
			// spin off new thread
			final int ii = inode;
			Thread t = new Thread(new Runnable() {

				@Override
				public void run() {
					listenerLoop(ii);
				}
			});
			log.debug("Starting App listener thread "+ inode);
			t.start();
		}
	}
	
	
	/**
	 * Infinite listen loop for messages on the App network
	 */
	private void listenerLoop(int otherID){
		Message msg = null;
		log.debug("Entering listener loop for node " + otherID);
		int connectionAttempts = 0;
		while(true){
			if(!netnode.isConnected(otherID)){
				connectionAttempts++;
				if(connectionAttempts<25 || connectionAttempts%50==0){
					log.trace("Node " + otherID + " not connected after " + connectionAttempts + " attempts!");
				}
				try {
					int wait_time = Math.min(500*connectionAttempts, 60000);
					Thread.sleep(wait_time);
				} catch (InterruptedException e) {}
				continue;
			}
			
			try{
				log.debug("Listener loop connected for node " + otherID);
				while( (msg = netnode.receiveMessage(otherID)) != null){
					log.debug("Received string " + msg + " from node " + otherID);
					
					// process message based on type
					paxnode.processMessage(msg);
					processMessage(msg);
				}
			} catch (Exception e){
				log.warn(e.getClass().getSimpleName() + ": " + e.getMessage());
				e.printStackTrace();
			}
			finally{
				log.warn("Uh oh! Lost connection with server " + otherID + ": clearing comms");
				connectionAttempts = 0;
				netnode.clearnode(otherID);
			}
			
			
			
		}
	}
	
	
}
