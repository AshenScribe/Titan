package com.titan.productservice.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "titan.cache.l2-enabled", havingValue = "true")
public class ProductCacheInvalidationListener implements MessageListener {
    private final ProductCacheService cache;
    private final ObjectMapper objectMapper;

    public ProductCacheInvalidationListener(
            @Qualifier("productCacheMessageListenerContainer")
                    RedisMessageListenerContainer container,
            ProductCacheService cache,
            ProductCacheProperties properties,
            ObjectMapper objectMapper) {
        this.cache = cache;
        this.objectMapper = objectMapper;
        container.addMessageListener(this, new ChannelTopic(properties.getInvalidationChannel()));
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            ProductChangedEvent event =
                    objectMapper.readValue(message.getBody(), ProductChangedEvent.class);
            cache.evictLocal(event);
        } catch (Exception exception) {
            cache.recordFailure();
        }
    }
}
