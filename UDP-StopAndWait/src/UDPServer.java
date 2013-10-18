   /*****************************************
	*Michelle Fish
	*
	*COMP 4320 - Project 2
	*March 27, 2013
	*Port Assignemnt: 10034 to 10037
	******************************************/
   
   import java.io.*;
   import java.net.*;
   import java.util.*;

   class UDPServer {
      static DatagramSocket serverSocket;
      final static int SERVER_PORT = 10034;
      final static int TIMEOUT = 40;
      final static int PACKET_SIZE = 512;
      final static int HEADER_SIZE = 116;
   
      public static void main(String args[]) throws Exception {
         serverSocket = new DatagramSocket(SERVER_PORT);
         while(true) {//server is always listening for connections
         	//receive packet and get IP address and port
            byte[] receiveData = new byte[PACKET_SIZE];
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            serverSocket.receive(receivePacket);
            InetAddress clientIPAddress = receivePacket.getAddress();
            int clientPort = receivePacket.getPort();
            
         	//parse out filename from get request
            String getRequest = new String(receivePacket.getData());
            if(!getRequest.startsWith("GET"))
               continue;
            System.out.println("\n-----FROM CLIENT-----\n" + getRequest.trim());
            String filename = getRequest.split(" ")[1];
         
            //send packets via Stop & Wait Protocol
            stopAndWait(clientIPAddress, clientPort, filename); 
         }
      }
      
      private static void stopAndWait(InetAddress clientIPAddress, int clientPort, String filename) throws Exception {
         int numberOfTimeouts = 0;
         byte[][] segmentedFile = segmentation(filename);
         byte[] packetData = new byte[PACKET_SIZE];
         int maxseq = 1;
         int seq = 0;
         
         for(int i = 0; i < segmentedFile.length; i++){
         	//create packet
            createPacket(packetData, segmentedFile[i], seq);
            DatagramPacket sendPacket = new DatagramPacket(packetData, packetData.length, clientIPAddress, clientPort);
         	
         	//send packet and wait for ack/nak
            serverSocket.setSoTimeout(TIMEOUT);
            while(true){
               serverSocket.send(sendPacket);
               String s = new String(sendPacket.getData());
               System.out.println("\n-----TO CLIENT-----\n" + s.trim());
               
               byte[] ackData = new byte[PACKET_SIZE];
               DatagramPacket getAck = new DatagramPacket(ackData, ackData.length);
               try{
                  serverSocket.receive(getAck);
                  String a = new String(getAck.getData());
                  System.out.println("\n-----FROM CLIENT-----\n" + a.trim());
               }
                  catch(SocketTimeoutException e){
                     numberOfTimeouts++;
                     continue;
                  }
               //check ack
               String ack = new String(getAck.getData());
               if(ack.startsWith("ACK") && getSeqNum(getAck)==seq)
                  break;
               else
                  continue;
            }
            seq = (seq+1)%(maxseq+1);
         }
      	//send null character to indicate EOF
         serverSocket.setSoTimeout(0);
         createPacket(packetData, "\0".getBytes(), seq);
         DatagramPacket sendPacket = new DatagramPacket(packetData, packetData.length, clientIPAddress, clientPort);
         serverSocket.send(sendPacket);
         String s = new String(sendPacket.getData());  
         System.out.println("\n-----TO CLIENT-----\n" + s.trim());
         
         System.out.println("\nNUMBER OF TIMEOUTS: " + numberOfTimeouts + "\n");
      }
      
      private static byte[][] segmentation(String filename) throws Exception{
         FileInputStream filestream = new FileInputStream(new File(filename));
         int size = (int)Math.ceil((double)(filestream.available())/(PACKET_SIZE-HEADER_SIZE));
         byte[][] segmentedFile = new byte[size][PACKET_SIZE-HEADER_SIZE];
         for(int i = 0; i < size; i++){
            for(int j = 0; j < (PACKET_SIZE-HEADER_SIZE); j++){
               if(filestream.available() != 0)
                  segmentedFile[i][j] = (byte)filestream.read();
               else
                  segmentedFile[i][j] = 0;
            }
         }
         filestream.close();
         return segmentedFile;
      }
   
      private static void createPacket(byte[] packetData, byte[] message, int seq) {
         Arrays.fill(packetData, (byte)0);
         String header = new String("HTTP/1.0 200 Document Follows\r\n"+
            								"Content-Type: text/plain\r\n"+
            								"Content-Length: " + message.length + "\r\n"+
            								"Checksum: " + errorDetectionChecksum(message) + "\r\n"+
            								"Seq: " + seq + "\r\n"+
            								"\r\n");
         byte[] headerData = header.getBytes();
         for (int i = 0; i < headerData.length; i++) {
            packetData[i] = headerData[i];
         }
         for (int i = 0; i < message.length; i++) {
            packetData[headerData.length+i] = message[i];
         }
      }
      
      private static int errorDetectionChecksum(byte[] message){
         int checksum = 0;
         for(int i = 0; i < message.length; i++){
            checksum += message[i];
         }
         return checksum;
      }
      
      private static int getSeqNum(DatagramPacket pkt){
         String packetString = new String(pkt.getData());
         int index = packetString.indexOf("Seq: ")+("Seq: ".length());
         int seq = Integer.parseInt(packetString.substring(index, index+1));
         return seq;
      }
   }

