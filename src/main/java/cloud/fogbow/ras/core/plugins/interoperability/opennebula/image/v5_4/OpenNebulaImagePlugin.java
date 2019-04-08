package cloud.fogbow.ras.core.plugins.interoperability.opennebula.image.v5_4;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.opennebula.client.Client;
import org.opennebula.client.image.ImagePool;

import cloud.fogbow.common.exceptions.FatalErrorException;
import cloud.fogbow.common.exceptions.FogbowException;
import cloud.fogbow.common.exceptions.InvalidParameterException;
import cloud.fogbow.common.models.CloudUser;
import cloud.fogbow.common.util.PropertiesUtil;
import cloud.fogbow.ras.api.http.response.Image;
import cloud.fogbow.ras.api.http.response.InstanceState;
import cloud.fogbow.ras.constants.Messages;
import cloud.fogbow.ras.core.models.ResourceType;
import cloud.fogbow.ras.core.plugins.interoperability.ImagePlugin;
import cloud.fogbow.ras.core.plugins.interoperability.opennebula.OpenNebulaClientUtil;
import cloud.fogbow.ras.core.plugins.interoperability.opennebula.OpenNebulaConfigurationPropertyKeys;
import cloud.fogbow.ras.core.plugins.interoperability.opennebula.OpenNebulaStateMapper;

public class OpenNebulaImagePlugin implements ImagePlugin<CloudUser> {

	private static final Logger LOGGER = Logger.getLogger(OpenNebulaImagePlugin.class);

	private static final String RESOURCE_NAME = "image";

	protected static final String IMAGE_SIZE_PATH = "/IMAGE/SIZE";

	private String endpoint;
	
	public OpenNebulaImagePlugin(String confFilePath) throws FatalErrorException {
		Properties properties = PropertiesUtil.readProperties(confFilePath);
		this.endpoint = properties.getProperty(OpenNebulaConfigurationPropertyKeys.OPENNEBULA_RPC_ENDPOINT_KEY);
	}
	
	@Override
	public Map<String, String> getAllImages(CloudUser cloudUser) throws FogbowException {
		LOGGER.info(String.format(Messages.Info.RECEIVING_GET_ALL_REQUEST, RESOURCE_NAME));
		Client client = OpenNebulaClientUtil.createClient(this.endpoint, cloudUser.getToken());
		ImagePool imagePool = OpenNebulaClientUtil.getImagePool(client);
		Map<String, String> allImages = new HashMap<>();
		for (org.opennebula.client.image.Image image : imagePool) {
			allImages.put(image.getId(), image.getName());
		}
		return allImages;
	}

	@Override
	public Image getImage(String imageId, CloudUser cloudUser) throws FogbowException {
		LOGGER.info(String.format(Messages.Info.GETTING_INSTANCE, imageId, cloudUser.getToken()));
		Client client = OpenNebulaClientUtil.createClient(this.endpoint, cloudUser.getToken());
		org.opennebula.client.image.Image image = OpenNebulaClientUtil.getImage(client, imageId);
		return mount(image);
	}

	protected Image mount(org.opennebula.client.image.Image image) throws InvalidParameterException {
		String id = image.getId();
		String name = image.getName();
		String imageSize = image.xpath(IMAGE_SIZE_PATH);
		int size = convertToInteger(imageSize);
		int minDisk = -1;
		int minRam = -1;
		int state = image.state();
		InstanceState instanceState = OpenNebulaStateMapper.map(ResourceType.IMAGE, state);
		String status = instanceState.getValue();
		return new Image(id, name, size, minDisk, minRam, status);
	}
	
	protected int convertToInteger(String number) throws InvalidParameterException {
		try {
			return Integer.parseInt(number);
		} catch (NumberFormatException e) {
			LOGGER.error(String.format(Messages.Error.ERROR_WHILE_CONVERTING_TO_INTEGER), e);
			throw new InvalidParameterException();
		}
	}

}
