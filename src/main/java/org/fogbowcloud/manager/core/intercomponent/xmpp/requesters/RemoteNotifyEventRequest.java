package org.fogbowcloud.manager.core.intercomponent.xmpp.requesters;

import com.google.gson.Gson;
import org.apache.log4j.Logger;
import org.dom4j.Element;
import org.fogbowcloud.manager.core.intercomponent.xmpp.*;
import org.fogbowcloud.manager.core.models.orders.Order;
import org.xmpp.packet.IQ;

public class RemoteNotifyEventRequest implements RemoteRequest<Void> {

    private static final Logger LOGGER = Logger.getLogger(RemoteNotifyEventRequest.class);

    private Order order;
    private Event event;

    public RemoteNotifyEventRequest(Order order, Event event) {
        this.order = order;
        this.event = event;
    }

    @Override
    public Void send() throws Exception {

        IQ iq = marshalIQ(this.order, this.event);

        IQ response = (IQ) PacketSenderHolder.getPacketSender().syncSendPacket(iq);

        XmppErrorConditionToExceptionTranslator.handleError(response, this.order.getRequestingMember());
        LOGGER.debug("Request for order: " + this.order.getId() + " has been sent to " + order.getProvidingMember());
        return null;
    }

    public static IQ marshalIQ(Order order, Event event) {

        LOGGER.debug("Creating IQ for order: " + order.getId() + " event: " + event);
        
        IQ iq = new IQ(IQ.Type.set);
        iq.setTo(order.getRequestingMember());
        iq.setID(order.getId());

        //marshall order parcel
        Element queryElement = iq.getElement().addElement(IqElement.QUERY.toString(),
                RemoteMethod.REMOTE_NOTIFY_EVENT.toString());

        Element orderElement = queryElement.addElement(IqElement.ORDER.toString());
        orderElement.setText(new Gson().toJson(order));

        Element orderClassNameElement = queryElement.addElement(IqElement.ORDER_CLASS_NAME.toString());
        orderClassNameElement.setText(order.getClass().getName());

        //marshall event parcel
        Element eventElement = queryElement.addElement(IqElement.EVENT.toString());
        eventElement.setText(new Gson().toJson(event));

        return iq;
    }

}
