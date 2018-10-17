package org.fogbowcloud.ras.core.plugins.interoperability.openstack.publicip.v2;

import org.apache.http.client.HttpResponseException;
import org.apache.log4j.Logger;
import org.fogbowcloud.ras.core.HomeDir;
import org.fogbowcloud.ras.core.constants.DefaultConfigurationConstants;
import org.fogbowcloud.ras.core.constants.Messages;
import org.fogbowcloud.ras.core.exceptions.FatalErrorException;
import org.fogbowcloud.ras.core.exceptions.FogbowRasException;
import org.fogbowcloud.ras.core.exceptions.UnavailableProviderException;
import org.fogbowcloud.ras.core.exceptions.UnexpectedException;
import org.fogbowcloud.ras.core.models.ResourceType;
import org.fogbowcloud.ras.core.models.instances.InstanceState;
import org.fogbowcloud.ras.core.models.instances.PublicIpInstance;
import org.fogbowcloud.ras.core.models.orders.PublicIpOrder;
import org.fogbowcloud.ras.core.models.tokens.OpenStackV3Token;
import org.fogbowcloud.ras.core.plugins.interoperability.PublicIpPlugin;
import org.fogbowcloud.ras.core.plugins.interoperability.openstack.OpenStackHttpToFogbowRasExceptionMapper;
import org.fogbowcloud.ras.core.plugins.interoperability.openstack.OpenStackStateMapper;
import org.fogbowcloud.ras.core.plugins.interoperability.openstack.compute.v2.OpenStackComputePlugin;
import org.fogbowcloud.ras.core.plugins.interoperability.openstack.network.v2.*;
import org.fogbowcloud.ras.core.plugins.interoperability.openstack.publicip.v2.CreateFloatingIpResponse.FloatingIp;
import org.fogbowcloud.ras.core.plugins.interoperability.openstack.publicip.v2.GetNetworkPortsResponse.Port;
import org.fogbowcloud.ras.util.PropertiesUtil;
import org.fogbowcloud.ras.util.connectivity.HttpRequestClientUtil;
import org.fogbowcloud.ras.util.connectivity.HttpRequestUtil;

import java.net.URISyntaxException;
import java.util.List;
import java.util.Properties;

import static org.fogbowcloud.ras.core.plugins.interoperability.openstack.compute.v2.OpenStackComputePlugin.COMPUTE_NOVAV2_URL_KEY;
import static org.fogbowcloud.ras.core.plugins.interoperability.openstack.compute.v2.OpenStackComputePlugin.COMPUTE_V2_API_ENDPOINT;

public class OpenStackPublicIpPlugin implements PublicIpPlugin<OpenStackV3Token> {

    private static final Logger LOGGER = Logger.getLogger(OpenStackPublicIpPlugin.class);

    protected static final String NETWORK_NEUTRONV2_URL_KEY = OpenStackNetworkPlugin.NETWORK_NEUTRONV2_URL_KEY;
    protected static final String DEFAULT_NETWORK_ID_KEY = OpenStackComputePlugin.DEFAULT_NETWORK_ID_KEY;
    protected static final String EXTERNAL_NETWORK_ID_KEY = OpenStackNetworkPlugin.KEY_EXTERNAL_GATEWAY_INFO;

    protected static final String SUFFIX_ENDPOINT_FLOATINGIPS = "/floatingips";
    protected static final String NETWORK_V2_API_ENDPOINT = "/v2.0";
    protected static final String SUFFIX_ENDPOINT_PORTS = "/ports";
    protected static final String SUFFIX_ENDPOINT_SECURITY_GROUP_RULES = "/security-group-rules";
    protected static final String SUFFIX_ENDPOINT_SECURITY_GROUPS = "/security-groups";

    private static final int MAXIMUM_PORTS_SIZE = 1;
    public static final String SECURITY_GROUP_PROTOCOL_ANY = "any";
    public static final String SECURITY_GROUP_DIRECTION_INGRESS = "ingress";

