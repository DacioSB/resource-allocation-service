package org.fogbowcloud.manager.core.models.orders;

public enum OrderType {
	COMPUTE("compute"), NETWORK("network"), STORAGE("storage");

	private String value;

	private OrderType(String value) {
		this.value = value;
	}

	public String getValue() {
		return this.value;
	}
}
