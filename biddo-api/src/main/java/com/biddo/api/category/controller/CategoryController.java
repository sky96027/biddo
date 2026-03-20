package com.biddo.api.category.controller;

import com.biddo.api.category.dto.response.CategoryResponse;
import com.biddo.api.common.response.ApiResponse;
import com.biddo.domain.category.entity.Category;
import com.biddo.domain.category.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public ApiResponse<List<CategoryResponse>> getCategories() {
        List<Category> categories = categoryService.findAll();
        List<CategoryResponse> tree = buildTree(categories);
        return ApiResponse.success(tree);
    }

    private List<CategoryResponse> buildTree(List<Category> categories) {
        Map<Long, List<Category>> childrenMap = categories.stream()
                .filter(c -> c.getParent() != null)
                .collect(Collectors.groupingBy(c -> c.getParent().getId()));

        return categories.stream()
                .filter(c -> c.getParent() == null)
                .map(root -> buildNode(root, childrenMap))
                .toList();
    }

    private CategoryResponse buildNode(Category category, Map<Long, List<Category>> childrenMap) {
        List<CategoryResponse> children = childrenMap.getOrDefault(category.getId(), List.of())
                .stream()
                .map(child -> buildNode(child, childrenMap))
                .toList();
        return CategoryResponse.of(category, children);
    }
}