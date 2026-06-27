package com.biddo.infra.s3;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3PresignedUrlService {

    private static final Duration PRESIGN_DURATION = Duration.ofMinutes(10);
    private static final Map<String, String> PURPOSE_PATHS = Map.of(
            "auction", "auctions",
            "chat", "chat",
            "profile", "profiles"
    );

    private final S3Presigner s3Presigner;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Value("${cloud.aws.cloudfront.domain:}")
    private String cloudfrontDomain;

    public PresignedUrlResult generatePresignedUrl(String fileName, String contentType, String purpose) {
        String path = PURPOSE_PATHS.getOrDefault(purpose.toLowerCase(), "etc");
        String key = path + "/" + UUID.randomUUID() + "_" + fileName;

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(PRESIGN_DURATION)
                .putObjectRequest(putRequest)
                .build();

        String presignedUrl = s3Presigner.presignPutObject(presignRequest).url().toString();

        String fileUrl = cloudfrontDomain.isBlank()
                ? String.format("https://%s.s3.amazonaws.com/%s", bucket, key)
                : String.format("https://%s/%s", cloudfrontDomain, key);

        return new PresignedUrlResult(presignedUrl, fileUrl, PRESIGN_DURATION.toSeconds());
    }

    public record PresignedUrlResult(String presignedUrl, String fileUrl, long expiresIn) {
    }
}