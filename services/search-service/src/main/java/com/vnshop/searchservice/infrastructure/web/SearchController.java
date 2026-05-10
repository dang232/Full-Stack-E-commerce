package com.vnshop.searchservice.infrastructure.web;

import com.vnshop.searchservice.domain.ProductReadModel;
import com.vnshop.searchservice.infrastructure.persistence.ProductReadModelRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping
public class SearchController {
    private final ProductReadModelRepository productReadModelRepository;

    public SearchController(ProductReadModelRepository productReadModelRepository) {
        this.productReadModelRepository = productReadModelRepository;
    }

    @GetMapping("/search")
    public List<ProductReadModel> search(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice
    ) {
        return productReadModelRepository.search(query, category, brand, minPrice, maxPrice);
    }

    @GetMapping("/categories")
    public List<String> categories() {
        return productReadModelRepository.findDistinctCategories();
    }
}
