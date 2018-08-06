package org.fogbowcloud.manager.core.plugins.serialization.openstack.compute.v2;

import com.google.gson.annotations.SerializedName;
import org.fogbowcloud.manager.core.plugins.serialization.GsonHolder;

import java.util.List;

/**
 * Documentation: https://developer.openstack.org/api-ref/compute/
 *
 * Response Example:
 * {
 *   "flavors":[
 *     {
 *       "id":"1"
 *     },
 *     {
 *       "id":"2"
 *     },
 *     {
 *       "id":"3"
 *     },
 *     {
 *       "id":"4"
 *     }
 *   ]
 * }
 *
 * We use the @SerializedName annotation to specify that the request parameter is not equal to the class field.
 */
public class GetAllFlavorsResponse {

    @SerializedName("flavors")
    private List<Flavor> flavors;

    public class Flavor {

        @SerializedName("id")
        private String id;

        public String getId() {
            return id;
        }

    }

    public List<Flavor> getFlavors() {
        return flavors;
    }

    public static GetAllFlavorsResponse fromJson(String json) {
        return GsonHolder.getInstance().fromJson(json, GetAllFlavorsResponse.class);
    }

}
