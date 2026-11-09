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

package com.titan.productservice.presentation.controller;

import com.titan.productservice.data.exception.ProductDataException;
import com.titan.productservice.domain.exception.ConflictException;
import com.titan.productservice.domain.exception.ProductApiException;
import com.titan.productservice.domain.exception.ProductDataExceptionMapper;
import com.titan.productservice.domain.service.CategoryService;
import com.titan.productservice.domain.service.ProductService;
import com.titan.productservice.presentation.dto.ErrorResponse;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionController {

    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionController.class);

    @ExceptionHandler(ProductApiException.class)
    ResponseEntity<ErrorResponse> domainApi(ProductApiException exception) {
        return response(HttpStatus.valueOf(exception.status()), exception.getMessage(), List.of());
    }

    @ExceptionHandler(ProductDataException.class)
    ResponseEntity<ErrorResponse> dataException(ProductDataException exception) {
        ProductApiException mapped = ProductDataExceptionMapper.toApi(exception);
        if (mapped.status() >= 500) {
            LOG.error("Unmapped persistence failure: {}", exception.getMessage(), exception);
        } else {
            LOG.warn(
                    "Persistence failure mapped to {}: {}",
                    mapped.status(),
                    exception.getMessage());
        }
        return response(HttpStatus.valueOf(mapped.status()), mapped.getMessage(), List.of());
    }

    @ExceptionHandler(ProductService.ResourceNotFoundException.class)
    ResponseEntity<ErrorResponse> notFound(ProductService.ResourceNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage(), List.of());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ErrorResponse> optimisticConflict(OptimisticLockingFailureException exception) {
        return response(HttpStatus.PRECONDITION_FAILED, exception.getMessage(), List.of());
    }

    @ExceptionHandler(CategoryService.ConflictException.class)
    ResponseEntity<ErrorResponse> businessConflict(CategoryService.ConflictException exception) {
        return response(HttpStatus.CONFLICT, exception.getMessage(), List.of());
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ErrorResponse> domainConflict(ConflictException exception) {
        return response(HttpStatus.CONFLICT, exception.getMessage(), List.of());
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    ResponseEntity<ErrorResponse> invalidOperation(RuntimeException exception) {
        return response(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage(), List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException exception) {
        List<FieldError> fieldErrors = exception.getBindingResult().getFieldErrors();

        boolean typeMismatch =
                fieldErrors.stream()
                        .anyMatch(
                                error -> {
                                    try {
                                        return error.contains(
                                                        Class.forName(
                                                                org.springframework.validation
                                                                                .BindException
                                                                                .MODEL_KEY_PREFIX
                                                                        + ""))
                                                || java.util.Arrays.asList(error.getCodes())
                                                        .contains("typeMismatch")
                                                || (error.getCodes() != null
                                                        && java.util.Arrays.stream(error.getCodes())
                                                                .anyMatch(
                                                                        code ->
                                                                                code != null
                                                                                        && code
                                                                                                .startsWith(
                                                                                                        "typeMismatch")));
                                    } catch (ClassNotFoundException e) {
                                        throw new RuntimeException(e);
                                    }
                                });

        if (typeMismatch) {
            List<String> details =
                    fieldErrors.stream()
                            .filter(
                                    error ->
                                            error.getCodes() != null
                                                    && java.util.Arrays.stream(error.getCodes())
                                                            .anyMatch(
                                                                    code ->
                                                                            code != null
                                                                                    && code
                                                                                            .startsWith(
                                                                                                    "typeMismatch")))
                            .map(FieldError::getDefaultMessage)
                            .toList();
            return response(
                    HttpStatus.BAD_REQUEST, "Request parameter has an invalid format", details);
        }

        List<String> details =
                fieldErrors.stream()
                        .map(error -> error.getField() + ": " + error.getDefaultMessage())
                        .toList();
        return response(HttpStatus.BAD_REQUEST, "Request validation failed", details);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorResponse> typeMismatch(MethodArgumentTypeMismatchException exception) {
        return response(
                HttpStatus.BAD_REQUEST, "Request parameter has an invalid format", List.of());
    }

    private ResponseEntity<ErrorResponse> response(
            HttpStatus status, String message, List<String> details) {
        return ResponseEntity.status(status)
                .body(
                        new ErrorResponse(
                                OffsetDateTime.now(),
                                status.value(),
                                status.getReasonPhrase(),
                                message,
                                details));
    }
}
