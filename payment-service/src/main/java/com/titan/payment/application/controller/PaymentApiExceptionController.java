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

package com.titan.payment.application.controller;

import com.titan.payment.domain.exception.PaymentApiException;
import com.titan.payment.infrastructure.persistence.exception.PaymentDataException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class PaymentApiExceptionController {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentApiExceptionController.class);

    @ExceptionHandler(PaymentApiException.class)
    ResponseEntity<Map<String, String>> handle(PaymentApiException exception) {
        return ResponseEntity.status(exception.status())
                .body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(PaymentDataException.class)
    ResponseEntity<Map<String, String>> handleData(PaymentDataException exception) {
        PaymentApiException mapped = PaymentDataExceptionMapper.toApi(exception);
        if (mapped.status().value() >= 500) {
            LOG.error("Unmapped persistence failure: {}", exception.getMessage(), exception);
        } else {
            LOG.warn("Persistence failure mapped to {}: {}",
                    mapped.status(), exception.getMessage());
        }
        return ResponseEntity.status(mapped.status())
                .body(Map.of("error", mapped.getMessage()));
    }
}