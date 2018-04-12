package org.fogbowcloud.manager.core.models.orders;

import org.fogbowcloud.manager.core.models.NetworkLink;
import org.fogbowcloud.manager.core.models.StorageLink;

public class ComputeOrder extends Order {

	private int vCPU;
	/** Memory attribute, must be set in MB. */
	private int memory;
	/** Disk attribute, must be set in GB. */
	private int disk;
	private String imageName;
	private UserData userData;
	private NetworkLink networkLink;
	private StorageLink storageLink;

	public int getvCPU() {
		return vCPU;
	}

	public void setvCPU(int vCPU) {
		this.vCPU = vCPU;
	}

	public int getMemory() {
		return memory;
	}

	public void setMemory(int memory) {
		this.memory = memory;
	}

	public int getDisk() {
		return disk;
	}

	public void setDisk(int disk) {
		this.disk = disk;
	}
	
	public String getImageName() {
		return imageName;
	}

	public void setImageName(String imageName) {
		this.imageName = imageName;
	}

	public UserData getUserData() {
		return userData;
	}

	public void setUserData(UserData userData) {
		this.userData = userData;
	}

	public NetworkLink getNetworkLink() {
		return networkLink;
	}

	public void setNetworkLink(NetworkLink networkLink) {
		this.networkLink = networkLink;
	}

	public StorageLink getStorageLink() {
		return storageLink;
	}

	public void setStorageLink(StorageLink storageLink) {
		this.storageLink = storageLink;
	}
	
	@Override
	public OrderType getType() {
		return OrderType.COMPUTE;
	}

	@Override
	public void handleOpenOrder() {
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + disk;
		result = prime * result + memory;
		result = prime * result + ((networkLink == null) ? 0 : networkLink.hashCode());
		result = prime * result + ((storageLink == null) ? 0 : storageLink.hashCode());
		result = prime * result + ((userData == null) ? 0 : userData.hashCode());
		result = prime * result + vCPU;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ComputeOrder other = (ComputeOrder) obj;
		if (disk != other.disk)
			return false;
		if (memory != other.memory)
			return false;
		if (networkLink == null) {
			if (other.networkLink != null)
				return false;
		} else if (!networkLink.equals(other.networkLink))
			return false;
		if (storageLink == null) {
			if (other.storageLink != null)
				return false;
		} else if (!storageLink.equals(other.storageLink))
			return false;
		if (userData == null) {
			if (other.userData != null)
				return false;
		} else if (!userData.equals(other.userData))
			return false;
		if (vCPU != other.vCPU)
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "ComputeOrder [vCPU=" + vCPU + ", memory=" + memory + ", disk=" + disk + ", userData=" + userData
				+ ", networkLink=" + networkLink + ", storageLink=" + storageLink + "]";
	}
}
