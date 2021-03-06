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
package com.salesforce.ouroboros.api.producer;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * 
 * @author hhildebrand
 * 
 */
public interface EventSource {

    /**
     * Assume primary responsibility for the channels, using the supplied
     * timstamp as the last committed event batch from the failed primary.
     * 
     * @param newPrimaries
     *            - the map between the ID of the channel and the last committed
     *            event batch sequenc number for the channel for
     */
    void assumePrimary(Map<UUID, Long> newPrimaries);

    /**
     * The channel has been closed.
     * 
     * @param channel
     *            the id of the channel
     */
    void closed(UUID channel);

    /**
     * Notification of the list of channels have been lost and cannot be
     * recovered.
     * 
     * @param deadChannels
     */
    void deactivated(Collection<UUID> deadChannels);

    /**
     * The channel has been opened.
     * 
     * @param channel
     *            the id of the channel
     */
    void opened(UUID channel);

    /**
     * Notification of the list of channels to pause event publishing
     * 
     * @param pausedChannels
     */
    void pause(Collection<UUID> pausedChannels);

    /**
     * Notification that the receiver is no longer the primary for the channels
     * 
     * @param channels
     */
    void relinquishPrimary(Collection<UUID> channels);

    /**
     * Notification that the list of channels should be resumed.
     * 
     * @param pausedChannels
     */
    void resume(Collection<UUID> pausedChannels);

}
