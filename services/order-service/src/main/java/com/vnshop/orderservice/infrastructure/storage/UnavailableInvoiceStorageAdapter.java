package com.vnshop.orderservice.infrastructure.storage;

import com.vnshop.orderservice.domain.port.out.InvoiceStoragePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;

@Component
@ConditionalOnMissingBean(InvoiceStoragePort.class)
public class UnavailableInvoiceStorageAdapter implements InvoiceStoragePort {
    @Override
    public void putInvoicePdf(String objectKey, byte[] content, String checksumSha256) {
        throw new IllegalStateException("invoice storage is not configured");
    }

    @Override
    public URI signedDownloadUrl(String objectKey, Duration ttl) {
        throw new IllegalStateException("invoice storage is not configured");
    }
}
