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
package com.salesforce.ouroboros.producer;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

import java.util.UUID;

import org.junit.Test;

import com.salesforce.ouroboros.BatchIdentity;

/**
 * 
 * @author hhildebrand
 * 
 */
public class TestBatchIdentity {
    @Test
    public void testCompare() {
        UUID channel = UUID.randomUUID();
        BatchIdentity lowBatch = new BatchIdentity(channel, 0L);
        BatchIdentity highBatch = new BatchIdentity(channel, Long.MAX_VALUE);
        assertTrue(lowBatch.compareTo(highBatch) < 0);
        assertTrue(highBatch.compareTo(lowBatch) > 0);

        UUID channel2 = new UUID(channel.getMostSignificantBits(),
                                 channel.getLeastSignificantBits() + 1);

        BatchIdentity otherBatch = new BatchIdentity(channel2, 0L);
        assertTrue(lowBatch.compareTo(otherBatch) < 0);
        assertTrue(highBatch.compareTo(otherBatch) < 0);
        assertTrue(otherBatch.compareTo(lowBatch) > 0);
        assertTrue(otherBatch.compareTo(highBatch) > 0);

        BatchIdentity equalsBatch = new BatchIdentity(channel, 0L);
        assertEquals(lowBatch, equalsBatch);
        assertEquals(0, lowBatch.compareTo(equalsBatch));
        assertEquals(0, equalsBatch.compareTo(lowBatch));
    }
}
