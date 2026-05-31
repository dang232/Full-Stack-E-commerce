package com.vnshop.searchservice.application;

import com.vnshop.searchservice.infrastructure.persistence.ProductReadModelRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

public class SearchProductsUseCase {

    private static final int MAX_SUGGESTIONS = 10;

    private final ProductReadModelRepository productReadModelRepository;

    public SearchProductsUseCase(ProductReadModelRepository productReadModelRepository) {
        this.productReadModelRepository = productReadModelRepository;
    }

    public Page<SearchProductResponse> searchPaged(String query, String category, String brand, BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable) {
        return productReadModelRepository.searchPaged(query, category, brand, minPrice, maxPrice, pageable)
                .map(SearchProductResponse::fromDomain);
    }

    public List<String> categories() {
        return productReadModelRepository.findDistinctCategories();
    }

    /** Returns up to {@value #MAX_SUGGESTIONS} product names that start with the prefix. */
    public List<String> suggest(String prefix) {
        return productReadModelRepository.suggestions(prefix, PageRequest.of(0, MAX_SUGGESTIONS));
    }

    /**
     * Returns category and brand facet aggregations for the same filter set the
     * caller would pass to {@link #searchPaged}. Each facet dimension is computed
     * with the OTHER filter relaxed so the user can see their other-axis options
     * without unselecting the current one (typical e-commerce facet UX).
     */
    public SearchFacetsResponse facets(String query, String category, String brand, BigDecimal minPrice, BigDecimal maxPrice) {
        return new SearchFacetsResponse(
                toFacetEntries(productReadModelRepository.categoryFacetsFor(query, brand, minPrice, maxPrice)),
                toFacetEntries(productReadModelRepository.brandFacetsFor(query, category, minPrice, maxPrice))
        );
    }

    /**
     * Maps JPA's Object[]-tuple result for a {@code (key, count)} GROUP BY into
     * our typed FacetEntry. Both facet queries share this shape, so the mapping
     * lives here once instead of being duplicated per axis.
     */
    private static List<SearchFacetsResponse.FacetEntry> toFacetEntries(List<Object[]> rows) {
        return rows.stream()
                .map(row -> new SearchFacetsResponse.FacetEntry((String) row[0], ((Number) row[1]).longValue()))
                .toList();
    }
}
