package cloud.fogbow.ras.core.plugins.interoperability.opennebula.quota.v5_4;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.opennebula.client.Client;
import org.opennebula.client.user.User;
import org.opennebula.client.user.UserPool;
import org.opennebula.client.vnet.VirtualNetworkPool;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;

import cloud.fogbow.common.exceptions.FogbowException;
import cloud.fogbow.ras.api.http.response.quotas.ResourceQuota;
import cloud.fogbow.ras.api.http.response.quotas.allocation.ResourceAllocation;
import cloud.fogbow.ras.core.TestUtils;
import cloud.fogbow.ras.core.datastore.DatabaseManager;
import cloud.fogbow.ras.core.plugins.interoperability.opennebula.OpenNebulaBaseTests;
import cloud.fogbow.ras.core.plugins.interoperability.opennebula.OpenNebulaClientUtil;

@PrepareForTest({ OpenNebulaClientUtil.class, DatabaseManager.class })
public class OpenNebulaQuotaPluginTest extends OpenNebulaBaseTests {

    private static final String CPU_MAX_VALUE = "800";
    private static final String CPU_USED_VALUE = "200";
    private static final String DISK_MAX_VALUE = "9216";
    private static final String DISK_USED_VALUE = "4096";
    private static final String FRACTION_RESOURCE_USED_VALUE = "1.5";
    private static final String MEMORY_MAX_VALUE = "15155";
    private static final String MEMORY_USED_VALUE = "2048";
    private static final String NETWORK_USED_VALUE = "0";
    private static final String PUBLIC_IP_MAX_VALUE = "5";
    private static final String PUBLIC_IP_USED_VALUE = "1";
    private static final String VMS_MAX_VALUE = "8";
    private static final String VMS_USED_VALUE = "1";
    
    private static final int FAKE_PUBLIC_NETWORK_ID = 100;
    
    private OpenNebulaQuotaPlugin plugin;
    private User user;
    
    @Before
    public void setUp() throws FogbowException {
        super.setUp();
        this.plugin = Mockito.spy(new OpenNebulaQuotaPlugin(this.openNebulaConfFilePath));
        this.user = Mockito.mock(User.class);
    }
    
    // test case: When calling the getUserQuota method, it must verify
    // that the call was successful.
    @Test
    public void testGetUserQuota() throws FogbowException {
        // set up
        PowerMockito.mockStatic(OpenNebulaClientUtil.class);
        Mockito.when(OpenNebulaClientUtil.createClient(Mockito.anyString(), Mockito.anyString()))
        .thenReturn(this.client);

        UserPool userPool = Mockito.mock(UserPool.class);
        Mockito.when(OpenNebulaClientUtil.getUserPool(Mockito.any(Client.class))).thenReturn(userPool);
        Mockito.when(OpenNebulaClientUtil.getUser(Mockito.eq(userPool), Mockito.anyString())).thenReturn(this.user);
        
        ResourceAllocation totalAllocation = buildTotalAllocation();
        Mockito.doReturn(totalAllocation).when(this.plugin).getTotalAllocation(Mockito.eq(this.user));
        
        ResourceAllocation usedAllocation = buildUsedAllocation();
        Mockito.doReturn(usedAllocation).when(this.plugin).getUsedAllocation(Mockito.eq(this.user), Mockito.eq(this.client));
        
        ResourceQuota expected = new ResourceQuota(totalAllocation, usedAllocation);

        // exercise
        ResourceQuota quota = this.plugin.getUserQuota(this.cloudUser);

        // verify
        PowerMockito.verifyStatic(OpenNebulaClientUtil.class, Mockito.times(TestUtils.RUN_ONCE));
        OpenNebulaClientUtil.createClient(Mockito.anyString(), Mockito.eq(this.cloudUser.getToken()));

        PowerMockito.verifyStatic(OpenNebulaClientUtil.class, Mockito.times(TestUtils.RUN_ONCE));
        OpenNebulaClientUtil.getUserPool(Mockito.any(Client.class));

        PowerMockito.verifyStatic(OpenNebulaClientUtil.class, Mockito.times(TestUtils.RUN_ONCE));
        OpenNebulaClientUtil.getUser(Mockito.eq(userPool), Mockito.eq(this.cloudUser.getId()));

        Mockito.verify(this.plugin, Mockito.times(TestUtils.RUN_ONCE)).getTotalAllocation(Mockito.eq(this.user));
        Mockito.verify(this.plugin, Mockito.times(TestUtils.RUN_ONCE)).getUsedAllocation(Mockito.eq(this.user), Mockito.eq(this.client));
        
        Assert.assertEquals(expected, quota);
    }
    
