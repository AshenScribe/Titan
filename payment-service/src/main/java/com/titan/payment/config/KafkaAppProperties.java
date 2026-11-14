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

package com.titan.payment.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.kafka")
@Validated
public class KafkaAppProperties {

    @NotNull
    @Valid
    private Topics topics = new Topics();
    @NotNull
    @Valid
    private Consumer consumer = new Consumer();
    @NotNull
    @Valid
    private Retry retry = new Retry();

    public static class Topics {
        @NotBlank
        private String orderCreated = "order.created";
        @NotBlank
        private String paymentSuccess = "payment.success";
        @NotBlank
        private String paymentFailed = "payment.failed";
        @NotBlank
        private String refundCreated = "refund.created";
        @NotBlank
        private String refundProcessed = "refund.processed";
        @NotBlank
        private String refundFailed = "refund.failed";

        public String getOrderCreated() {
            return orderCreated;
        }

        public void setOrderCreated( String orderCreated ) {
            this.orderCreated = orderCreated;
        }

        public String getPaymentSuccess() {
            return paymentSuccess;
        }

        public void setPaymentSuccess( String paymentSuccess ) {
            this.paymentSuccess = paymentSuccess;
        }

        public String getPaymentFailed() {
            return paymentFailed;
        }

        public void setPaymentFailed( String paymentFailed ) {
            this.paymentFailed = paymentFailed;
        }

        public String getRefundCreated() {
            return refundCreated;
        }

        public void setRefundCreated( String refundCreated ) {
            this.refundCreated = refundCreated;
        }

        public String getRefundProcessed() {
            return refundProcessed;
        }

        public void setRefundProcessed( String refundProcessed ) {
            this.refundProcessed = refundProcessed;
        }

        public String getRefundFailed() {
            return refundFailed;
        }

        public void setRefundFailed( String refundFailed ) {
            this.refundFailed = refundFailed;
        }
    }

    public static class Consumer {
        @NotBlank
        private String groupId = "payment-service";

        public String getGroupId() {
            return groupId;
        }

        public void setGroupId( String groupId ) {
            this.groupId = groupId;
        }
    }

    public static class Retry {
        @Min(1)
        @Max(50)
        private int maxAttempts = 5;
        @Min(100)
        private long initialIntervalMs = 1000;
        @Min(1)
        private double multiplier = 2.0;
        @Min(1000)
        private long maxIntervalMs = 10000;

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public long getInitialIntervalMs() {
            return initialIntervalMs;
        }

        public void setInitialIntervalMs( long initialIntervalMs ) {
            this.initialIntervalMs = initialIntervalMs;
        }

        public double getMultiplier() {
            return multiplier;
        }

        public void setMultiplier( double multiplier ) {
            this.multiplier = multiplier;
        }

        public long getMaxIntervalMs() {
            return maxIntervalMs;
        }

        public void setMaxIntervalMs( long maxIntervalMs ) {
            this.maxIntervalMs = maxIntervalMs;
        }

        public void setMaxAttempts( int maxAttempts ) {
            this.maxAttempts = maxAttempts;
        }
    }

    public Topics getTopics() {
        return topics;
    }

    public void setTopics( Topics topics ) {
        this.topics = topics;
    }

    public Consumer getConsumer() {
        return consumer;
    }

    public void setConsumer( Consumer consumer ) {
        this.consumer = consumer;
    }

    public Retry getRetry() {
        return retry;
    }

    public void setRetry( Retry retry ) {
        this.retry = retry;
    }
}