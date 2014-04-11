package ch.ethz.inf.vs.californium.test.lockstep;

import static ch.ethz.inf.vs.californium.coap.CoAP.Code.GET;
import static ch.ethz.inf.vs.californium.coap.CoAP.ResponseCode.CONTENT;
import static ch.ethz.inf.vs.californium.coap.CoAP.Type.ACK;
import static ch.ethz.inf.vs.californium.coap.CoAP.Type.CON;
import static ch.ethz.inf.vs.californium.coap.CoAP.Type.NON;
import static ch.ethz.inf.vs.californium.coap.CoAP.Type.RST;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import ch.ethz.inf.vs.californium.coap.CoAP.Type;
import ch.ethz.inf.vs.californium.coap.Response;
import ch.ethz.inf.vs.californium.network.config.NetworkConfig;
import ch.ethz.inf.vs.californium.network.config.NetworkConfigDefaults;
import ch.ethz.inf.vs.californium.server.Server;
import ch.ethz.inf.vs.californium.server.resources.CoapExchange;
import ch.ethz.inf.vs.californium.server.resources.ResourceBase;
import ch.ethz.inf.vs.californium.test.BlockwiseTransferTest.ServerBlockwiseInterceptor;
import ch.ethz.inf.vs.elements.UDPConnector;

public class ObserveServerSide {

private static boolean RANDOM_PAYLOAD_GENERATION = true;
	
	private Server server;
	private int serverPort = 5683;
	
	private int mid = 7000;
	
	private TestObserveResource testObsResource;
	private String respPayload;
	private Type respType;
	private int timeout = 100;
	
	private ServerBlockwiseInterceptor serverInterceptor = new ServerBlockwiseInterceptor();
	
	@Before
	public void setupServer() {
		System.out.println("\nStart "+getClass().getSimpleName());
		Logger ul = Logger.getLogger(UDPConnector.class.toString());
		ul.setLevel(Level.OFF);
		LockstepEndpoint.DEFAULT_VERBOSE = false;
		
		testObsResource = new TestObserveResource("obs");
		
		NetworkConfig config = new NetworkConfig()
			.setInt(NetworkConfigDefaults.ACK_TIMEOUT, timeout)
			.setFloat(NetworkConfigDefaults.ACK_RANDOM_FACTOR, 1.0f)
			.setInt(NetworkConfigDefaults.ACK_TIMEOUT_SCALE, 1)
			.setInt(NetworkConfigDefaults.MAX_MESSAGE_SIZE, 32)
			.setInt(NetworkConfigDefaults.DEFAULT_BLOCK_SIZE, 32);
		server = new Server(config, serverPort);
		server.add(testObsResource);
		server.getEndpoints().get(0).addInterceptor(serverInterceptor);
		server.start();
	}
	
	@After
	public void shutdownServer() {
		System.out.println();
		server.destroy();
		System.out.println("End "+getClass().getSimpleName());
	}
	
	@Test
	public void test() throws Throwable {
		try {
			
			testEstablishmentAndTimeout();
			testEstablishmentAndTimeoutWithUpdateInMiddle();
			testEstablishmentAndRejectCancellation();
			// testObserveWithBlock(); // TODO
			testNON();
			testNONWithBlock();
			testQuickChangeAndTimeout();
			
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		} catch (Throwable t) {
			System.err.println(t);
			throw t;
		}
	}
	
