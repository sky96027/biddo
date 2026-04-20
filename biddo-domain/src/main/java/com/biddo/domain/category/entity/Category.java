package com.biddo.domain.category.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "category")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    @Column(nullable = false)
    private int depth;

    @Column(nullable = false)
    private int sortOrder;

    @Builder
    public Category(String name, Category parent, int depth, int sortOrder) {
        this.name = name;
        this.parent = parent;
        this.depth = depth;
        this.sortOrder = sortOrder;
    }
}