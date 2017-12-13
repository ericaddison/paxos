package paxos.paxos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.Logger;

import paxos.NodeFileParser;
import paxos.application.AbstractApp;
import paxos.messages.Message;
import paxos.messages.MessageType;

public class PaxosNode{

	private AbstractApp app;
	private int id;
	private int Nprocs;
	private Logger log;
	private float[] acceptorWeights;
	private List<Integer> distinguishedLearners;
	private boolean distinguishedProposer = false;
	private PaxosState state;
	
	// Standard Ctor for initial startup
	public PaxosNode(AbstractApp app, Logger log) {
		this.log = log;
		this.app = app;
		Nprocs = app.getTotalNodeCount();
		
		// Set weight here
		// TODO: generalize for any input weight, specified in node list file
		acceptorWeights = new float[Nprocs];
		for(int i=0; i<Nprocs; i++)
			acceptorWeights[i] = 1.0f/Nprocs;
		
		this.id = app.getId();
		state = new PaxosState(id);
		
		// parse nodeListFileTokens
		distinguishedLearners = new ArrayList<>();
		for(int i=0; i<Nprocs; i++){
			String[] toks = app.getNodeFileTokens(i);
			if (toks[NodeFileParser.DL_COL].equals("1"))
				distinguishedLearners.add(i);
			acceptorWeights[i] = Float.parseFloat(toks[NodeFileParser.WEIGHT_COL]);
		}
		
		if(app.getNodeFileTokens(id)[NodeFileParser.DP_COL].equals("1")){
			distinguishedProposer = true;
		}
		
		if(isDistinguishedLearner())
			learnerInit();
		
		log.info("Created new " + this.getClass().getSimpleName() + " with:");
		log.info("\tid = " + id);
		log.info("\tweight = " + acceptorWeights[id]);
		log.info("\tDistinguished Proposer = " + distinguishedProposer);
		log.info("\tDistinguished Learner = " + isDistinguishedLearner());
		
		// create and write out state
		state.writeToFile();
	}

	
	// restart ctor, for restarting with a state file
	public PaxosNode(AbstractApp app, Logger log, String stateFilename){
		this(app, log);
		
		// repopulate state from statefile
		log.info("\tStatefile = " + stateFilename);
		state = PaxosState.readFromFile(stateFilename);
	}
	
	
	public void run(){
		log.info("Starting run phase");
	}

	
	public void processMessage(Message msg) {
		log.debug("Processing message " + msg);
		
		switch(msg.getType()){
		case ACCEPT_REQUEST:
			receiveAcceptRequest(msg);
			break;
		case NACK:
			receiveNack(msg);
			break;
		case NACK_OLDROUND:
			receiveNackOldRound(msg);
			break;
		case PREPARE_REQUEST:
			receivePrepareRequest(msg);
			break;
		case PREPARE_RESPONSE:
			receivePrepareResponse(msg);
			break;
		case ACCEPT_NOTIFICATION:
			receiveAcceptNotification(msg);
			break;
		case CHOSEN_VALUE:
			receiveChosenValue(msg);
			break;
		default:
			break;
		}
	}
	
	
//*************************************************8
//	Proposer methods	
	
