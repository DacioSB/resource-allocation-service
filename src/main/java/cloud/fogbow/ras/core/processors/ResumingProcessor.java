package cloud.fogbow.ras.core.processors;

import cloud.fogbow.common.exceptions.FogbowException;
import cloud.fogbow.common.exceptions.InternalServerErrorException;
import cloud.fogbow.common.exceptions.UnavailableProviderException;
import cloud.fogbow.common.models.linkedlists.ChainedList;
import cloud.fogbow.ras.api.http.response.OrderInstance;
import cloud.fogbow.ras.constants.Messages;
import cloud.fogbow.ras.core.OrderStateTransitioner;
import cloud.fogbow.ras.core.SharedOrderHolders;
import cloud.fogbow.ras.core.cloudconnector.CloudConnectorFactory;
import cloud.fogbow.ras.core.cloudconnector.LocalCloudConnector;
import cloud.fogbow.ras.core.models.orders.Order;
import cloud.fogbow.ras.core.models.orders.OrderState;
import org.apache.log4j.Logger;

public class ResumingProcessor implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(cloud.fogbow.ras.core.processors.ResumingProcessor.class);

    private String localProviderId;
    private ChainedList<Order> resumingOrderList;
    /**
     * Attribute that represents the thread sleep time when there are no orders to be processed.
     */
    private Long sleepTime;


    public ResumingProcessor(String providerId, String sleepTimeStr) {
        SharedOrderHolders sharedOrderHolders = SharedOrderHolders.getInstance();
        this.resumingOrderList = sharedOrderHolders.getResumingOrdersList();
        this.sleepTime = Long.valueOf(sleepTimeStr);
        this.localProviderId = providerId;
    }

    /**
     * Iterates over the spawning orders list and tries to process one order at a time. When the order
     * is null, it indicates that the iteration ended. A new iteration is started after some time.
     */
    @Override
    public void run() {
        boolean isActive = true;
        Order order = null;
        while (isActive) {
            try {
                order = this.resumingOrderList.getNext();
                if (order != null) {
                    processResumingOrder(order);
                } else {
                    this.resumingOrderList.resetPointer();
                    Thread.sleep(this.sleepTime);
                }
            } catch (InterruptedException e) {
                isActive = false;
                LOGGER.error(Messages.Log.THREAD_HAS_BEEN_INTERRUPTED, e);
            } catch (InternalServerErrorException e) {
                LOGGER.error(e.getMessage(), e);
            } catch (Throwable e) {
                LOGGER.error(Messages.Log.UNEXPECTED_ERROR, e);
            }
        }
    }

    protected void processResumingOrder(Order order) throws FogbowException {
        // The order object synchronization is needed to prevent a race
        // condition on order access. For example: a user can delete an spawning
        // order while this method is trying to check the status of an instance
        // that has been requested in the cloud.
        synchronized (order) {
            // Check if the order is still in the SPAWNING state (it could have been changed by another thread)
            OrderState orderState = order.getOrderState();
            if (!orderState.equals(OrderState.SPAWNING)) { //mudar aq
                return;
            }
            // Only local orders need to be monitored. Remote orders are monitored by the remote provider.
            // State changes that happen at the remote provider are synchronized by the RemoteOrdersStateSynchronization
            // processor.
            if (order.isProviderRemote(this.localProviderId)) {
                // This should never happen, but the bug can be mitigated by moving the order to the remoteOrders list
                OrderStateTransitioner.transition(order, OrderState.PENDING);
                LOGGER.error(Messages.Log.UNEXPECTED_ERROR);
                return;
            }
            // Here we know that the CloudConnector is local, but the use of CloudConnectFactory facilitates testing.
            LocalCloudConnector localCloudConnector = (LocalCloudConnector)
                    CloudConnectorFactory.getInstance().getCloudConnector(this.localProviderId, order.getCloudName());
            // We don't audit requests we make
            localCloudConnector.switchOffAuditing();

            try {
                OrderInstance instance = localCloudConnector.getInstance(order);
                if (instance.hasFailed()) {
                    OrderStateTransitioner.transition(order, OrderState.FAILED_AFTER_SUCCESSFUL_REQUEST);
                } else if (instance.isReady()) {
                    OrderStateTransitioner.transition(order, OrderState.FULFILLED);
                }
            } catch (UnavailableProviderException e1) {
                OrderStateTransitioner.transition(order, OrderState.UNABLE_TO_CHECK_STATUS);
                throw e1;
            } catch (Exception e2) {
                order.setOnceFaultMessage(e2.getMessage());
                LOGGER.info(String.format(Messages.Exception.GENERIC_EXCEPTION_S, e2));
                OrderStateTransitioner.transition(order, OrderState.FAILED_AFTER_SUCCESSFUL_REQUEST);
            }
        }
    }
}