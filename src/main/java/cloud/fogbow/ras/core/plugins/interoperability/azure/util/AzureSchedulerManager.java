package cloud.fogbow.ras.core.plugins.interoperability.azure.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AzureSchedulerManager {

    private static final int PUBLIC_IP_POOL_SIZE = 4;
    private static final int VIRTUAL_MACHINE_POOL_SIZE = 2;
    private static final int VIRTUAL_NETWORK_POOL_SIZE = 3;

    public static ExecutorService getVirtualMachineExecutor() {
        return Executors.newFixedThreadPool(VIRTUAL_MACHINE_POOL_SIZE);
    }

    public static ExecutorService getVirtualNetworkExecutor() {
        return Executors.newFixedThreadPool(VIRTUAL_NETWORK_POOL_SIZE);
    }
    
    public static ExecutorService getPublicIpExecutor() {
        return Executors.newFixedThreadPool(PUBLIC_IP_POOL_SIZE);
    }
    
}
