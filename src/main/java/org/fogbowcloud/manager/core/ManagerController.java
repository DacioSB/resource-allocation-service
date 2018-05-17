package org.fogbowcloud.manager.core;

import java.util.Properties;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.constants.ConfigurationConstants;
import org.fogbowcloud.manager.core.instanceprovider.InstanceProvider;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.compute.ComputePlugin;
import org.fogbowcloud.manager.core.threads.OpenProcessor;

public class ManagerController {

	private InstanceProvider localInstanceProvider;
	private InstanceProvider remoteInstanceProvider;

	private Thread openProcessorThread;

	private String localMemberId;

	private Properties properties;

	private ComputePlugin computePlugin;
	private IdentityPlugin localIdentityPlugin;
	private IdentityPlugin federationIdentityPlugin;

	private static final Logger LOGGER = Logger.getLogger(ManagerController.class);

	public ManagerController(Properties properties, InstanceProvider localInstanceProvider,
			InstanceProvider remoteInstanceProvider, ComputePlugin computePlugin, IdentityPlugin localIdentityPlugin,
			IdentityPlugin federationIdentityPlugin) {

		this.properties = properties;

		this.localMemberId = this.properties.getProperty(ConfigurationConstants.XMPP_ID_KEY);
		this.localInstanceProvider = localInstanceProvider;
		this.remoteInstanceProvider = remoteInstanceProvider;

		this.computePlugin = computePlugin;
		this.localIdentityPlugin = localIdentityPlugin;
		this.federationIdentityPlugin = federationIdentityPlugin;

		OpenProcessor openProcessor = new OpenProcessor(this.localInstanceProvider, this.remoteInstanceProvider,
				properties);
		this.openProcessorThread = new Thread(openProcessor);

		this.startManagerThreads();
	}

	/**
	 * This method starts all manager threads, if you defined a new manager
	 * operation and this operation require a new thread to run, you should start
	 * this thread at this method.
	 */
	private void startManagerThreads() {
		LOGGER.info("Starting manager open processor thread");
		this.openProcessorThread.start();
	}

}
