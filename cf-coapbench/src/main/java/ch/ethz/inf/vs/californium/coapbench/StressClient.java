package ch.ethz.inf.vs.californium.coapbench;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import ch.ethz.inf.vs.californium.producer.EcoMessageProducer;
import ch.ethz.inf.vs.elements.RawData;

/**
 * Sends a burst of requests to the server. After a burst it pauses. As soon as
 * half of the requests have been answered, another burst starts.
 */
public class StressClient {

	public static final String HOST = null; //ClientSlave.MASTER_ADDRESS;
//	public static final String HOST = "169.254.12.143";
	public static final int PORT = 5683;
	public static final String TARGET = "hello";
	public static final int OCCUPATION = 20000;
	public static final int CLIENT_COUNT = 1;
	
	private static InetAddress destination;
	private static Meter meter;
	
	private DatagramSocket socket;
	private EcoMessageProducer producer;
	
	private static boolean running;
	
	public StressClient() throws Exception {
		this.producer = new EcoMessageProducer("coap://"+HOST+":"+PORT+"/"+TARGET);
		this.socket = new DatagramSocket();
		socket.setReceiveBufferSize(10*1000*1000);
		socket.setSendBufferSize(10*1000*1000);
	}
	
	public void start() {
		new Thread("Receiver") {
			public void run() { receiveResponses(); } }.start();
		new Thread("Sender") {
			public void run() { sendRequests(); } }.start();
	}
		
	public void sendRequests() {
		try {
			System.out.println("Send requests to "+destination+":"+PORT);
			while (running) {
				RawData data = producer.next();
				DatagramPacket p = new DatagramPacket(data.getBytes(), data.getSize());
				p.setAddress(destination);
				p.setPort(PORT);
				socket.send(p);
				meter.requested();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void receiveResponses() {
		try {
			byte[] buf = new byte[100];
			while (running) {
				DatagramPacket p = new DatagramPacket(buf, buf.length);
				socket.receive(p);
				meter.responded();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) throws Exception {
		running = true;
		destination = InetAddress.getByName(HOST);
		meter = new Meter(OCCUPATION, CLIENT_COUNT);
		StressClient[] stresser = new StressClient[CLIENT_COUNT];
		for (int i=0;i<CLIENT_COUNT;i++)
			stresser[i] = new StressClient();
		
		for (int i=0;i<CLIENT_COUNT;i++)
			stresser[i].start();
	}
	
	public static void stop() {
		try {
			running = false;
			Thread.sleep(100);
			meter.resume();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
