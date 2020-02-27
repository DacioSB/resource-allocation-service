package cloud.fogbow.ras.core.plugins.interoperability.azure.compute.sdk;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;

import org.apache.log4j.Logger;

import com.google.common.annotations.VisibleForTesting;
import com.microsoft.azure.PagedList;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.compute.VirtualMachine;
import com.microsoft.azure.management.compute.VirtualMachineSize;
import com.microsoft.azure.management.network.NetworkInterface;
import com.microsoft.azure.management.resources.fluentcore.arm.Region;
import com.microsoft.azure.management.resources.fluentcore.model.Indexable;

import cloud.fogbow.common.exceptions.FogbowException;
import cloud.fogbow.common.exceptions.InstanceNotFoundException;
import cloud.fogbow.common.exceptions.NoAvailableResourcesException;
import cloud.fogbow.common.models.AzureUser;
import cloud.fogbow.ras.constants.Messages;
import cloud.fogbow.ras.core.plugins.interoperability.azure.compute.AzureVirtualMachineOperation;
import cloud.fogbow.ras.core.plugins.interoperability.azure.compute.sdk.model.AzureCreateVirtualMachineRef;
import cloud.fogbow.ras.core.plugins.interoperability.azure.compute.sdk.model.AzureGetImageRef;
import cloud.fogbow.ras.core.plugins.interoperability.azure.compute.sdk.model.AzureGetVirtualMachineRef;
import cloud.fogbow.ras.core.plugins.interoperability.azure.network.sdk.AzureNetworkSDK;
import cloud.fogbow.ras.core.plugins.interoperability.azure.util.AzureClientCacheManager;
import cloud.fogbow.ras.core.plugins.interoperability.azure.util.AzureSchedulerManager;
import cloud.fogbow.ras.core.plugins.interoperability.azure.volume.sdk.AzureVolumeSDK;
import rx.Completable;
import rx.Observable;
import rx.Scheduler;
import rx.schedulers.Schedulers;

public class AzureVirtualMachineOperationSDK implements AzureVirtualMachineOperation {

    private static final Logger LOGGER = Logger.getLogger(AzureVirtualMachineOperationSDK.class);
    private String regionName;

    private Scheduler scheduler;

    public AzureVirtualMachineOperationSDK() {}

    public AzureVirtualMachineOperationSDK(String regionName) {
        ExecutorService virtualMachineExecutor = AzureSchedulerManager.getVirtualMachineExecutor();
        this.scheduler = Schedulers.from(virtualMachineExecutor);
        this.regionName = regionName;
    }

    /**
     * Create asynchronously because this operation takes a long time to finish.
     */
    @Override
    public void doCreateInstance(AzureCreateVirtualMachineRef virtualMachineRef, AzureUser azureUser)
            throws FogbowException {

        Azure azure = AzureClientCacheManager.getAzure(azureUser);
        Observable<Indexable> virtualMachineAsync = buildAzureVirtualMachineObservable(virtualMachineRef, azure);
        subscribeCreateVirtualMachine(virtualMachineAsync);
    }

    @VisibleForTesting
    Observable<Indexable> buildAzureVirtualMachineObservable(
            AzureCreateVirtualMachineRef virtualMachineRef,
            Azure azure) throws FogbowException {

        String networkInterfaceId = virtualMachineRef.getNetworkInterfaceId();
        NetworkInterface networkInterface = AzureNetworkSDK
                .getNetworkInterface(azure, networkInterfaceId)
                .orElseThrow(InstanceNotFoundException::new);
        
        String resourceGroupName = virtualMachineRef.getResourceGroupName();
        String regionName = virtualMachineRef.getRegionName();
        String virtualMachineName = virtualMachineRef.getVirtualMachineName();
        String osUserName = virtualMachineRef.getOsUserName();
        String osUserPassword = virtualMachineRef.getOsUserPassword();
        String osComputeName = virtualMachineRef.getOsComputeName();
        String userData = virtualMachineRef.getUserData();
        String size = virtualMachineRef.getSize();
        int diskSize = virtualMachineRef.getDiskSize();
        AzureGetImageRef azureImage = virtualMachineRef.getAzureGetImageRef();
        Region region = Region.findByLabelOrName(regionName);
        String imagePublished = azureImage.getPublisher();
        String imageOffer = azureImage.getOffer();
        String imageSku = azureImage.getSku();

        return AzureVirtualMachineSDK.buildVirtualMachineObservable(
                azure, virtualMachineName, region, resourceGroupName, networkInterface,
                imagePublished, imageOffer, imageSku, osUserName, osUserPassword, osComputeName,
                userData, diskSize, size);
    }

    /**
     * Execute create Virtual Machine observable and set its behaviour.
     */
    @VisibleForTesting
    void subscribeCreateVirtualMachine(Observable<Indexable> virtualMachineObservable) {
        setCreateVirtualMachineBehaviour(virtualMachineObservable)
                .subscribeOn(this.scheduler)
                .subscribe();
    }

    private Observable<Indexable> setCreateVirtualMachineBehaviour(Observable<Indexable> virtualMachineObservable) {
        return virtualMachineObservable
                .onErrorReturn((error -> {
                    LOGGER.error(Messages.Error.ERROR_CREATE_VM_ASYNC_BEHAVIOUR, error);
                    return null;
                }))
                .doOnCompleted(() -> {
                    LOGGER.info(Messages.Info.END_CREATE_VM_ASYNC_BEHAVIOUR);
                });
    }