    // test case: When calling the getUsedAllocation method, it must verify that
    // the call was successful and returned the expected value.
    @Test
    public void testGetUsedAllocation() throws Exception {
        // set up
        String publicIpQuotaUsedPath = String.format(OpenNebulaQuotaPlugin.FORMAT_QUOTA_NETWORK_S_USED_PATH,
                FAKE_PUBLIC_NETWORK_ID);

        PowerMockito.mockStatic(OpenNebulaClientUtil.class);
        VirtualNetworkPool networkPool = Mockito.mock(VirtualNetworkPool.class);
        PowerMockito.when(OpenNebulaClientUtil.class, TestUtils.GET_NETWORK_POOL_BY_USER_METHOD, Mockito.eq(this.client)).thenReturn(networkPool);
        
        Mockito.when(this.user.xpath(Mockito.eq(publicIpQuotaUsedPath))).thenReturn(PUBLIC_IP_USED_VALUE);
        Mockito.when(this.user.xpath(Mockito.eq(OpenNebulaQuotaPlugin.QUOTA_VMS_USED_PATH))).thenReturn(VMS_USED_VALUE);
        Mockito.when(this.user.xpath(Mockito.eq(OpenNebulaQuotaPlugin.QUOTA_CPU_USED_PATH))).thenReturn(CPU_USED_VALUE);
        Mockito.when(this.user.xpath(Mockito.eq(OpenNebulaQuotaPlugin.QUOTA_MEMORY_USED_PATH)))
                .thenReturn(MEMORY_USED_VALUE);
        Mockito.when(this.user.xpath(Mockito.eq(OpenNebulaQuotaPlugin.QUOTA_DISK_SIZE_USED_PATH)))
                .thenReturn(DISK_USED_VALUE);
        

        ResourceAllocation expected = buildUsedAllocation();

        // exercise
        ResourceAllocation usedAllocation = this.plugin.getUsedAllocation(this.user, this.client);

        // verify
        PowerMockito.verifyStatic(OpenNebulaClientUtil.class, Mockito.timeout(TestUtils.RUN_ONCE));
        OpenNebulaClientUtil.getNetworkPoolByUser(Mockito.eq(this.client));
        
        Mockito.verify(this.user, Mockito.times(TestUtils.RUN_ONCE))
                .xpath(Mockito.eq(OpenNebulaQuotaPlugin.QUOTA_CPU_USED_PATH));
        
        Mockito.verify(this.user, Mockito.times(TestUtils.RUN_ONCE))
                .xpath(Mockito.eq(OpenNebulaQuotaPlugin.QUOTA_MEMORY_USED_PATH));
        
        Mockito.verify(this.user, Mockito.times(TestUtils.RUN_ONCE))
                .xpath(Mockito.eq(OpenNebulaQuotaPlugin.QUOTA_VMS_USED_PATH));
        
        Mockito.verify(this.user, Mockito.times(TestUtils.RUN_ONCE))
                .xpath(Mockito.eq(OpenNebulaQuotaPlugin.QUOTA_DISK_SIZE_USED_PATH));
        
        Mockito.verify(networkPool, Mockito.times(TestUtils.RUN_ONCE)).getLength();
        Mockito.verify(this.user, Mockito.times(TestUtils.RUN_ONCE)).xpath(Mockito.eq(publicIpQuotaUsedPath));
        Mockito.verify(this.plugin, Mockito.times(TestUtils.RUN_FIVE_TIMES)).convertToInteger(Mockito.anyString());
        
        Assert.assertEquals(expected, usedAllocation);
    }
    
