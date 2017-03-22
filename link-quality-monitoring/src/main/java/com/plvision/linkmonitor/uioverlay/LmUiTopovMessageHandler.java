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

package com.plvision.linkmonitor.uioverlay;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import org.onlab.osgi.ServiceDirectory;
import org.onlab.util.DefaultHashMap;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Element;
import org.onosproject.net.HostId;
import org.onosproject.net.Link;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.host.HostService;
import org.onosproject.net.link.LinkEvent;
import org.onosproject.net.link.LinkService;
import org.onosproject.ui.JsonUtils;
import org.onosproject.ui.RequestHandler;
import org.onosproject.ui.UiConnection;
import org.onosproject.ui.UiMessageHandler;
import org.onosproject.ui.topo.Highlights;
import org.onosproject.ui.topo.LinkHighlight;
import org.onosproject.ui.topo.LinkHighlight.Flavor;
import org.onosproject.ui.topo.Mod;
import org.onosproject.ui.topo.TopoJson;
import org.onosproject.ui.topo.TopoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.plvision.linkmonitor.LinkInfo;


public class LmUiTopovMessageHandler extends UiMessageHandler {

    private static final String LM_TOPO_DISPLAY_START = "LmTopoDisplayStart";
    private static final String LM_TOPO_DISPLAY_UPDATE = "LmTopoDisplayUpdate";
    private static final String LM_TOPO_DISPLAY_STOP = "LmTopoDisplayStop";

    private final Mod mYellow = new Mod("pColorYellow");
    private final Mod mOrange = new Mod("pColorOrange");
    private final Mod mRed = new Mod("pColorRed");

    private static final String ID = "id";
    private static final String MODE = "mode";
    private static final long UPDATE_PERIOD_MS = 1000;
    private static final Link[] EMPTY_LINK_SET = new Link[0];
    private enum Mode { IDLE, MOUSE, LINK }
    private final Logger log = LoggerFactory.getLogger(getClass());
    private DeviceService deviceService;
    private HostService hostService;
    private LinkService linkService;
    private final Timer timer = new Timer(LmUiTopovOverlay.OVERLAY_ID);
    private TimerTask demoTask = null;
    private Mode currentMode = Mode.IDLE;
    private Element elementOfNote;
    private Link[] linkSet = EMPTY_LINK_SET;
    private Set<LinkInfo> linkinfoList = null;

    // ===============-=-=-=-=-=-======================-=-=-=-=-=-=-================================

    @Override
    public void init(UiConnection connection, ServiceDirectory directory) {
        super.init(connection, directory);
        deviceService = directory.get(DeviceService.class);
        hostService = directory.get(HostService.class);
        linkService = directory.get(LinkService.class);
    }

    @Override
    protected Collection<RequestHandler> createRequestHandlers() {
        return ImmutableSet.of(
            new DisplayStartHandler(),
            new DisplayUpdateHandler(),
            new DisplayStopHandler()
        );
    }

    // === -------------------------
    // === Handler classes

    private final class DisplayStartHandler extends RequestHandler {
        public DisplayStartHandler() {
            super(LM_TOPO_DISPLAY_START);
        }

        @Override
        public void process(long sid, ObjectNode payload) {
            String mode = string(payload, MODE);

            log.debug("Start Display: mode [{}]", mode);
            clearState();
            clearForMode();

            switch (mode) {
                case "device":
                    currentMode = Mode.MOUSE;
                    cancelTask();
                    sendMouseData();
                    break;

                case "link":
                    currentMode = Mode.LINK;
                    scheduleTask();
                    initLinkSet();
                    sendLinkData();
                    break;

                default:
                    currentMode = Mode.IDLE;
                    cancelTask();
                    break;
            }
        }
    }

    private final class DisplayUpdateHandler extends RequestHandler {
        public DisplayUpdateHandler() {
            super(LM_TOPO_DISPLAY_UPDATE);
        }

        @Override
        public void process(long sid, ObjectNode payload) {
            String id = string(payload, ID);
            log.debug("Update Display: id [{}]", id);
            if (!Strings.isNullOrEmpty(id)) {
                updateForMode(id);
            } else {
                if (currentMode != Mode.LINK) 
                    clearForMode();
            }
        }
    }

    private final class DisplayStopHandler extends RequestHandler {
        public DisplayStopHandler() {
            super(LM_TOPO_DISPLAY_STOP);
        }

        @Override
        public void process(long sid, ObjectNode payload) {
            log.debug("Stop Display");
            cancelTask();
            clearState();
            clearForMode();
        }
    }

    // === ------------

    private void clearState() {
        currentMode = Mode.IDLE;
        elementOfNote = null;
        linkSet = EMPTY_LINK_SET;
    }

    private void updateForMode(String id) {
        switch (currentMode) {
            case MOUSE:
                try {
                    HostId hid = HostId.hostId(id);
                    log.debug("host id {}", hid);
                    elementOfNote = hostService.getHost(hid);
                    log.debug("host element {}", elementOfNote);
                } catch (Exception e) {
                    try {
                            DeviceId did = DeviceId.deviceId(id);
                            log.debug("device id {}", did);
                            elementOfNote = deviceService.getDevice(did);
                            log.debug("device element {}", elementOfNote);
                    } catch (Exception e2) {
                        log.debug("Unable to process ID [{}]", id);
                        elementOfNote = null;
                    }
                }
                sendMouseData();
                break;

            case LINK:
//              sendLinkData();
                break;

            default:
                break;
        }
    }