	public void sendPrepareRequest(){
		long t1 = System.currentTimeMillis();
		// reset propose response count
		state.prepareResponseSum = 0;
		state.nackSum = 0;
		
		// get new proposal number
		state.lastProposalNumber = (state.lastProposalNumber == -1) ? id : state.lastProposalNumber+Nprocs;
		
		// update state and write to file
		log.debug("Updated lastProposalNumber: writing state to file");
		state.writeToFile();
		
		// get acceptor set
		List<Integer> acceptorSet = getAcceptorSet();
		
		// send proposal request to all acceptors in set
		// include round number here
		for(int acceptorId : acceptorSet){
			Message msg = new Message(MessageType.PREPARE_REQUEST, ""+state.currentRound, state.lastProposalNumber, id);
			app.sendMessage(acceptorId, msg);
		}
		
		long t2 = System.currentTimeMillis();
		log.trace("Elapsed time = " + (t2-t1));
	}
	
	
	// start by assuming everyone is an acceptor, generate set
	private List<Integer> getAcceptorSet() {
		List<Integer> list = new ArrayList<Integer>();
		for(int i=id+1; i<id+(Nprocs/2)+2; i++){
			list.add(i%Nprocs);
		}
		return list;
	}

	
	public void sendAcceptRequest(){
		long t1 = System.currentTimeMillis();
		state.nackSum = 0;
		state.prepareResponseSum = 0;
		state.writeToFile();
		
		//TODO: send the accept request
		
		// check if received proposal is null
		// include round number here
		Proposal prop = new Proposal(state.lastProposalNumber, state.myValue, state.currentRound);
		if(state.receivedProposal != null){
			prop.value = state.receivedProposal.value;
		}
		
		// send proposal with value to acceptors
		List<Integer> acceptorSet = getAcceptorSet();
		
		// send proposal request to all acceptors in set
		for(int acceptorId : acceptorSet){
			Message msg = new Message(MessageType.ACCEPT_REQUEST, prop.toString(), state.lastProposalNumber, id);
			app.sendMessage(acceptorId, msg);
		}
		
		long t2 = System.currentTimeMillis();
		log.trace("Elapsed time = " + (t2-t1));
	}
	
	
	public synchronized void receivePrepareResponse(Message msg){
		long t1 = System.currentTimeMillis();
		log.debug("Received PREPARE_RESPONSE from " + msg.getId());
		
		// contents of PREPARE_RESPONSE (the promise)
		// msg.number == YOUR request number
		// msg.value == JSON-ified Proposal
		
		// if outdated response, ignore
		if(msg.getNumber() != state.lastProposalNumber){
			log.debug("Outdated response, ignoring");
			return;
		}

		Proposal responseProposal = Proposal.fromString(msg.getValue());

		if(responseProposal==null){
			log.debug("Received promise from " + msg.getId());
		} else {
			if( state.receivedProposal==null || state.receivedProposal.number < responseProposal.number ){
				log.debug("Received promise with more recent proposal, updating values...");
				state.receivedProposal = responseProposal;
			} else {
				log.debug("Received promise with outdated proposal, not updating values...");
			}
		}
		
		// update response sum. 
		state.prepareResponseSum += acceptorWeights[msg.getId()];
		
		// update state and write to file
		log.debug("Updated prepareResponseSum and receivedProposal: writing state to file");
		state.writeToFile();
		
		// If a majority (>0.5) is obtained, send accept request
		if(state.prepareResponseSum > 0.5){
			log.debug("Prepare response sum = " + state.prepareResponseSum + ", sending accept request");
			sendAcceptRequest();
		} else {
			log.debug("Prepare response sum = " + state.prepareResponseSum);
		}
		
		long t2 = System.currentTimeMillis();
		log.trace("Elapsed time = " + (t2-t1));
		
	}	
	
	// response when you are told "computer says no", proposal number is too low
	// NACKs contain the newer proposal information that must be recorded
	public synchronized void receiveNack(Message msg){
		long t1 = System.currentTimeMillis();
		log.debug("Received NACK");
		Proposal responseProposal = Proposal.fromString(msg.getValue());
		if(responseProposal != null){
			if( state.receivedProposal==null || state.receivedProposal.number < responseProposal.number ){
				log.debug("Received NACK with more recent proposal, updating values...");
				state.receivedProposal = responseProposal;
			} else {
				log.debug("Received NACK with outdated proposal, not updating values...");
			}
		}

		// tally NACKs. If >0.5, start a new prepare request
		state.nackSum += acceptorWeights[msg.getId()];
		
		// update state and write to file
		log.debug("Updated nackSum: writing state to file");
		state.writeToFile();
		
		if(state.nackSum > 0.5){
			log.debug("NACK sum = " + state.nackSum + ", starting new prepare request");
			sendPrepareRequest();
		}
		
		long t2 = System.currentTimeMillis();
		log.trace("Elapsed time = " + (t2-t1));
	}
	
	
	// if found that our round is out of date, 
	public synchronized void receiveNackOldRound(Message msg){
		long t1 = System.currentTimeMillis();
		log.debug("Received NACK_OLDROUND");
		int theirRound = Integer.parseInt(msg.getValue());
		
		if( state.currentRound < theirRound ){
			log.debug("Updating currentRound");
			state.currentRound = theirRound;
			state.writeToFile();
			sendPrepareRequest();
		} else {
			log.debug("Received old round " + theirRound);
		}
		
		long t2 = System.currentTimeMillis();
		log.trace("Elapsed time = " + (t2-t1));
	}
	
	
	
	
//*************************************************8
//	Acceptor methods	
	
