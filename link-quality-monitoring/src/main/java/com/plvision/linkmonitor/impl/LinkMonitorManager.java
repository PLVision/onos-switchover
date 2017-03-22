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

package com.plvision.linkmonitor.impl;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.onosproject.net.flow.DefaultTrafficTreatment.builder;

import java.nio.ByteBuffer;
import java.util.Dictionary;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Modified;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.Service;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.TimerTask;
import org.onlab.packet.Ethernet;
import org.onlab.packet.IPacket;
import org.onlab.util.Timer;
import org.onlab.util.Tools;
import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.event.AbstractListenerManager;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Link;
import org.onosproject.net.PortNumber;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.link.LinkEvent;
import org.onosproject.net.link.LinkListener;
import org.onosproject.net.link.LinkService;
import org.onosproject.net.packet.DefaultOutboundPacket;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.OutboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.onosproject.ui.UiExtension;
import org.onosproject.ui.UiExtensionService;
import org.onosproject.ui.UiMessageHandlerFactory;
import org.onosproject.ui.UiTopoOverlayFactory;
import org.onosproject.ui.UiView;
import org.onosproject.ui.UiViewHidden;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.plvision.linkmonitor.LinkInfo;
import com.plvision.linkmonitor.LinkMonitorEvent;
import com.plvision.linkmonitor.LinkMonitorListener;
import com.plvision.linkmonitor.LinkMonitorService;
import com.plvision.linkmonitor.ProbePacketPayload;
import com.plvision.linkmonitor.uioverlay.LmUiTopovMessageHandler;
import com.plvision.linkmonitor.uioverlay.LmUiTopovOverlay;


