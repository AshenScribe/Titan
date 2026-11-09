package com.titan.productservice.service;

import com.titan.productservice.data.entity.CategoryEntity;
import com.titan.productservice.presentation.dto.BrandResponse;
import com.titan.productservice.presentation.dto.CategoryResponse;
import com.titan.productservice.presentation.dto.PageResponse;
import com.titan.productservice.presentation.dto.ProductCategoriesReplaceRequest;
import com.titan.productservice.presentation.dto.ProductCategoryAssignRequest;
import com.titan.productservice.presentation.dto.ProductCategoryResponse;
import com.titan.productservice.presentation.dto.ProductCreateRequest;
import com.titan.productservice.presentation.dto.ProductListQuery;
import com.titan.productservice.presentation.dto.ProductMediaCreateRequest;
import com.titan.productservice.presentation.dto.ProductMediaReorderRequest;
import com.titan.productservice.presentation.dto.ProductMediaUpdateRequest;
import com.titan.productservice.presentation.dto.ProductResponse;
import com.titan.productservice.presentation.dto.ProductUpdateRequest;
import com.titan.productservice.presentation.dto.ProductVariantCreateRequest;
import com.titan.productservice.presentation.dto.ProductVariantUpdateRequest;
import com.titan.productservice.presentation.dto.PublicMediaResponse;
import com.titan.productservice.presentation.dto.PublicProductListQuery;
import com.titan.productservice.presentation.dto.PublicProductResponse;
import com.titan.productservice.presentation.dto.PublicVariantResponse;
import com.titan.productservice.data.entity.ProductCategoryEntity;
import com.titan.productservice.data.entity.ProductEntity;
import com.titan.productservice.data.entity.ProductMediaEntity;
import com.titan.productservice.data.entity.ProductVariantEntity;
import com.titan.productservice.data.repository.BrandRepository;
import com.titan.productservice.data.repository.CategoryRepository;
import com.titan.productservice.data.repository.ProductCategoryRepository;
import com.titan.productservice.data.repository.ProductMediaRepository;
import com.titan.productservice.data.repository.ProductRepository;
import com.titan.productservice.data.repository.ProductVariantRepository;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {
    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final BrandRepository brands;
    private final CategoryRepository categories;
    private final ProductCategoryRepository productCategories;
    private final ProductMediaRepository media;
    private final ProductDiscovery discovery;

    public ProductService(
            ProductRepository products,
            ProductVariantRepository variants,
            BrandRepository brands,
            CategoryRepository categories,
            ProductCategoryRepository productCategories,
            ProductMediaRepository media,
            ProductDiscovery discovery) {
        this.products = products;
        this.variants = variants;
        this.brands = brands;
        this.categories = categories;
        this.productCategories = productCategories;
        this.media = media;
        this.discovery = discovery;
    }

    @Transactional
    public ProductEntity create(ProductCreateRequest request) {
        ProductEntity product = new ProductEntity();
        product.setId(UUID.randomUUID());
        product.setBrandId(request.brandId());
        product.setName(request.name().trim());
        product.setSlug(request.slug());
        product.setDescription(request.description());
        product.setMetadata(request.metadata() == null ? Map.of() : request.metadata());
        product.setStatus("DRAFT");
        product.setVersion(0);
        products.insert(product);
        return products.findById(product.getId()).orElseThrow();
    }

    @Transactional
    public ProductEntity update(UUID id, ProductUpdateRequest request, long expectedVersion) {
        ProductEntity current = getIncludingDeleted(id);
        requireVersion(current, expectedVersion);
        if (current.getDeletedAt() != null) {
            throw new IllegalStateException("Deleted products cannot be updated");
        }
        if (request.brandId() != null) {
            current.setBrandId(request.brandId());
        }
        if (request.name() != null) {
            current.setName(request.name().trim());
        }
        if (request.slug() != null) {
            current.setSlug(request.slug());
        }
        if (request.description() != null) {
            current.setDescription(request.description());
        }
        if (request.metadata() != null) {
            current.setMetadata(request.metadata());
        }
        products.update(current);
        return products.findById(id).orElseThrow();
    }

    @Transactional
    public ProductEntity transition(UUID id, String targetStatus, long expectedVersion) {
        ProductEntity current = getIncludingDeleted(id);
        requireVersion(current, expectedVersion);
        if (current.getDeletedAt() != null) {
            throw new IllegalStateException("Deleted products cannot change state");
        }
        if ("ACTIVE".equals(targetStatus)) {
            current.setPublishedAt(
                    current.getPublishedAt() == null
                            ? OffsetDateTime.now()
                            : current.getPublishedAt());
        } else if ("DRAFT".equals(targetStatus)) {
            current.setPublishedAt(null);
        }
        current.setStatus(targetStatus);
        products.update(current);
        return products.findById(id).orElseThrow();
    }

    @Transactional
    public ProductEntity softDelete(UUID id, long expectedVersion) {
        ProductEntity current = getIncludingDeleted(id);
        requireVersion(current, expectedVersion);
        if (products.softDeleteById(id, expectedVersion).isEmpty()) {
            throw new OptimisticLockingFailureException(
                    "Product was changed or does not exist: " + id);
        }
        return products.findById(id).orElseThrow();
    }

    public ProductEntity getIncludingDeleted(UUID id) {
        return products.findIncludingDeletedById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
    }

    public ProductEntity getPublic(UUID id) {
        return products.findActiveById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
    }

    public ProductEntity getPublicBySlug(String slug) {
        ProductEntity product =
                products.findBySlug(slug)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Product not found: " + slug));
        if (!"ACTIVE".equals(product.getStatus())) {
            throw new ResourceNotFoundException("Product not found: " + slug);
        }
        return product;
    }

    public PageResponse<ProductResponse> list(ProductListQuery query) {
        ProductRepository.ProductFilter filter =
                new ProductRepository.ProductFilter(
                        query.getPage(),
                        query.getSize(),
                        query.getStatus(),
                        query.getBrandId(),
                        query.getCategoryId(),
                        query.getDeleted(),
                        query.getSort(),
                        query.getCreatedFrom(),
                        query.getCreatedTo(),
                        query.getUpdatedFrom(),
                        query.getUpdatedTo(),
                        query.getName(),
                        query.getSlug());
        ProductRepository.ProductPage result = products.findPage(filter);
        return PageResponse.of(
                result.products().stream().map(ProductResponse::from).toList(),
                filter.page(),
                filter.size(),
                result.totalElements());
    }

    public PageResponse<PublicProductResponse> publicList(PublicProductListQuery query) {
        ProductRepository.ProductPage result = discovery.search(query);
        return PageResponse.of(
                result.products().stream().map(this::publicResponse).toList(),
                query.getPage(),
                query.getSize(),
                result.totalElements());
    }

    public PageResponse<PublicProductResponse> publicSearch(PublicProductListQuery query) {
        return publicPage(discovery.search(query), query);
    }

    public PageResponse<PublicProductResponse> featured(PublicProductListQuery query) {
        return publicPage(discovery.featured(query), query);
    }

    public PageResponse<PublicProductResponse> newArrivals(PublicProductListQuery query) {
        return publicPage(discovery.newArrivals(query), query);
    }

    public PageResponse<PublicProductResponse> byBrand(UUID brandId, PublicProductListQuery query) {
        return publicPage(discovery.byBrand(brandId, query), query);
    }

    public PageResponse<PublicProductResponse> byCategory(
            UUID categoryId, PublicProductListQuery query) {
        return publicPage(discovery.byCategory(categoryId, query), query);
    }

    private PageResponse<PublicProductResponse> publicPage(
            ProductRepository.ProductPage result, PublicProductListQuery query) {
        return PageResponse.of(
                result.products().stream().map(this::publicResponse).toList(),
                query.getPage(),
                query.getSize(),
                result.totalElements());
    }

    public PublicProductResponse publicGet(UUID id) {
        return publicResponse(getPublic(id));
    }

    public PublicProductResponse publicGetBySlug(String slug) {
        return publicResponse(getPublicBySlug(slug));
    }

    public List<PublicVariantResponse> publicVariants(UUID productId) {
        getPublic(productId);
        return variants.findActiveByProductId(productId).stream()
                .map(PublicVariantResponse::from)
                .toList();
    }

    public PublicVariantResponse publicVariant(UUID productId, UUID variantId) {
        getPublic(productId);
        ProductVariantEntity variant =
                variants.findByProductIdAndId(productId, variantId)
                        .filter(value -> "ACTIVE".equals(value.getStatus()))
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Active variant not found: " + variantId));
        return PublicVariantResponse.from(variant);
    }

    public ProductVariantEntity publicVariantById(UUID variantId) {
        ProductVariantEntity variant =
                variants.findById(variantId)
                        .filter(value -> "ACTIVE".equals(value.getStatus()))
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Active variant not found: " + variantId));
        getPublic(variant.getProductId());
        return variant;
    }

    @Transactional
    public ProductVariantEntity createVariant(
            UUID productId, ProductVariantCreateRequest request, long expectedProductVersion) {
        ProductEntity product = requireMutableProduct(productId);
        requireVersion(product, expectedProductVersion);
        validateVariant(request.price(), request.compareAtPrice(), request.attributes());
        ProductVariantEntity variant = new ProductVariantEntity();
        variant.setId(UUID.randomUUID());
        variant.setProductId(productId);
        variant.setSku(request.sku().trim());
        variant.setBarcode(blankToNull(request.barcode()));
        variant.setName(request.name());
        variant.setPrice(request.price());
        variant.setCompareAtPrice(request.compareAtPrice());
        variant.setCurrency(request.currency());
        variant.setAttributes(request.attributes() == null ? Map.of() : request.attributes());
        variant.setStatus("ACTIVE");
        variants.insert(variant, expectedProductVersion);
        return variants.findById(variant.getId()).orElseThrow();
    }

    public List<ProductVariantEntity> listVariants(UUID productId) {
        requireMutableProduct(productId);
        return variants.findByProductId(productId);
    }

    public ProductVariantEntity getVariant(UUID productId, UUID variantId) {
        requireMutableProduct(productId);
        return variants.findByProductIdAndId(productId, variantId)
                .orElseThrow(
                        () ->
                                new ResourceNotFoundException(
                                        "Variant not found for product: " + variantId));
    }

    @Transactional
    public ProductVariantEntity updateVariant(
            UUID productId,
            UUID variantId,
            ProductVariantUpdateRequest request,
            long expectedProductVersion) {
        ProductEntity product = requireMutableProduct(productId);
        requireVersion(product, expectedProductVersion);
        ProductVariantEntity variant = getVariant(productId, variantId);
        validateUpdatedVariant(variant, request);
        applyVariantUpdate(variant, request);
        variants.update(variant, expectedProductVersion);
        return variants.findById(variantId).orElseThrow();
    }

    private void validateUpdatedVariant(
            ProductVariantEntity variant, ProductVariantUpdateRequest request) {
        if (request.price() != null
                || request.compareAtPrice() != null
                || request.attributes() != null) {
            validateVariant(
                    request.price() == null ? variant.getPrice() : request.price(),
                    request.compareAtPrice() == null
                            ? variant.getCompareAtPrice()
                            : request.compareAtPrice(),
                    request.attributes() == null ? variant.getAttributes() : request.attributes());
        }
    }

    private void applyVariantUpdate(
            ProductVariantEntity variant, ProductVariantUpdateRequest request) {
        if (request.sku() != null) {
            variant.setSku(request.sku().trim());
        }
        if (request.barcode() != null) {
            variant.setBarcode(blankToNull(request.barcode()));
        }
        if (request.name() != null) {
            variant.setName(request.name());
        }
        if (request.price() != null) {
            variant.setPrice(request.price());
        }
        if (request.compareAtPrice() != null) {
            variant.setCompareAtPrice(request.compareAtPrice());
        }
        if (request.currency() != null) {
            variant.setCurrency(request.currency());
        }
        if (request.attributes() != null) {
            variant.setAttributes(request.attributes());
        }
    }

    @Transactional
    public ProductVariantEntity setVariantStatus(
            UUID productId, UUID variantId, boolean active, long expectedProductVersion) {
        ProductEntity product = requireMutableProduct(productId);
        requireVersion(product, expectedProductVersion);
        ProductVariantEntity variant = getVariant(productId, variantId);
        if (!active
                && "ACTIVE".equals(product.getStatus())
                && "ACTIVE".equals(variant.getStatus())
                && variants.findActiveByProductId(productId).size() == 1) {
            throw new IllegalStateException(
                    "An active product must retain at least one active variant");
        }
        variant.setStatus(active ? "ACTIVE" : "INACTIVE");
        variants.update(variant, expectedProductVersion);
        return variants.findById(variantId).orElseThrow();
    }

    @Transactional
    public void deleteVariant(UUID productId, UUID variantId, long expectedProductVersion) {
        setVariantStatus(productId, variantId, false, expectedProductVersion);
    }

    @Transactional
    public ProductMediaEntity createMedia(
            UUID productId, ProductMediaCreateRequest request, long expectedProductVersion) {
        requireVersion(requireMutableProduct(productId), expectedProductVersion);
        ProductMediaEntity mediaItem = new ProductMediaEntity();
        mediaItem.setId(UUID.randomUUID());
        mediaItem.setProductId(productId);
        mediaItem.setMediaType(request.mediaType());
        mediaItem.setStorageKey(request.storageKey().trim());
        mediaItem.setAltText(request.altText());
        mediaItem.setPosition(request.position());
        mediaItem.setPrimary(request.primary());
        if (mediaItem.isPrimary()) {
            media.insertReplacingPrimary(mediaItem, expectedProductVersion);
        } else {
            media.insert(mediaItem, expectedProductVersion);
        }
        return media.findById(mediaItem.getId()).orElseThrow();
    }

    public List<ProductMediaEntity> listMedia(UUID productId) {
        requireMutableProduct(productId);
        return media.findByProductId(productId);
    }

    public ProductMediaEntity getMedia(UUID productId, UUID mediaId) {
        requireMutableProduct(productId);
        return media.findById(mediaId)
                .filter(value -> productId.equals(value.getProductId()))
                .orElseThrow(
                        () ->
                                new ResourceNotFoundException(
                                        "Media not found for product: " + mediaId));
    }

    @Transactional
    public ProductMediaEntity updateMedia(
            UUID productId,
            UUID mediaId,
            ProductMediaUpdateRequest request,
            long expectedProductVersion) {
        requireVersion(requireMutableProduct(productId), expectedProductVersion);
        ProductMediaEntity current = getMedia(productId, mediaId);
        if (request.mediaType() != null) {
            current.setMediaType(request.mediaType());
        }
        if (request.storageKey() != null) {
            current.setStorageKey(request.storageKey().trim());
        }
        if (request.altText() != null) {
            current.setAltText(request.altText());
        }
        if (request.position() != null) {
            current.setPosition(request.position());
        }
        if (request.primary() != null) {
            current.setPrimary(request.primary());
        }
        if (current.isPrimary()) {
            media.updateReplacingPrimary(current, expectedProductVersion);
        } else {
            media.update(current, expectedProductVersion);
        }
        return media.findById(mediaId).orElseThrow();
    }

    @Transactional
    public void deleteMedia(UUID productId, UUID mediaId, long expectedProductVersion) {
        requireVersion(requireMutableProduct(productId), expectedProductVersion);
        getMedia(productId, mediaId);
        media.deleteById(mediaId, productId, expectedProductVersion);
    }

    @Transactional
    public List<ProductMediaEntity> reorderMedia(
            UUID productId, ProductMediaReorderRequest request, long expectedProductVersion) {
        requireVersion(requireMutableProduct(productId), expectedProductVersion);
        List<ProductMediaEntity> existing = media.findByProductId(productId);
        validateCompleteIds(
                existing.stream().map(ProductMediaEntity::getId).toList(),
                request.mediaIds(),
                "media");
        media.reorder(productId, request.mediaIds(), expectedProductVersion);
        return media.findByProductId(productId);
    }

    @Transactional
    public ProductMediaEntity setPrimaryMedia(
            UUID productId, UUID mediaId, long expectedProductVersion) {
        requireVersion(requireMutableProduct(productId), expectedProductVersion);
        getMedia(productId, mediaId);
        media.setPrimary(productId, mediaId, expectedProductVersion);
        return media.findById(mediaId).orElseThrow();
    }

    public List<ProductCategoryResponse> listCategories(UUID productId) {
        requireMutableProduct(productId);
        return productCategories.findByProductId(productId).stream()
                .map(
                        relation ->
                                categories
                                        .findById(relation.getCategoryId())
                                        .map(
                                                category ->
                                                        ProductCategoryResponse.from(
                                                                category, relation.isPrimary())))
                .flatMap(java.util.Optional::stream)
                .toList();
    }

    @Transactional
    public List<ProductCategoryResponse> replaceCategories(
            UUID productId, ProductCategoriesReplaceRequest request, long expectedProductVersion) {
        requireVersion(requireMutableProduct(productId), expectedProductVersion);
        validateCategoryIds(request.categoryIds(), request.primaryCategoryId());
        List<ProductCategoryEntity> relations =
                request.categoryIds().stream()
                        .map(
                                categoryId ->
                                        categoryRelation(
                                                productId,
                                                categoryId,
                                                categoryId.equals(request.primaryCategoryId())))
                        .toList();
        productCategories.replace(productId, relations, expectedProductVersion);
        return listCategories(productId);
    }

    @Transactional
    public List<ProductCategoryResponse> assignCategory(
            UUID productId,
            UUID categoryId,
            ProductCategoryAssignRequest request,
            long expectedProductVersion) {
        requireVersion(requireMutableProduct(productId), expectedProductVersion);
        requireCategory(categoryId);
        if (productCategories.findByProductId(productId).stream()
                .anyMatch(value -> value.getCategoryId().equals(categoryId))) {
            throw new CategoryService.ConflictException("Category is already assigned");
        }
        ProductCategoryEntity relation = categoryRelation(productId, categoryId, request.primary());
        if (request.primary()) {
            productCategories.insertReplacingPrimary(relation, expectedProductVersion);
        } else {
            productCategories.insert(relation, expectedProductVersion);
        }
        return listCategories(productId);
    }

    @Transactional
    public void removeCategory(UUID productId, UUID categoryId, long expectedProductVersion) {
        requireVersion(requireMutableProduct(productId), expectedProductVersion);
        if (productCategories.findByProductId(productId).stream()
                .noneMatch(value -> value.getCategoryId().equals(categoryId))) {
            throw new ResourceNotFoundException("Category assignment not found: " + categoryId);
        }
        productCategories.delete(productId, categoryId, expectedProductVersion);
    }

    @Transactional
    public List<ProductCategoryResponse> setPrimaryCategory(
            UUID productId, UUID categoryId, long expectedProductVersion) {
        requireVersion(requireMutableProduct(productId), expectedProductVersion);
        productCategories.setPrimary(productId, categoryId, expectedProductVersion);
        return listCategories(productId);
    }

    private ProductCategoryEntity categoryRelation(
            UUID productId, UUID categoryId, boolean primary) {
        ProductCategoryEntity relation = new ProductCategoryEntity();
        relation.setProductId(productId);
        relation.setCategoryId(categoryId);
        relation.setPrimary(primary);
        return relation;
    }

    private void validateCategoryIds(List<UUID> categoryIds, UUID primaryCategoryId) {
        if (new HashSet<>(categoryIds).size() != categoryIds.size()) {
            throw new CategoryService.ConflictException("Duplicate category assignment");
        }
        if (primaryCategoryId != null && !categoryIds.contains(primaryCategoryId)) {
            throw new IllegalArgumentException("Primary category must be assigned");
        }
        categoryIds.forEach(this::requireCategory);
    }

    private void requireCategory(UUID categoryId) {
        categories
                .findById(categoryId)
                .orElseThrow(
                        () -> new ResourceNotFoundException("Category not found: " + categoryId));
    }

    private void validateCompleteIds(
            List<UUID> existingIds, List<UUID> requestedIds, String resourceName) {
        if (new HashSet<>(requestedIds).size() != requestedIds.size()
                || !new HashSet<>(existingIds).equals(new HashSet<>(requestedIds))) {
            throw new IllegalArgumentException(
                    "Reorder request must contain every " + resourceName + " exactly once");
        }
    }

    private ProductEntity requireMutableProduct(UUID id) {
        ProductEntity product = getIncludingDeleted(id);
        if (product.getDeletedAt() != null) {
            throw new IllegalStateException("Deleted products cannot be modified");
        }
        return product;
    }

    private void validateVariant(
            java.math.BigDecimal price,
            java.math.BigDecimal compareAtPrice,
            Map<String, Object> attributes) {
        if (price == null) {
            throw new IllegalArgumentException("Variant price is required");
        }
        if (compareAtPrice != null && compareAtPrice.compareTo(price) < 0) {
            throw new IllegalArgumentException("Compare-at-price must not be below price");
        }
        if (attributes != null
                && attributes.keySet().stream().anyMatch(key -> key == null || key.isBlank())) {
            throw new IllegalArgumentException("Variant attribute keys must not be blank");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private PublicProductResponse publicResponse(ProductEntity product) {
        BrandResponse brand =
                product.getBrandId() == null
                        ? null
                        : brands.findById(product.getBrandId())
                                .map(BrandResponse::from)
                                .orElse(null);
        List<CategoryResponse> categoryResponses = publicCategories(product.getId());
        return new PublicProductResponse(
                product.getId(),
                product.getName(),
                product.getSlug(),
                product.getDescription(),
                product.getMetadata(),
                product.getPublishedAt(),
                brand,
                categoryResponses,
                variants.findActiveByProductId(product.getId()).stream()
                        .map(PublicVariantResponse::from)
                        .toList(),
                media.findByProductId(product.getId()).stream()
                        .map(PublicMediaResponse::from)
                        .toList());
    }

    private List<CategoryResponse> publicCategories(UUID productId) {
        List<ProductCategoryEntity> relations = productCategories.findByProductId(productId);
        Map<UUID, CategoryEntity> categoryById =
                categories
                        .findByIds(
                                relations.stream()
                                        .map(ProductCategoryEntity::getCategoryId)
                                        .toList())
                        .stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        CategoryEntity::getId,
                                        value -> value));
        return relations.stream()
                .filter(relation -> categoryById.containsKey(relation.getCategoryId()))
                .map(relation -> CategoryResponse.from(categoryById.get(relation.getCategoryId())))
                .toList();
    }

    private void requireVersion(ProductEntity current, long expectedVersion) {
        if (current.getVersion() != expectedVersion) {
            throw new OptimisticLockingFailureException(
                    "Product version is " + current.getVersion() + ", expected " + expectedVersion);
        }
    }

    public static class ResourceNotFoundException extends RuntimeException {
        public ResourceNotFoundException(String message) {
            super(message);
        }
    }
}
