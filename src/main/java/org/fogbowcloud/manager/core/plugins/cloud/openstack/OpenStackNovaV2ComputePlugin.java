package org.fogbowcloud.manager.core.plugins.cloud.openstack;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeSet;
import java.util.UUID;

import org.apache.http.client.HttpResponseException;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.HomeDir;
import org.fogbowcloud.manager.core.constants.DefaultConfigurationConstants;
import org.fogbowcloud.manager.core.exceptions.*;
import org.fogbowcloud.manager.core.models.HardwareRequirements;
import org.fogbowcloud.manager.core.models.instances.ComputeInstance;
import org.fogbowcloud.manager.core.models.instances.InstanceState;
import org.fogbowcloud.manager.core.models.ResourceType;
import org.fogbowcloud.manager.core.models.orders.ComputeOrder;
import org.fogbowcloud.manager.core.models.quotas.allocation.ComputeAllocation;
import org.fogbowcloud.manager.core.models.tokens.Token;
import org.fogbowcloud.manager.core.plugins.cloud.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.cloud.util.DefaultLaunchCommandGenerator;
import org.fogbowcloud.manager.core.plugins.cloud.util.LaunchCommandGenerator;
import org.fogbowcloud.manager.core.plugins.serialization.openstack.compute.v2.*;
import org.fogbowcloud.manager.util.PropertiesUtil;
import org.fogbowcloud.manager.util.connectivity.HttpRequestClientUtil;
import org.fogbowcloud.manager.util.connectivity.HttpRequestUtil;
import org.json.JSONArray;
import org.json.JSONObject;

public class OpenStackNovaV2ComputePlugin implements ComputePlugin {

	protected static final String COMPUTE_NOVAV2_URL_KEY = "openstack_nova_v2_url";
	protected static final String DEFAULT_NETWORK_ID_KEY = "default_network_id";

	protected static final String ID_JSON_FIELD = "id";
	protected static final String NAME_JSON_FIELD = "name";
	protected static final String SERVER_JSON_FIELD = "server";
	protected static final String FLAVOR_REF_JSON_FIELD = "flavorRef";
	protected static final String FLAVOR_JSON_FIELD = "flavor";
	protected static final String FLAVOR_ID_JSON_FIELD = "id";
	protected static final String IMAGE_JSON_FIELD = "imageRef";
	protected static final String USER_DATA_JSON_FIELD = "user_data";
	protected static final String NETWORK_JSON_FIELD = "networks";
	protected static final String STATUS_JSON_FIELD = "status";
	protected static final String DISK_JSON_FIELD = "disk";
	protected static final String VCPU_JSON_FIELD = "vcpus";
	protected static final String MEMORY_JSON_FIELD = "ram";
	protected static final String SECURITY_JSON_FIELD = "security_groups";
	protected static final String FLAVOR_JSON_OBJECT = "flavor";
	protected static final String FLAVOR_JSON_KEY = "flavors";
	protected static final String KEY_JSON_FIELD = "key_name";
	protected static final String PUBLIC_KEY_JSON_FIELD = "public_key";
	protected static final String KEYPAIR_JSON_FIELD = "keypair";
	protected static final String UUID_JSON_FIELD = "uuid";
	protected static final String FOGBOW_INSTANCE_NAME = "fogbow-instance-";
	protected static final String TENANT_ID = "tenantId";

	protected static final String SERVERS = "/servers";
	protected static final String SUFFIX_ENDPOINT_KEYPAIRS = "/os-keypairs";
	protected static final String SUFFIX_ENDPOINT_FLAVORS = "/flavors";
	protected static final String COMPUTE_V2_API_ENDPOINT = "/v2/";

	protected static final Logger LOGGER = Logger.getLogger(OpenStackNovaV2ComputePlugin.class);
	protected static final String ADDRESS_FIELD = "addresses";
	protected static final String PROVIDER_NETWORK_FIELD = "provider";
	protected static final String ADDR_FIELD = "addr";

	private TreeSet<HardwareRequirements> hardwareRequirementsList;
	private Properties properties;
	private HttpRequestClientUtil client;
	private LaunchCommandGenerator launchCommandGenerator;

	public OpenStackNovaV2ComputePlugin() throws FatalErrorException {
		HomeDir homeDir = HomeDir.getInstance();
		this.properties = PropertiesUtil.readProperties(homeDir.getPath() + File.separator
				+ DefaultConfigurationConstants.OPENSTACK_CONF_FILE_NAME);
		this.launchCommandGenerator = new DefaultLaunchCommandGenerator();
		instantiateOtherAttributes();
	}

