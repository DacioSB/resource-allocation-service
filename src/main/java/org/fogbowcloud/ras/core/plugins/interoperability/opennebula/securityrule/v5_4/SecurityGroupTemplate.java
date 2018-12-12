package org.fogbowcloud.ras.core.plugins.interoperability.opennebula.securityrule.v5_4;

import static org.fogbowcloud.ras.core.plugins.interoperability.opennebula.OpenNebulaTagNameConstants.ID;
import static org.fogbowcloud.ras.core.plugins.interoperability.opennebula.OpenNebulaTagNameConstants.NAME;
import static org.fogbowcloud.ras.core.plugins.interoperability.opennebula.OpenNebulaTagNameConstants.RULE;
import static org.fogbowcloud.ras.core.plugins.interoperability.opennebula.OpenNebulaTagNameConstants.TEMPLATE;

import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.fogbowcloud.ras.core.plugins.interoperability.opennebula.OpenNebulaMarshallerTemplate;

@XmlRootElement(name = TEMPLATE)
public class SecurityGroupTemplate extends OpenNebulaMarshallerTemplate {

	public static final String RANGE_FORMAT = "%s:s%";
	
	private String id;
	private String name;
	private List<Rule> rules;

	public SecurityGroupTemplate() {}

	public SecurityGroupTemplate(String id, String name, List<Rule> rules) {
		this.id = id;
		this.name = name;
		this.rules = rules;
	}
	
	@XmlElement(name = ID)
	public String getId() {
		return id;
	}
	
	@XmlElement(name = NAME)
	public String getName() {
		return name;
	}

	@XmlElement(name = RULE)
	public List<Rule> getRules() {
		return rules;
	}

	public static Rule allocateSafetyRule(String protocol, String type, String ip, 
			String size, int portFrom, int portTo, String networkId) {

		String range = String.format(RANGE_FORMAT, String.valueOf(portFrom), String.valueOf(portTo));

		Rule rule = new Rule();
		rule.setProtocol(protocol);
		rule.setIp(ip);
		rule.setSize(size);
		rule.setRange(range);
		rule.setType(type);
		rule.setNetworkId(networkId);

		return rule;
	}
}
