package ch.ethz.inf.vs.californium;

import java.net.URI;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import ch.ethz.inf.vs.californium.coap.BlockOption;
import ch.ethz.inf.vs.californium.coap.CoAP.Type;
import ch.ethz.inf.vs.californium.coap.LinkFormat;
import ch.ethz.inf.vs.californium.coap.MediaTypeRegistry;
import ch.ethz.inf.vs.californium.coap.MessageObserverAdapter;
import ch.ethz.inf.vs.californium.coap.Request;
import ch.ethz.inf.vs.californium.coap.Response;
import ch.ethz.inf.vs.californium.network.Endpoint;
import ch.ethz.inf.vs.californium.network.config.NetworkConfig;
import ch.ethz.inf.vs.californium.network.config.NetworkConfigDefaults;
import ch.ethz.inf.vs.californium.observe.ObserveNotificationOrderer;

/**
 * The Class CoapClient.
 */
public class CoapClient {

	/** The logger. */
	private static final Logger LOGGER = Logger.getLogger(CoapClient.class.getCanonicalName());
	
	/** The timeout. */
	private long timeout = NetworkConfig.getStandard().getLong(NetworkConfigDefaults.MAX_TRANSMIT_WAIT);
	
	/** The destination URI */
	private String uri;
	
	/** The type used for requests (CON is default) */
	private Type type = Type.CON;
	
	private int blockwise = 0;
	
	/** The executor. */
	private Executor executor;
	
	/** The endpoint. */
	private Endpoint endpoint;
	
	/**
	 * Constructs a new CoapClient that has no destination URI yet.
	 */
	public CoapClient() {
		this("");
	}
	
	/**
	 * Constructs a new CoapClient that sends requests to the specified URI.
	 *
	 * @param uri the uri
	 */
	public CoapClient(String uri) {
		this.uri = uri;
	}
	
	/**
	 * Constructs a new CoapClient that sends request to the specified URI.
	 * 
	 * @param uri the uri
	 */
	public CoapClient(URI uri) {
		this(uri.toString());
	}
	
	/**
	 * Constructs a new CoapClient with the specified scheme, host, port and 
	 * path as URI.
	 *
	 * @param scheme the scheme
	 * @param host the host
	 * @param port the port
	 * @param path the path
	 */
	public CoapClient(String scheme, String host, int port, String... path) {
		StringBuilder builder = new StringBuilder()
			.append(scheme).append("://").append(host).append(":").append(port);
		for (String element:path)
			builder.append("/").append(element);
		this.uri = builder.toString();
	}
	
	/**
	 * Gets the timeout.
	 *
	 * @return the timeout
	 */
	public long getTimeout() {
		return timeout;
	}
	
	/**
	 * Sets the timeout how long synchronous method calls will wait until they
	 * give up and return anyways. The value 0 is equal to infinity.
	 *
	 * @param timeout the timeout
	 * @return the CoAP client
	 */
	public CoapClient setTimeout(long timeout) {
		this.timeout = timeout;
		return this;
	}

	/**
	 * Gets the destination URI of this client.
	 *
	 * @return the uri
	 */
	public String getURI() {
		return uri;
	}

	/**
	 * Sets the destination URI of this client.
	 *
	 * @param uri the uri
	 * @return the CoAP client
	 */
	public CoapClient setURI(String uri) {
		this.uri = uri;
		return this;
	}
	
	/**
	 * Sets a single-threaded executor to this client. All handlers will be
	 * invoked by this executor.
	 *
	 * @return the CoAP client
	 */
	public CoapClient useExecutor() {
		this.executor = Executors.newSingleThreadExecutor();
		return this;
	}

	/**
	 * Gets the executor of this client.
	 *
	 * @return the executor
	 */
	public Executor getExecutor() {
		if (executor == null)
			synchronized(this) {
			if (executor == null)
				executor = Executors.newSingleThreadExecutor();
		}
		return executor;
	}

	/**
	 * Sets the executor to this client. All handlers will be invoked by this
	 * executor.
	 *
	 * @param executor the executor
	 * @return the CoAP client
	 */
	public CoapClient setExecutor(Executor executor) {
		this.executor = executor;
		return this;
	}

