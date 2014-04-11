package ch.ethz.inf.vs.californium.network.stack;

import java.util.Random;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import ch.ethz.inf.vs.californium.coap.CoAP.Type;
import ch.ethz.inf.vs.californium.coap.EmptyMessage;
import ch.ethz.inf.vs.californium.coap.Message;
import ch.ethz.inf.vs.californium.coap.Request;
import ch.ethz.inf.vs.californium.coap.Response;
import ch.ethz.inf.vs.californium.network.Exchange;
import ch.ethz.inf.vs.californium.network.Exchange.Origin;
import ch.ethz.inf.vs.californium.network.config.NetworkConfig;
import ch.ethz.inf.vs.californium.network.config.NetworkConfigDefaults;

/**
 * The reliability layer 
 */
public class ReliabilityLayer extends AbstractLayer {

	/** The logger. */
	protected final static Logger LOGGER = Logger.getLogger(ReliabilityLayer.class.getCanonicalName());
	
	/** The random numbers generator for the back-off timer */
	private Random rand = new Random();
	
	/** The configuration */ 
	private NetworkConfig config;
	
	/**
	 * Constructs a new reliability layer.
	 * @param config the configuration
	 */
	public ReliabilityLayer(NetworkConfig config) {
		this.config = config;
	}
	
	/**
	 * Schedules a retransmission for confirmable messages. 
	 */
	@Override
	public void sendRequest(final Exchange exchange, final Request request) {

		LOGGER.finer("Send request, failed transmissions: "+exchange.getFailedTransmissionCount());
		
		if (request.getType() == null)
			request.setType(Type.CON);
		
		if (request.getType() == Type.CON) {
			prepareRetransmission(exchange, new RetransmissionTask(exchange, request) {
				public void retransmit() {
					sendRequest(exchange, request);
				}
			});
		}
		super.sendRequest(exchange, request);
	}

	/**
	 * Makes sure that the response type is correct. The response type for a NON
	 * can be NON or CON. The response type for a CON should either be an ACK
	 * with a piggy-backed response or, if an empty ACK has already be sent, a
	 * CON or NON with a separate response.
	 */
	@Override
	public void sendResponse(final Exchange exchange, final Response response) {

		LOGGER.finer("Send response, failed transmissions: "+exchange.getFailedTransmissionCount());

		// If a response type is set, we do not mess around with it.
		// Only if none is set, we have to decide for one here.
		
		Type respType = response.getType();
		if (respType == null) {
			Type reqType = exchange.getCurrentRequest().getType();
			if (reqType == Type.CON) {
				if (exchange.getCurrentRequest().isAcknowledged()) {
					// send separate response
					response.setType(Type.CON);
				} else {
					exchange.getCurrentRequest().setAcknowledged(true);
					// send piggy-backed response
					response.setType(Type.ACK);
					response.setMID(exchange.getCurrentRequest().getMID());
				}
			} else {
				// send NON response
				response.setType(Type.NON);
			}
			
			LOGGER.finest("Switched response message type from "+respType+" to "+response.getType()+" (request was "+reqType+")");
		
		} else if (respType == Type.ACK || respType == Type.RST) {
			response.setMID(exchange.getCurrentRequest().getMID());
		}
		
		if (response.getType() == Type.CON) {
			LOGGER.finer("Scheduling retransmission for " + response);
			prepareRetransmission(exchange, new RetransmissionTask(exchange, response) {
				public void retransmit() {
					sendResponse(exchange, response);
				}
			});
		}
		super.sendResponse(exchange, response);
	}
	
	
	/**
	 * Computes the back-off timer and schedules the specified retransmission
	 * task.
	 * 
	 * @param exchange the exchange
	 * @param task the retransmission task
	 */
	private void prepareRetransmission(Exchange exchange, RetransmissionTask task) {
		/*
		 * For a new confirmable message, the initial timeout is set to a
		 * random number between ACK_TIMEOUT and (ACK_TIMEOUT *
		 * ACK_RANDOM_FACTOR)
		 */
		int timeout;
		if (exchange.getFailedTransmissionCount() == 0) {
			int ack_timeout = config.getInt(NetworkConfigDefaults.ACK_TIMEOUT);
			float ack_random_factor = config.getFloat(NetworkConfigDefaults.ACK_RANDOM_FACTOR);
			timeout = getRandomTimeout(ack_timeout, (int) (ack_timeout*ack_random_factor));
		} else {
			int ack_timeout_scale = config.getInt(NetworkConfigDefaults.ACK_TIMEOUT_SCALE);
			timeout = ack_timeout_scale * exchange.getCurrentTimeout();
		}
		exchange.setCurrentTimeout(timeout);
		
		ScheduledFuture<?> f = executor.schedule(task , timeout, TimeUnit.MILLISECONDS);
		exchange.setRetransmissionHandle(f);
	}
	
