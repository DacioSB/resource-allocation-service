package cloud.fogbow.ras.core.plugins.interoperability.azure.volume;

import cloud.fogbow.common.constants.AzureConstants;
import cloud.fogbow.common.exceptions.FogbowException;
import cloud.fogbow.common.exceptions.InstanceNotFoundException;
import cloud.fogbow.common.models.AzureUser;
import cloud.fogbow.common.util.PropertiesUtil;
import cloud.fogbow.ras.api.http.response.InstanceState;
import cloud.fogbow.ras.api.http.response.VolumeInstance;
import cloud.fogbow.ras.api.http.response.quotas.allocation.VolumeAllocation;
import cloud.fogbow.ras.constants.Messages;
import cloud.fogbow.ras.core.models.ResourceType;
import cloud.fogbow.ras.core.models.orders.VolumeOrder;
import cloud.fogbow.ras.core.plugins.interoperability.VolumePlugin;
import cloud.fogbow.ras.core.plugins.interoperability.azure.AzureAsync;
import cloud.fogbow.ras.core.plugins.interoperability.azure.util.AzureClientCacheManager;
import cloud.fogbow.ras.core.plugins.interoperability.azure.util.AzureGeneralUtil;
import cloud.fogbow.ras.core.plugins.interoperability.azure.util.AzureResourceIdBuilder;
import cloud.fogbow.ras.core.plugins.interoperability.azure.util.AzureStateMapper;
import cloud.fogbow.ras.core.plugins.interoperability.azure.volume.sdk.AzureVolumeOperationSDK;
import cloud.fogbow.ras.core.plugins.interoperability.azure.volume.sdk.AzureVolumeSDK;
import com.google.common.annotations.VisibleForTesting;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.compute.Disk;
import com.microsoft.azure.management.compute.implementation.DiskInner;
import com.microsoft.azure.management.resources.fluentcore.model.Creatable;
import com.microsoft.azure.management.resources.fluentcore.model.Indexable;
import org.apache.log4j.Logger;
import rx.Completable;
import rx.Observable;

import java.util.Collections;
import java.util.Map;
import java.util.Properties;

public class AzureVolumePlugin extends AzureAsync<VolumeInstance> implements VolumePlugin<AzureUser>{

    private static final Logger LOGGER = Logger.getLogger(AzureVolumePlugin.class);
    
    private final String defaultRegionName;
    private final String defaultResourceGroupName;
    
    private AzureVolumeOperationSDK operation;

    public AzureVolumePlugin(String confFilePath) {
        Properties properties = PropertiesUtil.readProperties(confFilePath);
        this.defaultRegionName = properties.getProperty(AzureConstants.DEFAULT_REGION_NAME_KEY);
        this.defaultResourceGroupName = properties.getProperty(AzureConstants.DEFAULT_RESOURCE_GROUP_NAME_KEY);
        this.operation = new AzureVolumeOperationSDK();
    }

    @Override
    public boolean isReady(String instanceState) {
        return AzureStateMapper.map(ResourceType.VOLUME, instanceState).equals(InstanceState.READY);
    }

    @Override
    public boolean hasFailed(String instanceState) {
        return AzureStateMapper.map(ResourceType.VOLUME, instanceState).equals(InstanceState.FAILED);
    }

    @Override
    public String requestInstance(VolumeOrder volumeOrder, AzureUser azureUser) throws FogbowException {
        LOGGER.info(Messages.Info.REQUESTING_INSTANCE_FROM_PROVIDER);
        Azure azure = AzureClientCacheManager.getAzure(azureUser);
        String resourceName = AzureGeneralUtil.generateResourceName();
        int sizeInGB = volumeOrder.getVolumeSize();
        Map tags = Collections.singletonMap(AzureConstants.TAG_NAME, volumeOrder.getName());
        
        Creatable<Disk> diskCreatable = azure.disks().define(resourceName)
                .withRegion(this.defaultRegionName)
                .withExistingResourceGroup(this.defaultResourceGroupName)
                .withData()
                .withSizeInGB(sizeInGB)
                .withTags(tags);
        
        return doRequestInstance(volumeOrder, diskCreatable);
    }

