package org.fogbowcloud.ras.core.datastore;

import org.fogbowcloud.ras.core.exceptions.UnexpectedException;
import org.fogbowcloud.ras.core.models.linkedlists.SynchronizedDoublyLinkedList;
import org.fogbowcloud.ras.core.models.orders.Order;
import org.fogbowcloud.ras.core.models.orders.OrderState;

public interface StableStorage {
    /**
     * Add the order into database, so we can recovery it when necessary.
     *
     * @param order {@link Order}
     */
    void add(Order order) throws UnexpectedException;

    /**
     * Update the order in the stable storage
     *
     * @param order {@link Order}
     * @param stateChange this should be true if the order state was changed.
     */
    void update(Order order, boolean stateChange) throws UnexpectedException;

    /**
     * Retrive orders from the database based on its state.
     *
     * @param orderState {@link OrderState}
     * @return {@link SynchronizedDoublyLinkedList}
     */
    SynchronizedDoublyLinkedList readActiveOrders(OrderState orderState) throws UnexpectedException;
}
