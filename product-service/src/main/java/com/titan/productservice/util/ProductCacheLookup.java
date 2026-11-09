package com.titan.productservice.util;

import java.util.UUID;

public record ProductCacheLookup(String key, UUID productId, String fenceKey) {}
