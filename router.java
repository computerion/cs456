import java.io.*;  
import java.net.*;  
import java.util.*;
import java.nio.*;

public class router {

	public static final int NBR_ROUTER = 5;
	private static int routerId;
	private static link_cost[] linkCosts;

	private static DatagramSocket socket;
	private static int routerPort;
	private static InetAddress hostAddress;
	private static int hostPort;

	private static PrintWriter routerLog;

	public static void main(String[] args) throws Exception  {
		init(args);
		sendInit();
		recieveCircuitDB();
	}

	private static void init(String args[]) throws Exception {
	    if (args.length < 4) {
	      System.out.println("Execution is:\n$ java router <router_id> <nse_host> <nse_port> <router_port>");
	      System.exit(1);
	    }

	    socket = new DatagramSocket();
	    routerId = Integer.parseInt(args[0]);
	    hostAddress = InetAddress.getByName(args[1]);
	    hostPort = Integer.parseInt(args[2]);
	    routerPort = Integer.parseInt(args[3]);

	    routerLog = new PrintWriter(new FileWriter(String.format("router%03d.log", routerId)), true);
	  }

	private static void sendInit() throws Exception {
		ByteBuffer buffer = ByteBuffer.allocate(512);
		buffer.order(ByteOrder.LITTLE_ENDIAN);
		buffer.putInt(routerId);
    	DatagramPacket sendPacket = new DatagramPacket(buffer.array(), 512, hostAddress, hostPort); 
    	System.out.println("Sending"); 
    	socket.send(sendPacket);
    	System.out.println("Sent Packets");
	}

	private static void recieveCircuitDB() throws Exception {
		byte[] data = new byte[512];
	    DatagramPacket receivePacket = new DatagramPacket(data, data.length);  
	    System.out.println("Recieving");
	    socket.receive(receivePacket);
	    ByteBuffer buffer = ByteBuffer.wrap(receivePacket.getData());
	    buffer.order(ByteOrder.LITTLE_ENDIAN);
	    int numLinks = buffer.getInt();
	    System.out.println(numLinks);
	    linkCosts = new link_cost[numLinks];
	    for (int i=0; i<numLinks; i++) {
	    	linkCosts[i] = new link_cost();
	    	linkCosts[i].link = buffer.getInt();
	    	linkCosts[i].cost = buffer.getInt();
	    	System.out.println(linkCosts[i].link + " " + linkCosts[i].cost);
	    }
	}
}

class link {
	public link_cost link;
	public int reciever_router_id;
}

class pkt_HELLO { 
	public int router_id; /* id of the router who sends the HELLO PDU */ 
	public int link_id; /* id of the link through which it is sent */ 
} 

class pkt_LSPDU { 
	public int sender; /* sender of the LS PDU */ 
	public int router_id; /* router id */ 
	public int link_id; /* link id */ 
	public int cost; /* cost of the link */ 
	public int via; /* id of the link through which the LS PDU is sent */ 
}

class pkt_INIT { 
	public int router_id; /* id of the router that send the INIT PDU */
} 

class link_cost { 
	public int link; /* link id */ 
	public int cost; /* associated cost */ 
} 

class circuit_DB { 
	public int nbr_link; /* number of links attached to a router */ 
	public link_cost[] linkcost; 
/* we assume that at most NBR_ROUTER links are attached to each router */
} 