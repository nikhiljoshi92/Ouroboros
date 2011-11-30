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
import static junit.framework.Assert.assertNotNull;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.Serializable;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;

import org.junit.Test;
import org.mockito.internal.verification.Times;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.salesforce.ouroboros.ContactInformation;
import com.salesforce.ouroboros.Node;
import com.salesforce.ouroboros.RebalanceMessage;
import com.salesforce.ouroboros.partition.GlobalMessageType;
import com.salesforce.ouroboros.partition.Message;
import com.salesforce.ouroboros.partition.Switchboard;
import com.salesforce.ouroboros.spindle.CoordinatorContext.ControllerFSM;
import com.salesforce.ouroboros.spindle.CoordinatorContext.CoordinatorFSM;
import com.salesforce.ouroboros.util.ConsistentHashFunction;
import com.salesforce.ouroboros.util.Rendezvous;
import com.salesforce.ouroboros.util.Utils;

/**
 * 
 * @author hhildebrand
 * 
 */
public class TestCoordinator {

    private ContactInformation dummyInfo = new ContactInformation(
                                                                  new InetSocketAddress(
                                                                                        0),
                                                                  new InetSocketAddress(
                                                                                        0),
                                                                  new InetSocketAddress(
                                                                                        0));

    @Test
    public void testClose() throws Exception {
        ScheduledExecutorService timer = mock(ScheduledExecutorService.class);
        Weaver weaver = mock(Weaver.class);
        Switchboard switchboard = mock(Switchboard.class);
        Node localNode = new Node(0, 0, 0);
        when(weaver.getId()).thenReturn(localNode);
        when(weaver.getContactInformation()).thenReturn(dummyInfo);
        Coordinator coordinator = new Coordinator(
                                                  timer,
                                                  switchboard,
                                                  weaver,
                                                  new CoordinatorConfiguration());
        Node requester = new Node(-1, -1, -1);
        Node node1 = new Node(1, 1, 1);
        Node node2 = new Node(2, 1, 1);
        Node node3 = new Node(3, 1, 1);

        when(weaver.getId()).thenReturn(localNode);

        InetSocketAddress address = new InetSocketAddress("127.0.0.1", 0);
        ContactInformation contactInformation = new ContactInformation(address,
                                                                       address,
                                                                       address);
        ContactInformation contactInformation1 = new ContactInformation(
                                                                        address,
                                                                        address,
                                                                        address);
        ContactInformation contactInformation2 = new ContactInformation(
                                                                        address,
                                                                        address,
                                                                        address);
        ContactInformation contactInformation3 = new ContactInformation(
                                                                        address,
                                                                        address,
                                                                        address);
        Map<Node, ContactInformation> newMembers = new HashMap<Node, ContactInformation>();
        newMembers.put(localNode, contactInformation);
        newMembers.put(node1, contactInformation1);
        newMembers.put(node2, contactInformation2);
        newMembers.put(node3, contactInformation3);
        ConsistentHashFunction<Node> ring = new ConsistentHashFunction<Node>();
        ring.add(localNode, 1);
        ring.add(node1, 1);
        ring.add(node2, 1);
        ring.add(node3, 1);
        coordinator.setNextRing(ring);
        coordinator.commitNextRing();
        UUID primary = null;
        while (primary == null) {
            UUID test = UUID.randomUUID();
            List<Node> pair = ring.hash(Utils.point(test), 2);
            if (pair.get(0).equals(localNode)) {
                primary = test;
            }
        }
        UUID mirror = null;
        while (mirror == null) {
            UUID test = UUID.randomUUID();
            List<Node> pair = ring.hash(Utils.point(test), 2);
            if (pair.get(1).equals(localNode)) {
                mirror = test;
            }
        }
        coordinator.open(primary, requester);
        coordinator.open(mirror, requester);
        coordinator.close(primary, requester);
        coordinator.close(mirror, requester);
        verify(weaver).close(primary);
        verify(weaver).close(mirror);
        verify(switchboard, new Times(4)).send(isA(Message.class),
                                               eq(requester));
    }