	/**
	 * When we receive a duplicate of a request, we stop it here and do not
	 * forward it to the upper layer. If the server has already sent a response,
	 * we send it again. If the request has only been acknowledged (but the ACK
	 * has gone lost or not reached the client yet), we resent the ACK. If the
	 * request has neither been responded, acknowledged or rejected yet, the
	 * server has not yet decided what to do with the request and we cannot do
	 * anything.
	 */
	@Override
	public void receiveRequest(Exchange exchange, Request request) {
		
		if (request.isDuplicate()) {
			// Request is a duplicate, so resend ACK, RST or response
			if (exchange.getCurrentResponse() != null) {
				LOGGER.fine("Respond with the current response to the duplicate request");
				// Do not restart retransmission cycle
				super.sendResponse(exchange, exchange.getCurrentResponse());
				
			} else if (exchange.getCurrentRequest().isAcknowledged()) {
				LOGGER.fine("The duplicate request was acknowledged but no response computed yet. Retransmit ACK");
				EmptyMessage ack = EmptyMessage.newACK(request);
				sendEmptyMessage(exchange, ack);
			
			} else if (exchange.getCurrentRequest().isRejected()) {
				LOGGER.fine("The duplicate request was rejected. Reject again");
				EmptyMessage rst = EmptyMessage.newRST(request);
				sendEmptyMessage(exchange, rst);

			} else {
				LOGGER.fine("The server has not yet decided what to do with the request. We ignore the duplicate.");
				// The server has not yet decided, whether to acknowledge or
				// reject the request. We know for sure that the server has
				// received the request though and can drop this duplicate here.
			}

		} else {
			// Request is not a duplicate
			exchange.setCurrentRequest(request);
			super.receiveRequest(exchange, request);
		}
	}

	/**
	 * When we receive a Confirmable response, we acknowledge it and it also
	 * counts as acknowledgment for the request. If the response is a duplicate,
	 * we stop it here and do not forward it to the upper layer.
	 */
	@Override
	public void receiveResponse(Exchange exchange, Response response) {
		exchange.setFailedTransmissionCount(0);
		
		exchange.getCurrentRequest().setAcknowledged(true);
		LOGGER.finest("Cancel any retransmission");
		exchange.setRetransmissionHandle(null);
		
		if (response.getType() == Type.CON && !exchange.getRequest().isCanceled()) {
			LOGGER.finer("Response is confirmable, send ACK");
			EmptyMessage ack = EmptyMessage.newACK(response);
			sendEmptyMessage(exchange, ack);
		}
		
		if (response.isDuplicate()) {
			LOGGER.fine("Response is duplicate, ignore it");
		} else {
			super.receiveResponse(exchange, response);
		}
	}

	/**
	 * If we receive an ACK or RST, we mark the outgoing request or response
	 * as acknowledged or rejected respectively and cancel its retransmission.
	 */
	@Override
	public void receiveEmptyMessage(Exchange exchange, EmptyMessage message) {
		exchange.setFailedTransmissionCount(0);
		// TODO: If this is an observe relation, the current response might not
		// be the one that is being acknowledged. The current response might
		// already be the next NON notification.
		
		if (message.getType() == Type.ACK) {
			if (exchange.getOrigin() == Origin.LOCAL) {
				exchange.getCurrentRequest().setAcknowledged(true);
			} else {
				exchange.getCurrentResponse().setAcknowledged(true);
			}
		} else if (message.getType() == Type.RST) {
			if (exchange.getOrigin() == Origin.LOCAL) {
				exchange.getCurrentRequest().setRejected(true);
			} else {
				exchange.getCurrentResponse().setRejected(true);
			}
		} else {
			LOGGER.warning("Empty messgae was not ACK nor RST: "+message);
		}

		LOGGER.finer("Cancel retransmission");
		exchange.setRetransmissionHandle(null);
		
		super.receiveEmptyMessage(exchange, message);
	}
	
	/*
	 * Returns a random timeout between the specified min and max.
	 * @param min the min
	 * @param max the max
	 * @return a random value between min and max
	 */
	private int getRandomTimeout(int min, int max) {
		if (min == max) return min;
		return min + rand.nextInt(max - min);
	}
	
	/*
	 * The main reason to create this class was to enable the methods
	 * sendRequest and sendResponse to use the same code for sending messages
	 * but where the retransmission method calls sendRequest and sendResponse
	 * respectively.
	 */
	private abstract class RetransmissionTask implements Runnable {
		
		private Exchange exchange;
		private Message message;
		
		public RetransmissionTask(Exchange exchange, Message message) {
			this.exchange = exchange;
			this.message = message;
		}
		
		@Override
		public void run() {
			/*
			 * Do not retransmit a message if it has been acknowledged,
			 * rejected, canceled or already been retransmitted for the maximum
			 * number of times.
			 */
			try {
				int failedCount = exchange.getFailedTransmissionCount() + 1;
				exchange.setFailedTransmissionCount(failedCount);
				
				if (message.isAcknowledged()) {
					LOGGER.finest("Timeout: message already acknowledged, cancel retransmission of "+message);
					return;
					
				} else if (message.isRejected()) {
					LOGGER.finest("Timeout: message already rejected, cancel retransmission of "+message);
					return;
					
				} else if (message.isCanceled()) {
					LOGGER.finest("Timeout: canceled (MID="+message.getMID()+"), do not retransmit");
					return;
					
				} else if (failedCount <= config.getInt(NetworkConfigDefaults.MAX_RETRANSMIT)) {
					LOGGER.finer("Timeout: retransmit message, failed: "+failedCount+", message: "+message);
					
					// Trigger MessageObservers
					message.retransmitting(); // TODO by Martin: Do not set next notification if max reached!
					
					// MessageObserver might have canceled
					if (!message.isCanceled())
						retransmit();

				} else {
					LOGGER.info("Timeout: retransmission limit reached, exchange failed, message: "+message);
					exchange.setTimedOut();
					message.setTimedOut(true);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		public abstract void retransmit();
	}
	
}
