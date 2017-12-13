package paxos.paxos;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

public class Proposal {
	private static final Gson gson = new Gson();
	
	int number;
	int round;
	String value;
	
	public Proposal(int number, String value, int round) {
		this.number = number;
		this.value = value;
		this.round = round;
	}
	
	
	@Override
	public String toString() throws IllegalArgumentException{
		try{
			return gson.toJson(this);
		} catch(JsonSyntaxException e){
			throw new IllegalArgumentException("Could not encode Proposal from string");
		}
	}
	
	public static Proposal fromString(String string) throws IllegalArgumentException{
		try{
			return gson.fromJson(string, Proposal.class);
		} catch(JsonSyntaxException e){
			throw new IllegalArgumentException("Could not decode Proposal from string");
		}
	}
	
}
