package com.vnshop.recommendationsservice.infrastructure.web;

import com.vnshop.recommendationsservice.application.FrequentlyBoughtTogetherUseCase;
import com.vnshop.recommendationsservice.application.ProductProjection;
import com.vnshop.recommendationsservice.application.YouMayAlsoLikeUseCase;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

/**
 * Two-endpoint recommendations surface.
 *
 * <p>Both endpoints are public (no auth) — they leak no buyer-specific
 * information. The "frequently bought together" view is a co-purchase
 * aggregate maintained from {@code order.created} Kafka events; the "you
 * may also like" view is a same-category, ±30%-price filter.
 */
@RestController
@RequestMapping("/recommendations")
@Validated
public class RecommendationsController {
    private static final int FBT_DEFAULT_LIMIT = 4;
    private static final int YMAL_DEFAULT_LIMIT = 8;
    private static final int LIMIT_CEILING = 24;

    private final FrequentlyBoughtTogetherUseCase frequentlyBoughtTogetherUseCase;
    private final YouMayAlsoLikeUseCase youMayAlsoLikeUseCase;

    public RecommendationsController(
            FrequentlyBoughtTogetherUseCase frequentlyBoughtTogetherUseCase,
            YouMayAlsoLikeUseCase youMayAlsoLikeUseCase
    ) {
        this.frequentlyBoughtTogetherUseCase = frequentlyBoughtTogetherUseCase;
        this.youMayAlsoLikeUseCase = youMayAlsoLikeUseCase;
    }

    @GetMapping("/frequently-bought-together")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<List<RecommendationItem>> frequentlyBoughtTogether(
            @RequestParam("productId") @NotBlank String productId,
            @RequestParam(value = "limit", required = false) @Min(1) @Max(LIMIT_CEILING) Integer limit
    ) {
        int effectiveLimit = clamp(limit, FBT_DEFAULT_LIMIT);
        List<ProductProjection> projections = frequentlyBoughtTogetherUseCase.findFor(productId, effectiveLimit);
        return ApiResponse.ok(projections.stream().map(RecommendationItem::fromProjection).toList());
    }

    @GetMapping("/you-may-also-like")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<List<RecommendationItem>> youMayAlsoLike(
            @RequestParam("productId") @NotBlank String productId,
            @RequestParam(value = "limit", required = false) @Min(1) @Max(LIMIT_CEILING) Integer limit
    ) {
        int effectiveLimit = clamp(limit, YMAL_DEFAULT_LIMIT);
        List<ProductProjection> projections = youMayAlsoLikeUseCase.findFor(productId, effectiveLimit);
        return ApiResponse.ok(projections.stream().map(RecommendationItem::fromProjection).toList());
    }

    private static int clamp(Integer requested, int fallback) {
        if (requested == null || requested <= 0) return fallback;
        return Math.min(requested, LIMIT_CEILING);
    }
}
