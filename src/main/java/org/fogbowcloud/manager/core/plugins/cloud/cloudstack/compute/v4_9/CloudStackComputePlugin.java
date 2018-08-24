package org.fogbowcloud.manager.core.plugins.cloud.cloudstack.compute.v4_9;

import org.apache.commons.lang.StringUtils;
import org.apache.http.client.HttpResponseException;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.HomeDir;
import org.fogbowcloud.manager.core.constants.DefaultConfigurationConstants;
import org.fogbowcloud.manager.core.exceptions.*;
import org.fogbowcloud.manager.core.models.ResourceType;
import org.fogbowcloud.manager.core.models.instances.ComputeInstance;
import org.fogbowcloud.manager.core.models.instances.InstanceState;
import org.fogbowcloud.manager.core.models.orders.ComputeOrder;
import org.fogbowcloud.manager.core.models.tokens.CloudStackToken;
import org.fogbowcloud.manager.core.plugins.cloud.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.cloud.cloudstack.CloudStackHttpToFogbowManagerExceptionMapper;
import org.fogbowcloud.manager.core.plugins.cloud.cloudstack.CloudStackStateMapper;
import org.fogbowcloud.manager.core.plugins.cloud.cloudstack.CloudStackUrlUtil;
import org.fogbowcloud.manager.util.PropertiesUtil;
import org.fogbowcloud.manager.util.connectivity.HttpRequestClientUtil;
import org.fogbowcloud.manager.util.connectivity.HttpRequestUtil;

import java.io.File;
import java.util.*;

public class CloudStackComputePlugin implements ComputePlugin<CloudStackToken> {

    private static final Logger LOGGER = Logger.getLogger(CloudStackComputePlugin.class);

    private static final String ZONE_ID_KEY = "zone_id";
    private static final String EXPUNGE_ON_DESTROY_KEY = "compute_cloudstack_expunge_on_destroy";

    private Properties properties;
    private HttpRequestClientUtil client;

    private String zoneId;
    private String expungeOnDestroy;

    public CloudStackComputePlugin() throws FatalErrorException {
        HomeDir homeDir = HomeDir.getInstance();
        this.properties = PropertiesUtil.readProperties(homeDir.getPath() + File.separator
                + DefaultConfigurationConstants.CLOUDSTACK_CONF_FILE_NAME);

        this.zoneId = properties.getProperty(ZONE_ID_KEY);
        this.expungeOnDestroy = properties.getProperty(EXPUNGE_ON_DESTROY_KEY, "true");

        initClient();
    }

    private void initClient() {
        HttpRequestUtil.init();
        this.client = new HttpRequestClientUtil();
    }

    @Override
    public String requestInstance(ComputeOrder computeOrder, CloudStackToken cloudStackToken)
            throws FogbowManagerException, UnexpectedException {
        String templateId = computeOrder.getImageId();
        if (templateId == null || this.zoneId == null) {
            throw new InvalidParameterException();
        }

        // FIXME(pauloewerton): should this be creating a cloud-init script for cloudstack?
        String userData = Base64.getEncoder().encodeToString(computeOrder.getUserData().getExtraUserDataFileContent().getBytes());
        String networksId = StringUtils.join(computeOrder.getNetworksId(), ",");

        String serviceOfferingId = getServiceOfferingId(computeOrder.getvCPU(), computeOrder.getMemory(), cloudStackToken);
        if (serviceOfferingId == null) {
            throw new NoAvailableResourcesException();
        }

        int disk = computeOrder.getDisk();
        String diskOfferingId = disk > 0 ? getDiskOfferingId(disk, cloudStackToken) : null;

        // NOTE(pauloewerton): diskofferingid and hypervisor are required in case of ISO image. I haven't
        // found any clue pointing that ISO images were being used in mono though.
        DeployComputeRequest request = new DeployComputeRequest.Builder()
                .serviceOfferingId(serviceOfferingId)
                .templateId(templateId)
                .zoneId(this.zoneId)
                .diskOfferingId(diskOfferingId)
                .userData(userData)
                .networksId(networksId)
                .build();

        CloudStackUrlUtil.sign(request.getUriBuilder(), cloudStackToken.getTokenValue());

        String jsonResponse = null;
        try {
            jsonResponse = this.client.doGetRequest(request.getUriBuilder().toString(), cloudStackToken);
        } catch (HttpResponseException e) {
            CloudStackHttpToFogbowManagerExceptionMapper.map(e);
        }

        DeployComputeResponse response = DeployComputeResponse.fromJson(jsonResponse);

        return response.getId();
    }

