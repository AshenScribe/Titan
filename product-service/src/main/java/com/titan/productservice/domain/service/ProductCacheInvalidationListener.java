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

package com.titan.productservice.domain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.titan.productservice.config.ProductCacheProperties;
import com.titan.productservice.data.entity.ProductChangedEvent;
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
