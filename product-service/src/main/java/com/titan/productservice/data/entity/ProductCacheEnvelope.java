//------------------------------------------------------------------------------
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
//------------------------------------------------------------------------------

package com.titan.productservice.data.entity;

import java.time.Instant;
import java.util.UUID;

public record ProductCacheEnvelope(
        boolean found,
        ProductSnapshot product,
        UUID productId,
        String fenceKey,
        long version,
        long generation,
        int schemaVersion,
        Instant loadedAt) {
    public static ProductCacheEnvelope found(ProductSnapshot product) {
        return new ProductCacheEnvelope(
                true, product, product.id(), null, product.version(), 0, 1, Instant.now());
    }

    public static ProductCacheEnvelope missing(
            long version, UUID productId, String fenceKey, long generation) {
        return new ProductCacheEnvelope(
                false, null, productId, fenceKey, version, generation, 1, Instant.now());
    }

    public ProductCacheEnvelope withFenceKey(String key) {
        return new ProductCacheEnvelope(
                found, product, productId, key, version, generation, schemaVersion, loadedAt);
    }

    public ProductCacheEnvelope withGeneration(long value) {
        return new ProductCacheEnvelope(
                found, product, productId, fenceKey, version, value, schemaVersion, loadedAt);
    }
}
