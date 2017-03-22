/*
 * Copyright 2016-present PLVision
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * PLVision, developers@plvision.eu
 */
 
package com.plvision.switchover.fwd;

import java.util.Dictionary;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Modified;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.onlab.packet.Ethernet;
import org.onlab.packet.ICMP;
import org.onlab.packet.ICMP6;
import org.onlab.packet.IPv4;
import org.onlab.packet.IPv6;
import org.onlab.packet.Ip4Prefix;
import org.onlab.packet.Ip6Prefix;
import org.onlab.packet.MacAddress;
import org.onlab.packet.TCP;
import org.onlab.packet.TpPort;
import org.onlab.packet.UDP;
import org.onlab.packet.VlanId;
import org.onlab.util.Tools;
import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.event.Event;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Host;
import org.onosproject.net.HostId;
import org.onosproject.net.Link;
import org.onosproject.net.Path;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowEntry;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.criteria.Criterion;
import org.onosproject.net.flow.criteria.EthCriterion;
import org.onosproject.net.flow.instructions.Instruction;
import org.onosproject.net.flow.instructions.Instructions;
import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.host.HostService;
import org.onosproject.net.link.LinkEvent;
import org.onosproject.net.link.LinkService;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.topology.TopologyEvent;
import org.onosproject.net.topology.TopologyListener;
import org.onosproject.net.topology.TopologyService;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;
import com.plvision.linkmonitor.LinkInfo;
import com.plvision.linkmonitor.LinkMonitorEvent;
import com.plvision.linkmonitor.LinkMonitorListener;
import com.plvision.linkmonitor.LinkMonitorService;

/**
 * Switchover forwarding ONOS application. (Based on OnosLab Reactive Forwarding Application)
 *
 * @category Traffic steering
 * @since 13 September 2016
 * @version 1.0
 *
 */
@Component(immediate = true)
public class SwitchoverForwarding {

	private static final int DEFAULT_TIMEOUT = 10;
	private static final int DEFAULT_PRIORITY = 10;
	private static final int DEFAULT_PARH_COST_DEEP = 2;

	private final Logger log = LoggerFactory.getLogger(getClass());

	@Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
	protected TopologyService topologyService;

	@Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
	protected PacketService packetService;

	@Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
	protected HostService hostService;

	@Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
	protected FlowRuleService flowRuleService;

	@Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
	protected FlowObjectiveService flowObjectiveService;

	@Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
	protected CoreService coreService;

	@Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
	protected ComponentConfigService cfgService;

	@Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
	protected LinkService linkService;

	@Property(name = "packetOutOnly", boolValue = false, label = "Enable packet-out only forwarding; default is false")
	private boolean packetOutOnly = false;

	@Property(name = "packetOutOfppTable", boolValue = false, label = "Enable first packet forwarding using OFPP_TABLE port " + "instead of PacketOut with actual port; default is false")
	private boolean packetOutOfppTable = false;

	@Property(name = "flowTimeout", intValue = DEFAULT_TIMEOUT, label = "Flow Timeout for installed flow rules; " + "default is 10 sec")
	private int flowTimeout = DEFAULT_TIMEOUT;

	@Property(name = "flowPriority", intValue = DEFAULT_PRIORITY, label = "Flow Priority for installed flow rules; " + "default is 10")
	private int flowPriority = DEFAULT_PRIORITY;

	@Property(name = "ipv6Forwarding", boolValue = false, label = "Enable IPv6 forwarding; default is false")
	private boolean ipv6Forwarding = false;

	@Property(name = "matchDstMacOnly", boolValue = false, label = "Enable matching Dst Mac Only; default is false")
	private boolean matchDstMacOnly = false;

	@Property(name = "matchVlanId", boolValue = false, label = "Enable matching Vlan ID; default is false")
	private boolean matchVlanId = false;

	@Property(name = "matchIpv4Address", boolValue = false, label = "Enable matching IPv4 Addresses; default is false")
	private boolean matchIpv4Address = false;

	@Property(name = "matchIpv4Dscp", boolValue = false, label = "Enable matching IPv4 DSCP and ECN; default is false")
	private boolean matchIpv4Dscp = false;

	@Property(name = "matchIpv6Address", boolValue = false, label = "Enable matching IPv6 Addresses; default is false")
	private boolean matchIpv6Address = false;

	@Property(name = "matchIpv6FlowLabel", boolValue = false, label = "Enable matching IPv6 FlowLabel; default is false")
	private boolean matchIpv6FlowLabel = false;

	@Property(name = "matchTcpUdpPorts", boolValue = false, label = "Enable matching TCP/UDP ports; default is false")
	private boolean matchTcpUdpPorts = false;

	@Property(name = "matchIcmpFields", boolValue = false, label = "Enable matching ICMPv4 and ICMPv6 fields; default is false")
	private boolean matchIcmpFields = false;

