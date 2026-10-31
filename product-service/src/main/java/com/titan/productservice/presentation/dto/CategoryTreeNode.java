package com.titan.productservice.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "CategoryTreeNode", description = "Category with nested direct children.")
public record CategoryTreeNode(CategoryResponse category, List<CategoryTreeNode> children) {}
