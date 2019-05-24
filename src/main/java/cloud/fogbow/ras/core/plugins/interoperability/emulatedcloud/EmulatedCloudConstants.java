package cloud.fogbow.ras.core.plugins.interoperability.emulatedcloud;

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

        public static class PublicIp {

            public static final String ID_KEY_JSON = "id";
            public static final String STATUS_KEY_JSON = "status";
        }
    }
    public static class Json {
        public static final String CIDR_KEY_JSON = "cidr";
        public static final String CLOUD_STATE_KEY_JSON = "cloudState";
        public static final String CLOUD_NAME_KEY_JSON = "cloudName";
        public static final String COMPUTE_ID_KEY_JSON = "computeId";
        public static final String DEVICE_KEY_JSON = "device";
        public static final String DIRECTION_KEY_JSON = "direction";
        public static final String ETHER_TYPE_KEY_JSON = "etherType";
        public static final String FLOATING_IP_KEY_JSON = "floatingip";
        public static final String INSTANCE_KEY_JSON = "instanceId";
        public static final String PORT_FROM_KEY_JSON = "portFrom";
        public static final String PORT_TO_KEY_JSON = "portTo";
        public static final String PROTOCOL_KEY_JSON = "protocol";
        public static final String PROVIDER_KEY_JSON = "provider";
        public static final String SECURITY_RULES_KEY_JSON = "securityRules";
        public static final String STATE_KEY_JSON = "state";
        public static final String VOLUME_ATTACHMENT_KEY_JSON = "volumeAttachment";
        public static final String VOLUME_ID_KEY_JSON = "volumeId";
    }
}