    private void clearForMode() {
        sendHighlights(new Highlights());
    }

    private void sendHighlights(Highlights highlights) {
        sendMessage(TopoJson.highlightsMessage(highlights));
    }

    private void sendMouseData() {
        if (elementOfNote != null && elementOfNote instanceof Device) {
            DeviceId devId = (DeviceId) elementOfNote.id();
            Set<Link> links = linkService.getDeviceEgressLinks(devId);
            Highlights highlights = fromDevice(links, devId);
            sendHighlights(highlights);
        }
        // Note: could also process Host, if available
    }

    private Highlights fromDevice(Set<Link> links, DeviceId devId) {
        if (linkinfoList == null || linkinfoList.isEmpty()) {
            return null;
        }
        Highlights highlights = new Highlights();
        for (Link link : links) {
            for (LinkInfo info : linkinfoList) {
                if (info.getLink().hashCode() == link.hashCode()) {
                    LinkHighlight lh = null;
                    double qa = info.getQuality();
                    if (qa == 100) {
                        break;
                    } else if (qa > 90) {
                        lh = new LinkHighlight(TopoUtils.compactLinkString(info.getLink()), Flavor.PRIMARY_HIGHLIGHT).addMod(mYellow);
                    } else if (qa > 80) {
                        lh = new LinkHighlight(TopoUtils.compactLinkString(info.getLink()), Flavor.PRIMARY_HIGHLIGHT).addMod(mOrange);
                    } else {
                        lh = new LinkHighlight(TopoUtils.compactLinkString(info.getLink()), Flavor.PRIMARY_HIGHLIGHT).addMod(mRed);
                    }
                    lh.setLabel(String.valueOf(100 - qa) + "%");
                    highlights.add(lh);
                    break;
                }
            }
        }
        return highlights;
    }

    private void initLinkSet() {
        Set<Link> links = new HashSet<>();
        for (Link link : linkService.getActiveLinks()) {
            links.add(link);
        }
        linkSet = links.toArray(new Link[links.size()]);
        log.debug("initialized link set to {}", linkSet.length);
    }

    private void sendLinkData() {
        if (linkinfoList == null || linkinfoList.isEmpty()) {
            sendHighlights(new Highlights());
            return;
        }
        UiLinkMap linkMap = new UiLinkMap();
        for (LinkInfo linkinfo : linkinfoList) {
            UiLink dl = linkMap.add(linkinfo.getLink());
            dl.setQuality(linkinfo.getQuality());
        }
        Highlights highlights = new Highlights();
        for (UiLink link : linkMap.biLinks()) {
            LinkHighlight lh = link.highlight(null);
            if (lh != null) highlights.add(lh);
        }
        sendHighlights(highlights);
    }

    private synchronized void scheduleTask() {
        if (demoTask == null) {
            log.debug("Starting up demo task...");
            demoTask = new DisplayUpdateTask();
            timer.schedule(demoTask, UPDATE_PERIOD_MS, UPDATE_PERIOD_MS);
        } else {
            log.debug("(demo task already running");
        }
    }

    private synchronized void cancelTask() {
        if (demoTask != null) {
            demoTask.cancel();
            demoTask = null;
        }
    }

 
    private class DisplayUpdateTask extends TimerTask {
        @Override
        public void run() {
            try {
                switch (currentMode) {
                    case LINK:
//                        sendLinkData();
                        break;

                    default:
                        break;
                    }
            } catch (Exception e) {
                log.warn("Unable to process demo task: {}", e.getMessage());
                log.debug("Oops", e);
            }
        }
    }

    // Update links information
    public void updateLinkData(Set<LinkInfo> linkinfoList) {
        this.linkinfoList = linkinfoList;
        if (currentMode == Mode.LINK) sendLinkData();
    }

    private static final DefaultHashMap<LinkEvent.Type, String> LINK_EVENT = new DefaultHashMap<>("updateLink");
    static {
        LINK_EVENT.put(LinkEvent.Type.LINK_ADDED, "addLink");
        LINK_EVENT.put(LinkEvent.Type.LINK_REMOVED, "removeLink");
    }

    // Produces a link event message to the client.
    protected ObjectNode linkMessage(LinkEvent event) {
        Link link = event.subject();
        ObjectNode payload = objectNode()
                .put("id", TopoUtils.compactLinkString(link))
                .put("type", link.type().toString().toLowerCase())
                .put("expected", link.isExpected())
                .put("online", link.state() == Link.State.ACTIVE)
                .put("linkWidth", 1.2)
                .put("src", link.src().deviceId().toString())
                .put("srcPort", link.src().port().toString())
                .put("dst", link.dst().deviceId().toString())
                .put("dstPort", link.dst().port().toString());
        String type = LINK_EVENT.get(event.type());
        return JsonUtils.envelope(type, 0, payload);
    }
}
