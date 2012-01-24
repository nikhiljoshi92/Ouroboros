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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.hellblazer.pinkie.SocketOptions;
import com.salesforce.ouroboros.util.LabeledThreadFactory;

/**
 * 
 * @author hhildebrand
 * 
 */
public class ProducerConfiguration {
    private double              maximumEventRate             = 2000.0;
    private int                 maxQueueLength               = 100;
    private double              minimumEventRate             = 500.0;
    private int                 minimumTokenRegenerationTime = 10;
    private int                 retryLimit                   = 10;
    private int                 sampleFrequency              = 10;
    private int                 sampleWindowSize             = 1000;
    private ExecutorService     spinners                     = Executors.newCachedThreadPool(new LabeledThreadFactory(
                                                                                                                      "Spinner"));
    private final SocketOptions spinnerSocketOptions         = new SocketOptions();
    private double              targetEventRate              = 1000.0;
    private int                 tokenLimit                   = 1000;

    /**
     * @return the maximumEventRate
     */
    public double getMaximumEventRate() {
        return maximumEventRate;
    }

    public int getMaxQueueLength() {
        return maxQueueLength;
    }

    /**
     * @return the minimumEventRate
     */
    public double getMinimumEventRate() {
        return minimumEventRate;
    }

    /**
     * @return the minimumTokenRegenerationTime
     */
    public int getMinimumTokenRegenerationTime() {
        return minimumTokenRegenerationTime;
    }

    public int getRetryLimit() {
        return retryLimit;
    }

    /**
     * @return the sampleFrequency
     */
    public int getSampleFrequency() {
        return sampleFrequency;
    }

    /**
     * @return the sampleWindowSize
     */
    public int getSampleWindowSize() {
        return sampleWindowSize;
    }

    /**
     * @return the spinners
     */
    public ExecutorService getSpinners() {
        return spinners;
    }

    /**
     * @return the spinnerSocketOptions
     */
    public SocketOptions getSpinnerSocketOptions() {
        return spinnerSocketOptions;
    }

    /**
     * @return the targetEventRate
     */
    public double getTargetEventRate() {
        return targetEventRate;
    }

    /**
     * @return the tokenLimit
     */
    public int getTokenLimit() {
        return tokenLimit;
    }

    /**
     * @param maximumEventRate
     *            the maximumEventRate to set
     */
    public void setMaximumEventRate(double maximumEventRate) {
        this.maximumEventRate = maximumEventRate;
    }

    public void setMaxQueueLength(int maxQueueLength) {
        this.maxQueueLength = maxQueueLength;
    }

    /**
     * @param minimumEventRate
     *            the minimumEventRate to set
     */
    public void setMinimumEventRate(double minimumEventRate) {
        this.minimumEventRate = minimumEventRate;
    }

    /**
     * @param minimumTokenRegenerationTime
     *            the minimumTokenRegenerationTime to set
     */
    public void setMinimumTokenRegenerationTime(int minimumTokenRegenerationTime) {
        this.minimumTokenRegenerationTime = minimumTokenRegenerationTime;
    }

    public void setRetryLimit(int resendLimit) {
        this.retryLimit = resendLimit;
    }

    /**
     * @param sampleFrequency
     *            the sampleFrequency to set
     */
    public void setSampleFrequency(int sampleFrequency) {
        this.sampleFrequency = sampleFrequency;
    }

    /**
     * @param sampleWindowSize
     *            the sampleWindowSize to set
     */
    public void setSampleWindowSize(int sampleWindowSize) {
        this.sampleWindowSize = sampleWindowSize;
    }

    /**
     * @param spinners
     *            the spinners to set
     */
    public void setSpinners(ExecutorService spinners) {
        this.spinners = spinners;
    }

    /**
     * @param targetEventRate
     *            the targetEventRate to set
     */
    public void setTargetEventRate(double targetEventRate) {
        this.targetEventRate = targetEventRate;
    }

    /**
     * @param tokenLimit
     *            the tokenLimit to set
     */
    public void setTokenLimit(int tokenLimit) {
        this.tokenLimit = tokenLimit;
    }
}