    private Properties properties;
    private HttpRequestClientUtil client;

    public OpenStackPublicIpPlugin() {
        this(true);
    }

    public OpenStackPublicIpPlugin(boolean checkProperties) {
        this.properties = PropertiesUtil.readProperties(HomeDir.getPath() +
                DefaultConfigurationConstants.OPENSTACK_CONF_FILE_NAME);
        initClient();
        checkProperties(checkProperties);
    }

    @Override
    public String requestInstance(PublicIpOrder publicIpOrder, String computeInstanceId, OpenStackV3Token openStackV3Token)
            throws FogbowRasException, UnexpectedException {
        String securityGroupId = null;
        String securityGroupName = null; // TODO random name?
        try {
            securityGroupId = createSecurityGroup(openStackV3Token, securityGroupName);
            addAllowAllRule(securityGroupId, openStackV3Token);
            addSecurityGroupToCompute(securityGroupName, computeInstanceId, openStackV3Token);
        } catch (UnexpectedException e) {
            if (securityGroupId != null) {
                removeSecurityGroup(securityGroupId, openStackV3Token);
            }
            throw new UnexpectedException(Messages.Exception.UNABLE_TO_CREATE_AND_ASSOCIATE_SECURITY_GROUP, e);
        }

        // Network port id is the connection between the virtual machine and the network
        String networkPortId = getNetworkPortIp(computeInstanceId, openStackV3Token);
        String floatingNetworkId = getExternalNetworkId();
        String projectId = openStackV3Token.getProjectId();

        CreateFloatingIpRequest createFloatingIpRequest = new CreateFloatingIpRequest.Builder()
                .floatingNetworkId(floatingNetworkId)
                .projectId(projectId)
                .portId(networkPortId)
                .build();
        String body = createFloatingIpRequest.toJson();

        String responsePostFloatingIp = null;
        try {
            String floatingIpEndpoint = getFloatingIpEndpoint();
            responsePostFloatingIp = this.client.doPostRequest(floatingIpEndpoint, openStackV3Token, body);
        } catch (HttpResponseException e) {
            OpenStackHttpToFogbowRasExceptionMapper.map(e);
        }
        CreateFloatingIpResponse createFloatingIpResponse = CreateFloatingIpResponse.fromJson(responsePostFloatingIp);

        FloatingIp floatingIp = createFloatingIpResponse.getFloatingIp();
        return floatingIp.getId();
    }

    @Override
    public void deleteInstance(String floatingIpId, OpenStackV3Token openStackV3Token) throws FogbowRasException, UnexpectedException {
        try {
            String floatingIpEndpointPrefix = getFloatingIpEndpoint();
            String endpoint = String.format("%s/%s", floatingIpEndpointPrefix, floatingIpId);
            this.client.doDeleteRequest(endpoint, openStackV3Token);
        } catch (HttpResponseException e) {
            OpenStackHttpToFogbowRasExceptionMapper.map(e);
        }
    }

    @Override
    public PublicIpInstance getInstance(String publicIpInstanceId, OpenStackV3Token openStackV3Token)
            throws FogbowRasException, UnexpectedException {
        String responseGetFloatingIp = null;
        try {
            String floatingIpEndpointPrefix = getFloatingIpEndpoint();
            String endpoint = String.format("%s/%s", floatingIpEndpointPrefix, publicIpInstanceId);
            responseGetFloatingIp = this.client.doGetRequest(endpoint, openStackV3Token);
        } catch (HttpResponseException e) {
            OpenStackHttpToFogbowRasExceptionMapper.map(e);
        }

        GetFloatingIpResponse getFloatingIpResponse = GetFloatingIpResponse.fromJson(responseGetFloatingIp);

        String floatingIpStatus = getFloatingIpResponse.getFloatingIp().getStatus();
        InstanceState fogbowState = OpenStackStateMapper.map(ResourceType.PUBLIC_IP, floatingIpStatus);

        String ipAddressId = getFloatingIpResponse.getFloatingIp().getId();
        String floatingIpAddress = getFloatingIpResponse.getFloatingIp().getFloatingIpAddress();
        PublicIpInstance publicIpInstance = new PublicIpInstance(ipAddressId, fogbowState, floatingIpAddress);
        return publicIpInstance;
    }

