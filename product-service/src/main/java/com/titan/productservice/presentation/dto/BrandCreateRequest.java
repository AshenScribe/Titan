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

@Schema(name = "BrandCreateRequest", description = "Fields used to create a brand.")
public record BrandCreateRequest(
        @Schema(description = "Brand display name.", example = "Titan Apparel")
                @NotBlank
                @Size(max = 150)
                String name,
        @Schema(description = "Lowercase URL slug.", example = "titan-apparel")
                @NotBlank
                @Size(max = 180)
                @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*")
                String slug,
        @Schema(description = "Brand description.", example = "Everyday clothing essentials.")
                String description,
        @Schema(description = "Brand website URL.", example = "https://example.com")
                String websiteUrl,
        @Schema(description = "Brand logo URL.", example = "https://cdn.example.com/logo.svg")
                String logoUrl,
        @Schema(description = "Additional JSON metadata.", example = "{\"country\":\"IN\"}")
                Map<String, Object> metadata) {}
