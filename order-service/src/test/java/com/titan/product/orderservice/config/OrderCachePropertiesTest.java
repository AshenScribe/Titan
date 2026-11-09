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
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class OrderCachePropertiesTest {

    @Test
    void defaultsAreWhatTheCacheLayerExpects() {
        OrderCacheProperties properties = new OrderCacheProperties();

        Assertions.assertThat(properties.isL1Enabled()).isTrue();
        Assertions.assertThat(properties.getL1Ttl()).isEqualTo(Duration.ofSeconds(300));
        Assertions.assertThat(properties.getL1Tti()).isNull();
        Assertions.assertThat(properties.getL1MaximumSize()).isZero();

        Assertions.assertThat(properties.isL2Enabled()).isTrue();
        Assertions.assertThat(properties.getL2Ttl()).isEqualTo(Duration.ofSeconds(300));
        Assertions.assertThat(properties.getL2Tti()).isNull();

        Assertions.assertThat(properties.getNegativeTtl()).isEqualTo(Duration.ofSeconds(10));
        Assertions.assertThat(properties.getTtlJitter()).isEqualTo(Duration.ofSeconds(30));
        Assertions.assertThat(properties.isInvalidationEnabled()).isTrue();
        Assertions.assertThat(properties.getInvalidationChannel()).isNull();
        Assertions.assertThat(properties.getNamespace()).isNull();
        Assertions.assertThat(properties.isDistributedLockEnabled()).isFalse();
        Assertions.assertThat(properties.getLockLease()).isEqualTo(Duration.ofSeconds(5));
        Assertions.assertThat(properties.getLockWait()).isEqualTo(Duration.ofMillis(250));
    }

    @Test
    void settersRoundTrip() {
        OrderCacheProperties properties = new OrderCacheProperties();

        properties.setL1Enabled(false);
        properties.setL1Ttl(Duration.ofMinutes(1));
        properties.setL1Tti(Duration.ofSeconds(30));
        properties.setL1MaximumSize(1_000);
        properties.setL2Enabled(false);
        properties.setL2Ttl(Duration.ofMinutes(2));
        properties.setL2Tti(Duration.ofSeconds(45));
        properties.setNegativeTtl(Duration.ofSeconds(5));
        properties.setTtlJitter(Duration.ofSeconds(10));
        properties.setInvalidationEnabled(false);
        properties.setInvalidationChannel("orders.changed");
        properties.setNamespace("titan:test");
        properties.setDistributedLockEnabled(true);
        properties.setLockLease(Duration.ofSeconds(2));
        properties.setLockWait(Duration.ofMillis(100));

        Assertions.assertThat(properties.isL1Enabled()).isFalse();
        Assertions.assertThat(properties.getL1Ttl()).isEqualTo(Duration.ofMinutes(1));
        Assertions.assertThat(properties.getL1Tti()).isEqualTo(Duration.ofSeconds(30));
        Assertions.assertThat(properties.getL1MaximumSize()).isEqualTo(1_000);
        Assertions.assertThat(properties.isL2Enabled()).isFalse();
        Assertions.assertThat(properties.getL2Ttl()).isEqualTo(Duration.ofMinutes(2));
        Assertions.assertThat(properties.getL2Tti()).isEqualTo(Duration.ofSeconds(45));
        Assertions.assertThat(properties.getNegativeTtl()).isEqualTo(Duration.ofSeconds(5));
        Assertions.assertThat(properties.getTtlJitter()).isEqualTo(Duration.ofSeconds(10));
        Assertions.assertThat(properties.isInvalidationEnabled()).isFalse();
        Assertions.assertThat(properties.getInvalidationChannel()).isEqualTo("orders.changed");
        Assertions.assertThat(properties.getNamespace()).isEqualTo("titan:test");
        Assertions.assertThat(properties.isDistributedLockEnabled()).isTrue();
        Assertions.assertThat(properties.getLockLease()).isEqualTo(Duration.ofSeconds(2));
        Assertions.assertThat(properties.getLockWait()).isEqualTo(Duration.ofMillis(100));
    }
}