    private void addSecurityGroupToCompute(String securityGroupName, String computeInstanceId, OpenStackV3Token openStackV3Token) throws FogbowRasException, UnexpectedException {
        AddSecurityGroupToServerRequest request = new AddSecurityGroupToServerRequest.Builder()
                .name(securityGroupName)
                .build();

        try {
            String computeEndpoint = getComputeEndpoint(openStackV3Token.getProjectId());
            computeEndpoint = String.format("%s/%s", computeEndpoint, computeInstanceId);
            this.client.doPostRequest(computeEndpoint, openStackV3Token, request.toJson());
        } catch (HttpResponseException e) {
            OpenStackHttpToFogbowRasExceptionMapper.map(e);
        }
    }

    private void addAllowAllRule(String securityGroupId, OpenStackV3Token openStackV3Token) throws UnexpectedException, FogbowRasException {
        CreateSecurityGroupRuleRequest request = new CreateSecurityGroupRuleRequest.Builder()
                .securityGroupId(securityGroupId)
                .direction(SECURITY_GROUP_DIRECTION_INGRESS)
                .protocol(SECURITY_GROUP_PROTOCOL_ANY)
                .build();

        try {
            this.client.doPostRequest(getSecurityGroupRulesApiEndpoint(), openStackV3Token, request.toJson());
        } catch (HttpResponseException e) {
            OpenStackHttpToFogbowRasExceptionMapper.map(e);
        }
    }

    private String createSecurityGroup(OpenStackV3Token openStackV3Token, String securityGroupName) throws UnexpectedException, FogbowRasException {
        CreateSecurityGroupRequest request = new CreateSecurityGroupRequest.Builder()
                .projectId(openStackV3Token.getProjectId())
                .name(securityGroupName)
                .build();

        String response = null;
        try {
            response = this.client.doPostRequest(getSecurityGroupsApiEndpoint(), openStackV3Token, request.toJson());
        } catch (HttpResponseException e) {
            OpenStackHttpToFogbowRasExceptionMapper.map(e);
        }

        CreateSecurityGroupResponse securityGroupResponse = CreateSecurityGroupResponse.fromJson(response);
        return securityGroupResponse.getId();
    }

    private void removeSecurityGroup(String securityGroupId, OpenStackV3Token openStackV3Token) throws UnexpectedException, FogbowRasException {
        try {
            String endpoint = String.format("%s/%s", getSecurityGroupsApiEndpoint(), securityGroupId);
            this.client.doDeleteRequest(endpoint, openStackV3Token);
        } catch (HttpResponseException e) {
            OpenStackHttpToFogbowRasExceptionMapper.map(e);
        }
    }

    protected String getNetworkPortIp(String computeInstanceId, OpenStackV3Token openStackV3Token)
            throws FogbowRasException, UnexpectedException {
        String defaulNetworkId = getDefaultNetworkId();
        String networkPortsEndpointBase = getNetworkPortsEndpoint();

        GetNetworkPortsResquest getNetworkPortsResquest = null;
        try {
            getNetworkPortsResquest = new GetNetworkPortsResquest.Builder()
                    .url(networkPortsEndpointBase).deviceId(computeInstanceId).networkId(defaulNetworkId).build();
        } catch (URISyntaxException e) {
            String errorMsg = String.format(Messages.Exception.WRONG_URI_SYNTAX, networkPortsEndpointBase);
            throw new FogbowRasException(errorMsg, e);
        }

        String responseGetPorts = null;
        try {
            String networkPortsEndpoint = getNetworkPortsResquest.getUrl();
            responseGetPorts = this.client.doGetRequest(networkPortsEndpoint, openStackV3Token);
        } catch (HttpResponseException e) {
            OpenStackHttpToFogbowRasExceptionMapper.map(e);
        }

        GetNetworkPortsResponse networkPortsResponse = GetNetworkPortsResponse.fromJson(responseGetPorts);

        String networkPortId = null;
        List<Port> ports = networkPortsResponse.getPorts();
        // One is the maximum threshold of ports in the fogbow for default network
        if (isValidPorts(ports)) {
            return networkPortId = ports.get(0).getId();
        }

        throwPortsException(ports, computeInstanceId, defaulNetworkId);
        return networkPortId;
    }