	/**
	 * Gets the endpoint this client uses.
	 *
	 * @return the endpoint
	 */
	public Endpoint getEndpoint() {
		return endpoint;
	}

	/**
	 * Sets the endpoint this client is supposed to use.
	 *
	 * @param endpoint the endpoint
	 * @return the CoAP client
	 */
	public CoapClient setEndpoint(Endpoint endpoint) {
		this.endpoint = endpoint;
		return this;
	}
	
	/**
	 * Let the client use Confirmable requests.
	 * 
	 * @return the CoAP client
	 */
	public CoapClient useCONs() {
		this.type = Type.CON;
		return this;
	}
	
	/**
	 * Let the client use Non-Confirmable requests.
	 * 
	 * @return the CoAP client
	 */
	public CoapClient useNONs() {
		this.type = Type.NON;
		return this;
	}

	/**
	 * Let the client use early negotiation for the blocksize
	 * (16, 32, 64, 128, 256, 512, or 1024). Other values will
	 * be matched to the closest logarithm dualis.
	 * 
	 * @return the CoAP client
	 */
	public CoapClient useEarlyNegotiation(int size) {
		this.blockwise = size;
		return this;
	}
	
	/**
	 * Let the client use late negotiation for the block size (default).
	 * 
	 * @return the CoAP client
	 */
	public CoapClient useLateNegotiation() {
		this.blockwise = 0;
		return this;
	}

	/**
	 * Performs a CoAP ping using the default timeout for requests.
	 * 
	 * @return success of the ping
	 */
	public boolean ping() {
		return ping(this.timeout);
	}

	/**
	 * Performs a CoAP ping and gives up after the given number of milliseconds.
	 * 
	 * @param timeout the time to wait for a pong in ms
	 * @return success of the ping
	 */
	public boolean ping(long timeout) {
		try {
			Request request = new Request(null);
			request.setType(Type.CON);
			request.setToken(new byte[0]);
			request.setURI(uri);
			request.send().waitForResponse(5000);
			request.cancel();
			return request.isRejected();
		} catch (InterruptedException e) {
			// waiting was interrupted, which is fine
		}
		return false;
	}

	public Set<WebLink> discover() {
		return discover(null);
	}
	
	public Set<WebLink> discover(String query) {
		Request discover = Request.newGet();
		discover.setURI(uri);
		discover.getOptions().clearURIPaths().clearURIQuery().setURIPath("/.well-known/core");
		if (query!=null) {
			discover.getOptions().setURIQuery(query);
		}
		CoapResponse links = synchronous(discover);
		
		// check if Link Format
		if (links.getOptions().getContentFormat()!=MediaTypeRegistry.APPLICATION_LINK_FORMAT)
			return Collections.emptySet();
		
		// parse and return
		return LinkFormat.parse(links.getResponseText());
	}
	
	// Synchronous GET
	
	/**
	 * Sends a GET request and blocks until the response is available.
	 * 
	 * @return the CoAP response
	 */
	public CoapResponse get() {
		return synchronous(Request.newGet().setURI(uri));
	}
	
	/**
	 * Sends a GET request with the specified Accept option and blocks
	 * until the response is available.
	 * 
	 * @param accept the Accept option
	 * @return the CoAP response
	 */
	public CoapResponse get(int accept) {
		return synchronous(accept(Request.newGet().setURI(uri), accept));
	}
	
	// Asynchronous GET
	
	/**
	 * Sends a GET request and invokes the specified handler when a response
	 * arrives.
	 *
	 * @param handler the Response handler
	 */
	public void get(CoapHandler handler) {
		asynchronous(Request.newGet().setURI(uri), handler);
	}
	
	/**
	 * Sends  aGET request with the specified Accept option and invokes the
	 * handler when a response arrives.
	 * 
	 * @param handler the Response handler
	 * @param accept the Accept option
	 */
	public void get(CoapHandler handler, int accept) {
		asynchronous(accept(Request.newGet().setURI(uri), accept), handler);
	}
	
	// Synchronous POST
	
