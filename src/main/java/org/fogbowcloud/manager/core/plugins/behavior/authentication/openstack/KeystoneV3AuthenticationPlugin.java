package org.fogbowcloud.manager.core.plugins.behavior.authentication.openstack;

import org.apache.http.client.HttpResponseException;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.HomeDir;
import org.fogbowcloud.manager.core.PropertiesHolder;
import org.fogbowcloud.manager.core.constants.ConfigurationConstants;
import org.fogbowcloud.manager.core.constants.DefaultConfigurationConstants;
import org.fogbowcloud.manager.core.exceptions.FatalErrorException;
import org.fogbowcloud.manager.core.exceptions.UnavailableProviderException;
import org.fogbowcloud.manager.core.models.tokens.FederationUserToken;
import org.fogbowcloud.manager.core.models.tokens.generators.openstack.v3.KeystoneV3TokenGeneratorPlugin;
import org.fogbowcloud.manager.core.plugins.behavior.authentication.AuthenticationPlugin;
import org.fogbowcloud.manager.util.PropertiesUtil;
import org.fogbowcloud.manager.util.connectivity.HttpRequestClientUtil;

import java.io.File;
import java.util.Properties;

public class KeystoneV3AuthenticationPlugin implements AuthenticationPlugin {
    private static final Logger LOGGER = Logger.getLogger(KeystoneV3TokenGeneratorPlugin.class);

    private String v3TokensEndpoint;
    private HttpRequestClientUtil client;

    private String localProviderId;

    public KeystoneV3AuthenticationPlugin() {
        Properties properties = PropertiesUtil.readProperties(HomeDir.getPath() +
                DefaultConfigurationConstants.OPENSTACK_CONF_FILE_NAME);

        String identityUrl = properties.getProperty(KeystoneV3TokenGeneratorPlugin.OPENSTACK_KEYSTONE_V3_URL);
        if (isUrlValid(identityUrl)) {
            this.v3TokensEndpoint = identityUrl + KeystoneV3TokenGeneratorPlugin.V3_TOKENS_ENDPOINT_PATH;
        }
        this.client = new HttpRequestClientUtil();
        this.localProviderId = PropertiesHolder.getInstance().getProperty(ConfigurationConstants.LOCAL_MEMBER_ID);
    }

    @Override
    public boolean isAuthentic(FederationUserToken federationToken) throws UnavailableProviderException {
        if (federationToken.getTokenProvider().equals(this.localProviderId)) {
            try {
                this.client.doGetRequest(this.v3TokensEndpoint, federationToken);
                return true;
            } catch (HttpResponseException e) {
                return false;
            }
        } else {
            return true;
        }
    }

    private boolean isUrlValid(String url) throws FatalErrorException {
        if (url == null || url.trim().isEmpty()) {
            throw new FatalErrorException("Invalid Keystone_V3_URL " +
                    KeystoneV3TokenGeneratorPlugin.OPENSTACK_KEYSTONE_V3_URL);
        }
        return true;
    }

    // Used in testing
    protected void setClient(HttpRequestClientUtil client) {
        this.client = client;
    }
}