    protected void throwPortsException(List<Port> ports, String computeInstanceId, String defaulNetworkId) throws FogbowRasException {
        String errorMsg = null;
        if (ports == null || ports.size() == 0) {
            errorMsg = String.format(Messages.Exception.PORT_NOT_FOUND, computeInstanceId, defaulNetworkId);
        } else {
            errorMsg = String.format(Messages.Exception.INVALID_PORT_SIZE, String.valueOf(ports.size()), computeInstanceId, defaulNetworkId);
        }
        throw new FogbowRasException(errorMsg);
    }

    protected void checkProperties(boolean checkProperties) {
        if (!checkProperties) {
            return;
        }

        String defaultNetworkId = getDefaultNetworkId();
        if (defaultNetworkId == null || defaultNetworkId.isEmpty()) {
            throw new FatalErrorException(Messages.Fatal.DEFAULT_NETWORK_NOT_FOUND);
        }
        String externalNetworkId = getExternalNetworkId();
        if (externalNetworkId == null || externalNetworkId.isEmpty()) {
            throw new FatalErrorException(Messages.Fatal.EXTERNAL_NETWORK_NOT_FOUND);
        }
        String neutroApiEndpoint = getNeutronApiEndpoint();
        if (neutroApiEndpoint == null || neutroApiEndpoint.isEmpty()) {
            throw new FatalErrorException(Messages.Fatal.NEUTRON_ENDPOINT_NOT_FOUND);
        }
    }

    protected String getDefaultNetworkId() {
        return this.properties.getProperty(DEFAULT_NETWORK_ID_KEY);
    }

    protected String getExternalNetworkId() {
        return this.properties.getProperty(EXTERNAL_NETWORK_ID_KEY);
    }

    protected String getSecurityGroupsApiEndpoint() {
        return getNeutronApiEndpoint() + NETWORK_V2_API_ENDPOINT + SUFFIX_ENDPOINT_SECURITY_GROUPS;
    }

    protected String getSecurityGroupRulesApiEndpoint() {
        return getNeutronApiEndpoint() + NETWORK_V2_API_ENDPOINT + SUFFIX_ENDPOINT_SECURITY_GROUP_RULES;
    }

    protected String getNeutronApiEndpoint() {
        return this.properties.getProperty(NETWORK_NEUTRONV2_URL_KEY);
    }

    protected String getNetworkPortsEndpoint() {
        return getNeutronApiEndpoint() + NETWORK_V2_API_ENDPOINT + SUFFIX_ENDPOINT_PORTS;
    }

    protected String getFloatingIpEndpoint() {
        return getNeutronApiEndpoint() + NETWORK_V2_API_ENDPOINT + SUFFIX_ENDPOINT_FLOATINGIPS;
    }

    private String getComputeEndpoint(String projectId) {
        return this.properties.getProperty(COMPUTE_NOVAV2_URL_KEY) + COMPUTE_V2_API_ENDPOINT + projectId;
    }

    protected boolean isValidPorts(List<Port> ports) {
        return ports != null && ports.size() == MAXIMUM_PORTS_SIZE;
    }

    private void initClient() {
        HttpRequestUtil.init();
        this.client = new HttpRequestClientUtil();
    }

    protected void setClient(HttpRequestClientUtil client) {
        this.client = client;
    }

    public void setProperties(Properties properties) {
        this.properties = properties;
    }
}
