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

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.UUID;

import org.junit.Test;

import com.hellblazer.pinkie.SocketChannelHandler;
import com.hellblazer.pinkie.SocketOptions;
import com.salesforce.ouroboros.Event;
import com.salesforce.ouroboros.EventHeader;
import com.salesforce.ouroboros.Node;
import com.salesforce.ouroboros.spindle.AbstractAppenderContext.AbstractAppenderFSM;
import com.salesforce.ouroboros.spindle.DuplicatorContext.DuplicatorFSM;

/**
 * 
 * @author hhildebrand
 * 
 */
public class TestDuplicator {

    private class Reader implements Runnable {
        private final SocketChannel         inbound;
        private final ReplicatedBatchHeader header = new ReplicatedBatchHeader();
        final ByteBuffer                    replicated;

        public Reader(final SocketChannel inbound, final int payloadLength) {
            super();
            this.inbound = inbound;
            replicated = ByteBuffer.allocate(EventHeader.HEADER_BYTE_SIZE
                                             + payloadLength);
        }

        @Override
        public void run() {
            try {
                readHeader();
                for (inbound.read(replicated); replicated.hasRemaining(); inbound.read(replicated)) {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            replicated.flip();
        }

        private void readHeader() {
            try {
                for (boolean read = header.read(inbound); !read; read = header.read(inbound)) {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @Test
    public void testOutboundReplication() throws Exception {
        File tmpFile = File.createTempFile("outbound-replication", ".tst");
        tmpFile.deleteOnExit();
        Segment segment = new Segment(tmpFile);
        Bundle bundle = mock(Bundle.class);
        Acknowledger acknowledger = mock(Acknowledger.class);

        int magic = 666;
        UUID channel = UUID.randomUUID();
        final byte[] payload = "Give me Slack, or give me Food, or Kill me".getBytes();
        ByteBuffer payloadBuffer = ByteBuffer.wrap(payload);
        Event event = new Event(magic, payloadBuffer);
        EventChannel eventChannel = mock(EventChannel.class);

        event.rewind();
        event.write(segment);
        segment.write(payloadBuffer);
        segment.force(false);
        SocketChannelHandler handler = mock(SocketChannelHandler.class);

        when(bundle.eventChannelFor(channel)).thenReturn(eventChannel);
        final Duplicator replicator = new Duplicator();
        assertEquals(DuplicatorFSM.Waiting, replicator.getState());
        SocketOptions options = new SocketOptions();
        options.setSend_buffer_size(4);
        options.setReceive_buffer_size(4);
        options.setTimeout(100);
        ServerSocketChannel server = ServerSocketChannel.open();
        server.configureBlocking(true);
        server.socket().bind(new InetSocketAddress("127.0.0.1", 0));
        final SocketChannel outbound = SocketChannel.open();
        options.configure(outbound.socket());
        outbound.configureBlocking(true);
        outbound.connect(server.socket().getLocalSocketAddress());
        final SocketChannel inbound = server.accept();
        options.configure(inbound.socket());
        inbound.configureBlocking(true);

        assertTrue(inbound.isConnected());
        outbound.configureBlocking(false);
        inbound.configureBlocking(false);
        Reader reader = new Reader(inbound, payload.length);
        Thread inboundRead = new Thread(reader, "Inbound read thread");
        inboundRead.start();
        when(handler.getChannel()).thenReturn(outbound);
        replicator.connect(handler);
        Util.waitFor("Never achieved WAITING state", new Util.Condition() {

            @Override
            public boolean value() {
                return DuplicatorFSM.Waiting == replicator.getState();
            }
        }, 1000L, 100L);
        Node mirror = new Node(0x1638);
        long timestamp = System.currentTimeMillis();
        replicator.replicate(new ReplicatedBatchHeader(mirror,
                                                       event.totalSize(),
                                                       magic, channel,
                                                       timestamp, 0),
                             eventChannel, segment, acknowledger);
        Util.waitFor("Never achieved WAITING state", new Util.Condition() {

            @Override
            public boolean value() {
                replicator.writeReady();
                return DuplicatorFSM.Waiting == replicator.getState();
            }
        }, 1000L, 100L);
        inboundRead.join(4000);
        assertTrue(reader.replicated.hasRemaining());
        assertEquals(0, reader.header.getOffset());
        Event replicatedEvent = new Event(reader.replicated);
        assertEquals(event.size(), replicatedEvent.size());
        assertEquals(event.getMagic(), replicatedEvent.getMagic());
        assertEquals(event.getCrc32(), replicatedEvent.getCrc32());
        assertTrue(replicatedEvent.validate());
        verify(eventChannel).commit(0);
        verify(acknowledger).acknowledge(channel, timestamp);
    }

    @Test
    public void testReplicationLoop() throws Exception {
        SocketChannelHandler handler = mock(SocketChannelHandler.class);

        File inboundTmpFile = File.createTempFile("inbound-replication", ".tst");
        inboundTmpFile.deleteOnExit();
        Segment inboundSegment = new Segment(inboundTmpFile);
        EventChannel inboundEventChannel = mock(EventChannel.class);
        Bundle inboundBundle = mock(Bundle.class);
        Acknowledger outboundAcknowledger = mock(Acknowledger.class);
        Acknowledger inboundAcknowledger = mock(Acknowledger.class);

        final ReplicatingAppender inboundReplicator = new ReplicatingAppender(
                                                                              inboundBundle);
        EventChannel eventChannel = mock(EventChannel.class);

        File tmpOutboundFile = File.createTempFile("outbound", ".tst");
        tmpOutboundFile.deleteOnExit();
        Segment outboundSegment = new Segment(tmpOutboundFile);

        int magic = 666;
        UUID channel = UUID.randomUUID();
        long timestamp = System.currentTimeMillis();
        final byte[] payload = "Give me Slack, or give me Food, or Kill me".getBytes();
        ByteBuffer payloadBuffer = ByteBuffer.wrap(payload);
        Event event = new Event(magic, payloadBuffer);
        Node mirror = new Node(0x1638);
        ReplicatedBatchHeader batchHeader = new ReplicatedBatchHeader(
                                                                      mirror,
                                                                      event.totalSize(),
                                                                      magic,
                                                                      channel,
                                                                      timestamp,
                                                                      0);

        event.rewind();
        event.write(outboundSegment);
        outboundSegment.force(false);
        long offset = 0L;

        when(inboundBundle.eventChannelFor(channel)).thenReturn(inboundEventChannel);
        when(inboundBundle.getAcknowledger(mirror)).thenReturn(inboundAcknowledger);
        when(inboundEventChannel.segmentFor(offset)).thenReturn(inboundSegment);

        final Duplicator outboundDuplicator = new Duplicator();
        assertEquals(DuplicatorFSM.Waiting, outboundDuplicator.getState());
        SocketOptions options = new SocketOptions();
        options.setSend_buffer_size(4);
        options.setReceive_buffer_size(4);
        options.setTimeout(100);
        ServerSocketChannel server = ServerSocketChannel.open();
        server.configureBlocking(true);
        server.socket().bind(new InetSocketAddress("127.0.0.1", 0));
        final SocketChannel outbound = SocketChannel.open();
        options.configure(outbound.socket());
        outbound.configureBlocking(true);
        outbound.connect(server.socket().getLocalSocketAddress());
        final SocketChannel inbound = server.accept();
        options.configure(inbound.socket());
        inbound.configureBlocking(true);
        assertTrue(inbound.isConnected());
        outbound.configureBlocking(false);
        inbound.configureBlocking(false);
        when(handler.getChannel()).thenReturn(inbound);
        inboundReplicator.accept(handler);
        inboundReplicator.readReady();
        assertEquals(AbstractAppenderFSM.ReadBatchHeader,
                     inboundReplicator.getState());

        Runnable reader = new Runnable() {
            @Override
            public void run() {
                while (AbstractAppenderFSM.Ready != inboundReplicator.getState()) {
                    inboundReplicator.readReady();
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }
        };
        Thread inboundRead = new Thread(reader, "Inbound read thread");
        inboundRead.start();

        SocketChannelHandler outboundHandler = mock(SocketChannelHandler.class);
        when(outboundHandler.getChannel()).thenReturn(outbound);
        outboundDuplicator.connect(outboundHandler);
        outboundDuplicator.replicate(new ReplicatedBatchHeader(batchHeader, 0),
                                     eventChannel, outboundSegment,
                                     outboundAcknowledger);
        Util.waitFor("Never achieved WAITING state", new Util.Condition() {
            @Override
            public boolean value() {
                outboundDuplicator.writeReady();
                return DuplicatorFSM.Waiting == outboundDuplicator.getState();
            }
        }, 1000L, 100L);
        inboundRead.join(4000);

        assertEquals(AbstractAppenderFSM.Ready, inboundReplicator.getState());

        inboundSegment.close();
        outboundSegment.close();

        Segment segment = new Segment(inboundTmpFile);
        assertTrue("Nothing written to inbound segment", segment.size() > 0);
        Event replicatedEvent = new Event(segment);
        assertEquals(event.size(), replicatedEvent.size());
        assertEquals(event.getMagic(), replicatedEvent.getMagic());
        assertEquals(event.getCrc32(), replicatedEvent.getCrc32());
        assertTrue(replicatedEvent.validate());
        verify(outboundAcknowledger).acknowledge(channel, timestamp);
        verify(inboundAcknowledger).acknowledge(channel, timestamp);
    }
}
