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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.titan.product.orderservice.data.entity.OrderCacheEnvelope;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OrderCacheProperties.class)
public class OrderCacheConfig {

    @Bean
    @ConditionalOnBooleanProperty(prefix = "titan.cache", name = "l1-enabled")
    Cache<String, OrderCacheEnvelope> cache(OrderCacheProperties properties) {
        Caffeine<Object, Object> caffeine =
                Caffeine.newBuilder().maximumSize(properties.getL1MaximumSize());
        if (properties.getL1Ttl() != null && properties.getL1Ttl().isPositive())
            caffeine.expireAfterWrite(properties.getL1Ttl());
        if (properties.getL1Tti() != null && properties.getL1Tti().isPositive())
            caffeine.expireAfterAccess(properties.getL1Tti());
        return caffeine.build();
    }

    @Bean
    @ConditionalOnBooleanProperty(prefix = "titan.cache", name = "l2-enabled")
    RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();

        container.setConnectionFactory(connectionFactory);
        return container;
    }

    @Bean
    @ConditionalOnBooleanProperty(prefix = "titan.cache", name = "l2-enabled")
    RedisTemplate<String, OrderCacheEnvelope> redisTemplate(
            RedisConnectionFactory connectionFactory) {

        var mapper = JsonMapper.builder().build();

        RedisSerializer<OrderCacheEnvelope> serializer =
                new JacksonJsonRedisSerializer<>(mapper, OrderCacheEnvelope.class);

        RedisTemplate<String, OrderCacheEnvelope> template = new RedisTemplate<>();

        template.setConnectionFactory(connectionFactory);

        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);

        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);

        template.afterPropertiesSet();

        return template;
    }
}
