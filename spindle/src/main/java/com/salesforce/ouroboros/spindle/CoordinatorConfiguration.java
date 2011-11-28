package com.salesforce.ouroboros.spindle;

import static com.salesforce.ouroboros.spindle.WeaverConfigation.threadFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import com.hellblazer.pinkie.SocketOptions;

public class CoordinatorConfiguration {

    private InetSocketAddress   xeroxAddress       = new InetSocketAddress(
                                                                           "127.0.0.1",
                                                                           0);
    private Executor            xeroxes            = Executors.newSingleThreadExecutor(threadFactory("xerox"));
    private final SocketOptions xeroxSocketOptions = new SocketOptions();

    /**
     * @return the xeroxAddress
     */
    public InetSocketAddress getXeroxAddress() {
        return xeroxAddress;
    }

    /**
     * @return the xeroxes
     */
    public Executor getXeroxes() {
        return xeroxes;
    }

    /**
     * @return the xeroxSocketOptions
     */
    public SocketOptions getXeroxSocketOptions() {
        return xeroxSocketOptions;
    }

    /**
     * @param xeroxAddress
     *            the xeroxAddress to set
     */
    public void setXeroxAddress(InetSocketAddress xeroxAddress) {
        this.xeroxAddress = xeroxAddress;
    }

    /**
     * @param xeroxes
     *            the xeroxes to set
     */
    public void setXeroxes(Executor xeroxes) {
        this.xeroxes = xeroxes;
    }
}