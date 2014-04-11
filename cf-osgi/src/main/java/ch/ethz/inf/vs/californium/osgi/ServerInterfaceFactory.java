package ch.ethz.inf.vs.californium.osgi;

import ch.ethz.inf.vs.californium.network.config.NetworkConfig;
import ch.ethz.inf.vs.californium.server.Server;
import ch.ethz.inf.vs.californium.server.ServerInterface;

/**
 * A factory for {@link ServerInterface} instances.
 * This factory is used by the {@link ManagedServer} in order to create a new server instance
 * when properties are updated via OSGi's Config Admin Service.
 * 
 * @author Kai Hudalla
 */
interface ServerInterfaceFactory {
	
	/**
	 * Creates a new {@link ServerInterface} instance.
	 * 
	 * Can be overridden e.g. by test classes to use a mock instance instead of a <i>real</i> server.
	 * This default implementation returns a new instance of {@link Server}.
	 * 
	 * @param config the network configuration to use for setting up the server's endpoint. If <code>null</code>
	 * the default network configuration is used.
	 * @return the new instance
	 */
	ServerInterface newServer(NetworkConfig config);
	
	/**
	 * Creates a new {@link ServerInterface} instance with multiple endpoints.
	 * 
	 * Can be overridden e.g. by test classes to use a mock instance instead of a <i>real</i> server.
	 * This default implementation returns a new instance of {@link Server}.
	 * 
	 * @param config the network configuration to use for setting up the server's endpoints. If <code>null</code>
	 * the default network configuration is used.
	 * @param ports the ports to bind endpoints to
	 * @return the new instance
	 */
	ServerInterface newServer(NetworkConfig config, int... ports);
}