	/**
	 * Sends a POST request with the specified payload and the specified content
	 * format option and blocks until the response is available.
	 * 
	 * @param payload
	 *            the payload
	 * @param format
	 *            the Content-Format
	 * @return the CoAP response
	 */
	public CoapResponse post(String payload, int format) {
		return synchronous(format(Request.newPost().setURI(uri).setPayload(payload), format));
	}
	
	/**
	 * Sends a POST request with the specified payload and the specified content
	 * format option and blocks until the response is available.
	 * 
	 * @param payload
	 *            the payload
	 * @param format
	 *            the Content-Format
	 * @return the CoAP response
	 */
	public CoapResponse post(byte[] payload, int format) {
		return synchronous(format(Request.newPost().setURI(uri).setPayload(payload), format));
	}
	
	/**
	 * Sends a POST request with the specified payload, the specified content
	 * format and the specified Accept option and blocks until the response is
	 * available.
	 * 
	 * @param payload
	 *            the payload
	 * @param format
	 *            the Content-Format
	 * @param accept
	 *            the Accept option
	 * @return the CoAP response
	 */
	public CoapResponse post(String payload, int format, int accept) {
		return synchronous(accept(format(Request.newPost().setURI(uri).setPayload(payload), format), accept));
	}
	
	/**
	 * Sends a POST request with the specified payload, the specified content
	 * format and the specified Accept option and blocks until the response is
	 * available.
	 * 
	 * @param payload
	 *            the payload
	 * @param format
	 *            the Content-Format
	 * @param accept
	 *            the Accept option
	 * @return the CoAP response
	 */
	public CoapResponse post(byte[] payload, int format, int accept) {
		return synchronous(accept(format(Request.newPost().setURI(uri).setPayload(payload), format), accept));
	}
	
	// Asynchronous POST
	
	/**
	 * Sends a POST request with the specified payload and the specified content
	 * format and invokes the specified handler when a response arrives.
	 * 
	 * @param handler the Response handler
	 * @param payload the payload
	 * @param format the Content-Format
	 */
	public void post(CoapHandler handler, String payload, int format) {
		asynchronous(format(Request.newPost().setURI(uri).setPayload(payload), format), handler);
	}
	
	/**
	 * Sends a POST request with the specified payload and the specified content
	 * format and invokes the specified handler when a response arrives.
	 * 
	 * @param handler the Response handler
	 * @param payload the payload
	 * @param format the Content-Format
	 */
	public void post(CoapHandler handler, byte[] payload, int format) {
		asynchronous(format(Request.newPost().setURI(uri).setPayload(payload), format), handler);
	}
	
	/**
	 * Sends a POST request with the specified payload, the specified content
	 * format and accept and invokes the specified handler when a response
	 * arrives.
	 * 
	 * @param handler the Response handler
	 * @param payload the payload
	 * @param format the Content-Format
	 * @param accept the Accept option
	 */
	public void post(CoapHandler handler, String payload, int format, int accept) {
		asynchronous(accept(format(Request.newPost().setURI(uri).setPayload(payload), format), accept), handler);
	}
	
	/**
	 * Sends a POST request with the specified payload, the specified content
	 * format and accept and invokes the specified handler when a response
	 * arrives.
	 * 
	 * @param handler the Response handler
	 * @param payload the payload
	 * @param format the Content-Format
	 * @param accept the Accept option
	 */
	public void post(CoapHandler handler, byte[] payload, int format, int accept) {
		asynchronous(accept(format(Request.newPost().setURI(uri).setPayload(payload), format), accept), handler);
	}
	
	// Synchronous PUT
	
	/**
	 * Sends a PUT request with payload and required Content-Format and blocks
	 * until the response is available.
	 *
	 * @param payload the payload
	 * @param format the Content-Format
	 * @return the CoAP response
	 */
	public CoapResponse put(String payload, int format) {
		return synchronous(format(Request.newPut().setURI(uri).setPayload(payload), format));
	}
	
	/**
	 * Sends a PUT request with payload and required Content-Format and blocks
	 * until the response is available.
	 *
	 * @param payload the payload
	 * @param format the Content-Format
	 * @return the CoAP response
	 */
	public CoapResponse put(byte[] payload, int format) {
		return synchronous(format(Request.newPut().setURI(uri).setPayload(payload), format));
	}
	
