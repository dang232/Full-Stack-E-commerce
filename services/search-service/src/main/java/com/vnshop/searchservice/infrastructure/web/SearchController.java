package com.vnshop.searchservice.infrastructure.web;

import com.vnshop.searchservice.application.SearchProductResponse;
import com.vnshop.searchservice.application.SearchProductsUseCase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping
public class SearchController {
    private final SearchProductsUseCase searchProductsUseCase;

    public SearchController(SearchProductsUseCase searchProductsUseCase) {
        this.searchProductsUseCase = searchProductsUseCase;
    }

    @GetMapping("/search")
    public ApiResponse<Page<SearchProductResponse>> search(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            Pageable pageable
    ) {
        return ApiResponse.ok(searchProductsUseCase.searchPaged(query, category, brand, minPrice, maxPrice, pageable));
    }

    @GetMapping("/categories")
    public ApiResponse<List<String>> categories() {
        return ApiResponse.ok(searchProductsUseCase.categories());
    }
}
