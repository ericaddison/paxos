package paxos;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.LogManager;

import java.io.*;
import java.sql.SQLException;

public class log4jExample{

   /* Get actual class name to be printed on */
   static Logger log;
   
   public static void main(String[] args)throws IOException,SQLException{
	   
		// configure log filename
		System.setProperty("logFilename", "target/log4jExample.log");
		LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
		ctx.reconfigure();
	   
		log = LogManager.getLogger(log4jExample.class.getName());
		
      log.debug("Hello this is a debug message");
      log.info("Hello this is an info message");
      log.trace("Hello this is a trace message");
      log.fatal("Hello this is a fatal message");
      log.warn("Hello this is a warning message");
      log.error("Hello this is an error message");
      
      
      
      
   }
}