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

import static com.salesforce.ouroboros.spindle.transfer.Xerox.BUFFER_SIZE;
import static com.salesforce.ouroboros.spindle.transfer.Xerox.MAGIC;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hellblazer.pinkie.CommunicationsHandler;
import com.hellblazer.pinkie.SocketChannelHandler;
import com.salesforce.ouroboros.spindle.Bundle;
import com.salesforce.ouroboros.spindle.EventChannel;
import com.salesforce.ouroboros.spindle.Segment;
import com.salesforce.ouroboros.spindle.transfer.SinkContext.SinkState;
import com.salesforce.ouroboros.util.Utils;

/**
 * The inbound half of the Xerox, which receives the bulk transfer from the
 * outbound Xerox machine.
 * 
 * @author hhildebrand
 * 
 */
public class Sink implements CommunicationsHandler {
    private final static Logger  log                       = LoggerFactory.getLogger(Sink.class.getCanonicalName());
    public static final int      ACK_HEADER_SIZE           = 4;
    public final static int      CHANNEL_COUNT_HEADER_SIZE = 8;
    public final static int      CHANNEL_HEADER_SIZE       = 24;
    public static final int      SEGMENT_HEADER_SIZE       = 20;

    private final ByteBuffer     buffer                    = ByteBuffer.allocate(BUFFER_SIZE);
    private final Bundle         bundle;
    private long                 bytesWritten;
    private EventChannel         channel;
    private Segment              current;
    private boolean              error;
    private final SinkContext    fsm                       = new SinkContext(
                                                                             this);
    private SocketChannelHandler handler;
    private int                  segmentCount;
    private long                 segmentSize;
    private int                  channelCount;

    public Sink(Bundle bundle) {
        fsm.setName(Integer.toString(bundle.getId().processId));
        this.bundle = bundle;
    }

    @Override
    public void accept(SocketChannelHandler handler) {
        this.handler = handler;
        fsm.accept();
    }

    @Override
    public void closing() {
        if (log.isInfoEnabled()) {
            log.info(String.format("Closing %s", bundle.getId()));
        }
        channel = null;
    }

    @Override
    public void connect(SocketChannelHandler handler) {
        throw new UnsupportedOperationException();
    }

    public SinkState getState() {
        return fsm.getState();
    }

    @Override
    public void readReady() {
        fsm.readReady();
    }

    @Override
    public void writeReady() {
        fsm.writeReady();
    }

    protected void close() {
        handler.close();
    }

    protected void copy() {
        buffer.clear();
        buffer.limit(BUFFER_SIZE);
        if (copySegment()) {
            fsm.finished();
        } else {
            if (error) {
                fsm.close();
            } else {
                handler.selectForRead();
            }
        }
    }

    protected boolean copySegment() {
        long written;
        try {
            written = current.transferFrom(handler.getChannel(), bytesWritten,
                                           segmentSize - bytesWritten);
        } catch (IOException e) {
            error = true;
            if (log.isWarnEnabled()) {
                log.warn(String.format("Error copying segment %s on %s",
                                       current, bundle.getId()), e);
            }
            return false;
        }
        bytesWritten += written;
        if (log.isTraceEnabled()) {
            log.trace(String.format("copying segment %s, written %s, remaining: %s on %s",
                                    current, written, segmentSize
                                                      - bytesWritten,
                                    bundle.getId()));
        }
        if (bytesWritten == segmentSize) {
            segmentCount--;
            if (log.isInfoEnabled()) {
                log.info(String.format("Sink segment %s copy finished on %s",
                                       current, bundle.getId()));
            }
            return true;
        }
        return false;
    }

    protected void getChannelCount() {
        buffer.clear();
        buffer.limit(CHANNEL_COUNT_HEADER_SIZE);
        if (readChannelCount()) {
            fsm.finished();
        } else {
            if (error) {
                fsm.close();
            } else {
                handler.selectForRead();
            }
        }
    }

    protected boolean inError() {
        return error;
    }

    protected boolean isLastSegment() {
        return segmentCount == 0;
    }

    protected void nextChannel() {
        buffer.clear();
        buffer.limit(CHANNEL_HEADER_SIZE);
        if (readChannelHeader()) {
            fsm.finished();
        } else {
            if (error) {
                fsm.close();
            } else {
                handler.selectForRead();
            }
        }
    }

    protected void nextSegment() {
        if (segmentCount == 0) {
            if (channelCount == 0) {
                fsm.noMoreChannels();
            } else {
                fsm.noSegments();
            }
            return;
        }
        bytesWritten = 0;
        buffer.clear();
        buffer.limit(SEGMENT_HEADER_SIZE);
        if (readSegmentHeader()) {
            fsm.finished();
        } else {
            if (error) {
                fsm.close();
            } else {
                handler.selectForRead();
            }
        }
    }

