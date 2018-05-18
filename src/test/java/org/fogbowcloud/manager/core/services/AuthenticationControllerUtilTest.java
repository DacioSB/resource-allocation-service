package org.fogbowcloud.manager.core.services;

import java.util.Map;
import java.util.Properties;

import org.fogbowcloud.manager.core.manager.constants.ConfigurationConstants;
import org.junit.Assert;
import org.junit.Test;

public class AuthenticationControllerUtilTest {

	private String CREDENTIALS_PREFIX = AuthenticationControllerUtil.LOCAL_TOKEN_CREDENTIALS_PREFIX;
	
	@Test
	public void testGetDefaultLocalTokenCredentials() {
		Properties properties = new Properties();
		String keyOne = "one";
		String keyTwo = "two";
		String keyThree = "Three";
		String valueOne = "valueOne";
		String valueTwo = "valueTwo";
		String valueThree = "valueThree";
		properties.put(CREDENTIALS_PREFIX + keyOne, valueOne);
		properties.put(CREDENTIALS_PREFIX + keyTwo, valueTwo);
		properties.put(keyThree, valueThree);
		
		Map<String, String> defaulLocalTokenCredentials = 
				AuthenticationControllerUtil.getDefaultLocalTokenCredentials(properties);
		Assert.assertEquals(valueOne, defaulLocalTokenCredentials.get(keyOne));
		Assert.assertEquals(valueTwo, defaulLocalTokenCredentials.get(keyTwo));
		Assert.assertNull(defaulLocalTokenCredentials.get(keyThree));
	}
	
	@Test
	public void testGetDefaultLocalTokenCredentialsWithPropertiesNull() {
		Map<String, String> defaulLocalTokenCredentials = 
				AuthenticationControllerUtil.getDefaultLocalTokenCredentials(null);
		Assert.assertTrue(defaulLocalTokenCredentials.isEmpty());
	}
	
	@Test
	public void testIsOrderProvadingLocally() {
		String provadingMember = "localmember";
		Properties properties = new Properties();
		properties.put(ConfigurationConstants.XMPP_ID_KEY, provadingMember);
		
		boolean isProvadingLocally = AuthenticationControllerUtil
				.isOrderProvadingLocally(provadingMember, properties);
		
		Assert.assertTrue(isProvadingLocally);
	}
	
	@Test
	public void testIsOrderProvadingLocallyWithMemberNull() {
		Properties properties = new Properties();
		properties.put(ConfigurationConstants.XMPP_ID_KEY, "member");
		
		boolean isProvadingLocally = AuthenticationControllerUtil
				.isOrderProvadingLocally(null, properties);
		
		Assert.assertTrue(isProvadingLocally);
	}	
	
	@Test
	public void testIsOrderProvadingLocallyWithMemberRemote() {
		String provadingMember = "member";
		Properties properties = new Properties();
		properties.put(ConfigurationConstants.XMPP_ID_KEY, provadingMember);
		
		String remoteMember = "remotemember";
		boolean isProvadingLocally = AuthenticationControllerUtil
				.isOrderProvadingLocally(remoteMember, properties);
		
		Assert.assertFalse(isProvadingLocally);
	}		
	
}
