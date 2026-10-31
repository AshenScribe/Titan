package com.titan.productservice.cache;

import java.util.UUID;

record ProductCacheLookup(String key, UUID productId, String fenceKey) {}
