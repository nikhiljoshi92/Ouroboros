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

import static com.salesforce.ouroboros.util.Utils.point;

import java.io.File;
import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.hellblazer.pinkie.CommunicationsHandlerFactory;
import com.hellblazer.pinkie.ServerSocketChannelHandler;
import com.salesforce.ouroboros.ContactInformation;
import com.salesforce.ouroboros.Node;
import com.salesforce.ouroboros.spindle.EventChannel.Role;
import com.salesforce.ouroboros.spindle.WeaverConfigation.RootDirectory;
import com.salesforce.ouroboros.spindle.replication.Replicator;
import com.salesforce.ouroboros.spindle.source.Acknowledger;
import com.salesforce.ouroboros.spindle.source.Spindle;
import com.salesforce.ouroboros.spindle.transfer.Sink;
import com.salesforce.ouroboros.spindle.transfer.Xerox;
import com.salesforce.ouroboros.util.ConsistentHashFunction;
import com.salesforce.ouroboros.util.Rendezvous;

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
        public Replicator createCommunicationsHandler(SocketChannel channel) {
            return new Replicator(Weaver.this);
        }
    }

    private class SinkFactory implements CommunicationsHandlerFactory {
        @Override
        public Sink createCommunicationsHandler(SocketChannel channel) {
            return new Sink(Weaver.this);
        }
    }

    private class SpindleFactory implements CommunicationsHandlerFactory {
        @Override
        public Spindle createCommunicationsHandler(SocketChannel channel) {
            return new Spindle(Weaver.this);
        }
    }

    private static final Logger                     log               = Logger.getLogger(Weaver.class.getCanonicalName());
    private static final String                     WEAVER_REPLICATOR = "Weaver Replicator";
    private static final String                     WEAVER_SPINDLE    = "Weaver Spindle";
    private static final String                     WEAVER_XEROX      = "Weaver Xerox";
    static final int                                HANDSHAKE_SIZE    = Node.BYTE_LENGTH + 4;
    static final int                                MAGIC             = 0x1638;

    private final ConcurrentMap<Node, Acknowledger> acknowledgers     = new ConcurrentHashMap<Node, Acknowledger>();
    private final ConcurrentMap<UUID, EventChannel> channels          = new ConcurrentHashMap<UUID, EventChannel>();
    private final ContactInformation                contactInfo;
    private final Node                              id;
    private final long                              maxSegmentSize;
    private final ServerSocketChannelHandler        replicationHandler;
    private final ConcurrentMap<Node, Replicator>   replicators       = new ConcurrentHashMap<Node, Replicator>();
    private final ConsistentHashFunction<File>      roots             = new ConsistentHashFunction<File>();
    private final ServerSocketChannelHandler        spindleHandler;
    private ConsistentHashFunction<Node>            weaverRing        = new ConsistentHashFunction<Node>();
    private final ServerSocketChannelHandler        xeroxHandler;

    public Weaver(WeaverConfigation configuration) throws IOException {
        configuration.validate();
        id = configuration.getId();
        for (RootDirectory root : configuration.getRoots()) {
            roots.add(root.directory, root.weight);
            if (!root.directory.exists()) {
                if (!root.directory.mkdirs()) {
                    throw new IllegalStateException(
                                                    String.format("Cannot create root directory: %s",
                                                                  root.directory.getAbsolutePath()));
                }
            }
            if (!root.directory.isDirectory()) {
                throw new IllegalStateException(
                                                String.format("Root is not a directory: %s",
                                                              root.directory.getAbsolutePath()));
            }
        }
        maxSegmentSize = configuration.getMaxSegmentSize();
        replicationHandler = new ServerSocketChannelHandler(
                                                            WEAVER_REPLICATOR,
                                                            configuration.getReplicationSocketOptions(),
                                                            configuration.getReplicationAddress(),
                                                            configuration.getReplicators(),
                                                            new ReplicatorFactory());
        spindleHandler = new ServerSocketChannelHandler(
                                                        WEAVER_SPINDLE,
                                                        configuration.getSpindleSocketOptions(),
                                                        configuration.getSpindleAddress(),
                                                        configuration.getSpindles(),
                                                        new SpindleFactory());
        xeroxHandler = new ServerSocketChannelHandler(
                                                      WEAVER_XEROX,
                                                      configuration.getXeroxSocketOptions(),
                                                      configuration.getXeroxAddress(),
                                                      configuration.getXeroxes(),
                                                      new SinkFactory());
        contactInfo = new ContactInformation(
                                             spindleHandler.getLocalAddress(),
                                             replicationHandler.getLocalAddress(),
                                             xeroxHandler.getLocalAddress());
    }

    public void bootstrap(Node[] bootsrappingMembers) {
        for (Node node : bootsrappingMembers) {
            weaverRing.add(node, node.capacity);
        }
    }

    public void close(UUID channel) {
        EventChannel eventChannel = channels.remove(channel);
        if (channel != null) {
            eventChannel.close();
        }
    }

    @Override
    public void closeReplicator(Node node) {
        Replicator replicator = replicators.remove(node);
        if (replicator != null) {
            replicator.close();
        }
    }

    /**
     * Connect the originating replicators using the yellow pages to determine
     * endpoints
     * 
     * @param replicators
     *            - the collection of replicators to connnect
     * 
     * @param yellowPages
     *            - the contact information for the nodes
     */
    public void connectReplicators(Collection<Replicator> replicators,
                                   Map<Node, ContactInformation> yellowPages) {
        for (Replicator replicator : replicators) {
            try {
                replicator.connect(yellowPages, replicationHandler);
            } catch (IOException e) {
                if (log.isLoggable(Level.WARNING)) {
                    log.log(Level.WARNING,
                            String.format("Error connecting originating replicator from %s to %s",
                                          id, replicator.getPartner()), e);
                }
            }
        }
    }

    /**
     * Connnect and start the xeroxes.
     * 
     * @param xeroxes
     * @param yellowPages
     * @param rendezvous
     */
    public void connectXeroxes(Map<Node, Xerox> xeroxes,
                               Map<Node, ContactInformation> yellowPages,
                               Rendezvous rendezvous) {
        for (Map.Entry<Node, Xerox> entry : xeroxes.entrySet()) {
            try {
                Xerox xerox = entry.getValue();
                xerox.setRendezvous(rendezvous);
                xeroxHandler.connectTo(yellowPages.get(entry.getKey()).xerox,
                                       xerox);
            } catch (IOException e) {
                if (log.isLoggable(Level.INFO)) {
                    log.log(Level.INFO,
                            String.format("Error connecting xerox from %s to %s",
                                          id, entry.getKey()), e);
                }
                rendezvous.cancel();
                return;
            }
        }
    }

    @Override
    public EventChannel createEventChannelFor(UUID channel) {
        // This node is the primary for the event channel
        if (log.isLoggable(Level.INFO)) {
            log.fine(String.format(" Weaver[%s] created a new channel for %s",
                                   id, channel));
        }
        EventChannel ec = new EventChannel(channel, roots.hash(point(channel)),
                                           maxSegmentSize);
        channels.put(channel, ec);
        return ec;
    }

    @Override
    public EventChannel eventChannelFor(UUID channelId) {
        return channels.get(channelId);
    }

    /**
     * Failover any channels that we have been mirroring for the dead members
     * 
     * @param deadMembers
     *            - the weaver nodes that have died
     */
    public void failover(Collection<Node> deadMembers) {
        for (Node node : deadMembers) {
            if (log.isLoggable(Level.INFO)) {
                log.info(String.format("Removing %s from the partition on %s",
                                       node, id));
            }
            closeReplicator(node);
        }
        for (Entry<UUID, EventChannel> entry : channels.entrySet()) {
            UUID channelId = entry.getKey();
            EventChannel channel = entry.getValue();
            Node[] pair = getReplicationPair(channelId);
            if (deadMembers.contains(pair[0]) || deadMembers.contains(pair[1])) {
                if (channel.isPrimary()) {
                    // The mirror for this channel has died
                    if (log.isLoggable(Level.INFO)) {
                        log.info(String.format("%s is primary, mirror for %s has died, old mirror: %s",
                                               id, channelId, pair[1]));
                    }
                } else {
                    // This node is now the primary for the channel, xerox state to the new mirror
                    if (log.isLoggable(Level.INFO)) {
                        log.info(String.format("%s assuming primary role for: %s, old primary: %s",
                                               id, channelId, pair[1]));
                    }
                    channel.setPrimary();
                }
            }
        }
    }

    /* (non-Javadoc)
     * @see com.salesforce.ouroboros.spindle.Bundle#getAcknowledger(com.salesforce.ouroboros.Node)
     */
    @Override
    public Acknowledger getAcknowledger(Node node) {
        return acknowledgers.get(node);
    }

    public ContactInformation getContactInformation() {
        return contactInfo;
    }

    /**
     * @return the id
     */
    @Override
    public Node getId() {
        return id;
    }

    /**
     * Answer the replication pair of nodes that provide the primary and mirror
     * for the channel
     * 
     * @param channel
     *            - the id of the channel
     * @return the tuple of primary and mirror nodes for this channel
     */
    public Node[] getReplicationPair(UUID channel) {
        List<Node> pair = weaverRing.hash(point(channel), 2);
        return new Node[] { pair.get(0), pair.get(1) };
    }

    public Replicator getReplicator(Node node) {
        return replicators.get(node);
    }

    public void inactivate() {
        spindleHandler.closeOpenHandlers();
        replicationHandler.closeOpenHandlers();
        for (EventChannel channel : channels.values()) {
            channel.close();
        }
        channels.clear();
    }

    /* (non-Javadoc)
     * @see com.salesforce.ouroboros.spindle.Bundle#map(com.salesforce.ouroboros.Node, com.salesforce.ouroboros.spindle.Acknowledger)
     */
    @Override
    public void map(Node producer, Acknowledger acknowledger) {
        acknowledgers.put(producer, acknowledger);
    }

    /* (non-Javadoc)
     * @see com.salesforce.ouroboros.spindle.Bundle#map(com.salesforce.ouroboros.Node, com.salesforce.ouroboros.spindle.replication.Replicator)
     */
    @Override
    public void map(Node partner, Replicator replicator) {
        Replicator previous = replicators.put(partner, replicator);
        assert previous == null : String.format("No replicator should have been mapped to %s on %s",
                                                partner, replicator);
    }

    /**
     * Add the subscription with the receiver as the mirror for this channel
     * 
     * @param channel
     *            - The new subscription
     */
    public void openMirror(UUID channel) {
        if (channels.get(channel) != null) {
            return;
        }
        if (log.isLoggable(Level.INFO)) {
            log.fine(String.format(" Weaver[%s] is the mirror for the new subscription %s",
                                   id, channel));
        }
        channels.put(channel,
                     new EventChannel(Role.MIRROR, channel,
                                      roots.hash(point(channel)),
                                      maxSegmentSize, null));
    }

    /**
     * Add the subscription with the receiver as the primary for this channel
     * 
     * @param channel
     *            - the new subscription
     * @param mirror
     *            - the mirror node for this subscription
     */
    public void openPrimary(UUID channel, Node mirror) {
        if (channels.get(channel) != null) {
            return;
        }
        // This node is the primary for the event channel
        if (log.isLoggable(Level.INFO)) {
            log.fine(String.format(" Weaver[%s] is the primary for the new subscription %s",
                                   id, channel));
        }
        Replicator replicator = null;
        if (mirror != null) {
            replicator = replicators.get(mirror);
        }
        channels.put(channel,
                     new EventChannel(Role.PRIMARY, channel,
                                      roots.hash(point(channel)),
                                      maxSegmentSize, replicator));
    }

    /**
     * Open a replicator to the node
     * 
     * @param node
     *            - the replication node
     * @param info
     *            - the contact information for the node
     * @param rendezvous
     *            - the rendezvous used to sychronize connectivity
     * @return the Replicator to the node
     */
    public Replicator openReplicator(Node node, ContactInformation info,
                                     Rendezvous rendezvous) {
        Replicator replicator = new Replicator(this, node, rendezvous);
        Replicator previous = replicators.putIfAbsent(node, replicator);
        assert previous == null : String.format("Replicator already opend on weaver %s to weaver %s",
                                                id, node);
        return replicator;
    }

    /**
     * Rebalance a channel that this node serves as either the primary or
     * mirror. Add the list of channels to the Xerox machines that will perform
     * the state transfer, if needed, between this node and other nodes also
     * responsible for the channel
     * 
     * @param xeroxes
     *            - the map of Nodes to Xeroxes
     * 
     * @param channel
     *            - the id of the channel to rebalance
     * @param remappedPair
     *            - the pair of nodes that are the new primary/mirror
     *            responsible for the channel
     */
    public void rebalance(Map<Node, Xerox> xeroxes, UUID channel,
                          Node[] originalPair, Node[] remappedPair,
                          Collection<Node> deadMembers) {
        EventChannel eventChannel = channels.get(channel);
        assert eventChannel != null : String.format("The event channel to rebalance does not exist: %s",
                                                    channel);
        if (eventChannel.isPrimary()) {
            // self is the primary
            if (deadMembers.contains(originalPair[1])) {
                // mirror is down
                if (id.equals(remappedPair[0])) {
                    // Xerox state to the new mirror
                    Xerox xerox = xeroxes.get(remappedPair[1]);
                    if (xerox == null) {
                        xerox = new Xerox(remappedPair[1]);
                        xeroxes.put(remappedPair[1], xerox);
                    }
                    xerox.addChannel(eventChannel);
                    return;
                }
                // Xerox state to new primary and mirror
                Xerox xerox = xeroxes.get(remappedPair[0]);
                if (xerox == null) {
                    xerox = new Xerox(remappedPair[0]);
                    xeroxes.put(remappedPair[0], xerox);
                }
                xerox.addChannel(eventChannel);

                xerox = xeroxes.get(remappedPair[1]);
                if (xerox == null) {
                    xerox = new Xerox(remappedPair[1]);
                    xeroxes.put(remappedPair[1], xerox);
                }
                xerox.addChannel(eventChannel);
                return;
            }
            // mirror is up
            if (!id.equals(remappedPair[0])) {
                // Xerox state to the new primary
                Xerox xerox = xeroxes.get(remappedPair[0]);
                if (xerox == null) {
                    xerox = new Xerox(remappedPair[0]);
                    xeroxes.put(remappedPair[0], xerox);
                }
                xerox.addChannel(eventChannel);
                return;
            }

            return; // Nothing to do
        }

        // self is the secondary
        if (deadMembers.contains(originalPair[0])) {
            // primary is down
            if (id.equals(remappedPair[1])) {
                // Xerox state to the new primary
                Xerox xerox = xeroxes.get(remappedPair[0]);
                if (xerox == null) {
                    xerox = new Xerox(remappedPair[0]);
                    xeroxes.put(remappedPair[0], xerox);
                }
                xerox.addChannel(eventChannel);
                return;
            }
            // Xerox state to the new primary and mirror
            Xerox xerox = xeroxes.get(remappedPair[0]);
            if (xerox == null) {
                xerox = new Xerox(remappedPair[0]);
                xeroxes.put(remappedPair[0], xerox);
            }
            xerox.addChannel(eventChannel);

            xerox = xeroxes.get(remappedPair[1]);
            if (xerox == null) {
                xerox = new Xerox(remappedPair[1]);
                xeroxes.put(remappedPair[1], xerox);
            }
            xerox.addChannel(eventChannel);
            return;
        }

        // primary is up
        if (!id.equals(remappedPair[1])) {
            // Xerox state to the new mirror

            Xerox xerox = xeroxes.get(remappedPair[1]);
            if (xerox == null) {
                xerox = new Xerox(remappedPair[1]);
                xeroxes.put(remappedPair[1], xerox);
            }
            xerox.addChannel(eventChannel);
        }
        // nothing to do
    }

    public void setRing(ConsistentHashFunction<Node> nextRing) {
        weaverRing = nextRing;
    }

    /**
     * Start the weaver
     */
    public void start() {
        spindleHandler.start();
        replicationHandler.start();
        xeroxHandler.start();
    }

    /**
     * Terminate the weaver
     */
    public void terminate() {
        spindleHandler.terminate();
        replicationHandler.terminate();
        xeroxHandler.terminate();
        for (EventChannel channel : channels.values()) {
            channel.close();
        }
    }

    @Override
    public String toString() {
        return String.format("Weaver[%s], spindle endpoint: %s, replicator endpoint: %s",
                             id, spindleHandler.getLocalAddress(),
                             replicationHandler.getLocalAddress());
    }

    /**
     * Answer the remapped primary/mirror pairs using the nextRing
     * 
     * @param nextRing
     *            - the new weaver hash ring
     * @return the remapping of channels hosted on this node that have changed
     *         their primary or mirror in the new hash ring
     */
    protected Map<UUID, Node[][]> remap(ConsistentHashFunction<Node> nextRing) {
        Map<UUID, Node[][]> remapped = new HashMap<UUID, Node[][]>();
        for (UUID channel : channels.keySet()) {
            long channelPoint = point(channel);
            List<Node> newPair = nextRing.hash(channelPoint, 2);
            List<Node> oldPair = weaverRing.hash(channelPoint, 2);
            if (oldPair.contains(id)) {
                if (!oldPair.get(0).equals(newPair.get(0))
                    || !oldPair.get(1).equals(newPair.get(1))) {
                    remapped.put(channel,
                                 new Node[][] {
                                                 new Node[] { oldPair.get(0),
                                                                 oldPair.get(1) },
                                                 new Node[] { newPair.get(0),
                                                                 newPair.get(1) } });
                }
            }
        }
        return remapped;
    }
}
