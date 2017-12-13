package paxos.messages;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

public class Message {
	private static final Gson gson = new Gson();
	private MessageType type;
	private String value;
	private int number;
	private int id;
	
	public Message(MessageType type, String value, int number, int id) {
		super();
		this.type = type;
		this.value = value;
		this.number = number;
		this.id = id;
	}
	
	public MessageType getType() {
		return type;
	}

	public String getValue() {
		return value;
	}

	public int getNumber() {
		return number;
	}
	
	public int getId() {
		return id;
	}

	@Override
	public String toString() throws IllegalArgumentException{
		try{
			return gson.toJson(this);
		} catch(JsonSyntaxException e){
			throw new IllegalArgumentException("Could not encode Message from string");
		}
	}
	
	public static Message fromString(String string) throws IllegalArgumentException{
		try{
			return gson.fromJson(string, Message.class);
		} catch(JsonSyntaxException e){
			throw new IllegalArgumentException("Could not decode Message from string");
		}
	}
	
	public static void main(String[] args) {
		
		Message msg = new Message(MessageType.ACCEPT_REQUEST, "my value", 10, 0);
		System.out.println(msg);
		
		String json = msg.toString();
		System.out.println(json);
		
		Message msg2 = Message.fromString(json);
		System.out.println(msg2);
		
	}
	
}
