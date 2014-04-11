package ch.ethz.inf.vs.californium.network.stack;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import ch.ethz.inf.vs.californium.coap.CoAP.ResponseCode;
import ch.ethz.inf.vs.californium.coap.CoAP.Type;
import ch.ethz.inf.vs.californium.coap.EmptyMessage;
import ch.ethz.inf.vs.californium.coap.MessageObserverAdapter;
import ch.ethz.inf.vs.californium.coap.Request;
import ch.ethz.inf.vs.californium.coap.Response;
import ch.ethz.inf.vs.californium.network.Exchange;
import ch.ethz.inf.vs.californium.network.Exchange.Origin;
import ch.ethz.inf.vs.californium.network.config.NetworkConfig;
import ch.ethz.inf.vs.californium.network.config.NetworkConfigDefaults;
import ch.ethz.inf.vs.californium.observe.ObserveRelation;

public class ObserveLayer extends AbstractLayer {

	private long backoff = 0; // additional time to wait until re-registration
	
	public ObserveLayer(NetworkConfig config) {
		this.backoff = config.getInt(NetworkConfigDefaults.NOTIFICATION_REREGISTRATION_BACKOFF);
	}
	
	@Override
	public void sendRequest(Exchange exchange, Request request) {
		super.sendRequest(exchange, request);
	}
	
	@Override
	public void sendResponse(final Exchange exchange, final Response response) {
		final ObserveRelation relation = exchange.getRelation();
		if (relation != null && relation.isEstablished()) {
			
			if (exchange.getRequest().isAcknowledged() || exchange.getRequest().getType()==Type.NON) {
				// Transmit errors as CON
				if (!ResponseCode.isSuccess(response.getCode())) {
					LOGGER.fine("Response has error code "+response.getCode()+" and must be sent as CON");
					response.setType(Type.CON);
					relation.cancel();
				} else {
					// Make sure that every now and than a CON is mixed within
					if (relation.check()) {
						LOGGER.fine("The observe relation requires the notification to be sent as CON");
						response.setType(Type.CON);
					// By default use NON, but do not override resource decision
					} else if (response.getType()==null) {
						response.setType(Type.NON);
					}
				}
			}
			
			// This is a notification
			response.setLast(false);
			
			/*
			 * Only one Confirmable message is allowed to be in transit. A CON
			 * is in transit as long as it has not been acknowledged, rejected,
			 * or timed out. All further notifications are postponed here. If a
			 * former CON is acknowledged or timeouts, it starts the youngest
			 * notification (In case of a timeout, it keeps the retransmission
			 * counter). When a fresh/younger notification arrives but must be
			 * postponed we forget any former notification.
			 */
			if (response.getType() == Type.CON) {
				prepareSelfReplacement(exchange, response);
			}
			
			// The decision whether to postpone this notification or not and the
			// decision which notification is the youngest to send next must be
			// synchronized
			synchronized (exchange) {
				Response current = relation.getCurrentControlNotification();
				if (current != null && isInTransit(current)) {
					LOGGER.fine("A former notification is still in transit. Postpone " + response);
					relation.setNextControlNotification(response);
					return;
				} else {
					relation.setCurrentControlNotification(response);
					relation.setNextControlNotification(null);
				}
			}

		} // else no observe was requested or the resource does not allow it
		super.sendResponse(exchange, response);
	}
	
	/**
	 * Returns true if the specified response is still in transit. A response is
	 * in transit if it has not yet been acknowledged, rejected or its current
	 * transmission has not yet timed out. 
	 */
	private boolean isInTransit(Response response) {
		Type type = response.getType();
		boolean acked = response.isAcknowledged();
		boolean timeout = response.isTimedOut();
		boolean result = type == Type.CON && !acked && !timeout;
		return result;
	}

	@Override
	public void receiveResponse(Exchange exchange, Response response) {
		if (response.getOptions().hasObserve()) {
			if (exchange.getRequest().isCanceled()) {
				// The request was canceled and we no longer want notifications
				LOGGER.finer("ObserveLayer rejecting notification for canceled Exchange");
				EmptyMessage rst = EmptyMessage.newRST(response);
				sendEmptyMessage(exchange, rst);
			} else {
				prepareReregistration(exchange, response, new ReregistrationTask(exchange));
				super.receiveResponse(exchange, response);
			}
		} else {
			// No observe option in response => always deliver
			super.receiveResponse(exchange, response);
		}
	}
	
