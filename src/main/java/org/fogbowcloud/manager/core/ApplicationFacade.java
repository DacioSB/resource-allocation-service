package org.fogbowcloud.manager.core;

import java.util.*;

import org.fogbowcloud.manager.core.intercomponent.exceptions.RemoteRequestException;
import org.fogbowcloud.manager.core.cloudconnector.CloudConnector;
import org.fogbowcloud.manager.core.cloudconnector.CloudConnectorFactory;
import org.fogbowcloud.manager.core.constants.ConfigurationConstants;
import org.fogbowcloud.manager.core.exceptions.ImageException;
import org.fogbowcloud.manager.core.exceptions.InstanceNotFoundException;
import org.fogbowcloud.manager.core.exceptions.OrderManagementException;
import org.fogbowcloud.manager.core.exceptions.PropertyNotSpecifiedException;
import org.fogbowcloud.manager.core.exceptions.QuotaException;
import org.fogbowcloud.manager.core.exceptions.RequestException;
import org.fogbowcloud.manager.core.exceptions.UnauthenticatedException;
import org.fogbowcloud.manager.core.constants.Operation;
import org.fogbowcloud.manager.core.models.images.Image;
import org.fogbowcloud.manager.core.models.instances.*;
import org.fogbowcloud.manager.core.models.orders.*;
import org.fogbowcloud.manager.core.models.quotas.Quota;
import org.fogbowcloud.manager.core.models.quotas.allocation.Allocation;
import org.fogbowcloud.manager.core.plugins.exceptions.TokenCreationException;
import org.fogbowcloud.manager.core.plugins.exceptions.UnauthorizedException;
import org.fogbowcloud.manager.core.models.quotas.allocation.ComputeAllocation;
import org.fogbowcloud.manager.core.models.quotas.ComputeQuota;
import org.fogbowcloud.manager.core.models.token.FederationUser;

public class ApplicationFacade {

    private static ApplicationFacade instance;

    private AaController aaController;
    private OrderController orderController;

    private ApplicationFacade() {
    }

    public static ApplicationFacade getInstance() {
        synchronized (ApplicationFacade.class) {
            if (instance == null) {
                instance = new ApplicationFacade();
            }
            return instance;
        }
    }

    public String createCompute(ComputeOrder order, String federationTokenValue)
        throws UnauthenticatedException, UnauthorizedException, OrderManagementException {
        return activateOrder(order, federationTokenValue);
    }

    public List<ComputeInstance> getAllComputes(String federationTokenValue)
        throws UnauthorizedException, UnauthenticatedException, RequestException, TokenCreationException,
            PropertyNotSpecifiedException, InstanceNotFoundException, RemoteRequestException {
        List<Order> allOrders = getAllOrders(federationTokenValue, InstanceType.COMPUTE);
        return getAllInstances(allOrders, ComputeInstance.class);
    }

    public ComputeInstance getCompute(String orderId, String federationTokenValue)
        throws UnauthenticatedException, TokenCreationException, RequestException, PropertyNotSpecifiedException,
            UnauthorizedException, InstanceNotFoundException, RemoteRequestException {
        ComputeInstance instance = (ComputeInstance) getResourceInstance(orderId, federationTokenValue, InstanceType.COMPUTE);
        // The user believes that the order id is actually the instance id.
        // So we need to set the instance id accordingly before returning the instance.
        instance.setId(orderId);
        return instance;
    }

    public void deleteCompute(String computeId, String federationTokenValue)
        throws OrderManagementException, UnauthorizedException, UnauthenticatedException {
        deleteOrder(computeId, federationTokenValue, InstanceType.COMPUTE);
    }

    public ComputeAllocation getComputeAllocation(String memberId, String federationTokenValue)
            throws UnauthenticatedException, UnauthorizedException {

        return (ComputeAllocation) getUserAllocation(memberId, federationTokenValue, InstanceType.COMPUTE);
    }

    public ComputeQuota getComputeQuota(String memberId, String federationTokenValue)
            throws UnauthenticatedException, QuotaException, UnauthorizedException, PropertyNotSpecifiedException,
            TokenCreationException, RemoteRequestException {

            return (ComputeQuota) getUserQuota(memberId, federationTokenValue, InstanceType.COMPUTE);
    }

    public String createVolume(VolumeOrder volumeOrder, String federationTokenValue)
        throws OrderManagementException, UnauthorizedException, UnauthenticatedException {
        return activateOrder(volumeOrder, federationTokenValue);
    }

    public List<VolumeInstance> getAllVolumes(String federationTokenValue)
        throws UnauthorizedException, UnauthenticatedException, RequestException, TokenCreationException,
            PropertyNotSpecifiedException, InstanceNotFoundException, RemoteRequestException {

        List<Order> allOrders = getAllOrders(federationTokenValue, InstanceType.VOLUME);
        return getAllInstances(allOrders, VolumeInstance.class);
    }

