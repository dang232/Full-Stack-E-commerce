package com.vnshop.searchservice.infrastructure.web;

import com.vnshop.searchservice.application.SearchFacetsResponse;
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

    /**
     * Header autocomplete: returns up to 10 product names whose lower-cased
     * value starts with the prefix. Empty/missing prefix returns an empty list
     * rather than the full catalog.
     */
    @GetMapping("/search/suggest")
    public ApiResponse<List<String>> suggest(@RequestParam(name = "q", required = false) String query) {
        return ApiResponse.ok(searchProductsUseCase.suggest(query));
    }

    /**
     * Facet aggregations alongside a search query. Each axis (category, brand)
     * is computed with the other axis relaxed so the sidebar shows other-axis
     * options without forcing the user to unselect their current filter.
     */
    @GetMapping("/search/facets")
    public ApiResponse<SearchFacetsResponse> facets(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice
    ) {
        return ApiResponse.ok(searchProductsUseCase.facets(query, category, brand, minPrice, maxPrice));
    }
}