    // test case: When calling the getTotalAllocation method, it must verify that
    // the call was successful and returned the expected value.
    @Test
    public void testGetTotalAllocation() {
        // set up
        Mockito.when(this.user.xpath(Mockito.eq(OpenNebulaQuotaPlugin.QUOTA_CPU_PATH))).thenReturn(CPU_MAX_VALUE);
        Mockito.when(this.user.xpath(Mockito.eq(OpenNebulaQuotaPlugin.QUOTA_MEMORY_PATH))).thenReturn(MEMORY_MAX_VALUE);
        Mockito.when(this.user.xpath(Mockito.eq(OpenNebulaQuotaPlugin.QUOTA_VMS_PATH))).thenReturn(VMS_MAX_VALUE);
        Mockito.when(this.user.xpath(Mockito.eq(OpenNebulaQuotaPlugin.QUOTA_DISK_SIZE_PATH)))
                .thenReturn(DISK_MAX_VALUE);

        String publicIpQuotaPath = String.format(OpenNebulaQuotaPlugin.FORMAT_QUOTA_NETWORK_S_PATH,
                FAKE_PUBLIC_NETWORK_ID);
        Mockito.when(this.user.xpath(Mockito.eq(publicIpQuotaPath))).thenReturn(PUBLIC_IP_MAX_VALUE);

        ResourceAllocation expected = buildTotalAllocation();

        Mockito.doReturn(expected.getInstances()).when(this.plugin).convertToInteger(Mockito.eq(VMS_MAX_VALUE));
        Mockito.doReturn(expected.getvCPU()).when(this.plugin).convertToInteger(Mockito.eq(CPU_MAX_VALUE));
        Mockito.doReturn(expected.getRam()).when(this.plugin).convertToInteger(Mockito.eq(MEMORY_MAX_VALUE));
        Mockito.doReturn(expected.getDisk()).when(this.plugin).convertToInteger(Mockito.eq(DISK_MAX_VALUE));
        Mockito.doReturn(expected.getPublicIps()).when(this.plugin).convertToInteger(Mockito.eq(PUBLIC_IP_MAX_VALUE));

        // exercise
        ResourceAllocation totalAllocation = this.plugin.getTotalAllocation(this.user);

        // verify
        Mockito.verify(this.user, Mockito.times(TestUtils.RUN_ONCE))
                .xpath(Mockito.eq(OpenNebulaQuotaPlugin.QUOTA_CPU_PATH));
        Mockito.verify(this.user, Mockito.times(TestUtils.RUN_ONCE))
                .xpath(Mockito.eq(OpenNebulaQuotaPlugin.QUOTA_MEMORY_PATH));
        Mockito.verify(this.user, Mockito.times(TestUtils.RUN_ONCE))
                .xpath(Mockito.eq(OpenNebulaQuotaPlugin.QUOTA_VMS_PATH));
        Mockito.verify(this.user, Mockito.times(TestUtils.RUN_ONCE))
                .xpath(Mockito.eq(OpenNebulaQuotaPlugin.QUOTA_DISK_SIZE_PATH));
        Mockito.verify(this.user, Mockito.times(TestUtils.RUN_ONCE)).xpath(Mockito.eq(publicIpQuotaPath));
        Mockito.verify(this.plugin, Mockito.times(TestUtils.RUN_FIVE_TIMES)).convertToInteger(Mockito.anyString());

        Assert.assertEquals(expected, totalAllocation);
    }

    
    // test case: When invoking the testConvertToInteger method, with an
    // integer-convertible String, return the respective round integer value.
    @Test
    public void testConvertToInteger() {
        // set up
        int expectedIntSuccess = 2;

        // exercise
        int intSuccess = this.plugin.convertToInteger(FRACTION_RESOURCE_USED_VALUE);

        // verify
        Assert.assertEquals(expectedIntSuccess, intSuccess);
    }
    
    // test case: When invoking the testConvertToInteger method, with a String not
    // convertible to an integer, return the value 0 (zero).
    @Test
    public void testConvertToIntegerFail() {
        // set up
        int expectedIntFail = 0;

        // exercise
        int intFail = this.plugin.convertToInteger(TestUtils.EMPTY_STRING);

        // verify
        Assert.assertEquals(expectedIntFail, intFail);
    }
    
    private ResourceAllocation buildTotalAllocation() {
        int maxInstances = Integer.parseInt(VMS_MAX_VALUE);
        int maxCPU = Integer.parseInt(CPU_MAX_VALUE);
        int maxRam = Integer.parseInt(MEMORY_MAX_VALUE);
        int maxDisk = Integer.parseInt(DISK_MAX_VALUE);
        int maxNetworks = OpenNebulaQuotaPlugin.UNLIMITED_NETWORK_QUOTA_VALUE;
        int maxPublicIps = Integer.parseInt(PUBLIC_IP_MAX_VALUE);
        
        ResourceAllocation expected = ResourceAllocation.builder()
                .instances(maxInstances)
                .vCPU(maxCPU)
                .ram(maxRam)
                .disk(maxDisk)
                .networks(maxNetworks)
                .publicIps(maxPublicIps)
                .build();
        
        return expected;
    }
    
    private ResourceAllocation buildUsedAllocation() {
        int usedInstances = Integer.parseInt(VMS_USED_VALUE);
        int usedCPU = Integer.parseInt(CPU_USED_VALUE);
        int usedRam = Integer.parseInt(MEMORY_USED_VALUE);
        int usedDisk = Integer.parseInt(DISK_USED_VALUE);
        int usedNetworks = Integer.parseInt(NETWORK_USED_VALUE);
        int usedPublicIps = Integer.parseInt(PUBLIC_IP_USED_VALUE);
        
        ResourceAllocation expected = ResourceAllocation.builder()
                .instances(usedInstances)
                .vCPU(usedCPU)
                .ram(usedRam)
                .disk(usedDisk)
                .networks(usedNetworks)
                .publicIps(usedPublicIps)
                .build();
        
        return expected;
    }

}
