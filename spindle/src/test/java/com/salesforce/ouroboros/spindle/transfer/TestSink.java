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
package com.salesforce.ouroboros.spindle.transfer;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.UUID;

import org.junit.Test;
import org.mockito.internal.verification.Times;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.hellblazer.pinkie.SocketChannelHandler;
import com.hellblazer.pinkie.SocketOptions;
import com.salesforce.ouroboros.Event;
import com.salesforce.ouroboros.Node;
import com.salesforce.ouroboros.spindle.Bundle;
import com.salesforce.ouroboros.spindle.EventChannel;
import com.salesforce.ouroboros.spindle.Segment;
import com.salesforce.ouroboros.spindle.WeaverCoordinator;
import com.salesforce.ouroboros.spindle.Segment.Mode;
import com.salesforce.ouroboros.spindle.transfer.SinkContext.SinkFSM;
import com.salesforce.ouroboros.spindle.transfer.XeroxContext.XeroxFSM;
import com.salesforce.ouroboros.testUtils.Util;
import com.salesforce.ouroboros.util.Rendezvous;

/**
 * 
 * @author hhildebrand
 * 
 */
public class TestSink {
    @Test
    public void testCopy() throws Exception {
        Bundle bundle = mock(Bundle.class);
        when(bundle.getId()).thenReturn(new Node(0));
        EventChannel channel = mock(EventChannel.class);
        Segment segment = mock(Segment.class);
        SocketChannel socket = mock(SocketChannel.class);
        SocketChannelHandler handler = mock(SocketChannelHandler.class);
        when(handler.getChannel()).thenReturn(socket);
        final String content = "Give me Slack, or give me Food, or Kill me";

        Sink sink = new Sink(bundle);

        final UUID channelId = UUID.randomUUID();
        final long prefix = 567L;
        final long bytesLeft = content.getBytes().length;

        Answer<Integer> readHandshake = new Answer<Integer>() {
            @Override
            public Integer answer(InvocationOnMock invocation) throws Throwable {
                ByteBuffer buffer = (ByteBuffer) invocation.getArguments()[0];
                buffer.putInt(Xerox.MAGIC);
                buffer.putInt(0);
                buffer.putInt(666);
                return Sink.HANDSHAKE_HEADER_SIZE;
            }
        };

        Answer<Integer> channelHeaderRead = new Answer<Integer>() {
            @Override
            public Integer answer(InvocationOnMock invocation) throws Throwable {
                ByteBuffer buffer = (ByteBuffer) invocation.getArguments()[0];
                buffer.putInt(Xerox.MAGIC);
                buffer.putInt(1);
                buffer.putLong(channelId.getMostSignificantBits());
                buffer.putLong(channelId.getLeastSignificantBits());
                return Sink.CHANNEL_HEADER_SIZE;
            }
        };

        Answer<Integer> segmentHeaderRead = new Answer<Integer>() {
            @Override
            public Integer answer(InvocationOnMock invocation) throws Throwable {
                ByteBuffer buffer = (ByteBuffer) invocation.getArguments()[0];
                buffer.putInt(Xerox.MAGIC);
                buffer.putLong(prefix);
                buffer.putLong(bytesLeft);
                return Sink.SEGMENT_HEADER_SIZE;
            }
        };

        when(bundle.xeroxEventChannel(channelId)).thenReturn(channel);
        when(channel.appendSegmentFor(prefix)).thenReturn(segment);
        when(socket.read(isA(ByteBuffer.class))).thenReturn(0).thenAnswer(readHandshake).thenAnswer(channelHeaderRead).thenReturn(0).thenAnswer(segmentHeaderRead).thenReturn(0);
        when(segment.transferFrom(socket, 0, bytesLeft)).thenReturn(12L);
        when(segment.transferFrom(socket, 12, bytesLeft - 12)).thenReturn(bytesLeft - 12);

        sink.accept(handler);
        sink.readReady();
        sink.readReady();
        sink.readReady();
        sink.readReady();
        sink.readReady();

        verify(socket, new Times(6)).read(isA(ByteBuffer.class));
        verify(segment, new Times(2)).transferFrom(isA(ReadableByteChannel.class),
                                                   isA(Long.class),
                                                   isA(Long.class));
        assertEquals(SinkFSM.ReadChannelHeader, sink.getState());
    }

