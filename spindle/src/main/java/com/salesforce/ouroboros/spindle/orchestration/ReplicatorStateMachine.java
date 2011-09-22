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
package com.salesforce.ouroboros.spindle.orchestration;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import com.salesforce.ouroboros.Node;
import com.salesforce.ouroboros.partition.Message;
import com.salesforce.ouroboros.util.Rendezvous;

/**
 * 
 * @author hhildebrand
 * 
 */
public class ReplicatorStateMachine implements StateMachine {

    public enum State {
        REBALANCED, STABLIZED, SYNCHRONIZED, SYNCHRONIZING, UNSTABLE,
        UNSYNCHRONIZED;
    }

    protected static final Logger             log                  = Logger.getLogger(ReplicatorStateMachine.class.getCanonicalName());

    private final AtomicReference<Rendezvous> replicatorRendezvous = new AtomicReference<Rendezvous>();
    protected final Coordinator               coordinator;
    protected final AtomicReference<State>    state                = new AtomicReference<State>(
                                                                                                State.UNSTABLE);

    public ReplicatorStateMachine(Coordinator coordinator) {
        this.coordinator = coordinator;
    }

    @Override
    public void destabilize() {
    }

    public void replicatorsSynchronizedOn(Node sender) {
        throw new IllegalStateException(
                                        "Transition handled by coordinator only");
    }

    public void replicatorSynchronizeFailed(Node sender) {
        throw new IllegalStateException(
                                        "Transition handled by coordinator only");
    }

    @Override
    public void stabilized() {
        if (!state.compareAndSet(State.UNSTABLE, State.STABLIZED)) {
            throw new IllegalStateException(
                                            String.format("Can only stabilize in the unstable state: %s",
                                                          state.get()));
        }
    }

    /**
     * Synchronize the replicators on this node. Set up the replicator
     * rendezvous for local replicators, notifying the group leader when the
     * replicators have been synchronized or whether the synchronization has
     * failed.
     * 
     * @param leader
     *            - the group leader
     */
    public void synchronizeReplicators(final Node leader) {
        if (!state.compareAndSet(State.STABLIZED, State.SYNCHRONIZING)) {
            throw new IllegalStateException(
                                            String.format("Can only transition to synchronizing in state STABILIZED: ",
                                                          state.get()));
        }
        Runnable action = new Runnable() {
            @Override
            public void run() {
                coordinator.getSwitchboard().send(new Message(
                                                              coordinator.getId(),
                                                              ReplicatorSynchronization.REPLICATORS_SYNCHRONIZED),
                                                  leader);
            }
        };
        Runnable timeoutAction = new Runnable() {
            @Override
            public void run() {
                coordinator.getSwitchboard().send(new Message(
                                                              coordinator.getId(),
                                                              ReplicatorSynchronization.REPLICATOR_SYNCHRONIZATION_FAILED),
                                                  leader);
            }
        };
        replicatorRendezvous.set(coordinator.openReplicators(coordinator.getNewMembers(),
                                                             action,
                                                             timeoutAction));
    }

    /**
     * The synchronization of the replicators in the group failed.
     * 
     * @param leader
     *            - the leader of the spindle group
     */
    public void synchronizeReplicatorsFailed(Node leader) {
        if (replicatorRendezvous.get().cancel()) {
            state.compareAndSet(State.SYNCHRONIZING, State.UNSYNCHRONIZED);
        }
    }

    @Override
    public void transition(StateMachineDispatch type, Node sender,
                           Serializable payload, long time) {
        type.dispatch(this, sender, payload, time);
    }

    /* (non-Javadoc)
     * @see com.salesforce.ouroboros.spindle.orchestration.StateMachine#transition(com.salesforce.ouroboros.spindle.orchestration.ReplicatorSynchronization, com.salesforce.ouroboros.Node, java.io.Serializable, long)
     */
    @Override
    public void transition(ReplicatorSynchronization type, Node sender,
                           Serializable payload, long time) {
        type.dispatch(this, sender, payload, time);
    }

    public State getState() {
        return state.get();
    }

    public void partitionSynchronized(Node leader) {
        if (!state.compareAndSet(State.SYNCHRONIZING, State.SYNCHRONIZED)) {
            throw new IllegalStateException(
                                            String.format("Can only transition to SYNCHRONIZED from the SYNCHRONIZING state: %s",
                                                          state.get()));
        }
    }
}
