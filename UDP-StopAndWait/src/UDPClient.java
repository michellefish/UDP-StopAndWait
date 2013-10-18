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

   class UDPClient {
      static DatagramSocket clientSocket;
      final static int PORT = 10034;
      final static int PACKET_SIZE = 512;
      final static int HEADER_SIZE = 116;
      
      static Random generator = new Random(System.currentTimeMillis());
      static String ip;
      static double damageProb;
      static String filename;
    
      public static void main(String args[]) throws Exception {
         //get user input and send GET request
         getUserInput();
         clientSocket = new DatagramSocket(PORT);
         InetAddress serverIPAddress = InetAddress.getByName(ip);
         String request = sendRequestPacket(serverIPAddress, filename);
         System.out.println("\n-----TO SERVER-----\n" + request.trim());
         
         stopAndWait(serverIPAddress);
         clientSocket.close();
      }
      
      private static void stopAndWait(InetAddress serverIPAddress) throws Exception {
         int totalPacketsReceived = 0;
         int packetsDamaged = 0;
         int packetsDropped = 0;
         ArrayDeque<byte[]> segmentedFile = new ArrayDeque<byte[]>();
         byte[] receiveData = new byte[PACKET_SIZE];
         DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
         int expectedSeq = 0;
         int maxseq = 1;
      	
         while(true){
         	//receive packet
            clientSocket.receive(receivePacket);
            totalPacketsReceived++;
            String s = new String(receivePacket.getData());
            System.out.println("\n-----FROM SERVER-----\n" + s.trim());
           
         	//check if last packet
            if(isNullPacket(receivePacket))
               break;
            //gremlin then check is packet is corrupted or wrong packet
            gremlin(receivePacket);
            int packetSeq = getSeqNum(receivePacket);
            boolean isDamaged = isErrorDetected(receivePacket);
            boolean isCorrectSeq = (expectedSeq == packetSeq);
            
            //send ACK/NAK
            String msg;
            if(!isDamaged && isCorrectSeq){
               msg = "ACK";
               String packetString = new String(receivePacket.getData());
               byte[] data = packetString.split("\r\n\r\n")[1].getBytes();
               segmentedFile.add(data);
               expectedSeq = (expectedSeq+1)%(maxseq+1);
            }
            else{
               if(isDamaged){
                  System.out.println("***PACKET DAMAGED***");
                  packetsDamaged++;
               }
               else{
                  System.out.println("***PACKET IS NOT EXPECTED PACKET - DOES NOT HAVE CORRECT SEQ NUMBER***");
                  packetsDropped++;
               }
               msg = "NAK";
            }
            String ack = sendAcknowledgement(serverIPAddress, msg, packetSeq);
            System.out.println("\n-----TO SERVER-----\n" + ack.trim());
         }
         reassembleFile(segmentedFile);
         System.out.println("\nTOTAL PACKETS RECEIVED: " +	totalPacketsReceived);
         System.out.println("PACKETS DAMAGED: " + packetsDamaged);
         System.out.println("PACKETS DROPPED: " + packetsDropped + "\n");
      }
      
      private static void getUserInput() throws Exception {
         BufferedReader inputFromUser = new BufferedReader(new InputStreamReader(System.in));
         System.out.print("\nEngineering tux machine to connect to: ");
         ip = inputFromUser.readLine();
         System.out.print("\nProbabilty that a given packet will be damaged: ");
         damageProb = Double.parseDouble(inputFromUser.readLine());
         System.out.print("\nEnter file name to request: ");
         filename = inputFromUser.readLine();
         inputFromUser.close();
      }
      
      private static String sendRequestPacket(InetAddress serverIPAddress, String filename) throws Exception {
         String request = ("GET " + filename + " HTTP/1.0\r\n");
         byte[] requestData = new byte[request.length()];
         requestData = request.getBytes();
         DatagramPacket requestPacket = new DatagramPacket(requestData, requestData.length, serverIPAddress, PORT);
         clientSocket.send(requestPacket);
         return request;
      }
      
      private static String sendAcknowledgement(InetAddress serverIPAddress, String msg, int seq) throws Exception {
         String ack = (msg + " Seq: " + seq + "\r\n");
         byte[] ackData = new byte[ack.length()];
         ackData = ack.getBytes();
         DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length, serverIPAddress, PORT);
         clientSocket.send(ackPacket);
         return ack;
      }
   	
      private static void reassembleFile(ArrayDeque<byte[]> segmentedFile) throws Exception {
         FileOutputStream filestream = new FileOutputStream(new File("new_" + filename));
         while(!segmentedFile.isEmpty()){
            String msg = new String(segmentedFile.removeFirst());
            String data = msg.replaceAll("\00", "");
            byte[] byteData = new byte[data.length()];
            byteData = data.getBytes();
            filestream.write(byteData);
         }
         filestream.close();
      }
      
      private static void gremlin(DatagramPacket pkt){
         int rand = generator.nextInt(10)+1;
         if(rand <= damageProb*10){//corrupt packet
            int change = generator.nextInt(10)+1;
            if(change <= 5){//change 1 byte
               int damage = generator.nextInt(pkt.getLength()-HEADER_SIZE)+HEADER_SIZE;
               byte[] buf = pkt.getData(); 
               if(buf[damage] == 0)
                  buf[damage] += 1;
               else
                  buf[damage] -= 1;
            }
            else if(change <= 8){//change 2 bytes
               for(int i = 0; i < 2; i++){
                  int damage = generator.nextInt(pkt.getLength()-HEADER_SIZE)+HEADER_SIZE;
                  byte[] buf = pkt.getData(); 
                  if(buf[damage] == 0)
                     buf[damage] += 1;
                  else
                     buf[damage] -= 1;
               }
            }
            else{//change 3 bytes
               for(int i = 0; i < 3; i++){
                  int damage = generator.nextInt(pkt.getLength()-HEADER_SIZE)+HEADER_SIZE;
                  byte[] buf = pkt.getData(); 
                  if(buf[damage] == 0)
                     buf[damage] += 1;
                  else
                     buf[damage] -= 1;
               }
            }
         }
      }
      
      private static boolean isErrorDetected(DatagramPacket pkt){
         //get checksum from packet
         String packetString = new String(pkt.getData());
         int index = packetString.indexOf("Checksum: ")+("Checksum: ".length());
         int index2 = packetString.indexOf("\r\nSeq:");
         int checksum = Integer.parseInt(packetString.substring(index, index2));
      	
      	//compute checksum
         byte[] data = packetString.split("\r\n\r\n")[1].getBytes();
         int computedChecksum = 0;
         for(int i = 0; i < data.length; i++){
            computedChecksum += data[i];
         }
            
      	//compare checksums
         if(computedChecksum == checksum)
            return false;
         else
            return true;
      }
      
      private static int getSeqNum(DatagramPacket pkt){
         String packetString = new String(pkt.getData());
         int index = packetString.indexOf("Seq: ")+("Seq: ".length());
         int seq = Integer.parseInt(packetString.substring(index, index+1));
         return seq;
      }
      
      private static boolean isNullPacket(DatagramPacket pkt){
         String pktData = new String(pkt.getData());
         byte[] dataByte = pktData.split("\r\n\r\n")[1].getBytes();
         if(dataByte[0] == 0)
            return true;
         else
            return false;
      }
   }
