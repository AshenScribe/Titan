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

package com.titan.product.orderservice.domain.exception;

import com.titan.product.orderservice.data.exception.DuplicateOrderException;
import com.titan.product.orderservice.data.exception.DuplicateOrderItemException;
import com.titan.product.orderservice.data.exception.OrderCheckConstraintViolationException;
import com.titan.product.orderservice.data.exception.OrderDataException;
import com.titan.product.orderservice.data.exception.OrderForeignKeyViolationException;
import com.titan.product.orderservice.data.exception.OrderInvalidEnumValueException;
import com.titan.product.orderservice.data.exception.OrderNotNullViolationException;
import com.titan.product.orderservice.data.exception.OrderStringTruncationException;

public final class OrderDataExceptionMapper {

    private OrderDataExceptionMapper() {}

    public static OrderApiException toApi(OrderDataException exception) {
        return switch (exception) {
            case DuplicateOrderException e -> new ConflictException("Order already exists");

            case DuplicateOrderItemException e -> new ConflictException("Duplicate order item");

            case OrderForeignKeyViolationException e ->
                    new ConflictException("Referenced order or resource does not exist");

            case OrderCheckConstraintViolationException e ->
                    new OrderApiException(422, "Order violates a business constraint");

            case OrderNotNullViolationException e ->
                    new OrderApiException(400, "A required field is missing");

            case OrderStringTruncationException e ->
                    new OrderApiException(400, "A field value is too long");

            case OrderInvalidEnumValueException e ->
                    new OrderApiException(400, "An enum value is invalid");

            case OrderDataException e -> new OrderApiException(500, "Internal server error");
        };
    }
}
