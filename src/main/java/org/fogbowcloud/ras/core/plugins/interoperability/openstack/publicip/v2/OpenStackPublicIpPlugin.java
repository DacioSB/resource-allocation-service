package org.fogbowcloud.ras.core.plugins.interoperability.openstack.publicip.v2;

import java.net.URISyntaxException;
import java.util.List;
import java.util.Properties;

import org.apache.http.client.HttpResponseException;
import org.apache.log4j.Logger;
import org.fogbowcloud.ras.core.HomeDir;
import org.fogbowcloud.ras.core.constants.DefaultConfigurationConstants;
import org.fogbowcloud.ras.core.exceptions.FatalErrorException;
import org.fogbowcloud.ras.core.exceptions.FogbowRasException;
import org.fogbowcloud.ras.core.exceptions.UnexpectedException;
import org.fogbowcloud.ras.core.models.instances.PublicIpInstance;
import org.fogbowcloud.ras.core.models.orders.PublicIpOrder;
import org.fogbowcloud.ras.core.models.tokens.OpenStackV3Token;
import org.fogbowcloud.ras.core.plugins.interoperability.PublicIpPlugin;
import org.fogbowcloud.ras.core.plugins.interoperability.openstack.OpenStackHttpToFogbowRasExceptionMapper;
import org.fogbowcloud.ras.core.plugins.interoperability.openstack.compute.v2.OpenStackNovaV2ComputePlugin;
import org.fogbowcloud.ras.core.plugins.interoperability.openstack.network.v2.OpenStackV2NetworkPlugin;
import org.fogbowcloud.ras.core.plugins.interoperability.openstack.publicip.v2.CreateFloatingIpResponse.FloatingIp;
import org.fogbowcloud.ras.core.plugins.interoperability.openstack.publicip.v2.GetNetworkPortsResponse.Port;
import org.fogbowcloud.ras.util.PropertiesUtil;
import org.fogbowcloud.ras.util.connectivity.HttpRequestClientUtil;
import org.fogbowcloud.ras.util.connectivity.HttpRequestUtil;

public class OpenStackPublicIpPlugin implements PublicIpPlugin<OpenStackV3Token> {

	private static final Logger LOGGER = Logger.getLogger(OpenStackPublicIpPlugin.class);
	
	protected static final String NETWORK_NEUTRONV2_URL_KEY = OpenStackV2NetworkPlugin.NETWORK_NEUTRONV2_URL_KEY;	
	protected static final String DEFAULT_NETWORK_ID_KEY = OpenStackNovaV2ComputePlugin.DEFAULT_NETWORK_ID_KEY;
	protected static final String EXTERNAL_NETWORK_ID_KEY = "external_network_id";
	
	protected static final String SUFFIX_ENDPOINT_FLOATINGIPS = "/floatingips";	
	protected static final String NETWORK_V2_API_ENDPOINT = "/v2.0";
	protected static final String SUFFIX_ENDPOINT_PORTS = "/ports";

	private static final int MAXIMUM_PORTS_SIZE = 1;

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
        LOGGER.info("Creating floating ip in the " + computeInstanceId + " with tokens " + openStackV3Token);

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
	public void deleteInstance(String floatingIpId, OpenStackV3Token openStackV3Token) throws FogbowRasException, UnexpectedException  {
        LOGGER.info("Deleting floating ip " + floatingIpId + " with tokens " + openStackV3Token);     
        
        try {
        	String floatingIpEndpointPrefix = getFloatingIpEndpoint();
        	String endpoint = String.format("%s/%s", floatingIpEndpointPrefix, floatingIpId);
        	this.client.doDeleteRequest(endpoint, openStackV3Token);
        } catch (HttpResponseException e) {
            OpenStackHttpToFogbowRasExceptionMapper.map(e);
        }
	}		
	
	public PublicIpInstance getInstance(String publicIpOrderId, OpenStackV3Token openStackV3Token) 
			throws FogbowRasException, UnexpectedException {
		
		try {
			
			String floatingIpEndpointPrefix = getFloatingIpEndpoint();
        	// publicIdOrderId is worng. The correct is publicIpInstanceId
			String endpoint = String.format("%s/%s", floatingIpEndpointPrefix, publicIpOrderId);
			this.client.doGetRequest(endpoint, openStackV3Token);
		} catch (HttpResponseException e) {
			OpenStackHttpToFogbowRasExceptionMapper.map(e);
		}
		
		return null;
	}
	
	protected String getNetworkPortIp(String computeInstanceId, OpenStackV3Token openStackV3Token)
			throws FogbowRasException, UnexpectedException {
		LOGGER.debug("Searching the network port of the VM (" 
						+ computeInstanceId + ")  with tokens " + openStackV3Token);
		String defaulNetworkId = getDefaultNetworkId();
		String networkPortsEndpointBase = getNetworkPortsEndpoint();

		GetNetworkPortsResquest getNetworkPortsResquest = null;
		try {
			getNetworkPortsResquest = new GetNetworkPortsResquest.Builder()
					.url(networkPortsEndpointBase).deviceId(computeInstanceId).networkId(defaulNetworkId).build();
		} catch (URISyntaxException e) {
			String errorMsg = String.format("The endpoint(%s) syntax is wrong", networkPortsEndpointBase);
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
			errorMsg = String.format("None port found of the virtual machine(%s) and default network(%s) "
					, computeInstanceId, defaulNetworkId); 
		} else {
			errorMsg = String.format("Irregular ports size(%s) of the virtual machine(%s) and default network(%s) "
					, String.valueOf(ports.size()), computeInstanceId, defaulNetworkId);
		}
		throw new FogbowRasException(errorMsg);
	}
	
	protected void checkProperties(boolean checkProperties) {
		if (!checkProperties) {
			return;
		}
		
		String defaultNetworkId = getDefaultNetworkId();
		if (defaultNetworkId == null || defaultNetworkId.isEmpty()) {
        	throw new FatalErrorException("Default network not found");
        }
        String externalNetworkId = getExternalNetworkId();
		if (externalNetworkId == null || externalNetworkId.isEmpty()) {
        	throw new FatalErrorException("External network not found");
        }
        String neutroApiEndpoint = getNeutroApiEndpoint();
		if (neutroApiEndpoint == null || neutroApiEndpoint.isEmpty()) {
        	throw new FatalErrorException("Neutro endpoint not found");
        }
	}	
	
	protected String getDefaultNetworkId() {
		return this.properties.getProperty(DEFAULT_NETWORK_ID_KEY);
	}
	
	protected String getExternalNetworkId() {
		return this.properties.getProperty(EXTERNAL_NETWORK_ID_KEY);
	}
	
	protected String getNeutroApiEndpoint() {
		return this.properties.getProperty(NETWORK_NEUTRONV2_URL_KEY);
	}
	
	protected String getNetworkPortsEndpoint() {
        return getNeutroApiEndpoint() + NETWORK_V2_API_ENDPOINT + SUFFIX_ENDPOINT_PORTS;
    }

	
	protected String getFloatingIpEndpoint() {
        return getNeutroApiEndpoint() + NETWORK_V2_API_ENDPOINT + SUFFIX_ENDPOINT_FLOATINGIPS;
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
