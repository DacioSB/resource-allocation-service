package org.fogbowcloud.manager.core;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.constants.ConfigurationConstants;
import org.fogbowcloud.manager.core.exceptions.*;
import org.fogbowcloud.manager.core.constants.Operation;
import org.fogbowcloud.manager.core.models.ResourceType;
import org.fogbowcloud.manager.core.models.orders.Order;
import org.fogbowcloud.manager.core.models.tokens.FederationUserToken;
import org.fogbowcloud.manager.core.plugins.behavior.authorization.AuthorizationPlugin;
import org.fogbowcloud.manager.core.plugins.behavior.authentication.AuthenticationPlugin;
import org.fogbowcloud.manager.core.plugins.behavior.identity.FederationIdentityPlugin;

public class AaController {

    private static final Logger LOGGER = Logger.getLogger(AaController.class);

    private AuthenticationPlugin authenticationPlugin;
    private AuthorizationPlugin authorizationPlugin;
    private FederationIdentityPlugin federationIdentityPlugin;
    private String localMemberIdentity;

    public AaController(BehaviorPluginsHolder behaviorPluginsHolder) {
        this.authenticationPlugin = behaviorPluginsHolder.getAuthenticationPlugin();
        this.authorizationPlugin = behaviorPluginsHolder.getAuthorizationPlugin();
        this.federationIdentityPlugin = behaviorPluginsHolder.getFederationIdentityPlugin();
        this.localMemberIdentity = PropertiesHolder.getInstance().getProperty(ConfigurationConstants.LOCAL_MEMBER_ID);
    }

    public void authenticateAndAuthorize(FederationUserToken requester,
                                         Operation operation, ResourceType type)
            throws UnauthenticatedUserException, UnauthorizedRequestException, UnavailableProviderException {
        // Authenticate user based on the token received
        authenticate(requester);
        // Authorize the user based on user's attributes, requested operation and resource type
        authorize(requester, operation, type);
    }

    public void authenticateAndAuthorize(FederationUserToken requester, Operation operation, ResourceType type,
                                         Order order) throws FogbowManagerException {
        // Check if requested type matches order type
        if (!order.getType().equals(type)) throw new InstanceNotFoundException("Mismatching resource type");
        // Check whether requester owns order
        FederationUserToken orderOwner = order.getFederationUserToken();
        if (!orderOwner.getUserId().equals(requester.getUserId())) {
            throw new UnauthorizedRequestException("Requester does not own order");
        }
        // Authenticate user and get authorization to perform generic operation on the type of resource
        authenticateAndAuthorize(requester, operation, type);
    }

    public void remoteAuthenticateAndAuthorize(FederationUserToken federationUserToken, Operation operation,
                                                ResourceType type, String memberId) throws FogbowManagerException {
        if (!memberId.equals(this.localMemberIdentity)) {
            throw new InstanceNotFoundException("This is not the correct providing member");
        } else {
            authenticateAndAuthorize(federationUserToken, operation, type);
        }
    }

    public void remoteAuthenticateAndAuthorize(FederationUserToken federationUserToken, Operation operation,
                                                ResourceType type, Order order) throws FogbowManagerException {
        if (!order.getProvidingMember().equals(this.localMemberIdentity)) {
            throw new InstanceNotFoundException("This is not the correct providing member");
        } else {
            authenticateAndAuthorize(federationUserToken, operation, type, order);
        }
    }

    public FederationUserToken getFederationUser(String federationTokenValue) throws InvalidParameterException {
        return this.federationIdentityPlugin.createToken(federationTokenValue);
    }

    public void authenticate(FederationUserToken federationToken) throws UnauthenticatedUserException,
            UnavailableProviderException {
        if (!this.authenticationPlugin.isAuthentic(federationToken)) {
            throw new UnauthenticatedUserException();
        }
    }

    public void authorize(FederationUserToken federationUserToken, Operation operation, ResourceType type)
            throws UnauthorizedRequestException {
        if (!this.authorizationPlugin.isAuthorized(federationUserToken, operation, type)) {
            throw new UnauthorizedRequestException();
        }
    }
}