	/**
	 * Sends a PUT request with with the specified ETags in the If-Match option
	 * and blocks until the response is available.
	 * 
	 * @param payload the payload string
	 * @param format the Content-Format
	 * @param etags the ETags for the If-Match option
	 * @return the CoAP response
	 */
	public CoapResponse putIfMatch(String payload, int format, byte[] ... etags) {
		return synchronous(ifMatch(format(Request.newPut().setURI(uri).setPayload(payload), format), etags));
	}
	
	/**
	 * Sends a PUT request with with the specified ETags in the If-Match option
	 * and blocks until the response is available.
	 * 
	 * @param payload the payload
	 * @param format the Content-Format
	 * @param etags the ETags for the If-Match option
	 * @return the CoAP response
	 */
	public CoapResponse putIfMatch(byte[] payload, int format, byte[] ... etags) {
		return synchronous(ifMatch(format(Request.newPut().setURI(uri).setPayload(payload), format), etags));
	}
	
	/**
	 * Sends a PUT request with the If-None-Match option set and blocks until
	 * the response is available.
	 * 
	 * @param payload the payload string
	 * @param format the Content-Format
	 * @return the CoAP response
	 */
	public CoapResponse putIfNoneMatch(String payload, int format) {
		return synchronous(ifNoneMatch(format(Request.newPut().setURI(uri).setPayload(payload), format)));
	}
	
	/**
	 * Sends a PUT request with the If-None-Match option set and blocks until
	 * the response is available.
	 * 
	 * @param payload the payload
	 * @param format the Content-Format
	 * @return the CoAP response
	 */
	public CoapResponse putIfNoneMatch(byte[] payload, int format) {
		return synchronous(ifNoneMatch(format(Request.newPut().setURI(uri).setPayload(payload), format)));
	}
	
	// Asynchronous PUT
	
	/**
	 * Sends a PUT request with the specified payload and the specified content
	 * format and invokes the specified handler when a response arrives.
	 * 
	 * @param handler the Response handler
	 * @param payload the payload
	 * @param format the Content-Format
	 */
	public void put(CoapHandler handler, String payload, int format) {
		asynchronous(format(Request.newPut().setURI(uri).setPayload(payload), format), handler);
	}
	
	/**
	 * Sends a PUT request with the specified payload and the specified content
	 * format and invokes the specified handler when a response arrives.
	 * 
	 * @param handler the Response handler
	 * @param payload the payload
	 * @param format the Content-Format
	 */
	public void put(CoapHandler handler, byte[] payload, int format) {
		asynchronous(format(Request.newPut().setURI(uri).setPayload(payload), format), handler);
	}

	/**
	 * 
	 * @param handler the Response handler
	 * @param payload the payload
	 * @param format the Content-Format
	 * @param etags the ETags for the If-Match option
	 */
	public void putIfMatch(CoapHandler handler, String payload, int format, byte[] ... etags) {
		asynchronous(ifMatch(format(Request.newPut().setURI(uri).setPayload(payload), format), etags), handler);
	}
	
	/**
	 * 
	 * @param handler the Response handler
	 * @param payload the payload
	 * @param format the Content-Format
	 * @param etags the ETags for the If-Match option
	 */
	public void putIfMatch(CoapHandler handler, byte[] payload, int format, byte[] ... etags) {
		asynchronous(ifMatch(format(Request.newPut().setURI(uri).setPayload(payload), format), etags), handler);
	}

	/**
	 * 
	 * @param handler the Response handler
	 * @param payload the payload
	 * @param format the Content-Format
	 */
	public void putIfNoneMatch(CoapHandler handler, byte[] payload, int format) {
		asynchronous(ifNoneMatch(format(Request.newPut().setURI(uri).setPayload(payload), format)), handler);
	}
	
	// Synchronous DELETE
	
	/**
	 * Sends a DELETE request and waits for the response.
	 *
	 * @return the CoAP response
	 */
	public CoapResponse delete() {
		return synchronous(Request.newDelete().setURI(uri));
	}
	
	/**
	 * Sends a DELETE request and invokes the specified handler when a response
	 * arrives.
	 *
	 * @param handler the response handler
	 */
	public void delete(CoapHandler handler) {
		asynchronous(Request.newDelete().setURI(uri), handler);
	}
	