@Component(immediate = true)
@Service
public class LinkMonitorManager extends AbstractListenerManager<LinkMonitorEvent, LinkMonitorListener>
                                implements LinkMonitorService, TimerTask {

    private static final String APP_NAME = "com.plvision.linkmonitor";
    private static final String VIEW_ID = "LmTopoOv";
    private static final ClassLoader CL = LinkMonitorManager.class.getClassLoader();

    private static final short TYPE_ETH_MON = (short) 0xaabb;
    private static final int DEFAULT_MIN_QUALITY_LEVEL = 90;
    private static final int DEFAULT_MAX_QUALITY_LEVEL = 99;
    private static final int DEFAULT_PROBE_PACKETS = 50;
    private static final int DEFAULT_PROBE_RATE = 10;
    private static final int DEFAULT_WAIT_RESPONSE = 100;

    private static final String SRC_MAC_ADDRESS = "a5:23:05:aa:bb:01";
    private static final String DST_MAC_ADDRESS = "a5:23:05:aa:bb:02";

    private final int PROBE_PAUSE = 100;

    private final boolean opMode = false;

    // Timer task states
    private final int SEND_PROBES = 0;
    private final int WAIT_RESPONCES = 1;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected LinkService linkService;
    
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ComponentConfigService cfgService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected UiExtensionService uiExtensionService;

    // App properties
    @Property(name = "MinQualityThreshold", intValue = DEFAULT_MIN_QUALITY_LEVEL, label = "Min channel quality level in percent to indicate bad link; default is 90%")
    private int qualityThresholdMin = DEFAULT_MIN_QUALITY_LEVEL;

    @Property(name = "MaxQualityThreshold", intValue = DEFAULT_MAX_QUALITY_LEVEL, label = "Max channel quality level in percent to indicate good link; default is 99%")
    private int qualityThresholdMax = DEFAULT_MAX_QUALITY_LEVEL;

    @Property(name = "ProbePacketsNumber", intValue = DEFAULT_PROBE_PACKETS, label = "Number of probe packets for monitoring; range [10 .. 100], default is 50")
    private int probePacketNum = DEFAULT_PROBE_PACKETS;

    @Property(name = "ProbeRate", intValue = DEFAULT_PROBE_RATE, label = "Time between probes in millis; range [1 .. 200], default is 10")
    private int probeRate = DEFAULT_PROBE_RATE;

    @Property(name = "WaitResponseTime", intValue = DEFAULT_WAIT_RESPONSE, label = "Wait response time for probe packets in millis; range [10 .. 1000], default is 100")
    private int responseTime = DEFAULT_WAIT_RESPONSE;
    // End of App properties

    private ApplicationId appId;
    private final Logger log = LoggerFactory.getLogger(getClass());
    private final LinkListener linkListener = new InternalLinkListener();
    private LinkMonitorPacketProcessor processor = new LinkMonitorPacketProcessor();

    private Ethernet ethPacket;
    private volatile boolean isStopped = true;
    private volatile int timerTaskState = SEND_PROBES;
    private volatile int probesCounter = 0;
    private volatile int sequence = 0;
    private volatile Timeout timeout;

    private Set<LinkInfo> linkList = new HashSet<LinkInfo>();
    private final Set<Long> probes = Sets.newConcurrentHashSet();
    private final Set<DeviceId> devices = Sets.newConcurrentHashSet();
   
    // List of application views
    private final List<UiView> uiViews = ImmutableList.of(new UiViewHidden(VIEW_ID));
    private LmUiTopovMessageHandler uiMessageHandler =  new LmUiTopovMessageHandler();
    // Factory for UI message handlers
    private final UiMessageHandlerFactory messageHandlerFactory = () -> ImmutableList.of(uiMessageHandler);
    private LmUiTopovOverlay uiTopoOverlay = new LmUiTopovOverlay(LmUiTopovOverlay.OVERLAY_ID);
    // Factory for UI topology overlays
    private final UiTopoOverlayFactory topoOverlayFactory = () -> ImmutableList.of(uiTopoOverlay);
    // Application UI extension
    protected UiExtension extension =
            new UiExtension.Builder(CL, uiViews)
                    .resourcePath(VIEW_ID)
                    .messageHandlerFactory(messageHandlerFactory)
                    .topoOverlayFactory(topoOverlayFactory)
                    .build();
    
    @Activate
    public void activate(ComponentContext context) {
        appId = coreService.registerApplication(APP_NAME);
        // Register properties
        cfgService.registerProperties(getClass());
        // Read configuration
        readComponentConfiguration(context);
        // Add packet processor
        packetService.addProcessor(processor, PacketProcessor.advisor(1));
        // Add event to registry
        eventDispatcher.addSink(LinkMonitorEvent.class, listenerRegistry);
        // Create request for packets interception
        requestIntercepts();
        // Initial adding of links to LinksMonitor
        Iterable<Link> linkList = linkService.getLinks();
        for (Link ln : linkList) {
            addLink(ln);
        }
        // Add LinkEvent listener
        linkService.addListener(linkListener);
        // Monitoring task init
        monitorInit();
        // Create base Ethernet packet
        createBasePacket();
        // Register UI extension
        uiExtensionService.register(extension);
        // Start monitoring
        start();
        log.info("Started");
    }
 
    @Deactivate
    public void deactivate() {
        LinkMonitorEvent ev = new LinkMonitorEvent(LinkMonitorEvent.Type.SERVICE_STOPPED);
        if (ev != null) {
            post(ev);
        }
        // Stop monitoring
        stop();
        // Unregister properties
        cfgService.unregisterProperties(getClass(), false);
        // Cancel packets interception
        withdrawIntercepts();
        // Remove LinkEvent listener
        linkService.removeListener(linkListener);
        // Remove event from registry
        eventDispatcher.removeSink(LinkMonitorEvent.class);
        // Remove packet processor
        packetService.removeProcessor(processor);

        // Unregister UI extension
        uiExtensionService.unregister(extension);

        log.info("Stopped");
    }

    /**
     * Modify application method.
     * @param context - the component context
     */
    @Modified
    public void modified(ComponentContext context) {
        readComponentConfiguration(context);
    }

    /**
     * Request packet in via packet service.
     */
    @SuppressWarnings("deprecation")
    private void requestIntercepts() {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(TYPE_ETH_MON);
        packetService.requestPackets(selector.build(), PacketPriority.CONTROL, appId);
    }

    /**
     * Cancel request for packet in via packet service.
     */
    @SuppressWarnings("deprecation")
    private void withdrawIntercepts() {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(TYPE_ETH_MON);
        packetService.cancelPackets(selector.build(), PacketPriority.CONTROL, appId);
    }

    /**
     * Extracts properties from the component configuration context.
     *
     * @param context - the component context
     */
    private void readComponentConfiguration(ComponentContext context) {
        Dictionary<?, ?> properties = context.getProperties();
        
        qualityThresholdMin = Tools.getIntegerProperty(properties, "MinQualityThreshold", DEFAULT_MIN_QUALITY_LEVEL);
        qualityThresholdMin = checkIntRange(qualityThresholdMin, 1, 99);
        log.info("Configured. Min Quality Threshold is configured to {}", qualityThresholdMin);

        qualityThresholdMax = Tools.getIntegerProperty(properties, "MaxQualityThreshold", DEFAULT_MAX_QUALITY_LEVEL);
        qualityThresholdMax = checkIntRange(qualityThresholdMax, 2, 100);
        log.info("Configured. Max Quality Threshold is configured to {}", qualityThresholdMax);

        probePacketNum = Tools.getIntegerProperty(properties, "ProbePacketsNumber", DEFAULT_PROBE_PACKETS);
        probePacketNum = checkIntRange(probePacketNum, 10, 100);
        log.info("Configured. Number of probe packets is configured to {}", probePacketNum);
        
        probeRate = Tools.getIntegerProperty(properties, "ProbeRate", DEFAULT_PROBE_RATE);
        probeRate = checkIntRange(probeRate, 1, 200);
        log.info("Configured. Probe rate is configured to {}", probeRate);
        
        responseTime = Tools.getIntegerProperty(properties, "WaitResponseTime", DEFAULT_WAIT_RESPONSE);
        responseTime = checkIntRange(responseTime, 10, 1000);
        log.info("Configured. Wait response time is configured to {}", responseTime);
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
     * Internal link monitor packet processor
     */
    private class LinkMonitorPacketProcessor implements PacketProcessor {
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
            if (ethPkt.getEtherType() == TYPE_ETH_MON) {
                packetParser(ethPkt, pkt.receivedFrom().hashCode());
                context.block();
            }
        }
    }
 
    /**
     * Internal link listener
     */
    private class InternalLinkListener implements LinkListener {
        @Override
        public void event(LinkEvent event) {
            if (event.type() == LinkEvent.Type.LINK_ADDED) {
                addLink(event.subject());
            } else  if (event.type() == LinkEvent.Type.LINK_REMOVED) {
                removeLink(event.subject());
            } else  if (event.type() == LinkEvent.Type.LINK_UPDATED) {
                updateLink(event.subject());
            }
        }
    }

    private void monitorInit() {
        isStopped = true;
        probesCounter = 0;
        sequence = 0;
        probes.clear();
        devices.clear();
    }
    /**
     * Create base Ethernet packet
     */
    private void createBasePacket() {
        ethPacket = new Ethernet();
        ethPacket.setEtherType(TYPE_ETH_MON);
        ethPacket.setDestinationMACAddress(DST_MAC_ADDRESS);
        ethPacket.setSourceMACAddress(SRC_MAC_ADDRESS);
        ethPacket.setPad(true);
    }

    /**
     * Create outbonud packet with payload
     * @param info (LinkInfo)
     */
    private OutboundPacket CreateOutboundPacket(LinkInfo info) {
        DeviceId devId = info.getLink().src().deviceId();
        devices.add(devId);
        PortNumber port = info.getLink().src().port();
        ethPacket.setPayload(new ProbePacketPayload(sequence, info.getLink().src().hashCode()));
        OutboundPacket pkt;
        if (opMode) {
            pkt = new DefaultOutboundPacket(devId, builder().setOutput(PortNumber.FLOOD).build(), ByteBuffer.wrap(ethPacket.serialize()));
        } else {
            pkt = new DefaultOutboundPacket(devId, builder().setOutput(port).build(), ByteBuffer.wrap(ethPacket.serialize()));
        }
        return pkt;
    }

    /**
     * Send probe packet
     * @param info (LinkInfo)
     */
    public boolean sendProbe(LinkInfo info) {
        OutboundPacket pkt = CreateOutboundPacket(info);
        packetService.emit(pkt);
        return true;
    }

    /**
     * Parse packet from packet processor
     * @param pkt (Ethernet)
     */
    public void packetParser(Ethernet pkt, int egressHash) {
        IPacket payload = pkt.getPayload();
        byte[] pp = payload.serialize();
        ProbePacketPayload mp = new ProbePacketPayload();
        mp.deserialize(pp, 0, pp.length);
        if (probes.contains((long) mp.getSequenceNumber())) {
            for (LinkInfo info : linkList) {
                if (opMode) {
                    if (egressHash == info.getLink().dst().hashCode()) {
                        info.updateResponse();
                        return;
                    }
                } else {
                    if (mp.getCpHash() == info.getLink().src().hashCode()) {
                        info.updateResponse();
                        return;
                    }
                }
            }
        }
    }

    /**
     * Add new link to set for monitoring
     * @param link (Link)
     */
    public void addLink(Link link) {
        LinkInfo info = new LinkInfo(link);
        // Check if link already exist in list
        for (LinkInfo ln : linkList) {
            if (ln.getLink().src().hashCode() == info.getLink().src().hashCode()) {
                //log.info("already exist");
                return;
            }
        }
        linkList.add(info);
        //log.info("AddLink: From: {}/{} To: {}/{}", link.src().deviceId(), link.src().port(), link.dst().deviceId(), link.dst().port());
    }

    /**
     * Remove link from set for monitoring
     * @param link (Link)
     */
    public void removeLink(Link link) {
        for (LinkInfo ln : linkList) {
            if (ln.getLink().src().hashCode() == link.src().hashCode()) {
                linkList.remove(ln);
                //log.info("Remove: From: {}/{} To: {}/{}", link.src().deviceId(), link.src().port(), link.dst().deviceId(), link.dst().port());
                return;
            }
        }
    }

    /**
     * Update link
     * @param link (Link)
     */
    public void updateLink(Link link) {
        log.info("UpdateLink: From: {}/{} To: {}/{}", link.src().deviceId(), link.src().port(), link.dst().deviceId(), link.dst().port());
    }

    private void notifyListenets(LinkInfo info) {
        LinkMonitorEvent event = new LinkMonitorEvent(LinkMonitorEvent.Type.QUALITY_CHANGED, info);
        if (event != null) {
            //log.info("post event: {} {}", event.type(), event);
            post(event);
        }
        
    }

    protected synchronized void stop() {
        if (!isStopped) {
            isStopped = true;
            timeout.cancel();
        } else {
            log.warn("LinkMonitoring stopped multiple times?");
        }
    }

    protected synchronized void start() {
        if (isStopped) {
            isStopped = false;
            timerTaskState = SEND_PROBES; 
            probesCounter = 0;
            sequence = 0;
            probes.clear();
            devices.clear();
            timeout = Timer.getTimer().newTimeout(this, 0, MILLISECONDS);
        } else {
            log.warn("LinkMonitoring started multiple times?");
        }
    }

    protected boolean isStarted() {
        return !(isStopped || timeout.isCancelled());
    }

    protected synchronized boolean isStopped() {
        return isStopped || timeout.isCancelled();
    }

    @Override
    public void run(Timeout arg0) throws Exception {
        if (isStopped()) {
            return;
        } 
        
        if (packetService == null) {
            if (!isStopped()) {
                timeout = Timer.getTimer().newTimeout(this, PROBE_PAUSE, MILLISECONDS);
                return;
            }
        }

        switch (timerTaskState) {
            case SEND_PROBES:
                if (linkList.isEmpty()) {
                    if (!isStopped()) {
                        timeout = Timer.getTimer().newTimeout(this, PROBE_PAUSE, MILLISECONDS);
                    }
                    return;
                }
                if (probesCounter < probePacketNum) {
                    probes.add((long) sequence);
                    try {
                        if (opMode) {
                            for (LinkInfo info :linkList) {
                                if (devices.contains(info.getLink().src().deviceId())) continue;
                                sendProbe(info);
                            }
                            devices.clear();
                        } else {
                            linkList.forEach(info -> {
                                sendProbe(info);
                            });
                        }
                    } catch (Exception e) {
                        log.info("Exception on sendprobe %%");
                        if (!isStopped()) {
                            timeout = Timer.getTimer().newTimeout(this, PROBE_PAUSE, MILLISECONDS);
                        }
                        return;
                    }
                    probesCounter++;
                    sequence++;
                    if (!isStopped()) {
                        timeout = Timer.getTimer().newTimeout(this, probeRate, MILLISECONDS);
                    }
                } else {
                    timerTaskState = WAIT_RESPONCES;
                    if (!isStopped()) {
                        timeout = Timer.getTimer().newTimeout(this, responseTime, MILLISECONDS);
                    }
                }
            break;
            case WAIT_RESPONCES:
                if (!linkList.isEmpty()) {
                    try {
                        linkList.forEach(info -> {
                            info.calculateQuality(probesCounter, qualityThresholdMin, qualityThresholdMax);
                            info.reset();
                            log.info("Channel {}/{} quality: {}%", info.getLink().src().deviceId(), info.getLink().src().port(), info.getQuality());
                            if (info.isChanged()) {
                                info.setChanged(false);
                                notifyListenets(info);
                            }
                        });
                        uiMessageHandler.updateLinkData(linkList);
                    } catch (Exception e) {
                        log.info("Exception on wait response");
                    }
                }
                timerTaskState = SEND_PROBES; 
                probesCounter = 0;
                probes.clear();
                devices.clear();
                if (!isStopped()) {
                    timeout = Timer.getTimer().newTimeout(this, probeRate, MILLISECONDS);
                }
            break;
        }
    }

    @Override
    public int getLinkCount() {
        return linkList.size();
    }

    @Override
    public Iterable<LinkInfo> getLinksInfo() {
        return linkList;
    }

    @Override
    public double getLinkQuality(Link link) {
        if (isStopped) {
            return 100.0;
        }
        for (LinkInfo ln : linkList) {
            if (ln.getLink().src().hashCode() == link.src().hashCode()) {
                return ln.getQuality();
            }
        }
        return 100.0;
    }

    @Override
    public int getMinQualityThreshold() {
        return this.qualityThresholdMin;
    }

    @Override
    public int getMaxQualityThreshold() {
        return this.qualityThresholdMax;
    }
}
