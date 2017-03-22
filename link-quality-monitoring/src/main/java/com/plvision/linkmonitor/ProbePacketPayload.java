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

import java.nio.ByteBuffer;

import org.onlab.packet.BasePacket;
import org.onlab.packet.IPacket;

/**
 * This class used for working with monitoring packets payload.
 */
public class ProbePacketPayload extends BasePacket{
    private int seqNum = 0; 
    private int cpHash = 0; 

    public ProbePacketPayload() {
    }

    public ProbePacketPayload(int seq, int hash) {
        this.seqNum = seq;
        this.cpHash = hash;
    }
    
    @Override
    public byte[] serialize() {
        final byte[] data = new byte[8];
        final ByteBuffer bb = ByteBuffer.wrap(data);
        bb.putInt(seqNum);
        bb.putInt(cpHash);
        return data;
    }

    @Override
    public IPacket deserialize(byte[] data, int offset, int length) {
        if (length <= 0) {
            return null;
        }
        final ByteBuffer bb = ByteBuffer.wrap(data, offset, length);
        seqNum = bb.getInt();
        cpHash = bb.getInt();
        return this;
    }
    
    public int getSequenceNumber() {
        return this.seqNum; 
    }
    
    public void setSequenceNumber(int value) {
        this.seqNum = value; 
    }

    public int getCpHash() {
        return this.cpHash; 
    }
}