    @Test
    public void testReadHandshake() throws Exception {
        Bundle bundle = mock(Bundle.class);
        when(bundle.getId()).thenReturn(new Node(0));
        SocketChannel socket = mock(SocketChannel.class);
        SocketChannelHandler handler = mock(SocketChannelHandler.class);
        when(handler.getChannel()).thenReturn(socket);

        Sink sink = new Sink(bundle);

        Answer<Integer> readHandshake = new Answer<Integer>() {
            @Override
            public Integer answer(InvocationOnMock invocation) throws Throwable {
                ByteBuffer buffer = (ByteBuffer) invocation.getArguments()[0];
                buffer.putInt(Xerox.MAGIC);
                buffer.putInt(0);
                buffer.putInt(666);
                return 4;
            }
        };

        when(socket.read(isA(ByteBuffer.class))).thenReturn(0).thenAnswer(readHandshake);

        assertEquals(SinkFSM.Initial, sink.getState());
        sink.accept(handler);
        assertEquals(SinkFSM.ReadHandshake, sink.getState());
        sink.readReady();
        assertEquals(SinkFSM.Suspended, sink.getState());

        verify(socket, new Times(2)).read(isA(ByteBuffer.class));
    }

    @Test
    public void testReadChannelHeader() throws Exception {
        Bundle bundle = mock(Bundle.class);
        when(bundle.getId()).thenReturn(new Node(0));
        SocketChannel socket = mock(SocketChannel.class);
        SocketChannelHandler handler = mock(SocketChannelHandler.class);
        when(handler.getChannel()).thenReturn(socket);

        Sink sink = new Sink(bundle);

        final UUID channelId = UUID.randomUUID();

        Answer<Integer> readHandshake = new Answer<Integer>() {
            @Override
            public Integer answer(InvocationOnMock invocation) throws Throwable {
                ByteBuffer buffer = (ByteBuffer) invocation.getArguments()[0];
                buffer.putInt(Xerox.MAGIC);
                buffer.putInt(0);
                buffer.putInt(666);
                return Sink.HANDSHAKE_HEADER_SIZE;
            }
        };

        Answer<Integer> readChannelHeader = new Answer<Integer>() {
            @Override
            public Integer answer(InvocationOnMock invocation) throws Throwable {
                ByteBuffer buffer = (ByteBuffer) invocation.getArguments()[0];
                buffer.putInt(Xerox.MAGIC);
                buffer.putInt(1);
                buffer.putLong(channelId.getMostSignificantBits());
                buffer.putLong(channelId.getLeastSignificantBits());
                return Sink.CHANNEL_HEADER_SIZE;
            }
        };

        when(socket.read(isA(ByteBuffer.class))).thenAnswer(readHandshake).thenReturn(0).thenAnswer(readChannelHeader).thenReturn(0);

        sink.accept(handler);
        sink.readReady();
        assertEquals(SinkFSM.ReadChannelHeader, sink.getState());
        sink.readReady();
        assertEquals(SinkFSM.ReadSegmentHeader, sink.getState());

        verify(socket, new Times(4)).read(isA(ByteBuffer.class));
        verify(bundle).xeroxEventChannel(channelId);
    }

