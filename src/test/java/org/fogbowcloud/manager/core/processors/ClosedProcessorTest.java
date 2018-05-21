package org.fogbowcloud.manager.core.processors;

import static org.junit.Assert.*;

import java.util.Map;
import java.util.Properties;
import org.fogbowcloud.manager.core.BaseUnitTests;
import org.fogbowcloud.manager.core.OrderController;
import org.fogbowcloud.manager.core.SharedOrderHolders;
import org.fogbowcloud.manager.core.instanceprovider.LocalInstanceProvider;
import org.fogbowcloud.manager.core.instanceprovider.RemoteInstanceProvider;
import org.fogbowcloud.manager.core.manager.constants.ConfigurationConstants;
import org.fogbowcloud.manager.core.models.linkedlist.ChainedList;
import org.fogbowcloud.manager.core.models.orders.Order;
import org.fogbowcloud.manager.core.models.orders.instances.OrderInstance;
import org.fogbowcloud.manager.core.models.token.Token;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class ClosedProcessorTest extends BaseUnitTests {

    private ClosedProcessor closedProcessor;

    private RemoteInstanceProvider remoteInstanceProvider;
    private LocalInstanceProvider localInstanceProvider;

    private Properties properties;

    private Thread thread;
    private OrderController orderController;

    @Before
    public void setUp() {
        this.properties = new Properties();
        this.properties.setProperty(
                ConfigurationConstants.XMPP_ID_KEY, BaseUnitTests.LOCAL_MEMBER_ID);

        this.orderController = new OrderController(properties, localInstanceProvider, remoteInstanceProvider);

        this.localInstanceProvider = Mockito.mock(LocalInstanceProvider.class);
        this.remoteInstanceProvider = Mockito.mock(RemoteInstanceProvider.class);

        this.closedProcessor =
                Mockito.spy(
                        new ClosedProcessor(
                                this.localInstanceProvider,
                                this.remoteInstanceProvider,
                                this.orderController,
                                this.properties));
    }

    @Override
    public void tearDown() {
        if (this.thread != null) {
            this.thread.interrupt();
            this.thread = null;
        }
        super.tearDown();
    }

    @Test
    public void testProcessClosedLocalOrder() throws Exception {
        OrderInstance orderInstance = new OrderInstance("fake-id");
        Order localOrder = createLocalOrder(getLocalMemberId());
        localOrder.setOrderInstance(orderInstance);

        Token federationToken = null;
        this.orderController.activateOrder(localOrder, federationToken);

        SharedOrderHolders sharedOrderHolders = SharedOrderHolders.getInstance();
        ChainedList closedOrders = sharedOrderHolders.getClosedOrdersList();
        closedOrders.addItem(localOrder);

        Mockito.doNothing()
                .when(this.localInstanceProvider)
                .deleteInstance(Mockito.any(OrderInstance.class));

        this.thread = new Thread(this.closedProcessor);
        this.thread.start();

        Thread.sleep(500);

        Map<String, Order> activeOrdersMap = sharedOrderHolders.getActiveOrdersMap();
        assertNull(activeOrdersMap.get(localOrder.getId()));

        closedOrders.resetPointer();
        assertNull(closedOrders.getNext());
    }

    @Test
    public void testProcessClosedLocalOrderFails() throws Exception {
        OrderInstance orderInstance = new OrderInstance("fake-id");
        Order localOrder = createLocalOrder(getLocalMemberId());
        localOrder.setOrderInstance(orderInstance);

        Token federationToken = null;
        this.orderController.activateOrder(localOrder, federationToken);

        SharedOrderHolders sharedOrderHolders = SharedOrderHolders.getInstance();
        ChainedList closedOrders = sharedOrderHolders.getClosedOrdersList();
        closedOrders.addItem(localOrder);

        Mockito.doThrow(Exception.class)
                .when(this.localInstanceProvider)
                .deleteInstance(Mockito.any(OrderInstance.class));

        this.thread = new Thread(this.closedProcessor);
        this.thread.start();

        Thread.sleep(500);

        Map<String, Order> activeOrdersMap = sharedOrderHolders.getActiveOrdersMap();
        assertEquals(localOrder, activeOrdersMap.get(localOrder.getId()));

        closedOrders.resetPointer();
        assertEquals(localOrder, closedOrders.getNext());
    }
}
