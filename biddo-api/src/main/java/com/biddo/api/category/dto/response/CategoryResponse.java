package com.biddo.api.category.dto.response;

import com.biddo.domain.category.entity.Category;
import lombok.Getter;

import java.util.List;

@Getter
public class CategoryResponse {

    private final Long id;
    private final String name;
    private final int depth;
    private final List<CategoryResponse> children;

    private CategoryResponse(Long id, String name, int depth, List<CategoryResponse> children) {
        this.id = id;
        this.name = name;
        this.depth = depth;
        this.children = children;
    }

    public static CategoryResponse of(Category category, List<CategoryResponse> children) {
        return new CategoryResponse(category.getId(), category.getName(), category.getDepth(), children);
    }
}