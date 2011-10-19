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

import java.util.logging.Logger;

import com.salesforce.ouroboros.BatchHeader;
import com.salesforce.ouroboros.Node;
import com.salesforce.ouroboros.spindle.EventChannel.AppendSegment;

/**
 * The appender for receiving duplicated events from the primary.
 * 
 * @author hhildebrand
 * 
 */
public class ReplicatingAppender extends AbstractAppender {

    private static final Logger log = Logger.getLogger(ReplicatingAppender.class.getCanonicalName());

    public ReplicatingAppender(final Bundle bundle) {
        super(bundle);
    }

    /* (non-Javadoc)
     * @see com.salesforce.ouroboros.spindle.AbstractAppender#commit()
     */
    @Override
    protected void commit() {
        eventChannel.append(batchHeader, offset);
        Node node = batchHeader.getProducerMirror();
        Acknowledger acknowledger = bundle.getAcknowledger(node);
        if (acknowledger == null) {
            log.warning(String.format("Could not find an acknowledger for %s",
                                      node));
            return;
        }
        acknowledger.acknowledge(batchHeader.getChannel(),
                                 batchHeader.getTimestamp());
    }

    @Override
    protected BatchHeader createBatchHeader() {
        return new ReplicatedBatchHeader();
    }

    @Override
    protected AppendSegment getLogicalSegment() {
        ReplicatedBatchHeader replicatedBatchHeader = (ReplicatedBatchHeader) batchHeader;
        return new AppendSegment(
                                 eventChannel.segmentFor(replicatedBatchHeader.getOffset()),
                                 replicatedBatchHeader.getOffset());
    }
}