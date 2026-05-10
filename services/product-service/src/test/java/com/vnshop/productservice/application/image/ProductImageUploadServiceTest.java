package com.vnshop.productservice.application.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vnshop.productservice.application.storage.ObjectValidationPolicy;
import com.vnshop.productservice.application.storage.ObjectValidationService;
import com.vnshop.productservice.domain.Product;
import com.vnshop.productservice.domain.port.out.ObjectMetadataRepositoryPort;
import com.vnshop.productservice.domain.port.out.ObjectStoragePort;
import com.vnshop.productservice.domain.port.out.ProductRepositoryPort;
import com.vnshop.productservice.domain.storage.ObjectMetadata;
import com.vnshop.productservice.domain.storage.ObjectQuarantineState;
import com.vnshop.productservice.domain.storage.ObjectStorageClass;
import java.io.InputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProductImageUploadServiceTest {
    private final FakeProductRepository productRepository = new FakeProductRepository();
    private final FakeObjectStorage objectStorage = new FakeObjectStorage();
    private final FakeObjectMetadataRepository metadataRepository = new FakeObjectMetadataRepository();
    private final ProductImageUploadService service = new ProductImageUploadService(productRepository, objectStorage,
            metadataRepository, new ObjectValidationService(ObjectValidationPolicy.builder()
                    .storageClass(ObjectStorageClass.PRODUCT_IMAGE)
                    .maxBytes(5 * 1024 * 1024)
                    .allowedContentTypes(Set.of("image/jpeg", "image/png", "image/webp"))
                    .maxImageWidth(4096)
                    .maxImageHeight(4096)
                    .build()));

    @Test
    void createsSignedUploadUrlAndPendingMetadataForValidProductImage() {
        productRepository.save(product("product-1", "seller-1"));

        ProductImageUploadResponse response = service.createUpload(validRequest().build());

        assertThat(response.getUploadUrl()).isEqualTo(URI.create("https://storage.test/" + response.getObjectKey()));
        assertThat(response.getObjectKey()).startsWith("products/product-1/images/").endsWith(".png");
        assertThat(response.getChecksumSha256()).isEqualTo("a".repeat(64));
        assertThat(response.getQuarantineState()).isEqualTo("PENDING_VALIDATION");
        ObjectMetadata metadata = metadataRepository.saved.get(response.getObjectKey());
        assertThat(metadata.getQuarantineState()).isEqualTo(ObjectQuarantineState.PENDING_VALIDATION);
        assertThat(metadata.getSha256Hex()).isEqualTo("a".repeat(64));
        assertThat(metadata.getContentType()).isEqualTo("image/png");
        assertThat(objectStorage.lastMetadata).isEqualTo(metadata);
    }

    @Test
    void rejectsInvalidMetadataBeforeIssuingUploadUrl() {
        productRepository.save(product("product-1", "seller-1"));

        assertThatThrownBy(() -> service.createUpload(validRequest()
                .fileName("payload.exe")
                .declaredContentType("application/x-msdownload")
                .detectedContentType("application/x-msdownload")
                .contentLength(6 * 1024 * 1024)
                .sha256Hex("not-a-sha")
                .imageWidth(5000)
                .imageHeight(null)
                .build()))
                .isInstanceOf(ProductImageValidationException.class)
                .extracting(error -> ((ProductImageValidationException) error).failures())
                .asList()
                .contains("file_extension_rejected", "declared_content_type_rejected", "checksum_shape_invalid", "image_dimensions_required");
        assertThat(objectStorage.lastKey).isNull();
        assertThat(metadataRepository.saved.values()).singleElement()
                .extracting(ObjectMetadata::getQuarantineState)
                .isEqualTo(ObjectQuarantineState.REJECTED);
    }

    private ProductImageUploadRequest.ProductImageUploadRequestBuilder validRequest() {
        return ProductImageUploadRequest.builder()
                .productId("product-1")
                .sellerId("seller-1")
                .fileName("front.png")
                .declaredContentType("image/png")
                .detectedContentType("image/png")
                .contentLength(1024)
                .sha256Hex("a".repeat(64))
                .imageWidth(800)
                .imageHeight(600);
    }

    private Product product(String productId, String sellerId) {
        return new Product(productId, sellerId, "Phone", "Fast phone", "phones", "VNShop", null, null);
    }

    private static final class FakeProductRepository implements ProductRepositoryPort {
        private final Map<String, Product> products = new HashMap<>();

        @Override
        public Product save(Product product) {
            products.put(product.productId(), product);
            return product;
        }

        @Override
        public Optional<Product> findById(String productId) {
            return Optional.ofNullable(products.get(productId));
        }

        @Override
        public java.util.List<Product> findBySellerId(String sellerId) {
            return java.util.List.of();
        }

        @Override
        public java.util.List<Product> findByCategory(String categoryId) {
            return java.util.List.of();
        }

        @Override
        public java.util.List<Product> searchByName(String name) {
            return java.util.List.of();
        }

        @Override
        public java.util.List<String> findDistinctCategories() {
            return java.util.List.of();
        }
    }

    private static final class FakeObjectStorage implements ObjectStoragePort {
        private String lastKey;
        private ObjectMetadata lastMetadata;

        @Override
        public void putObject(String key, InputStream content, ObjectMetadata metadata) {
        }

        @Override
        public URI getSignedUploadUrl(String key, ObjectMetadata metadata) {
            lastKey = key;
            lastMetadata = metadata;
            return URI.create("https://storage.test/" + key);
        }

        @Override
        public URI getSignedDownloadUrl(String key, ObjectStorageClass storageClass) {
            return URI.create("https://storage.test/" + key);
        }

        @Override
        public void deleteObject(String key) {
        }

        @Override
        public Optional<ObjectMetadata> headObject(String key) {
            return Optional.empty();
        }
    }

    private static final class FakeObjectMetadataRepository implements ObjectMetadataRepositoryPort {
        private final Map<String, ObjectMetadata> saved = new HashMap<>();

        @Override
        public ObjectMetadata save(ObjectMetadata metadata) {
            saved.put(metadata.getKey(), metadata);
            return metadata;
        }

        @Override
        public Optional<ObjectMetadata> findByKey(String key) {
            return Optional.ofNullable(saved.get(key));
        }
    }
}
