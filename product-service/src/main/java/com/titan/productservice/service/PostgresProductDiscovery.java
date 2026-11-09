package com.titan.productservice.service;

import com.titan.productservice.presentation.dto.PublicProductListQuery;
import com.titan.productservice.data.repository.ProductRepository;
import com.titan.productservice.data.repository.ProductRepository.ProductPage;
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
