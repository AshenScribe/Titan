package com.titan.productservice.util;

import org.springframework.data.redis.core.script.DefaultRedisScript;

public final class ProductCacheScripts {

    public static final DefaultRedisScript<Long> VERSIONED_WRITE =
            new DefaultRedisScript<>(
                    """
            local fence = redis.call('GET', KEYS[2])
            local generation = redis.call('GET', KEYS[3]) or '0'
            if fence and tonumber(fence) > tonumber(ARGV[3]) then
                return 0
            end
            if tonumber(generation) ~= tonumber(ARGV[4]) then
                return 0
            end
            redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2])
            redis.call('SET', KEYS[2], ARGV[3], 'PX', ARGV[2])
            return 1
            """,
                    Long.class);

    public static final DefaultRedisScript<Long> INVALIDATE =
            new DefaultRedisScript<>(
                    """
            local version = tonumber(ARGV[1])
            local ttl = ARGV[2]
            for i = 1, #KEYS, 3 do
                local valueKey = KEYS[i]
                local fenceKey = KEYS[i + 1]
                local generationKey = KEYS[i + 2]
                local fence = redis.call('GET', fenceKey)
                if not fence or version >= tonumber(fence) then
                    redis.call('INCR', generationKey)
                    redis.call('SET', fenceKey, ARGV[1], 'PX', ttl)
                    redis.call('DEL', valueKey)
                end
            end
            return 1
            """,
                    Long.class);

    private ProductCacheScripts() {}
}