	@Override
	public void receiveEmptyMessage(Exchange exchange, EmptyMessage message) {
		// NOTE: We could also move this into the MessageObserverAdapter from
		// sendResponse into the method rejected().
		if (message.getType() == Type.RST && exchange.getOrigin() == Origin.REMOTE) {
			// The response has been rejected
			ObserveRelation relation = exchange.getRelation();
			if (relation != null) {
				relation.cancel();
			} // else there was no observe relation ship and this layer ignores the rst
		}
		super.receiveEmptyMessage(exchange, message);
	}
	
	private void prepareSelfReplacement(Exchange exchange, Response response) {
		response.addMessageObserver(new NotificationController(exchange, response));
	}
	
	private void prepareReregistration(Exchange exchange, Response response, ReregistrationTask task) {
		long timeout = response.getOptions().getMaxAge()*1000 + this.backoff;
		LOGGER.finest("Scheduling re-registration in " + timeout + "ms for " + exchange.getRequest());
		ScheduledFuture<?> f = executor.schedule(task , timeout, TimeUnit.MILLISECONDS);
		exchange.setReregistrationHandle(f);
	}
	
	/**
	 * Sends the next CON as soon as the former CON is no longer in transit.
	 */
	private class NotificationController extends MessageObserverAdapter {
		
		private Exchange exchange;
		private Response response;
		
		public NotificationController(Exchange exchange, Response response) {
			this.exchange = exchange;
			this.response = response;
		}
		
		@Override
		public void onAcknowledgement() {
			synchronized (exchange) {
				ObserveRelation relation = exchange.getRelation();
				Response next = relation.getNextControlNotification();
				relation.setCurrentControlNotification(next); // next may be null
				relation.setNextControlNotification(null);
				if (next != null) {
					LOGGER.fine("Notification has been acknowledged, send the next one");
					ObserveLayer.super.sendResponse(exchange, next); // TODO: make this as new task?
				}
			}
		}
		
		@Override
		public void onRetransmission() {
			synchronized (exchange) {
				final ObserveRelation relation = exchange.getRelation();
				final Response next = relation.getNextControlNotification();
				if (next != null) {
					LOGGER.fine("The notification has timed out and there is a younger notification. Send the younger one");
					relation.setNextControlNotification(null);
					// Send the next notification
					response.cancel();
					Type nt = next.getType();
					if (nt != Type.CON); {
						LOGGER.finer("The next notification's type was "+nt+". Since it replaces a CON control notification, it becomes a CON as well");
						prepareSelfReplacement(exchange, next);
						next.setType(Type.CON); // Force the next to be a Confirmable as well
					}
					relation.setCurrentControlNotification(next);
					// Create a new task for sending next response so that we can leave the sync-block
					executor.execute(new Runnable() {
						public void run() {
							ObserveLayer.super.sendResponse(exchange, next);
						}
					});
				}
			}
		}
		
		@Override
		public void onTimeout() {
			ObserveRelation relation = exchange.getRelation();
			LOGGER.info("Notification timed out. Cancel all relations with source "+relation.getSource());
			relation.cancelAll();
		}
		
		// Cancellation on RST is done in receiveEmptyMessage()
	}
	

	/*
	 * The main reason to create this class was to enable the methods
	 * sendRequest and sendResponse to use the same code for sending messages
	 * but where the retransmission method calls sendRequest and sendResponse
	 * respectively.
	 */
	private class ReregistrationTask implements Runnable {
		
		private Exchange exchange;
		
		public ReregistrationTask(Exchange exchange) {
			this.exchange = exchange;
		}
		
		@Override
		public void run() {
			if (!exchange.getRequest().isCanceled()) {
				Request refresh = Request.newGet();
				refresh.setOptions(exchange.getRequest().getOptions());
				// make sure Observe is set and zero
				refresh.setObserve();
				// use same Token
				refresh.setToken(exchange.getRequest().getToken());
				refresh.setDestination(exchange.getRequest().getDestination());
				refresh.setDestinationPort(exchange.getRequest().getDestinationPort());
				LOGGER.info("Re-registering for " + exchange.getRequest());
				sendRequest(exchange, refresh);
			} else {
				LOGGER.finer("Dropping re-registration for canceled " + exchange.getRequest());
			}
		}
	}
}