package com.vnshop.searchservice.application;

import com.vnshop.searchservice.infrastructure.persistence.ProductReadModelRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

public class SearchProductsUseCase {

    private final ProductReadModelRepository productReadModelRepository;

    public SearchProductsUseCase(ProductReadModelRepository productReadModelRepository) {
        this.productReadModelRepository = productReadModelRepository;
    }

    public List<SearchProductResponse> search(String query, String category, String brand, BigDecimal minPrice, BigDecimal maxPrice) {
        return productReadModelRepository.search(query, category, brand, minPrice, maxPrice).stream()
                .map(SearchProductResponse::fromDomain)
                .toList();
    }

    public Page<SearchProductResponse> searchPaged(String query, String category, String brand, BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable) {
        return productReadModelRepository.searchPaged(query, category, brand, minPrice, maxPrice, pageable)
                .map(SearchProductResponse::fromDomain);
    }

    public List<String> categories() {
        return productReadModelRepository.findDistinctCategories();
    }
}
