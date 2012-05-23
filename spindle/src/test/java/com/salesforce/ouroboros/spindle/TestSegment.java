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
import static org.mockito.Mockito.mock;

import java.io.File;
import java.util.Random;

import org.junit.Test;

import com.salesforce.ouroboros.spindle.Segment.Mode;

/**
 * 
 * @author hhildebrand
 * 
 */
public class TestSegment {

    @Test
    public void testPrefixTranslation() throws Exception {
        Random random = new Random();
        long prefix = random.nextLong();
        while (prefix < 0L) {
            prefix = random.nextLong();
        }

        File file = new File(Long.toHexString(prefix)
                             + EventChannel.SEGMENT_SUFFIX);
        file.deleteOnExit();

        Segment segment = new Segment(mock(EventChannel.class), file,
                                      Mode.APPEND);
        assertEquals(prefix, segment.getPrefix());
    }

    /**
     * Test the the segment correctly identifies whether or not it contains the
     * logical offset
     */
    @Test
    public void testContains() {

    }

    /**
     * Test that the segment correctly identifies the segment following in the
     * event channel
     */
    @Test
    public void testNextSegment() {

    }

    /**
     * Test that the segment correctly returns the offset of an event contained
     * in that segment
     */
    @Test
    public void testOffsetAfter() {

    }

    /**
     * Test that the segment returns the correct EventSpan for a given offset
     */
    @Test
    public void testSpan() {

    }
}
