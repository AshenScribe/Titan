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

package com.titan.product.orderservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.titan.product.orderservice.data.exception.OrderCheckConstraintViolationException;
import com.titan.product.orderservice.data.exception.OrderDataException;
import com.titan.product.orderservice.domain.service.OrderService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OrderController.class)
@Import(OrderApiExceptionController.class)
class OrderApiExceptionControllerTest {

    @Autowired private MockMvc mvc;
    @MockitoBean private OrderService service;

    @Test
    void dataExceptionBecomes422WithSanitizedMessage() throws Exception {
        when(service.create(any(), any()))
                .thenThrow(new OrderCheckConstraintViolationException(mock(PSQLException.class)));

        mvc.perform(
                        post("/api/v1/orders")
                                .principal(() -> UUID.randomUUID().toString())
                                .contentType(APPLICATION_JSON)
                                .content(
                                        """
                                {"items":[{"variantId":"%s","quantity":1}],
                                 "shippingAddress":{"line1":"x"}}
                                """
                                                .formatted(UUID.randomUUID())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("Order violates a business constraint"))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist());
    }

    @Test
    void unexpectedDataExceptionBecomes500WithGenericMessage() throws Exception {
        when(service.create(any(), any()))
                .thenThrow(new OrderDataException("boom", new RuntimeException()));

        mvc.perform(
                        post("/api/v1/orders")
                                .principal(() -> UUID.randomUUID().toString())
                                .contentType(APPLICATION_JSON)
                                .content(
                                        """
                                {"items":[{"variantId":"%s","quantity":1}],
                                 "shippingAddress":{"line1":"x"}}
                                """
                                                .formatted(UUID.randomUUID())))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Internal server error"));
    }
}