	// ETag validation

	public CoapResponse validate(byte[] ... etags) {
		return synchronous(etags(Request.newGet().setURI(uri), etags));
	}

	public void validate(CoapHandler handler, byte[] ... etags) {
		asynchronous(etags(Request.newGet().setURI(uri), etags), handler);
	}
	
	// Advanced requests
	
	/**
	 * Sends an advanced synchronous request that has to be configured by the developer.
	 * 
	 * @param request the custom request
	 * @return the CoAP response
	 */
	public CoapResponse advanced(Request request) {
		request.setURI(uri);
		return synchronous(request);
	}
	
	/**
	 * Sends an advanced asynchronous request that has to be configured by the
	 * developer.
	 * @param handler the response handler
	 * @param request the custom request
	 */
	public void advanced(CoapHandler handler, Request request) {
		request.setURI(uri);
		asynchronous(request, handler);
	}
	
	// Synchronous observer
	
	/**
	 * Sends an observe request and waits until it has been established 
	 * whereupon the specified handler is invoked when a notification arrives.
	 *
	 * @param handler the Response handler
	 * @return the CoAP observe relation
	 */
	public CoapObserveRelation observeAndWait(CoapHandler handler) {
		Request request = Request.newGet().setURI(uri).setObserve();
		return observeAndWait(request, handler);
	}
	
	/**
	 * Sends an observe request with the specified Accept option and waits until
	 * it has been established whereupon the specified handler is invoked when a
	 * notification arrives.
	 *
	 * @param handler the Response handler
	 * @param accept the Accept option
	 * @return the CoAP observe relation
	 */
	public CoapObserveRelation observeAndWait(CoapHandler handler, int accept) {
		Request request = Request.newGet().setURI(uri).setObserve();
		request.getOptions().setAccept(accept);
		return observeAndWait(request, handler);
	}
	
	// Asynchronous observe
	
	/**
	 * Sends an observe request and invokes the specified handler each time
	 * a notification arrives.
	 *
	 * @param handler the Response handler
	 * @return the CoAP observe relation
	 */
	public CoapObserveRelation observe(CoapHandler handler) {
		Request request = Request.newGet().setURI(uri).setObserve();
		return observe(request, handler);
	}
	
	/**
	 * Sends an observe request with the specified Accept option and invokes the
	 * specified handler each time a notification arrives.
	 *
	 * @param handler the Response handler
	 * @param accept the Accept option
	 * @return the CoAP observe relation
	 */
	public CoapObserveRelation observe(CoapHandler handler, int accept) {
		Request request = Request.newGet().setURI(uri).setObserve();
		return observe(accept(request, accept), handler);
	}
	
	// Implementation
	
	/*
	 * Asynchronously sends the specified request and invokes the specified
	 * handler when a response arrives.
	 *
	 * @param request the request
	 * @param handler the Response handler
	 */
	private void asynchronous(Request request, CoapHandler handler) {
		request.addMessageObserver(new MessageObserverImpl(handler));
		send(request);
	}
	
