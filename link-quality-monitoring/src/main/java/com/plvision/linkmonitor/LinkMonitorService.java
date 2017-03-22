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

import org.onosproject.event.ListenerService;
import org.onosproject.net.Link;

/**
 * Link quality monitoring service
 */
public interface LinkMonitorService extends ListenerService<LinkMonitorEvent, LinkMonitorListener> {

     /**
      * Get monitoring links counter.
      * @return number of monitoring links
      */
    int getLinkCount();

    /**
     * Get monitoring links info.
     * @return set of info of monitoring links
     */
    Iterable<LinkInfo> getLinksInfo();
    
    /**
    * Return quality for specified link
     * @param link (Link) 
     * @return link quality in percents (double) 
     */
    public double getLinkQuality(Link link);

    /**
     * Get Min quality threshold.
     * @return min quality threshold in percents
     */
    int getMinQualityThreshold();

    /**
     * Get Max quality threshold.
     * @return max quality threshold in percents
     */
    int getMaxQualityThreshold();
}
