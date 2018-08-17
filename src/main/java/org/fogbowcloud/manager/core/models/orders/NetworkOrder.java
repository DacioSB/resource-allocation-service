package org.fogbowcloud.manager.core.models.orders;

import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

import org.fogbowcloud.manager.core.models.ResourceType;
import org.fogbowcloud.manager.core.models.tokens.FederationUserToken;

@Entity
@Table(name = "network_order_table")
public class NetworkOrder extends Order {
	
	private static final long serialVersionUID = 1L;
	
	@Column
    private String gateway;
	
	@Column
    private String address;
	
	@Column
    private NetworkAllocationMode allocation;

    public NetworkOrder() {
        super(UUID.randomUUID().toString());
    }
    
    /** Creating Order with predefined Id. */
    public NetworkOrder(String id, FederationUserToken federationUserToken, String requestingMember, String providingMember,
                        String gateway, String address, NetworkAllocationMode allocation) {
        super(id, federationUserToken, requestingMember, providingMember);
        this.gateway = gateway;
        this.address = address;
        this.allocation = allocation;
    }

    public NetworkOrder(FederationUserToken federationUserToken, String requestingMember, String providingMember,
                        String gateway, String address, NetworkAllocationMode allocation) {
        this(UUID.randomUUID().toString(), federationUserToken, requestingMember, providingMember,
                gateway, address, allocation);
    }

    public String getGateway() {
        return gateway;
    }

    public String getAddress() {
        return address;
    }

    public NetworkAllocationMode getAllocation() {
        return allocation;
    }

    @Override
    public ResourceType getType() {
        return ResourceType.NETWORK;
    }
}