    public String getServiceOfferingId(int vcpusRequirement, int memoryRequirement, CloudStackToken cloudStackToken)
            throws NoAvailableResourcesException {
        GetServiceOfferingsResponse serviceOfferingsResponse = getServiceOfferings(cloudStackToken);
        List<GetServiceOfferingsResponse.ServiceOffering> serviceOfferings = serviceOfferingsResponse.getServiceOfferings();

        if (serviceOfferings != null) {
            for (GetServiceOfferingsResponse.ServiceOffering serviceOffering : serviceOfferings) {
                if (serviceOffering.getVcpus() >= vcpusRequirement && serviceOffering.getMemory() >= memoryRequirement) {
                    return serviceOffering.getId();
                }
            }
        } else {
            throw new NoAvailableResourcesException();
        }

        return null;
    }

    public String getDiskOfferingId(int diskSize, CloudStackToken cloudStackToken) {
        GetDiskOfferingsResponse serviceOfferingsResponse = getDiskOfferings(cloudStackToken);
        List<GetServiceOfferingsResponse.ServiceOffering> serviceOfferings = serviceOfferingsResponse.getServiceOfferings();

        if (serviceOfferings != null) {
            for (GetServiceOfferingsResponse.ServiceOffering serviceOffering : serviceOfferings) {
                if (serviceOffering.getVcpus() >= requirements.get("vcpus") &&
                        serviceOffering.getMemory() >= requirements.get("memory")) {
                    return serviceOffering.getId();
                }
            }
        }

        return null;
    }

    @Override
    public ComputeInstance getInstance(String computeInstanceId, CloudStackToken cloudStackToken)
            throws FogbowManagerException {
        GetComputeRequest request = new GetComputeRequest.Builder()
                .id(computeInstanceId)
                .build();

        CloudStackUrlUtil.sign(request.getUriBuilder(), cloudStackToken.getTokenValue());

        String jsonResponse = null;
        try {
            jsonResponse = this.client.doGetRequest(request.getUriBuilder().toString(), cloudStackToken);
        } catch (HttpResponseException e) {
            CloudStackHttpToFogbowManagerExceptionMapper.map(e);
        }

        GetComputeResponse computeResponse = GetComputeResponse.fromJson(jsonResponse);
        List<GetComputeResponse.VirtualMachine> vms = computeResponse.getVirtualMachines();
        if (vms != null) {
            return getComputeInstance(vms.get(0));
        } else {
            throw new InstanceNotFoundException();
        }
    }

    private ComputeInstance getComputeInstance(GetComputeResponse.VirtualMachine vm) throws FogbowManagerException {
        String instanceId = vm.getId();
        String hostName = vm.getName();
        int vcpusCount = vm.getCpuNumber();
        int memory = vm.getMemory();
        // TODO(pauloewerton): use volume plugin to request disk size
        int disk = 0;

        String cloudStackState = vm.getState();
        InstanceState fogbowState = CloudStackStateMapper.map(ResourceType.COMPUTE, cloudStackState);

        GetComputeResponse.Nic[] addresses = vm.getNic();
        String address = "";
        if (addresses != null) {
            boolean firstAddressEmpty = addresses == null || addresses.length == 0 || addresses[0].getIpAddress() == null;
            address = firstAddressEmpty ? "" : addresses[0].getIpAddress();
        }

        ComputeInstance computeInstance = new ComputeInstance(instanceId,
                fogbowState, hostName, vcpusCount, memory, disk, address);

        return computeInstance;
    }

    @Override
    public void deleteInstance(String computeInstanceId, CloudStackToken cloudStackToken)
            throws FogbowManagerException, UnexpectedException {
        DeleteInstanceRequest request = new DeleteInstanceRequest.Builder()
                .id(computeInstanceId)
                .build();

        CloudStackUrlUtil.sign(request.getUriBuilder(), cloudStackToken.getTokenValue());

        try {
            this.client.doGetRequest(request.getUriBuilder().toString(), cloudStackToken);
        } catch (HttpResponseException e) {
            CloudStackHttpToFogbowManagerExceptionMapper.map(e);
        }
    }
}
