package org.fogbowcloud.manager.core.intercomponent.xmpp.handlers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.fogbowcloud.manager.core.exceptions.FogbowManagerException;
import org.fogbowcloud.manager.core.exceptions.InvalidParameterException;
import org.fogbowcloud.manager.core.exceptions.UnexpectedException;
import org.fogbowcloud.manager.core.intercomponent.RemoteFacade;
import org.fogbowcloud.manager.core.intercomponent.xmpp.PacketSenderHolder;
import org.fogbowcloud.manager.core.intercomponent.xmpp.requesters.RemoteCreateOrderRequest;
import org.fogbowcloud.manager.core.models.orders.ComputeOrder;
import org.fogbowcloud.manager.core.models.orders.Order;
import org.fogbowcloud.manager.core.models.tokens.FederationUser;
import org.jamppa.component.PacketSender;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.BDDMockito;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.xmpp.packet.IQ;

@RunWith(PowerMockRunner.class)
@PrepareForTest({RemoteFacade.class, PacketSenderHolder.class})
public class RemoteCreateOrderRequestHandlerTest {

    public static final String IQ_RESULT = "\n<iq type=\"result\" id=\"%s\" from=\"%s\"/>";

    public static final String IQ_ERROR_RESULT = "\n<iq type=\"error\" id=\"%s\" from=\"%s\">\n"
            + "  <error code=\"500\" type=\"wait\">\n"
            + "    <undefined-condition xmlns=\"urn:ietf:params:xml:ns:xmpp-stanzas\"/>\n"
            + "    <text xmlns=\"urn:ietf:params:xml:ns:xmpp-stanzas\">Fogbow Manager exception</text>\n"
            + "  </error>\n" + "</iq>";

    private RemoteCreateOrderRequestHandler remoteCreateOrderRequestHandler;
    private RemoteFacade remoteFacade;
    private PacketSender packetSender;

    @Before
    public void setUp() {
        this.remoteCreateOrderRequestHandler = new RemoteCreateOrderRequestHandler();

        this.remoteFacade = Mockito.mock(RemoteFacade.class);
        PowerMockito.mockStatic(RemoteFacade.class);
        BDDMockito.given(RemoteFacade.getInstance()).willReturn(this.remoteFacade);
        
        this.packetSender = Mockito.mock(PacketSender.class);
        PowerMockito.mockStatic(PacketSenderHolder.class);
        BDDMockito.given(PacketSenderHolder.getPacketSender()).willReturn(this.packetSender);
    }

    // test case: When the handle method is called passing an IQ request, it must return the Order from
    // that.
    @Test
    public void testHandleWithValidIQ() throws FogbowManagerException, UnexpectedException {
        // set up
        FederationUser federationUser = createFederationUser();
        Order order = createOrder(federationUser);

        Mockito.doNothing().when(this.remoteFacade).activateOrder(Mockito.eq(order));

        RemoteCreateOrderRequest remoteCreateOrderRequest = new RemoteCreateOrderRequest(order);
        IQ iq = remoteCreateOrderRequest.createIq();

        // exercise
        IQ result = this.remoteCreateOrderRequestHandler.handle(iq);

        // verify
        Mockito.verify(this.remoteFacade, Mockito.times(1)).activateOrder(Mockito.eq(order));
        
        String orderId = order.getId();
        String providingMember = order.getProvidingMember();
        String expected = String.format(IQ_RESULT, orderId, providingMember);
        
        Assert.assertEquals(expected, result.toString());
    }

    // test case: When an Exception occurs, the handle method must return a response error.
    @Test
    public void testHandleWhenThrowsException() throws FogbowManagerException, UnexpectedException {
        // set up
        FederationUser federationUser = null;
        Order order = createOrder(federationUser);

        Mockito.doThrow(new FogbowManagerException()).when(this.remoteFacade)
                .activateOrder(Mockito.any(Order.class));

        RemoteCreateOrderRequest remoteCreateOrderRequest = new RemoteCreateOrderRequest(order);
        IQ iq = remoteCreateOrderRequest.createIq();

        // exercise
        IQ result = this.remoteCreateOrderRequestHandler.handle(iq);
        
        // verify
        Mockito.verify(this.remoteFacade, Mockito.times(1)).activateOrder(Mockito.eq(order));

        String orderId = order.getId();
        String providingMember = order.getProvidingMember();
        String expected = String.format(IQ_ERROR_RESULT, orderId, providingMember);

        Assert.assertEquals(expected, result.toString());
    }
    
    private Order createOrder(FederationUser federationUser) {
        return new ComputeOrder(federationUser, "requestingMember", "providingmember", 1, 2,
                3, "imageId", null, "publicKey", new ArrayList<>());
    }

    private FederationUser createFederationUser() throws InvalidParameterException {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("user-name", "fogbow");

        FederationUser federationUser = new FederationUser("fake-id", attributes);
        return federationUser;
    }

}
