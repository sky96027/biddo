package com.biddo.api.upload.controller;

import com.biddo.api.common.response.ApiResponse;
import com.biddo.api.upload.dto.request.PresignedUrlRequest;
import com.biddo.api.upload.dto.response.PresignedUrlResponse;
import com.biddo.infra.s3.S3PresignedUrlService;
import com.biddo.infra.s3.S3PresignedUrlService.PresignedUrlResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/upload")
@RequiredArgsConstructor
public class UploadController {

    private final S3PresignedUrlService s3PresignedUrlService;

    @PostMapping("/presigned-url")
    public ApiResponse<PresignedUrlResponse> getPresignedUrl(@Valid @RequestBody PresignedUrlRequest request) {
        PresignedUrlResult result = s3PresignedUrlService.generatePresignedUrl(
                request.getFileName(), request.getContentType(), request.getPurpose());
        return ApiResponse.success(PresignedUrlResponse.from(result));
    }
}