	/** Constructor used for testing only */
	protected OpenStackNovaV2ComputePlugin(Properties properties, 
			LaunchCommandGenerator launchCommandGenerator,
			HttpRequestClientUtil client) {
		LOGGER.debug("Creating OpenStackNovaV2ComputePlugin with properties=" + properties.toString());
		this.properties = properties;
		this.launchCommandGenerator = launchCommandGenerator;
		this.client = client;
		this.hardwareRequirementsList = new TreeSet<HardwareRequirements>();
	}

	public String requestInstance(ComputeOrder computeOrder, Token localToken)
			throws FogbowManagerException, UnexpectedException {
		LOGGER.debug("Requesting instance with tokens=" + localToken);

		HardwareRequirements hardwareRequirements = findSmallestFlavor(computeOrder, localToken);
		String flavorId = hardwareRequirements.getFlavorId();
		String tenantId = getTenantId(localToken);
		List<String> networksId = resolveNetworksId(computeOrder);
		String imageId = computeOrder.getImageId();
		String userData = this.launchCommandGenerator.createLaunchCommand(computeOrder);
		String keyName = getKeyName(tenantId, localToken, computeOrder.getPublicKey());
		String endpoint = getComputeEndpoint(tenantId, SERVERS);
		String instanceId = null;

		try {
			instanceId = doRequestInstance(localToken, flavorId, networksId, imageId, userData, keyName, endpoint);

			synchronized (computeOrder) {
				ComputeAllocation actualAllocation = new ComputeAllocation(
						hardwareRequirements.getCpu(),
						hardwareRequirements.getRam(), 
						1);
				// When the ComputeOrder is remote, this field must be copied into its local counterpart
				// that is updated when the requestingMember receives the reply from the providingMember
				// (see RemoteFacade.java)
				computeOrder.setActualAllocation(actualAllocation);
			}
		} catch (HttpResponseException e) {
			OpenStackHttpToFogbowManagerExceptionMapper.map(e);
		} finally {
			if (keyName != null) {
				deleteKeyName(tenantId, localToken, keyName);
			}
		}
		return instanceId;
	}

	private String doRequestInstance(Token localToken, String flavorId, List<String> networksId, String imageId, String userData, String keyName, String endpoint) throws UnavailableProviderException, HttpResponseException {
		CreateRequest createBody = getRequestBody(imageId, flavorId, userData, keyName, networksId);

		JSONObject json = new JSONObject(createBody.toJson());
		String response = this.client.doPostRequest(endpoint, localToken, json);
		CreateResponse createResponse = CreateResponse.fromJson(response);

		return createResponse.getId();
	}

	@Override
	public ComputeInstance getInstance(String instanceId, Token localToken)
			throws FogbowManagerException, UnexpectedException {
		LOGGER.info("Getting instance " + instanceId + " with tokens " + localToken);

		String tenantId = getTenantId(localToken);
		String requestEndpoint = getComputeEndpoint(tenantId, SERVERS + "/" + instanceId);

		String jsonResponse = null;
		try {
			jsonResponse = this.client.doGetRequest(requestEndpoint, localToken);
		} catch (HttpResponseException e) {
			OpenStackHttpToFogbowManagerExceptionMapper.map(e);
		}

		LOGGER.debug("Getting instance from json: " + jsonResponse);

		ComputeInstance computeInstance = instanceFromJson(jsonResponse, localToken);
		return computeInstance;
	}

	@Override
	public void deleteInstance(String instanceId, Token localToken) throws FogbowManagerException, UnexpectedException {
		LOGGER.info("Deleting instance " + instanceId + " with tokens " + localToken);
		String endpoint = getComputeEndpoint(getTenantId(localToken), SERVERS + "/" + instanceId);
		try {
			this.client.doDeleteRequest(endpoint, localToken);
		} catch (HttpResponseException e) {
			OpenStackHttpToFogbowManagerExceptionMapper.map(e);
		}
	}

	private void instantiateOtherAttributes() {
		this.hardwareRequirementsList = new TreeSet<HardwareRequirements>();
		this.initClient();
	}

	private void initClient() {
		HttpRequestUtil.init();
		this.client = new HttpRequestClientUtil();
	}

	private String getTenantId(Token localToken) throws InvalidParameterException {
		Map<String, String> tokenAttr = localToken.getAttributes();
		String tenantId = tokenAttr.get(TENANT_ID);
		if (tenantId == null) {
			throw new InvalidParameterException("No tenantId in local token.");
		}
		return tenantId;
	}

	private List<String> resolveNetworksId(ComputeOrder computeOrder) {
		List<String> requestedNetworksId = new ArrayList<>();
		String defaultNetworkId = this.properties.getProperty(DEFAULT_NETWORK_ID_KEY);

		//We add the default network before any other network, because the order is very important to Openstack
		//request. Openstack will configure the routes to the external network by the first network found on request body.
		requestedNetworksId.add(defaultNetworkId);
		requestedNetworksId.addAll(computeOrder.getNetworksId());
		computeOrder.setNetworksId(requestedNetworksId);
		return requestedNetworksId;
	}

