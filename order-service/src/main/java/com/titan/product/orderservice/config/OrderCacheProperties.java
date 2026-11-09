// ------------------------------------------------------------------------------
// MIT License
//
// Copyright (c) 2026 Rishabh Saraswat
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.
//
// ------------------------------------------------------------------------------

package com.titan.product.orderservice.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "titan.cache")
@Validated
public class OrderCacheProperties {

    private boolean l1Enabled = true;
    private Duration l1Ttl = Duration.ofSeconds(300);
    private Duration l1Tti;
    private int l1MaximumSize;

    private boolean l2Enabled = true;
    private Duration l2Ttl = Duration.ofSeconds(300);
    private Duration l2Tti;

    private Duration negativeTtl = Duration.ofSeconds(10);
    private Duration ttlJitter = Duration.ofSeconds(30);
    private boolean invalidationEnabled = true;
    private String invalidationChannel;
    private String namespace;
    private boolean distributedLockEnabled;
    private Duration lockLease = Duration.ofSeconds(5);
    private Duration lockWait = Duration.ofMillis(250);

    public Duration getL1Tti() {
        return l1Tti;
    }

    public void setL1Tti(Duration l1Tti) {
        this.l1Tti = l1Tti;
    }

    public Duration getL1Ttl() {
        return l1Ttl;
    }

    public void setL1Ttl(Duration l1Ttl) {
        this.l1Ttl = l1Ttl;
    }

    public boolean isL1Enabled() {
        return l1Enabled;
    }

    public void setL1Enabled(boolean l1Enabled) {
        this.l1Enabled = l1Enabled;
    }

    public int getL1MaximumSize() {
        return l1MaximumSize;
    }

    public void setL1MaximumSize(int l1MaximumSize) {
        this.l1MaximumSize = l1MaximumSize;
    }

    public boolean isL2Enabled() {
        return l2Enabled;
    }

    public void setL2Enabled(boolean l2Enabled) {
        this.l2Enabled = l2Enabled;
    }

    public Duration getL2Ttl() {
        return l2Ttl;
    }

    public void setL2Ttl(Duration l2Ttl) {
        this.l2Ttl = l2Ttl;
    }

    public Duration getL2Tti() {
        return l2Tti;
    }

    public void setL2Tti(Duration l2Tti) {
        this.l2Tti = l2Tti;
    }

    public Duration getNegativeTtl() {
        return negativeTtl;
    }

    public void setNegativeTtl(Duration negativeTtl) {
        this.negativeTtl = negativeTtl;
    }

    public Duration getTtlJitter() {
        return ttlJitter;
    }

    public void setTtlJitter(Duration ttlJitter) {
        this.ttlJitter = ttlJitter;
    }

    public boolean isInvalidationEnabled() {
        return invalidationEnabled;
    }

    public void setInvalidationEnabled(boolean invalidationEnabled) {
        this.invalidationEnabled = invalidationEnabled;
    }

    public String getInvalidationChannel() {
        return invalidationChannel;
    }

    public void setInvalidationChannel(String invalidationChannel) {
        this.invalidationChannel = invalidationChannel;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public boolean isDistributedLockEnabled() {
        return distributedLockEnabled;
    }

    public void setDistributedLockEnabled(boolean distributedLockEnabled) {
        this.distributedLockEnabled = distributedLockEnabled;
    }

    public Duration getLockLease() {
        return lockLease;
    }

    public void setLockLease(Duration lockLease) {
        this.lockLease = lockLease;
    }

    public Duration getLockWait() {
        return lockWait;
    }

    public void setLockWait(Duration lockWait) {
        this.lockWait = lockWait;
    }
}
