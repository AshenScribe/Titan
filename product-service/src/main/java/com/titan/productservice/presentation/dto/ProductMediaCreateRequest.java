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

package com.titan.productservice.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "ProductMediaCreateRequest", description = "Media metadata stored for a product.")
public record ProductMediaCreateRequest(
        @Schema(description = "Media type.", example = "IMAGE")
                @NotBlank
                @Pattern(regexp = "IMAGE|VIDEO")
                String mediaType,
        @Schema(
                        description = "Object storage key; binary upload is handled elsewhere.",
                        example = "products/7f8c/image-1.webp")
                @NotBlank
                @Size(max = 2000)
                String storageKey,
        @Schema(description = "Accessible alternative text.", example = "Blue Oxford shirt")
                @Size(max = 500)
                String altText,
        @Schema(description = "Zero-based display position.", example = "0") @Min(0) int position,
        @Schema(description = "Whether this is the primary media item.", example = "true")
                boolean primary) {}