	private String getKeyName(String tenantId, Token localToken, String publicKey)
			throws FogbowManagerException, UnexpectedException {
		String keyName = null;

		if (publicKey != null && !publicKey.isEmpty()) {
			String osKeypairEndpoint = getComputeEndpoint(tenantId, SUFFIX_ENDPOINT_KEYPAIRS);

			keyName = getRandomUUID();
			CreateOsKeypairRequest request = new CreateOsKeypairRequest.Builder()
					.name(keyName)
					.publicKey(publicKey)
					.build();

			JSONObject body = new JSONObject(request.toJson());
			try {
				this.client.doPostRequest(osKeypairEndpoint, localToken, body);
			} catch (HttpResponseException e) {
				OpenStackHttpToFogbowManagerExceptionMapper.map(e);
			}
		}

		return keyName;
	}

	private String getComputeEndpoint(String tenantId, String suffix) {
		return this.properties.getProperty(COMPUTE_NOVAV2_URL_KEY) + COMPUTE_V2_API_ENDPOINT + tenantId + suffix;
	}

	private void deleteKeyName(String tenantId, Token localToken, String keyName) throws FogbowManagerException, UnexpectedException {
		String suffixEndpoint = SUFFIX_ENDPOINT_KEYPAIRS + "/" + keyName;
		String keyNameEndpoint = getComputeEndpoint(tenantId, suffixEndpoint);
		
		try {
			this.client.doDeleteRequest(keyNameEndpoint, localToken);
		} catch (HttpResponseException e) {
			OpenStackHttpToFogbowManagerExceptionMapper.map(e);
		}
	}

	private CreateRequest getRequestBody(String imageRef, String flavorRef, String userdata, String keyName,
									 List<String> networksIds) {
		List<CreateRequest.Network> networks = new ArrayList<>();
		List<CreateRequest.SecurityGroup> securityGroups = new ArrayList<>();
		for (String networkId : networksIds) {
			networks.add(new CreateRequest.Network(networkId));

			String defaultNetworkId = this.properties.getProperty(DEFAULT_NETWORK_ID_KEY);
			if (!networkId.equals(defaultNetworkId)) {
				String prefix = OpenStackV2NetworkPlugin.SECURITY_GROUP_PREFIX;
				String securityGroupName = prefix + "-" + networkId;
				securityGroups.add(new CreateRequest.SecurityGroup(securityGroupName));
			}
		}

		// do not specify security groups if no additional network was given
		securityGroups = securityGroups.size() == 0 ? null : securityGroups;

		String name = FOGBOW_INSTANCE_NAME + getRandomUUID();
		CreateRequest createRequest = new CreateRequest.Builder()
				.name(name)
				.imageReference(imageRef)
				.flavorReference(flavorRef)
				.userData(userdata)
				.keyName(keyName)
				.networks(networks)
				.securityGroups(securityGroups)
				.build();

		return createRequest;
	}

	private HardwareRequirements findSmallestFlavor(ComputeOrder computeOrder, Token localToken)
			throws UnexpectedException, FogbowManagerException {
		HardwareRequirements bestFlavor = getBestFlavor(computeOrder, localToken);
		if (bestFlavor == null) {
			throw new NoAvailableResourcesException();
		}
		return bestFlavor;
	}

	private HardwareRequirements getBestFlavor(ComputeOrder computeOrder, Token localToken) throws FogbowManagerException, UnexpectedException {
		updateFlavors(localToken);
		TreeSet<HardwareRequirements> hardwareRequirementsList = getHardwareRequirementsList();
		for (HardwareRequirements hardwareRequirements : hardwareRequirementsList) {
			if (hardwareRequirements.getCpu() >= computeOrder.getvCPU()
					&& hardwareRequirements.getRam() >= computeOrder.getMemory()
					&& hardwareRequirements.getDisk() >= computeOrder.getDisk()) {
				return hardwareRequirements;
			}
		}
		return null;
	}