    @Override
    public VirtualMachineSize findVirtualMachineSize(int memoryRequired, int vCpuRequired, String regionName,
            AzureUser azureCloudUser) throws FogbowException {

        LOGGER.debug(String.format(Messages.Info.SEEK_VIRTUAL_MACHINE_SIZE_NAME, memoryRequired, vCpuRequired, regionName));
        Azure azure = AzureClientCacheManager.getAzure(azureCloudUser);
        Region region = Region.findByLabelOrName(regionName);
        
        PagedList<VirtualMachineSize> virtualMachineSizes =
                AzureVirtualMachineSDK.getVirtualMachineSizes(azure, region);
        
        VirtualMachineSize firstVirtualMachineSize = virtualMachineSizes.stream()
                .filter((virtualMachineSize) ->
                        virtualMachineSize.memoryInMB() >= memoryRequired &&
                                virtualMachineSize.numberOfCores() >= vCpuRequired
                )
                .sorted(Comparator
                        .comparingInt(VirtualMachineSize::memoryInMB)
                        .thenComparingInt(VirtualMachineSize::numberOfCores))
                .findFirst()
                .orElseThrow(NoAvailableResourcesException::new);

        return firstVirtualMachineSize;
    }

    @Override
    public AzureGetVirtualMachineRef doGetInstance(String azureInstanceId, AzureUser azureCloudUser)
            throws FogbowException {

        Azure azure = AzureClientCacheManager.getAzure(azureCloudUser);

        VirtualMachine virtualMachine = AzureVirtualMachineSDK
                .getVirtualMachine(azure, azureInstanceId)
                .orElseThrow(InstanceNotFoundException::new);
        
        String virtualMachineSizeName = virtualMachine.size().toString();
        String cloudState = virtualMachine.provisioningState();
        String name = virtualMachine.name();
        String primaryPrivateIp = virtualMachine.getPrimaryNetworkInterface().primaryPrivateIP();
        List<String> ipAddresses = Arrays.asList(primaryPrivateIp);

        VirtualMachineSize virtualMachineSize = findVirtualMachineSizeByName(virtualMachineSizeName, this.regionName, azure);
        int vCPU = virtualMachineSize.numberOfCores();
        int memory = virtualMachineSize.memoryInMB();
        int disk = virtualMachine.osDiskSize();

        return AzureGetVirtualMachineRef.builder()
                .cloudState(cloudState)
                .ipAddresses(ipAddresses)
                .disk(disk)
                .memory(memory)
                .name(name)
                .vCPU(vCPU)
                .build();
    }

    @VisibleForTesting
    VirtualMachineSize findVirtualMachineSizeByName(String virtualMachineSizeNameWanted, String regionName, Azure azure)
            throws FogbowException {

        LOGGER.debug(String.format(Messages.Info.SEEK_VIRTUAL_MACHINE_SIZE_BY_NAME, virtualMachineSizeNameWanted, regionName));
        Region region = Region.findByLabelOrName(regionName);
        
        PagedList<VirtualMachineSize> virtualMachineSizes = AzureVirtualMachineSDK.getVirtualMachineSizes(azure, region);
        return virtualMachineSizes.stream()
                .filter((virtualMachineSize) -> virtualMachineSizeNameWanted.equals(virtualMachineSize.name()))
                .findFirst()
                .orElseThrow(NoAvailableResourcesException::new);
    }

    /**
     * Delete asynchronously because this operation takes a long time to finish.
     */
    @Override
    public void doDeleteInstance(String azureInstanceId, AzureUser azureCloudUser) throws FogbowException {
        Azure azure = AzureClientCacheManager.getAzure(azureCloudUser);
        Completable firstDeleteVirtualMachine = buildDeleteVirtualMachineCompletable(azure, azureInstanceId);
        Completable secondDeleteVirtualMachineDisk = buildDeleteVirtualMachineDiskCompletable(azure, azureInstanceId);
        Completable.concat(firstDeleteVirtualMachine, secondDeleteVirtualMachineDisk)
                .subscribeOn(this.scheduler)
                .subscribe();
    }

    @VisibleForTesting
    Completable buildDeleteVirtualMachineDiskCompletable(Azure azure, String azureInstanceId) throws FogbowException {

        VirtualMachine virtualMachine = AzureVirtualMachineSDK
                .getVirtualMachine(azure, azureInstanceId)
                .orElseThrow(InstanceNotFoundException::new);
        
        String osDiskId = virtualMachine.osDiskId();
        Completable deleteVirutalMachineDisk = AzureVolumeSDK.buildDeleteDiskCompletable(azure, osDiskId);
        return setDeleteVirtualMachineDiskBehaviour(deleteVirutalMachineDisk);
    }

    @VisibleForTesting
    Completable buildDeleteVirtualMachineCompletable(Azure azure, String azureInstanceId) {
        Completable deleteVirtualMachine = AzureVirtualMachineSDK
                .buildDeleteVirtualMachineCompletable(azure, azureInstanceId);
        
        return setDeleteVirtualMachineBehaviour(deleteVirtualMachine);
    }

    private Completable setDeleteVirtualMachineDiskBehaviour(Completable deleteVirutalMachineDisk) {
        return deleteVirutalMachineDisk
                .doOnError((error -> {
                    LOGGER.error(Messages.Error.ERROR_DELETE_DISK_ASYNC_BEHAVIOUR);
                }))
                .doOnCompleted(() -> {
                    LOGGER.info(Messages.Info.END_DELETE_DISK_ASYNC_BEHAVIOUR);
                });
    }

    private Completable setDeleteVirtualMachineBehaviour(Completable deleteVirtualMachineCompletable) {
        return deleteVirtualMachineCompletable
                .doOnError((error -> {
                    LOGGER.error(Messages.Error.ERROR_DELETE_VM_ASYNC_BEHAVIOUR, error);
                }))
                .doOnCompleted(() -> {
                    LOGGER.info(Messages.Info.END_DELETE_VM_ASYNC_BEHAVIOUR);
                });
    }

    @VisibleForTesting
    void setScheduler(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

}
