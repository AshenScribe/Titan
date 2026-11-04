package com.titan.productservice.config;
import com.titan.productservice.controller.*;
import com.titan.productservice.service.*;
import com.titan.productservice.dto.*;
import com.titan.productservice.exception.*;
import com.titan.productservice.config.*;
import com.titan.productservice.entity.*;
import com.titan.productservice.repository.*;
import com.titan.productservice.util.*;

import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ProductCachePropertiesValidationTests {
    @Test
    void rejectsInvalidCacheBoundsAndLockSettings() {
        ProductCacheProperties properties = new ProductCacheProperties();
        properties.setL1MaximumSize(0);
        properties.setL1Ttl(Duration.ZERO);
        properties.setL2Ttl(Duration.ZERO);
        properties.setNegativeTtl(Duration.ZERO);
        properties.setTtlJitter(Duration.ofSeconds(-1));
        properties.setLockLease(Duration.ZERO);
        properties.setLockWait(Duration.ofSeconds(-1));

        var violations =
                Validation.buildDefaultValidatorFactory().getValidator().validate(properties);

        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("l1-ttl")));
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("l2-ttl")));
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("negative-ttl")));
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("ttl-jitter")));
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("lock-lease")));
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("lock-wait")));
        assertTrue(
                violations.stream()
                        .anyMatch(v -> v.getPropertyPath().toString().equals("l1MaximumSize")));
    }

    @Test
    void acceptsPositiveAndNonNegativeCacheDurations() {
        ProductCacheProperties properties = new ProductCacheProperties();
        properties.setL1Tti(Duration.ofSeconds(1));
        properties.setTtlJitter(Duration.ZERO);
        properties.setLockWait(Duration.ZERO);

        assertTrue(properties.isL1TtlValid());
        assertTrue(properties.isL1TtiValid());
        assertTrue(properties.isL2TtlValid());
        assertTrue(properties.isNegativeTtlValid());
        assertTrue(properties.isTtlJitterValid());
        assertTrue(properties.isLockLeaseValid());
        assertTrue(properties.isLockWaitValid());
    }

    @Test
    void rejectsNullCacheDurations() {
        ProductCacheProperties properties = new ProductCacheProperties();
        properties.setL1Ttl(null);
        properties.setL1Tti(null);
        properties.setL2Ttl(null);
        properties.setNegativeTtl(null);
        properties.setTtlJitter(null);
        properties.setLockLease(null);
        properties.setLockWait(null);

        assertTrue(!properties.isL1TtlValid());
        assertTrue(properties.isL1TtiValid());
        assertTrue(!properties.isL2TtlValid());
        assertTrue(!properties.isNegativeTtlValid());
        assertTrue(!properties.isTtlJitterValid());
        assertTrue(!properties.isLockLeaseValid());
        assertTrue(!properties.isLockWaitValid());
    }
}
