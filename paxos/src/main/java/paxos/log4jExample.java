package paxos;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.layout.JsonLayout;
import org.apache.logging.log4j.LogManager;

import java.io.*;
import java.sql.SQLException;

public class log4jExample{

   /* Get actual class name to be printed on */
   static Logger log = LogManager.getLogger(log4jExample.class.getName());
   
   public static void main(String[] args)throws IOException,SQLException{
      log.debug("Hello this is a debug message");
      log.info("Hello this is an info message");
      log.trace("Hello this is a trace message");
      log.fatal("Hello this is a fatal message");
      log.warn("Hello this is a warning message");
      log.error("Hello this is an error message");
   }
}