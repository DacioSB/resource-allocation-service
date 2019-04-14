package cloud.fogbow.ras.core.plugins.interoperability.opennebula.attachment.v5_4;

import java.util.Properties;

import org.apache.log4j.Logger;
import org.opennebula.client.Client;
import org.opennebula.client.OneResponse;
import org.opennebula.client.image.Image;
import org.opennebula.client.image.ImagePool;
import org.opennebula.client.vm.VirtualMachine;

import cloud.fogbow.common.exceptions.FatalErrorException;
import cloud.fogbow.common.exceptions.FogbowException;
import cloud.fogbow.common.exceptions.InstanceNotFoundException;
import cloud.fogbow.common.exceptions.InvalidParameterException;
import cloud.fogbow.common.exceptions.UnauthorizedRequestException;
import cloud.fogbow.common.models.CloudUser;
import cloud.fogbow.common.util.PropertiesUtil;
import cloud.fogbow.common.util.connectivity.cloud.opennebula.OpenNebulaTagNameConstants;
import cloud.fogbow.ras.api.http.response.AttachmentInstance;
import cloud.fogbow.ras.api.http.response.InstanceState;
import cloud.fogbow.ras.constants.Messages;
import cloud.fogbow.ras.core.models.ResourceType;
import cloud.fogbow.ras.core.models.orders.AttachmentOrder;
import cloud.fogbow.ras.core.plugins.interoperability.AttachmentPlugin;
import cloud.fogbow.ras.core.plugins.interoperability.opennebula.OpenNebulaClientUtil;
import cloud.fogbow.ras.core.plugins.interoperability.opennebula.OpenNebulaConfigurationPropertyKeys;
import cloud.fogbow.ras.core.plugins.interoperability.opennebula.OpenNebulaStateMapper;
import cloud.fogbow.ras.core.plugins.interoperability.opennebula.XmlUnmarshaller;

public class OpenNebulaAttachmentPlugin implements AttachmentPlugin<CloudUser> {

	private static final Logger LOGGER = Logger.getLogger(OpenNebulaAttachmentPlugin.class);
	
	private static final String DEFAULT_DEVICE_PREFIX = "vd";
    private static final String INSTANCE_ID= "%s %s %s";
	private static final String SEPARATOR_ID = " ";
	private String endpoint;

	public OpenNebulaAttachmentPlugin(String confFilePath) throws FatalErrorException {
		Properties properties = PropertiesUtil.readProperties(confFilePath);
		this.endpoint = properties.getProperty(OpenNebulaConfigurationPropertyKeys.OPENNEBULA_RPC_ENDPOINT_KEY);
	}

	@Override
	public boolean isReady(String cloudState) {
		return OpenNebulaStateMapper.map(ResourceType.ATTACHMENT, cloudState).equals(InstanceState.READY);
	}

	@Override
	public boolean hasFailed(String cloudState) {
		return OpenNebulaStateMapper.map(ResourceType.ATTACHMENT, cloudState).equals(InstanceState.FAILED);
	}

	@Override
	public String requestInstance(AttachmentOrder attachmentOrder, CloudUser cloudUser) throws FogbowException {
		LOGGER.info(String.format(Messages.Info.REQUESTING_INSTANCE, cloudUser));
		Client client = OpenNebulaClientUtil.createClient(this.endpoint, cloudUser.getToken());

		String virtualMachineId = attachmentOrder.getComputeId();
		String imageId = attachmentOrder.getVolumeId();

		CreateAttachmentRequest request = new CreateAttachmentRequest.Builder()
				.imageId(imageId)
				.build();
		
		String template = request.getAttachDisk().marshalTemplate();
		
		VirtualMachine virtualMachine = attachVolumeImageDisk(client, virtualMachineId, imageId, template);
		String diskId = getDiskIdFromContenOf(virtualMachine);
		String instanceId = String.format(INSTANCE_ID, virtualMachineId, imageId, diskId);
		return instanceId;
	}

	@Override
	public void deleteInstance(String attachmentInstanceId, CloudUser cloudUser) throws FogbowException {
		LOGGER.info(String.format(Messages.Info.GETTING_INSTANCE, attachmentInstanceId, cloudUser));
		Client client = OpenNebulaClientUtil.createClient(this.endpoint, cloudUser.getToken());

		String[] instanceIds = attachmentInstanceId.split(SEPARATOR_ID);
		int virtualMachineId = Integer.parseInt(instanceIds[0]);
		int diskId = Integer.parseInt(instanceIds[2]);

		// This detach operation is returning ok status but the volume is not being
		// detached from the virtual machine in the opennebula cloud, preventing
		// removing this volume until the virtual machine is removed. This is a documented
		// issue in OpenNebula v5.4.
		OneResponse response = VirtualMachine.diskDetach(client, virtualMachineId, diskId);
		if (response.isError()) {
			String message = response.getErrorMessage();
			String.format(Messages.Error.ERROR_WHILE_DETACHING_VOLUME, diskId, message);
			LOGGER.error(String.format(Messages.Error.ERROR_MESSAGE, message));
		}
	}

	@Override
	public AttachmentInstance getInstance(String attachmentInstanceId, CloudUser cloudUser) throws FogbowException {
		Client client = OpenNebulaClientUtil.createClient(this.endpoint, cloudUser.getToken());
		String[] instanceIds = attachmentInstanceId.split(SEPARATOR_ID);
		String virtualMachineId = instanceIds[0];
		String imageId = instanceIds[1];
		ImagePool imagePool = OpenNebulaClientUtil.getImagePool(client);
		Image image = imagePool.getById(Integer.parseInt(imageId));
		String imageDevice = image.xpath(DEFAULT_DEVICE_PREFIX);
		String imageState = image.stateString();

		AttachmentInstance attachmentInstance = new AttachmentInstance(
				attachmentInstanceId, 
				imageState,
				virtualMachineId, 
				imageId, 
				imageDevice);
		
		return attachmentInstance;
	}

    private String getDiskIdFromContenOf(VirtualMachine virtualMachine) {
		OneResponse response = virtualMachine.info();
		String xml = response.getMessage();
		XmlUnmarshaller xmlUnmarshaller = new XmlUnmarshaller(xml);
		String content = xmlUnmarshaller.getContentOfLastElement(OpenNebulaTagNameConstants.DISK_ID);
		return content;
	}

	private VirtualMachine attachVolumeImageDisk(Client client, String virtualMachineId, String imageId, String template) 
			throws UnauthorizedRequestException, InstanceNotFoundException, InvalidParameterException {
		
		VirtualMachine virtualMachine = OpenNebulaClientUtil.getVirtualMachine(client, virtualMachineId);
		OneResponse response = virtualMachine.diskAttach(template);
		if (response.isError()) {
			String message = response.getErrorMessage();
			LOGGER.error(String.format(Messages.Error.ERROR_WHILE_ATTACHING_VOLUME, imageId, response.getMessage()));
			LOGGER.error(String.format(Messages.Error.ERROR_MESSAGE, message));
			throw new InvalidParameterException();
		}
		return virtualMachine;
	}
}