    protected boolean readChannelCount() {
        try {
            if (handler.getChannel().read(buffer) < 0) {
                if (log.isTraceEnabled()) {
                    log.trace("Closing channel");
                }
                error = true;
                return false;
            }
        } catch (ClosedChannelException e) {
            if (log.isTraceEnabled()) {
                log.trace("Closing channel");
            }
            error = true;
            return false;
        } catch (IOException e) {
            error = true;
            if (log.isWarnEnabled()) {
                log.warn(String.format("Error reading handshake on %s",
                                       bundle.getId()), e);
            }
            return false;
        }
        if (!buffer.hasRemaining()) {
            buffer.flip();
            int magic = buffer.getInt();
            if (MAGIC != magic) {
                error = true;
                if (log.isWarnEnabled()) {
                    log.warn(String.format("Invalid handshake magic value %s on %s",
                                           magic, bundle.getId()));
                }
                return false;
            }
            channelCount = buffer.getInt();
            if (log.isInfoEnabled()) {
                log.info(String.format("Expecting %s channels on %s",
                                       channelCount, bundle.getId()));
            }
            return true;
        }
        return false;
    }

    protected boolean readChannelHeader() {
        try {
            if (handler.getChannel().read(buffer) < 0) {
                if (log.isTraceEnabled()) {
                    log.trace("Closing channel");
                }
                error = true;
                return false;
            }
        } catch (ClosedChannelException e) {
            if (log.isTraceEnabled()) {
                log.trace("Closing channel");
            }
            error = true;
            return false;
        } catch (IOException e) {
            error = true;
            if (log.isWarnEnabled()) {
                log.warn(String.format("Error reading channel header on %s",
                                       bundle.getId()), e);
            }
            error = true;
            return false;
        }

        if (!buffer.hasRemaining()) {
            buffer.flip();
            long magic = buffer.getInt();
            if (MAGIC != magic) {
                error = true;
                if (log.isWarnEnabled()) {
                    log.warn(String.format("Invalid channel header magic value %s on %s",
                                           magic, bundle.getId()));
                }
                error = true;
                return false;
            }
            segmentCount = buffer.getInt();
            UUID channelId = new UUID(buffer.getLong(), buffer.getLong());
            try {
                channel = bundle.xeroxEventChannel(channelId);
            } catch (Throwable e) {
                if (log.isWarnEnabled()) {
                    log.warn(String.format("Unable to create channel %s on %s",
                                           channelId, bundle.getId()), e);
                }
                error = true;
                return false;
            }
            if (log.isInfoEnabled()) {
                log.info(String.format("Sink started for channel %s, segment count %s on %s",
                                       channelId, segmentCount, bundle.getId()));
            }
            channelCount--;
            return true;
        }
        return false;
    }

    protected boolean readSegmentHeader() {
        try {
            if (handler.getChannel().read(buffer) < 0) {
                if (log.isTraceEnabled()) {
                    log.trace("Closing channel");
                }
                error = true;
                return false;
            }
        } catch (IOException e) {
            error = true;
            if (Utils.isClose(e)) {
                log.info(String.format("closing sink %s ", fsm.getName()));
            } else {
                if (log.isWarnEnabled()) {
                    log.warn(String.format("Error reading header on %s",
                                           bundle.getId()), e);
                }
            }
            error = true;
            return false;
        }

        if (!buffer.hasRemaining()) {
            buffer.flip();
            int magic = buffer.getInt();
            if (MAGIC != magic) {
                error = true;
                if (log.isWarnEnabled()) {
                    log.warn(String.format("Invalid handshake magic value %s on %s",
                                           magic, bundle.getId()));
                }
                error = true;
                return false;
            }
            long offset = buffer.getLong();
            try {
                current = channel.appendSegmentFor(offset).segment;
            } catch (IOException e) {
                log.warn(String.format("Cannot retrieve segment, shutting down sink %s",
                                       fsm.getName()), e);
                error = true;
                return false;
            }
            if (current == null) {
                error = true;
                if (log.isWarnEnabled()) {
                    log.warn(String.format("No segment for offset %s in channel %s on %s",
                                           offset, channel, bundle.getId()));
                }
                error = true;
                return false;
            }
            segmentSize = buffer.getLong();
            if (log.isInfoEnabled()) {
                log.info(String.format("Sink segment %s copy, total size %s, started on %s",
                                       current, segmentSize, bundle.getId()));
            }
            return true;
        }
        return false;
    }

    protected void selectForRead() {
        handler.selectForRead();
    }

    protected void selectForWrite() {
        handler.selectForWrite();
    }

    protected void sendAck() {
        if (log.isTraceEnabled()) {
            log.trace(String.format("Copy complete, sending ack from %s",
                                    bundle.getId()));
        }
        buffer.clear();
        buffer.limit(ACK_HEADER_SIZE);
        buffer.putInt(MAGIC);
        buffer.flip();
        if (writeAck()) {
            fsm.finished();
        } else {
            if (error) {
                fsm.close();
            } else {
                handler.selectForWrite();
            }
        }
    }

    protected boolean writeAck() {
        try {
            if (handler.getChannel().write(buffer) < 0) {
                if (log.isTraceEnabled()) {
                    log.trace(String.format("Closing channel on %s",
                                            bundle.getId()));
                }
                error = true;
                return false;
            }
        } catch (ClosedChannelException e) {
            if (log.isTraceEnabled()) {
                log.trace(String.format("Closing channel on %s", bundle.getId()));
            }
            error = true;
            return false;
        } catch (IOException e) {
            error = true;
            if (log.isWarnEnabled()) {
                log.warn(String.format("Error reading handshake on %s",
                                       bundle.getId()), e);
            }
            return false;
        }
        return !buffer.hasRemaining();
    }
}