	private void testEstablishmentAndTimeout() throws Exception {
		System.out.println("Establish an observe relation. Cancellation after timeout");
		respPayload = generatePayload(30);
		byte[] tok = generateNextToken();
		String path = "obs";
		
		LockstepEndpoint client = createLockstepEndpoint();
		respType = null;
		client.sendRequest(CON, GET, tok, ++mid).path(path).observe(0).go();
		client.expectResponse(ACK, CONTENT, tok, mid).storeObserve("Z").payload(respPayload).go();
		Assert.assertEquals("Resource has established relation:", 1, testObsResource.getObserverCount());
		serverInterceptor.log("\nObserve relation established");
		
		// First notification
		respType = NON;
		testObsResource.change("First notification");
		client.expectResponse().type(NON).code(CONTENT).token(tok).checkObs("Z", "A").payload(respPayload).go();
		
		// Second notification
		testObsResource.change("Second notification");
		client.expectResponse().type(NON).code(CONTENT).token(tok).checkObs("A", "B").payload(respPayload).go();
		
		// Third notification
		respType = CON;
		testObsResource.change("Third notification");
		client.expectResponse().type(CON).code(CONTENT).token(tok).storeMID("MID").checkObs("B", "C").payload(respPayload).go();
		client.sendEmpty(ACK).loadMID("MID").go();
		
		// Forth notification
		respType = NON;
		testObsResource.change("Forth notification");
		client.expectResponse().type(NON).code(CONTENT).token(tok).checkObs("C", "D").payload(respPayload).go();
		
		// Fifth notification
		respType = CON;
		testObsResource.change("Fifth notification");
		client.expectResponse().type(CON).code(CONTENT).token(tok).storeMID("MID").checkObs("D", "E").payload(respPayload).go();
		serverInterceptor.log("// lost");
		client.expectResponse().type(CON).code(CONTENT).token(tok).loadMID("MID").loadObserve("E").payload(respPayload).go();
		serverInterceptor.log("// lost");
		client.expectResponse().type(CON).code(CONTENT).token(tok).loadMID("MID").loadObserve("E").payload(respPayload).go();
		serverInterceptor.log("// lost");
		client.expectResponse().type(CON).code(CONTENT).token(tok).loadMID("MID").loadObserve("E").payload(respPayload).go();
		serverInterceptor.log("// lost");
		client.expectResponse().type(CON).code(CONTENT).token(tok).loadMID("MID").loadObserve("E").payload(respPayload).go();
		serverInterceptor.log("// lost");
		
		Thread.sleep(timeout+100);
		
		Assert.assertEquals("Resource has not removed relation:", 0, testObsResource.getObserverCount());
		
		printServerLog();
	}
	
	private void testEstablishmentAndTimeoutWithUpdateInMiddle() throws Exception {
		System.out.println("Establish an observe relation. Cancellation after timeout. During the timeouts, the resource still changes.");
		respPayload = generatePayload(30);
		byte[] tok = generateNextToken();
		String path = "obs";
		
		LockstepEndpoint client = createLockstepEndpoint();
		respType = null;
		client.sendRequest(CON, GET, tok, ++mid).path(path).observe(0).go();
		client.expectResponse(ACK, CONTENT, tok, mid).storeObserve("A").payload(respPayload).go();
		Assert.assertEquals("Resource has established relation:", 1, testObsResource.getObserverCount());
		serverInterceptor.log("\nObserve relation established");
		
		// First notification
		respType = CON;
		testObsResource.change("First notification "+generatePayload(10));
		client.expectResponse().type(CON).code(CONTENT).token(tok).storeMID("MID").checkObs("A", "B").payload(respPayload).go();
		serverInterceptor.log("// lost ");
		client.expectResponse().type(CON).code(CONTENT).token(tok).loadMID("MID").loadObserve("B").payload(respPayload).go();
		serverInterceptor.log("// lost (1. retransmission)");
		
		// Resource changes and sends next CON which will be transmitted after the former has timeouted
		testObsResource.change("Second notification "+generatePayload(10));
		client.expectResponse().type(CON).code(CONTENT).token(tok).storeMID("MID").checkObs("B", "C").payload(respPayload).go();
		serverInterceptor.log("// lost (2. retransmission)");
		
		// Resource changes. Even though the next notification is a NON it becomes
		// a CON because it replaces the retransmission of the former CON control notifiation
		respType = NON;
		testObsResource.change("Third notification "+generatePayload(10));
		client.expectResponse().type(CON).code(CONTENT).token(tok).storeMID("MID").checkObs("C", "D").payload(respPayload).go();
		serverInterceptor.log("// lost (3. retransmission)");
		
		client.expectResponse().type(CON).code(CONTENT).token(tok).loadMID("MID").loadObserve("D").payload(respPayload).go();
		serverInterceptor.log("// lost (4. retransmission)");
		
		Thread.sleep(timeout+100);
		Assert.assertEquals("Resource has not removed relation:", 0, testObsResource.getObserverCount());
		
		printServerLog();
	}
	
