package paxos;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class NodeFileParser {

	// Columns for nodeListFile
	public static final int IP_COL = 0;
	public static final int PORT_COL = 1;
	public static final int WEIGHT_COL = 2;
	public static final int MSG_DELAY_COL = 3;
	public static final int UNRELIABILITY_COL = 4;
	public static final int DL_COL = 5;
	public static final int DP_COL = 6;
	
	
	public static List<String[]> parseNodeFile(String filename) {
		
		List<String[]> toks = new ArrayList<>();
		
		try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(filename)))) {

			// remaining lines = server locations
			String nextServer = "";
			while ((nextServer = br.readLine()) != null) {
				String[] serverToks = nextServer.split(" ");
				toks.add(serverToks);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return toks;
	}
	
	
	
}
