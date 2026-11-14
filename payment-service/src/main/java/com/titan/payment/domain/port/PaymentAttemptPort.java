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

package com.titan.payment.domain.port;

import com.titan.payment.infrastructure.persistence.model.PaymentAttemptEntity;

import java.util.Optional;
import java.util.UUID;

/**
 * Port for the {@code payment_attempts} store.
 * <p>
 * <b>Layering note:</b> this port returns {@link PaymentAttemptEntity}, which
 * lives in {@code infrastructure.persistence.model}. Strictly speaking a
 * domain port should not depend on an infrastructure type; we accept the
 * coupling because {@code PaymentAttemptEntity} is an immutable record with no
 * persistence-library leakage (no {@code JdbcTemplate}, no {@code @Entity}),
 * and moving it to {@code domain.model} would be a mechanical refactor we've
 * deferred. Callers depend on this interface, not on the concrete
 * {@code PaymentAttemptRepository}, so the persistence implementation remains
 * swappable — which is the property the port was actually created for.
 */
public interface PaymentAttemptPort {
    Optional<PaymentAttemptEntity> findByOrderIdAndIdempotencyKey(UUID orderId, String key);
    void insert(PaymentAttemptEntity entity);
}