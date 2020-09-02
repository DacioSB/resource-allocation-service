package cloud.fogbow.ras.core.plugins.interoperability.emulatedcloud;

import com.google.gson.annotations.SerializedName;

public class EmulatedCloudConstants {

    public static class Conf {

        public static final String STATIC_RESOURCES_FOLDER = "static_resources_folder";
        public static final String RESOURCES_FOLDER = "resources_folder";
    }

    public static class File {

        public static final String ALL_IMAGES = "images.json";
    }

    public static class Plugins {
        public static final String STATE_ACTIVE = "active";
        public static final String STATE_READY = "ready";
        public static final String STATE_RUNNING = "running";

        public static class PublicIp {

            public static final String ID_KEY_JSON = "id";
            public static final String STATUS_KEY_JSON = "status";
        }

        public static class Quota {
            public static final int DEFAULT_INSTANCES = 12;
            public static final int DEFAULT_DISK = 1536;
            public static final int DEFAULT_MEMORY = 96;
            public static final int DEFAULT_VCPU = 28;
        }

        public static class SecurityRule {
            public static final String INGRESS_DIRECTION = "ingress";
            public static final String IPV4_ETHER_TYPE = "IPv4";
        }
    }
    public static class Json {
        public static final String AVAILABLE_QUOTA_KEY_JSON = "AVAILABLEQuota";
        public static final String CIDR_KEY_JSON = "cidr";
        public static final String CLOUD_STATE_KEY_JSON = "cloudState";
        public static final String CLOUD_NAME_KEY_JSON = "cloudName";
        public static final String COMPUTE_ID_KEY_JSON = "computeId";
        public static final String DEVICE_KEY_JSON = "device";
        public static final String DIRECTION_KEY_JSON = "direction";
        public static final String DISK_KEY_JSON = "disk";
        public static final String ETHER_TYPE_KEY_JSON = "etherType";
        public static final String FLOATING_IP_KEY_JSON = "floatingip";
        public static final String IMAGE_ID_KEY_JSON = "imageId";
        public static final String INSTANCE_ID_KEY_JSON = "instanceId";
        public static final String INSTANCES_KEY_JSON = "instances";
        public static final String IP_ADDRESSES_KEY_JSON = "ipAddresses";
        public static final String MAJOR_ORDER_KEY_JSON = "majorOrderId";
        public static final String MEMORY_KEY_JSON = "memory";
        public static final String ID_KEY_JSON = "id";
        public static final String NAME_KEY_JSON = "name";
        public static final String NETWORK_KEY_JSON = "network";
        public static final String PORT_FROM_KEY_JSON = "portFrom";
        public static final String PORT_TO_KEY_JSON = "portTo";
        public static final String PROTOCOL_KEY_JSON = "protocol";
        public static final String PROVIDER_KEY_JSON = "provider";
        public static final String PUBLIC_KEY_KEY_JSON = "publicKey";
        public static final String SECURITY_RULES_KEY_JSON = "securityRules";
        public static final String STATE_KEY_JSON = "state";
        public static final String TOTAL_QUOTA_KEY_JSON = "totalQuota";
        public static final String USED_QUOTA_KEY_JSON = "usedQuota";
        public static final String USER_DATA_KEY_JSON = "userData";
        public static final String VCPU_KEY_JSON = "VCPU";
        public static final String VOLUME_ATTACHMENT_KEY_JSON = "volumeAttachment";
        public static final String VOLUME_ID_KEY_JSON = "volumeId";
        public static final String VLAN_KEY_JSON = "vLAN";
        public static final String ALLOCATION_MODE_KEY_JSON = "allocationMode";
        public static final String GATEWAY_KEY_JSON = "gateway";
        public static final String NETWORK_INTERFACE_KEY_JSON = "networkInterface";
        public static final String INTERFACE_STATE_KEY_JSON = "interfaceState";
        public static final String MAC_INTERFACE_KEY_JSON = "macinterface";
        public static final String SECURITY_GROUP_ID_KEY_JSON = "securityGroupId";
    }

    public class Exception {
        public static final String UNABLE_TO_CREATE_RESOURCE_INVALID_INSTANCE_ID_S = "Unable to create resource. Invalid instance id %s";
        public static final String INVALID_INSTANCE_ID = "Invalid instance id %s";
        public static final String EMULATED_RESOURCE_UNDEFINED = "Emulated resource is undefined.";
        public static final String RESOURCE_NOT_FOUND = "Resource not found.";
        public static final String UNABLE_TO_ATTACH_COMPUTE_NOT_FOUND = "Unable to attach. Compute not found.";
        public static final String UNABLE_TO_ATTACH_VOLUME_NOT_FOUND = "Unable to attach. Volume not found.";
    }
}

