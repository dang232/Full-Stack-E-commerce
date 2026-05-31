package com.vnshop.orderservice.infrastructure.storage;

import com.vnshop.orderservice.domain.port.out.InvoiceStoragePort;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Fallback {@link InvoiceStoragePort} for environments without S3/MinIO.
 * Throws on actual storage operations so misconfigured production stays loud,
 * but lets the application context start so the service is reachable for
 * non-invoice traffic. The real S3 adapter takes precedence when
 * {@code vnshop.invoice-storage.enabled=true}.
 */
@Configuration
public class InvoiceStorageNoopConfig {

    @Bean
    @ConditionalOnMissingBean(InvoiceStoragePort.class)
    InvoiceStoragePort unavailableInvoiceStoragePort() {
        return new InvoiceStoragePort() {
            @Override
            public void putInvoicePdf(String objectKey, byte[] content, String checksumSha256) {
                throw new IllegalStateException("invoice storage is not configured");
            }

            @Override
            public URI signedDownloadUrl(String objectKey, Duration ttl) {
                throw new IllegalStateException("invoice storage is not configured");
            }
        };
    }
}
