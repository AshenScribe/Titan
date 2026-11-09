package com.titan.productservice.service;

import com.titan.productservice.presentation.dto.BrandCreateRequest;
import com.titan.productservice.presentation.dto.BrandListQuery;
import com.titan.productservice.presentation.dto.BrandResponse;
import com.titan.productservice.presentation.dto.BrandUpdateRequest;
import com.titan.productservice.presentation.dto.PageResponse;
import com.titan.productservice.data.entity.BrandEntity;
import com.titan.productservice.data.repository.BrandRepository;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BrandService {
    private final BrandRepository brands;

    public BrandService(BrandRepository brands) {
        this.brands = brands;
    }

    @Transactional
    public BrandEntity create(BrandCreateRequest request) {
        BrandEntity brand = new BrandEntity();
        brand.setId(UUID.randomUUID());
        brand.setName(request.name().trim());
        brand.setSlug(request.slug());
        brand.setDescription(request.description());
        brand.setWebsiteUrl(request.websiteUrl());
        brand.setLogoUrl(request.logoUrl());
        brand.setMetadata(request.metadata() == null ? Map.of() : request.metadata());
        brand.setActive(true);
        brands.insert(brand);
        return get(brand.getId());
    }

    @Transactional
    public BrandEntity update(UUID id, BrandUpdateRequest request) {
        BrandEntity brand = get(id);
        if (request.name() != null) {
            brand.setName(request.name().trim());
        }
        if (request.slug() != null) {
            brand.setSlug(request.slug());
        }
        if (request.description() != null) {
            brand.setDescription(request.description());
        }
        if (request.websiteUrl() != null) {
            brand.setWebsiteUrl(request.websiteUrl());
        }
        if (request.logoUrl() != null) {
            brand.setLogoUrl(request.logoUrl());
        }
        if (request.metadata() != null) {
            brand.setMetadata(request.metadata());
        }
        brands.update(brand);
        return get(id);
    }

    @Transactional
    public BrandEntity setStatus(UUID id, boolean active) {
        BrandEntity brand = get(id);
        brand.setActive(active);
        brands.update(brand);
        return get(id);
    }

    @Transactional
    public void delete(UUID id) {
        if (brands.deleteById(id) == 0) {
            throw new ProductService.ResourceNotFoundException("Brand not found: " + id);
        }
    }

    public BrandEntity get(UUID id) {
        return brands.findById(id)
                .orElseThrow(
                        () ->
                                new ProductService.ResourceNotFoundException(
                                        "Brand not found: " + id));
    }

    public PageResponse<BrandResponse> list(BrandListQuery query) {
        BrandRepository.BrandPage result = brands.findPage(query);
        return PageResponse.of(
                result.brands().stream().map(BrandResponse::from).toList(),
                query.getPage(),
                query.getSize(),
                result.totalElements());
    }
}
