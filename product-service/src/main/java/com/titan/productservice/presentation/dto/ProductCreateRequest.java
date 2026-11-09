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

package com.titan.productservice.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;

@Schema(name = "ProductCreateRequest", description = "Fields used to create a product draft.")
public record ProductCreateRequest(
        @Schema(
                        description = "Optional brand identifier.",
                        example = "7f8c2c1e-5f8a-4e8d-9f4e-9b6b6a8b7c10")
                UUID brandId,
        @Schema(description = "Customer-facing product name.", example = "Classic Oxford Shirt")
                @NotBlank
                @Size(max = 255)
                String name,
        @Schema(description = "Lowercase URL slug.", example = "classic-oxford-shirt")
                @NotBlank
                @Size(max = 280)
                @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*")
                String slug,
        @Schema(description = "Optional product description.", example = "A durable cotton shirt.")
                @Size(max = 10000)
                String description,
        @Schema(description = "Additional JSON metadata.", example = "{\"season\":\"summer\"}")
                Map<String, Object> metadata) {}
