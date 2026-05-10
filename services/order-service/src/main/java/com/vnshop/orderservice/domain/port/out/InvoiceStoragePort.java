package com.vnshop.orderservice.domain.port.out;

import java.net.URI;
import java.time.Duration;

public interface InvoiceStoragePort {
    void putInvoicePdf(String objectKey, byte[] content, String checksumSha256);

    URI signedDownloadUrl(String objectKey, Duration ttl);
}