	public synchronized void receivePrepareRequest(Message msg){
		long t1 = System.currentTimeMillis();
		int n = msg.getNumber();
		
		// check round number of incoming request
		int round = Integer.parseInt(msg.getValue());
		String propString = (state.acceptedProposal == null) ? "" : state.acceptedProposal.toString();
		
		if( round < state.currentRound ){
			log.debug("Received PREPARE for round " + round + " but I am expecting at least round " + state.currentRound);
			Message nackMsg = new Message(MessageType.NACK_OLDROUND, ""+(state.currentRound), n, id);
			sendNack(nackMsg, msg.getId());
		// if promising
		} else if(n>state.promiseNumber){
			state.promiseNumber = n;
			
			// update state and write to file
			log.debug("Updared promiseNumber: writing state to file");
			state.writeToFile();
			
			Message promiseMsg = new Message(MessageType.PREPARE_RESPONSE, propString, n, id);
			sendPrepareResponse(promiseMsg, msg.getId());
		} else {
			Message nackMsg = new Message(MessageType.NACK, propString, n, id);
			sendNack(nackMsg, msg.getId());
		}
		
		
		long t2 = System.currentTimeMillis();
		log.trace("Elapsed time = " + (t2-t1));
	}
	
	public synchronized void receiveAcceptRequest(Message msg){
		long t1 = System.currentTimeMillis();
		
		// parse message
		Proposal prop = Proposal.fromString(msg.getValue());
		
		if( prop.round < state.currentRound ){
			log.debug("Received ACCEPT_REQUEST for round " + prop.round + " but I am expecting at least round " + state.currentRound);
			Message nackMsg = new Message(MessageType.NACK_OLDROUND, ""+(state.currentRound), msg.getNumber(), id);
			sendNack(nackMsg, msg.getId());
		} else if(prop.number >= state.promiseNumber){
			log.debug("Accepted new proposal from node " + msg.getId() + ": " + prop);
			state.acceptedProposal = prop;
			
			// update state and write to file
			log.debug("Updated acceptedProposal: writing state to file");
			state.writeToFile();
			
			sendAcceptNotification();
		} else {
			log.debug("Ignoring new proposal: " + prop);
			// otherwise send NACK
			String propString = (state.acceptedProposal == null) ? "" : state.acceptedProposal.toString();
			Message nackMsg = new Message(MessageType.NACK, propString, state.promiseNumber, id);
			sendNack(nackMsg, msg.getId());
		}
		

		long t2 = System.currentTimeMillis();
		log.trace("Elapsed time = " + (t2-t1));
	}
	
	
	public void sendPrepareResponse(Message msg, int otherId){
		long t1 = System.currentTimeMillis();
		
		app.sendMessage(otherId, msg);
		
		long t2 = System.currentTimeMillis();
		log.trace("Elapsed time = " + (t2-t1));
	}
	
	
	public void sendNack(Message msg, int otherId){
		long t1 = System.currentTimeMillis();
		
		app.sendMessage(otherId, msg);
		
		long t2 = System.currentTimeMillis();
		log.trace("Elapsed time = " + (t2-t1));
	}
	
	
	// send to distinguished learner(s)
	public void sendAcceptNotification(){
		long t1 = System.currentTimeMillis();
		
		Message msg = new Message(MessageType.ACCEPT_NOTIFICATION, state.acceptedProposal.toString(), state.acceptedProposal.number, id);
		log.debug("Preparing to send out " + msg + " to DLs");
		for(Integer learnerID : distinguishedLearners){
			app.sendMessage(learnerID, msg);
		}
		
		long t2 = System.currentTimeMillis();
		log.trace("Elapsed time = " + (t2-t1));
	}
	
	
	
	
//*************************************************8
//	Learner methods	
	
