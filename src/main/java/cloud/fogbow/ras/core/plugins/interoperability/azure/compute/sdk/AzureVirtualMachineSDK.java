package cloud.fogbow.ras.core.plugins.interoperability.azure.compute.sdk;

import cloud.fogbow.common.exceptions.UnexpectedException;
import com.google.common.annotations.VisibleForTesting;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.compute.VirtualMachine;
import com.microsoft.azure.management.compute.VirtualMachines;

import java.util.Optional;

public class AzureVirtualMachineSDK {

    static Optional<VirtualMachine> getVirtualMachineById(Azure azure, String virtualMachineId)
            throws UnexpectedException {

        try {
            VirtualMachines virtualMachinesObject = getVirtualMachinesObject(azure);
            return Optional.ofNullable(virtualMachinesObject.getById(virtualMachineId));
        } catch (RuntimeException e) {
            throw new UnexpectedException(e.getMessage(), e);
        }
    }

    // This class is used only for test proposes.
    // It is necessary because was not possible mock the Azure(final class)
    @VisibleForTesting
    static VirtualMachines getVirtualMachinesObject(Azure azure) {
        return azure.virtualMachines();
    }

}
