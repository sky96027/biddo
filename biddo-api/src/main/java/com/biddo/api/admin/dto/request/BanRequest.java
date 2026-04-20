package com.biddo.api.admin.dto.request;

import com.biddo.domain.member.entity.BanType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class BanRequest {

    @NotNull
    private BanType banType;

    @NotBlank
    private String reason;

    private Integer durationDays;
}