	private void updateFlavors(Token localToken)
			throws FogbowManagerException, UnexpectedException {
		LOGGER.debug("Updating hardwareRequirements from OpenStack");

			String tenantId = getTenantId(localToken);
			String endpoint = getComputeEndpoint(tenantId, SUFFIX_ENDPOINT_FLAVORS);
			
			String jsonResponseFlavors = null;
			try {
				jsonResponseFlavors = this.client.doGetRequest(endpoint, localToken);
			} catch (HttpResponseException e) {
				OpenStackHttpToFogbowManagerExceptionMapper.map(e);
			}

			List<String> flavorsId = new ArrayList<>();

			JSONArray jsonArrayFlavors = new JSONObject(jsonResponseFlavors).getJSONArray(FLAVOR_JSON_KEY);

			for (int i = 0; i < jsonArrayFlavors.length(); i++) {
				JSONObject itemFlavor = jsonArrayFlavors.getJSONObject(i);
				flavorsId.add(itemFlavor.getString(ID_JSON_FIELD));
			}

			TreeSet<HardwareRequirements> newHardwareRequirements = detailFlavors(endpoint, localToken, flavorsId);

			setHardwareRequirementsList(newHardwareRequirements);

	}

	private TreeSet<HardwareRequirements> detailFlavors(String endpoint, Token localToken, List<String> flavorsIds)
			throws FogbowManagerException, UnexpectedException 
			{
		TreeSet<HardwareRequirements> newHardwareRequirements = new TreeSet<>();
		TreeSet<HardwareRequirements> flavorsCopy = new TreeSet<>(getHardwareRequirementsList());

		for (String flavorId : flavorsIds) {
			boolean containsFlavorForCaching = false;

			for (HardwareRequirements flavor : flavorsCopy) {
				if (flavor.getFlavorId().equals(flavorId)) {
					containsFlavorForCaching = true;
					newHardwareRequirements.add(flavor);
					break;
				}
			}

			if (!containsFlavorForCaching) {
				String newEndpoint = endpoint + "/" + flavorId;
				
				String getJsonResponse = null;
				
				try {
					getJsonResponse = this.client.doGetRequest(newEndpoint, localToken);
				} catch (HttpResponseException e) {
					OpenStackHttpToFogbowManagerExceptionMapper.map(e);
				}

				GetFlavorResponse getFlavorResponse = GetFlavorResponse.fromJson(getJsonResponse);

				String id = getFlavorResponse.getId();
				String name = getFlavorResponse.getName();
				int disk = getFlavorResponse.getDisk();
				int memory = getFlavorResponse.getMemory();
				int vcpusCount = getFlavorResponse.getVcpusCount();

				newHardwareRequirements.add(new HardwareRequirements(name, id, vcpusCount, memory, disk));
			}
		}

		return newHardwareRequirements;
	}

	private ComputeInstance instanceFromJson(String getRawResponse, Token localToken) throws FogbowManagerException, UnexpectedException {
		GetResponse getResponse = GetResponse.fromJson(getRawResponse);

		String flavorId = getResponse.getFlavor().getId();
		HardwareRequirements hardwareRequirements = getFlavorById(flavorId, localToken);

		if (hardwareRequirements == null) {
			throw new NoAvailableResourcesException("No matching flavor");
		}

		int vcpusCount = hardwareRequirements.getCpu();
		int memory = hardwareRequirements.getRam();
		int disk = hardwareRequirements.getDisk();

		String openStackState = getResponse.getStatus();
		InstanceState fogbowState = OpenStackStateMapper.map(ResourceType.COMPUTE, openStackState);

		String instanceId = getResponse.getId();
		String hostName = getResponse.getName();

		GetResponse.Addresses addressesContainer = getResponse.getAddresses();

		String address = "";
		if (addressesContainer != null) {
			GetResponse.Address[] addresses = addressesContainer.getProviderAddresses();
			boolean firstAddressEmpty = addresses == null || addresses.length == 0 || addresses[0].getAddress() == null;
			address = firstAddressEmpty ? "" : addresses[0].getAddress();
		}

		ComputeInstance computeInstance = new ComputeInstance(instanceId,
				fogbowState, hostName, vcpusCount, memory, disk, address);
		return computeInstance;
	}

	private HardwareRequirements getFlavorById(String id, Token localToken) throws FogbowManagerException, UnexpectedException {
		updateFlavors(localToken);
		TreeSet<HardwareRequirements> flavorsCopy = new TreeSet<>(getHardwareRequirementsList());
		for (HardwareRequirements hardwareRequirements : flavorsCopy) {
			if (hardwareRequirements.getFlavorId().equals(id)) {
				return hardwareRequirements;
			}
		}
		return null;
	}

	protected String getRandomUUID() {
		return UUID.randomUUID().toString();
	}
	
	private TreeSet<HardwareRequirements> getHardwareRequirementsList() {
		synchronized (this.hardwareRequirementsList) {
			return new TreeSet<HardwareRequirements>(this.hardwareRequirementsList);
		}
	}
	
	private void setHardwareRequirementsList(TreeSet<HardwareRequirements> hardwareRequirementsList) {
		synchronized (this.hardwareRequirementsList) {
			this.hardwareRequirementsList = hardwareRequirementsList;
		}
	}
}