	@Property(name = "ignoreIPv4Multicast", boolValue = false, label = "Ignore (do not forward) IPv4 multicast packets; default is false")
	private boolean ignoreIpv4McastPackets = false;

	@Property(name = "rebuildPathFromSrc", boolValue = true, label = "Enable rebuild path from src device; default is true")
	private boolean rebuildPathFromSrc = true;

	@Property(name = "pathSearchDepth", intValue = DEFAULT_PARH_COST_DEEP, label = "Depth of search of backup paths in path cost units, relatively the cost of primary path; " + "default is 2")
	private int pathSearchDepth = DEFAULT_PARH_COST_DEEP;

	private SwitchoverPacketProcessor processor = new SwitchoverPacketProcessor();

	private final TopologyListener topologyListener = new InternalTopologyListener();

	private Object linkMonitorListener = null;

	private Object linkMonitorService = null;

	private Boolean monitorConnected = false;

	private ApplicationId appId;

	// Application context
	protected ComponentContext context;

	private boolean DoUpdatefromSrc = false;

	/**
	 * Activate application method.
	 * @param context - the component context
	 */
	@Activate
	public void activate(ComponentContext context) {
		this.context = context;
		appId = coreService.registerApplication("com.plvision.switchover.fwd");
		// Register properties
		cfgService.registerProperties(getClass());
		// Read configuration
		readComponentConfiguration(context);
		// Add packet processor
		packetService.addProcessor(processor, PacketProcessor.director(2));
		// Add topology listener
		topologyService.addListener(topologyListener);
		// Create request for packets interception
		requestIntercepts();
		// Try to connect to link quality monitoring service
		connectToLinkMonitor();
		log.info("Started");
	}

	/**
	 * Deactivate application method.
	 */
	@Deactivate
	public void deactivate() {
		disconnectFromLinkMonitor();
		// Unregister properties
		cfgService.unregisterProperties(getClass(), false);
		// Cancel packets interception
		withdrawIntercepts();
		// Remove flow rules
		flowRuleService.removeFlowRulesById(appId);
		// Remove packet processor
		packetService.removeProcessor(processor);
		// Remove topology listener
		topologyService.removeListener(topologyListener);
		processor = null;
		log.info("Stopped");
	}

	/**
	 * Modify application method.
	 * @param context - the component context
	 */
	@Modified
	public void modified(ComponentContext context) {
		readComponentConfiguration(context);
		requestIntercepts();
	}

	/**
	 * Try to connect to link quality monitoring service
	 */
	private void connectToLinkMonitor() {
		if (monitorConnected == true) return;
		log.info("Try to connect to Link quality monitor service");
		try {
			@SuppressWarnings("rawtypes")
			Class cl = Class.forName("com.plvision.linkmonitor.LinkMonitorService", false, this.getClass().getClassLoader());
			if (cl != null) {
				ServiceReference sr = context.getBundleContext().getServiceReference(cl.getName());
				linkMonitorService = (LinkMonitorService) context.getBundleContext().getService(sr);
			}
			if (linkMonitorService != null) {
				linkMonitorListener = new InternalLinkMonitorListener();
				((LinkMonitorService) linkMonitorService).addListener((LinkMonitorListener) linkMonitorListener);
			}
			monitorConnected = true;
			log.info("Connected to Link quality monitor service");
		} catch (Exception ex) {
		}
	}

	/**
	 * Disconnect from link quality monitoring service
	 */
	private void disconnectFromLinkMonitor() {
		if (monitorConnected == false) return;
		monitorConnected = false;
		try {
			if (linkMonitorService != null) {
				if (linkMonitorListener != null) {
					((LinkMonitorService) linkMonitorService).removeListener((LinkMonitorListener) linkMonitorListener);
					linkMonitorListener = null;
				}
				linkMonitorService = null;
			}
			log.info("Disconnected from Link quality monitor service");
		} catch (Exception ex) {
		}
	}
	
