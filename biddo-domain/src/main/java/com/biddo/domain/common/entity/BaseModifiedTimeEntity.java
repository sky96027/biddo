package com.biddo.domain.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;

@Getter
@MappedSuperclass
public abstract class BaseModifiedTimeEntity extends BaseCreatedTimeEntity {

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}