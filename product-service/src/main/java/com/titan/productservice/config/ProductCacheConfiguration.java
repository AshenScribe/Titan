package com.titan.productservice.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.titan.productservice.data.entity.ProductCacheEnvelope;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ProductCacheProperties.class)
public class ProductCacheConfiguration {
    @Bean
    public Cache<String, ProductCacheEnvelope> productL1Cache(ProductCacheProperties properties) {
        Caffeine<Object, Object> builder =
                Caffeine.newBuilder().maximumSize(properties.getL1MaximumSize()).recordStats();
        if (!properties.getL1Ttl().isZero() && !properties.getL1Ttl().isNegative()) {
            builder.expireAfterWrite(properties.getL1Ttl());
        }
        Duration tti = properties.getL1Tti();
        if (tti != null && !tti.isZero() && !tti.isNegative()) {
            builder.expireAfterAccess(tti);
        }
        return builder.build();
    }

    @Bean
    StringRedisTemplate productStringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    @Bean
    @ConditionalOnProperty(name = "titan.cache.l2-enabled", havingValue = "true")
    RedisMessageListenerContainer productCacheMessageListenerContainer(
            RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        return container;
    }
}