	private void testEstablishmentAndRejectCancellation() throws Exception {
		System.out.println("Establish an observe relation. Cancellation due to a reject from the client");
		respPayload = generatePayload(30);
		byte[] tok = generateNextToken();
		String path = "obs";
		
		LockstepEndpoint client = createLockstepEndpoint();
		respType = null;
		client.sendRequest(CON, GET, tok, ++mid).path(path).observe(0).go();
		client.expectResponse(ACK, CONTENT, tok, mid).storeObserve("A").payload(respPayload).go();
		Assert.assertEquals("Resource has established relation:", 1, testObsResource.getObserverCount());
		serverInterceptor.log("\nObserve relation established");
		
		// First notification
		respType = CON;
		testObsResource.change("First notification "+generatePayload(10));
		client.expectResponse().type(CON).code(CONTENT).token(tok).storeMID("MID").checkObs("A", "B").payload(respPayload).go();
		serverInterceptor.log("// lost ");
		client.expectResponse().type(CON).code(CONTENT).token(tok).loadMID("MID").loadObserve("B").payload(respPayload).go();
		
		System.out.println("Reject notification");
		client.sendEmpty(RST).loadMID("MID").go();
		
		Thread.sleep(100);
		Assert.assertEquals("Resource has not removed relation:", 0, testObsResource.getObserverCount());
		printServerLog();
	}
	
	private void testObserveWithBlock() throws Exception {
		System.out.println("Observe with blockwise");
		respPayload = generatePayload(80);
		byte[] tok = generateNextToken();
		String path = "obs";
		
		// Establish relation
		LockstepEndpoint client = createLockstepEndpoint();
		respType = null;
		client.sendRequest(CON, GET, tok, ++mid).path(path).observe(0).go();
		client.expectResponse(ACK, CONTENT, tok, mid).storeObserve("A").block2(0, true, 32).payload(respPayload, 0, 32).go();
		
		byte[] tok2 = generateNextToken();
		client.sendRequest(CON, GET, tok2, ++mid).path(path).block2(1, false, 32).go();
		client.expectResponse(ACK, CONTENT, tok2, mid).block2(1, true, 32).payload(respPayload, 32, 64).go();
		client.sendRequest(CON, GET, tok2, ++mid).path(path).block2(2, false, 32).go();
		client.expectResponse(ACK, CONTENT, tok2, mid).block2(2, false, 32).payload(respPayload, 64, 80).go(); 
		
//		// First notification
		serverInterceptor.log("\n   === changed ===");
		respType = CON;
		testObsResource.change(generatePayload(80));
		client.expectResponse().type(CON).code(CONTENT).token(tok).storeMID("MID").checkObs("A", "B").block2(0, true, 32).payload(respPayload, 0, 32).go();
		client.sendEmpty(ACK).loadMID("MID").go();
		
		Thread.sleep(100);
		testObsResource.change(generatePayload(80));
		byte[] tok3 = generateNextToken();
		client.sendRequest(CON, GET, tok3, ++mid).path(path).block2(1, false, 32).go();
		client.expectResponse(ACK, CONTENT, tok3, mid).block2(1, true, 32).payload(respPayload, 32, 64).go();
		client.sendRequest(CON, GET, tok3, ++mid).path(path).block2(2, false, 32).go();
		client.expectResponse(ACK, CONTENT, tok3, mid).block2(2, false, 32).payload(respPayload, 64, 80).go(); 
		
		
		Thread.sleep(timeout+100);
//		Assert.assertEquals("Resource has not removed relation:", 0, testObsResource.getObserverCount());
		printServerLog();
	}
	