    @Test
    public void testReadSegmentHeader() throws Exception {
        Bundle bundle = mock(Bundle.class);
        when(bundle.getId()).thenReturn(new Node(0));
        EventChannel channel = mock(EventChannel.class);
        Segment segment = mock(Segment.class);
        SocketChannel socket = mock(SocketChannel.class);
        SocketChannelHandler handler = mock(SocketChannelHandler.class);
        when(handler.getChannel()).thenReturn(socket);

        Sink sink = new Sink(bundle);

        final UUID channelId = UUID.randomUUID();
        final long prefix = 567L;
        final long bytesLeft = 6456L;

        Answer<Integer> readHandshake = new Answer<Integer>() {
            @Override
            public Integer answer(InvocationOnMock invocation) throws Throwable {
                ByteBuffer buffer = (ByteBuffer) invocation.getArguments()[0];
                buffer.putInt(Xerox.MAGIC);
                buffer.putInt(0);
                buffer.putInt(666);
                return Sink.HANDSHAKE_HEADER_SIZE;
            }
        };

        Answer<Integer> channelHeaderRead = new Answer<Integer>() {
            @Override
            public Integer answer(InvocationOnMock invocation) throws Throwable {
                ByteBuffer buffer = (ByteBuffer) invocation.getArguments()[0];
                buffer.putInt(Xerox.MAGIC);
                buffer.putInt(1);
                buffer.putLong(channelId.getMostSignificantBits());
                buffer.putLong(channelId.getLeastSignificantBits());
                return Sink.CHANNEL_HEADER_SIZE;
            }
        };

        Answer<Integer> segmentHeaderRead = new Answer<Integer>() {
            @Override
            public Integer answer(InvocationOnMock invocation) throws Throwable {
                ByteBuffer buffer = (ByteBuffer) invocation.getArguments()[0];
                buffer.putInt(Xerox.MAGIC);
                buffer.putLong(prefix);
                buffer.putLong(bytesLeft);
                return Sink.SEGMENT_HEADER_SIZE;
            }
        };
        when(bundle.xeroxEventChannel(channelId)).thenReturn(channel);
        when(channel.appendSegmentFor(prefix)).thenReturn(segment);
        when(socket.read(isA(ByteBuffer.class))).thenReturn(0).thenAnswer(readHandshake).thenAnswer(channelHeaderRead).thenReturn(0).thenAnswer(segmentHeaderRead).thenReturn(0);

        sink.accept(handler);
        sink.readReady();
        sink.readReady();
        assertEquals(SinkFSM.ReadSegmentHeader, sink.getState());
        sink.readReady();
        assertEquals(SinkFSM.CopySegment, sink.getState());

        verify(socket, new Times(5)).read(isA(ByteBuffer.class));
        verify(channel).appendSegmentFor(prefix);

    }

