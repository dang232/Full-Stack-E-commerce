package com.vnshop.productservice.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.vnshop.productservice.domain.storage.ObjectStorageClass;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ObjectStorageTtlTest {
    @Test
    void mapsSignedUrlTtlsByObjectClass() {
        assertThat(ObjectStorageClass.PRODUCT_IMAGE.uploadTtl()).isEqualTo(Duration.ofMinutes(15));
        assertThat(ObjectStorageClass.PRODUCT_IMAGE.downloadTtl()).isEqualTo(Duration.ofMinutes(15));
        assertThat(ObjectStorageClass.SELLER_DOCUMENT.uploadTtl()).isEqualTo(Duration.ofMinutes(15));
        assertThat(ObjectStorageClass.SELLER_DOCUMENT.downloadTtl()).isEqualTo(Duration.ofMinutes(5));
        assertThat(ObjectStorageClass.INVOICE.downloadTtl()).isEqualTo(Duration.ofMinutes(5));
        assertThat(ObjectStorageClass.EXPORT.downloadTtl()).isEqualTo(Duration.ofMinutes(15));
    }
}