	/**
	 * Request packet in via packet service.
	 */
	@SuppressWarnings("deprecation")
	private void requestIntercepts() {
		TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
		selector.matchEthType(Ethernet.TYPE_IPV4);
		packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId);
		selector.matchEthType(Ethernet.TYPE_ARP);
		packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId);
		selector.matchEthType(Ethernet.TYPE_IPV6);
		if (ipv6Forwarding) {
			packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId);
		} else {
			packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, appId);
		}
	}

	/**
	 * Cancel request for packet in via packet service.
	 */
	@SuppressWarnings("deprecation")
	private void withdrawIntercepts() {
		TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
		selector.matchEthType(Ethernet.TYPE_IPV4);
		packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, appId);
		selector.matchEthType(Ethernet.TYPE_ARP);
		packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, appId);
		selector.matchEthType(Ethernet.TYPE_IPV6);
		packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, appId);
	}

	/**
	 * Extracts properties from the component configuration context.
	 *
	 * @param context - the component context
	 */
	private void readComponentConfiguration(ComponentContext context) {
		Dictionary<?, ?> properties = context.getProperties();

		Boolean packetOutOnlyEnabled = Tools.isPropertyEnabled(properties, "packetOutOnly");
		if (packetOutOnlyEnabled == null) {
			log.info("Packet-out is not configured, " + "using current value of {}", packetOutOnly);
		} else {
			packetOutOnly = packetOutOnlyEnabled;
			log.info("Configured. Packet-out only forwarding is {}", packetOutOnly ? "enabled" : "disabled");
		}

		Boolean packetOutOfppTableEnabled = Tools.isPropertyEnabled(properties, "packetOutOfppTable");
		if (packetOutOfppTableEnabled == null) {
			log.info("OFPP_TABLE port is not configured, " + "using current value of {}", packetOutOfppTable);
		} else {
			packetOutOfppTable = packetOutOfppTableEnabled;
			log.info("Configured. Forwarding using OFPP_TABLE port is {}", packetOutOfppTable ? "enabled" : "disabled");
		}

		Boolean ipv6ForwardingEnabled = Tools.isPropertyEnabled(properties, "ipv6Forwarding");
		if (ipv6ForwardingEnabled == null) {
			log.info("IPv6 forwarding is not configured, " + "using current value of {}", ipv6Forwarding);
		} else {
			ipv6Forwarding = ipv6ForwardingEnabled;
			log.info("Configured. IPv6 forwarding is {}", ipv6Forwarding ? "enabled" : "disabled");
		}

		Boolean matchDstMacOnlyEnabled = Tools.isPropertyEnabled(properties, "matchDstMacOnly");
		if (matchDstMacOnlyEnabled == null) {
			log.info("Match Dst MAC is not configured, " + "using current value of {}", matchDstMacOnly);
		} else {
			matchDstMacOnly = matchDstMacOnlyEnabled;
			log.info("Configured. Match Dst MAC Only is {}", matchDstMacOnly ? "enabled" : "disabled");
		}

		Boolean matchVlanIdEnabled = Tools.isPropertyEnabled(properties, "matchVlanId");
		if (matchVlanIdEnabled == null) {
			log.info("Matching Vlan ID is not configured, " + "using current value of {}", matchVlanId);
		} else {
			matchVlanId = matchVlanIdEnabled;
			log.info("Configured. Matching Vlan ID is {}", matchVlanId ? "enabled" : "disabled");
		}

		Boolean matchIpv4AddressEnabled = Tools.isPropertyEnabled(properties, "matchIpv4Address");
		if (matchIpv4AddressEnabled == null) {
			log.info("Matching IPv4 Address is not configured, " + "using current value of {}", matchIpv4Address);
		} else {
			matchIpv4Address = matchIpv4AddressEnabled;
			log.info("Configured. Matching IPv4 Addresses is {}", matchIpv4Address ? "enabled" : "disabled");
		}

		Boolean matchIpv4DscpEnabled = Tools.isPropertyEnabled(properties, "matchIpv4Dscp");
		if (matchIpv4DscpEnabled == null) {
			log.info("Matching IPv4 DSCP and ECN is not configured, " + "using current value of {}", matchIpv4Dscp);
		} else {
			matchIpv4Dscp = matchIpv4DscpEnabled;
			log.info("Configured. Matching IPv4 DSCP and ECN is {}", matchIpv4Dscp ? "enabled" : "disabled");
		}

		Boolean matchIpv6AddressEnabled = Tools.isPropertyEnabled(properties, "matchIpv6Address");
		if (matchIpv6AddressEnabled == null) {
			log.info("Matching IPv6 Address is not configured, " + "using current value of {}", matchIpv6Address);
		} else {
			matchIpv6Address = matchIpv6AddressEnabled;
			log.info("Configured. Matching IPv6 Addresses is {}", matchIpv6Address ? "enabled" : "disabled");
		}

		Boolean matchIpv6FlowLabelEnabled = Tools.isPropertyEnabled(properties, "matchIpv6FlowLabel");
		if (matchIpv6FlowLabelEnabled == null) {
			log.info("Matching IPv6 FlowLabel is not configured, " + "using current value of {}", matchIpv6FlowLabel);
		} else {
			matchIpv6FlowLabel = matchIpv6FlowLabelEnabled;
			log.info("Configured. Matching IPv6 FlowLabel is {}", matchIpv6FlowLabel ? "enabled" : "disabled");
		}

		Boolean matchTcpUdpPortsEnabled = Tools.isPropertyEnabled(properties, "matchTcpUdpPorts");
		if (matchTcpUdpPortsEnabled == null) {
			log.info("Matching TCP/UDP fields is not configured, " + "using current value of {}", matchTcpUdpPorts);
		} else {
			matchTcpUdpPorts = matchTcpUdpPortsEnabled;
			log.info("Configured. Matching TCP/UDP fields is {}", matchTcpUdpPorts ? "enabled" : "disabled");
		}

		Boolean matchIcmpFieldsEnabled = Tools.isPropertyEnabled(properties, "matchIcmpFields");
		if (matchIcmpFieldsEnabled == null) {
			log.info("Matching ICMP (v4 and v6) fields is not configured, " + "using current value of {}", matchIcmpFields);
		} else {
			matchIcmpFields = matchIcmpFieldsEnabled;
			log.info("Configured. Matching ICMP (v4 and v6) fields is {}", matchIcmpFields ? "enabled" : "disabled");
		}

		Boolean ignoreIpv4McastPacketsEnabled = Tools.isPropertyEnabled(properties, "ignoreIPv4Multicast");
		if (ignoreIpv4McastPacketsEnabled == null) {
			log.info("Matching Ignore IPv4 multi-cast packet is not configured, " + "using current value of {}", ignoreIpv4McastPackets);
		} else {
			ignoreIpv4McastPackets = ignoreIpv4McastPacketsEnabled;
			log.info("Configured. Ignore IPv4 multicast packets is {}", ignoreIpv4McastPackets ? "enabled" : "disabled");
		}

		Boolean rebuildPathFromSrcEnabled = Tools.isPropertyEnabled(properties, "rebuildPathFromSrc");
		if (rebuildPathFromSrcEnabled == null) {
			log.info("Matching Enable rebuild path from src device is not configured");
		} else {
			rebuildPathFromSrc = rebuildPathFromSrcEnabled;
			log.info("Configured. Rebuild path from src device is {}", rebuildPathFromSrc ? "enabled" : "disabled");
		}

		flowTimeout = Tools.getIntegerProperty(properties, "flowTimeout", DEFAULT_TIMEOUT);
		log.info("Configured. Flow Timeout is configured to {} seconds", flowTimeout);

		flowPriority = Tools.getIntegerProperty(properties, "flowPriority", DEFAULT_PRIORITY);
		log.info("Configured. Flow Priority is configured to {}", flowPriority);
		
		pathSearchDepth = Tools.getIntegerProperty(properties, "pathSearchDepth", DEFAULT_PARH_COST_DEEP);
		pathSearchDepth = checkIntRange(pathSearchDepth, 2, 10);
		log.info("Configured. Path Search Depth is configured to {}", pathSearchDepth);
	}

	/**
	 * Check range of property value
	 */
	private int checkIntRange(int value, int min, int max) {
		if (value < min) value = min;
		if (value > max) value = max;
		return value;
	}

	/**
	 * Packet processor responsible for forwarding packets along their paths.
	 */
	private class SwitchoverPacketProcessor implements PacketProcessor {

		@Override
		public void process(PacketContext context) {
			// Stop processing if the packet has been handled, since we can't do any more to it.
			if (context.isHandled()) {
				return;
			}

			InboundPacket pkt = context.inPacket();
			Ethernet ethPkt = pkt.parsed();

			if (ethPkt == null) {
				return;
			}

			// Bail if this is deemed to be a control packet.
			if (isControlPacket(ethPkt)) {
				return;
			}

			// Skip IPv6 multicast packet when IPv6 forward is disabled.
			if (!ipv6Forwarding && isIpv6Multicast(ethPkt)) {
				return;
			}

			HostId id = HostId.hostId(ethPkt.getDestinationMAC());

			// Do not process link-local addresses in any way.
			if (id.mac().isLinkLocal()) {
				return;
			}

			// Do not process IPv4 multicast packets, let mfwd handle them
			if (ignoreIpv4McastPackets && ethPkt.getEtherType() == Ethernet.TYPE_IPV4) {
				if (id.mac().isMulticast()) {
					return;
				}
			}

			// Do we know who this is for? If not, flood and bail.
			Host dst = hostService.getHost(id);
			if (dst == null) {
				flood(context);
				return;
			}

			// Are we on an edge switch that our destination is on? If so,
			// simply forward out to the destination and bail.
			if (pkt.receivedFrom().deviceId().equals(dst.location().deviceId())) {
				if (!context.inPacket().receivedFrom().port().equals(dst.location().port())) {
					installRule(context, dst.location().port());
				}
				return;
			}

			// Otherwise, get a set of paths that lead from here to the
			// destination edge switch.
			Set<Path> paths = PathFinder.result(topologyService, pkt.receivedFrom().deviceId(), dst.location().deviceId(), pathSearchDepth);
			if (paths.isEmpty()) {
				// If there are no paths, flood and bail.
				flood(context);
				return;
			}

			DoUpdatefromSrc = false;
			// Otherwise, pick a path that does not lead back to where we
			// came from; if no such path, flood and bail.
			Path path = pickForwardPathIfPossible(paths, pkt.receivedFrom().port());
			if (path == null) {
				log.warn("Don't know where to go from here {} for {} -> {}",
						pkt.receivedFrom(), ethPkt.getSourceMAC(), ethPkt.getDestinationMAC());
				flood(context);
				return;
			}

			// Otherwise forward and be done with it.
			installRule(context, path.src().port());
			
			if (rebuildPathFromSrc == true) {
				if (DoUpdatefromSrc == true) {
					remakePathFrom(HostId.hostId(ethPkt.getSourceMAC()));
				}
			}
		}
	}

	/**
	 * Indicates whether this is a control packet, e.g. LLDP, BDDP
	 * 
	 * @param context - the component context
	 */
	private boolean isControlPacket(Ethernet eth) {
		short type = eth.getEtherType();
		return type == Ethernet.TYPE_LLDP || type == Ethernet.TYPE_BSN;
	}

	/**
	 * Indicated whether this is an IPv6 multicast packet.
	 * 
	 * @param eth - Ethernet class
	 */
	private boolean isIpv6Multicast(Ethernet eth) {
		return eth.getEtherType() == Ethernet.TYPE_IPV6 && eth.isMulticast();
	}

	/**
	 * Selects a path from the given set that does not lead back to the
	 * specified port if possible.
	 * 
	 * @param paths - paths array
	 * @param notToPort - port number
	 */
	private Path pickForwardPathIfPossible(Set<Path> paths, PortNumber notToPort) {
		Path bestPath = null;
		double LinkQuality = 0;
		Map<Double, Path> resPaths = new HashMap<Double, Path>();
		for (Path path : paths) {
			double MinQuality = 100;
			if (!path.src().port().equals(notToPort)) {
				for (Link link : path.links()) {
					if (monitorConnected) {
						LinkQuality = ((LinkMonitorService) linkMonitorService).getLinkQuality(link);
						if (LinkQuality == -1) break;
						if (LinkQuality < MinQuality) MinQuality = LinkQuality;
					} else {
						return path;
					}
				}
				resPaths.put(MinQuality, path);
				if (MinQuality > ((LinkMonitorService) linkMonitorService).getMaxQualityThreshold()) {
					return path;
				}
			}
		}
		// Here, path with quality 100 is not found, so we will use one from available paths with max quality
		double MaxQuality = 0;
		for (Entry<Double, Path> en : resPaths.entrySet()) {
			if (en.getKey() > MaxQuality) {
				MaxQuality = en.getKey(); 
				bestPath = en.getValue();
			}
		}
		DoUpdatefromSrc = (MaxQuality < 100) ? true : false;
		return bestPath;
	}

	/**
	 * Floods the specified packet if permissible.
	 * 
	 * @param context - the packet context
	 */
	private void flood(PacketContext context) {
		if (topologyService.isBroadcastPoint(topologyService.currentTopology(), context.inPacket().receivedFrom())) {
			packetOut(context, PortNumber.FLOOD);
		} else {
			context.block();
		}
	}

	/**
	 * Sends a packet out the specified port.
	 * 
	 * @param context - the packet context
	 * @param portNumber - port number
	 */
	private void packetOut(PacketContext context, PortNumber portNumber) {
		context.treatmentBuilder().setOutput(portNumber);
		context.send();
	}

	/**
	 * Install a rule forwarding the packet to the specified port.
	 * 
	 * @param context - the packet context
	 * @param portNumber - port number
	 */
	private void installRule(PacketContext context, PortNumber portNumber) {
		//
		// We don't support (yet) buffer IDs in the Flow Service so
		// packet out first.
		//
		Ethernet inPkt = context.inPacket().parsed();
		TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();

		// If PacketOutOnly or ARP packet than forward directly to output port
		if (packetOutOnly || inPkt.getEtherType() == Ethernet.TYPE_ARP) {
			packetOut(context, portNumber);
			return;
		}

		//
		// If matchDstMacOnly
		//    Create flows matching dstMac only
		// Else
		//    Create flows with default matching and include configured fields
		//
		if (matchDstMacOnly) {
			selectorBuilder.matchEthDst(inPkt.getDestinationMAC());
		} else {
			selectorBuilder.matchInPort(context.inPacket().receivedFrom().port()).matchEthSrc(inPkt.getSourceMAC()).matchEthDst(inPkt.getDestinationMAC());

			// If configured Match Vlan ID
			if (matchVlanId && inPkt.getVlanID() != Ethernet.VLAN_UNTAGGED) {
				selectorBuilder.matchVlanId(VlanId.vlanId(inPkt.getVlanID()));
			}

			//
			// If configured and EtherType is IPv4 - Match IPv4 and
			// TCP/UDP/ICMP fields
			//
			if (matchIpv4Address && inPkt.getEtherType() == Ethernet.TYPE_IPV4) {
				IPv4 ipv4Packet = (IPv4) inPkt.getPayload();
				byte ipv4Protocol = ipv4Packet.getProtocol();
				Ip4Prefix matchIp4SrcPrefix = Ip4Prefix.valueOf(ipv4Packet.getSourceAddress(), Ip4Prefix.MAX_MASK_LENGTH);
				Ip4Prefix matchIp4DstPrefix = Ip4Prefix.valueOf(ipv4Packet.getDestinationAddress(), Ip4Prefix.MAX_MASK_LENGTH);
				selectorBuilder.matchEthType(Ethernet.TYPE_IPV4).matchIPSrc(matchIp4SrcPrefix).matchIPDst(matchIp4DstPrefix);

				if (matchIpv4Dscp) {
					byte dscp = ipv4Packet.getDscp();
					byte ecn = ipv4Packet.getEcn();
					selectorBuilder.matchIPDscp(dscp).matchIPEcn(ecn);
				}

				if (matchTcpUdpPorts && ipv4Protocol == IPv4.PROTOCOL_TCP) {
					TCP tcpPacket = (TCP) ipv4Packet.getPayload();
					selectorBuilder.matchIPProtocol(ipv4Protocol).matchTcpSrc(TpPort.tpPort(tcpPacket.getSourcePort())).matchTcpDst(TpPort.tpPort(tcpPacket.getDestinationPort()));
				}
				if (matchTcpUdpPorts && ipv4Protocol == IPv4.PROTOCOL_UDP) {
					UDP udpPacket = (UDP) ipv4Packet.getPayload();
					selectorBuilder.matchIPProtocol(ipv4Protocol).matchUdpSrc(TpPort.tpPort(udpPacket.getSourcePort())).matchUdpDst(TpPort.tpPort(udpPacket.getDestinationPort()));
				}
				if (matchIcmpFields && ipv4Protocol == IPv4.PROTOCOL_ICMP) {
					ICMP icmpPacket = (ICMP) ipv4Packet.getPayload();
					selectorBuilder.matchIPProtocol(ipv4Protocol).matchIcmpType(icmpPacket.getIcmpType()).matchIcmpCode(icmpPacket.getIcmpCode());
				}
			}

			//
			// If configured and EtherType is IPv6 - Match IPv6 and
			// TCP/UDP/ICMP fields
			//
			if (matchIpv6Address && inPkt.getEtherType() == Ethernet.TYPE_IPV6) {
				IPv6 ipv6Packet = (IPv6) inPkt.getPayload();
				byte ipv6NextHeader = ipv6Packet.getNextHeader();
				Ip6Prefix matchIp6SrcPrefix = Ip6Prefix.valueOf(ipv6Packet.getSourceAddress(), Ip6Prefix.MAX_MASK_LENGTH);
				Ip6Prefix matchIp6DstPrefix = Ip6Prefix.valueOf(ipv6Packet.getDestinationAddress(), Ip6Prefix.MAX_MASK_LENGTH);
				selectorBuilder.matchEthType(Ethernet.TYPE_IPV6).matchIPv6Src(matchIp6SrcPrefix).matchIPv6Dst(matchIp6DstPrefix);
				
				if (matchIpv6FlowLabel) {
					selectorBuilder.matchIPv6FlowLabel(ipv6Packet.getFlowLabel());
				}
				
				if (matchTcpUdpPorts && ipv6NextHeader == IPv6.PROTOCOL_TCP) {
					TCP tcpPacket = (TCP) ipv6Packet.getPayload();
					selectorBuilder.matchIPProtocol(ipv6NextHeader).matchTcpSrc(TpPort.tpPort(tcpPacket.getSourcePort())).matchTcpDst(TpPort.tpPort(tcpPacket.getDestinationPort()));
				}
				if (matchTcpUdpPorts && ipv6NextHeader == IPv6.PROTOCOL_UDP) {
					UDP udpPacket = (UDP) ipv6Packet.getPayload();
					selectorBuilder.matchIPProtocol(ipv6NextHeader).matchUdpSrc(TpPort.tpPort(udpPacket.getSourcePort())).matchUdpDst(TpPort.tpPort(udpPacket.getDestinationPort()));
				}
				if (matchIcmpFields && ipv6NextHeader == IPv6.PROTOCOL_ICMP6) {
					ICMP6 icmp6Packet = (ICMP6) ipv6Packet.getPayload();
					selectorBuilder.matchIPProtocol(ipv6NextHeader).matchIcmpv6Type(icmp6Packet.getIcmpType()).matchIcmpv6Code(icmp6Packet.getIcmpCode());
				}
			}
		}
		TrafficTreatment treatment = DefaultTrafficTreatment.builder().setOutput(portNumber).build();

		ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
				.withSelector(selectorBuilder.build())
				.withTreatment(treatment)
				.withPriority(flowPriority)
				.withFlag(ForwardingObjective.Flag.VERSATILE)
				.fromApp(appId)
				.makeTemporary(flowTimeout)
				.add();

		flowObjectiveService.forward(context.inPacket().receivedFrom().deviceId(), forwardingObjective);

		//
		// If packetOutOfppTable
		//  Send packet back to the OpenFlow pipeline to match installed flow
		// Else
		//  Send packet direction on the appropriate port
		//
		if (packetOutOfppTable) {
			packetOut(context, PortNumber.TABLE);
		} else {
			packetOut(context, portNumber);
		}
	}

	/**
	 * Internal Topology Listener Class
	 */
	private class InternalTopologyListener implements TopologyListener {
		@Override
		public void event(TopologyEvent event) {
			@SuppressWarnings("rawtypes")
			List<Event> reasons = event.reasons();
			if (reasons != null) {
				reasons.forEach(re -> {
					if (re instanceof LinkEvent) {
						LinkEvent le = (LinkEvent) re;
						if (le.type() == LinkEvent.Type.LINK_REMOVED) {
							fixBlackhole(le.subject().src());
						}
					}
				});
			}
		}
	}

	/**
	 * 
	 * @param egress - the egress connection point
	 */
	private void fixBlackhole(ConnectPoint egress) {
		Set<FlowEntry> rules = getFlowRulesFrom(egress);
		Set<SrcDstPair> pairs = findSrcDstPairs(rules);
		Map<DeviceId, Set<Path>> srcPaths = new HashMap<>();
		for (SrcDstPair sd : pairs) {
			// get the edge deviceID for the src host
			Host srcHost = hostService.getHost(HostId.hostId(sd.src));
			Host dstHost = hostService.getHost(HostId.hostId(sd.dst));
			if (srcHost != null && dstHost != null) {
				DeviceId srcId = srcHost.location().deviceId();
				DeviceId dstId = dstHost.location().deviceId();
				log.trace("SRC ID is " + srcId + ", DST ID is " + dstId);
				cleanFlowRules(sd, egress.deviceId());
				Set<Path> shortestPaths = srcPaths.get(srcId);
				if (shortestPaths == null) {
					shortestPaths = PathFinder.result(topologyService, egress.deviceId(), srcId, pathSearchDepth);
					srcPaths.put(srcId, shortestPaths);
				}
				backTrackBadNodes(shortestPaths, dstId, sd);
			}
		}
	}

	/**
	 * Backtracks from link down event to remove flows that lead to blackhole.
	 * 
	 * @param shortestPaths - the shortest paths array
	 * @param dstId - destination device id
	 * @param sd - source and destination pair
	 */
	private void backTrackBadNodes(Set<Path> shortestPaths, DeviceId dstId, SrcDstPair sd) {
		for (Path p : shortestPaths) {
			List<Link> pathLinks = p.links();
			for (int i = 0; i < pathLinks.size(); i = i + 1) {
				Link curLink = pathLinks.get(i);
				DeviceId curDevice = curLink.src().deviceId();
				// skipping the first link because this link's src has already been pruned beforehand
				if (i != 0) {
					cleanFlowRules(sd, curDevice);
				}
				Set<Path> pathsFromCurDevice = PathFinder.result(topologyService, curDevice, dstId, pathSearchDepth);
				if (pickForwardPathIfPossible(pathsFromCurDevice, curLink.src().port()) != null) {
					break;
				} else {
					if (i + 1 == pathLinks.size()) {
						cleanFlowRules(sd, curLink.dst().deviceId());
					}
				}
			}
		}
	}

	/**
	 * Removes flow rules off specified device with specific SrcDstPair.
	 * 
	 * @param pair - source and destination pair
	 * @param id - device id
	 */
	private void cleanFlowRules(SrcDstPair pair, DeviceId id) {
		log.trace("Searching for flow rules to remove from: " + id);
		log.trace("Removing flows w/ SRC=" + pair.src + ", DST=" + pair.dst);
		for (FlowEntry r : flowRuleService.getFlowEntries(id)) {
			boolean matchesSrc = false, matchesDst = false;
			for (Instruction i : r.treatment().allInstructions()) {
				if (i.type() == Instruction.Type.OUTPUT) {
					// if the flow has matching src and dst
					for (Criterion cr : r.selector().criteria()) {
						if (cr.type() == Criterion.Type.ETH_DST) {
							if (((EthCriterion) cr).mac().equals(pair.dst)) {
								matchesDst = true;
							}
						} else if (cr.type() == Criterion.Type.ETH_SRC) {
							if (((EthCriterion) cr).mac().equals(pair.src)) {
								matchesSrc = true;
							}
						}
					}
				}
			}
			if (matchesDst && matchesSrc) {
				log.trace("Removed flow rule from device: " + id);
				flowRuleService.removeFlowRules((FlowRule) r);
			}
		}
	}

	/**
	 * Returns a set of src/dst MAC pairs extracted from the specified set of flow entries.
	 * 
	 * @param rules - FlowEntry array
	 * 
	 * @return Set < SrcDstPair >
	 */
	private Set<SrcDstPair> findSrcDstPairs(Set<FlowEntry> rules) {
		ImmutableSet.Builder<SrcDstPair> builder = ImmutableSet.builder();
		for (FlowEntry r : rules) {
			MacAddress src = null, dst = null;
			for (Criterion cr : r.selector().criteria()) {
				if (cr.type() == Criterion.Type.ETH_DST) {
					dst = ((EthCriterion) cr).mac();
				} else if (cr.type() == Criterion.Type.ETH_SRC) {
					src = ((EthCriterion) cr).mac();
				}
			}
			builder.add(new SrcDstPair(src, dst));
		}
		return builder.build();
	}

	/**
	 * Returns set of flow entries which were created by this application and
	 * which egress from the specified connection port.
	 * 
	 * @param egress - the egress connection point
	 * 
	 * @return Set < FlowEntry >
	 */
	private Set<FlowEntry> getFlowRulesFrom(ConnectPoint egress) {
		ImmutableSet.Builder<FlowEntry> builder = ImmutableSet.builder();
		flowRuleService.getFlowEntries(egress.deviceId()).forEach(r -> {
			if (r.appId() == appId.id()) {
				r.treatment().allInstructions().forEach(i -> {
					if (i.type() == Instruction.Type.OUTPUT) {
						if (((Instructions.OutputInstruction) i).port().equals(egress.port())) {
							builder.add(r);
						}
					}
				});
			}
		});
		return builder.build();
	}

	/**
	 * Returns set of flow entries which were created by this application and
	 * which not egress from the specified connection port.
	 * 
	 * @param egress - the egress connection point
	 * 
	 * @return Set < FlowEntry >
	 */
	@SuppressWarnings("unused")
	private Set<FlowEntry> getFlowRulesNotFrom(ConnectPoint egress) {
		ImmutableSet.Builder<FlowEntry> builder = ImmutableSet.builder();
		flowRuleService.getFlowEntries(egress.deviceId()).forEach(r -> {
			if (r.appId() == appId.id()) {
				r.treatment().allInstructions().forEach(i -> {
					if (i.type() == Instruction.Type.OUTPUT) {
						if (!((Instructions.OutputInstruction) i).port().equals(egress.port())) {
							try {
								egress.hostId();
							} catch (Exception ex) {
								builder.add(r);
							}
						}
					}
				});
			}
		});
		return builder.build();
	}
	
	
	/**
	 * Wrapper class for a source and destination pair of MAC addresses 
	 */
	private final class SrcDstPair {
		final MacAddress src;
		final MacAddress dst;

		private SrcDstPair(MacAddress src, MacAddress dst) {
			this.src = src;
			this.dst = dst;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (o == null || getClass() != o.getClass()) {
				return false;
			}
			SrcDstPair that = (SrcDstPair) o;
			return Objects.equals(src, that.src) && Objects.equals(dst, that.dst);
		}

		@Override
		public int hashCode() {
			return Objects.hash(src, dst);
		}
	}

	/**
	 * Internal Link Quality Monitor Listener Class
	 */
	private class InternalLinkMonitorListener implements LinkMonitorListener {
		@Override
		public void event(LinkMonitorEvent event) {
			if (event.type() == LinkMonitorEvent.Type.SERVICE_STOPPED) {
				// TODO
				disconnectFromLinkMonitor();
				return;
			}
			if (event.type() == LinkMonitorEvent.Type.QUALITY_CHANGED) {
				update(event.subject());
			}
		}
	};

	// Updates from links monitor
	public void update(Object info) {
		// If link quality is less than threshold, try to fix 
		if (((LinkInfo) info).getQuality() <= ((LinkMonitorService) linkMonitorService).getMinQualityThreshold()) {
			fixBlackhole(((LinkInfo) info).getLink().src());
			return;
		}
		// Link quality is good again, try to switch to shortest path
		DeviceId devId = ((LinkInfo) info).getLink().src().deviceId();
		Set<Link> links = linkService.getDeviceEgressLinks(devId);
		for (Link link : links) {
			if (link.equals(((LinkInfo) info).getLink())) continue;
			fixBlackhole(link.src());
		}
	}

	/**
	 * Recreate path from source device 
	 */
	protected void remakePathFrom(HostId id) {
		Host src = hostService.getHost(id);
		if (src == null)
			return;
		DeviceId devId = src.location().deviceId();
		Set<Link> links = linkService.getDeviceEgressLinks(devId);
		for (Link link : links) {
			fixBlackhole(link.src());
		}
	}
}
