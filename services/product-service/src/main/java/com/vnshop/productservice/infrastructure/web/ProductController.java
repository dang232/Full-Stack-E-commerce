package com.vnshop.productservice.infrastructure.web;

import com.vnshop.productservice.application.CreateProductCommand;
import com.vnshop.productservice.application.CreateProductUseCase;
import com.vnshop.productservice.application.GetProductUseCase;
import com.vnshop.productservice.application.UpdateProductUseCase;
import com.vnshop.productservice.domain.Product;
import com.vnshop.productservice.infrastructure.config.JwtPrincipalUtil;
import com.vnshop.productservice.infrastructure.web.ImageRequest;
import com.vnshop.productservice.infrastructure.web.ImageResponse;
import com.vnshop.productservice.infrastructure.web.ProductRequest;
import com.vnshop.productservice.infrastructure.web.VariantRequest;
import com.vnshop.productservice.infrastructure.web.VariantResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping
public class ProductController {
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
    public ApiResponse<com.vnshop.productservice.application.ProductResponse> create(@RequestBody ProductRequest request) {
        CreateProductCommand command = new CreateProductCommand(
                JwtPrincipalUtil.currentSellerId(),
                request.name(),
                request.description(),
                request.categoryId(),
                request.brand(),
                request.toVariants(),
                request.toImages()
        );
        return ApiResponse.ok(createProductUseCase.create(command));
    }

    @PutMapping("/sellers/me/products/{id}")
    public ApiResponse<ProductResponse> update(@PathVariable UUID id, @RequestBody ProductRequest request) {
        return ApiResponse.ok(ProductResponse.fromDomain(updateProductUseCase.update(
                JwtPrincipalUtil.currentSellerId(),
                id,
                request.name(),
                request.description(),
                request.categoryId(),
                request.brand(),
                request.toVariants(),
                request.toImages()
        )));
    }

    @GetMapping("/products")
    public ApiResponse<List<ProductResponse>> findProducts(@RequestParam(required = false) String categoryId, @RequestParam(required = false) String q) {
        List<Product> products;
        if (categoryId != null && !categoryId.isBlank()) {
            products = getProductUseCase.findByCategory(categoryId);
        } else {
            products = getProductUseCase.searchByName(q == null ? "" : q);
        }
        return ApiResponse.ok(products.stream().map(ProductResponse::fromDomain).toList());
    }

    @GetMapping("/products/{id}")
    public ApiResponse<ProductResponse> findProduct(@PathVariable UUID id) {
        return ApiResponse.ok(getProductUseCase.findById(id)
                .map(ProductResponse::fromDomain)
                .orElseThrow(() -> new IllegalArgumentException("product not found")));
    }

    @GetMapping("/categories")
    public ApiResponse<List<String>> findCategories() {
        return ApiResponse.ok(getProductUseCase.findCategories());
    }

}