	/*
	 * Synchronously sends the specified request.
	 *
	 * @param request the request
	 * @return the CoAP response
	 */
	private CoapResponse synchronous(Request request) {
		try {
			Response response = send(request).waitForResponse(getTimeout());
			if (response == null) return null;
			else return new CoapResponse(response);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}
	
	/*
	 * Sets the specified Content-Format to the specified request.
	 *
	 * @param request the request
	 * @param contentFormat the Content-Format
	 * @return the request
	 */
	private Request format(Request request, int contentFormat) {
		request.getOptions().setContentFormat(contentFormat);
		return request;
	}
	
	/*
	 * Sets the specified Accept option of the request.
	 *
	 * @param request the request
	 * @param accept the Accept option
	 * @return the request
	 */
	private Request accept(Request request, int accept) {
		request.getOptions().setAccept(accept);
		return request;
	}
	
	/*
	 * Adds the specified ETag options to the request.
	 * 
	 * @param request the request
	 * @param etags the list of ETags
	 * @return the request
	 */
	private Request etags(Request request, byte[] ... etags) {
		for (byte[] etag : etags) {
			request.getOptions().addETag(etag);
		}
		return request;
	}
	
	/*
	 * Adds the specified ETags as If-Match options to the request.
	 * 
	 * @param request the request
	 * @param etags the ETags for the If-Match option
	 * @return the request
	 */
	private Request ifMatch(Request request, byte[] ... etags) {
		for (byte[] etag : etags) {
			request.getOptions().addIfMatch(etag);
		}
		return request;
	}
	
	/*
	 * Adds the specified ETags as If-Match options to the request.
	 * 
	 * @param request the request
	 * @param etags the ETags for the If-Match option
	 * @return the request
	 */
	private Request ifNoneMatch(Request request) {
		request.getOptions().setIfNoneMatch(true);
		return request;
	}
	
	/*
	 * Sends the specified observe request and waits for the response whereupon
	 * the specified handler is invoked when a notification arrives.
	 *
	 * @param request the request
	 * @param handler the Response handler
	 * @return the CoAP observe relation
	 */
	private CoapObserveRelation observeAndWait(Request request, CoapHandler handler) {
		CoapObserveRelation relation = new CoapObserveRelation(request);
		request.addMessageObserver(new ObserveMessageObserveImpl(handler, relation));
		CoapResponse response = synchronous(request);
		if (response == null || !response.advanced().getOptions().hasObserve())
			relation.setCanceled(true);
		relation.setCurrent(response);
		return relation;
	}
	
	/*
	 * Sends the specified observe request and invokes the specified handler
	 * each time a notification arrives.
	 *
	 * @param request the request
	 * @param handler the Response handler
	 * @return the CoAP observe relation
	 */
	private CoapObserveRelation observe(Request request, CoapHandler handler) {
		CoapObserveRelation relation = new CoapObserveRelation(request);
		request.addMessageObserver(new ObserveMessageObserveImpl(handler, relation));
		send(request);
		return relation;
	}
	
	/**
	 * Sends the specified request over the endpoint of the client if one is
	 * defined or over the default endpoint otherwise.
	 *
	 * @param request the request
	 * @return the request
	 */
	protected Request send(Request request) {
		
		// use the specified message type
		request.setType(this.type);

		if (blockwise!=0) {
			request.getOptions().setBlock2(new BlockOption(BlockOption.size2Szx(this.blockwise), false, 0));
		}
		
		if (endpoint != null)
			endpoint.sendRequest(request);
		else request.send();
		return request;
	}
	
	/**
	 * The MessageObserverImpl is called when a response arrives. It wraps the
	 * response into a CoapResponse and lets the executor invoke the handler's
	 * method.
	 */
	private class MessageObserverImpl extends MessageObserverAdapter {

		/** The handler. */
		protected CoapHandler handler;
		
		/**
		 * Constructs a new message observer that calls the specified handler
		 *
		 * @param handler the Response handler
		 */
		private MessageObserverImpl(CoapHandler handler) {
			this.handler = handler;
		}
		
		/* (non-Javadoc)
		 * @see ch.ethz.inf.vs.californium.coap.MessageObserverAdapter#responded(ch.ethz.inf.vs.californium.coap.Response)
		 */
		@Override public void onResponse(final Response response) {
			succeeded(response != null ? new CoapResponse(response) : null);
		}
		
		/* (non-Javadoc)
		 * @see ch.ethz.inf.vs.californium.coap.MessageObserverAdapter#rejected()
		 */
		@Override public void onReject()  { failed(); }
		
		/* (non-Javadoc)
		 * @see ch.ethz.inf.vs.californium.coap.MessageObserverAdapter#timedOut()
		 */
		@Override public void onTimeout() { failed(); }
		
		/**
		 * Invoked when a response arrives (even if the response code is not
		 * successful, the response still has been successfully transmitted).
		 *
		 * @param response the response
		 */
		protected void succeeded(final CoapResponse response) {
			Executor exe = getExecutor();
			if (exe == null) handler.onLoad(response);
			else exe.execute(new Runnable() {				
				public void run() {
					try {
						deliver(response);
					} catch (Throwable t) {
						LOGGER.log(Level.WARNING, "Exception while handling response", t);
					}}});
		}
		
		/**
		 * Invokes the handler's method with the specified response. This method
		 * must be invoked by the client's executor if it defines one.
		 *
		 * @param response the response
		 */
		protected void deliver(CoapResponse response) {
			
		}
		
		/**
		 * Invokes the handler's method failed() on the executor.
		 */
		protected void failed() {
			Executor exe = getExecutor();
			if (exe == null) handler.onError();
			else exe.execute(new Runnable() { 
				public void run() { 
					try {
						handler.onError(); 
					} catch (Throwable t) {
						LOGGER.log(Level.WARNING, "Exception while handling failure", t);
					}}});
		}
	}
	
	/**
	 * The ObserveMessageObserveImpl is called whenever a notification of an
	 * observed resource arrives. It wraps the response into a CoapResponse and
	 * lets the executor invoke the handler's method.
	 */
	private class ObserveMessageObserveImpl extends MessageObserverImpl {
		
		/** The observer relation relation. */
		private final CoapObserveRelation relation;
		
		/** The orderer. */
		private final ObserveNotificationOrderer orderer;
		
		/**
		 * Constructs a new message observer with the specified handler and the
		 * specified relation.
		 *
		 * @param handler the Response handler
		 * @param relation the Observe relation
		 */
		public ObserveMessageObserveImpl(CoapHandler handler, CoapObserveRelation relation) {
			super(handler);
			this.relation = relation;
			this.orderer = new ObserveNotificationOrderer();
		}
		
		/**
		 * Checks if the specified response truly is a new notification and if,
		 * invokes the handler's method or drops the notification otherwise.
		 */
		@Override protected void deliver(CoapResponse response) {
			synchronized (orderer) {
				if (orderer.isNew(response.advanced())) {
					relation.setCurrent(response);
					handler.onLoad(response);
				} else {
					LOGGER.finer("Dropping old notification: "+response.advanced());
					return;
				}
			}
		}
		
		/**
		 * Marks the relation as canceled and invokes the the handler's failed()
		 * method.
		 */
		@Override protected void failed() {
			relation.setCanceled(true);
			super.failed();
		}
	}
	
	/**
	 * The Builder can be used to build a CoapClient if the URI's pieces are
	 * available in separate strings. This is in particular useful to add 
	 * mutliple queries to the URI.
	 */
	public static class Builder {
		
		/** The scheme, host and port. */
		String scheme, host, port;
		
		/** The path and the query. */
		String[] path, query;
		
		/**
		 * Instantiates a new builder.
		 *
		 * @param host the host
		 * @param port the port
		 */
		public Builder(String host, int port) {
			this.host = host;
			this.port = Integer.toString(port);
		}
		
		/**
		 * Sets the specified scheme.
		 *
		 * @param scheme the scheme
		 * @return the builder
		 */
		public Builder scheme(String scheme) { this.scheme = scheme; return this; }
		
		/**
		 * Sets the specified host.
		 *
		 * @param host the host
		 * @return the builder
		 */
		public Builder host(String host) { this.host = host; return this; }
		
		/**
		 * Sets the specified port.
		 *
		 * @param port the port
		 * @return the builder
		 */
		public Builder port(String port) { this.port = port; return this; }
		
		/**
		 * Sets the specified port.
		 *
		 * @param port the port
		 * @return the builder
		 */
		public Builder port(int port) { this.port = Integer.toString(port); return this; }
		
		/**
		 * Sets the specified resource path.
		 *
		 * @param path the path
		 * @return the builder
		 */
		public Builder path(String... path) { this.path = path; return this; }
		
		/**
		 * Sets the specified query.
		 *
		 * @param query the query
		 * @return the builder
		 */
		public Builder query(String... query) { this.query = query; return this; }
		
		/**
		 * Creates the CoapClient
		 *
		 * @return the client
		 */
		public CoapClient create() {
			StringBuilder builder = new StringBuilder();
			if (scheme != null)	
				builder.append(scheme).append("://");
			builder.append(host).append(":").append(port);
			for (String element:path)
				builder.append("/").append(element);
			if (query.length > 0)
				builder.append("?");
			for (int i=0;i<query.length;i++) {
				builder.append(query[i]);
				if (i < query.length-1)
					builder.append("&");
			}
			return new CoapClient(builder.toString());
		}
	}
}
