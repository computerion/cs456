import java.io.*;  
import java.net.*;  
import java.util.*;
import java.nio.*;

public class router {

	public static final int NBR_ROUTER = 5;
	public static final int DIST_INFINITE = 2147483647;
	private static int routerId;
	private static link[] links;
	private static link[] routingTable;
	private static link_cost[][] adjacency;

	private static DatagramSocket socket;
	private static int routerPort;
	private static InetAddress hostAddress;
	private static int hostPort;

	private static PrintWriter routerLog;

	private static ArrayList<pkt_LSPDU> lspdus;

	public static void main(String[] args) throws Exception  {
		init(args);
		sendInit();
		recieveCircuitDB();
		sendHellos();

		while (true) {
			listen();
		}
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
	    routingTable = new link[NBR_ROUTER];
	    lspdus = new ArrayList<pkt_LSPDU>();
	    adjacency = new link_cost[NBR_ROUTER][NBR_ROUTER];

	    for (int i=0; i < NBR_ROUTER; i++) {
	    	for (int j=0; j < NBR_ROUTER; j++) {
	    		adjacency[i][j] = new link_cost(-1, DIST_INFINITE);
	    	}
	    	adjacency[i][i] = new link_cost(-1, 0);
	    }

	    for(int i=0; i<routingTable.length; i++) {
	    	routingTable[i] = new link(i+1, -1, DIST_INFINITE);
	    }

	    routerLog = new PrintWriter(new FileWriter(String.format("router%03d.log", routerId)), true);
	  }

	private static void recieveCircuitDB() throws Exception {
		byte[] data = new byte[512];
	    DatagramPacket receivePacket = new DatagramPacket(data, data.length);  
	    socket.receive(receivePacket);
	    circuit_DB circutDB = circuit_DB.getData(receivePacket.getData());

	    links = new link[circutDB.nbr_link];
	    for (int i=0; i<circutDB.nbr_link; i++) {
	    	link l = new link(circutDB.linkcost[i]);
	    	links[i] = l;
	    	pkt_LSPDU lspdu = new pkt_LSPDU(routerId, l.link_id, l.cost);
	    	lspdus.add(lspdu);
	    }
	    routerLog.printf("R%03d receives a CIRCUIT_DB: nbr_link %03d\n", routerId, circutDB.nbr_link);
	    routerLog.flush();

	    logTopology();
	}

	private static void listen() throws Exception {
		byte[] data = new byte[512];
	    DatagramPacket receivePacket = new DatagramPacket(data, data.length);  
	    socket.receive(receivePacket);
	    int packetSize = receivePacket.getLength();
	    if (packetSize == pkt_HELLO.SIZE) {
	    	pkt_HELLO pkt = pkt_HELLO.getData(receivePacket.getData());
	    	handleHello(pkt);
	    } else if (packetSize == pkt_LSPDU.SIZE) {
	    	pkt_LSPDU pkt = pkt_LSPDU.getData(receivePacket.getData());
	    	handleLSPDU(pkt);
	    }
	}

	private static void handleHello(pkt_HELLO pkt) throws Exception {
		int router_id = pkt.router_id;
		int link_id = pkt.link_id;
		for (int i=0; i<lspdus.size(); i++) {
			pkt_LSPDU lspdu_pkt = lspdus.get(i);
			sendLSPDU(link_id, lspdu_pkt);
		}
		for (int i=0; i<links.length; i++) {
			if (links[i].link_id == link_id) {
				links[i].reciever_router_id = router_id;
			}
		}
		routerLog.printf("R%03d receives a HELLO: router_id %03d link_id %03d\n", routerId, pkt.router_id, pkt.link_id);
    	routerLog.flush();
	}

	private static void handleLSPDU(pkt_LSPDU pkt) throws Exception {
		for (int i=0; i<lspdus.size(); i++) {
			if (lspdus.get(i).link_id == pkt.link_id && lspdus.get(i).router_id == pkt.router_id)  {
				return;
			}
		}

		updateRoutingTable(pkt);
		lspdus.add(pkt);
		int sender_link_id = pkt.link_id;
		for (int i=0; i < links.length; i++) {
			if (links[i].link_id != sender_link_id) {
 				sendLSPDU(links[i].link_id, pkt);
			}
		}

		routerLog.printf("R%03d receives an LS PDU: sender %03d, router_id %03d, link_id %03d, cost %03d, via %03d\n", routerId, pkt.sender, pkt.router_id, pkt.link_id, pkt.cost, pkt.via);
    	routerLog.flush();
	}

	private static void updateRoutingTable(pkt_LSPDU pkt) {
		Boolean adjacencyMatrixUpdated = false;
		for (int i=0; i<lspdus.size(); i++) {
			pkt_LSPDU seen_pkt = lspdus.get(i);
			if (pkt.link_id == seen_pkt.link_id) {
				adjacencyMatrixUpdated = true;
				adjacency[pkt.router_id-1][seen_pkt.router_id-1] = new link_cost(pkt.link_id, pkt.cost);
				adjacency[seen_pkt.router_id-1][pkt.router_id-1] = new link_cost(pkt.link_id, pkt.cost);
			}
		}

		logTopology();

		if (adjacencyMatrixUpdated) {
			updateShortestPath();
		}
	}

