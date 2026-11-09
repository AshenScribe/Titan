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

import static org.mockito.Mockito.mock;

import com.github.benmanes.caffeine.cache.Cache;
import com.titan.product.orderservice.data.entity.OrderCacheEnvelope;
import java.time.Duration;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

class OrderCacheConfigTest {

    private final OrderCacheConfig config = new OrderCacheConfig();

    @Test
    void cacheUsesConfiguredMaximumSize() {
        OrderCacheProperties props = new OrderCacheProperties();
        props.setL1MaximumSize(500);

        Cache<String, OrderCacheEnvelope> cache = config.cache(props);

        Assertions.assertThat(cache.policy().eviction().orElseThrow().getMaximum()).isEqualTo(500);
    }

    @Test
    void cacheAppliesTtlWhenPositive() throws InterruptedException {
        OrderCacheProperties props = new OrderCacheProperties();
        props.setL1Ttl(Duration.ofMillis(50));
        props.setL1MaximumSize(100);

        Cache<String, OrderCacheEnvelope> cache = config.cache(props);
        String key = "k";
        cache.put(key, OrderCacheEnvelope.missing(java.util.UUID.randomUUID()));

        Thread.sleep(120);

        Assertions.assertThat(cache.getIfPresent(key)).isNull();
    }

    @Test
    void cacheSkipsTtlWhenZero() throws InterruptedException {
        OrderCacheProperties props = new OrderCacheProperties();
        props.setL1Ttl(Duration.ZERO);
        props.setL1MaximumSize(100);

        Cache<String, OrderCacheEnvelope> cache = config.cache(props);
        String key = "k";
        cache.put(key, OrderCacheEnvelope.missing(java.util.UUID.randomUUID()));

        Thread.sleep(20);

        Assertions.assertThat(cache.getIfPresent(key)).isNotNull();
    }

    @Test
    void cacheAppliesTtiWhenPositive() {
        OrderCacheProperties props = new OrderCacheProperties();
        props.setL1Tti(Duration.ofMillis(50));
        props.setL1MaximumSize(100);

        Cache<String, OrderCacheEnvelope> cache = config.cache(props);

        Assertions.assertThat(cache.policy().expireAfterAccess()).isPresent();
    }

    @Test
    void cacheSkipsTtiWhenNull() {
        OrderCacheProperties props = new OrderCacheProperties();
        props.setL1MaximumSize(100);

        Cache<String, OrderCacheEnvelope> cache = config.cache(props);

        Assertions.assertThat(cache.policy().expireAfterAccess()).isEmpty();
    }

    @Test
    void redisTemplateUsesStringKeysAndJsonValues() {
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);

        RedisTemplate<String, OrderCacheEnvelope> template = config.redisTemplate(factory);

        Assertions.assertThat(template.getKeySerializer())
                .isInstanceOf(StringRedisSerializer.class);
        Assertions.assertThat(template.getValueSerializer())
                .isInstanceOf(JacksonJsonRedisSerializer.class);
        Assertions.assertThat(template.getHashKeySerializer())
                .isInstanceOf(StringRedisSerializer.class);
        Assertions.assertThat(template.getHashValueSerializer())
                .isInstanceOf(JacksonJsonRedisSerializer.class);
        Assertions.assertThat(template.getConnectionFactory()).isSameAs(factory);
    }
}