    public VolumeInstance getVolume(String orderId, String federationTokenValue)
        throws UnauthenticatedException, TokenCreationException, RequestException, PropertyNotSpecifiedException,
            UnauthorizedException, InstanceNotFoundException, RemoteRequestException {
        VolumeInstance instance = (VolumeInstance) getResourceInstance(orderId, federationTokenValue, InstanceType.VOLUME);
        // The user believes that the order id is actually the instance id.
        // So we need to set the instance id accordingly before returning the instance.
        instance.setId(orderId);
        return instance;
    }

    public void deleteVolume(String orderId, String federationTokenValue)
        throws OrderManagementException, UnauthorizedException, UnauthenticatedException {
        deleteOrder(orderId, federationTokenValue, InstanceType.VOLUME);
    }

    public String createNetwork(NetworkOrder networkOrder, String federationTokenValue)
        throws OrderManagementException, UnauthorizedException, UnauthenticatedException {
        return activateOrder(networkOrder, federationTokenValue);
    }

    public List<NetworkInstance> getAllNetworks(String federationTokenValue)
        throws UnauthorizedException, UnauthenticatedException, RequestException, TokenCreationException,
            PropertyNotSpecifiedException, InstanceNotFoundException, RemoteRequestException {

        List<Order> allOrders = getAllOrders(federationTokenValue, InstanceType.NETWORK);
        return getAllInstances(allOrders, NetworkInstance.class);
    }

    public NetworkInstance getNetwork(String orderId, String federationTokenValue)
        throws UnauthenticatedException, TokenCreationException, RequestException, PropertyNotSpecifiedException,
            UnauthorizedException, InstanceNotFoundException, RemoteRequestException {
        NetworkInstance instance = (NetworkInstance) getResourceInstance(orderId, federationTokenValue, InstanceType.NETWORK);
        // The user believes that the order id is actually the instance id.
        // So we need to set the instance id accordingly before returning the instance.
        instance.setId(orderId);
        return instance;
    }

    public void deleteNetwork(String orderId, String federationTokenValue)
        throws OrderManagementException, UnauthorizedException, UnauthenticatedException {
        deleteOrder(orderId, federationTokenValue, InstanceType.NETWORK);
    }

    public String createAttachment(AttachmentOrder attachmentOrder, String federationTokenValue) throws
            OrderManagementException, UnauthorizedException, UnauthenticatedException {

        // The AttachmentOrder uses the instanceId, therefore, we need to set the instanceId for source orderId and
        // target orderId received from request.
        Order sourceOrder = SharedOrderHolders.getInstance().getActiveOrdersMap().get(attachmentOrder.getSource());
        Order targetOrder = SharedOrderHolders.getInstance().getActiveOrdersMap().get(attachmentOrder.getTarget());
        attachmentOrder.setSource(sourceOrder.getInstanceId());
        attachmentOrder.setTarget(targetOrder.getInstanceId());

        return activateOrder(attachmentOrder, federationTokenValue);
    }

    public List<AttachmentInstance> getAllAttachments(String federationTokenValue) throws UnauthenticatedException,
            UnauthorizedException, PropertyNotSpecifiedException, TokenCreationException, RequestException,
            InstanceNotFoundException, RemoteRequestException {

        List<Order> allOrders = getAllOrders(federationTokenValue, InstanceType.ATTACHMENT);
        return getAllInstances(allOrders, AttachmentInstance.class);
    }

    public AttachmentInstance getAttachment(String orderId, String federationTokenValue) throws
            UnauthenticatedException, UnauthorizedException, RequestException, TokenCreationException,
            PropertyNotSpecifiedException, InstanceNotFoundException, RemoteRequestException {
        return (AttachmentInstance) getResourceInstance(orderId, federationTokenValue, InstanceType.ATTACHMENT);
    }

    public void deleteAttachment(String orderId, String federationTokenValue) throws UnauthenticatedException,
            UnauthorizedException, OrderManagementException {
        deleteOrder(orderId, federationTokenValue, InstanceType.ATTACHMENT);
    }

    public Map<String, String> getAllImages(String memberId, String federationTokenValue) throws
            UnauthenticatedException, UnauthorizedException, PropertyNotSpecifiedException, TokenCreationException,
            RemoteRequestException, ImageException {

        this.aaController.authenticate(federationTokenValue);

        if(memberId == null) {
            memberId = PropertiesHolder.getInstance().getProperty(ConfigurationConstants.LOCAL_MEMBER_ID);
        }

        FederationUser federationUser = this.aaController.getFederationUser(federationTokenValue);
        this.aaController.authorize(federationUser, Operation.GET_ALL_IMAGES);

        CloudConnector cloudConnector = CloudConnectorFactory.getInstance().getCloudConnector(memberId);
        return cloudConnector.getAllImages(federationUser);
    }

