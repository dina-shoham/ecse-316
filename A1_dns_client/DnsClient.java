import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.Random;

public class DnsClient {

  // class variables
  public static int timeout = 5000;
  public static int maxRetries = 3;
  public static int port = 53;
  public static String serverType = "A"; // serverType = 0 if IP address (A), 1 if mail server (MX), 2 if name server (NS)   
  public static byte[] ipAddress = new byte[4];
  public static String name = "";
  public static int QNAME_size = 0;


  public static void main(String[] args) throws IOException {

    // parsing arguments
    int i;
    
    if(args.length < 2) {
      System.out.println("ERROR \tIncorrect input syntax");
      return;
    }
    
    for(i = 0; i < args.length; i++) {
      if(args[i].contains("-t")) {
        timeout = Integer.parseInt(args[i+1]) * 1000; // convert to milliseconds
        i++;
        continue;
      }
      else if(args[i].equals("-r")) {
        maxRetries = Integer.parseInt(args[i+1]);
        i++;
        continue;
      }
      else if(args[i].equals("-p")) {
        port = Integer.parseInt(args[i+1]);
        i++;
        continue;
      }
      else if(args[i].equals("-mx")) {
        serverType = "MX";
        continue;
      }
      else if(args[i].equals("-ns")) {
        serverType = "NS";
        continue;
      }
      else if (args[i].charAt(0) == '@'){
        // convert to IP using helper method (checks for errors)
        try {
          ipAddress = convertToIP(args[i]);
        } catch (Exception e) {
          e.printStackTrace();
        }
        i++;
        name = args[i];

        // calculate size needed for QNAME
        for (String word : name.split("\\.")) {
          QNAME_size += (1 + word.length()); // size is length of word + 1 byte that stores length of word
        }
        QNAME_size++; 
      }
      else {
        System.out.println("ERROR \tIncorrect input syntax");
        return;
      }
    }

    System.out.println("DnsClient sending request for "+name+"\nServer: "+args[i-2]+"\nRequest type: "+serverType);

    // sends request and passes the output as a parameter to receive response method
    receiveResponse(sendRequest(0));
  }

  /* SEND DNS REQUEST */
  public static byte[] sendRequest(int retries) throws IOException {
    try {
      // create socket
      DatagramSocket clientSocket = new DatagramSocket();
      clientSocket.setSoTimeout(timeout);
      InetAddress address = InetAddress.getByAddress(ipAddress);

      // Forming the DNS packet
      byte[] sendDNSbytes = new byte[16 + QNAME_size]; // Not sure about size yet

      // HEADER
      // first 2 bytes: random ID
      byte[] id = new byte[2];
      Random r = new Random();
      r.nextBytes(id);
      sendDNSbytes[0] = id[0];
      sendDNSbytes[1] = id[1];

      // QR = 0, OPCODE = 0000, AA = 0, TC = 0, RD = 1
      sendDNSbytes[2] = (byte) 0x01; 

      // RA = 0, Z = 000, RCODE = 0000
      sendDNSbytes[3] = (byte) 0x00; 

      // QDCODE = 0x0001
      sendDNSbytes[4] = (byte) 0x00;
      sendDNSbytes[5] = (byte) 0x01; 

      // ANCOUNT = 0x0000
      sendDNSbytes[6] = (byte) 0x00;
      sendDNSbytes[7] = (byte) 0x00;

      // NSCOUNT = 0x0000
      sendDNSbytes[8] = (byte) 0x00;
      sendDNSbytes[9] = (byte) 0x00; 

      // ARCOUNT = 0x0000
      sendDNSbytes[10] = (byte) 0x00;
      sendDNSbytes[11] = (byte) 0x00;

      // QUESTION
      // QNAME
      int index = 12;
      for (String word : name.split("\\.")) {
        sendDNSbytes[index] = (byte) word.length();
        index++;
        for (int i = 0; i < word.length(); i++, index++) {
          sendDNSbytes[index] = (byte) word.charAt(i);
        }
      }
      sendDNSbytes[index] = (byte) 0x00; // End QNAME with byte 0
      index++;
      sendDNSbytes[index] = (byte) 0x00; // Start QTYPE with byte 0
      index++;

      // QTYPE
      if (serverType.equals("MX")) {
        sendDNSbytes[index] = (byte) 0x0f; 
        index++;
      }
      else if (serverType.equals("NS")) {
        sendDNSbytes[index] = (byte) 0x02; 
        index++;
      }
      else { // Type "A"
        sendDNSbytes[index] = (byte) 0x01; 
        index++;
      }

      // QCLASS = 0x0001
      sendDNSbytes[index] = (byte) 0x00;
      index++;
      sendDNSbytes[index] = (byte) 0x01; 

      // Send the created packet
      byte[] receiveDNSbytes = new byte[300]; // Unsure about size
      DatagramPacket sendPacket = new DatagramPacket(sendDNSbytes, sendDNSbytes.length, address, port);
      DatagramPacket receivePacket = new DatagramPacket(receiveDNSbytes, receiveDNSbytes.length);

      long startTime = System.nanoTime();
      clientSocket.send(sendPacket);
      clientSocket.receive(receivePacket);
      clientSocket.close();
      double timeTaken = ((double)(System.nanoTime() - startTime)) / 1000000000;

      System.out.println("Response received after "+timeTaken+" seconds and "+retries+" retries");
      return receiveDNSbytes;     

    } 
    // exception handling (retries)
    catch (SocketTimeoutException e) {
      System.out.println("Request timed out"); 
      if(retries < maxRetries) {
        retries++;
        sendRequest(retries);
      } else {
        throw new SocketTimeoutException("ERROR\tMaximum number of retries "+maxRetries+" exceeded.");
      }
    } catch (Exception e) {
      throw e;
    }
    return null;
  }


