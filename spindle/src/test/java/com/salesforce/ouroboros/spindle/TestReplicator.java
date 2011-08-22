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
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Matchers.eq;
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
import com.lmax.disruptor.AlertException;
import com.lmax.disruptor.ConsumerBarrier;
import com.salesforce.ouroboros.spindle.Replicator.State;

/**
 * 
 * @author hhildebrand
 * 
 */
public class TestReplicator {

    private class Reader implements Runnable {
        long                        offset       = -1;
        private final SocketChannel inbound;
        final ByteBuffer            replicated;
        private final ByteBuffer    offsetBuffer = ByteBuffer.allocate(8);

        public Reader(final SocketChannel inbound, final int payloadLength) {
            super();
            this.inbound = inbound;
            replicated = ByteBuffer.allocate(EventHeader.HEADER_BYTE_SIZE
                                             + payloadLength);
        }

        @Override
        public void run() {
            try {
                readOffset();
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

        private void readOffset() {
            try {
                for (inbound.read(offsetBuffer); offsetBuffer.hasRemaining(); inbound.read(offsetBuffer)) {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
                offsetBuffer.flip();
                offset = offsetBuffer.getLong();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @Test
    public void testInboundReplication() throws Exception {
        File tmpFile = File.createTempFile("inbound-replication", ".tst");
        tmpFile.deleteOnExit();
        Segment segment = new Segment(tmpFile);

        int magic = 666;
        UUID channel = UUID.randomUUID();
        long timestamp = System.currentTimeMillis();
        final byte[] payload = "Give me Slack, or give me Food, or Kill me".getBytes();
        ByteBuffer offsetBuffer = ByteBuffer.allocate(8);
        long offset = 0L;
        offsetBuffer.putLong(offset);
        offsetBuffer.flip();
        ByteBuffer payloadBuffer = ByteBuffer.wrap(payload);
        EventHeader event = new EventHeader(payload.length, magic, channel,
                                            timestamp, Event.crc32(payload));
        payloadBuffer.clear();
        EventChannel eventChannel = mock(EventChannel.class);
        Bundle bundle = mock(Bundle.class);
        when(bundle.eventChannelFor(eq(event))).thenReturn(eventChannel);
        when(eventChannel.segmentFor(offset)).thenReturn(segment);
        when(eventChannel.isNextAppend(offset)).thenReturn(true);
        @SuppressWarnings("unchecked")
        ConsumerBarrier<EventEntry> consumerBarrier = mock(ConsumerBarrier.class);
        when(consumerBarrier.waitFor(0)).thenThrow(AlertException.ALERT_EXCEPTION);
        SocketChannelHandler handler = mock(SocketChannelHandler.class);

        final Replicator replicator = new Replicator(bundle, consumerBarrier);
        SocketOptions options = new SocketOptions();
        options.setSend_buffer_size(4);
        options.setReceive_buffer_size(4);
        options.setTimeout(100);
        ServerSocketChannel server = ServerSocketChannel.open();
        server.configureBlocking(true);
        server.socket().bind(new InetSocketAddress(0));
        final SocketChannel outbound = SocketChannel.open();
        options.configure(outbound.socket());
        outbound.configureBlocking(true);
        outbound.connect(server.socket().getLocalSocketAddress());
        final SocketChannel inbound = server.accept();
        options.configure(inbound.socket());
        inbound.configureBlocking(true);

        assertTrue(inbound.isConnected());
        outbound.configureBlocking(true);
        inbound.configureBlocking(false);

        replicator.handleAccept(inbound, handler);
        assertEquals(Appender.State.ACCEPTED, replicator.getAppenderState());
        replicator.handleRead(inbound);
        assertEquals(Appender.State.READ_OFFSET, replicator.getAppenderState());

        Runnable reader = new Runnable() {
            @Override
            public void run() {
                while (Appender.State.ACCEPTED != replicator.getAppenderState()) {
                    replicator.handleRead(inbound);
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }
        };
        Thread inboundRead = new Thread(reader, "Inbound read thread");
        inboundRead.start();
        outbound.write(offsetBuffer);
        event.write(outbound);
        outbound.write(payloadBuffer);
        inboundRead.join(4000);
        assertEquals(Appender.State.ACCEPTED, replicator.getAppenderState());

        segment = new Segment(tmpFile);
        Event replicatedEvent = new Event(segment);
        assertEquals(event.size(), replicatedEvent.size());
        assertEquals(event.getMagic(), replicatedEvent.getMagic());
        assertEquals(event.getCrc32(), replicatedEvent.getCrc32());
        assertEquals(event.getId(), replicatedEvent.getId());
        assertTrue(replicatedEvent.validate());
        verify(eventChannel).append(eq(0L), eq(event));
    }

    @Test
    public void testOutboundReplication() throws Exception {
        File tmpFile = File.createTempFile("outbound-replication", ".tst");
        tmpFile.deleteOnExit();
        Segment segment = new Segment(tmpFile);

        int magic = 666;
        UUID channel = UUID.randomUUID();
        long timestamp = System.currentTimeMillis();
        final byte[] payload = "Give me Slack, or give me Food, or Kill me".getBytes();
        ByteBuffer payloadBuffer = ByteBuffer.wrap(payload);
        EventHeader event = new EventHeader(payload.length, magic, channel,
                                            timestamp, Event.crc32(payload));
        EventChannel eventChannel = mock(EventChannel.class);

        event.rewind();
        event.write(segment);
        segment.write(payloadBuffer);
        segment.force(false);

        EventEntry entry = new EventEntry();
        entry.set(eventChannel, 0, segment, event.totalSize());

        Bundle bundle = mock(Bundle.class);
        @SuppressWarnings("unchecked")
        ConsumerBarrier<EventEntry> consumerBarrier = mock(ConsumerBarrier.class);
        SocketChannelHandler handler = mock(SocketChannelHandler.class);
        when(consumerBarrier.waitFor(0)).thenReturn(0L).thenThrow(AlertException.ALERT_EXCEPTION);
        when(consumerBarrier.getEntry(0)).thenReturn(entry);

        final Replicator replicator = new Replicator(bundle, consumerBarrier);
        assertEquals(State.WAITING, replicator.getState());
        SocketOptions options = new SocketOptions();
        options.setSend_buffer_size(4);
        options.setReceive_buffer_size(4);
        options.setTimeout(100);
        ServerSocketChannel server = ServerSocketChannel.open();
        server.configureBlocking(true);
        server.socket().bind(new InetSocketAddress(0));
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
        replicator.handleConnect(outbound, handler);
        Util.waitFor("Never achieved WRITE_OFFSET state", new Util.Condition() {

            @Override
            public boolean value() {
                return State.WRITE_OFFSET == replicator.getState();
            }
        }, 1000L, 100L);
        replicator.halt();
        Util.waitFor("Never achieved WAITING state", new Util.Condition() {
            @Override
            public boolean value() {
                replicator.handleWrite(outbound);
                return State.WAITING == replicator.getState();
            }
        }, 1000L, 100L);
        inboundRead.join(4000);
        assertTrue(reader.replicated.hasRemaining());
        assertEquals(0, reader.offset);
        Event replicatedEvent = new Event(reader.replicated);
        assertEquals(event.size(), replicatedEvent.size());
        assertEquals(event.getMagic(), replicatedEvent.getMagic());
        assertEquals(event.getCrc32(), replicatedEvent.getCrc32());
        assertEquals(event.getId(), replicatedEvent.getId());
        assertTrue(replicatedEvent.validate());
        assertNull(entry.getChannel());
        assertNull(entry.getSegment());
        verify(eventChannel).commit(0);
    }
}
