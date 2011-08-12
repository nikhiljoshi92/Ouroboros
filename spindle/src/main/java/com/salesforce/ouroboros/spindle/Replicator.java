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

import java.nio.channels.SocketChannel;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import com.hellblazer.pinkie.CommunicationsHandler;
import com.hellblazer.pinkie.ServerSocketChannelHandler;
import com.hellblazer.pinkie.SocketChannelHandler;
import com.lmax.disruptor.BatchConsumer;
import com.lmax.disruptor.BatchHandler;
import com.lmax.disruptor.ConsumerBarrier;

/**
 * A replicator of event streams
 * 
 * @author hhildebrand
 * 
 */
public class Replicator implements BatchHandler<EventEntry> {
    private final Executor                   executor;
    private final ServerSocketChannelHandler commsHandler;

    public Replicator(ServerSocketChannelHandler commsHandler,
                      ConsumerBarrier<EventEntry> consumerBarrier,
                      Executor executor) {
        BatchConsumer<EventEntry> batchConsumer = new BatchConsumer<EventEntry>(
                                                                                consumerBarrier,
                                                                                this);
        this.commsHandler = commsHandler;
        this.executor = executor;
        this.executor.execute(batchConsumer);
    }

    @Override
    public void onAvailable(EventEntry entry) throws Exception {
    }

    @Override
    public void onEndOfBatch() throws Exception {
    }

    private class CommsHandler implements CommunicationsHandler {

        private final AtomicReference<SocketChannelHandler> handler = new AtomicReference<SocketChannelHandler>();

        @Override
        public void closing(SocketChannel channel) {
            // TODO Auto-generated method stub

        }

        @Override
        public void handleAccept(SocketChannel channel,
                                 SocketChannelHandler handler) {
            // TODO Auto-generated method stub

        }

        @Override
        public void handleConnect(SocketChannel channel,
                                  SocketChannelHandler handler) {
            // TODO Auto-generated method stub

        }

        @Override
        public void handleRead(SocketChannel channel) {
            // TODO Auto-generated method stub

        }

        @Override
        public void handleWrite(SocketChannel channel) {
            // TODO Auto-generated method stub

        }

    }
}