    @Test
    public void testFailover() throws Exception {
        ScheduledExecutorService timer = mock(ScheduledExecutorService.class);
        Weaver weaver = mock(Weaver.class);
        Switchboard switchboard = mock(Switchboard.class);
        Node localNode = new Node(0, 0, 0);
        when(weaver.getId()).thenReturn(localNode);
        when(weaver.getContactInformation()).thenReturn(dummyInfo);
        Coordinator coordinator = new Coordinator(
                                                  timer,
                                                  switchboard,
                                                  weaver,
                                                  new CoordinatorConfiguration());
        Node node1 = new Node(1, 1, 1);
        Node node2 = new Node(2, 1, 1);
        Node node3 = new Node(3, 1, 1);

        when(weaver.getId()).thenReturn(localNode);

        InetSocketAddress address = new InetSocketAddress("127.0.0.1", 0);
        ContactInformation contactInformation = new ContactInformation(address,
                                                                       address,
                                                                       address);
        ContactInformation contactInformation1 = new ContactInformation(
                                                                        address,
                                                                        address,
                                                                        address);
        ContactInformation contactInformation2 = new ContactInformation(
                                                                        address,
                                                                        address,
                                                                        address);
        ContactInformation contactInformation3 = new ContactInformation(
                                                                        address,
                                                                        address,
                                                                        address);
        Map<Node, ContactInformation> newMembers = new HashMap<Node, ContactInformation>();
        newMembers.put(localNode, contactInformation);
        newMembers.put(node1, contactInformation1);
        newMembers.put(node2, contactInformation2);
        newMembers.put(node3, contactInformation3);
        Set<Node> deadMembers = newMembers.keySet();
        when(switchboard.getDeadMembers()).thenReturn(deadMembers);
        coordinator.getFsm().setState(CoordinatorFSM.Failover);
        coordinator.failover();

        verify(weaver).failover(deadMembers);
        verify(weaver).closeReplicator(localNode);
        verify(weaver).closeReplicator(node1);
        verify(weaver).closeReplicator(node2);
        verify(weaver).closeReplicator(node2);
    }

    @Test
    public void testOpen() throws Exception {
        ScheduledExecutorService timer = mock(ScheduledExecutorService.class);
        Weaver weaver = mock(Weaver.class);
        Switchboard switchboard = mock(Switchboard.class);
        Node localNode = new Node(0, 0, 0);
        when(weaver.getId()).thenReturn(localNode);
        when(weaver.getContactInformation()).thenReturn(dummyInfo);
        Coordinator coordinator = new Coordinator(
                                                  timer,
                                                  switchboard,
                                                  weaver,
                                                  new CoordinatorConfiguration());
        Node requester = new Node(-1, -1, -1);
        Node node1 = new Node(1, 1, 1);
        Node node2 = new Node(2, 1, 1);
        Node node3 = new Node(3, 1, 1);

        when(weaver.getId()).thenReturn(localNode);

        ConsistentHashFunction<Node> ring = new ConsistentHashFunction<Node>();
        ring.add(localNode, 1);
        ring.add(node1, 1);
        ring.add(node2, 1);
        ring.add(node3, 1);
        coordinator.setNextRing(ring);
        coordinator.commitNextRing();
        UUID primary = null;
        while (primary == null) {
            UUID test = UUID.randomUUID();
            List<Node> pair = ring.hash(Utils.point(test), 2);
            if (pair.get(0).equals(localNode)) {
                primary = test;
            }
        }
        UUID mirror = null;
        while (mirror == null) {
            UUID test = UUID.randomUUID();
            List<Node> pair = ring.hash(Utils.point(test), 2);
            if (pair.get(1).equals(localNode)) {
                mirror = test;
            }
        }
        coordinator.open(primary, requester);
        coordinator.openMirror(mirror, requester);
        verify(weaver).openPrimary(eq(primary), isA(Node.class));
        verify(weaver).openMirror(eq(mirror), isA(Node.class));
    }

