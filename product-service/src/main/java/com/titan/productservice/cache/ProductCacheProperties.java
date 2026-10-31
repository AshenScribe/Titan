package com.titan.productservice.cache;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "titan.cache")
@Validated
public class ProductCacheProperties {
    private boolean l1Enabled = true;
    @Positive private long l1MaximumSize = 10_000;
    private Duration l1Ttl = Duration.ofSeconds(30);
    private Duration l1Tti;
    private boolean l2Enabled;
    private Duration l2Ttl = Duration.ofMinutes(10);
    private Duration negativeTtl = Duration.ofSeconds(10);
    private Duration ttlJitter = Duration.ofSeconds(30);
    private boolean invalidationEnabled = true;
    @NotBlank private String invalidationChannel = "titan:cache:invalidation:v1";
    @NotBlank private String namespace = "titan:product:v1";
    private boolean distributedLockEnabled;
    private Duration lockLease = Duration.ofSeconds(5);
    private Duration lockWait = Duration.ofMillis(250);

    @AssertTrue(message = "l1-ttl must be positive")
    public boolean isL1TtlValid() {
        return l1Ttl != null && l1Ttl.isPositive();
    }

    @AssertTrue(message = "l1-tti must be positive when configured")
    public boolean isL1TtiValid() {
        return l1Tti == null || l1Tti.isPositive();
    }

    @AssertTrue(message = "l2-ttl must be positive")
    public boolean isL2TtlValid() {
        return l2Ttl != null && l2Ttl.isPositive();
    }

    @AssertTrue(message = "negative-ttl must be positive")
    public boolean isNegativeTtlValid() {
        return negativeTtl != null && negativeTtl.isPositive();
    }

    @AssertTrue(message = "ttl-jitter must not be negative")
    public boolean isTtlJitterValid() {
        return ttlJitter != null && !ttlJitter.isNegative();
    }

    @AssertTrue(message = "lock-lease must be positive")
    public boolean isLockLeaseValid() {
        return lockLease != null && lockLease.isPositive();
    }

    @AssertTrue(message = "lock-wait must not be negative")
    public boolean isLockWaitValid() {
        return lockWait != null && !lockWait.isNegative();
    }

    public boolean isL1Enabled() {
        return l1Enabled;
    }

    public void setL1Enabled(boolean l1Enabled) {
        this.l1Enabled = l1Enabled;
    }

    public long getL1MaximumSize() {
        return l1MaximumSize;
    }

    public void setL1MaximumSize(long l1MaximumSize) {
        this.l1MaximumSize = l1MaximumSize;
    }

    public Duration getL1Ttl() {
        return l1Ttl;
    }

    public void setL1Ttl(Duration l1Ttl) {
        this.l1Ttl = l1Ttl;
    }

    public Duration getL1Tti() {
        return l1Tti;
    }

    public void setL1Tti(Duration l1Tti) {
        this.l1Tti = l1Tti;
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