	public void learnerInit(){
		state.acceptedProposals = new Proposal[Nprocs];
	}
	
	
	// receive from acceptor
	public synchronized void receiveAcceptNotification(Message msg){
		long t1 = System.currentTimeMillis();
		
		// parse message for accepted proposal
		int accId = msg.getId();
		Proposal prop = Proposal.fromString(msg.getValue());
		
		// store proposal in acceptedProposals
		state.acceptedProposals[accId] = prop;
		log.debug("Received new accepted proposal from node " + accId);
		
		// update state and write to file
		log.debug("Updated acceptedProposals: writing state to file");
		state.writeToFile();
		
		
		// inform other learners if value has been chosen if round has incremented
		if(state.currentRound == prop.round){
			// check if a value has been chosen
			String chosenVal = checkForChosenValue();
			
			if(chosenVal != null){
				log.debug("Chosen value (" + accId + ") = " + chosenVal);
				log.info("Determined new chosen value " + chosenVal + " for round " + state.currentRound);
				updateChosenValue(state.currentRound, chosenVal);
				sendChosenValue();
				// increment round number
				state.currentRound++;
				state.receivedProposal = null;
				log.debug("Updated round to " + state.currentRound + " : writing state to file");
				state.writeToFile();
			}
		}
		
		long t2 = System.currentTimeMillis();
		log.trace("Elapsed time = " + (t2-t1));
	}	
	
	
	private String checkForChosenValue(){
		
		// reset chosenChecker to check for value
		Map<String, Float> chosenChecker = new HashMap<>();
		
		for(int i=0; i<Nprocs; i++){
			Proposal p = state.acceptedProposals[i];
			if(p != null && p.round==state.currentRound){
				float sum = (chosenChecker.containsKey(p.value)) ? chosenChecker.get(p.value) : 0;
				sum += acceptorWeights[i];
				if(sum>0.5){
					return p.value;
				}
				chosenChecker.put(p.value, sum);
			}
		}
		
		return null;
	}
	
	
	private void sendChosenValue(){
		long t1 = System.currentTimeMillis();
		
		log.debug("Sending chosen value for round " + state.currentRound + " " + state.chosenValues + " to all");
		Message msg = new Message(MessageType.CHOSEN_VALUE, state.chosenValues.get(state.currentRound), state.currentRound, id);
		for(int i=0; i<Nprocs; i++){
			app.sendMessage(i, msg);
		}
		
		long t2 = System.currentTimeMillis();
		log.trace("Elapsed time = " + (t2-t1));
	}
	
	
	private synchronized void receiveChosenValue(Message msg){
		long t1 = System.currentTimeMillis();
		
		int theirRound = msg.getNumber();
		if(!state.chosenValues.containsKey(theirRound)){
			log.info("Received new chosen value from node " + msg.getId() + " for round " + theirRound + " : " + msg.getValue());
			updateChosenValue(theirRound, msg.getValue());
		} else {
			if(state.chosenValues.get(theirRound).equals(msg.getValue()))
				log.debug("Chosen value confirmed from node " + msg.getId() + " for round " + theirRound + " : " + msg.getValue());
			else
				log.warn("PROBLEM! CONFLICTING CHOSEN VALUE FROM " + msg.getId() + " for round " + theirRound + " : " + msg.getValue() + ". Current chosen value = " + state.chosenValues.get(theirRound));
		}
		
		long t2 = System.currentTimeMillis();
		log.trace("Elapsed time = " + (t2-t1));
	}
	
	
	private void updateChosenValue(int round, String value){
		state.chosenValues.put(round, value);
		// prepare for next round
		state.acceptedProposal = null;
		state.receivedProposal = null;
		state.writeToFile();
	}
	
	

//*************************************************8
//	Getters and Setters
	
	
	public int getId() {
		return id;
	}
	
	public int getNprocs() {
		return Nprocs;
	}
	
	public boolean isDistinguishedProposer() {
		return distinguishedProposer;
	}

	public boolean isDistinguishedLearner() {
		return distinguishedLearners.contains(id);
	}

	public void reset(String value) {
		log.info("Resetting Paxos state with preferred value: " + value);
		state.myValue = value;
		if(isDistinguishedLearner())
			learnerInit();
		
		// update state and write to file
		log.debug("Reset state: writing state to file");
		state.writeToFile();
	}

	public String getChosenValueForRound(int round){
		if(state.chosenValues.containsKey(round))
			return state.chosenValues.get(round);
		return null;
	}


	public String getLatestChosenValue() {
		return getChosenValueForRound(state.currentRound-1);
	}
	
	public int getCurrentRound(){
		return state.currentRound;
	}
	
}