    @Test
    public void testXeroxSink() throws Exception {
        mock(WeaverCoordinator.class);
        SocketChannelHandler inboundHandler = mock(SocketChannelHandler.class);
        Bundle inboundBundle = mock(Bundle.class);
        when(inboundBundle.getId()).thenReturn(new Node(0));
        UUID channelId = UUID.randomUUID();
        long prefix1 = 0x1600;
        long prefix2 = 0x1800;

        EventChannel inboundEventChannel = mock(EventChannel.class);

        File inboundTmpFile1 = File.createTempFile("sink1", ".tst");
        inboundTmpFile1.delete();
        inboundTmpFile1.deleteOnExit();
        Segment inboundSegment1 = new Segment(inboundEventChannel,
                                              inboundTmpFile1, Mode.APPEND);

        File inboundTmpFile2 = File.createTempFile("sink2", ".tst");
        inboundTmpFile2.delete();
        inboundTmpFile2.deleteOnExit();
        Segment inboundSegment2 = new Segment(inboundEventChannel,
                                              inboundTmpFile2, Mode.APPEND);

        EventChannel outboundEventChannel = mock(EventChannel.class);
        SocketChannelHandler outboundHandler = mock(SocketChannelHandler.class);

        File tmpOutboundFile1 = new File(Long.toHexString(prefix1)
                                         + EventChannel.SEGMENT_SUFFIX);
        tmpOutboundFile1.delete();
        tmpOutboundFile1.deleteOnExit();
        Segment outboundSegment1 = new Segment(outboundEventChannel,
                                               tmpOutboundFile1, Mode.APPEND);

        File tmpOutboundFile2 = new File(Long.toHexString(prefix2)
                                         + EventChannel.SEGMENT_SUFFIX);
        tmpOutboundFile2.delete();
        tmpOutboundFile2.deleteOnExit();
        Segment outboundSegment2 = new Segment(outboundEventChannel,
                                               tmpOutboundFile2, Mode.APPEND);

        Node toNode = new Node(0, 0, 0);
        Node fromNode = new Node(1, 1, 1);

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

        when(inboundHandler.getChannel()).thenReturn(inbound);
        when(outboundHandler.getChannel()).thenReturn(outbound);

        when(inboundBundle.xeroxEventChannel(channelId)).thenReturn(inboundEventChannel);
        when(inboundEventChannel.appendSegmentFor(prefix1)).thenReturn(inboundSegment1);
        when(inboundEventChannel.appendSegmentFor(prefix2)).thenReturn(inboundSegment2);
        when(outboundEventChannel.getId()).thenReturn(channelId);

        Event event1 = new Event(
                                 666,
                                 ByteBuffer.wrap("Give me Slack, or give me Food, or Kill me".getBytes()));
        event1.rewind();
        event1.write(outboundSegment1);
        outboundSegment1.force(false);
        outboundSegment1.close();
        outboundSegment1 = new Segment(outboundEventChannel, tmpOutboundFile1,
                                       Mode.READ);

        Event event2 = new Event(
                                 666,
                                 ByteBuffer.wrap("Give me Food, or give me Slack, or Kill me".getBytes()));
        event2.rewind();
        event2.write(outboundSegment2);
        outboundSegment2.force(false);
        outboundSegment2.close();
        outboundSegment2 = new Segment(outboundEventChannel, tmpOutboundFile2,
                                       Mode.READ);
        LinkedList<Segment> appendSegments = new LinkedList<Segment>(
                                                                     Arrays.asList(outboundSegment1,
                                                                                   outboundSegment2));
        when(outboundEventChannel.getSegmentStack()).thenReturn(appendSegments);

        Rendezvous rendezvous = mock(Rendezvous.class);
        final Xerox xerox = new Xerox(fromNode, toNode);
        xerox.setRendezvous(rendezvous);
        xerox.addChannel(outboundEventChannel);

        final Sink sink = new Sink(inboundBundle);

        sink.accept(inboundHandler);
        xerox.connect(outboundHandler);

        Runnable reader = new Runnable() {
            @Override
            public void run() {
                while (SinkFSM.Finished != sink.getState()) {
                    sink.readReady();
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

        Util.waitFor("Never achieved Finished state", new Util.Condition() {
            @Override
            public boolean value() {
                if (XeroxFSM.Finished == xerox.getState()) {
                    return true;
                }
                xerox.writeReady();
                return false;
            }
        }, 2000L, 100L);
        inboundRead.join(4000);

        Util.waitFor("Never achieved Closed state", new Util.Condition() {
            @Override
            public boolean value() {
                if (XeroxFSM.Closed == xerox.getState()) {
                    return true;
                }
                xerox.readReady();
                return false;
            }
        }, 2000L, 100L);

        verify(rendezvous).meet();

        assertTrue("first segment empty", inboundTmpFile1.length() != 0);
        assertTrue("second segment empty", inboundTmpFile2.length() != 0);
        inboundSegment1.close();
        inboundSegment2.close();

        inboundSegment1 = new Segment(inboundEventChannel, inboundTmpFile1,
                                      Mode.READ);
        inboundSegment2 = new Segment(inboundEventChannel, inboundTmpFile2,
                                      Mode.READ);

        Event testEvent = new Event(inboundSegment1);
        assertEquals(event1.getCrc32(), testEvent.getCrc32());

        testEvent = new Event(inboundSegment2);
        assertEquals(event2.getCrc32(), testEvent.getCrc32());
    }
}
