package com.biddo.api.upload.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class PresignedUrlRequest {

    @NotBlank
    private String fileName;

    @NotBlank
    private String contentType;

    @NotBlank
    private String purpose;
}