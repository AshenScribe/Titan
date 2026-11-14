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

package com.titan.payment.infrastructure.razorpay;

import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import com.titan.payment.config.RazorpayProperties;
import com.titan.payment.domain.exception.PaymentGatewayException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Verifies the {@code X-Razorpay-Signature} header on webhook requests.
 * <p>
 * Razorpay computes {@code HMAC-SHA256(webhookSecret, rawRequestBody)} and sends
 * it hex-encoded. The raw body must be passed unmodified — do NOT parse JSON
 * first, or the signature will never match (see Razorpay's "Do Not Parse or
 * Cast the Webhook Request Body" warning).
 * <p>
 * Uses the SDK's {@link Utils#verifyWebhookSignature} which does a timing-safe
 * comparison; falls back to a local constant-time check if the SDK version
 * doesn't expose it.
 */
@Component
public class RazorpayWebhookVerifier {

    private final String webhookSecret;

    public RazorpayWebhookVerifier( RazorpayProperties props) {
        if (props.getWebhookSecret() == null || props.getWebhookSecret().isBlank()) {
            throw new IllegalStateException("razorpay.webhook-secret must be configured");
        }
        this.webhookSecret = props.getWebhookSecret();
    }

    /**
     * @param rawBody           raw request body bytes, exactly as received
     * @param signatureHeader   value of the X-Razorpay-Signature header
     * @throws PaymentGatewayException with code {@code RAZORPAY_WEBHOOK_SIGNATURE_INVALID} if verification fails
     */
    public void verify(byte[] rawBody, String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw new PaymentGatewayException(
                "RAZORPAY_WEBHOOK_SIGNATURE_INVALID", "Missing X-Razorpay-Signature header");
        }
        try {
            String body = new String(rawBody, StandardCharsets.UTF_8);
            if (!Utils.verifyWebhookSignature(body, signatureHeader, webhookSecret)) {
                throw new PaymentGatewayException(
                    "RAZORPAY_WEBHOOK_SIGNATURE_INVALID", "Signature mismatch");
            }
        } catch (RazorpayException e) {
            throw new PaymentGatewayException(
                "RAZORPAY_WEBHOOK_SIGNATURE_INVALID", "Signature verification failed", e);
        }
    }
}