package org.fogbowcloud.ras.core.stubs;

import org.fogbowcloud.ras.core.exceptions.FogbowRasException;
import org.fogbowcloud.ras.core.exceptions.UnexpectedException;
import org.fogbowcloud.ras.core.models.instances.NetworkInstance;
import org.fogbowcloud.ras.core.models.orders.NetworkOrder;
import org.fogbowcloud.ras.core.models.tokens.Token;
import org.fogbowcloud.ras.core.plugins.cloud.NetworkPlugin;

/**
 * This class is a stub for the NetworkPlugin interface used for tests only.
 * Should not have a proper implementation.
 */
public class StubNetworkPlugin implements NetworkPlugin<Token> {

    public StubNetworkPlugin() {
    }

    @Override
    public String requestInstance(NetworkOrder networkOrder, Token token)
            throws FogbowRasException, UnexpectedException {
        return null;
    }

    @Override
    public NetworkInstance getInstance(String networkInstanceId, Token token)
            throws FogbowRasException, UnexpectedException {
        return null;
    }

    @Override
    public void deleteInstance(String networkInstanceId, Token token)
            throws FogbowRasException, UnexpectedException {
    }

}
