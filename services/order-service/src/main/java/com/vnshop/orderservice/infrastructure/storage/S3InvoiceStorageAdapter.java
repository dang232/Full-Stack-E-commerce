package com.vnshop.orderservice.infrastructure.storage;

import com.vnshop.orderservice.domain.port.out.InvoiceStoragePort;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

public class S3InvoiceStorageAdapter implements InvoiceStoragePort {
    private static final String CONTENT_TYPE = "application/pdf";
    private static final String METADATA_SHA256 = "sha256";

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final InvoiceStorageProperties properties;

    public S3InvoiceStorageAdapter(S3Client s3Client, S3Presigner s3Presigner, InvoiceStorageProperties properties) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.properties = properties;
    }

    @Override
    public void putInvoicePdf(String objectKey, byte[] content, String checksumSha256) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(objectKey)
                .contentType(CONTENT_TYPE)
                .contentLength((long) content.length)
                .metadata(Map.of(METADATA_SHA256, checksumSha256))
                .build();
        s3Client.putObject(request, RequestBody.fromBytes(content));
    }

    @Override
    public URI signedDownloadUrl(String objectKey, Duration ttl) {
        GetObjectPresignRequest request = GetObjectPresignRequest.builder()
                .getObjectRequest(GetObjectRequest.builder()
                        .bucket(properties.getBucket())
                        .key(objectKey)
                        .build())
                .signatureDuration(ttl)
                .build();
        return URI.create(s3Presigner.presignGetObject(request).url().toString());
    }
}
