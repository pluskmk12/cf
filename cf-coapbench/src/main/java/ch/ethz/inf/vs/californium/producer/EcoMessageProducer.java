package ch.ethz.inf.vs.californium.producer;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;

import ch.ethz.inf.vs.californium.coap.CoAP.Code;
import ch.ethz.inf.vs.californium.coap.CoAP.Type;
import ch.ethz.inf.vs.californium.coap.Request;
import ch.ethz.inf.vs.californium.network.serialization.Serializer;
import ch.ethz.inf.vs.elements.RawData;

/**
 * Produces requests in form of {@link RawData}, i.e. byte arrays. This produces
 * is rather economic because it creates all 65000 requests (with different
 * MIDs) once and only changes the port after all requests have been used once.
 * This producer is able to produce an infinit amount of requests but only needs
 * the memory for 65000 of them. However, setting the port needs some
 * computation and might cause worse cache-locality as with
 * {@link MessageProducer}.
 */
public class EcoMessageProducer implements Iterator<RawData> {

	private InetAddress address;

	private short[] ports;

	private ArrayList<byte[]> messages;
	private int ptr_message = 0;
	private int ptr_port = 0;
	
	private int counter;
	private int amount;

	public EcoMessageProducer(String targetURI){
		this(targetURI, Integer.MAX_VALUE);
	}
	
	public EcoMessageProducer(String targetURI, int amount) {
		this.amount = amount;
		try {
			
//			address = InetAddress.getByName("192.168.1.33");

			ports = new short[1 << 16];
			ArrayList<Short> ps = new ArrayList<Short>(ports.length);
			for (int i = 0; i < (1 << 16); i++)
				ps.add((short) i);
			Collections.shuffle(ps);
			for (int i = 0; i < (1 << 16); i++)
				ports[i] = ps.get(i);
			Collections.shuffle(Arrays.asList(ports));

			Serializer serializer = new Serializer();
			messages = new ArrayList<byte[]>(1 << 16);
			for (int i = 0; i < 1 << 16; i++) {
				Request request = new Request(Code.GET);
				request.setType(Type.NON);
				request.setToken(new byte[0]);
				request.setMID(i);
				request.setURI(targetURI);
				byte[] bytes = serializer.serialize(request).getBytes();
				messages.add(bytes);
			}
//			Collections.shuffle(messages);

		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public boolean hasNext() {
		return counter < amount;
	}

	@Override
	public RawData next() {
		RawData raw = new RawData(
				messages.get(ptr_message), address,ports[ptr_port]);
		if (++ptr_message >= 1 << 16) {
			ptr_message = 0;
			ptr_port++;
		}
		counter++;
		return raw;
	}

	@Override
	public void remove() { }

}
