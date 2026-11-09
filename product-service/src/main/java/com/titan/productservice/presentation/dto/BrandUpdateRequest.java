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
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

@Schema(name = "BrandUpdateRequest", description = "Partial brand update fields.")
public record BrandUpdateRequest(
        @Schema(description = "Replacement brand name.", example = "Titan Apparel")
                @Size(min = 1, max = 150)
                String name,
        @Schema(description = "Replacement lowercase URL slug.", example = "titan-apparel")
                @Size(min = 1, max = 180)
                @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*")
                String slug,
        @Schema(description = "Replacement description.", example = "Updated description.")
                String description,
        @Schema(description = "Replacement website URL.", example = "https://example.com")
                String websiteUrl,
        @Schema(description = "Replacement logo URL.", example = "https://cdn.example.com/logo.svg")
                String logoUrl,
        @Schema(description = "Replacement JSON metadata.", example = "{\"country\":\"IN\"}")
                Map<String, Object> metadata) {}
