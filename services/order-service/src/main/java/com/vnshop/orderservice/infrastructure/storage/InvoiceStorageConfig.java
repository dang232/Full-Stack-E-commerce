package com.vnshop.orderservice.infrastructure.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
@EnableConfigurationProperties(InvoiceStorageProperties.class)
@ConditionalOnProperty(prefix = "vnshop.invoice-storage", name = "enabled", havingValue = "true")
public class InvoiceStorageConfig {
    @Bean
    S3Client invoiceS3Client(InvoiceStorageProperties properties) {
        return S3Client.builder()
                .region(Region.of(properties.getRegion()))
                .endpointOverride(URI.create(properties.getEndpoint()))
                .credentialsProvider(credentialsProvider(properties))
                .serviceConfiguration(serviceConfiguration(properties))
                .build();
    }

    @Bean
    S3Presigner invoiceS3Presigner(InvoiceStorageProperties properties) {
        return S3Presigner.builder()
                .region(Region.of(properties.getRegion()))
                .endpointOverride(URI.create(properties.getEndpoint()))
                .credentialsProvider(credentialsProvider(properties))
                .serviceConfiguration(serviceConfiguration(properties))
                .build();
    }

    @Bean
    S3InvoiceStorageAdapter s3InvoiceStorageAdapter(S3Client invoiceS3Client, S3Presigner invoiceS3Presigner, InvoiceStorageProperties properties) {
        return new S3InvoiceStorageAdapter(invoiceS3Client, invoiceS3Presigner, properties);
    }

    private StaticCredentialsProvider credentialsProvider(InvoiceStorageProperties properties) {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey()));
    }

    private S3Configuration serviceConfiguration(InvoiceStorageProperties properties) {
        return S3Configuration.builder()
                .pathStyleAccessEnabled(properties.isPathStyleAccess())
                .build();
    }
}