    @Override
    public VolumeInstance getInstance(VolumeOrder volumeOrder, AzureUser azureUser) throws FogbowException {
        String instanceId = volumeOrder.getInstanceId();
        LOGGER.info(String.format(Messages.Info.GETTING_INSTANCE_S, instanceId));

        VolumeInstance creatingInstance = getCreatingInstance(instanceId);
        if (creatingInstance != null) {
            return creatingInstance;
        }

        Azure azure = AzureClientCacheManager.getAzure(azureUser);
        String resourceName = AzureGeneralUtil.defineResourceName(instanceId);
        String subscriptionId = azureUser.getSubscriptionId();
        String resourceId = buildResourceId(subscriptionId, resourceName);
        
        return doGetInstance(azure, resourceId);
    }
    
    @Override
    public void deleteInstance(VolumeOrder volumeOrder, AzureUser azureUser) throws FogbowException {
        LOGGER.info(String.format(Messages.Info.DELETING_INSTANCE_S, volumeOrder.getInstanceId()));
        Azure azure = AzureClientCacheManager.getAzure(azureUser);
        String resourceName = AzureGeneralUtil.defineResourceName(volumeOrder.getInstanceId());
        String subscriptionId = azureUser.getSubscriptionId();
        String resourceId = buildResourceId(subscriptionId, resourceName);
        
        doDeleteInstance(azure, resourceId); 
    }

    @VisibleForTesting
    void doDeleteInstance(Azure azure, String resourceId) throws FogbowException {
        Completable completable = AzureVolumeSDK.buildDeleteDiskCompletable(azure, resourceId);
        this.operation.subscribeDeleteDisk(completable);
    }

    @VisibleForTesting
    VolumeInstance doGetInstance(Azure azure, String resourceId) throws FogbowException {
        Disk disk = AzureVolumeSDK.getDisk(azure, resourceId)
                .orElseThrow(() -> new InstanceNotFoundException(Messages.Exception.INSTANCE_NOT_FOUND));

        return buildVolumeInstance(disk);
    }
    
    @VisibleForTesting
    VolumeInstance buildVolumeInstance(Disk disk) {
        DiskInner diskInner = disk.inner();
        String id = diskInner.id();
        String cloudState = diskInner.provisioningState();
        String name = disk.tags().get(AzureConstants.TAG_NAME);
        int size = disk.sizeInGB();
        return new VolumeInstance(id, cloudState, name , size);
    }

    @VisibleForTesting
    String buildResourceId(String subscriptionId, String resourceName) {
        String resourceId = AzureResourceIdBuilder.diskId()
                .withSubscriptionId(subscriptionId)
                .withResourceGroupName(this.defaultResourceGroupName)
                .withResourceName(resourceName)
                .build();
        
        return resourceId;
    }
    
    @VisibleForTesting
    String doRequestInstance(VolumeOrder volumeOrder, Creatable<Disk> diskCreatable) {
        Observable<Indexable> observable = AzureVolumeSDK.buildCreateDiskObservable(diskCreatable);
        String instanceId = AzureGeneralUtil.defineInstanceId(diskCreatable.name());
        Runnable finishCreationCallback = startInstanceCreation(instanceId);
        this.operation.subscribeCreateDisk(observable, finishCreationCallback);
        updateInstanceAllocation(volumeOrder);
        return instanceId;
    }

    @VisibleForTesting
    void updateInstanceAllocation(VolumeOrder volumeOrder) {
        synchronized (volumeOrder) {
            int sizeInGB = volumeOrder.getVolumeSize();
            VolumeAllocation actualAllocation = new VolumeAllocation(sizeInGB);
            volumeOrder.setActualAllocation(actualAllocation);
        }
    }
    
    @VisibleForTesting
    void setOperation(AzureVolumeOperationSDK operation) {
        this.operation = operation;
    }

    @Override
    protected VolumeInstance buildCreatingInstance(String instanceId) {
        return new VolumeInstance(instanceId, InstanceState.CREATING.getValue(), AzureGeneralUtil.NO_INFORMATION, 0);
    }
}