    public Image getImage(String memberId, String imageId, String federationTokenValue) throws
            UnauthenticatedException, UnauthorizedException, RemoteRequestException, TokenCreationException,
            PropertyNotSpecifiedException, ImageException {

        this.aaController.authenticate(federationTokenValue);

        if(memberId == null) {
            memberId = PropertiesHolder.getInstance().getProperty(ConfigurationConstants.LOCAL_MEMBER_ID);
        }

        FederationUser federationUser = this.aaController.getFederationUser(federationTokenValue);
        this.aaController.authorize(federationUser, Operation.GET_IMAGE);

        CloudConnector cloudConnector = CloudConnectorFactory.getInstance().getCloudConnector(memberId);
        return (Image) cloudConnector.getImage(imageId, federationUser);
    }

    public void setAaController(AaController aaController) {
        this.aaController = aaController;
    }

    public void setOrderController(OrderController orderController) {
        this.orderController = orderController;
    }

    private String activateOrder(Order order, String federationTokenValue)
    			throws OrderManagementException, UnauthorizedException, UnauthenticatedException {

        this.aaController.authenticate(federationTokenValue);
        FederationUser federationUser = this.aaController.getFederationUser(federationTokenValue);
        this.aaController.authorize(federationUser, Operation.CREATE, order);

        order.setId(UUID.randomUUID().toString());
        order.setFederationUser(federationUser);

        String localMemberId = PropertiesHolder.getInstance().getProperty(ConfigurationConstants.LOCAL_MEMBER_ID);

        order.setRequestingMember(localMemberId);
        if (order.getProvidingMember() == null) {
            order.setProvidingMember(localMemberId);
        }
        OrderStateTransitioner.activateOrder(order);
        return order.getId();
    }

    private void deleteOrder(String orderId, String federationTokenValue, InstanceType instanceType)
    			throws UnauthenticatedException, UnauthorizedException, OrderManagementException {
        this.aaController.authenticate(federationTokenValue);

        FederationUser federationUser = this.aaController.getFederationUser(federationTokenValue);
        Order order = this.orderController.getOrder(orderId, federationUser, instanceType);
        this.aaController.authorize(federationUser, Operation.DELETE, order);

        this.orderController.deleteOrder(order);
    }

    private List<Order> getAllOrders(String federationTokenValue, InstanceType instanceType)
    			throws UnauthorizedException, UnauthenticatedException {
        this.aaController.authenticate(federationTokenValue);
        FederationUser federationUser = this.aaController.getFederationUser(federationTokenValue);
        this.aaController.authorize(federationUser, Operation.GET_ALL, instanceType);

        return this.orderController.getAllOrders(federationUser, instanceType);
    }

    private Instance getResourceInstance(String orderId, String federationTokenValue, InstanceType instanceType)
        throws UnauthenticatedException, UnauthorizedException, RequestException, TokenCreationException,
            PropertyNotSpecifiedException, InstanceNotFoundException, RemoteRequestException {
        this.aaController.authenticate(federationTokenValue);

        FederationUser federationUser = this.aaController.getFederationUser(federationTokenValue);
        Order order = this.orderController.getOrder(orderId, federationUser, instanceType);
        this.aaController.authorize(federationUser, Operation.GET, order);

        return this.orderController.getResourceInstance(order);
    }

    private Allocation getUserAllocation(String memberId, String federationTokenValue, InstanceType instanceType)
            throws UnauthenticatedException, UnauthorizedException {

        this.aaController.authenticate(federationTokenValue);
        FederationUser federationUser = this.aaController.getFederationUser(federationTokenValue);
        this.aaController.authorize(federationUser, Operation.GET_USER_ALLOCATION, instanceType);

        return this.orderController.getUserAllocation(memberId, federationUser, instanceType);
    }

    private Quota getUserQuota(String memberId, String federationTokenValue, InstanceType instanceType)
            throws UnauthenticatedException, QuotaException, UnauthorizedException, PropertyNotSpecifiedException,
            TokenCreationException, RemoteRequestException {

        this.aaController.authenticate(federationTokenValue);
        FederationUser federationUser = this.aaController.getFederationUser(federationTokenValue);
        this.aaController.authorize(federationUser, Operation.GET_USER_QUOTA, instanceType);

        CloudConnector cloudConnector = CloudConnectorFactory.getInstance().getCloudConnector(memberId);
        return cloudConnector.getUserQuota(federationUser, instanceType);
    }

    private <T extends Instance> List<T> getAllInstances(List<Order> orders, Class<T> tClass) throws TokenCreationException,
            RequestException, RemoteRequestException, InstanceNotFoundException, PropertyNotSpecifiedException, UnauthorizedException {
        List<T> instances = new ArrayList<>();
        for (Order order : orders) {
            Instance instance = this.orderController.getResourceInstance(order);
            // The user believes that the order id is actually the instance id.
            // So we need to set the instance id accordingly before returning the instance.
            instance.setId(order.getId());
            instances.add(tClass.cast(instance));
        }
        return instances;

    }
}
