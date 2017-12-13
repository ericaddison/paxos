package paxos.application;

import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import paxos.messages.Message;
import paxos.network.NetworkNode;
import paxos.paxos.PaxosNode;

abstract public class AbstractApp {

	private final Logger log = Logger.getLogger(this.getClass().getCanonicalName());
	private Level logLevel = Level.ALL;
	
	private NetworkNode netnode;
	private PaxosNode paxnode;
	private int id;
	
	
	public AbstractApp(int id, String nodeListFileName, String statefile) {
		this.id = id;
		setupLogger();
		
		log.info("Created new " + this.getClass().getSimpleName() + " with:");
		log.info("\tid = " + id);
		
		netnode = new NetworkNode(id, nodeListFileName, (statefile!=null), log);
		if(statefile==null)
			paxnode = new PaxosNode(this, log);
		else
			paxnode = new PaxosNode(this, log, statefile);

	}
	
	public void run(){
		log.fine("Running netnode");
		netnode.run();
		
		log.fine("Running paxnode");
		paxnode.run();
		
		log.fine("Starting Listener loops");
		startListenerLoops();
		
		log.fine("Running run_app");
		run_app();
	}
	
	
	// Main app method for derived classes
	abstract public void run_app();
	
	abstract public void processMessage(Message msg);
	
	
	private void setupLogger(){
		// set logging format
		System.setProperty("java.util.logging.SimpleFormatter.format",
		           "%4$s ::: %2$s ::: %5$s %6$s%n");
     //           "%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS.%1$tL %4$s ::: %2$s ::: %5$s %6$s%n");
		
		// NO initial logging handlers
				log.getParent().removeHandler(log.getParent().getHandlers()[0]);
		
		try {
			FileHandler fh = new FileHandler("logs/log_" + id + ".log");
			fh.setFormatter(new SimpleFormatter());
			fh.setLevel(logLevel);
			log.addHandler(fh);
			log.setLevel(logLevel);
		} catch (SecurityException | IOException e) {
			e.printStackTrace();
		}
	}
	
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
			log.log(Level.FINEST, "Starting App listener thread "+ inode);
			t.start();
		}
	}
	
	
	/**
	 * Infinite listen loop for messages on the App network
	 */
	private void listenerLoop(int otherID){
		Message msg = null;
		log.finer("Entering listener loop for node " + otherID);
		int connectionAttempts = 0;
		while(true){
			if(!netnode.isConnected(otherID)){
				connectionAttempts++;
				if(connectionAttempts<25 || connectionAttempts%50==0){
					log.finest("Node " + otherID + " not connected after " + connectionAttempts + " attempts!");
				}
				try {
					int wait_time = Math.min(500*connectionAttempts, 60000);
					Thread.sleep(wait_time);
				} catch (InterruptedException e) {}
				continue;
			}
			
			try{
				log.finer("Listener loop connected for node " + otherID);
				while( (msg = netnode.receiveMessage(otherID)) != null){
					log.finest("Received string " + msg + " from node " + otherID);
					
					// process message based on type
					paxnode.processMessage(msg);
					processMessage(msg);
				}
			} catch (Exception e){
				log.warning(e.getClass().getSimpleName() + ": " + e.getMessage());
				e.printStackTrace();
			}
			finally{
				log.log(Level.WARNING, "Uh oh! Lost connection with server " + otherID + ": clearing comms");
				connectionAttempts = 0;
				netnode.clearnode(otherID);
			}
			
			
			
		}
	}
	
	
}