  /* RECEIVE DNS RESPONSE */
  public static void receiveResponse(byte[] data) {
    int numAnswers = ((data[6] & 0xff) << 8) + (data[7] & 0xff);
    System.out.println("***Answer Section ("+numAnswers+" records)***");

    // Start at index of answer
    int i = 16 + QNAME_size; 
    i = analyzeResponse(numAnswers, i, data);

    // check if authoritative records were found (ANCOUNT)
    int numAuthoritative = ((data[8] & 0xff) << 8) + (data[9] & 0xff);
    
    // skip all authoritative responses
    for (int auth_i = 0; auth_i < numAuthoritative; auth_i++) {
      // advance to type bit
      while (data[i] != 0) i++;
      i++;
      if (data[i - 3] != -64) i+=3;
      
      // if type MX, increment index by 11
      if(data[i] == 15) {
        i += 11;
      }
      // all other types increment by 9
      else {
        i+= 9;
      }
    }
    
    // check if additional records were found
    int numAdditional = ((data[10] & 0xff) << 8) + (data[11] & 0xff);

    if (numAdditional == 0) System.out.println("NOTFOUND: No records in the additional section");
    else {
      System.out.println("***Additional Section ("+numAdditional+" records)***");
      analyzeResponse(numAdditional,i, data);
    }  
  }    

  /* interprets DNS responses */
  public static int analyzeResponse(int numAnswers, int i, byte[] data) {

    // iterate over all responses
    for (int responseIndex = 0; responseIndex < numAnswers; responseIndex++) {
      
      // check for errors, skip to next response in for loop
      if(!handleResponseErrors(data)) {
        continue;
      }

      // advance to QTYPE bit
      while (data[i] != 0) i++;
      i++;
  	  if (data[i - 3] != -64) i+=3;


      // Get the "seconds can cache" (TTL)
      byte[] TTLBytes = new byte[4];
      TTLBytes[0] = data[i+3];
      TTLBytes[1] = data[i+4];
      TTLBytes[2] = data[i+5];
      TTLBytes[3] = data[i+6];
      int TTL = ByteBuffer.wrap(TTLBytes).getInt();

      // Check if authoritative
      int AA = (data[2] >> 5) & 1;
      String auth = "";
      if (AA == 0) auth += "nonauth";
      else auth += "auth";

      // depending on QTYPE, interpret response
      switch(data[i]) {
      //type A
        case 1: 
          System.out.println("IP\t"
              +Byte.toUnsignedInt(data[i+9])+"."
              +Byte.toUnsignedInt(data[i+10])+"."
              +Byte.toUnsignedInt(data[i+11])+"."
              +Byte.toUnsignedInt(data[i+12])
              +"\tseconds can cache\t"+TTL+"\t"+auth);
          i += 9;
          break;
          
        //type NS
        case 2: 
          String name = "";
          int j = i + 9;

          name += getName(data, j, "");

          System.out.println("NS\t"+name.substring(0, (name.length() - 1))+"\tseconds can cache\t"+TTL+"\t"+auth);
          i += 9;
          break;
          
        //type CNAME 
        case 5:
          String alias = "";
          int j2 = i + 9;

          alias += getName(data, j2, "");

          System.out.println("CNAME\t"+alias.substring(0, (alias.length() - 1))+"\tseconds can cache\t"+TTL+"\t"+auth);
          i += 9;
          break;
          
        //type MX
        case 15:          
          // Getting pref
          int pref = Byte.toUnsignedInt(data[i+9])*256 + Byte.toUnsignedInt(data[i+10]);

          // Getting the alias
          String alias2 = "";
          int j3 = i + 11;
          alias2 += getName(data, j3, "");

          System.out.println("MX\t"+alias2.substring(0, (alias2.length() - 1))+"\t"+pref+"\tseconds can cache\t"+TTL+"\t"+auth);
          i += 11;
          
          
          break;
        default:
          System.out.println("ERROR\tno valid type detected"); 
      }
    }

    return i;
  }


