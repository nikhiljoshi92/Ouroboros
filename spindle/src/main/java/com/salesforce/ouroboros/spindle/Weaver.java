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
package com.salesforce.ouroboros.spindle;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;

import com.hellblazer.pinkie.CommunicationsHandler;
import com.hellblazer.pinkie.CommunicationsHandlerFactory;
import com.hellblazer.pinkie.ServerSocketChannelHandler;
import com.salesforce.ouroboros.util.ConsistentHashFunction;

/**
 * The Weaver represents the channel buffer process that provides persistent,
 * replicated buffers of events, their subscriptions and the services for
 * publishing and consuming these events.
 * 
 * @author hhildebrand
 * 
 */
public class Weaver implements Bundle {
    private class ReplicatorFactory implements CommunicationsHandlerFactory {
        @Override
        public CommunicationsHandler createCommunicationsHandler(SocketChannel channel) {
            return new Replicator(Weaver.this);
        }
    }

    private class SpindleFactory implements CommunicationsHandlerFactory {
        @Override
        public CommunicationsHandler createCommunicationsHandler(SocketChannel channel) {
            return new Spinner(Weaver.this);
        }
    }

    private final long                              id;
    private final long                              maxSegmentSize;
    private final ConcurrentMap<UUID, EventChannel> openChannels = new ConcurrentHashMap<UUID, EventChannel>();
    private final ConcurrentMap<Long, Replicator>   replicators  = new ConcurrentHashMap<Long, Replicator>();
    private final ServerSocketChannelHandler        replicationHandler;
    private final File                              root;
    private final ServerSocketChannelHandler        spindleHandler;
    private final ConsistentHashFunction<Long>      weaverRing   = new ConsistentHashFunction<Long>();

    public Weaver(WeaverConfigation configuration) throws IOException {
        id = configuration.getId();
        weaverRing.add(id, 1);
        root = configuration.getRoot();
        maxSegmentSize = configuration.getMaxSegmentSize();
        replicationHandler = new ServerSocketChannelHandler(
                                                            "Weaver Replicator",
                                                            configuration.getReplicationSocketOptions(),
                                                            configuration.getReplicationAddress(),
                                                            Executors.newFixedThreadPool(2),
                                                            new ReplicatorFactory());
        spindleHandler = new ServerSocketChannelHandler(
                                                        "Weaver Spindle",
                                                        configuration.getSpindleSocketOptions(),
                                                        configuration.getSpindleAddress(),
                                                        configuration.getSpindles(),
                                                        new SpindleFactory());
    }

    @Override
    public EventChannel eventChannelFor(EventHeader header) {
        UUID channelTag = header.getChannel();
        return openChannels.get(channelTag);
    }

    public InetSocketAddress getReplicatorEndpoint() {
        return replicationHandler.getLocalAddress();
    }

    public InetSocketAddress getSpindleEndpoint() {
        return spindleHandler.getLocalAddress();
    }

    public void close(UUID channel) {
        EventChannel eventChannel = openChannels.remove(channel);
        if (channel != null) {
            eventChannel.close();
        }
    }

    public void open(UUID channel) {
        openChannels.putIfAbsent(channel,
                                 new EventChannel(channel, root,
                                                  maxSegmentSize, null));
    }

    @Override
    public void registerReplicator(long id, Replicator replicator) {
        replicators.put(id, replicator);
    }

    public void start() {
        spindleHandler.start();
        replicationHandler.start();
    }

    public void terminate() {
        spindleHandler.terminate();
        replicationHandler.terminate();
    }

    public String toString() {
        return String.format("Weaver[%s], spindle endpoint: %s, replicator endpoint: %s",
                             id, getSpindleEndpoint(), getReplicatorEndpoint());
    }
}