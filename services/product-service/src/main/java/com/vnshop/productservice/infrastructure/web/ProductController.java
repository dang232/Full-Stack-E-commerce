package com.vnshop.productservice.infrastructure.web;

import com.vnshop.productservice.application.CreateProductUseCase;
import com.vnshop.productservice.application.GetProductUseCase;
import com.vnshop.productservice.application.UpdateProductUseCase;
import com.vnshop.productservice.domain.Money;
import com.vnshop.productservice.domain.Product;
import com.vnshop.productservice.domain.ProductImage;
import com.vnshop.productservice.domain.ProductVariant;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping
public class ProductController {
    private static final String DEFAULT_SELLER_ID = "stub-seller";

    private final CreateProductUseCase createProductUseCase;
    private final UpdateProductUseCase updateProductUseCase;
    private final GetProductUseCase getProductUseCase;

    public ProductController(CreateProductUseCase createProductUseCase, UpdateProductUseCase updateProductUseCase, GetProductUseCase getProductUseCase) {
        this.createProductUseCase = createProductUseCase;
        this.updateProductUseCase = updateProductUseCase;
        this.getProductUseCase = getProductUseCase;
    }

    @PostMapping("/sellers/me/products")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@RequestHeader(name = "X-Seller-Id", required = false) String sellerId, @RequestBody ProductRequest request) {
        return ProductResponse.fromDomain(createProductUseCase.create(
                currentSellerId(sellerId),
                request.name(),
                request.description(),
                request.categoryId(),
                request.brand(),
                request.toVariants(),
                request.toImages()
        ));
    }

    @PutMapping("/sellers/me/products/{id}")
    public ProductResponse update(@RequestHeader(name = "X-Seller-Id", required = false) String sellerId, @PathVariable String id, @RequestBody ProductRequest request) {
        return ProductResponse.fromDomain(updateProductUseCase.update(
                currentSellerId(sellerId),
                id,
                request.name(),
                request.description(),
                request.categoryId(),
                request.brand(),
                request.toVariants(),
                request.toImages()
        ));
    }

    @GetMapping("/products")
    public List<ProductResponse> findProducts(@RequestParam(required = false) String categoryId, @RequestParam(required = false) String q) {
        List<Product> products;
        if (categoryId != null && !categoryId.isBlank()) {
            products = getProductUseCase.findByCategory(categoryId);
        } else {
            products = getProductUseCase.searchByName(q == null ? "" : q);
        }
        return products.stream().map(ProductResponse::fromDomain).toList();
    }

    @GetMapping("/products/{id}")
    public ProductResponse findProduct(@PathVariable String id) {
        return getProductUseCase.findById(id)
                .map(ProductResponse::fromDomain)
                .orElseThrow(() -> new IllegalArgumentException("product not found"));
    }

    @GetMapping("/categories")
    public List<String> findCategories() {
        return getProductUseCase.findCategories();
    }

    private static String currentSellerId(String sellerId) {
        return sellerId == null || sellerId.isBlank() ? DEFAULT_SELLER_ID : sellerId;
    }

    public record ProductRequest(String name, String description, String categoryId, String brand, List<VariantRequest> variants, List<ImageRequest> images) {
        List<ProductVariant> toVariants() {
            return variants == null ? List.of() : variants.stream().map(VariantRequest::toDomain).toList();
        }

        List<ProductImage> toImages() {
            return images == null ? List.of() : images.stream().map(ImageRequest::toDomain).toList();
        }
    }

    public record VariantRequest(String sku, String name, BigDecimal priceAmount, String priceCurrency, String imageUrl) {
        ProductVariant toDomain() {
            return new ProductVariant(sku, name, new Money(priceAmount, priceCurrency), imageUrl);
        }
    }

    public record ImageRequest(String url, String alt, int sortOrder) {
        ProductImage toDomain() {
            return new ProductImage(url, alt, sortOrder);
        }
    }

    public record ProductResponse(String id, String sellerId, String name, String description, String categoryId, String brand, String status, List<VariantResponse> variants, List<ImageResponse> images) {
        static ProductResponse fromDomain(Product product) {
            return new ProductResponse(
                    product.productId(),
                    product.sellerId(),
                    product.name(),
                    product.description(),
                    product.categoryId(),
                    product.brand(),
                    product.status().name(),
                    product.variants().stream().map(VariantResponse::fromDomain).toList(),
                    product.images().stream().map(ImageResponse::fromDomain).toList()
            );
        }
    }

    public record VariantResponse(String sku, String name, BigDecimal priceAmount, String priceCurrency, String imageUrl) {
        static VariantResponse fromDomain(ProductVariant variant) {
            return new VariantResponse(variant.sku(), variant.name(), variant.price().amount(), variant.price().currency(), variant.imageUrl());
        }
    }

    public record ImageResponse(String url, String alt, int sortOrder) {
        static ImageResponse fromDomain(ProductImage image) {
            return new ImageResponse(image.url(), image.alt(), image.sortOrder());
        }
    }
}
