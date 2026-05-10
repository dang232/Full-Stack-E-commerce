package com.vnshop.productservice.infrastructure.config;

import com.vnshop.productservice.application.CreateProductUseCase;
import com.vnshop.productservice.application.GetProductUseCase;
import com.vnshop.productservice.application.UpdateProductUseCase;
import com.vnshop.productservice.application.image.ProductImageUploadService;
import com.vnshop.productservice.application.storage.ObjectValidationPolicy;
import com.vnshop.productservice.application.storage.ObjectValidationService;
import com.vnshop.productservice.domain.port.out.ObjectMetadataRepositoryPort;
import com.vnshop.productservice.domain.port.out.ObjectStoragePort;
import com.vnshop.productservice.domain.port.out.ProductEventPublisherPort;
import com.vnshop.productservice.domain.port.out.ProductRepositoryPort;
import com.vnshop.productservice.domain.storage.ObjectStorageClass;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCaseConfig {
    @Bean
    CreateProductUseCase createProductUseCase(ProductRepositoryPort productRepositoryPort, ProductEventPublisherPort productEventPublisherPort) {
        return new CreateProductUseCase(productRepositoryPort, productEventPublisherPort);
    }

    @Bean
    UpdateProductUseCase updateProductUseCase(ProductRepositoryPort productRepositoryPort, ProductEventPublisherPort productEventPublisherPort) {
        return new UpdateProductUseCase(productRepositoryPort, productEventPublisherPort);
    }

    @Bean
    GetProductUseCase getProductUseCase(ProductRepositoryPort productRepositoryPort) {
        return new GetProductUseCase(productRepositoryPort);
    }

    @Bean
    ObjectValidationService productImageObjectValidationService() {
        return new ObjectValidationService(ObjectValidationPolicy.builder()
                .storageClass(ObjectStorageClass.PRODUCT_IMAGE)
                .maxBytes(5 * 1024 * 1024)
                .allowedContentTypes(Set.of("image/jpeg", "image/png", "image/webp"))
                .maxImageWidth(4096)
                .maxImageHeight(4096)
                .build());
    }

    @Bean
    ProductImageUploadService productImageUploadService(ProductRepositoryPort productRepositoryPort,
            ObjectStoragePort objectStoragePort, ObjectMetadataRepositoryPort objectMetadataRepositoryPort,
            ObjectValidationService productImageObjectValidationService) {
        return new ProductImageUploadService(productRepositoryPort, objectStoragePort, objectMetadataRepositoryPort,
                productImageObjectValidationService);
    }
}
