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

package com.titan.productservice.domain.service;

import com.titan.productservice.data.repository.ProductRepository;
import com.titan.productservice.data.repository.ProductRepository.ProductPage;
import com.titan.productservice.presentation.dto.PublicProductListQuery;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PostgresProductDiscovery implements ProductDiscovery {
    private final ProductRepository products;

    public PostgresProductDiscovery(ProductRepository products) {
        this.products = products;
    }

    @Override
    public ProductPage search(PublicProductListQuery query) {
        return products.findPublicPage(query, ProductRepository.DiscoveryMode.SEARCH);
    }

    @Override
    public ProductPage featured(PublicProductListQuery query) {
        return products.findPublicPage(query, ProductRepository.DiscoveryMode.FEATURED);
    }

    @Override
    public ProductPage newArrivals(PublicProductListQuery query) {
        return products.findPublicPage(query, ProductRepository.DiscoveryMode.NEW_ARRIVALS);
    }

    @Override
    public ProductPage byBrand(UUID brandId, PublicProductListQuery query) {
        query.setBrandId(brandId);
        return products.findPublicPage(query, ProductRepository.DiscoveryMode.SEARCH);
    }

    @Override
    public ProductPage byCategory(UUID categoryId, PublicProductListQuery query) {
        query.setCategoryId(categoryId);
        return products.findPublicPage(query, ProductRepository.DiscoveryMode.SEARCH);
    }
}