    @Test
    public void testOpenReplicators() throws Exception {
        ScheduledExecutorService timer = mock(ScheduledExecutorService.class);
        Weaver weaver = mock(Weaver.class);
        Switchboard switchboard = mock(Switchboard.class);
        when(weaver.getId()).thenReturn(new Node(0, 0, 0));
        when(weaver.getContactInformation()).thenReturn(dummyInfo);
        Coordinator coordinator = new Coordinator(
                                                  timer,
                                                  switchboard,
                                                  weaver,
                                                  new CoordinatorConfiguration());
        Node node1 = new Node(1, 1, 1);
        Node node2 = new Node(2, 1, 1);
        Node node3 = new Node(3, 1, 1);

        InetSocketAddress address = new InetSocketAddress("127.0.0.1", 0);
        ContactInformation contactInformation1 = new ContactInformation(
                                                                        address,
                                                                        address,
                                                                        address);
        ContactInformation contactInformation2 = new ContactInformation(
                                                                        address,
                                                                        address,
                                                                        address);
        ContactInformation contactInformation3 = new ContactInformation(
                                                                        address,
                                                                        address,
                                                                        address);
        coordinator.dispatch(GlobalMessageType.ADVERTISE_CHANNEL_BUFFER, node1,
                             new Serializable[] { contactInformation1, true },
                             0);
        coordinator.dispatch(GlobalMessageType.ADVERTISE_CHANNEL_BUFFER, node2,
                             new Serializable[] { contactInformation2, true },
                             0);
        coordinator.dispatch(GlobalMessageType.ADVERTISE_CHANNEL_BUFFER, node3,
                             new Serializable[] { contactInformation3, true },
                             0);
        coordinator.getInactiveMembers().addAll(Arrays.asList(node1, node2,
                                                              node3));
        coordinator.getFsm().setState(CoordinatorFSM.EstablishReplicators);
        coordinator.readyReplicators();
        Rendezvous rendezvous = coordinator.getRendezvous();
        assertNotNull(rendezvous);
        assertEquals(3, rendezvous.getParties());

        verify(weaver).openReplicator(node1, contactInformation1, rendezvous);
        verify(weaver).openReplicator(node2, contactInformation2, rendezvous);
        verify(weaver).openReplicator(node3, contactInformation3, rendezvous);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testRebalance() throws Exception {
        ScheduledExecutorService timer = mock(ScheduledExecutorService.class);
        Weaver weaver = mock(Weaver.class);
        Switchboard switchboard = mock(Switchboard.class);
        Node localNode = new Node(0, 0, 0);
        when(weaver.getId()).thenReturn(localNode);
        when(weaver.getContactInformation()).thenReturn(dummyInfo);
        Coordinator coordinator = new Coordinator(
                                                  timer,
                                                  switchboard,
                                                  weaver,
                                                  new CoordinatorConfiguration());
        Node requester = new Node(-1, -1, -1);
        Node node1 = new Node(1, 1, 1);
        Node node2 = new Node(2, 1, 1);
        Node node3 = new Node(3, 1, 1);

        when(weaver.getId()).thenReturn(localNode);
        ConsistentHashFunction<Node> ring = new ConsistentHashFunction<Node>();
        ring.add(localNode, 1);
        ring.add(node1, 1);
        ring.add(node2, 1);
        ring.add(node3, 1);
        coordinator.setNextRing(ring);
        coordinator.commitNextRing();
        UUID primary = null;
        while (primary == null) {
            UUID test = UUID.randomUUID();
            List<Node> pair = ring.hash(Utils.point(test), 2);
            if (pair.get(0).equals(localNode)) {
                primary = test;
            }
        }
        UUID mirror = null;
        while (mirror == null) {
            UUID test = UUID.randomUUID();
            List<Node> pair = ring.hash(Utils.point(test), 2);
            if (pair.get(1).equals(localNode)) {
                mirror = test;
            }
        }
        coordinator.open(primary, requester);
        coordinator.open(mirror, requester);
        Node removedMirror = coordinator.getReplicationPair(primary)[1];
        Node removedPrimary = coordinator.getReplicationPair(mirror)[0];

        ConsistentHashFunction<Node> newRing = ring.clone();
        newRing.remove(removedMirror);
        newRing.remove(removedPrimary);
        coordinator.setNextRing(newRing);
        Map<UUID, Node[][]> remapped = coordinator.remap();
        List<Node> deadMembers = Arrays.asList(removedPrimary, removedMirror);
        coordinator.rebalance(remapped, deadMembers);
        verify(weaver).rebalance(isA(Map.class), isA(Rendezvous.class),
                                 eq(primary), eq(remapped.get(primary)[0]),
                                 eq(remapped.get(primary)[1]), eq(deadMembers));
        verify(weaver).rebalance(isA(Map.class), isA(Rendezvous.class),
                                 eq(mirror), eq(remapped.get(mirror)[0]),
                                 eq(remapped.get(mirror)[1]), eq(deadMembers));
    }

    @Test
    public void testRemap() throws Exception {
        ScheduledExecutorService timer = mock(ScheduledExecutorService.class);
        Weaver weaver = mock(Weaver.class);
        Node localNode = new Node(0, 0, 0);
        when(weaver.getId()).thenReturn(localNode);
        when(weaver.getContactInformation()).thenReturn(dummyInfo);
        Switchboard switchboard = mock(Switchboard.class);
        Coordinator coordinator = new Coordinator(
                                                  timer,
                                                  switchboard,
                                                  weaver,
                                                  new CoordinatorConfiguration());
        Node requester = new Node(-1, -1, -1);
        Node node1 = new Node(1, 1, 1);
        Node node2 = new Node(2, 1, 1);
        Node node3 = new Node(3, 1, 1);

        when(weaver.getId()).thenReturn(localNode);
        ConsistentHashFunction<Node> ring = new ConsistentHashFunction<Node>();
        ring.add(localNode, 1);
        ring.add(node1, 1);
        ring.add(node2, 1);
        ring.add(node3, 1);
        coordinator.setNextRing(ring);
        coordinator.commitNextRing();
        UUID primary = null;
        while (primary == null) {
            UUID test = UUID.randomUUID();
            List<Node> pair = ring.hash(Utils.point(test), 2);
            if (pair.get(0).equals(localNode)) {
                primary = test;
            }
        }
        UUID mirror = null;
        while (mirror == null) {
            UUID test = UUID.randomUUID();
            List<Node> pair = ring.hash(Utils.point(test), 2);
            if (pair.get(1).equals(localNode)) {
                mirror = test;
            }
        }
        coordinator.open(primary, requester);
        coordinator.open(mirror, requester);
        ConsistentHashFunction<Node> newRing = ring.clone();
        newRing.remove(coordinator.getReplicationPair(primary)[1]);
        newRing.remove(coordinator.getReplicationPair(mirror)[0]);
        coordinator.setNextRing(newRing);
        Map<UUID, Node[][]> remapped = coordinator.remap();
        assertNotNull(remapped);
        assertEquals(2, remapped.size());
        assertNotNull(remapped.get(primary));
        assertNotNull(remapped.get(mirror));
    }

    @Test
    public void testInitiateBootstrap() throws Exception {
        ScheduledExecutorService timer = mock(ScheduledExecutorService.class);
        Weaver weaver = mock(Weaver.class);
        final Node localNode = new Node(0, 0, 0);
        when(weaver.getId()).thenReturn(localNode);
        when(weaver.getContactInformation()).thenReturn(dummyInfo);
        Switchboard switchboard = mock(Switchboard.class);
        Coordinator coordinator = new Coordinator(
                                                  timer,
                                                  switchboard,
                                                  weaver,
                                                  new CoordinatorConfiguration());
        Answer<Void> answer = new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Message message = (Message) invocation.getArguments()[0];
                assertEquals(RebalanceMessage.BOOTSTRAP, message.type);
                Node[] joiningMembers = (Node[]) message.arguments[0];
                assertNotNull(joiningMembers);
                assertEquals(1, joiningMembers.length);
                assertEquals(localNode, joiningMembers[0]);
                return null;
            }
        };
        doAnswer(answer).when(switchboard).ringCast(isA(Message.class));
        coordinator.stabilized();
        coordinator.getInactiveMembers().add(localNode);
        coordinator.initiateBootstrap(new Node[] { localNode });
        assertEquals(ControllerFSM.CoordinateBootstrap, coordinator.getState());
        coordinator.dispatch(RebalanceMessage.BOOTSTRAP, localNode,
                             new Node[] { localNode }, 0);
        assertEquals(CoordinatorFSM.Stable, coordinator.getState());
        verify(switchboard, new Times(1)).ringCast(isA(Message.class));
    }
}
