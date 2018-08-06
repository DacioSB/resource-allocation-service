package org.fogbowcloud.manager.core.plugins.serialization.openstack.network.v2;

import com.google.gson.annotations.SerializedName;
import org.fogbowcloud.manager.core.plugins.serialization.GsonHolder;
import static org.fogbowcloud.manager.core.plugins.serialization.openstack.OpenstackRestApiConstants.Network.*;

/**
 * Documentation: https://developer.openstack.org/api-ref/network/v2/
 *
 * Response Example:
 * {
 *   "network":{
 *     "id": "4e8e5957-649f-477b-9e5b-f1f75b21c03c"
 *   }
 * }
 *
 * We use the @SerializedName annotation to specify that the request parameter is not equal to the class field.
 */
public class CreateNetworkResponse {

    public Network network;

    public CreateNetworkResponse(Network network) {
        this.network = network;
    }

    public String getId() {
        return network.id;
    }

    public String toJson() {
        return GsonHolder.getInstance().toJson(this);
    }

    public static CreateNetworkResponse fromJson(String json) {
        return GsonHolder.getInstance().fromJson(json, CreateNetworkResponse.class);
    }

    public static class Network {

        @SerializedName(ID_KEY_JSON)
        private String id;

        public Network(String id) {
            this.id = id;
        }

    }

}
