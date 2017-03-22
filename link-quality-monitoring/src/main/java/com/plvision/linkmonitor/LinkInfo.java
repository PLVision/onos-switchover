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

package com.plvision.linkmonitor;

import org.onosproject.net.Link;

public class LinkInfo {

    private Link link = null;
    private double lastquality = 0.0;
    private double quality = 0.0;
    private int respCounter;
    private boolean good = true;
    private boolean changed;

    public LinkInfo(Link link) {
        this.link = link;
        setChanged(false);
        reset();
    }

    /**
     * @return the link
     */
    public Link getLink() {
        return link;
    }

    /**
     * @return the quality
     */
    public double getQuality() {
        return quality;
    }

    /**
     * Update Response counter
    */
    public void updateResponse() {
        respCounter++;
    }

    /**
     * Reset probe counter
     */
    public void reset() {
        respCounter = 0;
    }

    /**
     * Calculate probes result
     */
    public void calculateQuality(int cnt, int lMin, int lMax) {
        if (cnt == 0) return;
        quality = ((double) respCounter * 100) / cnt;

        if (lastquality < lMax && quality >= lMax) {
            good = true;
            changed = true;
        }
        if (lastquality > lMin && quality <= lMin) {
            good = false;
            changed = true;
        }
        lastquality = quality; 
        respCounter = 0;
    }

    /**
     * @return the changed
     */
    public boolean isChanged() {
        return changed;
    }

    /**
     * @param changed the changed to set
     */
    public void setChanged(boolean changed) {
        this.changed = changed;
    }

    /**
     * @return link good
     */
    public boolean isLinkGood() {
        return this.good;
    }
}
