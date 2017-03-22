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

import java.util.Map;

import org.onosproject.net.DeviceId;
import org.onosproject.net.link.LinkEvent;
import org.onosproject.ui.UiTopoOverlay;
import org.onosproject.ui.topo.PropertyPanel;

public class LmUiTopovOverlay extends UiTopoOverlay {

    // NOTE: this must match the ID defined in soFwdTopoOv.js
    public static final String OVERLAY_ID = "link-monitor-overlay";
 
    public LmUiTopovOverlay(String id) {
        super(id);
    }

    @Override
    public void modifySummary(PropertyPanel pp) {
    }
 
    @Override
    public void modifyDeviceDetails(PropertyPanel pp, DeviceId deviceId) {
    }

    @Override
    public Map<String, String> additionalLinkData(LinkEvent event) {
        return null;
    }
}
