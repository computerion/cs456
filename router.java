import java.io.*;  
import java.net.*;  
import java.util.*;
import java.nio.*;

public class router {

	public static final int NBR_ROUTER = 5;
	private static int routerId;
	private static link[] links;

	private static DatagramSocket socket;
	private static int routerPort;
	private static InetAddress hostAddress;
	private static int hostPort;

	private static PrintWriter routerLog;

	public static void main(String[] args) throws Exception  {
		init(args);
		sendInit();
		recieveCircuitDB();
		sendHellos();
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
		pkt_INIT pkt = new pkt_INIT(routerId);
    	DatagramPacket sendPacket = new DatagramPacket(pkt.toByte(), pkt_INIT.SIZE, hostAddress, hostPort); 
    	socket.send(sendPacket);
	}

	private static void recieveCircuitDB() throws Exception {
		byte[] data = new byte[512];
	    DatagramPacket receivePacket = new DatagramPacket(data, data.length);  
	    socket.receive(receivePacket);
	    circuit_DB circutDB = circuit_DB.getData(receivePacket.getData());

	    links = new link[circutDB.nbr_link];
	    for (int i=0; i<circutDB.nbr_link; i++) {
	    	links[i] = new link(circutDB.linkcost[i]);
	    }
	}

	private static void sendHellos() throws Exception {
		for (int i=0; i<links.length; i++) {
			pkt_HELLO pkt = new pkt_HELLO(routerId, links[i].link_id);
	    	DatagramPacket sendPacket = new DatagramPacket(pkt.toByte(), pkt_HELLO.SIZE, hostAddress, hostPort); 
	    	socket.send(sendPacket);
		}
	}

	private static void listen() throws Exception {
		byte[] data = new byte[512];
	    DatagramPacket receivePacket = new DatagramPacket(data, data.length);  
	    socket.receive(receivePacket);
	    System.out.println("Recieved: " + receivePacket.getLength());
	}
}

class link {
	public int reciever_router_id;
	public int link_id;
	public int cost;

	public link(link_cost lc) {
		link_id = lc.link;
		cost = lc.cost;
	}
}

class pkt_HELLO { 
	public static final int SIZE = 8;
	public int router_id; /* id of the router who sends the HELLO PDU */ 
	public int link_id; /* id of the link through which it is sent */

	public pkt_HELLO(int router_id, int link_id) {
		this.router_id = router_id;
		this.link_id = link_id;
	}

	public byte[] toByte() {
		ByteBuffer buffer = ByteBuffer.allocate(SIZE);
		buffer.order(ByteOrder.LITTLE_ENDIAN);
		buffer.putInt(router_id);
		buffer.putInt(link_id);
		return buffer.array();
	}
	
	public static pkt_HELLO getData(byte[] UDPdata) throws Exception {
		ByteBuffer buffer = ByteBuffer.wrap(UDPdata);
		buffer.order(ByteOrder.LITTLE_ENDIAN);
		int router_id = buffer.getInt();
		int link_id = buffer.getInt();
		return new pkt_HELLO(router_id, link_id);
	}
} 

class pkt_LSPDU {
	public static final int SIZE = 20;
	public int sender; /* sender of the LS PDU */ 
	public int router_id; /* router id */ 
	public int link_id; /* link id */ 
	public int cost; /* cost of the link */ 
	public int via; /* id of the link through which the LS PDU is sent */

	public pkt_LSPDU(int sender, int router_id, int link_id, int cost, int via) {
		this.sender = sender;
		this.router_id = router_id;
		this.link_id = link_id;
		this.cost = cost;
		this.via = via;
	}

	public byte[] toByte() {
		ByteBuffer buffer = ByteBuffer.allocate(SIZE);
		buffer.order(ByteOrder.LITTLE_ENDIAN);
		buffer.putInt(sender);
		buffer.putInt(router_id);
		buffer.putInt(link_id);
		buffer.putInt(cost);
		buffer.putInt(via);
		return buffer.array();
	}
	
	public static pkt_LSPDU getData(byte[] UDPdata) throws Exception {
		ByteBuffer buffer = ByteBuffer.wrap(UDPdata);
		buffer.order(ByteOrder.LITTLE_ENDIAN);
		int sender = buffer.getInt();
		int router_id = buffer.getInt();
		int link_id = buffer.getInt();
		int cost = buffer.getInt();
		int via = buffer.getInt();
		return new pkt_LSPDU(sender, router_id, link_id, cost, via);
	}
}

class pkt_INIT { 
	public static final int SIZE = 4;
	public int router_id; /* id of the router that send the INIT PDU */

	public pkt_INIT(int router_id) {
		this.router_id = router_id;
	}

	public byte[] toByte() {
		ByteBuffer buffer = ByteBuffer.allocate(4);
		buffer.order(ByteOrder.LITTLE_ENDIAN);
		buffer.putInt(router_id);
		return buffer.array();
	}
	
	public static pkt_INIT getData(byte[] UDPdata) throws Exception {
		ByteBuffer buffer = ByteBuffer.wrap(UDPdata);
		buffer.order(ByteOrder.LITTLE_ENDIAN);
		int router_id = buffer.getInt();
		return new pkt_INIT(router_id);
	}
} 

class link_cost { 
	public int link; /* link id */ 
	public int cost; /* associated cost */

	public link_cost(int link, int cost) {
		this.link = link;
		this.cost = cost;
	}
} 

class circuit_DB { 
	public int nbr_link; /* number of links attached to a router */ 
	public link_cost[] linkcost; /* we assume that at most NBR_ROUTER links are attached to each router */

	public circuit_DB(int nbr_link, link_cost[] linkcost) {
		this.nbr_link = nbr_link;
		this.linkcost = linkcost;
	}

	public static circuit_DB getData(byte[] UDPdata) throws Exception {
		ByteBuffer buffer = ByteBuffer.wrap(UDPdata);
		buffer.order(ByteOrder.LITTLE_ENDIAN);

	    int nbr_link = buffer.getInt();
	    link_cost[] linkcost = new link_cost[nbr_link];

	    for (int i=0; i < nbr_link; i++) {
	    	int link_id = buffer.getInt();
	    	int cost = buffer.getInt();
	    	linkcost[i] = new link_cost(link_id, cost);
	    	System.out.println(linkcost[i].link + " " + linkcost[i].cost);
	    }

	    return new circuit_DB(nbr_link, linkcost);
	}
} 