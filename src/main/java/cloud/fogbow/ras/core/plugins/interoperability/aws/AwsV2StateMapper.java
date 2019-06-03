package cloud.fogbow.ras.core.plugins.interoperability.aws;

import org.apache.log4j.Logger;

import cloud.fogbow.ras.api.http.response.InstanceState;
import cloud.fogbow.ras.constants.Messages;
import cloud.fogbow.ras.core.models.ResourceType;

public class AwsV2StateMapper {

	private static final Logger LOGGER = Logger.getLogger(AwsV2StateMapper.class);

	private static final String ATTACHMENT_PLUGIN = "AwsV2AttachmentPlugin";
	private static final String VOLUME_PLUGIN = "AwsV2VolumePlugin";
	private static final String IMAGE_PLUGIN = "AwsV2ImagePlugin";

	public static final String ATTACHED_STATE = "attached";
	public static final String ATTACHING_STATE = "attaching";
	public static final String AVAILABLE_STATE = "available";
	public static final String BUSY_STATE = "busy";
	public static final String CREATING_STATE = "creating";
	public static final String DELETING_STATE = "deleting";
	public static final String DEREGISTERED_STATE = "deregistered";
	public static final String DETACHING_STATE = "detaching";
	public static final String ERROR_STATE = "error";
	public static final String FAILED_STATE = "failed";
	public static final String IN_USE_STATE = "in-use";
	public static final String INVALID_STATE = "invalid";
	public static final String PENDING_STATE = "pending";
	public static final String RUNNING_STATE = "running";
	public static final String TRANSIENT_STATE = "transient";
	public static final String UNKNOWN_TO_SDK_VERSION_STATE = "unknown_to_sdk_version";

	public static InstanceState map(ResourceType type, String state) {
		state = state.toLowerCase();
		switch (type) {
		case ATTACHMENT:
			// cloud state values: [attaching, attached, detaching, detached, busy]
			switch (state) {
			case ATTACHING_STATE:
				return InstanceState.CREATING;
			case ATTACHED_STATE:
				return InstanceState.READY;
			case DETACHING_STATE:
			case BUSY_STATE:
				return InstanceState.BUSY;
			default:
				LOGGER.error(String.format(Messages.Error.UNDEFINED_INSTANCE_STATE_MAPPING, state, ATTACHMENT_PLUGIN));
				return InstanceState.INCONSISTENT;
			}
		case VOLUME:
			// cloud state values: [creating, available, in-use, deleting, deleted, error]
			switch (state) {
			case CREATING_STATE:
				return InstanceState.CREATING;
			case AVAILABLE_STATE:
			case IN_USE_STATE:
				return InstanceState.READY;
			case DELETING_STATE:
				return InstanceState.BUSY;
			case ERROR_STATE:
				return InstanceState.FAILED;
			default:
				LOGGER.error(String.format(Messages.Error.UNDEFINED_INSTANCE_STATE_MAPPING, state, VOLUME_PLUGIN));
				return InstanceState.INCONSISTENT;
			}
		case IMAGE:
			// cloud state values : [available, deregistered, error, failed, invalid, pending, transient, unknown_to_sdk_version]
			switch (state) {
			case AVAILABLE_STATE:
				return InstanceState.READY;
			case DEREGISTERED_STATE:
			case ERROR_STATE:
			case FAILED_STATE:
			case INVALID_STATE:
			case UNKNOWN_TO_SDK_VERSION_STATE:
				return InstanceState.FAILED;
			case PENDING_STATE:
			case TRANSIENT_STATE:
				return InstanceState.BUSY;
			default:
				LOGGER.error(String.format(Messages.Error.UNDEFINED_INSTANCE_STATE_MAPPING, state, IMAGE_PLUGIN));
				return InstanceState.INCONSISTENT;
			}
		default:
			LOGGER.error(Messages.Error.INSTANCE_TYPE_NOT_DEFINED);
			return InstanceState.INCONSISTENT;
		}
	}
}