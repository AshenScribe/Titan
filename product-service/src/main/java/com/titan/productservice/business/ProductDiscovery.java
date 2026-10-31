package com.titan.productservice.business;

import com.titan.productservice.data.repository.ProductRepository.ProductPage;
import com.titan.productservice.presentation.dto.PublicProductListQuery;
import java.util.UUID;

public interface ProductDiscovery {
    ProductPage search(PublicProductListQuery query);

    ProductPage featured(PublicProductListQuery query);

    ProductPage newArrivals(PublicProductListQuery query);

    ProductPage byBrand(UUID brandId, PublicProductListQuery query);

    ProductPage byCategory(UUID categoryId, PublicProductListQuery query);
}
