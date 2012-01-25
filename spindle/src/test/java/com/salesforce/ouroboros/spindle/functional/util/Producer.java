/**
 * Copyright (c) 2011, salesforce.com, inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided
 * that the following conditions are met:
 *
 *    Redistributions of source code must retain the above copyright notice, this list of conditions and the
 *    following disclaimer.
 *
 *    Redistributions in binary form must reproduce the above copyright notice, this list of conditions and
 *    the following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 *    Neither the name of salesforce.com, inc. nor the names of its contributors may be used to endorse or
 *    promote products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.salesforce.ouroboros.spindle.functional.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import com.hellblazer.pinkie.CommunicationsHandler;
import com.hellblazer.pinkie.SocketChannelHandler;
import com.salesforce.ouroboros.BatchHeader;
import com.salesforce.ouroboros.Event;
import com.salesforce.ouroboros.Node;
import com.salesforce.ouroboros.spindle.source.Spindle;

/**
 * A simulated producer
 * 
 * @author hhildebrand
 * 
 */
public class Producer implements CommunicationsHandler {
    private final int            numberOfBatches;
    private SocketChannelHandler handler;
    private final AtomicInteger  batches        = new AtomicInteger();
    private volatile ByteBuffer  batch;
    private final AtomicInteger  timestamp      = new AtomicInteger(0);
    private volatile BatchHeader currentHeader;
    private final CountDownLatch latch;
    private final List<UUID>     channelIds;
    private final AtomicInteger  currentChannel = new AtomicInteger();
    private final int            batchSize;
    private final Node           producerNode;
    private final Node           targetNode;

    public Producer(List<UUID> channelIds, CountDownLatch latch, int batches,
                    int batchSize, Node producerNode, Node targetNode) {
        this.channelIds = channelIds;
        this.latch = latch;
        this.numberOfBatches = batches;
        this.batchSize = batchSize;
        this.producerNode = producerNode;
        this.targetNode = targetNode;
    }

    public Producer(UUID channelId, CountDownLatch producerLatch, int batches,
                    int batchSize, Node producerNode, Node targetNode) {
        this(Arrays.asList(new UUID[] { channelId }), producerLatch, batches,
             batchSize, producerNode, targetNode);
    }

    @Override
    public void accept(SocketChannelHandler handler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void closing() {
    }

    @Override
    public void connect(SocketChannelHandler handler) {
        this.handler = handler;
        ByteBuffer buffer = ByteBuffer.allocate(Spindle.HANDSHAKE_SIZE);
        buffer.putInt(Spindle.MAGIC);
        producerNode.serialize(buffer);
        buffer.flip();
        try {
            while (buffer.hasRemaining()) {
                handler.getChannel().write(buffer);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        nextBatch();
    }

    @Override
    public void readReady() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void writeReady() {
        if (currentHeader.hasRemaining()) {
            try {
                if (currentHeader.write(handler.getChannel()) < 0) {
                    handler.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
            if (currentHeader.hasRemaining()) {
                handler.selectForWrite();
                return;
            }
        }
        try {
            handler.getChannel().write(batch);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        if (batch.hasRemaining()) {
            handler.selectForWrite();
        } else {
            nextBatch();
        }
    }

    private Event event(UUID channelId) {
        byte[] payload = String.format("%s Give me Slack, or give me Food, or Kill me %s",
                                       channelId, channelId).getBytes();
        ByteBuffer payloadBuffer = ByteBuffer.wrap(payload);
        return new Event(666, payloadBuffer);
    }

    private void nextBatch() {
        int batchNumber = batches.get();
        if (batchNumber == numberOfBatches) {
            System.out.println(String.format("All Events published to: %s",
                                             targetNode));
            latch.countDown();
            return;
        }
        int channel = currentChannel.get();
        if (currentChannel.incrementAndGet() == channelIds.size()) {
            currentChannel.set(0);
            batches.incrementAndGet();
        }
        UUID channelId = channelIds.get(channel);
        Event event = event(channelId);
        int batchLength = batchSize * event.totalSize();
        batch = ByteBuffer.allocate(batchLength);
        for (int i = 0; i < batchSize; i++) {
            event.rewind();
            batch.put(event.getBytes());
        }
        currentHeader = new BatchHeader(producerNode, batchLength,
                                        BatchHeader.MAGIC, channelId,
                                        timestamp.incrementAndGet());
        batch.rewind();
        handler.selectForWrite();
    }

}