  /*
   * HELPER METHODS
   */

  // converts a String ip address to an array of 4 bytes
  // also does some error handling for IP address format
  public static byte[] convertToIP(String s) throws Exception {
    s = s.replace("@", "");
    String sSplit[] = s.split("\\.");

    if(sSplit.length != 4) {
      throw new Exception ("ERROR\tIncorrect IP address format");
    }

    byte[] ip = new byte[4];
    for(int i = 0; i < sSplit.length; i++) {
    	ip[i] = (byte) Integer.parseInt(sSplit[i]);
    }

    return ip;
  }

  // Helper method to generate alias string from array of bytes
  public static String getName(byte[] data, int j, String alias) {
	  
	  while (data[j] != 0) {		
      	// Compressed data
      	if (data[j] == -64) {
      	  int k = data[j + 1];
      	  while (data[k] != 0 && data[k] != -64) {
      		int innerLength = Byte.toUnsignedInt(data[k]);
      	  	k++;
      	  	while (innerLength > 0) {
      	  	  alias += (char) data[k];
      	  	  k++;
      	  	  innerLength--;
      	  	}
      	  	alias += ".";
      	  }
      		  //alias += getName(data, data[j+1], "");
      	  return alias;
      	}
      	else {
          int length = Byte.toUnsignedInt(data[j]);
      	  j++;
      	  while (length > 0) {
      	    alias += (char) data[j];
      		j++;
      		length--;
      	  }
      	  alias += ".";
      	}
	  } 
	  return alias;
  }  
  
  // error handling helper method
  public static Boolean handleResponseErrors(byte[] data) {
    // QR should = 1
    if (((data[2]>>7) & 1) != 1) {
      System.out.println("ERROR\tNot a response");
      return false;
    }
    
    // RA should = 1
    if (((data[3]>>7) & 1) != 1) {
      System.out.println("Server does not support recursive queries");
    }
    
    // RCODE should = 0
    int rcode = (data[3] & 0x0F);
    switch (rcode) {
      case 0: 
        break;
      case 1: 
        System.out.println("ERROR\tFormat error: the name server was unable to interpret the query");
        return false;
      case 2: 
        System.out.println("ERROR\tServer failure: the name server was unable to process this query due to a problem with the name server");
        return false;
      case 3: 
        if ( ((data[2] >> 5) & 1) == 0) {
          System.out.println("NOTFOUND\tthe domain name referenced in the query does not exist");
          return false;
        }  
        break;
      case 4: 
        System.out.println("ERROR\tNot implemented: the name server does not support the requested kind of query");
        return false;
      case 5: 
        System.out.println("ERROR\tRefused: the name server refuses to perform the requested operation for policy reasons");
        return false;
    }
    
    // CLASS should = 0x0001
    if (data[12 + QNAME_size + 2] != 0 && data[12+QNAME_size+3] != 1) {
      System.out.println("ERROR\tUnexpected class value");
      return false;
    }
    
    return true;
  }
}
