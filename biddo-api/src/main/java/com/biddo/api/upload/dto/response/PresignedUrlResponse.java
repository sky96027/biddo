package com.biddo.api.upload.dto.response;

import com.biddo.infra.s3.S3PresignedUrlService.PresignedUrlResult;
import lombok.Getter;

@Getter
public class PresignedUrlResponse {

    private final String presignedUrl;
    private final String fileUrl;
    private final long expiresIn;

    private PresignedUrlResponse(String presignedUrl, String fileUrl, long expiresIn) {
        this.presignedUrl = presignedUrl;
        this.fileUrl = fileUrl;
        this.expiresIn = expiresIn;
    }

    public static PresignedUrlResponse from(PresignedUrlResult result) {
        return new PresignedUrlResponse(result.presignedUrl(), result.fileUrl(), result.expiresIn());
    }
}