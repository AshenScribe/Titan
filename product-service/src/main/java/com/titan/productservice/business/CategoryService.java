package com.titan.productservice.business;

import com.titan.productservice.data.entity.CategoryEntity;
import com.titan.productservice.data.repository.CategoryRepository;
import com.titan.productservice.presentation.dto.CategoryCreateRequest;
import com.titan.productservice.presentation.dto.CategoryListQuery;
import com.titan.productservice.presentation.dto.CategoryParentRequest;
import com.titan.productservice.presentation.dto.CategoryResponse;
import com.titan.productservice.presentation.dto.CategoryTreeNode;
import com.titan.productservice.presentation.dto.CategoryUpdateRequest;
import com.titan.productservice.presentation.dto.PageResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {
    private final CategoryRepository categories;

    public CategoryService(CategoryRepository categories) {
        this.categories = categories;
    }

    @Transactional
    public CategoryEntity create(CategoryCreateRequest request) {
        validateParent(request.parentId());
        CategoryEntity category = new CategoryEntity();
        category.setId(UUID.randomUUID());
        category.setParentId(request.parentId());
        category.setName(request.name().trim());
        category.setSlug(request.slug());
        category.setDescription(request.description());
        category.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        category.setMetadata(request.metadata() == null ? Map.of() : request.metadata());
        categories.insert(category);
        return get(category.getId());
    }

    @Transactional
    public CategoryEntity update(UUID id, CategoryUpdateRequest request) {
        CategoryEntity category = get(id);
        if (request.name() != null) {
            category.setName(request.name().trim());
        }
        if (request.slug() != null) {
            category.setSlug(request.slug());
        }
        if (request.description() != null) {
            category.setDescription(request.description());
        }
        if (request.sortOrder() != null) {
            category.setSortOrder(request.sortOrder());
        }
        if (request.metadata() != null) {
            category.setMetadata(request.metadata());
        }
        categories.update(category);
        return get(id);
    }

    @Transactional
    public CategoryEntity move(UUID id, CategoryParentRequest request) {
        CategoryEntity category = get(id);
        validateParent(request.parentId());
        if (request.parentId() != null
                && categories.findDescendants(id).stream()
                        .anyMatch(descendant -> descendant.getId().equals(request.parentId()))) {
            throw new ConflictException("Category hierarchy cycle detected");
        }
        category.setParentId(request.parentId());
        categories.update(category);
        return get(id);
    }

    @Transactional
    public CategoryEntity setStatus(UUID id, boolean active) {
        CategoryEntity category = get(id);
        category.setActive(active);
        categories.update(category);
        return get(id);
    }

    @Transactional
    public void delete(UUID id) {
        if (categories.deleteById(id) == 0) {
            throw new ProductService.ResourceNotFoundException("Category not found: " + id);
        }
    }

    public CategoryEntity get(UUID id) {
        return categories
                .findById(id)
                .orElseThrow(
                        () ->
                                new ProductService.ResourceNotFoundException(
                                        "Category not found: " + id));
    }

    public PageResponse<CategoryResponse> list(CategoryListQuery query) {
        CategoryRepository.CategoryPage page = categories.findPage(query);
        return PageResponse.of(
                page.categories().stream().map(CategoryResponse::from).toList(),
                query.getPage(),
                query.getSize(),
                page.totalElements());
    }

    public List<CategoryResponse> children(UUID id) {
        get(id);
        return categories.findChildren(id, 100, 0).stream().map(CategoryResponse::from).toList();
    }

    public List<CategoryResponse> descendants(UUID id) {
        get(id);
        return categories.findDescendants(id).stream().map(CategoryResponse::from).toList();
    }

    public List<CategoryTreeNode> tree() {
        List<CategoryEntity> all = categories.findAllHierarchy();
        Map<UUID, List<CategoryTreeNode>> children = new HashMap<>();
        for (CategoryEntity category : all) {
            children.computeIfAbsent(category.getParentId(), ignored -> new ArrayList<>())
                    .add(new CategoryTreeNode(CategoryResponse.from(category), new ArrayList<>()));
        }
        for (CategoryTreeNode node : children.values().stream().flatMap(List::stream).toList()) {
            node.children().addAll(children.getOrDefault(node.category().id(), List.of()));
        }
        return children.getOrDefault(null, List.of());
    }

    private void validateParent(UUID parentId) {
        if (parentId != null) {
            get(parentId);
        }
    }

    public static class ConflictException extends RuntimeException {
        public ConflictException(String message) {
            super(message);
        }
    }
}