	private LockstepEndpoint createLockstepEndpoint() {
		try {
			LockstepEndpoint endpoint = new LockstepEndpoint();
			endpoint.setDestination(new InetSocketAddress(InetAddress.getLocalHost(), serverPort));
			return endpoint;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	private void printServerLog() {
		System.out.println(serverInterceptor.toString());
		serverInterceptor.clear();
	}
	
	private static int currentToken = 10;
	private static byte[] generateNextToken() {
		return b(++currentToken);
	}
	
	private static byte[] b(int... is) {
		byte[] bytes = new byte[is.length];
		for (int i=0;i<bytes.length;i++)
			bytes[i] = (byte) is[i];
		return bytes;
	}
	
	private static String generatePayload(int length) {
		StringBuffer buffer = new StringBuffer();
		if (RANDOM_PAYLOAD_GENERATION) {
			Random rand = new Random();
			while(buffer.length() < length) {
				buffer.append(rand.nextInt());
			}
		} else { // Deterministic payload
			int n = 1;
			while(buffer.length() < length) {
				buffer.append(n++);
			}
		}
		return buffer.substring(0, length);
	}
	
	// All tests are made with this resource
	private class TestObserveResource extends ResourceBase {
		
		public TestObserveResource(String name) { 
			super(name);
			setObservable(true);
		}
		
		public void handleGET(CoapExchange exchange) {
			Response response = new Response(CONTENT);
			response.setType(respType);
			response.setPayload(respPayload);
			exchange.respond(response);
		}
		
		public void change(String newPayload) {
			System.out.println("Resource changed: "+newPayload);
			respPayload = newPayload;
			changed();
		}
	}
	
	private void testNON() throws Exception {
		System.out.println("Establish an observe relation and receive NON notifications");
		respPayload = generatePayload(30);
		byte[] tok = generateNextToken();
		String path = "obs";
		
		LockstepEndpoint client = createLockstepEndpoint();
		respType = null;
		client.sendRequest(NON, GET, tok, ++mid).path(path).observe(0).go();
		client.expectResponse().type(NON).code(CONTENT).token(tok).storeObserve("A").payload(respPayload).go();
		Assert.assertEquals("Resource has established relation:", 1, testObsResource.getObserverCount());
		serverInterceptor.log("\nObserve relation established");
		
		// First notification
		testObsResource.change("First notification "+generatePayload(10));
		client.expectResponse().type(NON).code(CONTENT).token(tok).storeMID("MID").checkObs("A", "B").payload(respPayload).go();
		
		respType = CON;
		testObsResource.change("Second notification "+generatePayload(10));
		client.expectResponse().type(CON).code(CONTENT).token(tok).storeMID("MID").checkObs("B", "C").payload(respPayload).go();

		/* In transit */ {
			respType = NON;
			testObsResource.change("Third notification "+generatePayload(10));
			// resource postpones third notification
		}
		client.sendEmpty(ACK).loadMID("MID").go();
		
		// resource releases third notification
		client.expectResponse().type(NON).code(CONTENT).token(tok).storeMID("MID").checkObs("C", "D").payload(respPayload).go();

		System.out.println("Reject notification");
		client.sendEmpty(RST).loadMID("MID").go();
		
		Thread.sleep(100);
		Assert.assertEquals("Resource has not removed relation:", 0, testObsResource.getObserverCount());
		printServerLog();
	}
	
	private void testNONWithBlock() throws Exception {
		System.out.println("Establish an observe relation and receive NON notifications");
		respPayload = generatePayload(30);
		byte[] tok = generateNextToken();
		String path = "obs";
		
		LockstepEndpoint client = createLockstepEndpoint();
		respType = null;
		client.sendRequest(NON, GET, tok, ++mid).path(path).observe(0).block2(0, false, 32).go();
		client.expectResponse().type(NON).code(CONTENT).token(tok).storeObserve("A").payload(respPayload).go();
		Assert.assertEquals("Resource has established relation:", 1, testObsResource.getObserverCount());
		serverInterceptor.log("\nObserve relation established");
		
		// First notification
		testObsResource.change("First notification "+generatePayload(10));
		client.expectResponse().type(NON).code(CONTENT).token(tok).storeMID("MID").checkObs("A", "B").payload(respPayload).go();
		
		respType = CON;
		testObsResource.change("Second notification "+generatePayload(10));
		client.expectResponse().type(CON).code(CONTENT).token(tok).storeMID("MID").checkObs("B", "C").payload(respPayload).go();

		/* In transit */ {
			respType = NON;
			testObsResource.change("Third notification "+generatePayload(10));
			// resource postpones third notification
		}
		client.sendEmpty(ACK).loadMID("MID").go();
		
		// resource releases third notification
		client.expectResponse().type(NON).code(CONTENT).token(tok).storeMID("MID").checkObs("C", "D").payload(respPayload).go();

		testObsResource.change("Fourth notification "+generatePayload(10));
		client.expectResponse().type(NON).code(CONTENT).token(tok).storeMID("MID").checkObs("C", "D").payload(respPayload).go();
		
		System.out.println("Reject notification");
		client.sendEmpty(RST).loadMID("MID").go();
		
		Thread.sleep(100);
		Assert.assertEquals("Resource has not removed relation:", 0, testObsResource.getObserverCount());
		printServerLog();
	}
	
	private void testQuickChangeAndTimeout() throws Exception {
		System.out.println("Establish an observe relation to a quickly changing resource and do no longer respond");
		respPayload = generatePayload(20);
		byte[] tok = generateNextToken();
		String path = "obs";
		
		LockstepEndpoint client = createLockstepEndpoint();
		respType = null;
		client.sendRequest(CON, GET, tok, ++mid).path(path).observe(0).go();
		client.expectResponse(ACK, CONTENT, tok, mid).storeObserve("A").payload(respPayload).go();
		Assert.assertEquals("Resource has established relation:", 1, testObsResource.getObserverCount());
		serverInterceptor.log("\nObserve relation established");
		
		// First notification
		testObsResource.change("First notification "+generatePayload(10));
		client.expectResponse().type(NON).code(CONTENT).token(tok).storeMID("MID").checkObs("A", "B").payload(respPayload).go();
		
		// Now client crashes and no longer responds
		
		respType = CON;
		testObsResource.change("Second notification "+generatePayload(10));
		client.expectResponse().type(CON).code(CONTENT).token(tok).checkObs("B", "C").payload(respPayload).go();

		respType = NON;
		testObsResource.change("NON notification 1 "+generatePayload(10));
		client.expectResponse().type(CON).code(CONTENT).token(tok).checkObs("B", "B").payload(respPayload).go();

		testObsResource.change("NON notification 2 "+generatePayload(10));
		client.expectResponse().type(CON).code(CONTENT).token(tok).checkObs("B", "B").payload(respPayload).go();

		testObsResource.change("NON notification 3 "+generatePayload(10));
		client.expectResponse().type(CON).code(CONTENT).token(tok).checkObs("B", "B").payload(respPayload).go();

		testObsResource.change("NON notification 4 "+generatePayload(10));
		client.expectResponse().type(CON).code(CONTENT).token(tok).checkObs("B", "B").payload(respPayload).go();

		serverInterceptor.log("\n   server cancels the relation");
		
		Thread.sleep(timeout+100);
		Assert.assertEquals("Resource has not removed relation:", 0, testObsResource.getObserverCount());
		printServerLog();
	}
	
}
