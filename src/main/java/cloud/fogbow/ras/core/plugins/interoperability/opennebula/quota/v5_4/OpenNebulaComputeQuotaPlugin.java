package cloud.fogbow.ras.core.plugins.interoperability.opennebula.quota.v5_4;

import java.util.Properties;

import org.opennebula.client.Client;
import org.opennebula.client.group.Group;
import org.opennebula.client.user.User;
import org.opennebula.client.user.UserPool;

import cloud.fogbow.common.exceptions.FatalErrorException;
import cloud.fogbow.common.exceptions.FogbowException;
import cloud.fogbow.common.models.CloudUser;
import cloud.fogbow.common.util.PropertiesUtil;
import cloud.fogbow.ras.api.http.response.quotas.ComputeQuota;
import cloud.fogbow.ras.api.http.response.quotas.allocation.ComputeAllocation;
import cloud.fogbow.ras.core.plugins.interoperability.ComputeQuotaPlugin;
import cloud.fogbow.ras.core.plugins.interoperability.opennebula.OpenNebulaClientUtil;
import cloud.fogbow.ras.core.plugins.interoperability.opennebula.OpenNebulaConfigurationPropertyKeys;

public class OpenNebulaComputeQuotaPlugin implements ComputeQuotaPlugin<CloudUser> {

	private static final String GROUPS_ID_PATH = "GROUPS/ID";
	private static final String QUOTA_CPU_USED_PATH = "VM_QUOTA/VM/CPU_USED";
	private static final String QUOTA_MEMORY_USED_PATH = "VM_QUOTA/VM/MEMORY_USED";
	private static final String QUOTA_VMS_USED_PATH = "VM_QUOTA/VM/VMS_USED";
	private static final String QUOTA_CPU_PATH = "VM_QUOTA/VM/CPU";
	private static final String QUOTA_MEMORY_PATH = "VM_QUOTA/VM/MEMORY";
	private static final String QUOTA_VMS_PATH = "VM_QUOTA/VM/VMS";
	
	private static final int DEFAULT_RESOURCE_MAX_VALUE = -1;
	private static final int VALUE_DEFAULT_QUOTA_OPENNEBULA = -1;
	private static final int VALUE_UNLIMITED_QUOTA_OPENNEBULA = -2;
	
	private String endpoint;

	public OpenNebulaComputeQuotaPlugin(String confFilePath) throws FatalErrorException {
		Properties properties = PropertiesUtil.readProperties(confFilePath);
		this.endpoint = properties.getProperty(OpenNebulaConfigurationPropertyKeys.OPENNEBULA_RPC_ENDPOINT_KEY);
	}
	
	@Override
	public ComputeQuota getUserQuota(CloudUser cloudUser) throws FogbowException {
		Client client = OpenNebulaClientUtil.createClient(this.endpoint, cloudUser.getToken());
		UserPool userPool = OpenNebulaClientUtil.getUserPool(client);
		User user = OpenNebulaClientUtil.getUser(userPool, cloudUser.getId());
		String maxCpuByUser = user.xpath(QUOTA_CPU_PATH);
		String maxMemoryByUser = user.xpath(QUOTA_MEMORY_PATH);
		String maxInstancesByUser = user.xpath(QUOTA_VMS_PATH);
		String cpuInUseByUser = user.xpath(QUOTA_CPU_USED_PATH);
		String memoryInUseByUser = user.xpath(QUOTA_MEMORY_USED_PATH);
		String instancesInUseByUser = user.xpath(QUOTA_VMS_USED_PATH);
		
		String groupId = user.xpath(GROUPS_ID_PATH);
		int id = Integer.parseInt(groupId);
		
		Group group = OpenNebulaClientUtil.getGroup(client, id);
		String maxCpuByGroup = group.xpath(QUOTA_CPU_PATH);
		String maxMemoryByGroup = group.xpath(QUOTA_MEMORY_PATH);
		String maxInstancesByGroup = group.xpath(QUOTA_VMS_PATH);
		String cpuInUseByGroup = group.xpath(QUOTA_CPU_USED_PATH);
		String memoryInUseByGroup = group.xpath(QUOTA_MEMORY_USED_PATH);
		String instancesInUseByGroup = group.xpath(QUOTA_VMS_USED_PATH);
		
		ResourceQuota resourceQuota = getQuota(maxCpuByUser, cpuInUseByUser, maxCpuByGroup, cpuInUseByGroup);
		Integer maxCpu = resourceQuota.getMaxResource();
		Integer cpuInUse = resourceQuota.getResourceInUse();
		
		resourceQuota = getQuota(maxMemoryByUser, memoryInUseByUser, maxMemoryByGroup, memoryInUseByGroup);
		Integer maxMemory = resourceQuota.getMaxResource();
		Integer memoryInUse = resourceQuota.getResourceInUse();
		
		resourceQuota = getQuota(maxInstancesByUser, instancesInUseByUser, maxInstancesByGroup, instancesInUseByGroup);
		Integer maxNumberInstances = resourceQuota.getMaxResource();
		Integer instancesInUse = resourceQuota.getResourceInUse();
		
		ComputeAllocation totalAllocation = new ComputeAllocation(maxCpu, maxMemory, maxNumberInstances);
		ComputeAllocation usedAllocation = new ComputeAllocation(cpuInUse, memoryInUse, instancesInUse);
		
		ComputeQuota computeQuota = new ComputeQuota(totalAllocation, usedAllocation);
		
		return computeQuota;
	}

