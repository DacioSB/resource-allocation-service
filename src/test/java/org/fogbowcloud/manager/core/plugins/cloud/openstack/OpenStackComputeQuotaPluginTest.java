package org.fogbowcloud.manager.core.plugins.cloud.openstack;

import java.util.HashMap;
import java.util.Map;
import org.fogbowcloud.manager.core.HomeDir;
import org.fogbowcloud.manager.core.exceptions.FogbowManagerException;
import org.fogbowcloud.manager.core.exceptions.UnexpectedException;
import org.fogbowcloud.manager.core.models.quotas.allocation.ComputeAllocation;
import org.fogbowcloud.manager.core.models.tokens.Token;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class OpenStackComputeQuotaPluginTest {
	
    private static final String TENANT_ID = "tenantId";
    private static final String FAKE_TENANT_ID = "fake-tenant-id";
    private static final String FAKE_VALUE = "fake-value";
    private static final String FAKE_JSON_RESPONSE =
            "{\"limits\": {\"rate\": [], \"absolute\": {\"maxServerMeta\": 128, \"maxPersonality\": 5, "
                    + "\"totalServerGroupsUsed\": 0, \"maxImageMeta\": 128, \"maxPersonalitySize\": 10240, "
                    + "\"maxTotalKeypairs\": 10, \"maxSecurityGroupRules\": 20, \"maxServerGroups\": 10, "
                    + "\"totalCoresUsed\": 6, \"totalRAMUsed\": 12288, \"totalInstancesUsed\": 6, "
                    + "\"maxSecurityGroups\": 10, \"totalFloatingIpsUsed\": 0, \"maxTotalCores\": 64, "
                    + "\"maxServerGroupMembers\": 10, \"maxTotalFloatingIps\": 10, "
                    + "\"totalSecurityGroupsUsed\": 1, \"maxTotalInstances\": 20, \"maxTotalRAMSize\": 46080}}}";
    
    private OpenStackComputeQuotaPlugin plugin;
	private Token token;
	
	@Before
	public void setUp() {
		HomeDir.getInstance().setPath("src/test/resources/private");
		this.plugin = Mockito.spy(new OpenStackComputeQuotaPlugin());
		
		this.token = new Token();
		Map<String, String> attributes = new HashMap<String, String>();
		attributes.put(TENANT_ID, FAKE_TENANT_ID);
		token.setAttributes(attributes);
		token.setAccessId("");
	}
	
    @Test
    public void getUserQuotaTest() throws FogbowManagerException, UnexpectedException {
        ComputeAllocation allocationTotal = new ComputeAllocation(64, 46080, 20);
        ComputeAllocation allocationUsed = new ComputeAllocation(6, 12288, 6);
        ComputeAllocation allocationAvailable = new ComputeAllocation(58, 33792, 14);

        Mockito.doReturn(FAKE_JSON_RESPONSE).when(this.plugin).getJson(this.token);

        Assert.assertEquals(allocationTotal.getvCPU(), this.plugin.getUserQuota(this.token).getTotalQuota().getvCPU());
        Assert.assertEquals(allocationTotal.getRam(), this.plugin.getUserQuota(this.token).getTotalQuota().getRam());
        Assert.assertEquals(allocationTotal.getInstances(), this.plugin.getUserQuota(this.token).getTotalQuota().getInstances());
        
        Assert.assertEquals(allocationUsed.getvCPU(), this.plugin.getUserQuota(this.token).getUsedQuota().getvCPU());
        Assert.assertEquals(allocationUsed.getRam(), this.plugin.getUserQuota(this.token).getUsedQuota().getRam());
        Assert.assertEquals(allocationUsed.getInstances(), this.plugin.getUserQuota(this.token).getUsedQuota().getInstances());
        
        Assert.assertEquals(allocationAvailable.getvCPU(), this.plugin.getUserQuota(this.token).getAvailableQuota().getvCPU());
        Assert.assertEquals(allocationAvailable.getRam(), this.plugin.getUserQuota(this.token).getAvailableQuota().getRam());
        Assert.assertEquals(allocationAvailable.getInstances(), this.plugin.getUserQuota(this.token).getAvailableQuota().getInstances());
    }
	
	@Test (expected = FogbowManagerException.class)
	public void getUserQuotaThrowFogbowManagerExceptionTest() throws FogbowManagerException, UnexpectedException {
	    Mockito.when(this.plugin.getJson(this.token)).thenReturn(FAKE_VALUE);
	    Mockito.doThrow(FogbowManagerException.class).when(this.plugin).getUserQuota(this.token);
	    Mockito.verify(this.plugin).getUserQuota(Mockito.eq(this.token));

	}
	
	@Test (expected = FogbowManagerException.class)
    public void getUserQuotaThrowUnexpectedExceptionTest() throws FogbowManagerException, UnexpectedException {
        Mockito.when(this.plugin.getJson(this.token)).thenReturn(FAKE_VALUE);
        Mockito.doThrow(UnexpectedException.class).when(this.plugin).getUserQuota(this.token);
        Mockito.verify(this.plugin).getUserQuota(Mockito.eq(this.token));
    }
	
}