	private static void logTopology() {
		for (int i=0; i < NBR_ROUTER; i++) {
			ArrayList<pkt_LSPDU> edges = new ArrayList<pkt_LSPDU>();
			for (int j=0; j<lspdus.size(); j++) {
				pkt_LSPDU lspdu_pkt = lspdus.get(j);
				if (lspdu_pkt.router_id == i + 1) {
					edges.add(lspdu_pkt);
				}
			}
			routerLog.printf("R%03d -> R%03d nbr link %03d\n", routerId, i+1, edges.size());
			for (int j=0; j<edges.size(); j++) {
				pkt_LSPDU lspdu_pkt = edges.get(j);
				routerLog.printf("R%03d -> R%03d link %03d cost %03d\n", routerId, i+1, lspdu_pkt.link_id, lspdu_pkt.cost);
			}
		}
		routerLog.flush();
	}

	private static void updateShortestPath() {
		int[] dist = new int[NBR_ROUTER];
		int[] next_router_id = new int[NBR_ROUTER];
		ArrayList<Integer> queue = new ArrayList<Integer>();
		for (int i=0; i<NBR_ROUTER; i++) {
			if (i != routerId - 1) {
				dist[i] = DIST_INFINITE;
			} else {
				dist[i] = 0;
			}
			next_router_id[i] = i;
			queue.add(i);
		}
		while (queue.size() > 0) {
			int min_dist = DIST_INFINITE;
			int index = 0;
			for (int i=0; i < queue.size(); i++) {
				int router = queue.get(i);
				if (min_dist > dist[router]) {
					min_dist = dist[router];
					index = i;
				}
			}
			int rid = queue.get(index);
			queue.remove(index);

			for (int i=0; i<NBR_ROUTER; i++) {
				if (adjacency[i][rid].cost < DIST_INFINITE) {
					int distance = dist[rid] + adjacency[i][rid].cost;
					if (distance < dist[i]) {
						dist[i] = distance;
						if (routerId - 1 != rid) {
							next_router_id[i] = next_router_id[rid];
						}
					}
				}
			}
		}
		for (int i=0; i<NBR_ROUTER; i++) {
			int nextId = next_router_id[i];
			link routingLink = new link(nextId, adjacency[routerId -1][nextId].link, dist[i]);
			if (dist[i] == DIST_INFINITE) {
				routerLog.printf("R%03d -> R%03d -> INF, INF\n", routerId, i + 1);
			} else if (i == routerId -1) {
				routerLog.printf("R%03d -> R%03d -> Local, 0\n", routerId, i + 1);
			} else {
				routerLog.printf("R%03d -> R%03d -> R%03d, 0\n", routerId, i + 1, nextId + 1, dist[i]);
			}
		}
		routerLog.flush();
	}

	private static void sendInit() throws Exception {
		pkt_INIT pkt = new pkt_INIT(routerId);
    	DatagramPacket sendPacket = new DatagramPacket(pkt.toByte(), pkt_INIT.SIZE, hostAddress, hostPort); 
    	socket.send(sendPacket);
    	routerLog.printf("R%03d sends an INIT: router_id %03d\n", routerId, routerId);
    	routerLog.flush();
	}

	private static void sendLSPDU(int link_id, pkt_LSPDU pkt) throws Exception {
		pkt.setDestination(routerId, link_id);
    	DatagramPacket sendPacket = new DatagramPacket(pkt.toByte(), pkt_LSPDU.SIZE, hostAddress, hostPort);
    	socket.send(sendPacket);
    	routerLog.printf("R%03d sends an LS PDU: sender %03d, router_id %03d, link_id %03d, cost %03d, via %03d\n", routerId, pkt.sender, pkt.router_id, pkt.link_id, pkt.cost, pkt.via);
    	routerLog.flush();
	}

	private static void sendHellos() throws Exception {
		for (int i=0; i<links.length; i++) {
			pkt_HELLO pkt = new pkt_HELLO(routerId, links[i].link_id);
	    	DatagramPacket sendPacket = new DatagramPacket(pkt.toByte(), pkt_HELLO.SIZE, hostAddress, hostPort); 
	    	socket.send(sendPacket);
	    	routerLog.printf("R%03d sends a HELLO: router_id %03d link_id %03d\n", routerId, routerId, links[i].link_id);
    		routerLog.flush();
		}
	}
}

class link {
	public int reciever_router_id;
	public int link_id;
	public int cost;

	public link(int reciever_router_id, int link_id, int cost) {
		this.reciever_router_id = reciever_router_id;
		this.link_id = link_id;
		this.cost = cost;
	}

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

	public pkt_LSPDU(int router_id, int link_id, int cost, int sender, int via) {
		this.sender = sender;
		this.router_id = router_id;
		this.link_id = link_id;
		this.cost = cost;
		this.via = via;
	}

	public pkt_LSPDU(int router_id, int link_id, int cost) {
		this.router_id = router_id;
		this.link_id = link_id;
		this.cost = cost;
	}

	public void setDestination(int sender, int via) {
		this.sender = sender;
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
		return new pkt_LSPDU(router_id, link_id, cost, sender, via);
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
	    }

	    return new circuit_DB(nbr_link, linkcost);
	}
} 