	private ResourceQuota getQuota(String maxUserResource, String resourceInUseByUser, String maxGroupResource, String resourceInUseByGroup) {
		if (isValidNumber(maxUserResource) && isValidNumber(maxGroupResource)) {
			if (isUnlimitedOrDefaultQuota(maxUserResource)){
				maxUserResource = String.valueOf(DEFAULT_RESOURCE_MAX_VALUE);
			}
			if (isUnlimitedOrDefaultQuota(maxGroupResource)){
				maxGroupResource = String.valueOf(DEFAULT_RESOURCE_MAX_VALUE);
			}
			if (isUserSmallerQuota(maxUserResource, maxGroupResource)) {
				return getUserResouceQuota(maxUserResource, resourceInUseByUser);
			} else {
				return getUserResouceQuota(maxGroupResource, resourceInUseByGroup);
			}
		} else if (isValidNumber(maxUserResource)) {
			if (isUnlimitedOrDefaultQuota(maxUserResource)){
				maxUserResource = String.valueOf(DEFAULT_RESOURCE_MAX_VALUE);
			}
			return getUserResouceQuota(maxUserResource, resourceInUseByUser);
		} else if (isValidNumber(maxGroupResource)) {
			if (isUnlimitedOrDefaultQuota(maxGroupResource)){
				maxGroupResource = String.valueOf(DEFAULT_RESOURCE_MAX_VALUE);
			}
			return getUserResouceQuota(maxGroupResource, resourceInUseByGroup);
		} else {
			String maxResource = String.valueOf(DEFAULT_RESOURCE_MAX_VALUE);
			String resourceInUse = String.valueOf(getBiggerValue(resourceInUseByUser, resourceInUseByGroup));
			return getUserResouceQuota(maxResource, resourceInUse);
		}
	}

	private ResourceQuota getUserResouceQuota(String maxUserResource, String resourceInUseByUser) {
		int maxResourceValue = parseToInteger(maxUserResource);
		int resourceInUseValue = parseToInteger(resourceInUseByUser);
		return new ResourceQuota(maxResourceValue, resourceInUseValue);
	}

	private int getBiggerValue(String userResource, String groupResource) {
		int userValue = parseToInteger(userResource);
		int groupValue = parseToInteger(groupResource);
		int resourceValue = Math.max(userValue, groupValue);
		return resourceValue;
	}
	
	private int parseToInteger(String number) {
		if (isValidNumber(number)) {
			return Integer.parseInt(number);
		}
		return 0;
	}
	
	private boolean isUserSmallerQuota(String userResource, String groupResource) {
		int userResourceValue = Integer.parseInt(userResource);
		int groupResourceValue = Integer.parseInt(groupResource);
		if (userResourceValue < groupResourceValue) {
			return true;
		}
		return false;
	}

	private boolean isUnlimitedOrDefaultQuota(String resource) {
		int resourceValue = Integer.parseInt(resource);
		return resourceValue == VALUE_DEFAULT_QUOTA_OPENNEBULA || resourceValue == VALUE_UNLIMITED_QUOTA_OPENNEBULA;
	}

	private boolean isValidNumber(String number) {
		try {
			Integer.parseInt(number);
		} catch (Exception e) {
			return false;
		}
		return true;
	}

	private static class ResourceQuota {
		
		private Integer maxResource;
		private Integer resourceInUse;
		
		public ResourceQuota(int maxResource, int resourceInUse) {
			this.maxResource = maxResource;
			this.resourceInUse = resourceInUse;
		}
		
		public Integer getResourceInUse() {	
			return resourceInUse;
		}
		
		public Integer getMaxResource() {
			return maxResource;
		}
	}
	
}
