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

import org.onosproject.net.Link;
import org.onosproject.net.LinkKey;
import org.onosproject.ui.topo.BiLink;
import org.onosproject.ui.topo.LinkHighlight;
import org.onosproject.ui.topo.LinkHighlight.Flavor;
import org.onosproject.ui.topo.Mod;
 
/**
 * Our demo concrete class of a bi-link. We give it state so we can decide
 * how to create link highlights.
 */
public class UiLink extends BiLink {

    private static double MAX_QUALITY = 100;
    private final Mod mYellow = new Mod("pColorYellow");
    private final Mod mOrange = new Mod("pColorOrange");
    private final Mod mRed = new Mod("pColorRed");

    private double quality1 = MAX_QUALITY;
    private double quality2 = MAX_QUALITY;
    private boolean dir = false;

    public UiLink(LinkKey key, Link link) {
        super(key, link);
    }

    public void setQuality(double value) {
        if (value < 0) value = 100;
        if (dir == false) {
            dir = true;
            this.quality1 = value;
        } else 
            this.quality2 = value;
    }

    @Override
    public LinkHighlight highlight(Enum<?> anEnum) {
        Flavor flavor = Flavor.PRIMARY_HIGHLIGHT;
        String linkId = linkId();
        LinkHighlight lh;
        double qa = (quality1 <= quality2) ? quality1 : quality2;
        if (qa == 100) {
            return null;
        } else if (qa > 90) {
            lh = new LinkHighlight(linkId, flavor).addMod(mYellow);
        } else if (qa > 80) {
            lh = new LinkHighlight(linkId, flavor).addMod(mOrange);
        } else {
            lh = new LinkHighlight(linkId, flavor).addMod(mRed);
        }
        lh.setLabel(String.valueOf(100 - quality1) + "% : " + String.valueOf(100 - quality2) + "%");
        return lh;
    }
}
