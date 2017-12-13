package paxos.application;

import java.io.File;

import paxos.messages.Message;

public class PretendApp extends AbstractApp{

	public PretendApp(int id, String nodeListFileName, String statefile) {
		super(id, nodeListFileName, statefile);
	}

	@Override
	public void run_app() {
		getLog().info("PretendApp run phase");
		commandLoop();
	}
	
	public void commandLoop(){
		
		// initial 5s sleep
		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		// start a Paxos round every 2 seconds, N times
		
		int N = 10;
		for(int cnt=0; cnt<N; cnt++){
			try {
				if(getPaxnode().isDistinguishedProposer()){
					int round = getPaxnode().getCurrentRound();
					long t1 = System.currentTimeMillis();
					getLog().info("Initiating Paxos round " + round);
					initiate_paxos("MyVal" + getId() + "-" + round);
					
					String chosenValue = null;
					while( (chosenValue = getPaxnode().getChosenValueForRound(round)) == null){
						Thread.sleep(1);
					}
					long t2 = System.currentTimeMillis();
					getLog().info("Chosen value for round " + round + " : " + chosenValue);
					getLog().info("Elapsed time for round " + round + " : " + (t2-t1) + " ms");
					Thread.sleep(100);
				}
			
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		
	}

	
//****************************************************************
//	main()
//****************************************************************
	
	/**
	 * Run the main program
	 * 
	 * @param args
	 *            command line input. Expects [id] [nodeList file] [optional "restart"]
	 *            
	 * Format of nodeList file:
	 * 127.0.0.1:8000
	 * 127.0.0.1:8005
	 * [ip]:[port]
	 * ...
	 */
	public static void main(String[] args) {
		if (args.length < 2 || args.length > 3) {
			System.out.println("ERROR: Provide 2 or 3 arguments");
			System.out.println("\t(1) <int>: process id, between 0 and number of nodes in node list file");
			System.out.println("\t(2) <file>: node list file");
			System.out.println("\t(3) <statefile>: optional path to statefile to restart failed server");
			System.exit(-1);
		}

		int id=0;
		try{
			id = Integer.parseInt(args[0]);
		} catch (NumberFormatException e){
			System.err.println("Error parsing process id from input string " + args[0]);
			System.exit(1);
		}
		
		String fileName = args[1];
		
		String statefile = null;
		if(args.length==3)
			statefile = args[2];

		File file = new File(fileName);
		if (!file.exists() || file.isDirectory()){
			System.err.println("IO error for nodeList file: " + fileName);
			System.exit(2);
		}


		AbstractApp app = new PretendApp(id, fileName, statefile);
		app.run();
	}

	@Override
	public void processMessage(Message msg) {}
	
	
	
}
